import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const path = resolve(root, 'scripts/continuum-fold7-update.sh');
const script = readFileSync(path, 'utf8');

test('one-command Fold7 update wrapper has valid Bash syntax', () => {
  const checked = spawnSync('bash', ['-n', path], {
    encoding: 'utf8', timeout: 10000
  });
  assert.equal(checked.status, 0, checked.stderr);
});

test('versionCode must increase from installed local version 26', () => {
  for (const args of [
    [], ['26'], ['25'], ['0'], ['main'], ['27', 'install'],
    ['27', '--offline', 'extra'], ['10000'], ['-1'], ['27;exit']
  ]) {
    const result = spawnSync('bash', [path, ...args], {
      encoding: 'utf8', timeout: 10000
    });
    assert.notEqual(result.status, 0, `Unexpectedly accepted ${args.join(' ')}`);
  }
});

test('wrapper cannot auto-install APK or delete owner data', () => {
  assert.doesNotMatch(script, /\badb\s+install\b|\bpm\s+(install|uninstall|clear)\b/);
  assert.doesNotMatch(script, /\brm\s+-r[f]?\b|\bkeytool\s+-genkeypair\b/);
  assert.match(script, /APK_PREPARED_NOT_INSTALLED/);
  assert.match(script, /continuum-fold7-standalone-apk\.sh build/);
});

test('release requires persistent owner P12 and certificate identity', () => {
  assert.match(script, /continuum-local\.p12/);
  assert.match(script, /continuum-local-cert-sha256\.txt/);
  assert.match(script, /NEVER regenerate/);
  assert.match(script, /CONTINUUM_MENTOR_SIGNING_PASSWORD CONTINUUM_MENTOR_SIGNING_FILE/);
});

test('wrapper is pinned to official git origin and a clean non-production branch', () => {
  assert.match(script, /Ullmann27\/device-ai\.git/);
  assert.match(script, /git status --porcelain --untracked-files=normal/);
  assert.match(script, /main\|master\|production\|release\|prod/);
  assert.match(script, /git pull --ff-only/);
  assert.match(script, /GIT_TERMINAL_PROMPT=0/);
});

test('full Android regression runs before owner signing', () => {
  const qa = script.indexOf('bash scripts/continuum-fusion-full-regression.sh');
  const apk = script.indexOf('bash scripts/continuum-fold7-standalone-apk.sh build');
  assert.ok(qa > 0 && apk > qa);
  assert.match(script, /CONTINUUM_FUSION_REGRESSION_OUTPUT/);
  assert.match(script, /CONTINUUM_UPDATE_FULL_QA_PASS/);
});

test('real Kotlin and Node cross-runtime parity is required', () => {
  for (const call of [
    'cre-parity.mjs prepare',
    'contrastive-parity.mjs prepare',
    'cre-parity.mjs verify',
    'contrastive-parity.mjs verify'
  ]) assert.ok(script.includes(call), call);
});

test('wrapper never prompts for or records the keystore password itself', () => {
  assert.doesNotMatch(script, /read -r -s -p|storepass:pass|CONTINUUM_LOCAL_SIGNING_PASSWORD=/);
  assert.match(script, /bash scripts\/continuum-fold7-standalone-apk\.sh build/);
});
