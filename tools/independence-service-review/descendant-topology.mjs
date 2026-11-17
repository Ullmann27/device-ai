/** Supplemental hazard proof: a harmless sleeping C program, never a model.
 * Uses the real launcher's bwrap/prlimit arguments and a transparent spawn
 * observer. The observation is CONTRACT_TEST, never neural execution evidence. */
import fs from 'node:fs';import os from 'node:os';import path from 'node:path';
import assert from 'node:assert/strict';import {spawn,spawnSync} from 'node:child_process';
import {pathToFileURL} from 'node:url';
import {candidateRoot,localEnvironment,until,delay,record} from './process-fixture.mjs';

const top=fs.mkdtempSync(path.join(os.tmpdir(),'service-descendant-proof-'));
const marker=path.join(top,'observed.json'),started=path.join(top,'payload-started');
const runtime=path.join(top,'harmless-runtime'),model=path.join(top,'not-a-model'),prompt=path.join(top,'prompt');
const wrapper=path.join(top,'wrapper.mjs'),outer=path.join(top,'outer.mjs');
const identities=new Map();
function identity(pid){try{const text=fs.readFileSync('/proc/'+pid+'/stat','utf8');const f=text.slice(text.lastIndexOf(')')+2).trim().split(/\s+/);return f[0]==='Z'?null:{pid,startTicks:f[19]};}catch(e){if(e.code==='ENOENT')return null;throw e;}}
const live=old=>{const now=old&&identity(old.pid);return now&&now.startTicks===old.startTicks;};
const stop=old=>{if(live(old))process.kill(old.pid,'SIGKILL');};
let worker;
try{
 fs.writeFileSync(path.join(top,'payload.c'),'#include <stdio.h>\n#include <unistd.h>\nint main(void){puts("HARMLESS_PAYLOAD_STARTED");fflush(stdout);sleep(12);return 0;}\n');
 const compile=spawnSync('/usr/bin/gcc',[path.join(top,'payload.c'),'-O0','-o',runtime],{encoding:'utf8',timeout:10000,env:localEnvironment()});
 assert.equal(compile.status,0,compile.stderr);fs.writeFileSync(model,'fixture bytes; not GGUF');fs.writeFileSync(prompt,'no inference');
 const source=pathToFileURL(path.join(candidateRoot,'scripts/continuum-local-model-observation.mjs')).href;
 fs.writeFileSync(wrapper,`import fs from 'node:fs';import {spawn} from 'node:child_process';import {launchLocalModel} from ${JSON.stringify(source)};
const fds=[${JSON.stringify(model)},${JSON.stringify(runtime)}].map(f=>fs.openSync(f,'r'));const promptFd=fs.openSync(${JSON.stringify(prompt)},'r');
try{await launchLocalModel({fds,promptFd,timeoutMs:10000,spawnImpl:(...args)=>{const child=spawn(...args);fs.writeFileSync(${JSON.stringify(marker)},JSON.stringify({wrapper:process.pid,sandbox:child.pid}));child.stdout.on('data',data=>{if(data.toString().includes('HARMLESS_PAYLOAD_STARTED'))fs.writeFileSync(${JSON.stringify(started)},'actual payload stdout');});return child;}});}catch{}finally{for(const fd of [...fds,promptFd])fs.closeSync(fd);}
`);
 fs.writeFileSync(outer,`import {spawnSync} from 'node:child_process';spawnSync(process.execPath,[${JSON.stringify(wrapper)}],{stdio:'ignore',timeout:15000});\n`);
 worker=spawn(process.execPath,[outer],{env:localEnvironment(),stdio:'ignore'});
 await until(()=>fs.existsSync(marker)&&fs.existsSync(started),Boolean,5000);
 const pids=JSON.parse(fs.readFileSync(marker));
 const outerIdentity=identity(worker.pid),wrapperIdentity=identity(pids.wrapper),sandboxIdentity=identity(pids.sandbox);
 for(const i of [outerIdentity,wrapperIdentity,sandboxIdentity]){assert.ok(i);identities.set(i.pid,i);}
 stop(outerIdentity);await until(()=>live(outerIdentity),v=>!v);await delay(100);
 const hazard={workerDead:!live(outerIdentity),intermediateCliStillAlive:Boolean(live(wrapperIdentity)),sandboxStillAlive:Boolean(live(sandboxIdentity))};
 assert.deepEqual(hazard,{workerDead:true,intermediateCliStillAlive:true,sandboxStillAlive:true});
 stop(wrapperIdentity);await until(()=>live(wrapperIdentity),v=>!v);await until(()=>live(sandboxIdentity),v=>!v);
 const result={schema:'continuum-descendant-hazard-proof',version:1,sourceRoot:candidateRoot,
  evidenceClass:'CONTRACT_TEST_PROCESS_TOPOLOGY',modelCalls:0,scriptedModelResponse:false,
  actualLauncher:true,harmlessPayload:'C sleep, no model weights or inference',
  ...hazard,sandboxDiesAfterImmediateCliParentKilled:true,
  conclusion:'--die-with-parent binds sandbox to intermediate CLI, not service worker'};
 record({kind:'DESCENDANT_HAZARD_OBSERVED',...result});console.log(JSON.stringify(result));
}finally{
 for(const value of [...identities.values()].reverse())stop(value);
 if(worker&&identity(worker.pid))worker.kill('SIGKILL');
 fs.rmSync(top,{recursive:true,force:true});
}
