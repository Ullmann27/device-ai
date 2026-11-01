import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const legacy = readFileSync(resolve(root, 'scripts/render-continuum-m8-owner-release.sh'), 'utf8');
const ownerPath = resolve(root, 'scripts/render-continuum-v37-owner-release.sh');
const qaPath = resolve(root, 'scripts/render-continuum-v37-qa.sh');
const owner = readFileSync(ownerPath, 'utf8');
const qa = readFileSync(qaPath, 'utf8');

test('both code37 build entrypoints parse as valid bash', () => {
  for (const script of [ownerPath, qaPath, resolve(root, 'scripts/render-continuum-m8-owner-release.sh')]) {
    const result = spawnSync('bash', ['-n', script], { encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr);
  }
});

test('existing publisher delegates only explicitly selected code37', () => {
  assert.match(legacy, /CONTINUUM_RELEASE_VERSION_CODE:-.*== "37"/);
  assert.match(legacy, /exec bash "\$root\/scripts\/render-continuum-v37-owner-release.sh"/);
  assert.match(legacy, /This pinned candidate accepts only versionCode 36/);
});

test('code37 keeps the original package, certificate and pinned Render publisher', () => {
  assert.match(owner, /at\.heinz\.deviceai\.continuumlocal/);
  assert.match(owner, /6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23/);
  assert.match(owner, /continuum-owner-v23-release-preflight\.onrender\.com/);
  assert.match(owner, /CONTINUUM_RELEASE_APPROVED_SHA/);
  assert.match(owner, /Only the pinned Render owner update site may publish/);
  assert.match(owner, /Original Continuum Lokal signing material unavailable/);
  assert.match(owner, /Wrong signing certificate/);
  assert.match(owner, /Built APK signer differs/);
  assert.match(owner, /Verified using v2 scheme/);
  assert.match(owner, /HEAD changed during release build/);
});

test('v37 requires exactly code37, matching Gradle, AAPT, proof and manifest', () => {
  assert.match(owner, /This pinned candidate accepts only versionCode 37/);
  assert.match(owner, /bash scripts\/render-continuum-v37-qa\.sh/);
  assert.match(owner, /versionCode: 37/);
  assert.match(owner, /name !== '0\.3\.36'/);
  assert.match(owner, /version=0\.3\.36/);
  assert.match(owner, /badging/);
  assert.match(owner, /latest\.json/);
  assert.match(owner, /sha256sum/);
  assert.match(qa, /continuumStandaloneVersionCode=37/);
  assert.match(qa, /CONTINUUM_PROOF_VERSION=37/);
  assert.match(qa, /version_code=37/);
  assert.match(qa, /:app:testDebugUnitTest :app:lintDebug/);
  assert.match(qa, /:app:assembleRelease :app:bundleRelease/);
  assert.match(qa, /BUILD_AND_TEST_GREEN_UNSIGNED_NOT_INSTALLABLE/);
  assert.match(qa, /apk_signed=FALSE/);
  assert.match(qa, /apk_published=FALSE/);
});

test('unapproved code37 cannot reach signing or deployment', () => {
  const env = { ...process.env,
    CONTINUUM_RELEASE_VERSION_CODE: '37',
    CONTINUUM_RELEASE_APPROVED_SHA: '',
    CONTINUUM_RELEASE_HOST: '',
    CONTINUUM_LOCAL_SIGNING_P12_BASE64: '',
    CONTINUUM_LOCAL_SIGNING_PASSWORD: '',
    CONTINUUM_MENTOR_SIGNING_FILE: '',
    CONTINUUM_MENTOR_SIGNING_PASSWORD: '',
    CONTINUUM_MENTOR_SIGNING_P12_BASE64: ''
  };
  const run = spawnSync('bash', [resolve(root, 'scripts/render-continuum-m8-owner-release.sh')], {
    cwd: root, env, encoding: 'utf8', timeout: 20000
  });
  assert.equal(run.status, 69, run.stderr);
  assert.match(run.stderr, /Exact source SHA not approved/);
  assert.doesNotMatch(run.stdout, /SIGNED_STAGED|BUILD_SUCCESSFUL/);
});

test('code37 QA refuses any signing credentials and does not publish APK files', () => {
  assert.match(qa, /QA must never receive private signing credentials/);
  assert.match(qa, /continuum-m8-render-qa\/STATUS\.txt/);
  assert.doesNotMatch(qa, /Continuum-Lokal-\$digest\.apk/);
  assert.doesNotMatch(qa, /approved: true/);
});

test('no version downgrade or unverified release to the owner host', () => {
  assert.match(owner, /\[\[ "\$version" == "37" \]\]/);
  assert.match(owner, /CONTINUUM_RELEASE_HOST/);
  assert.match(owner, /apk_signer.*expected_signer/);
  assert.match(owner, /package: name=/);
  assert.match(owner, /'OWNER_RELEASE'/);
  assert.match(owner, /approved: true/);
});
