import crypto from 'node:crypto';
import {BacklogStore} from './development-backlog.mjs';
import {jobTaskId} from '../../scripts/continuum-mobile-coding.mjs';
import fs from 'node:fs';import path from 'node:path';
import assert from 'node:assert/strict';import {spawnSync} from 'node:child_process';
import {startTask} from './ccc1-orchestrator.mjs';
import {DurableTaskJournal,runtimeKey} from './ccc1-journal.mjs';
import {envelope} from './ccc1-envelope.mjs';
import {jobIdFor} from './ccc1-recovery.mjs';
export function workspaceFixture(home,worker,{testSource=null}={}){
 const root=path.join(home,worker),stateDir=path.join(home,worker+'-state');
 fs.mkdirSync(path.join(root,'.github/continuum'),{recursive:true});
 fs.writeFileSync(path.join(root,worker+'.txt'),worker);
 if(testSource!==null)fs.writeFileSync(path.join(root,'.github/continuum/isolation.test.mjs'),testSource);
 const git=(...args)=>{const r=spawnSync('git',args,{cwd:root,encoding:'utf8'});assert.equal(r.status,0,r.stderr);return r.stdout.trim();};
 git('init','-q','-b','workspace-fixture');git('add','.');
 git('-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','workspace fixture');
 const taskId=startTask({project:root,stateDir,repoFullName:'local/workspace-fixture',goal:{text:'synthetic isolation test',repair:{chain:'node'}},permissions:['node-repair:node']});
 const journal=DurableTaskJournal.forTask(stateDir,taskId,runtimeKey(stateDir)),goal=journal.entries()[0];
 const hypothesis=journal.append(envelope({binding:goal,sender:'REASONER',kind:'HYPOTHESIS',payload:{claim:'synthetic test',repair:{chain:'node'},basis:'fixture'},sequence:1})).entry;
 const request=journal.append(envelope({binding:goal,sender:'REASONER',kind:'TEST_REQUEST',payload:{hypothesis:hypothesis.messageId,repair:{chain:'node'},budgetMinutes:1},sequence:2})).entry;
 return {root,stateDir,taskId,journal,request,job:jobIdFor(taskId,request.messageId),workerId:worker,git};
}


/** Test-only use of the existing owner backlog claim. Never serialize its token. */
export function claimWorkspaceFixture(f,{leaseMs=60000}={}){
 const backlog=path.join(f.stateDir,'teamleader/development-backlog.json');
 const journal=DurableTaskJournal.forTask(f.stateDir,f.taskId,runtimeKey(f.stateDir));
 const goal=journal.entries()[0],id=jobTaskId(f.job),token=crypto.randomBytes(16).toString('hex'),at=Date.now();
 const store=new BacklogStore(backlog,{actor:'workspace-fixture'});
 const result=store.apply([
  {op:'propose',actor:'workspace-fixture',at,task:{id,title:'synthetic workspace profile',kind:'REPAIR',source:'owner',subject:{base:goal.baseCommitSha,chain:goal.payload.repair.chain}}},
  {op:'accept',actor:'workspace-fixture',at,id},
  {op:'claim',actor:'workspace-fixture',at,id,owner:'workspace-fixture',token,leaseMs}
 ]);
 assert.ok(result.results.every(r=>r.ok===true));
 return {backlog,id,token};
}
