import test from "node:test";
import assert from "node:assert/strict";
import { GroqMentor } from "../src/groq-mentor.js";

const FAKE_KEY = "sk-or-v1-test-only-placeholder-never-real";

function success(message = "Eine isolierte Mentorantwort.") {
  return new Response(JSON.stringify({
    choices: [{ finish_reason: "stop", message: { role: "assistant", content: message } }]
  }), { status: 200, headers: { "Content-Type": "application/json" } });
}

test("OpenRouter stays off without secret or explicit enablement", async () => {
  let networkCalls = 0;
  const mentor = new GroqMentor({
    provider: "OPENROUTER", enabled: true, apiKey: "",
    model: "openai/gpt-4o", fetcher: () => { networkCalls++; return success(); }
  });
  assert.equal(mentor.status().available, false);
  assert.deepEqual(await mentor.answer("Erkläre Solarpanels"), { ok: false, code: "DISABLED" });
  assert.equal(networkCalls, 0);
});

test("OpenRouter uses its pinned HTTPS endpoint, a bounded one-off prompt and returns no tools", async () => {
  const requests = [];
  const mentor = new GroqMentor({
    provider: "OPENROUTER", enabled: true, apiKey: FAKE_KEY, model: "openai/gpt-4o",
    fetcher: async (url, options) => { requests.push({ url, options }); return success(); }
  });
  const answer = await mentor.answer("Was ist Photovoltaik?");
  assert.equal(answer.ok, true);
  assert.equal(answer.provider, "OPENROUTER");
  assert.equal(answer.trust, "UNVERIFIED_EXTERNAL");
  assert.equal(answer.memory_written, false);
  assert.deepEqual(answer.tool_calls, []);
  assert.equal(requests.length, 1);
  assert.equal(requests[0].url, "https://openrouter.ai/api/v1/chat/completions");
  assert.equal(requests[0].options.redirect, "error");
  assert.equal(requests[0].options.headers.Authorization, "Bearer " + FAKE_KEY);
  const request = JSON.parse(requests[0].options.body);
  assert.equal(request.model, "openai/gpt-4o");
  assert.equal(request.stream, false);
  assert.equal(request.messages[1].content, "Was ist Photovoltaik?");
  assert.equal(request.tools, undefined);
  assert.equal(request.history, undefined);
  assert.equal(request.device, undefined);
  assert.equal(JSON.stringify(request).includes(FAKE_KEY), false);
});

test("OpenRouter errors never expose upstream key or private payload", async () => {
  const mentor = new GroqMentor({
    provider: "OPENROUTER", enabled: true, apiKey: FAKE_KEY,
    model: "openai/gpt-4o",
    fetcher: async () => new Response(
      JSON.stringify({ error: { message: "secret internal data " + FAKE_KEY } }), { status: 401 })
  });
  assert.deepEqual(await mentor.answer("Schreibe eine Antwort"), {
    ok: false, code: "OPENROUTER_KEY_REJECTED"
  });
});

test("OpenRouter rejects too-long prompts without calling provider", async () => {
  let calls = 0;
  const mentor = new GroqMentor({
    provider: "OPENROUTER", enabled: true, apiKey: FAKE_KEY,
    model: "openai/gpt-4o", fetcher: () => { calls++; return success(); }
  });
  assert.equal((await mentor.answer("X".repeat(1801))).code, "INVALID_QUESTION");
  assert.equal(calls, 0);
});
