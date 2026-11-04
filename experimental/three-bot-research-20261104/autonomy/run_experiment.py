#!/usr/bin/env python3
"""Reproduce the bounded CCC1 experience experiment without changing source."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import sys
import time
from datetime import datetime, timezone
import xml.etree.ElementTree as ET

BASELINE = "e101e393dd6c80803ac9d9ac91fccd9812c4a18d"
HERE = Path(__file__).resolve().parent
DEFAULT_SOURCE = HERE.parents[1] / "continuum-research"
FILES = [
    ".github/continuum/ccc1-experience.mjs",
    ".github/continuum/ccc1-experience-durability.test.mjs",
    ".github/continuum/ccc1-technical-recall.mjs",
    ".github/continuum/ccc1-technical-recall.test.mjs",
    ".github/continuum/ccc1-journal.mjs",
    ".github/continuum/ccc1-envelope.mjs",
    ".github/continuum/ccc1-orchestrator.mjs",
    ".github/continuum/ccc1-worker.mjs",
    ".github/continuum/coding-orchestrator.mjs",
    ".github/continuum/coding-provider.mjs",
    "scripts/continuum-local-model-observation.mjs",
]


def utc() -> str:
    return datetime.now(timezone.utc).isoformat()


def digest(file: Path) -> str:
    return hashlib.sha256(file.read_bytes()).hexdigest()


def git(source: Path, *args: str) -> str:
    return subprocess.check_output(
        ["git", "-C", str(source), *args], text=True,
        env={**os.environ, "GIT_OPTIONAL_LOCKS": "0"}, timeout=15,
    ).strip()


def execute(command: list[str], source: Path, env: dict[str, str], output: Path,
            name: str, timeout: int = 90) -> dict:
    started = utc()
    clock = time.perf_counter()
    try:
        result = subprocess.run(command, cwd=source, env=env, text=True,
                                capture_output=True, timeout=timeout, check=False)
        stdout, stderr, code = result.stdout, result.stderr, result.returncode
        timed_out = False
    except subprocess.TimeoutExpired as error:
        def decode(value):
            return value.decode(errors="replace") if isinstance(value, bytes) else value or ""
        stdout, stderr, code = decode(error.stdout), decode(error.stderr), None
        timed_out = True
    elapsed = time.perf_counter() - clock
    stdout_file = output / f"{name}.stdout"
    stderr_file = output / f"{name}.stderr"
    stdout_file.write_text(stdout)
    stderr_file.write_text(stderr)
    return {"command": command, "cwd": str(source), "started_at": started,
            "finished_at": utc(), "wall_seconds": elapsed, "exit_code": code,
            "timeout_seconds": timeout, "timed_out": timed_out,
            "stdout_file": str(stdout_file.relative_to(HERE)),
            "stderr_file": str(stderr_file.relative_to(HERE)),
            "stdout_sha256": digest(stdout_file), "stderr_sha256": digest(stderr_file)}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    args = parser.parse_args()
    source = args.source.resolve(strict=True)
    output = HERE / "results"
    output.mkdir(parents=True, exist_ok=True)
    temporary = HERE / "tmp"
    temporary.mkdir(exist_ok=True)
    actual_sha = git(source, "rev-parse", "HEAD")
    if actual_sha != BASELINE:
        raise SystemExit(f"Baseline mismatch: {actual_sha}; required {BASELINE}")
    source_before = {name: digest(source / name) for name in FILES}
    tracked_before = git(source, "diff", "--name-only", "HEAD")
    if tracked_before:
        raise SystemExit("Frozen source contains tracked changes")
    env = {**os.environ, "TMPDIR": str(temporary), "TMP": str(temporary),
           "TEMP": str(temporary), "NO_COLOR": "1", "GIT_OPTIONAL_LOCKS": "0"}
    env.pop("NODE_TEST_CONTEXT", None)
    report = {
        "schema": "continuum-autonomy-research-v1", "started_at": utc(),
        "baseline_sha": BASELINE, "source_directory": str(source),
        "source_hashes_before": source_before,
        "tracked_changes_before": tracked_before,
        "untracked_paths_before": git(source, "ls-files", "--others", "--exclude-standard").splitlines(),
        "runtime": {"python": sys.version, "node": subprocess.check_output(
            ["node", "--version"], text=True).strip(), "platform": platform.platform(),
            "machine": platform.machine(), "cpu_count": os.cpu_count()},
        "evidence_class": "CPU_HOST_CONTRACT_TEST_WITH_SYNTHETIC_TASK_DATA",
        "model_inference": "NOT_RUN", "neural_training": "NOT_RUN",
        "device_fold7": "NOT_RUN", "apk_build_install": "NOT_RUN",
        "external_api_calls_by_experiment": 0,
        "temporary_directory": str(temporary), "runs": [],
    }
    clock = time.perf_counter()
    for name, file in [
        ("experience-durability", ".github/continuum/ccc1-experience-durability.test.mjs"),
        ("technical-recall", ".github/continuum/ccc1-technical-recall.test.mjs"),
    ]:
        run = execute(["node", "--test", "--test-concurrency=1", "--test-reporter=junit", file],
                      source, env, output, name)
        try:
            tree = ET.fromstring((HERE / run["stdout_file"]).read_text())
            cases = list(tree.iter("testcase"))
            run["testcases"] = [{"name": c.get("name"), "seconds": float(c.get("time", "0")),
                                  "failed": c.find("failure") is not None,
                                  "error": c.find("error") is not None,
                                  "skipped": c.find("skipped") is not None} for c in cases]
            run["totals"] = {"tests": len(cases),
                             "failures": sum(c["failed"] for c in run["testcases"]),
                             "errors": sum(c["error"] for c in run["testcases"]),
                             "skipped": sum(c["skipped"] for c in run["testcases"])}
        except (ET.ParseError, ValueError) as error:
            run["parse_error"] = str(error)
        report["runs"].append(run)
    probe = execute(["node", str(HERE / "probe.mjs"), str(source), str(temporary)],
                    source, env, output, "independent-probe", timeout=30)
    try:
        probe["observations"] = json.loads((HERE / probe["stdout_file"]).read_text())
    except json.JSONDecodeError as error:
        probe["parse_error"] = str(error)
    report["runs"].append(probe)
    report["wall_seconds"] = time.perf_counter() - clock
    report["finished_at"] = utc()
    report["source_hashes_after"] = {name: digest(source / name) for name in FILES}
    report["source_hashes_unchanged"] = report["source_hashes_after"] == source_before
    report["tracked_changes_after"] = git(source, "diff", "--name-only", "HEAD")
    report["head_after"] = git(source, "rev-parse", "HEAD")
    report["script_hashes"] = {p.name: digest(p) for p in [HERE / "run_experiment.py", HERE / "probe.mjs"]}
    report["passed"] = (all(run["exit_code"] == 0 for run in report["runs"])
                        and report["source_hashes_unchanged"]
                        and not report["tracked_changes_after"]
                        and report["head_after"] == BASELINE)
    (output / "measurements.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"passed": report["passed"], "wall_seconds": report["wall_seconds"],
                      "runs": [{"name": r["stdout_file"], "exit_code": r["exit_code"],
                                "wall_seconds": r["wall_seconds"], "totals": r.get("totals")}
                               for r in report["runs"]]}, ensure_ascii=False))
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
