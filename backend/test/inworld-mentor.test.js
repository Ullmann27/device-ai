import test from "node:test";
import assert from "node:assert/strict";
import { GroqMentor } from "../src/groq-mentor.js";

const KEY = "inworld-test-credential-already-encoded";
const MODEL = "inworld/models/gemma-4-31b-it";
const SERVED_MODEL = "inworld/gemma-4-31b-it";
const QUESTION = "Wie funktioniert ein Elektromotor?";

function completion(message = "Ein Elektromotor wandelt elektrische Energie in Bewegung um.", extra = {}) {
  return new Response(JSON.stringify({
    id: "chatcmpl-test", object: "chat.completion", created: 1793800000,
    model: SERVED_MODEL,
    choices: [{ index: 0, finish_reason: "stop", message: { role: "assistant", content: message } }],
    usage: { prompt_tokens: 12, completion_tokens: 15, total_tokens: 27 }, ...extra
  }), { headers: { "Content-Type": "application/json" } });
}

function mentor(overrides = {}) {
  return new GroqMentor({ provider: "INWORLD", enabled: true, apiKey: KEY,
    model: MODEL, dailyLimit: 2, ...overrides });
}

test("Inworld sends the configured credential unchanged with Basic and the Playground reasoning body", async () => {
  let calls = 0;
  const client = mentor({ fetcher: async (url, options) => {
    calls++;
    assert.equal(url, "https://api.inworld.ai/v1/chat/completions");
    assert.equal(options.method, "POST");
    assert.equal(options.redirect, "error");
    assert.equal(options.headers.Authorization, "Basic " + KEY);
    const body = JSON.parse(options.body);
    assert.equal(body.model, MODEL);
    assert.deepEqual(body.extra_body, { reasoning: { effort: "medium" } });
    assert.equal(body.stream, false);
    assert.equal(body.max_completion_tokens, 2048);
    assert.equal(body.messages.length, 2);
    assert.equal(body.messages[1].content, QUESTION);
    for (const field of ["tools", "history", "device", "metadata", "web_search", "reasoning_effort", "include_reasoning", "n"]) {
      assert.equal(Object.hasOwn(body, field), false, field);
    }
    assert.equal(JSON.stringify(body).includes(KEY), false);
    return completion();
  }});
  const answer = await client.answer(QUESTION);
  assert.equal(answer.ok, true);
  assert.equal(answer.provider, "INWORLD");
  assert.equal(answer.model, SERVED_MODEL);
  assert.equal(answer.trust, "UNVERIFIED_EXTERNAL");
  assert.deepEqual(answer.tool_calls, []);
  assert.equal(answer.memory_written, false);
  assert.equal(calls, 1);
  assert.equal(JSON.stringify(client.status()).includes(KEY), false);
});

test("Inworld configuration requires enablement and a nonblank server credential", async () => {
  let calls = 0;
  for (const config of [{ enabled: false }, { apiKey: "" }, { apiKey: "   " }]) {
    const client = mentor({ ...config, fetcher: async () => { calls++; return completion(); } });
    assert.equal(client.status().available, false);
    assert.deepEqual(await client.answer(QUESTION), { ok: false, code: "DISABLED" });
  }
  assert.equal(calls, 0);
});

test("Inworld validates reasoning before requests and accepts a server-selected effort", async () => {
  assert.throws(() => mentor({ reasoningEffort: "ultra" }), /Denkstufe/);
  assert.throws(() => mentor({ reasoningEffort: { effort: "high" } }), /Denkstufe/);
  const client = mentor({ reasoningEffort: "low", fetcher: async (_url, options) => {
    assert.deepEqual(JSON.parse(options.body).extra_body, { reasoning: { effort: "low" } });
    return completion();
  }});
  assert.equal((await client.answer(QUESTION)).ok, true);
});

test("Inworld returns only text and actual model identity, discarding reasoning and tool proposals", async () => {
  const client = mentor({ fetcher: async () => completion("Eine reine Textantwort.", {
    choices: [{ index: 0, finish_reason: "stop", message: {
      role: "assistant", content: "Eine reine Textantwort.", reasoning: "hidden reasoning",
      tool_calls: [{ id: "call_1", type: "function", function: { name: "delete_file", arguments: "{}" } }]
    } }], metadata: { internal: KEY }
  }) });
  assert.deepEqual(await client.answer(QUESTION), {
    ok: true, message: "Eine reine Textantwort.", model: SERVED_MODEL,
    provider: "INWORLD", trust: "UNVERIFIED_EXTERNAL", tool_calls: [], memory_written: false
  });
});

test("Inworld errors are sanitized, typed and do not cause a retry or provider fallback", async () => {
  for (const [status, expected] of [[400, "INWORLD_REQUEST_REJECTED"], [401, "INWORLD_KEY_REJECTED"],
    [403, "INWORLD_KEY_REJECTED"], [404, "INWORLD_MODEL_UNAVAILABLE"],
    [429, "UPSTREAM_LIMIT"], [503, "UPSTREAM_UNAVAILABLE"]]) {
    let calls = 0;
    const client = mentor({ fetcher: async () => {
      calls++;
      return new Response(JSON.stringify({ error: { message: KEY + " private response" } }), { status });
    }});
    assert.deepEqual(await client.answer(QUESTION), { ok: false, code: expected });
    assert.equal(calls, 1);
  }
});

test("Inworld reasoning-only or empty completions never repeat a billable request", async () => {
  for (const [reason, expected] of [["length", "RESPONSE_TOKEN_LIMIT"], ["stop", "EMPTY_RESPONSE"]]) {
    let calls = 0;
    const client = mentor({ fetcher: async () => {
      calls++;
      return completion(null, { choices: [{ index: 0, finish_reason: reason,
        message: { role: "assistant", content: null, reasoning: "hidden reasoning" } }] });
    }});
    assert.deepEqual(await client.answer(QUESTION), { ok: false, code: expected });
    assert.equal(calls, 1);
  }
});

test("Inworld rejects invalid prompts without spending quota and bounds each UTC day", async () => {
  let now = Date.UTC(2026, 10, 4, 12);
  let calls = 0;
  const client = mentor({ dailyLimit: 1, clock: () => now, fetcher: async () => { calls++; return completion(); } });
  for (const bad of ["", "ab", "x".repeat(1801), "hello\u0000there", {}]) {
    assert.equal((await client.answer(bad)).code, "INVALID_QUESTION");
  }
  assert.equal(calls, 0);
  assert.equal(client.status().remainingToday, 1);
  assert.equal((await client.answer(QUESTION)).ok, true);
  assert.equal((await client.answer(QUESTION)).code, "DAILY_LIMIT");
  assert.equal(calls, 1);
  now += 86400000;
  assert.equal((await client.answer(QUESTION)).ok, true);
  assert.equal(calls, 2);
});

test("Inworld bounds malformed and oversized upstream responses", async () => {
  for (const [body, expected] of [["not JSON", "UPSTREAM_UNAVAILABLE"], ["x".repeat(65537), "RESPONSE_TOO_LARGE"]]) {
    const client = mentor({ fetcher: async () => new Response(body) });
    assert.deepEqual(await client.answer(QUESTION), { ok: false, code: expected });
  }
});

test("Inworld rejects concurrent questions and releases its lease after failure", async () => {
  let reject;
  const client = mentor({ fetcher: () => new Promise((_resolve, fail) => { reject = fail; }) });
  const first = client.answer(QUESTION);
  assert.deepEqual(await client.answer(QUESTION), { ok: false, code: "BUSY" });
  reject(new Error(KEY));
  assert.deepEqual(await first, { ok: false, code: "UPSTREAM_UNAVAILABLE" });
  const second = client.answer(QUESTION);
  reject(new Error(KEY));
  assert.deepEqual(await second, { ok: false, code: "UPSTREAM_UNAVAILABLE" });
});

test("Inworld cancellation aborts upstream and prevents a pre-cancelled request", async () => {
  let calls = 0;
  const client = mentor({ fetcher: (_url, { signal }) => {
    calls++;
    return new Promise((_resolve, reject) => signal.addEventListener("abort", () => reject(signal.reason), { once: true }));
  }});
  const cancel = new AbortController();
  const pending = client.answer(QUESTION, { signal: cancel.signal });
  cancel.abort();
  assert.deepEqual(await pending, { ok: false, code: "REQUEST_CANCELLED" });
  assert.deepEqual(await client.answer(QUESTION, { signal: cancel.signal }), { ok: false, code: "REQUEST_CANCELLED" });
  assert.equal(calls, 1);
  assert.equal(client.status().remainingToday, 1);
});

test("Inworld enforces its overall deadline and releases the request slot", async (t) => {
  t.mock.timers.enable({ apis: ["setTimeout"] });
  let aborted = false;
  const client = mentor({ fetcher: (_url, { signal }) => new Promise((_resolve, reject) => {
    signal.addEventListener("abort", () => { aborted = true; reject(signal.reason); }, { once: true });
  }) });
  const pending = client.answer(QUESTION);
  t.mock.timers.tick(44_999);
  assert.equal(aborted, false);
  t.mock.timers.tick(1);
  assert.deepEqual(await pending, { ok: false, code: "UPSTREAM_TIMEOUT" });
  assert.equal(aborted, true);
  const again = client.answer(QUESTION);
  t.mock.timers.tick(45_000);
  assert.deepEqual(await again, { ok: false, code: "UPSTREAM_TIMEOUT" });
});

test("Inworld closes the upstream transport after an early HTTP error", async () => {
  let transportSignal;
  const client = mentor({ fetcher: async (_url, { signal }) => {
    transportSignal = signal;
    return new Response("unconsumed error body", { status: 503 });
  }});
  assert.equal((await client.answer(QUESTION)).code, "UPSTREAM_UNAVAILABLE");
  assert.equal(transportSignal.aborted, true);
});
