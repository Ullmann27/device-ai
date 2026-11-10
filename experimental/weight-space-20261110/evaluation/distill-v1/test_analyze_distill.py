#!/usr/bin/env python3
"""Reject altered EXP-12 controls using synthetic log adapters."""

import argparse
import copy
import csv
import hashlib
from pathlib import Path
import unittest

import analyze_distill as subject


class DistillAnalysisTests(unittest.TestCase):
    root = None
    base_path = None

    @classmethod
    def setUpClass(cls):
        cls.base = subject.load_module(cls.base_path, 'contrast_reference')
        cls.raw = (cls.root / 'training/contrast-answer-results-v1/stdout.tsv').read_text()
        cls.expected = {'dev': cls.base.read_rows(cls.root / 'data/note-contrasts-v1/dev.tsv'),
                        'retention': cls.base.read_rows(cls.root / 'data/retention.tsv')}

    def setUp(self):
        self.records = self.base.parse_log(self.raw)
        self.records['CONFIG'][0].update(mode='distill', replayBatch='2', replayWeight='0.1',
                                         teacherWeight='1.0', experiment='EXP-12', teacherScope='replay.txt-only')
        for row in self.records['UPDATE']:
            row['teacherKl'] = '0.0'
        self.records['RESULT'][0].update(teacherUnchanged='true', teacherCacheUnchanged='true')

    def analyze(self):
        return subject.analyze_records(self.records, self.expected, self.base)

    def test_original_gates_remain_failed(self):
        result = self.analyze()
        self.assertEqual(result['exp05FavorableDevSignal'], 'FAIL')
        self.assertFalse(result['exp05FrozenChecks']['baselineAllFourCapacityProbes'])
        self.assertEqual(result['exp12DevelopmentSignal'], 'FAIL')

    def test_teacher_weight_change_rejected(self):
        self.records['CONFIG'][0]['teacherWeight'] = '0.5'
        with self.assertRaisesRegex(ValueError, 'teacher weight'):
            self.analyze()

    def test_teacher_scope_change_rejected(self):
        self.records['CONFIG'][0]['teacherScope'] = 'dev.tsv'
        with self.assertRaisesRegex(ValueError, 'teacher scope'):
            self.analyze()

    def test_nonfinite_kl_rejected(self):
        self.records['UPDATE'][0]['teacherKl'] = 'NaN'
        with self.assertRaisesRegex(ValueError, 'nonfinite'):
            self.analyze()

    def test_negative_kl_rejected(self):
        self.records['UPDATE'][0]['teacherKl'] = '-0.01'
        with self.assertRaisesRegex(ValueError, 'negative KL'):
            self.analyze()

    def test_roundoff_tolerance_bounded(self):
        self.records['UPDATE'][0]['teacherKl'] = '-1e-7'
        self.assertEqual(self.analyze()['teacherKl']['minimum'], -1e-7)

    def test_changed_teacher_rejected(self):
        self.records['RESULT'][0]['teacherUnchanged'] = 'false'
        with self.assertRaisesRegex(ValueError, 'teacher changed'):
            self.analyze()

    def test_capacity_count_cannot_hide_identity_loss(self):
        for row in self.records['CAPACITY']:
            if row['step'] == '128':
                row['tokens'] = '32' if row['id'] in ('0', '1') else '0'
                row['eos'] = 'false' if row['id'] in ('0', '1') else 'true'
        result = self.analyze()
        self.assertTrue(result['exp05FrozenChecks']['capacityCountNoDecline'])
        self.assertFalse(result['exp12DevelopmentChecks']['originalCapacityIdsTwoAndThreePreserved'])

    def test_expanded_parser_rejects_unknown_fields(self):
        with self.assertRaisesRegex(ValueError, 'unexpected fields'):
            subject.parse_log('TEACHER\tphase=after\tfake=1', self.base)

    def test_teacher_digest_binds_actual_tensor_bytes(self):
        model = {'tensors': [('a', 1, 2, b'12345678', [])], 'sha256': 'a' * 64}
        digest = hashlib.sha256(b'a:1:2\n12345678').hexdigest()
        common = dict(parameterSha256=digest, cacheSha256='b' * 64, examples='16',
                      rows='512', source='replay.txt', originalCheckpointSha256='a' * 64)
        records = {'TEACHER': [dict(common, phase='before', elapsedMillis='1'), dict(common, phase='after')]}
        result = subject.validate_teacher(records, model, self.base)
        self.assertEqual(result['parameterSha256'], digest)
        for row in records['TEACHER']:
            row['parameterSha256'] = 'c' * 64
        with self.assertRaisesRegex(ValueError, 'original weights'):
            subject.validate_teacher(records, model, self.base)

    def test_missing_teacher_record_rejected(self):
        with self.assertRaisesRegex(ValueError, 'before/after'):
            subject.validate_teacher({'TEACHER': []}, {}, self.base)

    def test_java_rng_reference_vector(self):
        rng = subject.JavaRandom(20261110)
        self.assertEqual([rng.next_int(64) for _ in range(12)], self.java_reference)

    def test_schedule_change_rejected(self):
        path = self.root / 'data/note-contrasts-v1/train.tsv'
        with path.open(newline='') as stream:
            rows = list(csv.DictReader(stream, delimiter='\t'))
        groups = [[r['id'] for r in rows if r['label'] == label] for label in ('0', '3')]
        rng = subject.JavaRandom(20261110)
        schedule = []
        for step in range(128):
            ids = [group[rng.next_int(len(group))] for group in groups for _ in range(2)]
            schedule.append({'step': str(step+1), 'answerIds': ','.join(ids),
                             'replayIndices': f'{step*2 % 16},{(step*2+1) % 16}'})
        subject.validate_schedule({'SCHEDULE': schedule}, path, self.base)
        schedule[50]['answerIds'] = schedule[51]['answerIds']
        with self.assertRaisesRegex(ValueError, 'answer RNG'):
            subject.validate_schedule({'SCHEDULE': schedule}, path, self.base)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--fixture-root', type=Path, required=True)
    parser.add_argument('--base-analyzer', type=Path, required=True)
    parser.add_argument('--java-reference', required=True)
    args, remaining = parser.parse_known_args()
    DistillAnalysisTests.root = args.fixture_root
    DistillAnalysisTests.base_path = args.base_analyzer
    DistillAnalysisTests.java_reference = [int(x) for x in args.java_reference.split(',')]
    unittest.main(argv=[__file__, *remaining])
