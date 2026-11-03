import test from 'node:test';
import assert from 'node:assert/strict';
import {ALPHA_SQUAD_ROLES,alphaPlan,alphaRunOne} from './alpha-squad-ccc1.mjs';

const sha='a'.repeat(40);
const args={project:'/isolated/git',stateDir:'/safe/outside-git',chain:'node'};
const preflight=()=>({result:'READY_TO_REQUEST_OWNER_REVIEW',sourceCommit:sha});
const openTask={taskId:'t-'+('b'.repeat(16)),terminal:false,repair:'node'};
const runResult=async()=>({outcome:'VERIFIED_PATCH_PENDING_REVIEW'});
const deps={preflight,tasks:()=>[openTask],run:runResult};

test('four roles are descriptive checkpoints, not a claim of running model instances',()=>{
 assert.deepEqual(ALPHA_SQUAD_ROLES.map(r=>r.stage),['PLAN','RED','PATCH','VERIFY']);
 const result=alphaPlan(args,{compile:()=>({status:'READY',preflight:{
  sourceCommit:sha,findings:[]}})});
 assert.equal(result.mode,'PLAN_ONLY');
 assert.equal(result.state,'READY_FOR_OWNER_SUBMISSION');
 assert.equal(result.physicalCodingSandboxesStarted,0);
 assert.equal(result.modelCalls,0);
});
test('plan is blocked on failed canonical preflight, never asserts success',()=>{
 const result=alphaPlan(args,{compile:()=>({status:'BLOCKED',preflight:{
  sourceCommit:sha,findings:['DIRTY_SOURCE']}})});
 assert.equal(result.state,'BLOCKED');
 assert.deepEqual(result.findings,['DIRTY_SOURCE']);
});
test('run cannot begin without explicit operator action',async()=>{
 let invoked=0;
 const result=await alphaRunOne({...args,expectedSourceCommit:sha},
  {...deps,run:async()=>{invoked++;return runResult();}});
 assert.equal(result.status,'OWNER_CONFIRMATION_REQUIRED');
 assert.equal(invoked,0);
});
test('run rejects stale checkout and invalid expected head',async()=>{
 let invoked=0;
 const guarded={...deps,run:async()=>{invoked++;return runResult();}};
 const stale=await alphaRunOne({...args,ownerConfirmed:true,expectedSourceCommit:'c'.repeat(40)},guarded);
 assert.equal(stale.status,'PREFLIGHT_BLOCKED');
 assert.equal(invoked,0);
 await assert.rejects(alphaRunOne({...args,ownerConfirmed:true,expectedSourceCommit:'main'},guarded),
  /ALPHA_EXPECTED_SOURCE_REQUIRED/);
});
test('run refuses to choose among ambiguous owner tasks or mismatched chain',async()=>{
 let invoked=0;
 const noExec={...deps,run:async()=>{invoked++;return runResult();}};
 for(const [active,status] of [
  [[], 'NO_OWNER_TASK'],
  [[openTask, {...openTask,taskId:'other'}], 'MULTIPLE_OPEN_TASKS'],
  [[{...openTask,repair:'android'}],'TASK_CHAIN_MISMATCH']
 ]){
  const r=await alphaRunOne({...args,ownerConfirmed:true,expectedSourceCommit:sha},
   {...noExec,tasks:()=>active});
  assert.equal(r.status,status);
 }
 assert.equal(invoked,0);
});
test('one existing owner-approved task invokes canonical CCC1 cycle exactly once',async()=>{
 let invoked=0;
 let opts;
 const result=await alphaRunOne({...args,ownerConfirmed:true,expectedSourceCommit:sha},
  {...deps,run:async x=>{invoked++;opts=x;return runResult();}});
 assert.equal(invoked,1);
 assert.equal(opts.project,args.project);
 assert.equal(opts.stateDir,args.stateDir);
 assert.equal(result.status,'PATCH_PENDING_OWNER_REVIEW');
 assert.equal(result.workerCycles,1);
 assert.equal(result.patchMerged,false);
 assert.equal(result.deviceVerified,false);
});
test('an interrupted or unknown outcome is never promoted to a verified patch',async()=>{
 for(const output of [undefined,{}, {outcome:'INTERRUPTED'}, {outcome:'ERROR'}]){
  const result=await alphaRunOne({...args,ownerConfirmed:true,expectedSourceCommit:sha},
   {...deps,run:async()=>output});
  assert.equal(result.status,'CYCLE_FINISHED_NOT_VERIFIED');
  assert.equal(result.apkBuilt,false);
 }
});
test('invalid chains are never used to dispatch work',async()=>{
 await assert.rejects(alphaRunOne({...args,chain:'shell',ownerConfirmed:true,expectedSourceCommit:sha},deps),
  /ALPHA_INVALID_INPUT/);
});
