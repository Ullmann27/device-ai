import test,{before,after} from 'node:test';import assert from 'node:assert/strict';
import fs from 'node:fs';import os from 'node:os';import path from 'node:path';import {spawnSync} from 'node:child_process';
import {prepareCodingTask,runCodingTask} from './coding-orchestrator.mjs';
import {createCodingCandidateProvider} from './coding-provider.mjs';
import {createCcc1BotAssignment} from './ccc1-bot-assignment.mjs';
import {writeCcc1BotReport} from './ccc1-bot-report.mjs';
import {DurableTaskJournal,runtimeKey} from './ccc1-journal.mjs';
import {envelope} from './ccc1-envelope.mjs';
import {builderStateOf} from './ccc1-recovery.mjs';
import {sha256Hex} from './development-backlog.mjs';

// Real canonical software repair with an explicitly scripted numeric candidate.
// Profile port is a test double here; separate Docker/Kotlin test uses persisted profiles.
let f,calls=0;
before(async()=>{
 const home=fs.mkdtempSync(path.join(os.tmpdir(),'genesis-report-contract-')),root=path.join(home,'repo'),stateDir=path.join(home,'state');
 fs.mkdirSync(path.join(root,'.github/continuum'),{recursive:true});fs.mkdirSync(path.join(root,'lib'));
 fs.writeFileSync(path.join(root,'lib/offset.mjs'),'export function offset(a,b){return a-b;}\n');
 fs.writeFileSync(path.join(root,'.github/continuum/offset.test.mjs'),
  "import test from 'node:test';import assert from 'node:assert/strict';import {offset} from '../../lib/offset.mjs';test('offset',()=>assert.equal(offset(3,8),5));\n");
 const git=(...args)=>{const r=spawnSync('git',args,{cwd:root,encoding:'utf8'});assert.equal(r.status,0,r.stderr);return r.stdout.trim();};
 git('init','-q','-b','fixture');git('add','.');git('-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','scripted report fixture');
 const task={schema:'continuum-coding-task',version:1,id:'report-fixture',sourceCommit:git('rev-parse','HEAD'),kind:'BUGFIX',
  goal:'PRIVATE_OWNER_CANARY — controlled report fixture',allowedPaths:['lib/offset.mjs'],acceptance:['offset(3,8) equals5'],chain:'node',limits:{maxModelCalls:1,maxContextChars:6000}};
 const prepared=prepareCodingTask({project:root,stateDir,task});
 const profiles=new Map(['bot-forge','bot-lint','bot-echo'].map(agentId=>[agentId,{agentId,workspaceId:'workspace-'+agentId,
  resourceBudget:{maxModelCalls:1,deadlineMillis:120000}}]));
 const assignment=createCcc1BotAssignment({task,ccc1TaskId:prepared.ccc1TaskId,builderId:'bot-forge',verifierId:'bot-lint',reporterId:'bot-echo',profileStore:{get:id=>profiles.get(id)}});
 const result=await runCodingTask({project:root,stateDir,id:task.id,env:{},providerFactory:({task:jobTask})=>createCodingCandidateProvider({root,task:jobTask,env:{},run:()=>{
  calls++;return {status:0,stdout:JSON.stringify({mode:'LOCAL_CODING_PATCH_PROPOSAL_V1',trusted:false,autoApply:false,proposal:{
   plan:['Scripted report fixture only'],hypothesis:'No model inference',patches:[{file:'lib/offset.mjs',oldText:'a-b',newText:'a>b?a-b:b-a'}]}})};}})});
 assert.equal(result.status,'PATCH_PENDING_OWNER_REVIEW');
 const grantFile=path.join(stateDir,'coding-grants',task.id+'.json');
 const journal=DurableTaskJournal.forTask(stateDir,prepared.ccc1TaskId,runtimeKey(stateDir));
 f={home,root,stateDir,task,prepared,assignment,profiles,result,grantFile,journal,git};
});
after(()=>{if(f)fs.rmSync(f.home,{recursive:true,force:true});});
const report=()=>writeCcc1BotReport({assignment:f.assignment,task:f.task,ccc1TaskId:f.prepared.ccc1TaskId,project:f.root,stateDir:f.stateDir,
 readPreparedGrant:()=>JSON.parse(fs.readFileSync(f.grantFile,'utf8'))});
function edited(file,mutate,check){const original=fs.readFileSync(file);try{mutate();check();}finally{fs.writeFileSync(file,original);}}

test('Echo executes deterministic canonical report without extra model call, journal mutation or owner text',()=>{
 const before=fs.readFileSync(f.journal.file),r=report(),bytes=fs.readFileSync(r.path),body=JSON.parse(bytes);
 assert.equal(r.reporterId,'bot-echo');assert.equal(r.status,'COMPLETED');assert.equal(r.modelCalls,0);assert.equal(calls,1);
 assert.ok(before.equals(fs.readFileSync(f.journal.file)));
 assert.equal(sha256Hex(bytes),r.sha256);assert.equal(body.independentVerifier,false);assert.equal(body.modelInferenceAttestedByReport,false);
 assert.equal(body.interpretation,'PATCH_PENDING_OWNER_REVIEW');assert.equal(body.receipts[0].artifactMatched,true);
 assert.equal(body.receipts[0].attempt,1);assert.ok(!bytes.toString().includes('PRIVATE_OWNER_CANARY'));
 assert.deepEqual(report(),r,'replay reads the same artifact, does not execute repair again');
});
test('reporter must still exist; rename preserves stable identity',()=>{
 const p=f.profiles.get('bot-echo');p.displayName='Renamed';assert.equal(report().reporterId,'bot-echo');
 f.profiles.delete('bot-echo');try{assert.throws(report,/PROFILE_UNAVAILABLE/);}finally{f.profiles.set('bot-echo',p);}
});
test('current prepared grant fingerprint cannot be replaced',()=>{
 edited(f.grantFile,()=>{const g=JSON.parse(fs.readFileSync(f.grantFile));g.fingerprint='0'.repeat(64);fs.writeFileSync(f.grantFile,JSON.stringify(g));},
  ()=>assert.throws(report,/GRANT_CHANGED/));
});
test('changed source is blocked before a success report',()=>{
 const file=path.join(f.root,'lib/offset.mjs');edited(file,()=>fs.appendFileSync(file,'// changed\n'),()=>assert.throws(report,/SOURCE_CHANGED/));
});
test('tampered canonical journal MAC cannot be summarized',()=>{
 edited(f.journal.file,()=>fs.writeFileSync(f.journal.file,fs.readFileSync(f.journal.file,'utf8').replace('PRIVATE_OWNER_CANARY','CHANGED_OWNER_CANARY')),
  ()=>assert.throws(report,/modified/));
});
test('owner abort cannot be promoted to confirmed success',()=>{
 edited(f.journal.file,()=>{const entries=f.journal.entries();f.journal.append(envelope({binding:entries[0],sender:'OWNER',kind:'ABORT',
  payload:{code:'OWNER',reason:'Fixture withdrawal'},sequence:entries.length}));},()=>assert.throws(report,/GRANT_REVOKED/));
});
test('missing or modified execution receipt invalidates confirmed result',()=>{
 const entry=f.journal.entries().find(e=>e.kind==='RECEIPT');const file=path.join(builderStateOf(f.stateDir),'coding-jobs',entry.payload.job+'.json');
 edited(file,()=>fs.rmSync(file),()=>assert.throws(report,/CONFIRMED_RECEIPT_INVALID/));
 edited(file,()=>{const r=JSON.parse(fs.readFileSync(file));r.status='FAILED';fs.writeFileSync(file,JSON.stringify(r));},
  ()=>assert.throws(report,/CONFIRMED_RECEIPT_INVALID|corrupt/));
});
test('modified report artifact is rejected rather than silently overwritten',()=>{
 const r=report();edited(r.path,()=>fs.writeFileSync(r.path,'{}'),()=>assert.throws(report,/ARTIFACT_CHANGED/));
});
test('assignment expiry blocks reporter with no additional inference',()=>{
 const old=Date.now;Date.now=()=>f.assignment.expiresAt;try{assert.throws(report,/ASSIGNMENT_EXPIRED/);}finally{Date.now=old;}
 assert.equal(calls,1);
});
