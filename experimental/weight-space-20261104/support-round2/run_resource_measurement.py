#!/usr/bin/env python3
"""Execute one registered Linux host resource probe and retain its raw evidence.

The registration must be committed first. Its exact command, executable, JARs,
sources and immutable inputs are checked before launch. No shell is involved.
The JVM performs the single bounded optimizer update; this wrapper measures the
whole isolated timeout/Java process tree, not Android or model quality.
"""

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import resource
import struct
import subprocess
import sys
import time


REGISTRATION_PATH = "experimental/weight-space-20261104/support-round2/resource-registration.json"
PROBE_CLASS = "at.heinz.deviceai.learning.lm.LmTrainingResourceProbe"


def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def validate_registered_sequence(registration, tokenizer):
    """Bind the literal registered IDs to this tokenizer's actual special IDs."""
    path = Path(tokenizer)
    if not 8 <= path.stat().st_size <= 64008:
        raise ValueError("invalid tokenizer size")
    raw = path.read_bytes()
    magic, merges = struct.unpack(">ii", raw[:8])
    if magic != 0x4C4D4250 or not 0 <= merges <= 8000 or len(raw) != 8 + 8 * merges:
        raise ValueError("invalid LMBP tokenizer layout")
    for index, (left, right) in enumerate(struct.iter_unpack(">ii", raw[8:])):
        if not 0 <= left < 256 + index or not 0 <= right < 256 + index:
            raise ValueError("invalid tokenizer merge reference")
    expected = [256 + merges, 112, 114, 111, 98, 101, 33, 257 + merges]
    declared = registration.get("sequence")
    if (not isinstance(declared, list) or any(type(value) is not int for value in declared)
            or declared != expected or registration.get("targets") != 7):
        raise ValueError(f"registered sequence disagrees with tokenizer: expected {expected}, got {declared}")
    return expected


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--validate-only", action="store_true")
    args = parser.parse_args()
    repo = args.repo.resolve(strict=True)
    commit = args.source_commit
    if len(commit) != 40 or any(c not in "0123456789abcdef" for c in commit):
        raise ValueError("source commit must be a full SHA")
    blob = subprocess.check_output(["git", "-C", str(repo), "show", f"{commit}:{REGISTRATION_PATH}"])
    if (repo / REGISTRATION_PATH).read_bytes() != blob:
        raise ValueError("working registration differs from the committed protocol")
    registration = json.loads(blob)
    if registration["schema"] != "continuum-lm1-resource-probe-v1":
        raise ValueError("unsupported registration")
    if registration["timeoutSeconds"] != 120 or registration["optimizerUpdates"] != 1:
        raise ValueError("resource budget changed")
    command = registration["command"]
    if not isinstance(command, list) or not all(isinstance(x, str) and x for x in command):
        raise ValueError("command must be a nonempty argv list")
    if (len(command) != 10 or command[1:5] != ["-Xms64m", "-Xmx3g", "-XX:ActiveProcessorCount=1", "-cp"]
            or command[6:8] != [PROBE_CLASS, "--real16m"]
            or command[8:] != registration["immutableInputs"]):
        raise ValueError("unexpected JVM command")
    pins = registration["files"]
    for item in pins:
        if sha256(item["path"]) != item["sha256"]:
            raise ValueError(f"registered file identity changed: {item['path']}")
    covered = {str(Path(item["path"]).resolve()) for item in pins}
    required = [command[0], *command[5].split(":"), str(Path(__file__).resolve())]
    required.extend(registration["immutableInputs"])
    if any(str(Path(name).resolve()) not in covered for name in required):
        raise ValueError("registration does not bind every executable, JAR, wrapper and input")
    timeout = registration["timeoutExecutable"]
    if sha256(timeout["path"]) != timeout["sha256"]:
        raise ValueError("timeout executable changed")
    before = {name: sha256(name) for name in registration["immutableInputs"]}
    validate_registered_sequence(registration, registration["immutableInputs"][1])
    if args.validate_only:
        print(json.dumps({"status": "REGISTERED_INPUTS_VERIFIED", "sourceCommit": commit,
                          "trainingAttempts": 0, "outputCreated": False}))
        return 0
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    (output / "registration.json").write_bytes(blob)
    actual_command = [timeout["path"], "--signal=KILL", "120s", *command]
    started = datetime.now(timezone.utc).isoformat()
    (output / "attempt.json").write_text(json.dumps({
        "sourceCommit": commit, "startedUtc": started, "command": actual_command,
        "inputHashesBefore": before, "state": "STARTING"
    }, indent=2) + "\n")
    start = time.monotonic()
    preflight_usage = resource.getrusage(resource.RUSAGE_CHILDREN)
    print(json.dumps({"state": "STARTING", "output": str(output), "deadlineSeconds": 120}), flush=True)
    with (output / "native.tsv").open("wb") as stdout, (output / "stderr.log").open("wb") as stderr:
        process = subprocess.run(actual_command, cwd=repo, stdout=stdout, stderr=stderr,
                                 start_new_session=True, check=False)
    elapsed = time.monotonic() - start
    usage = resource.getrusage(resource.RUSAGE_CHILDREN)
    after = {name: sha256(name) for name in registration["immutableInputs"]}
    unchanged = before == after
    receipt = {
        "schema": "continuum-lm1-resource-measurement-v1", "sourceCommit": commit,
        "registrationSha256": hashlib.sha256(blob).hexdigest(),
        "startedUtc": started, "finishedUtc": datetime.now(timezone.utc).isoformat(),
        "command": actual_command, "exitCode": process.returncode,
        "processStatus": "COMPLETED" if process.returncode == 0 else "FAILED_OR_TERMINATED",
        "elapsedSeconds": elapsed, "maxRssKiB": usage.ru_maxrss,
        "rssMethod": "LINUX_RUSAGE_CHILDREN_MAX_RSS_WITH_PREFLIGHT_BASELINE",
        "preflightChildrenMaxRssKiB": preflight_usage.ru_maxrss,
        "probeExceedsPreflightRss": usage.ru_maxrss > preflight_usage.ru_maxrss,
        "rssScope": "wrapper children: Git preflight and timeout/Java tree; includes load, checks, retained comparison arrays and update",
        "userSeconds": usage.ru_utime - preflight_usage.ru_utime,
        "systemSeconds": usage.ru_stime - preflight_usage.ru_stime,
        "inputHashesBefore": before, "inputHashesAfter": after, "inputsUnchanged": unchanged,
        "stdoutSha256": sha256(output / "native.tsv"), "stderrSha256": sha256(output / "stderr.log"),
        "trainingAttempts": 1, "trainingOutcome": "REQUIRES_NATIVE_RECORD_REVIEW",
        "quality": "NOT_EVALUATED", "android": "NOT_RUN", "promotion": False
    }
    (output / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
    print(json.dumps(receipt, indent=2), flush=True)
    return 0 if process.returncode == 0 and unchanged else 1


if __name__ == "__main__":
    sys.exit(main())
