// CCC1 LanguageCore backed by an ON-DEVICE language model (OpenAI-compatible chat endpoint, e.g. a
// llama.cpp server in Termux). Owner goal: the own coding bots reason with a real model instead of
// lexical rules only. Privacy/offline boundary: loopback endpoints only, never a remote service.
// Fail-closed: any transport, timeout or schema problem returns the unchanged base ranking and records
// why, so a missing or misbehaving model can never invent a better-looking repair choice.
import {LexicalLanguageCore} from './ccc1-interpreter.mjs';

const LOOPBACK = new Set(['127.0.0.1', 'localhost', '[::1]', '::1']);

export function loopbackEndpoint(raw) {
  if (!raw) return null;
  let url;
  try { url = new URL(String(raw)); } catch { throw Error('CCC1_MODEL_ENDPOINT_INVALID'); }
  if (!['http:', 'https:'].includes(url.protocol) || !LOOPBACK.has(url.hostname) || url.username || url.password)
    throw Error('CCC1_MODEL_ENDPOINT_NOT_LOOPBACK');
  return url;
}

/** Strict reply schema: {"ranking":[{"id":<shortlist index>,"score":0..1}, ...]} with unique ids. */
export function parseModelRanking(text, shortlistSize) {
  const raw = String(text ?? '');
  const start = raw.indexOf('{'), end = raw.lastIndexOf('}');
  if (start < 0 || end <= start) return {ok: false, reason: 'NO_JSON'};
  let doc;
  try { doc = JSON.parse(raw.slice(start, end + 1)); } catch { return {ok: false, reason: 'INVALID_JSON'}; }
  if (!doc || !Array.isArray(doc.ranking) || doc.ranking.length === 0) return {ok: false, reason: 'NO_RANKING'};
  const scores = new Map();
  for (const item of doc.ranking) {
    if (!item || !Number.isInteger(item.id) || item.id < 0 || item.id >= shortlistSize) return {ok: false, reason: 'ID_OUT_OF_RANGE'};
    if (typeof item.score !== 'number' || !Number.isFinite(item.score) || item.score < 0 || item.score > 1)
      return {ok: false, reason: 'SCORE_OUT_OF_RANGE'};
    if (scores.has(item.id)) return {ok: false, reason: 'DUPLICATE_ID'};
    scores.set(item.id, item.score);
  }
  return {ok: true, scores};
}

export class LocalModelLanguageCore {
  constructor({endpoint = process.env.CONTINUUM_CCC1_MODEL_URL, model = process.env.CONTINUUM_CCC1_MODEL ?? 'local',
    base = new LexicalLanguageCore(), weight = 10, shortlist = 12, timeoutMs = 20000, fetchImpl = globalThis.fetch} = {}) {
    this.id = 'local-model-v1';
    this.endpoint = loopbackEndpoint(endpoint);
    this.model = String(model); this.base = base; this.weight = weight;
    this.shortlist = shortlist; this.timeoutMs = timeoutMs; this.fetchImpl = fetchImpl;
    this.last = null;
  }

  prompt(goalText, shortlisted) {
    const lines = shortlisted.map((c, i) => `${i}: ${c.export} (${c.module}) – ${String(c.description ?? '').slice(0, 240)}`);
    return [
      {role: 'system', content: 'You rank existing functions for a coding repair goal. Answer ONLY with JSON ' +
        '{"ranking":[{"id":<number>,"score":<0..1>}]}. Use only the listed ids. Never invent functions.'},
      {role: 'user', content: `Goal: ${String(goalText).slice(0, 2000)}\nCandidates:\n${lines.join('\n')}`},
    ];
  }

  async rank(goalText, candidates) {
    const base = await this.base.rank(goalText, candidates);
    const started = Date.now();
    const done = (used, reason) => { this.last = {used, reason, latencyMs: Date.now() - started, model: this.model}; };
    if (!this.endpoint) { done(false, 'NO_ENDPOINT'); return base; }
    if (typeof this.fetchImpl !== 'function') { done(false, 'NO_FETCH'); return base; }
    const order = base.map((score, index) => ({score, index})).sort((a, b) => b.score - a.score || a.index - b.index)
      .slice(0, Math.min(this.shortlist, candidates.length));
    const shortlisted = order.map(o => candidates[o.index]);
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.timeoutMs);
    let text;
    try {
      const response = await this.fetchImpl(new URL('/v1/chat/completions', this.endpoint), {
        method: 'POST', signal: controller.signal, headers: {'content-type': 'application/json'},
        body: JSON.stringify({model: this.model, temperature: 0, max_tokens: 400, messages: this.prompt(goalText, shortlisted)}),
      });
      if (!response.ok) { done(false, 'HTTP_' + response.status); return base; }
      const body = await response.json();
      text = body?.choices?.[0]?.message?.content;
    } catch (error) {
      done(false, controller.signal.aborted ? 'TIMEOUT' : 'TRANSPORT'); return base;
    } finally { clearTimeout(timer); }
    const parsed = parseModelRanking(text, shortlisted.length);
    if (!parsed.ok) { done(false, parsed.reason); return base; }
    const combined = base.slice();
    order.forEach((o, i) => { if (parsed.scores.has(i)) combined[o.index] = base[o.index] + this.weight * parsed.scores.get(i); });
    done(true, null);
    return combined;
  }
}
