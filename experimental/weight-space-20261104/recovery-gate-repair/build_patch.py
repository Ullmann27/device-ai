#!/usr/bin/env python3
"""Reproduce a source-pinned review patch without touching a live study runner.

Default mode checks the committed artifacts. --write-artifacts regenerates only
the three patched text fixtures, their unified patch and their source manifest.
This script never runs a study, invokes Java, reads datasets or writes receipts.
"""
from __future__ import annotations

import argparse
import difflib
import hashlib
import json
from pathlib import Path


HERE = Path(__file__).resolve().parent
REVIEWED_COMMIT = "be4e49600fed1db4c697230bb8e42c150dbfbdaf"
SOURCES = {
    "v1": ("experimental/lm1-training-recovery-20261104/run_registered_study.py",
           "28908e07d6217f10882429047c853d1dd8667912",
           "409f87f5a174804d7290c29972d6b4e2bbb19ad293ac1da027122c3f28d14d4f"),
    "long": ("experimental/lm1-training-recovery-20261104/long-study/run_registered_study.py",
             "30119435feed1728c5d22fec40830654a7a19861",
             "90877b90a7f138a9c469fb0350bd6c28ff56602b7afb61fe7ee05b1e11ab230b"),
    "suffix": ("experimental/lm1-training-recovery-20261104/suffix-study/run_registered_study.py",
               "417e4b3289bb692e72dc80398b51b67e106b8907",
               "276b2fc27f566fdb3947c9f7fa13e7c9b10cc549354f90b478f16883e9bd4c68"),
}


def digest(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def replace_once(source: str, before: str, after: str) -> str:
    if source.count(before) != 1:
        raise RuntimeError("REVIEWED_SOURCE_SHAPE_CHANGED: " + before.strip().splitlines()[0])
    return source.replace(before, after, 1)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write-artifacts", action="store_true")
    args = parser.parse_args()
    fragment_path = HERE / "post_capacity_evidence.py.fragment"
    fragment_raw = fragment_path.read_bytes()
    fragment = fragment_raw.decode("utf-8").rstrip() + "\n\n"
    outputs = {}
    entries = {}
    patch = []
    for variant, (target, git_blob, expected) in SOURCES.items():
        original_path = HERE / "fixtures" / variant / "original_run_registered_study.py.txt"
        original = original_path.read_bytes()
        if digest(original) != expected:
            raise RuntimeError("UNREVIEWED_SOURCE_REJECTED_BEFORE_COMPILE: " + variant)
        source = original.decode("utf-8")
        source = replace_once(source, "    def freeze_evidence(self) -> dict:\n",
                              fragment + "    def freeze_evidence(self) -> dict:\n")
        source = replace_once(source,
            '        selected = {variant: self.selection(variant) for variant in ["small", "wide"]}\n',
            '        selected = {variant: self.selection(variant) for variant in ["small", "wide"]}\n'
            '        post_capacity = {arm: self.post_capacity_evidence(variant, selected[variant])\n'
            '                         for variant, arm in (("small", "S1"), ("wide", "W1"))}\n')
        source = replace_once(source,
            '        return {"status": "FROZEN_BEFORE_TEST", "arms": arms,\n',
            '        return {"status": "FROZEN_BEFORE_TEST", "arms": arms, "postTrainingCapacity": post_capacity,\n')
        source = replace_once(source, '        for arm in ["S0", "W0", "S1", "W1"]:\n',
            '        for arm in ["S0", "W0", "S1", "W1"]:\n'
            '            if selection != self.freeze_evidence():\n'
            '                raise RuntimeError("INVALID_TEST_AUTHORIZATION")\n')
        source = replace_once(source, '        if sha(frozen) != frozen_sha:\n',
            '        if sha(frozen) != frozen_sha or selection != self.freeze_evidence():\n')
        source = replace_once(source,
            '        write_new(self.out / "intent-summary.json", {"selectionSha256": frozen_sha, "scores": outputs,\n',
            '        write_new(self.out / "intent-summary.json", {"selectionSha256": frozen_sha, "scores": outputs,\n'
            '                  "postTrainingCapacity": selection["postTrainingCapacity"],\n')
        patched = source.encode("utf-8")
        # Compile-only verification cannot import or execute the study.
        compile(patched, target, "exec")
        patched_path = HERE / "fixtures" / variant / "run_registered_study.py.txt"
        outputs[patched_path] = patched
        patch.extend(difflib.unified_diff(original.decode("utf-8").splitlines(keepends=True),
                                         source.splitlines(keepends=True),
                                         fromfile="a/" + target, tofile="b/" + target))
        entries[variant] = {"sourcePath": target, "sourceGitBlob": git_blob, "sourceSha256": expected,
                            "originalFixture": str(original_path.relative_to(HERE)),
                            "patchedFixture": str(patched_path.relative_to(HERE)),
                            "patchedSourceSha256": digest(patched)}
    patch_raw = "".join(patch).encode("utf-8")
    outputs[HERE / "pr508-post-capacity-gate.patch"] = patch_raw
    manifest = {"schemaVersion": 1, "status": "REVIEWABLE_REPAIR_ARTIFACT_NOT_APPLIED_TO_FOREIGN_BRANCH",
                "reviewedRepository": "Ullmann27/device-ai", "reviewedPullRequest": 508,
                "reviewedCommit": REVIEWED_COMMIT,
                "benchmarkSourcePath": "experimental/weight-space-20261104/LmCapacityBenchmark.kt",
                "benchmarkSourceSha256": "d88541b436089b6df04f50035cffa79378b939dabbf03ef77a9ca1acd52bbd9c",
                "sources": entries, "fragmentSha256": digest(fragment_raw),
                "generatorSha256": digest(Path(__file__).read_bytes()), "patchSha256": digest(patch_raw),
                "historicalRegistrationsModified": False, "historicalReceiptsModified": False,
                "actualModelProcesses": 0, "realHoldoutRead": False,
                "newExecutionRequiresProspectiveRegistration": True}
    outputs[HERE / "source-manifest.json"] = (json.dumps(manifest, indent=2) + "\n").encode("utf-8")
    for path, raw in outputs.items():
        if args.write_artifacts:
            path.write_bytes(raw)
        elif not path.is_file() or path.read_bytes() != raw:
            raise RuntimeError("ARTIFACT_DOES_NOT_MATCH_PINNED_REPAIR: " + str(path))
    print(json.dumps({"status": "REGENERATED" if args.write_artifacts else "SOURCE_BOUND_ARTIFACTS_MATCH",
                      "sources": len(entries), "outputs": len(outputs), "actualModelProcesses": 0,
                      "patchSha256": digest(patch_raw)}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
