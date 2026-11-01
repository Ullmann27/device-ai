import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, writeFileSync, rmSync, readFileSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';

const script = process.env.CONTINUUM_CHECKOUT_SCRIPT || join(import.meta.dirname, 'ensure-termux-checkout.sh');
const tracked = [
  'scripts/continuum-owned-app.mjs',
  'scripts/continuum-typed-coding-bridge.mjs',
  '.github/continuum/coding-task.mjs',
  '.github/continuum/ccc1-cli.mjs'
];
function call(cmd, args, cwd) {
  const p = spawnSync(cmd, args, { cwd, encoding: 'utf8', timeout: 5000 });
  assert.equal(p.error, undefined, p.error?.message);
  return p;
}
function fixture() {
  const root = mkdtempSync(join(tmpdir(), 'continuum-termux-'));
  const g = (...args) => call('git', args, root);
  assert.equal(g('init', '-q').status, 0);
  g('config', 'user.name', 'Continuum QA');
  g('config', 'user.email', 'continuum-qa@invalid.example');
  for (const p of tracked) {
    mkdirSync(dirname(join(root, p)), { recursive: true });
    writeFileSync(join(root, p), 'pinned code: ' + p + '\n');
  }
  assert.equal(g('add', '-A').status, 0);
  assert.equal(g('commit', '-qm', 'pinned fixture').status, 0);
  const sha = g('rev-parse', 'HEAD').stdout.trim();
  return { root, sha, run: (mode) => call('bash', [script, root, sha, ...mode], root), g };
}

test('clean pinned script tree validates without mutation', (t) => {
  const f = fixture(); t.after(() => rmSync(f.root, { recursive: true, force: true }));
  const p = f.run([]);
  assert.equal(p.status, 0, p.stderr);
  assert.match(p.stdout, /CONTINUUM_CHECKOUT_OK SHA=/);
  assert.equal(f.g('status', '--porcelain').stdout, '');
});

test('sparse-like missing script requires explicit repair then restores exact Git blob', (t) => {
  const f = fixture(); t.after(() => rmSync(f.root, { recursive: true, force: true }));
  const missing = join(f.root, tracked[0]);
  rmSync(missing);
  const check = f.run([]);
  assert.equal(check.status, 20);
  assert.match(check.stderr, /Missing local file/);
  const repair = f.run(['--repair-missing']);
  assert.equal(repair.status, 0, repair.stderr);
  assert.equal(readFileSync(missing, 'utf8'), 'pinned code: ' + tracked[0] + '\n');
  assert.equal(f.g('status', '--porcelain').stdout, '');
});

test('local edits are never overwritten, even with restore flag', (t) => {
  const f = fixture(); t.after(() => rmSync(f.root, { recursive: true, force: true }));
  writeFileSync(join(f.root, tracked[0]), 'owner modifications\n');
  const result = f.run(['--repair-missing']);
  assert.equal(result.status, 20);
  assert.match(result.stderr, /refused to overwrite/);
  assert.equal(readFileSync(join(f.root, tracked[0]), 'utf8'), 'owner modifications\n');
});

test('incorrect source SHA is rejected without checkout modifications', (t) => {
  const f = fixture(); t.after(() => rmSync(f.root, { recursive: true, force: true }));
  rmSync(join(f.root, tracked[0]));
  const p = call('bash', [script, f.root, 'a'.repeat(40), '--repair-missing'], f.root);
  assert.equal(p.status, 20);
  assert.match(p.stderr, /Source commit mismatch/);
  assert.equal(existsSync(join(f.root, tracked[0])), false);
});

test('untrusted path and unsupported modes fail closed', (t) => {
  const f = fixture(); t.after(() => rmSync(f.root, { recursive: true, force: true }));
  const bad = call('bash', [script, f.root, f.sha, '--reset-all'], f.root);
  assert.equal(bad.status, 20);
  const nested = call('bash', [script, join(f.root, 'scripts'), f.sha], f.root);
  assert.equal(nested.status, 20);
});
