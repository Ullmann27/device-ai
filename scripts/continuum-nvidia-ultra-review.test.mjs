import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {buildUltraResearchPacket,runUltraMasterReview,OWNER_CONSENT,MODEL,ENDPOINT,TECHNICAL_FILES}
 from './continuum-nvidia-ultra-review.mjs';
const key='nvapi-'+'X'.repeat(44);
function fixture(t){
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'ultra-master-test-'));
 const brief='docs/research/omega/NVIDIA-ULTRA-JARVIS-MASTER-RESEARCH-20261030.md';
 for(const [p,data] of [[brief,'Scientific constraints: no fake data; prove all assertions.'],...TECHNICAL_FILES.map(p=>[p,'export function example(){return true;}\n'])]){
  fs.mkdirSync(path.dirname(path.join(root,p)),{recursive:true});fs.writeFileSync(path.join(root,p),data);
 }
 const out=fs.mkdtempSync(path.join(os.tmpdir(),'ultra-master-output-'));
 t.after(()=>{fs.rmSync(root,{recursive:true,force:true});fs.rmSync(out,{recursive:true,force:true})});
 return {root,out,packet:buildUltraResearchPacket(root)};
}
function responseReport(report='## Fazit\nTestbar, aber nicht verifiziert.'){
 const events=[
  {model:MODEL,choices:[{index:0,delta:{reasoning_content:'REASONING_SHOULD_NOT_BE_SAVED'},finish_reason:null}]},
  {model:MODEL,choices:[{index:0,delta:{content:report.slice(0,10)},finish_reason:null}]},
  {model:MODEL,choices:[{index:0,delta:{content:report.slice(10)},finish_reason:'stop'}]}
 ];
 const s=events.map(e=>'data: '+JSON.stringify(e)+'\n\n').join('')+'data: [DONE]\n\n';
 return new Response(s,{status:200,headers:{'Content-Type':'text/event-stream'}});
}
test('one bounded packet uses only allowlisted technical code and SHA markers',t=>{
 const f=fixture(t);assert.equal(f.packet.files.length,9);assert.equal(f.packet.model,MODEL);
 assert.equal(f.packet.ownerTechnicalSources,true);assert.equal(f.packet.containsOwnerMemory,false);
 assert.ok(f.packet.prompt.includes('@@SOURCE'));
 assert.ok(f.packet.prompt.includes('Scientific constraints'));
 assert.equal(f.packet.prompt.includes(key),false);
});
test('reject secret accidentally committed into curated project input',t=>{
 const f=fixture(t);fs.appendFileSync(path.join(f.root,TECHNICAL_FILES[0]),'api_key = "nvapi-'+'x'.repeat(20)+'"\n');
 assert.throws(()=>buildUltraResearchPacket(f.root),/ULTRA_SENSITIVE_SOURCE_REFUSED/);
});
test('no private upload without separate consent, no network without key',async t=>{
 const f=fixture(t);let calls=0;
 const fetchImpl=async()=>{calls++;throw Error('should not contact')};
 await assert.rejects(runUltraMasterReview({packet:f.packet,apiKey:key,consent:'NO',fetchImpl}),/ULTRA_OWNER_CONSENT_REQUIRED/);
 await assert.rejects(runUltraMasterReview({packet:f.packet,apiKey:'',consent:OWNER_CONSENT,fetchImpl}),/ULTRA_AUTH_REQUIRED/);
 assert.equal(calls,0);
});
test('exactly one approved NVIDIA high-reasoning streamed request; saves only complete report and receipt',async t=>{
 const f=fixture(t);let calls=0;
 const r=await runUltraMasterReview({packet:f.packet,apiKey:key,consent:OWNER_CONSENT,root:f.root,
  outputDirectory:f.out,clock:(()=>{let n=100;return ()=>n+=500})(),
  fetchImpl:async(url,request)=>{
   calls++;assert.equal(url,ENDPOINT);assert.equal(request.method,'POST');assert.equal(request.redirect,'error');
   assert.equal(request.headers.Authorization,'Bearer '+key);
   const body=JSON.parse(request.body);
   assert.equal(body.model,MODEL);assert.equal(body.stream,true);
   assert.equal(body.reasoning_effort,'high');assert.equal(body.reasoning_budget,8192);
   assert.equal(body.messages[1].content,f.packet.prompt);
   return responseReport();
  }});
 assert.equal(calls,1);assert.equal(r.realModelCallCompleted,true);
 assert.equal(r.testsExecuted,false);assert.equal(r.codeExecuted,false);
 assert.equal(r.elapsedMs,500);
 const report=fs.readFileSync(path.join(f.out,'nvidia-ultra-report.md'),'utf8');
 assert.match(report,/Testbar/);assert.doesNotMatch(report,/REASONING_SHOULD_NOT_BE_SAVED/);
 const receipt=fs.readFileSync(path.join(f.out,'nvidia-ultra-receipt.json'),'utf8');
 assert.equal(receipt.includes(key),false);assert.equal(report.includes(key),false);
 assert.equal(fs.statSync(path.join(f.out,'nvidia-ultra-report.md')).mode&0o777,0o600);
 assert.equal(fs.statSync(path.join(f.out,'nvidia-ultra-receipt.json')).mode&0o777,0o600);
});
test('wrong model and incomplete stream cannot be considered completed',async t=>{
 const f=fixture(t);
 const badModel='data: '+JSON.stringify({model:'wrong-model',choices:[{delta:{content:'p'},finish_reason:'stop'}]})+'\n\ndata: [DONE]\n';
 await assert.rejects(runUltraMasterReview({packet:f.packet,apiKey:key,consent:OWNER_CONSENT,root:f.root,outputDirectory:f.out,fetchImpl:async()=>new Response(badModel)}),/ULTRA_MODEL_MISMATCH/);
 const missingDone='data: '+JSON.stringify({model:MODEL,choices:[{delta:{content:'p'},finish_reason:'stop'}]})+'\n';
 await assert.rejects(runUltraMasterReview({packet:f.packet,apiKey:key,consent:OWNER_CONSENT,root:f.root,outputDirectory:f.out,fetchImpl:async()=>new Response(missingDone)}),/ULTRA_RESPONSE_INCOMPLETE/);
 assert.deepEqual(fs.readdirSync(f.out),[]);
});
test('truncated finish_reason and remote tools rejected without writing output',async t=>{
 const f=fixture(t);
 const ev=term=>new Response('data: '+JSON.stringify({model:MODEL,choices:[{delta:{content:'result'},finish_reason:term}]})+'\n\ndata: [DONE]\n');
 await assert.rejects(runUltraMasterReview({packet:f.packet,apiKey:key,consent:OWNER_CONSENT,root:f.root,outputDirectory:f.out,fetchImpl:async()=>ev('length')}),/ULTRA_RESPONSE_INCOMPLETE/);
 const tools=new Response('data: '+JSON.stringify({model:MODEL,choices:[{delta:{tool_calls:[{}]},finish_reason:null}]})+'\n\ndata: [DONE]\n');
 await assert.rejects(runUltraMasterReview({packet:f.packet,apiKey:key,consent:OWNER_CONSENT,root:f.root,outputDirectory:f.out,fetchImpl:async()=>tools}),/ULTRA_TOOLS_FORBIDDEN/);
 assert.deepEqual(fs.readdirSync(f.out),[]);
});
test('cannot persist any report into project root',async t=>{
 const f=fixture(t);
 await assert.rejects(runUltraMasterReview({packet:f.packet,apiKey:key,consent:OWNER_CONSENT,
  root:f.root,outputDirectory:path.join(f.root,'data'),fetchImpl:async()=>responseReport()}),/ULTRA_OUTPUT_UNSAFE/);
});
test('transport failure consumes only one request; no secret shown in error',async t=>{
 const f=fixture(t);let n=0;
 await assert.rejects(runUltraMasterReview({packet:f.packet,apiKey:key,consent:OWNER_CONSENT,
  root:f.root,outputDirectory:f.out,fetchImpl:async()=>{n++;return new Response('unauthorized',{status:401})}}),/ULTRA_ENDPOINT_UNAVAILABLE/);
 assert.equal(n,1);assert.deepEqual(fs.readdirSync(f.out),[]);
});
