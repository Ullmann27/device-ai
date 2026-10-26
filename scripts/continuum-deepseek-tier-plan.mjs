import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";

export const MODEL_ID = "deepseek-ai/DeepSeek-V4.1-Flash-1017";
export const PINNED_REVISION = "1bae0e1088993b9b82de08f59de5be5a34e42170";
export const DECLARED_WEIGHT_BYTES = 510_296_708_312;
// Compatibility alias. This is pinned publisher metadata, not verified local bytes.
export const ESTIMATED_WEIGHT_BYTES = DECLARED_WEIGHT_BYTES;
const GiB = 1024 ** 3;
const GIB = n => Math.ceil(n / GiB);
const SHARD = /^model-(\d{5})-of-00048\.safetensors$/;
const SHA40 = /^[a-f0-9]{40}$/;

export function inspectHubManifest(input) {
  if (input === null || typeof input !== "object" || Array.isArray(input) ||
      !SHA40.test(input.sha ?? "") || input.sha !== PINNED_REVISION || !Array.isArray(input.siblings)) {
    throw new Error("Hub snapshot must match selected immutable revision and declare siblings");
  }
  if (input.id !== undefined && input.id !== MODEL_ID) throw Error("Unexpected model identity");
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
  if (!Number.isSafeInteger(weightsBytes) || weightsBytes !== DECLARED_WEIGHT_BYTES) {
    throw new Error("Declared weights differ materially from reference footprint");
  }
  return {
    repo: MODEL_ID,
    pinnedRevision: input.sha,
    weightsBytes,
    weightsGiB: weightsBytes / GiB,
    weightShards: 48,
    estimated: false,
    shards: [...shards].sort((a,b)=>a[0]-b[0]).map(([,entry])=>entry)
  };
}

/** Decimal link Mbps; binary GiB. A lower-bound estimate, never measured throughput. */
export function estimateTransfer({bytes = DECLARED_WEIGHT_BYTES, bandwidthMbps = 1000, utilization = 0.8} = {}) {
  if (!Number.isSafeInteger(bytes) || bytes <= 0 ||
      !Number.isFinite(bandwidthMbps) || bandwidthMbps <= 0 || bandwidthMbps > 1e9 ||
      !Number.isFinite(utilization) || utilization <= 0 || utilization > 1) {
    throw Error("Invalid transfer assumptions");
  }
  const seconds = bytes * 8 / (bandwidthMbps * 1e6 * utilization);
  return {bytes, gib: bytes / GiB, bandwidthMbps, utilization, seconds, hours: seconds / 3600,
    isMeasured: false, excludes: ["protocol overhead beyond utilization", "verification", "retries", "cache misses", "model conversion"]};
}

export function buildStoragePlan({
  weightBytes = DECLARED_WEIGHT_BYTES,
  networkVolumeGiB = 768,
  gpuNames = [],
  hostRamGiB = 0
} = {}) {
  const safeGiB = n => Number.isSafeInteger(n) && n >= 0 && Number.isSafeInteger(n * GiB);
  if (!Number.isSafeInteger(weightBytes) || weightBytes !== DECLARED_WEIGHT_BYTES ||
      !Number.isSafeInteger(Math.ceil(weightBytes * 1.25 + 100 * GiB)) ||
      !safeGiB(networkVolumeGiB) || !safeGiB(hostRamGiB) ||
      !Array.isArray(gpuNames) || gpuNames.length > 64 ||
      gpuNames.some(x=>typeof x!=="string" || x.length>180)) {
    throw new Error("Invalid storage or hardware capacity parameters");
  }
  // Local planning policy: 25% headroom plus 100GiB. Not an official runtime requirement.
  const installationBytes = Math.ceil(weightBytes * 1.25 + 100 * GiB);
  const blockers = [];
  if (networkVolumeGiB * GiB < installationBytes) blockers.push("PERSISTENT_VOLUME_TOO_SMALL");
  blockers.push("AUTHORIZED_DOWNLOAD_NOT_GRANTED", "RUNTIME_MEMORY_AND_TOPOLOGY_NOT_MEASURED");
  return {
    schema: "CONTINUUM_VIRTUAL_MODEL_FABRIC_CAPACITY_V2",
    repo: MODEL_ID, pinnedRevision: PINNED_REVISION,
    weightBytes, weightGiB: weightBytes / GiB,
    weightIntegrity: "PUBLISHER_METADATA_ONLY_NO_LOCAL_SHARD_HASH_VERIFICATION",
    storagePolicy: {weightMultiplier: 1.25, additionalGiB: 100, isRuntimeRequirement: false},
    recommendedVolumeGiB: GIB(installationBytes),
    provisionedVolumeGiB: networkVolumeGiB,
    reportedGpuNames: [...gpuNames], hostRamGiB, runtimeCapacityVerified: false,
    memoryModel: {
      fullWeightResidencyGiB: weightBytes / GiB,
      activeParameterCount: null,
      routedExperts: 384, activeRoutedExpertsPerToken: 6, sharedExperts: 1,
      architectureSource: "https://huggingface.co/" + MODEL_ID + "/blob/" + PINNED_REVISION + "/config.json",
      activeMoeComputeReducesStoredWeightBytes: false,
      cpuOffloadGiB: null, gpuResidencyGiB: null, kvCacheAndRuntimeGiB: null,
      storageCanReplaceRam: false,
      assumptions: [
        "MoE routing reduces per-token expert compute; all checkpoint shards still need storage and accessible weight residency",
        "CPU offload depends on supported kernels, DRAM bandwidth and CPU/GPU interconnect; no offload amount is measured",
        "Quantized file bytes do not establish runtime allocation; conversion, scales, activations, KV cache and context/batch size add memory",
        "Network/object storage cannot substitute for measured GPU/host RAM or a compatible serving topology"
      ]
    },
    transferScenarios: [100, 1000, 10000].map(bandwidthMbps => estimateTransfer({bytes: weightBytes, bandwidthMbps})),
    steps: [
      "Inspect the selected immutable 48-shard snapshot; metadata is not local file integrity",
      "Obtain separate owner authorization and existing no-charge storage/compute capacity before a weight download",
      "Verify each shard hash and source revision before model serving",
      "Measure a supported pinned runtime recipe, conversion format, CPU/GPU topology, context/KV budget and throughput",
      "Expose only authenticated HTTPS with immutable server identity and per-job consent/bounds",
      "Keep local/offline chat available; do not implicitly upload owner memory or promote teacher replies into training"
    ],
    blockers, canDownloadWeights: "NO",
    canServeModel: "UNVERIFIED_REQUIRES_REAL_RUNTIME_SMOKE",
    createsComputeOrCharges: false, executesModel: false, storesWeightBytes: false
  };
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
