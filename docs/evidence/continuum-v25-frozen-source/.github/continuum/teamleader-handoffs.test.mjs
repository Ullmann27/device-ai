import test from 'node:test';
import assert from 'node:assert/strict';
import {summarizeRepairHandoffs,verifyRepairHandoffs,REPAIR_ROLES}
  from './teamleader-handoffs.mjs';
import fs from 'node:fs';
import crypto from 'node:crypto';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {localTeamleaderDriver} from './autonomous-teamleader.mjs';

const HEAD='a'.repeat(40);
const testResult={profile:'node-continuum',passed:true,
  tests:{executed:3,failed:0,skipped:0}};
const failedBaseline={schemaVersion:1,base:HEAD,chain:'node',
  baseline:{passed:false,results:[{profile:'node-continuum',passed:false}],
    diagnostics:[{file:'test/fixture.mjs',kind:'MISSING_IMPORT'}]},
  attempts:[],verified:null,outcome:'NO_CODE_DIAGNOSTIC'};
function verified(){
 const good=structuredClone(failedBaseline);
 const attempt={candidate:'cand-42',strategy:'SAFE_IMPORT',
  outcome:'VERIFIED',critic:{approved:true,reviewRequired:true},
  edits:[{file:'test/fixture.mjs'}],tests:[structuredClone(testResult)],
  evidence:{sufficient:true}};
 good.attempts=[attempt];
 good.outcome='VERIFIED';
 const patch='diff --git a/test/fixture.mjs b/test/fixture.mjs\n--- a/test/fixture.mjs\n+++ b/test/fixture.mjs\n@@ -1 +1 @@\n-old\n+new\n';
 good.verified={patch,patchSha256:crypto.createHash('sha256').update(patch).digest('hex'),
   tests:[structuredClone(testResult)],reviewRequired:true};
 return good;
}
test('clean baseline ends after analyst with truthful stop, not fake repair',()=>{
 const source={...structuredClone(failedBaseline),baseline:{
   passed:true,results:[{profile:'node-continuum',passed:true}],
   diagnostics:[]},outcome:'NO_FAILURE'};
 const handoff=summarizeRepairHandoffs(source);
 assert.deepEqual(handoff.messages.map(m=>m.role),['ANALYST','TEAMLEADER']);
 assert.equal(handoff.messages.at(-1).approvedForMerge,false);
 assert.equal(verifyRepairHandoffs(handoff),true);
});
test('verified repair needs real critic + isolated patch + executed tests',()=>{
 const report=verified();
 const m=summarizeRepairHandoffs(report);
 assert.deepEqual(m.messages.map(x=>x.role),
  ['ANALYST','CRITIC','PATCHER','TESTER','TEAMLEADER']);
 assert.equal(m.messages.at(-1).event,'PATCH_PENDING_REVIEW');
 assert.equal(m.messages.at(-1).approvedForMerge,false);
 assert.equal(verifyRepairHandoffs(m),true);
 const tampered=structuredClone(m);
 tampered.messages[3].passed=false;
 assert.equal(verifyRepairHandoffs(tampered),false);
});
test('fabricated tests, missing evidence, rejected critic cannot approve patch',()=>{
 let r=verified();
 r.attempts[0].tests[0].passed=false;
 assert.throws(()=>summarizeRepairHandoffs(r),/lacks passing executed tests/);
 r=verified();
 r.attempts[0].tests[0].tests.executed=0;
 assert.throws(()=>summarizeRepairHandoffs(r),/lacks passing executed tests/);
 r=verified();
 r.attempts[0].evidence.sufficient=false;
 assert.throws(()=>summarizeRepairHandoffs(r),/lacks passing executed tests/);
 r=verified();
 r.attempts[0].critic.approved=false;
 assert.throws(()=>summarizeRepairHandoffs(r),/Critic bypass/);
 r=verified();
 r.verified=null;
 assert.throws(()=>summarizeRepairHandoffs(r),/full review evidence/);
 r=verified();
 r.verified.patch='';
 assert.throws(()=>summarizeRepairHandoffs(r),/full review evidence/);
 r=verified();
 r.verified.patch='a forged patch with an unrelated hash';
 assert.throws(()=>summarizeRepairHandoffs(r),/full review evidence/);
 r=verified();
 r.verified.reviewRequired='false';
 assert.throws(()=>summarizeRepairHandoffs(r),/full review evidence/);
});
test('reviewer rejection does not lead to edits or test execution',()=>{
 const r=structuredClone(failedBaseline);
 r.outcome='NO_VERIFIED_REPAIR';
 r.attempts=[{candidate:'cand-1',strategy:'UNSAFE_FIX',
  critic:{approved:false},outcome:'REJECTED_BY_CRITIC'}];
 const m=summarizeRepairHandoffs(r);
 assert.deepEqual(m.messages.map(x=>x.role),['ANALYST','CRITIC','TEAMLEADER']);
 assert.ok(m.messages.every(x=>x.event!=='PATCH_PENDING_REVIEW'));
});
function syntaxAttempt(outcome='FAILED_SYNTAX'){
 return {candidate:'cand-syntax',strategy:'JS_TYPO_REFERENCE',
  critic:{approved:true},edits:[{file:'.github/continuum/math.mjs'}],
  outcome,...(outcome==='FAILED_SYNTAX'
   ?{syntax:{passed:false,checked:[],failedFile:'.github/continuum/math.mjs',
      reason:'INVALID_JS_SYNTAX'}}
   :{syntaxReason:'INVALID_CHECK_REQUEST'})};
}
test('syntax-rejected candidates retain handoffs without claiming executed tests',()=>{
 for(const outcome of ['FAILED_SYNTAX','SYNTAX_CHECK_ERROR']){
  const report=structuredClone(failedBaseline);
  report.outcome='NO_VERIFIED_REPAIR';
  report.attempts=[syntaxAttempt(outcome)];
  const handoff=summarizeRepairHandoffs(report);
  assert.equal(verifyRepairHandoffs(handoff),true);
  assert.deepEqual(handoff.messages.map(m=>m.role),
   ['ANALYST','CRITIC','PATCHER','TESTER','TEAMLEADER']);
  const gate=handoff.messages.find(m=>m.event==='JS_SYNTAX_GATE');
  assert.equal(gate.passed,false);
  assert.equal(gate.executed,false);
  assert.equal(gate.outcome,outcome);
  assert.equal(handoff.messages.at(-1).approvedForMerge,false);
  assert.ok(handoff.messages.every(m=>m.event!=='FIXED_TEST_CHAIN'));
 }
});
test('syntax rejection cannot replace critic approval or claim full test evidence',()=>{
 const source=structuredClone(failedBaseline);
 source.outcome='NO_VERIFIED_REPAIR';
 source.attempts=[syntaxAttempt()];
 for(const modify of [
  a=>{a.critic.approved=false;},
  a=>{a.syntax.passed=true;},
  a=>{a.syntax=null;},
  a=>{a.tests=[structuredClone(testResult)];}
 ]){
  const report=structuredClone(source);modify(report.attempts[0]);
  assert.throws(()=>summarizeRepairHandoffs(report));
 }
});
test('unrecognized or oversized role payloads fail without execution',()=>{
 const r=verified();
 r.attempts[0].strategy='x'.repeat(141);
 assert.throws(()=>summarizeRepairHandoffs(r),/Invalid specialist/);
 r.attempts[0].strategy='SAFE_IMPORT';
 r.baseline.passed=true;
 assert.throws(()=>summarizeRepairHandoffs(r),/Baseline passed/);
 const m=summarizeRepairHandoffs(verified());
 const altered=structuredClone(m);
 altered.messages[0].role='ROOT';
 assert.equal(verifyRepairHandoffs(altered),false);
 assert.deepEqual(REPAIR_ROLES.TEAMLEADER,['REVIEW_PROPOSAL']);
});
test('real offline Teamleader keeps a syntax rejection before an independently verified proposal',
 {skip:Boolean(process.env.CONTINUUM_REPAIR_DEPTH),timeout:60000},async()=>{
 const base=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-handoff-integration-'));
 const root=path.join(base,'checkout'),state=path.join(base,'state');
 const dir=path.join(root,'.github','continuum');
 fs.mkdirSync(dir,{recursive:true});fs.mkdirSync(state);
 const source="const syntaxHint='await await await';\n"+
  'export function awaii(n){return n*3}\n'+
  'export function triple(n){return awai(n)}\n';
 fs.writeFileSync(path.join(dir,'math.mjs'),source);
 fs.writeFileSync(path.join(dir,'math.test.mjs'),
  "import test from 'node:test';\nimport assert from 'node:assert/strict';\n"+
  "import {triple} from './math.mjs';\ntest('triple',()=>assert.equal(triple(3),9));\n");
 const git=(...args)=>{
  const r=spawnSync('git',args,{cwd:root,encoding:'utf8',timeout:20000});
  assert.equal(r.status,0,r.stderr);return r.stdout.trim();
 };
 try{
  git('init','-q');git('add','.');
  git('-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','baseline');
  const head=git('rev-parse','HEAD');
  const driver=await localTeamleaderDriver();
  const report=driver.repair(root,state,head,{budgetMinutes:1});
  assert.equal(report.outcome,'VERIFIED');
  assert.equal(report.independent.passed,true);
  assert.deepEqual(report.attempts.map(a=>a.outcome),['FAILED_SYNTAX','VERIFIED']);
  assert.equal(verifyRepairHandoffs(report.handoffs),true);
  const syntax=report.handoffs.messages.find(m=>m.event==='JS_SYNTAX_GATE');
  assert.equal(syntax.executed,false);
  assert.equal(report.handoffs.messages.at(-1).event,'PATCH_PENDING_REVIEW');
  assert.equal(report.handoffs.messages.at(-1).approvedForMerge,false);
  assert.equal(fs.readFileSync(path.join(dir,'math.mjs'),'utf8'),source);
  assert.equal(git('status','--porcelain'),'');
  assert.equal(git('worktree','list','--porcelain').match(/^worktree /gm).length,1);
  const persisted=JSON.parse(fs.readFileSync(report.artifact,'utf8'));
  assert.equal(verifyRepairHandoffs(persisted.handoffs),true);
  const ledger=JSON.parse(fs.readFileSync(path.join(state,'repair-ledger.json'),'utf8'));
  assert.deepEqual(ledger.attempts.map(a=>a.outcome),['FAILED_SYNTAX','VERIFIED']);
  // A crashed parser is auditable but does not prove this candidate is bad.
  // On the next authorized attempt, the real worker must still try it.
  for(const [outcome,syntaxReason] of [
   ['SYNTAX_CHECK_ERROR','INVALID_CHECK_REQUEST'],
   ['FAILED_SYNTAX','SYNTAX_CHECK_INTERRUPTED']
  ]){
   const prior=structuredClone(ledger.attempts.find(a=>a.outcome==='VERIFIED'));
   Object.assign(prior,{outcome,syntaxReason,tests:[],patchSha256:null});
   fs.writeFileSync(path.join(state,'repair-ledger.json'),JSON.stringify({
    ...ledger,attempts:[prior],experiences:[],sessions:[]
   }));
   const retried=driver.repair(root,state,head,{budgetMinutes:1});
   assert.equal(retried.outcome,'VERIFIED',syntaxReason+' must allow candidate retry');
   assert.equal(retried.independent.passed,true);
  }
 }finally{fs.rmSync(base,{recursive:true,force:true});}
});
