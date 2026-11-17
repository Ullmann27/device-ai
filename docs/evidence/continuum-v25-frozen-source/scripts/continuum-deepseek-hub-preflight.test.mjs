import test from "node:test";
import assert from "node:assert/strict";
import { inspectPinnedDeepSeek, readHubJson } from "./continuum-deepseek-hub-preflight.mjs";

const SHA = "1".repeat(40);
const BASE = "https://huggingface.co/api/models/deepseek-ai/DeepSeek-V4.1-Flash-1017";
function fixture() {
  return { sha: SHA, siblings: Array.from({ length: 48 }, (_, i) => ({
    rfilename: "model-" + String(i + 1).padStart(5, "0") + "-of-00048.safetensors",
    size: 10_625_000_000,
    lfs: { oid: "a".repeat(64) }
  })) };
}
function mockFetch(first = fixture(), second = fixture()) {
  const calls = [];
  return {
    calls,
    fetchFn: async (url, options) => {
      calls.push({ url, options });
      return new Response(JSON.stringify(calls.length === 1 ? first : second), {
        status: 200, headers: { "content-type": "application/json" }
      });
    }
  };
}
test("snapshot verification binds second read to immutable revision and remains zero-cost", async () => {
  const m = mockFetch();
  const result = await inspectPinnedDeepSeek(m);
  assert.equal(m.calls.length, 2);
  assert.equal(m.calls[0].url, BASE + "?blobs=true");
  assert.equal(m.calls[1].url, BASE + "/revision/" + SHA + "?blobs=true");
  assert.equal(m.calls[0].options.redirect, "error");
  assert.equal(result.shardsDeclared, 48);
  assert.equal(result.shardsWithPublishedLfsDigest, 48);
  assert.equal(result.declaredWeightBytes, 510_000_000_000);
  assert.equal(result.verifiedWeightBytes, 0);
  assert.equal(result.actualWeightBytesDownloaded, 0);
  assert.equal(result.downloadStatus, "NOT_STARTED");
  assert.equal(result.inferenceStatus, "NOT_STARTED");
  assert.equal(result.serverCapacity.canDownloadWeights, "NO");
});
test("reject mutable identity and inconsistent pinned response", async () => {
  await assert.rejects(() => inspectPinnedDeepSeek(mockFetch({...fixture(), sha:"main"})), /immutable/);
  await assert.rejects(() => inspectPinnedDeepSeek(mockFetch(fixture(), {...fixture(), sha:"2".repeat(40)})), /revision changed/);
});
test("missing shard blocks declared integrity claim", async () => {
  const bad=fixture();bad.siblings.pop();
  await assert.rejects(() => inspectPinnedDeepSeek(mockFetch(fixture(), bad)), /Not all 48/);
});
test("absence of per-shard digests cannot become a verification success", async () => {
  const bad=fixture(); delete bad.siblings[0].lfs;
  const result=await inspectPinnedDeepSeek(mockFetch(fixture(), bad));
  assert.equal(result.shardsWithPublishedLfsDigest,47);
  assert.equal(result.integrity,"METADATA_ONLY_NO_FILE_HASH_VERIFIED");
});
test("non-allowlisted URLs, timeouts and HTTP failures fail closed", async () => {
  await assert.rejects(() => readHubJson("https://evil.example/model"), /allowlisted/);
  await assert.rejects(() => readHubJson(BASE + "?blobs=true", { timeoutMs: 0 }), /timeout/);
  await assert.rejects(() => readHubJson(BASE + "?blobs=true", { fetchFn: async()=>new Response("fail", {status:503})}), /200/);
});
test("oversize and malformed metadata are rejected", async () => {
  await assert.rejects(() => readHubJson(BASE + "?blobs=true", {
    fetchFn: async()=>new Response("{}",{status:200,headers:{"content-length":"99999999"}})
  }), /too large/);
  await assert.rejects(() => readHubJson(BASE + "?blobs=true", {
    fetchFn: async()=>new Response("broken",{status:200})
  }), SyntaxError);
});
test("reject stale mutable metadata and altered inventory sizes", async () => {
  const f=fixture();f.siblings[5].size=-1;
  await assert.rejects(() => inspectPinnedDeepSeek(mockFetch(fixture(),f)), /size/);
});
