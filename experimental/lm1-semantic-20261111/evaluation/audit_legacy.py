#!/usr/bin/env python3
"""Recheck historical evidence without model inference."""

import argparse
import collections
import csv
import hashlib
import importlib.util
import json
from pathlib import Path
import sys

sys.dont_write_bytecode = True


def module(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    assert not args.output.exists()
    root = args.repo / "experimental/weight-space-20261110"
    inventory = {}
    for path in sorted(root.rglob("*")):
        if path.is_file() and "__pycache__" not in path.parts:
            content = path.read_bytes()
            record = {"bytes": len(content), "sha256": hashlib.sha256(content).hexdigest()}
            if path.suffix not in (".lm1", ".zip"):
                text = content.decode("utf-8")
                record["textLinesRead"] = len(text.splitlines())
                if path.suffix == ".json":
                    json.loads(text)
                    record["jsonParsed"] = True
            inventory[str(path.relative_to(args.repo))] = record
    base = module(root / "evaluation/contrast-audit-v1/analyze_contrast.py", "old_contrast")
    distill = module(root / "evaluation/distill-v1/analyze_distill.py", "old_distill")
    reader = module(root / "evaluation/audit_lm1_weights.py", "old_weights")
    original = reader.read(args.baseline)
    assert original["sha256"] == "7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd"
    expected = {"dev": base.read_rows(root / "data/note-contrasts-v1/dev.tsv"),
                "retention": base.read_rows(root / "data/retention.tsv")}
    reports, before, rows = {}, None, None
    for name, directory, artifact in (
        ("answer", "training/contrast-answer-results-v1", "contrast-answer-128.lm1"),
        ("replay", "training/contrast-replay-results-v1", "contrast-replay-128.lm1"),
        ("teacher", "training/distill-v1/results-v1", "distill-128.lm1"),
    ):
        text = (root / directory / "stdout.tsv").read_text()
        receipt = json.loads((root / directory / "receipt.json").read_text())
        assert receipt["exitCode"] == 0 and not receipt["timedOut"]
        assert receipt["stdoutSha256"] == sha(root / directory / "stdout.tsv")
        records = distill.parse_log(text, base) if name == "teacher" else base.parse_log(text)
        result = distill.analyze_records(records, expected, base) if name == "teacher" else base.analyze_log(records, expected, 128, 512)
        baseline = distill.baseline_records(text)
        if before is None:
            before = baseline
            rows = [r for r in records["CASE"] if r["step"] == "0" and r["split"] == "retention"]
        assert before == baseline and len(baseline) == 135
        candidate = reader.read(root / "artifacts" / artifact)
        assert candidate["sha256"] == receipt["artifacts"]["final.lm1"]["sha256"]
        reports[name] = {"analysis": result, "weightDelta": reader.compare(original, candidate),
                         "checkpointSha256": candidate["sha256"], "loggedUpdates": len(records["UPDATE"])}
    shortcut = {}
    for split in ("train", "dev"):
        data = list(csv.DictReader((root / f"data/note-contrasts-v1/{split}.tsv").read_text().splitlines(), delimiter="\t"))
        correct = sum(("0" if "nicht" in row["prompt"].casefold() else "3") == row["label"] for row in data)
        shortcut[split] = {"correct": correct, "total": len(data)}
    report = {"schema": "lm1-historical-evidence-recheck-v1", "basisCommit": "1d9eab1275a7fcc92ad4c5e2d1dd52d1db411667",
              "fileInventory": inventory, "fileCount": len(inventory), "bytesRead": sum(x["bytes"] for x in inventory.values()),
              "modelUpdates": 0, "modelInference": 0, "baselineRecordsIdentical": True,
              "originalRetention": {"confusion": dict(collections.Counter(row["expected"] + "->" + row["predictedRestricted"] for row in rows)),
                 "predictions": dict(collections.Counter(row["predictedRestricted"] for row in rows)),
                 "originalCorrectIds": [row["id"] for row in rows if row["expected"] == row["predictedRestricted"]]},
              "legacyKeywordControl": shortcut, "candidates": reports,
              "limits": ["Historical registrations contain original absolute paths; current-file/raw-log recheck is not recreation of that past environment.",
                         "Complete file inventory and structural parsing do not by themselves certify every historical algorithm.",
                         "Old DEV was openly inspected and remains non-independent."]}
    with args.output.open("x") as stream:
        json.dump(report, stream, indent=2, sort_keys=True)
        stream.write("\n")
    print(json.dumps({"filesRead": report["fileCount"], "bytesRead": report["bytesRead"],
                      "legacyKeywordControl": shortcut, "originalRetention": report["originalRetention"]["confusion"],
                      "candidateUpdates": {k: v["loggedUpdates"] for k, v in reports.items()}}))


if __name__ == "__main__":
    main()
