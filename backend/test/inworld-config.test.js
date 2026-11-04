import test from "node:test";
import assert from "node:assert/strict";
import * as mentors from "../src/groq-mentor.js";

test("Inworld server configuration uses only its own environment credential and defaults", async () => {
  assert.equal(typeof mentors.createInworldMentor, "function");
  let calls = 0;
  const client = mentors.createInworldMentor({
    env: { INWORLD_MENTOR_ENABLED: "true", INWORLD_API_KEY: "synthetic-inworld-env-key",
      GROQ_API_KEY: "must-not-use-groq", OPENAI_API_KEY: "must-not-use-openai" },
    fetcher: async (_url, options) => {
      calls++;
      assert.equal(options.headers.Authorization, "Basic synthetic-inworld-env-key");
      const request = JSON.parse(options.body);
      assert.equal(request.model, "inworld/models/gemma-4-31b-it");
      assert.deepEqual(request.extra_body, { reasoning: { effort: "medium" } });
      return new Response(JSON.stringify({ choices: [{ message: { role: "assistant", content: "OK" } }] }));
    }
  });
  assert.equal(client.status().remainingToday, 10);
  assert.equal((await client.answer("Bitte antworte kurz.")).ok, true);
  assert.equal(calls, 1);
});

test("Inworld does not borrow another provider key and is opt-in even when its key exists", async () => {
  assert.equal(typeof mentors.createInworldMentor, "function");
  for (const env of [{ INWORLD_MENTOR_ENABLED: "true", GROQ_API_KEY: "synthetic-groq-key" },
    { INWORLD_API_KEY: "synthetic-inworld-env-key" }]) {
    const client = mentors.createInworldMentor({ env, fetcher: () => assert.fail("No provider call allowed") });
    assert.equal(client.status().available, false);
    assert.deepEqual(await client.answer("Bitte antworten"), { ok: false, code: "DISABLED" });
  }
});

test("Inworld operator settings select model, effort and daily budget without client control", async () => {
  assert.equal(typeof mentors.createInworldMentor, "function");
  const client = mentors.createInworldMentor({
    env: { INWORLD_MENTOR_ENABLED: "true", INWORLD_API_KEY: "synthetic-inworld-env-key",
      INWORLD_MENTOR_MODEL: "inworld/my-router", INWORLD_MENTOR_REASONING_EFFORT: "low",
      INWORLD_MENTOR_DAILY_LIMIT: "1" },
    fetcher: async (_url, options) => {
      const request = JSON.parse(options.body);
      assert.equal(request.model, "inworld/my-router");
      assert.deepEqual(request.extra_body, { reasoning: { effort: "low" } });
      return new Response(JSON.stringify({ choices: [{ message: { content: "OK" } }] }));
    }
  });
  assert.equal((await client.answer("Bitte antworten")).ok, true);
  assert.deepEqual(await client.answer("Bitte nochmals"), { ok: false, code: "DAILY_LIMIT" });
});
