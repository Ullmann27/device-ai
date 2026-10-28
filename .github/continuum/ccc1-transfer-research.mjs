/** Research-only, side-effect-free applicability checker for a CCC1 skill candidate.
 * Consumes independently checked CCC1 experience records; it does not write
 * memories, modify weights, issue owner permissions or run a patch.
 */
import {createHash} from 'node:crypto';

const ID=/^[a-z][a-z0-9-]{1,63}$/;
const HEX=/^[a-f0-9]{64}$/;
const COMMIT=/^[a-f0-9]{40}$/;
const sha=data=>createHash('sha256').update(JSON.stringify(data)).digest('hex');
const tagged=(items,min,max)=>{
 if(!Array.isArray(items)||items.length<min||items.length>max)throw Error('TRANSFER_TAGS_INVALID');
 if(items.some(v=>typeof v!=='string'||!ID.test(v)))throw Error('TRANSFER_TAGS_INVALID');
 if(new Set(items).size!==items.length)throw Error('TRANSFER_TAGS_DUPLICATE');
 return Object.freeze([...items].sort());
};
function checkedExperience(r) {
 if(r?.schema!=='continuum-ccc1-experience'||r.version!==1||
    r.outcome!=='COMPLETED'||typeof r.taskId!=='string'||r.taskId.length>128||
    !HEX.test(r.goalSha256??'')||!COMMIT.test(r.baseCommitSha??'')||
    !HEX.test(r.confirmed?.patchSha256??'')||
    r.confirmed?.repair!=='node')throw Error('TRANSFER_EXPERIENCE_UNVERIFIED');
 const matches=r.hypotheses?.filter(h=>h.verdict==='CONFIRMED'&&
    h.repair==='node'&&h.patchSha256===r.confirmed.patchSha256&&
    HEX.test(h.receiptSha256??''));
 if(!Array.isArray(matches)||matches.length!==1)throw Error('TRANSFER_RECEIPT_MISSING');
 return Object.freeze({taskId:r.taskId,goalSha256:r.goalSha256,
  sourceCommit:r.baseCommitSha,patchSha256:r.confirmed.patchSha256,
  receiptSha256:matches[0].receiptSha256});
}
/** An explicit independently trusted verifier must authenticate every CCC1
 * receipt using its existing journal and immutable test sources. No JSON
 * status, log hash chain or model statement can substitute for that callback.
 */
export function freezeResearchSkill({experiences,checkReceipt,taskFamily,requires,excludes,procedure}={}) {
 if(!Array.isArray(experiences)||experiences.length!==2||
    typeof checkReceipt!=='function')throw Error('TRANSFER_INDEPENDENT_VERIFIER_REQUIRED');
 if(typeof taskFamily!=='string'||!ID.test(taskFamily)||
    typeof procedure!=='string'||procedure.trim().length<16||procedure.length>1000)
  throw Error('TRANSFER_SKILL_INVALID');
 const sources=experiences.map(checkedExperience);
 if(new Set(sources.map(x=>x.taskId)).size!==2||
    new Set(sources.map(x=>x.goalSha256)).size!==2||
    new Set(sources.map(x=>x.receiptSha256)).size!==2)throw Error('TRANSFER_SOURCES_NOT_INDEPENDENT');
 for(let i=0;i<2;i++)if(checkReceipt(experiences[i],sources[i])!==true)
  throw Error('TRANSFER_RECEIPT_NOT_AUTHENTICATED');
 const needed=tagged(requires,1,12),denied=tagged(excludes,1,12);
 if(needed.some(x=>denied.includes(x)))throw Error('TRANSFER_CONTRADICTORY_CONDITIONS');
 const frozen={schema:'continuum-ccc1-transfer-research',version:1,
  taskFamily,requires:needed,excludes:denied,procedure:procedure.trim(),
  sourceReceipts:Object.freeze(sources.sort((a,b)=>a.taskId.localeCompare(b.taskId))),
  status:'RESEARCH_SHADOW',promotionEligible:false,toolPermissionsGranted:false};
 const fingerprint=sha(frozen);
 return Object.freeze({...frozen,sha256:fingerprint});
}
/** This function makes a read-only suggestion, never an execution decision. */
export function assessResearchApplicability(candidate,{taskFamily,tags}={}) {
 if(candidate?.schema!=='continuum-ccc1-transfer-research'||candidate.version!==1||
    candidate.status!=='RESEARCH_SHADOW'||candidate.promotionEligible!==false||
    candidate.toolPermissionsGranted!==false)throw Error('TRANSFER_CANDIDATE_INVALID');
 const {sha256:hash,...fields}=candidate;
 if(!HEX.test(hash??'')||sha(fields)!==hash)throw Error('TRANSFER_SKILL_TAMPERED');
 const context=tagged(tags,0,32);
 const excludes=candidate.excludes.filter(x=>context.includes(x));
 const missing=candidate.requires.filter(x=>!context.includes(x));
 if(taskFamily!==candidate.taskFamily)return Object.freeze({apply:false,reason:'OUTSIDE_TASK_FAMILY'});
 if(excludes.length)return Object.freeze({apply:false,reason:'EXCLUDED_CONTEXT',tags:excludes});
 if(missing.length)return Object.freeze({apply:false,reason:'MISSING_PRECONDITIONS',tags:missing});
 return Object.freeze({apply:true,reason:'RESEARCH_RECOMMENDATION_ONLY',ownerReviewRequired:true});
}
