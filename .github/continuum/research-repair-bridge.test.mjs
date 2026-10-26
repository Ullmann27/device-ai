import test from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import {validateCodingTask,taskFingerprint} from './coding-task.mjs';
import {runResearchRepairBridge} from './research-repair-bridge.mjs';

const hash=s=>crypto.createHash('sha256').update(s).digest('hex');
function task(){
 return validateCodingTask({schema:'continuum-coding-task',version:1,
  id:'research-bridge-repair-one',sourceCommit:'a'.repeat(40),kind:'BUGFIX',
  goal:'Correct pagination on a constrained owner-reviewed source file.',
  allowedPaths:['src/pagination.mjs'],acceptance:['Reproduce and fix test failures.'],
  chain:'node',limits:{maxModelCalls:1,maxContextChars:2048}});
}
const original='export const page=(n,size)=>n*size;\n';
const corrected='export const page=(n,size)=>(n-1)*size;\n';
function payload(){
 const typedTask=task();
 return {typedTask,file:'src/pagination.mjs',projectRoot:'/tmp',
  stateDir:'/var/tmp',candidate:{status:'CANDIDATE_UNVERIFIED',
   sourceCommit:typedTask.sourceCommit,taskSha256:taskFingerprint(typedTask),
   sourceSha256:hash(original),candidateSha256:hash(corrected),code:corrected}};
}
test('bad task/candidate cannot request OS execution or reach trusted verifier',()=>{
 let invoked=0;
 const options=payload();options.candidate.candidateSha256='f'.repeat(64);
 assert.throws(()=>runResearchRepairBridge({...options,
  candidateVerifier:()=>{invoked++;return null;}}),/CANDIDATE_IDENTITY_MISMATCH/);
 assert.equal(invoked,0);
});
test('typed research path never substitutes an unbranded host executor',()=>{
 let invoked=0;
 const options=payload();
 assert.throws(()=>runResearchRepairBridge({...options,executor:{execute(){invoked++;}},
  candidateVerifier:()=>{invoked++;return null;}}),/BRANDED_OS_EXECUTOR_REQUIRED/);
 assert.equal(invoked,0);
});
test('missing executor fails closed, including when feedback fingerprint is supplied',()=>{
 const options=payload();
 assert.throws(()=>runResearchRepairBridge({...options,executor:null,
  feedbackFingerprint:'f'.repeat(64)}),/BRANDED_OS_EXECUTOR_REQUIRED/);
});
