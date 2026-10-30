import test from 'node:test';
import assert from 'node:assert/strict';
import {makeNvidiaPublicCodingChallenge,evaluateNvidiaPublicCodingProposal,
 runNvidiaPublicCodingLab,NVIDIA_CODING_LAB_MODEL,NVIDIA_CODING_LAB_ENDPOINT}
 from './nvidia-nim-public-coding-lab.mjs';

const KEY='nvapi-'+'n'.repeat(36);
const task=makeNvidiaPublicCodingChallenge();
const publicFiles=JSON.parse(task.prompt).files;
function good(){
 const edits=[
  text=>text.replace('text.length <= maxBytes',"Buffer.byteLength(text, 'utf8') <= maxBytes"),
  text=>text.replace('e.validFrom <= atMillis','e.validFrom <= atMillis && (e.revokedAt === null || e.revokedAt > atMillis)')
   .replace('a.validFrom - b.validFrom','b.validFrom - a.validFrom'),
  text=>text.replace('Math.floor(runtimeSeconds','Math.ceil(runtimeSeconds')
 ];
 return {plan:['Fix UTF-8 bytes, revocation order, and billing ceiling.'],
  hypothesis:'Each fix restores the declared invariant.',
  patches:publicFiles.map((p,i)=>({file:p.file,oldText:p.source,newText:edits[i](p.source)}))};
}
const stream=object=>new Response(JSON.stringify(object),{status:200,headers:{'content-type':'application/json'}});
const packet=(candidate=good(),model=NVIDIA_CODING_LAB_MODEL)=>({
 model,choices:[{message:{role:'assistant',content:JSON.stringify(candidate)}}],
 usage:{prompt_tokens:410,completion_tokens:200}
});

test('public-only fixtures are immutable, correctly scoped, and bound to one synthetic task',()=>{
 assert.equal(task.synthetic,true);
 assert.equal(task.containsOwnerData,false);
 assert.equal(task.sourceCommitIsReal,false);
 assert.equal(task.task.sourceCommit,'0'.repeat(40));
 assert.equal(task.expectedFiles.length,3);
 assert.equal(new Set(task.expectedFiles).size,3);
 assert.equal(task.task.limits.maxModelCalls,1);
 assert.equal(task.prompt.includes('Ullmann'),false);
 assert.equal(task.prompt.includes('PRIVATE'),false);
 assert.ok(Object.isFrozen(task.task));
});
test('canonical coding-task validator accepts only scoped 3-file synthetic repair',()=>{
 const a=evaluateNvidiaPublicCodingProposal(good());
 assert.equal(a.schemaAccepted,true);
 assert.equal(a.allThreePresent,true);
 assert.equal(a.exactGoldenPatches,3);
 assert.equal(a.allExactGolden,true);
 assert.equal(a.ccc1LearningEligible,false);
 assert.equal(a.independentExecutionVerified,false);
});
test('wrong or partial patch never scores as full fix',()=>{
 const wrong=good();wrong.patches[1].newText=wrong.patches[1].oldText+'// unrelated';
 assert.equal(evaluateNvidiaPublicCodingProposal(wrong).exactGoldenPatches,2);
 assert.equal(evaluateNvidiaPublicCodingProposal(wrong).allExactGolden,false);
 const partial=good();partial.patches.pop();
 assert.equal(evaluateNvidiaPublicCodingProposal(partial).allThreePresent,false);
});
test('path substitution and malicious structure are rejected by existing coding schema',()=>{
 const evil=good();evil.patches[0].file='backend/secrets.js';
 assert.equal(evaluateNvidiaPublicCodingProposal(evil).schemaAccepted,false);
 assert.equal(evaluateNvidiaPublicCodingProposal({patches:[]}).schemaAccepted,false);
});
test('invalid API key prevents network requests',async()=>{
 let calls=0;
 await assert.rejects(runNvidiaPublicCodingLab({apiKey:'missing',
  fetchImpl:async()=>{calls++;throw Error('unexpected network');}}),/NVIDIA_AUTH_REQUIRED/);
 assert.equal(calls,0);
});
test('one HTTPS request sends only immutable synthetic prompt, validates response and measures rate',async()=>{
 let calls=0;
 const time=[1000,3000];
 const result=await runNvidiaPublicCodingLab({apiKey:KEY,now:()=>time.shift(),
  fetchImpl:async(url,opts)=>{
   calls++;
   assert.equal(url,NVIDIA_CODING_LAB_ENDPOINT);
   assert.equal(opts.method,'POST');
   assert.equal(opts.redirect,'error');
   assert.equal(opts.headers.Authorization,'Bearer '+KEY);
   const body=JSON.parse(opts.body);
   assert.equal(body.messages.length,2);
   assert.equal(body.messages[1].content,task.prompt);
   assert.equal(body.messages[1].content.includes(KEY),false);
   assert.equal(body.stream,false);
   assert.equal(body.chat_template_kwargs.enable_thinking,false);
   assert.equal(body.temperature,0);
   return stream(packet());
  }});
 assert.equal(calls,1);
 assert.equal(result.allExactGolden,true);
 assert.equal(result.exactGoldenPatches,3);
 assert.equal(result.elapsedMs,2000);
 assert.equal(result.measuredOutputTokensPerSecond,100);
 assert.equal(result.verifiedPhysicalGpu,false);
 assert.equal(result.updatesLocalBrain,false);
 assert.equal(result.ownerDataTransmitted,false);
 assert.equal(result.rawCandidateStored,false);
 assert.equal(JSON.stringify(result).includes(KEY),false);
});
test('wrong model, malformed model output and HTTP failure stop without fake success',async()=>{
 await assert.rejects(runNvidiaPublicCodingLab({apiKey:KEY,
  fetchImpl:async()=>stream(packet(good(),'unapproved/model'))}),/NVIDIA_RESPONSE_INVALID/);
 const bad={model:NVIDIA_CODING_LAB_MODEL,
  choices:[{message:{content:'This is not JSON'}}]};
 await assert.rejects(runNvidiaPublicCodingLab({apiKey:KEY,
  fetchImpl:async()=>stream(bad)}),/NVIDIA_PROPOSAL_FORMAT_INVALID/);
 await assert.rejects(runNvidiaPublicCodingLab({apiKey:KEY,
  fetchImpl:async()=>new Response('quota reached',{status:429})}),/NVIDIA_ENDPOINT_UNAVAILABLE_HTTP_429/);
});
test('truncated NVIDIA answer cannot count as a successful repair',async()=>{
 const p=packet();p.choices[0].finish_reason='length';
 await assert.rejects(runNvidiaPublicCodingLab({apiKey:KEY,
  fetchImpl:async()=>stream(p)}),/NVIDIA_RESPONSE_TRUNCATED/);
});
test('oversized provider response and improper timeout are rejected',async()=>{
 await assert.rejects(runNvidiaPublicCodingLab({apiKey:KEY,
  fetchImpl:async()=>stream({padding:'x'.repeat(100000)})}),/NVIDIA_RESPONSE_TOO_LARGE/);
 await assert.rejects(runNvidiaPublicCodingLab({apiKey:KEY,timeoutMs:0,
  fetchImpl:async()=>stream(packet())}),/NVIDIA_CODING_LAB_CONFIG_INVALID/);
});
test('network error is not retried or mislabeled as a repair',async()=>{
 let calls=0;
 await assert.rejects(runNvidiaPublicCodingLab({apiKey:KEY,
  fetchImpl:async()=>{calls++;throw Error('synthetic disconnect');}}),/synthetic disconnect/);
 assert.equal(calls,1);
});
