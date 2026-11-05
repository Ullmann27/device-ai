#!/usr/bin/env python3
"""Pinned host regression; no Android."""
from __future__ import annotations

import argparse
import fcntl
import hashlib
import json
import os
import signal
import subprocess
import sys
import tempfile
import time
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PRODUCTION = "app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryRetriever.kt"
TESTS = "app/src/test/java/at/heinz/deviceai/learning/"
BASE_COMMIT = "e8102654a34d435eee115643b414158cd34c6f1d"
BASE_BLOB = "dcaeed0b13eb3f4f88128e45c250d6d84a319c72"
PINS = {
    PRODUCTION: "d7639be8691d939f9aaa89c52bb85635ebe60f53",
    TESTS + "MemoryRelativeCueR13Test.kt": "89332fdb45bf3c46050a3ed751bb16441c7f3244",
    TESTS + "MemoryNumericEvidenceR12Test.kt": "c6fc1b7ed6a8cbbbaf87c46a2e3e685a5878ed8e",
    TESTS + "TemporalCueCalendarTest.kt": "17baa4a929361617da337615c4070149fce486b9",
    TESTS + "TemporalCueDailyBoundaryTest.kt": "63ada554c984cd42d235191d8df280bea86f02f2",
    TESTS + "ContinuumRetrievalExperimentTest.kt": "e86554e48efe833e63d80783f46d3c2d6d36dfe5",
}
CLASSES = [Path(path).stem for path in PINS if path.startswith(TESTS)]
TOOLS = [ROOT / "tools/memory-r13" / name for name in (
    "JUnitHostR13.kt", "EventEmittingBoundary.kt", "RunTests.kt", "Readout.kt")]
SHARED_LOCKS = [
    Path("/workspace/scratch/b6ee64d418cf/toolchain/gradle-build.lock"),
    Path("/workspace/scratch/d663e8444310/qa/gradle.lock"),
]


def blob(data: bytes) -> str:
    return hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    handles = []
    paths = SHARED_LOCKS if any(p.exists() for p in SHARED_LOCKS) else []
    if paths and not all(p.exists() for p in paths):
        print("BLOCKED_INCOMPLETE_SHARED_LOCKS", file=sys.stderr)
        return 75
    paths = [*paths, Path(tempfile.gettempdir()) / "continuum-memory-r13-host.lock"]
    out = None
    receipt: dict = {"scope": "FULL_RETRIEVER_SOURCE_HOST_ONLY", "steps": [], "locks": [str(p) for p in paths]}
    try:
        for path in paths:
            handle = path.open("a+")
            handles.append(handle)
            fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        if args.output is None:
            out = Path(tempfile.mkdtemp(prefix="continuum-memory-r13-"))
        else:
            requested = args.output.resolve()
            requested.mkdir(parents=True, exist_ok=False)
            out = requested
        print(f"OUTPUT={out}", flush=True)
        env = {**os.environ, "LANG": "C.UTF-8", "LC_ALL": "C.UTF-8"}
        for path, expected in PINS.items():
            actual = blob((ROOT / path).read_bytes())
            if actual != expected:
                raise RuntimeError(f"SOURCE_BLOB_MISMATCH {path}: {actual}")
        inputs = [ROOT / p for p in PINS] + TOOLS + [Path(__file__).resolve()]
        before = {str(p.relative_to(ROOT)): digest(p) for p in inputs}
        receipt["source_sha256_before"] = before
        receipt["source_git_blobs"] = PINS
        receipt["baseline_commit"] = BASE_COMMIT
        receipt["baseline_git_blob"] = BASE_BLOB

        def run(label: str, command: list[str], expected: int = 0, timeout: int = 120) -> bytes:
            started = datetime.now(timezone.utc).isoformat()
            clock = time.monotonic()
            timed_out = False
            target = out / (label + ".txt")
            with target.open("wb") as stream:
                process = subprocess.Popen(command, cwd=ROOT, env=env, stdout=stream,
                                           stderr=subprocess.STDOUT, start_new_session=True)
                try:
                    code = process.wait(timeout=timeout)
                except subprocess.TimeoutExpired:
                    timed_out = True
                    os.killpg(process.pid, signal.SIGTERM)
                    try:
                        code = process.wait(timeout=5)
                    except subprocess.TimeoutExpired:
                        os.killpg(process.pid, signal.SIGKILL)
                        code = process.wait()
            record = {"label": label, "argv": command, "started_utc": started,
                      "duration_seconds": time.monotonic() - clock, "exit_code": code,
                      "expected_exit_code": expected, "timed_out": timed_out,
                      "log_sha256": digest(target)}
            receipt["steps"].append(record)
            print(f"STEP {label} exit={code} timed_out={timed_out}", flush=True)
            if code != expected or timed_out:
                raise RuntimeError(f"UNEXPECTED_STEP_RESULT {label}")
            return target.read_bytes()

        run("kotlin-version", ["kotlinc", "-version"])
        run("java-version", ["java", "-version"])
        if args.baseline:
            original = args.baseline.read_bytes()
        else:
            original = run("git-baseline", ["git", "show", f"{BASE_COMMIT}:{PRODUCTION}"])
        if blob(original) != BASE_BLOB:
            raise RuntimeError("BASELINE_BLOB_MISMATCH")
        baseline_path = out / "baseline.kt"
        baseline_path.write_bytes(original)
        receipt["baseline_sha256"] = digest(baseline_path)
        common = [str(ROOT / path) for path in PINS if path.startswith(TESTS)] + [str(p) for p in TOOLS]
        readouts = []
        for label, source, expected_exit, summary in (
            ("baseline", baseline_path, 1, b"TOTAL=52 PASS=43 FAIL=9"),
            ("candidate", ROOT / PRODUCTION, 0, b"TOTAL=52 PASS=52 FAIL=0"),
        ):
            jar = out / (label + ".jar")
            run(label + "-compile", ["kotlinc", "-J-Xmx512m", str(source), *common,
                                    "-include-runtime", "-d", str(jar)])
            result = run(label + "-tests", ["java", "-Xmx512m", "-cp", str(jar),
                                           "at.heinz.deviceai.learning.RunTestsKt", *CLASSES], expected_exit)
            if summary not in result.splitlines():
                raise RuntimeError(f"UNEXPECTED_TEST_COUNTS {label}")
            readouts.append(run(label + "-readout", ["java", "-Xmx512m", "-cp", str(jar),
                                                    "at.heinz.deviceai.learning.ReadoutKt"]))
        if readouts[0] != readouts[1]:
            raise RuntimeError("EXPERIMENT_PARITY_CHANGED")
        if sum(line.startswith(b"RESULT ") for line in readouts[1].splitlines()) != 220:
            raise RuntimeError("EXPERIMENT_CASE_COUNT_CHANGED")
        after = {str(p.relative_to(ROOT)): digest(p) for p in inputs}
        receipt["source_sha256_after"] = after
        if before != after:
            raise RuntimeError("SOURCE_CHANGED_DURING_EXECUTION")
        receipt["status"] = "HOST_PASS"
        receipt["exact_readout_sha256"] = hashlib.sha256(readouts[1]).hexdigest()
        receipt["readout_results"] = 220
        receipt["boundaries"] = ["No JUnit4 runtime", "Fail-fast event adapter not exercised",
                                 "No Gradle/Android/UI/lint/device/model/APK acceptance"]
        print("R13_HOST_RED_43_9_GREEN_52_0_EXACT_PARITY_220", flush=True)
        return 0
    except BlockingIOError:
        print("BLOCKED_LOCK_HELD", file=sys.stderr)
        return 75
    except (OSError, RuntimeError, ValueError) as error:
        receipt["status"] = "FAIL_OR_UNAVAILABLE"
        receipt["error"] = str(error)
        print(str(error), file=sys.stderr)
        return 1
    finally:
        if out is not None and out.exists():
            (out / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
        for handle in reversed(handles):
            handle.close()


if __name__ == "__main__":
    sys.exit(main())
