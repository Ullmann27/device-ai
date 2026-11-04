"""Targeted negative checks; synthetic inputs only, no model or held-out data."""
import base64
import unittest
from unittest.mock import patch
import recovery_evidence_audit as audit


class AuditChecks(unittest.TestCase):
    def test_unavailable_registered_source_rejected_before_full_audit(self):
        registration = {'files': {'missing.kt': audit.sha(b'synthetic source')}}
        raw = audit.json.dumps(registration).encode()
        registration_hash = audit.sha(raw)
        available = {
            audit.EXPERIMENT + 'registration.json': raw,
            audit.EVIDENCE + 'diagnostic-summary.json': audit.json.dumps({'registrationSha256': registration_hash}).encode(),
            audit.EVIDENCE + 'intent-summary.json': audit.json.dumps({'sourceCommit': 'synthetic'}).encode(),
            audit.EVIDENCE + 'no-development-signal.json': audit.json.dumps({'registrationSha256': registration_hash}).encode(),
        }
        class MissingReader:
            commit = audit.SNAPSHOT
            cache = {}
            def read(self, path):
                if path == 'missing.kt':
                    raise audit.subprocess.CalledProcessError(128, ['git', 'show'])
                return available[path]
            def json(self, path):
                return audit.json.loads(self.read(path))
        with self.assertRaisesRegex(ValueError, 'registered source/data bytes unavailable'):
            audit.audit(MissingReader())

    def scores(self):
        return [dict(split='dev', step=str(step), correct=str(n), total='24', answerNll=str(loss))
                for step, n, loss in [(0, 0, 8.8), (32, 6, 1.7), (64, 8, 1.4), (128, 10, 1.25)]]

    def test_selects_highest_correct(self):
        self.assertEqual(audit.choose(self.scores())['step'], '128')

    def test_selection_ties_use_loss_then_earlier_step(self):
        scores = self.scores()
        for score in scores[1:]:
            score.update(correct='10', answerNll='1.25')
        self.assertEqual(audit.choose(scores)['step'], '32')
        scores[2]['answerNll'] = '1.0'
        self.assertEqual(audit.choose(scores)['step'], '64')

    def test_nonfinite_candidate_rejected(self):
        for bad in ['NaN', 'Infinity', '-Infinity']:
            scores = self.scores()
            scores[2]['answerNll'] = bad
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                audit.choose(scores)

    def test_missing_checkpoint_rejected(self):
        with self.assertRaises(ValueError):
            audit.choose(self.scores()[:-1])

    def test_duplicate_tsv_key_rejected(self):
        with self.assertRaises(ValueError):
            audit.records(b'SCORE\tcorrect=1\tcorrect=10\n')

    def test_receipt_log_tampering_rejected(self):
        raw = b'SCORE\tcorrect=10\n'
        receipt = dict(logSha256=audit.sha(raw), registrationSha256='reg', jarSha256='jar', sourceCommit='src',
                       registeredBytesUnchanged=True, timeout=False, returncode=0, wallSeconds=1, timeoutSeconds=600)
        audit.check_receipt(receipt, raw, 'reg', 'jar', 'src')
        with self.assertRaises(ValueError):
            audit.check_receipt(receipt, raw.replace(b'10', b'12'), 'reg', 'jar', 'src')
        for field in ['sourceCommit', 'registrationSha256', 'jarSha256']:
            bad = dict(receipt)
            bad[field] = 'other'
            with self.subTest(field=field), self.assertRaises(ValueError):
                audit.check_receipt(bad, raw, 'reg', 'jar', 'src')

    def capacity_fixture(self):
        result = dict(checkpointSha256='model', **{'pass': 'false'}, reasons='NO_FIRST_TOKEN_OBSERVATION,INCOMPLETE_32_TOKEN_WORKLOAD',
                      firstTokenObservations='0', full32TokenObservations='0', loadMs='1', maxFirstTokenCallMs='1', maxGeneration32CallMs='1', vmHwmKiB='1024')
        rows = {'CAPACITY_RESULT': [result], 'CAPACITY_MODEL': [dict(checkpointSha256='model', finiteWeights='true')],
                'CAPACITY_TRIAL': [dict(phase='measured', stop='EOS', actualNewTokens='0', requestedNewTokens=str(n)) for _ in range(3) for n in (1, 32)]}
        return rows, result, 'model', {'returncode': 1}

    def test_capacity_failure_binds_actual_trials(self):
        audit.check_capacity(*self.capacity_fixture())
        rows, result, checkpoint, receipt = self.capacity_fixture()
        rows['CAPACITY_TRIAL'][0]['actualNewTokens'] = '1'
        with self.assertRaises(ValueError):
            audit.check_capacity(rows, result, checkpoint, receipt)

    def test_capacity_forged_pass_and_wrong_checkpoint_rejected(self):
        rows, result, checkpoint, receipt = self.capacity_fixture()
        result['pass'] = 'true'
        with self.assertRaises(ValueError):
            audit.check_capacity(rows, result, checkpoint, receipt)
        rows, result, checkpoint, receipt = self.capacity_fixture()
        with self.assertRaises(ValueError):
            audit.check_capacity(rows, result, 'another-model', receipt)

    def test_sealed_read_rejected_before_git(self):
        reader = audit.GitReader.__new__(audit.GitReader)
        reader.cache = {}
        with patch.object(audit.subprocess, 'check_output') as git:
            with self.assertRaisesRegex(ValueError, 'sealed'):
                reader.read(audit.EXPERIMENT + 'intent-data/test.sealed.tsv')
            git.assert_not_called()

    def fixture_cases(self):
        cases = []
        scores = []
        for step in [0, 32, 64, 128]:
            for index in range(24):
                label = str(index // 6)
                cases.append(dict(split='dev', step=str(step), id=str(index), expected=label,
                                  textBase64=base64.b64encode(label.encode()).decode(), tokens='1', stop='EOS', correct='true',
                                  answerNll='1.0', eosNll='0.1', firstEosProbability='0.01'))
            scores.append(dict(split='dev', step=str(step), total='24', correct='24', classCorrect='6,6,6,6', classTotal='6,6,6,6',
                               prematureEos='0', missingEos='0', generatedTokens='24', answerNll='1.0', eosNll='0.1', meanFirstEosProbability='0.01'))
        return scores, cases

    def test_case_totals_reconstructed(self):
        audit.check_cases(*self.fixture_cases())

    def test_claimed_correct_but_wrong_output_rejected(self):
        scores, cases = self.fixture_cases()
        cases[0]['textBase64'] = base64.b64encode(b'wrong').decode()
        with self.assertRaises(ValueError):
            audit.check_cases(scores, cases)

    def test_two_tokens_cannot_count_as_exact_answer(self):
        scores, cases = self.fixture_cases()
        cases[0]['tokens'] = '2'
        with self.assertRaises(ValueError):
            audit.check_cases(scores, cases)

    def test_nan_case_and_wrong_aggregate_rejected(self):
        for key, value in [('answerNll', 'NaN'), ('firstEosProbability', '1.01')]:
            scores, cases = self.fixture_cases()
            cases[0][key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                audit.check_cases(scores, cases)
        scores, cases = self.fixture_cases()
        scores[0]['correct'] = '23'
        with self.assertRaises(ValueError):
            audit.check_cases(scores, cases)


if __name__ == '__main__':
    unittest.main(verbosity=2)
