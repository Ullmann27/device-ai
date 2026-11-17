import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {classifyBuildSource,classifyImportEvidence,classifyLearningContinuity,
  classifyCodingBridge,readOnlySourceAudit} from './continuum-sol-max-readiness.mjs';
const self=fileURLToPath(new URL('./continuum-sol-max-readiness.mjs',import.meta.url));
const good={
  gradle:'val continuumLocalStandalone=true; applicationId = if (continuumLocalStandalone) "at.heinz.deviceai.continuumlocal" else "at.heinz.deviceai"',
  regression:'--offline -PdeviceAiMentorPreview=true :app:testDebugUnitTest :app:lintDebug',
  ownerGate:'CONTINUUM_OWNER_LOCAL_APPROVED_SHA continuum-fold7-standalone-apk.sh',
  release:'-PcontinuumLocalStandalone=true at.heinz.deviceai.continuumlocal'
};
const imported={appId:'at.heinz.deviceai.continuumlocal',
  reportKind:'OWNER_DEVICE_PHASE_REPORT',
  modelSha256:'0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0',
  phases:[{name:'SAF_OPENED',elapsedMs:0},{name:'COPY_COMPLETED',elapsedMs:158000,copiedBytes:3659530240},
    {name:'SHA256_VERIFIED',elapsedMs:160000}],nativeResponseVerified:false,fold7Observed:true};
const swb={oldApp:'at.heinz.deviceai.mentorpaired',newApp:'at.heinz.deviceai.continuumlocal',
  oldSwbUpdates:331570,newSwbUpdates:4000,oldArchiveSources:538,newArchiveSources:532,
  oldCheckpointSha256:null,newCheckpointSha256:null,frozenHoldoutVerified:false,nativeGemmaTrainingVerified:false};
const synthetic={model:'local/owned-coder',mode:'SYNTHETIC_ONLY',transportKind:'SELF_HOSTED_LOOPBACK',
  completed:true,observedReadCalls:['src/add.mjs','test/add.test.mjs'],observedTestCall:true,
  proposalVerifiedOnIsolatedCopy:true,patchApplied:false,repositoryAccess:false,
  usageCostVerified:false,verifiedModelWeights:false,verifiedNoUpstreamProxy:false,modelIdentityExternallyVerified:false};
function sourceFixture(run){
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-sol-readiness-'));
  const map={
    'app/build.gradle.kts':good.gradle,
    'scripts/continuum-fusion-full-regression.sh':good.regression,
    'scripts/continuum-fold7-owner-update-gate.sh':good.ownerGate,
    'scripts/continuum-fold7-standalone-apk.sh':good.release
  };
  for(const [rel,content] of Object.entries(map)){
    const target=path.join(root,rel);fs.mkdirSync(path.dirname(target),{recursive:true});fs.writeFileSync(target,content);
  }
  try{return run(root);}finally{fs.rmSync(root,{recursive:true,force:true});}
}
test('milestone 1: source gate selects actual Continuum Lokal rather than historical Android main',()=>{
  const a=classifyBuildSource(good);
  assert.equal(a.appId,'at.heinz.deviceai.continuumlocal');
  assert.equal(a.appIdentity,true);assert.equal(a.gateIsolation,true);assert.equal(a.releaseIdentity,true);
  assert.equal(a.readyForOwnerRelease,false);assert.equal(a.signedApkProduced,false);
});
test('milestone 1: legacy Gradle test flag may not be advertised as standalone regression',()=>{
  const a=classifyBuildSource(good);
  assert.ok(a.blockers.includes('LEGACY_MENTOR_PREVIEW_TEST_VARIANT'));
  assert.equal(a.independentStandaloneQa,false);assert.equal(a.androidRegressionActuallyRun,false);
});
test('milestone 1: failing package, signer or regression checks are explicit blockers',()=>{
  const bad=classifyBuildSource({...good,gradle:'applicationId="at.heinz.deviceai"',ownerGate:'continuum-local-owner-apk.sh',regression:'echo skipped'});
  assert.ok(bad.blockers.includes('MAIN_APP_IDENTITY_NOT_VERIFIED'));
  assert.ok(bad.blockers.includes('OWNER_GATE_NOT_VERIFIED'));
  assert.ok(bad.blockers.includes('FULL_REGRESSION_NOT_CONFIGURED'));
});
test('milestone 2: import phase evidence never converts picker/file hash into native inference',()=>{
  const r=classifyImportEvidence(imported);
  assert.deepEqual(r.phasesReported,['SAF_OPENED','COPY_COMPLETED','SHA256_VERIFIED']);
  assert.equal(r.sourceHashMatchesExpected,true);
  assert.equal(r.nativeInferenceIndependentlyVerified,false);
  assert.equal(r.nextPhase,'INSPECT_OK');
});
test('milestone 2: genuine reply report remains unverified without independent device source',()=>{
  const r=classifyImportEvidence({...imported,phases:[...imported.phases,
    {name:'INSPECT_OK',elapsedMs:175000},{name:'RUNTIME_READY',elapsedMs:190000},
    {name:'FIRST_NATIVE_RESPONSE',elapsedMs:193000}],nativeResponseVerified:true});
  assert.equal(r.firstReplyReported,true);
  assert.equal(r.actualModelReady,false);
  assert.ok(r.blockers.includes('INDEPENDENT_FOLD7_RUNTIME_PROOF_REQUIRED'));
});
test('milestone 2: reordered phases, suspicious byte counts and foreign paths are refused',()=>{
  assert.throws(()=>classifyImportEvidence({...imported,phases:[{name:'RUNTIME_READY',elapsedMs:5},{name:'SAF_OPENED',elapsedMs:4}]}),/INVALID_IMPORT_PHASE_ORDER/);
  assert.throws(()=>classifyImportEvidence({...imported,phases:[{name:'SAF_OPENED',elapsedMs:4,copiedBytes:123}]}),/INVALID_IMPORT_COPY_COUNT/);
  assert.throws(()=>classifyImportEvidence({...imported,privateModelPath:'/sdcard/private/model'}),/INVALID_EVIDENCE_SCHEMA/);
});
test('milestone 3: isolated installed app counters cannot prove migration or Gemma training',()=>{
  const r=classifyLearningContinuity(swb);
  assert.equal(r.ownerDataTransferAllowed,false);assert.equal(r.countersAreGemmaTraining,false);
  assert.ok(r.blockers.includes('CHECKPOINT_IDENTITY_NOT_PROVEN'));
});
test('milestone 3: identical checkpoint hashes still cannot authorize data transfer',()=>{
  const hash='b'.repeat(64);
  const r=classifyLearningContinuity({...swb,oldCheckpointSha256:hash,newCheckpointSha256:hash,frozenHoldoutVerified:true});
  assert.equal(r.checkpointHashesMatch,true);assert.equal(r.ownerDataTransferAllowed,false);
  assert.equal(r.modelTransferIndependentlyVerified,false);
});
test('milestone 3: swapped apps, negative counters and false evidence fields fail',()=>{
  assert.throws(()=>classifyLearningContinuity({...swb,oldApp:swb.newApp}),/MISMATCHED_APP_ISOLATION/);
  assert.throws(()=>classifyLearningContinuity({...swb,newSwbUpdates:-1}),/INVALID_LEARNING_COUNTER/);
  assert.throws(()=>classifyLearningContinuity({...swb,oldCheckpointSha256:'garbage'}),/INVALID_CHECKPOINT_HASH/);
});
test('milestone 4: simulated local bridge can prove fixture protocol, never private repo access',()=>{
  const r=classifyCodingBridge(synthetic);
  assert.equal(r.reportedSyntheticSuccess,true);
  assert.equal(r.actualGenerativeModelVerified,false);
  assert.equal(r.privateRepositoryAccessAllowed,false);
  assert.equal(r.draftPrAuthorityGranted,false);
});
test('milestone 4: unsuffixed paid Cloud model is rejected',()=>{
  assert.throws(()=>classifyCodingBridge({...synthetic,model:'claude-fable-5.1',transportKind:'EXPERIENTIAL_FREE_ONLY'}),/PAID_MODEL_FALLBACK_REFUSED/);
  assert.throws(()=>classifyCodingBridge({...synthetic,model:'gpt-6-astra',transportKind:'EXPERIENTIAL_FREE_ONLY'}),/PAID_MODEL_FALLBACK_REFUSED/);
});
test('milestone 4: model reports touching actual repo or external paths are refused',()=>{
  assert.throws(()=>classifyCodingBridge({...synthetic,repositoryAccess:true}),/MODEL_ALREADY_TOUCHED_REPOSITORY/);
  assert.throws(()=>classifyCodingBridge({...synthetic,observedReadCalls:['../../.ssh/id_rsa']}),/UNTRUSTED_FILE_READ/);
  assert.throws(()=>classifyCodingBridge({...synthetic,patchApplied:true}),/MODEL_ALREADY_TOUCHED_REPOSITORY/);
});
test('milestone 5: source CLI checks exact bounded source and exits 69 for release blockers',()=>sourceFixture(root=>{
  const before=fs.readFileSync(path.join(root,'app/build.gradle.kts'));
  const r=spawnSync(process.execPath,[self,'--audit-source',root],{encoding:'utf8',timeout:5000});
  assert.equal(r.status,69,r.stderr);
  const report=JSON.parse(r.stdout);
  assert.equal(report.appId,'at.heinz.deviceai.continuumlocal');
  assert.equal(report.fullRegressionConfigured,true);
  assert.ok(report.sourceEvidenceSha256.match(/^[a-f0-9]{64}$/));
  assert.deepEqual(fs.readFileSync(path.join(root,'app/build.gradle.kts')),before);
}));
test('milestone 5: symlink source and missing root fail closed without path leakage',()=>sourceFixture(root=>{
  const target=path.join(root,'scripts/continuum-fold7-standalone-apk.sh');
  fs.unlinkSync(target);fs.symlinkSync('/etc/passwd',target);
  assert.throws(()=>readOnlySourceAudit(root),/SYMLINK_SOURCE_REFUSED/);
  const r=spawnSync(process.execPath,[self,'--audit-source',root],{encoding:'utf8'});
  assert.equal(r.status,69);assert.match(r.stderr,/SYMLINK_SOURCE_REFUSED/);
  assert.doesNotMatch(r.stderr,/etc\/passwd/);
}));
test('milestone 5: invalid import report cannot contain credentials or arbitrary keys',()=>{
  assert.throws(()=>classifyImportEvidence({...imported,oauthToken:'secret'}),/INVALID_EVIDENCE_SCHEMA/);
  assert.equal(classifyImportEvidence({...imported,modelSha256:'a'.repeat(64)}).sourceHashMatchesExpected,false);
});
