import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {proposeSecurityCodingPreview,verifySecurityCodingPreview} from './security-coding-preview.mjs';
const sha=x=>createHash('sha256').update(JSON.stringify(x)).digest('hex');
const sourceCommit='a'.repeat(40);
const result={advisoryId:'GHSA-aaaa-bbbb-cccc',ecosystem:'npm',packageName:'safe-demo',installedVersion:'1.2.3',match:'EXACT_LISTED_VERSION'};
const build=(finding=result,extras={})=>{
 const r={schema:'CONTINUUM_SECURITY_REVIEW_PROPOSAL_V1',version:1,project:'owner-project',sourceCommit,
  reportSha256:'b'.repeat(64),inventorySha256:'c'.repeat(64),advisorySetSha256:'d'.repeat(64),
  finding,disposition:finding.match==='EXACT_LISTED_VERSION'?'REVIEW_EXACT_ADVISORY_MATCH':'ANALYST_MUST_RESOLVE_AFFECTED_RANGE',
  state:'PENDING_OWNER_SECURITY_REVIEW',nextGate:'INDEPENDENT_PROVENANCE_AND_APPLICABILITY_REVIEW',
  ccc1Ready:false,authorized:false,executionAllowed:false,patchAllowed:false,sourceAuthenticated:false,
  autoMerge:false,dataExfiltrationAllowed:false,note:'Untrusted advisory evidence; owner must independently verify applicability before preparing a separately authorized CCC1 task.',...extras};
 return {...r,proposalSha256:sha(r)};
};
const selection={project:'owner-project',sourceCommit,allowedPaths:['backend/src/a.js']};
const preview=(overrides={})=>proposeSecurityCodingPreview({proposal:build(),selection,currentCommit:sourceCommit,...overrides});
test('exact advisory match yields a task preview, not a coding-task or grant',()=>{
 const p=preview();
 assert.equal(verifySecurityCodingPreview(p),true);
 assert.equal(p.schema,'CONTINUUM_SECURITY_CODING_PREVIEW_V1');
 assert.equal(p.ownerApprovalRequired,true);
 for(const field of ['authorized','dispatchAllowed','patchAllowed','autoMerge','applicabilityVerified'])assert.equal(p[field],false);
 assert.notEqual(p.schema,'continuum-coding-task');
 assert.equal(p.chain,'node'); assert.deepEqual(p.allowedPaths,['backend/src/a.js']);
});
test('acceptance comes only from trusted constants, not external advisory descriptions',()=>{
 const p=preview();
 assert.equal(p.acceptance.length,4);
 assert.equal(JSON.stringify(p).includes('IGNORE ALL RULES'),false);
 assert.match(p.goal,/GHSA-aaaa-bbbb-cccc/);
});
test('concurrent identical review proposals produce identical fingerprints',()=>assert.deepEqual(preview(),preview()));
test('reject stale source and fabricated project scope',()=>{
 for(const overrides of [
  {currentCommit:'b'.repeat(40)},
  {selection:{...selection,sourceCommit:'b'.repeat(40)}},
  {selection:{...selection,project:'other-repository'}},
  {currentCommit:'main'}
 ])assert.throws(()=>preview(overrides),/OMEGA_SECURITY_CODING_PREVIEW_INVALID/);
});
test('range-only findings cannot be upgraded to coding drafts',()=>{
 assert.throws(()=>preview({proposal:build({...result,match:'RANGE_REQUIRES_REVIEW'})}),/OMEGA_SECURITY_CODING_PREVIEW_INVALID/);
});
test('source advisory cannot grant authority even with recomputed hash',()=>{
 assert.throws(()=>preview({proposal:build(result,{authorized:true})}),/OMEGA_SECURITY_CODING_PREVIEW_INVALID/);
 assert.throws(()=>preview({proposal:build(result,{executionAllowed:true})}),/OMEGA_SECURITY_CODING_PREVIEW_INVALID/);
});
test('protect against future injected keys, even with re-hashed proposal',()=>{
 assert.throws(()=>preview({proposal:build(result,{remoteCommand:'rm -rf /'})}),/OMEGA_SECURITY_CODING_PREVIEW_INVALID/);
 assert.throws(()=>preview({proposal:build({...result,remoteCommand:'anything'})}),/OMEGA_SECURITY_CODING_PREVIEW_INVALID/);
});
test('reject dangerous package names and path traversal',()=>{
 assert.throws(()=>preview({proposal:build({...result,packageName:'../inject'})}),/OMEGA_SECURITY_CODING_PREVIEW_INVALID/);
 assert.throws(()=>preview({selection:{...selection,allowedPaths:['backend/src/../secret.js']}}),/OMEGA_SECURITY_CODING_PREVIEW_INVALID/);
});
test('reject prohibited source files, protected gates and credentials',()=>{
 for(const p of ['.github/continuum/repair-loop.mjs','scripts/install.js','backend/src/ToolGate.mjs','backend/src/credentials/secret.js','backend/test/api.test.js','backend/src/a.js\nnew'])
  assert.throws(()=>preview({selection:{...selection,allowedPaths:[p]}}),/OMEGA_SECURITY_CODING_PREVIEW_INVALID/);
});
test('reject mixed Android/Node source changes and duplicate entries',()=>{
 assert.throws(()=>preview({selection:{...selection,allowedPaths:['backend/src/a.js','app/src/main/java/com/example/B.kt']}}),/OMEGA_SECURITY_CODING_PREVIEW_INVALID/);
 assert.throws(()=>preview({selection:{...selection,allowedPaths:['backend/src/a.js','backend/src/a.js']}}),/OMEGA_SECURITY_CODING_PREVIEW_INVALID/);
});
test('valid Android-only paths may become non-executable Android previews',()=>{
 const p=preview({selection:{...selection,allowedPaths:['app/src/main/java/at/heinz/deviceai/SafeThing.kt']}});
 assert.equal(p.chain,'android'); assert.equal(p.dispatchAllowed,false);
});
test('no source paths and more than three source paths fail closed',()=>{
 for(const paths of [[],['lib/a.js','lib/b.js','lib/c.js','lib/d.js']])
  assert.throws(()=>preview({selection:{...selection,allowedPaths:paths}}),/OMEGA_SECURITY_CODING_PREVIEW_INVALID/);
});
test('preview tampering is detectable, even if authority flag changes',()=>{
 const p=preview();
 assert.equal(verifySecurityCodingPreview({...p,authorized:true}),false);
 assert.equal(verifySecurityCodingPreview({...p,goal:'changed'}),false);
 assert.equal(verifySecurityCodingPreview({...p,extra:'inject'}),false);
});
test('recomputed hash cannot turn a preview into an owner grant',()=>{
 const p=preview(); const {...other}=p;
 other.authorized=true; delete other.previewSha256;other.previewSha256=sha(other);
 assert.equal(verifySecurityCodingPreview(other),false);
});
test('invalid preview data and unsupported paths return false',()=>{
 for(const p of [null,{},[],{...preview(),allowedPaths:['scripts/x.js']}])assert.equal(verifySecurityCodingPreview(p),false);
});
