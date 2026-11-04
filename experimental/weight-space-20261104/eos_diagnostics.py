#!/usr/bin/env python3
"""Read-only consistency audit of the archived continuation EOS observations.

Reads Git blobs at one explicit revision. Never imports the training runner,
loads weights, evaluates a holdout, or starts model inference/training.
"""

import argparse
import hashlib
import json
import math
from pathlib import Path
import re
import subprocess
import sys


REVISION = "30cd3a89810ed14fa84eca95236d3177e016118d"
PREFIX = "experimental/weight-space-20261104/core-6-10m-evidence/run"
EMPTY_SHA256 = hashlib.sha256(b"").hexdigest()
STOP_REASONS = {"EOS", "MAX_TOKENS", "TIME", "CANCELLED", "CONTEXT"}


class AuditError(ValueError):
    """The supplied records do not support a consistent observation."""


def require(condition, message):
    if not condition:
        raise AuditError(message)


def records(text, allowed):
    result = {}
    for line_number, line in enumerate(text.splitlines(), 1):
        if not line:
            continue
        kind, *cells = line.split("\t")
        require(kind in allowed, f"line {line_number}: unexpected record {kind}")
        row = {}
        for cell in cells:
            key, separator, value = cell.partition("=")
            require(separator and key and key not in row,
                    f"line {line_number}: malformed or duplicate field {key}")
            row[key] = value
        result.setdefault(kind, []).append(row)
    return result


def one(rows, kind):
    found = rows.get(kind, [])
    require(len(found) == 1, f"expected exactly one {kind}")
    return found[0]


def integer(row, key, minimum=0):
    value = row.get(key, "")
    require(re.fullmatch(r"[0-9]+", value) is not None, f"invalid integer {key}")
    number = int(value)
    require(number >= minimum, f"{key} below {minimum}")
    return number


def digest(row, key):
    value = row.get(key, "")
    require(re.fullmatch(r"[a-f0-9]{64}", value) is not None, f"invalid SHA-256 {key}")
    return value


def identity(row, hash_key="sha256"):
    return {"checkpoint_sha256": digest(row, hash_key),
            "tokenizer_sha256": digest(row, "tokenizerSha256"),
            "parameters": integer(row, "parameters", 1)}


def inspect_probes(text):
    rows = records(text, {"SOURCE", "PROBE", "PROBE_RESULT"})
    source = identity(one(rows, "SOURCE"))
    probes = rows.get("PROBE", [])
    require([integer(row, "index") for row in probes] == list(range(6)),
            "expected all six ordered, unique probe indices")
    require(len({row.get("prompt") for row in probes}) == 6,
            "probe prompts must be distinct")
    immediate = []
    correct = 0
    for row in probes:
        require(all(key in row for key in ("prompt", "expected", "text", "stop")),
                "incomplete probe fields")
        tokens = integer(row, "tokens")
        require(tokens <= 32, "probe exceeds requested token budget")
        require(row["stop"] in STOP_REASONS,
                "unknown probe stop reason")
        if tokens == 0:
            require(row["text"] == "", "zero-token probe has nonempty text")
        is_correct = row["text"].strip() == row["expected"]
        require(row.get("correct") == str(is_correct).lower(), "incorrect probe scoring claim")
        correct += is_correct
        if tokens == 0 and row["stop"] == "EOS":
            immediate.append(integer(row, "index"))
    summary = one(rows, "PROBE_RESULT")
    require(integer(summary, "total") == 6 and integer(summary, "correct") == correct,
            "probe summary does not match records")
    require(summary.get("scoring") == "exact-trimmed-string", "unsupported scoring rule")
    return {"identity": source, "unique_prompts": 6, "correct": correct,
            "immediate_eos_indices": immediate,
            "prompt_expected_pairs": [[row["prompt"], row["expected"]] for row in probes]}


def inspect_training(text):
    rows = records(text, {"SOURCE", "TRAIN_INPUT", "TRAIN_STEP", "CHECKPOINT", "TRAIN_RESULT"})
    source = identity(one(rows, "SOURCE"))
    inputs = one(rows, "TRAIN_INPUT")
    hashes = {key: digest(inputs, key) for key in
              ("datasetSha256", "scheduleSha256", "tokenScheduleSha256", "tokenizerSha256")}
    require(hashes["tokenizerSha256"] == source["tokenizer_sha256"], "training tokenizer mismatch")
    steps = rows.get("TRAIN_STEP", [])
    require([integer(row, "step", 1) for row in steps] == list(range(1, 17)),
            "expected all sixteen ordered training steps")
    cumulative = [integer(row, "tokens", 1) for row in steps]
    require(all(a < b for a, b in zip([0] + cumulative, cumulative)),
            "training token counts must increase strictly")
    for row in steps:
        try:
            nll = float(row["nll"])
        except (KeyError, ValueError) as exc:
            raise AuditError("invalid training NLL") from exc
        require(math.isfinite(nll) and nll >= 0, "nonfinite or negative training NLL")
    checkpoint = one(rows, "CHECKPOINT")
    checkpoint_hash = digest(checkpoint, "sha256")
    require(integer(checkpoint, "parameters", 1) == source["parameters"], "training parameter mismatch")
    result = one(rows, "TRAIN_RESULT")
    require(integer(result, "steps") == 16 and integer(result, "tokens") == cumulative[-1],
            "training summary does not match steps")
    require(digest(result, "scheduleSha256") == hashes["scheduleSha256"], "training schedule mismatch")
    require(0 < integer(result, "changedWeights") <= source["parameters"], "invalid changed weight count")
    require(checkpoint_hash != source["checkpoint_sha256"], "trained checkpoint equals source")
    return {"source": source,
            "checkpoint": {**source, "checkpoint_sha256": checkpoint_hash},
            "steps": 16, "tokens": cumulative[-1], "inputs": hashes,
            "cumulative_tokens": cumulative}


def inspect_capacity(text):
    rows = records(text, {"CAPACITY_PROTOCOL", "CAPACITY_MODEL", "CAPACITY_MEMORY",
                          "CAPACITY_TRIAL", "CAPACITY_RESULT"})
    protocol = one(rows, "CAPACITY_PROTOCOL")
    model = one(rows, "CAPACITY_MODEL")
    source = identity(model, "checkpointSha256")
    prompt_hash = digest(protocol, "promptSha256")
    warmups = integer(protocol, "warmups", 1)
    repeats = integer(protocol, "measuredRepeats", 1)
    require(warmups == 1 and repeats == 3, "unexpected historical capacity protocol")
    require(model.get("finiteWeights") == "true" and model.get("fullLayoutLoaded") == "true",
            "capacity model was not fully loaded and finite")
    trials = rows.get("CAPACITY_TRIAL", [])
    expected = [(phase, number, requested) for phase, count in
                (("warmup", warmups), ("measured", repeats))
                for number in range(1, count + 1) for requested in (1, 32)]
    actual = [(row.get("phase"), integer(row, "trial", 1), integer(row, "requestedNewTokens", 1))
              for row in trials]
    require(actual == expected, "missing, duplicated, reordered or unexpected capacity trials")
    immediate = 0
    first = 0
    full = 0
    for row in trials:
        require(row.get("stop") in STOP_REASONS, "missing or unknown capacity stop reason")
        count = integer(row, "actualNewTokens")
        requested = integer(row, "requestedNewTokens", 1)
        require(count <= requested, "capacity output exceeds request")
        require(integer(row, "promptTokens") == integer(model, "promptTokens"), "capacity prompt length mismatch")
        require(row.get("promptTruncated") == "false", "capacity prompt was truncated")
        text_hash = digest(row, "textSha256")
        if count == 0:
            require(text_hash == EMPTY_SHA256, "zero-token trial has nonempty output hash")
            require(row.get("ttftUpperBoundMs") == "UNAVAILABLE", "zero-token trial claims observed TTFT")
        immediate += count == 0 and row.get("stop") == "EOS"
        if row["phase"] == "measured":
            first += requested == 1 and count == 1
            full += requested == 32 and count == 32
    result = one(rows, "CAPACITY_RESULT")
    require(digest(result, "checkpointSha256") == source["checkpoint_sha256"] and
            integer(result, "parameters", 1) == source["parameters"], "capacity result identity mismatch")
    require(integer(result, "measuredRepeats") == repeats and
            integer(result, "firstTokenObservations") == first and
            integer(result, "full32TokenObservations") == full, "capacity observation summary mismatch")
    require(result.get("pass") in {"true", "false"}, "missing capacity pass status")
    if first != repeats or full != repeats:
        require(result["pass"] == "false", "incomplete output cannot pass capacity")
    return {"identity": source, "protocol_prompt_sha256": prompt_hash,
            "distinct_protocol_prompts": 1, "trial_calls": len(trials),
            "warmup_calls": 2 * warmups, "measured_calls": 2 * repeats,
            "immediate_eos_calls": immediate, "first_token_observations": first,
            "full_32_token_observations": full, "reported_capacity_pass": result["pass"] == "true"}


def compare_run(training, before, after, capacity):
    require(training["source"] == before["identity"], "training source and initial probe identity mismatch")
    require(training["checkpoint"] == after["identity"] == capacity["identity"],
            "trained checkpoint, probe and capacity identity mismatch")
    require(before["prompt_expected_pairs"] == after["prompt_expected_pairs"], "before/after probe task mismatch")
    return {"parameters": training["checkpoint"]["parameters"],
            "source_sha256": training["source"]["checkpoint_sha256"],
            "checkpoint_sha256": training["checkpoint"]["checkpoint_sha256"],
            "steps": training["steps"], "tokens": training["tokens"],
            "initial_immediate_eos_indices": before["immediate_eos_indices"],
            "continued_immediate_eos_indices": after["immediate_eos_indices"],
            "new_immediate_eos_indices": sorted(set(after["immediate_eos_indices"]) -
                                                set(before["immediate_eos_indices"])),
            "unique_instruction_prompts": after["unique_prompts"],
            "initial_correct": before["correct"], "continued_correct": after["correct"],
            "capacity": capacity}


def audit(repo, revision=REVISION):
    require(re.fullmatch(r"[a-f0-9]{40}", revision) is not None, "revision must be a full commit SHA")
    sources = []

    def read(path):
        result = subprocess.run(["git", "-C", str(repo), "show", f"{revision}:{path}"],
                                capture_output=True, check=False)
        require(result.returncode == 0, f"cannot read pinned blob: {path}")
        sources.append({"path": path, "sha256": hashlib.sha256(result.stdout).hexdigest(),
                        "url": f"https://github.com/Ullmann27/device-ai/blob/{revision}/{path}"})
        return result.stdout.decode("utf-8")

    runs = []
    trainings = []
    shared_tasks = None
    for profile, initial_tag in (("core-6562304", "width-8"), ("core-9712128", "depth-3")):
        base = f"{PREFIX}/{profile}/logs"
        initial = {"small": inspect_probes(read(f"{base}/baseline-probes.tsv")),
                   "expanded": inspect_probes(read(f"{base}/{initial_tag}-probes.tsv"))}
        for size in ("small", "expanded"):
            training = inspect_training(read(f"{base}/continued-{size}-train.tsv"))
            probes = inspect_probes(read(f"{base}/continued-{size}-probes.tsv"))
            capacity = inspect_capacity(read(f"{base}/continued-{size}-capacity.tsv"))
            run = compare_run(training, initial[size], probes, capacity)
            if shared_tasks is not None:
                require(probes["prompt_expected_pairs"] == shared_tasks, "cross-run probe task mismatch")
            shared_tasks = probes["prompt_expected_pairs"]
            runs.append({"profile": profile, "size": size, **run})
            trainings.append(training)
    reference = trainings[0]
    require(all(t["inputs"] == reference["inputs"] and
                t["cumulative_tokens"] == reference["cumulative_tokens"] for t in trainings),
            "cross-run dataset, tokenizer, schedule or token counts differ")
    require(len({run["capacity"]["protocol_prompt_sha256"] for run in runs}) == 1,
            "cross-run capacity protocol prompts differ")
    return {"schema": "continuum-historical-eos-audit-v1", "revision": revision,
            "log_consistency": "PASS", "checkpoint_bytes_rehashed": False,
            "all_four_continuations_have_six_immediate_eos_probes":
                all(len(run["continued_immediate_eos_indices"]) == 6 for run in runs),
            "all_four_continuations_have_eight_immediate_eos_capacity_calls":
                all(run["capacity"]["immediate_eos_calls"] == 8 for run in runs),
            "small_control_checkpoint_identical_across_profiles":
                runs[0]["checkpoint_sha256"] == runs[2]["checkpoint_sha256"],
            "distinct_continued_checkpoint_count": len({run["checkpoint_sha256"] for run in runs}),
            "shared_training_inputs": reference["inputs"],
            "shared_cumulative_tokens": reference["cumulative_tokens"],
            "runs": runs, "sources": sources,
            "limits": ["Verifies archived log consistency, not authenticity or absent checkpoint bytes.",
                       "Capacity calls repeat one protocol prompt; they are not distinct tasks.",
                       "Six instruction prompts are reused across runs and are not a fresh quality test.",
                       "Logs do not contain first-token logits, EOS probability or margin.",
                       "The small controls exhibit the same phenomenon; width-specific causation is unproven.",
                       "No training, model inference, holdout evaluation or promotion was performed."]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--revision", default=REVISION)
    args = parser.parse_args()
    try:
        result = audit(args.repo, args.revision)
    except (AuditError, OSError, UnicodeError) as exc:
        print(f"EOS audit failed: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True, allow_nan=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
