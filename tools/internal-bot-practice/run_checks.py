#!/usr/bin/env python3
"""Compile the real Android sources and verify the bounded bot-practice contract offline.

This runs developer tests with fixture model replies. It does not start a model,
change weights, grant a capability, build an APK, or report completed bot training.
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


TEST_CLASSES = (
    "at.heinz.deviceai.chief.ChiefBotPracticeTest",
    "at.heinz.deviceai.chief.ChiefBotPracticeConversationTest",
    "at.heinz.deviceai.chief.ChiefEngineeringPracticeCurriculumTest",
    "at.heinz.deviceai.chief.ChiefAvatarPracticeCurriculumTest",
    "at.heinz.deviceai.chief.ChiefBotConversationTest",
    "at.heinz.deviceai.chief.ChiefBotProfileStoreTest",
    "at.heinz.deviceai.chief.ChiefBotStorageBoundaryTest",
)


def utc_now() -> str:
    return dt.datetime.now(dt.timezone.utc).isoformat()


def write_json(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def source_snapshot(root: Path) -> dict[str, str]:
    files = set()
    for relative in ("app/src/main", "app/src/test", "tools/internal-bot-practice"):
        folder = root / relative
        files.update(path for path in folder.rglob("*") if path.is_file() and "__pycache__" not in path.parts)
    for relative in ("settings.gradle.kts", "build.gradle.kts", "app/build.gradle.kts", "gradle.properties"):
        path = root / relative
        if path.is_file():
            files.add(path)
    return {
        path.relative_to(root).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
        for path in sorted(files)
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--gradle-home", type=Path, required=True)
    parser.add_argument("--gradle-cache", type=Path, required=True)
    parser.add_argument("--android-sdk", type=Path, required=True)
    parser.add_argument("--java-home", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--timeout-seconds", type=int, default=600)
    args = parser.parse_args()
    if not 30 <= args.timeout_seconds <= 1800:
        parser.error("timeout must be between 30 and 1800 seconds")
    root = args.root.resolve(strict=True)
    gradle = args.gradle_home.resolve(strict=True) / "bin/gradle"
    java_home = args.java_home.resolve(strict=True)
    java = java_home / "bin/java"
    sdk = args.android_sdk.resolve(strict=True)
    cache = args.gradle_cache.resolve(strict=True)
    for executable in (gradle, java):
        if not executable.is_file() or not os.access(executable, os.X_OK):
            parser.error(f"required executable unavailable: {executable}")
    if not (sdk / "platforms/android-36/android.jar").is_file():
        parser.error("existing Android 36 SDK is required; this runner downloads nothing")
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    before = source_snapshot(root)
    write_json(out / "source-before.json", before)
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
    worktree = subprocess.check_output(["git", "status", "--porcelain"], cwd=root, text=True)
    env = os.environ.copy()
    for key in ("CONTINUUM_LOCAL_SIGNING_FILE", "CONTINUUM_LOCAL_SIGNING_PASSWORD",
                "CONTINUUM_MENTOR_SIGNING_FILE", "CONTINUUM_MENTOR_SIGNING_PASSWORD"):
        env.pop(key, None)
    env.update(JAVA_HOME=str(java_home), ANDROID_HOME=str(sdk), ANDROID_SDK_ROOT=str(sdk),
               GRADLE_USER_HOME=str(cache), PATH=str(java_home / "bin") + os.pathsep + env.get("PATH", ""))
    command = [str(gradle), "--offline", "--no-daemon", "--console=plain", "--max-workers=2",
               "--rerun-tasks", "-Dorg.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8",
               "-Pkotlin.compiler.execution.strategy=in-process", ":app:testDebugUnitTest"]
    for name in TEST_CLASSES:
        command.extend(("--tests", name))
    write_json(out / "command.json", command)
    version = subprocess.run([str(java), "-version"], capture_output=True, text=True, timeout=10)
    (out / "java-version.txt").write_text(version.stdout + version.stderr, encoding="utf-8")
    started_at, started = utc_now(), time.monotonic()
    timed_out = False
    with (out / "gradle.log").open("w", encoding="utf-8") as log:
        process = subprocess.Popen(command, cwd=root, env=env, stdout=log, stderr=subprocess.STDOUT,
                                   start_new_session=True)
        try:
            exit_code = process.wait(timeout=args.timeout_seconds)
        except subprocess.TimeoutExpired:
            timed_out = True
            os.killpg(process.pid, signal.SIGTERM)
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()
            exit_code = 124
    after = source_snapshot(root)
    write_json(out / "source-after.json", after)
    suites = []
    xml_dir = root / "app/build/test-results/testDebugUnitTest"
    for name in TEST_CLASSES:
        source = xml_dir / ("TEST-" + name + ".xml")
        if not source.is_file():
            suites.append({"class": name, "status": "NO_FRESH_RESULT"})
            continue
        # A failed compile cannot turn a previous run's XML into current test evidence.
        if source.stat().st_mtime < dt.datetime.fromisoformat(started_at).timestamp():
            suites.append({"class": name, "status": "STALE_RESULT_REJECTED"})
            continue
        parsed = ET.parse(source).getroot()
        shutil.copyfile(source, out / source.name)
        suites.append({"class": name, "status": "EXECUTED", **{
            key: int(parsed.attrib.get(key, "0")) for key in ("tests", "failures", "errors", "skipped")
        }})
    totals = {key: sum(item.get(key, 0) for item in suites) for key in ("tests", "failures", "errors", "skipped")}
    complete = all(item["status"] == "EXECUTED" and item.get("tests", 0) > 0 for item in suites)
    unchanged = before == after
    passed = (exit_code == 0 and not timed_out and unchanged and complete and
              totals["failures"] == totals["errors"] == totals["skipped"] == 0)
    result = {"schema": "continuum-internal-bot-practice-developer-checks", "version": 1,
              "startedAt": started_at, "finishedAt": utc_now(), "seconds": round(time.monotonic() - started, 3),
              "baseHead": head, "worktreeAtStart": worktree, "exitCode": exit_code, "timedOut": timed_out,
              "sourceFiles": len(before), "sourceUnchangedDuringCheck": unchanged,
              "allSelectedClassesExecuted": complete, "suites": suites, "totals": totals, "passed": passed,
              "actualBotModelAttempts": 0, "weightUpdates": 0, "deviceVerified": False,
              "limits": "Real source compilation and developer tests with fixture replies; no bot-training success claim."}
    write_json(out / "result.json", result)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
