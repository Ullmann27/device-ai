import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";

export const MODEL_ID = "deepseek-ai/DeepSeek-V4.1-Flash-1017";
// Selected revision for the optional #154 transport. Generic inventory inspection
// below remains a read-only declaration and does not attest these weight bytes.
export const PINNED_REVISION = "1bae0e1088993b9b82de08f59de5be5a34e42170";
export const ESTIMATED_WEIGHT_BYTES = 511_000_000_000;
export const ENGRAM_OFFLOAD_GIB = 183.1;
const GiB = 1024 ** 3;
const GIB = n => Math.ceil(n / GiB);
const SHARD = /^model-(\d{5})-of-00048\.safetensors$/;
const SHA40 = /^[a-f0-9]{40}$/;

export function inspectHubManifest(input) {
  if (input === null || typeof input !== "object" || Array.isArray(input) ||
      !SHA40.test(input.sha ?? "") || !Array.isArray(input.siblings)) {
    throw new Error("Hub snapshot must have a pinned 40-digit hexadecimal revision and siblings");
  }
  const shards = new Map();
  for (const file of input.siblings) {
    const name = file?.rfilename;
    if (typeof name !== "string") continue;
    const match = SHARD.exec(name);
    if (!match) continue;
    const index = Number(match[1]);
    if (index < 1 || index > 48 || shards.has(index)) {
      throw new Error("Unexpected or duplicate weight shard number");
    }
    if (!Number.isSafeInteger(file.size) || file.size <= 0) {
      throw new Error("Missing positive weight-shard size");
    }
    shards.set(index, { filename: name, bytes: file.size });
  }
  if (shards.size !== 48) throw new Error("Not all 48 weight shards were declared");
  const weightsBytes = [...shards.values()].reduce((n, v) => n + v.bytes, 0);
  if (weightsBytes < 450_000_000_000 || weightsBytes > 600_000_000_000) {
    throw new Error("Declared weights differ materially from reference footprint");
  }
  return {
    repo: MODEL_ID,
    pinnedRevision: input.sha,
    weightsBytes,
    weightShards: 48,
    estimated: false,
    shards: [...shards].sort((a,b)=>a[0]-b[0]).map(([,entry])=>entry)
  };
}

export function buildStoragePlan({
  weightBytes = ESTIMATED_WEIGHT_BYTES,
  networkVolumeGiB = 768,
  gpuNames = [],
  hostRamGiB = 384
} = {}) {
  if (!Number.isSafeInteger(weightBytes) || weightBytes <= 0 ||
      !Number.isInteger(networkVolumeGiB) || networkVolumeGiB < 0 ||
      !Number.isInteger(hostRamGiB) || hostRamGiB < 0 ||
      !Array.isArray(gpuNames) || gpuNames.length > 64 ||
      gpuNames.some(x=>typeof x!=="string" || x.length>180)) {
    throw new Error("Invalid storage or hardware capacity parameters");
  }
  // 25% installation/cache headroom plus 100GiB for model artifacts and logs.
  // This is a conservative planning policy, not a guaranteed vLLM requirement.
  const installationBytes = Math.ceil(weightBytes * 1.25 + 100 * GiB);
  const h200Count = gpuNames.filter(x=>/\bH200\b/i.test(x)).length;
  const blockers = [];
  if (networkVolumeGiB * GiB < installationBytes) blockers.push("PERSISTENT_VOLUME_TOO_SMALL");
  if (h200Count < 4) blockers.push("FOUR_H200_IN_ONE_COMPATIBLE_HOST_NOT_VERIFIED");
  if (hostRamGiB < 384) blockers.push("CONSERVATIVE_HOST_RAM_BUDGET_NOT_MET");
  const plan = {
    schema: "CONTINUUM_VIRTUAL_MODEL_FABRIC_CAPACITY_V1",
    repo: MODEL_ID,
    weightBytes,
    recommendedVolumeGiB: GIB(installationBytes),
    provisionedVolumeGiB: networkVolumeGiB,
    h200VerifiedCount: h200Count,
    hostRamGiB,
    engramCpuOffloadGiB: ENGRAM_OFFLOAD_GIB,
    steps: [
      "Pin immutable Hugging Face revision and inspect model weight-shard inventory",
      "Use authorized persistent network volume in the SAME datacenter as compatible 4xH200 GPU host",
      "Download immutable revision once via Hugging Face Xet/cache onto that volume",
      "Verify model shard inventory and immutable source revision before any model serving",
      "Use the official vLLM 1017 mixed-precision model recipe with Engram CPU offload",
      "Expose serving only over authenticated private HTTPS/tunnel with explicit owner cloud consent",
      "Keep on-device Gemma and private SWB1 as offline fallback; never implicitly upload personal memories"
    ],
    blockers,
    canDownloadWeights: blockers.length === 0 ? "HOST_PREFLIGHT_REQUIRED" : "NO",
    canServeModel: "UNVERIFIED_REQUIRES_REAL_GPU_AND_NATIVE_VLLM_SMOKE",
    createsComputeOrCharges: false,
    executesModel: false,
    storesWeightBytes: false
  };
  return plan;
}

export function fromHubMetadata(metadata, capacity) {
  const manifest = inspectHubManifest(metadata);
  return { manifest, plan: buildStoragePlan({ ...capacity, weightBytes: manifest.weightsBytes }) };
}

if (process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url) {
  if (process.argv.length > 3) {
    process.stderr.write("Usage: node scripts/continuum-deepseek-tier-plan.mjs [local-hub-model-info.json]\n");
    process.exitCode = 2;
  } else {
    try {
      const input = process.argv[2] ? JSON.parse(readFileSync(process.argv[2],"utf8")) : null;
      const plan = input ? fromHubMetadata(input,{gpuNames:[],hostRamGiB:0}) :
        buildStoragePlan({gpuNames:[],hostRamGiB:0});
      process.stdout.write(JSON.stringify(plan,null,2)+"\n");
    } catch (error) {
      process.stderr.write(error.message+"\n");
      process.exitCode = 1;
    }
  }
}
