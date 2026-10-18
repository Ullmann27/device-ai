/**
 * CEFR v1: research-only repair strategy ranking.
 *
 * No production sort is changed. Developer CRE1 weights must never be reused
 * as the personal-memory checkpoint. No report can self-authorize training:
 * an independent verification callback MUST be supplied by the trusted
 * orchestrator that actually checks the patch in a second Git worktree.
 */
import crypto from 'node:crypto';
import {RelationalEncoder} from './cre-encoder.mjs';
import {rankCandidates,repairCandidateRiskTier} from './repair-loop.mjs';
import {validateVerifiedReport} from './repair-publish.mjs';

export const DEV_CRE1_FILENAME='cre1-dev-repair-v1.cre1';
export const MIN_HOLDOUT_CASES=4;
const SHA=/^[a-f0-9]{64}$/;
const sha256=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
const norm=s=>String(s??'').toLowerCase().replace(/[^\p{L}\p{N}]+/gu,' ').trim();
const rejection=(reason,bytes)=>({accepted:false,reason,activeBytes:Buffer.from(bytes),
 activeSha256:sha256(bytes)});

export function formatDiagnosticQuery(diag){
 return [diag?.kind??'ERROR',diag?.symbol??'',diag?.file??'',
  String(diag?.message??'').slice(0,240)].filter(Boolean).join(' ').slice(0,512);
}

export function formatCandidateTarget(candidate){
 return [candidate?.strategy??'UNKNOWN',candidate?.diagnosticKind??'',
  String(candidate?.description??'').slice(0,200)].filter(Boolean).join(' ').slice(0,320);
}

export function validateCefrHoldout(holdout,activeQuery=null){
 if(!Array.isArray(holdout)||holdout.length<MIN_HOLDOUT_CASES||holdout.length>256)
  throw Error('INVALID_HOLDOUT_CORPUS');
 const seen=new Set(),active=activeQuery==null?null:norm(activeQuery);
 for(const item of holdout){
  if(!item||typeof item.query!=='string'||typeof item.correctIndex!=='number'||
   !Array.isArray(item.candidates)||item.candidates.length<2||item.candidates.length>12||
   !Number.isSafeInteger(item.correctIndex)||item.correctIndex<0||
   item.correctIndex>=item.candidates.length||
   item.candidates.some(s=>typeof s!=='string'||s.length<5||s.length>320))
   throw Error('INVALID_HOLDOUT_CORPUS');
  const q=norm(item.query);
  if(q.length<5||seen.has(q)||q===active||
   new Set(item.candidates.map(norm)).size!==item.candidates.length)
   throw Error(q===active?'HOLDOUT_LEAKAGE':'INVALID_HOLDOUT_CORPUS');
  seen.add(q);
 }
}

export function evaluateCefrHoldout(model,holdout){
 validateCefrHoldout(holdout);
 let correct=0,mrr=0,margin=0;
 for(const item of holdout){
  const values=item.candidates.map((s,i)=>({i,score:model.score(item.query,s)}));
  if(values.some(s=>!Number.isFinite(s.score)))throw Error('INVALID_MODEL_SCORE');
  const positive=values[item.correctIndex].score;
  const negative=Math.max(...values.filter(x=>x.i!==item.correctIndex).map(x=>x.score));
  margin+=positive-negative;
  values.sort((a,b)=>b.score-a.score||a.i-b.i);
  const rank=values.findIndex(x=>x.i===item.correctIndex)+1;
  if(rank===1)correct++;
  mrr+=1/rank;
 }
 return {correct,total:holdout.length,mrr:mrr/holdout.length,meanMargin:margin/holdout.length};
}

function validatedIndependentProof(verify,report,patchText){
 // Callbacks are capabilities. Only trusted worker code may supply one; a
 // serialized report, its own verification flag or user input cannot replace it.
 if(typeof verify!=='function')throw Error('INDEPENDENT_VERIFIER_REQUIRED');
 const proof=verify(report,patchText);
 if(proof==null||typeof proof!=='object'||proof.passed!==true||
    proof.base!==report.base||proof.chain!==report.chain||
    proof.patchSha256!==sha256(patchText)||!SHA.test(proof.patchSha256)||
    !Array.isArray(proof.profiles)||proof.profiles.length===0||
    proof.profiles.some(p=>p?.passed!==true||p?.failed!==0)||
    !proof.profiles.some(p=>Number.isSafeInteger(p.executed)&&p.executed>0))
  throw Error('INDEPENDENT_VERIFICATION_FAILED');
 return proof;
}

/** All updates occur on a restored candidate. No baseline object or bytes mutate. */
export function trainCefrCandidateFromVerifiedReport({
 baselineBytes,report,patchText,holdoutCases,verifyIndependent,lr=0.008,tau=0.15
}={}){
 const bytes=Buffer.from(baselineBytes??[]);
 RelationalEncoder.restore(bytes);
 validateCefrHoldout(holdoutCases);
 if(!Number.isFinite(lr)||lr<=0||lr>0.02||!Number.isFinite(tau)||tau<0.05||tau>1)
  throw Error('INVALID_CEFR_HYPERPARAMETERS');
 if(typeof verifyIndependent!=='function')
  return rejection('INDEPENDENT_VERIFIER_REQUIRED',bytes);

 let winningAttempt;
 try{
  winningAttempt=validateVerifiedReport(report,patchText);
  validatedIndependentProof(verifyIndependent,report,patchText);
 }catch(e){
  return rejection('UNVERIFIED_REPAIR: '+String(e.message).slice(0,120),bytes);
 }
 const winnerId=winningAttempt.diagnostic;
 const diagnostic=(report.baseline?.diagnostics??[]).find(d=>d.fingerprint===winnerId);
 if(!diagnostic)return rejection('MISSING_TRAINING_DIAGNOSTIC',bytes);
 const query=formatDiagnosticQuery(diagnostic);
 try{validateCefrHoldout(holdoutCases,query);}
 catch(e){return rejection(String(e.message),bytes);}
 const failed=(report.attempts??[]).filter(a=>
  a.diagnostic===winnerId&&a.outcome==='FAILED_TESTS'&&
  Array.isArray(a.tests)&&a.tests.some(t=>t.passed===false||
   (t.testProfile&&t.tests?.failed>0)));
 if(failed.length===0)return rejection('NO_EXECUTED_HARD_NEGATIVES',bytes);
 const positive=formatCandidateTarget(winningAttempt);
 const negatives=[...new Set(failed.map(formatCandidateTarget))]
  .filter(s=>s!==positive).slice(0,6);
 if(negatives.length===0)return rejection('NO_DISTINCT_HARD_NEGATIVES',bytes);

 const before=RelationalEncoder.restore(bytes);
 const after=RelationalEncoder.restore(bytes);
 let holdoutBefore,holdoutAfter,scoreBefore,scoreAfter,loss;
 try{
  holdoutBefore=evaluateCefrHoldout(before,holdoutCases);
  scoreBefore=before.score(query,positive);
  const grads=RelationalEncoder.emptyGrads();
  loss=after.lossAndGrad(query,[positive,...negatives],tau,grads);
  if(!Number.isFinite(loss))return rejection('NON_FINITE_LOSS',bytes);
  after.apply(grads,lr);
  scoreAfter=after.score(query,positive);
  holdoutAfter=evaluateCefrHoldout(after,holdoutCases);
 }catch(e){return rejection('CEFR_TRAINING_FAILED: '+String(e.message).slice(0,100),bytes);}
 if(!Number.isFinite(scoreAfter)||scoreAfter<=scoreBefore+1e-6)
  return rejection('NO_TASK_SCORE_GAIN',bytes);
 if(holdoutAfter.correct<holdoutBefore.correct||
    holdoutAfter.mrr+1e-12<holdoutBefore.mrr||
    holdoutAfter.meanMargin+0.005<holdoutBefore.meanMargin)
  return rejection('HOLDOUT_REGRESSION',bytes);
 const promoted=after.save();
 RelationalEncoder.restore(promoted);
 return {accepted:true,reason:'EXPERIMENTAL_PROMOTION',steps:after.steps,loss,
  scoreBefore,scoreAfter,holdoutBefore,holdoutAfter,
  activeBytes:promoted,activeSha256:sha256(promoted)};
}

/** Pure shadow only: the production candidate order is returned unmodified. */
export function observeCefrCandidates({candidates,stats=[],failedIds=new Set(),
 diagnostics=[],checkpointBytes=null}={}){
 const productionOrder=rankCandidates(candidates,stats,failedIds);
 const unchanged={productionOrder,shadowOrder:productionOrder.map(c=>c.id),observations:[]};
 if(checkpointBytes==null)return {mode:'NO_DEV_CHECKPOINT',...unchanged};
 let model;
 try{model=RelationalEncoder.restore(checkpointBytes);}
 catch{return {mode:'INVALID_DEV_CHECKPOINT',...unchanged};}
 const index=new Map(diagnostics.map(d=>[d.fingerprint,d]));
 const observations=productionOrder.map(c=>{
  const diag=index.get(c.diagnostic)??{kind:c.diagnosticKind,message:c.description};
  let value=0;
  try{value=model.score(formatDiagnosticQuery(diag),formatCandidateTarget(c));}
  catch{}
  if(!Number.isFinite(value))value=0;
  return {id:c.id,tier:repairCandidateRiskTier(c.strategy),strategy:c.strategy,
   productionScore:c.score,neuralScore:value,
   shadowScore:0.7*c.score+0.3*(value+1)/2};
 });
 const shadowOrder=[...observations].sort((a,b)=>a.tier-b.tier||
  b.shadowScore-a.shadowScore||a.id.localeCompare(b.id)).map(x=>x.id);
 return {mode:'CEFR_DEV_SHADOW',modelSha256:sha256(checkpointBytes),
  steps:model.steps,productionOrder,shadowOrder,observations};
}
