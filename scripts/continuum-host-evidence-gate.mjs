/** Host-evidence consistency check, NOT an APK signature or device acceptance. */
import fs from 'node:fs';
import path from 'node:path';
import {verifyEvidenceManifest} from './continuum-evidence-integrity.mjs';
const HASH=/^[a-f0-9]{40}$/,HASH256=/^[a-f0-9]{64}$/;
export function checkHostEvidence({expectedSha,qa,full,manifest}) {
 if(!HASH.test(expectedSha??'')||!qa||!full||!manifest||
   qa.testedCode!==expectedSha||qa.android?.commit!==expectedSha||
   full.commit!==expectedSha||manifest.commit!==expectedSha)
  throw Error('SOURCE_IDENTITY_MISMATCH');
 const android=qa.android;
 if(android.status!=='PASS'||full.status!=='PASS'||android.gradleExit!==0||
  android.nodeExit!==0||full.gradleExit!==0||full.nodeExit!==0||
  android.stale!==false||full.stale!==false||
  android.counts?.tests!==1268||full.counts?.tests!==1268||
  android.suites!==240||full.suites!==240)
  throw Error('ANDROID_QA_INCOMPLETE');
 for(const x of [android.counts,full.counts]){
  if(x.failures!==0||x.errors!==0||x.skipped!==0)throw Error('ANDROID_FAILURES');
 }
 for(const [key,n] of [['workerNode',390],['canonicalNode',226],['python',10]]){
  const t=qa[key];
  if(t?.pass!==n||t.fail!==0||t.exit!==0||
   (t.skip!==undefined&&t.skip!==0))throw Error('SECONDARY_QA_INCOMPLETE');
 }
 if(!manifest.files||Object.keys(manifest.files).length<10||
  Object.values(manifest.files).some(v=>!HASH256.test(v)))throw Error('SOURCE_MANIFEST_INVALID');
 if(android.executedOnDevice!==false||android.modelInferenceVerified!==false||
  android.qualityImprovementProven!==false)throw Error('DEVICE_OR_INTELLIGENCE_CLAIM_UNSUPPORTED');
 const pending=['Fold7','native Gemma inference','instrumentation','APK/build/sign/install'];
 if(!Array.isArray(qa.notRun)||pending.some(x=>!qa.notRun.includes(x)))
  throw Error('MISSING_PENDING_DEVICE_DISCLOSURES');
 return Object.freeze({schema:1,sourceCommit:expectedSha,
  hostEvidence:'CONSISTENT_WITH_RECORDED_QA',deviceAcceptance:'NOT_RUN',
  nativeGemma:'NOT_RUN',apk:'NOT_BUILT_OR_SIGNED',productionMergeAuthorized:false,
  intelligentImprovementProven:false,nodeGroupsOverlap:true});
}
export function readHostEvidence(root,expectedSha){
 const base=path.resolve(root);
 const read=rel=>JSON.parse(fs.readFileSync(path.join(base,rel),'utf8'));
 const result=checkHostEvidence({expectedSha,qa:read('qa-summary.json'),
  full:read('final-qa/summary.json'),manifest:read('tested-source-manifest.json')});
 const integrity=verifyEvidenceManifest(base);
 return {...result,evidenceIntegrity:integrity};
}
if(process.argv[1]&&path.resolve(process.argv[1])===path.resolve(new URL(import.meta.url).pathname)){
 try {
  if(process.argv.length!==4)throw Error('USAGE: node scripts/continuum-host-evidence-gate.mjs <40-hex-sha> <evidence-dir>');
  console.log(JSON.stringify(readHostEvidence(process.argv[3],process.argv[2]),null,2));
  process.exitCode=2; // Host PASS is not device/release PASS.
 }catch(e){console.error('HOST_EVIDENCE_REJECTED: '+e.message);process.exitCode=1;}
}
