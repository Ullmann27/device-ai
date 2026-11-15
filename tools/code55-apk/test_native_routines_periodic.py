"""Validate synthetic periodic evidence boundaries."""
import hashlib
import json
from pathlib import Path
import struct
import unittest

import native_routines as proof
import test_native_routines as fixtures


class PeriodicEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.fixture = fixtures.RoutineEvidenceTest(methodName='runTest')
        self.fixture.setUp()
        self.addCleanup(self.fixture.doCleanups)
        self.fixture.complete()
        self.directory = self.fixture.directory

    def json_value(self, file, change):
        path = self.directory / file
        value = json.loads(path.read_text())
        change(value)
        path.write_text(json.dumps(value))

    def state_time(self, label, old, new):
        path = self.directory / (label + '-routines-stdout.txt')
        payload = path.read_bytes()[len(proof.STATE_MAGIC) + 32:]
        before = struct.pack('>q', old)
        self.assertEqual(1, payload.count(before))
        payload = payload.replace(before, struct.pack('>q', new))
        path.write_bytes(proof.STATE_MAGIC + hashlib.sha256(payload).digest() + payload)

    def trigger(self):
        self.json_value('periodic-observation.json',
            lambda value: value.update(mode='SATISFIED_SCHEDULER_TEST_TRIGGER'))
        self.fixture.clock('periodic-run-before', 1900350)
        self.fixture.raw('periodic-run-state', b'ready\n', arguments=
            ['shell', 'cmd', 'jobscheduler', 'get-job-state', '-u', '0', proof.PACKAGE, '620159'])
        self.fixture.raw('periodic-run-s', b'Running job\n', arguments=
            ['shell', 'cmd', 'jobscheduler', 'run', '-s', '-u', '0', proof.PACKAGE, '620159'])

    def test_valid_satisfied_test_trigger_is_distinct_from_natural(self):
        self.trigger()
        result = self.fixture.verify()
        self.assertEqual('SATISFIED_SCHEDULER_TEST_TRIGGER', result['periodic_due_trigger'])
        self.assertGreaterEqual(result['periodic_real_wait_ms'], 900000)
        self.assertEqual('PASS', result['periodic_unchanged_chat'])
        self.assertEqual('PASS', result['periodic_stop_removes_job'])

    def test_minimum_real_host_wait_is_required(self):
        self.json_value('periodic-wait-1-host-time.json', lambda value: value.update(
            started_ns=value['started_ns'] - 1_000_000_000,
            finished_ns=value['finished_ns'] - 1_000_000_000))
        with self.assertRaisesRegex(AssertionError, 'Real minimum'):
            self.fixture.verify()

    def test_device_elapsed_and_rtc_cannot_be_accelerated(self):
        for before, after in [('nowELAPSED=1000800', 'nowELAPSED=900800'),
                              ('nowRTC=1900800', 'nowRTC=2900800')]:
            with self.subTest(after=after):
                self.fixture.complete()
                path = self.directory / 'periodic-wait-1-stdout.txt'
                path.write_text(path.read_text().replace(before, after))
                with self.assertRaisesRegex(AssertionError, 'wall clock'):
                    self.fixture.verify()

    def test_actual_worker_deadline_remains_bounded(self):
        self.fixture.clock('periodic-wait-1', 2020400)
        with self.assertRaisesRegex(AssertionError, 'deadline'):
            self.fixture.verify()

    def test_process_budget_reserves_result_and_normal_stop(self):
        self.json_value('periodic-wait-start-host-time.json', lambda value: value.update(
            started_ns=250_000_000_000, finished_ns=250_001_000_000))
        with self.assertRaisesRegex(AssertionError, 'reserved'):
            self.fixture.verify()

    def test_original_due_boundary_cannot_move_forward(self):
        self.state_time('periodic-first', 1900120, 1900900)
        with self.assertRaises(AssertionError):
            self.fixture.verify()

    def test_second_receipt_cannot_reuse_first_execution(self):
        self.fixture.raw('periodic-complete-receipts', self.fixture.periodic_payloads['first-receipts'])
        with self.assertRaisesRegex(AssertionError, 'two distinct'):
            self.fixture.verify()

    def test_completed_state_requires_new_last_and_next_times(self):
        for old, new in [(1900500, 1000100), (2800520, 1900120)]:
            with self.subTest(old=old):
                self.fixture.complete()
                self.state_time('periodic-complete', old, new)
                with self.assertRaises(AssertionError):
                    self.fixture.verify()

    def test_poll_must_observe_the_completed_result(self):
        self.fixture.raw('periodic-wait-1-routines', self.fixture.periodic_payloads['first'])
        with self.assertRaisesRegex(AssertionError, 'poll and completed'):
            self.fixture.verify()

    def test_periodic_process_cannot_restart(self):
        path = self.directory / 'periodic-wait-1-pid-stdout.txt'
        path.write_text('31\n')
        with self.assertRaises(AssertionError):
            self.fixture.verify()

    def test_digest_must_remain_the_actual_normalized_measurement(self):
        path = self.directory / 'periodic-complete-routines-stdout.txt'
        payload = path.read_bytes()[len(proof.STATE_MAGIC) + 32:]
        original = proof.mutf(proof.single(proof.stored(self.directory, 'periodic-first'))['last_digest'])
        changed = proof.mutf('unrelated digest')
        self.assertEqual(1, payload.count(original))
        payload = payload.replace(original, changed)
        path.write_bytes(proof.STATE_MAGIC + hashlib.sha256(payload).digest() + payload)
        with self.assertRaises(AssertionError):
            self.fixture.verify()

    def test_unchanged_measurement_cannot_append_a_second_report(self):
        path = self.directory / 'periodic-complete-chat-stdout.txt'
        value = json.loads(path.read_text())
        value['at'] = 1900500
        path.write_text(path.read_text() + json.dumps(value) + '\n')
        with self.assertRaisesRegex(AssertionError, 'exactly once'):
            self.fixture.verify()

    def test_unchanged_chat_retains_the_initial_result_timestamp(self):
        path = self.directory / 'periodic-complete-chat-stdout.txt'
        value = json.loads(path.read_text())
        value['at'] = 1900500
        path.write_text(json.dumps(value) + '\n')
        with self.assertRaisesRegex(AssertionError, 'exactly once'):
            self.fixture.verify()

    def test_due_history_requires_matching_uid_service_and_finished_reason(self):
        mutations = [('START-P: #u0a209', 'START-P: #u0a210'),
                     ('OwnerRoutineJobService app called', 'WrongService app called'),
                     ('app called jobFinished', 'canceled'), ('-601ms', '-6ms')]
        for before, after in mutations:
            with self.subTest(after=after):
                self.fixture.complete()
                path = self.directory / 'periodic-complete-job-stdout.txt'
                path.write_text(path.read_text().replace(before, after))
                with self.assertRaisesRegex(AssertionError, 'Android service job|preceded'):
                    self.fixture.verify()

    def test_job_history_requires_one_unambiguous_completed_run(self):
        path = self.directory / 'periodic-complete-job-stdout.txt'
        value = path.read_text()
        events = value.split('Job history:\n')[1].split('\n\n')[0]
        path.write_text(value.replace(events, events + '\n' + events))
        with self.assertRaisesRegex(AssertionError, 'one completed'):
            self.fixture.verify()

    def test_elapsed_history_ages_are_parsed_from_android_format(self):
        for age, expected in [('0', 0), ('-601ms', 601), ('-2s192ms', 2192),
                              ('-4m01s496ms', 241496), ('-1d2h3m4s5ms', 93784005)]:
            self.assertEqual(expected, proof.history_age(age))
        for age in ('+10ms', '-NaN', '-', '1ms'):
            with self.subTest(age=age), self.assertRaises(AssertionError):
                proof.history_age(age)

    def test_force_or_soft_override_is_never_accepted(self):
        for mode in ('force', 'plain'):
            with self.subTest(mode=mode):
                self.fixture.complete()
                self.trigger()
                def change(value):
                    if mode == 'force':
                        value['args'][value['args'].index('run') + 1] = '-f'
                    else:
                        value['args'].remove('-s')
                self.json_value('periodic-run-s-command.json', change)
                with self.assertRaises(AssertionError):
                    self.fixture.verify()

    def test_ready_constraints_and_successful_request_are_both_required(self):
        for name, data in [('periodic-run-state', b'waiting\n'),
                           ('periodic-run-s', b'Job has unsatisfied constraints\n')]:
            with self.subTest(name=name):
                self.fixture.complete()
                self.trigger()
                (self.directory / (name + '-stdout.txt')).write_bytes(data)
                with self.assertRaises(AssertionError):
                    self.fixture.verify()

    def test_natural_result_must_not_hide_an_explicit_request(self):
        self.trigger()
        self.json_value('periodic-observation.json', lambda value: value.update(mode='NATURAL_SCHEDULER'))
        with self.assertRaisesRegex(AssertionError, 'Natural execution included'):
            self.fixture.verify()

    def test_trigger_cannot_precede_true_wait_and_due_time(self):
        self.trigger()
        self.fixture.clock('periodic-run-before', 1900100)
        with self.assertRaises(AssertionError):
            self.fixture.verify()

    def test_active_or_missing_job_is_not_completed(self):
        for text in ('active ready\n', 'pending\n', 'no-component\n'):
            with self.subTest(text=text):
                self.fixture.complete()
                (self.directory / 'periodic-wait-1-job-state-stdout.txt').write_text(text)
                with self.assertRaisesRegex(AssertionError, 'still active'):
                    self.fixture.verify()

    def test_final_stop_requires_revocation_and_job_absence(self):
        for mutation in ('state', 'job'):
            with self.subTest(mutation=mutation):
                self.fixture.complete()
                if mutation == 'state':
                    self.fixture.raw('periodic-stopped-routines', self.fixture.periodic_payloads['complete'])
                else:
                    self.json_value('periodic-after-stop-command.json', lambda value: value.update(returncode=0))
                with self.assertRaises(AssertionError):
                    self.fixture.verify()


if __name__ == '__main__':
    unittest.main()
