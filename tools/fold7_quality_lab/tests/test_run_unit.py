import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


LAB = Path(__file__).resolve().parents[1]


class UnitSourceBindingTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "repo"
        self.lab = self.root / "tools" / "fold7_quality_lab"
        self.tests = self.lab / "tests"
        self.tests.mkdir(parents=True)
        self.runner = self.lab / "run_unit.py"
        shutil.copyfile(LAB / "run_unit.py", self.runner)
        self.sample = self.tests / "test_sample.py"
        self.sample.write_text("import unittest\nclass Sample(unittest.TestCase):\n"
                               "    def test_observed(self):\n        self.assertTrue(True)\n")
        self.output = Path(self.temp.name) / "result.json"
        self.git("init", "-q")
        self.commit()

    def git(self, *args):
        return subprocess.check_output(
            ["git", "-C", str(self.root), "-c", "user.name=Lab fixture",
             "-c", "user.email=lab-fixture@example.invalid", *args], text=True).strip()

    def commit(self):
        self.git("add", ".")
        self.git("commit", "-qm", "Synthetic source fixture")
        self.sha = self.git("rev-parse", "HEAD")

    def run_lab(self, *args):
        return subprocess.run([sys.executable, str(self.runner), "--output", str(self.output), *args],
                              capture_output=True, text=True, timeout=20,
                              env={**os.environ, "PYTHONDONTWRITEBYTECODE": "1"})

    def evidence(self):
        return json.loads(self.output.read_text())

    def test_clean_checkout_records_observed_head(self):
        result = self.run_lab()
        self.assertEqual(result.returncode, 0, result.stderr)
        evidence = self.evidence()
        self.assertEqual(evidence["source_sha"], self.sha)
        self.assertIs(evidence["source_verified"], True)
        self.assertIs(evidence["source_unchanged"], True)
        self.assertEqual(evidence["status"], "PASS")

    def test_cli_source_is_expectation_not_replacement(self):
        result = self.run_lab("--source-sha", "b" * 40)
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(self.output.exists())

    def test_dirty_tracked_source_cannot_attest_pass(self):
        self.sample.write_text(self.sample.read_text() + "\n# Local change\n")
        result = self.run_lab("--source-sha", self.sha)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.evidence()["status"], "NOT_VERIFIED")
        self.assertIs(self.evidence()["source_verified"], False)
        self.assertEqual(self.evidence()["passed"], 1)

    def test_untracked_test_cannot_attest_pass(self):
        (self.tests / "test_extra.py").write_text(self.sample.read_text())
        result = self.run_lab("--source-sha", self.sha)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.evidence()["status"], "NOT_VERIFIED")
        self.assertEqual(self.evidence()["total"], 2)

    def test_ignored_python_source_cannot_hide_from_binding(self):
        (self.root / ".gitignore").write_text("test_extra.py\n")
        self.commit()
        (self.tests / "test_extra.py").write_text(self.sample.read_text())
        self.assertEqual(self.git("status", "--porcelain"), "")
        result = self.run_lab("--source-sha", self.sha)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.evidence()["status"], "NOT_VERIFIED")
        self.assertEqual(self.evidence()["total"], 2)

    def test_source_change_during_execution_cannot_attest_pass(self):
        self.sample.write_text("import unittest\nfrom pathlib import Path\n"
                               "class Sample(unittest.TestCase):\n"
                               "    def test_changes_source(self):\n"
                               "        p = Path(__file__)\n"
                               "        p.write_text(p.read_text() + '\\n# Changed\\n')\n")
        self.commit()
        result = self.run_lab("--source-sha", self.sha)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.evidence()["status"], "NOT_VERIFIED")
        self.assertIs(self.evidence()["source_unchanged"], False)

    def test_export_without_git_cannot_attest_pass(self):
        shutil.rmtree(self.root / ".git")
        result = self.run_lab()
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.evidence()["status"], "NOT_VERIFIED")

    def test_direct_report_execution_includes_second_class(self):
        result = subprocess.run([sys.executable, str(LAB / "tests" / "test_report.py"),
                                 "UnitEvidenceTest"], capture_output=True, text=True, timeout=20,
                                env={**os.environ, "PYTHONDONTWRITEBYTECODE": "1"})
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("Ran 7 tests", result.stderr)


if __name__ == "__main__":
    unittest.main()
