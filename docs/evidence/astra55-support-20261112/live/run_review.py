#!/usr/bin/env python3
"""Compile copied production sources; no Gradle or source mutations."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
from datetime import datetime, timezone

parser = argparse.ArgumentParser()
parser.add_argument("--source", type=Path, required=True)
parser.add_argument("--evidence", type=Path, required=True)
parser.add_argument("--compiler-lib", type=Path, required=True)
parser.add_argument("--patch", type=Path)
parser.add_argument("--neighbors", action="store_true")
parser.add_argument("--suite", choices=["all", "focused", "scroll"], default="all")
parser.add_argument("--revision")
args = parser.parse_args()
here = Path(__file__).resolve().parent
evidence = args.evidence.resolve()
evidence.mkdir(parents=True, exist_ok=False)
source = args.source.resolve()
paths = [
    "app/src/main/java/at/heinz/deviceai/cognitive/LiveComputerFrames.kt",
    "app/src/main/java/at/heinz/deviceai/cognitive/LiveComputerActions.kt",
]
enum_sources = ["app/src/main/java/at/heinz/deviceai/CommandInterpreter.kt",
                "app/src/main/java/at/heinz/deviceai/AgentToolLayer.kt"]
neighbor_tests = ["LiveComputerFramesTest", "LiveComputerActionsTest", "VerifiedNodeClickTest"]
if args.neighbors:
    paths += ["app/src/main/java/at/heinz/deviceai/cognitive/VerifiedNodeClick.kt",
              "app/src/main/java/at/heinz/deviceai/AgentProtocol.kt",
              "app/src/main/java/at/heinz/deviceai/ToolRegistry.kt",
              "app/src/main/java/at/heinz/deviceai/ToolExecutionReceiptStore.kt"]
    paths += [f"app/src/test/java/at/heinz/deviceai/cognitive/{name}.kt" for name in neighbor_tests]

def sha(data):
    return hashlib.sha256(data).hexdigest()

def git(*command):
    return subprocess.check_output(["git", "-C", str(source), *command], text=True).strip()

revision = git("rev-parse", "--verify", args.revision + "^{commit}") if args.revision else None

def source_bytes(relative):
    if revision:
        return subprocess.check_output(["git", "-C", str(source), "show", revision + ":" + relative])
    return (source / relative).read_bytes()

names = [
    "kotlin-compiler-embeddable-2.0.20.jar", "kotlin-stdlib-2.0.20.jar",
    "kotlin-script-runtime-2.0.20.jar", "kotlin-reflect-2.0.20.jar",
    "trove4j-1.0.20200330.jar", "annotations-24.0.1.jar",
    "kotlinx-coroutines-core-jvm-1.6.4.jar", "junit-4.13.2.jar", "hamcrest-core-1.3.jar",
]
jars = [args.compiler_lib.resolve() / name for name in names]
for jar in jars:
    if not jar.is_file():
        raise FileNotFoundError(jar)
cp = os.pathsep.join(map(str, jars))
record = {
    "started_at_utc": datetime.now(timezone.utc).isoformat(),
    "source_head_before": git("rev-parse", "HEAD"),
    "source_branch": git("branch", "--show-current"),
    "source_status_before": git("status", "--short"),
    "snapshot_origin": "git:" + revision if revision else "worktree",
    "boundary": "Pure Kotlin copied production classes; no Android/device test",
    "fold7": "NOT_RUN", "gradle": "NOT_RUN", "sources": {},
    "dependencies": {jar.name: sha(jar.read_bytes()) for jar in jars},
}
test_paths = [here / "ReviewTestRunner.kt", here / "LiveSupportRegressionTest.kt"]
if not args.neighbors:
    test_paths.append(here / "ToolTypeCompileFixtures.kt")
record["support_script_sha256"] = sha(Path(__file__).read_bytes())
record["suite"] = args.suite
record["neighbors"] = args.neighbors
record["tests"] = {path.name: sha(path.read_bytes()) for path in test_paths}
with tempfile.TemporaryDirectory(prefix="astra55-live-review-") as temp:
    temp = Path(temp)
    classes = temp / "classes"
    classes.mkdir()
    inputs = []
    for relative in paths:
        original = source / relative
        data = source_bytes(relative)
        target = temp / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        record["sources"][relative] = {
            "sha256_before_copy": sha(data), "sha256_snapshot": sha(target.read_bytes()),
            "sha256_after_copy": sha(source_bytes(relative)),
            "worktree_sha256_before": sha(original.read_bytes()),
        }
        inputs.append(target)
    if args.neighbors:
        enums = []
        for relative, name in zip(enum_sources, ["Risk", "ToolExecutionStatus"]):
            original = source / relative
            data = source_bytes(relative)
            text = data.decode()
            matched = re.search(r"enum class " + name + r"\s*\{[^}]+\}", text)
            if matched is None:
                raise ValueError(f"Missing enum {name}")
            enums.append(matched.group())
            record["sources"][relative] = {
                "sha256_before_copy": sha(data), "extract_sha256": sha(matched.group().encode()),
                "sha256_after_copy": sha(source_bytes(relative)), "compiled_excerpt": name,
                "worktree_sha256_before": sha(original.read_bytes()),
            }
        enum_file = temp / "ExtractedProductionEnums.kt"
        enum_file.write_text("package at.heinz.deviceai\n\n" + "\n".join(enums) + "\n")
        inputs.append(enum_file)
    if args.patch:
        patch = args.patch.resolve()
        record["patch_sha256"] = sha(patch.read_bytes())
        for flags in [["--check"], []]:
            subprocess.run(["git", "apply", *flags, str(patch)], cwd=temp, check=True,
                           text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        record["patched_sources"] = {relative: sha((temp / relative).read_bytes()) for relative in paths}
    command = ["java", "-Xmx512m", "-cp", cp, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
               "-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", cp,
               "-d", str(classes), *map(str, inputs), *map(str, test_paths)]
    result = subprocess.run(command, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    (evidence / "compile.log").write_text(result.stdout)
    record["compile_exit"] = result.returncode
    if result.returncode == 0:
        test_class = "at.heinz.deviceai.cognitive.LiveSupportRegressionTest"
        focused_methods = ["anotherWindowDoesNotConfirmSameTaskAction",
                           "uncertainActionNeverBecomesRetryableThroughEviction",
                           "secureEvidenceStillCoversRateLimitedFrame",
                           "pauseDoesNotForgetSessionTimestamp",
                           "stopRetainsLeasedFrameUntilExactOnceRelease"]
        if args.suite == "focused":
            selectors = [test_class + "#" + method for method in focused_methods]
        elif args.suite == "scroll":
            selectors = [test_class + "#unrelatedBadgeChangeDoesNotConfirmScroll"]
        else:
            selectors = [test_class]
        if args.neighbors and args.suite != "scroll":
            selectors += ["at.heinz.deviceai.cognitive." + name for name in neighbor_tests]
        record["selectors"] = selectors
        test_command = ["java", "-Xmx256m", "-cp", str(classes) + os.pathsep + cp,
                        "ReviewTestRunnerKt", *selectors]
        tested = subprocess.run(test_command, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        (evidence / "junit.log").write_text(tested.stdout)
        record["junit_exit"] = tested.returncode
        print(tested.stdout)
        if args.patch and args.suite == "focused":
            open_command = ["java", "-Xmx256m", "-cp", str(classes) + os.pathsep + cp,
                            "ReviewTestRunnerKt", test_class + "#unrelatedBadgeChangeDoesNotConfirmScroll"]
            open_result = subprocess.run(open_command, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
            (evidence / "open-scroll.log").write_text(open_result.stdout)
            record["open_scroll_exit"] = open_result.returncode
            print(open_result.stdout)
    else:
        print(result.stdout)
    for relative in record["sources"]:
        record["sources"][relative]["sha256_after_run"] = sha(source_bytes(relative))
        record["sources"][relative]["worktree_sha256_after"] = sha((source / relative).read_bytes())
    record["source_head_after"] = git("rev-parse", "HEAD")
    record["finished_at_utc"] = datetime.now(timezone.utc).isoformat()
    (evidence / "manifest.json").write_text(json.dumps(record, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"head": record["source_head_before"], "compile_exit": record["compile_exit"],
                      "junit_exit": record.get("junit_exit", "NOT_RUN"),
                      "source_stable": all(v["sha256_before_copy"] == v["sha256_after_run"]
                                           for v in record["sources"].values())}))
raise SystemExit(record.get("junit_exit", record["compile_exit"]))
