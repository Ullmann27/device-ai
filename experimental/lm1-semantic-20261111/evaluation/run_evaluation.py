#!/usr/bin/env python3
"""Run the frozen independent comparison once."""

import argparse
import csv
import datetime
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import platform
import subprocess
import sys
import time

import numpy as np
import sklearn

from analyze import metrics, parse, require, validate_native, verdict
from controls import predict


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def module(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


def read_rows(path):
    rows = list(csv.DictReader(Path(path).read_text().splitlines(), delimiter="\t"))
    require(rows and len(rows) == len({r["id"] for r in rows}), "invalid dataset identities")
    require(all(set(row) == {"id", "label", "prompt"} and row["label"] in "0123" for row in rows), "invalid dataset schema")
    return rows


def write(path, value):
    with Path(path).open("x") as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2, sort_keys=True)
        stream.write("\n")


def tensor_sha(model):
    digest = hashlib.sha256()
    for name, rows, cols, bits, _ in model["tensors"]:
        digest.update(f"{name}:{rows}:{cols}\n".encode())
        digest.update(bits)
    return digest.hexdigest()


def run_native(command, directory):
    directory.mkdir(exist_ok=False)
    cpu_command = ["/usr/bin/time", "-f", '{"userSeconds":%U,"systemSeconds":%S,"maximumRssKiB":%M}',
                   "-o", str(directory / "resources.json"), *command]
    started = time.perf_counter()
    timeout = False
    try:
        completed = subprocess.run(cpu_command, capture_output=True, timeout=180)
        stdout, stderr, code = completed.stdout, completed.stderr, completed.returncode
    except subprocess.TimeoutExpired as error:
        stdout, stderr, code = error.stdout or b"", error.stderr or b"", None
        timeout = True
    (directory / "stdout.tsv").write_bytes(stdout)
    (directory / "stderr.txt").write_bytes(stderr)
    resources = None
    if (directory / "resources.json").exists():
        try:
            resources = json.loads((directory / "resources.json").read_text())
        except json.JSONDecodeError:
            pass
    receipt = {"command": command, "measurementCommand": cpu_command,
        "exitCode": code, "timedOut": timeout, "wallSeconds": time.perf_counter() - started,
        "resources": resources, "stdoutSha256": sha(directory / "stdout.tsv"),
        "stderrSha256": sha(directory / "stderr.txt"), "optimizerUpdates": 0}
    write(directory / "receipt.json", receipt)
    require(code == 0 and not timeout, "native inference failed; retained raw output")
    return parse(stdout.decode()), receipt


def canonical(records):
    return {kind: records[kind] for kind in ("CONFIG", "LABEL", "LENGTH", "CASE", "TEXT", "CAPACITY")}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--data-repo", type=Path, required=True)
    parser.add_argument("--training-dir", type=Path)
    parser.add_argument("--models", type=Path, required=True)
    parser.add_argument("--tokenizer", type=Path, required=True)
    parser.add_argument("--build", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    started = time.perf_counter()
    root = args.repo.resolve()
    evaluation = Path(__file__).resolve().parent
    holdout = evaluation / "frozen-holdout"
    old = root / "experimental/weight-space-20261110"
    data = args.data_repo / "experimental/lm1-semantic-20261111/data"
    require(not args.output.exists(), "refuse to overwrite evidence")
    args.output.mkdir(parents=True)
    models = json.loads(args.models.read_text())
    require(models["status"] == "ALL_PLANNED_CANDIDATES_FROZEN", "candidate freeze required")
    paths = models["models"]
    require({"original", "old_answer", "old_replay", "old_teacher"} <= set(paths) <= {"original", "old_answer", "old_replay", "old_teacher", "A", "B"}, "unregistered model set")
    build = json.loads(args.build.read_text())
    jar = Path(build["classpath"].split(":")[0])
    require(sha(jar) == build["jarSha256"], "evaluator jar changed")
    require(build["extraSha256"]["LmSemanticEvaluator.kt"] == sha(evaluation / "LmSemanticEvaluator.kt"), "compiled source mismatch")
    manifest = json.loads((holdout / "manifest.json").read_text())
    for name, identity in manifest["files"].items():
        require(sha(holdout / name) == identity["sha256"], "holdout changed")
    require(manifest["generatorSha256"] == sha(evaluation / "make_holdout.py"), "holdout generator changed")
    tokenizer_module = module(data / "frozen_tokenizer.py", "independent_tok")
    tokenizer = tokenizer_module.Tokenizer(args.tokenizer.read_bytes())
    reader = module(old / "evaluation/audit_lm1_weights.py", "lm1_reader")
    train = read_rows(data / "pilot/train.tsv")
    dev = read_rows(data / "pilot/dev.tsv")
    sets = {"semantic": read_rows(holdout / "semantic.tsv"),
            "legacyRetention": read_rows(old / "data/retention.tsv"),
            "novelRetention": read_rows(holdout / "novel-retention.tsv")}
    texts = {"legacyText": (old / "data/retention_text.txt").read_text().splitlines(),
             "novelText": (holdout / "novel-text.txt").read_text().splitlines()}
    metadata = json.loads((holdout / "metadata.json").read_text())
    normalize = lambda text: " ".join(text.casefold().split())
    training_prompts = {normalize(row["prompt"]) for row in train + dev}
    if args.training_dir:
        for filename in ("fixtures/retention-candidates.tsv", "prepared/anchors.tsv"):
            file = args.training_dir / filename
            if file.exists():
                training_prompts.update(normalize(row["prompt"]) for row in read_rows(file))
    overlap = [row["id"] for rows in sets.values() for row in rows if normalize(row["prompt"]) in training_prompts]
    require(not overlap, "training/evaluation prompt overlap")
    models_read = {name: reader.read(Path(entry["path"])) for name, entry in paths.items()}
    require(models_read["original"]["sha256"] == "7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd", "wrong original")
    identities = {}
    for name, model in models_read.items():
        require(model["sha256"] == paths[name]["sha256"] and model["parameters"] == 132160, "weight identity mismatch")
        delta = reader.compare(models_read["original"], model)
        if name != "original":
            require(delta["changedNumericWeights"] > 0, "no real parameter change")
        identities[name] = {"checkpointSha256": model["sha256"], "tensorSha256": tensor_sha(model),
                            "config": model["config"], "tokenizerSha256": model["tokenizer"], **delta}
    files = list(holdout.glob("*")) + list(evaluation.glob("*.py")) + [evaluation / "PROTOCOL.md", evaluation / "LmSemanticEvaluator.kt",
            args.models, args.tokenizer, args.build, jar, data / "pilot/train.tsv", data / "pilot/dev.tsv", data / "frozen_tokenizer.py",
            old / "data/retention.tsv", old / "data/retention_text.txt"] + [Path(entry["path"]) for entry in paths.values()]
    hashes = {str(file.resolve()): sha(file) for file in files if file.is_file()}
    registration = {"schema": "lm1-independent-native-evaluation-registration-v1",
        "startedUtc": datetime.datetime.now(datetime.timezone.utc).isoformat(), "inputSha256": hashes,
        "modelIdentities": identities, "modelCount": len(paths), "maximumInvocations": len(paths) + 1 + len(set(paths) & {"A", "B"}),
        "heapMiB": 768, "reportedProcessors": 1, "secondsPerInvocation": 180,
        "bootstrapDraws": 20000, "bootstrapSeed": 2026111103, "trainingEvaluationOverlap": overlap,
        "environment": {"python": sys.version, "numpy": np.__version__, "sklearn": sklearn.__version__,
                        "platform": platform.platform(), "logicalCpusAvailable": os.cpu_count()},
        "scope": "Fixed synthetic research; no product invocation; zero optimizer updates"}
    write(args.output / "registration.json", registration)
    predictions, fits = predict(train, sets["semantic"], tokenizer)
    control_metrics = {name: metrics(sets["semantic"], pred, metadata) for name, pred in predictions.items()}
    write(args.output / "controls.json", {"metrics": control_metrics, "fittedStates": fits,
        "predictions": {row["id"]: {name: pred[i] for name, pred in predictions.items()} for i, row in enumerate(sets["semantic"])}})
    all_metrics, receipts, repetitions = {}, {}, {}
    for name, entry in paths.items():
        command = ["/usr/lib/jvm/java-21-openjdk-amd64/bin/java", "-Xmx768m", "-XX:ActiveProcessorCount=1", "-cp", build["classpath"],
            "at.heinz.deviceai.learning.lm.LmSemanticEvaluator", "evaluate", entry["path"], str(args.tokenizer.resolve()), str(holdout), str(old / "data")]
        records, receipt = run_native(command, args.output / name)
        all_metrics[name] = validate_native(records, sets, texts, metadata, entry["sha256"])
        receipts[name] = receipt
        if name in ("original", "A", "B"):
            repeated, repeat_receipt = run_native(command, args.output / (name + "-repeat"))
            validate_native(repeated, sets, texts, metadata, entry["sha256"])
            equal = canonical(records) == canonical(repeated)
            require(equal, "inference repeat differed")
            repetitions[name] = {"perCaseOutputsIdentical": equal, "receipt": repeat_receipt}
    unchanged = {path: sha(path) == digest for path, digest in hashes.items()}
    require(all(unchanged.values()), "registered inputs changed")
    decision = verdict(all_metrics, control_metrics, metadata, sets["legacyRetention"])
    report = {"schema": "lm1-independent-semantic-comparison-v1", "modelMetrics": all_metrics,
        "controlMetrics": control_metrics, "modelIdentities": identities, "repetitions": repetitions,
        "decision": decision, "inputUnchanged": unchanged, "modelRunReceipts": receipts,
        "totalWallSeconds": time.perf_counter() - started, "modelInvocations": len(receipts) + len(repetitions),
        "optimizerUpdates": 0, "additionalCloudGpuJobs": 0, "credits": "UNAVAILABLE_NOT_ESTIMATED",
        "limitations": ["Organizational separation only, not access-isolated blindness.",
            "128 synthetic cases, 64 pairs, eight templates within four broader constructions.",
            "Wilson row intervals ignore template dependence; paired and construction sensitivity are also reported.",
            "Four-choice scores do not establish fluent generation or safe tool execution.",
            "Continuation length and text likelihood do not establish coherent language.",
            "No APK, device, Gemma, user memory, or production model is changed."]}
    write(args.output / "analysis.json", report)
    print(json.dumps({"models": {name: {"semantic": m["semantic"]["correct"], "pairs": m["semantic"]["pairCorrect"],
        "exact": m["semantic"]["exact"], "legacyRetention": m["legacyRetention"]["correct"]} for name, m in all_metrics.items()},
        "decisions": {name: d["status"] for name, d in decision["candidates"].items()}, "elapsedSeconds": report["totalWallSeconds"]}))


if __name__ == "__main__":
    main()
