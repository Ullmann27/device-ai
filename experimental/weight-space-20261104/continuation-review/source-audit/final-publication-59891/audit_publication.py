#!/usr/bin/env python3
"""Audit pinned publication text and Git objects; never execute study code.

The auditor writes only JSON to stdout. Git network transports and optional
locks are disabled. Missing local objects fail instead of being fetched.
Datasets, generators, checkpoints, archives, JARs and tokenizer files are
outside the explicit blob allowlist, even when a published receipt names them.
"""

from __future__ import annotations

import argparse
from datetime import datetime, timedelta, timezone
from decimal import Decimal
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import posixpath
import re
import subprocess
import sys


SNAPSHOT = "59891cfad6dd015f47c187cc688ea94b0026ad7d"
PUBLICATION = "9ceffc26a2159c8dc726c1f13d2882119159c130"
STUDY = "experimental/lm1-training-recovery-20261104/"
EVIDENCE = "docs/evidence/lm1-training-recovery-20261104/"
SELECTION = EVIDENCE + "final-selection/"
EXECUTION = {
    "v1": "def5acf9cbba82ee51a53cc06fecc52e9cd0324d",
    "long-study": "fc2eb797210bee25091caf734aacbfd694a3372e",
    "suffix-study": "be4e49600fed1db4c697230bb8e42c150dbfbdaf",
}
REGISTRATIONS = {
    "v1": STUDY + "registration.json",
    "long-study": STUDY + "long-study/registration.json",
    "suffix-study": STUDY + "suffix-study/registration.json",
}
CONTROLS = [
    STUDY + "run_registered_study.py",
    STUDY + "long-study/run_registered_study.py",
    STUDY + "suffix-study/run_registered_study.py",
    STUDY + "suffix-study/choose_development_study.py",
    *REGISTRATIONS.values(),
]
SOURCE_ALLOWLIST = {
    STUDY + name for name in (
        "run_registered_study.py", "LmRecoveryDiagnostic.kt",
        "DIAGNOSTIC_PROTOCOL.md", "LmIntentStudy.kt", "LmIntentStudyChecks.kt",
        "INTENT_PROTOCOL.md", "registration.json", "suffix_probe.py",
        "long-study/LmIntentLongStudy.kt", "long-study/LmIntentLongStudyChecks.kt",
        "long-study/INTENT_LONG_PROTOCOL.md", "long-study/run_registered_study.py",
        "long-study/test_study_controller.py", "long-study/registration.json",
        "suffix-study/LmIntentSuffixStudy.kt", "suffix-study/LmIntentSuffixStudyChecks.kt",
        "suffix-study/SUFFIX_PROTOCOL.md", "suffix-study/choose_development_study.py",
        "suffix-study/run_registered_study.py", "suffix-study/test_study_controller.py",
        "suffix-study/registration.json",
    )
} | {
    "app/src/main/java/at/heinz/deviceai/learning/lm/" + name for name in (
        "LmBpeTokenizer.kt", "LmCheckpointStore.kt", "LmCorpus.kt",
        "LmModel.kt", "LmRuntime.kt", "LmTensor.kt",
    )
} | {
    "experimental/weight-space-20261104/" + name for name in (
        "LmCapacityBenchmark.kt", "LmWeightExperiment.kt", "LmWeightSurgery.kt",
    )
}
FINAL_LABELS = ("test-S0", "test-W0", "test-S1", "test-W1")
FIT_LABELS = ("train-fit-S1", "train-fit-W1")
LABELS = FINAL_LABELS + FIT_LABELS
DOCUMENTS = (
    EVIDENCE + "RESULTS.md", EVIDENCE + "REPRODUCE.md",
    EVIDENCE + "final-independent-review.md",
    "docs/research/omega/LM1-TRAINING-RECOVERY-HANDOFF-20261104.md",
)
EVIDENCE_ALLOWLIST = {
    EVIDENCE + "archive-manifest.json",
    EVIDENCE + "publication-integrity.json",
    EVIDENCE + "final-case-audit.json",
    EVIDENCE + "study-v3/intent-summary.json",
    EVIDENCE + "study-v3/training-fit-summary.json",
    *(SELECTION + name for name in (
        "development-study-choice.json", "selection-freeze.json",
        "before-test-verification.json", "publication-metadata.json",
    )),
    *(EVIDENCE + "study-v3/logs/" + label + ext
      for label in LABELS for ext in (".receipt.json", ".tsv")),
    *DOCUMENTS,
}
ALLOWED_COMMITS = {SNAPSHOT, PUBLICATION, *EXECUTION.values()}


def sha256(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def object_sha(kind: str, raw: bytes) -> str:
    return hashlib.sha1(kind.encode() + b" " + str(len(raw)).encode() + b"\0" + raw).hexdigest()


def utc(value: str) -> datetime:
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        raise ValueError("Timestamp without timezone")
    return parsed.astimezone(timezone.utc)


class Audit:
    def __init__(self, repo: Path):
        self.repo = repo.resolve()
        self.env = dict(os.environ, GIT_OPTIONAL_LOCKS="0", GIT_NO_LAZY_FETCH="1")
        self.cache: dict[tuple[str, str], bytes] = {}
        self.checks: list[dict] = []

    def git(self, *args: str) -> bytes:
        return subprocess.run(
            ["git", "--no-optional-locks", "-c", "protocol.allow=never", *args],
            cwd=self.repo, env=self.env, check=True, stdout=subprocess.PIPE,
            stderr=subprocess.PIPE, timeout=30,
        ).stdout

    def blob(self, commit: str, path: str) -> bytes:
        if commit not in ALLOWED_COMMITS:
            raise ValueError("Commit outside fixed audit scope")
        if path not in SOURCE_ALLOWLIST | EVIDENCE_ALLOWLIST:
            raise ValueError("Blob read outside explicit text allowlist: " + path)
        key = (commit, path)
        if key not in self.cache:
            raw = self.git("cat-file", "blob", commit + ":" + path)
            raw.decode("utf-8", errors="strict")
            self.cache[key] = raw
        return self.cache[key]

    def data(self, path: str, commit: str = SNAPSHOT):
        return json.loads(self.blob(commit, path))

    def check(self, label: str, actual, expected) -> None:
        self.checks.append({"label": label, "passed": actual == expected,
                            "actual": actual, "expected": expected})

    def commit(self, commit: str) -> dict:
        if commit not in ALLOWED_COMMITS:
            raise ValueError("Commit outside fixed audit scope")
        raw = self.git("cat-file", "commit", commit)
        self.check("Git object SHA-1: " + commit, object_sha("commit", raw), commit)
        header, message = raw.decode("utf-8").split("\n\n", 1)
        lines = header.splitlines()
        result = {"commitSha": commit,
                  "treeSha": next(line[5:] for line in lines if line.startswith("tree ")),
                  "parents": [line[7:] for line in lines if line.startswith("parent ")],
                  "message": message.rstrip("\n"),
                  "signatureHeaderPresent": any(line.startswith(("gpgsig ", "gpgsig-sha256 ")) for line in lines)}
        for role in ("author", "committer"):
            line = next(line for line in lines if line.startswith(role + " "))
            epoch, offset = line.rsplit(" ", 2)[-2:]
            result[role + "DateUtc"] = datetime.fromtimestamp(int(epoch), timezone.utc).isoformat()
            result[role + "RecordedOffset"] = offset
        return result


def identity_rows(raw: bytes, kind: str) -> list[dict]:
    result = []
    for line in raw.decode("utf-8").splitlines():
        columns = line.split("\t")
        if columns[0] != kind:
            continue
        row = {}
        for item in columns[1:]:
            key, value = item.split("=", 1)
            if key in row:
                raise ValueError("Duplicate identity field: " + key)
            row[key] = value
        result.append(row)
    return result


def omitted_reason(path: str) -> str:
    if path.endswith("make_intent_data.py"):
        return "DATA_GENERATOR_NOT_READ"
    if "/intent-data/" in path or path.endswith("/development.txt") or path == "input/train.txt":
        return "DATASET_OR_DATASET_MANIFEST_NOT_READ"
    if path == "jar" or path.startswith("input/"):
        return "BINARY_ARTIFACT_NOT_READ"
    raise ValueError("Unexpected unallowlisted integrity entry: " + path)


def run(audit: Audit) -> dict:
    commits = {commit: audit.commit(commit) for commit in sorted(ALLOWED_COMMITS)}
    audit.check("Final commit directly descends from freeze publication",
                commits[SNAPSHOT]["parents"], [PUBLICATION])
    current_paths = set(audit.git("ls-tree", "-r", "--name-only", "-z", SNAPSHOT).decode().strip("\0").split("\0"))
    prior_paths = set(audit.git("ls-tree", "-r", "--name-only", "-z", PUBLICATION).decode().strip("\0").split("\0"))
    changes = audit.git("diff-tree", "--no-commit-id", "--name-only", "-r", PUBLICATION, SNAPSHOT).decode().splitlines()
    audit.check("Publication delta contains only evidence and handoff text",
                all(p.startswith(EVIDENCE) or p == DOCUMENTS[-1] for p in changes), True)

    unchanged = []
    for path in CONTROLS:
        old, new = audit.blob(PUBLICATION, path), audit.blob(SNAPSHOT, path)
        audit.check("Control/registration unchanged: " + path, sha256(new), sha256(old))
        unchanged.append({"path": path, "publicationGitBlob": object_sha("blob", old),
                          "snapshotGitBlob": object_sha("blob", new), "sha256": sha256(new),
                          "byteIdentical": new == old})

    regs = {version: audit.data(path) for version, path in REGISTRATIONS.items()}
    for version, path in REGISTRATIONS.items():
        audit.check("Registration equals execution-commit bytes: " + version,
                    sha256(audit.blob(SNAPSHOT, path)), sha256(audit.blob(EXECUTION[version], path)))

    selection_files = {}
    for name in ("development-study-choice.json", "selection-freeze.json", "before-test-verification.json"):
        path = SELECTION + name
        raw = audit.blob(SNAPSHOT, path)
        audit.check("Selection evidence unchanged: " + name, sha256(raw), sha256(audit.blob(PUBLICATION, path)))
        selection_files[name] = {"sha256": sha256(raw), "gitBlob": object_sha("blob", raw)}
    choice = audit.data(SELECTION + "development-study-choice.json")
    freeze = audit.data(SELECTION + "selection-freeze.json")
    before = audit.data(SELECTION + "before-test-verification.json")
    freeze_sha = selection_files["selection-freeze.json"]["sha256"]
    audit.check("Pretest receipt binds choice bytes", before["choiceSha256"], selection_files["development-study-choice.json"]["sha256"])
    audit.check("Pretest receipt binds freeze bytes", before["freezeSha256"], freeze_sha)
    audit.check("Pretest receipt study agrees", before["selectedStudy"], choice["selectedStudy"])
    audit.check("Pretest receipt source agrees", before["selectedSourceCommit"], freeze["sourceCommit"])
    audit.check("Selected payload equals actual freeze", choice["selectedFreezePayload"], freeze)
    audit.check("Selected candidate equals actual freeze", choice["candidates"][choice["selectedStudy"]]["freezePayload"], freeze)
    ranked = sorted(
        (name for name, value in choice["candidates"].items() if value["status"] == "ELIGIBLE"),
        key=lambda name: (-int(choice["candidates"][name]["freezePayload"]["arms"]["W1"]["devCorrect"]),
                          Decimal(choice["candidates"][name]["freezePayload"]["arms"]["W1"]["devAnswerNll"]),
                          0 if name == "v2" else 1),
    )
    audit.check("Published winner agrees with published Dev rank", choice["selectedStudy"], ranked[0])
    audit.check("Final study is v3", choice["selectedStudy"], "v3")
    audit.check("Frozen source is registered v3 execution", freeze["sourceCommit"], EXECUTION["suffix-study"])
    audit.check("Frozen registration binds actual registration bytes", freeze["registrationSha256"], sha256(audit.blob(SNAPSHOT, REGISTRATIONS["suffix-study"])))
    audit.check("Frozen test hash agrees with registration declaration", freeze["testSha256"], regs["suffix-study"]["files"][STUDY + "intent-data/test.sealed.tsv"])
    audit.check("Choice common test identity", choice["testSha256"], freeze["testSha256"])
    audit.check("Choice example schedules agree", choice["schedules"]["v2"]["scheduleSha256"], choice["schedules"]["v3"]["scheduleSha256"])
    for study, candidate in choice["candidates"].items():
        payload = candidate["freezePayload"]
        audit.check("Candidate common test identity: " + study, payload["testSha256"], choice["testSha256"])
        audit.check("Candidate token schedule binding: " + study, payload["tokenScheduleSha256"], choice["schedules"][study]["tokenScheduleSha256"])
    audit.check("Pretest assertion: choices match", before["choiceMatchesFreezeExactly"], True)
    audit.check("Pretest assertion: final logs absent", before["allFinalTestLogsAbsent"], True)
    audit.check("Final test logs absent from publication tree",
                any(p.startswith(EVIDENCE + "study-v3/logs/test-") for p in prior_paths), False)

    metadata = audit.data(SELECTION + "publication-metadata.json")
    publication_commit = commits[PUBLICATION]
    for key in ("commitSha", "treeSha", "parents", "message"):
        audit.check("Publication metadata raw Git " + key, metadata[key], publication_commit[key])
    for role in ("author", "committer"):
        audit.check("Publication metadata Git " + role + " timestamp",
                    utc(metadata[role + "Date"]).isoformat(), publication_commit[role + "DateUtc"])
    audit.check("Publication metadata unsigned flag", metadata["commitSigned"], publication_commit["signatureHeaderPresent"])
    audit.check("Publication commit URL", metadata["commitUrl"], "https://github.com/" + metadata["repository"] + "/commit/" + PUBLICATION)

    receipt_lineage = []
    v3reg = regs["suffix-study"]
    for label in LABELS:
        prefix = EVIDENCE + "study-v3/logs/" + label
        receipt = audit.data(prefix + ".receipt.json")
        raw_log = audit.blob(SNAPSHOT, prefix + ".tsv")
        arm = label.rsplit("-", 1)[1]
        is_test = label in FINAL_LABELS
        input_name = "test.sealed.tsv" if is_test else "train.tsv"
        input_hash = v3reg["files"][STUDY + "intent-data/" + input_name]
        rows = identity_rows(raw_log, "TEST_INPUT")
        audit.check(label + ": one log identity row", len(rows), 1)
        identity = rows[0]
        audit.check(label + ": receipt label", receipt["label"], label)
        audit.check(label + ": actual published log hash", sha256(raw_log), receipt["logSha256"])
        audit.check(label + ": source commit", receipt["sourceCommit"], freeze["sourceCommit"])
        audit.check(label + ": registration raw hash", receipt["registrationSha256"], freeze["registrationSha256"])
        audit.check(label + ": JAR declaration", receipt["jarSha256"], v3reg["jarSha256"])
        audit.check(label + ": logged checkpoint declaration", identity["checkpointSha256"], freeze["arms"][arm]["checkpointSha256"])
        audit.check(label + ": logged input declaration", identity["testSha256"], input_hash)
        audit.check(label + ": recorded checkpoint argument", receipt["command"][7], freeze["arms"][arm]["path"])
        audit.check(label + ": checkpoint basename agrees with hash", PurePosixPath(receipt["command"][7]).stem, identity["checkpointSha256"])
        audit.check(label + ": recorded input argument", receipt["command"][9].endswith("/" + STUDY + "intent-data/" + input_name), True)
        audit.check(label + ": recorded tokenizer argument", PurePosixPath(receipt["command"][8]).name, "lm1-tokenizer.lmbp")
        audit.check(label + ": recorded runtime flags", receipt["command"][:4], ["java", "-Xms64m", "-Xmx3g", "-cp"])
        audit.check(label + ": recorded scorer", receipt["command"][5:7], ["at.heinz.deviceai.learning.lm.LmIntentSuffixStudy", "test"])
        audit.check(label + ": recorded timeout budget", receipt["timeoutSeconds"], v3reg["trainingProcessSeconds"])
        audit.check(label + ": successful recorded return", receipt["returncode"], 0)
        audit.check(label + ": no recorded timeout", receipt["timeout"], False)
        audit.check(label + ": recorded wall time within budget", 0 <= receipt["wallSeconds"] <= receipt["timeoutSeconds"], True)
        audit.check(label + ": registered bytes assertion", receipt["registeredBytesUnchanged"], True)
        receipt_lineage.append({
            "label": label, "receiptPath": prefix + ".receipt.json",
            "receiptSha256": sha256(audit.blob(SNAPSHOT, prefix + ".receipt.json")),
            "logPath": prefix + ".tsv", "measuredLogSha256": sha256(raw_log),
            "reportedCheckpointSha256": identity["checkpointSha256"],
            "reportedDatasetSha256": input_hash, "datasetRole": "FINAL_TEST" if is_test else "POST_SELECTION_TRAIN_FIT",
            "reportedParameters": int(identity["parameters"]), "recordedStartedUtc": receipt["startedUtc"],
            "recordedWallSeconds": receipt["wallSeconds"], "recordedTimeoutSeconds": receipt["timeoutSeconds"],
            "reportedJarSha256": receipt["jarSha256"], "recordedCommand": receipt["command"],
            "modelOrDatasetBytesRead": False, "independentProcessAttestation": False,
        })
    for earlier, later in zip(receipt_lineage, receipt_lineage[1:]):
        end = utc(earlier["recordedStartedUtc"]) + timedelta(seconds=earlier["recordedWallSeconds"])
        audit.check("Reported process chronology: " + earlier["label"] + " before " + later["label"],
                    end <= utc(later["recordedStartedUtc"]), True)
    first = utc(receipt_lineage[0]["recordedStartedUtc"])
    audit.check("Publication metadata first receipt start", utc(metadata["firstFinalTestStartedUtc"]).isoformat(), first.isoformat())
    date_lead = (first - utc(publication_commit["committerDateUtc"])).total_seconds()
    audit.check("Reported Git date precedes reported first process", metadata["commitDatePrecedesTest"], date_lead > 0)

    summary = audit.data(EVIDENCE + "study-v3/intent-summary.json")
    fit = audit.data(EVIDENCE + "study-v3/training-fit-summary.json")
    audit.check("Final summary binds original freeze", summary["selectionSha256"], freeze_sha)
    audit.check("TRAIN-fit summary binds original freeze", fit["selectionSha256"], freeze_sha)
    audit.check("TRAIN-fit dataset label", fit["dataset"], "TRAIN")
    audit.check("TRAIN-fit source identity", fit["sourceCommit"], freeze["sourceCommit"])
    audit.check("TRAIN-fit input declaration", fit["datasetSha256"], v3reg["files"][STUDY + "intent-data/train.tsv"])
    audit.check("TRAIN-fit does not claim selection effect", fit["affectsSelection"], False)
    audit.check("TRAIN-fit does not claim independent generalization", fit["independentGeneralizationEvidence"], False)
    audit.check("Final summary does not promote a model", summary["modelPromotion"], False)

    integrity = audit.data(EVIDENCE + "publication-integrity.json")
    reported_checks = integrity["checks"]
    audit.check("Reported integrity count", integrity["checkCount"], len(reported_checks))
    audit.check("Reported integrity labels unique", len({c["label"] for c in reported_checks}), len(reported_checks))
    audit.check("Reported allPassed agrees with reported flags", integrity["allPassed"], all(c["passed"] is True for c in reported_checks))
    audit.check("Reported integrity entries all pass", integrity["allPassed"], True)
    measured, omitted = [], []
    for entry in reported_checks[:95]:
        version, path = entry["label"].split("/", 1)
        audit.check("Integrity declaration internally agrees: " + entry["label"], entry["actual"], entry["expected"])
        reg = regs[version]
        expected = reg["jarSha256"] if path == "jar" else reg["inputs"][path[6:]] if path.startswith("input/") else reg["files"][path]
        audit.check("Integrity expected hash matches registration: " + entry["label"], entry["expected"], expected)
        if path not in SOURCE_ALLOWLIST:
            omitted.append({"label": entry["label"], "reportedSha256": entry["actual"], "reason": omitted_reason(path)})
            continue
        raw = audit.blob(SNAPSHOT, path)
        execution_raw = audit.blob(EXECUTION[version], path)
        audit.check("Actual source hash: " + entry["label"], sha256(raw), entry["expected"])
        audit.check("Source equals declared execution commit: " + entry["label"], sha256(raw), sha256(execution_raw))
        measured.append({"label": entry["label"], "path": path, "executionCommit": EXECUTION[version],
                         "measuredSha256": sha256(raw), "snapshotGitBlob": object_sha("blob", raw),
                         "executionGitBlob": object_sha("blob", execution_raw)})
    audit.check("Expected hash-entry shape", [c["label"] for c in reported_checks[95:]], [
        "Publication-only link normalization: final-independent-review.md",
        "RESULTS.md local relative links resolve", "final audit failed checks"])
    review_claim = reported_checks[95]
    audit.check("Published review actual raw SHA256", sha256(audit.blob(SNAPSHOT, EVIDENCE + "final-independent-review.md")), review_claim["publishedSha256"])
    case_audit = audit.data(EVIDENCE + "final-case-audit.json")
    audit.check("Final audit failed-check declaration", reported_checks[97]["actual"], case_audit["failedChecks"])
    audit.check("Final audit failure declaration matches expectation", reported_checks[97]["actual"], reported_checks[97]["expected"])
    audit.check("Final audit embeds same publication metadata", case_audit["publicationAndFreeze"]["rootPublicationMetadata"], metadata)
    audit.check("Final audit embeds same pretest receipt", case_audit["publicationAndFreeze"]["localBeforeTestVerification"], before)
    audit.check("Final audit published freeze identity", case_audit["publicationAndFreeze"]["publishedFreezeCopySha256"], freeze_sha)

    documents = []
    for path in DOCUMENTS:
        raw = audit.blob(SNAPSHOT, path)
        text = raw.decode("utf-8")
        links = []
        for target in re.findall(r"\[[^\]]*\]\(([^)\s]+)\)", text):
            if "://" in target or target.startswith(("#", "mailto:")):
                continue
            normalized = posixpath.normpath(posixpath.join(posixpath.dirname(path), target.split("#", 1)[0]))
            kind = "blob" if normalized in current_paths else "tree" if any(p.startswith(normalized + "/") for p in current_paths) else "missing"
            links.append({"target": target, "resolvedPath": normalized,
                          "targetKind": kind, "existsInTree": kind != "missing"})
        if path == EVIDENCE + "RESULTS.md":
            audit.check("RESULTS local links resolve in pinned Git tree", all(item["existsInTree"] for item in links), True)
        documents.append({"path": path, "sha256": sha256(raw), "localLinks": links,
                          "linkTargetsOpened": False})
    archive = audit.data(EVIDENCE + "archive-manifest.json")
    audit.check("Archive declaration source matches freeze", archive["sourceCommit"], freeze["sourceCommit"])
    audit.check("Archive declaration publication commit matches", archive["freezePublicationCommit"], PUBLICATION)
    audit.check("Archive declaration no promotion", archive["modelPromotion"], False)
    audit.check("Archive digest declaration shape", all(re.fullmatch("[0-9a-f]{64}", archive[k]) is not None for k in ("sha256", "manifestSha256")), True)
    audit.check("Archive filename is basename only", PurePosixPath(archive["filename"]).name, archive["filename"])

    failed = [check["label"] for check in audit.checks if not check["passed"]]
    return {
        "schemaVersion": 1, "scope": "PINNED_PUBLICATION_TEXT_LINEAGE_NO_MODEL_OR_DATASET_EXECUTION",
        "snapshotCommit": SNAPSHOT, "freezePublicationCommit": PUBLICATION,
        "auditorSha256": sha256(Path(__file__).read_bytes()),
        "status": "PASS_TEXT_CONSISTENCY_ONLY" if not failed else "FAIL_TEXT_CONSISTENCY",
        "allConsistencyChecksPassed": not failed, "checkCount": len(audit.checks), "failedChecks": failed,
        "gitCommits": commits, "changedPublicationPaths": changes,
        "unchangedControlsAndRegistrations": unchanged,
        "selectionEvidence": {"files": selection_files, "selectedStudy": choice["selectedStudy"],
            "publishedDevRanking": ranked, "selectedSourceCommit": freeze["sourceCommit"],
            "reportedFrozenCheckpointHashes": {arm: value["checkpointSha256"] for arm, value in freeze["arms"].items()},
            "postTrainingCapacityMarkers": {arm: freeze["arms"][arm]["postTrainingCapacity"] for arm in ("S1", "W1")},
            "summaryContainsTestAuthorization": "testAuthorization" in summary,
            "controlCodeChangedSincePublication": any(not row["byteIdentical"] for row in unchanged),
            "modelBytesRead": False},
        "publicationChronology": {"actualGitCommitMetadata": publication_commit,
            "reportedFirstProcessStartUtc": receipt_lineage[0]["recordedStartedUtc"],
            "gitCommitDateLeadOverRecordedStartSeconds": date_lead,
            "coordinatorReportedOrder": metadata["rootObservedSequence"],
            "coordinatorObservationScope": metadata["observationScope"],
            "serverPushTimestampIndependentlyVerified": False,
            "processLaunchIndependentlyAttested": False,
            "finalTestLogsAbsentFromEarlierGitTree": not any(p.startswith(EVIDENCE + "study-v3/logs/test-") for p in prior_paths),
            "absenceOutsideGitOrBeforeGitSnapshotProved": False},
        "receiptLogLineage": receipt_lineage,
        "publicationIntegrityRecheck": {"reportedCheckCount": integrity["checkCount"],
            "reportedHashEntryCount": 95, "independentlyRehashedSourceEntryCount": len(measured),
            "uniqueIndependentlyRehashedSourcePaths": len({row["path"] for row in measured}),
            "omittedHashEntryCount": len(omitted), "measuredSourceEntries": measured,
            "omittedBinaryDatasetAndGeneratorEntries": omitted,
            "publishedReviewHashVerified": sha256(audit.blob(SNAPSHOT, EVIDENCE + "final-independent-review.md")) == review_claim["publishedSha256"],
            "originalReviewSha256ReportedOnly": review_claim["originalSha256"],
            "reviewSemanticEquivalenceIndependentlyVerified": False,
            "reportedFinalCaseAuditChecksNotRecomputedHere": case_audit["checkCount"]},
        "archiveDeclaration": {**archive, "manifestTextPath": EVIDENCE + "archive-manifest.json",
            "manifestTextSha256": sha256(audit.blob(SNAPSHOT, EVIDENCE + "archive-manifest.json")),
            "archiveLocationInManifest": "BASENAME_ONLY_NO_ABSOLUTE_PATH_URI_OR_LIBRARY_ID",
            "exactMemberNamesInManifest": [], "archiveBytesRead": False,
            "internalArchiveManifestBytesRead": False,
            "archiveAndMemberIntegrityIndependentlyVerifiedByThisAuditor": False},
        "documents": documents,
        "limits": [
            "Git object hashes establish content identity; unsigned author/committer dates are not trusted push timestamps.",
            "Remote update/fetch ordering is the coordinator's reported observation; receipt timestamps and commands are text records, not independent host attestation.",
            "Absence of final logs in the earlier Git tree does not prove absence of uncommitted or external prior executions.",
            "Dataset, generator, tokenizer, checkpoint, JAR and ZIP bytes were not read; their declared SHA values were only cross-referenced between allowed text records.",
            "Log SHA checks authenticate published byte identity relative to receipts, not model execution, checkpoint validity or measured memory usage.",
            "The final-case auditor's 622 checks and numerical task scores were not rerun here; a separate helper owns the score audit.",
            "Seven control/registration files remain unchanged. Evidence consistency does not establish integration of the separately frozen prospective gate repair.",
            "The published review hash is checked; the earlier unpublished review and claimed semantic-only link normalization are not independently reconstructed.",
        ],
        "readTextBlobs": [{"commit": commit, "path": path, "bytes": len(raw),
                           "sha256": sha256(raw), "gitBlob": object_sha("blob", raw)}
                          for (commit, path), raw in sorted(audit.cache.items())],
        "checks": audit.checks,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[5])
    args = parser.parse_args()
    try:
        result = run(Audit(args.repo))
    except (KeyError, IndexError, TypeError, ValueError, OSError, subprocess.SubprocessError) as exc:
        print(json.dumps({"status": "AUDIT_ERROR", "errorType": type(exc).__name__, "error": str(exc)}), file=sys.stderr)
        return 2
    json.dump(result, sys.stdout, ensure_ascii=False, sort_keys=True, indent=2)
    sys.stdout.write("\n")
    return 0 if result["allConsistencyChecksPassed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
