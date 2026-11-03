/** Independent approved-pair registry contract, frozen before candidate inspection. */
import test from 'node:test';import assert from 'node:assert/strict';import path from 'node:path';import {pathToFileURL} from 'node:url';
const target=path.resolve(process.env.MOMEGA1_TARGET??'.');
const registry=await import(pathToFileURL(path.join(target,'scripts/continuum-local-model-pins.mjs')));
const get=registry.getReviewedLocalModelPair;
const modelSha256='cc324af070c2ecbfd324a30884d2f951a7ff756aba85cb811a6ec436933bb046';
const actualRuntime=process.env.MOMEGA1_REVIEWED_RUNTIME_SHA256;
test('registry exposes exact tuple getter',()=>assert.equal(typeof get,'function'));
test('unknown and malformed pairs cannot mint reviewed admission',()=>{
 for(const pair of [{},{modelSha256},{runtimeSha256:'a'.repeat(64)},{modelSha256,runtimeSha256:'f'.repeat(64)},{modelSha256:'a'.repeat(64),runtimeSha256:actualRuntime},{modelSha256,runtimeSha256:actualRuntime?.toUpperCase()},{modelSha256:{toString:()=>modelSha256},runtimeSha256:actualRuntime}])assert.equal(get(pair),null);
});
test('caller supplied approval fields cannot admit unknown binary',()=>{
 assert.equal(get({modelSha256,runtimeSha256:'e'.repeat(64),status:'REVIEWED_LOCAL_MODEL_HOST',independentReviewEvidenceSha256:'b'.repeat(64),verified:true}),null);
});
test('environment flags cannot register arbitrary model/runtime pair',()=>{
 const before={...process.env};try{
  process.env.CONTINUUM_LOCAL_BRAIN_RUNTIME_KIND='LLAMA_CPP';process.env.CONTINUUM_LOCAL_MODEL_REVIEWED='true';process.env.CONTINUUM_LOCAL_BRAIN_MODEL_SHA256=modelSha256;process.env.CONTINUUM_LOCAL_BRAIN_RUNTIME_SHA256='f'.repeat(64);
  assert.equal(get({modelSha256,runtimeSha256:'f'.repeat(64)}),null);
 }finally{for(const k of Object.keys(process.env))if(!(k in before))delete process.env[k];Object.assign(process.env,before);}
});
test('actual reviewed pair requires immutable exact official provenance and independent evidence',()=>{
 assert.match(actualRuntime??'',/^[a-f0-9]{64}$/,'explicit independently observed binary hash is required');
 const pair=get({modelSha256,runtimeSha256:actualRuntime});
 if(process.env.MOMEGA1_EXPECT_REVIEWED!=='1'){assert.equal(pair,null);return;}
 assert.ok(pair);assert.equal(pair.status,'REVIEWED_LOCAL_MODEL_HOST');assert.equal(pair.modelSha256,modelSha256);assert.equal(pair.runtimeSha256,actualRuntime);assert.equal(pair.modelRepository,'Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF');assert.equal(pair.modelRevision,'f86cb2c1fa58255f8052cc32aeede1b7482d4361');assert.equal(pair.modelFilename,'qwen2.5-coder-1.5b-instruct-q4_k_m.gguf');assert.equal(pair.modelBytes,1117320768);assert.equal(pair.runtimeRepository,'ggml-org/llama.cpp');assert.equal(pair.runtimeSourceCommit,'6cb3e672e8d10fd4feab1a048bf2967c40623133');assert.match(pair.buildEvidenceSha256,/^[a-f0-9]{64}$/);assert.match(pair.independentReviewEvidenceSha256,/^[a-f0-9]{64}$/);assert.equal(Object.isFrozen(pair),true);assert.throws(()=>{pair.runtimeSha256='a'.repeat(64);},TypeError);assert.equal(get({modelSha256,runtimeSha256:actualRuntime}).runtimeSha256,actualRuntime);
});
