/** Native regression for durable canonical coding-model outcome evidence.
 * Synthetic git fixtures; no inference, network, Android or patch execution.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {createCodingCandidateProvider} from './coding-provider.mjs';

function fixture(t) {
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-outcome-source-'));
 const evidenceDir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-outcome-evidence-'));
 t.after(()=>{fs.rmSync(root,{recursive:true,force:true});fs.rmSync(evidenceDir,{recursive:true,force:true});});
 fs.mkdirSync(path.join(root,'lib'));
 fs.writeFileSync(path.join(root,'lib','math.mjs'),'export const adjust=(a,b)=>a-b;\n');
 const git=(...args)=>{
  const r=spawnSync('git',args,{cwd:root,encoding:'utf8',timeout:5000});
  assert.equal(r.status,0,r.stderr);return r.stdout.trim();
 };
 git('init','-q');git('add','.');
 git('-c','user.name=continuum-test','-c','user.email=continuum@example.invalid','commit','-qm','fixture');
 const task={schema:'continuum-coding-task',version:1,id:'outcome-test',sourceCommit:git('rev-parse','HEAD'),kind:'BUGFIX',
  goal:'Correct the signed adjustment',allowedPaths:['lib/math.mjs'],
  acceptance:['Preserve safe arithmetic'],chain:'node',limits:{maxModelCalls:1,maxContextChars:6000}};
 return {root,evidenceDir,task};
}

function invoke(t,child) {
 const f=fixture(t);let calls=0;
 const opts={root:f.root,task:f.task,evidenceDir:f.evidenceDir,
  env:{CONTINUUM_LOCAL_BRAIN_API_KEY:'a'.repeat(40)},
  run:()=>{calls++;if(child instanceof Error)throw child;return child;}};
 const call=()=>createCodingCandidateProvider(opts)({base:f.task.sourceCommit,chain:'node',deadline:Date.now()+10000});
 return {call,trace:()=>JSON.parse(fs.readFileSync(path.join(f.evidenceDir,'request-1.json'),'utf8')),calls:()=>calls};
}

test('timeout is a durable FAILED/TIMEOUT, never a successful model response or a free retry',t=>{
 const p=invoke(t,{status:null,signal:'SIGTERM',error:{code:'ETIMEDOUT'},stdout:'',stderr:'SECRET_STDERR_SENTINEL'});
 assert.throws(p.call,/CODING_MODEL_UNAVAILABLE/);
 const trace=p.trace();
 assert.equal(trace.status,'FAILED');assert.equal(trace.failureKind,'TIMEOUT');
 assert.equal(trace.exitCode,null);assert.equal(trace.rawContentStored,false);
 assert.equal(JSON.stringify(trace).includes('SECRET_STDERR_SENTINEL'),false);
 assert.throws(p.call,/CODING_MODEL_BUDGET_EXHAUSTED/);assert.equal(p.calls(),1);
});

test('typed no-op rejection is recorded, not a repair success',t=>{
 const p=invoke(t,{status:1,stdout:'',stderr:'LOCAL_MODEL_UNAVAILABLE_OR_INVALID: CODING_NOOP_PATCH:'+('a'.repeat(64))});
 assert.throws(p.call,/CODING_NOOP_PATCH/);
 assert.equal(p.trace().status,'FAILED');assert.equal(p.trace().failureKind,'NOOP_PATCH');
 assert.throws(p.call,/CODING_MODEL_BUDGET_EXHAUSTED/);assert.equal(p.calls(),1);
});

test('valid exit but broken JSON is INVALID_OUTPUT, not RESPONDED',t=>{
 const p=invoke(t,{status:0,stdout:'not-json',stderr:''});
 assert.throws(p.call,/CODING_REPLY_INVALID/);
 assert.equal(p.trace().status,'INVALID_OUTPUT');assert.equal(p.trace().failureKind,'INVALID_JSON');
 assert.throws(p.call,/CODING_MODEL_BUDGET_EXHAUSTED/);assert.equal(p.calls(),1);
});

test('validated model proposal is RESPONDED, never claimed verified repair',t=>{
 const p=invoke(t,{status:0,stdout:JSON.stringify({mode:'LOCAL_CODING_PATCH_PROPOSAL_V1',
  trusted:false,autoApply:false,proposal:{plan:['Check sign'],hypothesis:'Incorrect subtraction',
   patches:[{file:'lib/math.mjs',oldText:'a-b',newText:'a+b'}]}})});
 const result=p.call();
 assert.equal(result.trusted,false);assert.equal(result.autoApply,false);
 assert.equal(p.trace().status,'RESPONDED');
 assert.equal(p.trace().proposalAccepted,true);
 assert.equal(p.trace().verifiedModelExecution,false);
 assert.throws(p.call,/CODING_MODEL_BUDGET_EXHAUSTED/);assert.equal(p.calls(),1);
});

test('abrupt child exception retains RESERVED as UNKNOWN; no unearned second call',t=>{
 const p=invoke(t,new Error('SIMULATED_PROCESS_LOSS'));
 assert.throws(p.call,/SIMULATED_PROCESS_LOSS/);
 assert.equal(p.trace().status,'RESERVED');
 assert.throws(p.call,/CODING_MODEL_BUDGET_EXHAUSTED/);assert.equal(p.calls(),1);
});
