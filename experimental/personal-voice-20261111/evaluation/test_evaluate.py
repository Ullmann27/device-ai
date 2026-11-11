#!/usr/bin/env python3
import base64
import copy
import unittest

from evaluate import BASE, TOKENIZER, compare, parse_run, summarize


class EvidenceValidationTests(unittest.TestCase):
    def valid(self):
        row = {"id": "probe", "label": "0", "prompt": "synthetic"}
        lines = [f"MODEL\tsha256={BASE}\ttokenizerSha256={TOKENIZER}\tparameters=132160\tmode=eval",
                 "CASE\tid=probe\texpected=0\tpredictedRestricted=0\ttokens=48\tdecodedBase64=MA==\tended=true\texact=true\tfirstEos=0.1\tp0=0.5\tp1=0.1\tp2=0.1\tp3=0.1\tprefixTokens=12\tfirstPredictionMillis=1.0\tcaseMillis=2.0",
                 "SCORE\texact=1\ttotal=1\trestrictedCorrect=1\tprematureEos=0",
                 "RETENTION\tbpb=2.8\tbytes=20",
                 *[f"CAPACITY\tid={i}\ttokens=3\teos=true\tdecodedBase64=YWJj" for i in range(4)],
                 "RESULT\tupdates=0\tinputUnchanged=true\telapsedMillis=20"]
        return row, "\n".join(lines)

    def test_valid_output_is_recomputed(self):
        row, text = self.valid()
        parsed = parse_run(text, [row], BASE)
        self.assertTrue(parsed["cases"]["probe"]["exact"])

    def test_duplicate_results_do_not_inflate_denominator(self):
        row, text = self.valid()
        case = next(x for x in text.splitlines() if x.startswith("CASE"))
        with self.assertRaisesRegex(ValueError, "duplicate/unexpected"):
            parse_run(text + "\n" + case, [row], BASE)

    def test_missing_results_are_not_silently_skipped(self):
        row, text = self.valid()
        text = "\n".join(x for x in text.splitlines() if not x.startswith("CASE"))
        with self.assertRaisesRegex(ValueError, "incomplete"):
            parse_run(text, [row], BASE)

    def test_reported_exact_does_not_override_actual_output(self):
        row, text = self.valid()
        with self.assertRaisesRegex(ValueError, "exact score"):
            parse_run(text.replace("decodedBase64=MA==", "decodedBase64=MQ=="), [row], BASE)

    def test_missing_eos_is_not_success(self):
        row, text = self.valid()
        with self.assertRaisesRegex(ValueError, "exact score"):
            parse_run(text.replace("ended=true", "ended=false"), [row], BASE)

    def test_restricted_prediction_must_match_probabilities(self):
        row, text = self.valid()
        with self.assertRaisesRegex(ValueError, "argmax"):
            parse_run(text.replace("predictedRestricted=0", "predictedRestricted=1"), [row], BASE)

    def test_changed_gold_or_weights_rejects_receipt(self):
        row, text = self.valid()
        with self.assertRaisesRegex(ValueError, "gold label"):
            parse_run(text.replace("expected=0", "expected=1"), [row], BASE)
        with self.assertRaisesRegex(ValueError, "mismatch"):
            parse_run(text, [row], "0" * 64)

    def test_nonfinite_probabilities_are_rejected(self):
        row, text = self.valid()
        with self.assertRaisesRegex(ValueError, "non-finite"):
            parse_run(text.replace("p0=0.5", "p0=NaN"), [row], BASE)

    def test_training_cannot_be_reported_as_readonly_evaluation(self):
        row, text = self.valid()
        with self.assertRaisesRegex(ValueError, "mutated inputs or trained"):
            parse_run(text.replace("updates=0", "updates=1"), [row], BASE)


class GateTests(unittest.TestCase):
    def fixtures(self):
        metadata = [{"id": f"x{i}", "pair_id": f"p{i // 2}", "label": i % 2,
                     "category": "test", "wordbag_identical_pair": True} for i in range(64)]
        cases = {r["id"]: {"expected": r["label"], "exact": True, "restricted": r["label"],
                          "free_label": r["label"], "ended": True, "tokens": [1],
                          "first_prediction_ms": 1.0, "case_ms": 2.0} for r in metadata}
        run = {"cases": cases, "capacity": {i: {"tokens": 32, "eos": False} for i in range(4)}, "bpb": 2.8}
        return metadata, run

    def test_constant_wordbag_prediction_cannot_pass_pairs(self):
        metadata, run = self.fixtures()
        for c in run["cases"].values():
            c["exact"] = c["expected"] == 0
            c["free_label"] = c["restricted"] = 0
        result = summarize(run, metadata, [])
        self.assertEqual(result["test_exact"]["correct"], 32)
        self.assertEqual(result["wordbag_pairs_exact"]["correct"], 0)

    def test_new_forbidden_prediction_is_detected_even_with_lower_total(self):
        metadata, original = self.fixtures()
        original["cases"]["x0"]["free_label"] = 1
        original["cases"]["x2"]["free_label"] = 1
        candidate = copy.deepcopy(original)
        candidate["cases"]["x0"]["free_label"] = 0
        candidate["cases"]["x2"]["free_label"] = 0
        candidate["cases"]["x4"]["free_label"] = 1
        result = compare(summarize(original, metadata, []), summarize(candidate, metadata, []), original, candidate, metadata)
        self.assertFalse(result["gates"]["no_new_forbidden_free"])
        self.assertEqual(result["new_forbidden_free"], ["x4"])

    def test_output_collapse_is_not_hidden_by_better_bpb(self):
        metadata, original = self.fixtures()
        candidate = copy.deepcopy(original)
        candidate["capacity"][0]["tokens"] = 0
        candidate["bpb"] = 1.5
        result = compare(summarize(original, metadata, []), summarize(candidate, metadata, []), original, candidate, metadata)
        self.assertTrue(result["gates"]["retention_bpb_within_5_percent"])
        self.assertFalse(result["gates"]["no_new_empty_continuation"])
        self.assertFalse(result["gates"]["continuation_total_at_least_90_percent"])


if __name__ == "__main__":
    unittest.main()
