#!/usr/bin/env python3
"""Restore pinned source locally, record runtime availability, and measure real recall."""
from __future__ import annotations
import argparse
from datetime import datetime, timezone
import hashlib
import io
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import tarfile
import time

BASELINE = "e101e393dd6c80803ac9d9ac91fccd9812c4a18d"
INTEGRATION_REPORTED = "2a364005b52e3240eb799f86aea31be90b390b10"
HERE = Path(__file__).resolve().parent


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def now() -> str:
    return datetime.now(timezone.utc).isoformat()


def run(command, *, cwd=None, env=None, timeout=60):
    start = time.perf_counter()
    result = subprocess.run(command, cwd=cwd, env=env, capture_output=True,
                            timeout=timeout, check=False)
    return result, {"command": list(command), "cwd": str(cwd) if cwd else None,
                    "exit_code": result.returncode, "wall_seconds": time.perf_counter() - start}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, required=True,
                        help="Read-only local Git object database containing the exact baseline")
    args = parser.parse_args()
    repository = args.repo.resolve(strict=True)
    run_dir = HERE / "runs" / datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    run_dir.mkdir(parents=True, exist_ok=False)
    source = run_dir / "pinned-source"
    source.mkdir()
    temporary = run_dir / "tmp"
    temporary.mkdir()
    env = {**os.environ, "GIT_NO_LAZY_FETCH": "1", "GIT_OPTIONAL_LOCKS": "0",
           "TMPDIR": str(temporary), "TMP": str(temporary), "TEMP": str(temporary)}
    env.pop("NODE_TEST_CONTEXT", None)
    commands = []
    start = time.perf_counter()
    archive, trace = run(["git", "-C", str(repository), "archive", "--format=tar",
                          BASELINE, ".github/continuum", "scripts"], env=env)
    commands.append(trace)
    if archive.returncode:
        raise RuntimeError(archive.stderr.decode(errors="replace"))
    sources = []
    with tarfile.open(fileobj=io.BytesIO(archive.stdout)) as packed:
        for member in packed.getmembers():
            relative = Path(member.name)
            if relative.is_absolute() or ".." in relative.parts:
                raise ValueError("unsafe source archive path")
            if member.isdir():
                (source / relative).mkdir(parents=True, exist_ok=True)
                continue
            if not member.isfile():
                raise ValueError("source archive must contain only regular files")
            data = packed.extractfile(member).read()
            target = source / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            sources.append({"path": member.name, "bytes": len(data), "sha256": sha(data)})
    (run_dir / "source-manifest.json").write_text(json.dumps({"baseline": BASELINE,
        "archive_sha256": sha(archive.stdout), "files": sources}, indent=2) + "\n")

    paths = ["node", "python3", "java", "llama-cli", "llama-completion", "llama-server",
             "ollama", "litert-lm", "litertlm", "adb", "bwrap"]
    inventory = {"checked_at": now(), "executables_in_path": {name: shutil.which(name) for name in paths},
        "configuration_variable_names_only": [name for name in os.environ if any(part in name.upper()
           for part in ["CONTINUUM", "LLAMA", "GEMMA", "OLLAMA", "MODEL_HOST", "WORKER_HOST"])],
        "credential_values_read_or_published": False,
        "historical_S01_replayed_or_unblocked": False, "network_probes": 0,
        "downloads": 0, "owner_task_submissions": 0, "model_invocations": 0,
        "scope_limit": "PATH, current process environment and named model/runtime files beneath /workspace/scratch; not an exhaustive machine-wide inventory"}
    files, trace = run(["rg", "--files", "-uuu", "/workspace/scratch", "-g", "*.gguf", "-g",
                       "*.litertlm", "-g", "*.safetensors", "-g", "llama-cli", "-g",
                       "llama-completion", "-g", "llama-server", "-g", "libllama.so"], timeout=30)
    commands.append(trace)
    inventory["named_model_or_runtime_paths"] = files.stdout.decode().splitlines()
    inventory["file_inventory_exit_code"] = files.returncode
    processes, trace = run(["ps", "-eo", "comm"], timeout=10)
    commands.append(trace)
    inventory["process_names_only"] = processes.stdout.decode().splitlines()
    integrated, trace = run(["git", "-C", str(repository), "cat-file", "-e", INTEGRATION_REPORTED + "^{commit}"], env=env)
    commands.append(trace)
    inventory["reported_pr510_sha"] = INTEGRATION_REPORTED
    inventory["pr510_object_available_in_supplied_repository"] = integrated.returncode == 0
    inventory["reported_pr510_status"] = "LIVE_ACTIVATION_BLOCKED (parent handoff; not a new live probe)"
    preflight, trace = run(["node", str(HERE / "runtime_preflight.mjs"), str(source)], env=env, timeout=15)
    commands.append(trace)
    (run_dir / "preflight.stdout.json").write_bytes(preflight.stdout)
    (run_dir / "preflight.stderr").write_bytes(preflight.stderr)
    inventory["production_preflight"] = json.loads(preflight.stdout)
    inventory["wall_seconds"] = time.perf_counter() - start
    (run_dir / "availability.json").write_text(json.dumps(inventory, indent=2) + "\n")
    if preflight.returncode != 0:
        raise RuntimeError("Runtime preflight did not complete")

    result, trace = run(["node", str(HERE / "recall_scale.mjs"), str(source), str(temporary)],
                        env=env, timeout=120)
    commands.append(trace)
    (run_dir / "recall.stdout.json").write_bytes(result.stdout)
    (run_dir / "recall.stderr").write_bytes(result.stderr)
    measured = json.loads(result.stdout)
    unchanged = all(sha((source / item["path"]).read_bytes()) == item["sha256"] for item in sources)
    summary = {"schema": "continuum-autonomy-round2-v1", "finished_at": now(),
        "baseline_sha": BASELINE, "source_object_repository": str(repository),
        "source_directory": str(source), "source_files": len(sources),
        "source_hashes_unchanged": unchanged, "commands": commands,
        "total_wall_seconds": time.perf_counter() - start,
        "runtime": {"python": platform.python_version(), "platform": platform.platform()},
        "evidence_class": "CONTRACT_TEST_SYNTHETIC_TECHNICAL_PROOFS",
        "real_model_repair": "NOT_RUN_BLOCKED_LOCAL_MODEL_UNAVAILABLE",
        "neural_training": "NOT_RUN", "owner_job_execution": "NOT_RUN", "device": "NOT_RUN",
        "script_hashes": {p.name: sha(p.read_bytes()) for p in [HERE / "run_experiment.py",
            HERE / "runtime_preflight.mjs", HERE / "recall_scale.mjs"]},
        "passed": result.returncode == 0 and measured["passed"] and unchanged,
        "result_file": "recall.stdout.json", "availability_file": "availability.json"}
    (run_dir / "measurements.json").write_text(json.dumps(summary, indent=2) + "\n")
    (HERE / "latest-run.txt").write_text(str(run_dir.relative_to(HERE)) + "\n")
    print(json.dumps({"run_directory": str(run_dir), "passed": summary["passed"],
                      "conditions": len(measured["conditions"]), "measured_calls": measured["measured_calls"],
                      "wall_seconds": trace["wall_seconds"]}))
    if not summary["passed"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
