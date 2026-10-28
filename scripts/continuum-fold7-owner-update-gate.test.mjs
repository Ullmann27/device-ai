import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const source = path.join(path.dirname(fileURLToPath(import.meta.url)),
  'continuum-fold7-owner-update-gate.sh');

function run(script, args = [], env = {}) {
  return spawnSync('bash', [script, ...args], {
    encoding: 'utf8', timeout: 10000,
    env: { ...process.env, ...env }
  });
}
function repository(action, origin = 'https://github.com/Ullmann27/device-ai.git') {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'fold7-gate-'));
  const scripts = path.join(root, 'scripts');
  const app = path.join(root, 'app');
  fs.mkdirSync(scripts);
  fs.mkdirSync(app);
  try {
    fs.copyFileSync(source, path.join(scripts, 'continuum-fold7-owner-update-gate.sh'));
    fs.writeFileSync(path.join(app, 'build.gradle.kts'), 'plugins {}\n');
    fs.writeFileSync(path.join(scripts, 'continuum-local-owner-apk.sh'),
      '#!/bin/bash\nprintf "owner:%s\\n" "$1" >> "$TEST_RECORD"\n');
    fs.writeFileSync(path.join(scripts, 'continuum-local-mainapp-qa.sh'),
      '#!/bin/bash\n[[ -z "${CONTINUUM_MENTOR_SIGNING_PASSWORD:-}" && -z "${CONTINUUM_MENTOR_SIGNING_FILE:-}" ]] || exit 39\nprintf "qa:%s\\n" "$1" >> "$TEST_RECORD"\n');
    fs.writeFileSync(path.join(scripts, 'continuum-fusion-full-regression.sh'),
      '#!/bin/bash\n[[ -z "${CONTINUUM_MENTOR_SIGNING_PASSWORD:-}" && -z "${CONTINUUM_MENTOR_SIGNING_FILE:-}" ]] || exit 38\nprintf "regression:%s\\n" "$1" >> "$TEST_RECORD"\n');
    const git = (...args) => {
      const result = spawnSync('git', args, { cwd: root, encoding: 'utf8' });
      assert.equal(result.status, 0, result.stderr);
      return result.stdout.trim();
    };
    git('init', '-q');
    git('config', 'user.name', 'Isolated Test');
    git('config', 'user.email', 'test@example.invalid');
    git('remote', 'add', 'origin', origin);
    git('add', 'app', 'scripts');
    git('commit', '-qm', 'fixture');
    const sha = git('rev-parse', 'HEAD');
    const record = path.join(root, 'record.txt');
    action({ root, script: path.join(scripts, 'continuum-fold7-owner-update-gate.sh'),
      sha, record });
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
}

test('shell syntax compiles', () => {
  const result = spawnSync('bash', ['-n', source], { encoding: 'utf8' });
  assert.equal(result.status, 0, result.stderr);
});

test('only explicit doctor, qa and owner with exact 40-digit SHA are accepted', () => {
  for (const args of [[], ['qa'], ['install', 'a'.repeat(40)],
    ['qa', 'short'], ['owner', 'A'.repeat(40)], ['qa', 'a'.repeat(40), 'extra']]) {
    assert.equal(run(source, args).status, 64);
  }
});

test('missing checkout never reaches build scripts', () => {
  assert.equal(run(source, ['qa', 'a'.repeat(40)]).status, 69);
});

test('mismatched HEAD is rejected before any build script', () =>
  repository(({ script, record }) => {
    const result = run(script, ['qa', 'b'.repeat(40)], { TEST_RECORD: record });
    assert.equal(result.status, 69);
    assert.match(result.stderr, /Wrong source SHA/);
    assert.equal(fs.existsSync(record), false);
  })
);

test('unofficial Git remote is rejected', () =>
  repository(({ script, sha, record }) => {
    const result = run(script, ['doctor', sha], { TEST_RECORD: record });
    assert.equal(result.status, 69);
    assert.match(result.stderr, /Git origin is not/);
    assert.equal(fs.existsSync(record), false);
  }, 'https://example.invalid/counterfeit.git')
);

test('dirty repository cannot run QA or signing', () =>
  repository(({ script, sha, record, root }) => {
    fs.writeFileSync(path.join(root, 'changed.txt'), 'x');
    for (const mode of ['qa', 'owner']) {
      const result = run(script, [mode, sha], { TEST_RECORD: record });
      assert.equal(result.status, 69);
      assert.match(result.stderr, /not clean/);
    }
    assert.equal(fs.existsSync(record), false);
  })
);

test('doctor is read-only and dispatches only owner doctor', () =>
  repository(({ script, sha, record }) => {
    const result = run(script, ['doctor', sha], { TEST_RECORD: record });
    assert.equal(result.status, 0, result.stderr);
    assert.equal(fs.readFileSync(record, 'utf8'), 'owner:doctor\n');
  })
);

test('QA strips signing secrets and runs regression before disposable main-app QA', () =>
  repository(({ script, sha, record }) => {
    const result = run(script, ['qa', sha], {
      TEST_RECORD: record,
      CONTINUUM_MENTOR_SIGNING_FILE: '/tmp/owner-original.p12',
      CONTINUUM_MENTOR_SIGNING_PASSWORD: 'test-secret-only',
      CONTINUUM_OWNER_LOCAL_APPROVED_SHA: 'a'.repeat(40)
    });
    assert.equal(result.status, 0, result.stderr);
    assert.equal(fs.readFileSync(record, 'utf8'), 'regression:' + sha + '\nqa:--offline\n');
    assert.match(result.stdout, /QA_INSTALLABLE=FALSE/);
  })
);

test('owner signing never runs without explicit approval, code or original key', () =>
  repository(({ script, sha, record, root }) => {
    const base = { TEST_RECORD: record };
    const p12 = path.join(root, 'original.p12');
    fs.writeFileSync(p12, 'not-a-real-key');
    const variants = [
      base,
      { ...base, CONTINUUM_OWNER_LOCAL_APPROVED_SHA: sha },
      { ...base, CONTINUUM_OWNER_LOCAL_APPROVED_SHA: sha, CONTINUUM_OWNER_NEXT_VERSION_CODE: '26' },
      { ...base, CONTINUUM_OWNER_LOCAL_APPROVED_SHA: sha, CONTINUUM_OWNER_NEXT_VERSION_CODE: '26',
        CONTINUUM_MENTOR_SIGNING_FILE: p12 }
    ];
    for (const variant of variants) {
      assert.equal(run(script, ['owner', sha], variant).status, 69);
    }
    assert.equal(fs.existsSync(record), false);
  })
);
