#!/usr/bin/env python3
"""Analytische LM1-Parameterzählung aus LmConfig.layout() (app/src/main/java/at/heinz/deviceai/learning/lm/LmModel.kt).

Reine Arithmetik, keine Modellprozesse, keine Dateien werden geschrieben.

    python3 tools/parameter-audit/lm1_param_count.py            # Tabelle aller bekannten Profile
    python3 tools/parameter-audit/lm1_param_count.py --json     # maschinenlesbar
    python3 tools/parameter-audit/lm1_param_count.py --arch mingru --vocab 512 --dim 512 --layers 5 --mlp 2048
"""
from __future__ import annotations

import argparse
import json

MAX_CHECKPOINT_BYTES = 64 << 20  # LmRuntime.MAX_CHECKPOINT_BYTES = 64 shl 20 = 67.108.864


def layout(arch: str, vocab: int, dim: int, layers: int, context: int = 256, mlp: int | None = None):
    """Spiegelt LmConfig.layout() 1:1 (Name, Zeilen, Spalten) in Checkpoint-Reihenfolge."""
    arch = arch.upper()
    if mlp is None:
        mlp = 4 * dim
    assert 258 <= vocab <= 8192 and 8 <= dim <= 512 and 1 <= layers <= 12, "LmConfig.init require() verletzt"
    assert 8 <= context <= 2048 and 8 <= mlp <= 4096, "LmConfig.init require() verletzt"
    out = [("tok", vocab, dim)]
    if arch == "TRANSFORMER":
        out.append(("pos", context, dim))
    for l in range(layers):
        out.append((f"l{l}.n1", 1, dim))
        if arch == "TRANSFORMER":
            for w in ("wq", "wk", "wv", "wo"):
                out.append((f"l{l}.{w}", dim, dim))
        else:
            out.append((f"l{l}.wz", dim, dim))
            out.append((f"l{l}.bz", 1, dim))
            for w in ("wc", "wg", "wo"):
                out.append((f"l{l}.{w}", dim, dim))
        out.append((f"l{l}.n2", 1, dim))
        out.append((f"l{l}.w1", dim, mlp))
        out.append((f"l{l}.b1", 1, mlp))
        out.append((f"l{l}.w2", mlp, dim))
        out.append((f"l{l}.b2", 1, dim))
    out.append(("nf", 1, dim))
    return out


def count(arch: str, vocab: int, dim: int, layers: int, context: int = 256, mlp: int | None = None) -> int:
    return sum(r * c for _, r, c in layout(arch, vocab, dim, layers, context, mlp))


def checkpoint_bytes_estimate(arch: str, vocab: int, dim: int, layers: int, context: int = 256,
                              mlp: int | None = None, tokenizer_sha_len: int = 64, meta: dict[str, str] | None = None) -> int:
    """Exakte Bytegröße eines LM1C-Checkpoints (LmCheckpoint.encode) bei gegebenen Metadaten.

    Header: 9 Ints (magic, version, arch, vocab, dim, layers, heads, context, mlp) = 36 Bytes,
    writeUTF(tokenizerSha) = 2 + 64, writeInt(meta.size) = 4,
    je Meta-Eintrag 2+len(k) + 2+len(v), je Matrix writeUTF(name) = 2+len(name), 2 Ints = 8, rows*cols*4 Bytes,
    plus 32 Bytes SHA-256-Trailer.
    """
    meta = meta or {}
    size = 36 + 2 + tokenizer_sha_len + 4
    for k, v in meta.items():
        size += 2 + len(k.encode("utf-8")[:64]) + 2 + len(v.encode("utf-8")[:512])
    for name, r, c in layout(arch, vocab, dim, layers, context, mlp):
        size += 2 + len(name) + 8 + 4 * r * c
    return size + 32


# Profile, die in Repo-Dokumenten/Belegen genannt werden (Quelle siehe GAMMA-PARAMETER-AUDIT-20261104.md).
PROFILES = [
    # name, arch, vocab, dim, layers, mlp, Quelle
    ("LM1-TS-v512 (Transformer, docs/evidence/lm1-20261022/CHECKPOINTS.tsv)", "TRANSFORMER", 512, 32, 2, 128),
    ("LM1-GM-v512 Original 64/2 (7a0af1e9…)", "MINGRU", 512, 64, 2, 256),
    ("Ladder 128/2 (width-2)", "MINGRU", 512, 128, 2, 512),
    ("Ladder 256/2 (width-4)", "MINGRU", 512, 256, 2, 1024),
    ("Ladder 512/2 (width-8) = core-6562304", "MINGRU", 512, 512, 2, 2048),
    ("Ladder 512/3 (depth-3) = core-9712128", "MINGRU", 512, 512, 3, 2048),
    ("Ladder 512/4 (depth-4)", "MINGRU", 512, 512, 4, 2048),
    ("Ladder 512/5 (depth-5) '16M'", "MINGRU", 512, 512, 5, 2048),
    ("Ladder 512/6 (depth-6, abgelehnt)", "MINGRU", 512, 512, 6, 2048),
    ("Maximum LmConfig erlaubt: 8192/512/12/4096 MINGRU", "MINGRU", 8192, 512, 12, 4096),
]


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--arch", choices=["mingru", "transformer"])
    ap.add_argument("--vocab", type=int, default=512)
    ap.add_argument("--dim", type=int)
    ap.add_argument("--layers", type=int)
    ap.add_argument("--mlp", type=int)
    ap.add_argument("--context", type=int, default=256)
    ap.add_argument("--json", action="store_true")
    a = ap.parse_args()

    if a.arch:
        n = count(a.arch, a.vocab, a.dim, a.layers, a.context, a.mlp)
        b = checkpoint_bytes_estimate(a.arch, a.vocab, a.dim, a.layers, a.context, a.mlp)
        print(json.dumps({"arch": a.arch, "vocab": a.vocab, "dim": a.dim, "layers": a.layers,
                          "mlp": a.mlp or 4 * a.dim, "context": a.context, "params": n,
                          "param_bytes_f32": 4 * n, "checkpoint_bytes_without_meta": b,
                          "exceeds_64MiB_contract": b > MAX_CHECKPOINT_BYTES}, indent=2))
        return

    rows = []
    for name, arch, vocab, dim, layers, mlp in PROFILES:
        n = count(arch, vocab, dim, layers, 256, mlp)
        b = checkpoint_bytes_estimate(arch, vocab, dim, layers, 256, mlp)
        rows.append({"profile": name, "arch": arch, "vocab": vocab, "dim": dim, "layers": layers, "mlp": mlp,
                     "params": n, "param_bytes_f32": 4 * n, "ckpt_bytes_no_meta": b,
                     "over_64MiB": b > MAX_CHECKPOINT_BYTES})
    if a.json:
        print(json.dumps(rows, indent=2, ensure_ascii=False))
        return
    print(f"{'Profil':<62} {'arch':<11} {'dim':>4} {'L':>2} {'mlp':>5} {'Parameter':>12} {'f32-Bytes':>12} {'LM1C-Bytes*':>12} 64MiB?")
    for r in rows:
        print(f"{r['profile']:<62} {r['arch']:<11} {r['dim']:>4} {r['layers']:>2} {r['mlp']:>5} "
              f"{r['params']:>12,} {r['param_bytes_f32']:>12,} {r['ckpt_bytes_no_meta']:>12,} {'ÜBER' if r['over_64MiB'] else 'ok'}")
    print("* LM1C-Bytes ohne Metadaten (Header + Namen + Floats + SHA-256-Trailer); tatsächliche Dateien enthalten zusätzlich Meta-Strings.")


if __name__ == "__main__":
    main()
