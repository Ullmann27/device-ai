#!/usr/bin/env python3
"""Focused negative checks for independent scoring."""

import copy
import unittest

from analyze import effect, metrics, parse, validate_native, wilson


ROWS = [{"id": "a", "label": "0", "prompt": "synthetic-a"},
        {"id": "b", "label": "1", "prompt": "synthetic-b"},
        {"id": "c", "label": "2", "prompt": "synthetic-c"},
        {"id": "d", "label": "3", "prompt": "synthetic-d"}]
META = [{"id": r["id"], "pairId": str(i // 2), "familyId": str(i // 2)}
        for i, r in enumerate(ROWS)]


def native():
    records = {"CONFIG": [{"mode": "evaluate", "parameters": "132160", "updates": "0", "checkpointSha256": "a"}],
               "RESULT": [{"updates": "0", "inputUnchanged": "true", "elapsedMillis": "1"}],
               "LABEL": [{"label": str(i), "token": str(48 + i)} for i in range(4)],
               "CASE": [], "TEXT": [], "CAPACITY": []}
    for row in ROWS:
        label = row["label"]
        records["CASE"].append({"split": "semantic", "id": row["id"], "expected": label,
            "predictedRestricted": label, "tokens": str(48 + int(label)), "ended": "true",
            "exact": "true", "firstEos": "0.01", "decodedBase64": "MA==",
            **{"p" + str(i): "0.5" if str(i) == label else "0.01" for i in range(4)}})
    return records


class ScoringChecks(unittest.TestCase):
    def test_row_gain_without_pairs(self):
        report = metrics(ROWS, ["0", "0", "2", "2"], META)
        self.assertEqual(report["correct"], 2)
        self.assertEqual(report["pairCorrect"], 0)
        self.assertEqual(report["samePredictionPairs"], 2)

    def test_complete_pairs(self):
        report = metrics(ROWS, ["0", "1", "2", "3"], META)
        self.assertEqual(report["pairCorrect"], 2)
        self.assertEqual(report["confusion"], [[1,0,0,0],[0,1,0,0],[0,0,1,0],[0,0,0,1]])

    def test_no_effect_is_not_success(self):
        report = metrics(ROWS, ["0", "1", "2", "3"], META)
        result = effect(report, report)
        self.assertEqual(result["accuracy"]["pairedBootstrap97_5"], [0.0, 0.0])
        self.assertFalse(result["accuracy"]["pairedLowerAboveZero"])

    def test_pair_bootstrap_preserves_effect(self):
        a = metrics(ROWS, ["0", "1", "2", "3"], META)
        b = metrics(ROWS, ["1", "0", "3", "2"], META)
        self.assertEqual(effect(a, b)["pairAccuracy"]["pairedBootstrap97_5"], [1.0, 1.0])

    def test_wilson_boundaries(self):
        self.assertAlmostEqual(wilson(0, 64)[0], 0)
        self.assertAlmostEqual(wilson(64, 64)[1], 1)
        self.assertLess(wilson(32, 64)[0], 0.5)

    def test_forged_exact_rejected(self):
        data = native()
        data["CASE"][0]["tokens"] = "49"
        with self.assertRaisesRegex(ValueError, "forged exact"):
            validate_native(data, {"semantic": ROWS}, {}, META, "a")

    def test_duplicate_case_rejected(self):
        data = native()
        data["CASE"].append(copy.deepcopy(data["CASE"][0]))
        with self.assertRaisesRegex(ValueError, "duplicate"):
            validate_native(data, {"semantic": ROWS}, {}, META, "a")

    def test_missing_case_rejected(self):
        data = native()
        data["CASE"].pop()
        with self.assertRaisesRegex(ValueError, "incomplete cases"):
            validate_native(data, {"semantic": ROWS}, {}, META, "a")

    def test_nonfinite_rejected(self):
        data = native()
        data["CASE"][0]["p0"] = "NaN"
        with self.assertRaisesRegex(ValueError, "nonfinite"):
            validate_native(data, {"semantic": ROWS}, {}, META, "a")

    def test_unknown_fields_rejected(self):
        with self.assertRaisesRegex(ValueError, "unexpected fields"):
            parse("LABEL\tlabel=0\ttoken=48\thidden=true")

    def test_duplicate_fields_rejected(self):
        with self.assertRaisesRegex(ValueError, "duplicate fields"):
            parse("LABEL\tlabel=0\ttoken=48\ttoken=49")

    def test_valid_trace(self):
        report = validate_native(native(), {"semantic": ROWS}, {}, META, "a")
        self.assertEqual(report["semantic"]["exactPairs"], 2)


if __name__ == "__main__":
    unittest.main(verbosity=2)
