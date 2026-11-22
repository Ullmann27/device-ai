const NVIDIA_URL = 'https://integrate.api.nvidia.com/v1/chat/completions';
const NVIDIA_MODEL = 'nvidia/nemotron-3-ultra-550b-a55b';
const FOUR_HOURS = 4 * 3600 * 1000;
const DAY = 24 * 3600 * 1000;
const TOPICS = [
  ['temporal-memory', 'Design falsifiable tests for time-valid episodic memory, corrected facts and revoked evidence.'],
  ['semantic-generalization', 'Design a sealed out-of-distribution semantic role, negation and paraphrase evaluation.'],
  ['coding-agent-safety', 'Specify isolated RED/GREEN/FRESH GREEN Android agent repair tests with no writes to main.'],
  ['on-device-efficiency', 'Specify reproducible Fold7 memory, thermal, battery and latency tests.'],
  ['neural-shadow', 'Compare CTRNN and hyperdimensional memory against deterministic controls with ablations.'],
  ['android-release-safety', 'Specify a consent-gated Android APK update, signing verification and rollback strategy.']
];
const reply = (value, status = 200) => new Response(JSON.stringify(value), {
  status, headers: {
    'content-type': 'application/json; charset=utf-8',
    'cache-control': 'no-store',
    'x-content-type-options': 'nosniff'
  }
});
function enabled(env) {
  return env?.NVIDIA_ENABLED === 'true' &&
    typeof env.NVIDIA_API_KEY === 'string' && env.NVIDIA_API_KEY.trim() !== '';
}
function prompt(topic) {
  return [
    'You are an external, untrusted research advisor for Device AI Continuum, a local-first Android assistant on a Samsung Galaxy Z Fold7.',
    'You have no private repository access, no personal memory and no permission to execute code or make code changes.',
    'Research objective: ' + topic,
    'Return a hypothesis, baseline, independent oracle, ablation matrix, expected tradeoffs, failure cases, minimal implementation plan and measurable success criteria.',
    'Clearly distinguish verified facts from untested predictions. Do not fabricate benchmarks, experimental results or code execution. Never bypass sandboxing, signed updates, tests or owner consent.'
  ].join('\n');
}
export function createHandlers({ httpFetch = globalThis.fetch } = {}) {
  async function scheduled(event, env) {
    if (!env?.DB) throw new Error('D1 binding DB missing');
    const now = Number.isFinite(event?.scheduledTime) ? event.scheduledTime : Date.now();
    const utc = new Date(now).toISOString();
    await env.DB.prepare('INSERT INTO jarvis_cron_log (cron_time, action) VALUES (?, ?)')
      .bind(utc, 'scheduled_tick_no_external_calls_by_default').run();
    if (!enabled(env)) return;
    const topic = TOPICS[Math.floor(now / FOUR_HOURS) % TOPICS.length];
    const id = crypto.randomUUID();
    const reserve = await env.DB.prepare(
      "INSERT INTO continuum_nvidia_runs (id,requested_at_ms,requested_at_utc,topic,status) " +
      "SELECT ?,?,?,?,'reserved' " +
      "WHERE (SELECT COUNT(*) FROM continuum_nvidia_runs) < ? " +
      "AND (SELECT COUNT(*) FROM continuum_nvidia_runs WHERE requested_at_ms > ?) < ? " +
      "AND NOT EXISTS (SELECT 1 FROM continuum_nvidia_runs WHERE requested_at_ms > ?)"
    ).bind(id, now, utc, topic[0], 100, now - DAY, 3, now - FOUR_HOURS).run();
    if (reserve?.meta?.changes !== 1) return;
    let status = 'network_or_runtime_error', result = null, requestId = '';
    try {
      const res = await httpFetch(NVIDIA_URL, {
        method: 'POST',
        headers: {
          authorization: 'Bearer ' + env.NVIDIA_API_KEY,
          'content-type': 'application/json',
          accept: 'application/json'
        },
        body: JSON.stringify({
          model: NVIDIA_MODEL,
          messages: [
            {role: 'system', content: 'Untrusted research proposals only. No code execution or private data.'},
            {role: 'user', content: prompt(topic[1])}
          ],
          temperature: 1.0,
          top_p: 0.95,
          max_tokens: 8192,
          stream: false,
          chat_template_kwargs: {enable_thinking: true, medium_effort: true}
        }),
        signal: AbortSignal.timeout(120000)
      });
      requestId = (res.headers?.get('x-request-id') || '').slice(0, 120);
      if (!res.ok) {
        status = 'provider_http_' + res.status;
      } else {
        const body = await res.json();
        const text = body?.choices?.[0]?.message?.content;
        if (typeof text === 'string' && text.trim()) {
          status = 'proposal_unreviewed';
          result = text.slice(0, 12000);
        } else {
          status = 'empty_response';
        }
      }
    } catch (err) {
      status = err?.name === 'AbortError' || err?.name === 'TimeoutError'
        ? 'provider_timeout' : 'network_or_runtime_error';
    }
    await env.DB.prepare(
      'UPDATE continuum_nvidia_runs SET status=?,completed_at_ms=?,model=?,result_excerpt=?,provider_request_id=? WHERE id=?'
    ).bind(status, Date.now(), NVIDIA_MODEL, result, requestId, id).run();
  }
  async function fetch(request, env) {
    const path = new URL(request.url).pathname;
    if (request.method !== 'GET') return reply({error: 'method_not_allowed'}, 405);
    if (path === '/health') return reply({
      project: 'device-ai-continuum', service: 'cloudflare-nvidia-bridge', version: '1.0.0', status: 'ok'
    });
    if (path === '/db-status') {
      if (!env?.DB) return reply({status: 'db_unavailable'}, 503);
      try {
        const row = await env.DB.prepare('SELECT 1 AS ok').first();
        return reply({status: row?.ok === 1 ? 'ok' : 'db_unavailable'}, row?.ok === 1 ? 200 : 503);
      } catch { return reply({status: 'db_unavailable'}, 503); }
    }
    if (path === '/db-test') return reply({error: 'disabled'}, 403);
    return reply({error: 'not_found'}, 404);
  }
  return { fetch, scheduled };
}
export default createHandlers();
