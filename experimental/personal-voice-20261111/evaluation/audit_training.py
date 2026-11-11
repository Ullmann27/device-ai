#!/usr/bin/env python3
"""Independent checkpoint, sample-schedule and TEST overlap audit; no training."""
import argparse
import collections
import csv
import importlib.util
import json
from pathlib import Path
import re


class JavaRandom:
    def __init__(self, seed):
        self.seed = (seed ^ 0x5DEECE66D) & ((1 << 48) - 1)

    def next(self, bits):
        self.seed = (self.seed * 0x5DEECE66D + 0xB) & ((1 << 48) - 1)
        return self.seed >> (48 - bits)

    def next_int(self, n):
        if n & (n - 1) == 0:
            return (n * self.next(31)) >> 31
        while True:
            bits = self.next(31)
            value = bits % n
            if bits - value + n - 1 < 1 << 31:
                return value


def rows(path):
    with Path(path).open() as stream:
        return list(csv.DictReader(stream, delimiter="\t"))


def tokens(text):
    return tuple(sorted(collections.Counter(re.findall(r"\w+", text.casefold())).items()))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo-root", required=True, type=Path)
    parser.add_argument("--training-root", required=True, type=Path)
    parser.add_argument("--data-root", required=True, type=Path)
    parser.add_argument("--baseline", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    spec = importlib.util.spec_from_file_location("weight_audit", args.repo_root / "experimental/weight-space-20261110/evaluation/audit_lm1_weights.py")
    auditor = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(auditor)
    baseline = auditor.read_bytes(args.baseline.read_bytes())
    if baseline["sha256"] != "7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd":
        raise ValueError("wrong original baseline")
    test = rows(args.repo_root / "experimental/personal-voice-20261111/evaluation/holdout/test.tsv")
    sets = {n: rows(args.data_root / f"{n}.tsv") for n in ("train", "dev", "supervised_replay")}
    overlaps = {n: {
        "exact_prompt_overlap": len({r["prompt"] for r in test} & {r["prompt"] for r in rs}),
        "wordbag_overlap": len({tokens(r["prompt"]) for r in test} & {tokens(r["prompt"]) for r in rs})
    } for n, rs in sets.items()}
    report = {"original_parameters": baseline["parameters"], "original_sha256": baseline["sha256"],
              "tokenizer_sha256": baseline["tokenizer"], "original_config": baseline["config"],
              "overlap_audit": overlaps, "weights": {}, "training_schedule": {}}
    groups = [[r for r in sets["train"] if r["label"] == str(i)] for i in range(4)]
    old_groups = [[r for r in sets["supervised_replay"] if r["label"] == str(i)] for i in (1, 2)]
    for arm in ("balanced-replay", "balanced-preserve"):
        model = auditor.read_bytes((args.training_root / "artifacts" / arm / "final.lm1").read_bytes())
        report["weights"][arm] = auditor.compare(baseline, model)
        parsed = collections.defaultdict(list)
        for line in (args.training_root / "runs" / arm / "stdout.tsv").read_text().splitlines():
            parts = line.split("\t")
            parsed[parts[0]].append(dict(item.split("=", 1) for item in parts[1:]))
        rng = JavaRandom(20261111)
        schedule_ok = True
        for step, actual in enumerate(parsed["SCHEDULE"]):
            ids = []
            for label in range(4):
                ids.append(groups[label][rng.next_int(len(groups[label]))]["id"])
                source = old_groups[label - 1] if label in (1, 2) else groups[label]
                ids.append(source[rng.next_int(len(source))]["id"])
            schedule_ok &= actual["answerIds"] == ",".join(ids) and int(actual["step"]) == step + 1
        updates = [int(x["step"]) for x in parsed["UPDATE"]]
        result = parsed["RESULT"][-1]
        teacher = parsed["TEACHER"]
        report["training_schedule"][arm] = {
            "schedule_matches_independent_java_rng": schedule_ok,
            "schedule_count": len(parsed["SCHEDULE"]),
            "updates_exactly_1_through_128": updates == list(range(1, 129)),
            "result_updates": result["updates"], "input_unchanged": result["inputUnchanged"],
            "model_record_sha256": parsed["MODEL"][0]["sha256"],
            "candidate_sha256_matches_log": parsed["CHECKPOINT"][0]["sha256"] == model["sha256"],
            "teacher_unchanged": None if not teacher else teacher[0]["parameterSha256"] == teacher[-1]["parameterSha256"] and teacher[0]["cacheSha256"] == teacher[-1]["cacheSha256"]
        }
    report["all_audits_passed"] = all(v["exact_prompt_overlap"] == 0 and v["wordbag_overlap"] == 0 for v in overlaps.values()) and all(
        x["schedule_matches_independent_java_rng"] and x["schedule_count"] == 128 and x["updates_exactly_1_through_128"] and
        x["candidate_sha256_matches_log"] and x["input_unchanged"] == "true" and x["model_record_sha256"] == baseline["sha256"]
        for x in report["training_schedule"].values())
    with args.output.open("x") as output:
        output.write(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
