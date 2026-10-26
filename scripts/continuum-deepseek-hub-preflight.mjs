#!/usr/bin/env node
/**
 * Read-only Hub metadata preflight for an explicitly chosen DeepSeek checkpoint.
 * Does not download weights, allocate GPU instances or change credentials.
 * Reads only the selected immutable revision; a moving HEAD cannot change the teacher.
 */
import { inspectHubManifest, buildStoragePlan, MODEL_ID, PINNED_REVISION } from "./continuum-deepseek-tier-plan.mjs";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";

const HUB_BASE = "https://huggingface.co/api/models/" + MODEL_ID;
const PINNED_URL = HUB_BASE + "/revision/" + PINNED_REVISION + "?blobs=true";
const MAX_METADATA_BYTES = 4 * 1024 * 1024;

export async function readHubJson(url, { fetchFn = fetch, timeoutMs = 20000 } = {}) {
  if (!Number.isInteger(timeoutMs) || timeoutMs < 100 || timeoutMs > 60000) {
    throw Error("Invalid metadata timeout");
  }
  if (url !== PINNED_URL) throw Error("Rejected non-allowlisted metadata URL");
  const controller = new AbortController();
  let timeout;
  const expired = new Promise((_, reject) => {
    timeout = setTimeout(() => {controller.abort(); reject(Error("Hub metadata timeout"));}, timeoutMs);
  });
  // Some adapters do not honor AbortSignal. Bound fetch and body reads as well.
  const bounded = promise => Promise.race([promise, expired]);
  try {
    const response = await bounded(fetchFn(url, {
      method: "GET", redirect: "error",
      headers: { "accept": "application/json" },
      signal: controller.signal
    }));
    if (!response || response.status !== 200) throw Error("Hub metadata request did not return HTTP 200");
    const declaredLength = Number(response.headers?.get?.("content-length") ?? 0);
    if (!Number.isFinite(declaredLength) || declaredLength > MAX_METADATA_BYTES) {
      throw Error("Hub metadata body too large");
    }
    if (!response.body?.getReader) throw Error("Hub metadata body unavailable");
    const reader = response.body.getReader();
    const chunks = [];
    let count = 0;
    try {
      while (true) {
        const { value, done } = await bounded(reader.read());
        if (done) break;
        count += value.byteLength;
        if (count > MAX_METADATA_BYTES) throw Error("Hub metadata body too large");
        chunks.push(value);
      }
    } finally {
      reader.cancel().catch(() => {});
      reader.releaseLock();
    }
    const text = new TextDecoder("utf-8", {fatal:true}).decode(Buffer.concat(chunks, count));
    const json = JSON.parse(text);
    if (json === null || typeof json !== "object" || Array.isArray(json)) {
      throw Error("Hub metadata must be a JSON object");
    }
    return json;
  } finally {
    clearTimeout(timeout);
    controller.abort();
  }
}

export async function inspectPinnedDeepSeek({ fetchFn = fetch, timeoutMs = 20000 } = {}) {
  const revision = PINNED_REVISION;
  const pinnedUrl = PINNED_URL;
  const pinned = await readHubJson(pinnedUrl, { fetchFn, timeoutMs });
  if (pinned.sha !== revision) throw Error("Pinned model revision changed");
  const inventory = inspectHubManifest(pinned);
  const missingPerShardDigests = pinned.siblings.filter(
    file => /^model-\d{5}-of-00048\.safetensors$/.test(file?.rfilename ?? "") &&
            !/^[a-f0-9]{64}$/.test(file.lfs?.oid ?? "")
  ).length;
  return {
    schema: "CONTINUUM_PINNED_DEEPSEEK_HUB_PREFLIGHT_V1",
    modelId: MODEL_ID,
    metadataSource: pinnedUrl,
    revision,
    declaredWeightBytes: inventory.weightsBytes,
    shardsDeclared: inventory.weightShards,
    shardsWithPublishedLfsDigest: inventory.weightShards - missingPerShardDigests,
    actualWeightBytesDownloaded: 0,
    verifiedWeightBytes: 0,
    integrity: "METADATA_ONLY_NO_FILE_HASH_VERIFIED",
    downloadStatus: "NOT_STARTED",
    inferenceStatus: "NOT_STARTED",
    costIncurredByThisCommand: false,
    serverCapacity: buildStoragePlan({ weightBytes: inventory.weightsBytes, gpuNames: [], hostRamGiB: 0 })
  };
}

if (process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url) {
  if (process.argv.length !== 2) {
    process.stderr.write("Usage: node scripts/continuum-deepseek-hub-preflight.mjs\n");
    process.exitCode = 2;
  } else {
    inspectPinnedDeepSeek().then(
      result => process.stdout.write(JSON.stringify(result, null, 2) + "\n"),
      err => { process.stderr.write("BLOCKED_HUB_METADATA: " + err.message + "\n"); process.exitCode = 1; }
    );
  }
}
