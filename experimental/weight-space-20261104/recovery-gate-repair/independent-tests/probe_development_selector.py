#!/usr/bin/env python3
"""Exercise the unchanged published selector main() with real Study controllers.

All files and 512-step records are synthetic; no training or holdout evaluation
occurs. This closes the separate v2/v3 selection entry point, without replacing
controller(), verify(), selection(), freeze_evidence() or audit_post_capacity().
Only Popen is intercepted; BASE and CLI arguments point at temporary fixtures.
"""
from __future__ import annotations

import argparse
from contextlib import redirect_stdout
from datetime import datetime, timezone
import importlib.util
import io
import json
import os
from pathlib import Path
import sys
import tempfile
from unittest.mock import patch


HERE = Path(__file__).resolve().parent
SELECTOR_SHA256 = "307cc96bbf893bf5432f163b10f4eb1ef3573eabfddc902e4c2416416013a520"
PROBE_SHA256 = "4dddb6459c578c1feefbaee0bbff72eaad7a469eb799a01e078b8b4d33d51aec"
PATCHED_HASHES = {
    "long": "638d02eaded31c3d7be7d978d6bcf08319dda9db35e948f1e0142008c2d5d387",
    "suffix": "907d1c9635dc0e375c8d0ef68afa6f85f6c27655758a1895281f74ae0cfc0067"}


def load_probe():
    import hashlib
    path = HERE / "probe_post_capacity_gate.py"
    if hashlib.sha256(path.read_bytes()).hexdigest() != PROBE_SHA256:
        raise RuntimeError("FIXTURE_BUILDER_SOURCE_CHANGED")
    spec = importlib.util.spec_from_file_location("independent_capacity_fixture", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def complete_comparison_fixture(probe, study, version: str, inputs: Path, stdlib: Path,
                                stronger: bool = False) -> None:
    """Add the selector's genuine 512-step data contract to synthetic receipts."""
    for size in ("small", "wide"):
        training_log = study.logs / ("intent-" + size + ".tsv")
        lines = []
        for line in training_log.read_text().splitlines():
            if line.startswith("MANIFEST\t"):
                fields = dict(part.split("=", 1) for part in line.split("\t")[1:])
                fields["scheduleSha256"] = probe.digest(b"SYNTHETIC MATCHED EXAMPLE ORDER")
                fields["tokenScheduleSha256"] = probe.digest(("SYNTHETIC WRAPPER " + version).encode())
                line = probe.row("MANIFEST", **fields).rstrip("\n")
            if stronger and size == "wide" and line.startswith("SCORE\t") and "\tstep=512\t" in line:
                line = line.replace("\tcorrect=16\t", "\tcorrect=18\t")
            if line.startswith("SELECTION\t"):
                lines.extend(probe.row("TRAIN_STEP", step=step, supervisedTargets=step * 8,
                                       answerEosNll=1.0, gradientNormBeforeClip=0.1,
                                       millis=step).rstrip("\n") for step in range(1, 513))
                if stronger and size == "wide":
                    line = line.replace("\tdevCorrect=16\t", "\tdevCorrect=18\t")
            lines.append(line)
        training_log.write_text("\n".join(lines) + "\n")
        receipt_path = study.logs / ("intent-" + size + ".receipt.json")
        receipt = json.loads(receipt_path.read_text())
        receipt["logSha256"] = probe.file_hash(training_log)
        receipt["command"][4] = str(study.jar) + os.pathsep + str(stdlib)
        receipt["command"][7] = str(inputs / "lm1-baseline.lm1")
        receipt["command"][8] = str(inputs / "lm1-tokenizer.lmbp")
        probe.put_json(receipt_path, receipt)
        log = probe.after_path(study, "tsv", size)
        raw = log.read_text().replace(str(study.inputs / "lm1-tokenizer.lmbp"), str(inputs / "lm1-tokenizer.lmbp"))
        log.write_text(raw)
        receipt_path = probe.after_path(study, "receipt.json", size)
        receipt = json.loads(receipt_path.read_text())
        receipt["logSha256"] = probe.file_hash(log)
        receipt["command"][4] = str(study.jar) + os.pathsep + str(stdlib)
        receipt["command"][-1] = str(inputs / "lm1-tokenizer.lmbp")
        probe.put_json(receipt_path, receipt)


def exercise(probe, phase: str, spec: dict) -> dict:
    selector_path = HERE / "fixtures/original-choose_development_study.py.txt"
    selector = probe.load_reviewed(selector_path, SELECTOR_SHA256)
    attempts = []
    with tempfile.TemporaryDirectory(prefix="selector-fixture-", dir=HERE) as temporary:
        root = Path(temporary)
        selector.BASE = root / "controller-imports"
        studies = {}
        controller_hashes = {}
        for version, kind, directory in (("v2", "long", "long-study"), ("v3", "suffix", "suffix-study")):
            source = (HERE / "fixtures" / ("original-" + kind + "-run_registered_study.py.txt")
                      if phase == "original" else HERE.parent / "fixtures" / kind / "run_registered_study.py.txt")
            expected = probe.ORIGINAL_HASHES[kind] if phase == "original" else PATCHED_HASHES[kind]
            module = probe.load_reviewed(source, expected)
            controller_hashes[kind] = expected
            study = probe.fixture(module, root / version, source, kind,
                                  outcome=spec.get("outcome", "pass"), no_signal=spec.get("no_signal", False))
            studies[version] = study
            destination = selector.BASE / directory / "run_registered_study.py"
            destination.parent.mkdir(parents=True)
            destination.symlink_to(root / version / probe.SOURCE_PATHS[kind])
        inputs, stdlib = studies["v2"].inputs, studies["v2"].stdlib
        for version, study in studies.items():
            complete_comparison_fixture(probe, study, version, inputs, stdlib,
                                        stronger=spec.get("stronger_v3", False) and version == "v3")
        if spec.get("mutate"):
            probe.mutate(studies["v3"], spec["mutate"], "suffix")
        protected = {str(p.relative_to(root)): probe.file_hash(p) for p in root.rglob("*")
                     if p.is_file() and not p.is_symlink()}
        output = root / "development-choice.json"
        argv = [str(selector_path), "--v2-output", str(studies["v2"].out),
                "--v2-jar", str(studies["v2"].jar), "--v2-source", probe.REVIEWED_COMMIT,
                "--v3-output", str(studies["v3"].out), "--v3-jar", str(studies["v3"].jar),
                "--v3-source", probe.REVIEWED_COMMIT, "--inputs", str(inputs), "--stdlib", str(stdlib),
                "--receipt", str(output)]
        error = None
        captured = io.StringIO()

        def block_process(command, *args, **kwargs):
            attempts.append(command)
            raise AssertionError("SELECTOR_MUST_NOT_LAUNCH_ANY_PROCESS")

        old_argv = sys.argv
        try:
            sys.argv = argv
            with patch("subprocess.Popen", side_effect=block_process), redirect_stdout(captured):
                try:
                    selector.main()
                except (OSError, RuntimeError, ValueError, KeyError, TypeError, AssertionError) as exc:
                    error = type(exc).__name__ + ": " + str(exc)
        finally:
            sys.argv = old_argv
        selection = json.loads(output.read_text()) if output.is_file() else None
        if spec.get("reject"):
            passed = error is not None and selection is None and not attempts
        elif spec.get("no_signal"):
            passed = error is None and selection is not None and selection["status"] == "NO_ELIGIBLE_STUDY"
            passed = passed and selection.get("testEvaluated") is False and not attempts
        else:
            passed = error is None and selection is not None and not attempts
            if passed:
                passed = selection["status"] == "DEVELOPMENT_CHOICE_BEFORE_TEST"
                passed = passed and selection["selectedStudy"] == ("v3" if spec.get("stronger_v3") else "v2")
                passed = passed and selection.get("testEvaluated") is False
                evidence = selection["selectedFreezePayload"].get("postTrainingCapacity", {})
                passed = passed and set(evidence) == {"S1", "W1"}
                if passed:
                    expected_status = "FAIL" if spec.get("outcome") == "eos-fail" else "PASS"
                    passed = evidence["S1"]["status"] == "PASS" and evidence["W1"]["status"] == expected_status
        unchanged = all(probe.file_hash(root / p) == value for p, value in protected.items())
        no_test_outputs = all(not list(study.logs.glob("test-*.tsv")) for study in studies.values())
        return {"case": spec["name"], "passed": bool(passed and unchanged and no_test_outputs),
                "controllerSourceSha256": controller_hashes,
                "choiceReceiptWritten": output.is_file(), "actualProcessesStarted": 0,
                "processBoundaryCalls": len(attempts), "existingEvidenceBytesUnchanged": unchanged,
                "noTestOutputsCreated": no_test_outputs,
                "selectedStudy": selection.get("selectedStudy") if selection else None,
                "status": selection.get("status") if selection else None,
                "error": error.replace(str(root), "<SYNTHETIC_ROOT>") if error else None,
                "stdout": captured.getvalue().replace(str(root), "<SYNTHETIC_ROOT>")}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--phase", choices=("original", "repaired"), required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    probe = load_probe()
    specs = [
        {"name": "complete_pass_tie_retains_v2_with_capacity"},
        {"name": "complete_eos_fail_stays_eligible_and_disclosed", "outcome": "eos-fail"},
        {"name": "higher_dev_exact_chooses_v3", "stronger_v3": True},
        {"name": "no_development_signal_produces_no_eligible_choice", "no_signal": True},
        {"name": "missing_post_capacity_blocks_choice", "mutate": "missing_receipt", "reject": True},
        {"name": "missing_raw_trial_blocks_choice", "mutate": "missing_trial", "reject": True},
        {"name": "process_budget_mismatch_blocks_choice", "mutate": "receipt_wrong_budget", "reject": True},
        {"name": "source_receipt_mismatch_blocks_choice", "mutate": "receipt_source_mismatch", "reject": True},
        {"name": "invalid_evidence_is_not_relabelled_no_development_signal", "mutate": "missing_trial",
         "reject": True, "no_signal": True}]
    results = [exercise(probe, args.phase, spec) for spec in specs]
    failed = sum(not result["passed"] for result in results)
    report = {"schemaVersion": 1, "observedUtc": datetime.now(timezone.utc).isoformat(),
              "reviewedCommit": probe.REVIEWED_COMMIT,
              "selectorSourcePath": probe.BASE + "/suffix-study/choose_development_study.py",
              "selectorSourceSha256": SELECTOR_SHA256, "fixtureBuilderSha256": PROBE_SHA256,
              "probeSha256": probe.file_hash(Path(__file__)), "controllerPhase": args.phase,
              "scope": "SYNTHETIC_REAL_SELECTOR_MAIN_AND_REAL_CONTROLLER_METHODS",
              "mockedBoundary": "subprocess.Popen only; selector BASE and CLI paths relocated",
              "actualModelProcesses": 0, "realHoldoutRead": False, "syntheticReceiptsAreModelEvidence": False,
              "publishedPr508Changed": False, "cases": results, "passed": len(results) - failed,
              "failed": failed, "status": "RED_REPRODUCED" if failed else "GREEN", "exitCode": int(bool(failed))}
    probe.put_json(args.report, report)
    print(json.dumps({key: report[key] for key in ("controllerPhase", "status", "passed", "failed", "exitCode")}))
    return int(bool(failed))


if __name__ == "__main__":
    raise SystemExit(main())
