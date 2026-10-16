import test from 'node:test';
import assert from 'node:assert/strict';
import {summarizeRepairHandoffs,verifyRepairHandoffs,REPAIR_ROLES}
  from './teamleader-handoffs.mjs';
import fs from 'node:fs';
import crypto from 'node:crypto';

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
test('existing Teamleader real repair driver produces these structured handoffs',()=>{
 const source=fs.readFileSync(new URL('./autonomous-teamleader.mjs',import.meta.url),'utf8');
 assert.match(source,/summarizeRepairHandoffs\(result\)/);
 assert.match(source,/createWorktree\(root,head\)/);
 assert.match(source,/removeWorktree\(root,wt\)/);
 assert.doesNotMatch(source,/git\(root,\['push'/);
});
