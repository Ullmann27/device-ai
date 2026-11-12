#!/usr/bin/env python3
"""Fail-closed Code54 original-signer evidence gate; never reads signing secrets."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import sys
import zipfile

SOURCE = "9713854bfb01d699f666058ac44379735307b66a"
TREE = "b815b5f2896b279edef15cad85aead82a85951af"
WORKFLOW_BLOB = "5a621b46802225b08bc085ad4e5202ac531a507c"
RUN = 43134158265
ANDROID_JOB = 130916433622
UNSIGNED_JOB = 130916433295
ARTIFACT_ID = 13908580815
ARTIFACT_SIZE = 73553790
ARTIFACT_DIGEST = "sha256:855e9877ed3eef8b96cf9da2dc96931dfd93a339800b3ce76a6894e5fb4a787b"
APK_SHA = "eda9d67a56d3e1bdc934eee8eda7a5ff9d2ea649f5b767587ef078d8faf840ab"
APK_BYTES = 102864175
APK_NAME = "Continuum-Code54-UNSIGNED-NOT-INSTALLABLE.apk"
ARTIFACT_NAME = "Code54-UNSIGNED-NOT-INSTALLABLE"
REPOSITORY = "Ullmann27/device-ai"
REPOSITORY_ID = 1415878393
QA_BRANCH = "integrate/omega-apk54-20261111"
QA_PATH = ".github/workflows/continuum-code54-qa.yml"
IDENTITY = "package: name='at.heinz.deviceai.continuumlocal' versionCode='54' versionName='0.3.53'"
REQUIRED_ANDROID_STEPS = {
    "Verify immutable source",
    "All three real UI classes, unchanged assertions",
    "Configured NewApi lint",
    "Full unfiltered Android, native PDF strict",
}
REQUIRED_UNSIGNED_STEPS = {
    "Build original-package ARM64 unsigned transport (not a release)",
    "Upload strictly unsigned APK with provenance",
}


class Rejected(ValueError):
    pass


def require(condition, code):
    if not condition:
        raise Rejected(code)


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, "DUPLICATE_JSON_FIELD")
        result[key] = value
    return result


def decode_json(data):
    return json.loads(data, object_pairs_hook=unique_object)


def digest_file(path):
    sha = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            sha.update(chunk)
    return sha.hexdigest()


def verify_binding(binding):
    # The committed binding is intentionally BLOCKED. Changing this field
    # requires a separate reviewed owner decision and a new commit.
    require(binding.get("release_gate") == "STRICT_NATIVE_PDF_PASS",
            "RELEASE_GATE_BLOCKED")
    require(binding.get("release_scope") == "UNPUBLISHED_DRAFT_ONLY",
            "PUBLISH_SCOPE_FORBIDDEN")
    expected = {
        "source_sha": SOURCE, "source_tree": TREE,
        "workflow_blob_sha": WORKFLOW_BLOB,
        "qa_branch": QA_BRANCH, "qa_workflow_path": QA_PATH,
        "qa_run_id": RUN, "qa_run_attempt": 1,
        "qa_job_android_id": ANDROID_JOB, "qa_job_unsigned_id": UNSIGNED_JOB,
        "apk_sha256": APK_SHA, "apk_bytes": APK_BYTES,
    }
    for key, value in expected.items():
        require(type(binding.get(key)) is type(value) and binding.get(key) == value,
                "BINDING_" + key.upper())
    require(binding.get("unsigned") == {
        "id": ARTIFACT_ID, "name": ARTIFACT_NAME,
        "bytes": ARTIFACT_SIZE, "digest": ARTIFACT_DIGEST,
    }, "BINDING_UNSIGNED_ARTIFACT")


def verify_metadata(binding, source, approved, run_id, run, jobs, artifacts, commit):
    verify_binding(binding)
    require(source == approved == SOURCE, "OWNER_SOURCE_APPROVAL")
    require(str(run_id) == str(RUN), "QA_RUN_INPUT")
    require(commit.get("sha") == SOURCE and
            commit.get("tree", {}).get("sha") == TREE, "REMOTE_SOURCE_TREE")
    require(run.get("id") == RUN and run.get("run_attempt") == 1,
            "QA_RUN_ATTEMPT")
    require(run.get("head_sha") == SOURCE and
            run.get("head_branch") == QA_BRANCH and
            run.get("event") == "push" and
            run.get("path", "").split("@", 1)[0] == QA_PATH,
            "QA_SOURCE_WORKFLOW")
    require(run.get("status") == "completed" and
            run.get("conclusion") == "success", "STRICT_ANDROID_QA_NOT_GREEN")
    for name in ("repository", "head_repository"):
        entry = run.get(name) or {}
        require(entry.get("id") == REPOSITORY_ID and
                entry.get("full_name") == REPOSITORY, "QA_REPOSITORY")
    for name, job_id, required_steps in (
        ("android-gates", ANDROID_JOB, REQUIRED_ANDROID_STEPS),
        ("unsigned-arm64-transport", UNSIGNED_JOB, REQUIRED_UNSIGNED_STEPS),
    ):
        matches = [x for x in jobs.get("jobs", []) if x.get("name") == name]
        require(len(matches) == 1, "QA_JOB_COUNT")
        job = matches[0]
        require(job.get("id") == job_id and job.get("run_id") == RUN and
                job.get("head_sha") == SOURCE and
                job.get("status") == "completed" and
                job.get("conclusion") == "success", "QA_JOB_NOT_GREEN")
        for step_name in required_steps:
            hits = [x for x in job.get("steps", []) if x.get("name") == step_name]
            require(len(hits) == 1 and hits[0].get("status") == "completed" and
                    hits[0].get("conclusion") == "success", "QA_REQUIRED_STEP")
    matches = [x for x in artifacts.get("artifacts", [])
               if x.get("name") == ARTIFACT_NAME]
    require(len(matches) == 1, "QA_ARTIFACT_COUNT")
    artifact = matches[0]
    require(artifact.get("id") == ARTIFACT_ID and
            artifact.get("size_in_bytes") == ARTIFACT_SIZE and
            artifact.get("digest") == ARTIFACT_DIGEST and
            artifact.get("expired") is False, "QA_ARTIFACT_IDENTITY")
    origin = artifact.get("workflow_run") or {}
    require(origin.get("id") == RUN and origin.get("head_sha") == SOURCE and
            origin.get("head_branch") == QA_BRANCH and
            origin.get("repository_id") == REPOSITORY_ID and
            origin.get("head_repository_id") == REPOSITORY_ID,
            "QA_ARTIFACT_ORIGIN")


def verify_archives(binding, unsigned_path, output):
    verify_binding(binding)
    require(not output.exists(), "OUTPUT_ALREADY_EXISTS")
    require(unsigned_path.is_file() and not unsigned_path.is_symlink(),
            "UNSIGNED_ARCHIVE_MISSING")
    require(unsigned_path.stat().st_size == ARTIFACT_SIZE and
            "sha256:" + digest_file(unsigned_path) == ARTIFACT_DIGEST,
            "UNSIGNED_ZIP_DIGEST")
    with zipfile.ZipFile(unsigned_path) as archive:
        infos = archive.infolist()
        allowed = {"BADGING.txt", APK_NAME, "BUILD-INFO.txt", "SHA256SUMS.txt"}
        names = [entry.filename for entry in infos]
        require(len(names) == len(allowed) and set(names) == allowed,
                "UNSIGNED_ARCHIVE_MEMBERS")
        for entry in infos:
            part = PurePosixPath(entry.filename)
            require(not part.is_absolute() and ".." not in part.parts
                    and "\\" not in entry.filename and
                    not stat.S_ISLNK(entry.external_attr >> 16) and
                    not entry.flag_bits & 1, "UNSIGNED_ARCHIVE_UNSAFE")
        apk = archive.getinfo(APK_NAME)
        require(apk.file_size == APK_BYTES, "UNSIGNED_APK_SIZE")
        for name in ("BUILD-INFO.txt", "SHA256SUMS.txt", "BADGING.txt"):
            require(0 < archive.getinfo(name).file_size < 10000,
                    "UNSIGNED_RECEIPT_SIZE")
        expected = (
            "status=UNSIGNED_QA_NOT_INSTALLABLE\n"
            f"source={SOURCE}\n"
            "package=at.heinz.deviceai.continuumlocal\n"
            "versionCode=54\n"
            "versionName=0.3.53\n"
            "originalSigner=NOT_USED\n"
            "fold7=NOT_RUN\n"
            "qualityGates=INDEPENDENT_NOT_IMPLIED\n"
        )
        require(archive.read("BUILD-INFO.txt") == expected.encode(),
                "UNSIGNED_BUILD_INFO")
        require(archive.read("SHA256SUMS.txt") ==
                f"{APK_SHA}  {APK_NAME}\n".encode(),
                "UNSIGNED_APK_SUMS")
        badging = archive.read("BADGING.txt").decode("utf-8")
        require(IDENTITY in badging and "native-code: 'arm64-v8a'" in badging,
                "UNSIGNED_BADGING")
        output.mkdir(mode=0o700)
        destination = output / APK_NAME
        with archive.open(apk) as source, destination.open("xb") as target:
            shutil.copyfileobj(source, target, length=1024 * 1024)
        require(digest_file(destination) == APK_SHA, "UNSIGNED_APK_DIGEST")
        (output / "BUILD-INFO.txt").write_text(expected, encoding="utf-8")
        (output / "SHA256SUMS.txt").write_bytes(archive.read("SHA256SUMS.txt"))


def main(arguments):
    require(len(arguments) >= 2, "ARGUMENTS")
    mode, binding_path, *paths = arguments
    binding = decode_json(Path(binding_path).read_bytes())
    if mode == "metadata" and len(paths) == 7:
        source, approved, run_id, *documents = paths
        verify_metadata(binding, source, approved, run_id,
                        *(decode_json(Path(path).read_bytes()) for path in documents))
    elif mode == "archives" and len(paths) == 2:
        verify_archives(binding, *(Path(path) for path in paths))
    else:
        raise Rejected("ARGUMENTS")
    print("CHECKPOINT=CODE54_" + mode.upper() + "_VERIFIED")


if __name__ == "__main__":
    try:
        main(sys.argv[1:])
    except (Rejected, OSError, ValueError, KeyError, TypeError,
            zipfile.BadZipFile) as error:
        label = str(error) if isinstance(error, Rejected) else type(error).__name__
        print("::error::CODE54_EVIDENCE_" + label, file=sys.stderr)
        raise SystemExit(69)
