#!/usr/bin/env python3
"""Build or verify a source-bound combined capacity/choice repair artifact.

The previously verified capacity patch is an immutable input. V1 remains exactly
its prior repaired fixture; only v2/v3 gain the explicitly pinned study-choice
authorization. No live runner, registration, receipt or model file is changed.
"""
from __future__ import annotations

import argparse
import ast
import difflib
import hashlib
import json
from pathlib import Path


HERE = Path(__file__).resolve().parent
REPAIR = HERE.parents[1] / "recovery-gate-repair"
CURRENT_SOURCE = "9ceffc26a2159c8dc726c1f13d2882119159c130"
BASE_PATCH_SHA = "c69cb427e4ba0624720380edef9f7f0ce3e418591c10bd0a35f56590ad67e38f"
BASE_REPAIRED = {
    "v1": "b1bed4338bb187ae6d835a46368cc05773ae96143559af368361103b64a7b327",
    "long": "638d02eaded31c3d7be7d978d6bcf08319dda9db35e948f1e0142008c2d5d387",
    "suffix": "907d1c9635dc0e375c8d0ef68afa6f85f6c27655758a1895281f74ae0cfc0067",
}


def digest(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def replace_once(source: str, before: str, after: str) -> str:
    require(source.count(before) == 1, "BASE_SOURCE_SHAPE_CHANGED: " + before.strip().splitlines()[0])
    return source.replace(before, after, 1)


def audit_method_changes(before: bytes, after: bytes, variant: str) -> dict:
    original, patched = ast.parse(before), ast.parse(after)
    require(len(original.body) == len(patched.body), "unexpected module-level additions")
    changed, added = [], []
    for old, new in zip(original.body, patched.body):
        if isinstance(old, ast.ClassDef) and old.name == "Study":
            require(isinstance(new, ast.ClassDef) and new.name == "Study", "Study class changed identity")
            old_methods = {node.name: node for node in old.body if isinstance(node, ast.FunctionDef)}
            new_methods = {node.name: node for node in new.body if isinstance(node, ast.FunctionDef)}
            require(len(old_methods) == len(old.body) and len(new_methods) == len(new.body), "unexpected class member")
            added = sorted(set(new_methods) - set(old_methods))
            require(not set(old_methods) - set(new_methods), "existing method removed")
            changed = sorted(name for name in old_methods if ast.dump(old_methods[name]) != ast.dump(new_methods[name]))
        elif isinstance(old, ast.FunctionDef) and old.name == "main" and variant != "v1":
            require(isinstance(new, ast.FunctionDef) and new.name == "main", "CLI entry point changed identity")
        else:
            require(ast.dump(old) == ast.dump(new), "unexpected change outside authorization/test CLI")
    require(added == ([] if variant == "v1" else ["study_choice_authorization"]), "unexpected new method")
    require(changed == ([] if variant == "v1" else ["test"]), "training/selection/capacity methods changed")
    return {"comparedWith": "previously_verified_capacity_patch", "addedStudyMethods": added,
            "changedStudyMethods": changed, "testCliExtended": variant != "v1",
            "trainingSelectionAndCapacityMethodsUnchanged": True}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write-artifacts", action="store_true")
    args = parser.parse_args()
    base_manifest = json.loads((REPAIR / "source-manifest.json").read_text())
    require(base_manifest["patchSha256"] == BASE_PATCH_SHA
            == digest((REPAIR / "pr508-post-capacity-gate.patch").read_bytes()), "PREVIOUS_CAPACITY_PATCH_CHANGED")
    fragment_path = HERE / "study_choice_authorization.py.fragment"
    fragment_raw = fragment_path.read_bytes()
    fragment = fragment_raw.decode("utf-8").rstrip() + "\n\n"
    outputs = {}
    entries = {}
    combined_patch, incremental_patch = [], []
    for variant, item in base_manifest["sources"].items():
        original = (REPAIR / item["originalFixture"]).read_bytes()
        base_repaired = (REPAIR / item["patchedFixture"]).read_bytes()
        require(digest(original) == item["sourceSha256"], "ORIGINAL_SOURCE_CHANGED: " + variant)
        require(digest(base_repaired) == item["patchedSourceSha256"] == BASE_REPAIRED[variant],
                "PREVIOUS_REPAIRED_SOURCE_CHANGED: " + variant)
        source = base_repaired.decode("utf-8")
        if variant != "v1":
            source = replace_once(source, "    def test(self) -> None:\n", fragment + "    def test(self) -> None:\n")
            source = replace_once(source, "        outputs = {}\n",
                "        test_authorization = self.study_choice_authorization(frozen, frozen_sha, selection)\n"
                "        if any(self.logs.glob(\"test-*.tsv\")) or any(self.logs.glob(\"test-*.receipt.json\")):\n"
                "            raise RuntimeError(\"FINAL_TEST_ALREADY_STARTED\")\n"
                "        outputs = {}\n")
            source = replace_once(source, '        for arm in ["S0", "W0", "S1", "W1"]:\n',
                '        for arm in ["S0", "W0", "S1", "W1"]:\n'
                '            if test_authorization != self.study_choice_authorization(frozen, frozen_sha, selection):\n'
                '                raise RuntimeError("STUDY_CHOICE_AUTHORIZATION_CHANGED")\n')
            tail = ('        if sha(frozen) != frozen_sha or selection != self.freeze_evidence():\n'
                    '            raise RuntimeError("FREEZE_CHANGED_DURING_TEST")\n')
            source = replace_once(source, tail, tail +
                '        if test_authorization != self.study_choice_authorization(frozen, frozen_sha, selection):\n'
                '            raise RuntimeError("STUDY_CHOICE_AUTHORIZATION_CHANGED")\n')
            summary = '        write_new(self.out / "intent-summary.json", {"selectionSha256": frozen_sha, "scores": outputs,\n'
            source = replace_once(source, summary, summary + '                  "testAuthorization": test_authorization,\n')
            source = replace_once(source, '    args = parser.parse_args()\n',
                '    parser.add_argument("--development-choice", type=Path)\n'
                '    parser.add_argument("--before-test-verification", type=Path)\n'
                '    parser.add_argument("--before-test-verification-sha256")\n'
                '    args = parser.parse_args()\n'
                '    if args.phase == "test" and any(getattr(args, name) is None for name in (\n'
                '            "development_choice", "before_test_verification", "before_test_verification_sha256")):\n'
                '        parser.error("test requires --development-choice, --before-test-verification, "\n'
                '                     "and --before-test-verification-sha256")\n')
        patched = source.encode("utf-8")
        compile(patched, item["sourcePath"], "exec")
        changes = audit_method_changes(base_repaired, patched, variant)
        folder = HERE / "fixtures" / variant
        original_path = folder / "original_run_registered_study.py.txt"
        patched_path = folder / "run_registered_study.py.txt"
        outputs[original_path] = original
        outputs[patched_path] = patched
        combined_patch.extend(difflib.unified_diff(original.decode().splitlines(keepends=True),
            source.splitlines(keepends=True), fromfile="a/" + item["sourcePath"], tofile="b/" + item["sourcePath"]))
        incremental_patch.extend(difflib.unified_diff(base_repaired.decode().splitlines(keepends=True),
            source.splitlines(keepends=True), fromfile="a/" + item["sourcePath"], tofile="b/" + item["sourcePath"]))
        entries[variant] = {"sourcePath": item["sourcePath"], "sourceGitBlob": item["sourceGitBlob"],
            "sourceSha256": digest(original), "capacityRepairedSha256": digest(base_repaired),
            "patchedSourceSha256": digest(patched), "originalFixture": str(original_path.relative_to(HERE)),
            "patchedFixture": str(patched_path.relative_to(HERE)), "methodChangeAudit": changes}
    combined = "".join(combined_patch).encode()
    incremental = "".join(incremental_patch).encode()
    outputs[HERE / "pr508-capacity-and-choice-gates.patch"] = combined
    outputs[HERE / "choice-guard-on-capacity-repair.patch"] = incremental
    manifest = {"schemaVersion": 1, "status": "PROSPECTIVE_COMBINED_REPAIR_ARTIFACT_NOT_APPLIED_TO_PR508",
        "reviewedRepository": "Ullmann27/device-ai", "reviewedPullRequest": 508,
        "reviewedCommit": CURRENT_SOURCE, "previousCapacityRepairPatchSha256": BASE_PATCH_SHA,
        "sources": entries, "fragmentSha256": digest(fragment_raw),
        "generatorSha256": digest(Path(__file__).read_bytes()), "combinedPatchSha256": digest(combined),
        "incrementalPatchSha256": digest(incremental),
        "additionalArgumentsRequiredForV2V3TestOnly": ["--development-choice", "--before-test-verification",
                                                       "--before-test-verification-sha256"],
        "v1ByteIdenticalToPreviousCapacityRepair": True,
        "expectedPretestHashAuthority": "Explicit caller/coordinator reference; not cryptographic publication attestation",
        "historicalRegistrationsModified": False, "historicalReceiptsModified": False,
        "actualModelProcesses": 0, "realHoldoutRead": False,
        "newExecutionRequiresProspectiveRegistration": True}
    outputs[HERE / "choice-guard-manifest.json"] = (json.dumps(manifest, indent=2) + "\n").encode()
    for path, raw in outputs.items():
        if args.write_artifacts:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(raw)
        else:
            require(path.is_file() and path.read_bytes() == raw, "ARTIFACT_DOES_NOT_MATCH_PINNED_REPAIR: " + str(path))
    print(json.dumps({"status": "REGENERATED" if args.write_artifacts else "SOURCE_BOUND_ARTIFACTS_MATCH",
                      "sources": len(entries), "outputs": len(outputs), "actualModelProcesses": 0,
                      "combinedPatchSha256": digest(combined), "incrementalPatchSha256": digest(incremental)}, indent=2))


if __name__ == "__main__":
    main()
