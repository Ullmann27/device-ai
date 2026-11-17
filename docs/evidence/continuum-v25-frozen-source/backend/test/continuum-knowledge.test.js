import assert from "node:assert/strict";
import test from "node:test";
import { selectResearchKnowledge } from "../src/continuum-knowledge.js";

const observations = [
  {
    id: "obs-1", query: "Android Kotlin",
    summary: "Zwei öffentliche Quellen zu Kotlin Android, nicht als Wahrheit bestätigt.",
    citations: [{ title: "Kotlin source", url: "https://developer.android.com/kotlin" }],
    uncertainty: 0.8, createdAt: 100, trust: "WEB_OBSERVATION", authority: "NONE"
  },
  {
    id: "obs-2", query: "Neural learning",
    summary: "Literaturhinweis zu neuronalen Lernprozessen ohne Faktenautorität.",
    citations: [{ title: "Open research", url: "https://doi.org/10.1000/learning" }],
    uncertainty: 0.7, createdAt: 200, trust: "WEB_OBSERVATION", authority: "NONE"
  },
  {
    id: "obs-3", query: "Unsafe note",
    summary: "This note is forbidden to promote.",
    citations: [{ title: "Unsafe", url: "https://example.org" }],
    createdAt: 300, trust: "WEB_OBSERVATION", authority: "CONFIRMED"
  },
  {
    id: "obs-4", query: "Unsafe address",
    summary: "Unsafe record must never be retrievable.",
    citations: [{ title: "Localhost", url: "http://127.0.0.1/" }],
    createdAt: 400, trust: "WEB_OBSERVATION", authority: "NONE"
  }
];

test("public memory retrieval respects query, citations and epistemic boundaries", () => {
  const output = selectResearchKnowledge({ observations }, { query: "Kotlin" });
  assert.equal(output.length, 1);
  assert.equal(output[0].id, "obs-1");
  assert.equal(output[0].authority, "NONE");
  assert.equal(output[0].trust, "WEB_OBSERVATION");
  assert.deepEqual(output[0].citations.map(c => c.url), ["https://developer.android.com/kotlin"]);
});
test("unverified research is never silently promoted to trusted memory", () => {
  const result = selectResearchKnowledge({ observations }, { limit: 8 });
  assert.deepEqual(result.map(x => x.id), ["obs-2", "obs-1"]);
  assert.ok(result.every(x => x.authority === "NONE"));
});
test("oversized queries and limits are rejected", () => {
  assert.throws(() => selectResearchKnowledge({ observations }, { query: "x".repeat(201) }), /query/);
  assert.throws(() => selectResearchKnowledge({ observations }, { limit: 100 }), /limit/);
});
