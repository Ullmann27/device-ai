#!/usr/bin/env python3
"""Check the existing workflow connection."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile

SOURCE_FILES = [
    "cockpit/CockpitAnimationEvent.kt",
    "cognitive/CockpitWorkEvent.kt",
    "cognitive/LiveActionCockpitAdapter.kt",
    "cognitive/OwnerComputerIntentParser.kt",
    "cognitive/OwnerComputerWorkflow.kt",
    "cognitive/LiveComputerActions.kt",
    "cognitive/LiveSearchSemantics.kt",
    "cognitive/VerifiedNodeClick.kt",
    "ToolExecutionReceiptStore.kt", "AgentProtocol.kt", "ToolRegistry.kt",
    "avatar/AvatarContract.kt", "avatar/AvatarDirector.kt", "avatar/AvatarSentenceAccentPolicy.kt",
    "avatar/ComputerWorkAvatarBridge.kt", "avatar/ComputerWorkCuePlayer.kt",
]
TEST_FILES = [
    "cognitive/LiveComputerActionsTest.kt", "cognitive/LiveSearchClickTest.kt", "cognitive/CockpitWorkEventTest.kt",
    "cognitive/OwnerComputerWorkflowTest.kt", "cognitive/OwnerComputerIntentParserTest.kt",
    "avatar/AvatarDirectorTest.kt", "cognitive/WorkflowReviewTest.kt",
    "cognitive/ComputerWorkPipelineTest.kt",
]
DECLARATIONS = ["cockpit/CockpitContracts.kt", "CommandInterpreter.kt", "AgentToolLayer.kt"]
GROUPS = {
    "existing": ["cognitive.LiveComputerActionsTest", "cognitive.LiveSearchClickTest", "cognitive.CockpitWorkEventTest",
                 "cognitive.OwnerComputerWorkflowTest", "cognitive.OwnerComputerIntentParserTest",
                 "avatar.AvatarDirectorTest", "avatar.AvatarPortedPartsTest"],
    "regression": ["cognitive.WorkflowReviewTest", "cognitive.ComputerWorkPipelineTest"],
}

def sha(data):
    return hashlib.sha256(data).hexdigest()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--compiler-lib", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    repo, lib, out = args.repo.resolve(), args.compiler_lib.resolve(), args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    def git(*command):
        return subprocess.check_output(["git", *command], cwd=repo, text=True).strip()
    main_root = "app/src/main/java/at/heinz/deviceai/"
    test_root = "app/src/test/java/at/heinz/deviceai/"
    paths = [main_root + x for x in SOURCE_FILES + DECLARATIONS]
    paths.extend(test_root + x for x in TEST_FILES)
    manifest = []
    metadata = {"head_before": git("rev-parse", "HEAD"), "branch": git("branch", "--show-current"),
                "compiler": "Kotlin 2.0.20", "max_heap_mb": 512, "active_processors": 1,
                "extracted_original_type_declarations": 6, "android_gradle": "NOT_RUN", "fold7": "NOT_RUN"}
    cp = ":".join(str(lib / x) for x in ["kotlin-stdlib-2.0.20.jar", "junit-4.13.2.jar", "hamcrest-core-1.3.jar"])
    with tempfile.TemporaryDirectory(prefix="astra55-workflow-connection-") as temp:
        frozen = Path(temp)
        for path in paths:
            data = (repo / path).read_bytes()
            target = frozen / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            manifest.append({"path": path, "frozen_sha256": sha(data)})
        contracts = (frozen / main_root / DECLARATIONS[0]).read_text()
        patterns = [r"data class CalcStep[^\n]*", r"enum class ChartKind[^\n]*",
                    r"enum class TransitionPattern[^\n]*", r"enum class AlarmKind \{[^}]*\}"]
        values = [re.search(pattern, contracts).group(0) for pattern in patterns]
        extra = frozen / "CockpitTypes.kt"
        extra.write_text("package at.heinz.deviceai.cockpit\n\n" + "\n\n".join(values) + "\n")
        risk = re.search(r"enum class Risk \{[^}]*\}", (frozen / main_root / DECLARATIONS[1]).read_text()).group(0)
        status = re.search(r"enum class ToolExecutionStatus[^\n]*", (frozen / main_root / DECLARATIONS[2]).read_text()).group(0)
        types = frozen / "ToolTypes.kt"
        types.write_text("package at.heinz.deviceai\n\n" + risk + "\n\n" + status + "\n")
        sources = [str(frozen / main_root / x) for x in SOURCE_FILES]
        sources.extend(str(frozen / test_root / x) for x in TEST_FILES)
        sources.extend([str(extra), str(types)])
        compiler = ["java", "-XX:ActiveProcessorCount=1", "-XX:-UsePerfData", "-Xmx512m",
                    "-cp", str(lib / "*"), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
                    "-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", cp,
                    "-d", str(frozen / "classes"), *sources]
        compiled = subprocess.run(compiler, text=True, capture_output=True)
        (out / "compile.txt").write_text(compiled.stdout + compiled.stderr)
        metadata["compile_exit"] = compiled.returncode
        print("COMPILE_EXIT", compiled.returncode, flush=True)
        if compiled.returncode:
            print(compiled.stdout + compiled.stderr)
        else:
            for group, tests in GROUPS.items():
                command = ["java", "-XX:ActiveProcessorCount=1", "-XX:-UsePerfData", "-Xmx512m", "-cp",
                           str(frozen / "classes") + ":" + cp, "org.junit.runner.JUnitCore"]
                run = subprocess.run(command + ["at.heinz.deviceai." + test for test in tests],
                                     text=True, capture_output=True)
                metadata[group + "_exit"] = run.returncode
                (out / (group + "-junit.txt")).write_text(run.stdout + run.stderr)
                print(group.upper() + "_EXIT", run.returncode, flush=True)
                print(run.stdout + run.stderr, flush=True)
        for item in manifest:
            item["after_sha256"] = sha((repo / item["path"]).read_bytes())
            item["unchanged_during_check"] = item["frozen_sha256"] == item["after_sha256"]
        metadata["head_after"] = git("rev-parse", "HEAD")
        metadata["sources_unchanged"] = all(row["unchanged_during_check"] for row in manifest)
        (out / "source-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
        (out / "result.json").write_text(json.dumps(metadata, indent=2) + "\n")
        return int(not metadata["sources_unchanged"] or any(value for key, value in metadata.items() if key.endswith("_exit")))

if __name__ == "__main__":
    raise SystemExit(main())
