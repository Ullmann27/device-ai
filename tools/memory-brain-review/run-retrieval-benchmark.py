#!/usr/bin/env python3
"""Run the compiled, fixed synthetic benchmark and bind it to its source receipt."""

import argparse
import hashlib
import json
import platform
import subprocess
from datetime import datetime, timezone
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--compiled-provenance", type=Path, required=True,
                        help="Successful run_focused.py receipt that compiled RetrievalBenchmark.kt")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[2])
    args = parser.parse_args()
    repo = args.repo.resolve()
    receipt = json.loads(args.compiled_provenance.read_text())
    if receipt.get("junit_exit") != 0:
        raise SystemExit("A successful focused compile/test receipt is required.")
    required = {
        "app/src/main/java/at/heinz/deviceai/cognitive/HybridMemoryRetriever.kt",
        "app/src/test/java/at/heinz/deviceai/cognitive/fixtures/HybridMemoryRetriever578.kt",
        "tools/memory-brain-review/RetrievalBenchmark.kt",
    }
    files = {entry["path"]: entry["sha256"] for entry in receipt["files"]}
    if not required.issubset(files):
        raise SystemExit("Receipt must include the candidate, frozen reference and benchmark source.")
    for name, expected in files.items():
        source = (repo / name).resolve()
        source.relative_to(repo)
        if hashlib.sha256(source.read_bytes()).hexdigest() != expected:
            raise SystemExit(f"Source differs from compiled receipt: {name}")
    junit_command = receipt["junit_command"]
    runner_index = junit_command.index("org.junit.runner.JUnitCore")
    command = junit_command[:runner_index] + ["at.heinz.deviceai.cognitive.review.RetrievalBenchmark"]
    result = subprocess.run(command, check=True, capture_output=True, text=True, timeout=180)
    report = json.loads(result.stdout)
    report["sourceCommitAtRun"] = subprocess.check_output(
        ["git", "-C", str(repo), "rev-parse", "HEAD"], text=True
    ).strip()
    report["sourceFiles"] = receipt["files"]
    report["compiledBaseline"] = receipt["baseline"]
    report["compiledReceiptSha256"] = hashlib.sha256(args.compiled_provenance.read_bytes()).hexdigest()
    report["timestampUtc"] = datetime.now(timezone.utc).isoformat()
    report["machineArchitecture"] = platform.machine()
    report["javaVersion"] = subprocess.run(
        [command[0], "-version"], check=True, capture_output=True, text=True
    ).stderr.strip()
    report["jvmFlags"] = [part for part in command[1:] if part.startswith(("-XX:", "-Xmx"))]
    report["stderr"] = result.stderr
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("x") as output:
        json.dump(report, output, indent=2)
        output.write("\n")
    print(args.output)
    for workload in report["workloads"]:
        baseline = workload["baseline"]
        candidate = workload["candidate"]
        ratio = candidate["medianNanos"] / baseline["medianNanos"]
        print(f'{workload["name"]}: median time ratio candidate/baseline={ratio:.3f}; '
              f'allocated bytes {baseline["medianAllocatedBytes"]} -> {candidate["medianAllocatedBytes"]}')


if __name__ == "__main__":
    main()
