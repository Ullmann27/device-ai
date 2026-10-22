import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {runMobileCodingJob} from '../../scripts/continuum-mobile-coding.mjs';
import {DialogJournal,dialogKey,validateBody,checkReasonerMessage,dialogState,verifyReceipt,runDialog,CAPABILITY} from './reasoner-dialog.mjs';
import {HypothesisReasoner,indexExports,parseExamples} from './hypothesis-reasoner.mjs';

// The coding job runs the fixture's own test suite; never recurse from inside a repair worktree.
const nested=Boolean(process.env.CONTINUUM_REPAIR_DEPTH);
const MODULE='.github/continuum/math-utils.mjs';

function fixture(){
 const home=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-dialog-'));
 const root=path.join(home,'device-ai'),dir=path.join(root,'.github','continuum');
 fs.mkdirSync(dir,{recursive:true});
 fs.writeFileSync(path.join(dir,'math-utils.mjs'),
  '/** Doubles a number. */\nexport function double(n) { return n * 2; }\n/** Triples a number. */\nexport function triple(n) { return n * 3; }\n');
 fs.writeFileSync(path.join(dir,'math-utils.test.mjs'),"import test from 'node:test';\nimport assert from 'node:assert/strict';\n"+
  "import {triple} from './math-utils.mjs';\ntest('baseline',()=>assert.equal(triple(2),6));\n");
 const git=(...args)=>{const r=spawnSync('git',args,{cwd:root,encoding:'utf8',timeout:15000});assert.equal(r.status,0,r.stderr);return r.stdout.trim();};
 git('init','-q');git('add','.');git('-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','seed');
 return {home,root,git,head:git('rev-parse','HEAD'),state:path.join(home,'dialog-state')};
}
const goal={text:'Finde die Funktion, die eine Zahl verdreifacht: (5) → 15 und (8) → 24.'};
/** Ranks `double` first on purpose so that the dialog has to refute and revise. */
const doubleFirst=candidates=>(_text,i)=>candidates[i].export==='double'?2:1;

test('message bodies are typed, bounded and role-bound',()=>{
 assert.deepEqual(validateBody('PLAN',{steps:['a'],capability:CAPABILITY}),[]);
 assert.ok(validateBody('PLAN',{steps:['a'],capability:'shell'}).length);
 assert.ok(validateBody('TEST_REQUEST',{hypothesis:1,module:'../x.mjs',export:'f',cases:[{args:[1],expected:2}],budgetMinutes:3}).length);
 assert.ok(validateBody('TEST_REQUEST',{hypothesis:1,module:MODULE,export:'f',cases:[{args:['x'.repeat(400)],expected:1}],budgetMinutes:3}).length);
 assert.ok(validateBody('HYPOTHESIS',{claim:'c',module:MODULE,export:'f',basis:'b',extra:1}).length,'unknown keys rejected');
 const s=dialogState([{seq:0,role:'owner',type:'GOAL',body:goal,binding:{}}]);
 const limits={maxRounds:2,budgetMinutes:3};
 assert.match(checkReasonerMessage({type:'RECEIPT',body:{}},s,limits),/only be written by the worker/);
 assert.match(checkReasonerMessage({type:'GOAL',body:goal},s,limits),/only be written by the owner/);
 assert.match(checkReasonerMessage({type:'TEST_REQUEST',body:{hypothesis:0,module:MODULE,export:'triple',cases:[{args:[1],expected:3}],budgetMinutes:3}},s,limits),
  /must reference a HYPOTHESIS/);
 const h={seq:1,role:'reasoner',type:'HYPOTHESIS',body:{claim:'c',module:MODULE,export:'triple',basis:'b'}};
 const s2=dialogState([...s.entries,h]);
 assert.match(checkReasonerMessage({type:'TEST_REQUEST',body:{hypothesis:1,module:MODULE,export:'double',cases:[{args:[1],expected:3}],budgetMinutes:3}},s2,limits),
  /exactly its hypothesis/);
 assert.match(checkReasonerMessage({type:'TEST_REQUEST',body:{hypothesis:1,module:MODULE,export:'triple',cases:[{args:[1],expected:3}],budgetMinutes:9}},s2,limits),
  /owner limit/);
 assert.equal(checkReasonerMessage({type:'TEST_REQUEST',body:{hypothesis:1,module:MODULE,export:'triple',cases:[{args:[1],expected:3}],budgetMinutes:3}},s2,limits),null);
});

test('the journal detects edits, broken chains, wrong roles and forged receipts',()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-journal-'));
 const key=dialogKey(dir),file=path.join(dir,'dialog.jsonl');
 const j=new DialogJournal(file,key);
 j.append('owner','GOAL',goal);
 j.append('worker','RECEIPT',{request:0,verdict:'REFUTED'});
 assert.equal(j.entries().length,2);
 const lines=fs.readFileSync(file,'utf8').split('\n').filter(Boolean);
 fs.writeFileSync(file,[lines[0],lines[1].replace('REFUTED','CONFIRMED')].join('\n')+'\n');
 assert.throws(()=>j.entries(),/was modified/);
 fs.writeFileSync(file,lines[0]+'\n');
 new DialogJournal(file,crypto.randomBytes(32)).append('worker','RECEIPT',{request:0,verdict:'CONFIRMED'});
 assert.throws(()=>j.entries(),/not a genuine worker receipt/,'a receipt written without the runner key is rejected');
 fs.writeFileSync(file,lines[0]+'\n');
 j.append('reasoner','RECEIPT',{request:0,verdict:'CONFIRMED'});
 assert.throws(()=>j.entries(),/wrong role/);
 fs.writeFileSync(file,lines[1]+'\n');
 assert.throws(()=>j.entries(),/chain broken/);
});

test('receipt verification uses real artifacts and the exact order',()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-receipt-'));
 const patch=path.join(dir,'candidate.patch');fs.writeFileSync(patch,'diff --git a/x b/x\nnew file mode 100644\n');
 const sha=crypto.createHash('sha256').update(fs.readFileSync(patch)).digest('hex');
 const casesJson=JSON.stringify([{args:[5],expected:15}]);
 const order={job:'j',commit:'c',module:MODULE,exportName:'triple',casesJson};
 const run=phase=>({phase,exitCode:0,passed:true,executed:phase==='baseline'?1:2,failed:0});
 const good={schema:'continuum-mobile-coding-receipt',version:1,job:'j',commit:'c',approvedForMerge:false,installed:false,apk:null,deviceVerified:false,
  order:{module:MODULE,export:'triple',casesSha256:crypto.createHash('sha256').update(casesJson).digest('hex')},
  status:'CODING_PATCH_VERIFIED_PENDING_REVIEW',patch:{path:patch,sha256:sha,bytes:fs.statSync(patch).size},
  evidence:{verificationSha256:'a'.repeat(64),baselineExecuted:1,candidateExecuted:2,independentExecuted:2,expectedNewTests:1},
  commands:[run('baseline'),run('candidate'),run('independent')]};
 assert.equal(verifyReceipt(good,order).verdict,'CONFIRMED');
 assert.equal(verifyReceipt({...good,patch:{...good.patch,sha256:'0'.repeat(64)}},order).verdict,'INCONCLUSIVE');
 assert.equal(verifyReceipt({...good,commit:'other'},order).verdict,'INCONCLUSIVE');
 assert.equal(verifyReceipt(good,{...order,exportName:'double'}).verdict,'INCONCLUSIVE','answers another order');
 assert.equal(verifyReceipt({...good,approvedForMerge:true},order).verdict,'INCONCLUSIVE');
 assert.equal(verifyReceipt({...good,commands:[run('baseline'),run('candidate')]},order).verdict,'INCONCLUSIVE');
 const failed={...good,status:'FAILED_CODING_CHECKS',patch:null,failure:{code:'CONTRACT_CHECKS_FAILED',message:'GENERATED_TESTS_FAILED'}};
 assert.equal(verifyReceipt(failed,order).verdict,'REFUTED');
 assert.equal(verifyReceipt({...failed,failure:{code:'CONTRACT_CHECKS_FAILED',message:'BASELINE_NOT_GREEN'}},order).verdict,'INCONCLUSIVE');
});

test('goal examples are read from German text and candidates are indexed with arity',()=>{
 assert.deepEqual(parseExamples('(5) → 15 und (8) = 24; aus "ab" wird "AB"'),
  [{args:[5],expected:15},{args:[8],expected:24},{args:['ab'],expected:'AB'}]);
 const f=fixture();
 const index=indexExports(f.root);
 assert.deepEqual(index.map(c=>[c.export,c.required,c.max,c.description]),[['double',1,1,'Doubles a number.'],['triple',1,1,'Triples a number.']]);
});

test('end to end: a refuted hypothesis is revised and the next one is confirmed by the real coding job',{skip:nested,timeout:240000},async()=>{
 const f=fixture();
 const before=fs.readFileSync(path.join(f.root,MODULE));
 const reasoner=new HypothesisReasoner({candidates:indexExports(f.root),scorer:doubleFirst,budgetMinutes:3});
 const r=await runDialog({project:f.root,stateDir:f.state,goal,reasoner,maxRounds:3,budgetMinutes:3});
 assert.equal(r.outcome,'CONFIRMED_PENDING_OWNER_REVIEW',JSON.stringify(r.detail));
 const types=r.state.entries.map(e=>e.type+(e.type==='RECEIPT'||e.type==='CRITIQUE'?':'+e.body.verdict:''));
 assert.deepEqual(types,['GOAL','HYPOTHESIS','PLAN','TEST_REQUEST','RECEIPT:REFUTED','CRITIQUE:REFUTED','REVISE',
  'HYPOTHESIS','PLAN','TEST_REQUEST','RECEIPT:CONFIRMED','CRITIQUE:CONFIRMED']);
 const confirmed=r.state.receipts.at(-1);
 assert.equal(confirmed.binding.commit,f.head);
 assert.deepEqual(confirmed.body.runs.map(x=>[x.phase,x.exitCode]),[['baseline',0],['candidate',0],['independent',0]]);
 assert.match(confirmed.body.patchSha256,/^[0-9a-f]{64}$/);
 assert.deepEqual(fs.readFileSync(path.join(f.root,MODULE)),before,'source checkout unchanged');
 assert.equal(f.git('status','--porcelain'),'');
 assert.equal(new DialogJournal(path.join(f.state,'dialog.jsonl'),dialogKey(f.state)).entries().length,12,'journal verifies');
});

test('a reasoner that misreports a receipt is stopped before anything is recorded as confirmed',{skip:nested,timeout:180000},async()=>{
 const f=fixture();
 const honest=new HypothesisReasoner({candidates:indexExports(f.root),scorer:doubleFirst});
 const liar={async turn(view){
  const open=view.entries.find(e=>e.type==='RECEIPT');
  if(!open)return honest.turn(view);
  return [{type:'CRITIQUE',body:{receipt:open.seq,verdict:'CONFIRMED',reason:'behauptet'}}];
 }};
 const r=await runDialog({project:f.root,stateDir:f.state,goal,reasoner:liar,maxRounds:2,budgetMinutes:3});
 assert.equal(r.outcome,'PROTOCOL_VIOLATION');
 assert.match(r.detail,/contradicts the verified receipt \(REFUTED\)/);
 assert.equal(r.state.outcome,null);
 assert.ok(!r.state.entries.some(e=>e.type==='CRITIQUE'));
});

test('a crash after execution resumes from the journal without running the job twice',{skip:nested,timeout:240000},async()=>{
 const f=fixture();
 const reasoner=new HypothesisReasoner({candidates:indexExports(f.root),scorer:()=>(_t,i)=>i===1?2:1});
 let executions=0;
 const crashing=async order=>{const receipt=await runMobileCodingJob(order);executions++;throw Error('power lost after '+receipt.status);};
 await assert.rejects(runDialog({project:f.root,stateDir:f.state,goal,reasoner,runJob:crashing}),/power lost/);
 const mid=dialogState(new DialogJournal(path.join(f.state,'dialog.jsonl'),dialogKey(f.state)).entries());
 assert.equal(mid.last.type,'TEST_REQUEST');assert.ok(mid.pending);
 const r=await runDialog({project:f.root,stateDir:f.state,goal,reasoner});
 assert.equal(r.outcome,'CONFIRMED_PENDING_OWNER_REVIEW');
 assert.equal(executions,1);
 const receipt=r.state.receipts[0];
 assert.equal(receipt.body.replayed,true,'the resumed request reused the stored job receipt');
 assert.equal(receipt.body.job,mid.pending.binding.job);
});

test('round limit ends in an honest abstention',{skip:nested,timeout:180000},async()=>{
 const f=fixture();
 const reasoner=new HypothesisReasoner({candidates:indexExports(f.root),scorer:doubleFirst});
 const r=await runDialog({project:f.root,stateDir:f.state,goal,reasoner,maxRounds:1,budgetMinutes:3});
 assert.equal(r.outcome,'ABSTAINED');
 assert.deepEqual(r.state.entries.map(e=>e.type).slice(-3),['RECEIPT','CRITIQUE','ABSTAIN']);
 assert.equal(r.state.receipts[0].body.verdict,'REFUTED');
});
