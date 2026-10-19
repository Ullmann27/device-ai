import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const activity = fs.readFileSync(
  new URL("../../app/src/main/java/at/heinz/deviceai/AgentActivity.kt", import.meta.url), "utf8"
);
const client = fs.readFileSync(
  new URL("../../app/src/main/java/at/heinz/deviceai/AgentBackendClient.kt", import.meta.url), "utf8"
);
const backend = fs.readFileSync(new URL("../../backend/src/server.js", import.meta.url), "utf8");

test("external mentor has an explicit per-request consent UI", () => {
  assert.match(activity, /private fun showGroqMentorDialog\(/);
  assert.match(activity, /setTitle\("GroqCloud · einmalige externe Anfrage"\)/);
  assert.match(activity, /setPositiveButton\("EINMALIG SENDEN"\)/);
  assert.match(activity, /setNegativeButton\("LOKAL BLEIBEN"/);
  assert.match(activity, /backend\.groqMentor\(content\)/);
  assert.match(activity, /request\.startsWith\("mentor:", ignoreCase = true\)/);
});

test("Android only sends bounded question text to the private backend", () => {
  const start = client.indexOf("fun groqMentor(");
  const end = client.indexOf("private fun request(", start);
  assert.ok(start >= 0 && end > start);
  const body = client.slice(start, end);
  assert.match(body, /put\("consent", true\)/);
  assert.match(body, /put\("provider", "GROQ"\)/);
  assert.match(body, /put\("question", question\.trim\(\)\)/);
  assert.doesNotMatch(body, /Build\.MODEL|shizuku_ready|manufacturer|sendDevice|put\("memory"|put\("device"/);
  assert.match(body, /"POST", "\$base\/v1\/mentor\/answer"/);
  assert.match(body, /UNVERIFIED_EXTERNAL/);
});

test("server never accepts mentor request without owner grant and bearer authentication", () => {
  assert.match(backend, /app\.post\("\/v1\/mentor\/answer", auth,/);
  assert.match(backend, /req\.body\?\.consent !== true/);
  assert.match(backend, /"consent\|provider\|question"/);
  assert.doesNotMatch(backend, /app\.post\("\/v1\/mentor\/answer",[^\n]*tool_choice/);
});

test("external reply is labelled unverified and cannot write memory or perform tools", () => {
  assert.match(activity, /EXTERNER KI-MENTOR · GROQ · UNBESTÄTIGT/);
  assert.match(client, /json\.optJSONArray\("tool_calls"\)\?\.length\(\) == 0/);
  assert.match(client, /!json\.optBoolean\("memory_written", true\)/);
  assert.doesNotMatch(
    activity.slice(activity.indexOf("private fun showGroqMentorDialog("),
      activity.indexOf("private fun showCloudSettings()")),
    /importSnapshot|confirmFact|ToolGate\(|setMode\(AssistantMode\.CLOUD/
  );
});
