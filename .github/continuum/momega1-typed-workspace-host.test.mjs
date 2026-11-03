import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {prepareCodingTask,runCodingTask} from './coding-orchestrator.mjs';
import {createCodingCandidateProvider} from './coding-provider.mjs';
import {createIsolatedExecutor,validWorkspaceEvidence} from './isolated-executor.mjs';

test('HOST_INTEGRATION: typed dispatch binds actual canonical repair containers and refuses replay',
 {skip:process.env.CONTINUUM_WORKSPACE_HOST_PROOF!=='1',timeout:60000},async()=>{
 const home=fs.mkdtempSync(path.join(os.tmpdir(),'momega1-typed-workspace-'));
 const root=path.join(home,'repo'),stateDir=path.join(home,'state');
 fs.mkdirSync(path.join(root,'.github/continuum'),{recursive:true});fs.mkdirSync(path.join(root,'lib'));
 fs.writeFileSync(path.join(root,'lib/offset.mjs'),'export function offset(a,b){return a-b;}\n');
 fs.writeFileSync(path.join(root,'.github/continuum/offset.test.mjs'),
  "import test from 'node:test';import assert from 'node:assert/strict';import {offset} from '../../lib/offset.mjs';test('offset',()=>assert.equal(offset(3,8),5));\n");
 const git=(...args)=>{const r=spawnSync('git',args,{cwd:root,encoding:'utf8'});assert.equal(r.status,0,r.stderr);return r.stdout.trim();};
 try{
  git('init','-q','-b','fixture');git('add','.');git('-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','synthetic RED');
  const task={schema:'continuum-coding-task',version:1,id:'owner-workspace',sourceCommit:git('rev-parse','HEAD'),kind:'BUGFIX',
   goal:'Repair synthetic offset symmetry',allowedPaths:['lib/offset.mjs'],acceptance:['offset(3,8) equals 5'],chain:'node',limits:{maxModelCalls:2,maxContextChars:6000}};
  const prepared=prepareCodingTask({project:root,stateDir,task});
  const executor=createIsolatedExecutor({image:'sha256:3f44842e8a052beda7e358b9fb8d8acc3e180a7f58aaceff7e1b49ad7f3d46e1',
   nodeHome:'/opt/codex/runtimes/codex-primary-runtime/dependencies/node',memoryMb:256,pids:64,cpus:1,timeoutMs:10000});
  let calls=0;
  const run={project:root,stateDir,id:task.id,executor,executorAbortMode:'CHECKPOINT_BOUNDARY_ONLY',requireIsolation:true,
   expectedTaskFingerprint:prepared.fingerprint,env:{},
   providerFactory:({task:jobTask})=>createCodingCandidateProvider({root,task:jobTask,env:{},run:()=>{
    calls++;return {status:0,stdout:JSON.stringify({mode:'LOCAL_CODING_PATCH_PROPOSAL_V1',trusted:false,autoApply:false,
     proposal:{plan:['Synthetic software contract only'],hypothesis:'Contract fixture, no inference',
      patches:[{file:'lib/offset.mjs',oldText:'a-b',newText:'a>b?a-b:b-a'}]}})};
   }})};
  const result=await runCodingTask(run);
  assert.equal(result.status,'PATCH_PENDING_OWNER_REVIEW');assert.equal(calls,1);
  const reports=path.join(stateDir,'teamleader/reports/owner-repair');
  const report=JSON.parse(fs.readFileSync(path.join(reports,fs.readdirSync(reports)[0],'report.json'),'utf8'));
  const observations=[...report.baseline.results,...report.independent.profiles].map(r=>r.isolation);
  assert.ok(observations.length>=2);
  for(const observed of observations){
   assert.equal(validWorkspaceEvidence(observed,'node-continuum'),true,'actual profile must carry observed workspace binding and teardown');
   assert.equal(observed.workspaceBinding.taskId,prepared.ccc1TaskId);
   assert.equal(observed.workspaceBinding.sourceCommit,task.sourceCommit);
   assert.equal(observed.workspaceBinding.jobId,result.receiptJobs[0]);
   assert.equal(observed.workspaceBinding.workerId,'typed-coding-worker');
  }
  assert.notEqual(observations[0].workspaceId,observations.at(-1).workspaceId);
  assert.equal(git('status','--porcelain'),'');
  const replay=await runCodingTask({...run,providerFactory:()=>{throw Error('replay must not infer');}});
  assert.equal(replay.status,'PATCH_PENDING_OWNER_REVIEW');assert.equal(calls,1);
  const out=process.env.CONTINUUM_WORKSPACE_EVIDENCE_DIR;
  if(out){fs.mkdirSync(out,{recursive:true});fs.writeFileSync(path.join(out,'typed-workspace.json'),JSON.stringify({
   evidenceClass:'HOST_INTEGRATION',proposalSource:'SCRIPTED_CONTRACT_FIXTURE',actualModelInference:false,
   sourceCommit:task.sourceCommit,result,observations},null,2)+'\n');}
 }finally{fs.rmSync(home,{recursive:true,force:true});}
});
