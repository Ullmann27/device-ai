#!/usr/bin/env python3
"""Measure the existing observation loop."""
import argparse
import csv
import hashlib
import json
from pathlib import Path
import re
import subprocess

BASE = "5f87bdc610944b93d3173a83c5beeea315abeb66"
MAIN = "app/src/main/java/at/heinz/deviceai/"
TEST = "app/src/test/java/at/heinz/deviceai/"
SOURCES = [
    "cockpit/CockpitAnimationEvent.kt", "cognitive/CockpitWorkEvent.kt",
    "cognitive/LiveActionCockpitAdapter.kt", "cognitive/OwnerComputerIntentParser.kt",
    "cognitive/OwnerComputerWorkflow.kt", "cognitive/OwnerComputerTaskGrant.kt",
    "cognitive/LiveComputerActions.kt", "cognitive/LiveScrollEvidence.kt",
    "cognitive/LiveSearchSemantics.kt", "cognitive/VerifiedNodeClick.kt",
    "ToolExecutionReceiptStore.kt", "AgentProtocol.kt", "ToolRegistry.kt",
    "avatar/AvatarContract.kt", "avatar/AvatarDirector.kt", "avatar/AvatarSentenceAccentPolicy.kt",
    "avatar/ComputerWorkAvatarBridge.kt", "avatar/ComputerWorkCuePlayer.kt",
]
TESTS = ["cognitive/ComputerWorkObservationExperimentTest.kt", "cognitive/OwnerComputerWorkflowTest.kt",
         "cognitive/LiveComputerActionsTest.kt", "cognitive/CockpitWorkEventTest.kt", "avatar/AvatarDirectorTest.kt"]
DECLARATIONS = ["cockpit/CockpitContracts.kt", "CommandInterpreter.kt", "AgentToolLayer.kt"]
GROUPS = {
    "experiment": ["cognitive.ComputerWorkObservationExperimentTest"],
    "neighbors": ["cognitive.OwnerComputerWorkflowTest", "cognitive.LiveComputerActionsTest",
                  "cognitive.CockpitWorkEventTest", "avatar.AvatarDirectorTest", "avatar.AvatarPortedPartsTest"],
}

def sha(data):
    return hashlib.sha256(data).hexdigest()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", required=True, type=Path)
    parser.add_argument("--cache", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()
    repo, cache, out = args.repo.resolve(), args.cache.resolve(), args.out.resolve()
    out.mkdir(parents=True, exist_ok=False)
    frozen = out / "frozen"
    frozen.mkdir()
    java_tmp = frozen / "java-tmp"
    java_tmp.mkdir()

    def git(*command):
        return subprocess.check_output(["git", *command], cwd=repo)

    def artifact(group, name, version):
        values = list((cache / group / name / version).glob("*/" + name + "-" + version + ".jar"))
        assert len(values) == 1, (group, name, version)
        return values[0]

    compiler = [artifact("org.jetbrains.kotlin", name, "2.4.0") for name in
                ["kotlin-compiler-embeddable", "kotlin-build-tools-api", "kotlin-stdlib",
                 "kotlin-script-runtime", "kotlin-daemon-embeddable"]]
    compiler += [artifact("org.jetbrains.kotlin", "kotlin-reflect", "1.6.10"),
                 artifact("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.8.0"),
                 artifact("org.jetbrains", "annotations", "23.0.0")]
    runtime = [artifact("org.jetbrains.kotlin", "kotlin-stdlib", "2.4.0"),
               artifact("junit", "junit", "4.13.2"), artifact("org.hamcrest", "hamcrest-core", "1.3")]
    metadata = {"head_before": git("rev-parse", "HEAD").decode().strip(), "production_base": BASE,
                "compiler": "Kotlin 2.4.0", "max_heap_mb": 512, "active_processors": 1,
                "clock": "synthetic_monotonic", "original_type_declarations_extracted": 6,
                "gradle": "NOT_RUN", "android_device": "NOT_RUN", "fold7": "NOT_RUN"}
    paths = [MAIN + name for name in SOURCES + DECLARATIONS] + [TEST + name for name in TESTS]
    manifest = []
    for path in paths:
        data = (repo / path).read_bytes()
        target = frozen / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        item = {"path": path, "frozen_sha256": sha(data)}
        if path.startswith(MAIN):
            item["base_sha256"] = sha(git("show", BASE + ":" + path))
            assert item["frozen_sha256"] == item["base_sha256"], "Production changed: " + path
        manifest.append(item)
    contracts = (frozen / MAIN / DECLARATIONS[0]).read_text()
    patterns = [r"data class CalcStep[^\n]*", r"enum class ChartKind[^\n]*",
                r"enum class TransitionPattern[^\n]*", r"enum class AlarmKind \{[^}]*\}"]
    values = [re.search(pattern, contracts).group(0) for pattern in patterns]
    cockpit_types = frozen / "CockpitTypes.kt"
    cockpit_types.write_text("package at.heinz.deviceai.cockpit\n\n" + "\n\n".join(values) + "\n")
    risk = re.search(r"enum class Risk \{[^}]*\}", (frozen / MAIN / DECLARATIONS[1]).read_text()).group(0)
    status = re.search(r"enum class ToolExecutionStatus[^\n]*", (frozen / MAIN / DECLARATIONS[2]).read_text()).group(0)
    tool_types = frozen / "ToolTypes.kt"
    tool_types.write_text("package at.heinz.deviceai\n\n" + risk + "\n\n" + status + "\n")
    sources = [frozen / MAIN / name for name in SOURCES] + [frozen / TEST / name for name in TESTS]
    sources += [cockpit_types, tool_types]
    java = ["java", "-XX:ActiveProcessorCount=1", "-XX:-UsePerfData", "-Xmx512m",
            "-Djava.io.tmpdir=" + str(java_tmp)]
    classpath = ":".join(map(str, runtime))
    command = [*java, "-cp", ":".join(map(str, compiler)), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
               "-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", classpath,
               "-d", str(frozen / "classes"), *map(str, sources)]
    result = subprocess.run(command, text=True, capture_output=True)
    metadata["compile_exit"] = result.returncode
    (out / "compile.txt").write_text(result.stdout + result.stderr)
    print("COMPILE_EXIT", result.returncode, flush=True)
    if result.returncode:
        print(result.stdout + result.stderr, flush=True)
    else:
        for name, tests in GROUPS.items():
            command = [*java, "-Dcontinuum.experiment.output=" + str(out / "metrics.csv"),
                       "-cp", str(frozen / "classes") + ":" + classpath, "org.junit.runner.JUnitCore",
                       *("at.heinz.deviceai." + test for test in tests)]
            result = subprocess.run(command, text=True, capture_output=True)
            metadata[name + "_exit"] = result.returncode
            (out / (name + "-junit.txt")).write_text(result.stdout + result.stderr)
            print(name.upper() + "_EXIT", result.returncode, flush=True)
            print(result.stdout + result.stderr, flush=True)
    if (out / "metrics.csv").exists():
        with (out / "metrics.csv").open() as stream:
            rows = list(csv.DictReader(stream))
        summary = {"trials": len(rows), "arms": {}, "latencies_are_synthetic": True}
        for arm in sorted({row["arm"] for row in rows}):
            group = [row for row in rows if row["arm"] == arm]
            summary["arms"][arm] = {"trials": len(group), **{
                name: sum(int(row[name]) for row in group)
                for name in ["native_inputs", "dispatched", "observed", "verified", "completed", "early_success", "avatar_inputs", "cues"]},
                "result_states": sorted({row["result"] for row in group}),
                "final_stages": sorted({row["final_stage"] for row in group})}
        (out / "metrics-summary.json").write_text(json.dumps(summary, indent=2) + "\n")
        print(json.dumps(summary), flush=True)
    for item in manifest:
        item["after_sha256"] = sha((repo / item["path"]).read_bytes())
        item["unchanged"] = item["frozen_sha256"] == item["after_sha256"]
    metadata["head_after"] = git("rev-parse", "HEAD").decode().strip()
    metadata["sources_unchanged"] = all(item["unchanged"] for item in manifest)
    metadata["java"] = subprocess.run(["java", "-version"], text=True, capture_output=True).stderr.strip()
    (out / "result.json").write_text(json.dumps(metadata, indent=2) + "\n")
    (out / "source-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    (out / "tool-artifacts.json").write_text(json.dumps([
        {"name": path.name, "sha256": sha(path.read_bytes())} for path in compiler + runtime
    ], indent=2) + "\n")
    return int(not metadata["sources_unchanged"] or any(value for key, value in metadata.items() if key.endswith("_exit")))

if __name__ == "__main__":
    raise SystemExit(main())
