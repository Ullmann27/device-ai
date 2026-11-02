/** Research-only CCC1 + counterexample evidence aggregation. No patch approval or training. */
const SHA256=/^[a-f0-9]{64}$/;
const SHA1=/^[a-f0-9]{40}$/;
const CASE_LIMIT=100000;

function executedEvidence(item) {
  if(!item||item.profile!=='node-continuum'||
     typeof item.passed!=='boolean'||item.evidenceComplete!==true||
     !Number.isSafeInteger(item.executed)||item.executed<1||item.executed>CASE_LIMIT||
     !Number.isSafeInteger(item.failed)||item.failed<0||item.failed>item.executed||
     !Number.isSafeInteger(item.skipped)||item.skipped<0||item.skipped>CASE_LIMIT||
     !Array.isArray(item.executedCaseHashes)||item.executedCaseHashes.length!==item.executed||
     item.executedCaseHashes.some(hash=>!SHA256.test(hash))||
     new Set(item.executedCaseHashes).size!==item.executed||
     ![0,1].includes(item.exitCode))return false;
  if(item.passed!==(item.failed===0&&item.exitCode===0))return false;
  if(item.skipped>0&&!SHA256.test(item.skippedCaseDigest))return false;
  return item.skipped===0&&item.skippedCaseDigest===null ||
    item.skipped>0&&SHA256.test(item.skippedCaseDigest);
}

function identicalCoverage(red,green){
  if(!executedEvidence(red)||!executedEvidence(green)||
     red.skipped!==green.skipped||red.skippedCaseDigest!==green.skippedCaseDigest||
     green.executed<red.executed)return false;
  const available=new Set(green.executedCaseHashes);
  return red.executedCaseHashes.every(x=>available.has(x));
}

function ccc1Valid(receipt,identity){
  if(!receipt||receipt.status!=='DUAL_WORKTREE_CHECKS_PASS_REVIEW_REQUIRED'||
     receipt.reason!=='DUAL_RUN_REVIEW_REQUIRED'||
     receipt.ownerReviewRequired!==true||receipt.verifiedSuccess!==false||
     receipt.learningEligible!==false||receipt.promotionEligible!==false||
     receipt.testEvidenceAuthenticated!==false||receipt.semanticRepairVerified!==false||
     receipt.sourceCommit!==identity.sourceCommit||
     receipt.taskSha256!==identity.taskSha256||
     receipt.sourceSha256!==identity.sourceSha256||
     receipt.candidateSha256!==identity.candidateSha256||
     !Array.isArray(receipt.before)||receipt.before.length!==1||
     !Array.isArray(receipt.after)||receipt.after.length!==1||
     !Array.isArray(receipt.fresh)||receipt.fresh.length!==1)return false;
  const [red]=receipt.before,[green]=receipt.after,[fresh]=receipt.fresh;
  return executedEvidence(red)&&red.passed===false&&red.failed>=1&&red.exitCode===1&&
    executedEvidence(green)&&green.passed===true&&green.failed===0&&
    executedEvidence(fresh)&&fresh.passed===true&&fresh.failed===0&&
    identicalCoverage(red,green)&&identicalCoverage(red,fresh)&&
    identicalCoverage(green,fresh);
}

function verifierValid(verdict,identity){
  return verdict?.schema==='continuum-candidate-verification'&&
    verdict.version===1&&verdict.status==='PASS'&&
    verdict.base===identity.sourceCommit&&
    verdict.candidateSha256===identity.verifierRequestSha256&&
    SHA256.test(verdict.suiteSha256??'')&&
    Number.isSafeInteger(verdict.checks)&&verdict.checks>=1&&
    verdict.checks<=512&&verdict.counterexample===null;
}

function identityValid(identity){
  return identity!==null&&typeof identity==='object'&&
    SHA1.test(identity.sourceCommit??'')&&
    ['taskSha256','sourceSha256','candidateSha256','verifierRequestSha256']
      .every(k=>SHA256.test(identity[k]??''))&&
    identity.sourceSha256!==identity.candidateSha256;
}

/** Hashes bind data but do not authenticate the verifier or the CCC1 receipt. */
export function evaluateResearchRepairEvidence({identity,ccc1Receipt,verifierVerdict,
  feedbackFingerprint=null}={}){
  const bound=identityValid(identity);
  const ccc1Pass=bound&&ccc1Valid(ccc1Receipt,identity);
  const verifierPass=ccc1Pass&&verifierValid(verifierVerdict,identity);
  const declaredFeedback=feedbackFingerprint===null?null:
    SHA256.test(feedbackFingerprint)?feedbackFingerprint:null;
  const reason=!bound?'INVALID_IDENTITY':
    !ccc1Pass?'CCC1_EVIDENCE_MISSING_OR_INCOMPLETE':
    !verifierPass?(verifierVerdict?.status==='FAIL'?'COUNTEREXAMPLE_VETO':'COUNTEREXAMPLE_ABSTAIN_OR_INVALID'):
    'REQUIRES_AUTHENTICATED_OWNER_REVIEW';
  return Object.freeze({
    schema:'continuum-research-repair-review',version:1,
    status:verifierPass?'RESEARCH_REVIEW_REQUIRED':'RESEARCH_EVIDENCE_BLOCKED',reason,
    sourceCommit:bound?identity.sourceCommit:null,
    taskSha256:bound?identity.taskSha256:null,
    candidateSha256:bound?identity.candidateSha256:null,
    ccc1EvidencePresent:ccc1Pass,counterexamplePass:verifierPass,
    feedbackFingerprint:declaredFeedback,feedbackAuthenticated:false,
    receiptAuthenticated:false,ownerReviewRequired:true,
    verifiedSuccess:false,learningEligible:false,promotionEligible:false,
    autoApply:false,modelWeightUpdates:0
  });
}
