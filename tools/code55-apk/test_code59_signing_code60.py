"""Negative tests for the manual Code60 adapter; all service/tool data are synthetic."""
import json
from pathlib import Path
import re
import subprocess
import tempfile
import unittest
from unittest import mock
import xml.etree.ElementTree as ET

import code59_signing_evidence as evidence
import test_code59_signing_preflight as legacy

BRANCH = 'integrate/continuum-command03-android-20261115'


class Code60PreflightTest(legacy.Code59PreflightTest):
    """Exercise the same protected preflight with Code60 metadata, without keys or network."""

    def run_gate(self, change=None, **options):
        def code60_fixture(value):
            for run in value['runs'].values():
                run['event'] = 'workflow_dispatch'
            for artifacts in value['artifacts'].values():
                for artifact in artifacts:
                    artifact['name'] = artifact['name'].replace('Code59-', 'Code60-')
            if change:
                change(value)

        with mock.patch.object(legacy, 'BRANCH', BRANCH):
            return super().run_gate(code60_fixture, **options)

    def rejected(self, expected, change=None, **options):
        output, calls = self.run_gate(change, **options)
        self.assertIn('CODE60_SIGN_' + expected, output)
        return calls

    def test_green_metadata_without_actual_proof_still_cannot_sign(self):
        calls = self.rejected('CODE60_RECEIPT_OR_APK_INVALID')
        self.assertEqual(6, calls.count('/zip"'))
        self.assertNotIn('ORIGINAL_SECRETS', calls)

    def test_both_code60_qa_runs_must_be_manual(self):
        for run in (legacy.RUN, legacy.CONTEXT):
            for event in ('push', 'pull_request', 'workflow_run', 'schedule'):
                with self.subTest(run=run, event=event):
                    self.rejected('QA_IDENTITY_OR_RESULT_MISMATCH',
                                  lambda value: value['runs'][run].update(event=event))

    def test_signer_itself_rejects_automatic_events_before_service_calls(self):
        for event in ('push', 'pull_request', 'workflow_run'):
            with self.subTest(event=event):
                self.assertEqual('', self.rejected('WRONG_EVENT', overrides={'GITHUB_EVENT_NAME': event}))

    def test_other_repository_is_rejected_before_service_calls(self):
        self.assertEqual('', self.rejected('WRONG_REPOSITORY',
                                          overrides={'GITHUB_REPOSITORY': 'another/device-ai'}))

    def test_unknown_branch_never_inherits_the_code60_profile(self):
        for branch in ('main', BRANCH + '-other', 'release/code60'):
            with self.subTest(branch=branch):
                output, calls = self.run_gate(overrides={'GITHUB_REF': 'refs/heads/' + branch})
                self.assertIn('SIGN_WRONG_BRANCH', output)
                self.assertEqual('', calls)

    def test_code59_runs_and_artifacts_cannot_be_reused_for_code60(self):
        for run in (legacy.RUN, legacy.CONTEXT):
            with self.subTest(run=run):
                self.rejected('QA_IDENTITY_OR_RESULT_MISMATCH',
                              lambda value: value['runs'][run].update(
                                  head_branch='fix/code59-context-complete-20261114'))
        self.rejected('ARTIFACT_IDENTITY_OR_EXPIRY', lambda value: value['artifacts'][legacy.RUN][0].update(
            name='Code59-Node-backend-QA'))

    def test_artifacts_cannot_cross_branch_or_repository(self):
        for field, changed in (('head_branch', 'main'), ('repository_id', 99), ('head_repository_id', 99)):
            with self.subTest(field=field):
                self.rejected('ARTIFACT_IDENTITY_OR_EXPIRY', lambda value:
                              value['artifacts'][legacy.RUN][0]['workflow_run'].update({field: changed}))

    def test_jobs_cannot_be_duplicated_or_omitted(self):
        for run in (legacy.RUN, legacy.CONTEXT):
            with self.subTest(run=run):
                self.rejected('REQUIRED_JOBS_NOT_GREEN', lambda value: value['jobs'][run]['jobs'].pop())


class Code60EvidenceContractTest(unittest.TestCase):
    def test_release_profile_is_fixed_and_code59_default_is_preserved(self):
        self.assertEqual({'code': '59', 'version': '0.3.58',
                          'apk': 'Continuum-Code59-UNSIGNED-NOT-INSTALLABLE.apk',
                          'evidence': 'code59-evidence', 'prefix': 'code59'}, evidence.release_profile())
        self.assertEqual('60', evidence.release_profile(60)['code'])
        self.assertEqual('0.3.59', evidence.release_profile(60)['version'])
        for code in (None, True, 0, 58, 61, 999, '60', 60.0):
            with self.subTest(code=code), self.assertRaisesRegex(ValueError, 'Unsupported release profile'):
                evidence.release_profile(code)

    def test_code60_requires_all_existing_composer_and_new_native_regressions(self):
        original, extended = set(evidence.required_host_cases()), set(evidence.required_host_cases(60))
        self.assertEqual(4, len(original))
        self.assertEqual(33, len(extended))
        self.assertTrue(original < extended)
        self.assertIn(('at.heinz.deviceai.ContinuumCommandNavigationUiTest',
                       'rapidSelectionsRejectAnOlderQueuedReadAndItsPersistentPointer'), extended)
        self.assertIn(('at.heinz.deviceai.ContinuumCommandNavigationUiTest',
                       'runningLocalRequestIsCancelledButTheChatWaitsForItsActualEnd'), extended)
        self.assertIn(('at.heinz.deviceai.ContinuumCommandNavigationUiTest',
                       'conversationKeepsOneMountedPresenceAndBoundFeedWithoutHiddenTargets'), extended)

    def test_missing_or_skipped_code60_regressions_are_rejected(self):
        required = evidence.required_host_cases(60)
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            suite = ET.Element('testsuite', tests=str(len(required)), failures='0', errors='0', skipped='0')
            for classname, name in required:
                ET.SubElement(suite, 'testcase', classname=classname, name=name)
            path = directory / 'TEST-code60.xml'
            ET.ElementTree(suite).write(path)
            self.assertEqual(33, evidence.junit(directory, 33, required=required)['executed'])
            missing = suite[-1]
            suite.remove(missing)
            suite.set('tests', str(len(required) - 1))
            ET.ElementTree(suite).write(path)
            with self.assertRaisesRegex(ValueError, 'Missing executed regression coverage'):
                evidence.junit(directory, 0, required=required)
            suite.append(missing)
            ET.SubElement(missing, 'skipped')
            suite.set('tests', str(len(required)))
            suite.set('skipped', '1')
            ET.ElementTree(suite).write(path)
            with self.assertRaisesRegex(ValueError, 'Unapproved skipped case'):
                evidence.junit(directory, 0, skipped=True, required=required)

    def test_every_bounded_history_and_stable_title_regression_is_mandatory(self):
        required = evidence.required_host_cases(60)
        names = ('titleSurvivesTheRestoreWindowAndAHistoryRestart',
                 'appendUpdatesOnlyItsCachedConversationUntilAnExplicitRefresh',
                 'firstOwnerMessageReplacesGreetingButLaterMessagesDoNotRenameIt',
                 'initialIndexOpensOnlyTheHundredMostRecentlyModifiedTranscripts')
        for name in names:
            missing = ('at.heinz.deviceai.ui.ContinuumChatHistoryTest', name)
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temporary:
                self.assertIn(missing, required)
                remaining = [case for case in required if case != missing]
                suite = ET.Element('testsuite', tests=str(len(remaining)), failures='0', errors='0', skipped='0')
                for classname, case in remaining:
                    ET.SubElement(suite, 'testcase', classname=classname, name=case)
                ET.ElementTree(suite).write(Path(temporary) / 'TEST-code60.xml')
                with self.assertRaisesRegex(ValueError, 'Missing executed regression coverage'):
                    evidence.junit(Path(temporary), 0, required=required)

    def test_code59_apk_receipt_cannot_be_accepted_as_code60(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            (directory / evidence.release_profile(60)['apk']).write_bytes(b'not an APK; receipt rejected first')
            receipt = {'source': legacy.SHA, 'source_tree': legacy.TREE, 'versionCode': '59',
                       'versionName': '0.3.58', 'package': 'at.heinz.deviceai.continuumlocal',
                       'abi': 'arm64-v8a', 'status': 'UNSIGNED_NOT_INSTALLABLE', 'fold7_test': 'NOT_RUN'}
            for change in ({}, {'versionCode': '60'}, {'versionCode': '60', 'versionName': '0.3.59',
                                                        'package': 'at.heinz.deviceai.mentorpaired'}):
                with self.subTest(change=change):
                    values = {**receipt, **change}
                    (directory / 'BUILD-INFO.txt').write_text(''.join(f'{key}={value}\n' for key, value in values.items()))
                    with self.assertRaisesRegex(ValueError, 'APK receipt mismatch'):
                        evidence.check_apk(directory, legacy.SHA, legacy.TREE, directory, directory, directory, 60)

    def test_historical_filename_cannot_hide_mixed_release_artifacts(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            (directory / evidence.release_profile()['apk']).write_bytes(b'synthetic')
            with self.assertRaisesRegex(ValueError, 'Missing or ambiguous proof'):
                evidence.check_apk(directory, legacy.SHA, legacy.TREE, directory, directory, directory, 60)


class Code60WorkflowSourceGuardTest(unittest.TestCase):
    def guards(self):
        guards = []
        for name, expected in (('continuum-code57-voice-full-qa.yml', 5),
                               ('code58-fold7-combined-context-qa.yml', 1)):
            source = (legacy.ROOT / '.github/workflows' / name).read_text()
            found = re.findall(r'^          if \[\[ "\$RELEASE_CODE" == 60 \]\]; then\n[\s\S]*?^          fi$',
                               source, re.M)
            self.assertEqual(expected, len(found))
            guards.extend(found)
        return guards

    def execute(self, guard, **overrides):
        env = {'RELEASE_CODE': '60', 'GITHUB_EVENT_NAME': 'workflow_dispatch',
               'GITHUB_REF': 'refs/heads/' + BRANCH, 'GITHUB_SHA': legacy.SHA,
               'EXPECTED_SOURCE_SHA': legacy.SHA, **overrides}
        return subprocess.run(['/bin/bash', '-euc', guard], env=env, text=True,
                              capture_output=True, timeout=5).returncode

    def test_all_six_actual_guards_accept_only_explicit_matching_code60_source(self):
        for index, guard in enumerate(self.guards()):
            with self.subTest(index=index):
                self.assertEqual(0, self.execute(guard))
                for change in ({'EXPECTED_SOURCE_SHA': ''}, {'EXPECTED_SOURCE_SHA': 'b' * 40},
                               {'EXPECTED_SOURCE_SHA': 'not-a-commit'}, {'GITHUB_EVENT_NAME': 'push'},
                               {'GITHUB_REF': 'refs/heads/main'}):
                    self.assertNotEqual(0, self.execute(guard, **change))

    def test_code59_does_not_gain_a_new_expected_sha_requirement(self):
        for guard in self.guards():
            self.assertEqual(0, self.execute(guard, RELEASE_CODE='59', EXPECTED_SOURCE_SHA='',
                                            GITHUB_EVENT_NAME='push',
                                            GITHUB_REF='refs/heads/fix/code59-context-complete-20261114'))


if __name__ == '__main__':
    unittest.main()
