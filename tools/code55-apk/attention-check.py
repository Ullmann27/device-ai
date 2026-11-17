#!/usr/bin/env python3
"""Compile actual render/action contracts without Android."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument("--deps", required=True, type=Path)
parser.add_argument("--out", required=True, type=Path)
parser.add_argument("--android-jar", required=True, type=Path)
args = parser.parse_args()
repo = Path(__file__).resolve().parents[2]
out = args.out.resolve()
out.mkdir(parents=True, exist_ok=True)
main = repo / "app/src/main/java/at/heinz/deviceai"
test = repo / "app/src/test/java/at/heinz/deviceai/cognitive"
names = ["cognitive/LiveComputerFrames.kt", "cognitive/LiveMonitorSubmission.kt",
         "cognitive/LiveComputerActions.kt", "cognitive/LiveScrollEvidence.kt",
         "cognitive/VerifiedNodeClick.kt", "cognitive/LiveSearchSemantics.kt",
         "cognitive/LiveComposeSemantics.kt", "ToolExecutionReceiptStore.kt",
         "ToolRegistry.kt", "AgentProtocol.kt"]
tests = ["LiveRenderedActionSourceTest", "LiveMonitorSubmissionTest",
         "LiveComputerFramesTest", "LiveComputerActionsTest"]
sources = [main / name for name in names] + [test / (name + ".kt") for name in tests]
extra = ["avatar/AvatarContract.kt", "avatar/AvatarDirector.kt", "avatar/AvatarSentenceAccentPolicy.kt",
         "avatar/ComputerWorkAvatarBridge.kt", "cognitive/CockpitWorkEvent.kt", "cockpit/CockpitAnimationEvent.kt",
         "cockpit/CockpitContracts.kt", "cockpit/CockpitTimeline.kt", "avatar3d/CockpitStageFrame.kt",
         "ui/JarvisWindowGaze.kt", "ui/ComputerMonitorGaze.kt"]
extra_tests = ["avatar/ComputerWorkAttentionTest", "avatar/AvatarDirectorTest", "ui/ComputerMonitorGazeTest"]
sources += [main / name for name in extra] + [test.parent / (name + ".kt") for name in extra_tests]
declarations = [("CommandInterpreter.kt", r"enum class Risk \{[^}]*\}"),
                ("AgentToolLayer.kt", r"enum class ToolExecutionStatus[^\n]*")]
types = "package at.heinz.deviceai\n" + "\n".join(
    re.search(pattern, (main / name).read_text()).group(0) for name, pattern in declarations)
(out / "ToolTypes.kt").write_text(types + "\n")
manifest = {str(path.relative_to(repo)): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in sources + [main / name for name, _ in declarations]}
sources.append(out / "ToolTypes.kt")
deps = args.deps.resolve()
runtime = ":".join(str(deps / name) for name in ["kotlin-stdlib-2.0.20.jar",
                  "junit-4.13.2.jar", "hamcrest-core-1.3.jar"])
runtime += ":" + str(args.android_jar.resolve())
java = ["java", "-Xmx700m", "-XX:ActiveProcessorCount=2"]
command = java + ["-cp", str(deps / "*"), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
                 "-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", runtime,
                 "-d", str(out / "tests.jar")] + list(map(str, sources))
compile_result = subprocess.run(command, text=True, capture_output=True)
(out / "compile.txt").write_text(compile_result.stdout + compile_result.stderr)
print(compile_result.stdout + compile_result.stderr)
result = {"source_sha": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip(),
          "source_files": manifest, "compile_exit": compile_result.returncode,
          "android_device": "NOT_RUN"}
exit_code = compile_result.returncode
if not exit_code:
    execution = subprocess.run(java + ["-cp", str(out / "tests.jar") + ":" + runtime,
        "org.junit.runner.JUnitCore"] + ["at.heinz.deviceai.cognitive." + name for name in tests] +
        ["at.heinz.deviceai." + name.replace("/", ".") for name in extra_tests],
        text=True, capture_output=True)
    (out / "junit.txt").write_text(execution.stdout + execution.stderr)
    print(execution.stdout + execution.stderr)
    result["junit_exit"] = exit_code = execution.returncode
(out / "source.json").write_text(json.dumps(result, indent=2) + "\n")
raise SystemExit(exit_code)
