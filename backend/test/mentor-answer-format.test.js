import test from "node:test";
import assert from "node:assert/strict";
import { GroqMentor } from "../src/groq-mentor.js";
import {
  formatMentorAnswer, MAX_MENTOR_ANSWER_CHARS
} from "../src/mentor-answer-format.js";

function reply(content, finishReason = "stop") {
  return new Response(JSON.stringify({
    choices: [{ finish_reason: finishReason, message: { role: "assistant", content } }]
  }), { status: 200, headers: { "Content-Type": "application/json" } });
}

test("long Groq answers are preserved beyond old 1800 character limit", async () => {
  const longAnswer = ("1. SWB1 verarbeitet lokale Muster.\n" +
    "2. CRE1 ist ein experimenteller Encoder.\n").repeat(41);
  assert.ok(longAnswer.length > 1800);
  assert.ok(longAnswer.length < MAX_MENTOR_ANSWER_CHARS);
  const mentor = new GroqMentor({
    enabled: true, apiKey: "gsk_mock_credential_not_live",
    fetcher: async () => reply(longAnswer)
  });
  const output = await mentor.answer("Welche Funktionen sind in Continuum schon vorhanden?");
  assert.equal(output.ok, true);
  assert.equal(output.message, longAnswer.trim());
  assert.ok(output.message.length > 1800);
  assert.equal(output.trust, "UNVERIFIED_EXTERNAL");
  assert.deepEqual(output.tool_calls, []);
  assert.equal(output.memory_written, false);
});

test("table markdown becomes plain, legible text without losing values", () => {
  const text = formatMentorAnswer(
    "**Aktuelle Funktionen**\n" +
    "| Komponente | Vorhanden | Geplant |\n" +
    "|---|---|---|\n" +
    "| **SWB1** | Lokaler Kern | Feinere Muster |\n" +
    "### Nächste Schritte\n" +
    "1. **Lokale Tests** behalten."
  );
  assert.match(text, /Aktuelle Funktionen/);
  assert.match(text, /SWB1: Lokaler Kern — Feinere Muster/);
  assert.match(text, /Nächste Schritte/);
  assert.match(text, /Lokale Tests behalten/);
  assert.doesNotMatch(text, /\|---|\*\*|^###/m);
});

test("oversized answers end at a boundary and explicitly signal shortening", () => {
  const text = formatMentorAnswer("Vollständiger Satz. ".repeat(300));
  assert.ok(text.length <= MAX_MENTOR_ANSWER_CHARS);
  assert.match(text, /Vollständiger Satz\.\n\n\[Antwort gekürzt/);
  assert.ok(text.length > 3600);
});

test("provider token-limit completion is explicitly marked as incomplete", async () => {
  const mentor = new GroqMentor({
    enabled: true, apiKey: "gsk_mock_credential_not_live",
    fetcher: async () => reply("1. SWB1 verarbeitet lokale Muster.", "length")
  });
  const out = await mentor.answer("Bitte erkläre die Kernfunktionen.");
  assert.equal(out.ok, true);
  assert.match(out.message, /Tokenlimit/);
});

test("unverified mentor has no current device or repo state", async () => {
  const mentor = new GroqMentor({
    enabled: true, apiKey: "gsk_mock_credential_not_live",
    fetcher: async (_url, options) => {
      const body = JSON.parse(options.body);
      const system = body.messages[0].content;
      assert.match(system, /keine Markdown-Tabellen/);
      assert.match(system, /Erfinde niemals ausgeschriebene Namen/);
      assert.match(system, /weder den aktuellen Gerätezustand noch das Repository/);
      assert.equal(body.messages.length, 2);
      assert.equal(Object.hasOwn(body, "tools"), false);
      return reply("Continuum hat einen lokalen Lernkern.");
    }
  });
  assert.equal((await mentor.answer("Welche Funktionen existieren?")).ok, true);
});
