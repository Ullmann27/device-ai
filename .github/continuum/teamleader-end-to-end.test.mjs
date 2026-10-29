import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {runTeamleaderCycle,localTeamleaderDriver,decodeLeaderState}
  from './autonomous-teamleader.mjs';
import {verifyRepairHandoffs} from './teamleader-handoffs.mjs';
import {BacklogStore} from './development-backlog.mjs';
import {analyzeRepository} from './code-analyzer.mjs';
import {syncBacklogOps} from './development-planner.mjs';
import {loadLedger} from './repair-ledger.mjs';

const nested=Boolean(process.env.CONTINUUM_REPAIR_DEPTH);
function fixture({broken}) {
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-real-teamleader-'));
 const home=path.join(root,'source');
 const stateDir=path.join(root,'private-state');
 const folder=path.join(home,'.github','continuum');
 fs.mkdirSync(folder,{recursive:true});
 fs.writeFileSync(path.join(folder,'math.mjs'),broken
  ?"export function square(n){return n*n}\nexport function quad(n){return sqare(square(n))}\n"
  :"export function square(n){return n*n}\nexport function quad(n){return square(square(n))}\n");
 fs.writeFileSync(path.join(folder,'math.test.mjs'),
  "import test from 'node:test';\n"+
  "import assert from 'node:assert/strict';\n"+
  "import {quad} from './math.mjs';\n"+
  "test('quad regression',()=>assert.equal(quad(2),16));\n");
 const git=(...args)=>{
  const result=spawnSync('git',args,{cwd:home,encoding:'utf8',timeout:10000});
  assert.equal(result.status,0,'git '+args.join(' ')+' -> '+result.stderr);
  return result.stdout.trim();
 };
 git('init','-q');
 git('-c','user.name=continuum-test','-c','user.email=continuum-test@example.invalid',
   'add','.');
 git('-c','user.name=continuum-test','-c','user.email=continuum-test@example.invalid',
   'commit','-qm','isolated fixture');
 return {root,home,stateDir,git,head:git('rev-parse','HEAD')};
}
test('actual offline Teamleader repairs a broken Git fixture and retains only a reviewable patch',
 {skip:nested,timeout:180000},async()=>{
 const f=fixture({broken:true});
 try {
  const driver=await localTeamleaderDriver();
  const result=runTeamleaderCycle({root:f.home,stateDir:f.stateDir,driver,budgetMinutes:2});
  assert.equal(result.head,f.head);
  assert.equal(result.kind,'BASELINE_REPAIR');
  assert.equal(result.outcome,'VERIFIED_PATCH_PENDING_REVIEW',result.detail);
  assert.ok(result.artifact);
  const saved=JSON.parse(fs.readFileSync(result.artifact,'utf8'));
  assert.equal(saved.outcome,'VERIFIED');
  assert.equal(saved.baseline.passed,false);
  assert.equal(saved.verified.strategy,'JS_TYPO_REFERENCE');
  assert.ok(saved.verified.tests.some(t=>t.tests.executed>0&&t.passed));
  assert.equal(saved.handoffs.conclusion,'VERIFIED');
  assert.equal(verifyRepairHandoffs(saved.handoffs),true);
  assert.equal(saved.handoffs.messages.at(-1).approvedForMerge,false);
  const patch=fs.readFileSync(path.join(path.dirname(result.artifact),'repair.patch'),'utf8');
  assert.match(patch,/-export function quad\(n\)\{return sqare/);
  assert.match(patch,/\+export function quad\(n\)\{return square/);
  const patchHash=createHash('sha256').update(patch,'utf8').digest('hex');
  assert.equal(patchHash,saved.verified.patchSha256,
    'Patch bytes must match the SHA-256 in the verified repair report');
  const applicability=spawnSync('git',['apply','--check','--binary','-'],{
    cwd:f.home,encoding:'utf8',input:patch,timeout:10000
  });
  assert.equal(applicability.status,0,
    'Verified patch must apply to the untouched base: '+applicability.stderr);
  assert.match(fs.readFileSync(path.join(f.home,'.github/continuum/math.mjs'),'utf8'),
    /return sqare/,'source checkout must not be modified by autonomous repair');
  assert.equal(f.git('status','--porcelain'),'');
  const leader=decodeLeaderState(fs.readFileSync(result.statePath));
  assert.equal(leader.events.at(-1).outcome,'VERIFIED_PATCH_PENDING_REVIEW');
  const ledger=JSON.parse(fs.readFileSync(path.join(f.stateDir,'repair-ledger.json')));
  assert.ok(ledger.attempts.some(a=>a.outcome==='VERIFIED'));
  assert.equal(ledger.sessions.at(-1).outcome,'VERIFIED');
  assert.ok(fs.existsSync(path.join(f.stateDir,'teamleader-state.json')));
  assert.deepEqual(saved.inference,{
   mode:'DETERMINISTIC_ONLY',modelId:null,modelInvocations:0,
   reason:'No generative model is invoked by this repair pipeline'
  },'a deterministic repair must not masquerade as local language-model inference');
  assert.equal(saved.independent.passed,true);
  assert.equal(saved.backlogSync.status,'RECORDED');
  const backlog=new BacklogStore(path.join(f.stateDir,'development-backlog.json'),{
   actor:'continuum-teamleader'});
  const matching=backlog.read().doc.tasks.filter(t=>
   t.source==='repair-loop'&&t.kind==='REPAIR');
  assert.equal(matching.length,1,'a real baseline repair should create exactly one backlog task');
  assert.equal(matching[0].state,'DONE');
  assert.ok(matching[0].evidence.some(e=>e.type==='TEST_RUN'&&
   e.profiles.some(p=>p.passed&&p.executed>0)));
  const review=fs.readFileSync(path.join(path.dirname(result.artifact),'review-summary.md'),'utf8');
  assert.match(review,/DETERMINISTIC_ONLY/);
  assert.match(review,/Independent check: PASSED/);
  assert.match(review,/Automatic merge: NEVER/);
 } finally {fs.rmSync(f.root,{recursive:true,force:true});}
});
test('scheduled owner task completes full real Git repair and independent verification',
 {skip:nested,timeout:180000},async()=>{
 const f=fixture({broken:true});
 try{
  const file=path.join(f.stateDir,'development-backlog.json');
  const backlog=new BacklogStore(file,{actor:'owner'});
  backlog.apply([
   {op:'propose',actor:'owner',at:1,task:{
     id:'owner-scheduled-one',title:'Repair real math regression',kind:'REPAIR',
     source:'owner',priority:900,maxAttempts:3,subject:{base:f.head,chain:'node'}
   }},
   {op:'accept',actor:'owner',at:2,id:'owner-scheduled-one',
     reason:'owner explicitly accepted this immutable repair task'}
  ]);
  const original=fs.readFileSync(path.join(f.home,'.github/continuum/math.mjs'));
  const driver=await localTeamleaderDriver({contextualExperience:true});
  const result=runTeamleaderCycle({root:f.home,stateDir:f.stateDir,
   driver,budgetMinutes:2});
  assert.equal(result.kind,'OWNER_REPAIR');
  assert.equal(result.outcome,'VERIFIED_PATCH_PENDING_REVIEW',result.detail);
  assert.ok(result.artifact);
  const report=JSON.parse(fs.readFileSync(result.artifact,'utf8'));
  assert.ok(report.attempts.every(attempt=>attempt.contextKey===undefined),
   'baseline contextual opt-in must not expand the owner repair scope');
  assert.equal(report.baseline.passed,false,'original fixture must reproduce its failure');
  assert.equal(report.outcome,'VERIFIED');
  assert.equal(verifyRepairHandoffs(report.handoffs),true);
  assert.equal(report.handoffs.messages.at(-1).approvedForMerge,false);
  const task=backlog.read().doc.tasks.find(t=>t.id==='owner-scheduled-one');
  assert.equal(task.state,'DONE');
  assert.ok(task.evidence.some(e=>e.profiles.some(p=>p.executed>0&&p.passed)));
  const patch=fs.readFileSync(path.join(path.dirname(result.artifact),'repair.patch'));
  assert.equal(createHash('sha256').update(patch).digest('hex'),report.verified.patchSha256);
  assert.deepEqual(fs.readFileSync(path.join(f.home,'.github/continuum/math.mjs')),original);
  assert.equal(f.git('status','--porcelain'),'');
  assert.equal(runTeamleaderCycle({root:f.home,stateDir:f.stateDir,driver}).outcome,'IDLE');
 }finally{fs.rmSync(f.root,{recursive:true,force:true});}
});

test('local driver rejects nonboolean contextual experience options',async()=>{
 for(const contextualExperience of [null,0,1,'false','true',{},[]])
  await assert.rejects(localTeamleaderDriver({contextualExperience}),/contextual experience option/i);
});

for(const consumer of ['baseline','owner'])test(
 'local '+consumer+' repair refuses a dangling ledger before executing checks',
 {timeout:30000},async()=>{
 const f=fixture({broken:true});
 try{
  fs.mkdirSync(f.stateDir,{recursive:true});
  const ledger=path.join(f.stateDir,'repair-ledger.json');
  const target=path.join(f.stateDir,'missing-ledger.json');
  fs.symlinkSync(target,ledger);
  const driver=await localTeamleaderDriver();
  let work;
  if(consumer==='owner'){
   const backlog=new BacklogStore(path.join(f.stateDir,'development-backlog.json'),{actor:'owner'});
   backlog.apply([
    {op:'propose',actor:'owner',at:1,task:{id:'owner-invalid-ledger',title:'Repair fixture',
     kind:'REPAIR',source:'owner',subject:{base:f.head,chain:'node'}}},
    {op:'accept',actor:'owner',at:2,id:'owner-invalid-ledger'}
   ]);
   work=()=>driver.ownerRepair(f.home,f.stateDir,f.head,'owner-invalid-ledger',{budgetMinutes:1});
  }else work=()=>driver.repair(f.home,f.stateDir,f.head,{budgetMinutes:1});
  assert.throws(work,{code:'ELOOP'});
  assert.equal(fs.readlinkSync(ledger),target);
  assert.equal(fs.existsSync(target),false);
  const reports=path.join(f.stateDir,'reports');
  if(fs.existsSync(reports))assert.equal(fs.readdirSync(reports,{recursive:true})
   .filter(file=>fs.lstatSync(path.join(reports,file)).isFile()).length,0,
   'invalid prior learning must fail before tests, patch or result artifacts');
  assert.equal(f.git('status','--porcelain'),'');
  assert.equal(f.git('worktree','list','--porcelain').match(/^worktree /gm).length,1);
 }finally{fs.rmSync(f.root,{recursive:true,force:true});}
});

test('real baseline driver scopes learning only with explicit contextual opt-in',
 {skip:nested,timeout:180000},async()=>{
 const f=fixture({broken:true});
 try{
  for(const [label,options,scoped] of [
   ['default',{},false],['disabled',{contextualExperience:false},false],
   ['enabled',{contextualExperience:true},true]
  ]){
   const state=path.join(f.root,label);
   const driver=await localTeamleaderDriver(options);
   const report=driver.repair(f.home,state,f.head,{budgetMinutes:1});
   assert.equal(report.outcome,'VERIFIED',label);
   assert.equal(report.independent.passed,true,label);
   const winner=report.attempts.find(attempt=>attempt.candidate===report.verified.candidate);
   assert.equal(winner.contextKey,scoped?'node.js.call':undefined,label);
   const ledger=loadLedger(path.join(state,'repair-ledger.json'));
   assert.ok(ledger.experiences.some(entry=>entry.kind===(scoped?
    'REFERENCE_ERROR|JS_TYPO_REFERENCE|node.js.call':'REFERENCE_ERROR|JS_TYPO_REFERENCE')),label);
  }
  assert.equal(f.git('status','--porcelain'),'');
  assert.equal(f.git('worktree','list','--porcelain').match(/^worktree /gm).length,1);
 }finally{fs.rmSync(f.root,{recursive:true,force:true});}
});

test('actual passing Git fixture generates no fake repair, patch, or changed source',
 {skip:nested,timeout:180000},async()=>{
 const f=fixture({broken:false});
 try {
  const driver=await localTeamleaderDriver();
  const result=runTeamleaderCycle({root:f.home,stateDir:f.stateDir,driver,budgetMinutes:2});
  assert.equal(result.outcome,'NO_FAILURE',result.detail);
  const saved=JSON.parse(fs.readFileSync(result.artifact,'utf8'));
  assert.equal(saved.baseline.passed,true);
  assert.equal(saved.verified,null);
  assert.ok(!fs.existsSync(path.join(path.dirname(result.artifact),'repair.patch')));
  assert.equal(saved.handoffs.messages.at(-1).event,'NO_AUTOMATIC_MERGE');
  assert.equal(saved.backlogSync.applied,0);
  assert.equal(saved.backlogSync.status,'SKIPPED');
  assert.ok(!fs.existsSync(path.join(f.stateDir,'development-backlog.json')),
   'clean baseline must not invent a repair task');
  assert.equal(f.git('status','--porcelain'),'');
 } finally {fs.rmSync(f.root,{recursive:true,force:true});}
});

test('actual offline Teamleader completes a priority investigation with real Git checks and evidence',
 {skip:nested,timeout:180000},async()=>{
 const f=fixture({broken:false});
 try {
  const src=path.join(f.home,'app','src','main','java','p');
  fs.mkdirSync(src,{recursive:true});
  fs.writeFileSync(path.join(src,'App.kt'),
   "package p\nclass App { fun live() { Connected().go() } }\nclass Connected { fun go()=1 }\n");
  fs.writeFileSync(path.join(src,'Lab.kt'),"package p\nclass Orphan\nclass Isolated\n");
  f.git('-c','user.name=continuum-test','-c','user.email=continuum-test@example.invalid',
    'add','.');
  f.git('-c','user.name=continuum-test','-c','user.email=continuum-test@example.invalid',
    'commit','-qm','add isolated investigation subject');
  const head=f.git('rev-parse','HEAD');
  const findings=analyzeRepository(f.home);
  assert.ok(findings.findings.length>0,'fixture must really contain static findings');
  const backlog=new BacklogStore(path.join(f.stateDir,'development-backlog.json'),
    {actor:'continuum-teamleader'});
  const {index:_index,...report}=findings;
  const ops=syncBacklogOps(backlog.read().doc,report,{
    at:Date.now(),actor:'code-analyzer',limit:1});
  assert.ok(ops.some(op=>op.op==='propose'));
  backlog.apply(ops);
  const task=backlog.read().doc.tasks.find(t=>t.kind==='INVESTIGATE'&&t.state==='PROPOSED');
  assert.ok(task);
  backlog.apply([{op:'accept',actor:'continuum-teamleader',
    at:Date.now(),id:task.id,reason:'Independent real Git test'}]);
  const driver=await localTeamleaderDriver();
  const first=runTeamleaderCycle({root:f.home,stateDir:f.stateDir,driver,budgetMinutes:2});
  assert.equal(first.head,head);
  assert.equal(first.outcome,'NO_FAILURE','baseline must be green before investigations');
  const second=runTeamleaderCycle({root:f.home,stateDir:f.stateDir,driver,budgetMinutes:2});
  assert.equal(second.kind,'INVESTIGATE');
  assert.equal(second.taskId,task.id);
  assert.equal(second.outcome,'DONE_INVESTIGATION',second.detail);
  const saved=backlog.read().doc.tasks.find(t=>t.id===task.id);
  assert.equal(saved.state,'DONE');
  assert.ok(saved.evidence.some(e=>e.type==='TEST_RUN' &&
    e.profiles.some(p=>p.profile==='node-continuum'&&p.passed&&p.executed>0)));
  const reportPath=path.join(f.stateDir,'reports','investigate-'+task.id+'.json');
  assert.ok(fs.existsSync(reportPath));
  const artifact=JSON.parse(fs.readFileSync(reportPath,'utf8'));
  assert.equal(artifact.taskId,task.id);
  assert.equal(artifact.base,head);
  assert.equal(f.git('status','--porcelain'),'',
    'investigation must not modify the source checkout');
 }finally{fs.rmSync(f.root,{recursive:true,force:true});}
});
