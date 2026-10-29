import test from 'node:test';
import assert from 'node:assert/strict';
import {checkHostEvidence} from './continuum-host-evidence-gate.mjs';
const sha='e06e17a4e8ba12a1dce3d5308e5eb455cda30b6d';
function evidence() {
 const counts={tests:1268,failures:0,errors:0,skipped:0};
 const full={commit:sha,status:'PASS',counts,suites:240,
  gradleExit:0,nodeExit:0,stale:false};
 return {expectedSha:sha,full,manifest:{commit:sha,
  files:Object.fromEntries(Array.from({length:11},(_,i)=>['path'+i,'a'.repeat(64)]))},
  qa:{testedCode:sha,android:{...full,executedOnDevice:false,
   modelInferenceVerified:false,qualityImprovementProven:false},
   workerNode:{pass:390,fail:0,skip:0,exit:0},
   canonicalNode:{pass:226,fail:0,skip:0,exit:0},
   python:{pass:10,fail:0,exit:0},
   notRun:['Fold7','native Gemma inference','instrumentation','APK/build/sign/install']}};
}
test('host evidence never grants device, native Gemma, APK or production merge',()=>{
 const r=checkHostEvidence(evidence());
 assert.equal(r.hostEvidence,'CONSISTENT_WITH_RECORDED_QA');
 assert.equal(r.deviceAcceptance,'NOT_RUN');
 assert.equal(r.productionMergeAuthorized,false);
 assert.equal(r.intelligentImprovementProven,false);
});
test('source mismatch, incomplete Android suite or missing test evidence rejects',()=>{
 const e=evidence();e.full.commit='b'.repeat(40);
 assert.throws(()=>checkHostEvidence(e),/SOURCE_IDENTITY/);
 const f=evidence();f.qa.android.counts={...f.qa.android.counts,failures:1};
 assert.throws(()=>checkHostEvidence(f),/ANDROID_FAILURES/);
 const g=evidence();g.qa.workerNode.pass=389;
 assert.throws(()=>checkHostEvidence(g),/SECONDARY_QA/);
});
test('absence of on-device testing cannot be relabelled as proven',()=>{
 const e=evidence();e.qa.android.executedOnDevice=true;
 assert.throws(()=>checkHostEvidence(e),/DEVICE_OR_INTELLIGENCE/);
 const f=evidence();f.qa.notRun=[];
 assert.throws(()=>checkHostEvidence(f),/MISSING_PENDING/);
});
