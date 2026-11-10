#!/usr/bin/env python3
"""Build isolated, source-pinned LM1 experiments."""

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess


SOURCE_REF = "4b8fa96effd5a390bd5fd83ed84b81ac850b1d5b"
NATIVE = "app/src/main/java/at/heinz/deviceai/learning/lm"
SUPPORT = "experimental/weight-space-20261104/support-round3"
PINS = {
    "LmTensor.kt": "a5497f18d09be50f7afbd201d54d97f012f7508f8c53c834ff6c10ef278290ab",
    "LmModel.kt": "abb4b50cc443344aefe720844aad4a9ca3e3d967973cc31a4e45ca07b732f099",
    "LmBpeTokenizer.kt": "fcff707a5a1679244cffbb6583e1594b6084e941c7e918f834d71499029b628e",
    "LmCheckpointStore.kt": "09bf68c1c765b6ed625d71672a2aa836fedc3f587546e9197d4b21ee0e446b17",
}
SUPPORT_PINS = {
    "training-continuity.patch": "7deb89ca77f41b45babdf223ec5ce8e1f4771aaea0cbbd92bdfd27edbbc3abb8",
    "LmTrainingState.kt": "e45eb6b39e2fbaeb07328b175ae118d2ea6f2d827d402b51f3275437d2a3fd10",
    "LmTrainingFixture.kt": "adbb7a4bcafcf781618c5ff4392edd959bddb97d280c3f3a7da2402521725f56",
}


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--compiler", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--extra", type=Path, action="append", required=True)
    args = parser.parse_args()
    repo = args.repo.resolve(strict=True)
    compiler = args.compiler.resolve(strict=True)
    for name, expected in SUPPORT_PINS.items():
        if sha(repo / SUPPORT / name) != expected:
            raise ValueError("support source identity changed: " + name)
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    source = out / "source" / NATIVE
    source.mkdir(parents=True)
    for name, expected in PINS.items():
        original = repo / NATIVE / name
        if sha(original) != expected:
            raise ValueError("native source identity changed: " + name)
        shutil.copyfile(original, source / name)
    patch = repo / SUPPORT / "training-continuity.patch"
    command = ["patch", "--batch", "--fuzz=0", "-p1", "-i", str(patch)]
    result = subprocess.run(command, cwd=out / "source", capture_output=True, timeout=15)
    (out / "patch.stdout.txt").write_bytes(result.stdout)
    (out / "patch.stderr.txt").write_bytes(result.stderr)
    result.check_returncode()
    extras = [repo / SUPPORT / "LmTrainingState.kt", repo / SUPPORT / "LmTrainingFixture.kt"]
    extras += [p.resolve(strict=True) for p in args.extra]
    copied = []
    for extra in extras:
        target = out / extra.name
        if target.exists():
            raise ValueError("duplicate experiment filename: " + extra.name)
        shutil.copyfile(extra, target)
        copied.append(target)
    lib = compiler.parent.parent / "lib"
    stdlib = lib / "kotlin-stdlib.jar"
    if not stdlib.is_file():
        raise ValueError("compiler standard library unavailable")
    jar = out / "experiment.jar"
    command = [str(compiler), "-jvm-target", "17", *map(str, sorted(source.glob("*.kt"))),
               *map(str, copied), "-d", str(jar)]
    result = subprocess.run(command, capture_output=True, timeout=180)
    (out / "compile.stdout.txt").write_bytes(result.stdout)
    (out / "compile.stderr.txt").write_bytes(result.stderr)
    version = subprocess.run([str(compiler), "-version"], capture_output=True, timeout=20)
    manifest = {
        "schema": "continuum-weight-native-build-v1", "historicalSourceRef": SOURCE_REF,
        "compileCommand": command, "compileExitCode": result.returncode,
        "originalSha256": PINS, "patchedSha256": {p.name: sha(p) for p in source.glob("*.kt")},
        "extraSha256": {p.name: sha(p) for p in copied}, "patchSha256": sha(patch),
        "compilerVersion": (version.stdout + version.stderr).decode(),
        "compilerJarSha256": sha(lib / "kotlin-compiler.jar"),
        "stdlibSha256": sha(stdlib), "jarSha256": sha(jar) if jar.exists() else None,
        "classpath": str(jar) + ":" + str(stdlib), "modelUpdates": 0,
    }
    bindings = {key: manifest[key] for key in
                ("originalSha256", "patchedSha256", "extraSha256", "patchSha256")}
    manifest["sourceBindingSha256"] = hashlib.sha256(
        json.dumps(bindings, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
    manifest["builderSha256"] = sha(__file__)
    (out / "build.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(json.dumps({"build": str(out / "build.json"), "compileExitCode": result.returncode,
                      "jarSha256": manifest["jarSha256"]}))
    result.check_returncode()


if __name__ == "__main__":
    main()
