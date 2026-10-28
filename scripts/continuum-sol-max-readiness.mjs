#!/usr/bin/env node
/** Read-only, fail-closed evidence classifier for a Continuum Lokal handoff.
 * Does not call model services, write code, collect secrets, invoke git writes,
 * mutate app data, or approve a release. Node.js >=20, built-ins only.
 */
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import crypto from 'node:crypto';

const APP = 'at.heinz.deviceai.continuumlocal';
const OLD = 'at.heinz.deviceai.mentorpaired';
const EXPECTED = '0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0';
const PHASES = Object.freeze(['SAF_OPENED','COPY_COMPLETED','SHA256_VERIFIED','INSPECT_OK','RUNTIME_READY','FIRST_NATIVE_RESPONSE','COLD_REOPEN_READY']);
const plain = o => o !== null && typeof o === 'object' && !Array.isArray(o);
function object(o, allowed) {
  if (!plain(o) || Object.keys(o).some(k=>!allowed.includes(k))) throw Error('INVALID_EVIDENCE_SCHEMA');
  return o;
}
function boundedInt(x,max) { return Number.isSafeInteger(x) && x>=0 && x<=max; }
function regular(parent, relative) {
  let current=path.resolve(parent);
  if (!fs.statSync(current).isDirectory() || fs.lstatSync(current).isSymbolicLink() ||
      fs.realpathSync(current)!==current) throw Error('UNSAFE_ROOT');
  for(const p of relative.split('/')) {
    current=path.join(current,p);
    const st=fs.lstatSync(current);
    if(st.isSymbolicLink()) throw Error('SYMLINK_SOURCE_REFUSED');
  }
  if (!fs.statSync(current).isFile() || fs.statSync(current).size>300000 || fs.statSync(current).size===0)
    throw Error('UNSAFE_SOURCE');
  return fs.readFileSync(current,'utf8');
}
export function classifyBuildSource({gradle,regression,ownerGate,release}) {
  for (const x of [gradle,regression,ownerGate,release])
    if (typeof x !== 'string' || x.length>300000) throw Error('INVALID_SOURCE_INPUT');
  const appIdentity = gradle.includes('"at.heinz.deviceai.continuumlocal"') &&
    gradle.includes('continuumLocalStandalone');
  const releaseIdentity = release.includes('-PcontinuumLocalStandalone=true') &&
    release.includes("at.heinz.deviceai.continuumlocal");
  const gateIsolation = ownerGate.includes('CONTINUUM_OWNER_LOCAL_APPROVED_SHA') &&
    ownerGate.includes('continuum-fold7-standalone-apk.sh') &&
    !ownerGate.includes('continuum-local-owner-apk.sh');
  const fullRegressionConfigured = regression.includes(':app:testDebugUnitTest') &&
    regression.includes(':app:lintDebug') && regression.includes('--offline');
  const legacyVariant = regression.includes('-PdeviceAiMentorPreview=true');
  const independentStandaloneQa = /-PcontinuumLocalStandaloneQa=true/.test(regression) &&
    gradle.includes('continuumLocalStandaloneQa');
  const reasons=[];
  if(!appIdentity) reasons.push('MAIN_APP_IDENTITY_NOT_VERIFIED');
  if(!releaseIdentity) reasons.push('LOCAL_RELEASE_ROUTE_NOT_VERIFIED');
  if(!gateIsolation) reasons.push('OWNER_GATE_NOT_VERIFIED');
  if(!fullRegressionConfigured) reasons.push('FULL_REGRESSION_NOT_CONFIGURED');
  if(legacyVariant && !independentStandaloneQa) reasons.push('LEGACY_MENTOR_PREVIEW_TEST_VARIANT');
  if(!independentStandaloneQa) reasons.push('STANDALONE_UNSIGNED_QA_VARIANT_NOT_VERIFIED');
  return Object.freeze({appId:APP,appIdentity,releaseIdentity,gateIsolation,
    fullRegressionConfigured,legacyVariant,independentStandaloneQa,
    androidRegressionActuallyRun:false,deviceActuallyTested:false,
    signedApkProduced:false,readyForOwnerRelease:false,blockers:reasons});
}
export function classifyImportEvidence(data) {
  object(data,['appId','modelSha256','phases','reportKind','nativeResponseVerified','fold7Observed']);
  if(data.appId!==APP || data.reportKind!=='OWNER_DEVICE_PHASE_REPORT')
    throw Error('INVALID_IMPORT_PROVENANCE');
  if(typeof data.modelSha256!=='string' || !/^[a-f0-9]{64}$/.test(data.modelSha256))
    throw Error('INVALID_MODEL_HASH');
  if(!Array.isArray(data.phases) || data.phases.length>PHASES.length)
    throw Error('INVALID_IMPORT_PHASES');
  let prev=-1,lastMs=-1;
  const accepted=[];
  for(const p of data.phases) {
    object(p,['name','elapsedMs','copiedBytes']);
    const position=PHASES.indexOf(p.name);
    if(position<0 || position<=prev || !boundedInt(p.elapsedMs,86400000) || p.elapsedMs<lastMs)
      throw Error('INVALID_IMPORT_PHASE_ORDER');
    if(p.copiedBytes!==undefined && (!boundedInt(p.copiedBytes,64*1024*1024*1024) || p.name!=='COPY_COMPLETED'))
      throw Error('INVALID_IMPORT_COPY_COUNT');
    accepted.push(p.name);prev=position;lastMs=p.elapsedMs;
  }
  // An externally supplied report is never enough to prove model weights,
  // genuine native inference or a Fold7 run without independent evidence.
  const sourceHashMatchesExpected=data.modelSha256===EXPECTED;
  const firstReplyReported=accepted.includes('FIRST_NATIVE_RESPONSE') && data.nativeResponseVerified===true;
  return Object.freeze({appId:APP,sourceHashMatchesExpected,
    phasesReported:accepted,firstReplyReported,fold7Claimed:data.fold7Observed===true,
    modelWeightsCryptographicallyObservedByThisTool:false,
    nativeInferenceIndependentlyVerified:false,
    actualModelReady:false,
    nextPhase:PHASES.find(x=>!accepted.includes(x))??null,
    blockers:[...(!sourceHashMatchesExpected?['MODEL_HASH_MISMATCH']:[]),
      ...(!firstReplyReported?['FIRST_NATIVE_RESPONSE_NOT_PROVEN']:[]),
      'INDEPENDENT_FOLD7_RUNTIME_PROOF_REQUIRED']});
}
export function classifyLearningContinuity(data) {
  object(data,['oldApp','newApp','oldSwbUpdates','newSwbUpdates','oldArchiveSources','newArchiveSources',
    'oldCheckpointSha256','newCheckpointSha256','frozenHoldoutVerified','nativeGemmaTrainingVerified']);
  if(data.oldApp!==OLD||data.newApp!==APP)throw Error('MISMATCHED_APP_ISOLATION');
  for(const n of ['oldSwbUpdates','newSwbUpdates','oldArchiveSources','newArchiveSources'])
    if(data[n]!==null&&!boundedInt(data[n],1000000000))throw Error('INVALID_LEARNING_COUNTER');
  const hash=(s)=>s===null || (typeof s==='string'&&/^[0-9a-f]{64}$/.test(s));
  if(!hash(data.oldCheckpointSha256)||!hash(data.newCheckpointSha256))throw Error('INVALID_CHECKPOINT_HASH');
  if(typeof data.frozenHoldoutVerified!=='boolean'||typeof data.nativeGemmaTrainingVerified!=='boolean')
    throw Error('INVALID_PROOF_FLAG');
  const hashesEqual=data.oldCheckpointSha256!==null&&
    data.oldCheckpointSha256===data.newCheckpointSha256;
  return Object.freeze({oldApp:OLD,newApp:APP,checkpointHashesMatch:hashesEqual,
    countersAreGemmaTraining:false,stableHoldoutReported:data.frozenHoldoutVerified,
    modelTransferIndependentlyVerified:false,ownerDataTransferAllowed:false,
    blockers:[...(!hashesEqual?['CHECKPOINT_IDENTITY_NOT_PROVEN']:[]),
      ...(!data.frozenHoldoutVerified?['FROZEN_HOLDOUT_NOT_VERIFIED']:[]),
      'OWNER_APPROVAL_AND_DEVICE_ISOLATION_REQUIRED']});
}
export function classifyCodingBridge(data) {
  object(data,['model','mode','transportKind','completed','observedReadCalls','observedTestCall',
    'proposalVerifiedOnIsolatedCopy','patchApplied','repositoryAccess','usageCostVerified',
    'verifiedModelWeights','verifiedNoUpstreamProxy','modelIdentityExternallyVerified']);
  if(typeof data.model!=='string'||data.model.length>80)throw Error('INVALID_MODEL_ID');
  if(data.mode!=='SYNTHETIC_ONLY'||!['MOCKED_OFFLINE','SELF_HOSTED_LOOPBACK','EXPERIENTIAL_FREE_ONLY'].includes(data.transportKind))
    throw Error('UNTRUSTED_MODEL_TRANSPORT');
  if(!Array.isArray(data.observedReadCalls)||data.observedReadCalls.length>2||
    data.observedReadCalls.some(p=>!['src/add.mjs','test/add.test.mjs'].includes(p)))
    throw Error('UNTRUSTED_FILE_READ');
  const isFreeCloud=data.transportKind==='EXPERIENTIAL_FREE_ONLY';
  if(isFreeCloud&&!['claude-fable-5.1:free','gpt-6-astra:free'].includes(data.model))
    throw Error('PAID_MODEL_FALLBACK_REFUSED');
  if(data.patchApplied!==false||data.repositoryAccess!==false)throw Error('MODEL_ALREADY_TOUCHED_REPOSITORY');
  const completed=data.completed===true && data.observedReadCalls.length===2 &&
    data.observedTestCall===true && data.proposalVerifiedOnIsolatedCopy===true;
  return Object.freeze({modelLabel:data.model,reportedSyntheticSuccess:completed,
    actualGenerativeModelVerified:false,privateRepositoryAccessAllowed:false,
    draftPrAuthorityGranted:false,releaseAuthorityGranted:false,
    blockers:['GENUINE_MODEL_SESSION_NOT_INDEPENDENTLY_VERIFIED',
      'PRIVATE_REPOSITORY_ACCESS_REQUIRES_EXPLICIT_OWNER_APPROVAL']});
}
export function readOnlySourceAudit(root) {
  const gradle=regular(root,'app/build.gradle.kts');
  const regression=regular(root,'scripts/continuum-fusion-full-regression.sh');
  const ownerGate=regular(root,'scripts/continuum-fold7-owner-update-gate.sh');
  const release=regular(root,'scripts/continuum-fold7-standalone-apk.sh');
  const result=classifyBuildSource({gradle,regression,ownerGate,release});
  const digest=crypto.createHash('sha256');
  for(const str of [gradle,regression,ownerGate,release])digest.update(str);
  return {...result,sourceEvidenceSha256:digest.digest('hex')};
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
  try{
    if(process.argv.length!==4||process.argv[2]!=='--audit-source')throw Error('USAGE');
    const report=readOnlySourceAudit(process.argv[3]);
    process.stdout.write(JSON.stringify(report,null,2)+'\n');
    if(report.blockers.length)process.exitCode=69;
  }catch(e){
    const code=/^[A-Z0-9_]+$/.test(e.message||'')?e.message:'SAFE_AUDIT_FAILED';
    process.stderr.write('CONTINUUM_SOL_READINESS_BLOCKED='+code+'\n');
    process.exitCode=69;
  }
}
