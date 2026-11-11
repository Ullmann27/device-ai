#!/usr/bin/env python3
"""Exercise Code53 signer rejection boundaries."""
import contextlib
import copy
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
import warnings
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location("evidence", ROOT / "scripts/continuum-code53-signing-evidence.py")
evidence = importlib.util.module_from_spec(spec)
spec.loader.exec_module(evidence)


def fixture():
    binding = {
        "source_sha": evidence.SOURCE, "source_tree": evidence.TREE,
        "workflow_sha": evidence.WORKFLOW, "qa_run_id": evidence.RUN,
        "qa_run_attempt": 1, "qa_job_id": evidence.JOB,
        "apk_sha256": "a" * 64, "apk_bytes": 1_000_000,
        "validator_result_path": "_temp/code53-package/RESULT.json",
        "unsigned": {"id": 1001, "name": evidence.ARTIFACT_NAMES["unsigned"],
                     "digest": "sha256:" + "b" * 64, "bytes": 1234},
        "evidence": {"id": 1002, "name": evidence.ARTIFACT_NAMES["evidence"],
                     "digest": "sha256:" + "c" * 64, "bytes": 2345},
    }
    repository = {"id": evidence.REPOSITORY_ID, "full_name": evidence.REPOSITORY}
    run = {"id": evidence.RUN, "run_attempt": 1, "head_sha": evidence.WORKFLOW,
           "head_branch": evidence.QA_BRANCH, "path": evidence.QA_PATH, "event": "push",
           "status": "completed", "conclusion": "success", "repository": repository,
           "head_repository": copy.deepcopy(repository)}
    job = {"id": evidence.JOB, "run_id": evidence.RUN, "head_sha": evidence.WORKFLOW,
           "name": "validate-frozen-source", "status": "completed", "conclusion": "success",
           "steps": [{"name": name, "status": "completed", "conclusion": "success"}
                     for name in sorted(evidence.REQUIRED_STEPS)]}
    artifacts = {"artifacts": []}
    for kind in evidence.ARTIFACT_NAMES:
        item = binding[kind]
        artifacts["artifacts"].append({"id": item["id"], "name": item["name"],
            "digest": item["digest"], "size_in_bytes": item["bytes"], "expired": False,
            "workflow_run": {"id": evidence.RUN, "head_sha": evidence.WORKFLOW,
                "head_branch": evidence.QA_BRANCH, "repository_id": evidence.REPOSITORY_ID,
                "head_repository_id": evidence.REPOSITORY_ID}})
    commit = {"sha": evidence.SOURCE, "tree": {"sha": evidence.TREE}}
    return {"binding": binding, "source": evidence.SOURCE, "approved": evidence.SOURCE,
            "run_id": str(evidence.RUN), "run": run, "jobs": {"jobs": [job]},
            "artifacts": artifacts, "commit": commit}


def replace(data, path, value):
    target = data
    for key in path[:-1]:
        target = target[key]
    target[path[-1]] = value


class MetadataContracts(unittest.TestCase):
    def test_complete_exact_binding_passes(self):
        evidence.verify_metadata(**fixture())

    def test_wrong_sources_runs_jobs_and_artifacts_fail_closed(self):
        changes = [
            (("source",), "d" * 40), (("approved",), "d" * 40), (("run_id",), "42977222214"),
            (("binding", "source_sha"), "d" * 40), (("binding", "source_tree"), "d" * 40),
            (("binding", "workflow_sha"), evidence.SOURCE), (("binding", "qa_job_id"), 1),
            (("binding", "unsigned", "digest"), ""), (("binding", "apk_sha256"), ""),
            (("binding", "validator_result_path"), "other/RESULT.json"),
            (("commit", "tree", "sha"), "d" * 40), (("commit", "sha"), "d" * 40),
            (("run", "head_sha"), evidence.SOURCE), (("run", "head_branch"), "main"),
            (("run", "path"), ".github/workflows/other.yml"), (("run", "event"), "workflow_dispatch"),
            (("run", "status"), "in_progress"), (("run", "conclusion"), "failure"),
            (("run", "run_attempt"), 2), (("run", "head_repository", "id"), 123),
            (("jobs", "jobs", 0, "id"), 1), (("jobs", "jobs", 0, "run_id"), 1),
            (("jobs", "jobs", 0, "head_sha"), evidence.SOURCE),
            (("jobs", "jobs", 0, "conclusion"), "failure"),
            (("jobs", "jobs", 0, "steps", 0, "conclusion"), "skipped"),
            (("artifacts", "artifacts", 0, "id"), 1003),
            (("artifacts", "artifacts", 0, "digest"), "sha256:" + "d" * 64),
            (("artifacts", "artifacts", 0, "expired"), True),
            (("artifacts", "artifacts", 0, "size_in_bytes"), 1),
            (("artifacts", "artifacts", 0, "workflow_run", "id"), 1),
            (("artifacts", "artifacts", 0, "workflow_run", "head_sha"), evidence.SOURCE),
            (("artifacts", "artifacts", 0, "workflow_run", "head_repository_id"), 123),
        ]
        for path, value in changes:
            with self.subTest(path=path):
                data = fixture()
                replace(data, path, value)
                with self.assertRaises(evidence.Rejected):
                    evidence.verify_metadata(**data)

    def test_missing_or_duplicate_records_rejected(self):
        for path in (("jobs", "jobs"), ("artifacts", "artifacts")):
            for duplicate in (False, True):
                with self.subTest(path=path, duplicate=duplicate):
                    data = fixture()
                    items = data[path[0]][path[1]]
                    replace(data, path, items + [copy.deepcopy(items[0])] if duplicate else [])
                    with self.assertRaises(evidence.Rejected):
                        evidence.verify_metadata(**data)

    def test_cli_consumes_separate_documents(self):
        data = fixture()
        with tempfile.TemporaryDirectory() as directory:
            paths = {}
            for key in ("binding", "run", "jobs", "artifacts", "commit"):
                paths[key] = str(Path(directory) / (key + ".json"))
                Path(paths[key]).write_text(json.dumps(data[key]))
            with contextlib.redirect_stdout(io.StringIO()) as output:
                evidence.main(["metadata", paths["binding"], data["source"], data["approved"], data["run_id"],
                               *(paths[key] for key in ("run", "jobs", "artifacts", "commit"))])
            self.assertIn("CODE53_METADATA_VERIFIED", output.getvalue())

    def test_duplicate_json_fields_rejected(self):
        with self.assertRaises(evidence.Rejected):
            evidence.decode_json('{"status":"FAIL","status":"PASS"}')


def archive_fixture(directory, mutate=None, unsafe=None, altered_apk=False):
    binding = fixture()["binding"]
    apk = b"a" * binding["apk_bytes"]
    binding["apk_sha256"] = hashlib.sha256(apk).hexdigest()
    source = {"commit": evidence.SOURCE, "tree": evidence.TREE, "status": "",
              "original_avatar_git_blob": "d" * 40}
    report = {"status": "PASS", "artifactStatus": "UNSIGNED_NOT_INSTALLABLE",
              "expectedSource": evidence.SOURCE, "expectedTree": evidence.TREE,
              "validatorSha256": evidence.VALIDATOR, "sha256": binding["apk_sha256"],
              "bytes": binding["apk_bytes"], "identity": copy.deepcopy(evidence.IDENTITY),
              "sourceBefore": source, "sourceAfter": copy.deepcopy(source),
              "signing": "NOT_USED", "installation": "NOT_RUN", "Fold7": "NOT_RUN", "GPU": "NOT_RUN",
              "archive": {"zipCrc": "PASS", "jarSignatureFiles": [], "apkSigningBlock": False},
              "zipalign16KiB": "PASS", "unsignedDiagnostic": "EXPECTED_MISSING_SIGNATURE"}
    receipt = f"source_sha={evidence.SOURCE}\nworkflow_sha={evidence.WORKFLOW}\nFold7=NOT_RUN\noriginal_signature=NOT_USED\n"
    documents = {"report": report, "receipt": receipt,
                 "sums": f'{binding["apk_sha256"]}  {evidence.APK_NAME}\n'}
    if mutate:
        mutate(documents)
    unsigned = directory / "unsigned.zip"
    with zipfile.ZipFile(unsigned, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr(evidence.APK_NAME, b"b" * len(apk) if altered_apk else apk)
        archive.writestr("BUILD-INFO.txt", documents["receipt"])
        archive.writestr("SHA256SUMS.txt", documents["sums"])
        if unsafe:
            with warnings.catch_warnings():
                warnings.simplefilter("ignore", UserWarning)
                archive.writestr(unsafe, "blocked")
    proof = directory / "evidence.zip"
    with zipfile.ZipFile(proof, "w") as archive:
        archive.writestr(binding["validator_result_path"], json.dumps(documents["report"]))
    for kind, path in (("unsigned", unsigned), ("evidence", proof)):
        binding[kind]["bytes"] = path.stat().st_size
        binding[kind]["digest"] = "sha256:" + evidence.digest_file(path)
    return binding, unsigned, proof, directory / "verified"


class ArchiveContracts(unittest.TestCase):
    def test_verified_archive_extracts_only_expected_files(self):
        with tempfile.TemporaryDirectory() as name:
            args = archive_fixture(Path(name))
            evidence.verify_archives(*args)
            self.assertEqual(sorted(path.name for path in args[-1].iterdir()),
                             ["BUILD-INFO.txt", evidence.APK_NAME, "SHA256SUMS.txt"])

    def test_receipt_and_validator_mutants_rejected(self):
        changes = [
            (("receipt",), "source_sha=" + evidence.SOURCE + "\n"),
            (("sums",), "a" * 64 + "  attacker.apk\n"),
            (("report", "status"), "FAIL"), (("report", "expectedSource"), "a" * 40),
            (("report", "expectedTree"), "a" * 40), (("report", "validatorSha256"), "a" * 64),
            (("report", "sha256"), "a" * 64), (("report", "identity", "package"), "other.app"),
            (("report", "identity", "versionCode"), 52), (("report", "identity", "versionName"), "0.3.51"),
            (("report", "identity", "debuggable"), True), (("report", "identity", "abi"), "x86_64"),
            (("report", "sourceAfter", "status"), " M app/build.gradle"),
            (("report", "signing"), "USED"), (("report", "Fold7"), "PASS"),
            (("report", "archive", "apkSigningBlock"), True),
            (("report", "archive", "jarSignatureFiles"), ["META-INF/CERT.RSA"]),
            (("report", "zipalign16KiB"), "FAIL"),
            (("report", "unsignedDiagnostic"), "MALFORMED_APK"),
        ]
        for path, value in changes:
            with self.subTest(path=path), tempfile.TemporaryDirectory() as name:
                args = archive_fixture(Path(name), lambda documents: replace(documents, path, value))
                with self.assertRaises(evidence.Rejected):
                    evidence.verify_archives(*args)

    def test_archive_bytes_are_independently_bound(self):
        with tempfile.TemporaryDirectory() as name:
            args = archive_fixture(Path(name))
            args[1].write_bytes(args[1].read_bytes() + b"tamper")
            with self.assertRaises(evidence.Rejected):
                evidence.verify_archives(*args)

    def test_apk_bytes_are_independently_bound(self):
        with tempfile.TemporaryDirectory() as name:
            with self.assertRaises(evidence.Rejected):
                evidence.verify_archives(*archive_fixture(Path(name), altered_apk=True))

    def test_unsafe_or_duplicate_members_rejected(self):
        for name in ("../escape", "/escape", "back\\escape", "BUILD-INFO.txt"):
            with self.subTest(name=name), tempfile.TemporaryDirectory() as directory:
                with self.assertRaises(evidence.Rejected):
                    evidence.verify_archives(*archive_fixture(Path(directory), unsafe=name))


class WorkflowContracts(unittest.TestCase):
    def test_existing_registered_manual_path_preserves_owner_boundary(self):
        workflow = (ROOT / ".github/workflows/continuum-code52-sign-release.yml").read_text()
        self.assertIn("workflow_dispatch:", workflow)
        self.assertNotRegex(workflow, r"(?m)^  (push|pull_request|workflow_run|schedule):")
        self.assertIn("github.ref == 'refs/heads/chatgpt/code52-owner-signing-20261109'", workflow)
        self.assertIn("environment: continuum-original-signing", workflow)
        self.assertNotIn("secrets.", workflow.split("  sign-draft-original:")[0])
        self.assertEqual(workflow.count("secrets."), 2)
        self.assertEqual(workflow.count("bash scripts/continuum-code52-github-signer.sh"), 2)
        self.assertFalse((ROOT / ".github/workflows/continuum-code53-sign-release.yml").exists())

    def test_only_exact_frozen_source_selects_code53(self):
        script = (ROOT / "scripts/continuum-code52-github-signer.sh").read_text()
        self.assertIn('profile="code52"', script)
        self.assertIn('if [[ "$source_sha" == ' + evidence.SOURCE + ' ]]; then', script)
        self.assertIn('code="53"', script)
        self.assertIn('name_version="0.3.52"', script)
        self.assertIn('"$source_sha" "$approved_sha" "$qa_run" "$tmp/run.json" "$tmp/jobs.json"', script)
        self.assertLess(script.index('CODE53_ARCHIVE_MISMATCH'), script.index('STATUS=PREFLIGHT_OK'))
        self.assertLess(script.index('CODE53_ARCHIVE_MISMATCH'), script.index('ORIGINAL_P12_BASE64_INVALID'))
        self.assertIn('"$tools/zipalign" -P 16 -f 4', script)
        self.assertIn('"$tools/zipalign" -c -P 16 -v 4', script)
        self.assertIn('--draft --latest=false', script)
        self.assertNotRegex(script, r'--draft=false|--latest=true|gh release (edit|publish)')

    def test_production_binding_is_complete(self):
        path = ROOT / ".github/continuum/code53-owner-signing-binding.json"
        evidence.verify_binding(evidence.decode_json(path.read_bytes()))


if __name__ == "__main__":
    unittest.main(verbosity=2)
