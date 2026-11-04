#!/usr/bin/env python3
"""Restore the exact research inputs from an already available local Git history.

This prepares a fresh experiment directory. It does not fetch, train, render,
modify the source checkout, change branches, or install dependencies.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import sys


HERE = Path(__file__).resolve().parent


def checked_path(value: str) -> PurePosixPath:
    path = PurePosixPath(value)
    if path.is_absolute() or not path.parts or any(part in {"..", "."} for part in path.parts):
        raise ValueError(f"unsafe manifest path: {value!r}")
    return path


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, required=True, help="Local repository containing the pinned Git objects")
    parser.add_argument("--additional-repo", type=Path, action="append", default=[],
                        help="Additional local object database for source snapshots; repeatable")
    parser.add_argument("--output", type=Path, required=True, help="New directory; existing data is never overwritten")
    args = parser.parse_args()
    repository = args.repo.resolve(strict=True)
    repositories = [repository, *(path.resolve(strict=True) for path in args.additional_repo)]
    output = args.output.absolute()
    if output.exists() or output.is_symlink():
        raise FileExistsError("use a new output directory")
    environment = os.environ.copy()
    environment["GIT_NO_LAZY_FETCH"] = "1"
    for candidate in repositories:
        subprocess.run(["git", "-C", str(candidate), "rev-parse", "--git-dir"],
                       check=True, capture_output=True, timeout=15, env=environment)
    weight_manifest = json.loads((HERE / "provenance/lm1-source-manifest.json").read_text())
    avatar_manifest = json.loads((HERE / "avatar/results/source-manifest.json").read_text())
    planned: list[tuple[PurePosixPath, bytes]] = []
    observations = []

    def collect(commit: str, source_path: str, destination: str, expected: str, size: int) -> None:
        if not re.fullmatch(r"[0-9a-f]{40}", commit):
            raise ValueError("manifest commit must be an exact SHA")
        checked_path(source_path)
        target = checked_path(destination)
        raw = None
        found_in = None
        for candidate in repositories:
            result = subprocess.run(["git", "-C", str(candidate), "show", f"{commit}:{source_path}"],
                                    capture_output=True, timeout=30, env=environment)
            if result.returncode == 0:
                raw = result.stdout
                found_in = str(candidate)
                break
        if raw is None:
            raise FileNotFoundError(f"pinned object unavailable in supplied local repositories: {commit}:{source_path}")
        if len(raw) != size or digest(raw) != expected:
            raise ValueError(f"source identity mismatch: {commit}:{source_path}")
        planned.append((target, raw))
        observations.append({"commit": commit, "path": source_path, "local": str(target), "repository": found_in,
                             "bytes": len(raw), "sha256": expected})

    for record in weight_manifest["files"]:
        if record["status"] != "READY":
            raise ValueError("unavailable source in LM1 manifest")
        collect(weight_manifest["sha"], record["path"], "sources/weight/" + record["path"],
                record["sha256"], record["bytes"])
    for record in avatar_manifest["records"]:
        collect(record["commit"], record["path"], "avatar/" + record["local"],
                record["sha256"], record["bytes"])
    targets = [str(path) for path, _ in planned]
    if len(targets) != len(set(targets)):
        raise ValueError("duplicate destination in source manifests")

    output.mkdir(parents=True, exist_ok=False)
    for path, raw in planned:
        destination = output / path
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(raw)
    weight = output / "sources/weight"
    (weight / "SOURCE_MANIFEST.json").write_text(json.dumps(weight_manifest, indent=2) + "\n")
    for area, extensions in [("autonomy", {".py", ".mjs"}), ("language", {".py", ".kt", ".tsv"})]:
        destination = output / area
        destination.mkdir(exist_ok=True)
        for source in sorted((HERE / area).iterdir()):
            if source.is_file() and source.suffix in extensions:
                shutil.copyfile(source, destination / source.name)
    for source in sorted((HERE / "avatar/scripts").glob("*")):
        if source.is_file() and source.suffix in {".py", ".kt"}:
            destination = output / "avatar/scripts" / source.name
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, destination)
    for subdir in ["avatar/results", "avatar/tmp", "avatar/renders"]:
        (output / subdir).mkdir(parents=True, exist_ok=True)
    shutil.copyfile(HERE / "avatar/results/source-manifest.json", output / "avatar/results/source-manifest.json")
    command = [sys.executable, str(weight / "experimental/weight-space-20261104/prepare_inputs.py"),
               "--repo", str(repository), "--output", str(output / "sources/lm1-original-inputs")]
    prepared = subprocess.run(command, check=True, capture_output=True, text=True,
                              timeout=180, env=environment)
    manifest = {"scope": "RESEARCH_SHADOW_INPUT_RESTORE", "sourceRepository": str(repository),
                "additionalSourceRepositories": [str(path) for path in repositories[1:]],
                "output": str(output), "sourceFiles": observations, "inputPreparationCommand": command,
                "inputPreparation": json.loads(prepared.stdout), "networkFetchRequested": False,
                "experimentsExecuted": False,
                "autonomySourceRequirement": "Separate clean checkout at e101e393dd6c80803ac9d9ac91fccd9812c4a18d; pass --source explicitly"}
    (output / "RESTORE-MANIFEST.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(json.dumps({"output": str(output), "sourceFiles": len(observations),
                      "trainingDocuments": manifest["inputPreparation"]["trainingDocuments"],
                      "experimentsExecuted": False}, indent=2))


if __name__ == "__main__":
    main()
