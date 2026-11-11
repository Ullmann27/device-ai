#!/usr/bin/env python3
"""Validate frozen Code53 signing evidence."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import sys
import zipfile

SOURCE = "050a57cb019dcc1b7fe6cfa2fb5c11176b048e05"
TREE = "539ea3aac0af04644a015b3fb69ba865af80d1a8"
WORKFLOW = "07efc77652638cfa40a67270475aa2c08ded5c76"
RUN = 42985335421
JOB = 130405169817
REPOSITORY = "Ullmann27/device-ai"
REPOSITORY_ID = 1415878393
QA_BRANCH = "integration/continuum-final-visibility-20261111"
QA_PATH = ".github/workflows/continuum-code53-frozen-qa.yml"
PACKAGE = "at.heinz.deviceai.continuumlocal"
VALIDATOR = "2a0aeaef48bb5482f0da304ee24e3a69a66d3fa2a86c81885436d28b7c8a535f"
APK_NAME = "Continuum-Code53-UNSIGNED-NOT-INSTALLABLE.apk"
ARTIFACT_NAMES = {
    "unsigned": "Code53-050a57cb-UNSIGNED-NOT-INSTALLABLE",
    "evidence": "Code53-050a57cb-QA-Evidence",
}
IDENTITY = {"package": PACKAGE, "versionCode": 53, "versionName": "0.3.52",
            "abi": "arm64-v8a", "minSdk": 26, "targetSdk": 36, "debuggable": False}
REQUIRED_STEPS = {
    "Bind source and separate workflow identity",
    "Source safety, full Node and critical subset",
    "Backend and software rendering gates",
    "Maya settings lifecycle preflight",
    "Full Android suite and configured NewApi lint",
    "Build and validate unsigned original-package Code53",
    "Preserve validated unsigned candidate",
    "Preserve source-bound evidence",
}


class Rejected(ValueError):
    pass


def require(condition: bool, code: str) -> None:
    if not condition:
        raise Rejected(code)


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, "DUPLICATE_JSON_KEY")
        result[key] = value
    return result


def decode_json(data):
    return json.loads(data, object_pairs_hook=unique_object)


def digest_file(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def verify_binding(binding: dict) -> None:
    require(binding.get("source_sha") == SOURCE, "BINDING_SOURCE")
    require(binding.get("source_tree") == TREE, "BINDING_TREE")
    require(binding.get("workflow_sha") == WORKFLOW, "BINDING_WORKFLOW")
    require(binding.get("qa_run_id") == RUN and binding.get("qa_run_attempt") == 1,
            "BINDING_RUN")
    require(binding.get("qa_job_id") == JOB, "BINDING_JOB")
    require(re.fullmatch(r"[0-9a-f]{64}", binding.get("apk_sha256", "")) is not None,
            "BINDING_APK_DIGEST")
    require(type(binding.get("apk_bytes")) is int and 1_000_000 <= binding["apk_bytes"] <= 2_000_000_000,
            "BINDING_APK_SIZE")
    result_path = binding.get("validator_result_path", "")
    require(result_path in {"code53-package/RESULT.json", "_temp/code53-package/RESULT.json"},
            "BINDING_RESULT_PATH")
    ids = []
    for kind, name in ARTIFACT_NAMES.items():
        artifact = binding.get(kind, {})
        require(artifact.get("name") == name, "BINDING_ARTIFACT_NAME")
        require(type(artifact.get("id")) is int and artifact["id"] > 0, "BINDING_ARTIFACT_ID")
        require(type(artifact.get("bytes")) is int and 0 < artifact["bytes"] <= 2_000_000_000,
                "BINDING_ARTIFACT_SIZE")
        require(re.fullmatch(r"sha256:[0-9a-f]{64}", artifact.get("digest", "")) is not None,
                "BINDING_ARTIFACT_DIGEST")
        ids.append(artifact["id"])
    require(len(set(ids)) == 2, "BINDING_ARTIFACT_IDS_COLLIDE")


def verify_metadata(binding, source, approved, run_id, run, jobs, artifacts, commit):
    verify_binding(binding)
    require(source == approved == SOURCE, "OWNER_SOURCE_APPROVAL")
    require(str(run_id) == str(RUN), "RUN_INPUT")
    require(commit.get("sha") == SOURCE and commit.get("tree", {}).get("sha") == TREE,
            "REMOTE_SOURCE_TREE")
    require(run.get("id") == RUN and run.get("run_attempt") == 1, "QA_RUN_ATTEMPT")
    require(run.get("head_sha") == WORKFLOW and run.get("head_branch") == QA_BRANCH,
            "QA_WORKFLOW_IDENTITY")
    require(run.get("path") == QA_PATH and run.get("event") == "push", "QA_WORKFLOW_EVENT")
    require(run.get("status") == "completed" and run.get("conclusion") == "success",
            "QA_RUN_NOT_GREEN")
    for key in ("repository", "head_repository"):
        repository = run.get(key, {})
        require(repository.get("id") == REPOSITORY_ID and repository.get("full_name") == REPOSITORY,
                "QA_REPOSITORY")
    matching = [job for job in jobs.get("jobs", []) if job.get("name") == "validate-frozen-source"]
    require(len(matching) == 1, "QA_JOB_COUNT")
    job = matching[0]
    require(job.get("id") == JOB and job.get("run_id") == RUN and job.get("head_sha") == WORKFLOW,
            "QA_JOB_IDENTITY")
    require(job.get("status") == "completed" and job.get("conclusion") == "success",
            "QA_JOB_NOT_GREEN")
    for name in REQUIRED_STEPS:
        steps = [step for step in job.get("steps", []) if step.get("name") == name]
        require(len(steps) == 1 and steps[0].get("status") == "completed"
                and steps[0].get("conclusion") == "success", "QA_REQUIRED_STEP")
    for kind in ARTIFACT_NAMES:
        expected = binding[kind]
        found = [entry for entry in artifacts.get("artifacts", []) if entry.get("name") == expected["name"]]
        require(len(found) == 1, "QA_ARTIFACT_COUNT")
        artifact = found[0]
        require(artifact.get("id") == expected["id"] and artifact.get("digest") == expected["digest"]
                and artifact.get("size_in_bytes") == expected["bytes"] and artifact.get("expired") is False,
                "QA_ARTIFACT_IDENTITY")
        origin = artifact.get("workflow_run", {})
        require(origin.get("id") == RUN and origin.get("head_sha") == WORKFLOW
                and origin.get("head_branch") == QA_BRANCH
                and origin.get("repository_id") == REPOSITORY_ID
                and origin.get("head_repository_id") == REPOSITORY_ID, "QA_ARTIFACT_ORIGIN")


def check_zip(path: Path, expected: dict):
    require(path.is_file() and not path.is_symlink(), "ARCHIVE_FILE")
    require(path.stat().st_size == expected["bytes"], "ARCHIVE_SIZE")
    require("sha256:" + digest_file(path) == expected["digest"], "ARCHIVE_DIGEST")
    archive = zipfile.ZipFile(path)
    try:
        names = archive.namelist()
        require(len(names) == len(set(names)), "ARCHIVE_DUPLICATE_MEMBER")
        for entry in archive.infolist():
            member = PurePosixPath(entry.filename)
            require(not member.is_absolute() and ".." not in member.parts and "\\" not in entry.filename,
                    "ARCHIVE_UNSAFE_MEMBER")
            require(not stat.S_ISLNK(entry.external_attr >> 16) and not entry.flag_bits & 1,
                    "ARCHIVE_UNSAFE_MEMBER")
    except BaseException:
        archive.close()
        raise
    return archive


def small_member(archive, name: str, limit=1_000_000):
    entry = archive.getinfo(name)
    require(0 < entry.file_size <= limit, "EVIDENCE_MEMBER_SIZE")
    return archive.read(entry)


def verify_report(binding, report):
    require(report.get("status") == "PASS" and report.get("artifactStatus") == "UNSIGNED_NOT_INSTALLABLE",
            "VALIDATOR_RESULT")
    require(report.get("expectedSource") == SOURCE and report.get("expectedTree") == TREE,
            "VALIDATOR_SOURCE_TREE")
    require(report.get("validatorSha256") == VALIDATOR, "VALIDATOR_DIGEST")
    require(report.get("sha256") == binding["apk_sha256"] and report.get("bytes") == binding["apk_bytes"],
            "VALIDATOR_APK_DIGEST")
    require(report.get("identity") == IDENTITY, "VALIDATOR_PACKAGE_VERSION")
    before, after = report.get("sourceBefore", {}), report.get("sourceAfter", {})
    require(before == after and before.get("commit") == SOURCE and before.get("tree") == TREE
            and before.get("status") == "", "VALIDATOR_SOURCE_CHANGED")
    require(report.get("signing") == "NOT_USED" and report.get("installation") == "NOT_RUN"
            and report.get("Fold7") == "NOT_RUN" and report.get("GPU") == "NOT_RUN", "VALIDATOR_BOUNDARIES")
    archive = report.get("archive", {})
    require(archive.get("zipCrc") == "PASS" and archive.get("jarSignatureFiles") == []
            and archive.get("apkSigningBlock") is False, "VALIDATOR_UNSIGNED_ARCHIVE")
    require(report.get("zipalign16KiB") == "PASS"
            and report.get("unsignedDiagnostic") == "EXPECTED_MISSING_SIGNATURE", "VALIDATOR_UNSIGNED_ALIGNMENT")


def verify_archives(binding, unsigned_path: Path, evidence_path: Path, output: Path):
    verify_binding(binding)
    require(not output.exists(), "OUTPUT_ALREADY_EXISTS")
    with check_zip(unsigned_path, binding["unsigned"]) as unsigned, check_zip(evidence_path, binding["evidence"]) as evidence:
        report = decode_json(small_member(evidence, binding["validator_result_path"]))
        verify_report(binding, report)
        receipt = small_member(unsigned, "BUILD-INFO.txt")
        expected_receipt = (f"source_sha={SOURCE}\nworkflow_sha={WORKFLOW}\n"
                            "Fold7=NOT_RUN\noriginal_signature=NOT_USED\n").encode()
        require(receipt == expected_receipt, "BUILD_RECEIPT")
        sums = small_member(unsigned, "SHA256SUMS.txt")
        require(sums == f'{binding["apk_sha256"]}  {APK_NAME}\n'.encode(), "CHECKSUM_RECEIPT")
        entry = unsigned.getinfo(APK_NAME)
        require(entry.file_size == binding["apk_bytes"], "APK_SIZE")
        output.mkdir(mode=0o700)
        apk = output / APK_NAME
        with unsigned.open(entry) as source, apk.open("xb") as target:
            shutil.copyfileobj(source, target)
        require(digest_file(apk) == binding["apk_sha256"], "APK_DIGEST")
        (output / "BUILD-INFO.txt").write_bytes(receipt)
        (output / "SHA256SUMS.txt").write_bytes(sums)


def main(arguments):
    mode, binding_path, *paths = arguments
    binding = decode_json(Path(binding_path).read_bytes())
    if mode == "metadata" and len(paths) == 7:
        source, approved, run_id, *documents = paths
        verify_metadata(binding, source, approved, run_id,
                        *(decode_json(Path(path).read_bytes()) for path in documents))
    elif mode == "archives" and len(paths) == 3:
        verify_archives(binding, *(Path(path) for path in paths))
    else:
        raise Rejected("BAD_MODE_OR_ARGUMENTS")
    print("CHECKPOINT=CODE53_" + mode.upper() + "_VERIFIED")


if __name__ == "__main__":
    try:
        main(sys.argv[1:])
    except (Rejected, OSError, ValueError, KeyError, TypeError, zipfile.BadZipFile) as error:
        code = str(error) if isinstance(error, Rejected) else type(error).__name__
        print("::error::CODE53_EVIDENCE_" + code, file=sys.stderr)
        raise SystemExit(69)
