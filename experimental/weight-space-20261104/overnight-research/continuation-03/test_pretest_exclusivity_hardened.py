"""Source-pinned, no-model RED/ GREEN regressions for cross-study local preflight.

Loads the exact original continuation-02 guard and its twelve original tests
by Git blob identity. Original tests are reused unchanged against the hardened
candidate. No imported module invokes a registered training controller.
"""
from __future__ import annotations

import hashlib
import importlib.util
import json
import sys
from unittest.mock import patch
from pathlib import Path
import tempfile
import unittest

HERE = Path(__file__).resolve().parent
ORIGINAL = HERE.parent / "continuation-02" / "pretest_exclusivity.py"
ORIGINAL_TEST = HERE.parent / "continuation-02" / "test_pretest_exclusivity.py"
HARDENED = HERE / "pretest_exclusivity_hardened.py"
ORIGINAL_BLOB = "5b068596a0b2301f373fe89421189654db15c963"
ORIGINAL_TEST_BLOB = "aa85775476cbb70e9d3e5852db860575f76cc932"


def blob_sha1(path: Path) -> str:
    raw = path.read_bytes()
    return hashlib.sha1(b"blob " + str(len(raw)).encode("ascii") + b"\0" + raw).hexdigest()


def load_path(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    assert spec and spec.loader
    spec.loader.exec_module(module)
    return module


# Block execution if the original GitHub-published sources differ from their pins.
if blob_sha1(ORIGINAL) != ORIGINAL_BLOB or blob_sha1(ORIGINAL_TEST) != ORIGINAL_TEST_BLOB:
    raise RuntimeError("ORIGINAL_CONTINUATION_02_SOURCE_PIN_MISMATCH")
original = load_path("continuation02_original", ORIGINAL)
hardened = load_path("continuation03_hardened", HARDENED)


class SourcePinnedAdversarialTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.base = Path(self.temporary.name)
        self.outputs = {}
        for study in ("v1", "v2", "v3"):
            output = self.base / study
            (output / "logs").mkdir(parents=True)
            self.outputs[study] = str(output)
        self.plan_path = self.base / "inventory.json"
        self.write_plan()

    def write_plan(self):
        payload = {"version": 1, "selectedStudy": "v3", "studyOutputs": self.outputs}
        self.plan_path.write_text(json.dumps(payload), encoding="utf-8")
        self.pin = hashlib.sha256(self.plan_path.read_bytes()).hexdigest()

    def assert_red_green(self, path: Path | None = None):
        path = self.plan_path if path is None else path
        red = original.verify_unused(path, self.pin, "v3")
        self.assertEqual(red["status"], "LOCALLY_UNCONSUMED_AT_INSPECTION")
        with self.assertRaisesRegex(hardened.ReuseBlocked, "CROSS_STUDY_FINAL_REUSE_BLOCKED"):
            hardened.verify_unused(path, self.pin, "v3")

    def test_clean_control_is_never_an_authorization(self):
        for module in (original, hardened):
            result = module.verify_unused(self.plan_path, self.pin, "v3")
            self.assertEqual(result["status"], "LOCALLY_UNCONSUMED_AT_INSPECTION")

    def test_partial_test_tsv_red_green(self):
        (self.base / "v2/logs/test-W1.tsv.partial").write_text("synthetic")
        self.assert_red_green()

    def test_partial_test_receipt_red_green(self):
        (self.base / "v1/logs/test-S0.receipt.json.tmp").write_text("synthetic")
        self.assert_red_green()

    def test_unknown_test_artifact_extension_red_green(self):
        (self.base / "v3/logs/test-W0.error").write_text("synthetic")
        self.assert_red_green()

    def test_partial_summary_red_green(self):
        (self.base / "v1/intent-summary.json.tmp").write_text("synthetic")
        self.assert_red_green()

    def test_symlinked_output_ancestor_red_green(self):
        alias = self.base / "alias-parent"
        alias.symlink_to(self.base, target_is_directory=True)
        self.outputs["v2"] = str(alias / "v2")
        self.write_plan()
        self.assert_red_green()

    def test_symlinked_inventory_ancestor_red_green(self):
        alias = self.base / "alias-parent"
        alias.symlink_to(self.base, target_is_directory=True)
        self.assert_red_green(alias / "inventory.json")


def load_tests(loader, tests, pattern):
    """Additionally reuse all twelve *unaltered* continuation-02 tests against candidate."""
    with patch.dict(sys.modules, {"pretest_exclusivity": hardened}):
        older_tests = load_path("continuation02_original_tests", ORIGINAL_TEST)
    older_tests.verify_unused = hardened.verify_unused
    older_tests.ReuseBlocked = hardened.ReuseBlocked
    tests.addTests(loader.loadTestsFromModule(older_tests))
    return tests


if __name__ == "__main__":
    unittest.main()
