#!/usr/bin/env python3
"""DELTA_JVM_GATE: two current Kotlin sources over a pinned, real Gradle baseline.

This is not a full Android build. It creates no application stubs and runs no
model. The original Gradle unit-test classpath, including AGP's mockable Android
dependency, is reused. Only the codec/conversation file and its changed test are
compiled anew; six unchanged test classes come from the successful baseline.
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import signal
import subprocess
import time
import xml.etree.ElementTree as ET
import zipfile


PRODUCTION = "app/src/main/java/at/heinz/deviceai/chief/ChiefBotConversation.kt"
CHANGED_TEST = "app/src/test/java/at/heinz/deviceai/chief/ChiefBotPracticeConversationTest.kt"
TEST_CLASSES = (
    "at.heinz.deviceai.chief.ChiefBotPracticeTest",
    "at.heinz.deviceai.chief.ChiefBotPracticeConversationTest",
    "at.heinz.deviceai.chief.ChiefEngineeringPracticeCurriculumTest",
    "at.heinz.deviceai.chief.ChiefAvatarPracticeCurriculumTest",
    "at.heinz.deviceai.chief.ChiefBotConversationTest",
    "at.heinz.deviceai.chief.ChiefBotProfileStoreTest",
    "at.heinz.deviceai.chief.ChiefBotStorageBoundaryTest",
)
NEW_TEST_CLASS = TEST_CLASSES[1]
BUILD_FILES = ("settings.gradle.kts", "build.gradle.kts", "app/build.gradle.kts", "gradle.properties")
EXPECTED_TESTS = 70

# This launcher inspects real loaded classes, then obtains counts from JUnit.
# It does not implement or replace any application class or Android API.
AUDITOR_SOURCE = r'''
import java.nio.file.*;
import java.util.*;
import org.junit.runner.*;
import org.junit.internal.TextListener;

public final class DeltaJvmGate {
    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c < 32) out.append(String.format("\\u%04x", (int)c));
            else out.append(c);
        }
        return out.append('"').toString();
    }
    private static Class<?> requireOrigin(String name, String expected, Map<String,String> origins) throws Exception {
        Class<?> cls = Class.forName(name, false, DeltaJvmGate.class.getClassLoader());
        if (cls.getProtectionDomain().getCodeSource() == null)
            throw new IllegalStateException("Missing CodeSource: " + name);
        Path actual = Paths.get(cls.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
        origins.put(name, actual.toString());
        if (!actual.equals(Paths.get(expected).toRealPath()))
            throw new IllegalStateException("Unexpected CodeSource: " + name + " -> " + actual);
        return cls;
    }
    public static void main(String[] args) throws Exception {
        // report, new production directory, new test directory, baseline app jar,
        // then seven pairs of test-class name and required origin.
        if (args.length != 18) throw new IllegalArgumentException("Expected four paths and seven class/origin pairs");
        Map<String,String> origins = new LinkedHashMap<>();
        Result result = null;
        Throwable problem = null;
        boolean originsVerified = false;
        boolean junitStarted = false;
        try {
            requireOrigin("at.heinz.deviceai.chief.ChiefBotConversationCodec", args[1], origins);
            requireOrigin("at.heinz.deviceai.chief.ChiefBotConversation", args[1], origins);
            requireOrigin("at.heinz.deviceai.chief.ChiefBotProfileStore", args[3], origins);
            requireOrigin("at.heinz.deviceai.chief.ChiefBotPracticeConversationTest", args[2], origins);
            Class<?>[] classes = new Class<?>[7];
            for (int i = 0; i < classes.length; i++)
                classes[i] = requireOrigin(args[4 + i * 2], args[5 + i * 2], origins);
            originsVerified = true;
            JUnitCore core = new JUnitCore();
            core.addListener(new TextListener(System.out));
            junitStarted = true;
            result = core.run(classes);
        } catch (Throwable error) {
            problem = error;
            error.printStackTrace(System.out);
        }
        int tests = result == null ? 0 : result.getRunCount();
        int failures = result == null ? 0 : result.getFailureCount();
        int ignored = result == null ? 0 : result.getIgnoreCount();
        int assumptions = result == null ? 0 : result.getAssumptionFailureCount();
        boolean passed = problem == null && originsVerified && result != null &&
            result.wasSuccessful() && tests == 70 && ignored == 0 && assumptions == 0;
        StringBuilder json = new StringBuilder("{\"schema\":\"continuum-delta-junit-result\",\"version\":1");
        json.append(",\"codeSourcesVerified\":").append(originsVerified);
        json.append(",\"junitStarted\":").append(junitStarted);
        json.append(",\"junitCompleted\":").append(result != null);
        json.append(",\"tests\":").append(result == null ? "null" : Integer.toString(tests));
        json.append(",\"failures\":").append(result == null ? "null" : Integer.toString(failures));
        json.append(",\"ignored\":").append(result == null ? "null" : Integer.toString(ignored));
        json.append(",\"assumptionFailures\":").append(result == null ? "null" : Integer.toString(assumptions));
        json.append(",\"passed\":").append(passed).append(",\"codeSources\":{");
        boolean first = true;
        for (Map.Entry<String,String> entry : origins.entrySet()) {
            if (!first) json.append(',');
            first = false;
            json.append(quote(entry.getKey())).append(':').append(quote(entry.getValue()));
        }
        json.append("},\"error\":").append(problem == null ? "null" : quote(problem.toString())).append("}\n");
        Files.writeString(Paths.get(args[0]), json.toString(), StandardOpenOption.CREATE_NEW);
        System.exit(passed ? 0 : 1);
    }
}
'''


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def utc_now() -> str:
    return dt.datetime.now(dt.timezone.utc).isoformat()


def write_json(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def digest(path: Path) -> str:
    require(path.is_file() and not path.is_symlink(), f"Regular input file required: {path}")
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


def source_snapshot(root: Path) -> dict[str, str]:
    files = set()
    for relative in ("app/src/main", "app/src/test"):
        for path in (root / relative).rglob("*"):
            require(not path.is_symlink(), f"Source symlink refused: {path}")
            if path.is_file() and "__pycache__" not in path.parts:
                files.add(path)
    files.update(root / name for name in BUILD_FILES)
    return {path.relative_to(root).as_posix(): digest(path) for path in sorted(files)}


def input_snapshot(paths: list[Path]) -> dict[str, object]:
    result = {}
    for path in sorted(set(paths)):
        require(not path.is_symlink(), f"Input symlink refused: {path}")
        if path.is_file():
            result[str(path)] = {"bytes": path.stat().st_size, "sha256": digest(path)}
        elif path.is_dir():
            items = {}
            for item in sorted(path.rglob("*")):
                require(not item.is_symlink(), f"Dependency symlink refused: {item}")
                if item.is_file():
                    items[item.relative_to(path).as_posix()] = digest(item)
            result[str(path)] = {"files": items}
        else:
            raise ValueError(f"Input disappeared: {path}")
    return result


def artifact(cache: Path, group: str, name: str, version: str, suffix: str = "jar") -> Path:
    matches = list((cache / "caches/modules-2/files-2.1" / group / name / version)
                   .glob(f"*/{name}-{version}.{suffix}"))
    require(len(matches) == 1, f"Exactly one cached artifact required: {group}:{name}:{version}:{suffix}")
    require(matches[0].is_file() and not matches[0].is_symlink(), "Unsafe cached artifact")
    return matches[0]


def compiler_inputs(cache: Path) -> tuple[list[Path], Path]:
    name, version = "kotlin-compiler-embeddable", "2.4.0"
    compiler = artifact(cache, "org.jetbrains.kotlin", name, version)
    pom = artifact(cache, "org.jetbrains.kotlin", name, version, "pom")
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    dependencies = []
    for dependency in ET.parse(pom).findall("./m:dependencies/m:dependency", namespace):
        field = lambda key: dependency.findtext("m:" + key, namespaces=namespace)
        if field("scope") not in (None, "compile", "runtime") or field("optional") == "true":
            continue
        group, module, release = field("groupId"), field("artifactId"), field("version")
        require(all(value and "${" not in value for value in (group, module, release)), "Unresolved compiler POM dependency")
        dependencies.append(artifact(cache, group, module, release))
    require(len(dependencies) == 6, "Unexpected Kotlin 2.4.0 compiler dependency set; inspect before running")
    # Required by JVM code generation; already present in the original toolchain.
    dependencies.append(artifact(cache, "org.jetbrains", "annotations", "13.0"))
    with zipfile.ZipFile(compiler) as jar:
        require("org/jetbrains/kotlin/cli/jvm/K2JVMCompiler.class" in jar.namelist(), "Compiler CLI missing")
    return [compiler, *dependencies], pom


def run_phase(name: str, command: list[str], out: Path, cwd: Path,
              env: dict[str, str], timeout: int) -> dict[str, object]:
    write_json(out / (name + ".command.json"), command)
    started_at, started = utc_now(), time.monotonic()
    timed_out = False
    with (out / (name + ".log")).open("x", encoding="utf-8") as log:
        process = subprocess.Popen(command, cwd=cwd, env=env, stdout=log,
                                   stderr=subprocess.STDOUT, start_new_session=True)
        try:
            code = process.wait(timeout=timeout)
        except (subprocess.TimeoutExpired, KeyboardInterrupt) as error:
            timed_out = isinstance(error, subprocess.TimeoutExpired)
            try:
                os.killpg(process.pid, signal.SIGTERM)
            except ProcessLookupError:
                pass
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                try:
                    os.killpg(process.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
                process.wait(timeout=5)
            code = 124 if timed_out else 130
    result = {"phase": name, "startedAt": started_at, "finishedAt": utc_now(),
              "seconds": round(time.monotonic() - started, 3), "exitCode": code, "timedOut": timed_out}
    write_json(out / (name + ".exit.json"), result)
    print(json.dumps(result), flush=True)
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--gradle-classpath", type=Path, required=True)
    parser.add_argument("--gradle-cache", type=Path, required=True)
    parser.add_argument("--java-home", type=Path, required=True)
    parser.add_argument("--baseline-app-sha256", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--heap-mb", type=int, default=640)
    parser.add_argument("--compile-timeout-seconds", type=int, default=180)
    parser.add_argument("--test-timeout-seconds", type=int, default=180)
    args = parser.parse_args()
    require(512 <= args.heap_mb <= 768, "JVM heap must remain between 512 and 768 MiB")
    require(30 <= args.compile_timeout_seconds <= 600 and 30 <= args.test_timeout_seconds <= 600, "Timeout out of bounds")
    require(len(args.baseline_app_sha256) == 64 and all(c in "0123456789abcdef" for c in args.baseline_app_sha256), "Expected baseline SHA256 required")
    root, baseline = args.root.resolve(strict=True), args.baseline.resolve(strict=True)
    cache, java_home = args.gradle_cache.resolve(strict=True), args.java_home.resolve(strict=True)
    exported = args.gradle_classpath.resolve(strict=True)
    out = args.output.resolve()
    require(not any(out == item or item in out.parents for item in (root, baseline, cache, java_home)), "Output must be outside all source/baseline/toolchain trees")
    out.mkdir(parents=True, exist_ok=False)
    started_at = utc_now()
    result = {"schema": "continuum-delta-jvm-gate", "version": 1, "evidenceClass": "DELTA_JVM_GATE",
              "startedAt": started_at, "passed": False, "fullAndroidBuildVerified": False,
              "fullAndroidBuildStatus": "BLOCKED", "modelAttempts": 0, "weightUpdates": 0,
              "deviceVerified": False, "expectedTests": EXPECTED_TESTS, "phases": [],
              "limits": "Two freshly compiled Kotlin files over the real successful Gradle baseline; not a current full Android build, APK, device or model-training result."}
    sources_before = None
    inputs_before = None
    inputs: list[Path] = []
    try:
        result["currentHead"] = subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=root, text=True, timeout=10).strip()
        result["worktreeAtStart"] = subprocess.check_output(
            ["git", "status", "--porcelain"], cwd=root, text=True, timeout=10)
        baseline_result = json.loads((baseline / "result.json").read_text())
        baseline_sources = json.loads((baseline / "source-before.json").read_text())
        require(baseline_sources == json.loads((baseline / "source-after.json").read_text()), "Baseline source changed during its check")
        require(baseline_result.get("passed") is True and baseline_result.get("exitCode") == 0 and
                baseline_result.get("allSelectedClassesExecuted") is True and
                baseline_result.get("sourceUnchangedDuringCheck") is True and
                baseline_result.get("totals") == {"tests": 70, "failures": 0, "errors": 0, "skipped": 0}, "Successful 70-test baseline required")
        sources_before = source_snapshot(root)
        baseline_app_sources = {key: value for key, value in baseline_sources.items()
                                if key.startswith(("app/src/main/", "app/src/test/")) or key in BUILD_FILES}
        changes = {key for key in set(sources_before) | set(baseline_app_sources)
                   if sources_before.get(key) != baseline_app_sources.get(key)}
        require(changes == {PRODUCTION, CHANGED_TEST}, f"Unexpected app/build source delta: {sorted(changes)}")
        write_json(out / "source-before.json", sources_before)
        data = json.loads(exported.read_text())
        require(isinstance(data.get("classpath"), list) and isinstance(data.get("testClassesDirs"), list), "Invalid Gradle classpath export")
        require(1 <= len(data["classpath"]) <= 128, "Unexpected classpath size")
        no_source = root / "app/build/intermediates/javac/debugUnitTest/compileDebugUnitTestJavaWithJavac/classes"
        classpath, missing = [], []
        for value in data["classpath"]:
            require(isinstance(value, str) and Path(value).is_absolute(), "Absolute classpath entries required")
            path = Path(value)
            if not path.exists():
                require(path == no_source and value in data["testClassesDirs"], f"Missing dependency: {path}")
                missing.append({"path": str(path), "reason": "ORIGINAL_GRADLE_JAVAC_NO_SOURCE_DIRECTORY"})
                continue
            require(not path.is_symlink() and (path.is_file() or path.is_dir()), f"Unsafe dependency: {path}")
            classpath.append(path.resolve(strict=True))
        app_jars = [p for p in classpath if p.as_posix().endswith(
            "/runtime_app_classes_jar/debug/bundleDebugClassesToRuntimeJar/classes.jar")]
        require(len(app_jars) == 1, "Exactly one original runtime app JAR required")
        app_jar = app_jars[0]
        require(digest(app_jar) == args.baseline_app_sha256, "Baseline app JAR hash mismatch")
        with zipfile.ZipFile(app_jar) as jar:
            require("META-INF/DeviceAI_app_debug.kotlin_module" in jar.namelist(), "Baseline Kotlin module mismatch")
        begin = dt.datetime.fromisoformat(baseline_result["startedAt"]).timestamp()
        end = dt.datetime.fromisoformat(baseline_result["finishedAt"]).timestamp()
        require(begin <= app_jar.stat().st_mtime <= end, "Baseline app JAR timestamp outside successful run")
        test_dirs = [Path(p).resolve(strict=True) for p in data["testClassesDirs"] if Path(p).is_dir()]
        require(test_dirs and all(p in classpath for p in test_dirs), "Original test directories missing from classpath")
        origins = {}
        for name in TEST_CLASSES:
            source = "app/src/test/java/" + name.replace(".", "/") + ".kt"
            if name != NEW_TEST_CLASS:
                require(sources_before.get(source) == baseline_app_sources.get(source) and source in sources_before,
                        f"Unchanged baseline test source required: {name}")
            relative = name.replace(".", "/") + ".class"
            candidates = [p for p in test_dirs if (p / relative).is_file()]
            require(len(candidates) == 1, f"Ambiguous or missing original test class: {name}")
            require(begin <= (candidates[0] / relative).stat().st_mtime <= end, f"Test class timestamp outside baseline: {name}")
            origins[name] = candidates[0]
        compiler, pom = compiler_inputs(cache)
        java, javac = java_home / "bin/java", java_home / "bin/javac"
        require(all(p.is_file() and os.access(p, os.X_OK) for p in (java, javac)), "Existing JDK required")
        inputs = [*classpath, *compiler, pom, exported, Path(__file__).resolve(), java, javac,
                  java_home / "release", java_home / "lib/modules", java_home / "lib/server/libjvm.so",
                  baseline / "result.json", baseline / "source-before.json", baseline / "source-after.json"]
        inputs_before = input_snapshot(inputs)
        write_json(out / "inputs-before.json", inputs_before)
        result.update(baseHead=baseline_result.get("baseHead"), changedSources=sorted(changes),
                      baselineAppJar=str(app_jar), baselineAppSha256=args.baseline_app_sha256,
                      omittedClasspathEntries=missing, compilerVersion="2.4.0", heapMiB=args.heap_mb,
                      baselineProvenance="Original Gradle task outputs within run01 timestamps; app JAR externally pinned by SHA256. Run01 did not archive a separate test-bytecode manifest.")
        main_out, test_out, audit_out = out / "production", out / "tests", out / "audit"
        for path in (main_out, test_out, audit_out):
            path.mkdir()
        env = os.environ.copy()
        for key in ("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH",
                    "CONTINUUM_LOCAL_SIGNING_FILE", "CONTINUUM_LOCAL_SIGNING_PASSWORD",
                    "CONTINUUM_MENTOR_SIGNING_FILE", "CONTINUUM_MENTOR_SIGNING_PASSWORD"):
            env.pop(key, None)
        joined = lambda paths: os.pathsep.join(map(str, paths))
        compiler_command = [str(java), f"-Xmx{args.heap_mb}m", "-cp", joined(compiler),
                            "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-jvm-target", "17"]
        commands = [
            ("compile-production", compiler_command + ["-module-name", "DeviceAI_app_debug",
                "-Xfriend-paths=" + str(app_jar), "-classpath", joined([p for p in classpath if p not in test_dirs]),
                "-d", str(main_out), str(root / PRODUCTION)]),
            ("compile-current-test", compiler_command + ["-module-name", "DeviceAI_app_debugUnitTest",
                "-Xfriend-paths=" + str(main_out) + "," + str(app_jar), "-classpath", joined([main_out, *classpath]),
                "-d", str(test_out), str(root / CHANGED_TEST)]),
        ]
        for name, command in commands:
            phase = run_phase(name, command, out, root, env, args.compile_timeout_seconds)
            result["phases"].append(phase)
            require(phase["exitCode"] == 0, f"Phase failed: {name}")
        audit_source = out / "DeltaJvmGate.java"
        audit_source.write_text(AUDITOR_SOURCE, encoding="utf-8")
        audit_compile = [str(javac), f"-J-Xmx{args.heap_mb}m", "-encoding", "UTF-8", "--release", "17",
                         "-classpath", joined(classpath), "-d", str(audit_out), str(audit_source)]
        phase = run_phase("compile-origin-auditor", audit_compile, out, root, env, args.compile_timeout_seconds)
        result["phases"].append(phase)
        require(phase["exitCode"] == 0, "CodeSource auditor compilation failed")
        run = [str(java), f"-Xmx{args.heap_mb}m", "-cp", joined([audit_out, test_out, main_out, *classpath]),
               "DeltaJvmGate", str(out / "junit-result.json"), str(main_out), str(test_out), str(app_jar)]
        for name in TEST_CLASSES:
            run.extend((name, str(test_out if name == NEW_TEST_CLASS else origins[name])))
        phase = run_phase("junit", run, out, root / "app", env, args.test_timeout_seconds)
        result["phases"].append(phase)
        junit = json.loads((out / "junit-result.json").read_text()) if (out / "junit-result.json").is_file() else None
        result["junit"] = junit
        result["passed"] = bool(phase["exitCode"] == 0 and junit and junit.get("passed") is True and
                                junit.get("codeSourcesVerified") is True and junit.get("tests") == EXPECTED_TESTS and
                                junit.get("failures") == junit.get("ignored") == junit.get("assumptionFailures") == 0)
        write_json(out / "compiled-outputs.json", input_snapshot([main_out, test_out, audit_out, audit_source]))
    except (Exception, KeyboardInterrupt) as error:
        result["passed"] = False
        result["error"] = {"type": type(error).__name__, "message": str(error)[:2000]}
    finally:
        try:
            sources_after = source_snapshot(root)
            write_json(out / "source-after.json", sources_after)
            result["sourceUnchangedDuringCheck"] = sources_before is not None and sources_after == sources_before
            inputs_after = input_snapshot(inputs) if inputs_before is not None else None
            if inputs_after is not None:
                write_json(out / "inputs-after.json", inputs_after)
            result["dependenciesUnchangedDuringCheck"] = inputs_before is not None and inputs_after == inputs_before
        except Exception as error:
            result["sourceUnchangedDuringCheck"] = False
            result["dependenciesUnchangedDuringCheck"] = False
            result["bindingError"] = str(error)[:2000]
        result["passed"] = bool(result["passed"] and result["sourceUnchangedDuringCheck"] and result["dependenciesUnchangedDuringCheck"])
        result["status"] = "DELTA_JVM_PASS" if result["passed"] else "DELTA_JVM_FAILED"
        result["finishedAt"] = utc_now()
        write_json(out / "result.json", result)
        print(json.dumps(result, ensure_ascii=False, indent=2), flush=True)
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
