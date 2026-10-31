import test from 'node:test';
import assert from 'node:assert/strict';
import { createHandlers } from '../worker.mjs';

class MemoryD1 {
  constructor() { this.cron = []; this.runs = []; }
  prepare(sql) {
    return {
      bind: (...params) => ({
        run: async () => this.execute(sql, params),
        first: async () => this.first(sql, params)
      }),
      first: async () => this.first(sql, [])
    };
  }
  async first(sql) {
    if (sql.includes('SELECT 1 AS ok')) return { ok: 1 };
    throw new Error('Unexpected D1 first query');
  }
  async execute(sql, params) {
    if (sql.includes('INSERT INTO jarvis_cron_log')) {
      this.cron.push({ cron_time: params[0], action: params[1] });
      return { meta: { changes: 1 } };
    }
    if (sql.includes('INSERT INTO continuum_nvidia_runs')) {
      const [id, now, utc, topic, lifetimeLimit, dayStart, dailyLimit, cooldownStart] = params;
      const allowed = this.runs.length < lifetimeLimit &&
        this.runs.filter(r => r.requested_at_ms > dayStart).length < dailyLimit &&
        !this.runs.some(r => r.requested_at_ms > cooldownStart);
      if (!allowed) return { meta: { changes: 0 } };
      this.runs.push({ id, requested_at_ms: now, requested_at_utc: utc, topic, status: 'reserved' });
      return { meta: { changes: 1 } };
    }
    if (sql.includes('UPDATE continuum_nvidia_runs')) {
      const [status, finished, model, excerpt, requestId, id] = params;
      const record = this.runs.find(r => r.id === id);
      if (!record) throw new Error('Missing research reservation');
      Object.assign(record, {
        status, completed_at_ms: finished, model,
        result_excerpt: excerpt, provider_request_id: requestId
      });
      return { meta: { changes: 1 } };
    }
    throw new Error('Unexpected SQL: ' + sql.slice(0, 80));
  }
}
function clock(hours = 0) { return Date.UTC(2026, 9, 31, 2, 0, 0) + hours * 3600_000; }
const env = db => ({ DB: db, NVIDIA_ENABLED: 'true', NVIDIA_API_KEY: 'only-in-test-secret' });
const success = () => new Response(JSON.stringify({
  choices: [{ message: { content: 'A verifiable research hypothesis.' } }]
}), { status: 200, headers: { 'x-request-id': 'sample-id' } });

test('default deployment never calls NVIDIA, but records a cron tick', async () => {
  const db = new MemoryD1();
  const worker = createHandlers({ httpFetch: () => { throw Error('unexpected network access'); } });
  await worker.scheduled({ scheduledTime: clock() }, { DB: db, NVIDIA_ENABLED: 'false' });
  assert.equal(db.cron.length, 1);
  assert.equal(db.runs.length, 0);
});
test('enabled research sends a bounded API request and labels result unreviewed', async () => {
  const db = new MemoryD1();
  let calls = 0;
  const worker = createHandlers({ httpFetch: async (url, opts) => {
    calls++;
    assert.equal(url, 'https://integrate.api.nvidia.com/v1/chat/completions');
    assert.equal(opts.headers.authorization, 'Bearer only-in-test-secret');
    const body = JSON.parse(opts.body);
    assert.equal(body.model, 'nvidia/nemotron-3-ultra-550b-a55b');
    assert.equal(body.chat_template_kwargs.enable_thinking, true);
    assert.ok(body.max_tokens <= 8192);
    assert.ok(!opts.body.includes('only-in-test-secret'));
    return success();
  } });
  await worker.scheduled({ scheduledTime: clock() }, env(db));
  await worker.scheduled({ scheduledTime: clock() }, env(db));
  assert.equal(calls, 1);
  assert.equal(db.runs.length, 1);
  assert.equal(db.runs[0].status, 'proposal_unreviewed');
  assert.equal(db.runs[0].provider_request_id, 'sample-id');
});
test('rolling-day quota allows three calls, fourth denied regardless of interval', async () => {
  const db = new MemoryD1();
  let calls = 0;
  const worker = createHandlers({ httpFetch: async () => { calls++; return success(); } });
  for (const h of [0, 4.1, 8.2, 12.3]) await worker.scheduled({ scheduledTime: clock(h) }, env(db));
  assert.equal(calls, 3);
  await worker.scheduled({ scheduledTime: clock(24.1) }, env(db));
  assert.equal(calls, 4);
});
test('failed attempts count against cooldown; no automatic retries', async () => {
  const db = new MemoryD1();
  let calls = 0;
  const worker = createHandlers({ httpFetch: async () => {
    calls++; throw new Error('secret-laden internal network diagnostic');
  } });
  await worker.scheduled({ scheduledTime: clock() }, env(db));
  await worker.scheduled({ scheduledTime: clock(.1) }, env(db));
  assert.equal(calls, 1);
  assert.equal(db.runs[0].status, 'network_or_runtime_error');
  assert.ok(!JSON.stringify(db.runs).includes('secret-laden'));
});
test('provider failure saves HTTP code only, not provider error body', async () => {
  const db = new MemoryD1();
  const worker = createHandlers({ httpFetch: async () =>
    new Response('sensitive NVIDIA error body', { status: 429 })
  });
  await worker.scheduled({ scheduledTime: clock() }, env(db));
  assert.equal(db.runs[0].status, 'provider_http_429');
  assert.equal(db.runs[0].result_excerpt, null);
});
test('empty NVIDIA completion is not mistaken for success', async () => {
  const db = new MemoryD1();
  const worker = createHandlers({ httpFetch: async () =>
    new Response('{"choices":[{"message":{"content":""}}]}')
  });
  await worker.scheduled({ scheduledTime: clock() }, env(db));
  assert.equal(db.runs[0].status, 'empty_response');
});
test('lifetime cap blocks after 100 reservations', async () => {
  const db = new MemoryD1();
  let calls = 0;
  const worker = createHandlers({ httpFetch: async () => { calls++; return success(); } });
  for (let i = 0; i < 105; i++)
    await worker.scheduled({ scheduledTime: clock(i * 10) }, env(db));
  assert.equal(calls, 100);
});
test('public API is read-only and proposals remain private', async () => {
  const db = new MemoryD1();
  const worker = createHandlers();
  const req = (path, method = 'GET') =>
    new Request('https://example.workers.dev' + path, { method });
  assert.equal((await worker.fetch(req('/health'), { DB: db })).status, 200);
  const status = await worker.fetch(req('/db-status'), { DB: db });
  assert.deepEqual(await status.json(), { status: 'ok' });
  assert.equal((await worker.fetch(req('/db-test'), { DB: db })).status, 403);
  assert.equal((await worker.fetch(req('/research/latest'), { DB: db })).status, 404);
  assert.equal((await worker.fetch(req('/health', 'POST'), { DB: db })).status, 405);
  assert.equal(db.cron.length, 0);
});
test('missing D1 binding is rejected before provider call', async () => {
  const worker = createHandlers();
  await assert.rejects(
    () => worker.scheduled({ scheduledTime: clock() },
      { NVIDIA_ENABLED: 'true', NVIDIA_API_KEY: 'secret' }), /DB missing/
  );
});
