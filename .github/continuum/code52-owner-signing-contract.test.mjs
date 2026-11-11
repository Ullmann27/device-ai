import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
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

function pureDraftFunction(name) {
  const start = script.indexOf(`${name}() {\n`);
  assert.ok(start >= 0, `production function ${name} exists`);
  const end = script.indexOf('\n}\n', start);
  assert.ok(end > start, 'complete function body');
  const body = script.slice(start, end + 2);
  assert.doesNotMatch(body, /\bgh\b|ORIGINAL_|SIGN_PASS/);
  return body;
}

function runDraftFunction(name, args, input) {
  const result = spawnSync('bash', ['-c', `${pureDraftFunction(name)}\n${name} "$@"`, name, ...args], {
    input, encoding: 'utf8', timeout: 10000,
  });
  assert.ifError(result.error);
  assert.equal(result.signal, null);
  return result;
}

const draftSource = '050a57cb019dcc1b7fe6cfa2fb5c11176b048e05';
const draftTag = `continuum-local-code53-${draftSource}`;
const draftDigest = 'a'.repeat(64);
const draftName = `Continuum-Lokal-${draftDigest}.apk`;
const draftSize = 102701335;
const draftId = 531234567;
const draftUrlPrefix = 'https://github.com/Ullmann27/device-ai/releases/';
function draftFixture() {
  return {id: draftId, tag_name: draftTag, target_commitish: draftSource,
    html_url: `${draftUrlPrefix}tag/untagged-test-fixture`,
    draft: true, prerelease: false, assets: [
      {name: draftName, digest: `sha256:${draftDigest}`, size: draftSize},
      {name: 'BUILD-INFO.txt', digest: `sha256:${'b'.repeat(64)}`, size: 500},
    ]};
}
function selectDraft(pages) {
  return runDraftFunction('select_exact_draft_id', [draftTag, draftSource], pages.map(p => JSON.stringify(p)).join('\n'));
}
function verifyDraft(draft) {
  return runDraftFunction('verify_exact_draft_metadata',
    [String(draftId), draftTag, draftSource, draftName, draftDigest, String(draftSize), draftUrlPrefix], JSON.stringify(draft));
}

test('draft readback uses authenticated pagination and the selected release id', () => {
  assert.match(script, /gh api --paginate "repos\/\$repo\/releases\?per_page=100"/);
  assert.match(script, /gh api "repos\/\$repo\/releases\/\$draft_id"/);
  assert.doesNotMatch(script, /releases\/tags\/\$tag/);
  assert.match(script, /select_exact_draft_id "\$tag" "\$source_sha"/);
  assert.match(script, /verify_exact_draft_metadata "\$draft_id" "\$tag" "\$source_sha"/);
  assert.match(script, /\[\[ "\$latest_after" == "\$latest_tag" \]\]/);
  assert.ok(script.indexOf('GITHUB_READBACK_SIGNATURE_FAILED') < script.indexOf('gh api --paginate'));
});

test('exact draft is selected across pages and metadata verifies', () => {
  const unrelated = {...draftFixture(), id: draftId - 1, tag_name: 'another-tag', draft: false};
  const selected = selectDraft([[unrelated], [draftFixture()]]);
  assert.equal(selected.status, 0, selected.stderr);
  assert.equal(selected.stdout.trim(), String(draftId));
  const verified = verifyDraft(draftFixture());
  assert.equal(verified.status, 0, verified.stderr);
});

test('absent or duplicate tag never selects a draft', () => {
  for (const pages of [[], [[]], [[{...draftFixture(), tag_name: 'another-tag'}]],
    [[draftFixture()], [{...draftFixture(), id: draftId + 1}]]]) {
    assert.notEqual(selectDraft(pages).status, 0);
  }
});

test('published, prerelease and wrong-source releases are rejected during selection', () => {
  for (const mutation of [{draft: false}, {prerelease: true}, {target_commitish: 'main'},
    {target_commitish: 'd'.repeat(40)}, {id: null}, {id: '531234567'}, {id: 0}, {id: -1}, {id: 1.5}]) {
    assert.notEqual(selectDraft([[{...draftFixture(), ...mutation}]]).status, 0, JSON.stringify(mutation));
  }
});

test('release readback cannot substitute another id, tag, source or published state', () => {
  for (const mutation of [{id: draftId + 1}, {tag_name: 'another-tag'}, {target_commitish: 'main'},
    {target_commitish: 'd'.repeat(40)}, {draft: false}, {prerelease: true}]) {
    assert.notEqual(verifyDraft({...draftFixture(), ...mutation}).status, 0, JSON.stringify(mutation));
  }
});

test('release readback rejects altered APK digest, bytes or name', () => {
  for (const mutation of [{digest: `sha256:${'d'.repeat(64)}`}, {digest: null},
    {size: draftSize + 1}, {name: `Continuum-Lokal-${'c'.repeat(64)}.apk`}]) {
    const draft = draftFixture();
    Object.assign(draft.assets[0], mutation);
    assert.notEqual(verifyDraft(draft).status, 0, JSON.stringify(mutation));
  }
});

test('release readback requires exactly one APK including unexpected names', () => {
  for (const extraName of [draftName, 'unexpected.apk', 'unexpected.APK']) {
    const draft = draftFixture();
    draft.assets.push({...draft.assets[0], name: extraName});
    assert.notEqual(verifyDraft(draft).status, 0, extraName);
  }
  const withoutApk = draftFixture();
  withoutApk.assets.shift();
  assert.notEqual(verifyDraft(withoutApk).status, 0);
});

test('draft URL comes from validated HTTPS repository metadata', () => {
  for (const html_url of [null, '', 12,
    'http://github.com/Ullmann27/device-ai/releases/tag/untagged-test',
    'https://github.com/another-owner/device-ai/releases/tag/untagged-test',
    'https://github.com/Ullmann27/another-repo/releases/tag/untagged-test',
    'https://github.com/Ullmann27/device-ai-extra/releases/tag/untagged-test',
    `${draftUrlPrefix}tag/untagged-test\nPUBLISHED=true`,
    `${draftUrlPrefix}tag/untagged-test\t`,
    'https://github.com.evil.example/Ullmann27/device-ai/releases/tag/untagged-test']) {
    assert.notEqual(verifyDraft({...draftFixture(), html_url}).status, 0, String(html_url));
  }
  assert.ok(script.includes('draft_url="$(jq -er \'.html_url\' <<< "$draft_json")"'));
  assert.match(script, /verify_exact_draft_metadata[^\n]*"https:\/\/github\.com\/\$repo\/releases\/"/);
  assert.match(script, /printf 'DRAFT_RELEASE_URL=%s\\n/);
  assert.doesNotMatch(script, /DRAFT_RELEASE_URL=https:\/\/github\.com/);
});
