#!/usr/bin/env python3
"""Audit pinned current study sources and published selection identities only.

This reads explicit Git text blobs. It never imports a study controller, starts
a model, opens a dataset or reads checkpoint/JAR/tokenizer bytes. Historical
registrations and receipts remain unchanged; all output is confined to this
source-audit directory.
"""
from __future__ import annotations

import argparse
import ast
import hashlib
import json
from pathlib import Path
import subprocess


HERE = Path(__file__).resolve().parent
PIN = "9ceffc26a2159c8dc726c1f13d2882119159c130"
PREVIOUS = "be4e49600fed1db4c697230bb8e42c150dbfbdaf"
BASE = "experimental/lm1-training-recovery-20261104/"
EVIDENCE = "docs/evidence/lm1-training-recovery-20261104/"
HANDOFF = "docs/research/omega/LM1-TRAINING-RECOVERY-HANDOFF-20261104.md"
PATCH_SHA = "c69cb427e4ba0624720380edef9f7f0ce3e418591c10bd0a35f56590ad67e38f"
REPAIR = HERE.parents[1] / "recovery-gate-repair"
REGISTRATIONS = {
    "v1": BASE + "registration.json",
    "v2": BASE + "long-study/registration.json",
    "v3": BASE + "suffix-study/registration.json",
}
SELECTOR = BASE + "suffix-study/choose_development_study.py"
ALLOWED = {
    BASE + "run_registered_study.py", BASE + "long-study/run_registered_study.py",
    BASE + "suffix-study/run_registered_study.py", SELECTOR,
    *REGISTRATIONS.values(), BASE + "INTENT_PROTOCOL.md",
    BASE + "long-study/INTENT_LONG_PROTOCOL.md", BASE + "suffix-study/SUFFIX_PROTOCOL.md",
    EVIDENCE + "final-selection/development-study-choice.json",
    EVIDENCE + "final-selection/selection-freeze.json",
    EVIDENCE + "final-selection/before-test-verification.json", HANDOFF,
}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def digest(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


class TextArchive:
    def __init__(self, repo: Path):
        self.repo = repo
        self.sources: dict[str, dict] = {}
        self.cache: dict[str, bytes] = {}

    def git(self, *arguments: str) -> bytes:
        return subprocess.run(["git", *arguments], cwd=self.repo, check=True,
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout

    def read(self, path: str) -> bytes:
        require(path in ALLOWED, "outside explicit source-only allowlist: " + path)
        if path not in self.cache:
            raw = self.git("show", PIN + ":" + path)
            raw.decode("utf-8")
            self.cache[path] = raw
            self.sources[path] = {
                "sha256": digest(raw), "bytes": len(raw),
                "gitBlobSha1": hashlib.sha1(b"blob " + str(len(raw)).encode() + b"\0" + raw).hexdigest(),
            }
        return self.cache[path]

    def json(self, path: str) -> dict:
        return json.loads(self.read(path))


def study_methods(raw: bytes) -> dict[str, ast.FunctionDef]:
    tree = ast.parse(raw)
    classes = [node for node in tree.body if isinstance(node, ast.ClassDef) and node.name == "Study"]
    require(len(classes) == 1, "expected exactly one Study class")
    methods = [node for node in classes[0].body if isinstance(node, ast.FunctionDef)]
    require(len({node.name for node in methods}) == len(methods), "duplicate Study methods")
    return {node.name: node for node in methods}


def call_names(node: ast.AST) -> list[str]:
    calls = []
    for child in ast.walk(node):
        if isinstance(child, ast.Call):
            if isinstance(child.func, ast.Attribute):
                calls.append(child.func.attr)
            elif isinstance(child.func, ast.Name):
                calls.append(child.func.id)
    return sorted(set(calls))


def audit(repo: Path) -> dict:
    archive = TextArchive(repo)
    repair_manifest = json.loads((REPAIR / "source-manifest.json").read_text())
    require(repair_manifest["reviewedCommit"] == PREVIOUS, "unexpected original repair binding")
    require(digest((REPAIR / "pr508-post-capacity-gate.patch").read_bytes()) == PATCH_SHA
            == repair_manifest["patchSha256"], "reviewed patch changed")
    sources = {}
    for variant, expected in repair_manifest["sources"].items():
        raw = archive.read(expected["sourcePath"])
        original = (REPAIR / expected["originalFixture"]).read_bytes()
        repaired = (REPAIR / expected["patchedFixture"]).read_bytes()
        require(raw == original and digest(raw) == expected["sourceSha256"],
                "current source differs from the previously reproduced original: " + variant)
        require(digest(repaired) == expected["patchedSourceSha256"], "reviewed repair fixture changed")
        methods = study_methods(raw)
        source_text = raw.decode()
        require("post_capacity_evidence" not in methods, "a new gate requires separate review")
        require("development-study-choice.json" not in source_text
                and "before-test-verification.json" not in source_text,
                "a new coordinator binding requires separate review")
        sources[variant] = {
            "sourcePath": expected["sourcePath"], "sha256": digest(raw),
            "gitBlobSha1": archive.sources[expected["sourcePath"]]["gitBlobSha1"],
            "byteIdenticalToReproducedOriginal": True, "byteIdenticalToRepairedFixture": raw == repaired,
            "reviewedRepairedSha256": digest(repaired), "postCapacityValidatorPresent": False,
            "freezeEvidenceCalls": call_names(methods["freeze_evidence"]),
            "testCalls": call_names(methods["test"]),
            "studyMethods": sorted(methods),
            "currentTestFunctionSha256": digest(ast.get_source_segment(source_text, methods["test"]).encode()),
        }
    selector_raw = archive.read(SELECTOR)
    previous_selector = archive.git("show", PREVIOUS + ":" + SELECTOR)
    require(selector_raw == previous_selector, "selector source changed since previous review")
    selector_tree = ast.parse(selector_raw)
    functions = {node.name: node for node in selector_tree.body if isinstance(node, ast.FunctionDef)}
    require("freeze_evidence" in call_names(functions["main"]), "selector no longer uses Study freeze evidence")
    registrations = {}
    for version, path in REGISTRATIONS.items():
        raw = archive.read(path)
        require(raw == archive.git("show", PREVIOUS + ":" + path), "historical registration was modified")
        registration = json.loads(raw)
        checked = {}
        for source in sorted(set(registration["files"]) & ALLOWED):
            actual = digest(archive.read(source))
            require(actual == registration["files"][source], "registered source bytes differ: " + source)
            checked[source] = actual
        registrations[version] = {"path": path, "sha256": digest(raw),
            "unchangedSincePreviousSourceCommit": True, "checkedRegisteredSourceFiles": checked,
            "registeredFileCount": len(registration["files"]),
            "trainingProcessSeconds": registration["trainingProcessSeconds"],
            "capacityProcessSeconds": registration["capacityProcessSeconds"],
            "testSha256ReportedOnly": registration["files"][BASE + "intent-data/test.sealed.tsv"]}
    choice_path = EVIDENCE + "final-selection/development-study-choice.json"
    freeze_path = EVIDENCE + "final-selection/selection-freeze.json"
    before_path = EVIDENCE + "final-selection/before-test-verification.json"
    choice, freeze, before = (archive.json(path) for path in (choice_path, freeze_path, before_path))
    require(choice["selectedStudy"] == before["selectedStudy"] == "v3", "different published study choice")
    require(choice["selectedFreezePayload"] == freeze and before["choiceMatchesFreezeExactly"] is True,
            "published choice and freeze disagree")
    require(before["choiceSha256"] == digest(archive.read(choice_path))
            and before["freezeSha256"] == digest(archive.read(freeze_path)), "coordinator receipt hash mismatch")
    require(freeze["registrationSha256"] == registrations["v3"]["sha256"]
            and freeze["sourceCommit"] == before["selectedSourceCommit"] == PREVIOUS,
            "selected freeze has different source or registration")
    require(choice["testEvaluated"] is False and before["allFinalTestLogsAbsent"] is True,
            "published pre-test state changed")
    require("postTrainingCapacity" not in freeze,
            "new bound freeze evidence requires separate review")
    protocol_raw = archive.read(BASE + "suffix-study/SUFFIX_PROTOCOL.md")
    require(b"The coordinator then freezes only the selected version" in protocol_raw,
            "coordinator protocol changed")
    handoff_raw = archive.read(HANDOFF)
    changed = archive.git("diff", "--name-status", PREVIOUS, PIN).decode().splitlines()
    require(all(line.split("\t")[-1].startswith("docs/") for line in changed),
            "new executable files since the reviewed sources")
    published_paths = archive.git("ls-tree", "-r", "--name-only", PIN, EVIDENCE).decode().splitlines()
    final_test_paths = [path for path in published_paths if "/logs/test-" in path]
    require(not final_test_paths, "new final-test artifacts require separate review")
    return {
        "schemaVersion": 1, "status": "CURRENT_FREEZE_CONSISTENT_REPAIR_NOT_INTEGRATED",
        "reviewedCommit": PIN, "previousReviewedSourceCommit": PREVIOUS,
        "patchSha256": PATCH_SHA, "runnerComparisons": sources,
        "selector": {"sourcePath": SELECTOR, "sha256": digest(selector_raw),
                     "unchangedSincePreviousSourceCommit": True,
                     "capacityAuditCalls": call_names(functions["audit_post_capacity"]),
                     "mainCalls": call_names(functions["main"])},
        "registrations": registrations, "changedPathsSincePreviousSourceCommit": changed,
        "publishedCoordinatorEvidence": {
            "choicePath": choice_path, "freezePath": freeze_path, "verificationPath": before_path,
            "selectedStudy": choice["selectedStudy"], "choiceSha256": digest(archive.read(choice_path)),
            "freezeSha256": digest(archive.read(freeze_path)), "verificationSha256": digest(archive.read(before_path)),
            "choiceAndFreezePayloadEqual": True, "coordinatorRecordedHashesMatch": True,
            "selectedSourceCommit": freeze["sourceCommit"],
            "selectedRegistrationSha256": freeze["registrationSha256"],
            "boundPostCapacityObjectInFreeze": False,
            "selectedArmPostCapacityMarkers": {arm: freeze["arms"][arm].get("postTrainingCapacity") for arm in ("S1", "W1")},
            "testEvaluatedReported": False, "publishedFinalTestPaths": final_test_paths,
            "coordinatorEvidenceReadByCurrentStudyTest": False,
            "interpretation": "Published coordinator comparison is consistent; the standalone test entry point does not enforce the version-choice artifact. This is not evidence of an actual wrong-study execution.",
        },
        "handoffContainsStaleRunningV3Description": b"final registered suffix comparison v3 running" in handoff_raw,
        "sourceInventory": dict(sorted(archive.sources.items())),
        "modelProcessesStarted": 0, "modelAndDatasetBytesRead": 0,
        "historicalFilesModified": False,
        "scope": "PINNED_TEXT_SOURCE_AND_PUBLISHED_COORDINATOR_IDENTITY_REVIEW_ONLY",
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path.cwd())
    parser.add_argument("--output", type=Path, default=HERE / "source-review.json")
    args = parser.parse_args()
    output = args.output.resolve()
    require(output.is_relative_to(HERE), "output must stay in source-audit directory")
    report = audit(args.repo.resolve())
    output.write_text(json.dumps(report, indent=2, ensure_ascii=False, allow_nan=False) + "\n")
    print(json.dumps({"status": report["status"], "sources": len(report["sourceInventory"]),
                      "unchangedOriginalRunners": len(report["runnerComparisons"]),
                      "publishedCoordinatorHashesMatch": True, "modelProcessesStarted": 0}, indent=2))


if __name__ == "__main__":
    main()
