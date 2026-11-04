#!/usr/bin/env python3
"""Compile frozen native LM1 classes and run a bounded offline mechanism experiment."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import sys
import time

SOURCE_SHA = "30cd3a89810ed14fa84eca95236d3177e016118d"
BASE_SHA = "7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd"
TOKENIZER_SHA = "94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f"
LM_PATH = "app/src/main/java/at/heinz/deviceai/learning/lm/"
EXP_PATH = "experimental/weight-space-20261104/"
SOURCES = [LM_PATH + n + ".kt" for n in ["LmBpeTokenizer", "LmCheckpointStore", "LmCorpus", "LmModel", "LmRuntime", "LmTensor"]]
SOURCES += [EXP_PATH + n + ".kt" for n in ["LmWeightSurgery", "LmCapacityBenchmark"]]


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    root = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=root.parent / "sources/weight")
    parser.add_argument("--inputs", type=Path, default=root.parent / "sources/lm1-original-inputs")
    parser.add_argument("--kotlin-lib", type=Path, default=Path("/workspace/scratch/b7d23de0e715/build-toolchain/gradle-8.11.1/lib"))
    parser.add_argument("--output", type=Path, default=root / "run-20261104")
    args = parser.parse_args()
    output = args.output.resolve()
    if not output.is_relative_to(root) or output.exists():
        raise ValueError("output must be a new directory inside research/language")
    args.source = args.source.resolve(strict=True)
    args.inputs = args.inputs.resolve(strict=True)
    manifest = json.loads((args.source / "SOURCE_MANIFEST.json").read_text())
    if manifest["sha"] != SOURCE_SHA:
        raise ValueError("wrong frozen source commit")
    source_records = {r["path"]: r for r in manifest["files"]}
    input_hashes = {}
    for name, expected in [("lm1-baseline.lm1", BASE_SHA), ("lm1-tokenizer.lmbp", TOKENIZER_SHA)]:
        actual = digest(args.inputs / name)
        if actual != expected:
            raise ValueError(f"input hash mismatch: {name}")
        input_hashes[name] = actual
    docs = args.inputs / "original-train-docs"
    records = json.loads((docs / "manifest.json").read_text())
    if records != json.loads((args.source / EXP_PATH / "train-source-manifest.json").read_text()) or len(records) != 77:
        raise ValueError("TRAIN manifest differs from frozen source")
    for record in records:
        path_bytes = record["original_path"].encode()
        if hashlib.sha256(path_bytes).digest()[0] % 10 in {0, 1}:
            raise ValueError("holdout in training")
        if record["local_name"] != hashlib.sha256(path_bytes).hexdigest() + ".md":
            raise ValueError("invalid document path identity")
        if digest(docs / record["local_name"]) != record["sha256"]:
            raise ValueError("document hash mismatch")
        input_hashes["original-train-docs/" + record["local_name"]] = record["sha256"]
    for name in ["paths.tsv", "manifest.json"]:
        input_hashes["original-train-docs/" + name] = digest(docs / name)
    expected_paths = "".join(f'{r["local_name"]}\t{r["original_path"]}\n' for r in records)
    if (docs / "paths.tsv").read_text() != expected_paths:
        raise ValueError("TRAIN order mismatch")
    output.mkdir(parents=True)
    copied = output / "frozen-source"
    copied.mkdir()
    compiled_sources = []
    source_hashes = {}
    for name in SOURCES:
        actual = digest(args.source / name)
        if actual != source_records[name]["sha256"]:
            raise ValueError(f"source hash mismatch: {name}")
        destination = copied / Path(name).name
        shutil.copyfile(args.source / name, destination)
        compiled_sources.append(str(destination))
        source_hashes[name] = actual
    jars = list(args.kotlin_lib.glob("*.jar"))
    stdlib = args.kotlin_lib / "kotlin-stdlib-2.0.20.jar"
    annotations = args.kotlin_lib / "annotations-24.0.1.jar"
    compiler = args.kotlin_lib / "kotlin-compiler-embeddable-2.0.20.jar"
    for needed in [stdlib, annotations, compiler]:
        if not needed.is_file():
            raise FileNotFoundError(needed)
    compiler_cp = os.pathsep.join(str(p) for p in sorted(jars))
    harness_jar = output / "mechanism.jar"
    cp = os.pathsep.join([str(harness_jar), str(stdlib), str(annotations)])
    commands = []
    started = time.perf_counter()
    started_utc = datetime.now(timezone.utc).isoformat()

    def run(label: str, command: list[str], timeout: int) -> subprocess.CompletedProcess[str]:
        t0 = time.perf_counter()
        completed = subprocess.run(command, capture_output=True, text=True, timeout=timeout, cwd=root)
        (output / f"{label}.stdout").write_text(completed.stdout)
        (output / f"{label}.stderr").write_text(completed.stderr)
        commands.append({"label": label, "argv": command, "timeoutSeconds": timeout,
                         "exitCode": completed.returncode, "wallSeconds": time.perf_counter() - t0})
        (output / "commands.json").write_text(json.dumps(commands, indent=2) + "\n")
        if completed.returncode:
            raise RuntimeError(f"{label} failed; see recorded stdout/stderr")
        return completed

    run("compile", ["java", "-Xmx1024m", "-cp", compiler_cp, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
        "-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", os.pathsep.join([str(stdlib), str(annotations)]),
        "-d", str(harness_jar), *compiled_sources, str(root / "MechanismExperiment.kt")], 120)
    capacity = run("baseline-capacity", ["java", "-Xmx1024m", "-cp", cp,
        "at.heinz.deviceai.learning.lm.LmCapacityBenchmark", str(args.inputs / "lm1-baseline.lm1"),
        str(args.inputs / "lm1-tokenizer.lmbp")], 60)
    verdicts = [line for line in capacity.stdout.splitlines() if line.startswith("CAPACITY_RESULT\t")]
    if len(verdicts) != 1 or "\tpass=true\t" not in verdicts[0]:
        raise RuntimeError("BASELINE_GATE: no training allowed")
    result = run("mechanism", ["java", "-Xmx1024m", "-cp", cp,
        "at.heinz.deviceai.learning.lm.MechanismExperiment", str(args.inputs), str(root / "probes.tsv"), str(output)], 120)
    events = [json.loads(line) for line in result.stdout.splitlines() if line.strip()]
    if not events or events[-1]["kind"] != "complete":
        raise RuntimeError("incomplete experiment")
    for name, expected in input_hashes.items():
        if digest(args.inputs / name) != expected:
            raise RuntimeError(f"read-only input changed: {name}")
    for name, expected in source_hashes.items():
        if digest(args.source / name) != expected:
            raise RuntimeError(f"read-only source changed: {name}")
    summary = {
        "schemaVersion": 1, "scope": "RESEARCH_SHADOW", "sourceCommit": SOURCE_SHA,
        "checkpointCommit": "d2304a7658e1256b160c8012e1bac28268ff64a5",
        "corpusCommit": "1d92db03b87558c0b330dc584fd1f4c025a93c82",
        "startedUtc": started_utc, "wallSeconds": time.perf_counter() - started,
        "host": {"platform": platform.platform(), "cpuCount": os.cpu_count(),
                 "python": sys.version, "java": subprocess.run(["java", "-version"], capture_output=True, text=True).stderr},
        "baselineCapacityVerdict": verdicts[0], "trainStartsFromPassedOriginalOnly": True,
        "sourceHashes": source_hashes, "inputHashes": input_hashes,
        "harnessSha256": digest(root / "MechanismExperiment.kt"), "runnerSha256": digest(Path(__file__)),
        "probesSha256": digest(root / "probes.tsv"), "compilerSha256": digest(compiler),
        "commands": commands, "events": events,
        "limitations": ["Host/JVM only; no Fold7 measurement", "Small mechanism test; no linguistic capability claim",
                         "No promotion or change to gates", "EOS ablation changes target count and loss normalization",
                         "New probes are diagnostic, not a representative external benchmark",
                         "No original VALIDATION/TEST texts read", "No grown model trained; parity only"]}
    (output / "results.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"output": str(output), "events": len(events), "wallSeconds": summary["wallSeconds"],
                      "resultsSha256": digest(output / "results.json")}, indent=2))


if __name__ == "__main__":
    main()
