import test from 'node:test';
import assert from 'node:assert/strict';
import {createFold7AcceptanceTemplate,assessFold7Acceptance}
 from './continuum-fold7-acceptance.mjs';
const sha='e06e17a4e8ba12a1dce3d5308e5eb455cda30b6d';
test('default device checklist honestly reports NOT_RUN',()=>{
 const p=createFold7AcceptanceTemplate(sha),r=assessFold7Acceptance(p,sha);
 assert.equal(p.tests.length,12);assert.equal(r.status,'INCOMPLETE');
 assert.equal(r.completed,0);assert.equal(r.releaseAuthorized,false);
});
test('falsely marked pass without evidence is rejected',()=>{
 const p=createFold7AcceptanceTemplate(sha);p.tests[0].status='PASS';
 assert.throws(()=>assessFold7Acceptance(p,sha),/INVALID_DEVICE_TEST_EVIDENCE/);
});
test('even twelve self-reported passes do not authorize release',()=>{
 const p=createFold7AcceptanceTemplate(sha);
 p.installedApkSha256='a'.repeat(64);p.modelSha256='b'.repeat(64);
 for(const t of p.tests){t.status='PASS';t.evidenceSha256='c'.repeat(64);t.elapsedMs=10;}
 const r=assessFold7Acceptance(p,sha);
 assert.equal(r.status,'REPORTED_COMPLETE_NOT_ATTESTED');
 assert.equal(r.deviceExecutionIndependentlyVerified,false);
 assert.equal(r.releaseAuthorized,false);
});
test('foreign source and reordered cases are rejected',()=>{
 const p=createFold7AcceptanceTemplate(sha);
 assert.throws(()=>assessFold7Acceptance(p,'b'.repeat(40)),/INVALID_DEVICE_REPORT/);
 [p.tests[0],p.tests[1]]=[p.tests[1],p.tests[0]];
 assert.throws(()=>assessFold7Acceptance(p,sha),/MISSING_OR_REORDERED/);
});
