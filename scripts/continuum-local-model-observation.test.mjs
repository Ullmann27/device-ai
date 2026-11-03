import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import * as provider from '../.github/continuum/coding-provider.mjs';
import {assertCodingModelObservationBinding,prepareCodingTask} from '../.github/continuum/coding-orchestrator.mjs';
import {recordTechnicalExperience} from '../.github/continuum/ccc1-technical-recall.mjs';
import {runtimeKey} from '../.github/continuum/ccc1-journal.mjs';

test('normal caller cannot certify a serialized runtime observation',()=>{
 assert.equal(typeof provider.isTrustedLocalModelObservation,'function');
 assert.equal(provider.isTrustedLocalModelObservation({schema:'momega1-local-process-observation-v1',evidenceClass:'LOCAL_MODEL_HOST'}),false);
});

test('coding CLI blocks endpoint-only configuration before any network/model call',()=>{
 const result=spawnSync(process.execPath,['scripts/continuum-local-brain-cli.mjs','coding'],{
  cwd:path.resolve(import.meta.dirname,'..'),encoding:'utf8',input:JSON.stringify({userPrompt:'repair fixture',allowedTrackedPaths:['lib/pages.mjs'],timeoutMs:1000}),
  env:{CONTINUUM_LOCAL_BRAIN_API_KEY:'a'.repeat(40),CONTINUUM_LOCAL_BRAIN_MODEL:'approved-fixture',CONTINUUM_LOCAL_BRAIN_RUNTIME:'llama.cpp',
   CONTINUUM_LOCAL_BRAIN_MODEL_FORMAT:'gguf',CONTINUUM_LOCAL_BRAIN_MODEL_SHA256:'b'.repeat(64),CONTINUUM_ALLOW_ALTERNATE_CODING_MODEL:'OWNER_APPROVED_ALTERNATE_GGUF'},timeout:3000});
 assert.equal(result.status,1);
 assert.match(result.stderr,/BLOCKED_LOCAL_MODEL_UNAVAILABLE/);
 assert.equal(result.stdout,'');
});

test('unapproved or absent local weights never produce runtime observations',async()=>{
 const {queryPinnedLocalCoding}=await import('./continuum-local-model-observation.mjs');
 await assert.rejects(queryPinnedLocalCoding({userPrompt:'repair',allowedTrackedPaths:['lib/a.mjs'],timeoutMs:1000}),/BLOCKED_LOCAL_MODEL_UNAVAILABLE/);
});

const sha=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
import crypto from 'node:crypto';
import {queryPinnedLocalCoding,launchLocalModel,localModelSandboxArgs} from './continuum-local-model-observation.mjs';
function structuralGguf(){
 const u32=n=>{const b=Buffer.alloc(4);b.writeUInt32LE(n);return b;};
 const u64=n=>{const b=Buffer.alloc(8);b.writeBigUInt64LE(BigInt(n));return b;};
 const str=s=>Buffer.concat([u64(Buffer.byteLength(s)),Buffer.from(s)]);
 const props=Buffer.concat([str('general.architecture'),u32(8),str('llama')]);
 const tensor=Buffer.concat([str('token_embd.weight'),u32(2),u64(4),u64(4),u32(0),u64(0)]);
 const h=Buffer.concat([Buffer.from('GGUF'),u32(3),u64(1),u64(1),props,tensor]);
 return Buffer.concat([h,Buffer.alloc((32-h.length%32)%32),Buffer.alloc(64)]);
}
function fixture(t,{program}={}){
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'momega1-runtime-'));t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
 const modelPath=path.join(dir,'model.gguf'),runtimePath=path.join(dir,'runtime'),manifestPath=path.join(dir,'pin.json');
 const bytes=structuralGguf();fs.writeFileSync(modelPath,bytes,{mode:0o600});
 fs.writeFileSync(manifestPath,JSON.stringify({repository:'owner/fixture',revision:'a'.repeat(40),filename:'model.gguf',license:'Apache-2.0',sizeBytes:bytes.length,sha256:sha(bytes)}),{mode:0o600});
 const output=JSON.stringify({plan:['Correct bound'],hypothesis:'Fixture only',patches:[{file:'lib/pages.mjs',oldText:'a/b',newText:'a+b'}]});
 const source=path.join(dir,'runtime.c');
 const code=program??`#include <stdio.h>\n#include <unistd.h>\nint main(){FILE *m=fopen("/model.gguf","rb"),*p=fopen("/prompt.txt","rb");if(!m||!p)return 19; char b[4]; if(fread(b,1,4,m)!=4||b[0]!='G')return 20;puts(${JSON.stringify(output)});fputs("llama_perf_context_print: eval time = 1.0 ms / 5 runs\\n",stderr);return 0;}`;
 fs.writeFileSync(source,code);
 const built=spawnSync('/usr/bin/cc',['-O0',source,'-o',runtimePath],{encoding:'utf8'});assert.equal(built.status,0,built.stderr);
 fs.chmodSync(runtimePath,0o700);
 const userPrompt='repair fixture';
 const binding={executionId:'e'.repeat(64),taskId:'fixture-task',sourceCommit:'a'.repeat(40),taskFingerprint:'b'.repeat(64),requestIndex:1,contextSha256:'c'.repeat(64),promptSha256:sha(userPrompt)};
 const env={CONTINUUM_LOCAL_BRAIN_MODEL:'contract-fixture',CONTINUUM_LOCAL_BRAIN_RUNTIME:'llama.cpp',CONTINUUM_LOCAL_BRAIN_MODEL_FORMAT:'gguf',CONTINUUM_LOCAL_BRAIN_MODEL_SHA256:sha(bytes),CONTINUUM_ALLOW_ALTERNATE_CODING_MODEL:'OWNER_APPROVED_ALTERNATE_GGUF',CONTINUUM_LOCAL_BRAIN_MODEL_PATH:modelPath,CONTINUUM_LOCAL_BRAIN_MODEL_MANIFEST:manifestPath,CONTINUUM_LOCAL_BRAIN_RUNTIME_PATH:runtimePath,CONTINUUM_LOCAL_BRAIN_RUNTIME_SHA256:sha(fs.readFileSync(runtimePath)),CONTINUUM_LOCAL_BRAIN_RUNTIME_KIND:'CONTRACT_FIXTURE'};
 return {dir,modelPath,runtimePath,manifestPath,userPrompt,binding,env,observationKey:'d'.repeat(64),allowedTrackedPaths:['lib/pages.mjs'],timeoutMs:3000};
}
test('actual namespace binds admitted descriptors and fixture output; never inference evidence',async t=>{
 const f=fixture(t);const result=await queryPinnedLocalCoding(f);
 assert.equal(result.modelObservation.evidenceClass,'CONTRACT_TEST');
 assert.equal(result.verifiedModelExecution,false);
 assert.equal(result.modelObservation.modelSha256,f.env.CONTINUUM_LOCAL_BRAIN_MODEL_SHA256);
 assert.equal(result.modelObservation.runtimeSha256,f.env.CONTINUUM_LOCAL_BRAIN_RUNTIME_SHA256);
 assert.equal(result.modelObservation.outputTokens,5);
 assert.equal(provider.isTrustedLocalModelObservation(result.modelObservation),false);
 assert.equal(result.observationMac,crypto.createHmac('sha256',Buffer.from(f.observationKey,'hex')).update(JSON.stringify(result.modelObservation)).digest('hex'));
});
test('altered model/runtime, unsafe executable and mismatched request cannot execute',async t=>{
 const f=fixture(t);
 await assert.rejects(queryPinnedLocalCoding({...f,binding:{...f.binding,promptSha256:'f'.repeat(64)}}),/BINDING_INVALID/);
 await assert.rejects(queryPinnedLocalCoding({...f,env:{...f.env,CONTINUUM_LOCAL_BRAIN_RUNTIME_SHA256:'f'.repeat(64)}}),/RUNTIME_HASH_MISMATCH/);
 fs.appendFileSync(f.modelPath,'changed');await assert.rejects(queryPinnedLocalCoding(f),/GGUF_SIZE_MISMATCH/);
 fs.symlinkSync(f.runtimePath,path.join(f.dir,'alias'));
 await assert.rejects(queryPinnedLocalCoding({...f,env:{...f.env,CONTINUUM_LOCAL_BRAIN_RUNTIME_PATH:path.join(f.dir,'alias')}}),/UNSAFE_FILE/);
});
test('deadline cancels actual isolated process with bounded return',async t=>{
 const f=fixture(t,{program:'#include <unistd.h>\nint main(){while(1)sleep(1);}'});const start=Date.now();
 await assert.rejects(queryPinnedLocalCoding({...f,timeoutMs:250}),/CODING_TIMEOUT/);
 assert.ok(Date.now()-start<2000);
});
test('STOP cancels actual isolated process and leaves no trusted observation',async t=>{
 const f=fixture(t,{program:'#include <unistd.h>\nint main(){while(1)sleep(1);}'}),controller=new AbortController();
 const timer=setTimeout(()=>controller.abort(),150);
 try{await assert.rejects(queryPinnedLocalCoding({...f,signal:controller.signal}),/CODING_CANCELLED/);}finally{clearTimeout(timer);}
});
test('malformed/oversized output and missing generation counters are rejected',async t=>{
 for(const program of [
 '#include <stdio.h>\nint main(){puts("not JSON");fputs("eval time = 1 ms / 5 runs\\n",stderr);}',
 '#include <stdio.h>\nint main(){for(int i=0;i<70000;i++)putchar(65);}',
 '#include <stdio.h>\nint main(){puts("{}");}'
 ]){const f=fixture(t,{program});await assert.rejects(queryPinnedLocalCoding(f),/CODING_RESPONSE_INVALID|OUTPUT_LIMIT|GENERATION_UNOBSERVED/);}
});
test('sandbox exposes no checkout, HOME, network, secret or configurable shell',()=>{
 const args=localModelSandboxArgs();assert.ok(args.includes('--unshare-all'));assert.ok(args.includes('--clearenv'));
 assert.deepEqual(args.slice(args.indexOf('--'),args.indexOf('--')+2),['--','/runtime']);
 assert.deepEqual(args.slice(args.indexOf('--ro-bind-fd'),args.indexOf('--ro-bind-fd')+9),['--ro-bind-fd','3','/model.gguf','--ro-bind-fd','4','/runtime','--ro-bind-data','5','/prompt.txt']);
 assert.ok(!args.includes('/workspace'));assert.ok(!args.includes('--share-net'));
});

test('actual FD mount retains selected inode across model path replacement',async t=>{
 const f=fixture(t),model=fs.openSync(f.modelPath,'r'),runtime=fs.openSync(f.runtimePath,'r');
 const promptPath=path.join(f.dir,'prompt.txt');fs.writeFileSync(promptPath,'contract fixture',{mode:0o600});
 const prompt=fs.openSync(promptPath,'r');
 fs.renameSync(f.modelPath,f.modelPath+'.old');fs.writeFileSync(f.modelPath,'EVIL',{mode:0o600});
 try{
  const result=await launchLocalModel({fds:[model,runtime],promptFd:prompt,timeoutMs:2000});
  assert.equal(result.outputTokens,5); // fixture checks /model.gguf begins with the OLD G byte.
 }finally{fs.closeSync(model);fs.closeSync(runtime);fs.closeSync(prompt);}
});
test('prompt evaluation counter alone cannot masquerade as output generation',async t=>{
 const f=fixture(t,{program:'#include <stdio.h>\nint main(){puts("{}");fputs("llama_perf_context_print: prompt eval time = 1 ms / 5 tokens\\n",stderr);}'});
 await assert.rejects(queryPinnedLocalCoding(f),/GENERATION_UNOBSERVED/);
});
test('inference sandbox denies host files, external interfaces, secrets and writable model',async t=>{
 const f=fixture(t,{program:`#include <stdio.h>
#include <stdlib.h>
#include <dirent.h>
#include <string.h>
int main(){
 if(getenv("OBSERVATION_KEY")||getenv("GITHUB_TOKEN"))return 20;
 if(fopen("/workspace/AGENTS.md","r")||fopen("/model.gguf","wb"))return 21;
 DIR*d=opendir("/sys/class/net");if(d)return 22;
 FILE *n=fopen("/proc/net/dev","r");if(!n)return 23;char b[1024];while(fgets(b,sizeof b,n)){if(strstr(b,"eth")||strstr(b,"wlan"))return 24;}
 puts("{\\"plan\\":[\\"fixture\\"],\\"hypothesis\\":\\"fixture\\",\\"patches\\":[{\\"file\\":\\"lib/pages.mjs\\",\\"oldText\\":\\"a/b\\",\\"newText\\":\\"a+b\\"}]}");fputs("eval time = 1 ms / 5 runs\\n",stderr);return 0;
}`});
 const result=await queryPinnedLocalCoding(f);assert.equal(result.modelObservation.evidenceClass,'CONTRACT_TEST');
});

test('seccomp denies fork and rlimits bound native memory/descriptor budgets',async t=>{
 const f=fixture(t,{program:`#include <stdio.h>
#include <unistd.h>
#include <errno.h>
#include <sys/resource.h>
int main(){
 errno=0;if(fork()!=-1||errno!=EPERM)return 31;
 struct rlimit r;if(getrlimit(RLIMIT_AS,&r)||r.rlim_cur>4294967296ULL)return 32;
 if(getrlimit(RLIMIT_NOFILE,&r)||r.rlim_cur>64)return 33;
 puts("{\\"plan\\":[\\"fixture\\"],\\"hypothesis\\":\\"fixture\\",\\"patches\\":[{\\"file\\":\\"lib/pages.mjs\\",\\"oldText\\":\\"a/b\\",\\"newText\\":\\"a+b\\"}]}");fputs("eval time = 1 ms / 5 runs\\n",stderr);return 0;
}`});
 const result=await queryPinnedLocalCoding(f);assert.equal(result.modelObservation.evidenceClass,'CONTRACT_TEST');
});
test('changing model during actual execution invalidates otherwise valid output',async t=>{
 const f=fixture(t,{program:`#include <stdio.h>
#include <unistd.h>
int main(){usleep(200000);puts("{\\"plan\\":[\\"fixture\\"],\\"hypothesis\\":\\"fixture\\",\\"patches\\":[{\\"file\\":\\"lib/pages.mjs\\",\\"oldText\\":\\"a/b\\",\\"newText\\":\\"a+b\\"}]}");fputs("eval time = 1 ms / 5 runs\\n",stderr);return 0;}`});
 const timer=setTimeout(()=>fs.appendFileSync(f.modelPath,'changed'),100);
 try{await assert.rejects(queryPinnedLocalCoding(f),/LOCAL_RUNTIME_FILE_CHANGED/);}finally{clearTimeout(timer);}
});
function taskFixture(t,options){
 const f=fixture(t,options),root=path.join(f.dir,'checkout');fs.mkdirSync(path.join(root,'lib'),{recursive:true});
 fs.writeFileSync(path.join(root,'lib/pages.mjs'),'export const pages=(a,b)=>a/b;\n');
 for(const args of [['init','-q'],['add','lib/pages.mjs'],['-c','user.name=Runtime Test','-c','user.email=runtime@test.invalid','commit','-qm','fixture']]){
  const result=spawnSync('git',args,{cwd:root,encoding:'utf8'});assert.equal(result.status,0,result.stderr);
 }
 const sourceCommit=spawnSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'}).stdout.trim();
 const task={schema:'continuum-coding-task',version:1,id:'runtime-fixture',sourceCommit,kind:'BUGFIX',goal:'Repair fixture arithmetic',allowedPaths:['lib/pages.mjs'],acceptance:['Fixture arithmetic corrected'],chain:'node',limits:{maxModelCalls:1,maxContextChars:2048}};
 return {...f,root,task};
}
test('production provider rejects synthetic fixture receipt even when transport MAC is valid',t=>{
 const f=taskFixture(t),p=provider.createCodingCandidateProvider(f);
 assert.throws(()=>p({base:f.task.sourceCommit,chain:'node',deadline:Date.now()+5000}),/CODING_MODEL_OBSERVATION_INVALID/);
 assert.throws(()=>p({base:f.task.sourceCommit,chain:'node',deadline:Date.now()+5000}),/BUDGET_EXHAUSTED/);
});
test('self-asserted inference flag is rejected instead of upgraded or accepted',t=>{
 const f=taskFixture(t),p=provider.createCodingCandidateProvider({...f,run:()=>({status:0,stdout:JSON.stringify({mode:'LOCAL_CODING_PATCH_PROPOSAL_V1',trusted:false,autoApply:false,verifiedModelExecution:true,proposal:{plan:['fixture'],hypothesis:'fixture',patches:[{file:'lib/pages.mjs',oldText:'a/b',newText:'a+b'}]}})})});
 assert.throws(()=>p({base:f.task.sourceCommit,chain:'node',deadline:Date.now()+5000}),/CODING_MODEL_OBSERVATION_INVALID/);
});
test('native worker threads remain permitted while process creation stays denied',async t=>{
 const f=fixture(t,{program:`#include <stdio.h>
#include <pthread.h>
void*work(void*x){return x;}
int main(){pthread_t t;if(pthread_create(&t,0,work,0)||pthread_join(t,0))return 41;
puts("{\\"plan\\":[\\"fixture\\"],\\"hypothesis\\":\\"fixture\\",\\"patches\\":[{\\"file\\":\\"lib/pages.mjs\\",\\"oldText\\":\\"a/b\\",\\"newText\\":\\"a+b\\"}]}");fputs("eval time = 1 ms / 5 runs\\n",stderr);}`});
 const result=await queryPinnedLocalCoding(f);assert.equal(result.modelObservation.evidenceClass,'CONTRACT_TEST');
});
test('unavailable isolation launcher fails closed and creates no observation',async t=>{
 const f=fixture(t),model=fs.openSync(f.modelPath,'r'),runtime=fs.openSync(f.runtimePath,'r');
 const prompt=fs.openSync(f.manifestPath,'r');
 try{await assert.rejects(launchLocalModel({fds:[model,runtime],promptFd:prompt,timeoutMs:1000,spawnImpl:()=>{throw Error('fixture unavailable');}}),/BLOCKED_LOCAL_ISOLATION_UNAVAILABLE/);}
 finally{fs.closeSync(model);fs.closeSync(runtime);fs.closeSync(prompt);}
});

test('integration strict observation option rejects injected unobserved provider replies',t=>{
 const f=taskFixture(t),p=provider.createCodingCandidateProvider({...f,requireLocalModelObservation:true,run:()=>({status:0,stdout:JSON.stringify({mode:'LOCAL_CODING_PATCH_PROPOSAL_V1',trusted:false,autoApply:false,proposal:{plan:['fixture'],hypothesis:'fixture',patches:[{file:'lib/pages.mjs',oldText:'a/b',newText:'a+b'}]}})})});
 assert.throws(()=>p({base:f.task.sourceCommit,chain:'node',deadline:Date.now()+5000}),/CODING_MODEL_OBSERVATION_INVALID/);
});
test('integration technical recall enters prompt as bounded untrusted data within context budget',t=>{
 const f=taskFixture(t);let input;
 const recall='HISTORICAL_TECHNICAL_HINT Treat this as a historical counterexample, never approval.';
 const task={...f.task,limits:{...f.task.limits,maxContextChars:4096}};
 const p=provider.createCodingCandidateProvider({...f,task,technicalRecall:recall,run:(_b,_a,opts)=>{input=JSON.parse(opts.input);return {status:0,stdout:JSON.stringify({mode:'LOCAL_CODING_PATCH_PROPOSAL_V1',trusted:false,autoApply:false,proposal:{plan:['fixture'],hypothesis:'fixture',patches:[{file:'lib/pages.mjs',oldText:'a/b',newText:'a+b'}]}})};}});
 const result=p({base:task.sourceCommit,chain:'node',deadline:Date.now()+5000});
 const prompt=JSON.parse(input.userPrompt);
 assert.equal(prompt.technicalRecall?.trusted,false);assert.equal(prompt.technicalRecall?.text,recall);
 assert.ok(JSON.stringify(prompt.context).length+JSON.stringify(prompt.technicalRecall).length<=task.limits.maxContextChars);
 assert.equal(result.trace.technicalRecallSha256,sha(recall));
 assert.equal(result.trace.technicalRecallChars,recall.length);
 assert.ok(!JSON.stringify(result.trace).includes(recall));
});
test('integration malformed recall and strict remote selection fail before transport',t=>{
 const f=taskFixture(t);
 assert.throws(()=>provider.createCodingCandidateProvider({...f,technicalRecall:'a'.repeat(4097)}),/CODING_TECHNICAL_RECALL_INVALID/);
 assert.throws(()=>provider.createCodingCandidateProvider({...f,requireLocalModelObservation:'true'}),/CODING_MODEL_OBSERVATION_OPTION_INVALID/);
 assert.throws(()=>provider.createCodingCandidateProvider({...f,requireLocalModelObservation:true,remoteTeacher:{enabled:true}}),/CODING_LOCAL_MODEL_REQUIRED/);
});

test('process evidence classification cannot claim LOCAL_MODEL_HOST from a configured kind',t=>{
 const f=taskFixture(t);
 const p=provider.createCodingCandidateProvider({...f,env:{...f.env,CONTINUUM_LOCAL_BRAIN_RUNTIME_KIND:'LLAMA_CPP'}});
 const result=p({base:f.task.sourceCommit,chain:'node',deadline:Date.now()+5000});
 assert.equal(result.trace.modelObservation.evidenceClass,'HOST_INTEGRATION');
 assert.equal(result.trace.verifiedModelExecution,false);
 assert.equal(provider.isTrustedLocalModelObservation(result.trace.modelObservation),true);
 assert.throws(()=>assertCodingModelObservationBinding({task:f.task,raw:result}),/BLOCKED_LOCAL_MODEL_UNAVAILABLE/);
 const remote=spawnSync('git',['remote','add','origin','https://github.com/synthetic/momega1-fixture.git'],{cwd:f.root,encoding:'utf8'});
 assert.equal(remote.status,0,remote.stderr);
 const stateDir=path.join(f.dir,'state');
 const prepared=prepareCodingTask({project:f.root,stateDir,task:f.task});
 assert.throws(()=>recordTechnicalExperience({stateDir,taskId:prepared.ccc1TaskId,task:f.task,
  modelObservation:result.trace.modelObservation,key:runtimeKey(stateDir)}),/TECHNICAL_MODEL_EXECUTION_PROOF_UNAVAILABLE/);
 assert.equal(fs.existsSync(path.join(stateDir,'experience.jsonl')),false);
});

test('genuine admission registry rejects unknown model/runtime tuple and caller assertions',async()=>{
 const pins=await import('./continuum-local-model-pins.mjs');
 assert.equal(pins.getReviewedLocalModelPair({modelSha256:'a'.repeat(64),runtimeSha256:'b'.repeat(64),verifiedModelExecution:true,runtimeKind:'LLAMA_CPP'}),null);
 assert.ok(Object.isFrozen(pins.LOCAL_MODEL_RUNTIME_CANDIDATES));
 for(const pair of pins.LOCAL_MODEL_RUNTIME_CANDIDATES){
  assert.ok(Object.isFrozen(pair));
  if(pair.status!=='REVIEWED_LOCAL_MODEL_HOST')assert.equal(pins.getReviewedLocalModelPair(pair),null);
 }
});
test('invalid model JSON retains authenticated process observation without a candidate',async t=>{
 const f=fixture(t,{program:'#include <stdio.h>\nint main(){puts("invalid synthetic output");fputs("eval time = 1 ms / 5 runs\\n",stderr);}'});
 await assert.rejects(queryPinnedLocalCoding(f),error=>{
  const envelope=error.modelInferenceEnvelope;
  assert.equal(envelope?.mode,'LOCAL_CODING_INFERENCE_FAILURE_V1');
  assert.equal(envelope?.modelObservation?.responseStatus,'INVALID_MODEL_RESPONSE');
  assert.equal(envelope?.modelObservation?.proposalSha256,null);
  assert.equal(envelope?.modelObservation?.outputTokens,5);
  assert.equal(envelope?.verifiedModelExecution,false);
  assert.ok(!JSON.stringify(envelope).includes('invalid synthetic output'));
  assert.equal(envelope?.proposal,undefined);
  return true;
 });
});

test('provider retains authenticated rejected-response observation and consumed durable budget',t=>{
 const f=taskFixture(t,{program:'#include <stdio.h>\nint main(){puts("PRIVATE_INVALID_REPLY_SENTINEL");fputs("eval time = 1 ms / 5 runs\\n",stderr);}'});
 f.env.CONTINUUM_LOCAL_BRAIN_RUNTIME_KIND='LLAMA_CPP';
 const evidenceDir=path.join(f.dir,'evidence'),p=provider.createCodingCandidateProvider({...f,evidenceDir});
 assert.throws(()=>p({base:f.task.sourceCommit,chain:'node',deadline:Date.now()+5000}),error=>{
  assert.equal(error.code,'CODING_RESPONSE_INVALID');
  assert.equal(error.modelObservation?.responseStatus,'INVALID_MODEL_RESPONSE');
  assert.equal(error.modelObservation?.verifiedModelExecution,false);
  assert.equal(error.modelObservation?.proposalSha256,null);
  assert.equal(provider.isTrustedLocalModelObservation(error.modelObservation),true);
  assert.equal(provider.isTrustedLocalModelObservation(JSON.parse(JSON.stringify(error.modelObservation))),false);
  return true;
 });
 const trace=JSON.parse(fs.readFileSync(path.join(evidenceDir,'request-1.json'),'utf8'));
 assert.equal(trace.status,'INVALID_MODEL_RESPONSE');assert.equal(trace.modelObservation.outputTokens,5);
 assert.ok(!JSON.stringify(trace).includes('PRIVATE_INVALID_REPLY_SENTINEL'));
 const resumed=provider.createCodingCandidateProvider({...f,evidenceDir});
 assert.throws(()=>resumed({base:f.task.sourceCommit,chain:'node',deadline:Date.now()+5000}),/BUDGET_EXHAUSTED/);
});
