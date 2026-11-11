#!/usr/bin/env python3
"""Recompute component scores without accepting native aggregate assertions."""
import argparse
import base64
import collections
import json
from pathlib import Path


def analyze(path):
    raw = [dict(v.split("=", 1) for v in line.split("\t")) for line in Path(path).read_text().splitlines()]
    rows = [r for r in raw if "id" in r]
    if len(rows) != 24 or {r["id"] for r in rows} != {f"ctx-{n:02d}" for n in range(1, 25)}:
        raise ValueError("incomplete or duplicate frozen component cases")
    for row in rows:
        a = row["A"] == row["expected"] and row["Afocus64"] == row["expectedFocus64"] and row["input64"] == row["expectedText64"]
        b = row["B"] == row["expected"] and row["Bfocus64"] == row["expectedFocus64"] and row["Beffective64"] == row["expectedText64"]
        if a != (row["Apass"] == "true") or b != (row["Bpass"] == "true"):
            raise ValueError("native result disagrees with independent recomputation")
    groups = collections.defaultdict(list)
    for row in rows:
        groups[row["group"]].append(row)
    return {
        "case_count": 24, "Apass": sum(r["Apass"] == "true" for r in rows),
        "Bpass": sum(r["Bpass"] == "true" for r in rows),
        "strata": {g: {"cases": len(rs), "Apass": sum(r["Apass"] == "true" for r in rs),
                       "Bpass": sum(r["Bpass"] == "true" for r in rs)} for g, rs in groups.items()},
        "regressions": [r["id"] for r in rows if r["Apass"] == "true" and r["Bpass"] != "true"],
        "remaining_failures": [{"id": r["id"], "input": base64.b64decode(r["input64"]).decode(),
                                "expected": r["expected"], "actual": r["B"]} for r in rows if r["Bpass"] != "true"],
        "all_advisory_invariant": all(r["advisoryInvariant"] == "true" for r in rows),
        "actual_tool_grants": sum(r["toolGrant"] == "true" for r in rows),
        "training_starts": sum(r["trainingStarted"] == "true" for r in rows),
        "scope": "native components only; full app A/B/C ASR and Fold7 NOT_RUN"
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--stdout", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = analyze(args.stdout)
    with args.output.open("x") as output:
        output.write(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(result, ensure_ascii=False, indent=2))
