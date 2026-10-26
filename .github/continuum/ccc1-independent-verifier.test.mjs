import test from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {validateCodingTask,taskFingerprint} from './coding-task.mjs';
import {validateCcc1CandidateBinding,validateCcc1ResearchRequest,
 runIndependentCcc1PatchVerification,verifyCcc1OwnerGrant,CCC1_RESEARCH_OUTCOMES} from './ccc1-independent-verifier.mjs';

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
