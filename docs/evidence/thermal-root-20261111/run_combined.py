#!/usr/bin/env python3
"""Compile thermal changes and run focused host regressions."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

repo = Path(os.environ.get("THERMAL_REPO", Path(__file__).resolve().parents[3]))
out = Path(sys.argv[1]).resolve()
out.mkdir(parents=True, exist_ok=True)
cache = Path(os.environ.get("CONTINUUM_KOTLIN_CACHE", "/workspace/scratch/c9f74e2967e3/gradle-home/caches/modules-2/files-2.1"))
compiled = Path(os.environ.get("CONTINUUM_COMPILED_APP", "/workspace/scratch/6139925ecf70/device-ai/app/build/tmp/kotlin-classes/debug"))
java = os.environ.get("CONTINUUM_JAVA", "/workspace/scratch/6139925ecf70/toolchain/jdk-21.0.13+9/bin/java")
litert = Path(os.environ.get("CONTINUUM_LITERT_JAR", "/workspace/scratch/c9f74e2967e3/gradle-home/caches/8.11.1/transforms/f69ea6ce302d275edfe54d72a778205e/transformed/litertlm-android-0.18.0/jars/classes.jar"))


def jar(group, name, version):
    return next((cache / group / name / version).glob("*/*.jar"))


compiler = [jar("org.jetbrains.kotlin", name, "2.4.0") for name in (
    "kotlin-compiler-embeddable", "kotlin-stdlib", "kotlin-script-runtime", "kotlin-reflect")]
compiler += [jar("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.8.0"),
             jar("org.jetbrains", "annotations", "23.0.0")]
libraries = [compiled, litert, jar("junit", "junit", "4.13.2"),
             jar("org.hamcrest", "hamcrest-core", "1.3"), jar("org.json", "json", "20240303")]
classpath = ":".join(map(str, [*libraries, *compiler]))
classes = ["at.heinz.deviceai." + name for name in (
    "localmodel.LocalModelLoadSourceTest", "localmodel.LocalModelCatalogTest",
    "localmodel.LocalModelImportStreamTest", "localmodel.LocalModelReloadLifecycleTest",
    "localmodel.LocalModelThermalGuardTest", "localmodel.LocalModelGenerationPolicyTest",
    "localmodel.LocalModelComputeBudgetTest", "learning.ContinuumLearningStatusReuseTest",
    "learning.ContinuumLearningCoreTest", "learning.TrainableSubwordEncoderTest",
    "learning.ContinuumLearningStatusCacheReviewTest", "ui.JarvisLightStreamReuseTest",
    "ui.JarvisLivingSceneTest", "ui.JarvisThermalVisualContractTest",
    "ui.JarvisRenderQualityTest", "ui.JarvisRenderQualityAdaptiveTest")]
sources = [repo / "app/src/main/java/at/heinz/deviceai" / (name + ".kt") for name in (
    "localmodel/LocalModelCatalog", "localmodel/LiteRtLmLocalModelRuntime",
    "localmodel/LocalModelComputeBudget", "localmodel/LocalModelGenerationPolicy",
    "localmodel/LocalModelThermalGuard", "learning/ContinuumLearningCore",
    "learning/TrainableSubwordEncoder", "ui/ContinuumPainter", "ui/JarvisSceneFills",
    "ui/JarvisHeadAura", "ui/JarvisEnergyFlows", "ui/JarvisRenderQuality",
    "ui/JarvisComputePhasePolicy", "ui/AvatarEssentialMotionPolicy")]
sources += [repo / "app/src/test/java" / (name.replace(".", "/") + ".kt") for name in classes]
sources += [repo / "tools/thermal-render/FlowWorkProbe.kt"]
manifest = {
    "source_head": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip(),
    "source_tree": subprocess.check_output(["git", "rev-parse", "HEAD^{tree}"], cwd=repo, text=True).strip(),
    "scope": "Focused JVM compile and tests; cached unchanged dependencies; not Android APK or Fold7 execution",
    "compiled_dependencies": str(compiled), "test_classes": classes,
    "source_sha256": {str(p.relative_to(repo)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources},
    "dependency_sha256": {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in [*compiler, *libraries[1:]]},
    "device_thermal": "NOT_RUN",
}


def run(label, command):
    with (out / (label + ".log")).open("w") as stream:
        result = subprocess.run(command, cwd=repo, stdout=stream, stderr=subprocess.STDOUT)
    manifest[label] = {"command": command, "exit_code": result.returncode}
    (out / "inputs.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(label.upper(), result.returncode, flush=True)
    print((out / (label + ".log")).read_text(), flush=True)
    return result.returncode


rc = run("compile", [java, "-Xmx512m", "-XX:ActiveProcessorCount=1", "-cp",
    ":".join(map(str, compiler)), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
    "-no-stdlib", "-no-reflect", "-jvm-target", "17", "-Xfriend-paths=" + str(compiled),
    "-classpath", classpath, "-d", str(out / "classes"), *map(str, sources)])
if rc:
    raise SystemExit(rc)
runtime = str(out / "classes") + ":" + classpath
rc = run("junit", [java, "-Xmx512m", "-XX:ActiveProcessorCount=1", "-cp", runtime,
    "org.junit.runner.JUnitCore", *classes])
probe_rc = run("draw_work", [java, "-Xmx256m", "-XX:ActiveProcessorCount=1", "-cp", runtime,
    "thermal.render.FlowWorkProbeKt", str(out / "classes"), "70"])
digest_line = next((line for line in (out / "draw_work.log").read_text().splitlines()
                    if line.startswith("DRAW_COMMAND_SHA256=")), "")
manifest["draw_command_sha256"] = digest_line.partition("=")[2]
manifest["draw_matches_baseline"] = manifest["draw_command_sha256"] == "33f640d7a6108c7245cf355eeb3e62131ad327d8fb11944e9ad2d73dd82dccd7"
(out / "inputs.json").write_text(json.dumps(manifest, indent=2) + "\n")
raise SystemExit(0 if rc == 0 and probe_rc == 0 and manifest["draw_matches_baseline"] else 1)
