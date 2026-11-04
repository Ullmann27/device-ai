#!/usr/bin/env python3
"""Recompute the published final test at a fixed Git commit; never run a model.

Only explicit text-evidence paths are read. Reported test hashes are compared,
not resolved to sealed datasets or model files. The published audit is not an
oracle for this checker. Exit zero means evidence consistency, not model success.
"""

from __future__ import annotations

import argparse
import ast
import base64
import hashlib
import json
import math
import re
import struct
import subprocess
from datetime import datetime
from pathlib import Path


FINAL = "59891cfad6dd015f47c187cc688ea94b0026ad7d"
BEFORE = "9ceffc26a2159c8dc726c1f13d2882119159c130"
SOURCE = "be4e49600fed1db4c697230bb8e42c150dbfbdaf"
EVIDENCE = "docs/evidence/lm1-training-recovery-20261104/"
STUDY = "experimental/lm1-training-recovery-20261104/suffix-study/"
REVIEW = "experimental/weight-space-20261104/continuation-review/"
ARMS = ("S0", "W0", "S1", "W1")
PARAMETERS = {"S0": 132160, "S1": 132160, "W0": 6562304, "W1": 6562304}
JAR = "8075d799b42f3b4f23f2f154af09685f52074503b0a4103ba336a618a3be1950"
CHOICE_FILES = tuple(EVIDENCE + "final-selection/" + name for name in (
    "development-study-choice.json", "selection-freeze.json", "before-test-verification.json"))
SUMMARY = EVIDENCE + "study-v3/intent-summary.json"
HARNESS = STUDY + "LmIntentSuffixStudy.kt"
CONTROLLER = STUDY + "run_registered_study.py"
PROTOCOL = STUDY + "SUFFIX_PROTOCOL.md"
LOGS = {arm: EVIDENCE + f"study-v3/logs/test-{arm}.tsv" for arm in ARMS}
RECEIPTS = {arm: EVIDENCE + f"study-v3/logs/test-{arm}.receipt.json" for arm in ARMS}
ALLOWED = {
    FINAL: set(CHOICE_FILES) | set(LOGS.values()) | set(RECEIPTS.values()) |
           {SUMMARY, HARNESS, CONTROLLER, PROTOCOL},
    BEFORE: set(CHOICE_FILES) | {PROTOCOL},
    SOURCE: {HARNESS, CONTROLLER},
}
FROZEN_FILES = {
    "README.md": "20b0b92b3b5d21ceadd55d59c8cffd3b8fcf548d12dea03353b76ccb1c529cde",
    "audit_choice_sources.py": "0327b70c36d9c4e853a288272fcf4e779b682d52de47cb0f3572c27db16f2ead",
    "capacity-repair-choice-binding.json": "3c457a000863e00d39f40a4903a61505235056016a38b35e4d54094474e0588d",
    "capacity-repair-registration.json": "4e19e11a3dbfee2c0bbcb45ec87dd9f5cd17541c9d110d408414f49297ebf128",
    "choice-repair-choice-binding.json": "2448b021e1bc0fcc0558acac795ebe7f76049e7158410729d7946c028f8b89a8",
    "choice-repair-registration.json": "a55bb00be92ffa6f28fa87a8980e8fc4239fb8df09169ee7b076c5742d2a29ab",
    "fixtures/original-long.py.txt": "90877b90a7f138a9c469fb0350bd6c28ff56602b7afb61fe7ee05b1e11ab230b",
    "fixtures/original-selector.py.txt": "307cc96bbf893bf5432f163b10f4eb1ef3573eabfddc902e4c2416416013a520",
    "fixtures/original-suffix.py.txt": "276b2fc27f566fdb3947c9f7fa13e7c9b10cc549354f90b478f16883e9bd4c68",
    "independent-source-comparison.json": "9514ca4ee2e1ad21e11fc7c6a174b85aa64f951db6419526f5c8553e805e9164",
    "original-choice-binding.json": "4980a0f1be2bf0adfda607fa05dd88c797b010bfcca5119999a0e1eef863842f",
    "probe_choice_binding.py": "5954cf5cfd6effe29738f9dae5213287b73430b85f9f076d5d6e3481670cd4a9",
    "probe_prospective_registration.py": "03102af2856607b88d607c0c84caa6647b3fba445485242c625e4a6937ad183b",
    "published-binding-review.json": "82e20c37e99ad6f9e5721430330c89e1f2357c2bfcd95428a739356a8cb9bf2c",
    "source-inventory.json": "0ef9d94dd579db3bd994309320bf73969a826317e6038caf6184c06888b757df",
}
REPAIRED = REVIEW + "source-audit/fixtures/suffix/run_registered_study.py.txt"
REPAIRED_SHA = "67da16654388d322f3735a67f3fbf1d42635ffccf6ac62668ff46b80b47f1942"
FIELDS = {
    "TEST_INPUT": set("checkpointSha256 testSha256 parameters".split()),
    "CASE": set("split step id expected textBase64 tokens stop correct answerNll eosNll firstEosProbability".split()),
    "SCORE": set("split step correct total answerNll eosNll prematureEos classCorrect classTotal meanFirstEosProbability missingEos generatedTokens".split()),
}


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


class Evidence:
    def __init__(self, repo: Path):
        self.repo = repo
        self.inventory: dict[str, dict] = {}
        self.checks: list[str] = []
        self.cache: dict[tuple[str, str], bytes] = {}

    def check(self, name: str, condition: bool) -> None:
        require(condition, name)
        self.checks.append(name)

    def git(self, commit: str, path: str) -> bytes:
        require(path in ALLOWED.get(commit, set()), "path is outside the text evidence allowlist")
        key = (commit, path)
        if key not in self.cache:
            raw = subprocess.check_output(["git", "show", f"{commit}:{path}"], cwd=self.repo)
            raw.decode("utf-8", errors="strict")
            self.cache[key] = raw
            self.inventory[f"{commit}:{path}"] = {"sha256": sha(raw), "bytes": len(raw)}
        return self.cache[key]

    def local(self, path: str, expected: str) -> bytes:
        raw = (self.repo / path).read_bytes()
        self.check("frozen local SHA256: " + path, sha(raw) == expected)
        self.inventory[path] = {"sha256": sha(raw), "bytes": len(raw)}
        return raw


def read_log(raw: bytes, arm: str) -> tuple[dict, list[dict], dict, list[str]]:
    records: list[tuple[str, dict]] = []
    diagnostics = []
    for line in raw.decode("utf-8").splitlines():
        parts = line.split("\t")
        kind = parts[0]
        if kind not in FIELDS:
            # Explicitly retained JVM diagnostic, never silently counted as a case.
            require(arm == "W0" and re.fullmatch(
                r"\[\d+\.\d+s\]\[warning\]\[perf,memops\] Cannot use file "
                r"/tmp/hsperfdata_[^/]+/\d+ because it is locked by another process \(errno = 11\)",
                line) is not None, f"unrecognized log line in {arm}")
            diagnostics.append(line)
            continue
        values = {}
        for item in parts[1:]:
            key, separator, value = item.partition("=")
            require(bool(separator) and key not in values, f"invalid/duplicate field in {arm}")
            values[key] = value
        require(set(values) == FIELDS[kind], f"unexpected {kind} fields in {arm}")
        records.append((kind, values))
    require([kind for kind, _ in records] == ["TEST_INPUT"] + ["CASE"] * 48 + ["SCORE"],
            f"incomplete or reordered records in {arm}")
    require(len(diagnostics) == (1 if arm == "W0" else 0), f"unexpected diagnostic count in {arm}")
    return records[0][1], [v for _, v in records[1:-1]], records[-1][1], diagnostics


def audit(repo: Path) -> dict:
    e = Evidence(repo)
    prior = {path: e.git(BEFORE, path) for path in CHOICE_FILES}
    for path, raw in prior.items():
        e.check("pretest document unchanged: " + path, raw == e.git(FINAL, path))
    choice, freeze, before = (json.loads(prior[path]) for path in CHOICE_FILES)
    e.check("published choice selects the complete V3 freeze",
            choice["selectedStudy"] == "v3" and choice["selectedFreezePayload"] == freeze and
            choice["candidates"]["v3"]["freezePayload"] == freeze and set(freeze["arms"]) == set(ARMS))
    e.check("pretest JSON binds choice and freeze raw hashes",
            before["choiceSha256"] == sha(prior[CHOICE_FILES[0]]) and
            before["freezeSha256"] == sha(prior[CHOICE_FILES[1]]) and
            before["selectedStudy"] == "v3" and before["selectedSourceCommit"] == SOURCE and
            before["allFinalTestLogsAbsent"] is True and before["choiceMatchesFreezeExactly"] is True)
    e.check("freeze source and common test identity",
            freeze["sourceCommit"] == SOURCE and freeze["testSha256"] == choice["testSha256"] and
            freeze["status"] == "FROZEN_BEFORE_TEST")
    e.check("acceptance protocol predates results and is unchanged",
            e.git(BEFORE, PROTOCOL) == e.git(FINAL, PROTOCOL))
    for path in (HARNESS, CONTROLLER):
        e.check("recorded execution source unchanged: " + path,
                e.git(SOURCE, path) == e.git(FINAL, path))
    harness = e.git(FINAL, HARNESS).decode()
    e.check("strict scorer is source bound",
            "result.text == label && result.newTokens == 1 && result.stop == LmRuntime.Stop.EOS" in harness)
    controller = e.git(FINAL, CONTROLLER).decode()
    e.check("unweakened numerical acceptance is source bound", all(text in controller for text in (
        'int(w["correct"]) >= 36', 'min(map(int, w["classCorrect"].split(","))) >= 8',
        'int(w["correct"]) >= int(outputs[a]["correct"]) + 8 for a in ["S0", "W0"]',
        'int(w["correct"]) >= int(outputs["S1"]["correct"]) + 4')))
    summary = json.loads(e.git(FINAL, SUMMARY))
    e.check("summary binds the previously published freeze",
            summary["selectionSha256"] == sha(prior[CHOICE_FILES[1]]))
    all_cases: dict[str, dict] = {}
    results = {}
    for arm in ARMS:
        raw = e.git(FINAL, LOGS[arm])
        identity, cases, score, diagnostics = read_log(raw, arm)
        selected = freeze["arms"][arm]
        step = int(selected["step"])
        e.check(arm + " exact frozen checkpoint/test/parameter identity",
                identity["checkpointSha256"] == selected["checkpointSha256"] and
                identity["testSha256"] == freeze["testSha256"] and
                int(identity["parameters"]) == PARAMETERS[arm])
        e.check(arm + " score equals published summary", summary["scores"][arm] == score)
        e.check(arm + " frozen step and final split",
                score["split"] == "test" and int(score["step"]) == step and
                all(row["split"] == "test" and int(row["step"]) == step for row in cases))
        receipt = json.loads(e.git(FINAL, RECEIPTS[arm]))
        command = receipt["command"]
        e.check(arm + " receipt binds log and declared source artifacts",
                receipt["label"] == "test-" + arm and receipt["logSha256"] == sha(raw) and
                receipt["sourceCommit"] == SOURCE and receipt["registrationSha256"] == freeze["registrationSha256"] and
                receipt["jarSha256"] == JAR and receipt["registeredBytesUnchanged"] is True)
        e.check(arm + " unchanged parent process bounds and selected checkpoint path",
                len(command) == 10 and command[:4] == ["java", "-Xms64m", "-Xmx3g", "-cp"] and
                command[5:7] == ["at.heinz.deviceai.learning.lm.LmIntentSuffixStudy", "test"] and
                command[7] == selected["path"] and receipt["timeoutSeconds"] == 900 and
                receipt["returncode"] == 0 and receipt["timeout"] is False and
                0 < receipt["wallSeconds"] <= receipt["timeoutSeconds"])
        e.check(arm + " timestamp has an explicit timezone", datetime.fromisoformat(receipt["startedUtc"]).tzinfo is not None)
        checked = {}
        confusion = [[0] * 5 for _ in range(4)]
        for row in cases:
            case_id, expected = row["id"], row["expected"]
            require(case_id and case_id not in checked and expected in ("0", "1", "2", "3"),
                    f"invalid case identity or label in {arm}")
            text = base64.b64decode(row["textBase64"], validate=True).decode("utf-8")
            tokens = int(row["tokens"])
            require(0 <= tokens <= 4 and row["stop"] in ("EOS", "MAX_TOKENS"), f"invalid stop/tokens in {arm}")
            valid_format = text in ("0", "1", "2", "3") and tokens == 1 and row["stop"] == "EOS"
            correct = text == expected and tokens == 1 and row["stop"] == "EOS"
            require(row["correct"] in ("true", "false") and correct == (row["correct"] == "true"),
                    f"incorrect logged CASE verdict: {arm}/{case_id}")
            nll = [float(row["answerNll"]), float(row["eosNll"])]
            probability = struct.unpack("<f", struct.pack("<f", float(row["firstEosProbability"])))[0]
            require(all(math.isfinite(v) and v >= 0 for v in nll) and
                    math.isfinite(probability) and 0 <= probability <= 1, f"invalid metric: {arm}/{case_id}")
            confusion[int(expected)][int(text) if valid_format else 4] += 1
            checked[case_id] = {"expected": expected, "text": text, "tokens": tokens, "stop": row["stop"],
                                "correct": correct, "validFormat": valid_format,
                                "answerNll": nll[0], "eosNll": nll[1], "firstEosFloat32": probability}
        values = list(checked.values())
        computed = {
            "correct": sum(row["correct"] for row in values), "total": len(values),
            "classCorrect": [sum(row["correct"] and row["expected"] == str(k) for row in values) for k in range(4)],
            "classTotal": [sum(row["expected"] == str(k) for row in values) for k in range(4)],
            "prematureEos": sum(row["tokens"] == 0 and row["stop"] == "EOS" for row in values),
            "missingEos": sum(row["stop"] != "EOS" for row in values),
            "generatedTokens": sum(row["tokens"] for row in values),
            "answerNll": sum(row["answerNll"] for row in values) / len(values),
            "eosNll": sum(row["eosNll"] for row in values) / len(values),
            "meanFirstEosProbability": sum(row["firstEosFloat32"] for row in values) / len(values),
        }
        e.check(arm + " complete balanced final cases", computed["total"] == 48 and computed["classTotal"] == [12] * 4)
        for field, observed in computed.items():
            if isinstance(observed, list):
                matches = observed == [int(value) for value in score[field].split(",")]
            elif isinstance(observed, float):
                matches = math.isclose(observed, float(score[field]), rel_tol=1e-12, abs_tol=1e-15)
            else:
                matches = observed == int(score[field])
            e.check(arm + " CASE-derived SCORE " + field, matches)
        all_cases[arm] = checked
        results[arm] = {**computed, "parameters": PARAMETERS[arm], "selectedStep": step,
                        "checkpointSha256": identity["checkpointSha256"],
                        "validDigitAndEosFormat": sum(row["validFormat"] for row in values),
                        "confusionColumns": ["0", "1", "2", "3", "invalid"], "confusionRows": confusion,
                        "nonCaseDiagnostics": diagnostics, "receiptWallSeconds": receipt["wallSeconds"],
                        "receiptStartedUtc": receipt["startedUtc"]}
    reference = [(key, row["expected"]) for key, row in all_cases["S0"].items()]
    for arm, cases in all_cases.items():
        e.check(arm + " same ordered IDs and expected labels across arms",
                [(key, row["expected"]) for key, row in cases.items()] == reference)
    paired = {name: [] for name in ("bothCorrect", "smallOnly", "wideOnly", "bothWrong")}
    for case_id, small in all_cases["S1"].items():
        wide = all_cases["W1"][case_id]
        key = ("bothCorrect" if small["correct"] and wide["correct"] else "smallOnly" if small["correct"]
               else "wideOnly" if wide["correct"] else "bothWrong")
        paired[key].append(case_id)
    w, s = results["W1"], results["S1"]
    criteria = {
        "wideAtLeast36Of48": w["correct"] >= 36,
        "wideEveryClassAtLeast8Of12": min(w["classCorrect"]) >= 8,
        "wideAtLeast8MoreThanS0": w["correct"] >= results["S0"]["correct"] + 8,
        "wideAtLeast8MoreThanW0": w["correct"] >= results["W0"]["correct"] + 8,
    }
    limited, wider_advantage = all(criteria.values()), w["correct"] >= s["correct"] + 4
    e.check("summary limited criterion agrees with unchanged thresholds",
            summary["limitedBenchmarkLearningCriterionPassed"] is limited)
    e.check("summary wider advantage agrees with unchanged four-case threshold",
            summary["widerSelectedCheckpointHasFourCaseAdvantage"] is wider_advantage)
    e.check("summary makes no promotion, general-intelligence or Android claim",
            summary["modelPromotion"] is False and summary["generalIntelligenceClaim"] is False and
            summary["androidValidation"] == "NOT_RUN")

    frozen_raw = {}
    for path, expected_sha in FROZEN_FILES.items():
        frozen_raw[path] = e.local(REVIEW + "independent-tests/" + path, expected_sha)
    repaired_raw = e.local(REPAIRED, REPAIRED_SHA)
    repaired_ast = ast.parse(repaired_raw.decode())
    study_class = next(node for node in repaired_ast.body if isinstance(node, ast.ClassDef) and node.name == "Study")
    test_method = next(node for node in study_class.body if isinstance(node, ast.FunctionDef) and node.name == "test")
    guards = [node for node in test_method.body if isinstance(node, ast.If) and any(
        isinstance(child, ast.Raise) and isinstance(child.exc, ast.Call) and
        child.exc.args and isinstance(child.exc.args[0], ast.Constant) and
        child.exc.args[0].value == "FINAL_TEST_ALREADY_STARTED" for child in node.body)]
    require(len(guards) == 1, "missing unique top-level replay guard")
    guard = guards[0]
    expected_guard = ast.parse('any(self.logs.glob("test-*.tsv")) or any(self.logs.glob("test-*.receipt.json"))', mode="eval").body
    run_calls = [node for node in ast.walk(test_method) if isinstance(node, ast.Call) and
                 isinstance(node.func, ast.Attribute) and isinstance(node.func.value, ast.Name) and
                 node.func.value.id == "self" and node.func.attr == "run"]
    e.check("repaired runner refuses either existing final artifact before first process",
            ast.dump(guard.test) == ast.dump(expected_guard) and bool(run_calls) and
            guard.lineno < min(call.lineno for call in run_calls))
    old_probe = json.loads(frozen_raw["choice-repair-choice-binding.json"])
    replay_cases = [row for row in old_probe["cases"] if row["case"] in (
        "later_test_log_already_exists", "later_test_receipt_already_exists")]
    e.check("existing real-method synthetic probes bind this exact repaired source",
            old_probe["runnerSha256"]["suffix"] == REPAIRED_SHA and len(replay_cases) == 2 and
            all(row["passed"] is True and row["processBoundaryCalls"] == 0 and
                row["errorOrBoundary"] == "RuntimeError: FINAL_TEST_ALREADY_STARTED" and
                row["existingEvidenceBytesUnchangedByTest"] is True for row in replay_cases))
    control_outputs_identical = all(
        tuple(all_cases["S0"][case_id][k] for k in ("text", "tokens", "stop")) ==
        tuple(all_cases["W0"][case_id][k] for k in ("text", "tokens", "stop")) for case_id, _ in reference)
    return {
        "schemaVersion": 1,
        "status": "PASS_TEXT_EVIDENCE_CONSISTENCY__PREDECLARED_MODEL_CRITERION_FAILED" if not limited else
                  "PASS_TEXT_EVIDENCE_CONSISTENCY__PREDECLARED_MODEL_CRITERION_PASSED",
        "scope": "INDEPENDENT_PUBLISHED_FINAL_LOG_RECOMPUTATION_WITHOUT_MODEL_EXECUTION",
        "finalCommit": FINAL, "previousChoiceFreezeCommit": BEFORE, "recordedExecutionSourceCommit": SOURCE,
        "checkerSha256": sha(Path(__file__).read_bytes()), "actualModelProcesses": 0,
        "sealedDatasetOrGeneratorOrModelOrTokenizerOrJarBytesRead": False,
        "testCases": 48, "armCaseJudgmentsRecomputed": 192, "results": results,
        "selectedStudy": "v3", "selectedFreezeSha256": sha(prior[CHOICE_FILES[1]]),
        "criteria": criteria, "limitedBenchmarkLearningCriterionPassed": limited,
        "widerSelectedCheckpointHasFourCaseAdvantage": wider_advantage,
        "wideMinusSmallCorrect": w["correct"] - s["correct"],
        "pairedSmallWideCounts": {key: len(value) for key, value in paired.items()}, "pairedSmallWideIds": paired,
        "untrainedControlOutputsIdenticalOnAll48": control_outputs_identical,
        "replay": {"assessment": "STATIC_GUARD_PLUS_EXISTING_SYNTHETIC_REAL_METHOD_PROBES",
                   "repairedSourceSha256": REPAIRED_SHA, "publishedFinalLogs": list(LOGS.values()),
                   "publishedFinalReceipts": list(RECEIPTS.values()),
                   "existingProbeCases": replay_cases, "newReplayExecutionPerformed": False,
                   "applicability": "With these test artifacts present in the active study logs directory, the repaired runner rejects a new final test before any process; earlier authorization checks may reject historical evidence first.",
                   "historicalFinalRunUsedThisRepair": False},
        "unchangedEarlierIndependentFiles": FROZEN_FILES,
        "interpretation": {
            "confirmationRoundClosed": True, "newSelectionOrTrainingRuleDerived": False,
            "causalParameterAdvantageEstablished": False, "statisticalSignificanceClaim": False,
            "selectedStepsDiffer": s["selectedStep"] != w["selectedStep"],
            "generalCapacityRequirementsRelaxed": False, "generalCapacityRecomputedHere": False,
            "modelPromotion": False,
        },
        "limitations": [
            "Expected labels are compared across the published logs; the sealed test file is deliberately not opened.",
            "Checkpoint, registration and JAR identities are compared as recorded; the referenced binary bytes are not rehashed.",
            "Reported process limits and completion are checked from receipts; no independent resource measurement is performed.",
            "The 31 versus 25 result describes preselected checkpoints at steps 512 and 128; it does not isolate a parameter-count effect.",
            "The protocol states template dependence; neither 48 independent draws nor statistical significance is claimed.",
            "Historical choice and freeze bytes are checked at fixed commits; cryptographic publication time is not established.",
            "Post-test train-fit data and the published final audit are not inputs to this independent recomputation.",
            "Replay enforcement is a source-bound inference backed by earlier synthetic probes, not a new execution of historical models.",
        ],
        "checkCount": len(e.checks), "checks": e.checks, "inputs": e.inventory,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[5])
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = audit(args.repo.resolve())
    rendered = json.dumps(result, indent=2, sort_keys=True, ensure_ascii=False) + "\n"
    if args.output is None:
        print(rendered, end="")
    else:
        args.output.write_text(rendered, encoding="utf-8")
        print(json.dumps({"status": result["status"], "checks": result["checkCount"],
                          "armCaseJudgmentsRecomputed": 192, "modelCriterionPassed":
                          result["limitedBenchmarkLearningCriterionPassed"], "output": str(args.output)}))


if __name__ == "__main__":
    main()
