import assert from "node:assert/strict";
import test from "node:test";
import { createPublicResearchFunction } from "../src/public-research.js";

const DATA = {
  wikipedia: { query: { search: [
    { title: "Künstliche Intelligenz", snippet: "Übersicht zu maschinellem Lernen" }
  ] } },
  openalex: { results: [
    { title: "Independent Research on Artificial Intelligence", doi: "https://doi.org/10.1000/ai-study", publication_year: 2025 },
    { title: "Artificial Intelligence Review Study", id: "https://openalex.org/W1234" }
  ] },
  crossref: { message: { items: [
    { title: ["Independent Research on Artificial Intelligence"], URL: "https://doi.org/10.1000/ai-study" },
    { title: ["Artificial Intelligence and Evaluation"], URL: "https://doi.org/10.1000/other-study" }
  ] } }
};

function mockFetch(calls, responses = DATA) {
  return async (url, options) => {
    calls.push({ url, options });
    const host = new URL(url).hostname;
    const key = host === "de.wikipedia.org" ? "wikipedia" :
      host === "api.openalex.org" ? "openalex" :
      host === "api.crossref.org" ? "crossref" : "unknown";
    if (!(key in responses)) throw Error("Mock source unavailable");
    const item = responses[key];
    if (item instanceof Error) throw item;
    return new Response(JSON.stringify(item), { status: 200,
      headers: { "content-type": "application/json" } });
  };
}

test("free research collects deduplicated cited metadata without a paid API", async () => {
  const calls = [];
  const result = await createPublicResearchFunction({ fetchImpl: mockFetch(calls) })({
    query: "Künstliche Intelligenz",
    target: { type: "topic", text: "Künstliche Intelligenz" }
  });
  assert.equal(calls.length, 3);
  assert.ok(calls.every(call => new URL(call.url).protocol === "https:" &&
    call.options.redirect === "error"));
  assert.ok(result.summary.includes("keine unabhängig bestätigten Aussagen"));
  assert.equal(result.citations.length, 4);
  assert.equal(result.citations.filter(x => x.url === "https://doi.org/10.1000/ai-study").length, 1);
  assert.equal(result.uncertainty, 0.75);
  assert.ok(result.contradictions.includes("nicht unabhängig"));
});

test("one unavailable source does not stop other public sources", async () => {
  const calls = [];
  const result = await createPublicResearchFunction({ fetchImpl: mockFetch(calls, {
    wikipedia: Error("Wikipedia offline"), openalex: DATA.openalex, crossref: DATA.crossref
  }) })({ query: "Android learning systems", target: { type: "topic", text: "Android" } });
  assert.equal(calls.length, 3);
  assert.ok(result.citations.length >= 2);
});

test("empty results never become made-up observations", async () => {
  const research = createPublicResearchFunction({ fetchImpl: mockFetch([], {
    wikipedia: { query: { search: [] } }, openalex: { results: [] },
    crossref: { message: { items: [] } }
  }) });
  await assert.rejects(research({ query: "unlikely test topic", target: { type: "gap", text: "unknown" } }),
    /Keine verwendbaren/);
});

test("unsafe citation URLs and oversized metadata responses are rejected", async () => {
  const research = createPublicResearchFunction({ fetchImpl: mockFetch([], {
    wikipedia: { query: { search: [{ title: "Benign article", snippet: "ok" }] } },
    openalex: { results: [{ title: "Harmful reference", doi: "http://127.0.0.1/secret" }] },
    crossref: { message: { items: [{ title: ["Unsafe reference"], URL: "https://evil.invalid:8443/" }] } }
  }) });
  const output = await research({ query: "public evidence", target: { type: "gap", text: "public evidence" } });
  assert.equal(output.citations.length, 1);
  assert.equal(output.uncertainty, 0.9);
  const huge = createPublicResearchFunction({
    fetchImpl: async () => new Response("{".repeat(400_000))
  });
  await assert.rejects(huge({ query: "public evidence", target: { type: "topic", text: "public evidence" } }),
    /Keine verwendbaren/);
});

test("query/output bounds and timeout input validation stay enforced", async () => {
  assert.throws(() => createPublicResearchFunction({ timeoutMs: 0 }), /timeout/);
  const calls = [];
  const output = await createPublicResearchFunction({ fetchImpl: mockFetch(calls) })({
    query: "a".repeat(1000), target: { type: "topic", text: "b".repeat(8000) }
  });
  assert.ok(calls.every(call => decodeURIComponent(new URL(call.url).search).length < 300));
  assert.ok(output.summary.length < 4901);
});
