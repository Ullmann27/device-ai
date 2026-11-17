"""Check protected gates using synthetic metadata."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[2]
SHA, TREE = 'a' * 40, 'b' * 40
BRANCH = 'fix/code59-context-complete-20261114'
RUN, CONTEXT = '123456789', '123456790'


def fixture():
    value = {
        'environment': {'deployment_branch_policy': {'custom_branch_policies': True},
            'protection_rules': [{'type': 'required_reviewers', 'reviewers': [
                {'type': 'User', 'reviewer': {'login': 'Ullmann27'}}]}]},
        'policies': {'branch_policies': [{'name': BRANCH, 'type': 'branch'}]},
        'runs': {}, 'jobs': {}, 'artifacts': {RUN: [], CONTEXT: []}}
    for run, workflow, jobs in [
        (RUN, 'continuum-code57-voice-full-qa.yml',
         ['unsigned-arm64', 'renderer-preflight', 'full-android', 'node-backend', 'native-runtime']),
        (CONTEXT, 'code58-fold7-combined-context-qa.yml', ['android-context-regression'])]:
        value['runs'][run] = {'id': int(run), 'head_sha': SHA, 'head_branch': BRANCH,
            'repository': {'id': 123, 'full_name': 'Ullmann27/device-ai'}, 'head_repository': {'id': 123},
            'status': 'completed', 'conclusion': 'success', 'event': 'push',
            'path': '.github/workflows/' + workflow, 'run_attempt': 1, 'run_started_at': '2026-11-15T00:00:00Z'}
        value['jobs'][run] = {'total_count': len(jobs), 'jobs': [
            {'name': name, 'status': 'completed', 'conclusion': 'success'} for name in jobs]}
    return value


DRIVER = r'''#!/usr/bin/env python3
import json,os,pathlib,re,sys
args=sys.argv[1:];command=pathlib.Path(sys.argv[0]).name
with open(os.environ['SIGNING_CALLS'],'a') as f:f.write(json.dumps([command,*args])+'\n')
fx=json.loads(pathlib.Path(os.environ['SIGNING_FIXTURE']).read_text())
def emit(value):
 print(json.dumps(value));sys.exit(0)
if command=='git':
 if args[0]=='status':sys.exit(0)
 if args[1]=='HEAD':print('a'*40);sys.exit(0)
 if args[1]=='HEAD^{tree}':print('b'*40);sys.exit(0)
 if args[1].endswith(':app/src/main/assets/avatar3d/continuum-avatar.glb'):print('c'*40);sys.exit(0)
if command=='gh' and args[0]=='api':
 uri=args[1]
 if uri.endswith('/deployment-branch-policies'):emit(fx['policies'])
 if uri.endswith('/environments/continuum-original-signing'):emit(fx['environment'])
 m=re.search(r'/runs/(\d+)/attempts/\d+/jobs\?',uri)
 if m:emit(fx['jobs'][m[1]])
 m=re.search(r'/runs/(\d+)/artifacts\?',uri)
 if m:emit({'total_count':len(fx['artifacts'][m[1]]),'artifacts':fx['artifacts'][m[1]]})
 m=re.search(r'/artifacts/\d+/zip$',uri)
 if m:sys.stdout.buffer.write(pathlib.Path(os.environ['SIGNING_ZIP']).read_bytes());sys.exit(0)
 m=re.search(r'/runs/(\d+)$',uri)
 if m:emit(fx['runs'][m[1]])
raise SystemExit(97)
'''


class Code59PreflightTest(unittest.TestCase):
    def run_gate(self, change=None, *, overrides=None, archive_path='marker'):
        value = fixture()
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            zipped = directory / 'fixture.zip'
            with zipfile.ZipFile(zipped, 'w') as archive:
                archive.writestr(archive_path, 'synthetic incomplete evidence')
            payload = zipped.read_bytes()
            groups = [(RUN, name) for name in [
                'Code59-Node-backend-QA', 'Code59-Android-full-QA', 'Code59-Renderer-regressions',
                'Code59-native-runtime-QA', 'Code59-UNSIGNED-NOT-INSTALLABLE']]
            groups.append((CONTEXT, 'fold7-long-context-junit'))
            for index, (run, name) in enumerate(groups):
                value['artifacts'][run].append({'id': 1000 + index, 'name': name, 'expired': False,
                    'created_at': '2026-11-15T00:10:00Z', 'size_in_bytes': len(payload),
                    'digest': 'sha256:' + hashlib.sha256(payload).hexdigest(),
                    'workflow_run': {'id': int(run), 'head_sha': SHA, 'head_branch': BRANCH,
                                     'repository_id': 123, 'head_repository_id': 123}})
            if change:
                change(value)
            (directory / 'fixture.json').write_text(json.dumps(value))
            binary = directory / 'bin';binary.mkdir()
            android = directory / 'android/build-tools/35.0.0';android.mkdir(parents=True)
            for name in ['gh', 'git']:
                file = binary / name;file.write_text(DRIVER);file.chmod(0o755)
            for name in ['aapt2', 'apksigner', 'zipalign']:
                file = android / name;file.write_text('#!/bin/sh\nexit 97\n');file.chmod(0o755)
            env = {'PATH': str(binary) + os.pathsep + os.environ['PATH'], 'HOME': temporary, 'TMPDIR': temporary,
                   'GITHUB_REPOSITORY': 'Ullmann27/device-ai', 'GITHUB_REF': 'refs/heads/' + BRANCH,
                   'GITHUB_EVENT_NAME': 'workflow_dispatch', 'GITHUB_SHA': SHA,
                   'SOURCE_SHA': SHA, 'OWNER_APPROVED_SHA': SHA, 'QA_RUN_ID': RUN, 'CONTEXT_RUN_ID': CONTEXT,
                   'ANDROID_HOME': str(directory / 'android'), 'SIGNING_FIXTURE': str(directory / 'fixture.json'),
                   'SIGNING_CALLS': str(directory / 'calls'), 'SIGNING_ZIP': str(zipped), **(overrides or {})}
            result = subprocess.run(['bash', str(ROOT / 'scripts/continuum-code50-github-signer.sh'), 'sign'],
                                    cwd=ROOT, env=env, text=True, capture_output=True, timeout=30)
            calls = (directory / 'calls').read_text() if (directory / 'calls').exists() else ''
            self.assertNotRegex(calls, r'"(?:sign|keytool|create|edit)"')
            self.assertEqual(69, result.returncode, result.stdout + result.stderr)
            return result.stdout + result.stderr, calls

    def rejected(self, expected, change=None, **options):
        output, calls = self.run_gate(change, **options)
        self.assertIn('CODE59_SIGN_' + expected, output)
        return calls

    def test_owner_sha_and_source_are_explicit(self):
        for override, error in [({'OWNER_APPROVED_SHA': ''}, 'APPROVED_SHA_FORMAT'),
            ({'OWNER_APPROVED_SHA': 'c' * 40}, 'APPROVED_SHA_DIFFERS_FROM_SOURCE'),
            ({'GITHUB_SHA': 'c' * 40}, 'SOURCE_DIFFERS_FROM_RUN_COMMIT')]:
            with self.subTest(error=error):
                self.assertEqual('', self.rejected(error, overrides=override))

    def test_context_run_is_required_and_apk_run_is_forbidden(self):
        for override, error in [({'CONTEXT_RUN_ID': ''}, 'CONTEXT_RUN_FORMAT'),
            ({'CONTEXT_RUN_ID': RUN}, 'CONTEXT_RUN_FORMAT'),
            ({'APK_RUN_ID': CONTEXT}, 'UNEXPECTED_APK_RUN')]:
            with self.subTest(error=error):
                self.rejected(error, overrides=override)

    def test_owner_reviewer_cannot_be_omitted(self):
        self.rejected('ENVIRONMENT_REQUIRED_OWNER_REVIEWER_MISSING',
                      lambda f: f['environment'].update(protection_rules=[]))

    def test_branch_protection_is_exact(self):
        for branch in ['*', 'main', 'integrate/code55-apk-fable-20261112']:
            with self.subTest(branch=branch):
                self.rejected('ENVIRONMENT_BRANCH_POLICY_NOT_EXACT',
                              lambda f: f['policies']['branch_policies'][0].update(name=branch))

    def test_full_and_context_runs_are_source_bound_and_green(self):
        for run in [RUN, CONTEXT]:
            for field, value in [('head_sha', 'd' * 40), ('conclusion', 'failure'),
                                 ('status', 'in_progress'), ('path', '.github/workflows/foreign.yml')]:
                with self.subTest(run=run, field=field):
                    self.rejected('QA_IDENTITY_OR_RESULT_MISMATCH',
                                  lambda f: f['runs'][run].update({field: value}))

    def test_every_native_and_context_job_is_required(self):
        for run in [RUN, CONTEXT]:
            with self.subTest(run=run):
                self.rejected('REQUIRED_JOBS_NOT_GREEN',
                              lambda f: f['jobs'][run]['jobs'][-1].update(conclusion='failure'))

    def test_artifact_identity_and_freshness_are_checked(self):
        for field, value in [('expired', True), ('created_at', '2026-11-14T00:00:00Z'), ('name', 'foreign')]:
            with self.subTest(field=field):
                self.rejected('ARTIFACT_IDENTITY_OR_EXPIRY',
                              lambda f: f['artifacts'][RUN][0].update({field: value}))
        self.rejected('ARTIFACT_IDENTITY_OR_EXPIRY',
                      lambda f: f['artifacts'][CONTEXT][0]['workflow_run'].update(head_sha='d' * 40))

    def test_downloaded_bytes_are_rechecked(self):
        self.rejected('ARTIFACT_DIGEST_MISMATCH',
                      lambda f: f['artifacts'][RUN][0].update(digest='sha256:' + '0' * 64))
        self.rejected('ARTIFACT_SIZE_MISMATCH',
                      lambda f: f['artifacts'][RUN][0].update(size_in_bytes=1))
        self.rejected('ARTIFACT_ARCHIVE_UNSAFE', archive_path='../escape')

    def test_green_metadata_without_actual_proof_still_cannot_sign(self):
        calls = self.rejected('CODE59_RECEIPT_OR_APK_INVALID')
        self.assertEqual(6, calls.count('/zip"'))
        self.assertNotIn('ORIGINAL_SECRETS', calls)
