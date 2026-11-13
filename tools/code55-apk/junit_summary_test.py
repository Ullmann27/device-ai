"""Exercise actual XML acceptance boundaries."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET

TOOL = Path(__file__).with_name("junit-summary.py")
POLICY = json.loads(TOOL.with_name("known-junit-skips.json").read_text())


class JUnitGateTest(unittest.TestCase):
    def result(self, cases, strict=True):
        with tempfile.TemporaryDirectory() as folder:
            suite = ET.Element("testsuite", tests=str(len(cases)),
                failures=str(sum(kind == "failure" for _, _, kind in cases)),
                errors=str(sum(kind == "error" for _, _, kind in cases)),
                skipped=str(sum(kind == "skipped" for _, _, kind in cases)))
            for cls, name, kind in cases:
                case = ET.SubElement(suite, "testcase", classname=cls, name=name)
                if kind:
                    ET.SubElement(case, kind, message="synthetic")
            ET.ElementTree(suite).write(Path(folder) / "TEST-synthetic.xml")
            args = [sys.executable, "-B", str(TOOL), folder]
            if strict:
                args.append("--strict-skips")
            return subprocess.run(args, env={**os.environ, "GITHUB_SHA": "a" * 40},
                text=True, capture_output=True)

    def test_known_skips_with_executed_test_pass(self):
        known = [(cls, case, "skipped") for cls, cases in POLICY.items() for case in cases]
        self.assertEqual(17, len(known))
        result = self.result(known + [("Synthetic", "actuallyExecuted", "")])
        self.assertEqual(0, result.returncode, result.stderr)
        receipt = json.loads(result.stdout)
        self.assertEqual(1, receipt["executed"])
        self.assertEqual([], receipt["unexpected_skips"])

    def test_new_skip_is_rejected(self):
        result = self.result([("Synthetic", "executed", ""), ("Synthetic", "newSkip", "skipped")])
        self.assertNotEqual(0, result.returncode)

    def test_only_known_skips_are_not_execution(self):
        cls, cases = next(iter(POLICY.items()))
        self.assertNotEqual(0, self.result([(cls, cases[0], "skipped")]).returncode)

    def test_failures_and_errors_are_rejected(self):
        for kind in ("failure", "error"):
            with self.subTest(kind=kind):
                self.assertNotEqual(0, self.result([("Synthetic", "executed", ""),
                    ("Synthetic", "broken", kind)]).returncode)

    def test_empty_suite_is_rejected(self):
        self.assertNotEqual(0, self.result([]).returncode)


if __name__ == "__main__":
    unittest.main()
