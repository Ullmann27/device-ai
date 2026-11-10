import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const ROOT = fileURLToPath(new URL("../../", import.meta.url));
const kotlin = (path) => readFileSync(ROOT + path, "utf8");
const client = kotlin("app/src/main/java/at/heinz/deviceai/NvidiaNemotronDirectClient.kt");
const store = kotlin("app/src/main/java/at/heinz/deviceai/SecureTokenStore.kt");
const activity = kotlin("app/src/main/java/at/heinz/deviceai/AgentActivity.kt");

test("Nemotron uses pinned NVIDIA HTTPS endpoint, not an arbitrary host", () => {
  assert.match(client, /https:\/\/integrate\.api\.nvidia\.com\/v1\/chat\/completions/);
  assert.match(client, /nvidia\/nemotron-3-ultra-550b-a55b/);
  assert.match(client, /instanceFollowRedirects = false/);
  assert.match(client, /requestMethod = "POST"/);
  assert.match(client, /setRequestProperty\("Authorization", "Bearer \$key"\)/);
  assert.match(client, /"stream", false/);
});

test("BYOK token stored with Android Keystore AES-GCM; no embedded key", () => {
  assert.match(store, /fun saveNvidiaToken\(/);
  assert.match(store, /fun loadNvidiaToken\(/);
  assert.match(store, /fun clearNvidiaToken\(/);
  assert.match(store, /KEYSTORE = "AndroidKeyStore"/);
  assert.match(store, /AES\/GCM\/NoPadding/);
  assert.match(store, /nvapi-\[A-Za-z0-9_-\]\{32,240\}/);
  assert.doesNotMatch(client + store + activity, /nvapi-[A-Za-z0-9_-]{32,}/);
});

test("NVIDIA is explicit and local internet OFF blocks sending", () => {
  assert.match(activity, /showNvidiaNemotronKeyDialog\(\)/);
  assert.match(activity, /showNvidiaNemotronDialog\(\)/);
  assert.match(activity, /NVIDIA NEMOTRON 3 ULTRA · TESTFRAGE/);
  assert.match(activity, /EINMALIG AN NVIDIA SENDEN/);
  assert.match(activity, /internetConsent\.mode == at\.heinz\.deviceai\.cognitive\.InternetAccessMode\.OFF/);
  assert.match(client, /getSharedPreferences\("continuum-internet", Context\.MODE_PRIVATE\)/);
  assert.match(client, /getString\("mode", null\) == "OFF"/);
  assert.match(activity, /NvidiaNemotronDirectClient\(this\)\.answer\(content\)/);
  assert.doesNotMatch(activity, /NvidiaNemotronDirectClient\(this\)\.answer\(request\)/);
});

test("No device context, conversation history or tools sent to NVIDIA", () => {
  assert.match(client, /"role", "system"/);
  assert.match(client, /"role", "user"/);
  assert.match(client, /put\("content", question\)/);
  assert.doesNotMatch(client, /put\("(?:device|history|tool_outputs|session_id|tools|tool_choice)"/);
  assert.match(client, /MAX_QUESTION_CHARS = 1800/);
  assert.match(client, /MAX_RESPONSE_BYTES = 128 \* 1024/);
  assert.match(client, /DAILY_LIMIT = 5/);
  assert.match(client, /"enable_thinking", true/);
});

test("Remote response remains unverified and does not grant tool or memory writes", () => {
  assert.match(activity, /NVIDIA · NEMOTRON 3 ULTRA · EXTERN · UNBESTÄTIGT/);
  assert.match(activity, /Keine automatische Gedächtnisübernahme/);
  assert.match(client, /reasoning_content is deliberately neither shown nor saved/);
  assert.match(client, /connection\.disconnect\(\)/);
  assert.match(client, /connection\.contentLengthLong > MAX_RESPONSE_BYTES/);
});
