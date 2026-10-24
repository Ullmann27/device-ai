import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync, execFileSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const entry = readFileSync(new URL('./render-mentor-preview.sh', import.meta.url), 'utf8');
function fixture(run) {
  const root = mkdtempSync(join(tmpdir(), 'continuum-owner-entrypoint-'));
  try {
    mkdirSync(join(root, 'scripts'));
    writeFileSync(join(root, 'scripts/render-mentor-preview.sh'), entry);
    writeFileSync(join(root, 'scripts/render-mentor-owner-update-v18.sh'), "printf 'OWNER_V18_DISPATCHED\\n'");
    writeFileSync(join(root, 'scripts/render-mentor-owner-update-v19.sh'), "printf 'OWNER_V19_DISPATCHED\\n'");
    execFileSync('git', ['init', '-q'], {cwd: root});
    execFileSync('git', ['-C', root, 'config', 'user.name', 'fixture']);
    execFileSync('git', ['-C', root, 'config', 'user.email', 'fixture@example.invalid']);
    execFileSync('git', ['-C', root, 'add', '.']);
    execFileSync('git', ['-C', root, 'commit', '-qm', 'fixture']);
    const head = execFileSync('git', ['-C', root, 'rev-parse', 'HEAD'], {encoding: 'utf8'}).trim();
    const invoke = (vars = {}) => spawnSync('bash', ['scripts/render-mentor-preview.sh'], {
      cwd: root, encoding: 'utf8',
      env: {
        ...process.env,
        CONTINUUM_OWNER_RELEASE_VERSION: '18',
        CONTINUUM_RELEASE_SHA: '',
        CONTINUUM_OWNER_V19_APPROVED_SHA: '',
        ...vars
      }
    });
    return run({head, invoke, root});
  } finally {
    rmSync(root, {recursive: true, force: true});
  }
}
test('default signing entrypoint retains v18', () => fixture(({invoke}) => {
  const result = invoke();
  assert.equal(result.status, 0);
  assert.equal(result.stdout.trim(), 'OWNER_V18_DISPATCHED');
}));
test('explicit v19 requires owner-approved pinned exact commit', () => fixture(({head, invoke}) => {
  for (const vars of [
    {CONTINUUM_OWNER_RELEASE_VERSION: '19'},
    {CONTINUUM_OWNER_RELEASE_VERSION: '19', CONTINUUM_RELEASE_SHA: head},
    {CONTINUUM_OWNER_RELEASE_VERSION: '19', CONTINUUM_OWNER_V19_APPROVED_SHA: head},
    {CONTINUUM_OWNER_RELEASE_VERSION: '19', CONTINUUM_RELEASE_SHA: '0'.repeat(40),
      CONTINUUM_OWNER_V19_APPROVED_SHA: '0'.repeat(40)}
  ]) {
    const result = invoke(vars);
    assert.equal(result.status, 69);
    assert.doesNotMatch(result.stdout, /DISPATCHED/);
  }
  const approved = invoke({CONTINUUM_OWNER_RELEASE_VERSION: '19',
    CONTINUUM_RELEASE_SHA: head, CONTINUUM_OWNER_V19_APPROVED_SHA: head});
  assert.equal(approved.status, 0);
  assert.equal(approved.stdout.trim(), 'OWNER_V19_DISPATCHED');
}));
test('unknown release version always fails closed', () => fixture(({invoke}) => {
  const result = invoke({CONTINUUM_OWNER_RELEASE_VERSION: '20'});
  assert.equal(result.status, 69);
  assert.doesNotMatch(result.stdout, /DISPATCHED/);
}));
test('non-code values cannot override the source pin guard', () => fixture(({head, invoke}) => {
  const result = invoke({CONTINUUM_OWNER_RELEASE_VERSION: '19',
    CONTINUUM_RELEASE_SHA: head, CONTINUUM_OWNER_V19_APPROVED_SHA: head + ' '});
  assert.equal(result.status, 69);
  assert.doesNotMatch(result.stdout, /DISPATCHED/);
}));
