#!/usr/bin/env python3
"""Run exact production classes against explicit host boundary fixtures."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

BASELINE = "5f87bdc610944b93d3173a83c5beeea315abeb66"
FULL = [
    "app/src/main/java/at/heinz/deviceai/avatar3d/CockpitLiveTexture.kt",
    "app/src/main/java/at/heinz/deviceai/avatar3d/CockpitPresentationSequence.kt",
    "app/src/main/java/at/heinz/deviceai/avatar3d/CockpitStageFrame.kt",
    "app/src/main/java/at/heinz/deviceai/cognitive/LiveComputerFrames.kt",
]
EXCERPTS = {
    "app/src/main/java/at/heinz/deviceai/AndroidLiveComputer.kt": (
        "AndroidLiveFrame.kt", "internal class AndroidLiveFrame(", "\ninternal data class LiveComputerStart(",
        "package at.heinz.deviceai\nimport android.hardware.HardwareBuffer\n"
        "import android.graphics.ColorSpace\nimport java.util.concurrent.atomic.AtomicBoolean\n"),
    "app/src/main/java/at/heinz/deviceai/cognitive/LiveComputerActions.kt": (
        "LiveTargetBounds.kt", "internal data class LiveTargetBounds(", "\n/**", "package at.heinz.deviceai.cognitive\n"),
    "app/src/main/java/at/heinz/deviceai/cognitive/CockpitWorkEvent.kt": (
        "CockpitWorkEvent.kt", "data class CockpitWorkEvent(", "\n/** No owner text",
        "package at.heinz.deviceai.cognitive\nimport at.heinz.deviceai.cockpit.CockpitAnimationEvent\n"),
}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def git(repo, *args):
    return subprocess.check_output(["git", *args], cwd=repo)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--compiler-lib", type=Path, required=True)
    parser.add_argument("--baseline", default=BASELINE)
    parser.add_argument("--holdout", action="store_true")
    parser.add_argument("--java", default="java")
    parser.add_argument("--tmp", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    here = Path(__file__).resolve().parent
    repo = here.parent.parent
    baseline = git(repo, "rev-parse", "--verify", args.baseline + "^{commit}").decode().strip()
    args.tmp.mkdir(parents=True, exist_ok=True)
    args.output.mkdir(parents=True, exist_ok=True)
    scratch = Path(tempfile.mkdtemp(prefix="uniform-ab-", dir=args.tmp))
    (scratch / "jvm-tmp").mkdir()
    libs = [args.compiler_lib / name for name in (
        "kotlin-compiler-embeddable-2.0.20.jar", "kotlin-stdlib-2.0.20.jar",
        "kotlin-reflect-2.0.20.jar", "kotlin-script-runtime-2.0.20.jar",
        "trove4j-1.0.20200330.jar", "annotations-24.0.1.jar",
        "kotlinx-coroutines-core-jvm-1.6.4.jar")]
    for path in libs:
        if not path.is_file():
            raise FileNotFoundError(path.name)
    stdlib = args.compiler_lib / "kotlin-stdlib-2.0.20.jar"
    source_paths = FULL + list(EXCERPTS)
    submission = "app/src/main/java/at/heinz/deviceai/cognitive/LiveMonitorSubmission.kt"
    if (repo / submission).exists():
        source_paths.append(submission)
    original_hashes = {p: digest((repo / p).read_bytes()) for p in source_paths}
    manifest = {
        "baseline_commit": baseline,
        "candidate_head": git(repo, "rev-parse", "HEAD").decode().strip(),
        "candidate_status": git(repo, "status", "--short").decode(),
        "candidate_source_sha256": original_hashes,
        "boundary": "Host execution of full newly compiled CockpitLiveTexture; Android/Filament fixtures, no native GPU",
        "metric": "Calls at Filament interface; no native latency or energy claim",
        "max_heap_mib": 512, "device": "NOT_RUN", "gradle": "NOT_RUN",
        "suite": "partial-write-holdout" if args.holdout else "preregistered-14",
        "dependencies": {p.name: digest(p.read_bytes()) for p in libs},
        "harness_sha256": {str(p.relative_to(here)): digest(p.read_bytes()) for p in sorted(here.rglob("*.kt"))},
        "runner_sha256": digest(Path(__file__).read_bytes()),
        "arms": {},
    }
    env = dict(os.environ, TMPDIR=str(args.tmp))
    all_results = {}
    for arm in ("baseline", "candidate"):
        work = scratch / arm
        work.mkdir()
        classes = work / "classes"
        hashes = {}
        extracts = {}
        sources = []
        for path in source_paths:
            raw = git(repo, "show", f"{baseline}:{path}") if arm == "baseline" else (repo / path).read_bytes()
            hashes[path] = digest(raw)
            if path in EXCERPTS:
                name, start, end, prefix = EXCERPTS[path]
                text = raw.decode()
                begin = text.index(start)
                finish = text.index(end, begin)
                raw = (prefix + text[begin:finish] + "\n").encode()
                extracts[path] = {"symbol": start, "excerpt_sha256": digest(raw)}
            else:
                name = Path(path).name
            target = work / name
            target.write_bytes(raw)
            sources.append(target)
        for fixture in sorted((here / "fixtures").glob("*.kt")) + [here / "DisplayUniformExperiment.kt"]:
            target = work / fixture.name
            shutil.copyfile(fixture, target)
            sources.append(target)
        command = [args.java, "-Xmx512m", f"-Djava.io.tmpdir={scratch / 'jvm-tmp'}", "-cp", os.pathsep.join(map(str, libs)),
                   "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-jvm-target", "17",
                   "-classpath", os.pathsep.join(map(str, [stdlib, args.compiler_lib / "annotations-24.0.1.jar"])),
                   "-d", str(classes), *map(str, sources)]
        compilation = subprocess.run(command, cwd=work, env=env, capture_output=True, text=True, timeout=180)
        log = compilation.stdout + compilation.stderr
        (args.output / f"{arm}-compile.log").write_text(log)
        manifest["arms"][arm] = {"compile_exit": compilation.returncode, "source_sha256": hashes,
                                  "excerpts": extracts, "compile_log_sha256": digest(log.encode())}
        if compilation.returncode:
            (args.output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
            print(json.dumps({"arm": arm, "compile_exit": compilation.returncode, "output": str(args.output)}))
            return 1
        for mode in (["baseline"] if arm == "baseline" else ["enabled", "disabled"]):
            command = [args.java, "-Xmx512m", f"-Djava.io.tmpdir={scratch / 'jvm-tmp'}"]
            if mode == "disabled":
                command += ["-DuniformControl=true"]
            if args.holdout:
                command += ["-DuniformHoldout=true"]
            command += ["-cp", os.pathsep.join(map(str, [classes, stdlib])),
                        "at.heinz.deviceai.avatar3d.DisplayUniformExperimentKt"]
            run = subprocess.run(command, cwd=work, env=env, capture_output=True, text=True, timeout=60)
            (args.output / f"{mode}.jsonl").write_text(run.stdout)
            (args.output / f"{mode}.stderr").write_text(run.stderr)
            manifest["arms"].setdefault(mode, {})["run_exit"] = run.returncode
            if run.returncode:
                (args.output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
                print(json.dumps({"mode": mode, "run_exit": run.returncode, "output": str(args.output)}))
                return 1
            all_results[mode] = [json.loads(line) for line in run.stdout.splitlines()]
    comparisons = []
    for a, b, off in zip(all_results["baseline"], all_results["enabled"], all_results["disabled"], strict=True):
        comparable = ("case", "ticks", "trace", "importsAndReleases", "bufferEvents", "maxBuffers")
        equal = all(a[key] == b[key] == off[key] for key in comparable)
        rollback = a["stableWrites"] == off["stableWrites"]
        primary = a["case"] in ("sixty_ticks_ten_frames", "sixty_ticks_one_frame")
        saving = 1 - b["stableWrites"] / a["stableWrites"] if a["stableWrites"] else 0
        comparisons.append({"case": a["case"], "equivalent_trace": equal, "disabled_matches_baseline": rollback,
                            "baseline_writes": a["stableWrites"], "enabled_writes": b["stableWrites"],
                            "disabled_writes": off["stableWrites"], "saving": saving,
                            "primary_gate": (saving >= .5) if primary else None})
    current_hashes = {p: digest((repo / p).read_bytes()) for p in source_paths}
    manifest["candidate_source_sha256_after"] = current_hashes
    manifest["source_unchanged"] = original_hashes == current_hashes
    manifest["comparisons"] = comparisons
    manifest["accepted_host_gate"] = manifest["source_unchanged"] and all(
        c["equivalent_trace"] and c["disabled_matches_baseline"] and c["primary_gate"] is not False for c in comparisons)
    (args.output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(json.dumps({"accepted_host_gate": manifest["accepted_host_gate"], "scenarios_per_arm": len(comparisons),
                      "arms": 3, "primary": [c for c in comparisons if c["primary_gate"] is not None],
                      "output": str(args.output)}, indent=2))
    return 0 if manifest["accepted_host_gate"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
