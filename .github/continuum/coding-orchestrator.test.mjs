import test from 'node:test';import assert from 'node:assert/strict';import fs from 'node:fs';import os from 'node:os';import path from 'node:path';import {spawnSync} from 'node:child_process';
import {prepareCodingTask,runCodingTask,readCodingTaskStatus} from './coding-orchestrator.mjs';
import {createCodingCandidateProvider} from './coding-provider.mjs';

test('typed task reaches CCC1, canonical teamleader, repair and fresh verifier without modifying owner source',{skip:!!process.env.CONTINUUM_REPAIR_DEPTH},async()=>{
 const temp=fs.mkdtempSync(path.join(os.tmpdir(),'coding-ccc1-')),root=path.join(temp,'repo'),state=path.join(temp,'state');
 fs.mkdirSync(path.join(root,'.github/continuum'),{recursive:true});fs.mkdirSync(path.join(root,'lib'));
 fs.writeFileSync(path.join(root,'lib/offset.mjs'),'export function offset(a,b){return a-b;}\n');
 fs.writeFileSync(path.join(root,'.github/continuum/offset.test.mjs'),"import test from 'node:test';import assert from 'node:assert/strict';import {offset} from '../../lib/offset.mjs';test('offset',()=>assert.equal(offset(3,8),5));\n");
 const git=(...args)=>{const r=spawnSync('git',args,{cwd:root,encoding:'utf8'});assert.equal(r.status,0,r.stderr);return r.stdout.trim();};
 try{
  git('init','-q','-b','coding-fixture');git('add','.');git('-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','baseline');
  const spec={schema:'continuum-coding-task',version:1,id:'owner-offset',sourceCommit:git('rev-parse','HEAD'),kind:'BUGFIX',goal:'Repair offset symmetry',allowedPaths:['lib/offset.mjs'],acceptance:['offset(3,8) must be 5'],chain:'node',limits:{maxModelCalls:2,maxContextChars:6000}};
  const prepared=prepareCodingTask({project:root,stateDir:state,task:spec});
  const status=readCodingTaskStatus({project:root,stateDir:state,id:spec.id,expectedTaskFingerprint:prepared.fingerprint});
  assert.equal(status.phase,'TASK_ACCEPTED');assert.equal(status.observationOnly,true);
  assert.throws(()=>readCodingTaskStatus({project:root,stateDir:state,id:spec.id,expectedTaskFingerprint:'f'.repeat(64)}),/BINDING_MISMATCH/);
  assert.equal(prepareCodingTask({project:root,stateDir:state,task:spec}).ccc1TaskId,prepared.ccc1TaskId);
  assert.throws(()=>prepareCodingTask({project:root,stateDir:state,task:{...spec,goal:'Another goal'}}),/REUSED/);
  let calls=0;
  const result=await runCodingTask({project:root,stateDir:state,id:spec.id,providerFactory:({task})=>createCodingCandidateProvider({root,task,
   env:{CONTINUUM_LOCAL_BRAIN_API_KEY:'a'.repeat(40)},run:()=>{calls++;return {status:0,stdout:JSON.stringify({mode:'LOCAL_CODING_PATCH_PROPOSAL_V1',trusted:false,autoApply:false,
    proposal:{plan:['Fix symmetry'],hypothesis:'Negative ordering is incorrect',patches:[{file:'lib/offset.mjs',oldText:'a-b',newText:'a>b?a-b:b-a'}]}})};}})});
  assert.equal(result.status,'PATCH_PENDING_OWNER_REVIEW');assert.equal(calls,1);assert.equal(result.approvedForMerge,false);
  assert.equal(git('status','--porcelain'),'');
  const observed=readCodingTaskStatus({project:root,stateDir:state,id:spec.id,expectedTaskFingerprint:prepared.fingerprint});
  assert.equal(observed.status,'PATCH_PENDING_OWNER_REVIEW');assert.equal(observed.phase,'INDEPENDENT_VERIFIED');
  assert.ok(observed.progress.some(p=>p.phase==='MODEL_WORKING'));
  assert.ok(observed.progress.some(p=>p.phase==='PLAN_CREATED'));
  assert.equal(calls,1);assert.equal(observed.approvedForMerge,false);
  const replay=await runCodingTask({project:root,stateDir:state,id:spec.id,providerFactory:()=>{throw Error('must not infer again');}});
  assert.equal(replay.status,'PATCH_PENDING_OWNER_REVIEW');assert.equal(calls,1);
 }finally{fs.rmSync(temp,{recursive:true,force:true});}
});
