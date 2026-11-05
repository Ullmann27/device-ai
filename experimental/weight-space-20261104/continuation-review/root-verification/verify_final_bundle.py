#!/usr/bin/env python3
"""Cross-check already executed independent audits and actual binary evidence.

This verifies the saved results; it does not rerun models or the earlier suites.
Each underlying auditor has its own documented reproduction command.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path


HERE = Path(__file__).resolve().parent
REVIEW = HERE.parent
PIN = "59891cfad6dd015f47c187cc688ea94b0026ad7d"
INPUTS = {
    "results/final-evidence-59891/audit_final_evidence.py": "1ac0fdd94315f7d538f9f23933192b1c1a881b75b67bed0a6e8951883be1fbc1",
    "results/final-evidence-59891/final-results.json": "9375ab0276d3508160ac1ebb64a879dba6a779ef2fbdb48cce35bff4dccb1dd1",
    "independent-tests/final-evidence-59891/check_final_evidence.py": "118b7fdf75e7286a0e3ddc431f1662980680d7cadd51e8da8f69eac1752dc4e9",
    "independent-tests/final-evidence-59891/audit.json": "582a8852b97032290d297b463d1c9bc8acad269894d0df55d6f80772e8f36cf0",
    "source-audit/final-publication-59891/audit_publication.py": "697c3c431218937348aafd8fe2403224af08ac586842b38876d91636a76cd95e",
    "source-audit/final-publication-59891/audit-result.json": "9d0a91db9482d5b4dc153c35af399dd82f95da122046e9b619002efe0803b9ec",
    "root-verification/audit_binary_archive.py": "dca91cb311ac022a3f8f257f8ca61fffc9de41e33fae35a73b8412786b4fbc86",
    "source-audit/pr508-capacity-and-choice-gates.patch": "87d54102113944c0884081b44cbb05eb6d253d56f8df0c84f9c31bc4f7039d05",
}


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def require(condition: bool, detail: str) -> None:
    if not condition:
        raise RuntimeError(detail)


def read(name: str) -> dict:
    return json.loads((HERE / name).read_bytes())


def main() -> None:
    for name, expected in INPUTS.items():
        require(sha(REVIEW / name) == expected, "reviewed input changed: " + name)
    repeats = (
        ("final-results-59891.json", "results/final-evidence-59891/final-results.json"),
        ("independent-final-59891.json", "independent-tests/final-evidence-59891/audit.json"),
        ("publication-audit-59891.json", "source-audit/final-publication-59891/audit-result.json"),
    )
    for own, helper in repeats:
        require((HERE / own).read_bytes() == (REVIEW / helper).read_bytes(), "root repeat differs: " + own)
    final = read("final-results-59891.json")
    independent = read("independent-final-59891.json")
    publication = read("publication-audit-59891.json")
    binary = read("binary-integrity.json")
    gates = read("verification.json")
    require(gates["status"] == "ROOT_REPEAT_PASS" and gates["newChoiceAndRegistrationPassed"] == 37,
            "missing independent gate repetition")
    require(final["reviewedCommit"] == independent["finalCommit"] == publication["snapshotCommit"]
            == binary["reviewedCommit"] == PIN, "final audit snapshot mismatch")
    require(publication["allConsistencyChecksPassed"] is True and publication["failedChecks"] == [], "publication mismatch")
    require(binary["status"] == "ACTUAL_ARCHIVE_CHECKPOINT_AND_RUNTIME_IDENTITIES_PASS"
            and binary["verifierSha256"] == INPUTS["root-verification/audit_binary_archive.py"], "unbound binary audit")
    for result in (final, independent):
        require(result["limitedBenchmarkLearningCriterionPassed"] is False
                and result["widerSelectedCheckpointHasFourCaseAdvantage"] is False, "unexpected acceptance result")
    models = {}
    for arm in ("S0", "W0", "S1", "W1"):
        expected = final["finalTest"][arm]
        other, actual = independent["results"][arm], binary["models"][arm]
        score = expected["recomputedScore"]
        require(expected["frozen"]["checkpointSha256"] == other["checkpointSha256"] == actual["sha256"],
                "actual binary differs from independent final evidence: " + arm)
        require(expected["parameters"] == other["parameters"] == actual["parameterCountRecomputed"], "parameter mismatch")
        require(int(expected["frozen"]["step"]) == other["selectedStep"] == actual["frozenStep"], "selected step mismatch")
        require(score["correct"] == other["correct"] and score["total"] == other["total"] == 48
                and score["classCorrect"] == other["classCorrect"], "independent score mismatch")
        require(actual["allParametersFinite"] and actual["bodyChecksumVerified"]
                and actual["completeLayoutAndEndOfFileVerified"]
                and actual["adaptationMetadataMatchesFrozenStepScheduleAndTrainingIdentity"], "binary structure invalid")
        models[arm] = {"parameters": actual["parameterCountRecomputed"], "checkpointBytes": actual["bytes"],
                       "float32ParameterBytes": actual["parameterBytes"], "selectedStep": actual["frozenStep"],
                       "checkpointSha256": actual["sha256"], "finalCorrect": score["correct"],
                       "finalTotal": 48, "classCorrect": score["classCorrect"]}
    require(final["counts"]["finalArmCasesRecomputed"] == independent["armCaseJudgmentsRecomputed"] == 192,
            "final case coverage differs")
    require(final["counts"]["knownTrainingArmCasesRecomputed"] == 256
            and all(final["trainingFit"][a]["recomputedScore"]["correct"] == 120 for a in ("S1", "W1")),
            "training-fit separation differs")
    require(binary["actualModelProcesses"] == independent["actualModelProcesses"] == 0
            and final["counts"]["modelProcessesExecutedByThisAudit"] == 0, "unexpected model execution")
    report = {"schemaVersion": 1,
              "status": "REPAIR_AND_EVIDENCE_INTEGRITY_PASS__MODEL_QUALITY_AND_CAPACITY_FAIL",
              "reviewedFinalCommit": PIN, "verifierSha256": sha(Path(__file__)), "pinnedInputs": INPUTS,
              "rootAuditorRepeatsByteIdentical": [own for own, _ in repeats],
              "retainedGateCasesPassed": 37, "gateSuiteRepeatedAfterFinalEvidenceRead": False,
              "finalArmCaseJudgmentsRecomputed": 192, "separateTrainFitCaseJudgmentsRecomputed": 256,
              "publicationTextChecks": publication["checkCount"],
              "independentFinalTextChecks": independent["checkCount"],
              "actualCheckpointBinariesVerified": 4, "actualJarBinariesVerified": 4,
              "actualTokenizerVerifiedSeparately": True, "archiveSha256": binary["archive"]["sha256"],
              "binaryReportSha256": sha(HERE / "binary-integrity.json"), "models": models,
              "pairedSmallWideCounts": independent["pairedSmallWideCounts"],
              "wideMinusSmallCorrect": independent["wideMinusSmallCorrect"],
              "limitedBenchmarkLearningCriterionPassed": False,
              "widerSelectedCheckpointHasFourCaseAdvantage": False,
              "capacityStatusFromPreviouslyAuditedUnchangedLogs": "FAIL_BOTH_SELECTED_CHECKPOINTS",
              "independentBinaryReaderSourceReview": {
                  "reviewer": "independent_current_gate", "scope": "SOURCE_REVIEW_ONLY_NO_BINARY_EXECUTION_BY_REVIEWER",
                  "scriptSha256": INPUTS["root-verification/audit_binary_archive.py"],
                  "kotlinLayoutAndFormatConsistent": True},
              "publishedHeldoutPredictionsRead": True, "originalDatasetFilesOrGeneratorsRead": False,
              "newModelProcesses": 0, "newTrainingProcesses": 0, "selectionOrThresholdsChanged": False,
              "activeForeignRunnersModified": False, "productiveRuntimeModified": False, "modelPromotion": False,
              "interpretation": "The recorded evidence and actual binary artifacts are consistent. This does not turn the failed predeclared quality/capacity requirements into a model approval."}
    (HERE / "final-verification.json").write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")
    print(json.dumps({"status": report["status"], "gateCases": 37, "finalCaseJudgments": 192,
                      "actualCheckpointBinaries": 4, "newModelProcesses": 0}, indent=2))


if __name__ == "__main__":
    main()
