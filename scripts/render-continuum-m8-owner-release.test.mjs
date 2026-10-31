import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const build = resolve(root, 'scripts/render-continuum-m8-owner-release.sh');
const script = readFileSync(build, 'utf8');
const qa = readFileSync(resolve(root, 'scripts/render-continuum-m8-qa.sh'), 'utf8');

test('owner release entry point is valid bash', () => {
  const syntax = spawnSync('bash', ['-n', build], { encoding: 'utf8' });
  assert.equal(syntax.status, 0, syntax.stderr);
});

test('owner approval is mandatory before any build or signing', () => {
  const run = spawnSync('bash', [build], {
    cwd: root,
    env: {
      ...process.env,
      CONTINUUM_RELEASE_APPROVED_SHA: '',
      CONTINUUM_RELEASE_HOST: '',
      CONTINUUM_LOCAL_SIGNING_P12_BASE64: '',
      CONTINUUM_LOCAL_SIGNING_PASSWORD: ''
    },
    encoding: 'utf8',
    timeout: 20_000
  });
  assert.equal(run.status, 69);
  assert.match(run.stderr, /Exact source SHA not approved/);
  assert.doesNotMatch(run.stdout, /SIGNED_STAGED|BUILD_SUCCESSFUL/);
});

test('release refuses a different Render host and service name', () => {
  assert.match(script, /continuum-owner-v23-release-preflight\.onrender\.com/);
  assert.match(script, /CONTINUUM_RELEASE_HOST/);
  assert.match(script, /RENDER_SERVICE_NAME/);
  assert.match(script, /Only the pinned Render owner update site may publish/);
});

test('release pins the installed Continuum Lokal app and signer, not Mentor Pair', () => {
  assert.match(script, /at\.heinz\.deviceai\.continuumlocal/);
  assert.match(script, /6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23/);
  assert.match(script, /CONTINUUM_MENTOR_SIGNING/);
  assert.match(script, /Wrong signing certificate/);
  assert.match(script, /Built APK signer differs/);
  assert.match(script, /Verified using v2 scheme/);
  assert.match(script, /badging/);
  assert.match(script, /:app:assembleRelease/);
  assert.match(script, /:app:lintRelease/);
  assert.doesNotMatch(script, /mentorpaired/);
});

test('release requires approved v29 and performs real SHA-bound QA before signing', () => {
  assert.match(script, /This pinned candidate accepts only versionCode 29/);
  assert.match(script, /bash scripts\/render-continuum-m8-qa\.sh/);
  assert.match(script, /HEAD changed during QA/);
  assert.match(qa, /:app:testDebugUnitTest :app:lintDebug/);
  assert.match(qa, /:app:assembleRelease/);
  assert.match(qa, /BUILD_AND_TEST_GREEN_UNSIGNED_NOT_INSTALLABLE/);
});

test('manifest is written only for a verified, originally signed APK at the pinned host', () => {
  assert.match(script, /CONTINUUM_OWNER_RENDER_SIGNED_STAGED_FOR_PINNED_HOST/);
  assert.match(script, /latest\.json/);
  assert.match(script, /'OWNER_RELEASE'/);
  assert.match(script, /'approved'|approved: true/);
  assert.match(script, /sha256sum/);
  assert.match(script, /Continuum-Lokal-/);
  assert.match(script, /sourceCommit: sha/);
  assert.match(script, /signerSha256:/);
  assert.match(script, /versionCode: 29/);
  assert.match(script, /writeFileSync\(target/);
});

test('no public release proceeds without private original key', () => {
  const head = spawnSync('git', ['rev-parse', '--verify', 'HEAD'], {
    cwd: root, encoding: 'utf8'
  });
  assert.equal(head.status, 0);
  const run = spawnSync('bash', [build], {
    cwd: root,
    env: {
      ...process.env,
      CONTINUUM_RELEASE_APPROVED_SHA: head.stdout.trim(),
      CONTINUUM_RELEASE_HOST: 'continuum-owner-v23-release-preflight.onrender.com',
      RENDER_SERVICE_NAME: 'continuum-owner-v23-release-preflight',
      CONTINUUM_RELEASE_VERSION_CODE: '29',
      CONTINUUM_LOCAL_SIGNING_P12_BASE64: '',
      CONTINUUM_LOCAL_SIGNING_PASSWORD: '',
      CONTINUUM_MENTOR_SIGNING_FILE: '',
      CONTINUUM_MENTOR_SIGNING_PASSWORD: '',
      CONTINUUM_MENTOR_SIGNING_P12_BASE64: ''
    },
    encoding: 'utf8',
    timeout: 20_000
  });
  assert.equal(run.status, 69);
  assert.match(run.stderr, /Original Continuum Lokal signing material unavailable/);
  assert.doesNotMatch(run.stdout, /SIGNED_STAGED/);
});
