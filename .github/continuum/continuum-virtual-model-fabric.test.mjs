import test from 'node:test';
import assert from 'node:assert/strict';
import {MODEL_ID} from '../../scripts/continuum-deepseek-tier-plan.mjs';
import {buildPinnedDeepSeekResourceManifest,planVirtualModelRoute,FABRIC_SCHEMA} from './continuum-virtual-model-fabric.mjs';

const revision='a'.repeat(40),localRevision='b'.repeat(40);
function request(extra={}){
 return {taskId:'test-0001',taskSha256:'b'.repeat(64),modelId:MODEL_ID,
  revision,dataClassification:'PUBLIC',allowFallback:true,
  allowedFallbackModels:[{modelId:'small-local',revision:localRevision}],
  internetEnabled:false,ownerAllowsRemoteCompute:false,
  maxChargeMicrounits:0,maxInputBytes:1024,maxOutputBytes:2048,
  deadlineMillis:30000,...extra};
}
function offer(extra={}){
 return {nodeId:'node-1',kind:'LOCAL_DEVICE',modelId:'small-local',
  revision:localRevision,available:true,modelLoaded:true,ownerEnrolled:true,
  maxInputBytes:4096,maxOutputBytes:4096,declaredChargeMicrounits:0,
  expiresAtMillis:30000,...extra};
}
const plan=(r=request(),offers=[offer()])=>planVirtualModelRoute({request:r,offers,nowMillis:10000});

test('local pinned fallback is advisory, not a fake 511GB inference',()=>{
 const result=plan();
 assert.equal(result.schema,FABRIC_SCHEMA);
 assert.equal(result.status,'ROUTE_PROPOSED_NOT_AUTHORIZED');
 assert.equal(result.selected.modelId,'small-local');
 assert.equal(result.fallbackEquivalentToRequestedModel,false);
 for(const k of ['downloadsWeights','performsInference','grantsPermission',
  'createsComputeOrCharges','realProviderReachabilityVerified'])assert.equal(result[k],false);
 assert.equal(result.selected.ownerEnrollmentAttested,false);
 assert.equal(result.selected.actualModelRevisionAttested,false);
 assert.ok(Object.isFrozen(result));assert.ok(Object.isFrozen(result.selected));
});

test('remote route requires separate internet and owner-compute consent',()=>{
 const remote=offer({kind:'REMOTE_PROVIDER',modelId:MODEL_ID,revision,
  declaredChargeMicrounits:900});
 const denied=plan(request({maxChargeMicrounits:5000}),[remote]);
 assert.equal(denied.selected,null);
 assert.ok(denied.reasons.includes('REMOTE_CONSENT_REQUIRED'));
 const proposed=plan(request({internetEnabled:true,ownerAllowsRemoteCompute:true,
  maxChargeMicrounits:5000}),[remote]);
 assert.equal(proposed.selected.exactRequestedRevision,true);
 assert.equal(proposed.performsInference,false);
});

test('private memories never leave local site through this planner',()=>{
 const remote=offer({kind:'OWNER_REMOTE_COMPUTE',modelId:MODEL_ID,revision});
 const q=plan(request({dataClassification:'OWNER_PRIVATE',internetEnabled:true,
  ownerAllowsRemoteCompute:true}),[remote]);
 assert.equal(q.selected,null);
 assert.ok(q.reasons.includes('PRIVATE_CONTEXT_REMOTE_DENIED'));
});

test('remote model identity must match both exact model and pinned revision',()=>{
 const q=plan(request({internetEnabled:true,ownerAllowsRemoteCompute:true,
  maxChargeMicrounits:1000}),[offer({kind:'REMOTE_PROVIDER',modelId:MODEL_ID,
   revision:'c'.repeat(40)})]);
 assert.equal(q.selected,null);
 assert.ok(q.reasons.includes('MODEL_REVISION_OR_FALLBACK_MISMATCH'));
});

test('reject unavailable, expired, unowned, oversized and overbudget compute',()=>{
 const req=request({internetEnabled:true,ownerAllowsRemoteCompute:true,
  maxChargeMicrounits:300});
 for(const change of [{available:false},{modelLoaded:false},{ownerEnrolled:false},
  {expiresAtMillis:5000},{maxInputBytes:100},{maxOutputBytes:10},
  {declaredChargeMicrounits:400}]){
  assert.equal(plan(req,[offer({kind:'REMOTE_PROVIDER',modelId:MODEL_ID,
   revision,...change})]).selected,null);
 }
});

test('local selection is preferred, but never substitutes an unpinned model',()=>{
 const options=[offer({nodeId:'remote',kind:'REMOTE_PROVIDER',modelId:MODEL_ID,
  revision,declaredChargeMicrounits:0}),offer({nodeId:'local'})];
 const req=request({internetEnabled:true,ownerAllowsRemoteCompute:true});
 assert.equal(plan(req,options).selected.nodeId,'local');
 assert.equal(plan(request({allowFallback:false}),[offer()]).selected,null);
 assert.equal(plan(request(),[offer({revision:'c'.repeat(40)})]).selected,null);
});

test('reuses pinned 48-shard Hub manifest validator without downloading weights',()=>{
 const siblings=Array.from({length:48},(_,i)=>({
  rfilename:'model-'+String(i+1).padStart(5,'0')+'-of-00048.safetensors',
  size:10600000000
 }));
 const m=buildPinnedDeepSeekResourceManifest({sha:revision,siblings});
 assert.equal(m.shards.length,48);assert.equal(m.storageBytes,508800000000);
 assert.equal(m.inferenceAvailable,false);
 assert.equal(m.checkedAgainstHubMetadata,false);
 assert.throws(()=>buildPinnedDeepSeekResourceManifest({sha:revision,
  siblings:siblings.slice(0,47)}));
});

test('malformed request, duplicate nodes and deadline fail closed',()=>{
 assert.throws(()=>plan(request({revision:'abc'})),/FABRIC_REQUEST_INVALID/);
 assert.throws(()=>plan(request(),[offer(),offer()]),/FABRIC_DUPLICATE_NODE/);
 assert.throws(()=>plan(request({maxChargeMicrounits:-1})),/FABRIC_REQUEST_INVALID/);
 const q=plan(request({deadlineMillis:9000}));
 assert.equal(q.selected,null);
 assert.ok(q.reasons.includes('TASK_DEADLINE_EXPIRED'));
});

test('routing snapshots primitive offer data against later mutation',()=>{
 const o=offer();const p=plan(request(),[o]);o.modelId='evil';
 assert.equal(p.selected.modelId,'small-local');
 assert.ok(Object.isFrozen(p.alternatives));
});
