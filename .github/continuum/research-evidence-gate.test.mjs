import test from 'node:test';
import assert from 'node:assert/strict';
import {evaluateResearchRepairEvidence} from './research-evidence-gate.mjs';

const H=n=>n.toString(16).padStart(64,'0');
const identity={sourceCommit:'a'.repeat(40),taskSha256:H(1),sourceSha256:H(2),
 candidateSha256:H(3),verifierRequestSha256:H(4)};
function sample(executed,failed,exitCode){
 return {profile:'node-continuum',passed:failed===0,evidenceComplete:true,
  executed,failed,skipped:0,executedCaseHashes:Array.from({length:executed},(_,i)=>H(i+10)),
  skippedCaseDigest:null,exitCode,sourceSnapshotSha256:H(111)};
}
const baseline=()=>({status:'DUAL_WORKTREE_CHECKS_PASS_REVIEW_REQUIRED',reason:'DUAL_RUN_REVIEW_REQUIRED',
 ownerReviewRequired:true,verifiedSuccess:false,learningEligible:false,promotionEligible:false,
 testEvidenceAuthenticated:false,semanticRepairVerified:false,
 sourceCommit:identity.sourceCommit,taskSha256:identity.taskSha256,
 sourceSha256:identity.sourceSha256,candidateSha256:identity.candidateSha256,
 before:[sample(3,1,1)],after:[sample(3,0,0)],fresh:[sample(3,0,0)]});
const verifier=()=>({schema:'continuum-candidate-verification',version:1,status:'PASS',
 base:identity.sourceCommit,candidateSha256:identity.verifierRequestSha256,
 suiteSha256:H(20),checks:3,counterexample:null});
const review=(ccc1Receipt=baseline(),verifierVerdict=verifier(),other={})=>
 evaluateResearchRepairEvidence({identity,ccc1Receipt,verifierVerdict,...other});

test('full synthetic RED GREEN fresh and counterexample PASS remain review-only',()=>{
 const result=review();
 assert.equal(result.status,'RESEARCH_REVIEW_REQUIRED');
 assert.equal(result.ccc1EvidencePresent,true);
 assert.equal(result.counterexamplePass,true);
 for(const flag of ['verifiedSuccess','learningEligible','promotionEligible','autoApply',
  'receiptAuthenticated','feedbackAuthenticated'])assert.equal(result[flag],false);
 assert.equal(result.modelWeightUpdates,0);
 assert.ok(Object.isFrozen(result));
});
test('default absent verifier abstains even if CCC1 is complete',()=>{
 const result=review(baseline(),null);
 assert.equal(result.status,'RESEARCH_EVIDENCE_BLOCKED');
 assert.equal(result.counterexamplePass,false);
});
test('independent counterexample defeats apparently green code',()=>{
 const result=review(baseline(),{...verifier(),status:'FAIL',
  counterexample:{id:'distance',file:'lib/math.mjs',inputs:[2,2],actual:5,expected:0}});
 assert.equal(result.reason,'COUNTEREXAMPLE_VETO');
 assert.equal(result.learningEligible,false);
});
test('malformed and replayed verifier evidence fails closed',()=>{
 for(const change of [{candidateSha256:H(99)},{base:'b'.repeat(40)},
  {checks:0},{counterexample:{id:'fake'}},{suiteSha256:'invalid'},
  {status:'ABSTAIN'},{status:'PASS',version:2}]){
  assert.equal(review(baseline(),{...verifier(),...change}).status,'RESEARCH_EVIDENCE_BLOCKED',
    JSON.stringify(change));
 }
});
test('missing, substituted and skipped tests cannot turn into CCC1 success',()=>{
 const cases=[
  x=>{x.before=[]},
  x=>{x.after[0].evidenceComplete=false},
  x=>{x.fresh[0].executed=2},
  x=>{x.fresh[0].executedCaseHashes[0]=H(99)},
  x=>{x.after[0].executedCaseHashes=[H(10),H(10),H(12)]},
  x=>{x.before[0].skipped=1},
  x=>{x.after[0].exitCode=1},
  x=>{x.before[0].failed=0},
  x=>{x.ownerReviewRequired=false},
  x=>{x.testEvidenceAuthenticated=true},
  x=>{x.status='CHECKS_FAILED_NO_LEARNING'},
  x=>{x.sourceCommit='b'.repeat(40)},
  x=>{x.reason='FAKE_SUCCESS'}
 ];
 for(const modify of cases){const x=baseline();modify(x);
  assert.equal(review(x).status,'RESEARCH_EVIDENCE_BLOCKED',modify.toString());}
});
test('review is not an owner confirmation, even with a matching feedback fingerprint',()=>{
 const r=review(baseline(),verifier(),{feedbackFingerprint:H(76)});
 assert.equal(r.feedbackFingerprint,H(76));
 assert.equal(r.feedbackAuthenticated,false);
 assert.equal(r.learningEligible,false);
 assert.equal(review(baseline(),verifier(),{feedbackFingerprint:'fake'}).feedbackFingerprint,null);
});
test('empty or mutated bound identities fail closed',()=>{
 assert.equal(evaluateResearchRepairEvidence({}).reason,'INVALID_IDENTITY');
 assert.equal(evaluateResearchRepairEvidence({identity:{...identity,sourceSha256:identity.candidateSha256},
  ccc1Receipt:baseline(),verifierVerdict:verifier()}).status,'RESEARCH_EVIDENCE_BLOCKED');
});
test('no caller input is ever mutated',()=>{
 const receipt=baseline(),v=verifier(),raw=JSON.stringify({receipt,v});
 review(receipt,v);assert.equal(JSON.stringify({receipt,v}),raw);
});
