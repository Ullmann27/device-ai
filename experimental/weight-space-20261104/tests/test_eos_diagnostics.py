"""Corruption controls plus an optional pinned historical regression test."""

import importlib.util
import os
from pathlib import Path
import unittest


SPEC = importlib.util.spec_from_file_location(
    "eos_diagnostics", Path(__file__).resolve().parents[1] / "eos_diagnostics.py")
audit = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(audit)


def record(kind, **fields):
    return kind + "\t" + "\t".join(f"{key}={value}" for key, value in fields.items()) + "\n"


def probes(checkpoint="a" * 64):
    text = record("SOURCE", sha256=checkpoint, tokenizerSha256="b" * 64, parameters=100)
    for i in range(6):
        text += record("PROBE", index=i, prompt=f"unique task {i}", expected="yes", text="",
                       correct="false", stop="EOS", tokens=0)
    return text + record("PROBE_RESULT", correct=0, total=6, scoring="exact-trimmed-string")


def training():
    text = record("SOURCE", sha256="a" * 64, tokenizerSha256="b" * 64, parameters=100)
    text += record("TRAIN_INPUT", datasetSha256="d" * 64, scheduleSha256="e" * 64,
                   tokenScheduleSha256="f" * 64, tokenizerSha256="b" * 64)
    for i in range(1, 17):
        text += record("TRAIN_STEP", step=i, nll=2.0, tokens=i * 10)
    text += record("CHECKPOINT", sha256="c" * 64, parameters=100)
    return text + record("TRAIN_RESULT", steps=16, tokens=160, changedWeights=99,
                         scheduleSha256="e" * 64)


def capacity():
    text = record("CAPACITY_PROTOCOL", warmups=1, measuredRepeats=3, promptSha256="d" * 64)
    text += record("CAPACITY_MODEL", checkpointSha256="c" * 64,
                   tokenizerSha256="b" * 64, parameters=100, promptTokens=64,
                   finiteWeights="true", fullLayoutLoaded="true")
    for phase, count in (("warmup", 1), ("measured", 3)):
        for trial in range(1, count + 1):
            for requested in (1, 32):
                text += record("CAPACITY_TRIAL", phase=phase, trial=trial,
                               requestedNewTokens=requested, actualNewTokens=0,
                               promptTokens=64, promptTruncated="false", stop="EOS",
                               ttftUpperBoundMs="UNAVAILABLE", textSha256=audit.EMPTY_SHA256)
    return text + record("CAPACITY_RESULT", **{"pass": "false"},
                         checkpointSha256="c" * 64, parameters=100, measuredRepeats=3,
                         firstTokenObservations=0, full32TokenObservations=0)


class EosDiagnosticsTests(unittest.TestCase):
    def test_valid_chain_counts_calls_separately_from_prompts(self):
        result = audit.compare_run(audit.inspect_training(training()),
                                   audit.inspect_probes(probes()),
                                   audit.inspect_probes(probes("c" * 64)),
                                   audit.inspect_capacity(capacity()))
        self.assertEqual(result["capacity"]["trial_calls"], 8)
        self.assertEqual(result["capacity"]["distinct_protocol_prompts"], 1)
        self.assertEqual(result["capacity"]["immediate_eos_calls"], 8)
        self.assertEqual(result["continued_immediate_eos_indices"], list(range(6)))

    def test_missing_or_duplicate_capacity_trial_is_rejected(self):
        lines = capacity().splitlines(keepends=True)
        trial_index = next(i for i, line in enumerate(lines) if line.startswith("CAPACITY_TRIAL"))
        for damaged in (lines[:trial_index] + lines[trial_index + 1:],
                        lines[:trial_index] + [lines[trial_index]] + lines[trial_index:]):
            with self.subTest(lines=len(damaged)), self.assertRaises(audit.AuditError):
                audit.inspect_capacity("".join(damaged))

    def test_zero_output_and_result_claims_are_verified(self):
        mutations = [
            capacity().replace(audit.EMPTY_SHA256, "a" * 64, 1),
            capacity().replace("stop=EOS", "stop=UNKNOWN", 1),
            capacity().replace("ttftUpperBoundMs=UNAVAILABLE", "ttftUpperBoundMs=0.1", 1),
            capacity().replace("pass=false", "pass=true"),
            capacity().replace("firstTokenObservations=0", "firstTokenObservations=1"),
            capacity().replace("CAPACITY_RESULT\tpass=false\tcheckpointSha256=" + "c" * 64,
                               "CAPACITY_RESULT\tpass=false\tcheckpointSha256=" + "a" * 64),
        ]
        for index, text in enumerate(mutations):
            with self.subTest(mutation=index), self.assertRaises(audit.AuditError):
                audit.inspect_capacity(text)

    def test_empty_time_stop_is_not_counted_as_eos(self):
        result = audit.inspect_capacity(capacity().replace("stop=EOS", "stop=TIME", 1))
        self.assertEqual(result["immediate_eos_calls"], 7)

    def test_probe_completeness_scoring_and_text_are_verified(self):
        mutations = [probes().replace("index=5", "index=4"),
                     probes().replace("correct=false", "correct=true", 1),
                     probes().replace("text=\t", "text=x\t", 1),
                     probes().replace("total=6", "total=5")]
        for index, text in enumerate(mutations):
            with self.subTest(mutation=index), self.assertRaises(audit.AuditError):
                audit.inspect_probes(text)

    def test_training_completeness_and_numerical_validity_are_verified(self):
        mutations = [training().replace("step=16", "step=15"),
                     training().replace("nll=2.0", "nll=NaN", 1),
                     training().replace("tokens=20", "tokens=10", 1),
                     training().replace("steps=16\ttokens=160", "steps=16\ttokens=159"),
                     training().replace("TRAIN_RESULT\tsteps=16\ttokens=160\tchangedWeights=99\tscheduleSha256=" + "e" * 64,
                                        "TRAIN_RESULT\tsteps=16\ttokens=160\tchangedWeights=99\tscheduleSha256=" + "d" * 64)]
        for index, text in enumerate(mutations):
            with self.subTest(mutation=index), self.assertRaises(audit.AuditError):
                audit.inspect_training(text)

    def test_source_checkpoint_and_tokenizer_must_match_across_logs(self):
        for source, after, cap in [
            (probes("d" * 64), probes("c" * 64), capacity()),
            (probes(), probes("d" * 64), capacity()),
            (probes(), probes("c" * 64), capacity().replace("b" * 64, "d" * 64)),
        ]:
            with self.subTest(source=source[:90], after=after[:90]), self.assertRaises(audit.AuditError):
                audit.compare_run(audit.inspect_training(training()), audit.inspect_probes(source),
                                  audit.inspect_probes(after), audit.inspect_capacity(cap))

    def test_duplicate_field_is_not_silently_overwritten(self):
        with self.assertRaises(audit.AuditError):
            audit.inspect_probes(probes().replace("tokens=0", "tokens=1\ttokens=0", 1))

    @unittest.skipUnless(os.environ.get("EOS_AUDIT_REPO"), "set EOS_AUDIT_REPO for pinned raw-log regression")
    def test_pinned_historical_evidence(self):
        result = audit.audit(Path(os.environ["EOS_AUDIT_REPO"]))
        self.assertEqual(result["revision"], audit.REVISION)
        self.assertEqual(len(result["sources"]), 16)
        self.assertEqual(len(result["runs"]), 4)
        self.assertTrue(result["all_four_continuations_have_six_immediate_eos_probes"])
        self.assertTrue(result["all_four_continuations_have_eight_immediate_eos_capacity_calls"])
        self.assertTrue(result["small_control_checkpoint_identical_across_profiles"])
        self.assertEqual(result["distinct_continued_checkpoint_count"], 3)
        for run in result["runs"]:
            self.assertEqual(run["initial_immediate_eos_indices"], [0, 1, 4, 5])
            self.assertEqual(run["new_immediate_eos_indices"], [2, 3])
            self.assertEqual((run["steps"], run["tokens"]), (16, 3255))
            self.assertEqual((run["initial_correct"], run["continued_correct"]), (0, 0))


if __name__ == "__main__":
    unittest.main()
