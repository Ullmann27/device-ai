import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {describeDeepSeekCodingRequest, queryDeepSeekCodingBrain, REMOTE_LIMITS} from './continuum-deepseek-bridge.mjs';
import {MODEL_ID, PINNED_REVISION} from './continuum-deepseek-tier-plan.mjs';

const KEY = 'unit_test_only_' + 'a'.repeat(32);
const proposal = {plan:['Inspect rounding'],hypothesis:'Partial pages need ceiling',
  patches:[{file:'lib/pages.mjs',oldText:'x/y',newText:'Math.ceil(x/y)'}]};
const request = (changes={}) => ({schema:'CONTINUUM_REMOTE_CODING_REQUEST_V1',
  endpoint:'https://teacher.example.invalid/v1/chat/completions',jobId:'repair-pages',
  taskFingerprint:'a'.repeat(64),sourceCommit:'b'.repeat(40),requestIndex:1,
  deadline:Date.now()+10000,userPrompt:'SCOPED_PRIVATE_SOURCE',allowedTrackedPaths:['lib/pages.mjs'],...changes});
function consentFor(r) {
  return {schema:'CONTINUUM_REMOTE_MODEL_JOB_CONSENT_V1',
    requestSha256:describeDeepSeekCodingRequest(r).requestSha256,expiresAt:r.deadline,
    allowExternal:true,allowRepositoryContext:true,allowOwnerMemory:false,allowSecrets:false,
    allowTraining:false,maxCostMicrousd:0};
}
function capabilities(changes={}) {
  return {schema:'CONTINUUM_MODEL_ENDPOINT_CAPABILITIES_V1',modelId:MODEL_ID,revision:PINNED_REVISION,
    noCharge:true,retention:'NONE',training:false,tools:false,...REMOTE_LIMITS,...changes};
}
const json = data => new Response(JSON.stringify(data), {status:200,headers:{'content-type':'application/json'}});
function upstream(r,{caps=capabilities(),result={},response=null}={}) {
  const calls=[];
  return {calls,fetchImpl:async(url,options)=>{
    calls.push({url,options});
    if (options.method==='GET') return json(caps);
    return response ?? json({model:MODEL_ID,model_revision:PINNED_REVISION,
      request_sha256:describeDeepSeekCodingRequest(r).requestSha256,job_id:r.jobId,cost_microusd:0,
      usage:{completion_tokens:70},choices:[{finish_reason:'stop',message:{role:'assistant',content:JSON.stringify(proposal)}}],...result});
  }};
}

test('no opt-in, wrong payload consent, secrets/memory/training or paid allowance never sends a request',async()=>{
  const r=request(), valid=consentFor(r);let calls=0;
  const fetchImpl=async()=>{calls++;throw Error('MUST_NOT_SEND_PRIVATE_SOURCE');};
  for (const consent of [undefined,{...valid,allowExternal:false},{...valid,allowRepositoryContext:false},
    {...valid,requestSha256:'c'.repeat(64)},{...valid,expiresAt:Date.now()-1},
    {...valid,allowOwnerMemory:true},{...valid,allowSecrets:true},{...valid,allowTraining:true},
    {...valid,maxCostMicrousd:1},{...valid,other:'unreviewed'}]) {
    await assert.rejects(queryDeepSeekCodingBrain({request:r,consent,apiKey:KEY,fetchImpl}),error=>{
      assert.equal(error.code,'REMOTE_CONSENT_REQUIRED');
      assert.equal(error.fallback.mode,'KEEP_OFFLINE_LOCAL');
      assert.equal(error.fallback.externalRetry,false);return true;
    });
  }
  assert.equal(calls,0);
});

test('endpoint, authentication and request bounds are validated before network',async()=>{
  for (const endpoint of ['http://teacher.example.invalid/v1/chat/completions',
    'https://user:secret@teacher.example.invalid/v1/chat/completions',
    'https://teacher.example.invalid/v1/chat/completions?key=secret',
    'https://teacher.example.invalid/v1/chat/completions#fragment',
    'https://teacher.example.invalid/redirect'])
    assert.throws(()=>describeDeepSeekCodingRequest(request({endpoint})),/REMOTE_ENDPOINT_INVALID/);
  for (const changes of [{deadline:Date.now()-1},{deadline:Date.now()+61000},
    {userPrompt:'x'.repeat(28001)},{userPrompt:'\u0000'}, {ownerMemory:['private']},
    {requestIndex:4},{sourceCommit:'main'},{allowedTrackedPaths:['secrets/key.pem']}])
    assert.throws(()=>describeDeepSeekCodingRequest(request(changes)));
  const r=request();let calls=0;
  await assert.rejects(queryDeepSeekCodingBrain({request:r,consent:consentFor(r),apiKey:'',fetchImpl:()=>{calls++;}}),/REMOTE_AUTH_REQUIRED/);
  assert.equal(calls,0);
});

test('authenticated capability/identity/privacy preflight happens before the consented prompt',async()=>{
  for (const caps of [capabilities({revision:'c'.repeat(40)}),capabilities({modelId:'other'}),
    capabilities({noCharge:false}),capabilities({retention:'LOGGED'}),capabilities({training:true}),
    capabilities({tools:true}),capabilities({maxOutputTokens:2})]) {
    const r=request(),stub=upstream(r,{caps});
    await assert.rejects(queryDeepSeekCodingBrain({request:r,consent:consentFor(r),apiKey:KEY,...stub}),/REMOTE_CAPABILITY_MISMATCH/);
    assert.equal(stub.calls.length,1);
    assert.equal(stub.calls[0].options.method,'GET');
    assert.equal(stub.calls[0].options.body,undefined);
  }
});

test('successful HTTPS transport reuses the untrusted canonical proposal contract',async()=>{
  const r=request(),stub=upstream(r);
  const out=await queryDeepSeekCodingBrain({request:r,consent:consentFor(r),apiKey:KEY,...stub});
  assert.deepEqual(out.proposal,proposal);
  assert.equal(out.mode,'LOCAL_CODING_PATCH_PROPOSAL_V1');
  assert.equal(out.trusted,false);assert.equal(out.autoApply,false);
  assert.equal(out.transport,'AUTHENTICATED_HTTPS_TEACHER');
  assert.equal(out.verifiedModelExecution,false);
  assert.equal(out.identityEvidence,'AUTHENTICATED_SERVER_ASSERTION_NOT_WEIGHT_VERIFICATION');
  assert.equal(out.modelRevision,PINNED_REVISION);
  assert.equal(stub.calls.length,2);
  assert.equal(stub.calls[0].url,'https://teacher.example.invalid/v1/continuum/capabilities');
  const call=stub.calls[1],body=JSON.parse(call.options.body);
  assert.equal(call.url,r.endpoint);assert.equal(call.options.redirect,'error');
  assert.equal(call.options.headers.authorization,'Bearer '+KEY);
  assert.equal(call.options.headers['x-continuum-request-sha256'],consentFor(r).requestSha256);
  assert.equal(body.model,MODEL_ID);assert.equal(body.max_tokens,1800);
  assert.equal(body.stream,false);assert.equal(body.tool_choice,'none');
  assert.equal(body.messages.length,2);assert.equal(body.messages[1].content,r.userPrompt);
  assert.equal(body.metadata.max_cost_microusd,0);assert.equal(body.metadata.training_allowed,false);
  assert.equal(Object.hasOwn(body,'tools'),false);assert.equal(Object.hasOwn(body,'memory'),false);
  assert.equal(JSON.stringify(body).includes(KEY),false);
  assert.equal(createHash('sha256').update(call.options.body).digest('hex'),describeDeepSeekCodingRequest(r).bodySha256);
});

test('revision drift, replay, paid, truncated, tool and over-budget responses fail closed',async()=>{
  for(const result of [{model_revision:'c'.repeat(40)},{model:'other'},
    {request_sha256:'c'.repeat(64)},{job_id:'other-job'},{cost_microusd:1},
    {usage:{completion_tokens:1801}}, {usage:{}},
    {choices:[{finish_reason:'length',message:{role:'assistant',content:JSON.stringify(proposal)}}]},
    {choices:[{finish_reason:'stop',message:{role:'assistant',content:JSON.stringify(proposal),tool_calls:[{function:{name:'run_shell'}}]}}]}]){
    const r=request(),stub=upstream(r,{result});
    await assert.rejects(queryDeepSeekCodingBrain({request:r,consent:consentFor(r),apiKey:KEY,...stub}),error=>{
      assert.match(error.code,/^REMOTE_/);assert.equal(error.fallback.memoryWritten,false);return true;
    });
    assert.equal(stub.calls.length,2);
  }
});

test('HTTP errors and untrusted exception text are sanitized; no retry or private output',async()=>{
  for(const status of [301,401,403,429,503]){
    const r=request();let calls=0;
    const error=await queryDeepSeekCodingBrain({request:r,consent:consentFor(r),apiKey:KEY,
      fetchImpl:async()=>{calls++;return new Response('PRIVATE_SENTINEL '+KEY,{status});}}).catch(e=>e);
    assert.match(error.code,/^REMOTE_/);
    assert.equal(error.message.includes('PRIVATE_SENTINEL'),false);
    assert.equal(error.message.includes(KEY),false);assert.equal(calls,1);
  }
  const r=request();
  await assert.rejects(queryDeepSeekCodingBrain({request:r,consent:consentFor(r),apiKey:KEY,
    fetchImpl:async()=>{throw Error('PRIVATE_SENTINEL '+KEY);}}),/REMOTE_UNAVAILABLE/);
});

test('output byte limit is enforced on streamed bodies even without content-length',async()=>{
  const r=request(),stub=upstream(r,{response:new Response('x'.repeat(REMOTE_LIMITS.maxResponseBytes+1))});
  await assert.rejects(queryDeepSeekCodingBrain({request:r,consent:consentFor(r),apiKey:KEY,...stub}),/REMOTE_RESPONSE_TOO_LARGE/);
});

test('deadline bounds both a fetch and a body reader ignoring AbortSignal',async()=>{
  for(const stallBody of [false,true]){
    const r=request({deadline:Date.now()+130});
    const hanging=()=>new Promise(()=>{});
    const fetchImpl=stallBody ? async()=>({status:200,headers:new Headers(),body:{getReader:()=>({read:hanging,cancel:async()=>{},releaseLock(){}})}}) : hanging;
    const start=Date.now();
    await assert.rejects(queryDeepSeekCodingBrain({request:r,consent:consentFor(r),apiKey:KEY,fetchImpl}),/REMOTE_DEADLINE_EXCEEDED/);
    assert.ok(Date.now()-start<1000);
  }
});
