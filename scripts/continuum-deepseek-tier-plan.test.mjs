import test from "node:test";
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { readFileSync } from "node:fs";
import * as tier from "./continuum-deepseek-tier-plan.mjs";

const REVISION = "1bae0e1088993b9b82de08f59de5be5a34e42170";
const WEIGHTS = 510296708312;
const GiB = 1024 ** 3;
const H200 = Array.from({length:4},() => "NVIDIA H200 141GiB");
const fakeHub = () => ({
  id: tier.MODEL_ID, sha: REVISION,
  siblings: Array.from({length:48},(_,i) => ({
    rfilename: "model-" + String(i+1).padStart(5,"0") + "-of-00048.safetensors",
    size: Math.floor(WEIGHTS / 48) + (i < WEIGHTS % 48 ? 1 : 0)
  }))
});

test("capacity and transport identity stay tied to the existing selected teacher config", () => {
  const teacher = JSON.parse(readFileSync(new URL('../config/continuum-selected-models.json',import.meta.url),'utf8')).roles.research_teacher;
  assert.equal(tier.MODEL_ID,teacher.model_id);
  assert.equal(tier.PINNED_REVISION,teacher.pinned_hub_revision);
  assert.equal(tier.DECLARED_WEIGHT_BYTES,teacher.declared_weight_bytes);
  assert.equal(teacher.declared_weight_shards,48);
});

test("selected model snapshot is exact, immutable and reports byte/GiB units", () => {
  assert.equal(tier.PINNED_REVISION, REVISION);
  assert.equal(tier.DECLARED_WEIGHT_BYTES, WEIGHTS);
  const inventory = tier.inspectHubManifest(fakeHub());
  assert.equal(inventory.pinnedRevision, REVISION);
  assert.equal(inventory.weightShards, 48);
  assert.equal(inventory.weightsBytes, WEIGHTS);
  assert.equal(inventory.weightsGiB, WEIGHTS / GiB);
  assert.equal(inventory.estimated, false);
});

test("GPU marketing names and virtual storage never establish serving capability", () => {
  const plan = tier.buildStoragePlan({networkVolumeGiB:768,gpuNames:H200,hostRamGiB:384});
  assert.equal(plan.weightBytes, WEIGHTS);
  assert.equal(plan.weightGiB, WEIGHTS / GiB);
  assert.equal(plan.recommendedVolumeGiB, 695);
  assert.equal(plan.runtimeCapacityVerified, false);
  assert.equal(plan.canDownloadWeights, "NO");
  assert.equal(plan.canServeModel, "UNVERIFIED_REQUIRES_REAL_RUNTIME_SMOKE");
  assert.ok(plan.blockers.includes("AUTHORIZED_DOWNLOAD_NOT_GRANTED"));
  assert.ok(plan.blockers.includes("RUNTIME_MEMORY_AND_TOPOLOGY_NOT_MEASURED"));
  assert.equal(plan.blockers.some(x=>/H200|384/.test(x)), false);
  assert.equal(plan.createsComputeOrCharges, false);
  assert.equal(plan.executesModel, false);
});

test("capacity arithmetic separates full weights from active MoE compute/offload", () => {
  const plan = tier.buildStoragePlan({networkVolumeGiB:4096,gpuNames:[],hostRamGiB:1024});
  assert.equal(plan.memoryModel.fullWeightResidencyGiB, WEIGHTS / GiB);
  assert.equal(plan.memoryModel.activeParameterCount, null);
  assert.equal(plan.memoryModel.kvCacheAndRuntimeGiB, null);
  assert.equal(plan.memoryModel.cpuOffloadGiB, null);
  assert.equal(plan.memoryModel.gpuResidencyGiB, null);
  assert.equal(plan.memoryModel.activeMoeComputeReducesStoredWeightBytes, false);
  assert.equal(plan.memoryModel.storageCanReplaceRam, false);
});

test("transfer lower bound uses decimal Mbps, GiB bytes and explicit utilization", () => {
  const x = tier.estimateTransfer({bytes:WEIGHTS,bandwidthMbps:1000,utilization:0.8});
  assert.equal(x.seconds, WEIGHTS * 8 / (1000 * 1e6 * 0.8));
  assert.equal(x.hours, x.seconds / 3600);
  assert.equal(x.bytes, WEIGHTS);
  assert.equal(x.gib, WEIGHTS / GiB);
  assert.equal(x.isMeasured, false);
  assert.equal(tier.estimateTransfer({bytes:WEIGHTS,bandwidthMbps:1000,utilization:1}).seconds,
    WEIGHTS * 8 / 1e9);
  for (const bad of [{bandwidthMbps:0},{bandwidthMbps:Infinity},{utilization:0},{utilization:1.01},
    {bytes:Number.MAX_SAFE_INTEGER+1}]) assert.throws(()=>tier.estimateTransfer(bad), /Invalid/);
});

test("capacity rejects arithmetic overflow and independently reports storage policy", () => {
  assert.ok(tier.buildStoragePlan({networkVolumeGiB:512}).blockers.includes("PERSISTENT_VOLUME_TOO_SMALL"));
  for (const bad of [{networkVolumeGiB:-1},{hostRamGiB:Number.MAX_SAFE_INTEGER},
    {networkVolumeGiB:Number.MAX_SAFE_INTEGER},{weightBytes:Number.MAX_SAFE_INTEGER}])
    assert.throws(()=>tier.buildStoragePlan(bad), /Invalid/);
});

test("reject moving, other or materially changed snapshots before capacity calculations", () => {
  const template = fakeHub();
  for (const sha of ["main", "a".repeat(40)])
    assert.throws(()=>tier.inspectHubManifest({...template,sha}), /revision/);
  assert.throws(()=>tier.inspectHubManifest({...template,id:"someone-else/model"}), /model/);
  assert.throws(()=>tier.inspectHubManifest({...template,siblings:template.siblings.slice(1)}), /Not all/);
  assert.throws(()=>tier.inspectHubManifest({...template,siblings:[...template.siblings,template.siblings[0]]}), /duplicate/);
  assert.throws(()=>tier.inspectHubManifest({...template,siblings:template.siblings.map(s=>({...s,size:s.size+1}))}), /footprint/);
  assert.throws(()=>tier.inspectHubManifest({...template,siblings:template.siblings.map(s=>({...s,size:Number.MAX_SAFE_INTEGER}))}), /size|footprint/);
  const outcome = tier.fromHubMetadata(template, {networkVolumeGiB:768});
  assert.equal(outcome.plan.weightBytes, WEIGHTS);
});

test("CLI remains a read-only blocked plan without download authority or runtime proof", () => {
  const file = fileURLToPath(new URL("./continuum-deepseek-tier-plan.mjs",import.meta.url));
  const report = JSON.parse(execFileSync(process.execPath,[file],{encoding:"utf8",timeout:10_000}));
  assert.equal(report.canDownloadWeights,"NO");
  assert.equal(report.executesModel,false);
  assert.equal(report.pinnedRevision,REVISION);
});
