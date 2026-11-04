import test from 'node:test';
import assert from 'node:assert/strict';
import {copyFileSync, existsSync, mkdirSync, mkdtempSync, rmSync, writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {dirname, join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const script = 'scripts/render-continuum-owner-continuous-qa.sh';
const branch = 'integration/continuum-all-systems-apk-qa-20261102';
const ownerService = 'continuum-owner-v23-release-preflight';
const ordinaryService = 'continuum-unified-research-apk-20261102';
const host = ownerService + '.onrender.com';
const signingKeys = [
  'CONTINUUM_LOCAL_SIGNING_FILE', 'CONTINUUM_LOCAL_SIGNING_PASSWORD',
  'CONTINUUM_LOCAL_SIGNING_P12_BASE64', 'CONTINUUM_MENTOR_SIGNING_FILE',
  'CONTINUUM_MENTOR_SIGNING_PASSWORD', 'CONTINUUM_MENTOR_SIGNING_P12_BASE64'
];

function runPreflight(t, {mode = '0', version, overrides = {}} = {}) {
  const scratch = mkdtempSync(join(tmpdir(), 'continuum-qa-preflight-'));
  t.after(() => rmSync(scratch, {recursive: true, force: true}));
  const checkout = join(scratch, 'checkout');
  const bin = join(scratch, 'bin');
  const canary = join(scratch, 'network-attempted');
  mkdirSync(join(checkout, 'scripts'), {recursive: true});
  mkdirSync(bin);
  copyFileSync(join(root, script), join(checkout, script));
  // Execute the real QA entry point, but stop at its first download. The test
  // never invokes a remote service, a Gradle build, or a signing operation.
  writeFileSync(join(bin, 'curl'),
    '#!/bin/sh\nprintf "download-attempted\\n" > "$CONTINUUM_PREFLIGHT_CANARY"\nexit 97\n',
    {mode: 0o700});
  const env = {
    ...process.env,
    GIT_CONFIG_NOSYSTEM: '1',
    GIT_CONFIG_GLOBAL: '/dev/null',
    CONTINUUM_OWNER_RELEASE_QA: mode,
    RENDER_SERVICE_NAME: mode === '1' ? ownerService : ordinaryService,
    CONTINUUM_RELEASE_HOST: host,
    CONTINUUM_PREFLIGHT_CANARY: canary,
    PATH: bin + ':' + process.env.PATH
  };
  for (const key of [...signingKeys, 'CONTINUUM_RELEASE_VERSION_CODE',
    'GIT_DIR', 'GIT_WORK_TREE', 'GIT_INDEX_FILE', 'BASH_ENV', 'ENV']) delete env[key];
  const git = args => {
    const result = spawnSync('git', [
      '-c', 'core.hooksPath=/dev/null', '-c', 'commit.gpgSign=false',
      '-c', 'user.name=Continuum QA Fixture', '-c', 'user.email=qa@example.invalid',
      ...args
    ], {cwd: checkout, env, encoding: 'utf8', timeout: 10_000});
    assert.equal(result.status, 0, result.stderr);
    return result.stdout.trim();
  };
  git(['init', '-q', '-b', branch]);
  git(['add', script]);
  git(['commit', '-q', '-m', 'Isolated QA preflight fixture']);
  env.CONTINUUM_RELEASE_APPROVED_SHA = git(['rev-parse', 'HEAD']);
  if (version !== undefined) env.CONTINUUM_RELEASE_VERSION_CODE = version;
  Object.assign(env, overrides);
  const result = spawnSync('bash', [script], {
    cwd: checkout, env, encoding: 'utf8', timeout: 10_000
  });
  assert.equal(result.error, undefined, String(result.error));
  return {
    ...result,
    downloadAttempted: existsSync(canary),
    workspaceCreated: existsSync(join(checkout, '.continuum-render-build')),
    logsCreated: existsSync(join(checkout, 'continuum-all-systems-logs'))
  };
}

const invalidVersions = [
  ['missing', undefined], ['empty', ''], ['below the supported range', '38'],
  ['above the supported range', '10000'], ['fractional', '39.5'],
  ['negative', '-1'], ['leading zero', '039'], ['whitespace', ' 39'],
  ['non-numeric', 'current'], ['overflowing shell integer', '18446744073709551655']
];

for (const mode of ['0', '1']) {
  const name = mode === '1' ? 'owner' : 'ordinary';
  for (const [description, version] of invalidVersions) {
    test(name + ' QA rejects ' + description + ' version before setup or downloads', t => {
      const result = runPreflight(t, {mode, version});
      assert.equal(result.status, 69, result.stderr);
      assert.match(result.stderr, /CONTINUUM_QA_BLOCKED:/);
      assert.equal(result.downloadAttempted, false, 'invalid version reached a download');
      assert.equal(result.workspaceCreated, false, 'invalid version created a build workspace');
      assert.equal(result.logsCreated, false, 'invalid version created QA output');
      assert.doesNotMatch(result.stderr, /unbound variable|value too great for base/);
    });
  }
  for (const version of ['39', '42', '9999']) {
    test(name + ' QA still admits canonical version ' + version + ' to setup', t => {
      const result = runPreflight(t, {mode, version});
      assert.equal(result.status, 69, result.stderr);
      assert.equal(result.downloadAttempted, true, result.stderr);
      assert.match(result.stderr, /Pinned JDK download failed/);
      assert.doesNotMatch(result.stdout, /PRESIGN_QA_PASS|SIGNED_STAGED/);
    });
  }
}

for (const [name, mode, overrides, message] of [
  ['owner source approval', '1', {CONTINUUM_RELEASE_APPROVED_SHA: '0'.repeat(40)}, /Owner QA requires/],
  ['owner publisher', '1', {RENDER_SERVICE_NAME: 'wrong-service'}, /Owner QA requires/],
  ['owner pinned host', '1', {CONTINUUM_RELEASE_HOST: 'example.invalid'}, /Owner QA requires/],
  ['ordinary isolated service', '0', {RENDER_SERVICE_NAME: 'wrong-service'}, /Only isolated QA service/],
  ['QA mode', '2', {}, /Invalid owner QA mode/],
  ['QA signing exclusion', '0', {CONTINUUM_LOCAL_SIGNING_FILE: 'fixture-not-a-key'}, /Signing material forbidden/]
]) {
  test(name + ' gate remains enforced for a valid version', t => {
    const result = runPreflight(t, {mode, version: '42', overrides});
    assert.equal(result.status, 69, result.stderr);
    assert.match(result.stderr, message);
    assert.equal(result.downloadAttempted, false);
  });
}
