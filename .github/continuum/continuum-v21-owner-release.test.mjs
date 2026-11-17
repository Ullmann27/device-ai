import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const read = name => readFileSync(resolve(root, name), 'utf8');
const dispatcher = read('scripts/render-mentor-preview.sh');
const recipe = read('scripts/render-mentor-owner-update-v21.sh');
const audit = read('scripts/continuum-v21-source-gate.mjs');

test('v21 is separate and earlier owner release recipes stay available', () => {
  for (const number of [18, 19, 20, 21])
    assert.match(dispatcher, new RegExp('^  ' + number + '\\)', 'm'));
  assert.match(dispatcher, /CONTINUUM_OWNER_V21_APPROVED_SHA/);
  assert.match(dispatcher, /exec bash scripts\/render-mentor-owner-update-v21\.sh/);
  assert.match(dispatcher, /OWNER_V21_BLOCKED/);
});

test('unknown owner or wrong approved revision cannot invoke signer', () => {
  const run = env => spawnSync('bash', ['scripts/render-mentor-preview.sh'], {
    cwd: root, encoding: 'utf8', timeout: 10000,
    env: { PATH: process.env.PATH, HOME: process.env.HOME,
      CONTINUUM_OWNER_RELEASE_VERSION: '21', ...env }
  });
  const missing = run({});
  assert.equal(missing.status, 69, missing.stderr);
  assert.match(missing.stderr, /OWNER_V21_BLOCKED/);
  const bogus = run({
    CONTINUUM_OWNER_V21_APPROVED_SHA: '0'.repeat(40),
    CONTINUUM_RELEASE_SHA: '0'.repeat(40)
  });
  assert.equal(bogus.status, 69, bogus.stderr);
});

test('inventory approval is separate from builder SHA approval', () => {
  const run = spawnSync(process.execPath, ['scripts/continuum-v21-source-gate.mjs'], {
    cwd: root, encoding: 'utf8', timeout: 10000,
    env: { PATH: process.env.PATH }
  });
  assert.equal(run.status, 69, run.stderr);
  assert.match(run.stderr, /OWNER_V21_BLOCKED/);
  assert.match(audit, /CONTINUUM_OWNER_V21_APPROVED_SOURCE_SHA/);
  assert.match(audit, /CONTINUUM_OWNER_V21_APPROVED_MANIFEST_SHA256/);
  assert.match(audit, /item\.sha256/);
  assert.match(audit, /git\('hash-object', '--', p\)/);
  assert.match(audit, /git\('ls-files', '-z'/);
});

test('signing recipe checks exact owner gate before reading private signer bytes', () => {
  assert.match(recipe, /CONTINUUM_OWNER_V21_APPROVED_SHA/);
  assert.match(recipe, /node scripts\/continuum-v21-source-gate\.mjs/);
  assert.ok(recipe.indexOf('node scripts/continuum-v21-source-gate.mjs') <
    recipe.indexOf('base64 --decode'));
  assert.match(recipe, /versionCode='21' versionName='0\.3\.20'/);
  assert.match(recipe, /versionCode='20' versionName='0\.3\.19'/);
  assert.match(recipe, /expected_signer="2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc"/);
  assert.match(recipe, /sha256sum --check --status/);
  assert.match(recipe, /SIGNED_BUILD_VERIFIED_NOT_DEVICE_INSTALLED/);
  assert.doesNotMatch(recipe, /APPROVED_RELEASE_SOURCES/);
});

test('shell recipes are parseable without executing Gradle or signing', () => {
  for (const script of ['scripts/render-mentor-preview.sh',
    'scripts/render-mentor-owner-update-v21.sh']) {
    const p = spawnSync('bash', ['-n', script], { cwd: root, encoding: 'utf8', timeout: 10000 });
    assert.equal(p.status, 0, script + ': ' + p.stderr);
  }
});
