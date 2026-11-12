#!/usr/bin/env python3
"""Compile the combined source against existing Android dependencies."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import time


def digest(path):
    value = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--environment-receipt", type=Path, required=True)
    parser.add_argument("--gradle-cache", type=Path, required=True)
    parser.add_argument("--android-jar", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--baseline", default="5f87bdc610944b93d3173a83c5beeea315abeb66")
    parser.add_argument("--java", default=str(Path(os.environ.get("JAVA_HOME", "/usr")) / "bin/java"))
    args = parser.parse_args()
    root = args.repo.resolve()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=False)
    temporary = out / "tmp"
    temporary.mkdir()

    def git(*values):
        return subprocess.check_output(["git", *values], cwd=root, text=True).strip()

    if git("status", "--porcelain", "--", "app/src"):
        raise SystemExit("Commit production and tests before verification")
    source_commit = git("rev-parse", "HEAD")
    environment = json.loads(args.environment_receipt.read_text())
    entries = [Path(value) for value in environment["classpath"]]
    base_candidates = [value for value in entries if value.name == "base-classes.jar"]
    if len(base_candidates) != 1:
        raise SystemExit("Exactly one frozen base classes JAR required")
    base = out / "base-classes.jar"
    shutil.copy2(base_candidates[0], base)
    classpath = [args.android_jar.resolve(), base]
    for entry in entries:
        if not entry.exists():
            raise SystemExit("Missing dependency: " + entry.name)
        if entry.name not in ("base-classes.jar", "android.jar") and entry not in classpath:
            classpath.append(entry)
    frozen = out / "environment"
    frozen.mkdir()
    for index, entry in enumerate(classpath):
        if entry == base or entry == args.android_jar.resolve():
            continue
        if entry.is_dir() or "caches" not in entry.parts:
            destination = frozen / (str(index) + "-" + entry.name)
            if entry.is_dir():
                shutil.copytree(entry, destination)
            else:
                shutil.copy2(entry, destination)
            classpath[index] = destination
    if sum(path.name == "filament-android-1.56.0-runtime.jar" for path in classpath) != 1:
        raise SystemExit("Filament 1.56.0 runtime required")
    platform_properties = args.android_jar.parent / "source.properties"
    platform = dict(line.split("=", 1) for line in platform_properties.read_text().splitlines()
                    if "=" in line) if platform_properties.exists() else {}
    platform = {key.strip(): value.strip() for key, value in platform.items()}
    android_api = platform.get("AndroidVersion.ApiLevel", "UNVERIFIED")

    def jar(group, name, version):
        matches = sorted((args.gradle_cache / group / name / version).glob("*/*.jar"))
        if len(matches) != 1:
            raise SystemExit("Expected one dependency: " + name + ":" + version)
        return matches[0].resolve()

    compiler = [jar("org.jetbrains.kotlin", name, "2.4.0") for name in (
        "kotlin-compiler-embeddable", "kotlin-stdlib", "kotlin-script-runtime", "kotlin-reflect")]
    compiler += [jar("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.8.0"),
                 jar("org.jetbrains", "annotations", "23.0.0")]
    sources = set(environment.get("source_hashes", environment.get("product_source_hashes", {})))
    sources.update(git("diff", "--name-only", args.baseline, "HEAD", "--", "app/src/main/java").splitlines())
    if any(Path(path).is_absolute() or ".." in Path(path).parts for path in sources):
        raise SystemExit("Repository-relative source paths required")
    sources = sorted(path for path in sources if path.endswith(".kt") and (root / path).is_file())
    if not sources:
        raise SystemExit("No production sources selected")
    before = {path: digest(root / path) for path in sources}
    dependencies = [{"name": path.name, "sha256": digest(path)}
                    for path in classpath + compiler if path.is_file()]
    classes = out / "product-classes"
    classes.mkdir()
    command = [args.java, "-Xmx512m", "-XX:ActiveProcessorCount=1",
               "-Djava.io.tmpdir=" + str(temporary), "-cp", os.pathsep.join(map(str, compiler)),
               "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect",
               "-jvm-target", "17", "-Xfriend-paths=" + str(base),
               "-classpath", os.pathsep.join(map(str, classpath)), "-d", str(classes)]
    command += [str(root / path) for path in sources]
    start = time.monotonic()
    environment_vars = os.environ.copy()
    environment_vars["TMPDIR"] = str(temporary)
    result = subprocess.run(command, cwd=root, env=environment_vars, text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    log = result.stdout.replace(str(root), "${REPO}").replace(str(out), "${OUTPUT}")
    (out / "compile.txt").write_text(log)
    stable = before == {path: digest(root / path) for path in sources}
    stable = stable and source_commit == git("rev-parse", "HEAD")
    dependencies_stable = dependencies == [{"name": path.name, "sha256": digest(path)}
                                          for path in classpath + compiler if path.is_file()]
    receipt = {"source_commit": source_commit, "baseline": args.baseline,
               "source_hashes": before, "source_stable": stable,
               "compiler": "Kotlin 2.4.0", "filament": "1.56.0", "android_api": android_api,
               "environment_receipt_sha256": digest(args.environment_receipt),
               "dependencies_stable": dependencies_stable,
               "dependencies": dependencies, "heap_mib": 512, "processors": 1,
               "result": result.returncode, "elapsed_seconds": time.monotonic() - start,
               "limits": "Scoped production compilation; existing classes supply unchanged dependencies. No full Gradle, native execution, APK or device result."}
    (out / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
    print(json.dumps({key: receipt[key] for key in ("source_commit", "source_stable", "result", "elapsed_seconds")}))
    if result.returncode:
        print(log[-6000:])
    return result.returncode or (0 if stable and dependencies_stable else 2)


if __name__ == "__main__":
    raise SystemExit(main())
