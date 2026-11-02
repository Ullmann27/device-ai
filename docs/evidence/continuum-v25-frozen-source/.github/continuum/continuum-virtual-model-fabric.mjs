/**
 * Research-only virtual-model route proposal: zero network, GPU provisioning,
 * model loading, payment, permission granting or execution.
 * Actual authority remains with Android ComputeFabric, the owner-controlled
 * CodingProvider and the canonical CCC1 executor.
 */
import {MODEL_ID,inspectHubManifest} from '../../scripts/continuum-deepseek-tier-plan.mjs';

const SHA40=/^[a-f0-9]{40}$/;
const HEX64=/^[a-f0-9]{64}$/;
const ID=/^[A-Za-z0-9._:@/-]{1,128}$/;
const CATEGORIES=new Set(['PUBLIC','OWNER_PRIVATE']);
const KINDS=new Set(['LOCAL_DEVICE','LOCAL_WORKER','OWNER_REMOTE_COMPUTE','REMOTE_PROVIDER']);
export const FABRIC_SCHEMA='continuum-virtual-model-fabric-plan';
export const FABRIC_VERSION=1;

const integer=(v,min,max)=>Number.isSafeInteger(v)&&v>=min&&v<=max;
const id=v=>typeof v==='string'&&ID.test(v);
function reject(error){throw Error(error)}

/** Validates an untrusted local copy of pinned HF hub metadata, never downloads weights. */
export function buildPinnedDeepSeekResourceManifest(hubSnapshot){
 const pinned=inspectHubManifest(hubSnapshot);
 if(pinned.repo!==MODEL_ID||!SHA40.test(pinned.pinnedRevision))reject('MODEL_IDENTITY_INVALID');
 const shards=pinned.shards.map(x=>Object.freeze({filename:x.filename,bytes:x.bytes}));
 return Object.freeze({
  schema:'continuum-virtual-model-resource',version:1,
  modelId:pinned.repo,revision:pinned.pinnedRevision,
  storageBytes:pinned.weightsBytes,shards:Object.freeze(shards),
  checkedAgainstHubMetadata:false,dataDownloaded:false,inferenceAvailable:false
 });
}

/** No offer is trusted: this is only a capacity estimate for an owner review. */
export function planVirtualModelRoute({request,offers,nowMillis}={}){
 if(!request||typeof request!=='object'||Array.isArray(request)||
  !id(request.taskId)||!id(request.modelId)||!SHA40.test(request.revision??'')||
  !HEX64.test(request.taskSha256??'')||!CATEGORIES.has(request.dataClassification)||
  typeof request.allowFallback!=='boolean'||
  !Array.isArray(request.allowedFallbackModels)||request.allowedFallbackModels.length>8||
  request.allowedFallbackModels.some(m=>!m||typeof m!=='object'||Array.isArray(m)||
   !id(m.modelId)||!SHA40.test(m.revision??''))||
  new Set(request.allowedFallbackModels.map(m=>m.modelId+'@'+m.revision)).size!==request.allowedFallbackModels.length||
  request.allowedFallbackModels.some(m=>m.modelId===request.modelId&&m.revision===request.revision)||
  typeof request.internetEnabled!=='boolean'||typeof request.ownerAllowsRemoteCompute!=='boolean'||
  !integer(request.maxChargeMicrounits,0,1000000000)||
  !integer(request.maxInputBytes,0,1000000)||
  !integer(request.maxOutputBytes,1,4194304)||
  !integer(request.deadlineMillis,1,Number.MAX_SAFE_INTEGER)||
  !integer(nowMillis,0,Number.MAX_SAFE_INTEGER))reject('FABRIC_REQUEST_INVALID');
 if(!Array.isArray(offers)||offers.length>32)reject('FABRIC_OFFERS_INVALID');

 const snapshot=offers.map(o=>{
  if(!o||typeof o!=='object'||Array.isArray(o)||!id(o.nodeId)||
   !KINDS.has(o.kind)||!id(o.modelId)||!SHA40.test(o.revision??'')||
   typeof o.available!=='boolean'||typeof o.modelLoaded!=='boolean'||
   typeof o.ownerEnrolled!=='boolean'||
   !integer(o.maxInputBytes,0,1000000000)||!integer(o.maxOutputBytes,0,1000000000)||
   !integer(o.declaredChargeMicrounits,0,1000000000)||
   !integer(o.expiresAtMillis,1,Number.MAX_SAFE_INTEGER))reject('FABRIC_OFFER_INVALID');
  return Object.freeze({
   nodeId:o.nodeId,kind:o.kind,modelId:o.modelId,revision:o.revision,
   available:o.available,modelLoaded:o.modelLoaded,ownerEnrolled:o.ownerEnrolled,
   maxInputBytes:o.maxInputBytes,maxOutputBytes:o.maxOutputBytes,
   declaredChargeMicrounits:o.declaredChargeMicrounits,expiresAtMillis:o.expiresAtMillis
  });
 });
 if(new Set(snapshot.map(o=>o.nodeId)).size!==snapshot.length)reject('FABRIC_DUPLICATE_NODE');

 const reasons=[],options=[];
 if(request.deadlineMillis<=nowMillis)reasons.push('TASK_DEADLINE_EXPIRED');
 for(const o of snapshot){
  const local=o.kind==='LOCAL_DEVICE'||o.kind==='LOCAL_WORKER';
  const exact=o.modelId===request.modelId&&o.revision===request.revision;
  const fallback=request.allowFallback&&request.allowedFallbackModels.some(m=>
   m.modelId===o.modelId&&m.revision===o.revision);
  let reason=null;
  if(request.deadlineMillis<=nowMillis)reason='TASK_DEADLINE_EXPIRED';
  else if(!o.available||!o.modelLoaded)reason='MODEL_COMPUTE_UNAVAILABLE';
  else if(!o.ownerEnrolled)reason='NODE_NOT_OWNER_ENROLLED';
  else if(o.expiresAtMillis<=nowMillis)reason='NODE_EXPIRED';
  else if(!exact&&!(local&&fallback))reason='MODEL_REVISION_OR_FALLBACK_MISMATCH';
  else if(request.maxInputBytes>o.maxInputBytes||request.maxOutputBytes>o.maxOutputBytes)
   reason='MODEL_LIMITS_INSUFFICIENT';
  else if(!local&&(!request.internetEnabled||!request.ownerAllowsRemoteCompute))
   reason='REMOTE_CONSENT_REQUIRED';
  else if(!local&&request.dataClassification!=='PUBLIC')reason='PRIVATE_CONTEXT_REMOTE_DENIED';
  else if(o.declaredChargeMicrounits>request.maxChargeMicrounits)
   reason='DECLARED_COST_ABOVE_OWNER_BUDGET';
  if(reason){reasons.push(reason);continue}
  options.push(Object.freeze({
   nodeId:o.nodeId,kind:o.kind,modelId:o.modelId,revision:o.revision,
   exactRequestedRevision:exact,expectedChargeMicrounits:o.declaredChargeMicrounits,
   ownerEnrollmentAttested:false,actualModelRevisionAttested:false,executable:false
  }));
 }
 const priority=o=>o.kind.startsWith('LOCAL_')?(o.exactRequestedRevision?0:1):2;
 options.sort((a,b)=>priority(a)-priority(b)||
  a.expectedChargeMicrounits-b.expectedChargeMicrounits||a.nodeId.localeCompare(b.nodeId));
 const chosen=options[0]??null;
 return Object.freeze({
  schema:FABRIC_SCHEMA,version:FABRIC_VERSION,
  status:chosen?'ROUTE_PROPOSED_NOT_AUTHORIZED':'DEFERRED_NO_VERIFIED_CAPACITY',
  taskId:request.taskId,taskSha256:request.taskSha256,
  requestedModelId:request.modelId,requestedRevision:request.revision,
  dataClassification:request.dataClassification,
  selected:chosen,alternatives:Object.freeze(options.slice(1)),
  reasons:Object.freeze([...new Set(reasons)].sort()),
  createsComputeOrCharges:false,downloadsWeights:false,performsInference:false,
  grantsPermission:false,realProviderReachabilityVerified:false,
  requiresExistingOwnerComputeFabricAndCodingProvider:true,
  fallbackEquivalentToRequestedModel:chosen?chosen.exactRequestedRevision:false,
  deferredTaskPersisted:false
 });
}
