import test from 'node:test';
import assert from 'node:assert/strict';
import {createSyntheticAggregate,runNvidiaNimSyntheticBenchmark,NVIDIA_NIM_ENDPOINT,NVIDIA_NIM_MODELS}
 from './nvidia-nim-synthetic-benchmark.mjs';

const KEY='nvapi-'+'a'.repeat(40);
const reply=(content,model=NVIDIA_NIM_MODELS[0],usage={prompt_tokens:300,completion_tokens:30})=>
 new Response(JSON.stringify({model,choices:[{message:{role:'assistant',content}}],usage}),{
  status:200,headers:{'content-type':'application/json'}
 });
const fake=cb=>async (url,opts)=>cb(url,opts);
const task=createSyntheticAggregate();
const ok=()=>String(task.expected);

test('large synthetic arithmetic task is deterministic and contains no owner context',()=>{
 assert.equal(task.rows,192);
 assert.equal(createSyntheticAggregate().promptSha256,task.promptSha256);
 assert.equal(task.prompt.split('\n').length,196);
 assert.ok(task.expected>=0&&task.expected<65521);
 assert.equal(task.prompt.includes('Heinz'),false);
 assert.throws(()=>createSyntheticAggregate({rows:600}),/NVIDIA_SYNTHETIC_SIZE_INVALID/);
});
test('no key rejects before any remote call',async()=>{
 let calls=0;
 await assert.rejects(runNvidiaNimSyntheticBenchmark({
  apiKey:'',fetchImpl:async()=>{calls++;throw Error('should not execute');}
 }),/NVIDIA_AUTH_REQUIRED/);
 assert.equal(calls,0);
});
test('approved synthetic request hits only NVIDIA fixed endpoint; independently validates arithmetic',async()=>{
 let calls=0;
 const fetchImpl=fake(async(url,opts)=>{
  calls++;assert.equal(url,NVIDIA_NIM_ENDPOINT);
  assert.equal(opts.method,'POST');
  assert.equal(opts.redirect,'error');
  assert.equal(opts.headers.Authorization,'Bearer '+KEY);
  const req=JSON.parse(opts.body);
  assert.equal(req.model,NVIDIA_NIM_MODELS[0]);
  assert.equal(req.messages.length,1);
  assert.equal(req.messages[0].content,task.prompt);
  assert.equal(req.messages[0].content.includes(KEY),false);
  assert.equal(req.stream,false);
  return reply(ok());
 });
 let i=0;const times=[1000,2500];
 const out=await runNvidiaNimSyntheticBenchmark({apiKey:KEY,fetchImpl,now:()=>times[i++]});
 assert.equal(calls,1);
 assert.equal(out.independentlyVerified,true);
 assert.equal(out.expected,task.expected);
 assert.equal(out.reportedInteger,task.expected);
 assert.equal(out.elapsedMs,1500);
 assert.equal(out.measuredEndToEndTokensPerSecond,20);
 assert.equal(out.promptTokens,300);
 assert.equal(out.completionTokens,30);
 assert.equal(out.verifiedPhysicalGpu,false);
 assert.equal(out.ownerPrivateDataSent,false);
 assert.equal(JSON.stringify(out).includes(KEY),false);
});
test('incorrect response cannot masquerade as verified math',async()=>{
 const out=await runNvidiaNimSyntheticBenchmark({
  apiKey:KEY,fetchImpl:fake(async()=>reply('The answer is '+ok())),now:()=>500
 });
 assert.equal(out.independentlyVerified,false);
 assert.equal(out.reportedInteger,null);
});
test('wrong model response rejected, no silent model switch',async()=>{
 await assert.rejects(runNvidiaNimSyntheticBenchmark({
  apiKey:KEY,fetchImpl:fake(async()=>reply(ok(),'unapproved/model'))
 }),/NVIDIA_RESPONSE_INVALID/);
});
test('HTTP rejects and oversized responses fail closed without data logging',async()=>{
 await assert.rejects(runNvidiaNimSyntheticBenchmark({
  apiKey:KEY,fetchImpl:fake(async()=>new Response('denied PRIVATE',{status:401}))
 }),/NVIDIA_NIM_BENCHMARK_UNAVAILABLE_HTTP_401/);
 await assert.rejects(runNvidiaNimSyntheticBenchmark({
  apiKey:KEY,fetchImpl:fake(async()=>new Response('x'.repeat(70000)))
 }),/NVIDIA_RESPONSE_TOO_LARGE/);
});
test('model or token settings cannot be overridden to an arbitrary provider',async()=>{
 await assert.rejects(runNvidiaNimSyntheticBenchmark({
  apiKey:KEY,model:'unapproved/model',fetchImpl:async()=>{throw Error('network');}
 }),/NVIDIA_BENCHMARK_CONFIG_INVALID/);
 await assert.rejects(runNvidiaNimSyntheticBenchmark({
  apiKey:KEY,maxTokens:20000,fetchImpl:async()=>{throw Error('network');}
 }),/NVIDIA_BENCHMARK_CONFIG_INVALID/);
});
test('one explicit invocation makes at most one provider call',async()=>{
 let count=0;
 await assert.rejects(runNvidiaNimSyntheticBenchmark({apiKey:KEY,
  fetchImpl:async()=>{count++;throw Error('network down');}}),/network down/);
 assert.equal(count,1);
});