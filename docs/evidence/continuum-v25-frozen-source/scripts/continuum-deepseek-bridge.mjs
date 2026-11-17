/**
 * Optional teacher transport for the EXISTING coding proposal/repair contract.
 * No endpoint is configured by default. This module creates no server/compute,
 * downloads no weights, executes no model output and writes no memory/training.
 * Capability and revision fields are assertions by the authenticated operator,
 * not proof that the actual checkpoint ran or a financial enforcement mechanism.
 */
import {createHash} from 'node:crypto';
import {buildCodingRequest,parseCodingResponse} from './continuum-local-brain.mjs';
import {MODEL_ID,PINNED_REVISION} from './continuum-deepseek-tier-plan.mjs';

export const REMOTE_LIMITS = Object.freeze({maxRequestBytes:98_304,maxResponseBytes:32_768,maxOutputTokens:1800});
export const REMOTE_FAILURE_CODES = Object.freeze(['REMOTE_ENDPOINT_INVALID','REMOTE_REQUEST_INVALID',
  'REMOTE_REQUEST_TOO_LARGE','REMOTE_CONSENT_REQUIRED','REMOTE_AUTH_REQUIRED','REMOTE_UNAVAILABLE',
  'REMOTE_REDIRECT_REFUSED','REMOTE_AUTH_REJECTED','REMOTE_RATE_LIMITED','REMOTE_RESPONSE_TOO_LARGE',
  'REMOTE_RESPONSE_INVALID','REMOTE_CAPABILITY_MISMATCH','REMOTE_DEADLINE_EXCEEDED',
  'REMOTE_IDENTITY_OR_REQUEST_MISMATCH','REMOTE_COST_POLICY_MISMATCH','REMOTE_OUTPUT_BUDGET_EXCEEDED',
  'REMOTE_OUTPUT_CONTRACT_REJECTED','REMOTE_PROPOSAL_INVALID']);
const SHA40=/^[a-f0-9]{40}$/, SHA64=/^[a-f0-9]{64}$/;
const sha = text => createHash('sha256').update(text).digest('hex');
const exact=(v,keys)=>v&&typeof v==='object'&&!Array.isArray(v)&&Object.keys(v).sort().join(',')===[...keys].sort().join(',');
const REQUEST_KEYS=['schema','endpoint','jobId','taskFingerprint','sourceCommit','requestIndex','deadline','userPrompt','allowedTrackedPaths'];
const CONSENT_KEYS=['schema','requestSha256','expiresAt','allowExternal','allowRepositoryContext','allowOwnerMemory','allowSecrets','allowTraining','maxCostMicrousd'];
export const DEEPSEEK_OFFLINE_FALLBACK = Object.freeze({mode:'KEEP_OFFLINE_LOCAL',externalRetry:false,toolCalls:Object.freeze([]),memoryWritten:false,trainingWritten:false});

function blocked(code) {
  const error=Error(code); error.code=code; error.fallback=DEEPSEEK_OFFLINE_FALLBACK; return error;
}

function endpointUrl(endpoint) {
  try {
    if(typeof endpoint!=='string'||endpoint.length>2048)throw Error();
    const u=new URL(endpoint);
    if(u.protocol!=='https:'||!u.hostname||u.username||u.password||u.search||u.hash||
       u.pathname!=='/v1/chat/completions'||u.href!==endpoint)throw Error();
    return u;
  } catch {throw blocked('REMOTE_ENDPOINT_INVALID');}
}

function prepare(request,clock) {
  if(!exact(request,REQUEST_KEYS)||request.schema!=='CONTINUUM_REMOTE_CODING_REQUEST_V1'||
    !/^[a-z][a-z0-9-]{2,63}$/.test(request.jobId??'')||!SHA40.test(request.sourceCommit??'')||
    !SHA64.test(request.taskFingerprint??'')||!Number.isInteger(request.requestIndex)||
    request.requestIndex<1||request.requestIndex>3||!Number.isSafeInteger(request.deadline)||
    request.deadline-clock()<100||request.deadline-clock()>60_000)throw blocked('REMOTE_REQUEST_INVALID');
  const endpoint=endpointUrl(request.endpoint);
  let coding;
  try {coding=buildCodingRequest({...request,model:MODEL_ID});}
  catch {throw blocked('REMOTE_REQUEST_INVALID');}
  const body=JSON.stringify({...coding.body,tool_choice:'none',n:1,metadata:{
    protocol:'CONTINUUM_REMOTE_CODING_V1',job_id:request.jobId,source_commit:request.sourceCommit,
    task_fingerprint:request.taskFingerprint,request_index:request.requestIndex,model_revision:PINNED_REVISION,
    max_cost_microusd:0,training_allowed:false,retention:'NONE'
  }});
  const requestBytes=Buffer.byteLength(body,'utf8');
  if(requestBytes>REMOTE_LIMITS.maxRequestBytes)throw blocked('REMOTE_REQUEST_TOO_LARGE');
  const disclosure={schema:'CONTINUUM_REMOTE_MODEL_DISCLOSURE_V1',endpoint:endpoint.href,
    modelId:MODEL_ID,revision:PINNED_REVISION,jobId:request.jobId,sourceCommit:request.sourceCommit,
    taskFingerprint:request.taskFingerprint,requestIndex:request.requestIndex,deadline:request.deadline,
    bodySha256:sha(body),requestBytes,...REMOTE_LIMITS,maxCostMicrousd:0,
    dataClass:'SCOPED_REPOSITORY_CONTEXT',ownerMemory:false,secrets:false,training:false,retention:'NONE'};
  return {body,paths:coding.paths,endpoint,disclosure:Object.freeze({...disclosure,requestSha256:sha(JSON.stringify(disclosure))})};
}

/** Show this manifest AND the prepared prompt to the owner before issuing consent.
 * A hash binds consent to bytes; it cannot prove the caller reviewed/redacted secrets.
 */
export function describeDeepSeekCodingRequest(request,{clock=Date.now}={}) {
  return prepare(request,clock).disclosure;
}

export function validateDeepSeekConsent(consent,disclosure) {
  if(!exact(consent,CONSENT_KEYS)||consent.schema!=='CONTINUUM_REMOTE_MODEL_JOB_CONSENT_V1'||
    consent.requestSha256!==disclosure.requestSha256||consent.expiresAt!==disclosure.deadline||
    consent.allowExternal!==true||consent.allowRepositoryContext!==true||
    consent.allowOwnerMemory!==false||consent.allowSecrets!==false||consent.allowTraining!==false||
    consent.maxCostMicrousd!==0)throw blocked('REMOTE_CONSENT_REQUIRED');
}

async function readJson(response,bounded) {
  if(response?.redirected||response?.status>=300&&response?.status<400)throw blocked('REMOTE_REDIRECT_REFUSED');
  if(response?.status===401||response?.status===403)throw blocked('REMOTE_AUTH_REJECTED');
  if(response?.status===429)throw blocked('REMOTE_RATE_LIMITED');
  if(response?.status!==200)throw blocked('REMOTE_UNAVAILABLE');
  const declared=response.headers?.get?.('content-length');
  if(declared!==null&&declared!==undefined&&(!/^\d+$/.test(declared)||Number(declared)>REMOTE_LIMITS.maxResponseBytes))
    throw blocked('REMOTE_RESPONSE_TOO_LARGE');
  const reader=response.body?.getReader?.();
  if(!reader)throw blocked('REMOTE_RESPONSE_INVALID');
  let count=0;const chunks=[];
  try {
    while(true) {
      const {value,done}=await bounded(reader.read());if(done)break;
      if(!(value instanceof Uint8Array))throw blocked('REMOTE_RESPONSE_INVALID');
      count+=value.byteLength;if(count>REMOTE_LIMITS.maxResponseBytes)throw blocked('REMOTE_RESPONSE_TOO_LARGE');
      chunks.push(value);
    }
  } finally {
    // Cancellation must not extend the deadline if a faulty adapter also stalls cancel().
    Promise.resolve(reader.cancel()).catch(()=>{});reader.releaseLock();
  }
  try {return JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(Buffer.concat(chunks,count)));}
  catch {throw blocked('REMOTE_RESPONSE_INVALID');}
}

function validateCapabilities(caps) {
  if(caps?.schema!=='CONTINUUM_MODEL_ENDPOINT_CAPABILITIES_V1'||caps.modelId!==MODEL_ID||caps.revision!==PINNED_REVISION||
    caps.noCharge!==true||caps.retention!=='NONE'||caps.training!==false||caps.tools!==false||
    Object.entries(REMOTE_LIMITS).some(([key,value])=>!Number.isSafeInteger(caps[key])||caps[key]<value))
    throw blocked('REMOTE_CAPABILITY_MISMATCH');
}

/** Exactly one authenticated preflight and one POST; no retries or provider switching.
 * Only an already authorized, operator-enforced NO_CHARGE endpoint is eligible.
 * An HTTP client's zero-cost request cannot override the endpoint's billing setup.
 */
export async function queryDeepSeekCodingBrain({request,consent,apiKey,fetchImpl=globalThis.fetch,clock=Date.now}={}) {
  const ready=prepare(request,clock);
  validateDeepSeekConsent(consent,ready.disclosure);
  if(typeof apiKey!=='string'||!/^[A-Za-z0-9_-]{32,128}$/.test(apiKey))throw blocked('REMOTE_AUTH_REQUIRED');
  if(typeof fetchImpl!=='function')throw blocked('REMOTE_UNAVAILABLE');
  const controller=new AbortController();let timer;
  const expired=new Promise((_,reject)=>{timer=setTimeout(()=>{
    controller.abort();reject(blocked('REMOTE_DEADLINE_EXCEEDED'));
  },request.deadline-clock());});
  const bounded=promise=>Promise.race([promise,expired]);
  const options={redirect:'error',cache:'no-store',signal:controller.signal,
    headers:{authorization:'Bearer '+apiKey,accept:'application/json'}};
  try {
    const capabilitiesUrl=new URL('/v1/continuum/capabilities',ready.endpoint).href;
    const caps=await readJson(await bounded(fetchImpl(capabilitiesUrl,{...options,method:'GET'})),bounded);
    validateCapabilities(caps);
    if(request.deadline-clock()<1)throw blocked('REMOTE_DEADLINE_EXCEEDED');
    const response=await bounded(fetchImpl(ready.endpoint.href,{...options,method:'POST',
      headers:{...options.headers,'content-type':'application/json','x-continuum-request-sha256':ready.disclosure.requestSha256},
      body:ready.body}));
    const payload=await readJson(response,bounded);
    if(payload?.model!==MODEL_ID||payload.model_revision!==PINNED_REVISION||
      payload.request_sha256!==ready.disclosure.requestSha256||payload.job_id!==request.jobId)
      throw blocked('REMOTE_IDENTITY_OR_REQUEST_MISMATCH');
    if(payload.cost_microusd!==0)throw blocked('REMOTE_COST_POLICY_MISMATCH');
    if(!Number.isSafeInteger(payload.usage?.completion_tokens)||payload.usage.completion_tokens<1||
      payload.usage.completion_tokens>REMOTE_LIMITS.maxOutputTokens)throw blocked('REMOTE_OUTPUT_BUDGET_EXCEEDED');
    if(!Array.isArray(payload.choices)||payload.choices.length!==1||payload.choices[0]?.finish_reason!=='stop'||
      payload.choices[0].message?.role!=='assistant'||payload.choices[0].message.function_call||
      (payload.choices[0].message.tool_calls!==undefined&&
        (!Array.isArray(payload.choices[0].message.tool_calls)||payload.choices[0].message.tool_calls.length)))
      throw blocked('REMOTE_OUTPUT_CONTRACT_REJECTED');
    let proposal;
    try {proposal=parseCodingResponse(payload,ready.paths);}
    catch {throw blocked('REMOTE_PROPOSAL_INVALID');}
    return {...proposal,transport:'AUTHENTICATED_HTTPS_TEACHER',modelId:MODEL_ID,modelRevision:PINNED_REVISION,
      requestSha256:ready.disclosure.requestSha256,verifiedModelExecution:false,
      identityEvidence:'AUTHENTICATED_SERVER_ASSERTION_NOT_WEIGHT_VERIFICATION',
      costMicrousd:0,memoryWritten:false,trainingWritten:false};
  } catch(error) {
    if(error?.fallback===DEEPSEEK_OFFLINE_FALLBACK)throw error;
    throw blocked(controller.signal.aborted?'REMOTE_DEADLINE_EXCEEDED':'REMOTE_UNAVAILABLE');
  } finally {clearTimeout(timer);controller.abort();}
}
