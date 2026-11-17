#!/usr/bin/env python3
"""Offline production-source work probe."""
import argparse
import hashlib
import json
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--gradle-cache", required=True, type=Path)
parser.add_argument("--compiled-dependencies", required=True, type=Path)
parser.add_argument("--out", required=True, type=Path)
parser.add_argument("--expected-digest")
args = parser.parse_args()
repo = Path(__file__).resolve().parents[2]
out = args.out.resolve()
out.mkdir(parents=True, exist_ok=True)


def jar(group, name, version):
    return next((args.gradle_cache / group / name / version).glob("*/*.jar"))


compiler = [jar("org.jetbrains.kotlin", name, "2.4.0") for name in (
    "kotlin-compiler-embeddable", "kotlin-stdlib", "kotlin-script-runtime", "kotlin-reflect")]
compiler += [jar("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.8.0"),
             jar("org.jetbrains", "annotations", "23.0.0")]
libraries = [args.compiled_dependencies,
             jar("junit", "junit", "4.13.2"), jar("org.hamcrest", "hamcrest-core", "1.3")]
classpath = ":".join(map(str, [*libraries, *compiler]))
source_dir = repo / "app/src/main/java/at/heinz/deviceai/ui"
test_dir = repo / "app/src/test/java/at/heinz/deviceai/ui"
sources = [source_dir / name for name in (
    "ContinuumPainter.kt", "JarvisSceneFills.kt", "JarvisHeadAura.kt", "JarvisEnergyFlows.kt")]
sources += [test_dir / "JarvisLightStreamReuseTest.kt", test_dir / "JarvisLivingSceneTest.kt",
            repo / "tools/thermal-render/FlowWorkProbe.kt"]
manifest = {
    "source_head": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip(),
    "status": "FOCUSED_HOST_ONLY",
    "compiled_dependencies": str(args.compiled_dependencies),
    "source_sha256": {str(p.relative_to(repo)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources},
    "dependency_sha256": {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in [*compiler, *libraries[1:]]},
    "device_thermal": "NOT_RUN",
}


def run(label, command):
    result = subprocess.run(command, cwd=repo, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    (out / (label + ".log")).write_text(result.stdout)
    manifest[label] = {"command": command, "exit_code": result.returncode}
    print(label.upper(), result.returncode, result.stdout, sep="\n", flush=True)
    return result


compiled = run("compile", ["java", "-Xmx512m", "-XX:ActiveProcessorCount=1", "-cp",
    ":".join(map(str, compiler)), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
    "-no-stdlib", "-no-reflect", "-jvm-target", "17",
    "-Xfriend-paths=" + str(args.compiled_dependencies), "-classpath", classpath,
    "-d", str(out / "classes"), *map(str, sources)])
if compiled.returncode:
    (out / "inputs.json").write_text(json.dumps(manifest, indent=2) + "\n")
    raise SystemExit(compiled.returncode)
runtime = str(out / "classes") + ":" + classpath
junit = run("junit", ["java", "-Xmx256m", "-XX:ActiveProcessorCount=1", "-cp", runtime,
    "org.junit.runner.JUnitCore", "at.heinz.deviceai.ui.JarvisLightStreamReuseTest",
    "at.heinz.deviceai.ui.JarvisLivingSceneTest"])
probe = run("work", ["java", "-Xmx256m", "-XX:ActiveProcessorCount=1", "-cp", runtime,
    "thermal.render.FlowWorkProbeKt", str(out / "classes"), "70"])
digest_line = next((line for line in probe.stdout.splitlines() if line.startswith("DRAW_COMMAND_SHA256=")), "")
digest = digest_line.partition("=")[2]
manifest["draw_command_sha256"] = digest
matches = args.expected_digest is None or args.expected_digest == digest
manifest["matches_expected_commands"] = matches
(out / "inputs.json").write_text(json.dumps(manifest, indent=2) + "\n")
raise SystemExit(0 if junit.returncode == 0 and probe.returncode == 0 and matches else 1)
