import test from 'node:test';
import assert from 'node:assert/strict';
import {assessCcc1BridgeProposal} from './continuum-ccc1-review-gate.mjs';
const PATCH=JSON.stringify({path:'src/add.mjs',old:'return Math.abs(a) + Math.abs(b);',replacement:'return a + b;'});
const good=()=>({schema:'continuum-optional-coding-bridge-v1',mode:'SYNTHETIC_ONLY',
  repositoryAccess:false,patchApplied:false,ownerReviewRequired:true,transportKind:'MOCKED_OFFLINE',
  model:'claude-fable-5.1:free',completed:true,proposalVerifiedOnIsolatedCopy:true,
  observedTestCall:true,observedReadCalls:['src/add.mjs','test/add.test.mjs'],
  testResult:{tests:3,pass:2,fail:1,exitCode:1},
  proposalReview:{testResult:{tests:3,pass:3,fail:0,exitCode:0}},proposal:PATCH});
const fails=(p,re)=>assert.throws(()=>assessCcc1BridgeProposal({...good(),...p}),re);
test('accepted synthetic preview is review only; never grants CCC1 authority',()=>{
  const result=assessCcc1BridgeProposal(good());
  assert.equal(result.handoffAllowedToOwnerReview,true);
  for(const f of ['ownerApprovalGranted','ccc1TaskSubmitted','ccc1PatchApplied','privateRepositoryAccessGranted','modelCapabilityIndependentlyVerified'])assert.equal(result[f],false);
  assert.match(result.patchSha256,/^[0-9a-f]{64}$/);
  assert.doesNotMatch(JSON.stringify(result),/Math.abs|\.ssh|Bearer/);
});
test('unknown report schema fails',()=>fails({schema:'another-schema'},/UNTRUSTED_BRIDGE_REPORT/));
test('real checkout cannot be reported as synthetic',()=>fails({mode:'PRIVATE_REPO'},/UNTRUSTED_BRIDGE_REPORT/));
test('reported checkout access fails',()=>fails({repositoryAccess:true},/UNAUTHORIZED_WRITE_REPORTED/));
test('reported patch application fails',()=>fails({patchApplied:true},/UNAUTHORIZED_WRITE_REPORTED/));
test('claimed owner approval is explicitly refused',()=>fails({ownerReviewRequired:false},/UNAUTHORIZED_WRITE_REPORTED/));
test('unknown remote transport fails',()=>fails({transportKind:'FALLBACK_PAID'},/UNKNOWN_TRANSPORT/));
test('unsuffixed Fable and Astra cloud IDs fail',()=>{
  for(const model of ['claude-fable-5.1','gpt-6-astra']) fails({transportKind:'EXPERIENTIAL_FREE_ONLY',model},/BILLABLE_MODEL_REFUSED/);
});
test('valid free cloud ID still cannot grant repository rights',()=>{
  const r=assessCcc1BridgeProposal({...good(),transportKind:'EXPERIENTIAL_FREE_ONLY',model:'gpt-6-astra:free'});
  assert.equal(r.privateRepositoryAccessGranted,false);
});
test('local model must have local identity',()=>fails({transportKind:'SELF_HOSTED_LOOPBACK',model:'gpt-6-astra:free'},/UNKNOWN_LOCAL_MODEL/));
test('valid local advertisement still requires independent model evidence',()=>{
  const r=assessCcc1BridgeProposal({...good(),transportKind:'SELF_HOSTED_LOOPBACK',model:'local/owned-coder'});
  assert.equal(r.modelCapabilityIndependentlyVerified,false);
});
test('missing tool proof fails',()=>fails({observedTestCall:false},/MISSING_SYNTHETIC_TEST_PROOF/));
test('missing read path fails',()=>fails({observedReadCalls:['src/add.mjs']},/MISSING_SYNTHETIC_TEST_PROOF/));
test('spurious path cannot replace one of two permitted reads',()=>fails({observedReadCalls:['src/add.mjs','../../secret']},/MISSING_SYNTHETIC_TEST_PROOF/));
test('baseline falsely all green fails',()=>fails({testResult:{tests:3,pass:3,fail:0}},/UNVERIFIED_SYNTHETIC_REGRESSION/));
test('candidate not all green fails',()=>fails({proposalReview:{testResult:{tests:3,pass:2,fail:1,exitCode:1}}},/UNVERIFIED_SYNTHETIC_REGRESSION/));
test('plain-text proposal fails',()=>fails({proposal:'I fixed it'},/INVALID_PROPOSAL_JSON/));
test('file traversal proposal fails',()=>fails({proposal:JSON.stringify({path:'../../app/AgentActivity.kt',old:'a',replacement:'b'})},/OUT_OF_SCOPE_PATCH/));
test('arbitrary code cannot be promoted through a JSON patch',()=>fails({proposal:JSON.stringify({path:'src/add.mjs',old:'return Math.abs(a) + Math.abs(b);',replacement:'process.exit(0)'})},/OUT_OF_SCOPE_PATCH/));
test('extra proposal fields fail',()=>fails({proposal:JSON.stringify({...JSON.parse(PATCH),tool:'bash'})},/OUT_OF_SCOPE_PATCH/));
test('missing proposal is refused',()=>fails({proposal:null},/INVALID_PROPOSAL_BYTES/));

test('missing or mistyped owner-review requirement fails closed',()=>{
  for (const value of [undefined,null,1,'true']) fails({ownerReviewRequired:value},/UNAUTHORIZED_WRITE_REPORTED/);
});
