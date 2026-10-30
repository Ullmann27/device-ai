import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {priceGpuSeconds,planCloudGpuAdmission} from './continuum-cloud-gpu-admission.mjs';

const fixtureUrl=new URL('../../app/src/test/resources/compute-fabric-planning-v1.json',import.meta.url);
const sha='a'.repeat(64), revision='b'.repeat(40);
const readSnapshot=()=>{
 const s=JSON.parse(fs.readFileSync(fixtureUrl,'utf8'));
 s.nodes[0].nodeId='gpu-node';
 s.nodes[0].maxRamMb=1048576;
 s.nodes[0].maxRuntimeMillis=3600000;
 s.nodes[0].maxOutputBytes=4096;
 s.nodes[0].expiresAtMillis=12000;
 s.task.requiredRamMb=1024;
 s.task.maxRuntimeMillis=1000;
 s.task.deadlineMillis=10000;
 return s;
};
const request=(change={})=>({taskId:'fabric-coding-01',taskSha256:sha,
 modelId:'continuum/large-coder',revision,dataClassification:'PUBLIC',
 allowFallback:false,allowedFallbackModels:[],internetEnabled:true,ownerAllowsRemoteCompute:true,
 maxChargeMicrounits:20000,maxInputBytes:1024,maxOutputBytes:2048,deadlineMillis:9000,...change});
const job=(change={})=>({modelId:'continuum/large-coder',revision,
 weightBytes:6000000000,requiredStorageGiB:10,expectedComputeSeconds:600,
 maxChargeMicrounits:20000,dataClassification:'PUBLIC',...change});
const quote=(change={})=>({nodeId:'gpu-node',providerId:'rental',quoteId:'offer-1',
 modelId:'continuum/large-coder',revision,gpuCount:1,vramMiBPerGpu:24576,
 storageGiB:20,rateMicrousdPerHour:60000,billingQuantumSeconds:1,
 setupSeconds:0,expiresAtMillis:11000,capacityVerified:true,
 runtimeVerified:true,weightsVerified:true,endpointReady:true,...change});
const offer=(q=quote(),change={})=>({nodeId:q.nodeId,kind:'REMOTE_PROVIDER',
 modelId:'continuum/large-coder',revision,available:true,modelLoaded:true,
 ownerEnrolled:true,maxInputBytes:16000,maxOutputBytes:4096,
 declaredChargeMicrounits:priceGpuSeconds(q.rateMicrousdPerHour,600+q.setupSeconds,q.billingQuantumSeconds).quotedComputeMicrousd,
 expiresAtMillis:11000,...change});
function plan(options={}){
 const q=options.q??quote();
 return planCloudGpuAdmission({snapshotJson:JSON.stringify(options.snapshot??readSnapshot()),
  request:options.request??request(),modelOffers:options.offers??[offer(q)],
  quotes:options.quotes??[q],job:options.job??job(),nowMillis:options.nowMillis??2000});
}

test('canonical registry plus rental quote remain review-only; no GPU provisioned',()=>{
 const p=plan();
 assert.equal(p.status,'GPU_QUOTE_REVIEW_ONLY');
 assert.equal(p.selected.nodeId,'gpu-node');
 assert.equal(p.selected.quotedComputeMicrousd,10000);
 assert.equal(p.selected.billedSeconds,600);
 for(const key of ['performsNetworkRequests','performsInference','provisionsGpu',
   'chargesAccount','grantsPermission'])assert.equal(p[key],false);
 assert.equal(p.requiresLiveOwnerRegistryRecheck,true);
 assert.equal(p.requiresPerRequestConsent,true);
 assert.ok(Object.isFrozen(p.selected));
});

test('integer price rounds to billing quantum without float drift',()=>{
 assert.deepEqual(priceGpuSeconds(3600000,61,60),{
  billedSeconds:120,quotedComputeMicrousd:120000});
 assert.equal(priceGpuSeconds(1,1,1).quotedComputeMicrousd,1);
 assert.throws(()=>priceGpuSeconds(-1,1,1),/GPU_PRICE_INVALID/);
 assert.throws(()=>priceGpuSeconds(1000000000,86400,1),/GPU_PRICE_EXCEEDS_SUPPORTED_LIMIT/);
});

test('insufficient storage, weight VRAM and overbudget rental rejected',()=>{
 for(const [q,reason] of [
  [quote({storageGiB:2}),'GPU_STORAGE_INSUFFICIENT'],
  [quote({vramMiBPerGpu:1024}),'GPU_AGGREGATE_VRAM_BELOW_WEIGHTS'],
  [quote({rateMicrousdPerHour:600000}),'GPU_COST_OVER_BUDGET'],
  [quote({expiresAtMillis:1}),'GPU_QUOTE_EXPIRED'],
  [quote({revision:'c'.repeat(40)}),'GPU_QUOTE_MODEL_MISMATCH']]){
  const p=plan({q});
  assert.equal(p.selected,null,reason);
  assert.ok(p.evaluated[0].reasons.includes(reason),reason);
 }
});

test('unattested runtime, capacity, weights or endpoint never eligible',()=>{
 for(const change of [{capacityVerified:false},{runtimeVerified:false},
  {weightsVerified:false},{endpointReady:false}]){
  const p=plan({q:quote(change)});
  assert.equal(p.selected,null);
  assert.ok(p.evaluated[0].reasons.includes('GPU_RUNTIME_NOT_ATTESTED'));
 }
});

test('fake zero price and node outside registry rejected',()=>{
 const q=quote();
 const p=plan({offers:[offer(q,{declaredChargeMicrounits:0})]});
 assert.equal(p.selected,null);
 assert.ok(p.evaluated[0].reasons.includes('GPU_PRICE_DECLARATION_MISMATCH'));
 const other=quote({nodeId:'outside'});
 assert.ok(plan({q:other}).evaluated[0].reasons.includes('GPU_NOT_IN_AUTHORIZED_REGISTRY_PLAN'));
});

test('remote private-memory disclosure denied even with internet flag',()=>{
 const p=plan({request:request({dataClassification:'OWNER_PRIVATE'}),
  job:job({dataClassification:'OWNER_PRIVATE'})});
 assert.equal(p.selected,null);
 assert.ok(p.evaluated[0].reasons.includes('GPU_PRIVATE_CONTEXT_DENIED'));
});

test('canonical task hash, remote consent and expiry remain enforced',()=>{
 assert.throws(()=>plan({request:request({taskSha256:'d'.repeat(64)})}),/REGISTRY_TASK_BINDING_INVALID/);
 assert.equal(plan({request:request({ownerAllowsRemoteCompute:false})}).selected,null);
 assert.throws(()=>plan({nowMillis:10000}),/REGISTRY_SNAPSHOT_STALE/);
 assert.throws(()=>plan({quotes:[quote(),quote()]}),/GPU_DUPLICATE_QUOTE_NODE/);
});