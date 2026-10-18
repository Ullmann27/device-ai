/**
 * CEFR prospective shadow benchmark.
 *
 * These are ranking proxies, NEVER replay evidence, a test success claim,
 * or an authorization to change the live repair order. The calling worker
 * must have independently run its second worktree before recording an audit.
 */
const SHA40=/^[0-9a-f]{40}$/;
const SHA64=/^[0-9a-f]{64}$/;
const validOrder=(ids,size)=>Array.isArray(ids)&&ids.length===size&&
 ids.every(id=>typeof id==='string'&&id.length>0&&id.length<=140)&&
 new Set(ids).size===size;

/** Audit a winner independently confirmed by the local worker. */
export function auditCefrShadowWinner({report,independent}={}){
 if(report?.outcome!=='VERIFIED'||independent?.passed!==true||
    !Array.isArray(independent.profiles)||independent.profiles.length===0||
    !independent.profiles.every(p=>p?.passed===true&&p.failed===0))
  return {status:'NOT_INDEPENDENTLY_VERIFIED'};
 // Only trusted in-process worker output may be used for this call. An
 // untrusted JSON flag "passed" is not independent proof.
 if(!independent.profiles.some(p=>Number.isSafeInteger(p.executed)&&p.executed>0))
  return {status:'NOT_INDEPENDENTLY_VERIFIED'};
 if(!SHA40.test(report.base??'')||!SHA64.test(report.verified?.patchSha256??'')||
    typeof report.verified?.candidate!=='string')
  return {status:'INVALID_REPAIR_PROVENANCE'};
 const shadow=report.cefrShadow;
 if(shadow?.mode!=='CEFR_DEV_SHADOW'||!SHA64.test(shadow.modelSha256??''))
  return {status:'NO_VALID_DEV_MODEL'};
 const count=shadow.candidateCount;
 const prod=shadow.productionOrder,trial=shadow.shadowOrder;
 if(!Number.isSafeInteger(count)||count<2||count>64||
    !validOrder(prod,count)||!validOrder(trial,count)||
    prod.some(id=>!trial.includes(id)))
  return {status:'INVALID_SHADOW_RANKING'};
 const winner=report.verified.candidate;
 const pr=prod.indexOf(winner)+1,sr=trial.indexOf(winner)+1;
 if(!pr||!sr)return {status:'WINNER_NOT_OBSERVED'};
 return {
  schema:'continuum-cefr-shadow-audit',version:1,status:'RANKING_PROXY_ONLY',
  base:report.base,patchSha256:report.verified.patchSha256,
  modelSha256:shadow.modelSha256,candidateCount:count,
  productionRank:pr,shadowRank:sr,
  winnerBetter:sr<pr,winnerWorse:sr>pr
 };
}

/** Requires one unchanged model SHA and independently confirmed, unique
 * repair cases. Positive proxy delta does NOT prove success on unseen tasks.
 */
export function summarizeCefrShadowAudits(audits,{minimum=20}={}){
 if(!Number.isSafeInteger(minimum)||minimum<4||minimum>500||
    !Array.isArray(audits)||audits.length>500)
  throw Error('INVALID_CEFR_SHADOW_COHORT');
 const unique=new Set();let model=null,production=0,shadow=0,better=0,worse=0;
 for(const a of audits){
  if(a?.schema!=='continuum-cefr-shadow-audit'||a.version!==1||
     a.status!=='RANKING_PROXY_ONLY'||!SHA40.test(a.base??'')||
     !SHA64.test(a.patchSha256??'')||!SHA64.test(a.modelSha256??'')||
     !Number.isSafeInteger(a.candidateCount)||a.candidateCount<2||
     a.candidateCount>64||
     !Number.isSafeInteger(a.productionRank)||!Number.isSafeInteger(a.shadowRank)||
     a.productionRank<1||a.shadowRank<1||
     a.productionRank>a.candidateCount||a.shadowRank>a.candidateCount||
     a.winnerBetter!==(a.shadowRank<a.productionRank)||
     a.winnerWorse!==(a.shadowRank>a.productionRank))
   throw Error('INVALID_CEFR_SHADOW_AUDIT');
  if(model!==null&&a.modelSha256!==model)throw Error('MIXED_CEFR_MODEL_GENERATIONS');
  model=a.modelSha256;
  const id=a.base+':'+a.patchSha256;
  if(unique.has(id))throw Error('DUPLICATE_CEFR_REPAIR_CASE');
  unique.add(id);
  production+=1/a.productionRank;shadow+=1/a.shadowRank;
  better+=Number(a.winnerBetter);worse+=Number(a.winnerWorse);
 }
 const n=audits.length;
 return {schema:'continuum-cefr-shadow-benchmark',version:1,
  status:n>=minimum?'RANKING_PROXY_COHORT':'INSUFFICIENT_INDEPENDENT_CASES',
  samples:n,minimum,modelSha256:model,
  meanReciprocalRankProduction:n?production/n:null,
  meanReciprocalRankShadow:n?shadow/n:null,
  meanReciprocalRankDelta:n?(shadow-production)/n:null,
  improvedCases:better,worsenedCases:worse,unchangedCases:n-better-worse,
  successRateMeasured:false,productionPromotionAuthorized:false};
}
