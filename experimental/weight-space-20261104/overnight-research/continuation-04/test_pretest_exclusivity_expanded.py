"""No-model source-pinned RED/GREEN tests for strictly local study inventory.

Exactly seven new adversarial cases + one clean control; reuse original 12
regressions to check the candidate against original behavior. No controller,
registered model data, checkpoint, Java process, or final test is invoked.
"""
from __future__ import annotations

import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

HERE = Path(__file__).resolve().parent
OLDER = HERE.parent / 'continuation-03' / 'pretest_exclusivity_hardened.py'
OLDER_TESTS = HERE.parent / 'continuation-02' / 'test_pretest_exclusivity.py'
CANDIDATE = HERE / 'pretest_exclusivity_expanded.py'
OLDER_BLOB = 'b322675b6c68798ff9cef83160bab436a77f8814'
OLDER_TESTS_BLOB = 'aa85775476cbb70e9d3e5852db860575f76cc932'


def git_blob(path: Path) -> str:
    raw = path.read_bytes()
    return hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest()


def load(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        raise RuntimeError('module unavailable')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


if git_blob(OLDER) != OLDER_BLOB or git_blob(OLDER_TESTS) != OLDER_TESTS_BLOB:
    raise RuntimeError('SOURCE_PIN_MISMATCH')
prior = load('continuation03_prior', OLDER)
next_guard = load('continuation04_candidate', CANDIDATE)


class AdversarialCases(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.roots = {}
        for study in ('v1', 'v2', 'v3'):
            root = self.base / study
            (root / 'logs').mkdir(parents=True)
            self.roots[study] = str(root)
        self.plan = self.base / 'inventory.json'
        self.write_plan()

    def write_plan(self):
        self.plan.write_text(json.dumps({'version': 1, 'selectedStudy': 'v3',
                                         'studyOutputs': self.roots}), encoding='utf-8')
        self.pin = hashlib.sha256(self.plan.read_bytes()).hexdigest()

    def assert_red_green(self):
        self.assertEqual(prior.verify_unused(self.plan, self.pin, 'v3')['status'],
                         'LOCALLY_UNCONSUMED_AT_INSPECTION')
        with self.assertRaisesRegex(next_guard.ReuseBlocked, 'CROSS_STUDY_FINAL_REUSE_BLOCKED'):
            next_guard.verify_unused(self.plan, self.pin, 'v3')

    def test_clean_control_remains_non_authorizing(self):
        for module in (prior, next_guard):
            result = module.verify_unused(self.plan, self.pin, 'v3')
            self.assertEqual(result['status'], 'LOCALLY_UNCONSUMED_AT_INSPECTION')

    def test_test_artifact_nested_in_logs(self):
        path = self.base / 'v2/logs/archived'
        path.mkdir()
        (path / 'test-W1.tsv').write_text('synthetic')
        self.assert_red_green()

    def test_test_artifact_at_study_root(self):
        (self.base / 'v1/test-S0.receipt.json').write_text('{}')
        self.assert_red_green()

    def test_hidden_summary_at_study_root(self):
        (self.base / 'v2/.intent-summary.json.tmp').write_text('{}')
        self.assert_red_green()

    def test_nested_start_marker(self):
        path = self.base / 'v3/archived'
        path.mkdir()
        (path / 'final-test-started.json').write_text('{}')
        self.assert_red_green()

    def test_symlink_inside_logs_not_named_test(self):
        (self.base / 'v3/logs/old-receipts').symlink_to(self.base / 'v1', target_is_directory=True)
        self.assert_red_green()

    def test_overlap_not_identical(self):
        nested = self.base / 'v1/secondary-study'
        (nested / 'logs').mkdir(parents=True)
        self.roots['v2'] = str(nested)
        self.write_plan()
        self.assert_red_green()

    def test_late_artifact_in_first_study(self):
        destination = self.base / 'v1/logs/test-W1.tsv'
        for guard, expected_block in ((prior, False), (next_guard, True)):
            destination.unlink(missing_ok=True)
            old_sha = guard._sha
            calls = 0

            def after_second_hash(data):
                nonlocal calls
                calls += 1
                result = old_sha(data)
                if calls == 2:
                    destination.write_text('synthetic late receipt')
                return result

            with patch.object(guard, '_sha', side_effect=after_second_hash):
                if expected_block:
                    with self.assertRaisesRegex(guard.ReuseBlocked, 'CROSS_STUDY_FINAL_REUSE_BLOCKED'):
                        guard.verify_unused(self.plan, self.pin, 'v3')
                else:
                    self.assertEqual(guard.verify_unused(self.plan, self.pin, 'v3')['status'],
                                     'LOCALLY_UNCONSUMED_AT_INSPECTION')
            self.assertTrue(destination.is_file())


def load_tests(loader, tests, pattern):
    # Imported predecessor tests are source-pinned, not rewritten.
    with patch.dict(sys.modules, {'pretest_exclusivity': next_guard}):
        earlier = load('continuation02_original_regressions', OLDER_TESTS)
    earlier.verify_unused = next_guard.verify_unused
    earlier.ReuseBlocked = next_guard.ReuseBlocked
    tests.addTests(loader.loadTestsFromModule(earlier))
    return tests


if __name__ == '__main__':
    unittest.main()
