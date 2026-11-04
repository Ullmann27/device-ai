#!/usr/bin/env python3
"""Offline, selected real Kotlin source graph; no Gradle, APK or model inference."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[2]
BASELINE = "e101e393dd6c80803ac9d9ac91fccd9812c4a18d"
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
    old = subprocess.check_output(["git", "show", f"{BASELINE}:{TARGET}"], cwd=ROOT).decode()
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
    baseline_source.write_text(imports + "\n\nclass BaselineAiContextCompiler(" + old_class)
    sources = [ROOT / name for name in (ROOT / "tools/tuning-memory-20261104/sources.txt").read_text().splitlines()]
    sources += [baseline_source, ROOT / "tools/tuning-memory-20261104/ContextCompilerParity.kt"]
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
        "worktreeHead": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT).decode().strip(),
        "baselineCompilerSha256": sha(out / "original-AiContextCompiler.kt"),
        "candidateCompilerSha256": sha(ROOT / TARGET),
        "sources": {str(path): sha(path) for path in sources},
        "dependencies": {str(path): sha(path) for path in dict.fromkeys(dependencies_paths + compiler_paths)},
        "runtimeClasspath": str(out / "classes") + os.pathsep + dependencies,
        "sourceGraphUsesTestStubs": False,
        "androidFrameworkExecuted": False,
        "baselineTransformation": "Shared declarations verified unchanged; original compiler class name only renamed",
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


def benchmark(out):
    manifest = json.loads((out / "manifest.json").read_text())
    if json.loads((out / "parity.json").read_text())["status"] != "PASS":
        raise RuntimeError("Parity must pass before measuring")
    for group in ("sources", "dependencies"):
        for path, expected in manifest[group].items():
            if sha(Path(path)) != expected:
                raise RuntimeError(f"Changed after parity: {path}")
    (out / "load-before.txt").write_text(Path("/proc/loadavg").read_text())
    command(["java", "-Xms256m", "-Xmx512m", "-cp", manifest["runtimeClasspath"],
             "at.heinz.deviceai.cognitive.ContextCompilerParity", "benchmark"], out / "benchmark.json")
    (out / "load-after.txt").write_text(Path("/proc/loadavg").read_text())
    report = json.loads((out / "benchmark.json").read_text())
    if len(report["workloads"]) != 4:
        raise RuntimeError("Incomplete benchmark")
    print("Four complete measured workloads; raw samples in benchmark.json", flush=True)


def control(out):
    manifest = json.loads((out / "manifest.json").read_text())
    for group in ("sources", "dependencies"):
        for path, expected in manifest[group].items():
            if sha(Path(path)) != expected:
                raise RuntimeError(f"Changed after parity: {path}")
    source = ROOT / "tools/tuning-memory-20261104/ContextCompilerControlCheck.kt"
    compiler = os.environ["CONTINUUM_KOTLIN_COMPILER_CP"]
    args = ["java", "-Xmx768m", "-cp", compiler, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
            "-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", manifest["runtimeClasspath"],
            "-d", str(out / "control-classes"), str(source)]
    (out / "control-manifest.json").write_text(json.dumps({
        "source": str(source), "sha256": sha(source), "compileCommand": args,
        "baselineCompilerSha256": manifest["baselineCompilerSha256"],
        "candidateCompilerSha256": manifest["candidateCompilerSha256"],
        "reason": "One prespecified longer minimal-control diagnostic after noisy primary wall timings",
    }, indent=2) + "\n")
    command(args, out / "control-compile.log")
    (out / "control-load-before.txt").write_text(Path("/proc/loadavg").read_text())
    command(["java", "-Xms256m", "-Xmx512m", "-cp",
             str(out / "control-classes") + os.pathsep + manifest["runtimeClasspath"],
             "at.heinz.deviceai.cognitive.ContextCompilerControlCheck"], out / "control.json")
    (out / "control-load-after.txt").write_text(Path("/proc/loadavg").read_text())
    if len(json.loads((out / "control.json").read_text())["samples"]) != 20:
        raise RuntimeError("Incomplete control diagnostic")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("check", "benchmark", "control"))
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    output = args.output.resolve()
    if output == ROOT or ROOT in output.parents:
        raise SystemExit("Use an external output directory for host classes/evidence")
    output.mkdir(parents=True, exist_ok=True)
    if args.mode == "check":
        check(output)
    elif args.mode == "benchmark":
        benchmark(output)
    else:
        control(output)
