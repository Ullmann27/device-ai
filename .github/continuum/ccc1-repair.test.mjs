// CCC1 repair capability (protocol v2): an owner repair order runs the EXISTING owner repair path (fixed test chain
// in an isolated worktree -> deterministic repair strategies -> independent second worktree) through the CCC1
// journal, gate and verifier. The end-to-end tests use a real regression in a throw-away repository: the chain
// really fails, the pipeline really repairs it, and only the verifier's check of the real artifacts confirms it.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {runMobileRepairJob,readReceipt,jobTaskId,cancelMobileCodingJob,REPAIR_PROFILE_COMMAND} from '../../scripts/continuum-mobile-coding.mjs';
import {envelope,envelopeProblems,payloadProblems,PROTOCOL_HASH,PROTOCOL_HASH_V1,CAPABILITY,REPAIR_CAPABILITY,newTaskId,sha256} from './ccc1-envelope.mjs';
import {gateTestRequest} from './ccc1-gate.mjs';
import {verifyRepairReceipt} from './ccc1-verifier.mjs';
import {DurableTaskJournal,runtimeKey} from './ccc1-journal.mjs';
import {jobIdFor,recover,builderStateOf} from './ccc1-recovery.mjs';
import {taskState} from './ccc1-orchestrator.mjs';
import {submitRepair,runCcc1Cycle,compileRepair} from './ccc1-worker.mjs';
import {ExperienceLog} from './ccc1-experience.mjs';
import {BacklogStore,newToken} from './development-backlog.mjs';

process.env.HOME=fs.mkdtempSync(path.join(os.tmpdir(),'ccc1-repair-home-'));
const nested=Boolean(process.env.CONTINUUM_REPAIR_DEPTH);   // never start real repair sessions inside a repair worktree

const GOOD='/** Slug of a title for file names. */\nexport function slug(title){\n  return title.trim().toLowerCase().replace(/\\s+/g,"-");\n}\n';
const BROKEN=GOOD.replace('toLowerCase','toUpperCase');

/** Throw-away repository on an isolated branch; with `broken`, its HEAD commit introduces a real regression. */
function fixture({broken=true}={}){
 const home=fs.mkdtempSync(path.join(os.tmpdir(),'ccc1-repair-'));
 const root=path.join(home,'device-ai'),dir=path.join(root,'.github','continuum');
 fs.mkdirSync(dir,{recursive:true});
 fs.writeFileSync(path.join(dir,'text-utils.mjs'),GOOD);
 fs.writeFileSync(path.join(dir,'text-utils.test.mjs'),"import test from 'node:test';\nimport assert from 'node:assert/strict';\n"+
  "import {slug} from './text-utils.mjs';\ntest('slug',()=>assert.equal(slug(' Hallo Welt '),'hallo-welt'));\n");
 const git=(...args)=>{const r=spawnSync('git',args,{cwd:root,encoding:'utf8',timeout:15000});assert.equal(r.status,0,r.stderr);return r.stdout.trim();};
 const commit=msg=>{git('add','.');git('-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm',msg);};
 git('init','-q','-b','ccc1-work');commit('seed');
 if(broken){fs.writeFileSync(path.join(dir,'text-utils.mjs'),BROKEN);commit('regression: slug upper-cases');}
 return {home,root,git,head:git('rev-parse','HEAD'),state:path.join(home,'ccc1-state')};
}
const binding=(over={})=>({sessionId:crypto.randomUUID(),taskId:newTaskId(),repoFullName:'local/device-ai',baseCommitSha:'a'.repeat(40),
 worktreeId:'main',deadline:Date.now()+3_600_000,maxRounds:2,maxCpuMillis:3_600_000,maxMemoryBytes:2e9,permissions:[REPAIR_CAPABILITY+':node'],
 checkpointHash:null,...over});

test('protocol v2: repair goals, hypotheses and requests are strict; v1 journals stay valid; foreign hashes do not',()=>{
 assert.deepEqual(payloadProblems('GOAL',{text:'Repariere',repair:{chain:'node'}}),[]);
 assert.notDeepEqual(payloadProblems('GOAL',{text:'x',repair:{chain:'bash'}}),[]);
 assert.notDeepEqual(payloadProblems('GOAL',{text:'x',repair:{chain:'node'},examples:[{args:[1],expected:1}]}),[],'repair and examples exclude each other');
 const h={claim:'c',repair:{chain:'node'},basis:'b',confidence:0.5};
 assert.deepEqual(payloadProblems('HYPOTHESIS',h),[]);
 assert.notDeepEqual(payloadProblems('HYPOTHESIS',{...h,module:'.github/continuum/x.mjs'}),[],'no mixed shapes');
 assert.deepEqual(payloadProblems('TEST_REQUEST',{hypothesis:crypto.randomUUID(),repair:{chain:'node'},budgetMinutes:20}),[]);
 assert.notDeepEqual(payloadProblems('TEST_REQUEST',{hypothesis:crypto.randomUUID(),repair:{chain:'node',cmd:'rm -rf /'},budgetMinutes:20}),[]);
 assert.notDeepEqual(payloadProblems('TEST_REQUEST',{hypothesis:crypto.randomUUID(),repair:{chain:'node'},budgetMinutes:31}),[]);
 assert.deepEqual(payloadProblems('PLAN',{steps:['a'],capability:REPAIR_CAPABILITY}),[]);
 const b=binding();
 const e=envelope({binding:b,sender:'OWNER',kind:'GOAL',payload:{text:'Repariere',repair:{chain:'node'}},sequence:0});
 assert.equal(e.protocolHash,PROTOCOL_HASH);
 assert.deepEqual(envelopeProblems(e),[]);
 assert.deepEqual(envelopeProblems({...e,protocolHash:PROTOCOL_HASH_V1}),[],'entries of v1 tasks stay readable');
 assert.match(envelopeProblems({...e,protocolHash:'f'.repeat(64)}).join(),/foreign protocol hash/);
 const v1=envelope({binding:{...b,protocolHash:PROTOCOL_HASH_V1},sender:'OWNER',kind:'GOAL',payload:{text:'x'},sequence:0});
 assert.equal(v1.protocolHash,PROTOCOL_HASH_V1,'a v1 task keeps its hash for later entries');
 assert.match(envelopeProblems({...e,permissions:['node-repair:bash']}).join(),/permissions/);
 assert.deepEqual(envelopeProblems({...e,permissions:['node-repair','node-repair:android',CAPABILITY]}),[]);
});

test('the gate allows a repair only for a chain the owner granted, within deadline, rounds and CPU budget',()=>{
 const goal=envelope({binding:binding(),sender:'OWNER',kind:'GOAL',payload:{text:'Repariere',repair:{chain:'node'}},sequence:0});
 const usage={rounds:0,cpuMillis:0,now:Date.now()};
 assert.equal(gateTestRequest({repair:{chain:'node'},budgetMinutes:20},[goal],usage).decision,'ALLOW');
 const other=gateTestRequest({repair:{chain:'android'},budgetMinutes:20},[goal],usage);
 assert.equal(other.decision,'NEEDS_APPROVAL');assert.deepEqual(other.needs,['node-repair:android']);
 assert.equal(gateTestRequest({repair:{chain:'bash'},budgetMinutes:20},[goal],usage).decision,'DENY');
 assert.equal(gateTestRequest({repair:{chain:'node'},budgetMinutes:20},[goal],{...usage,rounds:2}).decision,'DENY');
 assert.equal(gateTestRequest({repair:{chain:'node'},budgetMinutes:20},[goal],{...usage,cpuMillis:3_600_000}).decision,'DENY');
 assert.equal(gateTestRequest({repair:{chain:'node'},budgetMinutes:20},[goal],{...usage,now:goal.deadline+1}).decision,'DENY');
});

/** A well-formed verified repair receipt with a real patch file; tests mutate copies of it. */
function verifiedReceipt(dir){
 const patch=Buffer.from('diff --git a/x b/x\n');
 const file=path.join(dir,'repair.patch');fs.writeFileSync(file,patch);
 const run=(phase,passed,executed,failed)=>({phase,exitCode:passed?0:1,passed,executed,failed,timedOut:false,command:REPAIR_PROFILE_COMMAND.node});
 return {schema:'continuum-mobile-coding-receipt',version:1,job:'11111111-1111-4111-8111-111111111111',operation:'code-repair',
  taskId:'mob-x',commit:'c'.repeat(40),toolchain:{node:'v22.0.0'},order:{chain:'node'},status:'REPAIR_PATCH_VERIFIED_PENDING_REVIEW',
  patch:{sha256:sha256(patch),path:file,bytes:patch.length},evidence:{verificationSha256:'d'.repeat(64),baselineFailed:1,baselineExecuted:3,
   candidateExecuted:3,independentExecuted:3,evidenceSufficient:true},
  commands:[run('baseline',false,3,1),run('candidate',true,3,0),run('independent',true,3,0)],
  approvedForMerge:false,installed:false,apk:null,deviceVerified:false,failure:null};
}

test('the verifier confirms a repair only from real artifacts: failing baseline, passing candidate and independent run',()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'ccc1-repair-verify-'));
 const ok=verifiedReceipt(dir);
 const bind={job:ok.job,commit:ok.commit,chain:'node'};
 assert.equal(verifyRepairReceipt(ok,bind).verdict,'CONFIRMED');
 const clone=()=>structuredClone(ok);
 const cases=[
  ['another job',r=>{r.job='22222222-2222-4222-8222-222222222222';}],
  ['another commit',r=>{r.commit='e'.repeat(40);}],
  ['another chain',r=>{r.order={chain:'android'};}],
  ['a merge claim',r=>{r.approvedForMerge=true;}],
  ['a contract receipt',r=>{r.operation='code-test';}],
  ['changed patch bytes',r=>{fs.writeFileSync(path.join(dir,'other.patch'),'evil');r.patch={...r.patch,path:path.join(dir,'other.patch')};}],
  ['a baseline that did not fail',r=>{r.commands[0]={...r.commands[0],passed:true,exitCode:0,failed:0};r.evidence.baselineFailed=0;}],
  ['an independent run with fewer tests',r=>{r.commands[2].executed=2;r.evidence.independentExecuted=2;}],
  ['a failing independent run',r=>{r.commands[2]={...r.commands[2],passed:false,failed:1};}],
  ['a timed-out candidate run',r=>{r.commands[1].timedOut=true;}],
  ['a timed-out independent run',r=>{r.commands[2].timedOut=true;}],
  ['a command outside the allowlist',r=>{r.commands[1].command='node evil.js';}],
  ['a missing evidence hash',r=>{r.evidence.verificationSha256=null;}],
  ['insufficient evidence',r=>{r.evidence.evidenceSufficient=false;}]];
 for(const [what,change] of cases){
  const r=clone();change(r);
  assert.notEqual(verifyRepairReceipt(r,bind).verdict,'CONFIRMED',what);
 }
 const none={...clone(),status:'NO_FAILURE_FOUND',patch:null,evidence:null,commands:[{phase:'baseline',exitCode:0,passed:true,executed:3,failed:0,timedOut:false,command:REPAIR_PROFILE_COMMAND.node}]};
 assert.equal(verifyRepairReceipt(none,bind).verdict,'REFUTED');
 assert.equal(verifyRepairReceipt({...none,commands:[]},bind).verdict,'INCONCLUSIVE','NO_FAILURE_FOUND needs its passing baseline');
 const failed={...clone(),status:'FAILED_REPAIR',patch:null,evidence:null,commands:[ok.commands[0]],failure:{code:'NO_VERIFIED_REPAIR',message:'x'}};
 assert.equal(verifyRepairReceipt(failed,bind).verdict,'REFUTED');
 assert.equal(verifyRepairReceipt({...clone(),status:'BLOCKED_UNCLEAN_SOURCE'},bind).verdict,'INCONCLUSIVE');
});

test('repair receipt proof binds coverage to command details without losing independent tests',async t=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'ccc1-repair-proof-'));
 t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
 const ok=verifiedReceipt(dir);
 const bind={job:ok.job,commit:ok.commit,chain:'node'};
 const cases=[
  ['candidate command lost tests behind an unchanged summary',r=>{r.commands[1].executed=1;}],
  ['independent command lost tests behind an unchanged summary',r=>{r.commands[2].executed=1;}],
  ['baseline summary understates the real failing suite',r=>{r.evidence.baselineExecuted=1;r.evidence.candidateExecuted=2;r.evidence.independentExecuted=2;r.commands[1].executed=2;r.commands[2].executed=2;}],
  ['summary claims a different failing-test count',r=>{r.evidence.baselineFailed=2;}],
  ['candidate summary differs from command',r=>{r.evidence.candidateExecuted=4;r.evidence.independentExecuted=4;r.commands[2].executed=4;}],
  ['independent summary differs from command',r=>{r.evidence.independentExecuted=4;}],
  ['independent rerun drops tests introduced by candidate',r=>{r.commands[1].executed=6;r.evidence.candidateExecuted=6;}],
  ['count strings are not executed-test counts',r=>{for(const x of r.commands)x.executed='3';for(const k of ['baselineExecuted','candidateExecuted','independentExecuted'])r.evidence[k]='3';}],
  ['fractional counts are not executed-test counts',r=>{for(const x of r.commands)x.executed=3.5;for(const k of ['baselineExecuted','candidateExecuted','independentExecuted'])r.evidence[k]=3.5;}],
  ['unsafe integer counts are not executed-test counts',r=>{for(const x of r.commands)x.executed=Number.MAX_SAFE_INTEGER+1;for(const k of ['baselineExecuted','candidateExecuted','independentExecuted'])r.evidence[k]=Number.MAX_SAFE_INTEGER+1;}],
  ['baseline cannot have more failures than executed tests',r=>{r.commands[0].failed=4;r.evidence.baselineFailed=4;}],
  ['baseline exit zero contradicts a claimed failure',r=>{r.commands[0].exitCode=0;}],
  ['timed-out baseline is not a completed RED run',r=>{r.commands[0].timedOut=true;}],
  ['signal termination is not a completed RED run',r=>{r.commands[0].exitCode=null;}],
  ['unknown chain is not an implicit undefined allowlist',r=>{r.order.chain='unknown';for(const x of r.commands)delete x.command;}],
  ['extra test phases cannot supplement the three fixed phases',r=>{r.commands.push({...r.commands[2],phase:'extra'});}],
  ['missing command details cannot be verified',r=>{r.commands[1]=null;}],
 ];
 for(const [what,change] of cases)await t.test(what,()=>{
  const r=structuredClone(ok);change(r);
  assert.equal(verifyRepairReceipt(r,{...bind,chain:r.order.chain}).verdict,'INCONCLUSIVE');
 });
 for(const [what,counts] of [['equal coverage',[3,3,3]],['increased coverage',[3,6,7]]])await t.test(what,()=>{
  const r=structuredClone(ok);
  ['baseline','candidate','independent'].forEach((phase,i)=>{r.commands[i].executed=counts[i];r.evidence[phase+'Executed']=counts[i];});
  assert.equal(verifyRepairReceipt(r,bind).verdict,'CONFIRMED');
 });
});

test('non-success repair verdicts require completed baseline evidence',async t=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'ccc1-repair-refutation-'));
 t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
 const good=verifiedReceipt(dir),bind={job:good.job,commit:good.commit,chain:'node'};
 for(const status of ['NO_FAILURE_FOUND','FAILED_REPAIR']){
  const r={...structuredClone(good),status,patch:null,evidence:null,commands:[{...good.commands[0]}]};
  if(status==='NO_FAILURE_FOUND')Object.assign(r.commands[0],{passed:true,exitCode:0,failed:0});
  await t.test(status+' valid completed baseline',()=>assert.equal(verifyRepairReceipt(r,bind).verdict,'REFUTED'));
  const changes=[
   ['timeout',x=>{x.commands[0].timedOut=true;}],
   ['signal exit',x=>{x.commands[0].exitCode=null;}],
   ['contradicting exit',x=>{x.commands[0].exitCode=status==='NO_FAILURE_FOUND'?1:0;}],
   ['zero executed tests',x=>{x.commands[0].executed=0;}],
   ['string executed count',x=>{x.commands[0].executed='3';}],
   ['extra run record',x=>{x.commands.push({...x.commands[0],phase:'extra'});}],
  ];
  for(const [what,mutate] of changes)await t.test(status+' '+what,()=>{
   const x=structuredClone(r);mutate(x);
   assert.equal(verifyRepairReceipt(x,bind).verdict,'INCONCLUSIVE');
  });
 }
});

test('CCC1 repairs a real regression end to end and records it once for the owner to review',{skip:nested,timeout:300000},async()=>{
 const f=fixture();
 assert.equal(compileRepair({project:f.root,stateDir:f.state,chain:'node'}).status,'BLOCKED','the fixture lacks the CCC1 runtime files the preflight requires');
 const taskId=submitRepair({project:f.root,stateDir:f.state,chain:'node'});
 const r=await runCcc1Cycle({project:f.root,stateDir:f.state});
 assert.equal(r.ccc1,'COMPLETED',JSON.stringify(r));
 assert.equal(r.outcome,'VERIFIED_PATCH_PENDING_REVIEW');
 const entries=DurableTaskJournal.forTask(f.state,taskId,runtimeKey(f.state)).entries();
 assert.deepEqual(entries.map(e=>e.kind+(['RECEIPT','CRITIQUE'].includes(e.kind)?':'+e.payload.verdict:'')),
  ['GOAL','HYPOTHESIS','PLAN','TEST_REQUEST','RECEIPT:CONFIRMED','CRITIQUE:CONFIRMED']);
 const receiptEntry=entries.find(e=>e.kind==='RECEIPT');
 assert.equal(receiptEntry.payload.status,'REPAIR_PATCH_VERIFIED_PENDING_REVIEW');
 const [baseline,candidate,independent]=receiptEntry.payload.runs;
 assert.ok(baseline.failed>0,'a real test failure was detected');
 assert.ok(candidate.executed>0&&candidate.failed===0&&independent.executed>=baseline.executed&&independent.failed===0);
 // the patch is offered, not applied: the checkout is untouched; applied to a fresh clone it repairs the chain
 assert.equal(f.git('status','--porcelain'),'');assert.equal(f.git('rev-parse','HEAD'),f.head);
 const job=jobIdFor(taskId,entries.find(e=>e.kind==='TEST_REQUEST').messageId);
 const stored=readReceipt(builderStateOf(f.state),job);
 assert.equal(stored.patch.sha256,receiptEntry.payload.patchSha256);
 const clone=fs.mkdtempSync(path.join(os.tmpdir(),'ccc1-repair-clone-'));
 assert.equal(spawnSync('git',['clone','-q',f.root,clone]).status,0);
 assert.equal(spawnSync('git',['apply',stored.patch.path],{cwd:clone}).status,0);
 const after=spawnSync(process.execPath,['--test','.github/continuum/text-utils.test.mjs'],{cwd:clone,encoding:'utf8',
  env:{...process.env,NODE_TEST_CONTEXT:''}});
 assert.equal(after.status,0,after.stdout+after.stderr);
 const [exp]=new ExperienceLog(path.join(f.state,'experience.jsonl')).entries();
 assert.deepEqual(exp.record.confirmed,{repair:'node',patchSha256:stored.patch.sha256});
 assert.equal(exp.record.hypotheses[0].verdict,'CONFIRMED');
 assert.equal((await runCcc1Cycle({project:f.root,stateDir:f.state})).outcome,'IDLE','a finished task is not repeated');
});

test('a passing chain is reported as nothing to repair, never as a repair',{skip:nested,timeout:300000},async()=>{
 const f=fixture({broken:false});
 const taskId=submitRepair({project:f.root,stateDir:f.state,chain:'node'});
 const r=await runCcc1Cycle({project:f.root,stateDir:f.state});
 assert.equal(r.ccc1,'ABSTAINED',JSON.stringify(r));
 const entries=DurableTaskJournal.forTask(f.state,taskId,runtimeKey(f.state)).entries();
 assert.equal(entries.find(e=>e.kind==='RECEIPT').payload.status,'NO_FAILURE_FOUND');
 assert.equal(entries.find(e=>e.kind==='RECEIPT').payload.verdict,'REFUTED');
 assert.match(entries.find(e=>e.kind==='ABSTAIN').payload.reason,/ohne Fehler; es gibt nichts zu reparieren/);
 assert.equal(entries.find(e=>e.kind==='RECEIPT').payload.patchSha256,null);
});

test('repair orders refuse invalid input, dirty checkouts, reused job ids and stale bases; replays never execute again',{skip:nested,timeout:300000},async()=>{
 const f=fixture({broken:false});
 const builderState=path.join(f.home,'builder'),teamleaderState=path.join(f.home,'teamleader');
 const job='33333333-3333-4333-8333-333333333333';
 const base={project:f.root,builderState,teamleaderState,budgetMinutes:5};
 assert.equal((await runMobileRepairJob({...base,job,chain:'bash'})).status,'BLOCKED_INVALID_ORDER');
 assert.equal((await runMobileRepairJob({...base,job:'not-a-uuid',chain:'node'})).status,'BLOCKED_INVALID_ORDER');
 fs.writeFileSync(path.join(f.root,'stray.txt'),'x');
 assert.equal((await runMobileRepairJob({...base,job,chain:'node'})).status,'BLOCKED_UNCLEAN_SOURCE');
 fs.rmSync(path.join(f.root,'stray.txt'));
 const first=await runMobileRepairJob({...base,job,chain:'node'});
 assert.equal(first.status,'NO_FAILURE_FOUND');
 const store=new BacklogStore(path.join(teamleaderState,'development-backlog.json'),{actor:'test'});
 const attempts=store.read().doc.tasks.find(t=>t.id===jobTaskId(job)).attempts;
 const again=await runMobileRepairJob({...base,job,chain:'node'});
 assert.equal(again.replayed,true);assert.equal(again.status,'NO_FAILURE_FOUND');
 assert.equal(store.read().doc.tasks.find(t=>t.id===jobTaskId(job)).attempts,attempts,'a replay executes nothing');
 // a job id that already belongs to another operation is never reinterpreted
 const job2='44444444-4444-4444-8444-444444444444';
 fs.mkdirSync(path.join(builderState,'coding-jobs'),{recursive:true});
 fs.writeFileSync(path.join(builderState,'coding-jobs',job2+'.json'),JSON.stringify({...first,job:job2,operation:'code-test',replayed:undefined}));
 assert.equal((await runMobileRepairJob({...base,job:job2,chain:'node'})).failure.code,'JOB_ID_REUSED');
 // an order bound to an earlier commit does not run on a moved checkout
 const job3='55555555-5555-4555-8555-555555555555';
 const {backlogCommand}=await import('./backlog-cli.mjs');
 const file=path.join(teamleaderState,'development-backlog.json');
 await backlogCommand('propose-repair',{file,actor:'android-owner',id:jobTaskId(job3),chain:'node'},{root:f.root,log:()=>{}});
 await backlogCommand('accept',{file,actor:'android-owner',id:jobTaskId(job3),reason:'test'},{root:f.root,log:()=>{}});
 fs.writeFileSync(path.join(f.root,'.github','continuum','more.mjs'),'export const x=1;\n');
 f.git('add','.');f.git('-c','user.name=f','-c','user.email=f@example.invalid','commit','-qm','move');
 assert.equal((await runMobileRepairJob({...base,job:job3,chain:'node'})).status,'BLOCKED_STALE_BASE');
 // the owner can cancel an order before it runs
 const cancelled=cancelMobileCodingJob({project:f.root,teamleaderState,job:job3});
 assert.equal(cancelled.status,'CANCELLED');
});

test('an interrupted repair is never repeated without the owner; recovery classifies it like any coding job',()=>{
 const f=fixture({broken:false});
 const taskId=submitRepair({project:f.root,stateDir:f.state,chain:'node'});
 const journal=DurableTaskJournal.forTask(f.state,taskId,runtimeKey(f.state));
 const goal=journal.entries()[0];
 const b={sessionId:goal.sessionId,taskId,repoFullName:goal.repoFullName,baseCommitSha:goal.baseCommitSha,worktreeId:goal.worktreeId,
  deadline:goal.deadline,maxRounds:goal.maxRounds,maxCpuMillis:goal.maxCpuMillis,maxMemoryBytes:goal.maxMemoryBytes,
  permissions:goal.permissions,checkpointHash:null,protocolHash:goal.protocolHash};
 const h=envelope({binding:b,sender:'REASONER',kind:'HYPOTHESIS',payload:{claim:'c',repair:{chain:'node'},basis:'b',confidence:0.5},sequence:1});
 journal.append(h);
 const q=envelope({binding:b,sender:'REASONER',kind:'TEST_REQUEST',payload:{hypothesis:h.messageId,repair:{chain:'node'},budgetMinutes:20},sequence:2});
 journal.append(q);
 // the worker claimed the job's backlog task and stopped (process killed): IN_PROGRESS with a live lease
 const job=jobIdFor(taskId,q.messageId);
 const file=path.join(f.state,'teamleader','development-backlog.json');
 const store=new BacklogStore(file,{actor:'android-owner'});
 store.apply([{op:'propose',actor:'android-owner',at:Date.now(),task:{id:jobTaskId(job),title:'repair',kind:'REPAIR',source:'owner',
  priority:900,maxAttempts:3,subject:{base:f.head,chain:'node'}}},{op:'accept',actor:'android-owner',at:Date.now(),id:jobTaskId(job)},
  {op:'claim',actor:'worker',at:Date.now(),id:jobTaskId(job),owner:'worker',token:newToken(),leaseMs:600_000}]);
 const {report}=recover({project:f.root,stateDir:f.state,journal});
 assert.equal(report.pending.status,'INTERRUPTED');
 assert.equal(report.allowedNext,'REQUIRE_OWNER_APPROVAL');
 assert.equal(taskState(journal.entries()).state,'APPROVED');
});
