import test from "node:test";
import assert from "node:assert/strict";
import { inspectPinnedDeepSeek, readHubJson } from "./continuum-deepseek-hub-preflight.mjs";

const SHA = "1bae0e1088993b9b82de08f59de5be5a34e42170";
const WEIGHTS = 510296708312;
const BASE = "https://huggingface.co/api/models/deepseek-ai/DeepSeek-V4.1-Flash-1017";
function fixture() {
  return { sha: SHA, siblings: Array.from({ length: 48 }, (_, i) => ({
    rfilename: "model-" + String(i + 1).padStart(5, "0") + "-of-00048.safetensors",
    size: Math.floor(WEIGHTS / 48) + (i < WEIGHTS % 48 ? 1 : 0),
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
  assert.equal(m.calls.length, 1);
  assert.equal(m.calls[0].url, BASE + "/revision/" + SHA + "?blobs=true");
  assert.equal(m.calls[0].options.redirect, "error");
  assert.equal(result.shardsDeclared, 48);
  assert.equal(result.shardsWithPublishedLfsDigest, 48);
  assert.equal(result.declaredWeightBytes, WEIGHTS);
  assert.equal(result.verifiedWeightBytes, 0);
  assert.equal(result.actualWeightBytesDownloaded, 0);
  assert.equal(result.downloadStatus, "NOT_STARTED");
  assert.equal(result.inferenceStatus, "NOT_STARTED");
  assert.equal(result.serverCapacity.canDownloadWeights, "NO");
});
test("reject mutable identity and inconsistent pinned response", async () => {
  await assert.rejects(() => inspectPinnedDeepSeek(mockFetch({...fixture(), sha:"main"})), /revision/);
  await assert.rejects(() => inspectPinnedDeepSeek(mockFetch({...fixture(), sha:"2".repeat(40)})), /revision changed/);
});
test("missing shard blocks declared integrity claim", async () => {
  const bad=fixture();bad.siblings.pop();
  await assert.rejects(() => inspectPinnedDeepSeek(mockFetch(bad)), /Not all 48/);
});
test("absence of per-shard digests cannot become a verification success", async () => {
  const bad=fixture(); delete bad.siblings[0].lfs;
  const result=await inspectPinnedDeepSeek(mockFetch(bad));
  assert.equal(result.shardsWithPublishedLfsDigest,47);
  assert.equal(result.integrity,"METADATA_ONLY_NO_FILE_HASH_VERIFIED");
});
test("non-allowlisted URLs, timeouts and HTTP failures fail closed", async () => {
  await assert.rejects(() => readHubJson("https://evil.example/model"), /allowlisted/);
  await assert.rejects(() => readHubJson(BASE + "/revision/" + SHA + "?blobs=true", { timeoutMs: 0 }), /timeout/);
  await assert.rejects(() => readHubJson(BASE + "/revision/" + SHA + "?blobs=true", { fetchFn: async()=>new Response("fail", {status:503})}), /200/);
});
test("oversize and malformed metadata are rejected", async () => {
  await assert.rejects(() => readHubJson(BASE + "/revision/" + SHA + "?blobs=true", {
    fetchFn: async()=>new Response("{}",{status:200,headers:{"content-length":"99999999"}})
  }), /too large/);
  await assert.rejects(() => readHubJson(BASE + "/revision/" + SHA + "?blobs=true", {
    fetchFn: async()=>new Response("broken",{status:200})
  }), SyntaxError);
});
test("reject stale mutable metadata and altered inventory sizes", async () => {
  const f=fixture();f.siblings[5].size=-1;
  await assert.rejects(() => inspectPinnedDeepSeek(mockFetch(f)), /size/);
});

test("mutable latest URL cannot silently select another checkpoint", async () => {
  await assert.rejects(() => readHubJson(BASE + "?blobs=true", {fetchFn:async()=>{throw Error("UNEXPECTED_NETWORK");}}), /allowlisted/);
});
test("metadata deadline bounds a fetch that ignores AbortSignal", async () => {
  let alarm;
  try {
    await assert.rejects(Promise.race([
      readHubJson(BASE + "/revision/" + SHA + "?blobs=true", {timeoutMs:100,fetchFn:()=>new Promise(()=>{})}),
      new Promise((_,reject)=>{alarm=setTimeout(()=>reject(Error("TEST_FETCH_HUNG")),500);})
    ]), /metadata timeout/i);
  } finally {clearTimeout(alarm);}
});
