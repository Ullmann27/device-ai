#!/usr/bin/env python3
"""Verify actual checkpoint/runtime callers."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = "at.heinz.deviceai.learning.lm"


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run(command, log):
    with log.open("w") as stream:
        result = subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT)
    print(f"{log.name}: exit {result.returncode}", flush=True)
    if result.returncode:
        print(log.read_text()[-16000:], flush=True)
        raise SystemExit(result.returncode)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("checked_source_directory", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    source = args.checked_source_directory.resolve()
    out = args.output.resolve()
    assert ROOT != out and ROOT not in out.parents
    assert not out.exists(), "Preserve previous output"
    out.mkdir(parents=True)
    receipt = json.loads((source / "manifest.json").read_text())
    assert receipt["status"] == "CHECKS_PASS"
    sources = []
    for name, expected in receipt["sourceSha256"].items():
        path = source / name
        data = path.read_bytes()
        if name.startswith("baseline/"):
            original = ("package " + PACKAGE + ".reference\n").encode()
            assert data.count(original) == 1
            data = data.replace(original, ("package " + PACKAGE + "\n").encode(), 1)
        else:
            current = Path(receipt["candidateSourceLocations"][path.name])
            assert sha(current) == expected, name
        assert hashlib.sha256(data).hexdigest() == expected, name
        sources.append(path)
    assert len(sources) == 12
    cp = receipt["classpath"]
    for filename in cp.split(os.pathsep):
        path = Path(filename)
        assert sha(path) == receipt["dependencies"][path.name]
    test = ROOT / "app/src/test/java/at/heinz/deviceai/learning/lm/LmRuntimeTest.kt"
    original = subprocess.check_output(["git", "show", receipt["resolvedBaseline"] + ":" + str(test.relative_to(ROOT))], cwd=ROOT)
    assert test.read_bytes() == original
    relocated = out / "ReferenceLmRuntimeTest.kt"
    header = ("package " + PACKAGE + "\n").encode()
    assert original.count(header) == 1
    relocated.write_bytes(original.replace(header, ("package " + PACKAGE + ".reference\n").encode(), 1))
    jar = out / "runtime-checks.jar"
    lib = Path(receipt["compilerDirectory"])
    command = ["java", "-Xmx768m", "-cp", str(lib / "*"), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
               "-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", cp,
               *map(str, sources), str(test), str(relocated), "-d", str(jar)]
    manifest = {"status": "RUNNING", "baselineCommit": receipt["publicBaseline"],
                "baselineTree": receipt["baselineTree"], "sourceReceiptSha256": sha(source / "manifest.json"),
                "sourceSha256": receipt["sourceSha256"], "runtimeTestSha256": sha(test),
                "referenceTestSha256": sha(relocated), "harnessSha256": sha(Path(__file__).resolve()),
                "packageTransformation": "baseline source and test package declarations only",
                "dependencies": receipt["dependencies"], "classpath": cp,
                "distinctTestMethods": 5, "suiteExecutions": 2}
    (out / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    (out / "compile-command.json").write_text(json.dumps(command, indent=2) + "\n")
    run(command, out / "compile.txt")
    commands = {}
    for variant, package in (("baseline", PACKAGE + ".reference"), ("candidate", PACKAGE)):
        commands[variant] = ["java", "-Xmx512m", "-cp", str(jar) + os.pathsep + cp,
                             "org.junit.runner.JUnitCore", package + ".LmRuntimeTest"]
        run(commands[variant], out / f"{variant}-tests.txt")
    manifest.update(status="CHECKS_PASS", jarSha256=sha(jar), testCommands=commands)
    (out / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")


if __name__ == "__main__":
    main()
