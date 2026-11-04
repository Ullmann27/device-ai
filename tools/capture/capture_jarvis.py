#!/usr/bin/env python3
"""Run explicit native-Canvas still captures through the shared serial Gradle runner."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import time


CASES = (
    "compact390", "compact412", "mediumShortLandscape", "mediumMultiwindow",
    "mediumNearSquare", "expandedCanonical", "expandedTablet",
    "compactFont150", "compactFont200", "mediumFont150", "mediumFont200",
    "expandedFont150", "expandedFont200", "referenceRasterActivityCanvas",
)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--gradle-runner", type=Path, required=True,
                        help="Existing run_gradle.py, which owns the shared cross-worktree lock and toolchain")
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--case", action="append", choices=CASES)
    parser.add_argument("--matrix", action="store_true")
    parser.add_argument("--timeout-seconds", type=int, default=600)
    args = parser.parse_args()
    repo = args.repo.resolve()
    output = args.output_dir.resolve()
    runner = args.gradle_runner.resolve()
    if not runner.is_file():
        raise ValueError("Shared serial Gradle runner not found")
    if args.timeout_seconds < 30 or args.timeout_seconds > 1800:
        raise ValueError("Capture timeout must be between 30 and 1800 seconds")
    if args.matrix and args.case:
        raise ValueError("Choose --matrix or explicit --case selections")
    def git(*arguments: str) -> str:
        return subprocess.check_output(["git", *arguments], cwd=repo, text=True).strip()
    if git("status", "--porcelain", "--untracked-files=no"):
        raise ValueError("Commit tracked source changes before capturing so provenance is unambiguous")
    if output.exists() and any(output.iterdir()):
        raise ValueError("Use a new output directory; a previous successful PNG must never survive a failed run")
    output.mkdir(parents=True, exist_ok=True)
    source = git("rev-parse", "HEAD")
    main_tree = git("rev-parse", "HEAD:app/src/main")
    selected = list(CASES) if args.matrix else args.case or ["expandedCanonical"]
    command = [sys.executable, str(runner), "-I", str(Path(__file__).with_name("force_capture.gradle")),
               ":app:testDebugUnitTest",
               "-PcontinuumUiWidgetTests=true", f"-PcontinuumUiEvidenceDir={output}"]
    for case in selected:
        command.extend(["--tests", f"at.heinz.deviceai.JarvisDeterministicCaptureUiTest.{case}"])
    environment = os.environ.copy()
    environment.update({
        "CONTINUUM_BUILD_ROOT": str(repo),
        "CONTINUUM_BUILD_LOG": f"capture-{source[:12]}-{int(time.time())}.log",
        "CONTINUUM_BUILD_WORKERS": "1",
        "CONTINUUM_CAPTURE_SOURCE_COMMIT": source,
    })
    provenance = {
        "source_commit": source, "application_main_tree": main_tree,
        "fidelity": "android_activity_host_native_canvas_api28_explicit_2d_head",
        "cases": selected, "command": command,
        "animation_policy": "duration scale zero before Activity setup; normal app source unchanged",
        "physical_device_verified": False, "native_filament_verified": False,
    }
    started = time.monotonic()
    with (output / "gradle-capture.log").open("w") as log:
        process = subprocess.Popen(command, cwd=repo, env=environment, stdout=log,
                                   stderr=subprocess.STDOUT, start_new_session=True)
        try:
            exit_code = process.wait(timeout=args.timeout_seconds)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGTERM)
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()
            exit_code = 124
    provenance.update({"exit_code": exit_code, "elapsed_seconds": time.monotonic() - started})
    images = []
    validation_errors = []
    for image in sorted(output.glob("*.png")):
        manifest_path = image.with_suffix(".json")
        if not manifest_path.is_file():
            validation_errors.append(f"Missing per-frame manifest: {image.name}")
            continue
        manifest = json.loads(manifest_path.read_text())
        image_hash = hashlib.sha256(image.read_bytes()).hexdigest()
        if manifest.get("source_commit") != source or manifest.get("png_sha256") != image_hash:
            validation_errors.append(f"Frame provenance mismatch: {image.name}")
            continue
        images.append({"image": image.name, "manifest": manifest_path.name, "sha256": image_hash})
    provenance["frames"] = images
    provenance["post_run_source_commit"] = git("rev-parse", "HEAD")
    provenance["tracked_changes_after_run"] = git("status", "--porcelain", "--untracked-files=no")
    if len(images) != len(selected):
        validation_errors.append(f"Expected {len(selected)} complete frame manifests, observed {len(images)}")
    if exit_code == 0 and (validation_errors or
                           provenance["post_run_source_commit"] != source or
                           provenance["tracked_changes_after_run"]):
        provenance["exit_code"] = exit_code = 2
        validation_errors.append("Missing/invalid frames or source changed during capture")
    provenance["validation_errors"] = validation_errors
    (output / "capture-run.json").write_text(json.dumps(provenance, indent=2) + "\n")
    print(json.dumps({"output": str(output), "source_commit": source,
                      "exit_code": exit_code, "frames": len(images)}, indent=2))
    raise SystemExit(exit_code)


if __name__ == "__main__":
    main()
