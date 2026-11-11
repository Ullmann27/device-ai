#!/usr/bin/env python3
"""Compile actual source snapshots and run the fixed native component probes."""
import argparse
import hashlib
import json
from pathlib import Path
import resource
import subprocess
import time

from analyze_context import analyze

COGNITIVE = """LocalConversationContext MemoryEcology ResearchDialogueHandoff
FreshKnowledgeNeed InternetConsentPolicy ResearchEngine BoundedResearchTextSanitizer
JarvisUnifiedEvidenceContract ResearchQuestionFocus ResearchNegationSignals Research
PinnedResearchHttpsConnection ContinuumState ComputeFabric ContinuumSeed
ProofCarryingMemory SelfModel NetworkTurnConstraint PublicKnowledgeTopicRouter
CognitiveCore CognitiveGenome IdentityState BehaviorProfile BehaviorUpdate
IdentityUpdateValidator IdentityDrift AdaptiveIdentity OwnerChatIntentRouter
LocalClockIntent""".split()
ROOT = "AgentToolLayer ToolRegistry AgentProtocol ToolExecutionReceiptStore ToolExecutionStartLatch CommandInterpreter".split()
NATIVE = "PersonalVoiceCorrectionBridge LocalConversationVoiceAdapter PersonalVoiceLearningStore AndroidPersonalVoiceStore".split()
DEPENDENCIES = {
    "android-all-15-robolectric-12650502.jar": "6c0670454f6fcee9f1b17aa393e81d71bf26e7e6ae7c5777ed3924b062191d5b",
    "okio-jvm-3.6.0.jar": "67543f0736fc422ae927ed0e504b98bc5e269fda0d3500579337cb713da28412",
    "json-20240303.jar": "3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed"
}


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo-root", required=True, type=Path)
    parser.add_argument("--native-root", type=Path)
    parser.add_argument("--kotlin-home", required=True, type=Path)
    parser.add_argument("--dependencies", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    repo = args.repo_root.resolve()
    ev = repo / "experimental/personal-voice-20261111/evaluation"
    native = (args.native_root or repo / "experimental/personal-voice-20261111/native").resolve()
    frozen = json.loads((ev / "component-frozen-inputs.json").read_text())
    if any(sha(repo / p) != h for p, h in frozen["hashes"].items()):
        raise ValueError("frozen component protocol/harness changed")
    jars = [args.dependencies.resolve() / name for name in DEPENDENCIES]
    for path in jars:
        if sha(path) != DEPENDENCIES[path.name]:
            raise ValueError(f"dependency identity mismatch: {path.name}")
    # The OkHttp version is the existing app's pinned dependency. Identity is
    # bound from the actual original compile registration rather than invented.
    okhttp = args.dependencies.resolve() / "okhttp-4.12.0.jar"
    known = json.loads((ev / "component-results-v1/compile-attempt5/registration.json").read_text())["dependencies"]
    expected_okhttp = [h for p, h in known.items() if Path(p).name == okhttp.name]
    if len(expected_okhttp) != 1 or sha(okhttp) != expected_okhttp[0]:
        raise ValueError("OkHttp identity mismatch")
    jars.append(okhttp)
    sources = [repo / f"app/src/main/java/at/heinz/deviceai/cognitive/{n}.kt" for n in COGNITIVE]
    sources += [repo / f"app/src/main/java/at/heinz/deviceai/{n}.kt" for n in ROOT]
    sources += [native / f"{n}.kt" for n in NATIVE]
    sources += [ev / "ContextCorrectionEvaluation.kt"]
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    snapshot = out / "sources"
    snapshot.mkdir()
    mapping = {}
    for source in sources:
        target = snapshot / source.name
        with target.open("xb") as stream:
            stream.write(source.read_bytes())
        mapping[str(target)] = {"original_path": str(source), "sha256": sha(target)}
    compiler = args.kotlin_home.resolve() / "bin/kotlinc"
    stdlib = args.kotlin_home.resolve() / "lib/kotlin-stdlib.jar"
    compile_command = [str(compiler), "-jvm-target", "17", "-classpath", ":".join(map(str, jars)), "-d", str(out / "classes"), *mapping]
    run_command = ["java", "-Xmx768m", "-XX:ActiveProcessorCount=1", "-cp", ":".join([str(out / "classes"), str(stdlib), *map(str, jars)]),
                   "at.heinz.deviceai.research.voice.ContextCorrectionEvaluation"]
    registration = {"compile_command": compile_command, "run_command": run_command,
                    "source_mapping": mapping, "dependency_hashes": {str(p): sha(p) for p in jars + [stdlib]},
                    "frozen_manifest_sha256": sha(ev / "component-frozen-inputs.json"),
                    "scope": "actual native context components and Android factory compile only; not device/ASR/full-app ABC"}
    (out / "registration.json").write_text(json.dumps(registration, indent=2) + "\n")
    with (out / "compile.stdout.txt").open("w") as stdout, (out / "compile.stderr.txt").open("w") as stderr:
        compiled = subprocess.run(compile_command, stdout=stdout, stderr=stderr, timeout=180)
    (out / "compile-exit.json").write_text(json.dumps({"exit_code": compiled.returncode}) + "\n")
    if compiled.returncode != 0:
        raise RuntimeError("compile failed; receipts retained, tests NOT_RUN")
    before = resource.getrusage(resource.RUSAGE_CHILDREN)
    started = time.monotonic()
    with (out / "stdout.tsv").open("w") as stdout, (out / "stderr.txt").open("w") as stderr:
        run = subprocess.run(run_command, stdout=stdout, stderr=stderr, timeout=60)
    after = resource.getrusage(resource.RUSAGE_CHILDREN)
    receipt = {"exit_code": run.returncode, "wall_seconds": time.monotonic() - started,
               "user_seconds": after.ru_utime - before.ru_utime, "system_seconds": after.ru_stime - before.ru_stime,
               "max_rss_kib_all_children_including_compile": after.ru_maxrss,
               "inputs_unchanged": all(sha(p) == item["sha256"] for p, item in mapping.items()),
               "stdout_sha256": sha(out / "stdout.tsv"), "stderr_sha256": sha(out / "stderr.txt")}
    (out / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
    if run.returncode != 0 or not receipt["inputs_unchanged"]:
        raise RuntimeError("component run failed or source snapshot changed")
    result = analyze(out / "stdout.tsv")
    (out / "analysis.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
