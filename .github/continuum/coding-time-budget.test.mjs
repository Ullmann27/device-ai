import test from 'node:test';import assert from 'node:assert/strict';
import fs from 'node:fs';import os from 'node:os';import path from 'node:path';import {spawnSync} from 'node:child_process';
import {createCodingCandidateProvider} from './coding-provider.mjs';
import {queryLocalCodingBrain} from '../../scripts/continuum-local-brain.mjs';
function fixture(t){
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'coding-time-source-')),evidenceDir=fs.mkdtempSync(path.join(os.tmpdir(),'coding-time-evidence-'));
 t.after(()=>{fs.rmSync(root,{recursive:true,force:true});fs.rmSync(evidenceDir,{recursive:true,force:true});});
 fs.mkdirSync(path.join(root,'lib'));fs.writeFileSync(path.join(root,'lib/value.mjs'),'export const value = 0;\n');
 const git=(...a)=>{const r=spawnSync('git',a,{cwd:root,encoding:'utf8'});assert.equal(r.status,0);return r.stdout.trim();};
 git('init','-q');git('add','.');git('-c','user.name=test','-c','user.email=test@example.invalid','commit','-qm','test');
 const task={schema:'continuum-coding-task',version:1,id:'coding-time-test',sourceCommit:git('rev-parse','HEAD'),kind:'BUGFIX',goal:'Correct value',allowedPaths:['lib/value.mjs'],acceptance:['Correct value'],chain:'node',limits:{maxModelCalls:2,maxContextChars:4000}};
 return {root,evidenceDir,task,env:{CONTINUUM_LOCAL_BRAIN_API_KEY:'a'.repeat(40)}};
}
test('explicit slow local CPU budget is bounded by the caller deadline and recorded',t=>{
 const f=fixture(t);let input,timeout;
 const p=createCodingCandidateProvider({...f,modelTimeoutMs:240000,run:(_b,_a,o)=>{
  input=JSON.parse(o.input);timeout=o.timeout;return {status:1,stderr:'LOCAL_MODEL_UNAVAILABLE_OR_INVALID: CODING_TIMEOUT',stdout:''};}});
 assert.throws(()=>p({base:f.task.sourceCommit,chain:'node',deadline:Date.now()+180000}),/CODING_MODEL_TIMEOUT/);
 assert.ok(input.timeoutMs>170000&&input.timeoutMs<=180000);assert.equal(timeout,input.timeoutMs+500);
 const trace=JSON.parse(fs.readFileSync(path.join(f.evidenceDir,'request-1.json')));
 assert.equal(trace.failureKind,'TIMEOUT');assert.equal(trace.modelTimeoutMs,input.timeoutMs);assert.equal(trace.rawContentStored,false);
});
test('invalid configured model time budgets fail before any request',t=>{
 const f=fixture(t);for(const modelTimeoutMs of [0,99,300001,Infinity,'120000'])
  assert.throws(()=>createCodingCandidateProvider({...f,modelTimeoutMs}),/CODING_MODEL_TIME_BUDGET_INVALID/);
 assert.deepEqual(fs.readdirSync(f.evidenceDir),[]);
});
test('local inference abort produces a typed timeout without leaking transport error text',async()=>{
 const opts={userPrompt:'Bounded correction',allowedTrackedPaths:['lib/value.mjs'],apiKey:'a'.repeat(40),timeoutMs:100};
 await assert.rejects(queryLocalCodingBrain({...opts,fetchImpl:(_url,o)=>new Promise((_ok,no)=>
  o.signal.addEventListener('abort',()=>no(Error('PRIVATE_TRANSPORT_SENTINEL')),{once:true}))}),/^Error: CODING_TIMEOUT$/);
});
test('long local budget does not weaken response validation or maximum deadline',async()=>{
 const opts={userPrompt:'Bounded correction',allowedTrackedPaths:['lib/value.mjs'],apiKey:'a'.repeat(40)};
 await assert.rejects(queryLocalCodingBrain({...opts,timeoutMs:240000,fetchImpl:async()=>new Response(JSON.stringify({choices:[]}))}),/CODING_RESPONSE_INVALID/);
 await assert.rejects(queryLocalCodingBrain({...opts,timeoutMs:300001}),/INVALID_CODING_BOUNDS/);
});
