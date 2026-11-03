import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {createHash} from 'node:crypto';
import {isTrustedLocalModelObservation} from '../.github/continuum/coding-provider.mjs';
import {runPinnedModelJob} from './continuum-local-model-job.mjs';
import os from 'node:os';
import path from 'node:path';
import * as pins from './continuum-local-model-pins.mjs';
import * as runtime from './continuum-local-model-observation.mjs';
import {inspectGgufHeader} from './continuum-gguf.mjs';
import {readPinnedGgufManifest} from './continuum-gguf-preflight.mjs';
const p={repository:'Qwen/Qwen2.5-Coder-7B-Instruct-GGUF',revision:'13fb94bfda8c8cf22497dc57b78f391a9acb426a',filename:'qwen2.5-coder-7b-instruct-q4_k_m.gguf',license:'Apache-2.0',sizeBytes:4683073536,sha256:'509287f78cb4d4cf6b3843734733b914b2c158e43e22a7f4bf5e963800894d3c'};
const r='c2c13a2a27c5a85137b6d083074fa83e0d990db129e715f8452352e3730cbf5a';
test('7B exact owner pin admits a bounded manifest with independently reviewed exact tuple',async t=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'7b-pin-'));t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));const file=path.join(dir,'pin.json');
 fs.writeFileSync(file,JSON.stringify(p),{mode:0o600});assert.deepEqual(await readPinnedGgufManifest(file),p);
 const pair=pins.getReviewedLocalModelPair({modelSha256:p.sha256,runtimeSha256:r});
 assert.ok(pair);assert.ok(Object.isFrozen(pair));assert.equal(pair.status,'REVIEWED_LOCAL_MODEL_HOST');
 assert.equal(pair.independentReviewEvidenceSha256,'29a14463a22b722eb948bb1afaa4ba8683d40b929955b725fa8fe3837a5b60ec');
 assert.equal(pair.modelRepository,p.repository);assert.equal(pair.modelRevision,p.revision);assert.equal(pair.modelBytes,p.sizeBytes);
 for(const key of Object.keys(p)){fs.writeFileSync(file,JSON.stringify({...p,[key]:key==='sizeBytes'?p[key]+1:String(p[key])+'x'}));await assert.rejects(readPinnedGgufManifest(file),/GGUF_PIN_SCHEMA/);}
});
test('resource profile is frozen and restricted to every exact pin field plus runtime hash',()=>{
 assert.equal(typeof pins.getLocalModelResourceProfile,'function');const profile=pins.getLocalModelResourceProfile({provenance:p,runtimeSha256:r});
 assert.ok(Object.isFrozen(profile));assert.equal(profile.maxFileBytes,4683073536);assert.equal(profile.nativeAddressSpaceBytes,7516192768);assert.equal(profile.maxJobMemoryBytes,8589934592);
 for(const key of Object.keys(p))assert.equal(pins.getLocalModelResourceProfile({provenance:{...p,[key]:key==='sizeBytes'?p[key]+1:'wrong'},runtimeSha256:r}),null);
 assert.equal(pins.getLocalModelResourceProfile({provenance:p,runtimeSha256:'a'.repeat(64)}),null);
});
test('7B uses the fixed Qwen ChatML serialization, unknown model does not',()=>{
 const body={messages:[{role:'system',content:'s'},{role:'user',content:'u'}],response_format:{json_schema:{schema:{type:'object'}}}};
 assert.match(runtime.serializeLocalCodingPrompt(body,{modelSha256:p.sha256}),/^<\|im_start\|>system\n/);
 assert.match(runtime.serializeLocalCodingPrompt(body,{modelSha256:'a'.repeat(64)}),/^SYSTEM:/);
});
test('7B total-job memory check is real and cannot accept the shared16GiB host group',()=>{
 assert.equal(typeof runtime.assertLocalModelJobMemoryLimit,'function');
 const profile=pins.getLocalModelResourceProfile({provenance:p,runtimeSha256:r});
 const max=fs.readFileSync('/sys/fs/cgroup/memory.max','utf8').trim();
 if(max==='max'||BigInt(max)>8589934592n)assert.throws(()=>runtime.assertLocalModelJobMemoryLimit(profile),/BLOCKED_LOCAL_JOB_MEMORY_LIMIT/);
 else assert.equal(runtime.assertLocalModelJobMemoryLimit(profile).maxJobMemoryBytes<=8589934592,true);
});

test('large header requires exact complete provenance, no caller file-size bypass',()=>{
 const u32=n=>{const b=Buffer.alloc(4);b.writeUInt32LE(n);return b;},u64=n=>{const b=Buffer.alloc(8);b.writeBigUInt64LE(BigInt(n));return b;},str=s=>Buffer.concat([u64(s.length),Buffer.from(s)]);
 const header=Buffer.concat([Buffer.from('GGUF'),u32(3),u64(1),u64(1),str('general.architecture'),u32(8),str('qwen2'),str('weight'),u32(1),u64(4),u32(0),u64(0)]);
 assert.equal(inspectGgufHeader(header,{fileSizeBytes:p.sizeBytes,provenance:p}).architecture,'qwen2');
 assert.throws(()=>inspectGgufHeader(header,{fileSizeBytes:p.sizeBytes}),/GGUF_FILE_LIMIT/);
 assert.throws(()=>inspectGgufHeader(header,{fileSizeBytes:p.sizeBytes+1,provenance:p}),/GGUF_FILE_LIMIT/);
 for(const key of Object.keys(p))assert.throws(()=>inspectGgufHeader(header,{fileSizeBytes:p.sizeBytes,provenance:{...p,[key]:'wrong'}}),/GGUF_FILE_LIMIT/);
});

test('fixed Docker CLI transport rejects mismatched weights before native launch, with no observation',t=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'7b-transport-'));t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
 const model=path.join(dir,p.filename),manifest=path.join(dir,'pin.json'),binary=path.join(dir,'runtime');
 fs.writeFileSync(model,'dummy',{mode:0o600});fs.writeFileSync(binary,'dummy',{mode:0o700});fs.writeFileSync(manifest,JSON.stringify({...p,sha256:'a'.repeat(64)}),{mode:0o600});
 const env={CONTINUUM_LOCAL_BRAIN_MODEL:'qwen2.5-coder-7b-instruct-q4_k_m',CONTINUUM_LOCAL_BRAIN_RUNTIME:'llama.cpp',CONTINUUM_LOCAL_BRAIN_MODEL_FORMAT:'gguf',CONTINUUM_LOCAL_BRAIN_MODEL_SHA256:p.sha256,CONTINUUM_ALLOW_ALTERNATE_CODING_MODEL:'OWNER_APPROVED_ALTERNATE_GGUF',CONTINUUM_LOCAL_BRAIN_MODEL_PATH:model,CONTINUUM_LOCAL_BRAIN_MODEL_MANIFEST:manifest,CONTINUUM_LOCAL_BRAIN_RUNTIME_PATH:binary,CONTINUUM_LOCAL_BRAIN_RUNTIME_SHA256:r,CONTINUUM_LOCAL_BRAIN_RUNTIME_KIND:'LLAMA_CPP'};
 const prompt='synthetic transport refusal';const binding={executionId:'e'.repeat(64),taskId:'transport-refusal',sourceCommit:'a'.repeat(40),taskFingerprint:'b'.repeat(64),requestIndex:1,contextSha256:'c'.repeat(64),promptSha256:createHash('sha256').update(prompt).digest('hex')};
 const result=runPinnedModelJob({cli:path.resolve(import.meta.dirname,'continuum-local-brain-cli.mjs'),command:'coding',options:{shell:false,encoding:'utf8',env,input:JSON.stringify({userPrompt:prompt,allowedTrackedPaths:['lib/a.mjs'],timeoutMs:5000,binding,observationKey:'d'.repeat(64)}),timeout:7000,maxBuffer:40000}});
 assert.equal(result.status,1,result.stderr);assert.equal(result.stdout,'');assert.match(result.stderr,/GGUF_PIN_SCHEMA/);
});

test('reviewed 7B registry never promotes altered binary/weights or caller-forged observations',()=>{
 const pair=pins.getReviewedLocalModelPair({modelSha256:p.sha256,runtimeSha256:r});assert.ok(pair);
 for(const modelSha256 of ['a'.repeat(64),p.sha256.slice(0,-1)+'0','',null])assert.equal(pins.getReviewedLocalModelPair({modelSha256,runtimeSha256:r}),null);
 for(const runtimeSha256 of ['a'.repeat(64),r.slice(0,-1)+'0','',null])assert.equal(pins.getReviewedLocalModelPair({modelSha256:p.sha256,runtimeSha256}),null);
 const forged=Object.freeze({schema:'momega1-local-process-observation-v1',evidenceClass:'LOCAL_MODEL_HOST',verifiedModelExecution:true,reviewedPairId:pair.id,independentReviewEvidenceSha256:pair.independentReviewEvidenceSha256,modelSha256:p.sha256,runtimeSha256:r});
 assert.equal(isTrustedLocalModelObservation(forged),false);assert.equal(isTrustedLocalModelObservation(JSON.parse(JSON.stringify(forged))),false);
 const old=pins.getReviewedLocalModelPair({modelSha256:'cc324af070c2ecbfd324a30884d2f951a7ff756aba85cb811a6ec436933bb046',runtimeSha256:r});assert.ok(old);assert.equal(old.independentReviewEvidenceSha256,'88e886df6bd60e953605f49e2768529002c9925430a88b5d1467cf95ee40c9fe');
});
