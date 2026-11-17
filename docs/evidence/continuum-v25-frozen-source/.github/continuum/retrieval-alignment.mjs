// Measure relevance ranking mismatch: trained SWB1, normalized SWB1 and
// the ACTUAL production lexical retrieval (not a fabricated generic baseline).
// Read-only synthetic audit; no model promotion and no private memories.
import fs from 'node:fs';
import {SubwordEncoder} from './portable-subword.mjs';
import {trainContrastiveCandidate} from './contrastive-views.mjs';
import {rankMemories} from './dialogue-retrieval.mjs';
import {TRAIN,HOLDOUTS,SEEDS,DROPS} from './contrastive-generalization.mjs';

class Rng {
  constructor(seed){this.s=seed>>>0;}
  next(){this.s=(Math.imul(this.s,1664525)+1013904223)>>>0;return this.s/4294967296;}
}
const tokenize=s=>s.toLowerCase().replace(/[^\p{L}\p{N}]+/gu,' ').trim().slice(0,512).split(' ').filter(Boolean);
function mask(tokens,rng,drop){
  const keep=tokens.filter(()=>rng.next()>=drop);
  return (keep.length?keep:[tokens[Math.min(tokens.length-1,Math.floor(rng.next()*tokens.length))]]).join(' ');
}
const cos=(a,b)=>{
  let d=0,na=0,nb=0;
  for(let i=0;i<a.length;i++){d+=a[i]*b[i];na+=a[i]*a[i];nb+=b[i]*b[i];}
  return na&&nb?d/Math.sqrt(na*nb):0;
};
function evaluate(model,docs,{seed,drop,policy}){
  const rng=new Rng(seed),memories=docs.map((text,i)=>({id:String(i),text}));
  let top1=0,rr=0,abstentions=0;
  for(let i=0;i<docs.length;i++){
    const q=mask(tokenize(docs[i]),rng,drop);
    let ranks;
    if(policy==='lexical'){
      ranks=rankMemories(q,memories,{limit:docs.length}).map(x=>Number(x.id));
    }else{
      const a=policy==='cosine'?model.encode(q):null;
      ranks=memories.map((m,j)=>({j,s:policy==='cosine'?cos(a,model.encode(m.text)):model.similarity(q,m.text)}))
        .sort((x,y)=>y.s-x.s||x.j-y.j).map(x=>x.j);
    }
    const rank=ranks.indexOf(i)+1;
    if(rank===0){abstentions++;continue;}
    rr+=1/rank;if(rank===1)top1++;
  }
  return {mrr:rr/docs.length,top1,abstentions,total:docs.length};
}
export function auditRetrievalAlignment(){
  const policies=['legacy','cosine','lexical'];
  const cases=[];
  const unknownQueries=[
    'zqvfix mnopqrst volxquartz',
    'xyzzymno plqwert zzzfragment',
    'unanwenderbare sonderzeichenkettex'
  ];
  const records=[];
  for(const seed of SEEDS){
    const baseline=new SubwordEncoder(seed);
    const training=trainContrastiveCandidate(baseline,TRAIN,HOLDOUTS.ecology,
      {steps:280,seed:seed*101+4,drop:0.65,rate:0.08});
    if(training.interrupted||training.steps!==280)throw Error('Training incomplete');
    for(const [domain,docs] of Object.entries(HOLDOUTS))for(const drop of DROPS){
      const viewSeed=131+Math.round(drop*100)+seed*7;
      for(const policy of policies){
        const before=evaluate(baseline,docs,{seed:viewSeed,drop,policy});
        const after=evaluate(training.candidate,docs,{seed:viewSeed,drop,policy});
        cases.push({seed,domain,drop,policy,before,after,
          deltaMRR:Number((after.mrr-before.mrr).toFixed(12))});
      }
    }
    const memories=Object.values(HOLDOUTS).flat().map((text,i)=>({id:String(i),text}));
    for(const q of unknownQueries){
      const found=rankMemories(q,memories,{limit:3});
      records.push({seed,query:q,lexicalHits:found.length,
        neuralAlwaysRanks:memories.length>0});
    }
  }
  const summaries=Object.fromEntries(policies.map(policy=>{
    const rows=cases.filter(c=>c.policy===policy);
    const mean=k=>Number((rows.reduce((n,x)=>n+x[k].mrr,0)/rows.length).toFixed(12));
    const delta=rows.map(x=>x.deltaMRR);
    return [policy,{cases:rows.length,meanMRRBefore:mean('before'),meanMRRAfter:mean('after'),
      meanDelta:Number((delta.reduce((s,x)=>s+x,0)/delta.length).toFixed(12)),
      improved:delta.filter(x=>x>0).length,regressed:delta.filter(x=>x<0).length,
      unchanged:delta.filter(x=>x===0).length,
      meanAbstentions:Number((rows.reduce((s,x)=>s+x.after.abstentions,0)/rows.length).toFixed(3))}];
  }));
  return {schema:1,method:'production-retrieval-alignment-frozen-holdouts',
    policies,modelSeeds:SEEDS,wordDropRates:DROPS,cases,summaries,
    unknownQueryControls:records,
    automatedPromotion:false,
    decision:'DIAGNOSTIC_ONLY_NOT_AUTHORIZED_FOR_MODEL_REPLACEMENT',
    caution:'Masked words from each indexed sentence are not paraphrase understanding or generalization to unrelated facts.'};
}
if(process.argv[1]?.endsWith('retrieval-alignment.mjs')){
  const result=auditRetrievalAlignment();
  console.log(JSON.stringify({summaries:result.summaries,
    unknownQueryFalseHits:result.unknownQueryControls.filter(x=>x.lexicalHits>0).length},null,2));
  if(process.argv[2])fs.writeFileSync(process.argv[2],JSON.stringify(result,null,2)+'\n');
}
