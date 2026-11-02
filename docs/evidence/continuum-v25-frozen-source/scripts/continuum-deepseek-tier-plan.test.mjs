import test from "node:test";
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { buildStoragePlan, inspectHubManifest, fromHubMetadata, MODEL_ID } from "./continuum-deepseek-tier-plan.mjs";

const H200 = Array.from({length:4},() => "NVIDIA H200 141GiB");
const fakeHub = () => ({
  sha: "a".repeat(40),
  siblings: Array.from({length:48},(_,i) => ({
    rfilename: "model-" + String(i+1).padStart(5,"0") + "-of-00048.safetensors",
    size: 10_625_000_000
  }))
});

test("capacity planning is honest about verified requirements and no resource creation", () => {
  const plan=buildStoragePlan({ networkVolumeGiB: 768, gpuNames: H200, hostRamGiB: 384});
  assert.equal(plan.repo,MODEL_ID);
  assert.equal(plan.blockers.length,0);
  assert.equal(plan.canDownloadWeights,"HOST_PREFLIGHT_REQUIRED");
  assert.equal(plan.canServeModel,"UNVERIFIED_REQUIRES_REAL_GPU_AND_NATIVE_VLLM_SMOKE");
  assert.equal(plan.createsComputeOrCharges,false);
  assert.equal(plan.storesWeightBytes,false);
  assert.ok(plan.recommendedVolumeGiB > 512);
  assert.ok(plan.recommendedVolumeGiB <= 768);
});

test("virtual storage does not pretend to be GPU RAM or substitute for physical host", () => {
  const plan=buildStoragePlan({ networkVolumeGiB: 4096, gpuNames: [], hostRamGiB: 1024});
  assert.deepEqual(plan.blockers,["FOUR_H200_IN_ONE_COMPATIBLE_HOST_NOT_VERIFIED"]);
  assert.equal(plan.canDownloadWeights,"NO");
});
test("reject too-small volume, GPU count and host DRAM independently", () => {
  const plan=buildStoragePlan({
    networkVolumeGiB:512,
    gpuNames:H200.slice(0,3),
    hostRamGiB:256
  });
  assert.deepEqual(plan.blockers,[
    "PERSISTENT_VOLUME_TOO_SMALL",
    "FOUR_H200_IN_ONE_COMPATIBLE_HOST_NOT_VERIFIED",
    "CONSERVATIVE_HOST_RAM_BUDGET_NOT_MET"
  ]);
});
test("immutable snapshot metadata validates all shard identities and size before use", () => {
  const inventory=inspectHubManifest(fakeHub());
  assert.equal(inventory.pinnedRevision,"a".repeat(40));
  assert.equal(inventory.weightShards,48);
  assert.equal(inventory.weightsBytes,510_000_000_000);
  assert.equal(inventory.estimated,false);
  const outcome=fromHubMetadata(fakeHub(),{networkVolumeGiB:768, gpuNames:H200, hostRamGiB:384});
  assert.equal(outcome.plan.weightBytes,inventory.weightsBytes);
  assert.equal(outcome.plan.blockers.length,0);
});
test("reject moving refs, missing, duplicate, bad shard size or implausible metadata", () => {
  const template=fakeHub();
  assert.throws(()=>inspectHubManifest({...template,sha:"main"}),/pinned/);
  assert.throws(()=>inspectHubManifest({...template,siblings:template.siblings.slice(1)}),/Not all/);
  assert.throws(()=>inspectHubManifest({...template,siblings:[...template.siblings,template.siblings[0]]}),/duplicate/);
  assert.throws(()=>inspectHubManifest({...template,siblings:[...template.siblings.slice(0,47),{...template.siblings[47],size:0}]}),/size/);
  assert.throws(()=>inspectHubManifest({...template,siblings:template.siblings.map(s=>({...s,size:1000}))}),/footprint/);
  assert.throws(()=>buildStoragePlan({networkVolumeGiB:-1}),/Invalid/);
});
test("CLI defaults to a read-only blocked plan when no GPU inventory is available", () => {
  const file=fileURLToPath(new URL("./continuum-deepseek-tier-plan.mjs",import.meta.url));
  const report=JSON.parse(execFileSync(process.execPath,[file],{encoding:"utf8",timeout:10_000}));
  assert.equal(report.canDownloadWeights,"NO");
  assert.equal(report.executesModel,false);
  assert.ok(report.blockers.includes("FOUR_H200_IN_ONE_COMPATIBLE_HOST_NOT_VERIFIED"));
});
