/** Read-only MCP observer. No scheduler, shell, model, task submission or execution authority. */
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import { createHash, randomBytes, timingSafeEqual } from 'node:crypto';

export const MCP_PROTOCOL_VERSION = '2025-06-18';
export const CODING_MCP_LIMITS = Object.freeze({
  maxBodyBytes: 16384, maxResultBytes: 65536, callTimeoutMs: 10000,
  bodyTimeoutMs: 5000, maxSessions: 16, sessionIdleMs: 300000,
  sessionLifetimeMs: 900000, maxCallsPerSession: 100,
  maxConcurrentReads: 2, maxRequestsPerMinute: 120, maxCursorsPerSession: 32,
});
const hash = value => createHash('sha256').update(value).digest();
const hex = value => hash(value).toString('hex');
const fail = code => { throw Object.assign(new Error(code), { code }); };
const record = value => value !== null && typeof value === 'object' && !Array.isArray(value) && Object.getPrototypeOf(value) === Object.prototype;
const keysOnly = (value, keys) => record(value) && Object.keys(value).every(key => keys.includes(key));
const shortString = (value, max = 128) => typeof value === 'string' && value.length > 0 && value.length <= max;
const inside = (file, root) => file === root || file.startsWith(root + path.sep);

function secureParent(file) {
  if (!path.isAbsolute(file) || path.normalize(file) !== file) fail('MCP_CREDENTIAL_PATH_INVALID');
  const dir = path.dirname(file);
  if (fs.realpathSync(dir) !== dir) fail('MCP_CREDENTIAL_SYMLINK');
  const stat = fs.lstatSync(dir);
  if (!stat.isDirectory() || (stat.mode & 0o077) || stat.uid !== process.getuid?.()) fail('MCP_CREDENTIAL_DIRECTORY_UNSAFE');
}

/** Only the local owner invokes this CLI helper. Existing credentials are never overwritten. */
export function createCodingMcpToken({ tokenFile }) {
  secureParent(tokenFile);
  const fd = fs.openSync(tokenFile, fs.constants.O_WRONLY | fs.constants.O_CREAT | fs.constants.O_EXCL | fs.constants.O_NOFOLLOW, 0o600);
  try { fs.writeFileSync(fd, randomBytes(32).toString('hex') + '\n'); fs.fsyncSync(fd); }
  finally { fs.closeSync(fd); }
  const dir = fs.openSync(path.dirname(tokenFile), fs.constants.O_RDONLY);
  try { fs.fsyncSync(dir); } finally { fs.closeSync(dir); }
  return Object.freeze({ created: true, credential: 'OWNER_FILE', tokenPrinted: false });
}

function credentialDigest(tokenFile, protectedRoots) {
  secureParent(tokenFile);
  if (fs.realpathSync(tokenFile) !== tokenFile) fail('MCP_CREDENTIAL_SYMLINK');
  for (const root of protectedRoots) {
    if (typeof root !== 'string' || !path.isAbsolute(root)) fail('MCP_PROTECTED_ROOT_INVALID');
    // Roots must already exist; guessing a future path is not an isolation guarantee.
    if (inside(tokenFile, fs.realpathSync(root))) fail('MCP_CREDENTIAL_IN_WORKSPACE');
  }
  const fd = fs.openSync(tokenFile, fs.constants.O_RDONLY | fs.constants.O_NOFOLLOW);
  try {
    const stat = fs.fstatSync(fd);
    if (!stat.isFile() || (stat.mode & 0o077) || stat.uid !== process.getuid?.() || stat.nlink !== 1 || stat.size > 256 || stat.size < 44) fail('MCP_CREDENTIAL_FILE_UNSAFE');
    const bytes = Buffer.alloc(257); const n = fs.readSync(fd, bytes, 0, bytes.length, 0);
    const token = bytes.subarray(0, n).toString('utf8').trim();
    if (!/^[a-zA-Z0-9_-]{43,128}$/.test(token)) fail('MCP_CREDENTIAL_INVALID');
    return hash(token);
  } finally { fs.closeSync(fd); }
}

function safeJson(value, maxBytes) {
  let nodes = 0;
  const walk = (v, depth) => {
    if (++nodes > 12000 || depth > 12) fail('MCP_RESULT_INVALID');
    if (v === null || typeof v === 'boolean') return;
    if (typeof v === 'number' && Number.isFinite(v)) return;
    if (typeof v === 'string' && v.length <= maxBytes) return;
    if (Array.isArray(v)) { for (const item of v) walk(item, depth + 1); return; }
    if (!record(v)) fail('MCP_RESULT_INVALID');
    for (const [key, item] of Object.entries(v)) {
      if (['__proto__', 'constructor', 'prototype'].includes(key)) fail('MCP_RESULT_INVALID');
      walk(item, depth + 1);
    }
  };
  walk(value, 0); const encoded = JSON.stringify(value);
  if (Buffer.byteLength(encoded) > maxBytes) fail('MCP_RESULT_TOO_LARGE');
  return encoded;
}

const pageSchema = { type: 'object', properties: { cursor: { type: 'string', minLength: 1, maxLength: 128 }, limit: { type: 'integer', minimum: 1, maximum: 50, default: 20 } }, additionalProperties: false };
const noArgs = { type: 'object', properties: {}, additionalProperties: false };
const idPattern = '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$';
const TOOL_DEFINITIONS = [
  ['get_status', 'Read current local coding service state; no task is started.', noArgs],
  ['list_agents', 'Read a bounded page of actual persisted bot profiles and observed state.', pageSchema],
  ['list_tasks', 'Read a bounded page of actual canonical coding tasks.', pageSchema],
  ['get_evidence', 'Read a currently authorized, integrity-checked projected report or result by task ID.', { type: 'object', properties: { taskId: { type: 'string', pattern: idPattern, maxLength: 128 }, evidenceId: { type: 'string', enum: ['report', 'result'] } }, required: ['taskId', 'evidenceId'], additionalProperties: false }],
].map(([name, description, inputSchema]) => ({ name, description, inputSchema, annotations: { readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: false } }));
const METHOD = Object.freeze({ get_status: 'getStatus', list_agents: 'listAgents', list_tasks: 'listTasks', get_evidence: 'getEvidence' });

function toolArguments(name, args, session) {
  if (!Object.hasOwn(METHOD, name)) fail('MCP_UNKNOWN_TOOL');
  if (name === 'get_status') { if (!keysOnly(args, [])) fail('MCP_ARGUMENTS_INVALID'); return {}; }
  if (name === 'get_evidence') {
    if (!keysOnly(args, ['taskId', 'evidenceId']) || typeof args.taskId !== 'string' || !new RegExp(idPattern).test(args.taskId) || !['report', 'result'].includes(args.evidenceId)) fail('MCP_ARGUMENTS_INVALID');
    return { taskId: args.taskId, evidenceId: args.evidenceId };
  }
  if (!keysOnly(args, ['cursor', 'limit'])) fail('MCP_ARGUMENTS_INVALID');
  const limit = args.limit ?? 20;
  if (!Number.isInteger(limit) || limit < 1 || limit > 50) fail('MCP_ARGUMENTS_INVALID');
  let cursor = null;
  if (Object.hasOwn(args, 'cursor')) {
    if (!shortString(args.cursor) || !session.cursors.has(args.cursor)) fail('MCP_CURSOR_INVALID');
    const bound = session.cursors.get(args.cursor);
    if (bound.tool !== name) fail('MCP_CURSOR_INVALID');
    cursor = bound.value;
  }
  return { cursor, limit };
}

function projectResult(name, result, args, session, limits) {
  safeJson(result, limits.maxResultBytes);
  if (!record(result)) fail('MCP_RESULT_INVALID');
  if (name === 'list_agents' || name === 'list_tasks') {
    if (!keysOnly(result, ['items', 'nextCursor']) || !Array.isArray(result.items) || result.items.length > args.limit || !(result.nextCursor === null || shortString(result.nextCursor, 4096))) fail('MCP_RESULT_INVALID');
    let nextCursor = null;
    if (result.nextCursor !== null) {
      if (session.cursors.size >= limits.maxCursorsPerSession) fail('MCP_CURSOR_BUDGET');
      nextCursor = randomBytes(24).toString('base64url');
      session.cursors.set(nextCursor, { tool: name, value: result.nextCursor });
    }
    return { items: result.items, nextCursor };
  }
  if (name === 'get_evidence') {
    if (!keysOnly(result, ['taskId', 'evidenceId', 'sha256', 'mediaType', 'content', 'environment', 'deviceVerified']) || result.taskId !== args.taskId || result.evidenceId !== args.evidenceId || result.mediaType !== 'application/json' || !record(result.content) || result.environment !== 'HOST' || result.deviceVerified !== false || !/^[a-f0-9]{64}$/.test(result.sha256) || hex(JSON.stringify(result.content)) !== result.sha256) fail('MCP_EVIDENCE_INVALID');
  }
  return result;
}

/** `reader` is a trusted application adapter; all clients remain untrusted. Production uses the fixed process bridge. */
export async function startCodingMcpServer({ reader, tokenFile, protectedRoots = [], host = '127.0.0.1', port = 0, limits = {} }) {
  if (host !== '127.0.0.1' || !Number.isInteger(port) || port < 0 || port > 65535) fail('MCP_LOOPBACK_REQUIRED');
  if (!reader || Object.values(METHOD).some(method => typeof reader[method] !== 'function')) fail('MCP_READER_REQUIRED');
  if (!Array.isArray(protectedRoots)) fail('MCP_PROTECTED_ROOT_INVALID');
  const L = { ...CODING_MCP_LIMITS };
  for (const [key, value] of Object.entries(limits)) {
    if (!Object.hasOwn(L, key) || !Number.isInteger(value) || value < 1 || value > L[key]) fail('MCP_LIMIT_INVALID');
    L[key] = value;
  }
  let currentDigest = credentialDigest(tokenFile, protectedRoots);
  let closing = false, active = 0, rateStart = Date.now(), rateCount = 0;
  const sessions = new Map(), sockets = new Set();
  const metrics = { authenticatedRequests: 0, rejectedRequests: 0, readCalls: 0, readErrors: 0, readTimeouts: 0 };
  const invalidate = session => { for (const pending of session.pending.values()) pending.abort(); session.cursors.clear(); };
  const refresh = () => {
    let next;
    try { next = credentialDigest(tokenFile, protectedRoots); }
    catch { for (const s of sessions.values()) invalidate(s); sessions.clear(); return null; }
    if (!timingSafeEqual(next, currentDigest)) { for (const s of sessions.values()) invalidate(s); sessions.clear(); currentDigest = next; }
    return next;
  };
  const prune = () => { const now = Date.now(); for (const [id, s] of sessions) if (now - s.touched >= L.sessionIdleMs || now - s.created >= L.sessionLifetimeMs) { invalidate(s); sessions.delete(id); } };
  const server = http.createServer({ maxHeaderSize: 8192, requestTimeout: L.bodyTimeoutMs, headersTimeout: L.bodyTimeoutMs, keepAliveTimeout: 2000 }, async (req, res) => {
    const send = (status, payload = null, headers = {}) => {
      if (res.destroyed || res.writableEnded) return;
      const encoded = payload === null ? '' : JSON.stringify(payload);
      res.writeHead(status, { 'cache-control': 'no-store', 'x-content-type-options': 'nosniff', ...(payload === null ? {} : { 'content-type': 'application/json' }), ...headers }); res.end(encoded);
    };
    const reject = (status, code, headers) => { metrics.rejectedRequests++; req.resume(); send(status, { error: code }, headers); };
    const rpcError = (id, code, message, status = 200) => send(status, { jsonrpc: '2.0', id, error: { code, message } });
    try {
      if (closing) return reject(503, 'MCP_STOPPING');
      if (Date.now() - rateStart >= 60000) { rateStart = Date.now(); rateCount = 0; }
      if (++rateCount > L.maxRequestsPerMinute) return reject(429, 'MCP_REQUEST_BUDGET', { 'retry-after': '60' });
      const repeated = new Set();
      for (let i = 0; i < req.rawHeaders.length; i += 2) {
        const key = req.rawHeaders[i].toLowerCase();
        if (['authorization', 'host', 'origin', 'accept', 'content-type', 'content-length', 'mcp-session-id', 'mcp-protocol-version'].includes(key)) { if (repeated.has(key)) return reject(400, 'MCP_DUPLICATE_HEADER'); repeated.add(key); }
      }
      const authority = `127.0.0.1:${server.address().port}`;
      if (req.headers.host !== authority) return reject(403, 'MCP_HOST_REJECTED');
      if (req.headers.origin !== undefined && req.headers.origin !== `http://${authority}`) return reject(403, 'MCP_ORIGIN_REJECTED');
      if (req.url !== '/mcp') return reject(400, 'MCP_ENDPOINT_REJECTED');
      const expected = refresh();
      const bearer = /^Bearer ([A-Za-z0-9_-]{43,128})$/.exec(req.headers.authorization ?? '');
      if (!expected || !bearer || !timingSafeEqual(hash(bearer[1]), expected)) return reject(401, 'MCP_AUTH_REQUIRED', { 'www-authenticate': 'Bearer realm="continuum-local-owner"' });
      metrics.authenticatedRequests++; prune();
      if (req.headers['mcp-protocol-version'] !== undefined && req.headers['mcp-protocol-version'] !== MCP_PROTOCOL_VERSION) return reject(400, 'MCP_PROTOCOL_UNSUPPORTED');
      if (req.method === 'GET') return reject(405, 'MCP_SSE_NOT_SUPPORTED', { allow: 'POST, DELETE' });
      if (!['POST', 'DELETE'].includes(req.method)) return reject(405, 'MCP_METHOD_REJECTED', { allow: 'POST, DELETE' });
      const sessionId = req.headers['mcp-session-id'];
      let session = sessionId ? sessions.get(sessionId) : null;
      if (sessionId && !session) return reject(404, 'MCP_SESSION_UNKNOWN');
      if (session && req.headers['mcp-protocol-version'] !== MCP_PROTOCOL_VERSION) return reject(400, 'MCP_PROTOCOL_REQUIRED');
      if (req.method === 'DELETE') {
        if (!session) return reject(400, 'MCP_SESSION_REQUIRED');
        invalidate(session); sessions.delete(sessionId); return send(204);
      }
      const accept = (req.headers.accept ?? '').split(',').map(value => value.trim().toLowerCase());
      if (!accept.includes('application/json') || !accept.includes('text/event-stream')) return reject(406, 'MCP_ACCEPT_REQUIRED');
      if (!/^application\/json(?:\s*;\s*charset=utf-8)?$/i.test(req.headers['content-type'] ?? '') || (req.headers['content-encoding'] !== undefined && req.headers['content-encoding'] !== 'identity')) return reject(415, 'MCP_JSON_REQUIRED');
      if (req.headers['content-length'] !== undefined && (!/^\d+$/.test(req.headers['content-length']) || Number(req.headers['content-length']) > L.maxBodyBytes)) return reject(413, 'MCP_BODY_TOO_LARGE');
      let chunks = [], size = 0;
      const body = await new Promise((resolve, rejectBody) => {
        const timer = setTimeout(() => { rejectBody(Object.assign(new Error('MCP_BODY_TIMEOUT'), { code: 'MCP_BODY_TIMEOUT' })); req.destroy(); }, L.bodyTimeoutMs);
        const done = (fn, value) => { clearTimeout(timer); req.removeListener('data', data); req.removeListener('end', end); req.removeListener('error', error); req.removeListener('aborted', aborted); fn(value); };
        const data = chunk => { size += chunk.length; if (size > L.maxBodyBytes) { done(rejectBody, Object.assign(new Error('MCP_BODY_TOO_LARGE'), { code: 'MCP_BODY_TOO_LARGE' })); req.resume(); } else chunks.push(chunk); };
        const end = () => { try { done(resolve, new TextDecoder('utf-8', { fatal: true }).decode(Buffer.concat(chunks))); } catch { done(rejectBody, new Error('MCP_BODY_UTF8_INVALID')); } };
        const error = () => done(rejectBody, new Error('MCP_BODY_ABORTED'));
        const aborted = () => done(rejectBody, new Error('MCP_BODY_ABORTED'));
        req.on('data', data); req.once('end', end); req.once('error', error); req.once('aborted', aborted);
      }).catch(error => { if (error.code === 'MCP_BODY_TOO_LARGE') reject(413, error.code); else reject(408, 'MCP_BODY_INCOMPLETE'); return null; });
      chunks = [];
      if (body === null) return;
      let message;
      try { message = JSON.parse(body); safeJson(message, L.maxBodyBytes); } catch { return rpcError(null, -32700, 'Parse error', 400); }
      if (!keysOnly(message, ['jsonrpc', 'id', 'method', 'params']) || message.jsonrpc !== '2.0' || !shortString(message.method, 80) || (message.params !== undefined && !record(message.params)) || (Object.hasOwn(message, 'id') && !(shortString(message.id, 64) || Number.isSafeInteger(message.id)))) return rpcError(null, -32600, 'Invalid request', 400);
      const hasId = Object.hasOwn(message, 'id'), params = message.params ?? {}, id = hasId ? message.id : null;
      if (message.method === 'initialize') {
        if (!hasId || session || !keysOnly(params, ['protocolVersion', 'capabilities', 'clientInfo']) || !shortString(params.protocolVersion, 32) || !record(params.capabilities) || !keysOnly(params.clientInfo, ['name', 'version', 'title']) || !shortString(params.clientInfo.name) || !shortString(params.clientInfo.version)) return rpcError(id, -32602, 'Invalid initialization', 400);
        if (sessions.size >= L.maxSessions) return reject(429, 'MCP_SESSION_BUDGET');
        const newId = randomBytes(32).toString('base64url'), now = Date.now();
        session = { created: now, touched: now, ready: false, calls: 0, pending: new Map(), cursors: new Map() };
        sessions.set(newId, session);
        return send(200, { jsonrpc: '2.0', id, result: { protocolVersion: MCP_PROTOCOL_VERSION, capabilities: { tools: { listChanged: false } }, serverInfo: { name: 'continuum-coding-readonly', version: '1.0.0' }, instructions: 'Local owner-only read access. No task submission, authorization or execution tools. Host evidence is not Fold7 acceptance.' } }, { 'mcp-session-id': newId });
      }
      if (!session) return reject(400, 'MCP_SESSION_REQUIRED');
      session.touched = Date.now();
      if (!hasId) {
        if (message.method === 'notifications/initialized' && keysOnly(params, []) && !session.ready) { session.ready = true; return send(202); }
        if (message.method === 'notifications/cancelled' && keysOnly(params, ['requestId', 'reason']) && (shortString(params.requestId, 64) || Number.isSafeInteger(params.requestId))) { session.pending.get(params.requestId)?.abort(); return send(202); }
        return reject(400, 'MCP_NOTIFICATION_REJECTED');
      }
      if (message.method === 'ping' && keysOnly(params, [])) return send(200, { jsonrpc: '2.0', id, result: {} });
      if (!session.ready) return rpcError(id, -32002, 'Session is not initialized');
      if (message.method === 'tools/list') {
        if (!keysOnly(params, [])) return rpcError(id, -32602, 'Invalid parameters');
        return send(200, { jsonrpc: '2.0', id, result: { tools: TOOL_DEFINITIONS } });
      }
      if (message.method !== 'tools/call') return rpcError(id, -32601, 'Method not found');
      if (!keysOnly(params, ['name', 'arguments']) || typeof params.name !== 'string') return rpcError(id, -32602, 'Invalid parameters');
      let args;
      try { args = toolArguments(params.name, params.arguments ?? {}, session); }
      catch { return rpcError(id, -32602, 'Unknown tool or invalid arguments'); }
      if (session.calls >= L.maxCallsPerSession || active >= L.maxConcurrentReads || session.pending.size) return reject(429, 'MCP_READ_BUDGET');
      session.calls++; active++; metrics.readCalls++;
      const controller = new AbortController(); session.pending.set(id, controller);
      let timer;
      try {
        const timeout = new Promise((_, rejectCall) => {
          timer = setTimeout(() => { metrics.readTimeouts++; controller.abort(); rejectCall(new Error('MCP_READ_TIMEOUT')); }, L.callTimeoutMs);
          controller.signal.addEventListener('abort', () => rejectCall(new Error('MCP_READ_ABORTED')), { once: true });
        });
        const result = await Promise.race([Promise.resolve().then(() => reader[METHOD[params.name]](args, { signal: controller.signal })), timeout]);
        const fresh = refresh(); prune();
        if (!fresh || !timingSafeEqual(fresh, expected)) return reject(401, 'MCP_AUTH_REVOKED');
        if (sessions.get(sessionId) !== session) return reject(404, 'MCP_SESSION_UNKNOWN');
        const view = projectResult(params.name, result, args, session, L), encoded = safeJson(view, L.maxResultBytes);
        send(200, { jsonrpc: '2.0', id, result: { content: [{ type: 'text', text: encoded }], structuredContent: view, isError: false } });
      } catch {
        metrics.readErrors++;
        const fresh = refresh();
        if (!fresh || !timingSafeEqual(fresh, expected)) return reject(401, 'MCP_AUTH_REVOKED');
        if (sessions.get(sessionId) !== session) return reject(404, 'MCP_SESSION_UNKNOWN');
        send(200, { jsonrpc: '2.0', id, result: { content: [{ type: 'text', text: 'READ_UNAVAILABLE: current authority, evidence integrity or read budget could not be confirmed.' }], isError: true } });
      } finally { clearTimeout(timer); session.pending.delete(id); active--; }
    } catch { reject(500, 'MCP_REQUEST_FAILED'); }
  });
  server.maxConnections = 32; server.maxRequestsPerSocket = 100;
  server.on('connection', socket => { sockets.add(socket); socket.once('close', () => sockets.delete(socket)); });
  server.on('clientError', (_, socket) => { if (socket.writable) socket.end('HTTP/1.1 400 Bad Request\r\nConnection: close\r\nContent-Length: 0\r\n\r\n'); });
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(port, host, () => { server.removeListener('error', reject); resolve(); }); });
  return Object.freeze({
    url: `http://127.0.0.1:${server.address().port}/mcp`,
    getMetrics: () => Object.freeze({ ...metrics, activeReads: active, sessions: sessions.size }),
    close: async () => { if (closing) return; closing = true; for (const s of sessions.values()) invalidate(s); sessions.clear(); await reader.close?.(); for (const socket of sockets) socket.destroy(); await new Promise(resolve => server.close(resolve)); },
  });
}
