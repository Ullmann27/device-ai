#!/usr/bin/env python3
"""Audit open DEV contrast pairs without model execution."""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import csv
import hashlib
import importlib.util
import json
from pathlib import Path


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def keyed(rows, field):
    result = {}
    for row in rows:
        key = row[field]
        if key in result:
            raise ValueError("duplicate " + field)
        result[key] = row
    return result


def validate_pairs(rows, metadata):
    table, meta = keyed(rows, "id"), keyed(metadata, "id")
    if set(table) != set(meta):
        raise ValueError("metadata coverage mismatch")
    pairs = defaultdict(list)
    for identity, row in table.items():
        item = meta[identity]
        if row["label"] != item["label"]:
            raise ValueError("metadata label mismatch")
        if len(row["prompt"].encode()) != item["promptUtf8Bytes"]:
            raise ValueError("metadata byte count mismatch")
        pairs[item["pairId"]].append(row)
    for pair in pairs.values():
        if len(pair) != 2 or {r["label"] for r in pair} != {"0", "3"}:
            raise ValueError("invalid contrast pair")
        if len({len(r["prompt"].encode()) for r in pair}) != 1:
            raise ValueError("pair byte lengths differ")
        for field in ("familyId", "objectId", "lengthLevel", "directivePosition"):
            if len({meta[r["id"]][field] for r in pair}) != 1:
                raise ValueError("pair metadata differs: " + field)
    return table, meta, pairs


def parse_cases(text, expected, label_tokens):
    cases = defaultdict(dict)
    for line in text.splitlines():
        fields = line.split("\t")
        if fields[0] != "CASE":
            continue
        values = {}
        for field in fields[1:]:
            key, value = field.split("=", 1)
            if key in values:
                raise ValueError("duplicate CASE field")
            values[key] = value
        if values["split"] != "dev":
            continue
        step = int(values["step"])
        if step not in (0, 128):
            raise ValueError("unexpected evaluation step")
        identity = values["id"]
        if identity in cases[step] or identity not in expected:
            raise ValueError("duplicate or unknown CASE id")
        if values["expected"] != expected[identity]["label"]:
            raise ValueError("CASE label mismatch")
        if values["ended"] not in ("true", "false") or values["exact"] not in ("true", "false"):
            raise ValueError("invalid CASE boolean")
        if values["predictedRestricted"] not in ("0", "1", "2", "3"):
            raise ValueError("invalid restricted prediction")
        tokens = [int(t) for t in values["tokens"].split(",") if t]
        if len(tokens) > 4 or any(t < 0 or t >= 512 for t in tokens):
            raise ValueError("invalid generated tokens")
        exact = values["ended"] == "true" and tokens == label_tokens[values["expected"]]
        if exact != (values["exact"] == "true"):
            raise ValueError("CASE exact flag disagrees")
        cases[step][identity] = {**values, "exact": exact, "tokens": tokens}
    if set(cases) != {0, 128}:
        raise ValueError("missing evaluation step")
    if any(set(group) != set(expected) for group in cases.values()):
        raise ValueError("incomplete CASE coverage")
    return cases


def counts(rows):
    return {"rows": len(rows), "exact": sum(r["exact"] for r in rows),
            "restrictedCorrect": sum(r["predictedRestricted"] == r["expected"] for r in rows),
            "restrictedPredictions": dict(sorted(Counter(r["predictedRestricted"] for r in rows).items()))}


def validate_input_hashes(text, paths):
    found = {}
    for line in text.splitlines():
        if not line.startswith("INPUT\t"):
            continue
        fields = line.split("\t")[1:]
        values = dict(field.split("=", 1) for field in fields)
        if len(values) != len(fields):
            raise ValueError("duplicate INPUT field")
        name = values["file"]
        if name in found:
            raise ValueError("duplicate INPUT file")
        found[name] = values["sha256"]
    for path in paths:
        if found.get(path.name) != digest(path):
            raise ValueError("raw INPUT hash mismatch: " + path.name)


def summarize(cases, metadata, pairs):
    result = counts(list(cases.values()))
    result["pairs"] = {
        "total": len(pairs),
        "bothExact": sum(all(cases[r["id"]]["exact"] for r in pair) for pair in pairs.values()),
        "neitherExact": sum(not any(cases[r["id"]]["exact"] for r in pair) for pair in pairs.values()),
        "sameRestrictedPrediction": sum(len({cases[r["id"]]["predictedRestricted"] for r in pair}) == 1 for pair in pairs.values()),
        "sameGreedyOutput": sum(len({(tuple(cases[r["id"]]["tokens"]), cases[r["id"]]["ended"]) for r in pair}) == 1 for pair in pairs.values()),
    }
    result["groups"] = {}
    for field in ("label", "lengthLevel", "directivePosition", "familyId"):
        groups = defaultdict(list)
        for identity, case in cases.items():
            groups[metadata[identity][field]].append(case)
        result["groups"][field] = {name: counts(rows) for name, rows in sorted(groups.items())}
    return result


def analyze(data, log, tokenizer):
    validate_input_hashes(log.read_text(), [tokenizer, data / "note-contrasts-v1/train.tsv",
                                         data / "note-contrasts-v1/dev.tsv"])
    module_path = data / "audit_data_package.py"
    spec = importlib.util.spec_from_file_location("frozen_data_audit", module_path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    encoder = module.Tokenizer(tokenizer.read_bytes())
    label_tokens = {str(i): encoder.encode(str(i)) for i in range(4)}
    metadata_path = data / "note-contrasts-v1/row-metadata.json"
    metadata = json.loads(metadata_path.read_text())
    report = {"schema": "lm1-contrast-pair-diagnostic-v1", "scope": "POST_HOC_OPEN_DEV",
              "modelExecutions": 0, "parameterUpdates": 0, "promotion": False,
              "sourceHashes": {"analysis": digest(Path(__file__)), "tokenizer": digest(tokenizer),
                               "tokenizerImplementation": digest(module_path),
                               "metadata": digest(metadata_path), "rawLog": digest(log)},
              "dataset": {}, "results": {}}
    for split, expected_count in (("train", 128), ("dev", 64)):
        path = data / f"note-contrasts-v1/{split}.tsv"
        rows = list(csv.DictReader(path.read_text().splitlines(), delimiter="\t"))
        if len(rows) != expected_count:
            raise ValueError("dataset row count mismatch")
        table, meta, pairs = validate_pairs(rows, metadata[split])
        lengths = {r["id"]: 1 + len(encoder.encode("Auftrag: " + r["prompt"] + "\n"))
                   for r in rows}
        deltas = []
        for pair in pairs.values():
            by_label = {r["label"]: r for r in pair}
            deltas.append(lengths[by_label["0"]["id"]] - lengths[by_label["3"]["id"]])
        marker_correct = sum(("0" if "Nicht" in r["prompt"] else "3") == r["label"] for r in rows)
        report["sourceHashes"][split] = digest(path)
        report["dataset"][split] = {
            "rows": len(rows), "pairs": len(pairs), "allPairsEqualUtf8Bytes": True,
            "prefixTokenDeltaLabel0MinusLabel3": dict(sorted(Counter(deltas).items())),
            "prefixTokenCountByLabel": {label: dict(sorted(Counter(lengths[r["id"]] for r in rows if r["label"] == label).items())) for label in ("0", "3")},
            "postHocMarkerRule": {"rule": "contains Nicht => 0; otherwise => 3", "correct": marker_correct, "total": len(rows)},
        }
        if split == "dev":
            cases = parse_cases(log.read_text(), table, label_tokens)
            report["results"] = {str(step): summarize(group, meta, pairs) for step, group in sorted(cases.items())}
    report["limitations"] = [
        "Post-hoc open DEV diagnostics; no independent generalization evidence.",
        "Token counts use the frozen Python tokenizer port, not a new native execution.",
        "Equal bytes do not remove the observed token-length and marker shortcuts.",
        "Group differences describe this dataset; no causal attribution is established.",
        "Pair success requires both opposing labels, whereas row accuracy can reward class collapse.",
    ]
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--log", type=Path, required=True)
    parser.add_argument("--tokenizer", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(analyze(args.data, args.log, args.tokenizer), indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
