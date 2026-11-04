"""Regression for the actual V1 registration error; no model process is started."""
import importlib.util
import json
from pathlib import Path
import struct
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("resource_measurement", ROOT / "run_resource_measurement.py")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ResourceSequenceChecks(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.tokenizer = Path(self.directory.name) / "tokenizer.lmbp"

    def write_tokenizer(self, merges):
        self.tokenizer.write_bytes(struct.pack(">ii", 0x4C4D4250, merges) + struct.pack(">ii", 97, 98) * merges)

    def test_byte_tokenizer_uses_256_257(self):
        self.write_tokenizer(0)
        expected = [256, 112, 114, 111, 98, 101, 33, 257]
        self.assertEqual(MODULE.validate_registered_sequence({"sequence": expected, "targets": 7}, self.tokenizer), expected)

    def test_512_vocabulary_uses_510_511(self):
        self.write_tokenizer(254)
        expected = [510, 112, 114, 111, 98, 101, 33, 511]
        self.assertEqual(MODULE.validate_registered_sequence({"sequence": expected, "targets": 7}, self.tokenizer), expected)

    def test_actual_archived_v1_registration_is_rejected(self):
        self.write_tokenizer(254)
        registration = json.loads((ROOT / "resource-registration.json").read_text())
        self.assertEqual(registration["sequence"], [256, 112, 114, 111, 98, 101, 33, 257])
        with self.assertRaisesRegex(ValueError, "disagrees with tokenizer"):
            MODULE.validate_registered_sequence(registration, self.tokenizer)

    def test_wrong_payload_or_target_count_is_rejected(self):
        self.write_tokenizer(254)
        correct = [510, 112, 114, 111, 98, 101, 33, 511]
        for record in [{"sequence": [510, 111, 114, 111, 98, 101, 33, 511], "targets": 7},
                       {"sequence": correct, "targets": 6},
                       {"sequence": [float(value) for value in correct], "targets": 7}]:
            with self.subTest(record=record), self.assertRaises(ValueError):
                MODULE.validate_registered_sequence(record, self.tokenizer)

    def test_invalid_tokenizer_is_rejected(self):
        registration = {"sequence": [256, 112, 114, 111, 98, 101, 33, 257], "targets": 7}
        for raw in [b"bad", struct.pack(">ii", 0, 0), struct.pack(">ii", 0x4C4D4250, 1),
                    struct.pack(">iiii", 0x4C4D4250, 1, 999, 1)]:
            self.tokenizer.write_bytes(raw)
            with self.subTest(raw=raw), self.assertRaises(ValueError):
                MODULE.validate_registered_sequence(registration, self.tokenizer)


if __name__ == "__main__":
    unittest.main()
