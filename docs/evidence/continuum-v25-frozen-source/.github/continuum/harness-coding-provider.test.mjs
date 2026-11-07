import test from 'node:test';import assert from 'node:assert/strict';
import fs from 'node:fs';import os from 'node:os';import path from 'node:path';
import {queryHarnessCodingBrain,inspectHarnessRuntime} from './harness-coding-provider.mjs';
const runtimeDirectory=process.env.CONTINUUM_HARNESS_RUNTIME;
const proposal={plan:['Correct signed arithmetic'],hypothesis:'The operator reverses signed adjustments.',patches:[{file:'lib/ledger.mjs',oldText:'return a-b;',newText:'return a+b;'}]};
const input={userPrompt:'Public synthetic transport fixture, not neural inference.',allowedTrackedPaths:['lib/ledger.mjs'],apiKey:'a'.repeat(40),model:'fixture-model',timeoutMs:5000};
test('unknown or symlinked SDK installation cannot be silently substituted',()=>{
 assert.throws(()=>inspectHarnessRuntime('/not/a/configured/runtime'),/HARNESS_RUNTIME/);
 const d=fs.mkdtempSync(path.join(os.tmpdir(),'harness-link-'));
 try{fs.symlinkSync(os.tmpdir(),path.join(d,'link'));assert.throws(()=>inspectHarnessRuntime(path.join(d,'link')),/HARNESS_RUNTIME/);}finally{fs.rmSync(d,{recursive:true,force:true});}
});
test('real Harness service preserves the existing strict wire schema without mounting tools',{skip:runtimeDirectory?false:'NOT_RUN: configure actual reviewed Harness SDK'},async()=>{
 let requests=0;
 const result=await queryHarnessCodingBrain(input,{runtimeDirectory,fetchImpl:async(url,opts)=>{
  requests++;assert.equal(url,'http://127.0.0.1:8089/v1/chat/completions');
  const body=JSON.parse(opts.body);assert.equal(body.model,input.model);assert.equal(body.response_format.type,'json_schema');
  assert.equal(body.temperature,0);assert.equal(body.stream,false);assert.equal(body.tools,undefined);
  assert.equal(opts.headers.authorization,'Bearer '+input.apiKey);
  assert.ok(Object.keys(opts.headers).some(k=>/user-agent/i.test(k)));
  return new Response(JSON.stringify({choices:[{message:{content:JSON.stringify(proposal)}}]}),{headers:{'content-type':'application/json'}});
 }});
 assert.deepEqual(result.proposal,proposal);assert.equal(result.autoApply,false);assert.equal(result.trusted,false);assert.equal(requests,1);
});
test('authority smuggling, no-op and HTTP failure remain rejected without hidden retries',{skip:runtimeDirectory?false:'NOT_RUN: actual SDK required'},async()=>{
 for(const bad of [{...proposal,approvedForMerge:true},{...proposal,patches:[{...proposal.patches[0],newText:'return a-b;'}]},null]) {
  let count=0;
  await assert.rejects(queryHarnessCodingBrain(input,{runtimeDirectory,fetchImpl:async()=>{
   count++;return bad===null?new Response('not available',{status:503}):new Response(JSON.stringify({choices:[{message:{content:JSON.stringify(bad)}}]}));
  }}),/CODING_RESPONSE_INVALID|CODING_NOOP_PATCH|LOCAL_HTTP_503/);
  assert.equal(count,1);
 }
});
