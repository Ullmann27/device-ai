import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {draftOwnedSecurityReview,verifyOwnedSecurityProposal} from './security-review-proposal.mjs';
const sha=x=>createHash('sha256').update(JSON.stringify(x)).digest('hex');
const match={advisoryId:'OSV-2026-1001',ecosystem:'npm',packageName:'pkg',installedVersion:'1.2.3',match:'EXACT_LISTED_VERSION',action:'OWNER_SECURITY_REVIEW_REQUIRED'};
const reportWith=(finding=match)=>{
 const body={schema:'CONTINUUM_SECURITY_REVIEW_V1',project:'owner-project',sourceCommit:'a'.repeat(40),inventorySha256:'b'.repeat(64),advisorySetSha256:'c'.repeat(64),checkedComponents:1,checkedAdvisories:1,findings:[finding],reportStatus:'SECURITY_FINDINGS_PENDING_OWNER_REVIEW',coverage:'SUPPLIED_ADVISORIES_AND_EXACT_VERSION_LISTS_ONLY',vulnerabilityFreeClaim:false,patchesGenerated:0,automaticActions:0};
 return {...body,reportSha256:sha(body)};
};
const id={advisoryId:match.advisoryId,ecosystem:match.ecosystem,packageName:match.packageName,installedVersion:match.installedVersion};
test('exact version match produces non-executable review proposal',()=>{
 const result=draftOwnedSecurityReview({report:reportWith(),finding:id});
 assert.equal(result.disposition,'REVIEW_EXACT_ADVISORY_MATCH');
 assert.equal(result.ccc1Ready,false); assert.equal(result.patchAllowed,false);
 assert.equal(result.executionAllowed,false);assert.equal(result.authorized,false);
 assert.equal(verifyOwnedSecurityProposal(result),true);
});
test('range is unresolved and cannot become a coding grant',()=>{
 const result=draftOwnedSecurityReview({report:reportWith({...match,match:'RANGE_REQUIRES_REVIEW'}),finding:id});
 assert.equal(result.disposition,'ANALYST_MUST_RESOLVE_AFFECTED_RANGE');
 assert.equal(result.ccc1Ready,false);
});
test('unauthorized and unknown findings are rejected',()=>{
 assert.throws(()=>draftOwnedSecurityReview({report:reportWith(),finding:{...id,advisoryId:'OSV-2026-unknown'}}),/SECURITY_REVIEW_PROPOSAL_INVALID/);
 assert.throws(()=>draftOwnedSecurityReview({report:reportWith(),finding:{...id,extra:true}}),/SECURITY_REVIEW_PROPOSAL_INVALID/);
});
test('report tampering and incomplete hashes fail closed',()=>{
 const r=reportWith();
 assert.throws(()=>draftOwnedSecurityReview({report:{...r,automaticActions:1},finding:id}),/SECURITY_REVIEW_PROPOSAL_INVALID/);
 assert.throws(()=>draftOwnedSecurityReview({report:{...r,reportSha256:'0'.repeat(64)},finding:id}),/SECURITY_REVIEW_PROPOSAL_INVALID/);
});
test('malformed purported findings fail even when report fingerprint is recomputed',()=>{
 for(const bad of [{...match,packageName:'../escape'}, {...match,match:'VERIFIED_SAFE'}, {...match,extra:'injected'}]){
  assert.throws(()=>draftOwnedSecurityReview({report:reportWith(bad),finding:id}),/SECURITY_REVIEW_PROPOSAL_INVALID/);
 }
});
test('unknown report fields and duplicates reject even with recomputed hashes',()=>{
 const report=reportWith();const {reportSha256,...body}=report;
 const duplicate={...body,findings:[match,match]};
 assert.throws(()=>draftOwnedSecurityReview({report:{...duplicate,reportSha256:sha(duplicate)},finding:id}),/SECURITY_REVIEW_PROPOSAL_INVALID/);
 const extraneous={...body,approved:true};
 assert.throws(()=>draftOwnedSecurityReview({report:{...extraneous,reportSha256:sha(extraneous)},finding:id}),/SECURITY_REVIEW_PROPOSAL_INVALID/);
});
test('proposal detects mutation and falsified authority flags',()=>{
 const p=draftOwnedSecurityReview({report:reportWith(),finding:id});
 assert.equal(verifyOwnedSecurityProposal({...p,authorized:true}),false);
 assert.equal(verifyOwnedSecurityProposal({...p,proposalSha256:'f'.repeat(64)}),false);
 assert.equal(verifyOwnedSecurityProposal(null),false);
});
test('receipt is deterministic on repeated input',()=>{
 const a=draftOwnedSecurityReview({report:reportWith(),finding:id});
 const b=draftOwnedSecurityReview({report:reportWith(),finding:id});
 assert.deepEqual(a,b);
});
