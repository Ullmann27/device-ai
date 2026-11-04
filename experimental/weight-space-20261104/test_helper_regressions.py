"""G1-REPAIR-WEIGHT-001 training-gate regressions with generated inputs only.

The real constructor, receipt validation, and phase control flow run unchanged.
Only Java process boundaries are replaced; no model or sealed dataset is read.
"""
from __future__ import annotations

import argparse
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch


class ProcessBoundaryReached(Exception):
    """Stop a valid transition before any external process can launch."""


class TrainingGateRegressions(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="weight-helper-regression-")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        source = self.root / "source"
        source.mkdir()
        script = source / "run_experiment.py"
        script.write_bytes(Path(__file__).with_name("run_experiment.py").read_bytes())
        (source / "PROTOCOL.md").write_text("Synthetic test protocol.\n")
        (source / "development.txt").write_text("Synthetic development data.\n")
        spec = importlib.util.spec_from_file_location("temporary_weight_experiment", script)
        self.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.module)
        inputs = self.root / "inputs"
        inputs.mkdir()
        base = inputs / "lm1-baseline.lm1"
        tokenizer = inputs / "lm1-tokenizer.lmbp"
        holdout = self.root / "synthetic-holdout.txt"
        jar = self.root / "synthetic.jar"
        for path, data in ((base, b"synthetic checkpoint"), (tokenizer, b"synthetic tokenizer"),
                           (holdout, b"synthetic held-out bytes"), (jar, b"synthetic jar")):
            path.write_bytes(data)
        self.module.BASE_SHA = self.module.sha(base)
        self.module.TOKENIZER_SHA = self.module.sha(tokenizer)
        self.module.HOLDOUT_SHA = self.module.sha(holdout)
        self.args = argparse.Namespace(inputs=inputs, output=self.root / "output", jar=jar, holdout=holdout)
        with patch.object(self.module.subprocess, "run", return_value=subprocess.CompletedProcess(
                ["java", "-version"], 0, "", "synthetic Java version")):
            self.experiment = self.module.Experiment(self.args)

    @staticmethod
    def row(kind, **values):
        return "\t".join([kind, *(f"{key}={value}" for key, value in values.items())]) + "\n"

    def source_row(self, checkpoint):
        return self.row("SOURCE", sha256=self.module.sha(checkpoint),
                        tokenizerSha256=self.module.TOKENIZER_SHA)

    def cache(self, label, arguments, text, *, benchmark=False, timeout=False):
        experiment = self.experiment
        path = experiment.logs / f"{label}.tsv"
        path.write_text(text)
        self.module.write_new(experiment.logs / f"{label}.receipt.json", {
            "arguments": arguments, "returncode": 0, "timeout": timeout,
            "logSha256": self.module.sha(path),
            "inputHashes": experiment.input_hashes(arguments, benchmark),
            "orchestratorSha256": experiment.orchestrator_sha,
            "identityValidationFailure": None,
        })

    def evaluation_receipt(self, label, checkpoint, bpb):
        experiment = self.experiment
        self.cache(label + "-dev", ["evaluate", str(checkpoint), str(experiment.tokenizer),
                                    str(experiment.development)],
                   self.source_row(checkpoint) + self.row(
                       "EVALUATION", bpb=bpb, datasetSha256=experiment.development_sha,
                       tokenizerSha256=self.module.TOKENIZER_SHA))

    def complete_candidate_receipts(self, candidate, bpb, *, evaluation=True):
        experiment = self.experiment
        checkpoint = Path(candidate["path"])
        if evaluation:
            self.evaluation_receipt(candidate["label"], checkpoint, bpb)
        self.cache(candidate["label"] + "-capacity", [str(checkpoint), str(experiment.tokenizer)],
                   self.row("CAPACITY_MODEL", checkpointSha256=candidate["sha256"],
                            tokenizerSha256=self.module.TOKENIZER_SHA) + self.row(
                       "CAPACITY_RESULT", **{"pass": "true", "checkpointSha256": candidate["sha256"],
                                             "measuredRepeats": 3, "firstTokenObservations": 3,
                                             "full32TokenObservations": 3}), benchmark=True)
        self.cache(candidate["label"] + "-probes", ["probes", str(checkpoint), str(experiment.tokenizer)],
                   self.source_row(checkpoint))

    def synthetic_candidate(self, label, parameters):
        checkpoint = self.experiment.store / (label + ".lm1")
        checkpoint.write_text("Synthetic checkpoint: " + label)
        return {"label": label, "path": str(checkpoint), "sha256": self.module.sha(checkpoint),
                "parameters": parameters, "bytes": checkpoint.stat().st_size, "capacityEligible": True}

    def successful_training_receipt(self, initial, label, bpb):
        experiment = self.experiment
        trained = self.synthetic_candidate(label, initial["parameters"])
        text = (self.source_row(Path(initial["path"])) + self.row(
            "TRAIN_INPUT", datasetSha256=experiment.training_hash(),
            tokenizerSha256=self.module.TOKENIZER_SHA, tokenScheduleSha256="1" * 64)
            + self.row("TRAIN_RESULT", steps=16)
            + self.row("CHECKPOINT", **{key: trained[key] for key in ("path", "sha256", "parameters", "bytes")}))
        self.cache(label + "-train", ["train", initial["path"], str(experiment.tokenizer),
                                     str(experiment.out / "train.txt"), str(experiment.store)], text)
        self.complete_candidate_receipts(trained, bpb)
        return trained

    def continuation_fixture(self, *, last_passed=True, stop=None):
        experiment = self.experiment
        documents = experiment.inputs / "original-train-docs"
        documents.mkdir()
        (documents / "paths.tsv").write_text("synthetic.md\n")
        (documents / "synthetic.md").write_text("Synthetic training data.\n")
        train = experiment.out / "train.txt"
        train.write_text("Synthetic training data.\n")
        self.cache("prepare-corpus", ["corpus", str(documents), str(train)],
                   self.row("CORPUS", sha256=self.module.sha(train)))
        baseline = {"label": "baseline", "path": str(experiment.base),
                    "sha256": self.module.BASE_SHA, "parameters": 132160,
                    "bytes": experiment.base.stat().st_size, "devBpb": 8.0,
                    "capacityEligible": True}
        self.evaluation_receipt("baseline", experiment.base, 8.0)
        self.module.write_new(experiment.out / "calibration.json", {
            "candidates": [baseline], "selected": baseline})
        self.module.write_new(experiment.out / "ladder.json", {
            "stages": [baseline], "lastPassed": baseline if last_passed else None,
            "stop": stop})
        return baseline

    def assert_training_blocked(self, experiment=None, *, existing_selection=False):
        experiment = experiment or self.experiment
        attempts = []
        failure = None
        selection = experiment.out / "frozen-selection.json"
        before = selection.read_bytes() if existing_selection else None

        def record_process(command, **unused):
            attempts.append(command)
            raise ProcessBoundaryReached()

        with patch.object(self.module.subprocess, "Popen", side_effect=record_process), \
                patch.object(experiment, "invoke", wraps=experiment.invoke) as invoke:
            try:
                experiment.continuation()
            except ProcessBoundaryReached:
                pass
            except (ValueError, RuntimeError) as error:
                failure = error
        self.assertEqual([], [call for call in invoke.call_args_list if call.args[1] == "train"],
                         "a closed capacity gate must also prevent cached training invocations")
        self.assertEqual([], attempts, "a closed capacity gate must prevent the JVM training launch")
        self.assertIsNotNone(failure, "a closed capacity gate needs an explicit failure, not silent success")
        self.assertRegex(str(failure), r"^CONTINUATION_GATE_REJECTED\b")
        if existing_selection:
            self.assertEqual(before, selection.read_bytes(), "a failed gate must not rewrite an existing selection")
        else:
            self.assertFalse(selection.exists(), "a failed gate must not freeze a selection")

    def assert_training_transition(self):
        attempts = []

        def record_process(command, **unused):
            attempts.append(command)
            raise ProcessBoundaryReached()

        with patch.object(self.module.subprocess, "Popen", side_effect=record_process):
            with self.assertRaises(ProcessBoundaryReached):
                self.experiment.continuation()
        self.assertEqual(1, len(attempts))
        self.assertEqual("train", attempts[0][6])
        self.assertFalse((self.experiment.out / "frozen-selection.json").exists())

    def test_baseline_gate_blocks_training_and_selection(self):
        self.continuation_fixture(last_passed=False, stop="BASELINE_GATE")
        self.assert_training_blocked()

    def test_failed_baseline_gate_remains_closed_after_resume(self):
        self.continuation_fixture(last_passed=False, stop="BASELINE_GATE")
        self.assert_training_blocked(self.module.Experiment(self.args))

    def test_missing_last_passed_cannot_be_bypassed_by_absent_stop_reason(self):
        self.continuation_fixture(last_passed=False)
        self.assert_training_blocked()

    def test_absent_last_passed_field_reports_gate_failure(self):
        self.continuation_fixture()
        path = self.experiment.out / "ladder.json"
        ladder = json.loads(path.read_text())
        del ladder["lastPassed"]
        path.write_text(json.dumps(ladder))
        self.assert_training_blocked()

    def test_ineligible_last_passed_cannot_open_training_gate(self):
        self.continuation_fixture()
        path = self.experiment.out / "ladder.json"
        ladder = json.loads(path.read_text())
        ladder["lastPassed"]["capacityEligible"] = False
        path.write_text(json.dumps(ladder))
        self.assert_training_blocked()

    def test_baseline_gate_rejects_contradictory_last_passed(self):
        self.continuation_fixture(last_passed=True, stop="BASELINE_GATE")
        self.assert_training_blocked()

    def test_existing_selection_cannot_bypass_failed_gate_on_resume(self):
        self.continuation_fixture(last_passed=False, stop="BASELINE_GATE")
        self.module.write_new(self.experiment.out / "frozen-selection.json", {"selectionStatus": "SELECTED"})
        self.assert_training_blocked(self.module.Experiment(self.args), existing_selection=True)

    def test_all_phase_propagates_gate_failure_without_running_final(self):
        self.continuation_fixture(last_passed=False, stop="BASELINE_GATE")
        arguments = [self.module.__file__, "all", "--inputs", str(self.args.inputs),
                     "--output", str(self.args.output), "--jar", str(self.args.jar),
                     "--holdout", str(self.args.holdout)]
        with patch("sys.argv", arguments), \
                patch.object(self.module.Experiment, "final", autospec=True) as final, \
                patch.object(self.module.subprocess, "Popen", side_effect=AssertionError("unexpected JVM launch")) as process:
            with self.assertRaisesRegex(RuntimeError, r"^CONTINUATION_GATE_REJECTED\b"):
                self.module.main()
            final.assert_not_called()
            process.assert_not_called()
        self.assertFalse((self.experiment.out / "frozen-selection.json").exists())

    def test_valid_last_passed_allows_training_transition(self):
        self.continuation_fixture()
        self.assert_training_transition()

    def test_later_stage_failure_preserves_valid_training_transition(self):
        self.continuation_fixture(stop="PARITY_OR_CAPACITY_FAILURE")
        self.assert_training_transition()

    def test_capacity_limit_preserves_last_passed_training_transition(self):
        self.continuation_fixture(stop="CHECKPOINT_64_MIB_CONTRACT")
        self.assert_training_transition()

    def test_both_training_paths_freeze_selection_then_resume_without_training(self):
        experiment = self.experiment
        baseline = self.continuation_fixture()
        self.complete_candidate_receipts(baseline, 8.0, evaluation=False)
        expanded = self.synthetic_candidate("width-8", 6_562_304)
        self.complete_candidate_receipts(expanded, 8.0)
        (experiment.out / "ladder.json").write_text(json.dumps({
            "stages": [baseline, expanded], "lastPassed": expanded, "stop": "CHECKPOINT_64_MIB_CONTRACT"}))
        self.successful_training_receipt(baseline, "continued-small", 7.8)
        trained_expanded = self.successful_training_receipt(expanded, "continued-expanded", 7.7)
        with patch.object(self.module.subprocess, "Popen", side_effect=AssertionError("unexpected JVM launch")), \
                patch.object(experiment, "invoke", wraps=experiment.invoke) as invoke:
            experiment.continuation()
        self.assertEqual(["continued-small-train", "continued-expanded-train"],
                         [call.args[0] for call in invoke.call_args_list if call.args[1] == "train"])
        frozen = experiment.out / "frozen-selection.json"
        selection = json.loads(frozen.read_text())
        self.assertEqual("SELECTED", selection["selectionStatus"])
        self.assertIs(True, selection["matchedTrainingInputs"])
        self.assertEqual(trained_expanded["sha256"], selection["developmentPreferred"]["sha256"])
        self.assertIs(False, selection["holdoutOpened"])
        before = frozen.read_bytes()
        resumed = self.module.Experiment(self.args)
        with patch.object(self.module.subprocess, "Popen", side_effect=AssertionError("unexpected JVM launch")), \
                patch.object(resumed, "invoke", wraps=resumed.invoke) as invoke:
            resumed.continuation()
        self.assertEqual([], [call for call in invoke.call_args_list if call.args[1] == "train"])
        self.assertEqual(before, frozen.read_bytes())


if __name__ == "__main__":
    unittest.main()
