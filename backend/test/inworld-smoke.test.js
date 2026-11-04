import test from "node:test";
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const script = fileURLToPath(new URL("../scripts/inworld-smoke.mjs", import.meta.url));
const key = "synthetic-smoke-key-never-real";
const noNetwork = "globalThis.fetch = async () => { throw new Error('UNEXPECTED_NETWORK'); };";

function run(args = [], env = {}, fixture = noNetwork) {
  const child = spawnSync(process.execPath, ["--import", "data:text/javascript," + encodeURIComponent(fixture), script, ...args], {
    env, encoding: "utf8", timeout: 3000
  });
  assert.equal(child.error, undefined);
  return { code: child.status, text: child.stdout + child.stderr };
}

test("Inworld smoke test never runs without the explicit live-test flag", () => {
  const result = run([], { INWORLD_MENTOR_ENABLED: "true", INWORLD_API_KEY: key });
  assert.equal(result.code, 2);
  assert.match(result.text, /LIVE_TEST_NOT_CONFIRMED/);
  assert.doesNotMatch(result.text, /UNEXPECTED_NETWORK/);
  assert.equal(result.text.includes(key), false);
});

test("Inworld smoke test reports a missing server key before any network operation", () => {
  const result = run(["--confirm-live"], { INWORLD_MENTOR_ENABLED: "true" });
  assert.equal(result.code, 2);
  assert.match(result.text, /INWORLD_API_KEY_MISSING/);
  assert.doesNotMatch(result.text, /UNEXPECTED_NETWORK/);
});

test("Inworld smoke test respects disabled provider configuration", () => {
  const result = run(["--confirm-live"], { INWORLD_API_KEY: key });
  assert.equal(result.code, 2);
  assert.match(result.text, /INWORLD_MENTOR_DISABLED/);
  assert.doesNotMatch(result.text, /UNEXPECTED_NETWORK/);
});

test("Inworld smoke test prints only result flags and never key, prompt, response text or reasoning", () => {
  const fixture = `globalThis.fetch = async () => new Response(JSON.stringify({
    model: "inworld/gemma-4-31b-it", choices: [{ finish_reason: "stop", message: {
      content: "synthetic answer must never be logged", reasoning: "hidden reasoning"
    } }]
  }));`;
  const result = run(["--confirm-live"], { INWORLD_MENTOR_ENABLED: "true", INWORLD_API_KEY: key }, fixture);
  assert.equal(result.code, 0);
  const flags = JSON.parse(result.text);
  assert.equal(flags.ok, true);
  assert.equal(flags.textReceived, true);
  assert.equal(flags.provider, "INWORLD");
  for (const hidden of [key, "synthetic answer", "hidden reasoning", "Antworte ausschließlich"]) {
    assert.equal(result.text.includes(hidden), false);
  }
});

test("Inworld smoke test sanitizes configuration errors", () => {
  const result = run(["--confirm-live"], {
    INWORLD_MENTOR_ENABLED: "true", INWORLD_API_KEY: key,
    INWORLD_MENTOR_REASONING_EFFORT: "invalid-secret-like-config"
  });
  assert.equal(result.code, 2);
  assert.match(result.text, /INWORLD_CONFIGURATION_INVALID/);
  assert.equal(result.text.includes(key), false);
  assert.doesNotMatch(result.text, /invalid-secret-like-config|UNEXPECTED_NETWORK/);
});
