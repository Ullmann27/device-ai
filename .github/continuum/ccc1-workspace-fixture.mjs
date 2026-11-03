import fs from 'node:fs';import path from 'node:path';
import assert from 'node:assert/strict';import {spawnSync} from 'node:child_process';
import {startTask} from './ccc1-orchestrator.mjs';
import {DurableTaskJournal,runtimeKey} from './ccc1-journal.mjs';
import {envelope} from './ccc1-envelope.mjs';
import {jobIdFor} from './ccc1-recovery.mjs';
export function workspaceFixture(home,worker){
 const root=path.join(home,worker),stateDir=path.join(home,worker+'-state');
 fs.mkdirSync(path.join(root,'.github/continuum'),{recursive:true});
 fs.writeFileSync(path.join(root,worker+'.txt'),worker);
 const git=(...args)=>{const r=spawnSync('git',args,{cwd:root,encoding:'utf8'});assert.equal(r.status,0,r.stderr);return r.stdout.trim();};
 git('init','-q','-b','workspace-fixture');git('add','.');
 git('-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','workspace fixture');
 const taskId=startTask({project:root,stateDir,repoFullName:'local/workspace-fixture',goal:{text:'synthetic isolation test',repair:{chain:'node'}},permissions:['node-repair:node']});
 const journal=DurableTaskJournal.forTask(stateDir,taskId,runtimeKey(stateDir)),goal=journal.entries()[0];
 const hypothesis=journal.append(envelope({binding:goal,sender:'REASONER',kind:'HYPOTHESIS',payload:{claim:'synthetic test',repair:{chain:'node'},basis:'fixture'},sequence:1})).entry;
 const request=journal.append(envelope({binding:goal,sender:'REASONER',kind:'TEST_REQUEST',payload:{hypothesis:hypothesis.messageId,repair:{chain:'node'},budgetMinutes:1},sequence:2})).entry;
 return {root,stateDir,taskId,journal,request,job:jobIdFor(taskId,request.messageId),workerId:worker,git};
}

