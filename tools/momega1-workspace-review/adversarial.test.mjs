import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';import os from 'node:os';import path from 'node:path';import {pathToFileURL} from 'node:url';
const target=process.env.WORKSPACE_REVIEW_TARGET;
if(!target)throw Error('WORKSPACE_REVIEW_TARGET required');
const module=async name=>import(pathToFileURL(path.join(target,'.github/continuum',name)));
const {createIsolatedExecutor,validWorkspaceEvidence}=await module('isolated-executor.mjs');
const {bindCcc1Workspace}=await module('ccc1-workspace.mjs');
const {workspaceFixture}=await module('ccc1-workspace-fixture.mjs');
const {ownerAction}=await module('ccc1-orchestrator.mjs');
const {BacklogStore}=await module('development-backlog.mjs');
const {jobTaskId}=await import(pathToFileURL(path.join(target,'scripts/continuum-mobile-coding.mjs')));
const config={image:'sha256:3f44842e8a052beda7e358b9fb8d8acc3e180a7f58aaceff7e1b49ad7f3d46e1',nodeHome:'/opt/codex/runtimes/codex-primary-runtime/dependencies/node',memoryMb:256,pids:64,cpus:1,timeoutMs:5000};
const source="import test from 'node:test';import assert from 'node:assert/strict';test('fixed harmless synthetic profile',()=>assert.equal(2+2,4));";
function fixture(run){const home=fs.mkdtempSync(path.join(os.tmpdir(),'independent-workspace-'));try{const f=workspaceFixture(home,'worker-review',{testSource:source}),out=path.join(home,'evidence');const input={executor:createIsolatedExecutor(config),project:f.root,stateDir:f.stateDir,taskId:f.taskId,job:f.job,workerId:f.workerId};return run(f,bindCcc1Workspace(input),out,input);}finally{fs.rmSync(home,{recursive:true,force:true});}}
function evidence(label,result){console.log(JSON.stringify({label,status:result.status,isolation:result.isolation}));assert.equal(result.status,0);assert.equal(validWorkspaceEvidence(result.isolation,'node-continuum'),true);}
test('owner ABORT after binding must prevent a real Docker launch',()=>fixture((f,scoped,out,input)=>{
 ownerAction({stateDir:f.stateDir,taskId:f.taskId,kind:'ABORT',payload:{code:'OWNER',reason:'independent revocation probe'}});
 assert.throws(()=>bindCcc1Workspace(input),/WORKSPACE_REQUEST_MISMATCH/);
 let result;assert.throws(()=>{result=scoped.execute('node-continuum',f.root,out);evidence('ABORTED_TASK_EXECUTED',result);},/WORKSPACE_|ABORT|REVOKED/);
}));
test('canonical claimed job after binding must prevent replay in real Docker',()=>fixture((f,scoped,out,input)=>{
 const store=new BacklogStore(path.join(f.stateDir,'teamleader/development-backlog.json'),{actor:'independent-review'}),id=jobTaskId(f.job),at=Date.now();
 store.apply([{op:'propose',actor:'independent-review',at,task:{id,title:'review interrupted task',kind:'TEST',source:'cli'}},{op:'accept',actor:'independent-review',at:at+1,id},{op:'claim',actor:'independent-review',at:at+2,id,owner:f.workerId,token:'a'.repeat(32),leaseMs:60000}]);
 assert.throws(()=>bindCcc1Workspace(input),/REQUIRE_OWNER_APPROVAL/);
 assert.throws(()=>evidence('CLAIMED_TASK_EXECUTED',scoped.execute('node-continuum',f.root,out)),/WORKSPACE_|APPROVAL|REPLAY/);
}));
test('bound request cannot execute twice without fresh canonical admission',()=>fixture((f,scoped,out)=>{
 evidence('FIRST_EXECUTION',scoped.execute('node-continuum',f.root,out));
 assert.throws(()=>evidence('REPEATED_EXECUTION',scoped.execute('node-continuum',f.root,out)),/WORKSPACE_|REPLAY|ALREADY/);
}));
test('corrupted MAC journal after binding must prevent execution',()=>fixture((f,scoped,out,input)=>{
 const raw=fs.readFileSync(f.journal.file,'utf8');fs.writeFileSync(f.journal.file,raw.replace('synthetic isolation','tampered isolation'));
 assert.throws(()=>bindCcc1Workspace(input),/modified/);
 assert.throws(()=>evidence('CORRUPT_JOURNAL_EXECUTED',scoped.execute('node-continuum',f.root,out)),/WORKSPACE_|modified|CORRUPT/);
}));
