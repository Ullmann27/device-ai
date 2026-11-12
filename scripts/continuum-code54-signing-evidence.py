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


# A single, known host-only defect may be deferred to physical Fold7 testing.
# This never marks the native PDF test green and never waives any other case.
PDF_CASE = ("at.heinz.deviceai.PdfIntakeNativeDecodeProbeUiTest",
            "actualProviderReadRendersAndExtractsSyntheticPdf")
ALLOWED_SKIPS = {
    *(("at.heinz.deviceai.chief.ChiefPatternE2ETest", n) for n in (
        "s2b_pending_workerHasOwnBriefOwnToolAllowlistAndReportsBack",
        "s4b_pending_profileDiarySharedLayers",
        "s5b_pending_textFileSkillLibraryLoadedOnDemand",
        "s6_pending_routineReportsOnlyWhenSomethingIsNew",
        "s7_pending_agentMessagesUrgentWakesInfoWaits",
        "s8d_pending_limitGuardIsWiredBeforeWorkerStart")),
    *(("at.heinz.deviceai.chief.FirstThinkingRunE2ETest", n) for n in (
        "schritt1b_ziel_turnLifecycleQuittiertGenauEinmal",
        "schritt3b_erinnern_geteiltesWissenUeberLayersSharedSearch",
        "schritt4c_handeln_alsChiefWorkerPoolJobMitWerkzeugScope",
        "schritt4d_handeln_eingeschleusterBefehlInWerkzeugausgabeWirdIgnoriert",
        "schritt6b_lernen_hypotheseUndTagebuchEintrag",
        "schritt7b_naechsterSchritt_wirdStrukturiertGeplantUndAngestossen")),
    ("at.heinz.deviceai.chief.ChiefWorkspaceWiringHostTest",
     "actualPoolMailboxAndTwoFixedNodeContainersReturnOnlyObservations"),
    ("at.heinz.deviceai.chief.ChiefWorkspaceWiringHostTest",
     "cancellationOfRealContainerClosesRunnerAndReportsToChiefMailbox"),
    ("at.heinz.deviceai.learning.ContinuumContrastiveParityTest",
     "actualNodeKotlinNodeTrainingParity"),
    ("at.heinz.deviceai.learning.ContinuumContrastiveParityTest",
     "actualNodeKotlinNodeSwb2TrainingParityAfter350Rounds"),
    ("at.heinz.deviceai.learning.Cre1InferenceEncoderTest",
     "trainedNodeCheckpointMatchesKotlinFeaturesAndScores"),
}
assert len(ALLOWED_SKIPS) == 17
DEVICE_TEST_EXCEPTION = "DEVICE_TEST_NATIVE_PDF_EXCEPTION_VERIFIED"
OWNER_AUTH_ID = "OWNER_CHAT_APPROVAL_2026_11_12_CODE54_PDF_DEVICE_TEST_ONLY"
EVIDENCE_NAME = "Code54-Android-Source-QA-Receipts"


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
    gate = binding.get("release_gate")
    require(gate in ("STRICT_NATIVE_PDF_PASS", DEVICE_TEST_EXCEPTION),
            "RELEASE_GATE_BLOCKED")
    if gate == DEVICE_TEST_EXCEPTION:
        require(binding.get("release_scope") == "UNPUBLISHED_FOLD7_DEVICE_TEST_ONLY",
                "DEVICE_TEST_SCOPE_REQUIRED")
        exception = binding.get("owner_exception") or {}
        require(exception.get("authorization_id") == OWNER_AUTH_ID and
                exception.get("source_sha") == SOURCE and
                exception.get("test_class") == PDF_CASE[0] and
                exception.get("test_name") == PDF_CASE[1] and
                exception.get("device_pdf_test_status") == "NOT_RUN" and
                exception.get("publication") == "FORBIDDEN" and
                exception.get("fold7_acceptance_required") is True,
                "OWNER_PDF_EXCEPTION_SCOPE")
        expected_evidence = binding.get("qa_evidence") or {}
        require(expected_evidence.get("name") == EVIDENCE_NAME and
                type(expected_evidence.get("id")) is int and
                expected_evidence["id"] > 0 and
                type(expected_evidence.get("bytes")) is int and
                1000 <= expected_evidence["bytes"] < 1_000_000_000 and
                isinstance(expected_evidence.get("digest"), str) and
                re.fullmatch(r"sha256:[0-9a-f]{64}",
                             expected_evidence["digest"]) is not None,
                "DEVICE_TEST_QA_EVIDENCE_NOT_BOUND")
    else:
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
    require(run.get("status") == "completed", "QA_RUN_INCOMPLETE")
    device_exception = binding.get("release_gate") == DEVICE_TEST_EXCEPTION
    if device_exception:
        # This is an explicitly *failed* host run. It is NOT a full QA PASS.
        require(run.get("conclusion") == "failure",
                "DEVICE_TEST_EXPECTED_FAILED_HOST_RUN")
    else:
        require(run.get("conclusion") == "success",
                "STRICT_ANDROID_QA_NOT_GREEN")
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
        expected_job = ("failure" if device_exception and name == "android-gates"
                        else "success")
        require(job.get("id") == job_id and job.get("run_id") == RUN and
                job.get("head_sha") == SOURCE and
                job.get("status") == "completed" and
                job.get("conclusion") == expected_job, "QA_JOB_OUTCOME")
        required = set(required_steps)
        if device_exception and name == "android-gates":
            required.add("Preserve actual Android receipts")
        for step_name in required:
            hits = [x for x in job.get("steps", []) if x.get("name") == step_name]
            expected_step = ("failure" if device_exception and
                             step_name == "Full unfiltered Android, native PDF strict"
                             else "success")
            require(len(hits) == 1 and hits[0].get("status") == "completed" and
                    hits[0].get("conclusion") == expected_step,
                    "QA_REQUIRED_STEP")
    matches = [x for x in artifacts.get("artifacts", [])
               if x.get("name") == ARTIFACT_NAME]
    require(len(matches) == 1, "QA_ARTIFACT_COUNT")
    artifact = matches[0]
    require(artifact.get("id") == ARTIFACT_ID and
            artifact.get("size_in_bytes") == ARTIFACT_SIZE and
            artifact.get("digest") == ARTIFACT_DIGEST and
            artifact.get("expired") is False, "QA_ARTIFACT_IDENTITY")
    if device_exception:
        expected_evidence = binding["qa_evidence"]
        matches = [x for x in artifacts.get("artifacts", [])
                   if x.get("name") == EVIDENCE_NAME]
        require(len(matches) == 1, "QA_EVIDENCE_COUNT")
        proof = matches[0]
        require(proof.get("id") == expected_evidence["id"] and
                proof.get("size_in_bytes") == expected_evidence["bytes"] and
                proof.get("digest") == expected_evidence["digest"] and
                proof.get("expired") is False,
                "QA_EVIDENCE_IDENTITY")
        evidence_origin = proof.get("workflow_run") or {}
        require(evidence_origin.get("id") == RUN and
                evidence_origin.get("head_sha") == SOURCE and
                evidence_origin.get("head_branch") == QA_BRANCH and
                evidence_origin.get("repository_id") == REPOSITORY_ID and
                evidence_origin.get("head_repository_id") == REPOSITORY_ID,
                "QA_EVIDENCE_ORIGIN")

    origin = artifact.get("workflow_run") or {}
    require(origin.get("id") == RUN and origin.get("head_sha") == SOURCE and
            origin.get("head_branch") == QA_BRANCH and
            origin.get("repository_id") == REPOSITORY_ID and
            origin.get("head_repository_id") == REPOSITORY_ID,
            "QA_ARTIFACT_ORIGIN")



def verify_pdf_only_host_exception(binding, evidence_zip):
    """Check the actual complete Gradle journal; exactly one PDF host failure."""
    require(binding.get("release_gate") == DEVICE_TEST_EXCEPTION,
            "PDF_EXCEPTION_NOT_APPROVED")
    require(evidence_zip is not None and evidence_zip.is_file() and
            not evidence_zip.is_symlink(), "QA_EVIDENCE_ZIP_MISSING")
    expected = binding["qa_evidence"]
    require(evidence_zip.stat().st_size == expected["bytes"] and
            "sha256:" + digest_file(evidence_zip) == expected["digest"],
            "QA_EVIDENCE_ZIP_DIGEST")
    with zipfile.ZipFile(evidence_zip) as archive:
        infos = archive.infolist()
        names = [item.filename for item in infos]
        require(len(names) == len(set(names)), "QA_EVIDENCE_DUPLICATE_MEMBER")
        require(len(infos) < 10000, "QA_EVIDENCE_TOO_MANY_FILES")
        for item in infos:
            part = PurePosixPath(item.filename)
            require(not part.is_absolute() and ".." not in part.parts and
                    "\\" not in item.filename and
                    not stat.S_ISLNK(item.external_attr >> 16) and
                    not item.flag_bits & 1, "QA_EVIDENCE_UNSAFE_MEMBER")
        journals = [n for n in names if n == "_temp/code54-full/junit-progress.ndjson"]
        require(len(journals) == 1, "FULL_ANDROID_JOURNAL_MISSING")
        info = archive.getinfo(journals[0])
        require(1_000_000 <= info.file_size <= 60_000_000,
                "FULL_ANDROID_JOURNAL_SIZE")
        journal = archive.read(journals[0])
        data = [decode_json(line) for line in journal.splitlines() if line.strip()]
        starts = [entry for entry in data if entry.get("type") == "run_start"]
        ends = [entry for entry in data if entry.get("type") == "suite_end"]
        cases = [entry for entry in data if entry.get("type") == "test_result"]
        require(len(starts) == 1 and len(ends) == 1 and
                4_800 <= len(cases) <= 6_000, "FULL_ANDROID_INCOMPLETE")
        start, end = starts[0], ends[0]
        source = start.get("source") or {}
        end_source = end.get("sourceEnd") or {}
        require(start.get("task") == ":app:testDebugUnitTest" and
                start.get("includePatterns") == [] and
                start.get("excludePatterns") == [] and
                source.get("head") == SOURCE and source.get("tree") == TREE and
                source.get("status") == "" and
                end_source.get("head") == SOURCE and
                end_source.get("tree") == TREE and
                end_source.get("status") == "" and
                end.get("sourceUnchanged") is True,
                "FULL_ANDROID_SOURCE_OR_FILTER")
        props = (start.get("environment") or {}).get("projectProperties") or {}
        require(not props.get("continuumPdfAllowMissingHostNative"),
                "NATIVE_PDF_WAS_BYPASSED")
        seen, failed, skipped, passed = set(), [], set(), 0
        for entry in cases:
            ident = (entry.get("class"), entry.get("name"))
            require(all(isinstance(x, str) and x for x in ident) and
                    ident not in seen, "FULL_ANDROID_DUPLICATE_OR_UNKNOWN_CASE")
            seen.add(ident)
            result = entry.get("result")
            if result == "SUCCESS":
                passed += 1
            elif result == "SKIPPED":
                require(ident in ALLOWED_SKIPS, "UNAPPROVED_TEST_SKIP")
                skipped.add(ident)
            elif result == "FAILURE":
                require(ident == PDF_CASE, "UNRELATED_ANDROID_TEST_FAILURE")
                failed.append(entry)
            else:
                raise Rejected("UNRECOGNIZED_ANDROID_TEST_RESULT")
        require(len(failed) == 1 and skipped == ALLOWED_SKIPS and
                end.get("tests") == len(cases) and
                end.get("passed") == passed and
                end.get("failed") == 1 and
                end.get("skipped") == 17 and
                end.get("result") == "FAILURE",
                "PDF_EXCEPTION_NOT_EXACTLY_ONE_FAILURE")
        errors = failed[0].get("exceptions") or []
        detail = "\n".join(str(item.get("type", "")) + "\n" +
                           str(item.get("message", "")) + "\n" +
                           str(item.get("stackTrace", "")) for item in errors)
        require("NoSuchMethodError" in detail and
                "java.io.FileDescriptor" in detail and
                "getOwnerId" in detail and
                ("IoUtils" in detail or "PdfProcessor" in detail),
                "PDF_HOST_FAILURE_FINGERPRINT")
        # Bind independent upload metadata, not just the Gradle test log.
        source_receipt = [n for n in names if n == "_temp/CODE54-SOURCE.txt"]
        require(len(source_receipt) == 1 and
                archive.getinfo(source_receipt[0]).file_size <= 4096,
                "QA_SOURCE_RECEIPT_MISSING")
        receipt = archive.read(source_receipt[0]).decode("utf-8")
        require(f"source_sha={SOURCE}" in receipt and
                f"source_tree={TREE}" in receipt and
                "original_signer=NOT_USED" in receipt,
                "QA_SOURCE_RECEIPT_INVALID")
    return {
        "mode": "UNPUBLISHED_FOLD7_DEVICE_TEST_ONLY",
        "source_sha": SOURCE,
        "native_pdf_host_result": "FAILURE_HOST_ONLY",
        "native_pdf_fold7_result": "NOT_RUN",
        "full_suite_pass": False,
        "total": len(cases), "passed": passed, "skipped": 17, "failed": 1,
    }


def verify_archives(binding, unsigned_path, output, evidence_path=None):
    verify_binding(binding)
    require(not output.exists(), "OUTPUT_ALREADY_EXISTS")
    if binding.get("release_gate") == DEVICE_TEST_EXCEPTION:
        verify_pdf_only_host_exception(binding, evidence_path)
    else:
        require(evidence_path is None, "STRICT_NO_EXCEPTION_EVIDENCE")
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
    elif mode == "archives" and len(paths) in (2, 3):
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
