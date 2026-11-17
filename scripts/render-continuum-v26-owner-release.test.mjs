import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const file = path.join(root, 'scripts', 'render-continuum-v26-owner-release.sh');
const source = fs.readFileSync(file, 'utf8');

const headResult = spawnSync('git', ['rev-parse', '--verify', 'HEAD'], {
  cwd: root, encoding: 'utf8', timeout: 5000
});
assert.equal(headResult.status, 0, headResult.stderr);
const head = headResult.stdout.trim();
assert.match(head, /^[0-9a-f]{40}$/);

function blocked(env = {}) {
  const result = spawnSync('bash', [file], {
    cwd: root,
    encoding: 'utf8',
    timeout: 8000,
    env: {
      PATH: process.env.PATH ?? '/usr/bin:/bin',
      HOME: process.env.HOME ?? '/tmp',
      ...env
    }
  });
  assert.equal(result.status, 69, 'Signer must never start without complete local authorization');
  assert.equal(result.stdout.includes('CONTINUUM_RENDER_V26_SIGNED_READY'), false);
  return result.stderr;
}

test('Render v26 release script has valid Bash syntax', () => {
  const r = spawnSync('bash', ['-n', file], {encoding: 'utf8', timeout: 5000});
  assert.equal(r.status, 0, r.stderr);
});

test('No explicit matching owner SHA means fail closed before accessing signer', () => {
  assert.match(blocked(), /Exact v26 source approval missing or mismatched/);
});

test('Outdated, different 40-digit release SHA cannot authorize signing', () => {
  const other = head === '0'.repeat(40) ? '1'.repeat(40) : '0'.repeat(40);
  assert.match(blocked({CONTINUUM_OWNER_V26_APPROVED_SHA: other}),
    /Exact v26 source approval missing or mismatched/);
});

test('Correct SHA but missing original P12 cannot release a new APK', () => {
  assert.match(blocked({CONTINUUM_OWNER_V26_APPROVED_SHA: head}),
    /Original P12 secret unavailable|Tracked source content has changed|Untracked production source detected/);
});

test('Both original private P12 and password remain mandatory', () => {
  assert.match(source, /CONTINUUM_MENTOR_SIGNING_P12_BASE64/);
  assert.match(source, /CONTINUUM_MENTOR_SIGNING_PASSWORD/);
  assert.match(source, /Original P12 secret unavailable/);
  assert.match(source, /Original signer password unavailable/);
  assert.match(source, /CONTINUUM_OWNER_LOCAL_APPROVED_SHA/);
  assert.match(source, /CONTINUUM_OWNER_NEXT_VERSION_CODE=26/);
});

test('Legacy version25 source-pinning and obsolete QA approval removed', () => {
  assert.equal(source.includes('continuum-0324-live-source-gate.mjs'), false);
  assert.equal(source.includes('CONTINUUM_OWNER_0324_LIVE_APPROVED_BUILDER_SHA'), false);
  assert.equal(source.includes('signed_release_ref='), false);
  assert.equal(source.includes('Wrong owner versionCode'), false);
});

test('Real owner builder and signer/package/hash checks remain mandatory', () => {
  assert.match(source, /scripts\/continuum-local-owner-apk\.sh" build/);
  assert.match(source, /status=SIGNED_NOT_DEVICE_INSTALLED/);
  assert.match(source, /owner_signer_sha256=/);
  assert.match(source, /package=\$owner_package/);
  assert.match(source, /version_code=26/);
  assert.match(source, /apk_sha256=/);
  assert.match(source, /sha256sum --check --status/);
});

test('Previously signed owner v25 APK must be retained and authenticated', () => {
  assert.match(source, /f415e7858e8aeb278e1da9a70098db4827dbd55226376f0d127a1f3d231feeac/);
  assert.match(source, /versionCode='25'/);
  assert.match(source, /apksigner" verify --print-certs/);
  assert.match(source, /rollback_artifact=/);
});

test('Build never automatically installs, resets app or creates another signing key', () => {
  assert.equal(/\badb\s+install\b|\bpm\s+install\b|\bkeytool\s+-genkeypair\b/.test(source), false);
  assert.equal(/\bpm\s+uninstall\b|\bpm\s+clear\b/.test(source), false);
  assert.match(source, /SIGNED_BUILD_VERIFIED_NOT_DEVICE_INSTALLED/);
});
