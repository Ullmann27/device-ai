import copy
import unittest
import tempfile
from pathlib import Path

from analyze_contrast_pairs import parse_cases, summarize, validate_pairs, validate_input_hashes


class PairAuditTests(unittest.TestCase):
    def setUp(self):
        self.rows = [{"id": "a", "label": "0", "prompt": "Nicht x"},
                     {"id": "b", "label": "3", "prompt": "Bitte x"}]
        self.metadata = [{"id": row["id"], "label": row["label"], "pairId": "pair",
                          "familyId": "f", "objectId": "o", "lengthLevel": "short",
                          "directivePosition": "first", "promptUtf8Bytes": 7}
                         for row in self.rows]
        self.labels = {"0": [48], "3": [51]}

    def log(self):
        return "\n".join(
            f"CASE\tsplit=dev\tstep={step}\tid={row['id']}\texpected={row['label']}"
            f"\tpredictedRestricted=0\ttokens=48\tended=true\texact={str(row['label']=='0').lower()}"
            for step in (0, 128) for row in self.rows)

    def test_class_collapse_is_not_pair_success(self):
        rows, metadata, pairs = validate_pairs(self.rows, self.metadata)
        cases = parse_cases(self.log(), rows, self.labels)
        report = summarize(cases[128], metadata, pairs)
        self.assertEqual(report["exact"], 1)
        self.assertEqual(report["pairs"]["bothExact"], 0)
        self.assertEqual(report["pairs"]["sameRestrictedPrediction"], 1)

    def test_reject_duplicate_case(self):
        rows, _, _ = validate_pairs(self.rows, self.metadata)
        with self.assertRaisesRegex(ValueError, "duplicate or unknown"):
            parse_cases(self.log()+"\n"+self.log().splitlines()[0], rows, self.labels)

    def test_reject_missing_case(self):
        rows, _, _ = validate_pairs(self.rows, self.metadata)
        with self.assertRaisesRegex(ValueError, "incomplete"):
            parse_cases("\n".join(self.log().splitlines()[:-1]), rows, self.labels)

    def test_reject_false_success_flag(self):
        rows, _, _ = validate_pairs(self.rows, self.metadata)
        with self.assertRaisesRegex(ValueError, "exact flag"):
            parse_cases(self.log().replace("exact=false", "exact=true"), rows, self.labels)

    def test_reject_success_without_eos(self):
        rows, _, _ = validate_pairs(self.rows, self.metadata)
        with self.assertRaisesRegex(ValueError, "exact flag"):
            parse_cases(self.log().replace("ended=true", "ended=false"), rows, self.labels)

    def test_reject_wrong_pair_label(self):
        metadata = copy.deepcopy(self.metadata)
        metadata[1]["label"] = "0"
        with self.assertRaisesRegex(ValueError, "label mismatch"):
            validate_pairs(self.rows, metadata)

    def test_reject_duplicate_metadata(self):
        with self.assertRaisesRegex(ValueError, "duplicate"):
            validate_pairs(self.rows, self.metadata + [self.metadata[0]])

    def test_reject_pair_byte_difference(self):
        rows, metadata = copy.deepcopy(self.rows), copy.deepcopy(self.metadata)
        rows[1]["prompt"] += "!"
        metadata[1]["promptUtf8Bytes"] += 1
        with self.assertRaisesRegex(ValueError, "byte lengths"):
            validate_pairs(rows, metadata)

    def test_reject_raw_input_hash_mismatch(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "dev.tsv"
            path.write_text("changed prompt")
            with self.assertRaisesRegex(ValueError, "INPUT hash mismatch"):
                validate_input_hashes("INPUT\tfile=dev.tsv\tsha256=wrong", [path])


if __name__ == "__main__":
    unittest.main()
