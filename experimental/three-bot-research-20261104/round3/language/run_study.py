#!/usr/bin/env python3
"""Prepare and execute the prospectively registered native LM1 single-binding objective study."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parent
SOURCE_COMMIT = "30cd3a89810ed14fa84eca95236d3177e016118d"
BASE_SHA = "7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd"
TOKENIZER_SHA = "94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f"
SOURCES = ["app/src/main/java/at/heinz/deviceai/learning/lm/" + n + ".kt" for n in
           ["LmBpeTokenizer", "LmCheckpointStore", "LmCorpus", "LmModel", "LmRuntime", "LmTensor"]]
SOURCES += ["experimental/weight-space-20261104/LmCapacityBenchmark.kt"]
CODE = ["PROTOCOL.md", "oracle.py", "generate_data.py", "ObjectiveStudy.kt", "GradientPreflight.kt", "run_study.py", "analyze_study.py", "source-check.json"]
SEEDS = [202611043, 202611044]
ARMS = ["full_text", "answer_eos"]


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_json(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")


def now() -> str:
    return datetime.now(timezone.utc).isoformat()


def bounded_subdirectory(path: Path) -> Path:
    path = path.resolve()
    if not path.is_relative_to(ROOT) or path == ROOT:
        raise ValueError("output must be a new child of research/round2/language")
    return path


def source_identities(source: Path) -> dict[str, str]:
    manifest = json.loads((source / "SOURCE_MANIFEST.json").read_text())
    if manifest["sha"] != SOURCE_COMMIT:
        raise ValueError("wrong source commit")
    records = {item["path"]: item["sha256"] for item in manifest["files"]}
    result = {}
    for path in SOURCES:
        result[path] = digest(source / path)
        if result[path] != records[path]:
            raise ValueError("changed original Kotlin source")
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=["prepare", "run"])
    parser.add_argument("--source", type=Path, default=ROOT.parents[1] / "sources/weight")
    parser.add_argument("--inputs", type=Path, default=ROOT.parents[1] / "sources/lm1-original-inputs")
    parser.add_argument("--kotlin-lib", type=Path, default=Path("/workspace/scratch/b7d23de0e715/build-toolchain/gradle-8.11.1/lib"))
    parser.add_argument("--output", type=Path, default=ROOT / "run-v1")
    parser.add_argument("--registration-commit")
    args = parser.parse_args()
    source, inputs = args.source.resolve(strict=True), args.inputs.resolve(strict=True)
    source_hashes = source_identities(source)
    baseline, tokenizer = inputs / "lm1-baseline.lm1", inputs / "lm1-tokenizer.lmbp"
    if digest(baseline) != BASE_SHA or digest(tokenizer) != TOKENIZER_SHA:
        raise ValueError("wrong original model/tokenizer")
    data = ROOT / "data"
    data_manifest = json.loads((data / "manifest.json").read_text())
    for name, value in data_manifest["files"].items():
        if digest(data / name) != value["sha256"]:
            raise ValueError("changed data")
    build = ROOT / "build"
    stdlib = args.kotlin_lib / "kotlin-stdlib-2.0.20.jar"
    annotations = args.kotlin_lib / "annotations-24.0.1.jar"
    compiler = args.kotlin_lib / "kotlin-compiler-embeddable-2.0.20.jar"
    jar = build / "objective-study.jar"
    cp = os.pathsep.join(map(str, [jar, stdlib, annotations]))
    java = ["java", "-Xmx1024m", "-XX:ActiveProcessorCount=2", "-cp", cp]
    klass = "at.heinz.deviceai.learning.lm.ObjectiveStudy"
    all_start = time.monotonic()
    receipts = []
    if args.mode == "prepare":
        output = build
        if output.exists():
            raise FileExistsError("build directory already exists; do not silently replace registration")
    else:
        output = bounded_subdirectory(args.output)
        if output.exists():
            raise FileExistsError("use a new run directory")
    output.mkdir(parents=True)

    def execute(label: str, argv: list[str], timeout: int, allowed: tuple[int, ...] = (0,)) -> dict:
        remaining = 600 - (time.monotonic() - all_start)
        if remaining <= 0:
            raise TimeoutError("whole-study budget exhausted")
        start = time.monotonic()
        stdout, stderr = output / f"{label}.stdout", output / f"{label}.stderr"
        timed_out = False
        with stdout.open("w") as out, stderr.open("w") as err:
            try:
                process = subprocess.run(argv, cwd=ROOT, stdout=out, stderr=err,
                                         timeout=min(timeout, remaining), env=os.environ | {"PYTHONDONTWRITEBYTECODE": "1"})
                code = process.returncode
            except subprocess.TimeoutExpired:
                timed_out, code = True, -999
        receipt = {"label": label, "argv": argv, "wallSeconds": time.monotonic() - start,
                   "exitCode": code, "timedOut": timed_out, "timeoutSeconds": timeout,
                   "stdout": stdout.name, "stdoutSha256": digest(stdout), "stderr": stderr.name,
                   "stderrSha256": digest(stderr), "completedUtc": now()}
        write_json(output / f"{label}.receipt.json", receipt)
        receipts.append(receipt)
        if code not in allowed:
            raise RuntimeError(f"{label} failed; see receipt")
        return receipt

    if args.mode == "prepare":
        reference_manifest = json.loads((ROOT / "source-check.json").read_text())
        for reference in reference_manifest["references"]:
            raw = (ROOT / reference["file"]).read_bytes()
            git_hash = hashlib.sha1(b"blob " + str(len(raw)).encode() + b"\0" + raw).hexdigest()
            if git_hash != reference["gitBlobSha"]:
                raise ValueError("reference bytes do not match pinned Git blob")
        compiler_cp = os.pathsep.join(str(p) for p in sorted(args.kotlin_lib.glob("*.jar")))
        execute("compile", ["java", "-Xmx1024m", "-XX:ActiveProcessorCount=2", "-cp", compiler_cp,
                "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-jvm-target", "17",
                "-classpath", os.pathsep.join(map(str, [stdlib, annotations])), "-d", str(jar),
                *(str(source / path) for path in SOURCES), str(ROOT / "ObjectiveStudy.kt"), str(ROOT / "GradientPreflight.kt")], 120)
        execute("preflight", java + [klass, "preflight", str(baseline), str(tokenizer), str(data / "train.tsv")], 180)
        events = [json.loads(line) for line in (output / "preflight.stdout").read_text().splitlines() if json.loads(line).get("kind") == "preflight_result"]
        if len(events) != 1 or events[0].get("prefixStable") is not True or events[0].get("passed") is not True or events[0].get("optimizerUpdates") != 0:
            raise ValueError("invalid token-boundary preflight")
        registered = {name: digest(ROOT / name) for name in CODE}
        registered.update({str(p.relative_to(ROOT)): digest(p) for p in sorted(data.iterdir()) if p.is_file()})
        registered.update({str(p.relative_to(ROOT)): digest(p) for p in sorted((ROOT / "references").iterdir()) if p.is_file()})
        registered.update({str(p.relative_to(ROOT)): digest(p) for p in sorted(build.iterdir()) if p.is_file()})
        write_json(ROOT / "registration.json", {"schema": "lm1-single-binding-objective-v1", "createdUtc": now(),
                   "sourceCommit": SOURCE_COMMIT, "sourceHashes": source_hashes, "files": registered,
                   "baselineSha256": BASE_SHA, "tokenizerSha256": TOKENIZER_SHA,
                   "compilerSha256": digest(compiler), "stdlibSha256": digest(stdlib), "annotationsSha256": digest(annotations),
                   "preflight": events[0], "preflightReceipts": receipts,
                   "arms": ARMS, "seeds": SEEDS, "stepsPerArm": 256, "batchSize": 4,
                   "learningRate": 0.001, "trainingThreads": 1, "trainingStarted": False})
        print(json.dumps({"prepared": True, "registrationSha256": digest(ROOT / "registration.json"),
                          "preflight": events[0]}, indent=2))
        return

    registration_path = ROOT / "registration.json"
    registration = json.loads(registration_path.read_text())
    commit = args.registration_commit or ""
    if len(commit) != 40 or any(c not in "0123456789abcdef" for c in commit):
        raise ValueError("exact published prospective commit is required")
    registration_hash = digest(registration_path)

    def verify_registered() -> None:
        if digest(registration_path) != registration_hash:
            raise ValueError("registration changed")
        for name, expected in registration["files"].items():
            if digest(ROOT / name) != expected:
                raise ValueError(f"registered file changed: {name}")
        if source_identities(source) != registration["sourceHashes"]:
            raise ValueError("runtime changed")
        if digest(baseline) != BASE_SHA or digest(tokenizer) != TOKENIZER_SHA:
            raise ValueError("original inputs changed")
        if digest(compiler) != registration["compilerSha256"] or digest(stdlib) != registration["stdlibSha256"] or digest(annotations) != registration["annotationsSha256"]:
            raise ValueError("toolchain changed")

    def capacity(name: str, checkpoint: Path, expected_hash: str) -> dict:
        if digest(checkpoint) != expected_hash:
            raise ValueError("capacity input identity mismatch")
        receipt = execute(name + "-capacity", java + ["at.heinz.deviceai.learning.lm.LmCapacityBenchmark",
                          str(checkpoint), str(tokenizer)], 60, allowed=(0, 1))
        lines = (output / receipt["stdout"]).read_text().splitlines()
        final = [line for line in lines if line.startswith("CAPACITY_RESULT\t")]
        if len(final) != 1:
            raise ValueError("missing/unreadable post-capacity result")
        values = dict(field.split("=", 1) for field in final[0].split("\t")[1:])
        if values.get("pass") not in {"true", "false"} or values.get("checkpointSha256") != expected_hash:
            raise ValueError("invalid capacity identity/verdict")
        if values.get("measuredRepeats") != "3" or values.get("failure") != "UNAVAILABLE":
            raise ValueError("capacity execution incomplete or invalid")
        passed = values["pass"] == "true"
        if receipt["exitCode"] != (0 if passed else 1):
            raise ValueError("capacity exit/verdict disagreement")
        if digest(checkpoint) != expected_hash:
            raise ValueError("capacity input changed during execution")
        return {"pass": passed, "verdict": values, "receipt": receipt,
                "receiptSha256": digest(output / f"{name}-capacity.receipt.json")}

    def verify_all_models(models: list[dict]) -> None:
        identities = {"baseline": ("baseline", "none")}
        identities.update({f"{a}-{s}": (a, str(s)) for s in SEEDS for a in ARMS})
        if len(models) != 5 or {m["name"] for m in models} != set(identities):
            raise ValueError("incomplete preregistered model set")
        for model in models:
            if (model["objective"], model["seed"]) != identities[model["name"]]:
                raise ValueError("model objective/seed binding mismatch")
            if digest(Path(model["checkpoint"])) != model["checkpointSha256"]:
                raise ValueError("frozen model identity changed")
            cap = model["capacity"]
            cap_file = output / f"{model['name']}-capacity.receipt.json"
            if digest(cap_file) != cap["receiptSha256"]:
                raise ValueError("post-capacity receipt missing/corrupt")
            if json.loads(cap_file.read_text()) != cap["receipt"]:
                raise ValueError("post-capacity receipt structure changed")
            for stream in ("stdout", "stderr"):
                if digest(output / cap["receipt"][stream]) != cap["receipt"][stream + "Sha256"]:
                    raise ValueError("post-capacity evidence missing/corrupt")
            if model["name"] != "baseline":
                path = output / f"{model['name']}-train.receipt.json"
                if digest(path) != model["trainingReceiptSha256"] or json.loads(path.read_text()) != model["trainingReceipt"]:
                    raise ValueError("training receipt missing/corrupt")
                for stream in ("stdout", "stderr"):
                    receipt = model["trainingReceipt"]
                    if digest(output / receipt[stream]) != receipt[stream + "Sha256"]:
                        raise ValueError("training log missing/corrupt")

    try:
        verify_registered()
        write_json(output / "run-start.json", {"registrationCommit": commit, "registrationSha256": registration_hash,
                   "startedUtc": now(), "platform": platform.platform(), "python": sys.version,
                   "java": subprocess.run(["java", "-version"], capture_output=True, text=True).stderr,
                   "physicalReportedCpus": os.cpu_count(), "effectiveJavaCpus": 2, "hostOnly": True})
        base_capacity = capacity("baseline", baseline, BASE_SHA)
        if not base_capacity["pass"]:
            raise RuntimeError("BASELINE_GATE: training blocked")
        models = [{"name": "baseline", "objective": "baseline", "seed": "none", "checkpoint": str(baseline),
                   "checkpointSha256": BASE_SHA, "capacity": base_capacity}]
        for seed in SEEDS:
            for arm in ARMS:
                verify_registered()
                name = f"{arm}-{seed}"
                checkpoint = output / f"{name}.lm1"
                training_receipt = execute(name + "-train", java + [klass, "train", str(baseline), str(tokenizer),
                        str(data / "train.tsv"), str(data / f"schedule-{seed}.txt"),
                        str(checkpoint), arm, str(seed)], 90)
                events = [json.loads(line) for line in (output / f"{name}-train.stdout").read_text().splitlines()]
                if len(events) < 2 or events[-1].get("kind") != "training_result" or events[-1].get("steps") != 256:
                    raise ValueError("training incomplete")
                outcome = events[-1]
                expected = outcome["checkpointSha256"]
                if digest(checkpoint) != expected or outcome["eosTargets"] != 1024 or outcome["answerTargets"] != 1024 or outcome["examples"] != 1024:
                    raise ValueError("invalid training output")
                if any(e.get("objective") != arm or str(e.get("seed")) != str(seed) for e in events):
                    raise ValueError("training identity mismatch")
                if events[0].get("sourceSha256") != BASE_SHA:
                    raise ValueError("training did not start from original")
                full_positions = registration["preflight"]["fullTargetsPerEpoch"] * 8
                active_targets = full_positions if arm == "full_text" else 2048
                if outcome["fullSequencePositions"] != full_positions or outcome["activeTargets"] != active_targets:
                    raise ValueError("training supervision budget mismatch")
                post_capacity = capacity(name, checkpoint, expected)
                models.append({"name": name, "objective": arm, "seed": str(seed), "checkpoint": str(checkpoint),
                               "checkpointSha256": expected, "training": outcome, "capacity": post_capacity,
                               "trainingReceipt": training_receipt,
                               "trainingReceiptSha256": digest(output / f"{name}-train.receipt.json")})
        for seed in SEEDS:
            pair = [m for m in models if m["seed"] == str(seed)]
            if len(pair) != 2 or pair[0]["training"]["sequenceMultisetSha256"] != pair[1]["training"]["sequenceMultisetSha256"]:
                raise ValueError("matched-budget multiset mismatch")
            if pair[0]["training"]["fullSequencePositions"] != pair[1]["training"]["fullSequencePositions"]:
                raise ValueError("matched-budget token mismatch")
            if pair[0]["training"]["sequenceScheduleSha256"] != pair[1]["training"]["sequenceScheduleSha256"]:
                raise ValueError("paired sequence order differs")
        verify_registered()
        verify_all_models(models)
        freeze = {"registrationCommit": commit, "registrationSha256": registration_hash,
                  "frozenUtc": now(), "testEvaluated": False, "modelSelection": "NONE_ALL_PREREGISTERED_ARMS",
                  "capacityFailPermitsPromotion": False, "models": models}
        write_json(output / "freeze.json", freeze)
        freeze_hash = digest(output / "freeze.json")
        evaluation_receipts = []
        for model in models:
            for split, filename in [("dev", "dev.tsv"), ("test", "test.sealed.tsv")]:
                verify_registered()
                verify_all_models(models)
                if digest(output / "freeze.json") != freeze_hash or digest(Path(model["checkpoint"])) != model["checkpointSha256"]:
                    raise ValueError("frozen model identity changed")
                cap = model["capacity"]
                cap_file = output / f"{model['name']}-capacity.receipt.json"
                if digest(cap_file) != cap["receiptSha256"]:
                    raise ValueError("post-capacity receipt missing/corrupt")
                for stream in ("stdout", "stderr"):
                    if digest(output / cap["receipt"][stream]) != cap["receipt"][stream + "Sha256"]:
                        raise ValueError("post-capacity evidence changed")
                receipt = execute(f"{model['name']}-{split}", java + [klass, "evaluate", model["checkpoint"],
                                  str(tokenizer), str(data / filename), model["objective"], model["seed"],
                                  model["checkpointSha256"], split], 90)
                events = [json.loads(line) for line in (output / receipt["stdout"]).read_text().splitlines()]
                expected_rows = 64
                if events[-1].get("kind") != "evaluation_complete" or sum(e["kind"] == "case" for e in events) != expected_rows:
                    raise ValueError("evaluation incomplete")
                if events[0].get("kind") != "evaluation_start" or events[0].get("checkpointSha256") != model["checkpointSha256"] or events[0].get("datasetSha256") != digest(data / filename):
                    raise ValueError("evaluation input binding mismatch")
                if any((e.get("objective"), str(e.get("seed")), e.get("split")) != (model["objective"], model["seed"], split) for e in events):
                    raise ValueError("evaluation metadata mismatch")
                evaluation_receipts.append(receipt)
        verify_registered()
        verify_all_models(models)
        write_json(output / "completed.json", {"schema": "lm1-single-binding-objective-v1", "completedUtc": now(),
                   "registrationCommit": commit, "registrationSha256": registration_hash,
                   "freezeSha256": freeze_hash, "wallSeconds": time.monotonic() - all_start,
                   "models": models, "evaluations": evaluation_receipts, "testEvaluated": True,
                   "promotion": False, "trainingAfterFreeze": False, "hostOnly": True})
        print(json.dumps({"completed": True, "output": str(output), "wallSeconds": time.monotonic() - all_start,
                          "completedSha256": digest(output / "completed.json")}, indent=2))
    except Exception as error:
        write_json(output / "failed.json", {"errorType": type(error).__name__, "error": str(error), "utc": now(),
                   "registrationCommit": commit, "noAutomaticRetry": True})
        raise


if __name__ == "__main__":
    main()
