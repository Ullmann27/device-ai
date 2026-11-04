#!/usr/bin/env python3
"""Validate every frozen observation."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import statistics


def read(path, phase, group):
    rows, warnings = [], []
    for line in path.read_text().splitlines():
        try:
            rows.append(json.loads(line))
        except json.JSONDecodeError:
            if not rows and not warnings and re.fullmatch(r"\[\d+\.\d+s\]\[warning\]\[perf,memops\] Cannot use file /tmp/hsperfdata_agent/\d+ because it is locked by another process \(errno = 11\)", line):
                warnings.append(line)
            else:
                raise
    expected = set()
    for kind in ("nll", "distribution"):
        if group in ("all", kind):
            expected.update(f"{kind}-{arch}-v{v}-t{t}" for arch in ("TRANSFORMER", "MINGRU")
                            for v, t in ((258, 1), (258, 16), (2048, 1), (2048, 64)))
    if group in ("all", "tokenizer"):
        expected.update(f"token-{kind}-{case}" for kind in ("bytes", "merged")
                        for case in ("empty", "single", "sentence", "unicode", "unique-word"))
    assert len(rows) == 2 + 18 * len(expected)
    assert rows[0]["type"] == "meta" and rows[-1]["type"] == "complete"
    assert rows[0]["phase"] == rows[-1]["phase"] == phase and rows[0]["group"] == group
    assert rows[0]["cases"] == rows[-1]["cases"] == len(expected) and rows[0]["rounds"] == 9
    samples = rows[1:-1]
    assert all(x["type"] == "sample" and x["phase"] == phase for x in samples)
    assert {x["case"] for x in samples} == expected
    for i in range(0, len(samples), 2):
        a, b = samples[i:i + 2]
        assert a["case"] == b["case"] and a["round"] == b["round"]
        assert [a["side"], b["side"]] == (["baseline", "candidate"] if a["round"] % 2 == 0 else ["candidate", "baseline"])
        for key in ("checksum", "iterations", "fixtureSha256"):
            assert a[key] == b[key]
    result = []
    for name in sorted(expected):
        members = [x for x in samples if x["case"] == name]
        by_side = {s: sorted((x for x in members if x["side"] == s), key=lambda x: x["round"])
                   for s in ("baseline", "candidate")}
        assert all([x["round"] for x in values] == list(range(9)) for values in by_side.values())
        assert len({x["fixtureSha256"] for x in members}) == 1
        metrics = {}
        for metric in ("wallNs", "cpuNs", "allocatedBytes"):
            a = [x[metric] / x["iterations"] for x in by_side["baseline"]]
            b = [x[metric] / x["iterations"] for x in by_side["candidate"]]
            assert min(a + b) >= 0
            before, after = statistics.median(a), statistics.median(b)
            metrics[metric] = {"baselineMedian": before, "candidateMedian": after,
                               "changeOfMediansPercent": None if before == 0 else (after / before - 1) * 100,
                               "pairedMedianDelta": statistics.median(y - x for x, y in zip(a, b)),
                               "candidateLowerPairs": sum(y < x for x, y in zip(a, b)),
                               "candidateHigherPairs": sum(y > x for x, y in zip(a, b)),
                               "baselineRange": [min(a), max(a)], "candidateRange": [min(b), max(b)]}
        result.append({"case": name, "fixtureSha256": members[0]["fixtureSha256"], "metrics": metrics})
    return {"phase": phase, "group": group, "environment": rows[0], "complete": rows[-1],
            "rawSha256": hashlib.sha256(path.read_bytes()).hexdigest(), "warnings": warnings,
            "caseCount": len(expected), "sampleCount": len(samples), "cases": result}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path)
    parser.add_argument("phase")
    parser.add_argument("group", choices=("all", "nll", "distribution", "tokenizer"))
    parser.add_argument("--control", type=Path)
    args = parser.parse_args()
    summary = read(args.directory / f"{args.phase}-{args.group}.jsonl", args.phase, args.group)
    if args.control:
        control = read(args.control / "aa-all.jsonl", "aa", "all")
        controls = {x["case"]: x for x in control["cases"]}
        for case in summary["cases"]:
            original = controls[case["case"]]
            assert case["fixtureSha256"] == original["fixtureSha256"]
            case["identicalSourceControl"] = original["metrics"]
    manifest = json.loads((args.directory / "manifest.json").read_text())
    summary["sourceSha256"] = manifest["sourceSha256"]
    summary["baselineCommit"] = manifest["resolvedBaseline"]
    summary["status"] = "VALIDATED_COMPLETE_MATRIX"
    path = args.directory / "summary.json"
    path.write_text(json.dumps(summary, indent=2) + "\n")
    for case in summary["cases"]:
        a, c = case["metrics"]["allocatedBytes"], case["metrics"]["cpuNs"]
        print(f"{case['case']}: allocation {a['baselineMedian']:.3f}->{a['candidateMedian']:.3f} B; CPU {c['baselineMedian']/1000:.3f}->{c['candidateMedian']/1000:.3f} us; higher {c['candidateHigherPairs']}/9")
