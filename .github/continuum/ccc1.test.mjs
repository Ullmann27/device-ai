// CCC1 runtime tests: schema, journal integrity, capability gate, receipt verifier, recovery and the orchestrator with
// the REAL coding job (canonical backlog, isolated worktrees, independent second run) in a throw-away fixture repo.
// Failure modes required by the owner: kills (SIGKILL at several points, torn journal write, interrupted job),
// manipulated receipts, duplicates, wrong git revisions, unauthorized actions and bounded repair loops.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawnSync,spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {runMobileCodingJob,jobTaskId} from '../../scripts/continuum-mobile-coding.mjs';
import {envelope,envelopeProblems,payloadProblems,PROTOCOL_HASH,CAPABILITY,canonical,sha256,newTaskId} from './ccc1-envelope.mjs';
import {DurableTaskJournal,JournalCorruption,WriterLock,runtimeKey} from './ccc1-journal.mjs';
import {capabilityCheck,gateTestRequest,effectivePermissions,permissionFor} from './ccc1-gate.mjs';
import {verifyJobReceipt,issueVerificationId,checkVerificationId,ALLOWED_PROFILE} from './ccc1-verifier.mjs';
import {jobIdFor,jobStatus,recover} from './ccc1-recovery.mjs';
import {startTask,runTask,ownerAction,taskState,MAX_ENTRIES} from './ccc1-orchestrator.mjs';
import {Ccc1Reasoner,indexExports,interpretGoal} from './ccc1-interpreter.mjs';
import {BacklogStore} from './development-backlog.mjs';

const HERE=path.dirname(fileURLToPath(import.meta.url));
// Worker leases and states default to $HOME; tests never touch the real home of the machine.
process.env.HOME=fs.mkdtempSync(path.join(os.tmpdir(),'ccc1-home-'));
const nested=Boolean(process.env.CONTINUUM_REPAIR_DEPTH);   // never recurse from inside a repair worktree
const MODULE='.github/continuum/math-utils.mjs';
const goal={text:'Finde die Funktion, die eine Zahl verdreifacht: (5) → 15 und (8) → 24.'};

function fixture(){
 const home=fs.mkdtempSync(path.join(os.tmpdir(),'ccc1-'));
 const root=path.join(home,'device-ai'),dir=path.join(root,'.github','continuum');
 fs.mkdirSync(dir,{recursive:true});
 fs.writeFileSync(path.join(dir,'math-utils.mjs'),
  '/** Doubles a number. */\nexport function double(n) { return n * 2; }\n/** Triples a number. */\nexport function triple(n) { return n * 3; }\n');
 fs.writeFileSync(path.join(dir,'math-utils.test.mjs'),"import test from 'node:test';\nimport assert from 'node:assert/strict';\n"+
  "import {triple} from './math-utils.mjs';\ntest('baseline',()=>assert.equal(triple(2),6));\n");
 const git=(...args)=>{const r=spawnSync('git',args,{cwd:root,encoding:'utf8',timeout:15000});assert.equal(r.status,0,r.stderr);return r.stdout.trim();};
 git('init','-q');git('add','.');git('-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','seed');
 return {home,root,git,head:git('rev-parse','HEAD'),state:path.join(home,'ccc1-state')};
}
/** Language core that ranks `double` first, so the dialog has to refute and revise. */
const doubleFirst={id:'double-first',async rank(_g,candidates){return candidates.map(c=>c.export==='double'?2:1);}};
const tripleFirst={id:'triple-first',async rank(_g,candidates){return candidates.map(c=>c.export==='triple'?2:1);}};
const reasonerFor=(f,core=doubleFirst)=>new Ccc1Reasoner({candidates:indexExports(f.root),languageCore:core,budgetMinutes:3});
const start=(f,extra={})=>startTask({project:f.root,stateDir:f.state,goal,repoFullName:'owner/device-ai',...extra});
const journalOf=(f,taskId)=>DurableTaskJournal.forTask(f.state,taskId,runtimeKey(f.state));
const kinds=entries=>entries.map(e=>e.kind+(['RECEIPT','CRITIQUE'].includes(e.kind)?':'+e.payload.verdict:''));
/** Appends an entry the way an attacker with file access but without the runtime key would: valid chain, wrong MAC. */
function forge(file,env){
 const lines=fs.readFileSync(file,'utf8').split('\n').filter(Boolean);
 const record={...env,sequence:lines.length};
 const prevHash=JSON.parse(lines.at(-1)).hash;
 const hash=sha256(canonical({...record,prevHash}));
 fs.appendFileSync(file,JSON.stringify({...record,prevHash,hash,mac:crypto.randomBytes(32).toString('hex')})+'\n');
}
const binding=(over={})=>({sessionId:crypto.randomUUID(),taskId:newTaskId(),repoFullName:'owner/device-ai',baseCommitSha:'a'.repeat(40),
 worktreeId:'main',deadline:Date.now()+600_000,maxRounds:3,maxCpuMillis:600_000,maxMemoryBytes:2e9,permissions:[CAPABILITY],checkpointHash:null,...over});

test('envelopes are strict about fields, roles, versions and payload shapes',()=>{
 const b=binding();
 const g=envelope({binding:b,sender:'OWNER',kind:'GOAL',payload:goal,sequence:0});
 assert.deepEqual(envelopeProblems(g),[]);
 assert.match(envelopeProblems({...g,extra:1})[0],/unknown envelope fields: extra/);
 assert.ok(envelopeProblems({...g,sender:'REASONER'}).some(p=>/REASONER may not send GOAL/.test(p)));
 assert.ok(envelopeProblems({...g,schemaVersion:2}).some(p=>/schemaVersion/.test(p)));
 assert.ok(envelopeProblems({...g,protocolHash:'0'.repeat(64)}).some(p=>/foreign protocol/.test(p)));
 assert.ok(envelopeProblems({...g,baseCommitSha:'main'}).some(p=>/full commit id/.test(p)));
 assert.ok(envelopeProblems({...g,permissions:['shell:rm -rf']}).some(p=>/permissions/.test(p)));
 const r=envelope({binding:b,sender:'REASONER',kind:'RECEIPT',payload:{},sequence:1});
 assert.ok(envelopeProblems(r).some(p=>/REASONER may not send RECEIPT/.test(p)),'only the verifier writes receipts');
 assert.ok(payloadProblems('TEST_REQUEST',{hypothesis:crypto.randomUUID(),module:'../x.mjs',export:'f',cases:[{args:[1],expected:2}],budgetMinutes:3}).length);
 assert.ok(payloadProblems('TEST_REQUEST',{hypothesis:crypto.randomUUID(),module:MODULE,export:'f',cases:[{args:[{}],expected:2}],budgetMinutes:3}).length,
  'objects are outside the contract grammar');
 assert.ok(payloadProblems('PLAN',{steps:['a'],capability:'shell'}).length);
 assert.ok(payloadProblems('ABORT',{reason:'x',code:'BECAUSE'}).length);
 assert.equal(PROTOCOL_HASH.length,64);
});

test('the journal detects edits, forged trusted senders, broken chains and binding changes; duplicates apply once',()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'ccc1-journal-'));
 const key=runtimeKey(dir),file=path.join(dir,'t.jsonl');
 const j=new DurableTaskJournal(file,key);
 const b=binding();
 const g=envelope({binding:b,sender:'OWNER',kind:'GOAL',payload:goal,sequence:0});
 j.append(g);
 const h=envelope({binding:b,sender:'REASONER',kind:'HYPOTHESIS',payload:{claim:'c',module:MODULE,export:'triple',basis:'b'},sequence:1,parentMessageId:g.messageId});
 j.append(h);
 assert.equal(j.append(h).duplicate,true,'identical duplicate is applied once');
 assert.throws(()=>j.append({...h,payload:{...h.payload,export:'double'}}),/conflicting duplicate/);
 assert.equal(j.entries().length,2);
 const lines=fs.readFileSync(file,'utf8').split('\n').filter(Boolean);
 fs.writeFileSync(file,lines[0]+'\n'+lines[1].replace('"triple"','"double"')+'\n');
 assert.throws(()=>j.entries(),/was modified/);
 // a forged VERIFIER entry written with another key
 fs.writeFileSync(file,lines.join('\n')+'\n');
 forge(file,envelope({binding:b,sender:'TEAMLEADER',kind:'ABORT',payload:{reason:'x',code:'OWNER'},sequence:2}));
 assert.throws(()=>j.entries(),/without a valid runtime MAC/);
 fs.writeFileSync(file,lines[1]+'\n');
 assert.throws(()=>j.entries(),/chain broken|starts with the owner GOAL/);
 fs.writeFileSync(file,lines.join('\n')+'\n');
 assert.throws(()=>j.append(envelope({binding:{...b,baseCommitSha:'b'.repeat(40)},sender:'REASONER',kind:'ABSTAIN',payload:{reason:'x'},sequence:2})),
  /changes the task binding baseCommitSha/,'a message for another commit cannot join the task');
 // torn tail: detected, reported, truncated exactly
 fs.appendFileSync(file,'{"schemaVersion":1,"sess');
 assert.throws(()=>j.entries(),JournalCorruption);
 assert.equal(j.truncateTornTail().bytes,24);
 assert.equal(j.entries().length,2);
 // writer lock: one writer per worktree; a dead writer's lock is stale
 const lock=new WriterLock(dir,'main');
 assert.equal(lock.acquire().acquired,true);
 assert.equal(new WriterLock(dir,'main').acquire().acquired,false);
 lock.release();
 fs.writeFileSync(path.join(dir,'locks','main.lock'),JSON.stringify({pid:2**22+12345,host:os.hostname(),at:new Date().toISOString()}));
 assert.deepEqual(new WriterLock(dir,'main').acquire(),{acquired:true,stale:true});
});

test('the gate allows only owner-granted, critic-approved requests within deadline, rounds and budget',()=>{
 const b=binding({permissions:[permissionFor(MODULE)]});
 const g=envelope({binding:b,sender:'OWNER',kind:'GOAL',payload:goal,sequence:0});
 const req={hypothesis:crypto.randomUUID(),module:MODULE,export:'triple',cases:[{args:[5],expected:15}],budgetMinutes:3};
 assert.equal(gateTestRequest(req,[g],{rounds:0,cpuMillis:0}).decision,'ALLOW');
 const other={...req,module:'.github/continuum/other-utils.mjs'};
 assert.deepEqual(gateTestRequest(other,[g],{rounds:0,cpuMillis:0}),{decision:'NEEDS_APPROVAL',reasons:['module not covered by the owner grant'],
  needs:[permissionFor(other.module)]});
 const guarded={...req,module:'.github/continuum/repair-critic.mjs',export:'critique'};
 const d=gateTestRequest(guarded,[g],{rounds:0,cpuMillis:0});
 assert.equal(d.decision,'DENY');
 assert.match(d.reasons.join(';'),/outside the worker capability: protected path/,'even the owner cannot grant tests on the agent\'s own judges');
 assert.equal(capabilityCheck(MODULE,'triple',JSON.stringify([{args:[5],expected:15}])).allowed,true);
 assert.match(gateTestRequest(req,[g],{rounds:3,cpuMillis:0}).reasons.join(),/round limit 3/);
 assert.match(gateTestRequest(req,[g],{rounds:0,cpuMillis:600_000}).reasons.join(),/CPU budget/);
 assert.match(gateTestRequest(req,[g],{rounds:0,cpuMillis:0,now:b.deadline+1}).reasons.join(),/deadline passed/);
 assert.match(gateTestRequest({...req,budgetMinutes:30},[{...g,deadline:Date.now()+60_000}],{rounds:0,cpuMillis:0}).reasons.join(),/remaining time/);
 // permissions widen only through OWNER approvals
 const ar=envelope({binding:b,sender:'TEAMLEADER',kind:'APPROVAL_REQUEST',payload:{request:req.hypothesis,reason:'r',needs:[permissionFor(other.module)]},sequence:1});
 const ok=envelope({binding:b,sender:'OWNER',kind:'APPROVAL',payload:{approvalRequest:ar.messageId,decision:'GRANTED'},sequence:2});
 assert.ok(effectivePermissions([g,ar,ok]).has(permissionFor(other.module)));
 assert.ok(!effectivePermissions([g,ar]).has(permissionFor(other.module)));
});

test('the verifier checks a receipt against the real patch bytes, runs, profile and the exact order',async t=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'ccc1-receipt-'));
 t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
 const patch=path.join(dir,'candidate.patch');fs.writeFileSync(patch,'diff --git a/x b/x\nnew file mode 100644\n');
 const casesJson=JSON.stringify([{args:[5],expected:15}]);
 const order={job:'j',commit:'c',module:MODULE,exportName:'triple',casesJson};
 const run=phase=>({phase,exitCode:0,passed:true,executed:phase==='baseline'?1:2,failed:0,timedOut:false,command:ALLOWED_PROFILE});
 const good={schema:'continuum-mobile-coding-receipt',version:1,job:'j',commit:'c',approvedForMerge:false,installed:false,apk:null,deviceVerified:false,
  order:{module:MODULE,export:'triple',casesSha256:sha256(casesJson)},toolchain:{node:'v20.0.0'},
  status:'CODING_PATCH_VERIFIED_PENDING_REVIEW',patch:{path:patch,sha256:sha256(fs.readFileSync(patch)),bytes:fs.statSync(patch).size},
  evidence:{verificationSha256:'a'.repeat(64),baselineExecuted:1,candidateExecuted:2,independentExecuted:2,expectedNewTests:1},
  commands:[run('baseline'),run('candidate'),run('independent')]};
 assert.equal(verifyJobReceipt(good,order).verdict,'CONFIRMED');
 const bad=[
  ['patch bytes',{...good,patch:{...good.patch,sha256:'0'.repeat(64)}}],
  ['other job',{...good,job:'k'}],
  ['other commit',{...good,commit:'d'}],
  ['claims a merge',{...good,approvedForMerge:true}],
  ['two runs only',{...good,commands:[run('baseline'),run('candidate')]}],
  ['foreign command',{...good,commands:[run('baseline'),run('candidate'),{...run('independent'),command:'bash -c "true"'}]}],
  ['fewer independent tests',{...good,evidence:{...good.evidence,independentExecuted:1}}],
  ['no toolchain',{...good,toolchain:null}],
 ];
 for(const [what,r] of bad)assert.equal(verifyJobReceipt(r,order).verdict,'INCONCLUSIVE',what);
 const mutations=[
  ['baseline execution exceeds its summary',r=>{r.commands[0].executed=2;}],
  ['candidate execution is below its summary',r=>{r.commands[1].executed=1;}],
  ['independent execution is below its summary',r=>{r.commands[2].executed=1;}],
  ['summary changes candidate count without command evidence',r=>{r.evidence.candidateExecuted=3;r.evidence.independentExecuted=3;r.commands[2].executed=3;}],
  ['independent command drops candidate tests',r=>{r.commands[1].executed=3;}],
  ['string command counts',r=>{for(const x of r.commands)x.executed=String(x.executed);}],
  ['fractional command counts',r=>{for(const x of r.commands)x.executed+=0.5;}],
  ['unsafe command counts',r=>{for(const x of r.commands)x.executed=Number.MAX_SAFE_INTEGER+1;}],
  ['expected tests cannot be zero',r=>{r.evidence.expectedNewTests=0;r.evidence.candidateExecuted=1;r.evidence.independentExecuted=1;r.commands[1].executed=1;r.commands[2].executed=1;}],
  ['expected tests must match the ordered cases',r=>{r.evidence.expectedNewTests=2;r.evidence.candidateExecuted=3;r.evidence.independentExecuted=3;r.commands[1].executed=3;r.commands[2].executed=3;}],
  ['null command entry',r=>{r.commands[1]=null;}],
  ['commands are not an array',r=>{r.commands={};}],
  ['missing timeout completion evidence',r=>{delete r.commands[1].timedOut;}],
 ];
 for(const [what,mutate] of mutations)await t.test(what,()=>{
  const r=structuredClone(good);mutate(r);
  assert.equal(verifyJobReceipt(r,order).verdict,'INCONCLUSIVE');
 });
 await t.test('additional candidate and independent coverage is accepted',()=>{
  const r=structuredClone(good);r.commands[1].executed=3;r.commands[2].executed=4;
  r.evidence.candidateExecuted=3;r.evidence.independentExecuted=4;
  assert.equal(verifyJobReceipt(r,order).verdict,'CONFIRMED');
 });
 assert.equal(verifyJobReceipt(good,{...order,exportName:'double'}).verdict,'INCONCLUSIVE','answers another order');
 const failed={...good,status:'FAILED_CODING_CHECKS',patch:null,failure:{message:'GENERATED_TESTS_FAILED'}};
 assert.equal(verifyJobReceipt(failed,order).verdict,'REFUTED');
 assert.equal(verifyJobReceipt({...failed,failure:{message:'BASELINE_NOT_GREEN'}},order).verdict,'INCONCLUSIVE');
 const key=crypto.randomBytes(32),fields={request:crypto.randomUUID(),receiptSha256:'b'.repeat(64),verdict:'CONFIRMED'};
 const id=issueVerificationId(key,fields);
 assert.equal(checkVerificationId(key,id,fields),true);
 assert.equal(checkVerificationId(key,id,{...fields,verdict:'REFUTED'}),false);
 assert.equal(checkVerificationId(crypto.randomBytes(32),id,fields),false);
});

test('the interpreter abstains without verifiable examples and reads them from German text',()=>{
 assert.equal(interpretGoal({text:'Mach den Code schneller.'}).abstain,true);
 const s=interpretGoal(goal);
 assert.equal(s.abstain,false);
 assert.deepEqual(s.acceptance,[{args:[5],expected:15},{args:[8],expected:24}]);
 assert.equal(interpretGoal({text:'Liste: ([1,2]) → 3'}).abstain,true,'non-primitive examples are not testable');
});

test('end to end: refute, revise, confirm with the real coding job; source untouched',{skip:nested,timeout:240000},async()=>{
 const f=fixture();
 const before=fs.readFileSync(path.join(f.root,MODULE));
 const taskId=start(f);
 const r=await runTask({project:f.root,stateDir:f.state,taskId,reasoner:reasonerFor(f)});
 assert.equal(r.outcome,'COMPLETED',JSON.stringify(kinds(r.entries)));
 assert.deepEqual(kinds(r.entries),['GOAL','HYPOTHESIS','PLAN','TEST_REQUEST','RECEIPT:REFUTED','CRITIQUE:REFUTED','REVISE',
  'HYPOTHESIS','PLAN','TEST_REQUEST','RECEIPT:CONFIRMED','CRITIQUE:CONFIRMED']);
 const confirmed=r.entries.filter(e=>e.kind==='RECEIPT').at(-1);
 assert.deepEqual(confirmed.payload.runs.map(x=>[x.phase,x.exitCode]),[['baseline',0],['candidate',0],['independent',0]]);
 assert.ok(checkVerificationId(runtimeKey(f.state),confirmed.verificationReceiptId,
  {request:confirmed.payload.request,receiptSha256:confirmed.payload.receiptSha256,verdict:'CONFIRMED'}));
 assert.deepEqual(fs.readFileSync(path.join(f.root,MODULE)),before,'source checkout unchanged');
 assert.equal(f.git('status','--porcelain'),'');
 assert.equal(journalOf(f,taskId).entries().length,12,'journal verifies');
});

test('a reasoner that contradicts its receipt fails the task; nothing is recorded as confirmed',{skip:nested,timeout:180000},async()=>{
 const f=fixture();
 const honest=reasonerFor(f);
 const liar={async turn(view){
  const open=view.entries.find(e=>e.kind==='RECEIPT');
  if(!open)return honest.turn(view);
  return [{messageId:crypto.randomUUID(),parentMessageId:open.messageId,kind:'CRITIQUE',payload:{receipt:open.messageId,verdict:'CONFIRMED',reason:'behauptet'}}];
 }};
 const taskId=start(f);
 const r=await runTask({project:f.root,stateDir:f.state,taskId,reasoner:liar});
 assert.equal(r.outcome,'FAILED');
 const abort=r.entries.at(-1);
 assert.equal(abort.kind,'ABORT');assert.equal(abort.payload.code,'PROTOCOL_VIOLATION');
 assert.match(abort.payload.reason,/contradicts the verified receipt \(REFUTED\)/);
 assert.ok(!r.entries.some(e=>e.kind==='CRITIQUE'));
});

test('manipulated job receipts never confirm; a receipt forged into the journal stops the task',{skip:nested,timeout:180000},async()=>{
 const f=fixture();
 const tamper=async order=>{const receipt=await runMobileCodingJob(order);return receipt.patch?{...receipt,patch:{...receipt.patch,sha256:'0'.repeat(64)}}:receipt;};
 const taskId=start(f,{maxRounds:1});
 const r=await runTask({project:f.root,stateDir:f.state,taskId,reasoner:reasonerFor(f,tripleFirst),runJob:tamper});
 assert.equal(r.outcome,'ABSTAINED');
 const receipt=r.entries.find(e=>e.kind==='RECEIPT');
 assert.equal(receipt.payload.verdict,'INCONCLUSIVE');
 assert.match(receipt.payload.reasons.join(),/patch bytes do not match/);
 // an attacker with file access but without the runtime key appends a CONFIRMED receipt
 const f2=fixture();
 const t2=start(f2);
 const j=journalOf(f2,t2);
 const g=j.entries()[0];
 const {prevHash,hash,mac,...goalEnv}=g;
 forge(j.file,envelope({binding:goalEnv,sender:'VERIFIER',kind:'RECEIPT',sequence:1,
  payload:{request:g.messageId,job:'x',status:'CODING_PATCH_VERIFIED_PENDING_REVIEW',verdict:'CONFIRMED',reasons:[],receiptSha256:'c'.repeat(64),
   patchSha256:null,evidenceSha256:null,runs:[],replayed:false,execution:'EXECUTED',cost:{cpuMillis:0,wallMillis:0}},verificationReceiptId:'d'.repeat(64)}));
 const r2=await runTask({project:f2.root,stateDir:f2.state,taskId:t2,reasoner:reasonerFor(f2)});
 assert.equal(r2.outcome,'STOPPED_CORRUPT_JOURNAL');
 assert.match(r2.report.corruption,/without a valid runtime MAC/);
});

test('duplicate deliveries are applied once; a conflicting duplicate is a protocol violation',{skip:nested,timeout:180000},async()=>{
 const f=fixture();
 const inner=reasonerFor(f,tripleFirst);
 const twice={async turn(view){const m=await inner.turn(view);return m.flatMap(x=>x.kind==='TEST_REQUEST'?[x]:[x,x]);}};
 const taskId=start(f);
 const r=await runTask({project:f.root,stateDir:f.state,taskId,reasoner:twice});
 assert.equal(r.outcome,'COMPLETED');
 assert.equal(new Set(r.entries.map(e=>e.messageId)).size,r.entries.length);
 assert.equal(r.entries.filter(e=>e.kind==='TEST_REQUEST').length,1);
 const f2=fixture();
 const conflicting={async turn(view){
  const m=await inner.turn(view);
  return [m[0],{...m[0],payload:{...m[0].payload,claim:'anders'}}];
 }};
 const t2=start(f2);
 const r2=await runTask({project:f2.root,stateDir:f2.state,taskId:t2,reasoner:conflicting});
 assert.equal(r2.outcome,'FAILED');
 assert.match(r2.entries.at(-1).payload.reason,/conflicting duplicate/);
});

test('a moved checkout aborts the task as STALE_BASE before anything runs',{skip:nested,timeout:120000},async()=>{
 const f=fixture();
 const taskId=start(f);
 fs.writeFileSync(path.join(f.root,'NOTE.md'),'neu\n');
 f.git('add','.');f.git('-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','moved');
 const r=await runTask({project:f.root,stateDir:f.state,taskId,reasoner:reasonerFor(f)});
 assert.equal(r.outcome,'ABORTED');
 assert.equal(r.entries.at(-1).payload.code,'STALE_BASE');
 assert.equal(r.entries.filter(e=>e.kind==='TEST_REQUEST').length,0);
});

test('unauthorized actions: protected modules fail the task; ungranted modules wait for the owner',{skip:nested,timeout:240000},async()=>{
 const f=fixture();
 const evil={async turn(view){
  const h=crypto.randomUUID();
  return [{messageId:h,kind:'HYPOTHESIS',payload:{claim:'c',module:'.github/continuum/repair-critic.mjs',export:'critique',basis:'b'}},
   {messageId:crypto.randomUUID(),parentMessageId:h,kind:'TEST_REQUEST',payload:{hypothesis:h,module:'.github/continuum/repair-critic.mjs',export:'critique',
    cases:[{args:[1],expected:true}],budgetMinutes:3}}];
 }};
 const t1=start(f);
 const r1=await runTask({project:f.root,stateDir:f.state,taskId:t1,reasoner:evil});
 assert.equal(r1.outcome,'FAILED');
 assert.equal(r1.entries.at(-1).payload.code,'CAPABILITY_DENIED');
 // grant only another module: the request needs an owner approval; DENIED aborts, GRANTED runs
 for(const decision of ['DENIED','GRANTED']){
  const g=fixture();
  const t=startTask({project:g.root,stateDir:g.state,goal,repoFullName:'owner/device-ai',permissions:[permissionFor('.github/continuum/other.mjs')]});
  const waiting=await runTask({project:g.root,stateDir:g.state,taskId:t,reasoner:reasonerFor(g,tripleFirst)});
  assert.equal(waiting.outcome,'WAITING_FOR_OWNER');
  const ask=waiting.entries.find(e=>e.kind==='APPROVAL_REQUEST');
  assert.deepEqual(ask.payload.needs,[permissionFor(MODULE)]);
  ownerAction({stateDir:g.state,taskId:t,kind:'APPROVAL',payload:{approvalRequest:ask.messageId,decision}});
  const done=await runTask({project:g.root,stateDir:g.state,taskId:t,reasoner:reasonerFor(g,tripleFirst)});
  assert.equal(done.outcome,decision==='GRANTED'?'COMPLETED':'ABORTED');
  if(decision==='DENIED')assert.equal(done.entries.at(-1).payload.code,'APPROVAL_DENIED');
  assert.throws(()=>ownerAction({stateDir:g.state,taskId:t,kind:'APPROVAL',payload:{approvalRequest:ask.messageId,decision:'GRANTED'}}),/already ended|already answered/);
 }
});

test('repair loops are bounded: the round limit ends in an abstention, the turn limit in a pause',{skip:nested,timeout:180000},async()=>{
 const f=fixture();
 const t=start(f,{maxRounds:1});
 const r=await runTask({project:f.root,stateDir:f.state,taskId:t,reasoner:reasonerFor(f)});
 assert.equal(r.outcome,'ABSTAINED');
 assert.deepEqual(kinds(r.entries).slice(-3),['RECEIPT:REFUTED','CRITIQUE:REFUTED','ABSTAIN']);
 const f2=fixture();
 const t2=start(f2);
 const chatty={async turn(){return [{messageId:crypto.randomUUID(),kind:'PLAN',payload:{steps:['nachdenken'],capability:CAPABILITY}}];}};
 const r2=await runTask({project:f2.root,stateDir:f2.state,taskId:t2,reasoner:chatty,maxTurns:5});
 assert.equal(r2.outcome,'TURN_LIMIT_REACHED');
 assert.equal(r2.entries.length,6);
 const r3=await runTask({project:f2.root,stateDir:f2.state,taskId:t2,reasoner:chatty,maxTurns:500});
 assert.equal(r3.outcome,'ABORTED','talking forever is not possible');
 assert.equal(r3.entries.length,MAX_ENTRIES+1);
 assert.equal(r3.entries.at(-1).payload.code,'BUDGET');
});

/** Runs one runTask in a child process that is SIGKILLed at the crash point (or by the job poller). */
function childRun(f,taskId,env){
 const script=path.join(f.home,'child.mjs');
 fs.writeFileSync(script,`
import {runTask} from ${JSON.stringify(path.join(HERE,'ccc1-orchestrator.mjs'))};
import {Ccc1Reasoner,indexExports} from ${JSON.stringify(path.join(HERE,'ccc1-interpreter.mjs'))};
import {runMobileCodingJob} from ${JSON.stringify(path.join(HERE,'..','..','scripts','continuum-mobile-coding.mjs'))};
const [root,state,taskId,mode]=process.argv.slice(2);
const core={id:'triple-first',async rank(_g,c){return c.map(x=>x.export==='triple'?2:1);}};
const reasoner=new Ccc1Reasoner({candidates:indexExports(root),languageCore:core,budgetMinutes:3});
const r=await runTask({project:root,stateDir:state,taskId,reasoner});
console.log(r.outcome);
`);
 return new Promise(resolve=>{
  const c=spawn(process.execPath,[script,f.root,f.state,taskId,env.MODE??'normal'],{env:{...process.env,...env},stdio:['ignore','pipe','pipe']});
  let out='';c.stdout.on('data',d=>out+=d);c.stderr.on('data',d=>out+=d);
  // The Teamleader cycle runs synchronously inside the child, so the kill comes from here: as soon as the job's
  // backlog task is claimed (IN_PROGRESS) the child dies like a phone that loses power mid-job.
  const timer=env.MODE==='kill-during-job'?setInterval(()=>{
   try{const b=JSON.parse(fs.readFileSync(path.join(f.state,'teamleader','development-backlog.json'),'utf8'));
    if(b.tasks?.some(t=>t.state==='IN_PROGRESS'))c.kill('SIGKILL');}catch{}
  },3):null;
  c.on('exit',(code,signal)=>{if(timer)clearInterval(timer);resolve({code,signal,out});});
 });
}

test('kills: SIGKILL before or after execution and during a journal write resume without a second execution',{skip:nested,timeout:300000},async()=>{
 for(const point of ['before-execute','after-execute','torn-RECEIPT']){
  const f=fixture();
  const taskId=start(f);
  const killed=await childRun(f,taskId,{CCC1_CRASH_AT:point,CCC1_CRASH_N:'1'});
  assert.equal(killed.signal,'SIGKILL',point+': '+killed.out);
  const mid=journalOf(f,taskId);
  if(point==='torn-RECEIPT')assert.throws(()=>mid.entries(),JournalCorruption,'a torn line is detected');
  const pending=taskState(mid.entries({allowTorn:true})).pending;
  assert.ok(pending,point+': a request is pending');
  const job=jobIdFor(taskId,pending.messageId);
  assert.equal(jobStatus(f.state,job),point==='before-execute'?'NOT_STARTED':'FINISHED_UNRECORDED');
  const r=await runTask({project:f.root,stateDir:f.state,taskId,reasoner:reasonerFor(f,tripleFirst)});
  assert.equal(r.outcome,'COMPLETED',point);
  const receipts=r.entries.filter(e=>e.kind==='RECEIPT');
  assert.equal(receipts.length,1,point+': one receipt for one request');
  assert.equal(receipts[0].payload.execution,point==='before-execute'?'EXECUTED':'REPLAYED',point);
  if(point!=='before-execute')assert.equal(receipts[0].payload.replayed,true,point+': the stored job receipt was reused');
  if(point==='torn-RECEIPT')assert.ok(r.report.tornTail,'recovery truncated the torn tail');
 }
});

test('an interrupted job is never repeated without the owner; with approval the task still ends verified',{skip:nested,timeout:300000},async()=>{
 const f=fixture();
 const taskId=start(f);
 const killed=await childRun(f,taskId,{MODE:'kill-during-job'});
 assert.equal(killed.signal,'SIGKILL',killed.out);
 const pending=taskState(journalOf(f,taskId).entries()).pending;
 const job=jobIdFor(taskId,pending.messageId);
 assert.equal(jobStatus(f.state,job),'INTERRUPTED');
 // make the lease of the interrupted claim look expired: the coding job alone would now silently run again
 const file=path.join(f.state,'teamleader','development-backlog.json');
 const store=new BacklogStore(file,{actor:'test'});
 assert.equal(store.read().doc.tasks.find(t=>t.id===jobTaskId(job)).state,'IN_PROGRESS');
 const waiting=await runTask({project:f.root,stateDir:f.state,taskId,reasoner:reasonerFor(f,tripleFirst)});
 assert.equal(waiting.outcome,'WAITING_FOR_OWNER');
 const ask=waiting.entries.filter(e=>e.kind==='APPROVAL_REQUEST').at(-1);
 assert.match(ask.payload.needs[0],/^retry-after-interruption:/);
 assert.equal(recover({project:f.root,stateDir:f.state,journal:journalOf(f,taskId)}).report.allowedNext,'REQUIRE_OWNER_APPROVAL');
 ownerAction({stateDir:f.state,taskId,kind:'APPROVAL',payload:{approvalRequest:ask.messageId,decision:'GRANTED'}});
 // The killed run still holds its locks, which by design are only taken over after a timeout (backlog 60 s,
 // Teamleader 5 min). Until then every call waits for the worker; nothing is executed or claimed meanwhile.
 // "Time passes" is simulated by ageing exactly those lock files; nothing else is touched.
 const backlogLock=path.join(f.state,'teamleader','development-backlog.json.lock');
 const leaderLock=path.join(f.state,'teamleader','teamleader-state.json.lock');
 const age=()=>{
  if(fs.existsSync(backlogLock)){const t=new Date(Date.now()-120_000);fs.utimesSync(backlogLock,t,t);}
  if(fs.existsSync(leaderLock)){const l=JSON.parse(fs.readFileSync(leaderLock,'utf8'));fs.writeFileSync(leaderLock,JSON.stringify({...l,at:l.at-6*60_000}));}
 };
 const waits=[];
 let r;
 for(let attempt=0;attempt<4;attempt++){
  r=await runTask({project:f.root,stateDir:f.state,taskId,reasoner:reasonerFor(f,tripleFirst)});
  if(r.outcome!=='WAITING_FOR_WORKER')break;
  waits.push(r.reason);
  assert.ok(taskState(r.entries).pending||taskState(r.entries).state==='APPROVED','the request stays pending while waiting');
  age();
 }
 assert.ok(waits.length>=1&&waits.every(w=>/LOCK_TIMEOUT|Teamleader already running|lock requires recovery/.test(w)),JSON.stringify(waits));
 assert.match(r.entries.find(e=>e.kind==='RECEIPT').payload.reasons.join(),/build lease: (released \(holder pid \d+ no longer exists\)|NO_LEASE)/);
 r=r??await runTask({project:f.root,stateDir:f.state,taskId,reasoner:reasonerFor(f,tripleFirst)});
 assert.equal(r.outcome,'COMPLETED',JSON.stringify(r.entries.filter(e=>e.kind==='RECEIPT').map(e=>[e.payload.status,e.payload.reasons])));
 assert.deepEqual(kinds(r.entries).filter(k=>k.startsWith('RECEIPT')),['RECEIPT:INCONCLUSIVE','RECEIPT:CONFIRMED']);
 assert.equal(r.entries.find(e=>e.kind==='RECEIPT').payload.execution,'INTERRUPTED');
 assert.equal(store.read().doc.tasks.find(t=>t.id===jobTaskId(job)).state,'ABORTED','the interrupted job was closed, not repeated');
});
