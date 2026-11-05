"""Isolated no-model synthetic regressions for pretest_exclusivity.py."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from pretest_exclusivity import ReuseBlocked, verify_unused


class ExclusivityTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.outputs = {}
        for v in ("v1", "v2", "v3"):
            output = self.root / v
            (output / "logs").mkdir(parents=True)
            self.outputs[v] = str(output)
        self.plan = self.root / "plan.json"
        self.write_plan()

    def write_plan(self, selected="v3", outputs=None):
        self.plan.write_text(json.dumps({"version": 1, "selectedStudy": selected,
                                         "studyOutputs": self.outputs if outputs is None else outputs}))
        self.pin = hashlib.sha256(self.plan.read_bytes()).hexdigest()

    def check_block(self, pin=None):
        with self.assertRaisesRegex(ReuseBlocked, "CROSS_STUDY_FINAL_REUSE_BLOCKED"):
            verify_unused(self.plan, self.pin if pin is None else pin, "v3")

    def test_clean_three_root_inventory_passes_locally(self):
        self.assertEqual(verify_unused(self.plan, self.pin, "v3")["checkedRoots"], 3)

    def test_other_study_final_log_blocks(self):
        (self.root / "v2/logs/test-W1.tsv").write_text("synthetic")
        self.check_block()

    def test_other_study_receipt_blocks(self):
        (self.root / "v1/logs/test-S0.receipt.json").write_text("{}")
        self.check_block()

    def test_selected_study_partial_result_blocks(self):
        (self.root / "v3/logs/test-S0.tsv").write_text("partial")
        self.check_block()

    def test_orphan_summary_blocks(self):
        (self.root / "v2/intent-summary.json").write_text("{}")
        self.check_block()

    def test_consumption_marker_blocks(self):
        (self.root / "v1/final-test-started.json").write_text("{}")
        self.check_block()

    def test_mutated_plan_hash_blocks(self):
        self.plan.write_text(self.plan.read_text() + " ")
        self.check_block()

    def test_missing_competitor_root_blocks(self):
        (self.root / "v2/logs").rmdir()
        self.check_block()

    def test_aliasing_competitor_root_blocks(self):
        altered = dict(self.outputs); altered["v2"] = altered["v1"]
        self.write_plan(outputs=altered)
        self.check_block()

    def test_wrong_selected_study_blocks(self):
        with self.assertRaises(ReuseBlocked):
            verify_unused(self.plan, self.pin, "v2")

    def test_duplicate_json_field_blocks(self):
        self.plan.write_text('{"version":1,"version":1,"selectedStudy":"v3","studyOutputs":{}}')
        self.pin = hashlib.sha256(self.plan.read_bytes()).hexdigest()
        self.check_block()

    def test_output_symlink_blocks(self):
        link = self.root / "alias-v2"
        link.symlink_to(self.root / "v2", target_is_directory=True)
        altered = dict(self.outputs); altered["v2"] = str(link)
        self.write_plan(outputs=altered)
        self.check_block()


if __name__ == "__main__":
    unittest.main()
