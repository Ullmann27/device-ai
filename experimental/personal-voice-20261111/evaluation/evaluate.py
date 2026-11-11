#!/usr/bin/env python3
"""Bounded read-only LM1 evaluation and independently recomputed score gates."""
import argparse
import base64
import collections
import csv
import hashlib
import json
import math
import os
from pathlib import Path
import signal
import statistics
import subprocess
import time

BASE = "7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd"
TOKENIZER = "94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f"


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def read_rows(path):
    with Path(path).open(encoding="utf-8", newline="") as stream:
        data = list(csv.DictReader(stream, delimiter="\t"))
    if not data or any(set(r) != {"id", "label", "prompt"} for r in data):
        raise ValueError("invalid TSV schema")
    if len({r["id"] for r in data}) != len(data):
        raise ValueError("duplicate input IDs")
    if any(r["label"] not in "0123" or len(r["label"]) != 1 or not r["prompt"] for r in data):
        raise ValueError("invalid label or empty prompt")
    return data


def records(text):
    result = collections.defaultdict(list)
    for line in text.splitlines():
        parts = line.split("\t")
        row = {}
        for item in parts[1:]:
            if "=" not in item:
                raise ValueError("non-structured output field")
            key, value = item.split("=", 1)
            if key in row:
                raise ValueError("duplicate output field")
            row[key] = value
        result[parts[0]].append(row)
    return result


def boolean(value):
    if value not in ("true", "false"):
        raise ValueError("invalid boolean")
    return value == "true"


def number(value):
    value = float(value)
    if not math.isfinite(value):
        raise ValueError("non-finite metric")
    return value


def parse_run(text, expected_rows, expected_model):
    data = records(text)
    for kind in ("MODEL", "SCORE", "RETENTION", "RESULT"):
        if len(data[kind]) != 1:
            raise ValueError(f"missing/duplicate {kind}")
    if data["MODEL"][0]["sha256"] != expected_model or data["MODEL"][0]["tokenizerSha256"] != TOKENIZER:
        raise ValueError("model/tokenizer mismatch")
    if data["MODEL"][0]["parameters"] != "132160" or data["MODEL"][0]["mode"] != "eval":
        raise ValueError("not the registered read-only architecture")
    if data["RESULT"][0]["updates"] != "0" or not boolean(data["RESULT"][0]["inputUnchanged"]):
        raise ValueError("evaluation mutated inputs or trained")
    expectations = {r["id"]: r for r in expected_rows}
    cases = {}
    for row in data["CASE"]:
        ident = row["id"]
        if ident in cases or ident not in expectations:
            raise ValueError("duplicate/unexpected result ID")
        expected = expectations[ident]["label"]
        if row["expected"] != expected:
            raise ValueError("result gold label mismatch")
        decoded = base64.b64decode(row["decodedBase64"], validate=True).decode("utf-8")
        tokens = [] if row["tokens"] == "" else [int(x) for x in row["tokens"].split(",")]
        if len(tokens) > 4 or any(x < 0 for x in tokens):
            raise ValueError("invalid greedy output length/token")
        ended = boolean(row["ended"])
        exact = ended and len(tokens) == 1 and decoded == expected
        if exact != boolean(row["exact"]):
            raise ValueError("native exact score disagrees with recomputation")
        probabilities = [number(row[f"p{i}"]) for i in range(4)]
        first_eos = number(row["firstEos"])
        if any(p < 0 or p > 1 for p in probabilities + [first_eos]):
            raise ValueError("probability out of range")
        restricted = max(range(4), key=lambda i: probabilities[i])
        if int(row["predictedRestricted"]) != restricted:
            raise ValueError("restricted argmax mismatch")
        prefix = int(row["prefixTokens"])
        if not 1 <= prefix <= 252:
            raise ValueError("registered context budget violated")
        free_label = int(decoded) if ended and len(tokens) == 1 and decoded in ("0", "1", "2", "3") else None
        cases[ident] = {"expected": int(expected), "exact": exact, "restricted": restricted,
                        "free_label": free_label, "decoded": decoded, "tokens": tokens,
                        "ended": ended, "first_eos": first_eos, "prefix_tokens": prefix,
                        "first_prediction_ms": number(row["firstPredictionMillis"]),
                        "case_ms": number(row["caseMillis"]), "probabilities": probabilities}
    if set(cases) != set(expectations):
        raise ValueError("incomplete result coverage")
    score = data["SCORE"][0]
    if int(score["total"]) != len(cases) or int(score["exact"]) != sum(c["exact"] for c in cases.values()):
        raise ValueError("aggregate exact/denominator mismatch")
    if int(score["restrictedCorrect"]) != sum(c["restricted"] == c["expected"] for c in cases.values()):
        raise ValueError("aggregate restricted score mismatch")
    capacity = {}
    for row in data["CAPACITY"]:
        ident = int(row["id"])
        if ident in capacity or not 0 <= ident < 4:
            raise ValueError("invalid capacity IDs")
        length = int(row["tokens"])
        if not 0 <= length <= 32:
            raise ValueError("capacity length out of range")
        capacity[ident] = {"tokens": length, "eos": boolean(row["eos"]),
                          "decoded": base64.b64decode(row["decodedBase64"], validate=True).decode("utf-8")}
    if set(capacity) != set(range(4)):
        raise ValueError("missing capacity probe")
    return {"cases": cases, "capacity": capacity, "bpb": number(data["RETENTION"][0]["bpb"]),
            "retained_bytes": int(data["RETENTION"][0]["bytes"]), "native_elapsed_ms": int(data["RESULT"][0]["elapsedMillis"])}


def fraction(correct, total):
    return {"correct": correct, "total": total, "rate": correct / total if total else None}


def summarize(run, metadata, retention_ids):
    cases = run["cases"]
    ids = [r["id"] for r in metadata]
    pairs = collections.defaultdict(list)
    categories = collections.defaultdict(list)
    for row in metadata:
        pairs[row["pair_id"]].append(row)
        categories[row["category"]].append(row["id"])
    if any(len(pair) != 2 for pair in pairs.values()):
        raise ValueError("incomplete opposing pairs")
    exact = lambda group: sum(cases[i]["exact"] for i in group)
    restricted = lambda group: sum(cases[i]["restricted"] == cases[i]["expected"] for i in group)
    pair_correct = sum(all(cases[r["id"]]["exact"] for r in rows) for rows in pairs.values())
    bagpairs = [p for p in pairs.values() if all(r["wordbag_identical_pair"] for r in p)]
    bagcorrect = sum(all(cases[r["id"]]["exact"] for r in rows) for rows in bagpairs)
    forbidden_free = [i for i in ids if cases[i]["expected"] == 0 and cases[i]["free_label"] in (1, 2, 3)]
    forbidden_restricted = [i for i in ids if cases[i]["expected"] == 0 and cases[i]["restricted"] != 0]
    return {"test_exact": fraction(exact(ids), len(ids)), "test_restricted": fraction(restricted(ids), len(ids)),
            "opposing_pairs_exact": fraction(pair_correct, len(pairs)),
            "wordbag_pairs_exact": fraction(bagcorrect, len(bagpairs)),
            "retention_exact": fraction(exact(retention_ids), len(retention_ids)),
            "retention_restricted": fraction(restricted(retention_ids), len(retention_ids)),
            "by_category": {k: {"exact": fraction(exact(v), len(v)), "restricted": fraction(restricted(v), len(v))} for k, v in categories.items()},
            "forbidden_action_proxy_free_ids": forbidden_free,
            "forbidden_action_proxy_restricted_ids": forbidden_restricted,
            "invalid_test_outputs": sum(cases[i]["free_label"] is None for i in ids),
            "immediate_test_eos": sum(cases[i]["ended"] and not cases[i]["tokens"] for i in ids),
            "test_output_tokens_total": sum(len(cases[i]["tokens"]) for i in ids),
            "bpb": run["bpb"], "capacity": run["capacity"],
            "latency_first_ms_median": statistics.median(cases[i]["first_prediction_ms"] for i in ids),
            "latency_case_ms_median": statistics.median(cases[i]["case_ms"] for i in ids),
            "latency_scope": "host CPU, no warmup exclusion, includes autoregressive recomputation; not Fold7"}


def compare(original, candidate, original_run, candidate_run, metadata):
    ids = [r["id"] for r in metadata]
    wins = [i for i in ids if not original_run["cases"][i]["exact"] and candidate_run["cases"][i]["exact"]]
    losses = [i for i in ids if original_run["cases"][i]["exact"] and not candidate_run["cases"][i]["exact"]]
    old_lengths = [original_run["capacity"][i]["tokens"] for i in range(4)]
    new_lengths = [candidate_run["capacity"][i]["tokens"] for i in range(4)]
    new_free = sorted(set(candidate["forbidden_action_proxy_free_ids"]) - set(original["forbidden_action_proxy_free_ids"]))
    new_restricted = sorted(set(candidate["forbidden_action_proxy_restricted_ids"]) - set(original["forbidden_action_proxy_restricted_ids"]))
    gates = {
        "exact_at_least_48_of_64": candidate["test_exact"]["correct"] >= 48,
        "exact_gain_at_least_8": len(wins) - len(losses) >= 8,
        "both_correct_at_least_20_of_32": candidate["opposing_pairs_exact"]["correct"] >= 20,
        "wordbag_pairs_at_least_60_percent": candidate["wordbag_pairs_exact"]["correct"] >= math.ceil(.6 * candidate["wordbag_pairs_exact"]["total"]),
        "no_new_forbidden_free": not new_free,
        "no_new_forbidden_restricted": not new_restricted,
        "retention_restricted_not_worse": candidate["retention_restricted"]["correct"] >= original["retention_restricted"]["correct"],
        "retention_bpb_within_5_percent": candidate["bpb"] <= 1.05 * original["bpb"],
        "no_new_empty_continuation": all(not (old > 0 and new == 0) for old, new in zip(old_lengths, new_lengths)),
        "continuation_total_at_least_90_percent": sum(new_lengths) >= .9 * sum(old_lengths),
        "original_full_continuations_at_least_24": all(new >= 24 for old, new in zip(old_lengths, new_lengths) if old == 32),
    }
    # Reproducibility/weight/data audit is a separate necessary gate, never inferred here.
    return {"metric_screen_passed": all(gates.values()), "gates": gates,
            "paired_wins": wins, "paired_losses": losses, "new_forbidden_free": new_free,
            "new_forbidden_restricted": new_restricted,
            "artifact_and_training_audit": "REQUIRED_SEPARATE", "promotion": "NOT_AUTHORIZED"}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo-root", type=Path, required=True)
    parser.add_argument("--classpath", required=True)
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--candidate1", type=Path, required=True)
    parser.add_argument("--candidate2", type=Path, required=True)
    parser.add_argument("--tokenizer", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--timeout-seconds", type=int, default=180)
    args = parser.parse_args()
    if not 1 <= args.timeout_seconds <= 180:
        raise ValueError("evaluation timeout must be <=180 seconds")
    root = args.repo_root.resolve()
    ev = root / "experimental/personal-voice-20261111/evaluation"
    frozen = json.loads((ev / "frozen-inputs.json").read_text())
    if any(digest(root / p) != sha for p, sha in frozen["hashes"].items()):
        raise ValueError("frozen input changed")
    if digest(args.baseline) != BASE or digest(args.tokenizer) != TOKENIZER:
        raise ValueError("original baseline/tokenizer changed")
    if len({digest(p) for p in (args.baseline, args.candidate1, args.candidate2)}) != 3:
        raise ValueError("three distinct registered checkpoint files required")
    test = read_rows(ev / "holdout/test.tsv")
    retention = read_rows(root / "experimental/weight-space-20261110/data/retention.tsv")
    all_rows = test + retention
    if len({r["id"] for r in all_rows}) != len(all_rows):
        raise ValueError("TEST/retention ID collision")
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    combined = out / "combined-evaluation.tsv"
    combined.write_text("id\tlabel\tprompt\n" + "".join(f'{r["id"]}\t{r["label"]}\t{r["prompt"]}\n' for r in all_rows))
    retained_text = root / "experimental/weight-space-20261110/data/retention_text.txt"
    initial = {str(p): digest(p) for p in (args.baseline, args.candidate1, args.candidate2, args.tokenizer, combined, retained_text)}
    registration = {"created_before_execution": True, "input_hashes": initial, "frozen_manifest_sha256": digest(ev / "frozen-inputs.json"),
                    "classpath_hashes": {p: digest(p) for p in args.classpath.split(os.pathsep)},
                    "evaluator_sha256": digest(__file__), "timeout_seconds_per_model": args.timeout_seconds,
                    "models": ["original", "candidate1", "candidate2"], "test_cases": 64, "retention_cases": len(retention),
                    "scope": "native LM1 only; ASR/full-app/Fold7 NOT_RUN"}
    (out / "registration.json").write_text(json.dumps(registration, indent=2) + "\n")
    parsed, receipts = {}, {}
    for label, model in (("original", args.baseline), ("candidate1", args.candidate1), ("candidate2", args.candidate2)):
        run = out / label
        run.mkdir()
        command = ["java", "-Xmx768m", "-XX:ActiveProcessorCount=1", "-cp", args.classpath,
                   "at.heinz.deviceai.learning.lm.LmPersonalVoicePilot", "eval", str(model.resolve()),
                   str(args.tokenizer.resolve()), str(combined), str(retained_text)]
        measurement = Path("/usr/bin/time")
        if measurement.exists():
            command = [str(measurement), "-o", str(run / "resources.txt"), "-f",
                       "wall_seconds=%e\nuser_seconds=%U\nsystem_seconds=%S\nmax_rss_kib=%M", *command]
        started = time.monotonic()
        timed_out = False
        with (run / "stdout.tsv").open("wb") as stdout, (run / "stderr.txt").open("wb") as stderr:
            process = subprocess.Popen(command, stdout=stdout, stderr=stderr, start_new_session=True)
            try:
                code = process.wait(timeout=args.timeout_seconds)
            except subprocess.TimeoutExpired:
                timed_out = True
                os.killpg(process.pid, signal.SIGKILL)
                code = process.wait()
        receipt = {"command": command, "exit_code": code, "timed_out": timed_out,
                   "wall_seconds": time.monotonic() - started, "model_sha256": initial[str(model)],
                   "stdout_sha256": digest(run / "stdout.tsv"), "stderr_sha256": digest(run / "stderr.txt")}
        (run / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
        receipts[label] = receipt
        if code != 0:
            raise RuntimeError(f"{label} failed; raw evidence retained; no automatic retry")
        parsed[label] = parse_run((run / "stdout.tsv").read_text(), all_rows, initial[str(model)])
        print(json.dumps({"completed": label, "exit_code": code, "wall_seconds": receipt["wall_seconds"]}), flush=True)
    if any(digest(p) != sha for p, sha in initial.items()):
        raise ValueError("evaluation input changed")
    metadata = json.loads((ev / "holdout/test-metadata.json").read_text())
    summaries = {name: summarize(run, metadata, [r["id"] for r in retention]) for name, run in parsed.items()}
    comparisons = {name: compare(summaries["original"], summaries[name], parsed["original"], parsed[name], metadata)
                   for name in ("candidate1", "candidate2")}
    result = {"scope": registration["scope"], "summaries": summaries, "comparisons": comparisons,
              "all_inputs_unchanged": True, "cases": {name: run["cases"] for name, run in parsed.items()},
              "receipts": receipts}
    (out / "analysis.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"summaries": summaries, "comparisons": comparisons}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
