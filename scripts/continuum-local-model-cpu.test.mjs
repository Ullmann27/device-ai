/** Synthetic launcher contracts. Kernel success proofs require the real bounded job group; never inference. */
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {spawnSync} from 'node:child_process';
import {EventEmitter} from 'node:events';
import {PassThrough} from 'node:stream';
import {inspectBoundedModelTestHost} from './continuum-model-test-host.mjs';
import {getLocalModelResourceProfile} from './continuum-local-model-pins.mjs';
import {launchLocalModel,assertLocalModelJobMemoryLimit} from './continuum-local-model-observation.mjs';
const provenance={repository:'Qwen/Qwen2.5-Coder-7B-Instruct-GGUF',revision:'13fb94bfda8c8cf22497dc57b78f391a9acb426a',filename:'qwen2.5-coder-7b-instruct-q4_k_m.gguf',license:'Apache-2.0',sizeBytes:4683073536,sha256:'509287f78cb4d4cf6b3843734733b914b2c158e43e22a7f4bf5e963800894d3c'};
const profile=getLocalModelResourceProfile({provenance,runtimeSha256:'c2c13a2a27c5a85137b6d083074fa83e0d990db129e715f8452352e3730cbf5a'});
function fake({code=0,signal=null,hang=false}={}){
 let args,options;const child=new EventEmitter();child.stdout=new PassThrough();child.stderr=new PassThrough();child.pid=2147483647;
 child.kill=()=>setImmediate(()=>child.emit('close',null,'SIGKILL'));
 return {get args(){return args;},get options(){return options;},spawn(command,a,o){assert.equal(command,'/usr/bin/prlimit');args=a;options=o;
  if(!hang)setImmediate(()=>{child.stderr.write('eval time = 1.0 ms / 1 tokens\n');child.emit('close',code,signal);});return child;}};
}
const launch=(f,extra={})=>launchLocalModel({fds:[0,0],promptFd:0,timeoutMs:176123,spawnImpl:f.spawn,...extra});
// A missing kernel capability is not success evidence. Keep these cases visible as SKIP,
// and independently prove below that the real launcher refuses before spawning.
function boundedJobAvailable(t){
 const host=inspectBoundedModelTestHost();
 if(!host.available){
  assert.throws(()=>assertLocalModelJobMemoryLimit(profile),/BLOCKED_LOCAL_JOB_MEMORY_LIMIT/);
  t.skip('HOST_RESOURCE_PROOF_NOT_RUN: '+host.reason);return false;
 }
 // A guard that always rejects on a capable host must fail, never become a skip.
 assert.equal(assertLocalModelJobMemoryLimit(profile).maxJobMemoryBytes,host.memoryBytes);
 return true;
}
test('real launcher refuses an unavailable private job memory bound before any spawn',async()=>{
 const host=inspectBoundedModelTestHost();
 if(!host.available){
  let spawned=false;
  await assert.rejects(launchLocalModel({fds:[0,0],promptFd:0,timeoutMs:100,resourceProfile:profile,
   spawnImpl:()=>{spawned=true;throw Error('unexpected spawn');}}),/BLOCKED_LOCAL_JOB_MEMORY_LIMIT/);
  assert.equal(spawned,false);return;
 }
 const actual=assertLocalModelJobMemoryLimit(profile);
 assert.equal(actual.enforcement,'CGROUP_V2');assert.equal(actual.maxJobMemoryBytes,host.memoryBytes);
});
test('actual launcher gives exact private 7B profile two CPU seconds per remaining wall second',async t=>{
 if(!boundedJobAvailable(t))return;
 const f=fake();const result=await launch(f,{resourceProfile:profile});assert.ok(f.args.includes('--cpu=354'));assert.ok(f.args.includes('--as=7516192768'));
 assert.equal(result.nativeCpuSeconds,354);assert.equal(result.nativeCpuThreads,2);assert.equal(result.nativeWallTimeoutMs,176123);assert.equal(result.evidenceClass,'CONTRACT_TEST');
 assert.equal(f.options.env&&Object.keys(f.options.env).length,0);
});
test('legacy launcher CPU budget remains one CPU second per wall second and ignores env override',async()=>{
 process.env.CONTINUUM_LOCAL_BRAIN_NATIVE_CPU_THREADS='999';try{const f=fake();await launch(f);assert.ok(f.args.includes('--cpu=177'));assert.ok(f.args.includes('--as=4294967296'));}finally{delete process.env.CONTINUUM_LOCAL_BRAIN_NATIVE_CPU_THREADS;}
});
test('resource profile clone or arbitrary caller budget cannot reach spawn',async()=>{
 for(const resourceProfile of [{...profile},{nativeCpuThreads:999,requiresJobMemoryLimit:false}]){let called=false;
 await assert.rejects(launchLocalModel({fds:[0,0],promptFd:0,timeoutMs:100,resourceProfile,spawnImpl:()=>{called=true;throw Error();}}),/INVALID_LOCAL_RESOURCE_PROFILE/);assert.equal(called,false);}
});
test('two-thread CPU allowance leaves the existing wall cancellation deadline unchanged',async t=>{
 if(!boundedJobAvailable(t))return;
 const f=fake({hang:true}),start=Date.now();await assert.rejects(launch(f,{resourceProfile:profile,timeoutMs:40}),/CODING_TIMEOUT/);assert.ok(f.args.includes('--cpu=2'));assert.ok(Date.now()-start<1000);
});
test('native termination returns bounded signal or numeric-exit code without output or inference receipt',async()=>{
 for(const [code,signal,want] of [[null,'SIGXCPU','LOCAL_RUNTIME_SIGNAL_SIGXCPU'],[null,'SIGKILL','LOCAL_RUNTIME_SIGNAL_SIGKILL'],[137,null,'LOCAL_RUNTIME_EXIT_137'],[152,null,'LOCAL_RUNTIME_EXIT_152']]){
 const f=fake({code,signal});await assert.rejects(launch(f),e=>{assert.equal(e.message,want);assert.equal(e.modelInferenceEnvelope,undefined);assert.equal(e.modelObservation,undefined);return true;});}
});

test('fixed container CLI heap is bounded independently of model weights and native address space',async()=>{
 const {LOCAL_MODEL_JOB_NODE_ARGS}=await import('./continuum-local-model-job.mjs');
 assert.ok(Object.isFrozen(LOCAL_MODEL_JOB_NODE_ARGS));assert.deepEqual(LOCAL_MODEL_JOB_NODE_ARGS,['--max-old-space-size=256','--max-semi-space-size=16']);
 // The production job owns its Node arguments; the unrelated parent test runner does not.
 const probe=spawnSync(process.execPath,[...LOCAL_MODEL_JOB_NODE_ARGS,'--input-type=module','-e',
  "import {getHeapStatistics} from 'node:v8'; process.stdout.write(JSON.stringify({args:process.execArgv,heap:getHeapStatistics().heap_size_limit}));"],
  {env:{},encoding:'utf8',timeout:5000,maxBuffer:4096});
 assert.equal(probe.status,0,probe.stderr);assert.equal(probe.error,undefined);
 const result=JSON.parse(probe.stdout);
 assert.ok(result.args.includes('--max-old-space-size=256'));
 assert.ok(result.heap<=384*1024*1024);
});

test('actual isolated synthetic ELF observes the new kernel CPU limit and unchanged native AS limit',async t=>{
 if(!boundedJobAvailable(t))return;
 for(const executable of ['/usr/bin/x86_64-linux-gnu-gcc-14','/usr/bin/prlimit','/usr/bin/bwrap']){
  if(!fs.existsSync(executable)){t.skip('HOST_RESOURCE_PROOF_NOT_RUN: required executable unavailable: '+executable);return;}
 }
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'cpu-kernel-'));t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
 const source=path.join(dir,'probe.c'),binary=path.join(dir,'probe'),model=path.join(dir,'model'),prompt=path.join(dir,'prompt');
 fs.writeFileSync(source,'#include <sys/resource.h>\n#include <stdio.h>\nint main(){struct rlimit c,a; if(getrlimit(RLIMIT_CPU,&c)||getrlimit(RLIMIT_AS,&a))return 3; printf("CPU=%llu AS=%llu\\n",(unsigned long long)c.rlim_cur,(unsigned long long)a.rlim_cur); fputs("eval time = 1.0 ms / 1 tokens\\n",stderr); return 0;}');
 const compiled=spawnSync('/usr/bin/x86_64-linux-gnu-gcc-14',[source,'-o',binary],{encoding:'utf8'});assert.equal(compiled.status,0,compiled.stderr);fs.chmodSync(binary,0o700);
 fs.writeFileSync(model,'GGUF',{mode:0o600});fs.writeFileSync(prompt,'synthetic limit probe',{mode:0o600});
 const fds=[model,binary,prompt].map(f=>fs.openSync(f,'r'));try{
 const result=await launchLocalModel({fds:fds.slice(0,2),promptFd:fds[2],timeoutMs:176123,resourceProfile:profile});
 assert.equal(result.text,'CPU=354 AS=7516192768\n');assert.equal(result.nativeCpuSeconds,354);assert.equal(result.modelObservation,undefined);assert.equal(result.verifiedModelExecution,undefined);
 }finally{for(const fd of fds)fs.closeSync(fd);}
});
