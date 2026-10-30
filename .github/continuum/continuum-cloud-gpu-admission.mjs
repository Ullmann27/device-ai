/**
 * Read-only cloud-GPU admission on top of the existing Android ComputeFabric
 * registry snapshot and virtual-model planner. Never provisions or executes.
 */
import {planVirtualModelRouteFromRegistry} from './compute-fabric-model-adapter.mjs';

const SHA40=/^[a-f0-9]{40}$/;
const ID=/^[A-Za-z0-9._:-]{1,128}$/;
const int=(v,min,max)=>Number.isSafeInteger(v)&&v>=min&&v<=max;
const reject=reason=>{throw Error(reason);};
const MAX_MICROUSD=1000000000;
const MAX_BYTES=2000000000000;

/** Exact integer ceiling of billed fractional hours; no floating currency math. */
export function priceGpuSeconds(rateMicrousdPerHour,requestedSeconds,billingQuantumSeconds){
 if(!int(rateMicrousdPerHour,0,MAX_MICROUSD)||
    !int(requestedSeconds,1,604800)||
    !int(billingQuantumSeconds,1,3600))reject('GPU_PRICE_INVALID');
 const seconds=Math.ceil(requestedSeconds/billingQuantumSeconds)*billingQuantumSeconds;
 const price=(BigInt(rateMicrousdPerHour)*BigInt(seconds)+3599n)/3600n;
 if(price>BigInt(MAX_MICROUSD))reject('GPU_PRICE_EXCEEDS_SUPPORTED_LIMIT');
 return Object.freeze({billedSeconds:seconds,quotedComputeMicrousd:Number(price)});
}

function validateJob(job){
 if(!job||typeof job!=='object'||Array.isArray(job)||
    typeof job.modelId!=='string'||!job.modelId||job.modelId.length>128||
    !SHA40.test(job.revision??'')||!int(job.weightBytes,1,MAX_BYTES)||
    !int(job.requiredStorageGiB,1,16384)||
    !int(job.expectedComputeSeconds,1,86400)||
    !int(job.maxChargeMicrounits,0,MAX_MICROUSD)||
    !['PUBLIC','OWNER_PRIVATE'].includes(job.dataClassification))reject('GPU_JOB_INVALID');
 return job;
}

function quoteOne(quote,job,nowMillis){
 if(!quote||typeof quote!=='object'||Array.isArray(quote)||
    !ID.test(quote.nodeId??'')||!ID.test(quote.providerId??'')||
    !ID.test(quote.quoteId??'')||!int(quote.gpuCount,1,128)||
    !int(quote.vramMiBPerGpu,1024,524288)||
    !int(quote.storageGiB,0,16384)||
    !int(quote.rateMicrousdPerHour,0,MAX_MICROUSD)||
    !int(quote.billingQuantumSeconds,1,3600)||
    !int(quote.setupSeconds,0,86400)||
    !int(quote.expiresAtMillis,1,Number.MAX_SAFE_INTEGER)||
    typeof quote.runtimeVerified!=='boolean'||
    typeof quote.weightsVerified!=='boolean'||
    typeof quote.modelId!=='string'||quote.modelId.length>128||
    !SHA40.test(quote.revision??'')||
    typeof quote.capacityVerified!=='boolean'||
    typeof quote.endpointReady!=='boolean')reject('GPU_QUOTE_INVALID');
 const duration=job.expectedComputeSeconds+quote.setupSeconds;
 if(duration>604800)reject('GPU_QUOTE_INVALID');
 const price=priceGpuSeconds(quote.rateMicrousdPerHour,duration,quote.billingQuantumSeconds);
 const aggregateVramMiB=quote.gpuCount*quote.vramMiBPerGpu;
 const weightMiB=Math.ceil(job.weightBytes/1048576);
 const reasons=[];
 if(quote.expiresAtMillis<=nowMillis)reasons.push('GPU_QUOTE_EXPIRED');
 if(quote.modelId!==job.modelId||quote.revision!==job.revision)
  reasons.push('GPU_QUOTE_MODEL_MISMATCH');
 if(quote.storageGiB<job.requiredStorageGiB)reasons.push('GPU_STORAGE_INSUFFICIENT');
 // Aggregate VRAM is a necessary, never a sufficient inference-capacity test.
 // Tensor parallelism, runtime compatibility, KV/activation overhead are unproven.
 if(aggregateVramMiB<weightMiB)reasons.push('GPU_AGGREGATE_VRAM_BELOW_WEIGHTS');
 if(price.quotedComputeMicrousd>job.maxChargeMicrounits)reasons.push('GPU_COST_OVER_BUDGET');
 if(!quote.capacityVerified||!quote.runtimeVerified||!quote.weightsVerified||!quote.endpointReady)
  reasons.push('GPU_RUNTIME_NOT_ATTESTED');
 return Object.freeze({nodeId:quote.nodeId,providerId:quote.providerId,quoteId:quote.quoteId,
  aggregateVramMiB,weightMiB,storageGiB:quote.storageGiB,requiredStorageGiB:job.requiredStorageGiB,
  ...price,reasons:Object.freeze(reasons),
  executable:false,providerReachabilityVerified:false,physicalCapacityAttested:false,
  mayProvision:false,mayCharge:false});
}

/** JSON snapshot is not an authenticated owner grant. Every result is advisory. */
export function planCloudGpuAdmission({snapshotJson,request,modelOffers,quotes,job,nowMillis}={}){
 validateJob(job);
 if(!int(nowMillis,0,Number.MAX_SAFE_INTEGER)||
    !Array.isArray(modelOffers)||!Array.isArray(quotes)||quotes.length>32||
    request?.modelId!==job.modelId||request?.revision!==job.revision||
    request?.dataClassification!==job.dataClassification||
    request?.maxChargeMicrounits!==job.maxChargeMicrounits)
  reject('GPU_ADMISSION_BINDING_INVALID');
 if(new Set(quotes.map(q=>q?.nodeId)).size!==quotes.length)
  reject('GPU_DUPLICATE_QUOTE_NODE');
 const planned=planVirtualModelRouteFromRegistry({snapshotJson,request,modelOffers,nowMillis});
 const admissibleNodes=new Map([planned.selected,...planned.alternatives]
  .filter(Boolean).filter(r=>r.kind==='REMOTE_PROVIDER'||r.kind==='OWNER_REMOTE_COMPUTE')
  .map(r=>[r.nodeId,r]));
 const considered=quotes.map(q=>{
  const x=quoteOne(q,job,nowMillis);
  const route=admissibleNodes.get(x.nodeId);
  const modelOffer=modelOffers.find(o=>o.nodeId===x.nodeId);
  const reasons=[...x.reasons];
  if(!route)reasons.push('GPU_NOT_IN_AUTHORIZED_REGISTRY_PLAN');
  // No dishonest zero-price declarations to bypass the owner's budget.
  if(!modelOffer||modelOffer.declaredChargeMicrounits!==x.quotedComputeMicrousd)
   reasons.push('GPU_PRICE_DECLARATION_MISMATCH');
  if(job.dataClassification!=='PUBLIC')reasons.push('GPU_PRIVATE_CONTEXT_DENIED');
  return Object.freeze({...x,reasons:Object.freeze([...new Set(reasons)].sort())});
 });
 const eligible=considered.filter(x=>x.reasons.length===0).sort((a,b)=>
  a.quotedComputeMicrousd-b.quotedComputeMicrousd||
  b.aggregateVramMiB-a.aggregateVramMiB||a.nodeId.localeCompare(b.nodeId));
 return Object.freeze({schema:'continuum-cloud-gpu-admission',version:1,
  status:eligible.length?'GPU_QUOTE_REVIEW_ONLY':'GPU_NO_VERIFIED_QUOTE',
  registrySnapshotSha256:planned.registrySnapshotSha256,
  selected:eligible[0]??null,evaluated:Object.freeze(considered),
  modelId:job.modelId,revision:job.revision,
  requiresLiveOwnerRegistryRecheck:true,requiresPerRequestConsent:true,
  requiresSeparateProviderBillingApproval:true,requiresNativeGpuInferenceBenchmark:true,
  performsInference:false,provisionsGpu:false,chargesAccount:false,
  performsNetworkRequests:false,grantsPermission:false});
}