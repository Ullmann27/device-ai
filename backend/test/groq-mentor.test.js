import test from "node:test";
import assert from "node:assert/strict";
import { GroqMentor } from "../src/groq-mentor.js";

function reply(message = "Solarzellen nutzen den photovoltaischen Effekt.") {
  return new Response(JSON.stringify({
    choices: [{ message: { role: "assistant", content: message } }]
  }), { status: 200, headers: { "Content-Type": "application/json" } });
}

const KEY = "gsk_test_key_only_for_mock_calls_not_real";

test("mentor is opt-in disabled and never invokes external fetch without key", async () => {
  let called = 0;
  const mentor = new GroqMentor({
    enabled: true, apiKey: "", fetcher: () => { called++; return reply(); }
  });
  assert.equal(mentor.status().available, false);
  assert.deepEqual(await mentor.answer("Wie funktionieren Solarzellen?"), { ok: false, code: "DISABLED" });
  assert.equal(called, 0);
});

test("one authorized message is bounded and contains no tools, memory or device context", async () => {
  const calls = [];
  const mentor = new GroqMentor({
    enabled: true, apiKey: KEY, fetcher: async (url, options) => {
      calls.push({ url, options });
      return reply();
    }
  });
  const result = await mentor.answer("Wie funktionieren Solarzellen?");
  assert.equal(result.ok, true);
  assert.equal(result.provider, "GROQ");
  assert.equal(result.trust, "UNVERIFIED_EXTERNAL");
  assert.equal(result.memory_written, false);
  assert.deepEqual(result.tool_calls, []);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, "https://api.groq.com/openai/v1/chat/completions");
  assert.equal(calls[0].options.headers.Authorization, "Bearer " + KEY);
  assert.equal(calls[0].options.redirect, "error");
  const payload = JSON.parse(calls[0].options.body);
  assert.equal(payload.model, "openai/gpt-oss-20b");
  assert.equal(payload.messages.length, 2);
  assert.equal(payload.messages[1].content, "Wie funktionieren Solarzellen?");
  assert.equal(payload.max_completion_tokens, 450);
  assert.equal(payload.stream, false);
  assert.equal(payload.tool_choice, "none");
  assert.equal(Object.hasOwn(payload, "device"), false);
  assert.equal(Object.hasOwn(payload, "history"), false);
  assert.equal(Object.hasOwn(payload, "tools"), false);
  assert.equal(JSON.stringify(payload).includes(KEY), false);
});

test("input length validation happens before network and before quota consumption", async () => {
  let calls = 0;
  const mentor = new GroqMentor({
    enabled: true, apiKey: KEY,
    dailyLimit: 1, fetcher: async () => { calls++; return reply(); }
  });
  assert.equal((await mentor.answer("a".repeat(1801))).code, "INVALID_QUESTION");
  assert.equal((await mentor.answer("a")).code, "INVALID_QUESTION");
  assert.equal(calls, 0);
  assert.equal((await mentor.answer("Solarzellen erklären")).ok, true);
  assert.equal((await mentor.answer("Noch eine Frage")).code, "DAILY_LIMIT");
  assert.equal(calls, 1);
});

test("daily quota is isolated by UTC day and refuses concurrent calls", async () => {
  let current = Date.UTC(2026, 9, 19, 5);
  let fulfill;
  let calls = 0;
  const mentor = new GroqMentor({
    enabled: true, apiKey: KEY, dailyLimit: 1,
    clock: () => current,
    fetcher: () => { calls++; return new Promise(resolve => { fulfill = resolve; }); }
  });
  const pending = mentor.answer("Bitte erklären");
  assert.equal((await mentor.answer("Wieso reagiert nichts?")).code, "BUSY");
  fulfill(reply("Es funktioniert."));
  assert.equal((await pending).ok, true);
  assert.equal((await mentor.answer("Erneut erklären")).code, "DAILY_LIMIT");
  current = Date.UTC(2026, 9, 20, 5);
  assert.equal(mentor.status().remainingToday, 1);
  const again = mentor.answer("Neuer Versuch");
  fulfill(reply());
  assert.equal((await again).ok, true);
  assert.equal(calls, 2);
});

test("upstream errors do not expose API response contents or key", async () => {
  const mentor = new GroqMentor({
    enabled: true, apiKey: KEY, fetcher: async () =>
      new Response('{"error":{"message":"private secret from provider"}}', { status: 401 })
  });
  assert.deepEqual(await mentor.answer("Testfrage an Groq"), {
    ok: false, code: "GROQ_KEY_REJECTED"
  });
});

test("Groq throttling and malformed output are non-tool error responses", async () => {
  let mode = "throttle";
  const mentor = new GroqMentor({
    enabled: true, apiKey: KEY,
    fetcher: async () => mode === "throttle"
      ? new Response('rate limited', { status: 429 })
      : new Response('{"choices":[{"message":{"content":""}}]}', { status: 200 })
  });
  assert.equal((await mentor.answer("Testfrage")).code, "UPSTREAM_LIMIT");
  mode = "blank";
  assert.equal((await mentor.answer("Testfrage")).code, "EMPTY_RESPONSE");
});

test("oversized responses are rejected without parsing or logging private contents", async () => {
  const mentor = new GroqMentor({
    enabled: true, apiKey: KEY,
    fetcher: async () => new Response("x".repeat(70 * 1024), { status: 200 })
  });
  assert.equal((await mentor.answer("Übergroßes Ergebnis")).code, "RESPONSE_TOO_LARGE");
});

test("mentor ignores whitespace around environment key and never follows a redirect", async () => {
  const mentor = new GroqMentor({
    enabled: true,
    apiKey: "  " + KEY + "  ",
    fetcher: async (_url, options) => {
      assert.equal(options.headers.Authorization, "Bearer " + KEY);
      assert.equal(options.redirect, "error");
      return reply();
    }
  });
  assert.equal((await mentor.answer("Warum scheint die Sonne?")).ok, true);
});

test("provider rejects unknown models and invalid requests without exposing response bodies", async () => {
  for (const [status, expected] of [
    [404, "GROQ_MODEL_UNAVAILABLE"],
    [400, "GROQ_REQUEST_REJECTED"],
    [422, "GROQ_REQUEST_REJECTED"],
    [503, "UPSTREAM_UNAVAILABLE"]
  ]) {
    const mentor = new GroqMentor({
      enabled: true,
      apiKey: KEY,
      fetcher: async () => new Response(
        '{"error":{"message":"internal credential do not print"}}',
        { status }
      )
    });
    assert.deepEqual(await mentor.answer("Bitte erklären"), {
      ok: false, code: expected
    });
  }
});
