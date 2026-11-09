import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const script = fs.readFileSync(path.join(root, 'scripts/continuum-code52-github-signer.sh'), 'utf8');
const workflow = fs.readFileSync(path.join(root, '.github/workflows/continuum-code52-sign-release.yml'), 'utf8');

test('signing stays manual, non-main and owner-review protected', () => {
  assert.match(workflow, /^on:\n  workflow_dispatch:\n    inputs:/m);
  assert.doesNotMatch(workflow, /^  (push|pull_request|workflow_run|schedule):/m);
  assert.match(workflow, /github.ref == 'refs\/heads\/chatgpt\/code52-owner-signing-20261109'/);
  assert.match(workflow, /environment: continuum-original-signing/);
  assert.match(script, /branch="chatgpt\/code52-owner-signing-20261109"/);
  assert.match(script, /owner_login="Ullmann27"/);
  assert.match(script, /ENVIRONMENT_REQUIRED_OWNER_REVIEWER_MISSING/);
  assert.match(script, /ENVIRONMENT_BRANCH_POLICY_NOT_EXACT/);
  assert.ok(workflow.indexOf('  preflight:') < workflow.indexOf('  release-original-signed:'));
  const preflight = workflow.slice(workflow.indexOf('  preflight:'), workflow.indexOf('  release-original-signed:'));
  assert.doesNotMatch(preflight, /secrets\./);
  assert.equal([...workflow.matchAll(/secrets\./g)].length, 2);
});

test('unverified or unsigned Code52 source can never be published', () => {
  assert.match(script, /qa_branch="chatgpt\/code52-unified-integration-20261109"/);
  assert.match(script, /continuum-code52-integration-qa\.yml/);
  assert.match(script, /\.event=="push" and \.status=="completed"/);
  assert.match(script, /validate-source-and-build/);
  assert.match(script, /APPROVED_SHA_DIFFERS_FROM_SOURCE/);
  assert.match(script, /QA_IDENTITY_OR_RESULT_MISMATCH/);
  assert.match(script, /CODE52_QA_NOT_GREEN/);
  assert.match(script, /release_state=UNSIGNED_QA_ONLY/);
  assert.match(script, /sha256sum -c SHA256SUMS\.txt/);
  assert.match(script, /UNSIGNED_APK_UNEXPECTEDLY_SIGNED/);
  assert.match(script, /continuum-local-code51-6c23d295e2fca2d4e5950a866547733ec5ff22b0/);
  assert.match(script, /code="52"/);
  assert.match(script, /name_version="0\.3\.51"/);
});

test('original certificate bound and no secret echoed', () => {
  assert.match(script, /fingerprint="6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23"/);
  assert.match(script, /pkg="at\.heinz\.deviceai\.continuumlocal"/);
  assert.match(script, /ORIGINAL_CERTIFICATE_MISMATCH/);
  assert.match(script, /SIGNED_APK_CERTIFICATE_MISMATCH/);
  assert.match(script, /PUBLIC_DOWNLOAD_SHA_MISMATCH/);
  assert.ok(script.indexOf('ENVIRONMENT_REQUIRED_OWNER_REVIEWER_MISSING') <
    script.indexOf('ORIGINAL_P12_BASE64_INVALID'));
  assert.ok(script.indexOf('--draft --latest=false') <
    script.indexOf('GITHUB_READBACK_SHA_MISMATCH'));
  assert.doesNotMatch(script, /^set -x$/m);
  assert.doesNotMatch(script, /\b(echo|printf|cat|tee)\b[^\n]*\$\{?(?:ORIGINAL_P12|ORIGINAL_PASSWORD|SIGN_PASS)\b/);
});
