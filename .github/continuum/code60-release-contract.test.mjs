import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {releaseProfile, selectChecks} from '../../tools/code55-apk/code59-node-proof.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const read = name => fs.readFileSync(path.join(root, name), 'utf8');
const branch = 'integrate/continuum-command03-android-20261115';
const ref = 'refs/heads/' + branch;
const repairRef = 'refs/heads/fix/code60-full-qa-html-mode-20261116';
const full = read('.github/workflows/continuum-code57-voice-full-qa.yml');
const context = read('.github/workflows/code58-fold7-combined-context-qa.yml');
const workflow = read('.github/workflows/continuum-code50-sign-release.yml');
const signer = read('scripts/continuum-code50-github-signer.sh');

test('only the two exact release branches select fixed Node evidence profiles', () => {
  assert.equal(releaseProfile('fix/code59-context-complete-20261114'), '59');
  assert.equal(releaseProfile(branch), '60');
  for (const value of ['main', '', null, undefined, branch + '-other', 'refs/heads/' + branch,
    'release/code60', 'fix/code59-context-complete-20261114/child'])
    assert.throws(() => releaseProfile(value), /Unsupported release branch/);
});

test('manual-only owner release and isolated repair push remain separate', () => {
  const push = text => text.match(/^  push:\n([\s\S]*?)(?=^  [a-z_]+:|^[a-z]+:)/m)?.[1];
  assert.equal(push(full), '    branches:\n      - release/code59-complete-qa-20261114\n' +
    '      - fix/code59-context-complete-20261114\n      - diagnostic/code59-native-lint-20261114\n' +
    '      - fix/code60-full-qa-html-mode-20261116\n');
  assert.equal(push(context), '    paths: [app/**, .github/continuum/**, .github/workflows/code58-fold7-combined-context-qa.yml]\n' +
    '    branches:\n      - fix/code59-context-complete-20261114\n' +
    '      - fix/code60-full-qa-html-mode-20261116\n');
  assert.doesNotMatch(push(full) + push(context), /- integrate\/continuum-command03-android-20261115/);
  assert.doesNotMatch(workflow, /^  (?:push|pull_request|workflow_run|schedule):/m);
});

test('Code60 retains all five full jobs and gates every checkout by manual event, exact ref and expected SHA', () => {
  const jobs = [...full.slice(full.indexOf('\njobs:\n')).matchAll(/^  ([a-z][a-z0-9-]+):$/gm)].map(match => match[1]);
  assert.deepEqual(jobs, ['unsigned-arm64', 'renderer-preflight', 'full-android', 'node-backend', 'native-runtime']);
  for (const [text, count] of [[full, 5], [context, 1]]) {
    assert.equal((text.match(/test "\$GITHUB_EVENT_NAME" = workflow_dispatch/g) || []).length, count);
    assert.equal((text.match(/test "\$EXPECTED_SOURCE_SHA" = "\$GITHUB_SHA"/g) || []).length, count);
    assert.equal((text.match(/\[\[ "\$EXPECTED_SOURCE_SHA" =~ \^\[0-9a-f\]\{40\}\$ \]\]/g) || []).length, count);
    assert.equal(text.split(`test "$GITHUB_REF" = '${ref}'`).length - 1, count);
    assert.ok(text.includes(`github.event_name == 'workflow_dispatch' && github.ref == '${ref}'`));
    assert.ok(text.includes(`github.ref == '${ref}' || github.ref == '${repairRef}'`));
    assert.ok(text.includes(`&& '60' || '59'`));
    assert.ok(text.includes(`&& '0.3.59' || '0.3.58'`));
    assert.match(text, /EXPECTED_SOURCE_SHA: \$\{\{ inputs\.expected_sha \|\| \(github\.ref == 'refs\/heads\/fix\/code60-full-qa-html-mode-20261116' && github\.sha\) \|\| '' \}\}/);
    assert.match(text, /github.event_name == 'push' && github.ref == 'refs\/heads\/fix\/code60-full-qa-html-mode-20261116'/);
    assert.doesNotMatch(text, /run:[\s\S]*\$\{\{\s*inputs\.expected_sha\s*\}\}/);
  }
});

test('Code60 does not substitute targeted local tests for complete QA or weaken original gates', () => {
  assert.match(full, /name: Full unfiltered Android and UI regression suite/);
  assert.match(full, /name: Full Android lint/);
  assert.match(full, /--strict-skips/);
  assert.match(full, /required=required_host_cases\(60\)/);
  assert.match(full, /junit\(Path\('app\/build\/test-results\/testDebugUnitTest'\), 5832/);
  assert.match(full, /-PcontinuumOwnerArm64=true -PcontinuumStandaloneVersionCode="\$RELEASE_CODE"/);
  assert.match(full, /"\$tools\/zipalign" -c -P 16/);
  assert.match(full, /Refusing unexpectedly signed test artifact/);
  assert.match(signer, /\n  verify_code59_evidence\n/);
  assert.match(signer, /\n  verify_code60_evidence\n/);
  const code60 = signer.slice(signer.indexOf('verify_code60_evidence()'), signer.indexOf('# 1. Environment'));
  assert.equal((code60.match(/'\["workflow_dispatch"\]'/g) || []).length, 2);
  assert.doesNotMatch(code60, /'\["push/);
  for (const guard of ['CHECKOUT_SHA_MISMATCH', 'CHECKOUT_NOT_CLEAN', 'CHECKOUT_TREE_INVALID',
    'CODE60_RECEIPT_OR_APK_INVALID', 'CODE60_NODE_PROOF_INVALID', 'QA_ATTEMPT_CHANGED']) assert.ok(code60.includes(guard));
  assert.ok(code60.includes('code59_signing_evidence.py "$tmp" "$source_sha" "$source_tree" "$tools" 60'));
});

test('exact owner approval and protected required reviewer remain mandatory before key use', () => {
  assert.match(workflow, /owner_approved_sha:\n[\s\S]*?required: true/);
  assert.match(workflow, /needs: preflight/);
  const preflight = workflow.slice(workflow.indexOf('  preflight:'), workflow.indexOf('  release-original-signed:'));
  assert.doesNotMatch(preflight, /environment:|secrets\./);
  assert.match(workflow, /environment: continuum-original-signing/);
  assert.equal((workflow.match(/secrets\./g) || []).length, 2);
  for (const check of ['APPROVED_SHA_DIFFERS_FROM_SOURCE', 'SOURCE_DIFFERS_FROM_RUN_COMMIT',
    'ENVIRONMENT_REQUIRED_OWNER_REVIEWER_MISSING', 'ENVIRONMENT_BRANCH_POLICY_NOT_EXACT']) {
    assert.ok(signer.indexOf(check) > 0);
    assert.ok(signer.indexOf(check) < signer.indexOf('ORIGINAL_SECRETS'));
  }
  assert.match(signer, /\.branch_policies \| length==1 and \.\[0\]\.type=="branch" and \.\[0\]\.name==\$b/);
  assert.match(signer, /owner_login="Ullmann27"/);
  assert.match(signer, /fingerprint="6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23"/);
  assert.match(signer, /pkg="at\.heinz\.deviceai\.continuumlocal"/);
});

test('Code60 upgrade baseline pins the published original Code59 bytes and identity', () => {
  for (const literal of [
    'baseline_tag="continuum-local-code59-79c16e57aeb7e0cf4a851e921d7441f7f6384fdf"',
    'baseline_digest="7fa6387e68fc4be9ee46079aac1b95feb67ab2656fec3c359128c58e6a82210e"',
    'baseline_size=104438996', 'baseline_code=59', 'baseline_version=0.3.58',
    'fail CODE59_BASELINE_CHANGED', 'CHECKPOINT=PINNED_CODE59_BASELINE_VERIFIED',
    'BASELINE_SIZE_MISMATCH', 'BASELINE_SHA_MISMATCH', 'BASELINE_CERTIFICATE_MISMATCH',
    'BASELINE_V2_MISSING', 'BASELINE_PACKAGE_MISMATCH', 'GITHUB_READBACK_SHA_MISMATCH',
    'PUBLIC_DOWNLOAD_SHA_MISMATCH', 'PUBLIC_DOWNLOAD_CERT_MISMATCH']) assert.ok(signer.includes(literal), literal);
  assert.ok(signer.includes('[[ "$code" == 55 || "$code" == 59 ]] || [[ "$code" == 60 ]]'));
  assert.ok(signer.includes('CODE55_BASELINE_CHANGED'));
  assert.ok(signer.includes('CODE51_BASELINE_CHANGED'));
});

test('Node evidence still rejects fabricated physical-device success and missing checks', () => {
  const outside = ['gradle', 'lint', 'apk', 'fold7', 'model-inference'].map(id => ({
    id, status: 'NOT_RUN', reason: 'OUTSIDE_SELECTED_HOST_SCOPE', severity: 'P0'}));
  const report = {checks: [{id: 'full-node'}, ...outside]};
  assert.deepEqual(selectChecks(report, ['full-node']), [{id: 'full-node'}]);
  assert.throws(() => selectChecks({checks: report.checks.slice(1)}, ['full-node']), /proof rejected/);
  assert.throws(() => selectChecks({checks: report.checks.map(check => check.id === 'fold7'
    ? {...check, status: 'PASS'} : check)}, ['full-node']), /proof rejected/);
});
