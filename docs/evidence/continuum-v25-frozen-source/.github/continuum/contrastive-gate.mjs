// Pure fail-closed experimental gate. NEVER writes model weights or approves rollout.
import fs from 'node:fs';
export function assessContrastiveCandidate(generalization,retention){
 const reasons=[];
 const reject=why=>({status:'REJECTED',reasons:[why],automaticPromotion:false});
 if(generalization?.schema!==1||retention?.schema!==1||
    !Array.isArray(generalization.cases)||!Array.isArray(retention.cases))
    return reject('INVALID_EVIDENCE');
 const cases=generalization.cases,skills=retention.cases;
 const seeds=new Set(),domains=new Set(),drops=new Set(),seen=new Set();
 const deltas=[];
 for(const item of cases){
   if(!Number.isInteger(item.modelSeed)||typeof item.domain!=='string'||
      !Number.isFinite(item.drop)||
      ![item.beforeMRR,item.afterMRR,item.deltaMRR].every(Number.isFinite)||
      item.beforeMRR<=0||item.beforeMRR>1||item.afterMRR<=0||item.afterMRR>1||
      Math.abs(item.afterMRR-item.beforeMRR-item.deltaMRR)>1e-8)
      return reject('INVALID_RETRIEVAL_DATA');
   const id=[item.modelSeed,item.domain,item.drop].join(':');
   if(seen.has(id))return reject('DUPLICATE_CASE');
   seen.add(id);seeds.add(item.modelSeed);domains.add(item.domain);drops.add(item.drop);
   deltas.push(item.afterMRR-item.beforeMRR);
 }
 if(seeds.size<3||domains.size<3||drops.size<2||
   cases.length!==seeds.size*domains.size*drops.size)
   reasons.push('INSUFFICIENT_GENERALIZATION_COVERAGE');
 const mean=deltas.length?deltas.reduce((a,b)=>a+b,0)/deltas.length:0;
 if(mean<0.03)reasons.push('HOLDOUT_GAIN_INSUFFICIENT');
 if(deltas.some(d=>d< -0.01))reasons.push('HOLDOUT_REGRESSION');
 if(deltas.filter(d=>d>=0.01).length<Math.ceil(deltas.length*0.6))
   reasons.push('HOLDOUT_GAIN_NOT_CONSISTENT');
 for(const domain of domains){
   const ds=cases.filter(c=>c.domain===domain).map(c=>c.afterMRR-c.beforeMRR);
   if(!ds.length||ds.reduce((a,b)=>a+b,0)/ds.length<0.01){
     reasons.push('DOMAIN_GAIN_INSUFFICIENT:'+domain);
   }
 }
 const skillSeeds=new Set();
 for(const item of skills){
   if(!Number.isInteger(item.modelSeed)){reasons.push('INVALID_SKILL_DATA');break;}
   skillSeeds.add(item.modelSeed);
   for(const kind of ['intent','semantic']){
     const s=item[kind];
     if(!s || !Number.isInteger(s.total)||s.total<1 ||
       !Number.isInteger(s.beforeCorrect)||!Number.isInteger(s.afterCorrect)||
       s.beforeCorrect<0||s.afterCorrect<0||s.beforeCorrect>s.total||s.afterCorrect>s.total||
       !Number.isFinite(s.beforeLoss)||!Number.isFinite(s.afterLoss)||
       s.beforeLoss<0||s.afterLoss<0){
       reasons.push('INVALID_SKILL_DATA');break;
     }
     if(s.afterCorrect<s.beforeCorrect)reasons.push('SKILL_ACCURACY_REGRESSION');
     if(s.afterLoss>s.beforeLoss+0.015)reasons.push('SKILL_LOSS_REGRESSION');
   }
 }
 if(skillSeeds.size!==seeds.size||[...seeds].some(s=>!skillSeeds.has(s)) ||
    skills.length!==seeds.size)reasons.push('INCOMPLETE_RETENTION_EVIDENCE');
 // Eligibility is NEVER automatic promotion; an owner-controlled integration
 // must independently validate provenance and current model revision.
 return {
   status:reasons.length?'REJECTED':'REVIEW_REQUIRED',
   reasons:[...new Set(reasons)],
   automaticPromotion:false,
   meanHoldoutDelta:Number(mean.toFixed(12)),
   evaluatedRetrievalCases:cases.length,
   evaluatedSkillSeeds:skills.length
 };
}
if(process.argv[1]?.endsWith('contrastive-gate.mjs')){
 if(process.argv.length!==5)throw Error('Usage: gate GENERALIZATION_JSON RETENTION_JSON OUT_JSON');
 const general=JSON.parse(fs.readFileSync(process.argv[2],'utf8'));
 const retained=JSON.parse(fs.readFileSync(process.argv[3],'utf8'));
 const decision=assessContrastiveCandidate(general,retained);
 fs.writeFileSync(process.argv[4],JSON.stringify(decision,null,2)+'\n');
 console.log(JSON.stringify(decision,null,2));
}
