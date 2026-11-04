#!/usr/bin/env python3
"""Bounded offline checks and measurements."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
PUBLIC = "73b64ec299fd314dfdf07835fc7d8fc0d74b51a2"
LOCAL = "2f20c19b7e37197a6accac0c226b5401e25f1f99"
TREE = "f6297e08d03eae74283f43b1ce5abf80c5f7e666"
SOURCE = "app/src/main/java/at/heinz/deviceai/learning/lm"
PACKAGE = "at.heinz.deviceai.learning.lm"
TESTS = ["LmCoreTest", "LmSamplingTest", "LmSamplingWeightsTest"]
TOOLS = ROOT / "tools/tuning-r3-20261104"


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run(args, path):
    with path.open("w") as log:
        result = subprocess.run(args, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
    print(f"{path.name}: exit {result.returncode}", flush=True)
    if result.returncode:
        print(path.read_text()[-16000:], flush=True)
        raise SystemExit(result.returncode)


def check(out, lib, candidate):
    refs = []
    for ref in (PUBLIC, LOCAL):
        result = subprocess.run(["git", "rev-parse", "--verify", ref + "^{tree}"], cwd=ROOT, capture_output=True, text=True)
        refs.append({"commit": ref, "available": result.returncode == 0})
        if result.returncode == 0:
            assert result.stdout.strip() == TREE
            baseline = ref
            break
    else:
        raise RuntimeError("Fetch the public baseline")
    sources = []
    hashes = {}
    locations = {}
    archive = ROOT / "docs/evidence/tuning-r3-20261104/m3a"
    archived_hashes = json.loads((archive / "manifest.json").read_text())["sourceSha256"] if candidate == "m3a" else None
    for variant in ("baseline", "candidate"):
        target = out / variant
        target.mkdir(parents=True, exist_ok=True)
        for original in sorted((ROOT / SOURCE).glob("*.kt")):
            selected = archive / "candidate-LmBpeTokenizer.kt" if candidate == "m3a" and original.name == "LmBpeTokenizer.kt" else original
            data = subprocess.check_output(["git", "show", f"{baseline}:{SOURCE}/{original.name}"], cwd=ROOT) if variant == "baseline" else selected.read_bytes()
            if variant == "candidate":
                locations[original.name] = str(selected)
                if archived_hashes is not None:
                    assert hashlib.sha256(data).hexdigest() == archived_hashes[f"candidate/{original.name}"], "Archive companion source mismatch"
            hashes[f"{variant}/{original.name}"] = hashlib.sha256(data).hexdigest()
            text = data.decode()
            if variant == "baseline":
                assert text.count("package " + PACKAGE + "\n") == 1
                text = text.replace("package " + PACKAGE + "\n", "package " + PACKAGE + ".reference\n", 1)
            path = target / original.name
            path.write_text(text)
            sources.append(path)
    assert len(sources) == 12
    tests = [ROOT / f"app/src/test/java/{PACKAGE.replace('.', '/')}/{name}.kt" for name in TESTS]
    tests += [TOOLS / name for name in ("LmTuningR3Checks.kt", "LmTuningR3Benchmark.kt", "LmHexR3Checks.kt", "LmHexR3Benchmark.kt")]
    cp = os.pathsep.join(str(lib / name) for name in ["kotlin-stdlib-2.0.20.jar", "junit-4.13.2.jar", "hamcrest-core-1.3.jar"])
    for path in cp.split(os.pathsep):
        assert Path(path).is_file(), path
    jar = out / "checks.jar"
    manifest = {"publicBaseline": PUBLIC, "resolvedBaseline": baseline, "baselineTree": TREE, "resolutionAttempts": refs,
                "checkoutHead": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT).decode().strip(),
                "candidateSelection": candidate, "candidateSourceLocations": locations,
                "sourceSha256": hashes, "harnessSha256": {str(p.relative_to(ROOT)): sha(p) for p in tests + [Path(__file__).resolve()]},
                "classpath": cp, "compilerDirectory": str(lib), "packageTransformation": "baseline package declaration only",
                "dependencies": {Path(p).name: sha(Path(p)) for p in cp.split(os.pathsep)}}
    (out / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    command = ["java", "-Xmx768m", "-cp", str(lib / "*"), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
               "-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", cp, *map(str, sources + tests), "-d", str(jar)]
    (out / "compile-command.json").write_text(json.dumps(command, indent=2) + "\n")
    run(command, out / "compile.txt")
    run(["java", "-Xmx512m", "-cp", str(jar) + os.pathsep + cp, "org.junit.runner.JUnitCore",
         *[PACKAGE + "." + name for name in TESTS], PACKAGE + ".LmTuningR3Checks", PACKAGE + ".LmHexR3Checks"], out / "tests.txt")
    manifest["jarSha256"] = sha(jar)
    manifest["status"] = "CHECKS_PASS"
    (out / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")


def measure(out, group, phase):
    manifest = json.loads((out / "manifest.json").read_text())
    assert manifest["status"] == "CHECKS_PASS"
    assert sha(out / "checks.jar") == manifest["jarSha256"]
    for name, expected in manifest["harnessSha256"].items():
        assert sha(ROOT / name) == expected, name
    for name, expected in manifest["sourceSha256"].items():
        if name.startswith("candidate/"):
            assert sha(Path(manifest["candidateSourceLocations"][Path(name).name])) == expected, name
    run(["java", "-Xms256m", "-Xmx512m", "-cp", str(out / "checks.jar") + os.pathsep + manifest["classpath"],
         PACKAGE + (".LmHexR3Benchmark" if group == "hex" else ".LmTuningR3Benchmark"), group, phase], out / f"{phase}-{group}.jsonl")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("check", "measure"))
    parser.add_argument("output", type=Path)
    parser.add_argument("--lib", type=Path)
    parser.add_argument("--candidate", choices=("checkout", "m3a"), default="checkout")
    parser.add_argument("--group", choices=("all", "nll", "distribution", "tokenizer", "hex"), default="all")
    parser.add_argument("--phase", default="baseline")
    args = parser.parse_args()
    out = args.output.resolve()
    assert out != ROOT and ROOT not in out.parents
    out.mkdir(parents=True, exist_ok=True)
    if args.mode == "check":
        assert args.lib is not None
        check(out, args.lib.resolve(), args.candidate)
    else:
        measure(out, args.group, args.phase)
