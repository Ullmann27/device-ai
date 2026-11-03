import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { createCodingMcpReader } from './coding-mcp-reader.mjs';

const cli = fileURLToPath(new URL('../../scripts/continuum-coding-mcp.mjs', import.meta.url));
function directory(t) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'coding-mcp-process-')); fs.chmodSync(dir, 0o700);
  t.after(() => fs.rmSync(dir, { force: true, recursive: true })); return dir;
}

test('actual token CLI creates a private non-overwritable credential without printing it', t => {
  const dir = directory(t), tokenFile = path.join(dir, 'owner-token');
  const run = () => spawnSync(process.execPath, [cli, 'init-token', '--token-file', tokenFile], { encoding: 'utf8', timeout: 5000, shell: false });
  const first = run(); assert.equal(first.status, 0, first.stderr);
  const token = fs.readFileSync(tokenFile, 'utf8').trim();
  assert.match(token, /^[a-f0-9]{64}$/); assert.equal(fs.statSync(tokenFile).mode & 0o777, 0o600);
  assert.equal(first.stdout.includes(token), false); assert.equal(first.stderr.includes(token), false);
  const second = run(); assert.equal(second.status, 1); assert.equal(fs.readFileSync(tokenFile, 'utf8').trim(), token);
  assert.equal(second.stderr.includes(token), false);
});

test('actual owner CLI refuses permissive credential directories and undeclared flags', t => {
  const dir = directory(t); fs.chmodSync(dir, 0o755);
  const unsafe = spawnSync(process.execPath, [cli, 'init-token', '--token-file', path.join(dir, 'token')], { encoding: 'utf8', timeout: 5000, shell: false });
  assert.equal(unsafe.status, 1); assert.equal(fs.existsSync(path.join(dir, 'token')), false);
  const unknown = spawnSync(process.execPath, [cli, 'serve', '--token-file', path.join(dir, 'token'), '--host', '0.0.0.0'], { encoding: 'utf8', timeout: 5000, shell: false });
  assert.equal(unknown.status, 1); assert.equal(unknown.stdout.includes('LISTENING'), false);
});

test('fixed canonical child reader sanitizes preload environment and reports no arbitrary error text', async t => {
  const dir = directory(t), configPath = path.join(dir, 'invalid-config.json'), poison = path.join(dir, 'preload.cjs'), marker = path.join(dir, 'executed');
  fs.writeFileSync(configPath, '{}', { mode: 0o600 });
  fs.writeFileSync(poison, `require('node:fs').writeFileSync(${JSON.stringify(marker)},'wrong');\n`);
  const previous = process.env.NODE_OPTIONS;
  process.env.NODE_OPTIONS = '--require=' + poison;
  const reader = createCodingMcpReader({ configPath });
  try {
    await assert.rejects(reader.getStatus(), { message: 'MCP_CANONICAL_READ_UNAVAILABLE' });
    assert.equal(fs.existsSync(marker), false);
  } finally { previous === undefined ? delete process.env.NODE_OPTIONS : process.env.NODE_OPTIONS = previous; await reader.close(); }
});

test('a real spawned read process has a timeout, and closed reader cannot start another process', async t => {
  const dir = directory(t), configPath = path.join(dir, 'config.json'); fs.writeFileSync(configPath, '{}', { mode: 0o600 });
  const reader = createCodingMcpReader({ configPath, timeoutMs: 1 });
  const began = performance.now();
  await assert.rejects(reader.listAgents(), { code: 'MCP_CANONICAL_READ_UNAVAILABLE' });
  await reader.close(); assert.ok(performance.now() - began < 1500);
  await assert.rejects(reader.getStatus(), { code: 'MCP_CANONICAL_READ_UNAVAILABLE' });
});
