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
  assert.ok(workflow.indexOf('  preflight:') < workflow.indexOf('  sign-draft-original:'));
  const preflight = workflow.slice(workflow.indexOf('  preflight:'), workflow.indexOf('  sign-draft-original:'));
  assert.doesNotMatch(preflight, /secrets\./);
  assert.equal([...workflow.matchAll(/secrets\./g)].length, 2);
});

test('unverified or unsigned Code52 source can never be published', () => {
  assert.match(script, /qa_branch="code52\/grok-release-candidate-0351-20261110"/);
  assert.doesNotMatch(script, /qa_branch="code52\/cinema-native-qa-review-fixes-20261110"/);
  assert.match(script, /qa_workflow_path="\.github\/workflows\/continuum-code52-cinematic-qa\.yml"/);
  assert.match(script, /artifact="Code52-CINEMA-UNSIGNED-NOT-INSTALLABLE"/);
  assert.match(script, /unsigned_name="Continuum-Code52-CINEMA-UNSIGNED-NOT-INSTALLABLE\.apk"/);
  assert.doesNotMatch(script, /qa_branch="chatgpt\/code52-unified-integration-20261109"/);
  assert.doesNotMatch(script, /continuum-code52-integration-qa\.yml/);
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

test('Cinema2030 signer accepts only pinned source and QA-run pair', () => {
  assert.ok(script.includes("cinema_source_sha=\"0df55d7c8d5cae8a53bf1a43e26651f902881e78\""), "cinema_source_sha=\"0df55d7c8d5cae8a53bf1a43e26651f902881e78\"");
  assert.ok(script.includes("if [[ \"$source_sha\" == \"$cinema_source_sha\" ]]; then"), "if [[ \"$source_sha\" == \"$cinema_source_sha\" ]]; then");
  assert.ok(script.includes("[[ \"$qa_run\" == \"42742886395\" ]] || fail CINEMA_QA_RUN_MISMATCH"), "[[ \"$qa_run\" == \"42742886395\" ]] || fail CINEMA_QA_RUN_MISMATCH");
  assert.ok(script.includes("qa_branch=\"chatgpt/code52-cinema-prism-pixel-fix-20261110\""), "qa_branch=\"chatgpt/code52-cinema-prism-pixel-fix-20261110\"");
  assert.ok(script.includes("qa_workflow_path=\".github/workflows/continuum-code52-cinematic-qa.yml\""), "qa_workflow_path=\".github/workflows/continuum-code52-cinematic-qa.yml\"");
  assert.ok(script.includes("artifact=\"Code52-CINEMA-UNSIGNED-NOT-INSTALLABLE\""), "artifact=\"Code52-CINEMA-UNSIGNED-NOT-INSTALLABLE\"");
  assert.ok(script.includes("unsigned_name=\"Continuum-Code52-CINEMA-UNSIGNED-NOT-INSTALLABLE.apk\""), "unsigned_name=\"Continuum-Code52-CINEMA-UNSIGNED-NOT-INSTALLABLE.apk\"");
  assert.ok(script.includes('qa_branch="chatgpt/code52-unified-integration-20261109"'));
  assert.match(script, /APPROVED_SHA_DIFFERS_FROM_SOURCE/);
  assert.match(script, /--draft --latest=false/);
});

test('original certificate bound and no secret echoed', () => {
  assert.match(script, /fingerprint="6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23"/);
  assert.match(script, /pkg="at\.heinz\.deviceai\.continuumlocal"/);
  assert.match(script, /ORIGINAL_CERTIFICATE_MISMATCH/);
  assert.match(script, /SIGNED_APK_CERTIFICATE_MISMATCH/);
  assert.match(script, /DRAFT_RELEASE_METADATA_MISMATCH/);
  assert.ok(script.indexOf('ENVIRONMENT_REQUIRED_OWNER_REVIEWER_MISSING') <
    script.indexOf('ORIGINAL_P12_BASE64_INVALID'));
  assert.ok(script.indexOf('--draft --latest=false') <
    script.indexOf('GITHUB_READBACK_SHA_MISMATCH'));
  assert.doesNotMatch(script, /^set -x$/m);
  const safeDecode = 'printf \'%s\' "$ORIGINAL_P12" | base64 --decode > "$tmp/original.p12"';
  assert.equal(script.split(safeDecode).length, 2, 'exact private-file-only P12 decode');
  assert.doesNotMatch(script.replace(safeDecode, ''), /\b(echo|printf|cat|tee)\b[^\n]*\$\{?(?:ORIGINAL_P12|ORIGINAL_PASSWORD|SIGN_PASS)\b/);
});

test('signed APK never publishes automatically; owner retains a separate release decision', () => {
  assert.match(script, /gh release create[^\n]*--draft --latest=false/);
  assert.match(script, /CHECKPOINT=SIGNED_DRAFT_READBACK_VERIFIED/);
  assert.match(script, /STATUS=SIGNED_DRAFT_VERIFIED_NOT_PUBLISHED/);
  assert.match(script, /DRAFT_RELEASE_METADATA_MISMATCH/);
  assert.match(script, /DRAFT_CHANGED_LATEST_RELEASE/);
  assert.doesNotMatch(script, /\bgh release edit\b/);
  assert.doesNotMatch(script, /--draft=false|--latest=true|gh release publish/);
  assert.match(workflow, /  sign-draft-original:/);
  assert.doesNotMatch(workflow, /  release-original-signed:/);
});
