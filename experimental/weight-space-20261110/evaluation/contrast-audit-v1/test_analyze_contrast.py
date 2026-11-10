#!/usr/bin/env python3
"""Exercise missing evidence and corrupted metric rejection."""

import argparse
import copy
import json
from pathlib import Path
import tempfile
import unittest

import analyze_contrast as subject


class AnalysisTests(unittest.TestCase):
    fixture_root = None

    @classmethod
    def setUpClass(cls):
        root = cls.fixture_root
        cls.raw = (root / 'training/contrast-answer-results-v1/stdout.tsv').read_text()
        cls.reference = subject.parse_log(cls.raw)
        cls.expected = {
            'dev': subject.read_rows(root / 'data/note-contrasts-v1/dev.tsv'),
            'retention': subject.read_rows(root / 'data/retention.tsv'),
        }

    def setUp(self):
        self.records = copy.deepcopy(self.reference)

    def analyze(self):
        return subject.analyze_log(self.records, self.expected, 128, 512)

    def test_known_failure_stays_failure(self):
        result = self.analyze()
        self.assertEqual(result['favorableDevSignal'], 'FAIL')
        self.assertEqual(result['scores']['dev']['after']['exact'], 38)
        self.assertEqual(result['capacity']['beforeFullLength'], 2)
        self.assertEqual(result['capacity']['afterFullLength'], 0)
        self.assertTrue(result['frozenChecks']['retentionExactNoDecline'])
        self.assertFalse(result['frozenChecks']['baselineAllFourCapacityProbes'])

    def test_missing_update_rejected(self):
        self.records['UPDATE'].pop(20)
        with self.assertRaisesRegex(ValueError, 'updates'):
            self.analyze()

    def test_duplicate_case_rejected(self):
        self.records['CASE'].append(self.records['CASE'][0])
        with self.assertRaisesRegex(ValueError, 'duplicate case'):
            self.analyze()

    def test_missing_case_rejected(self):
        self.records['CASE'].pop(0)
        with self.assertRaisesRegex(ValueError, 'missing expected case'):
            self.analyze()

    def test_corrupt_label_rejected(self):
        self.records['CASE'][0]['expected'] = '3'
        with self.assertRaisesRegex(ValueError, 'label or id'):
            self.analyze()

    def test_forged_exact_match_rejected(self):
        self.records['CASE'][0]['exact'] = 'true'
        with self.assertRaisesRegex(ValueError, 'exact score'):
            self.analyze()

    def test_invalid_eos_token_rejected(self):
        self.records['CASE'][0]['tokens'] = '511'
        with self.assertRaisesRegex(ValueError, 'generated tokens'):
            self.analyze()

    def test_nonfinite_probability_rejected(self):
        self.records['CASE'][0]['firstEos'] = 'NaN'
        with self.assertRaisesRegex(ValueError, 'nonfinite'):
            self.analyze()

    def test_missing_capacity_rejected(self):
        self.records['CAPACITY'].pop()
        with self.assertRaisesRegex(ValueError, 'missing capacity'):
            self.analyze()

    def test_aggregate_disagreement_rejected(self):
        self.records['SCORE'][-1]['restrictedCorrect'] = '32'
        with self.assertRaisesRegex(ValueError, 'aggregate diagnostics'):
            self.analyze()

    def test_duplicate_field_rejected(self):
        with self.assertRaisesRegex(ValueError, 'duplicate field'):
            subject.parse_log('RESULT\tupdates=128\tupdates=0\tinputUnchanged=true\telapsedMillis=1')

    def test_unknown_record_rejected(self):
        with self.assertRaisesRegex(ValueError, 'unknown record'):
            subject.parse_log(self.raw + 'FAVORABLE\tstatus=PASS\n')

    def test_changed_log_rejected_by_receipt(self):
        root = self.fixture_root
        source = root / 'training/contrast-answer-results-v1'
        registration = root / 'training/contrast-registration/registration.json'
        spec = json.loads(registration.read_text())
        command = spec['commands']['answer']
        with tempfile.TemporaryDirectory() as temporary:
            evidence = Path(temporary)
            (evidence / 'stdout.tsv').write_text(self.raw + '\n')
            (evidence / 'receipt.json').write_bytes((source / 'receipt.json').read_bytes())
            (evidence / 'stderr.txt').write_bytes((source / 'stderr.txt').read_bytes())
            with self.assertRaisesRegex(ValueError, 'stdout identity'):
                subject.audit(registration, evidence, Path(command[-4]),
                              Path(command[-1]) / 'final.lm1', root / 'data',
                              root / 'evaluation/audit_lm1_weights.py')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--fixture-root', required=True, type=Path)
    args, remaining = parser.parse_known_args()
    AnalysisTests.fixture_root = args.fixture_root
    unittest.main(argv=[__file__, *remaining])
