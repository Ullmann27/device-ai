// Handwritten synthetic paraphrases: separate from training and word-masking views.
// This evaluates actual retrieval from user-like queries, NOT learned world knowledge.
import fs from 'node:fs';
import {SubwordEncoder} from './portable-subword.mjs';
import {trainContrastiveCandidate} from './contrastive-views.mjs';
import {rankMemories} from './dialogue-retrieval.mjs';
import {TRAIN,HOLDOUTS,SEEDS} from './contrastive-generalization.mjs';
export const QUESTIONS={
 ecology:[
  'woher bekommt die pflanze mineralstoffe im boden',
  'welcher vorgang eines baumes setzt atembares gas frei',
  'welche tiere helfen blueten bei der vermehrung',
  'was verwertet totes pflanzenmaterial in der natur',
  'wo schuetzen voegel ihre jungen'
 ],
 communication:[
  'wer bringt schriftliche sendungen zum empfaenger',
  'wie gelangen nachrichten ohne kabel durch die luft',
  'wo kann ich viele lesewerke ausleihen',
  'wie werden audioinhalte ohne ton als schrift verstanden',
  'welche technik erlaubt unterhaltungen aus der ferne'
 ],
 geometry:[
  'welche form besitzt genau drei kanten',
  'welche strecke geht vom zentrum eines kreises nach aussen',
  'wie viele flaechen hat ein rechteckiger koerper',
  'was misst man zwischen zwei sich schneidenden geraden',
  'welche transformation erzeugt ein spiegelbild'
 ]
};
const cosine=(a,b)=>{
  let dot=0,na=0,nb=0;
  for(let i=0;i<a.length;i++){dot+=a[i]*b[i];na+=a[i]*a[i];nb+=b[i]*b[i];}
  return na&&nb?dot/Math.sqrt(na*nb):0;
};
function rank(model,query,memories,policy){
 if(policy==='lexical')
   return rankMemories(query,memories.map((text,i)=>({id:String(i),text})),{limit:memories.length})
     .map(x=>Number(x.id));
 const z=policy==='cosine'?model.encode(query):null;
 return memories.map((text,i)=>({i,s:policy==='cosine'?
   cosine(z,model.encode(text)):model.similarity(query,text)}))
   .sort((a,b)=>b.s-a.s||a.i-b.i).map(x=>x.i);
}
export function auditParaphrases(){
 const records=[],policies=['lexical','legacy','cosine'];
 for(const seed of SEEDS){
  const baseline=new SubwordEncoder(seed);
  const trained=trainContrastiveCandidate(baseline,TRAIN,HOLDOUTS.ecology,
    {steps:280,seed:seed*101+4,drop:0.65,rate:0.08});
  if(trained.interrupted)throw Error('Incomplete training');
  for(const [domain,docs] of Object.entries(HOLDOUTS)){
   const questions=QUESTIONS[domain];
   if(questions.length!==docs.length)throw Error('Corpus mismatch');
   for(const policy of policies){
    for(const state of ['before','after']){
     const model=state==='before'?baseline:trained.candidate;
     let rr=0,top1=0,abstained=0;
     questions.forEach((q,i)=>{
      const results=rank(model,q,docs,policy);
      const position=results.indexOf(i);
      if(position===-1)abstained++;
      else {rr+=1/(position+1);if(position===0)top1++;}
     });
     records.push({seed,domain,policy,state,
       mrr:Number((rr/docs.length).toFixed(12)),top1,abstained,total:docs.length});
    }
   }
  }
 }
 const summaries=Object.fromEntries(policies.map(policy=>{
  const before=records.filter(x=>x.policy===policy&&x.state==='before');
  const after=records.filter(x=>x.policy===policy&&x.state==='after');
  const avg=(xs,key)=>Number((xs.reduce((a,x)=>a+x[key],0)/xs.length).toFixed(12));
  return [policy,{queries:after.reduce((a,x)=>a+x.total,0),
   beforeMRR:avg(before,'mrr'),afterMRR:avg(after,'mrr'),
   beforeTop1Rate:Number((before.reduce((a,x)=>a+x.top1,0)/before.reduce((a,x)=>a+x.total,0)).toFixed(12)),
   afterTop1Rate:Number((after.reduce((a,x)=>a+x.top1,0)/after.reduce((a,x)=>a+x.total,0)).toFixed(12)),
   abstentions:after.reduce((a,x)=>a+x.abstained,0)}];
 }));
 return {schema:1,method:'human-authored-synthetic-paraphrase-retrieval',
  questions:QUESTIONS,records,summaries,automaticPromotion:false,
  qualification:'Small synthetic, manually authored paraphrases. Absent word evidence is safer than guessing. No real-world gains inferred.'};
}
if(process.argv[1]?.endsWith('retrieval-paraphrase.mjs')){
 const report=auditParaphrases();
 console.log(JSON.stringify(report.summaries,null,2));
 if(process.argv[2])fs.writeFileSync(process.argv[2],JSON.stringify(report,null,2)+'\n');
}
