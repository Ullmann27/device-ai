/**
 * Owner-invoked CEFR research lifecycle: independent compiler+test execution,
 * copy-on-write CRE1 training, disjoint holdout, and optional CAS promotion.
 * This DOES NOT modify production repair ordering or personal Android models.
 */
import {RelationalEncoder} from './cre-encoder.mjs';
import {verifyIndependentCefrPatch} from './cefr-independent-verifier.mjs';
import {loadCefrCheckpoint,promoteCefrCheckpoint} from './cefr-dev-checkpoint.mjs';
import {trainCefrCandidateFromVerifiedReport,validateCefrHoldout,
 DEV_CRE1_FILENAME} from './cefr-repair-ranker.mjs';

export function runCefrResearchCycle({
 repoRoot,stateDir,report,patchText,holdoutCases,deadline,
 promote=false,initialSeed=109
}={}){
 if(typeof promote!=='boolean'||!Number.isSafeInteger(initialSeed)||
    initialSeed<0||initialSeed>2147483647)throw Error('INVALID_CEFR_RESEARCH_OPTIONS');
 // The research set must be bounded and labeled before an expensive test run.
 validateCefrHoldout(holdoutCases);
 const active=loadCefrCheckpoint({stateDir,repoRoot});
 const baseline=active.bytes??new RelationalEncoder(initialSeed).save();
 const independent=verifyIndependentCefrPatch({root:repoRoot,report,patchText,deadline});
 if(!independent.passed){
  return {trained:false,promoted:false,reason:'INDEPENDENT_CHECK_FAILED',
   modelFile:DEV_CRE1_FILENAME,baselineSha256:active.sha256,independent};
 }
 // This callback is created within trusted local worker code, after it has
 // executed the independent test run. Untrusted report fields cannot forge it.
 const trained=trainCefrCandidateFromVerifiedReport({
  baselineBytes:baseline,report,patchText,holdoutCases,
  verifyIndependent:()=>independent
 });
 if(!trained.accepted){
  return {trained:false,promoted:false,reason:trained.reason,
   modelFile:DEV_CRE1_FILENAME,baselineSha256:active.sha256,independent};
 }
 if(!promote){
  return {trained:true,promoted:false,reason:'SHADOW_ONLY',
   modelFile:DEV_CRE1_FILENAME,baselineSha256:active.sha256,
   candidateSha256:trained.activeSha256,
   holdoutBefore:trained.holdoutBefore,holdoutAfter:trained.holdoutAfter,independent};
 }
 const checkpoint=promoteCefrCheckpoint({stateDir,repoRoot,
  promotion:trained,expectedSha256:active.sha256});
 return {trained:true,promoted:true,reason:'RESEARCH_CHECKPOINT_PROMOTED',
  modelFile:DEV_CRE1_FILENAME,baselineSha256:active.sha256,
  candidateSha256:checkpoint.sha256,
  holdoutBefore:trained.holdoutBefore,holdoutAfter:trained.holdoutAfter,independent};
}
