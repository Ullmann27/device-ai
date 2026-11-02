import test from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {validateCodingTask,taskFingerprint} from './coding-task.mjs';
import {CoinWmV3ShadowModel} from './coin-wm-v3-shadow-model.mjs';
import {induceStructuredStatementOperator} from './structured-statement-translator.mjs';
import {proposeShadowStructuredCandidates} from './coin-wm-v3-structured-shadow.mjs';
import {validateCcc1CandidateBinding,validateCcc1ResearchRequest,
 runIndependentCcc1PatchVerification,verifyCcc1OwnerGrant,assertCcc1RunMeasurement,assertCcc1TestEvidenceParity,CCC1_RESEARCH_OUTCOMES} from './ccc1-independent-verifier.mjs';

const sha=value=>crypto.createHash('sha256').update(value).digest('hex');
const original='export const page = (number, size) => number * size;\n';
const fixed='export const page = (number, size) => (number - 1) * size;\n';
function task(){
 return validateCodingTask({
  schema:'continuum-coding-task',version:1,id:'ccc1-source-verify-01',
  sourceCommit:'a'.repeat(40),kind:'BUGFIX',
  goal:'Correct the pagination offset without changing test files.',
  allowedPaths:['src/pagination.mjs'],
  acceptance:['Existing Node test profile must reproduce and then pass.'],
  chain:'node',limits:{maxModelCalls:1,maxContextChars:2048}
 });
}
function data(){
 const t=task();
 return {typedTask:t,file:'src/pagination.mjs',candidate:{
  status:'CANDIDATE_UNVERIFIED',sourceCommit:t.sourceCommit,
  taskSha256:taskFingerprint(t),
  sourceSha256:sha(original),candidateSha256:sha(fixed),code:fixed
 }};
}

test('read-only binding checks the canonical typed task and all four identities',()=>{
 const item=data(),result=validateCcc1CandidateBinding(item);
 assert.equal(result.task.id,item.typedTask.id);
 assert.equal(result.file,'src/pagination.mjs');
 assert.equal(result.candidate.status,'CANDIDATE_UNVERIFIED');
 assert.equal(typeof result.execute,'undefined');
 assert.equal(typeof result.trainStep,'undefined');
 assert.equal(typeof result.appendVerifiedEntry,'undefined');
 assert.ok(Object.isFrozen(result));
 assert.ok(Object.isFrozen(result.candidate));
 const pinned=result.candidate.code;
 item.candidate.code='malicious late mutation';
 assert.equal(result.candidate.code,pinned);
});

test('invalid source, task or patch hash cannot cross the research binding',()=>{
 const d=data();
 for(const candidate of [
  {...d.candidate,sourceCommit:'b'.repeat(40)},
  {...d.candidate,taskSha256:'0'.repeat(64)},
  {...d.candidate,sourceSha256:'nope'},
  {...d.candidate,candidateSha256:'0'.repeat(64)},
  {...d.candidate,candidateSha256:d.candidate.sourceSha256},
  {...d.candidate,status:'VERIFIED'},
  {...d.candidate,code:''}
 ]){
  assert.throws(()=>validateCcc1CandidateBinding({...d,candidate}),/CANDIDATE_IDENTITY_MISMATCH/);
 }
});

test('forbid test tampering and unapproved paths independently of candidate label',()=>{
 const d=data();
 assert.throws(()=>validateCcc1CandidateBinding({...d,file:'test/pagination.test.mjs'}),/UNSUPPORTED_SCOPE/);
 assert.throws(()=>validateCcc1CandidateBinding({...d,file:'src/evil.mjs'}),/UNSUPPORTED_SCOPE/);
 const disguised=validateCodingTask({...d.typedTask,allowedPaths:['src/disguised.spec.mjs']});
 assert.throws(()=>validateCcc1CandidateBinding({typedTask:disguised,
   file:'src/disguised.spec.mjs',candidate:d.candidate}),/UNSUPPORTED_SCOPE/);
 assert.throws(()=>validateCcc1CandidateBinding({
  ...d,typedTask:{...d.typedTask,sourceCommit:'a'.repeat(8)}
 }),/CODING_TASK_INVALID/);
});

test('a fake executor or a caller-supplied sandbox cannot issue a dual-run claim',()=>{
 const d=data();
 let launched=false;
 const impostor={execute(){launched=true;return {status:0}}},
   sandboxRunner={runBaselineBeforePatch(){launched=true;return {exitCode:1}}};
 assert.throws(()=>validateCcc1ResearchRequest({...d,executor:impostor}),/BRANDED_OS_EXECUTOR_REQUIRED/);
 assert.throws(()=>runIndependentCcc1PatchVerification({
  ...d,projectRoot:'/tmp',executor:impostor,sandboxRunner,
  testCommand:'node -e process.exit(0)',ntw1Model:{trainStep(){launched=true}}
 }),/BRANDED_OS_EXECUTOR_REQUIRED/);
 assert.equal(launched,false);
 assert.equal(CCC1_RESEARCH_OUTCOMES.PASS,'DUAL_WORKTREE_CHECKS_PASS_REVIEW_REQUIRED');
});

test('existing CCC1 owner grant must be valid, canonical and fully prepared',()=>{
 const base=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-ccc1-grant-'));
 try{
  const projectRoot=path.join(base,'repo'),stateDir=path.join(base,'state');
  fs.mkdirSync(projectRoot);fs.mkdirSync(path.join(stateDir,'coding-grants'),{recursive:true});
  const typedTask=task(),fingerprint=taskFingerprint(typedTask);
  const file=path.join(stateDir,'coding-grants',typedTask.id+'.json');
  const original={task:typedTask,fingerprint,ccc1TaskId:'t-'+'b'.repeat(16)};
  fs.writeFileSync(file,JSON.stringify(original));
  const grant=verifyCcc1OwnerGrant({projectRoot,stateDir,typedTask});
  assert.equal(grant.fingerprint,fingerprint);
  assert.equal(grant.ccc1TaskId,original.ccc1TaskId);
  assert.ok(Object.isFrozen(grant));
  fs.writeFileSync(file,JSON.stringify({...original,ccc1TaskId:null}));
  assert.throws(()=>verifyCcc1OwnerGrant({projectRoot,stateDir,typedTask}),/OWNER_GRANT_MISMATCH/);
  fs.writeFileSync(file,JSON.stringify({...original,fingerprint:'0'.repeat(64)}));
  assert.throws(()=>verifyCcc1OwnerGrant({projectRoot,stateDir,typedTask}),/OWNER_GRANT_MISMATCH/);
  fs.writeFileSync(file,JSON.stringify(original));
  assert.throws(()=>verifyCcc1OwnerGrant({projectRoot,stateDir:projectRoot,typedTask}),
    /CCC1_STATE_INSIDE_SOURCE/);
 }finally{fs.rmSync(base,{recursive:true,force:true});}
});

test('NTW1 rank -> atomic translator -> CCC1 typed candidate binding is an executable-free chain',()=>{
 const t=task();
 const operator=induceStructuredStatementOperator('sort',
  'function rankScores(scores) { return scores.sort((a, b) => a - b); }',
  'function rankScores(scores) { return [...scores].sort((a, b) => a - b); }');
 const input='function sortVisible(items) { return items.sort((a, b) => a - b); }';
 const model=new CoinWmV3ShadowModel(1);
 const shadow=proposeShadowStructuredCandidates({
  model,operators:[operator],state16:new Float64Array(16),
  source:input,sourceCommit:t.sourceCommit,taskSha256:taskFingerprint(t),maxCandidates:1
 });
 assert.equal(shadow.status,'RESEARCH_SHADOW');
 assert.equal(shadow.promotionEligible,false);
 assert.equal(shadow.candidates.length,1);
 const candidate=shadow.candidates[0];
 const binding=validateCcc1CandidateBinding({
  typedTask:t,candidate,file:'src/pagination.mjs'
 });
 assert.equal(binding.candidate.status,'CANDIDATE_UNVERIFIED');
 assert.equal(binding.candidate.candidateSha256,sha(binding.candidate.code));
 assert.equal(binding.candidate.sourceSha256,sha(input));
 assert.equal(typeof binding.execute,'undefined');
 assert.equal(model.updatesCount,0);
});

function evidence(executed,failed=0,skipped=0,skippedCaseDigest=null){
 return {results:[{profile:'node-continuum',testProfile:true,passed:failed===0,
  tests:{executed,failed,passed:executed-failed,skipped,total:executed+skipped,
    placeholders:0,evidenceComplete:true,executedCaseHashes:Array.from({length:executed},(_,i)=>i.toString(16).padStart(64,'0')),
    ...(skippedCaseDigest?{skippedCaseDigest}:{})}
 }]};
}

test('CCC1: omitted or non-true evidenceComplete cannot establish RED/GREEN/fresh parity',()=>{
 const goodRed=evidence(3,1),goodGreen=evidence(3);
 assert.equal(assertCcc1TestEvidenceParity(goodRed,goodGreen).candidateExecuted,3);
 for(const value of [undefined,null,false,0,'true']){
  for(const phase of ['baseline','candidate']){
   const red=evidence(3,1),green=evidence(3);
   const target=(phase==='baseline'?red:green).results[0].tests;
   if(value===undefined)delete target.evidenceComplete;
   else target.evidenceComplete=value;
   assert.throws(()=>assertCcc1TestEvidenceParity(red,green),/TEST_COUNT_EVIDENCE_INVALID/,
    String(phase)+' '+String(value));
  }
 }
});

test('CCC1: run measurement requires explicit complete XML evidence',()=>{
 const isolation={
  schema:'continuum-isolation-v1',image:'sha256:'+'a'.repeat(64),network:'none',
  user:'1000:1000',readOnly:true,noNewPrivileges:true,capabilities:[],
  profile:'node-continuum',sourceSha256:'b'.repeat(64),untrustedReports:true,
  memoryMb:512,pids:128,cpus:1,timeoutMs:30000
 };
 const run=value=>({results:[{profile:'node-continuum',testProfile:true,
  passed:true,exitCode:0,isolation,tests:{executed:1,failed:0,
   ...(value!==undefined?{evidenceComplete:value}:{})}}]});
 assert.equal(assertCcc1RunMeasurement(run(true)).tests.executed,1);
 for(const value of [undefined,null,false,0,'true'])
  assert.throws(()=>assertCcc1RunMeasurement(run(value)),/TEST_RUN_INTERRUPTED/,
   String(value));
 // This fixture only checks receipt-shape validation; no OS isolation or tests run here.
});

test('CCC1: an apparent green with fewer executed tests than RED is NOT verified',()=>{
 const red=evidence(120,1);
 const green=evidence(119,0);
 assert.throws(()=>assertCcc1TestEvidenceParity(red,green),/EXECUTED_TEST_COVERAGE_REGRESSION/);
});

test('CCC1: equal or increased executed coverage preserves same-profile evidence',()=>{
 const red=evidence(120,1),green=evidence(120),fresh=evidence(125);
 assert.equal(assertCcc1TestEvidenceParity(red,green).baselineExecuted,120);
 assert.equal(assertCcc1TestEvidenceParity(green,fresh).candidateExecuted,125);
});

test('CCC1: skipped tests must remain identical across RED, GREEN and fresh run',()=>{
 const digest='a'.repeat(64),changed='b'.repeat(64);
 const baseline=evidence(10,1,1,digest);
 assert.equal(assertCcc1TestEvidenceParity(baseline,evidence(10,0,1,digest)).skipped,1);
 assert.throws(()=>assertCcc1TestEvidenceParity(baseline,evidence(10,0,2,digest)),/SKIPPED_TEST_SET_CHANGED/);
 assert.throws(()=>assertCcc1TestEvidenceParity(baseline,evidence(10,0,1,changed)),/SKIPPED_TEST_IDENTITY_CHANGED/);
 assert.throws(()=>assertCcc1TestEvidenceParity(baseline,evidence(10,0,1)),/SKIPPED_TEST_IDENTITY_CHANGED/);
});

test('CCC1: no missing profile, zero executed tests or contradictory JUnit counts',()=>{
 const baseline=evidence(3,1);
 assert.throws(()=>assertCcc1TestEvidenceParity(baseline,{results:[]}),/TEST_PROFILE_PARITY_INVALID/);
 assert.throws(()=>assertCcc1TestEvidenceParity(baseline,evidence(0)),/TEST_COUNT_EVIDENCE_INVALID/);
 const bad=evidence(3);
 bad.results[0].tests.passed=9;
 assert.throws(()=>assertCcc1TestEvidenceParity(baseline,bad),/TEST_COUNT_EVIDENCE_INVALID/);
});


test('CCC1: same number of tests but a replaced testcase cannot fake equal coverage',()=>{
 const red=evidence(10,1);
 const fake=evidence(10,0);
 fake.results[0].tests.executedCaseHashes[8]='f'.repeat(64);
 assert.throws(()=>assertCcc1TestEvidenceParity(red,fake),/EXECUTED_TEST_IDENTITY_MISSING/);
});

test('CCC1: missing, duplicate or malformed executed identities never attest repairs',()=>{
 const red=evidence(4,1);
 const bad=evidence(4);
 delete bad.results[0].tests.executedCaseHashes;
 assert.throws(()=>assertCcc1TestEvidenceParity(red,bad),/EXECUTED_TEST_IDENTITIES_INCOMPLETE/);
 const duplicate=evidence(4);
 duplicate.results[0].tests.executedCaseHashes[1]=duplicate.results[0].tests.executedCaseHashes[0];
 assert.throws(()=>assertCcc1TestEvidenceParity(red,duplicate),/EXECUTED_TEST_IDENTITIES_INCOMPLETE/);
});
