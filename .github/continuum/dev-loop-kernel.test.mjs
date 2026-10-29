import test from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import {createCycle,applyEvent,verifyJournal,creditOutcome,thompsonRank,frozenRank,emptyRegistry,preregister,
  recordHoldoutRun,mcnemarOneSided,mineWeaknesses,betaSample,__test} from './dev-loop-kernel.mjs';
import {recordSession,emptyLedger} from './repair-ledger.mjs';

const BASE='a'.repeat(40), PATCH='b'.repeat(64), OTHER='c'.repeat(64);
const h=s=>crypto.createHash('sha256').update(s).digest('hex');
const cycle=(o={})=>createCycle({id:'cyc-1',base:BASE,branch:'continuum/fix-1',goal:'Repair the failing worker test',
  capabilities:['read_repo','write_worktree','run_tests','open_draft_pr'],deadlineMs:10_000,...o});
const tests=(failed=0,complete=true)=>({total:20,executed:20,failed,evidenceComplete:complete});
let t=0;const ev=(type,actor,evidence={})=>({type,actor,at:++t,evidence});

function happyPath(){
  t=0;let c=cycle();
  c=applyEvent(c,ev('DIAGNOSE','agent',{baseSha:BASE,kinds:['JS_TEST_FAILURE']}));
  c=applyEvent(c,ev('PLAN','agent',{strategy:'GUARD_NULL'}));
  c=applyEvent(c,ev('PATCH','agent',{patchSha256:PATCH,branch:'continuum/fix-1',files:1,worktree:'wt-local'}));
  c=applyEvent(c,ev('LOCAL_TESTS','agent',{patchSha256:PATCH,tests:tests()}));
  c=applyEvent(c,ev('INDEPENDENT_VERIFY','verifier',{worktree:'wt-second',baseSha:BASE,patchSha256:PATCH,tests:tests()}));
  c=applyEvent(c,ev('CRITIQUE','agent',{accepted:true}));
  c=applyEvent(c,ev('OPEN_REVIEW','agent',{pullRequest:241,headPatchSha256:PATCH,targetBranch:'continuum/fix-1'}));
  return c;
}

test('a full cycle ends in an owner decision with an intact hash-chained journal',()=>{
  let c=happyPath();
  assert.equal(c.state,'REVIEW_OPEN');
  c=applyEvent(c,ev('OWNER_DECISION','owner',{accept:true}));
  assert.equal(c.state,'OWNER_ACCEPTED');
  assert.deepEqual(verifyJournal(c.journal),{entries:9,state:'OWNER_ACCEPTED',head:c.journal.at(-1).hash});
  assert.deepEqual(creditOutcome(c),{kind:'JS_TEST_FAILURE|GUARD_NULL',status:'COMPILES'});
});

test('the agent never targets a protected branch, never merges and never widens its capabilities',()=>{
  assert.throws(()=>cycle({branch:'main'}),/PROTECTED_OR_INVALID_BRANCH/);
  assert.throws(()=>cycle({branch:'release/1.0'}),/PROTECTED_OR_INVALID_BRANCH/);
  assert.throws(()=>cycle({capabilities:['push_main']}),/UNKNOWN_CAPABILITY/);
  const c=happyPath();
  assert.throws(()=>applyEvent(c,ev('MERGE','agent',{})),/AGENT_MERGE_REFUSED/);
  assert.throws(()=>applyEvent(c,ev('OWNER_DECISION','agent',{accept:true})),/ONLY_THE_OWNER_DECIDES/);
  t=0;const narrow=cycle({capabilities:['read_repo']});
  assert.throws(()=>applyEvent(narrow,ev('DIAGNOSE','agent',{baseSha:BASE,kinds:['X'],capabilities:['write_worktree']})),
    /CAPABILITY_ESCALATION_REFUSED/);
  assert.throws(()=>applyEvent(narrow,ev('DIAGNOSE','agent',{baseSha:BASE,kinds:['X'],targetBranch:'main'})),/PROTECTED_BRANCH_WRITE_REFUSED/);
});

test('an own test is never an independent verification, and evidence must match base and patch',()=>{
  t=0;let c=cycle();
  c=applyEvent(c,ev('DIAGNOSE','agent',{baseSha:BASE,kinds:['K']}));
  c=applyEvent(c,ev('PLAN','agent',{strategy:'S'}));
  c=applyEvent(c,ev('PATCH','agent',{patchSha256:PATCH,branch:'continuum/fix-1',files:1,worktree:'wt-local'}));
  assert.throws(()=>applyEvent(c,ev('LOCAL_TESTS','agent',{patchSha256:OTHER,tests:tests()})),/TESTED_A_DIFFERENT_PATCH/);
  assert.throws(()=>applyEvent(c,ev('LOCAL_TESTS','agent',{patchSha256:PATCH,tests:{total:0,executed:0,failed:0}})),/TESTS_WITHOUT_EXECUTED_EVIDENCE/);
  c=applyEvent(c,ev('LOCAL_TESTS','agent',{patchSha256:PATCH,tests:tests()}));
  const v={worktree:'wt-second',baseSha:BASE,patchSha256:PATCH,tests:tests()};
  assert.throws(()=>applyEvent(c,ev('INDEPENDENT_VERIFY','agent',v)),/SELF_VERIFICATION_REFUSED/);
  assert.throws(()=>applyEvent(c,ev('INDEPENDENT_VERIFY','verifier',{...v,worktree:'wt-local'})),/VERIFIED_IN_THE_SAME_WORKTREE/);
  assert.throws(()=>applyEvent(c,ev('INDEPENDENT_VERIFY','verifier',{...v,patchSha256:OTHER})),/VERIFIED_SOMETHING_ELSE/);
  assert.throws(()=>applyEvent(c,ev('INDEPENDENT_VERIFY','verifier',{...v,tests:tests(0,false)})),/INCOMPLETE_TEST_EVIDENCE/);
  assert.throws(()=>applyEvent(c,ev('INDEPENDENT_VERIFY','verifier',{...v,tests:tests(2)})),/INDEPENDENT_TESTS_FAILED/);
});

test('red local tests cannot reach independent verification; budget and deadline stop the loop',()=>{
  t=0;let c=cycle({maxAttempts:1});
  c=applyEvent(c,ev('DIAGNOSE','agent',{baseSha:BASE,kinds:['K']}));
  c=applyEvent(c,ev('PLAN','agent',{strategy:'S'}));
  c=applyEvent(c,ev('PATCH','agent',{patchSha256:PATCH,branch:'continuum/fix-1',files:1,worktree:'wt-local'}));
  c=applyEvent(c,ev('LOCAL_TESTS','agent',{patchSha256:PATCH,tests:tests(3)}));
  assert.throws(()=>applyEvent(c,ev('INDEPENDENT_VERIFY','verifier',{worktree:'w2',baseSha:BASE,patchSha256:PATCH,tests:tests()})),
    /LOCAL_TESTS_NOT_GREEN/);
  assert.throws(()=>applyEvent(c,ev('PATCH','agent',{patchSha256:OTHER,branch:'continuum/fix-1',files:1,worktree:'wt-local'})),
    /ATTEMPT_BUDGET_EXHAUSTED/);
  const late={type:'PLAN',actor:'agent',at:20_000,evidence:{strategy:'S'}};
  t=0;const d=applyEvent(cycle(),ev('DIAGNOSE','agent',{baseSha:BASE,kinds:['K']}));
  assert.throws(()=>applyEvent(d,late),/DEADLINE_EXCEEDED/);
  assert.throws(()=>applyEvent(d,{type:'PLAN',actor:'agent',at:0,evidence:{strategy:'S'}}),/NON_MONOTONIC_TIME/);
});

test('abort and rollback work from the middle; rollback must restore the base; terminal states are final',()=>{
  let c=happyPath();
  assert.throws(()=>applyEvent(c,ev('ROLLBACK','agent',{restoredSha:'d'.repeat(40)})),/ROLLBACK_NOT_TO_BASE/);
  const rolled=applyEvent(c,ev('ROLLBACK','agent',{restoredSha:BASE}));
  assert.equal(rolled.state,'ROLLED_BACK');
  assert.equal(creditOutcome(rolled).status,'NEUTRAL');
  const aborted=applyEvent(happyPath(),ev('ABORT','owner',{}));
  assert.equal(aborted.state,'ABORTED');
  assert.throws(()=>applyEvent(aborted,ev('PLAN','agent',{strategy:'S'})),/CYCLE_ALREADY_TERMINAL/);
  t=0;assert.throws(()=>applyEvent(cycle(),ev('PATCH','agent',{patchSha256:PATCH,branch:'continuum/fix-1',files:1})),/ILLEGAL_TRANSITION/);
});

test('a tampered, reordered or truncated journal is detected after a crash',()=>{
  const c=happyPath();
  const forged=structuredClone(c.journal);forged[4].evidence.patchSha256=OTHER;
  assert.throws(()=>verifyJournal(forged),/JOURNAL_TAMPERED/);
  const gap=[c.journal[0],...c.journal.slice(2)];
  assert.throws(()=>verifyJournal(gap),/JOURNAL_GAP/);
  assert.throws(()=>verifyJournal([]),/JOURNAL_WITHOUT_GENESIS/);
  assert.equal(verifyJournal(c.journal.slice(0,3)).entries,3);   // a prefix is a valid earlier state
  assert.ok(!JSON.stringify(c.journal).includes('Repair the failing'));   // the goal text is stored as a digest
});

test('only independent verification teaches success; executed red tests teach failure; infrastructure is neutral',()=>{
  t=0;let c=cycle();
  c=applyEvent(c,ev('DIAGNOSE','agent',{baseSha:BASE,kinds:['K']}));
  c=applyEvent(c,ev('PLAN','agent',{strategy:'S'}));
  c=applyEvent(c,ev('PATCH','agent',{patchSha256:PATCH,branch:'continuum/fix-1',files:1,worktree:'w'}));
  const red=applyEvent(applyEvent(c,ev('LOCAL_TESTS','agent',{patchSha256:PATCH,tests:tests(2)})),ev('FAIL','agent',{reason:'TESTS'}));
  assert.equal(creditOutcome(red).status,'REJECTED');
  const infra=applyEvent(c,ev('FAIL','agent',{reason:'RUNNER_LOST'}));
  assert.equal(creditOutcome(infra).status,'NEUTRAL');
  assert.equal(creditOutcome(c),null);   // not finished: no signal at all
});

test('Thompson ranking is reproducible, keeps the safety tiers and is a different arm than the frozen control',()=>{
  const cands=[
    {id:'a',diagnostic:'d',diagnosticKind:'K',strategy:'REVERT_FILE_TO_PARENT',prior:0.9},
    {id:'b',diagnostic:'d',diagnosticKind:'K',strategy:'GUARD_NULL',prior:0.5},
    {id:'c',diagnostic:'d',diagnosticKind:'K',strategy:'FIX_IMPORT',prior:0.5}];
  const stats=[{kind:'K|GUARD_NULL',successes:1,failures:9},{kind:'K|FIX_IMPORT',successes:9,failures:1},
    {kind:'K|REVERT_FILE_TO_PARENT',successes:50,failures:0}];
  const r1=thompsonRank(cands,stats,new Set(),42).map(c=>c.id),r2=thompsonRank(cands,stats,new Set(),42).map(c=>c.id);
  assert.deepEqual(r1,r2);
  assert.equal(r1.at(-1),'a');                 // a destructive revert never outranks a local fix, whatever its record
  assert.equal(r1[0],'c');
  assert.deepEqual(frozenRank(cands,stats).map(c=>c.id).at(-1),'a');
  assert.deepEqual(thompsonRank(cands,stats,new Set(['d:c']),42).map(c=>c.id),['b','a']);
  const rnd=__test.mulberry32(7);let mean=0;for(let i=0;i<4000;i++)mean+=betaSample(3,7,rnd);
  assert.ok(Math.abs(mean/4000-0.3)<0.02);     // Beta(3,7) has mean 0.3
});

test('a strategy change is a preregistered experiment: sealed holdout, one run, exact paired test, kept if negative',()=>{
  const tasks=Array.from({length:12},(_,i)=>h('task-'+i));
  let reg=preregister(emptyRegistry(),{id:'EXP-DL1',hypothesis:'Thompson ranking repairs more holdout tasks than Laplace',
    metric:'independently_verified_repairs',control:'laplace-v1',candidate:'thompson-v1',holdoutTasks:tasks});
  assert.throws(()=>preregister(reg,{id:'EXP-DL1',hypothesis:'again with other data',metric:'m',control:'a',candidate:'b',holdoutTasks:tasks}),
    /ALREADY_PREREGISTERED/);
  assert.throws(()=>preregister(emptyRegistry(),{id:'EXP-X',hypothesis:'too few holdout tasks',metric:'m',control:'a',candidate:'b',
    holdoutTasks:tasks.slice(0,3)}),/HOLDOUT_TOO_SMALL_OR_INVALID/);
  const wrongSet=tasks.slice(1).concat(h('other')).map(task=>({task,control:false,candidate:true}));
  assert.throws(()=>recordHoldoutRun(reg,'EXP-DL1',wrongSet),/NOT_THE_SEALED_HOLDOUT/);
  const tampered={...reg,experiments:{'EXP-DL1':{...reg.experiments['EXP-DL1'],alpha:0.5}}};
  assert.throws(()=>recordHoldoutRun(tampered,'EXP-DL1',tasks.map(task=>({task,control:false,candidate:true}))),/PREREGISTRATION_MODIFIED/);
  const pairs=tasks.map((task,i)=>({task,control:i<3,candidate:i<11}));   // candidate wins 8, control wins 0
  reg=recordHoldoutRun(reg,'EXP-DL1',pairs);
  assert.equal(reg.experiments['EXP-DL1'].result.verdict,'PASSED');
  assert.throws(()=>recordHoldoutRun(reg,'EXP-DL1',pairs),/HOLDOUT_ALREADY_USED/);
  let neg=preregister(emptyRegistry(),{id:'EXP-DL2',hypothesis:'A worse candidate is recorded, not hidden',metric:'m',control:'a',
    candidate:'b',holdoutTasks:tasks});
  neg=recordHoldoutRun(neg,'EXP-DL2',tasks.map((task,i)=>({task,control:i<9,candidate:i<3})));
  assert.equal(neg.experiments['EXP-DL2'].result.verdict,'FAILED');
  assert.equal(neg.experiments['EXP-DL2'].result.controlWins,6);
  // two discordant pairs prove nothing either way
  let few=preregister(emptyRegistry(),{id:'EXP-DL3',hypothesis:'Too few discordant pairs stay inconclusive',metric:'m',control:'a',
    candidate:'b',holdoutTasks:tasks});
  few=recordHoldoutRun(few,'EXP-DL3',tasks.map((task,i)=>({task,control:i<6,candidate:i<4})));
  assert.equal(few.experiments['EXP-DL3'].result.verdict,'INCONCLUSIVE');
});

test('the exact McNemar p-value matches the binomial tail',()=>{
  assert.equal(mcnemarOneSided(0,0),1);
  assert.ok(Math.abs(mcnemarOneSided(8,0)-1/256)<1e-12);
  assert.ok(Math.abs(mcnemarOneSided(8,1)-10/512)<1e-12);    // P(X>=8 | n=9) = (9+1)/512
  assert.ok(mcnemarOneSided(3,3)>0.5);
});

test('weak strategies in the real CCC1 ledger become research questions, never automatic changes',()=>{
  let ledger=emptyLedger();
  const attempt=(outcome,i)=>({diagnostic:'ab',diagnosticKind:'KOTLIN_UNRESOLVED',candidate:'c'+i,strategy:'ADD_IMPORT',outcome,
    edits:[],tests:[{profile:'unit',passed:false,tests:{total:10,executed:10,failed:2,skipped:0}}]});
  for(let i=0;i<5;i++)ledger=recordSession(ledger,{outcome:'FAILED_TESTS',base:BASE,chain:'x',attempts:[attempt('FAILED_TESTS',i)]});
  const w=mineWeaknesses(ledger.experiences);
  assert.equal(w.length,1);
  assert.equal(w[0].kind,'KOTLIN_UNRESOLVED');
  assert.match(w[0].question,/vorregistriertes Experiment/);
  assert.deepEqual(mineWeaknesses([{kind:'A|B',successes:1,failures:1}]),[]);   // too few observations
});
