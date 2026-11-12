#!/usr/bin/env python3
"""No-secret Code54 signer contract tests: strict native PDF, run and archive binding."""
from __future__ import annotations
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
MODULE = ROOT / "scripts/continuum-code54-signing-evidence.py"
spec = importlib.util.spec_from_file_location("code54_evidence", MODULE)
evidence = importlib.util.module_from_spec(spec)
spec.loader.exec_module(evidence)


def fixture():
    binding = json.loads((ROOT / ".github/continuum/code54-owner-signing-binding.json").read_text())
    binding["release_gate"] = "STRICT_NATIVE_PDF_PASS"  # isolated synthetic owner-PASS case only
    binding["release_scope"] = "UNPUBLISHED_DRAFT_ONLY"
    repo = {"id": evidence.REPOSITORY_ID, "full_name": evidence.REPOSITORY}
    run = {"id": evidence.RUN, "run_attempt": 1,
           "head_sha": evidence.SOURCE, "head_branch": evidence.QA_BRANCH,
           "path": evidence.QA_PATH, "event": "push",
           "status": "completed", "conclusion": "success",
           "repository": repo, "head_repository": copy.deepcopy(repo)}
    jobs = {"jobs": []}
    for name, job_id, steps in (
        ("android-gates", evidence.ANDROID_JOB, evidence.REQUIRED_ANDROID_STEPS),
        ("unsigned-arm64-transport", evidence.UNSIGNED_JOB, evidence.REQUIRED_UNSIGNED_STEPS),
    ):
        jobs["jobs"].append({"id": job_id, "name": name,
                             "run_id": evidence.RUN, "head_sha": evidence.SOURCE,
                             "status": "completed", "conclusion": "success",
                             "steps": [{"name": step, "status": "completed", "conclusion": "success"}
                                       for step in sorted(steps)]})
    artifact = {"id": evidence.ARTIFACT_ID, "name": evidence.ARTIFACT_NAME,
                "size_in_bytes": evidence.ARTIFACT_SIZE, "digest": evidence.ARTIFACT_DIGEST,
                "expired": False, "workflow_run": {
                    "id": evidence.RUN, "head_sha": evidence.SOURCE,
                    "head_branch": evidence.QA_BRANCH,
                    "repository_id": evidence.REPOSITORY_ID,
                    "head_repository_id": evidence.REPOSITORY_ID,
                }}
    return {"binding": binding, "source": evidence.SOURCE,
            "approved": evidence.SOURCE, "run_id": str(evidence.RUN), "run": run,
            "jobs": jobs, "artifacts": {"artifacts": [artifact]},
            "commit": {"sha": evidence.SOURCE, "tree": {"sha": evidence.TREE}}}


def mutate(data, path, value):
    dest = data
    for field in path[:-1]:
        dest = dest[field]
    dest[path[-1]] = value


class MetadataContracts(unittest.TestCase):
    def test_real_binding_is_intentionally_unusable(self):
        binding = json.loads((ROOT / ".github/continuum/code54-owner-signing-binding.json").read_text())
        with self.assertRaisesRegex(evidence.Rejected, "RELEASE_GATE_BLOCKED"):
            evidence.verify_binding(binding)
        self.assertEqual(binding["release_gate"],
                         "DEVICE_TEST_NATIVE_PDF_EXCEPTION_PENDING_EVIDENCE")

    def test_synthetic_completed_green_identity(self):
        evidence.verify_metadata(**fixture())

    def test_owner_would_need_explicit_frozen_source(self):
        for key in ("source", "approved"):
            d = fixture()
            d[key] = "f" * 40
            with self.assertRaisesRegex(evidence.Rejected, "OWNER_SOURCE_APPROVAL"):
                evidence.verify_metadata(**d)

    def test_frozen_binding_and_artifact_require_exact_bytes(self):
        changes = [
            (("binding", "source_sha"), "f" * 40),
            (("binding", "source_tree"), "f" * 40),
            (("binding", "workflow_blob_sha"), "f" * 40),
            (("binding", "qa_run_id"), evidence.RUN + 1),
            (("binding", "qa_job_android_id"), evidence.ANDROID_JOB + 1),
            (("binding", "apk_sha256"), "f" * 64),
            (("binding", "unsigned", "id"), evidence.ARTIFACT_ID + 1),
            (("binding", "unsigned", "digest"), "sha256:" + "f" * 64),
            (("binding", "release_scope"), "PUBLISHED"),
            (("binding", "release_gate"), "OWNER_EXCEPTION_UNDOCUMENTED"),
        ]
        for path, value in changes:
            with self.subTest(path=path):
                d = fixture()
                mutate(d, path, value)
                with self.assertRaises(evidence.Rejected):
                    evidence.verify_metadata(**d)

    def test_native_pdf_is_not_skippable_or_failed(self):
        for value in ("skipped", "failure", "cancelled", None):
            with self.subTest(value=value):
                d = fixture()
                step = next(s for s in d["jobs"]["jobs"][0]["steps"]
                            if s["name"] == "Full unfiltered Android, native PDF strict")
                step["conclusion"] = value
                with self.assertRaisesRegex(evidence.Rejected, "QA_REQUIRED_STEP"):
                    evidence.verify_metadata(**d)

    def test_run_status_and_qa_job_are_strict(self):
        changes = [
            (("run", "conclusion"), "failure"), (("run", "status"), "in_progress"),
            (("run", "event"), "workflow_dispatch"), (("run", "run_attempt"), 2),
            (("run", "head_sha"), "f" * 40), (("run", "head_branch"), "main"),
            (("run", "head_repository", "id"), 1),
            (("jobs", "jobs", 0, "conclusion"), "failure"),
            (("jobs", "jobs", 1, "conclusion"), "skipped"),
            (("jobs", "jobs", 0, "id"), 1),
            (("artifacts", "artifacts", 0, "digest"), "sha256:" + "f" * 64),
            (("artifacts", "artifacts", 0, "expired"), True),
            (("artifacts", "artifacts", 0, "workflow_run", "id"), 1),
            (("commit", "tree", "sha"), "f" * 40),
        ]
        for path, value in changes:
            with self.subTest(path=path):
                d = fixture()
                mutate(d, path, value)
                with self.assertRaises(evidence.Rejected):
                    evidence.verify_metadata(**d)

    def test_duplicate_records_rejected(self):
        for section, key in (("jobs", "jobs"), ("artifacts", "artifacts")):
            for double in (True, False):
                with self.subTest(section=section, double=double):
                    d = fixture()
                    seq = d[section][key]
                    d[section][key] = seq + [copy.deepcopy(seq[0])] if double else []
                    with self.assertRaises(evidence.Rejected):
                        evidence.verify_metadata(**d)

    def test_duplicate_json_fields_rejected(self):
        with self.assertRaisesRegex(evidence.Rejected, "DUPLICATE_JSON_FIELD"):
            evidence.decode_json('{"status":"PASS","status":"FAIL"}')

    def test_executable_cli_fails_before_any_signer_secret(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / "locked.json"
            p.write_text((ROOT / ".github/continuum/code54-owner-signing-binding.json").read_text())
            result = subprocess.run([sys.executable, "-I", str(MODULE), "metadata",
                                     str(p), evidence.SOURCE, evidence.SOURCE, str(evidence.RUN),
                                     "/path/that/must/not/be/read"], capture_output=True, text=True)
            self.assertEqual(result.returncode, 69)
            self.assertIn("CODE54_EVIDENCE_ARGUMENTS", result.stderr)


class ArchiveContracts(unittest.TestCase):
    def archive_case(self, directory, edit=None, wrong_apk=False, extra=None):
        apk_bytes = b"synthetic APK only -- not a real Android package"
        digest = hashlib.sha256(apk_bytes).hexdigest()
        receipt = ("status=UNSIGNED_QA_NOT_INSTALLABLE\n"
                   f"source={evidence.SOURCE}\n"
                   "package=at.heinz.deviceai.continuumlocal\n"
                   "versionCode=54\nversionName=0.3.53\n"
                   "originalSigner=NOT_USED\nfold7=NOT_RUN\n"
                   "qualityGates=INDEPENDENT_NOT_IMPLIED\n")
        files = {
            evidence.APK_NAME: b"altered" if wrong_apk else apk_bytes,
            "BUILD-INFO.txt": receipt.encode(),
            "SHA256SUMS.txt": f"{digest}  {evidence.APK_NAME}\n".encode(),
            "BADGING.txt": (evidence.IDENTITY + "\nnative-code: 'arm64-v8a'\n").encode(),
        }
        if edit:
            edit(files)
        path = directory / "unsigned.zip"
        with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as arc:
            for name, contents in files.items():
                arc.writestr(name, contents)
            if extra:
                arc.writestr(extra, b"unsafe")
        return path, directory / "output", digest, len(apk_bytes)

    def test_valid_archive_and_mutants(self):
        with tempfile.TemporaryDirectory() as name:
            folder = Path(name)
            path, output, digest, size = self.archive_case(folder)
            with patch.object(evidence, "APK_SHA", digest), \
                 patch.object(evidence, "APK_BYTES", size), \
                 patch.object(evidence, "ARTIFACT_SIZE", path.stat().st_size), \
                 patch.object(evidence, "ARTIFACT_DIGEST",
                                   "sha256:" + evidence.digest_file(path)):
                binding = fixture()["binding"]
                binding["unsigned"]["bytes"] = evidence.ARTIFACT_SIZE
                binding["unsigned"]["digest"] = evidence.ARTIFACT_DIGEST
                binding["apk_sha256"] = digest
                binding["apk_bytes"] = size
                evidence.verify_archives(binding, path, output)
                self.assertEqual((output / evidence.APK_NAME).read_bytes(),
                                 b"synthetic APK only -- not a real Android package")
                with self.assertRaisesRegex(evidence.Rejected, "OUTPUT_ALREADY_EXISTS"):
                    evidence.verify_archives(binding, path, output)

    def test_tamper_and_unsafe_zip_fail(self):
        for case in ("tampered apk", "wrong receipt", "wrong badging", "extra", "path escape"):
            with self.subTest(case=case), tempfile.TemporaryDirectory() as name:
                def edit(files):
                    if case == "wrong receipt":
                        files["BUILD-INFO.txt"] = b"status=PASS\n"
                    if case == "wrong badging":
                        files["BADGING.txt"] = b"package: name='other'\n"
                directory = Path(name)
                path, output, digest, size = self.archive_case(
                    directory, edit=edit, wrong_apk=case=="tampered apk",
                    extra="../unsafe" if case=="path escape" else ("unexpected" if case=="extra" else None))
                with patch.object(evidence, "APK_SHA", digest), \
                     patch.object(evidence, "APK_BYTES", size), \
                     patch.object(evidence, "ARTIFACT_SIZE", path.stat().st_size), \
                     patch.object(evidence, "ARTIFACT_DIGEST",
                                       "sha256:" + evidence.digest_file(path)):
                    binding = fixture()["binding"]
                    binding["unsigned"]["bytes"] = evidence.ARTIFACT_SIZE
                    binding["unsigned"]["digest"] = evidence.ARTIFACT_DIGEST
                    binding["apk_sha256"] = digest
                    binding["apk_bytes"] = size
                    with self.assertRaises(evidence.Rejected):
                        evidence.verify_archives(binding, path, output)


class WorkflowContracts(unittest.TestCase):
    def test_original_workflow_remains_manual_and_owner_guarded(self):
        s = (ROOT / ".github/workflows/continuum-code52-sign-release.yml").read_text()
        self.assertIn("workflow_dispatch:", s)
        self.assertNotRegex(s, r"(?m)^  (push|pull_request|workflow_run|schedule):")
        self.assertIn("environment: continuum-original-signing", s)
        self.assertEqual(s.count("secrets."), 2)
        self.assertIn("refs/heads/chatgpt/code52-owner-signing-20261109", s)

    def test_signer_still_pins_previous_profiles_and_draft_only(self):
        s = (ROOT / "scripts/continuum-code52-github-signer.sh").read_text()
        self.assertIn('profile="code52"', s)
        self.assertIn('profile="code53"', s)
        self.assertIn('profile="code54"', s)
        self.assertIn('code="54"', s)
        self.assertIn('name_version="0.3.53"', s)
        self.assertIn("CODE54_METADATA_MISMATCH", s)
        self.assertIn("CODE54_ARCHIVE_MISMATCH", s)
        self.assertIn("RELEASE_GATE_BLOCKED", MODULE.read_text())
        self.assertIn("--draft --latest=false", s)
        self.assertNotRegex(s, r"--draft=false|--latest=true|gh release (edit|publish)")
        self.assertNotIn("set -x", s)
        self.assertLess(s.index("CODE54_METADATA_MISMATCH"),
                        s.index("ORIGINAL_P12_BASE64_INVALID"))



def exception_fixture():
    data = fixture()
    binding = data["binding"]
    locked = json.loads((ROOT / ".github/continuum/code54-owner-signing-binding.json").read_text())
    binding["release_gate"] = evidence.DEVICE_TEST_EXCEPTION
    binding["release_scope"] = "UNPUBLISHED_FOLD7_DEVICE_TEST_ONLY"
    binding["owner_exception"] = locked["owner_exception"]
    binding["qa_evidence"] = {
        "id": 13909000000, "name": evidence.EVIDENCE_NAME,
        "bytes": 45000, "digest": "sha256:" + "a" * 64
    }
    data["run"]["conclusion"] = "failure"
    android = data["jobs"]["jobs"][0]
    android["conclusion"] = "failure"
    full = next(x for x in android["steps"] if
                x["name"] == "Full unfiltered Android, native PDF strict")
    full["conclusion"] = "failure"
    android["steps"].append({
        "name": "Preserve actual Android receipts",
        "status": "completed", "conclusion": "success"
    })
    data["artifacts"]["artifacts"].append({
        "id": 13909000000, "name": evidence.EVIDENCE_NAME,
        "size_in_bytes": 45000, "digest": "sha256:" + "a" * 64,
        "expired": False, "workflow_run": {
            "id": evidence.RUN, "head_sha": evidence.SOURCE,
            "head_branch": evidence.QA_BRANCH,
            "repository_id": evidence.REPOSITORY_ID,
            "head_repository_id": evidence.REPOSITORY_ID
        }
    })
    return data


def journal_fixture():
    source = {
        "head": evidence.SOURCE, "tree": evidence.TREE,
        "status": "", "branch": evidence.QA_BRANCH
    }
    start = {
        "type": "run_start", "task": ":app:testDebugUnitTest",
        "source": source, "includePatterns": [], "excludePatterns": [],
        "environment": {"projectProperties": {"continuumPdfAllowMissingHostNative": None}},
    }
    cases = [
        {"type": "test_result", "class": "at.heinz.deviceai.fake.ContractTest",
         "name": f"otherCase{i:05d}", "result": "SUCCESS", "padding": "p" * 170}
        for i in range(4839)
    ]
    for cls, name in sorted(evidence.ALLOWED_SKIPS):
        cases.append({"type": "test_result", "class": cls, "name": name,
                      "result": "SKIPPED"})
    cases.append({
        "type": "test_result", "class": evidence.PDF_CASE[0],
        "name": evidence.PDF_CASE[1], "result": "FAILURE",
        "exceptions": [{
            "type": "java.lang.NoSuchMethodError",
            "message": "long java.io.FileDescriptor.getOwnerId$()",
            "stackTrace": "at libcore.io.IoUtils.acquireRawFd\\nat android.graphics.pdf.PdfProcessor.create"
        }]
    })
    end = {
        "type": "suite_end", "result": "FAILURE",
        "tests": len(cases), "passed": 4839, "failed": 1, "skipped": 17,
        "sourceEnd": source, "sourceUnchanged": True,
    }
    return start, cases, end


def write_pdf_evidence(root, journal=None):
    path = root / "qa.zip"
    if journal is None:
        journal = journal_fixture()
    start, cases, end = journal
    lines = [start] + cases + [end]
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("code54-full/junit-progress.ndjson",
                         "\n".join(json.dumps(item, separators=(",", ":")) for item in lines))
        archive.writestr("CODE54-SOURCE.txt", (
            f"source_sha={evidence.SOURCE}\nsource_tree={evidence.TREE}\n"
            "qa_only=true\noriginal_signer=NOT_USED\nFold7=NOT_RUN\n"))
    return path


class DeviceTestExceptionContracts(unittest.TestCase):
    def test_narrow_signed_draft_exception_accepts_only_exact_synthetic_run(self):
        evidence.verify_metadata(**exception_fixture())

    def test_exception_rejects_missing_owner_data_and_any_unrelated_failure(self):
        mutations = [
            (("binding", "owner_exception", "authorization_id"), "forged"),
            (("binding", "owner_exception", "source_sha"), "f" * 40),
            (("binding", "owner_exception", "publication"), "ALLOWED"),
            (("binding", "owner_exception", "device_pdf_test_status"), "PASS"),
            (("binding", "qa_evidence", "digest"), "sha256:" + "f" * 64),
            (("run", "conclusion"), "success"),
            (("jobs", "jobs", 0, "conclusion"), "success"),
            (("jobs", "jobs", 0, "steps", 0, "conclusion"), "failure"),
            (("jobs", "jobs", 0, "steps", -1, "conclusion"), "failure"),
            (("artifacts", "artifacts", 1, "expired"), True),
        ]
        for path, value in mutations:
            with self.subTest(path=path):
                data = exception_fixture()
                mutate(data, path, value)
                with self.assertRaises(evidence.Rejected):
                    evidence.verify_metadata(**data)

    def test_exception_validates_4857_complete_cases_and_pdf_fingerprint(self):
        with tempfile.TemporaryDirectory() as name:
            path = write_pdf_evidence(Path(name))
            data = exception_fixture()
            data["binding"]["qa_evidence"]["bytes"] = path.stat().st_size
            data["binding"]["qa_evidence"]["digest"] = "sha256:" + evidence.digest_file(path)
            result = evidence.verify_pdf_only_host_exception(data["binding"], path)
            self.assertEqual(result["total"], 4857)
            self.assertEqual(result["failed"], 1)
            self.assertEqual(result["native_pdf_fold7_result"], "NOT_RUN")
            self.assertFalse(result["full_suite_pass"])

    def test_exception_rejects_corrupted_or_unrelated_cases(self):
        test_cases = ("another failure", "unknown skip", "missing case", "fake PDF PASS",
                      "bad native fingerprint", "new source", "filtered")
        for case in test_cases:
            with self.subTest(case=case), tempfile.TemporaryDirectory() as name:
                start, cases, end = journal_fixture()
                if case == "another failure":
                    cases[0]["result"] = "FAILURE"
                elif case == "unknown skip":
                    cases[0]["result"] = "SKIPPED"
                elif case == "missing case":
                    cases.pop(1)
                elif case == "fake PDF PASS":
                    cases[-1]["result"] = "SUCCESS"
                elif case == "bad native fingerprint":
                    cases[-1]["exceptions"][0]["message"] = "noSuchMethod elsewhere"
                    cases[-1]["exceptions"][0]["stackTrace"] = "unrelated"
                elif case == "new source":
                    start["source"] = {**start["source"], "head": "f" * 40}
                elif case == "filtered":
                    start["includePatterns"] = ["fake"]
                path = write_pdf_evidence(Path(name), (start, cases, end))
                binding = exception_fixture()["binding"]
                binding["qa_evidence"]["bytes"] = path.stat().st_size
                binding["qa_evidence"]["digest"] = "sha256:" + evidence.digest_file(path)
                with self.assertRaises(evidence.Rejected):
                    evidence.verify_pdf_only_host_exception(binding, path)

    def test_unsigned_archive_cannot_substitute_native_pdf_evidence(self):
        data = exception_fixture()
        with tempfile.TemporaryDirectory() as name:
            p = Path(name)
            with self.assertRaises(evidence.Rejected):
                evidence.verify_archives(data["binding"], p / "unsigned.zip", p / "output", None)

    def test_device_test_scope_does_not_remove_protected_owner_gate(self):
        signer = (ROOT / "scripts/continuum-code52-github-signer.sh").read_text()
        workflow = (ROOT / ".github/workflows/continuum-code52-sign-release.yml").read_text()
        self.assertIn("CODE54_QA_EVIDENCE_DOWNLOAD_FAILED", signer)
        self.assertIn("CODE54_ARCHIVE_MISMATCH", signer)
        self.assertIn("publication_permitted=false", signer)
        self.assertIn("--draft --latest=false", signer)
        self.assertIn("environment: continuum-original-signing", workflow)
        self.assertNotIn("set -x", signer)
        self.assertLess(signer.index("CODE54_QA_EVIDENCE_DOWNLOAD_FAILED"),
                        signer.index("ORIGINAL_P12_BASE64_INVALID"))


if __name__ == "__main__":
    unittest.main(verbosity=2)
