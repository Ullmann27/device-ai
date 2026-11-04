#!/usr/bin/env python3
"""Probe the actual v2/v3 selector and test entry points with synthetic evidence.

The previous 165-case capacity suite is not rerun. Its hash-pinned fixture
builders supply complete capacity observations and 512 synthetic training rows.
No Study or selector method is mocked. Only subprocess.Popen is intercepted;
all model/JAR/tokenizer/data bytes in these tests are nonexecutable sentinels.
"""
from __future__ import annotations

import argparse
from contextlib import redirect_stdout
from datetime import datetime, timezone
import hashlib
import io
import json
from pathlib import Path
import sys
import tempfile
import types
from unittest.mock import patch


HERE = Path(__file__).resolve().parent
OLD = HERE.parents[1] / "recovery-gate-repair"
PIN = "9ceffc26a2159c8dc726c1f13d2882119159c130"
SELECTOR_SHA = "307cc96bbf893bf5432f163b10f4eb1ef3573eabfddc902e4c2416416013a520"
FIXTURE_SHA = "4dddb6459c578c1feefbaee0bbff72eaad7a469eb799a01e078b8b4d33d51aec"
COMPARISON_SHA = "9a359c885823407abc211300eb5103a57d6f9c73aeddd8187763f0ae5209614d"
ORIGINAL = {
    "long": "90877b90a7f138a9c469fb0350bd6c28ff56602b7afb61fe7ee05b1e11ab230b",
    "suffix": "276b2fc27f566fdb3947c9f7fa13e7c9b10cc549354f90b478f16883e9bd4c68",
}
CAPACITY_REPAIR = {
    "long": "638d02eaded31c3d7be7d978d6bcf08319dda9db35e948f1e0142008c2d5d387",
    "suffix": "907d1c9635dc0e375c8d0ef68afa6f85f6c27655758a1895281f74ae0cfc0067",
}


def digest(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def sha(path: Path) -> str:
    return digest(path.read_bytes())


def load(path: Path, expected: str, name: str) -> types.ModuleType:
    raw = path.read_bytes()
    if digest(raw) != expected:
        raise RuntimeError("UNREVIEWED_SOURCE_REJECTED_BEFORE_COMPILE: " + str(path))
    module = types.ModuleType(name)
    module.__file__ = str(path)
    exec(compile(raw, str(path), "exec"), module.__dict__)
    return module


def write_json(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, sort_keys=True, indent=2) + "\n")


def subjects(args: argparse.Namespace) -> dict:
    result = {}
    for kind in ("long", "suffix"):
        if args.phase == "original":
            path, expected = HERE / "fixtures" / ("original-" + kind + ".py.txt"), ORIGINAL[kind]
        elif args.phase == "capacity-repair":
            path, expected = OLD / "fixtures" / kind / "run_registered_study.py.txt", CAPACITY_REPAIR[kind]
        else:
            path, expected = getattr(args, kind + "_runner"), getattr(args, kind + "_sha256")
            if path is None or expected is None:
                raise ValueError("choice-repair requires both exact runner paths and hashes")
        load(path, expected, "reviewed_" + kind)
        result[kind] = (path, expected)
    return result


def cases() -> list[dict]:
    result = [
        {"name": "selected_v3_authorizes", "expected": "boundary"},
        {"name": "v2_retained_on_exact_tie", "expected": "boundary", "tie": True},
        {"name": "v3_selected_on_lower_nll", "expected": "boundary", "tie": True, "lower_nll_v3": True},
        {"name": "valid_capacity_fail_remains_visible", "expected": "summary", "outcome": "eos-fail"},
        {"name": "summary_binds_actual_choice_and_pretest", "expected": "summary"},
    ]
    mutations = ["missing_choice_argument", "missing_pretest_argument", "missing_anchor_argument",
                 "malformed_anchor", "wrong_anchor", "missing_choice_file", "missing_pretest_file",
                 "malformed_choice_json", "malformed_pretest_json", "changed_choice_after_pin",
                 "changed_pretest_after_pin", "false_choice_match", "false_logs_absent",
                 "wrong_selected_source", "wrong_choice_hash", "wrong_freeze_hash", "wrong_runner",
                 "mismatched_selected_payload", "wrong_winner_bound", "wrong_tie_winner_bound",
                 "later_test_log_already_exists", "later_test_receipt_already_exists"]
    result += [{"name": name, "expected": "reject", "mutate": name,
                "tie": name == "wrong_tie_winner_bound"} for name in mutations]
    result += [{"name": f"{target}_changed_{when}", "expected": "reject-during-test",
                "after_call": call, "change_during": target}
               for target in ("choice", "pretest")
               for when, call in (("after_arm1", 1), ("before_summary", 4))]
    return result


class ProcessBoundaryReached(RuntimeError):
    pass


def exercise(fixture, comparison, subject: dict, spec: dict) -> dict:
    attempts = []
    with tempfile.TemporaryDirectory(prefix="choice-binding-", dir=HERE) as temporary:
        root = Path(temporary)
        selector = load(HERE / "fixtures/original-selector.py.txt", SELECTOR_SHA, "real_development_selector")
        selector.BASE = root / "controller-imports"
        modules, studies = {}, {}
        for version, kind, directory in (("v2", "long", "long-study"), ("v3", "suffix", "suffix-study")):
            path, expected = subject[kind]
            module = load(path, expected, "real_study_" + version)
            modules[version] = module
            study = fixture.fixture(module, root / version, path, kind, outcome=spec.get("outcome", "pass"))
            studies[version] = study
            destination = selector.BASE / directory / "run_registered_study.py"
            destination.parent.mkdir(parents=True)
            destination.symlink_to(root / version / fixture.SOURCE_PATHS[kind])
        shared_inputs, shared_stdlib = studies["v2"].inputs, studies["v2"].stdlib
        for version, study in studies.items():
            comparison.complete_comparison_fixture(fixture, study, version, shared_inputs, shared_stdlib,
                stronger=version == "v3" and not spec.get("tie", False))
            if version == "v3" and spec.get("lower_nll_v3"):
                path = study.logs / "intent-wide.tsv"
                lines = path.read_text().splitlines()
                lines = [line.replace("answerNll=1.0", "answerNll=0.5")
                         if line.startswith("SCORE\t") and "\tstep=512\t" in line else
                         line.replace("devAnswerNll=1.0", "devAnswerNll=0.5")
                         if line.startswith("SELECTION\t") else line for line in lines]
                path.write_text("\n".join(lines) + "\n")
                receipt_path = study.logs / "intent-wide.receipt.json"
                value = json.loads(receipt_path.read_text())
                value["logSha256"] = sha(path)
                write_json(receipt_path, value)
            studies[version] = modules[version].Study(argparse.Namespace(output=study.out, jar=study.jar,
                stdlib=shared_stdlib, inputs=shared_inputs, source_commit=PIN))
        directory = root / "final-selection"
        directory.mkdir()
        choice = directory / "development-study-choice.json"
        pretest = directory / "before-test-verification.json"
        argv = ["choose_development_study.py", "--v2-output", str(studies["v2"].out),
                "--v2-jar", str(studies["v2"].jar), "--v2-source", PIN,
                "--v3-output", str(studies["v3"].out), "--v3-jar", str(studies["v3"].jar),
                "--v3-source", PIN, "--inputs", str(shared_inputs), "--stdlib", str(shared_stdlib),
                "--receipt", str(choice)]
        old_argv = sys.argv
        selector_output = io.StringIO()

        def prohibit_selector_process(*unused, **kwargs):
            raise AssertionError("SELECTOR_ATTEMPTED_A_PROCESS")

        try:
            sys.argv = argv
            with patch("subprocess.Popen", side_effect=prohibit_selector_process), redirect_stdout(selector_output):
                selector.main()
        finally:
            sys.argv = old_argv
        choice_value = json.loads(choice.read_text())
        actual_winner = "v2" if spec.get("tie") and not spec.get("lower_nll_v3") else "v3"
        assert choice_value["selectedStudy"] == actual_winner
        tested_version = actual_winner
        mutation = spec.get("mutate")
        if mutation == "wrong_runner":
            tested_version = "v2" if actual_winner == "v3" else "v3"
        elif mutation in {"wrong_winner_bound", "wrong_tie_winner_bound"}:
            tested_version = "v2" if actual_winner == "v3" else "v3"
            choice_value["selectedStudy"] = tested_version
            choice_value["selectedFreezePayload"] = choice_value["candidates"][tested_version]["freezePayload"]
            write_json(choice, choice_value)
        study = studies[tested_version]
        with redirect_stdout(io.StringIO()):
            studies[actual_winner].freeze()
            if tested_version != actual_winner:
                study.freeze()
        frozen = study.out / "selection-freeze.json"
        selected_frozen = studies[actual_winner].out / "selection-freeze.json"
        if mutation in {"wrong_winner_bound", "wrong_tie_winner_bound"}:
            selected_frozen = frozen
        declared_version = actual_winner if mutation == "wrong_runner" else tested_version
        pretest_value = {"choiceSha256": sha(choice), "freezeSha256": sha(selected_frozen),
                         "selectedStudy": declared_version, "selectedSourceCommit": PIN,
                         "allFinalTestLogsAbsent": True, "choiceMatchesFreezeExactly": True}
        write_json(pretest, pretest_value)
        anchor = sha(pretest)
        if mutation == "missing_choice_file":
            choice.unlink()
        elif mutation == "missing_pretest_file":
            pretest.unlink()
        elif mutation == "malformed_choice_json":
            choice.write_text('{"selectedStudy":')
        elif mutation == "malformed_pretest_json":
            pretest.write_text('{"selectedStudy":')
        elif mutation == "changed_choice_after_pin":
            choice.write_bytes(choice.read_bytes() + b"\n")
        elif mutation == "changed_pretest_after_pin":
            pretest.write_bytes(pretest.read_bytes() + b"\n")
        elif mutation in {"false_choice_match", "false_logs_absent", "wrong_selected_source",
                          "wrong_choice_hash", "wrong_freeze_hash"}:
            key, value = {"false_choice_match": ("choiceMatchesFreezeExactly", False),
                          "false_logs_absent": ("allFinalTestLogsAbsent", False),
                          "wrong_selected_source": ("selectedSourceCommit", "f" * 40),
                          "wrong_choice_hash": ("choiceSha256", "0" * 64),
                          "wrong_freeze_hash": ("freezeSha256", "0" * 64)}[mutation]
            pretest_value[key] = value
            write_json(pretest, pretest_value)
            anchor = sha(pretest)
        elif mutation == "mismatched_selected_payload":
            choice_value["selectedFreezePayload"]["arms"]["W1"]["checkpointSha256"] = "0" * 64
            write_json(choice, choice_value)
            pretest_value["choiceSha256"] = sha(choice)
            write_json(pretest, pretest_value)
            anchor = sha(pretest)
        elif mutation == "later_test_log_already_exists":
            (study.logs / "test-W1.tsv").write_text("SYNTHETIC PRIOR TEST LOG\n")
        elif mutation == "later_test_receipt_already_exists":
            write_json(study.logs / "test-W1.receipt.json", {"syntheticPriorAttempt": True})
        study.args.development_choice = None if mutation == "missing_choice_argument" else choice
        study.args.before_test_verification = None if mutation == "missing_pretest_argument" else pretest
        study.args.before_test_verification_sha256 = (None if mutation == "missing_anchor_argument" else
            "not-a-sha256" if mutation == "malformed_anchor" else "0" * 64 if mutation == "wrong_anchor" else anchor)
        protected = {str(path.relative_to(root)): sha(path) for path in root.rglob("*")
                     if path.is_file() and not path.is_symlink() and path not in (choice, pretest)}
        frozen_sha = sha(frozen)
        expected_choice_sha = sha(choice) if choice.is_file() else None
        summary_path = study.out / "intent-summary.json"
        error = None
        output = io.StringIO()

        def fake_process(command, *unused, **kwargs):
            attempts.append(command)
            assert command[5] in {"at.heinz.deviceai.learning.lm.LmIntentLongStudy",
                                  "at.heinz.deviceai.learning.lm.LmIntentSuffixStudy"} and command[6] == "test"
            if spec["expected"] not in {"summary", "reject-during-test"}:
                raise ProcessBoundaryReached("MODEL_PROCESS_BLOCKED_BY_INDEPENDENT_PROBE")
            arm = ["S0", "W0", "S1", "W1"][len(attempts) - 1]
            correct = {"S0": 8, "W0": 8, "S1": 32, "W1": 40}[arm]
            log = fixture.row("TEST_INPUT", checkpointSha256=sha(Path(command[7])), testSha256=sha(Path(command[9])))
            log += fixture.row("SCORE", split="test", step=0, correct=correct, total=48,
                classCorrect=",".join([str(correct // 4)] * 4), classTotal="12,12,12,12", answerNll=1.0,
                eosNll=1.0, prematureEos=0, missingEos=0, generatedTokens=48)
            kwargs["stdout"].write(log.encode())

            def wait(timeout):
                assert timeout == 900
                if len(attempts) == spec.get("after_call"):
                    path = choice if spec["change_during"] == "choice" else pretest
                    path.write_bytes(path.read_bytes() + b"\n")
                return 0

            return types.SimpleNamespace(wait=wait)

        with patch("subprocess.Popen", side_effect=fake_process), redirect_stdout(output):
            try:
                study.test()
            except (OSError, RuntimeError, ValueError, KeyError, TypeError, AssertionError) as exc:
                error = type(exc).__name__ + ": " + str(exc)
        expected = spec["expected"]
        authorization = None
        if expected == "boundary":
            passed = len(attempts) == 1 and error is not None and error.startswith("ProcessBoundaryReached:")
        elif expected == "reject":
            passed = not attempts and error is not None and not summary_path.exists()
        elif expected == "reject-during-test":
            passed = len(attempts) == spec["after_call"] and error is not None and not summary_path.exists()
        else:
            passed = len(attempts) == 4 and error is None and summary_path.is_file()
            if passed:
                summary = json.loads(summary_path.read_text())
                authorization = summary.get("testAuthorization")
                passed = isinstance(authorization, dict)
                if passed:
                    passed = (authorization.get("developmentChoiceSha256") == expected_choice_sha
                              and authorization.get("beforeTestVerificationSha256") == anchor
                              and authorization.get("selectedStudy") == tested_version)
                    capacity = summary.get("postTrainingCapacity", {})
                    expected_status = "FAIL" if spec.get("outcome") == "eos-fail" else "PASS"
                    passed = passed and capacity.get("W1", {}).get("status") == expected_status
                    passed = passed and summary.get("modelPromotion") is False
        unchanged = all(sha(root / path) == value for path, value in protected.items())
        passed = bool(passed and unchanged and sha(frozen) == frozen_sha)
        return {"case": spec["name"], "expected": expected, "passed": passed,
                "realSelectorWinner": actual_winner, "testedStudy": tested_version,
                "processBoundaryCalls": len(attempts), "actualModelProcesses": 0,
                "summaryCreated": summary_path.exists(), "existingEvidenceBytesUnchangedByTest": unchanged,
                "freezeBytesUnchanged": sha(frozen) == frozen_sha,
                "summaryAuthorizationPresent": isinstance(authorization, dict),
                "errorOrBoundary": error.replace(str(root), "<SYNTHETIC_ROOT>") if error else None,
                "stdout": output.getvalue().replace(str(root), "<SYNTHETIC_ROOT>")}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--phase", choices=("original", "capacity-repair", "choice-repair"), required=True)
    parser.add_argument("--report", type=Path, required=True)
    for kind in ("long", "suffix"):
        parser.add_argument("--" + kind + "-runner", type=Path)
        parser.add_argument("--" + kind + "-sha256")
    args = parser.parse_args()
    subject = subjects(args)
    fixture = load(OLD / "independent-tests/probe_post_capacity_gate.py", FIXTURE_SHA, "pinned_capacity_fixture")
    comparison = load(OLD / "independent-tests/probe_development_selector.py", COMPARISON_SHA, "pinned_comparison_fixture")
    fixture.REVIEWED_COMMIT = PIN
    results = [exercise(fixture, comparison, subject, case) for case in cases()]
    failed = sum(not item["passed"] for item in results)
    report = {"schemaVersion": 1, "observedUtc": datetime.now(timezone.utc).isoformat(),
        "reviewedOriginalCommit": PIN, "phase": args.phase, "probeSha256": sha(Path(__file__)),
        "selectorSha256": SELECTOR_SHA, "fixtureBuilderSha256": FIXTURE_SHA,
        "comparisonFixtureBuilderSha256": COMPARISON_SHA,
        "runnerSha256": {kind: expected for kind, (_, expected) in subject.items()},
        "scope": "SYNTHETIC_REAL_SELECTOR_AND_STUDY_TEST_AUTHORIZATION_PATHS",
        "mockedProductionFunction": "subprocess.Popen only; path constants and CLI arguments point to synthetic files",
        "actualModelProcesses": 0, "realHoldoutRead": False,
        "syntheticReceiptsAreTrainingEvidence": False,
        "passed": len(results) - failed, "failed": failed,
        "status": "RED_REPRODUCED" if failed else "GREEN", "exitCode": int(bool(failed)), "cases": results}
    write_json(args.report, report)
    print(json.dumps({key: report[key] for key in ("phase", "passed", "failed", "status", "probeSha256", "exitCode")}))
    return report["exitCode"]


if __name__ == "__main__":
    raise SystemExit(main())
