import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import { startCodingMcpServer, createCodingMcpToken, MCP_PROTOCOL_VERSION } from './coding-mcp-server.mjs';

const digest = value => crypto.createHash('sha256').update(JSON.stringify(value)).digest('hex');
const rpc = (id, method, params = {}) => ({ jsonrpc: '2.0', id, method, params });
async function fixture(t, { reader = {}, limits = {} } = {}) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'continuum-mcp-test-'));
  fs.chmodSync(dir, 0o700);
  const tokenFile = path.join(dir, 'token');
  createCodingMcpToken({ tokenFile });
  const token = fs.readFileSync(tokenFile, 'utf8').trim();
  const calls = [];
  const adapter = {
    getStatus: async () => ({ serviceState: 'STOPPED', environment: 'HOST', deviceVerified: false }),
    listAgents: async args => ({ items: [{ agentId: args.cursor ? 'bot-lint' : 'bot-forge' }], nextCursor: args.cursor ? null : 'canonical-page-two' }),
    listTasks: async () => ({ items: [], nextCursor: null }),
    getEvidence: async ({ taskId, evidenceId }) => {
      const content = { state: 'BLOCKED', verified: false };
      return { taskId, evidenceId, sha256: digest(content), mediaType: 'application/json', content, environment: 'HOST', deviceVerified: false };
    }, ...reader,
  };
  const observed = Object.fromEntries(Object.entries(adapter).map(([name, fn]) => [name, async (...args) => { calls.push({ name, args: args[0] }); return fn(...args); }]));
  const server = await startCodingMcpServer({ reader: observed, tokenFile, protectedRoots: [], limits });
  t.after(async () => { await server.close(); fs.rmSync(dir, { recursive: true, force: true }); });
  async function post(body, { session, auth = token, headers = {}, suffix = '' } = {}) {
    const response = await fetch(server.url + suffix, { method: 'POST', headers: { 'content-type': 'application/json', accept: 'application/json, text/event-stream', ...(auth ? { authorization: `Bearer ${auth}` } : {}), ...(session ? { 'mcp-session-id': session, 'mcp-protocol-version': MCP_PROTOCOL_VERSION } : {}), ...headers }, body: typeof body === 'string' ? body : JSON.stringify(body) });
    const text = await response.text();
    return { status: response.status, headers: response.headers, text, body: text ? JSON.parse(text) : null };
  }
  async function init() {
    const response = await post(rpc(1, 'initialize', { protocolVersion: MCP_PROTOCOL_VERSION, capabilities: {}, clientInfo: { name: 'independence-host-test', version: '1' } }));
    assert.equal(response.status, 200);
    const session = response.headers.get('mcp-session-id');
    assert.ok(session);
    const initialized = await post({ jsonrpc: '2.0', method: 'notifications/initialized' }, { session });
    assert.equal(initialized.status, 202);
    assert.equal(initialized.text, '');
    return session;
  }
  return { ...server, dir, tokenFile, token, post, init, calls };
}

test('real loopback HTTP lifecycle exposes exactly four read-only tools and fresh adapter state', async t => {
  const f = await fixture(t); const session = await f.init();
  const listed = await f.post(rpc(2, 'tools/list'), { session });
  assert.deepEqual(listed.body.result.tools.map(t => t.name), ['get_status', 'list_agents', 'list_tasks', 'get_evidence']);
  assert.ok(listed.body.result.tools.every(t => t.annotations.readOnlyHint === true && t.inputSchema.additionalProperties === false));
  const result = await f.post(rpc(3, 'tools/call', { name: 'get_status', arguments: {} }), { session });
  assert.equal(result.body.result.structuredContent.serviceState, 'STOPPED');
  assert.equal(f.calls.length, 1);
});

test('missing/wrong/query credentials and hostile Origin/Host never read service state', async t => {
  const f = await fixture(t);
  for (const option of [{ auth: null }, { auth: 'not-the-owner' }, { suffix: '?token=not-accepted' }, { headers: { origin: 'https://evil.example' } }, { headers: { host: 'evil.example' } }]) {
    const result = await f.post(rpc(1, 'ping'), option);
    assert.ok([400, 401, 403].includes(result.status));
    assert.equal(result.text.includes(f.token), false);
  }
  assert.equal(f.calls.length, 0);
});

test('write tools, path arguments, invalid pagination, batches and unsolicited responses rejected', async t => {
  const f = await fixture(t); const session = await f.init();
  for (const params of [
    { name: 'submit_task', arguments: {} },
    { name: 'get_status', arguments: { path: '/etc/passwd' } },
    { name: 'get_evidence', arguments: { taskId: '../other', evidenceId: 'report' } },
    { name: 'get_evidence', arguments: { taskId: 'task-1', evidenceId: '../../token' } },
    { name: 'list_agents', arguments: { limit: 0 } },
    { name: 'list_agents', arguments: { limit: null } },
    { name: 'get_status', arguments: null },
    { name: 'list_agents', arguments: { limit: 51 } },
    { name: 'list_tasks', arguments: { cursor: 'made-up' } },
  ]) {
    const result = await f.post(rpc(2, 'tools/call', params), { session });
    assert.ok(result.body.error, JSON.stringify(params));
  }
  for (const body of [[rpc(3, 'ping')], { jsonrpc: '2.0', id: 3, result: {} }]) {
    assert.equal((await f.post(body, { session })).status, 400);
  }
  assert.equal(f.calls.length, 0);
});

test('pagination cursor is session and tool bound and canonical cursor is not disclosed', async t => {
  const f = await fixture(t); const session = await f.init(); const other = await f.init();
  const first = await f.post(rpc(2, 'tools/call', { name: 'list_agents', arguments: { limit: 1 } }), { session });
  const cursor = first.body.result.structuredContent.nextCursor;
  assert.ok(cursor); assert.equal(first.text.includes('canonical-page-two'), false);
  for (const [s, tool] of [[other, 'list_agents'], [session, 'list_tasks']]) {
    const rejected = await f.post(rpc(3, 'tools/call', { name: tool, arguments: { cursor, limit: 1 } }), { session: s });
    assert.ok(rejected.body.error);
  }
  const second = await f.post(rpc(4, 'tools/call', { name: 'list_agents', arguments: { cursor, limit: 1 } }), { session });
  assert.equal(second.body.result.structuredContent.items[0].agentId, 'bot-lint');
});

test('token rotation revokes existing sessions and previously valid token', async t => {
  const f = await fixture(t); const session = await f.init();
  fs.writeFileSync(f.tokenFile, crypto.randomBytes(32).toString('hex') + '\n', { mode: 0o600 });
  assert.equal((await f.post(rpc(2, 'ping'), { session })).status, 401);
  const next = fs.readFileSync(f.tokenFile, 'utf8').trim();
  assert.equal((await f.post(rpc(3, 'ping'), { session, auth: next })).status, 404);
});

test('a grant reader failure is redacted and digest mismatch cannot become evidence', async t => {
  const f = await fixture(t, { reader: { getStatus: () => { throw new Error('/private/owner-token secret'); }, getEvidence: ({ taskId, evidenceId }) => ({ taskId, evidenceId, sha256: '0'.repeat(64), mediaType: 'application/json', content: { verified: true }, environment: 'HOST', deviceVerified: false }) } });
  const session = await f.init();
  for (const params of [{ name: 'get_status', arguments: {} }, { name: 'get_evidence', arguments: { taskId: 'task-1', evidenceId: 'report' } }]) {
    const result = await f.post(rpc(2, 'tools/call', params), { session });
    assert.equal(result.body.result.isError, true);
    assert.equal(result.text.includes('/private'), false);
    assert.equal(result.text.includes('secret'), false);
    assert.equal(result.text.includes('"verified":true'), false);
  }
});

test('no successful read after credential revocation during a pending call', async t => {
  let release; const waiting = new Promise(resolve => { release = resolve; });
  let entered; const started = new Promise(resolve => { entered = resolve; });
  const f = await fixture(t, { reader: { getStatus: async () => { entered(); await waiting; return { serviceState: 'STOPPED' }; } } });
  const session = await f.init(); const pending = f.post(rpc(2, 'tools/call', { name: 'get_status', arguments: {} }), { session });
  await started; fs.unlinkSync(f.tokenFile); release();
  const response = await pending;
  assert.equal(response.status, 401); assert.equal(response.text.includes('STOPPED'), false);
});

test('timeouts, oversized results/body and per-session parallel reads fail closed', async t => {
  const f = await fixture(t, { limits: { callTimeoutMs: 40, maxBodyBytes: 2048, maxResultBytes: 1024 }, reader: { getStatus: () => new Promise(() => {}), listTasks: () => ({ items: [{ details: 'x'.repeat(5000) }], nextCursor: null }) } });
  const session = await f.init();
  const pending = f.post(rpc(2, 'tools/call', { name: 'get_status', arguments: {} }), { session });
  await new Promise(resolve => setTimeout(resolve, 10));
  assert.equal((await f.post(rpc(3, 'tools/call', { name: 'list_tasks', arguments: {} }), { session })).status, 429);
  assert.equal((await pending).body.result.isError, true);
  assert.equal((await f.post(rpc(4, 'tools/call', { name: 'list_tasks', arguments: {} }), { session })).body.result.isError, true);
  assert.equal((await f.post('x'.repeat(3000), { session })).status, 413);
});

test('terminated, absent, uninitialized and unknown sessions cannot query service', async t => {
  const f = await fixture(t);
  assert.equal((await f.post(rpc(2, 'tools/list'))).status, 400);
  assert.equal((await f.post(rpc(2, 'tools/list'), { session: 'unknown' })).status, 404);
  const initialized = await f.post(rpc(1, 'initialize', { protocolVersion: MCP_PROTOCOL_VERSION, capabilities: {}, clientInfo: { name: 't', version: '1' } }));
  const s = initialized.headers.get('mcp-session-id');
  assert.ok((await f.post(rpc(2, 'tools/list'), { session: s })).body.error);
  const session = await f.init();
  assert.equal((await fetch(f.url, { method: 'DELETE', headers: { authorization: `Bearer ${f.token}`, 'mcp-session-id': session, 'mcp-protocol-version': MCP_PROTOCOL_VERSION } })).status, 204);
  assert.equal((await f.post(rpc(3, 'ping'), { session })).status, 404);
  assert.equal(f.calls.length, 0);
});

test('MCP version negotiation and JSON content negotiation follow pinned protocol', async t => {
  const f = await fixture(t);
  const init = await f.post(rpc(1, 'initialize', { protocolVersion: '2099-01-01', capabilities: {}, clientInfo: { name: 't', version: '1' } }));
  assert.equal(init.body.result.protocolVersion, MCP_PROTOCOL_VERSION);
  const session = await f.init();
  assert.equal((await f.post(rpc(2, 'ping'), { session, headers: { 'mcp-protocol-version': '2099-01-01' } })).status, 400);
  assert.equal((await f.post(rpc(2, 'ping'), { session, headers: { accept: 'application/json' } })).status, 406);
  assert.equal((await f.post(rpc(2, 'ping'), { session, headers: { 'content-type': 'text/plain' } })).status, 415);
  assert.equal((await fetch(f.url, { headers: { authorization: `Bearer ${f.token}` } })).status, 405);
});

test('unknown session is 404 for GET too, before optional stream rejection', async t => {
  const f = await fixture(t);
  const headers = { authorization: `Bearer ${f.token}`, 'mcp-session-id': 'not-a-current-session', 'mcp-protocol-version': MCP_PROTOCOL_VERSION, accept: 'text/event-stream' };
  assert.equal((await fetch(f.url, { headers })).status, 404);
  const session = await f.init(); headers['mcp-session-id'] = session;
  assert.equal((await fetch(f.url, { headers })).status, 405);
  assert.equal((await fetch(f.url, { method: 'DELETE', headers })).status, 204);
  assert.equal((await fetch(f.url, { headers })).status, 404);
});

test('credential files reject symlinks, permissive access, hard links and bot writable placement', async t => {
  const f = await fixture(t);
  for (const mutate of [() => fs.chmodSync(f.tokenFile, 0o644), () => { fs.chmodSync(f.tokenFile, 0o600); fs.linkSync(f.tokenFile, path.join(f.dir, 'linked')); }]) {
    mutate(); assert.equal((await f.post(rpc(1, 'ping'))).status, 401);
  }
  const nested = path.join(f.dir, 'nested'); fs.mkdirSync(nested, { mode: 0o700 });
  const link = path.join(nested, 'token'); fs.symlinkSync(f.tokenFile, link);
  assert.throws(() => createCodingMcpToken({ tokenFile: link }));
  await assert.rejects(startCodingMcpServer({ reader: {}, tokenFile: f.tokenFile, protectedRoots: [f.dir] }));
});
