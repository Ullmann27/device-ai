import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createServer } from 'node:http';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const backendRoot = fileURLToPath(new URL('../', import.meta.url));
const preload = fileURLToPath(new URL('./fixtures/inworld-fetch.mjs', import.meta.url));
const apiToken = 'test-only-inworld-http-api-token';
const apiKey = 'test-only-inworld-key:never-real==';
const configuredModel = 'inworld/models/gemma-4-31b-it';
const responseModel = 'inworld/models/test-fixture-response';
const pinnedUrl = 'https://api.inworld.ai/v1/chat/completions';
const answerText = 'Synthetic local fixture answer.';

function reply(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(body));
}

function successfulReply(res) {
  reply(res, 200, {
    model: responseModel,
    choices: [{ message: { role: 'assistant', content: answerText }, finish_reason: 'stop' }],
  });
}

async function listen(server) {
  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', resolve);
  });
  return server.address().port;
}

async function closeServer(server) {
  server.closeAllConnections();
  await new Promise((resolve) => server.close(resolve));
}

async function startServer(t, overrides = {}, handler = ({ res }) => successfulReply(res)) {
  const directory = await mkdtemp(path.join(tmpdir(), 'inworld-http-test-'));
  const auditPath = path.join(directory, 'fetch-audit.jsonl');
  const requests = [];
  const fixtureErrors = [];
  const upstream = createServer(async (req, res) => {
    try {
      const chunks = [];
      for await (const chunk of req) chunks.push(chunk);
      const request = {
        req,
        res,
        method: req.method,
        path: req.url,
        headers: req.headers,
        body: JSON.parse(Buffer.concat(chunks).toString('utf8')),
      };
      requests.push(request);
      await handler(request);
    } catch (error) {
      fixtureErrors.push(error);
      if (!res.headersSent) reply(res, 500, { error: 'Local test fixture failed' });
      else res.end();
    }
  });
  let child;
  let output = '';
  t.after(async () => {
    if (child && child.exitCode === null && child.signalCode === null) {
      const stopped = new Promise((resolve) => child.once('exit', resolve));
      child.kill('SIGTERM');
      await Promise.race([stopped, delay(1500)]);
      if (child.exitCode === null && child.signalCode === null) {
        child.kill('SIGKILL');
        await stopped;
      }
    }
    await closeServer(upstream);
    const attempts = await readAudit();
    await rm(directory, { recursive: true, force: true });
    assert.deepEqual(fixtureErrors, [], 'Local upstream handler must not fail');
    assert.deepEqual(attempts.filter(({ url }) => url !== pinnedUrl), [],
      'The server must not attempt another upstream or fallback');
  });

  const upstreamPort = await listen(upstream);
  const portReservation = createServer();
  const port = await listen(portReservation);
  await closeServer(portReservation);

  // Deliberately do not inherit the developer's environment or load its .env.
  const env = {
    NODE_ENV: 'test',
    PORT: String(port),
    DEVICE_AI_TOKEN: apiToken,
    OPENAI_API_KEY: 'test-only-unused-openai-key',
    OPENAI_BASE_URL: `http://127.0.0.1:${upstreamPort}/unused-openai`,
    OPENAI_MODEL: 'test-only-unused-model',
    WEB_SEARCH_ENABLED: 'false',
    CONTINUUM_DAEMON_ENABLED: 'false',
    CONTINUUM_BRAIN_FACTORY_ENABLED: 'false',
    CONTINUUM_HYPOTHESIS_TOURNAMENT_ENABLED: 'false',
    GROQ_MENTOR_ENABLED: 'false',
    OPENROUTER_MENTOR_ENABLED: 'false',
    INWORLD_MENTOR_ENABLED: 'true',
    INWORLD_API_KEY: apiKey,
    INWORLD_MENTOR_MODEL: configuredModel,
    INWORLD_MENTOR_REASONING_EFFORT: 'medium',
    INWORLD_MENTOR_DAILY_LIMIT: '10',
    INWORLD_TEST_UPSTREAM_URL: `http://127.0.0.1:${upstreamPort}/v1/chat/completions`,
    INWORLD_TEST_AUDIT_PATH: auditPath,
    ...overrides,
  };
  for (const [key, value] of Object.entries(env)) {
    if (value === undefined) delete env[key];
  }
  child = spawn(process.execPath, ['--import', preload, path.join(backendRoot, 'src/server.js')], {
    cwd: directory,
    env,
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  child.stdout.on('data', (chunk) => { output = (output + chunk).slice(-32_768); });
  child.stderr.on('data', (chunk) => { output = (output + chunk).slice(-32_768); });

  const base = `http://127.0.0.1:${port}`;
  const deadline = Date.now() + 10_000;
  let ready = false;
  while (Date.now() < deadline) {
    if (child.exitCode !== null || child.signalCode !== null) {
      assert.fail(`The isolated server exited before becoming ready: ${output}`);
    }
    try {
      const response = await fetch(`${base}/health`, { signal: AbortSignal.timeout(500) });
      if (response.ok) {
        await response.body?.cancel();
        ready = true;
        break;
      }
      await response.body?.cancel();
    } catch {
      // The child may still be binding its listener.
    }
    await delay(30);
  }
  assert.equal(ready, true, `The isolated server did not become ready: ${output}`);

  async function readAudit() {
    try {
      const contents = await readFile(auditPath, 'utf8');
      return contents.trim().split('\n').filter(Boolean).map((line) => JSON.parse(line));
    } catch (error) {
      if (error.code === 'ENOENT') return [];
      throw error;
    }
  }

  async function api(route, { body, token = apiToken, signal } = {}) {
    const headers = {};
    if (token !== null) headers.Authorization = `Bearer ${token}`;
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    const response = await fetch(`${base}${route}`, {
      method: body === undefined ? 'GET' : 'POST',
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: signal ?? AbortSignal.timeout(5000),
    });
    return { status: response.status, headers: response.headers, body: await response.json() };
  }

  const answer = (body = {}, options = {}) => api('/v1/mentor/answer', {
    body: { provider: 'INWORLD', consent: true, question: 'Explain this synthetic test.', ...body },
    ...options,
  });
  return { api, answer, requests, readAudit, output: () => output };
}

test('Inworld HTTP is disabled by default and never contacts upstream', async (t) => {
  const server = await startServer(t, { INWORLD_MENTOR_ENABLED: undefined });
  const status = await server.api('/v1/mentor/status?provider=INWORLD');
  assert.equal(status.status, 200);
  assert.deepEqual(status.body, {
    available: false, provider: 'INWORLD', model: null, remainingToday: 0,
  });
  const answer = await server.answer();
  assert.equal(answer.status, 503);
  assert.deepEqual(answer.body, { error: 'DISABLED' });
  assert.equal(server.requests.length, 0);
  assert.deepEqual(await server.readAudit(), []);
});

test('Inworld HTTP stays unavailable without an API key', async (t) => {
  const server = await startServer(t, { INWORLD_API_KEY: undefined });
  const status = await server.api('/v1/mentor/status?provider=INWORLD');
  assert.equal(status.status, 200);
  assert.deepEqual(status.body, {
    available: false, provider: 'INWORLD', model: null, remainingToday: 0,
  });
  const answer = await server.answer();
  assert.equal(answer.status, 503);
  assert.deepEqual(answer.body, { error: 'DISABLED' });
  assert.deepEqual(await server.readAudit(), []);
});

test('Inworld HTTP requires mentor authentication for status and answers', async (t) => {
  const server = await startServer(t);
  for (const token of [null, 'test-only-incorrect-token']) {
    const status = await server.api('/v1/mentor/status?provider=INWORLD', { token });
    assert.equal(status.status, 401);
    const answer = await server.answer({}, { token });
    assert.equal(answer.status, 401);
  }
  assert.deepEqual(await server.readAudit(), []);
});

test('Inworld HTTP selects the explicit provider without changing the default', async (t) => {
  const server = await startServer(t);
  const defaultStatus = await server.api('/v1/mentor/status');
  assert.equal(defaultStatus.status, 200);
  assert.equal(defaultStatus.body.provider, 'GROQ');
  const otherStatus = await server.api('/v1/mentor/status?provider=OPENROUTER');
  assert.equal(otherStatus.status, 200);
  assert.equal(otherStatus.body.provider, 'OPENROUTER');
  const status = await server.api('/v1/mentor/status?provider=INWORLD');
  assert.equal(status.status, 200);
  assert.deepEqual(status.body, {
    available: true, provider: 'INWORLD', model: configuredModel, remainingToday: 10,
  });
  assert.equal(status.headers.get('cache-control'), 'no-store');
  for (const query of ['UNKNOWN', 'inworld', 'INWORLD&provider=GROQ']) {
    const invalid = await server.api(`/v1/mentor/status?provider=${query}`);
    assert.equal(invalid.status, 400);
    assert.deepEqual(invalid.body, { error: 'INVALID_PROVIDER' });
  }
  assert.deepEqual(await server.readAudit(), []);
});

test('Inworld HTTP requires explicit consent and rejects extra fields before sending', async (t) => {
  const server = await startServer(t);
  for (const consent of [undefined, false, 'true', 1, null]) {
    const result = await server.answer({ consent });
    assert.equal(result.status, 400);
    assert.deepEqual(result.body, { error: 'OWNER_CONSENT_REQUIRED' });
  }
  for (const extra of [
    { model: 'unapproved-model' },
    { api_key: 'test-only-unapproved-key' },
    { messages: [] },
    { tools: [] },
    { history: [] },
    { memory: 'synthetic-memory' },
    { endpoint: 'https://example.invalid' },
  ]) {
    const result = await server.answer(extra);
    assert.equal(result.status, 400);
    assert.deepEqual(result.body, { error: 'INVALID_QUESTION' });
  }
  for (const question of [undefined, null, 123, '', '  ', 'ab', 'x'.repeat(1801)]) {
    const result = await server.answer({ question });
    assert.equal(result.status, 400);
    assert.deepEqual(result.body, { error: 'INVALID_QUESTION' });
  }
  for (const provider of [undefined, 'UNKNOWN', 'inworld']) {
    const result = await server.answer({ provider });
    assert.equal(result.status, 400);
    assert.deepEqual(result.body, { error: 'OWNER_CONSENT_REQUIRED' });
  }
  const status = await server.api('/v1/mentor/status?provider=INWORLD');
  assert.equal(status.body.remainingToday, 10);
  assert.equal(server.requests.length, 0);
  assert.deepEqual(await server.readAudit(), []);
});

test('Inworld HTTP completes one bounded request and preserves the mentor response contract', async (t) => {
  const server = await startServer(t);
  const question = 'Explain the synthetic round trip.';
  const result = await server.answer({ question: `  ${question}  ` });
  assert.equal(result.status, 200);
  assert.equal(result.headers.get('cache-control'), 'no-store');
  assert.deepEqual(result.body, {
    ok: true,
    message: answerText,
    model: responseModel,
    provider: 'INWORLD',
    trust: 'UNVERIFIED_EXTERNAL',
    tool_calls: [],
    memory_written: false,
  });
  assert.equal(server.requests.length, 1);
  const request = server.requests[0];
  assert.equal(request.method, 'POST');
  assert.equal(request.path, '/v1/chat/completions');
  assert.equal(request.headers.authorization, `Basic ${apiKey}`);
  assert.match(request.headers['content-type'], /^application\/json/);
  assert.equal(request.headers.accept, 'application/json');
  assert.deepEqual(Object.keys(request.body).sort(), [
    'extra_body', 'max_completion_tokens', 'messages', 'model', 'stream', 'temperature', 'tool_choice',
  ]);
  assert.equal(request.body.model, configuredModel);
  assert.equal(request.body.temperature, 0.3);
  assert.equal(request.body.max_completion_tokens, 2048);
  assert.deepEqual(request.body.extra_body, { reasoning: { effort: 'medium' } });
  assert.equal(request.body.stream, false);
  assert.equal(request.body.tool_choice, 'none');
  assert.equal(request.body.messages.length, 2);
  assert.equal(request.body.messages[0].role, 'system');
  assert.equal(typeof request.body.messages[0].content, 'string');
  assert.ok(request.body.messages[0].content.length > 0);
  assert.deepEqual(request.body.messages[1], { role: 'user', content: question });
  assert.deepEqual(await server.readAudit(), [{ url: pinnedUrl, method: 'POST', redirect: 'error' }]);
  const status = await server.api('/v1/mentor/status?provider=INWORLD');
  assert.equal(status.body.remainingToday, 9);
});

test('Inworld HTTP sanitizes upstream failures without retry or fallback', async (t) => {
  const canary = 'SYNTHETIC_UPSTREAM_SECRET_MUST_NOT_ESCAPE';
  let upstreamStatus = 500;
  const server = await startServer(t, {}, ({ res }) => {
    reply(res, upstreamStatus, { error: `${canary}: Basic ${apiKey}` });
  });
  const cases = [
    [401, 502, 'INWORLD_KEY_REJECTED'],
    [403, 502, 'INWORLD_KEY_REJECTED'],
    [404, 502, 'INWORLD_MODEL_UNAVAILABLE'],
    [429, 429, 'UPSTREAM_LIMIT'],
    [500, 502, 'UPSTREAM_UNAVAILABLE'],
  ];
  for (const [upstream, status, error] of cases) {
    upstreamStatus = upstream;
    const result = await server.answer();
    assert.equal(result.status, status, `Upstream ${upstream}`);
    assert.deepEqual(result.body, { error });
    assert.equal(result.headers.get('cache-control'), 'no-store');
    assert.equal(JSON.stringify(result.body).includes(canary), false);
  }
  assert.equal(server.requests.length, cases.length);
  assert.equal((await server.readAudit()).length, cases.length);
  assert.equal(server.output().includes(canary), false);
  assert.equal(server.output().includes(apiKey), false);
});

test('Inworld HTTP enforces the daily limit before another upstream request', async (t) => {
  const server = await startServer(t, { INWORLD_MENTOR_DAILY_LIMIT: '1' });
  assert.equal((await server.answer()).status, 200);
  const limited = await server.answer();
  assert.equal(limited.status, 429);
  assert.deepEqual(limited.body, { error: 'DAILY_LIMIT' });
  const status = await server.api('/v1/mentor/status?provider=INWORLD');
  assert.equal(status.body.remainingToday, 0);
  assert.equal(server.requests.length, 1);
  assert.equal((await server.readAudit()).length, 1);
});

test('Inworld HTTP rejects concurrent answers while one request is in flight', async (t) => {
  let signalArrival;
  const arrived = new Promise((resolve) => { signalArrival = resolve; });
  let release;
  const held = new Promise((resolve) => { release = resolve; });
  t.after(() => release());
  const server = await startServer(t, {}, async ({ res }) => {
    signalArrival();
    await held;
    successfulReply(res);
  });
  const first = server.answer();
  await Promise.race([
    arrived,
    delay(3000).then(() => { throw new Error('The first request did not reach the local fixture'); }),
  ]);
  try {
    const second = await server.answer();
    assert.equal(second.status, 429);
    assert.deepEqual(second.body, { error: 'BUSY' });
    assert.equal(server.requests.length, 1);
  } finally {
    release();
  }
  assert.equal((await first).status, 200);
  assert.equal((await server.readAudit()).length, 1);
});
