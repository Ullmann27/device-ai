#!/usr/bin/env node
/**
 * Read-only Hub metadata preflight for an explicitly chosen DeepSeek checkpoint.
 * Does not download weights, allocate GPU instances or change credentials.
 * The mutable HEAD is only a discovery step; its commit is pinned and re-read.
 */
import { inspectHubManifest, buildStoragePlan, MODEL_ID } from "./continuum-deepseek-tier-plan.mjs";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";

const HUB_BASE = "https://huggingface.co/api/models/" + MODEL_ID;
const SHA40 = /^[a-f0-9]{40}$/;
const MAX_METADATA_BYTES = 4 * 1024 * 1024;

export async function readHubJson(url, { fetchFn = fetch, timeoutMs = 20000 } = {}) {
  if (!Number.isInteger(timeoutMs) || timeoutMs < 100 || timeoutMs > 60000) {
    throw Error("Invalid metadata timeout");
  }
  if (url !== HUB_BASE + "?blobs=true" &&
      !(url.startsWith(HUB_BASE + "/revision/") &&
        SHA40.test(url.slice((HUB_BASE + "/revision/").length).replace(/\?blobs=true$/, "")) &&
        url.endsWith("?blobs=true"))) {
    throw Error("Rejected non-allowlisted metadata URL");
  }
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetchFn(url, {
      method: "GET", redirect: "error",
      headers: { "accept": "application/json" },
      signal: controller.signal
    });
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
        const { value, done } = await reader.read();
        if (done) break;
        count += value.byteLength;
        if (count > MAX_METADATA_BYTES) throw Error("Hub metadata body too large");
        chunks.push(value);
      }
    } finally {
      await reader.cancel().catch(() => {});
    }
    const text = Buffer.concat(chunks, count).toString("utf8");
    const json = JSON.parse(text);
    if (json === null || typeof json !== "object" || Array.isArray(json)) {
      throw Error("Hub metadata must be a JSON object");
    }
    return json;
  } finally {
    clearTimeout(timeout);
  }
}

export async function inspectPinnedDeepSeek({ fetchFn = fetch, timeoutMs = 20000 } = {}) {
  const latest = await readHubJson(HUB_BASE + "?blobs=true", { fetchFn, timeoutMs });
  const revision = latest.sha;
  if (typeof revision !== "string" || !SHA40.test(revision)) {
    throw Error("Hub did not provide an immutable 40-character revision");
  }
  const pinnedUrl = HUB_BASE + "/revision/" + revision + "?blobs=true";
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
