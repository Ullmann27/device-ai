/** NTW1 v3 ranker -> restricted atomic statement translator.
 * Research shadow adapter only. Never executes, commits, grants or verifies code.
 */
import crypto from 'node:crypto';
import {CoinWmV3ShadowModel} from './coin-wm-v3-shadow-model.mjs';
import {isStructuredStatementOperator} from './structured-statement-translator.mjs';
export function proposeShadowStructuredCandidates({model,state16,operators,source,sourceCommit,taskSha256,maxCandidates=2}={}){
  if(!(model instanceof CoinWmV3ShadowModel)||model.status!=='RESEARCH_SHADOW'||
      model.promotionEligible!==false)throw Error('RESEARCH_MODEL_REQUIRED');
  if(!Array.isArray(operators)||operators.length<1||operators.length!==model.maxOperators||
      operators.length>12)throw Error('OPERATOR_SET_INVALID');
  if(!operators.every(op=>isStructuredStatementOperator(op)))throw Error('OPERATOR_NOT_STRUCTURED');
  if(typeof source!=='string'||source.length===0||source.length>16384)throw Error('SOURCE_BOUNDS');
  if(!/^[a-f0-9]{40}$/.test(sourceCommit??'')||!/^[a-f0-9]{64}$/.test(taskSha256??''))
    throw Error('TASK_SOURCE_IDENTITY_REQUIRED');
  const sha256=value=>crypto.createHash('sha256').update(value,'utf8').digest('hex');
  const sourceSha256=sha256(source);
  if(!Number.isSafeInteger(maxCandidates)||maxCandidates<1||maxCandidates>2)throw Error('CANDIDATE_BUDGET');
  const ranked=model.rank(state16,operators.length);
  const candidates=[];
  for(const candidate of ranked){
    if(candidates.length>=maxCandidates)break;
    const op=operators[candidate.operatorIndex];
    const result=op.applyToCode(source);
    if(result.status!=='CANDIDATE_UNVERIFIED'||typeof result.code!=='string'||result.code===source)continue;
    candidates.push(Object.freeze({
      operatorId:op.id,operatorIndex:candidate.operatorIndex,
      researchScore:candidate.score,status:'CANDIDATE_UNVERIFIED',code:result.code,
      candidateSha256:sha256(result.code),sourceSha256,sourceCommit,taskSha256
    }));
  }
  return Object.freeze({
    status:'RESEARCH_SHADOW',promotionEligible:false,
    needsIndependentExecution:true,sourceSha256,sourceCommit,taskSha256,
    candidates:Object.freeze(candidates)
  });
}
