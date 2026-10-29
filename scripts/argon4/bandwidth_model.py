#!/usr/bin/env python3
"""ARGON4 F2 - how many bytes must move per token? Analytical lower bounds for a ~500 GB model on a Fold7, a PC,
and both together. Every hardware number is an ASSUMPTION (a named variable with its source), not a measurement.

The physics in one line: if a token needs B bytes that are not already in fast memory, and the slowest link on
their path delivers W bytes/s, then t_token >= B / W, no matter how clever the scheduling. Overlap can at best
hide compute behind I/O (t >= max of the stages); it cannot make I/O disappear. Virtual memory, mmap and paging
change WHERE bytes wait, not how many must cross the link.

Usage: python3 -I scripts/argon4/bandwidth_model.py [out.json]
"""
import json
import sys
from dataclasses import dataclass, asdict


@dataclass(frozen=True)
class Model:
    name: str
    total_params: float          # parameters stored
    active_params: float         # parameters touched per token (dense: = total)
    routed_active_params: float  # part of active that lives in routed experts (0 for dense)
    routed_total_params: float   # all routed expert parameters (0 for dense)
    kv_bytes_per_token: float    # KV cache growth per context token (bytes, 16-bit)
    note: str


@dataclass(frozen=True)
class Device:
    name: str
    ram_for_model_gb: float      # RAM the model may occupy (after OS and app)
    dram_gbps: float             # effective DRAM bandwidth for streaming weights
    storage_gbps: float          # effective large-block read bandwidth of local storage
    link_gbps: float             # bandwidth to the other device (0 = none)
    compute_tflops: float        # effective dense matmul throughput for this precision class
    io_watts: float              # extra power while storage streams at full rate
    compute_watts: float         # extra power while computing at full rate
    sources: str


# DeepSeek-V3 (arXiv 2412.19437): 671B total, 37B activated per token; 256 routed experts (8 active) + 1 shared in
# 58 MoE layers, expert FFN 3 x 7168 x 2048. Routed active = 8 * 58 * 44.04M = 20.4B; the rest of the 37B
# (attention/MLA, shared experts, 3 dense layers, LM head) is touched every token. MLA KV ~ (512+64) x 61 layers x 2 B.
EXPERT = 3 * 7168 * 2048
DSV3 = Model("DeepSeek-V3-class MoE (671B/37B)", 671e9, 37e9, 8 * 58 * EXPERT, 256 * 58 * EXPERT, (512 + 64) * 61 * 2,
             "Published architecture; at ~6 bit/weight the checkpoint is ~500 GB, the 'hypothetical 500 GB DeepSeek'.")
DENSE = Model("Dense 500B (hypothetical)", 500e9, 500e9, 0.0, 0.0, 2 * 120 * 8 * 128 * 2,
              "Hypothetical dense model, 120 layers, GQA 8 KV heads x 128; every weight is read for every token.")
GEMMA_E4B = Model("Gemma 4 E4B (today's Continuum model)", 8e9, 4e9, 0.0, 0.0, 0.0,
                  "Effective ~4B per token per the 'E4B' naming; exact layer data not verified here (UNKNOWN).")

FOLD7 = Device("Galaxy Z Fold7 alone", 6.0, 50.0, 3.0, 0.0, 2.0, 2.0, 3.0,
               "12/16 GB LPDDR5X (spec sheets), 84.8 GB/s peak -> ~50 effective; UFS 4.0 ~4.2 GB/s peak -> ~3 "
               "effective for large blocks; ~2 TFLOP/s effective FP16-class on CPU/GPU is an assumption; "
               "watts are assumptions to be replaced by Fold7 measurements.")
PC = Device("Own PC (64 GB RAM, PCIe 4 NVMe)", 56.0, 60.0, 5.0, 0.0, 20.0, 6.0, 120.0,
            "DDR5 dual channel ~60 GB/s effective, NVMe PCIe 4.0 ~7 GB/s peak -> 5; ~20 TFLOP/s effective is an "
            "assumption for a mid-range GPU/CPU mix; 120 W compute is an assumption.")
FOLD7_PLUS_PC = Device("Fold7 + PC over USB 3.2 Gen 2", 6.0, 50.0, 3.0, 1.0, 2.0, 2.0, 3.0,
                       "Phone computes, PC serves weights over ~1 GB/s effective USB link (10 Gbit/s raw).")


def bytes_per_param(bits: float) -> float:
    return bits / 8.0


def per_token(model: Model, dev: Device, bits: float, hit_rate: float, context_tokens: int = 4096) -> dict:
    """Lower-bound time per token and where the bytes come from.

    The resident set (non-routed active weights) must fit in RAM to be read from DRAM every token; otherwise it is
    streamed too. Routed experts hit the RAM cache with probability hit_rate, else they come from storage (or link).
    """
    assert 0.0 <= hit_rate <= 1.0 and bits > 0
    bpp = bytes_per_param(bits)
    stored_gb = model.total_params * bpp / 1e9
    resident_gb = (model.active_params - model.routed_active_params) * bpp / 1e9
    kv_gb = model.kv_bytes_per_token * context_tokens / 1e9
    ram_needed_gb = resident_gb + kv_gb
    fits = ram_needed_gb <= dev.ram_for_model_gb
    cache_gb = max(0.0, dev.ram_for_model_gb - ram_needed_gb) if fits else 0.0
    routed_gb_tok = model.routed_active_params * bpp / 1e9
    miss_gb_tok = routed_gb_tok * (1.0 - hit_rate)
    if not fits:                       # resident part itself must stream: every active byte crosses storage
        miss_gb_tok = model.active_params * bpp / 1e9 - min(dev.ram_for_model_gb, model.active_params * bpp / 1e9)
    slow_bw = dev.link_gbps if dev.link_gbps > 0 else dev.storage_gbps
    t_io = miss_gb_tok / slow_bw
    t_dram = (resident_gb + kv_gb + routed_gb_tok * hit_rate) / dev.dram_gbps if fits else model.active_params * bpp / 1e9 / dev.dram_gbps
    t_compute = 2.0 * model.active_params / (dev.compute_tflops * 1e12)
    t_lower = max(t_io, t_dram, t_compute)
    energy_j = t_io * dev.io_watts + t_compute * dev.compute_watts
    return {
        "bits": bits, "hit_rate": hit_rate, "stored_gb": round(stored_gb, 1), "resident_gb": round(resident_gb, 2),
        "kv_gb": round(kv_gb, 3), "fits_resident": fits, "expert_cache_gb": round(cache_gb, 2),
        "expert_cache_share": round(min(1.0, cache_gb / (model.routed_total_params * bpp / 1e9)) if model.routed_total_params else 0.0, 4),
        "flash_gb_per_token": round(miss_gb_tok, 3), "t_io_s": round(t_io, 4), "t_dram_s": round(t_dram, 4),
        "t_compute_s": round(t_compute, 4), "t_lower_s": round(t_lower, 4),
        "tokens_per_s_upper": round(1.0 / t_lower, 3) if t_lower > 0 else None,
        "bound_by": ["io", "dram", "compute"][[t_io, t_dram, t_compute].index(t_lower)],
        "energy_j_per_token": round(energy_j, 3),
    }


def split_moe(model: Model, bits: float, link_gbps: float = 1.0, link_rtt_ms: float = 0.5, hidden: int = 7168,
              moe_layers: int = 58, pc: Device = PC, phone: Device = FOLD7, pc_hit_rate: float = 0.9) -> dict:
    """Phone keeps the resident (non-routed) part, the PC holds and computes the routed experts.

    Only activations cross the link: per MoE layer the hidden state goes to the PC and the expert output comes back
    (2 x hidden x 2 bytes). Each MoE layer is a round trip, so link latency multiplies by the layer count unless
    layers are batched speculatively. Weights never cross the link.
    """
    bpp = bytes_per_param(bits)
    act_gb = moe_layers * 2 * hidden * 2 / 1e9
    t_link = act_gb / link_gbps + moe_layers * link_rtt_ms / 1000.0
    resident_gb = (model.active_params - model.routed_active_params) * bpp / 1e9
    t_phone = max(resident_gb / phone.dram_gbps, 2 * (model.active_params - model.routed_active_params) / (phone.compute_tflops * 1e12))
    routed_gb = model.routed_active_params * bpp / 1e9
    t_pc = max(routed_gb * (1 - pc_hit_rate) / pc.storage_gbps, routed_gb * pc_hit_rate / pc.dram_gbps,
               2 * model.routed_active_params / (pc.compute_tflops * 1e12))
    t = t_phone + t_pc + t_link            # sequential per layer: no overlap assumed (pessimistic but honest)
    return {"bits": bits, "phone_resident_gb": round(resident_gb, 2), "phone_fits": resident_gb <= phone.ram_for_model_gb,
            "activation_mb_per_token": round(act_gb * 1000, 2), "t_link_s": round(t_link, 4), "t_phone_s": round(t_phone, 4),
            "t_pc_s": round(t_pc, 4), "t_total_s": round(t, 4), "tokens_per_s": round(1 / t, 3), "pc_hit_rate": pc_hit_rate,
            "link_gbps": link_gbps, "link_rtt_ms": link_rtt_ms}


BATTERY_J = 4400e-3 * 3.88 * 3600   # Fold7 4400 mAh at ~3.88 V nominal ~ 61.5 kJ (assumption: nominal voltage)


def table() -> dict:
    rows = []
    for model in (DSV3, DENSE):
        for dev in (FOLD7, FOLD7_PLUS_PC, PC):
            for bits in (16, 8, 6, 4, 3, 2):
                for hit in ((0.0, 0.5, 0.8, 0.9) if model.routed_total_params else (0.0,)):
                    r = per_token(model, dev, bits, hit)
                    r.update(model=model.name, device=dev.name,
                             answer_200_tokens_s=round(200 * r["t_lower_s"], 1),
                             battery_pct_200_tokens=round(100 * 200 * r["energy_j_per_token"] / BATTERY_J, 3) if dev is not PC else None)
                    rows.append(r)
    split = [split_moe(DSV3, bits, link, rtt) for bits in (6, 4, 3, 2) for (link, rtt) in ((1.0, 0.5), (0.25, 3.0))]
    return {
        "schema": "argon4-f2-bandwidth-model-v1",
        "split_moe": split,
        "assumptions": {"models": [asdict(m) for m in (DSV3, DENSE, GEMMA_E4B)],
                        "devices": [asdict(d) for d in (FOLD7, FOLD7_PLUS_PC, PC)],
                        "battery_joules": BATTERY_J},
        "rows": rows,
    }


def main() -> None:
    result = table()
    out = sys.argv[1] if len(sys.argv) > 1 else None
    text = json.dumps(result, indent=1, ensure_ascii=False)
    if out:
        with open(out, "w", encoding="utf-8") as f:
            f.write(text + "\n")
    for r in result["split_moe"]:
        print(f'SPLIT phone-resident/PC-experts | {r["bits"]} bit | link {r["link_gbps"]} GB/s rtt {r["link_rtt_ms"]} ms | '
              f'phone RAM {r["phone_resident_gb"]} GB fits={r["phone_fits"]} | {r["tokens_per_s"]} tok/s')
    for r in result["rows"]:
        if r["bits"] in (6, 4, 2):
            print(f'{r["model"][:28]:28} | {r["device"][:30]:30} | {r["bits"]:>2} bit | hit {r["hit_rate"]:.1f} | '
                  f'flash {r["flash_gb_per_token"]:8.3f} GB/tok | t>= {r["t_lower_s"]:9.4f} s ({r["bound_by"]}) | '
                  f'<= {r["tokens_per_s_upper"]} tok/s')


if __name__ == "__main__":
    main()
