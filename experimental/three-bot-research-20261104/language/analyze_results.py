#!/usr/bin/env python3
"""Summarize recorded observations; never train, choose, or promote a model."""
from __future__ import annotations

import argparse
from collections import defaultdict
import hashlib
import json
from pathlib import Path
import struct


def parameter_digest(path: Path) -> str:
    raw = path.read_bytes()
    body, checksum = raw[:-32], raw[-32:]
    if hashlib.sha256(body).digest() != checksum:
        raise ValueError(f"damaged checkpoint: {path}")
    at = 0

    def integer() -> int:
        nonlocal at
        value, = struct.unpack_from(">i", body, at)
        at += 4
        return value

    def skip_utf() -> None:
        nonlocal at
        length, = struct.unpack_from(">H", body, at)
        at += 2 + length

    if integer() != 0x4C4D3143 or integer() != 1:
        raise ValueError("not LM1C version 1")
    for _ in range(7):
        integer()
    skip_utf()
    count = integer()
    if not 0 <= count <= 64:
        raise ValueError("invalid metadata count")
    for _ in range(count):
        skip_utf()
        skip_utf()
    return hashlib.sha256(body[at:]).hexdigest()


def main() -> None:
    root = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", type=Path, default=root / "run-20261104")
    args = parser.parse_args()
    folder = args.run.resolve(strict=True)
    if not folder.is_relative_to(root):
        raise ValueError("results must be inside research/language")
    raw = json.loads((folder / "results.json").read_text())
    events = raw["events"]
    probes = [e for e in events if e["kind"] == "probe"]
    training = [e for e in events if e["kind"] == "training"]
    groups = defaultdict(list)
    for p in probes:
        groups[(p["arm"], p["seed"], p["split"], p["promptKind"])].append(p)
    summary_groups = []
    for key, rows in groups.items():
        summary_groups.append(dict(zip(["arm", "seed", "split", "promptKind"], key)) | {
            "count": len(rows), "immediateEos": sum(r["newTokens"] == 0 and r["stop"] == "EOS" for r in rows),
            "meanPEos": sum(r["pEos"] for r in rows) / len(rows),
            "contentBpb": sum(r["contentBits"] for r in rows) / sum(r["utf8Bytes"] for r in rows),
            "meanEndTargetNll": sum(r["endTargetNll"] for r in rows) / len(rows),
        })
    null_controls = []
    content_controls = []
    for seed in sorted({r["seed"] for r in training}):
        a = next(r for r in training if r["seed"] == seed and r["arm"] == "original_eos")
        b = next(r for r in training if r["seed"] == seed and r["arm"] == "paragraph_end_eos")
        ablation = next(r for r in training if r["seed"] == seed and r["arm"] == "no_eos_target_ablation")
        da = parameter_digest(folder / f"{seed}-original_eos.lm1")
        db = parameter_digest(folder / f"{seed}-paragraph_end_eos.lm1")
        if a["tokenScheduleSha256"] != b["tokenScheduleSha256"] or da != db or a["losses"] != b["losses"]:
            raise ValueError("unexpected difference in null control")
        null_controls.append({"seed": seed, "identicalTokenSchedule": True, "identicalParameterBytes": True,
                              "parameterPayloadSha256": da, "identicalLosses": True})
        if a["contentScheduleSha256"] != ablation["contentScheduleSha256"]:
            raise ValueError("ablation content schedule mismatch")
        if a["trainingTokens"] - a["eosTargets"] != ablation["trainingTokens"]:
            raise ValueError("ablation changes more than EOS target count")
        content_controls.append({"seed": seed, "identicalContentSchedule": True,
                                 "originalTargets": a["trainingTokens"], "removedEosTargets": a["eosTargets"],
                                 "ablationTargets": ablation["trainingTokens"],
                                 "targetNormalizationChangePercent": 100 * a["eosTargets"] / a["trainingTokens"]})
    output = {"sourceResultsSha256": hashlib.sha256((folder / "results.json").read_bytes()).hexdigest(),
              "groups": summary_groups, "nullControls": null_controls, "contentControls": content_controls,
              "training": training, "growth": [e for e in events if e["kind"] == "growth_parity"],
              "capacityPrompts": [e for e in probes if e["id"].startswith("capacity_")],
              "decision": "No candidate selected or promoted; diagnostic EOS ablation is not a proposed repair."}
    (folder / "analysis.json").write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"groups": len(summary_groups), "nullControls": null_controls,
                      "contentControls": content_controls}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
