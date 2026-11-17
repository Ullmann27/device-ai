import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import intelligence as lab


class IntelligenceLabTest(unittest.TestCase):
    def setUp(self):
        self.suite = lab.generate(36, "holdout", b"test-only-seed")
        self.responses = [{"id": c["id"], "answer": c["expected"]} for c in self.suite["cases"]]

    def test_reproducible_and_separate_splits(self):
        same = lab.generate(36, "holdout", b"test-only-seed")
        dev = lab.generate(36, "development", b"test-only-seed")
        self.assertEqual(self.suite["suite_id"], same["suite_id"])
        self.assertNotEqual(self.suite["suite_id"], dev["suite_id"])

    def test_six_domains(self):
        self.assertEqual({c["domain"] for c in self.suite["cases"]}, set(lab.DOMAINS))

    def test_prompts_exclude_expected(self):
        prompts = lab.export_prompts(self.suite)
        self.assertNotIn("expected", json.dumps(prompts))

    def test_perfect_synthetic_is_explicitly_synthetic(self):
        result = lab.grade(self.suite, self.responses, "synthetic_selftest", "fake", "fake")
        self.assertEqual(result["totals"]["pass"], 36)
        self.assertFalse(result["inference_execution_verified"])
        self.assertEqual(result["provenance"], "synthetic_selftest")

    def test_corruption_and_missing_are_distinct(self):
        broken = self.responses[:3]
        broken[0] = {"id": broken[0]["id"], "answer": {"invalid": "x"}}
        result = lab.grade(self.suite, broken, "synthetic_selftest", "fake", "fake")
        self.assertEqual(result["totals"]["fail"], 1)
        self.assertEqual(result["totals"]["pass"], 2)
        self.assertEqual(result["totals"]["not_run"], 33)

    def test_boolean_does_not_equal_one(self):
        case = next(c for c in self.suite["cases"] if c["domain"] == "memory")
        answers = [{"id": case["id"], "answer": {"aktuell": case["expected"]["aktuell"],
                                                       "alt_ungueltig": 1}}]
        result = lab.grade(self.suite, answers, "synthetic_selftest", "fake", "fake")
        self.assertEqual(result["totals"]["fail"], 1)

    def test_duplicate_ids_rejected(self):
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            lab.grade(self.suite, self.responses + self.responses[:1], "synthetic_selftest", "x", "y")

    def test_tampered_suite_rejected(self):
        self.suite["cases"][0]["expected"]["farbe"] = "other"
        with self.assertRaisesRegex(ValueError, "integrity"):
            lab.export_prompts(self.suite)

    def test_synthetic_not_comparable_to_model(self):
        score = lab.grade(self.suite, self.responses, "synthetic_selftest", "fake", "fake")
        self.assertEqual(lab.compare_scores(score, score)["status"], "NOT_COMPARABLE")

    def test_declared_model_not_execution_verified(self):
        a = lab.grade(self.suite, self.responses, "device_model_declared", "v1", "v1")
        b = lab.grade(self.suite, self.responses, "device_model_declared", "v2", "v2")
        self.assertEqual(lab.compare_scores(a, b)["status"], "DECLARED_MODEL_COMPARISON_UNVERIFIED")

    def test_no_overwrite(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "suite.json"
            lab.write_new(path, self.suite)
            with self.assertRaises(FileExistsError):
                lab.write_new(path, self.suite)


if __name__ == "__main__":
    unittest.main()