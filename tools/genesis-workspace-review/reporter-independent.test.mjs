// Independent reporter tests: real canonical task/grant/journal, synthetic profile
// reader, no inference. The two race hooks modify actual fixture files.
import test from 'node:test';import assert from 'node:assert/strict';
import fs from 'node:fs';import os from 'node:os';import path from 'node:path';
import {spawnSync} from 'node:child_process';import {pathToFileURL} from 'node:url';
import {createHash} from 'node:crypto';
const root=process.env.GENESIS_REVIEW_TARGET;assert.ok(root);
const mod=name=>import(pathToFileURL(path.join(root,'.github/continuum',name)));
const {prepareCodingTask}=await mod('coding-orchestrator.mjs');
const {createCcc1BotAssignment}=await mod('ccc1-bot-assignment.mjs');
const {writeCcc1BotReport}=await mod('ccc1-bot-report.mjs');
const {DurableTaskJournal,runtimeKey}=await mod('ccc1-journal.mjs');
function fixture(t){
 const home=fs.mkdtempSync(path.join(os.tmpdir(),'independent-echo-'));t.after(()=>fs.rmSync(home,{recursive:true,force:true}));
 const project=path.join(home,'repo'),stateDir=path.join(home,'state');fs.mkdirSync(project);
 fs.mkdirSync(path.join(project,'lib'));
 const source=path.join(project,'lib/example.mjs');fs.writeFileSync(source,'export const value=1;\n');
 const git=(...args)=>{const r=spawnSync('git',args,{cwd:project,encoding:'utf8'});assert.equal(r.status,0,r.stderr);return r.stdout.trim();};
 git('init','-q');git('add','.');git('-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','baseline');
 const task={schema:'continuum-coding-task',version:1,id:'review-report',sourceCommit:git('rev-parse','HEAD'),kind:'BUGFIX',
  goal:'Synthetic reporter contract',allowedPaths:['lib/example.mjs'],acceptance:['A bounded report'],chain:'node',limits:{maxModelCalls:1,maxContextChars:2048}};
 const prepared=prepareCodingTask({project,stateDir,task});
 const profiles=new Map(['bot-forge','bot-lint','bot-echo'].map(agentId=>[agentId,{agentId,workspaceId:'workspace-'+agentId,
  resourceBudget:{maxModelCalls:1,deadlineMillis:120000}}]));
 const assignment=createCcc1BotAssignment({task,ccc1TaskId:prepared.ccc1TaskId,builderId:'bot-forge',verifierId:'bot-lint',reporterId:'bot-echo',profileStore:{get:id=>profiles.get(id)}});
 const grantFile=path.join(stateDir,'coding-grants',task.id+'.json');
 const read=()=>JSON.parse(fs.readFileSync(grantFile,'utf8'));
 const journal=DurableTaskJournal.forTask(stateDir,prepared.ccc1TaskId,runtimeKey(stateDir));
 const report=(reader=read)=>writeCcc1BotReport({assignment,task,ccc1TaskId:prepared.ccc1TaskId,project,stateDir,readPreparedGrant:reader});
 return {task,prepared,profiles,assignment,grantFile,read,journal,report,project,stateDir,source};
}
test('pending deterministic report hashes actual bytes, repeats and never mutates journal',t=>{
 const f=fixture(t),before=JSON.stringify(f.journal.entries()),a=f.report(),b=f.report();
 assert.equal(a.sha256,b.sha256);assert.equal(a.status,'DRAFT');assert.equal(a.modelCalls,0);
 const bytes=fs.readFileSync(a.path),body=JSON.parse(bytes);
 assert.equal(createHash('sha256').update(bytes).digest('hex'),a.sha256);
 assert.equal(body.interpretation,'PENDING_OR_UNKNOWN');assert.equal(body.independentVerifier,false);
 assert.equal(body.modelInferenceAttestedByReport,false);assert.equal(body.deviceVerified,false);
 assert.equal(body.receipts.length,0);assert.equal(JSON.stringify(f.journal.entries()),before);
});
test('canonical grant substitution during reporting is rechecked before returning',t=>{
 const f=fixture(t);let reads=0;
 const reader=()=>{if(++reads===2){const g=f.read();g.ccc1TaskId='t-'+'0'.repeat(16);fs.writeFileSync(f.grantFile,JSON.stringify(g));}return f.read();};
 assert.throws(()=>f.report(reader));
});
test('uncommitted source mutation during reporting is rechecked before returning',t=>{
 const f=fixture(t);let reads=0;
 const reader=()=>{if(++reads===2)fs.appendFileSync(f.source,'// concurrent edit\n');return f.read();};
 assert.throws(()=>f.report(reader));
});
test('initial dirty source is rejected',t=>{
 const f=fixture(t);fs.appendFileSync(f.source,'// dirty\n');assert.throws(()=>f.report(),/SOURCE_CHANGED/);
});
test('reporter removal invalidates report without changing canonical state',t=>{
 const f=fixture(t),before=JSON.stringify(f.journal.entries());f.profiles.delete('bot-echo');
 assert.throws(()=>f.report(),/PROFILE_UNAVAILABLE/);assert.equal(JSON.stringify(f.journal.entries()),before);
});
test('assignment expiry cannot produce a report',t=>{
 const f=fixture(t),now=Date.now;
 try{Date.now=()=>f.assignment.expiresAt;assert.throws(()=>f.report(),/EXPIRED/);}finally{Date.now=now;}
});
test('tampered report cannot be accepted by deterministic regeneration',t=>{
 const f=fixture(t),a=f.report();fs.appendFileSync(a.path,'tampered');assert.throws(()=>f.report(),/ARTIFACT_CHANGED/);
});
test('symlink evidence directory never receives report bytes',t=>{
 const f=fixture(t),foreign=path.join(path.dirname(f.stateDir),'foreign');fs.mkdirSync(foreign);
 fs.symlinkSync(foreign,path.join(f.stateDir,'coding-evidence'),'dir');
 assert.throws(()=>f.report(),/PATH_UNSAFE/);assert.deepEqual(fs.readdirSync(foreign),[]);
});
