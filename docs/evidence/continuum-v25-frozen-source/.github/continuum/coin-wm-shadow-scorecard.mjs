/**
 * Research-only transfer scorecard. It never executes generated code, grants
 * tools, updates model weights or authorizes any promotion. A record is a claim
 * about an external verifier, NOT proof that the verifier actually ran.
 */
const SHA40=/^[a-f0-9]{40}$/;
const SHA256=/^[a-f0-9]{64}$/;
export const SHADOW_ARMS=Object.freeze([
  'ZERO_MEMORY','EPISODIC_RETRIEVAL','OPERATOR_ONLY','COIN_WM'
]);
const MAX_ATTEMPTS=2;
function reject(reason){throw new Error(reason);}
function validRun(v){
  if(v===null||typeof v!=='object'||Array.isArray(v))reject('INVALID_EXECUTION');
  const keys=['attempts','executedTests','failedTests'];
  if(keys.some(k=>!Number.isSafeInteger(v[k])))reject('INVALID_EXECUTION');
  if(v.attempts<1||v.attempts>MAX_ATTEMPTS)reject('ATTEMPT_BUDGET');
  if(v.executedTests<1||v.failedTests<0||v.failedTests>v.executedTests)reject('INVALID_EXECUTION');
  if(typeof v.passed!=='boolean'||v.passed!==(v.failedTests===0))reject('INVALID_EXECUTION');
}
export function summarizeTransferScorecard(records){
  if(!Array.isArray(records)||records.length===0||records.length>20000)reject('INVALID_DATASET');
  const names=new Set(),families=new Set();
  const solved=Object.fromEntries(SHADOW_ARMS.map(a=>[a,0]));
  for(const row of records){
    if(row===null||typeof row!=='object'||Array.isArray(row))reject('INVALID_RECORD');
    if(typeof row.caseId!=='string'||!/^[-a-z0-9_]{5,100}$/.test(row.caseId))reject('INVALID_CASE_ID');
    if(names.has(row.caseId))reject('DUPLICATE_CASE');
    names.add(row.caseId);
    if(typeof row.family!=='string'||!/^[-a-z0-9_]{3,100}$/.test(row.family))reject('INVALID_FAMILY');
    families.add(row.family);
    if(!SHA40.test(row.sourceCommit)||!SHA256.test(row.taskSha256)||!SHA256.test(row.evidenceSha256))reject('INVALID_EVIDENCE');
    if(row.arms===null||typeof row.arms!=='object'||Array.isArray(row.arms))reject('INVALID_ARMS');
    if(Object.keys(row.arms).sort().join('|')!==[...SHADOW_ARMS].sort().join('|'))reject('INVALID_ARMS');
    for(const arm of SHADOW_ARMS){validRun(row.arms[arm]);if(row.arms[arm].passed)solved[arm]++;}
  }
  return Object.freeze({
    status:'RESEARCH_SHADOW',
    promotionEligible:false,
    needsIndependentEvidenceVerification:true,
    total:records.length,
    familyCount:families.size,
    solved:Object.freeze(solved),
    neuralVsOperatorOnlyDelta:solved.COIN_WM-solved.OPERATOR_ONLY,
    neuralVsRetrievalDelta:solved.COIN_WM-solved.EPISODIC_RETRIEVAL
  });
}
