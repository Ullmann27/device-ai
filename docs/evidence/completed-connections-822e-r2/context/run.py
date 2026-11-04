#!/usr/bin/env python3
"""Offline, selected real Kotlin source graph; no Gradle, APK or model inference."""
import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import subprocess


HERE = Path(__file__).resolve().parent
ROOT = Path(subprocess.check_output(["git", "rev-parse", "--show-toplevel"], cwd=HERE).decode().strip())
BASELINE = "1a5302c504a78a50d5e64ca727f2915529bf2b06"
BASELINE_SOURCE_BLOB = "f42f526cefaa96295205f161241fbc8035f27c01"
TARGET = "app/src/main/java/at/heinz/deviceai/cognitive/AiContextCompiler.kt"
TESTS = ["AiContextCompilerTest", "AiContextByteBudgetTest", "AiContextIntegrityTest",
         "LocalPromptSizeAndStabilityTest"]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def command(args, destination):
    with destination.open("w") as log:
        result = subprocess.run(args, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
    print(f"{destination.name}: exit {result.returncode}", flush=True)
    if result.returncode:
        print(destination.read_text(), flush=True)
        raise SystemExit(result.returncode)


def check(out):
    compiler = os.environ["CONTINUUM_KOTLIN_COMPILER_CP"]
    dependencies = os.environ["CONTINUUM_KOTLIN_DEPENDENCY_CP"]
    generated = out / "generated"
    generated.mkdir(parents=True, exist_ok=True)
    old = subprocess.check_output(["git", "show", BASELINE_SOURCE_BLOB], cwd=ROOT).decode()
    current = (ROOT / TARGET).read_text()
    marker = "\nclass AiContextCompiler("
    old_header, old_class = old.split(marker, 1)
    current_header, _ = current.split(marker, 1)
    if old_header != current_header:
        raise RuntimeError("The shared input/output/policy declarations changed")
    (out / "original-AiContextCompiler.kt").write_text(old)
    imports = "\n".join(line for line in old_header.splitlines()
                        if line.startswith(("package ", "import ")))
    baseline_source = generated / "BaselineAiContextCompiler.kt"
    baseline_source.write_text(imports + "\n\nclass BaselineAiContextCompiler(" + re.sub(r"\bAiContextCompiler\b", "BaselineAiContextCompiler", old_class))
    sources = [ROOT / name for name in (HERE / "sources.txt").read_text().splitlines()]
    sources += [baseline_source, HERE / "ContextCompilerParity.kt"]
    for name in TESTS:
        original = (ROOT / f"app/src/test/java/at/heinz/deviceai/cognitive/{name}.kt").read_text()
        marker = f"class {name} {{"
        if original.count(marker) != 1:
            raise RuntimeError(f"Unexpected test declaration: {name}")
        renamed = original.replace(marker, f"class Baseline{name} {{", 1)
        renamed = renamed.replace("AiContextCompiler(", "BaselineAiContextCompiler(")
        path = generated / f"Baseline{name}.kt"
        path.write_text(renamed)
        sources.append(path)
    dependencies_paths = [Path(p) for p in dependencies.split(os.pathsep)]
    compiler_paths = [Path(p) for p in compiler.split(os.pathsep)]
    manifest = {
        "baselineCommit": BASELINE,
        "baselineSourceBlob": BASELINE_SOURCE_BLOB,
        "worktreeHead": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT).decode().strip(),
        "baselineCompilerSha256": sha(out / "original-AiContextCompiler.kt"),
        "candidateCompilerSha256": sha(ROOT / TARGET),
        "sources": {str(path): sha(path) for path in sources},
        "dependencies": {str(path): sha(path) for path in dict.fromkeys(dependencies_paths + compiler_paths)},
        "runtimeClasspath": str(out / "classes") + os.pathsep + dependencies,
        "sourceGraphUsesTestStubs": False,
        "androidFrameworkExecuted": False,
        "baselineTransformation": "Shared declarations verified unchanged; compiler class and its self-references renamed, including C1 observer factory",
    }
    (out / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    command(["java", "-version"], out / "java.txt")
    command(["java", "-cp", compiler, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-version"], out / "compiler.txt")
    args = ["java", "-Xmx768m", "-cp", compiler, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
            "-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", dependencies,
            "-d", str(out / "classes"), *map(str, sources)]
    (out / "compile-command.json").write_text(json.dumps(args, indent=2) + "\n")
    command(args, out / "compile.log")
    junit = ["java", "-Xmx512m", "-cp", manifest["runtimeClasspath"], "org.junit.runner.JUnitCore"]
    for prefix in ("Baseline", ""):
        classes = [f"at.heinz.deviceai.cognitive.{prefix}{name}" for name in TESTS]
        command(junit + classes, out / ("baseline-junit.txt" if prefix else "candidate-junit.txt"))
    command(["java", "-Xms256m", "-Xmx512m", "-cp", manifest["runtimeClasspath"],
             "at.heinz.deviceai.cognitive.ContextCompilerParity", "parity"], out / "parity.json")
    print((out / "parity.json").read_text(), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("check",))
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    output = args.output.resolve()
    if output == ROOT or ROOT in output.parents:
        raise SystemExit("Use an external output directory for host classes/evidence")
    output.mkdir(parents=True, exist_ok=True)
    check(output)
