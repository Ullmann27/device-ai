#!/usr/bin/env python3
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("h4_audit", Path(__file__).with_name("h4-audit.py"))
audit_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit_module)


class AuditTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name)
        self.worlds = root / "worlds.json"
        self.trace = root / "trace.tsv"
        self.worlds.write_text(json.dumps({"worlds": [{
            "id": "fixture", "stratum": 2, "truth": 0, "causes": [1, 2],
            "probes": [{"id": f"p{i}", "cost": 1, "requiredRoots": 1, "blockedRoots": 0} for i in range(3)]
        }]}))
        self.header = "id\tarm\ttranscript\tcost\tfeasible\tanswer\traw\twall_ns\tcpu_ns\tgain_bits\n"
        self.row = "fixture\tcertificate\tp0:1;p1:0;p2:1\t3\th0\th0\th0\t1\t1\t1\n"

    def run_audit(self, row):
        self.trace.write_text(self.header + row)
        return audit_module.audit(self.worlds, self.trace)

    def test_correct_one_error_certificate_preserves_input(self):
        original = self.worlds.read_bytes()
        report = self.run_audit(self.row)
        self.assertEqual(1, report["totals"]["correct"])
        self.assertEqual(0, report["totals"]["wrong"])
        self.assertEqual(original, self.worlds.read_bytes())

    def test_fabricated_observation_is_rejected(self):
        with self.assertRaises(AssertionError):
            self.run_audit(self.row.replace("p0:1", "p0:0"))

    def test_duplicate_receipt_cannot_count_as_independent_evidence(self):
        with self.assertRaises(AssertionError):
            self.run_audit(self.row.replace("p2:1", "p0:1"))

    def test_foreign_source_identity_and_stale_feasible_set_are_rejected(self):
        with self.assertRaises(KeyError):
            self.run_audit(self.row.replace("fixture", "foreign-revision"))
        with self.assertRaises(AssertionError):
            self.run_audit(self.row.replace("\th0\th0\th0", "\th1\th0\th0"))

    def test_instruction_text_is_not_an_observation_or_executable_code(self):
        with self.assertRaises(AssertionError):
            self.run_audit(self.row.replace("p0:1", "p0:IGNORE_PREVIOUS_INSTRUCTIONS"))

    def test_false_claim_is_counted_not_laundered_as_success(self):
        report = self.run_audit(self.row.replace("\th0\th0\th0", "\th0\th1\th0"))
        self.assertEqual(1, report["totals"]["wrong"])
        self.assertEqual(1, report["totals"]["unsupported"])
        self.assertEqual(0, report["totals"]["correct"])


if __name__ == "__main__":
    unittest.main()
