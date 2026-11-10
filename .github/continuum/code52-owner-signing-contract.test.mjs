import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import os from 'node:os';
import {spawnSync} from 'node:child_process';

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

const readbackMarker = '# 8. ';
const sourceSha = '17491f4d8103f0e55836369333fab07e80e6e9c2';
const releaseTag = `continuum-local-code52-${sourceSha}`;
const apkDigest = '9c811cb2f6ce69825b67f536f75bad3ec427b45b5dc63b3827c26d0891433f27';
const apkName = `Continuum-Lokal-${apkDigest}.apk`;
const previousTag = 'continuum-local-code51-6c23d295e2fca2d4e5950a866547733ec5ff22b0';
const draftUrl = 'https://github.com/Ullmann27/device-ai/releases/tag/untagged-fixture';

function readback({listed, metadata = {}, latest = previousTag, unreadable = false} = {}) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'code52-readback-'));
  try {
    const draft = {
      id: 440706901, tag_name: releaseTag, target_commitish: sourceSha,
      draft: true, prerelease: false, html_url: draftUrl,
      assets: [{name: apkName, digest: `sha256:${apkDigest}`, size: 102586623}],
      ...metadata,
    };
    fs.writeFileSync(path.join(dir, 'list.json'), JSON.stringify(listed ?? [[{id: 1, tag_name: 'older'}], [draft]]));
    fs.writeFileSync(path.join(dir, 'draft.json'), JSON.stringify(draft));
    fs.writeFileSync(path.join(dir, 'latest.txt'), latest);
    fs.writeFileSync(path.join(dir, 'gh'), `#!/usr/bin/env bash
set -eu
printf '%s\\n' "$*" >> "$FIXTURE_DIR/calls.txt"
case "$*" in
  'api --paginate --slurp repos/Ullmann27/device-ai/releases?per_page=100') cat "$FIXTURE_DIR/list.json" ;;
  'api repos/Ullmann27/device-ai/releases/440706901')
    [ "$UNREADABLE" = false ] || exit 1
    cat "$FIXTURE_DIR/draft.json" ;;
  'api repos/Ullmann27/device-ai/releases/latest --jq .tag_name') cat "$FIXTURE_DIR/latest.txt" ;;
  *) printf '%s\\n' 'gh: Not Found (HTTP 404)' >&2; exit 1 ;;
esac
`, {mode: 0o700});
    const offset = script.indexOf(readbackMarker);
    assert.ok(offset >= 0);
    const section = script.slice(offset);
    const setup = `set -Eeuo pipefail
fail() { printf 'CODE52_SIGN_%s\\n' "$1" >&2; exit 69; }
repo=Ullmann27/device-ai
source_sha=${sourceSha}
tag=${releaseTag}
name=${apkName}
digest=${apkDigest}
size=102586623
latest_tag=${previousTag}
`;
    const result = spawnSync('bash', ['-c', setup + section], {
      encoding: 'utf8', timeout: 5000,
      env: {...process.env, PATH: `${dir}:${process.env.PATH}`, FIXTURE_DIR: dir, UNREADABLE: String(unreadable)},
    });
    return {...result, calls: fs.readFileSync(path.join(dir, 'calls.txt'), 'utf8')};
  } finally {
    fs.rmSync(dir, {recursive: true, force: true});
  }
}

test('unpublished draft validates by ID when tag endpoint returns 404', () => {
  const result = readback();
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /STATUS=SIGNED_DRAFT_VERIFIED_NOT_PUBLISHED/);
  assert.ok(result.stdout.includes(`DRAFT_RELEASE_URL=${draftUrl}`));
  assert.doesNotMatch(result.calls, /releases\/tags\//);
});

for (const [title, options, error] of [
  ['missing draft', {listed: [[]]}, 'DRAFT_IDENTITY_AMBIGUOUS'],
  ['duplicate tag', {listed: [[{id: 1, tag_name: releaseTag}, {id: 2, tag_name: releaseTag}]]}, 'DRAFT_IDENTITY_AMBIGUOUS'],
  ['invalid ID', {listed: [[{id: 'not-an-id', tag_name: releaseTag}]]}, 'DRAFT_ID_INVALID'],
  ['unreadable ID', {unreadable: true}, 'DRAFT_READBACK_UNREADABLE'],
  ['changed source', {metadata: {target_commitish: '0'.repeat(40)}}, 'DRAFT_RELEASE_METADATA_MISMATCH'],
  ['published state', {metadata: {draft: false}}, 'DRAFT_RELEASE_METADATA_MISMATCH'],
  ['changed asset', {metadata: {assets: [{name: apkName, digest: 'sha256:wrong', size: 102586623}]}}, 'DRAFT_RELEASE_METADATA_MISMATCH'],
  ['changed latest', {latest: releaseTag}, 'DRAFT_CHANGED_LATEST_RELEASE'],
]) {
  test(`draft readback rejects ${title}`, () => {
    const result = readback(options);
    assert.equal(result.status, 69, result.stderr);
    assert.ok(result.stderr.includes(error), result.stderr);
    assert.doesNotMatch(result.stdout, /STATUS=SIGNED_DRAFT_VERIFIED_NOT_PUBLISHED/);
  });
}

