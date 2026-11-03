// Independent software probes, disposable repositories only. No model/device claim.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {pathToFileURL} from 'node:url';
const root=path.resolve(process.env.GENESIS_REVIEW_TARGET??new URL('../..',import.meta.url).pathname);
const load=name=>import(pathToFileURL(path.join(root,'.github/continuum',name)).href);
const {workspaceFixture,claimWorkspaceFixture}=await load('ccc1-workspace-fixture.mjs');
const {validWorkspaceBinding}=await load('isolated-executor.mjs');
const {bindCcc1Workspace}=await load('ccc1-workspace.mjs');
const {recover}=await load('ccc1-recovery.mjs');
const {prepareCodingTask}=await load('coding-orchestrator.mjs');
const {recordTechnicalExperience,recallTechnicalExperiences}=await load('ccc1-technical-recall.mjs');
const {runtimeKey}=await load('ccc1-journal.mjs');
function home(t){const dir=fs.mkdtempSync(path.join(os.tmpdir(),'genesis-review-'));t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));return dir;}
function task(source){return {schema:'continuum-coding-task',version:1,id:'review-new-task',sourceCommit:source,kind:'BUGFIX',goal:'Synthetic review only',allowedPaths:['lib/review.mjs'],acceptance:['Keep scope bounded'],chain:'node',limits:{maxModelCalls:1,maxContextChars:6000}};}

test('B01 workspace strings are correlation, not persisted identity admission',()=>{
 const binding={workerId:'not-a-registered-profile',taskId:'t-'+'a'.repeat(16),requestMessageId:'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',requestHash:'b'.repeat(64),sourceCommit:'c'.repeat(40),jobId:'dddddddd-dddd-4ddd-8ddd-dddddddddddd'};
 assert.equal(validWorkspaceBinding(binding),true,'baseline permits bounded correlation; Genesis must add actual profile resolution');
 assert.equal(validWorkspaceBinding({...binding,permission:'OWNER_APPROVED'}),false,'unknown authority fields cannot be smuggled into executor binding');
});
test('B02 lookalike executor cannot acquire canonical workspace authority',()=>{
 assert.throws(()=>bindCcc1Workspace({executor:{forWorkspace(){throw Error('must not execute');}},workerId:'bot-forge',taskId:'t-'+'a'.repeat(16)}),/WORKSPACE_ISOLATION_REQUIRED/);
});
test('B03 claimed unfinished work requires owner recovery and never becomes executable pending',t=>{
 const f=workspaceFixture(home(t),'worker-forge');claimWorkspaceFixture(f);
 const r=recover({project:f.root,stateDir:f.stateDir,journal:f.journal}).report;
 assert.equal(r.pending.status,'INTERRUPTED');assert.equal(r.allowedNext,'REQUIRE_OWNER_APPROVAL');
});
test('B04 changed checkout source blocks old authorized pending work',t=>{
 const f=workspaceFixture(home(t),'worker-forge');fs.writeFileSync(path.join(f.root,'worker-forge.txt'),'changed');f.git('add','.');f.git('-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','changed source');
 const r=recover({project:f.root,stateDir:f.stateDir,journal:f.journal}).report;
 assert.equal(r.headMatches,false);assert.equal(r.allowedNext,'ABORT_STALE_BASE');
});
test('B05 a valid prepared grant cannot turn copied model prose into admitted experience',t=>{
 const f=workspaceFixture(home(t),'worker-forge');const spec=task(f.git('rev-parse','HEAD'));const prepared=prepareCodingTask({project:f.root,stateDir:f.stateDir,task:spec});
 assert.throws(()=>recordTechnicalExperience({stateDir:f.stateDir,taskId:prepared.ccc1TaskId,task:spec,key:runtimeKey(f.stateDir),modelObservation:{verifiedModelExecution:true,evidenceClass:'LOCAL_MODEL_HOST'},verification:{verdict:'VERIFIED'},reflection:{summary:'I passed'}}),/TECHNICAL_RUNTIME_OBSERVATION_REQUIRED/);
});
test('B06 recall without a current authorized grant remains blocked for a named bot',t=>{
 const stateDir=home(t);const r=recallTechnicalExperiences({stateDir,task:task('a'.repeat(40)),repoFullName:'synthetic/review',key:Buffer.alloc(32,1)});
 assert.equal(r.status,'BLOCKED_REQUEST_NOT_AUTHORIZED');assert.deepEqual(r.items,[]);assert.equal(r.text,'');
});
