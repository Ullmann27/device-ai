// CRE1 research-only arbitration between established TF-IDF retrieval and
// trainable relation embeddings. Thresholds are selected ONLY on DEV; never
// inspect TEST when choosing them. This experiment cannot enable production.
import fs from 'node:fs';
import {RelationalEncoder,train} from './cre-encoder.mjs';
import {buildData,files,fingerprint} from './relational-memory-data.mjs';
import {rankMemories} from './dialogue-retrieval.mjs';
import {decide,trainedVocabulary,FROZEN} from './relational-retrieval-audit.mjs';

/** Reusable, read-only query evidence. All expensive neural comparisons are
 * computed once per seed and corpus, not repeated per calibration threshold. */
export function prepareQueryViews(model,split,trained) {
 if(!(model instanceof RelationalEncoder))throw Error('CRE1 model required');
 const memories=split.memories, queries=[...split.questions,...split.unanswerable];
 const mEmb=memories.map(m=>model.embed(m.text,'m'));
 const views=new Map();
 for(const {text:q} of queries) {
  if(views.has(q))continue;
  const qEmb=model.embed(q,'q');
  const scores=new Map();
  memories.forEach((m,i)=>{
   let dot=0;for(let j=0;j<qEmb.length;j++)dot+=qEmb[j]*mEmb[i][j];
   scores.set(m.text,dot);
  });
  views.set(q,prepareView(q,memories,trained,(_q,t)=>scores.get(t)??-2));
 }
 return views;
}
function prepareView(query,memories,trained,scoreFn) {
 const neural=decide(scoreFn,query,memories,{trained}).answer;
 const lexical=rankMemories(query,memories,{limit:2});
 const [best,next]=lexical;
 if(!best)return {neural,lexical:null,lexScore:0,gap:0,evidence:0};
 const indexed=new Map(lexical.map(m=>[m.id,m.score]));
 const safeFallback=decide((_q,text)=>{
  const match=memories.find(m=>m.text===text);
  return match?indexed.get(match.id)??-1:-1;
 },query,memories,{trained,delta:0}).answer;
 return {neural,lexical:safeFallback?.id===best.id?best:null,
   lexScore:best.score,gap:best.score-(next?.score??0),evidence:best.evidence};
}
export function selectHybridView(view,policy) {
 if(!view||!policy||![policy.lexFloor,policy.lexGap,policy.evidenceFloor].every(Number.isFinite))
  throw Error('Invalid hybrid research policy');
 const {neural,lexical,lexScore,gap,evidence}=view;
 if(lexical && lexScore>=policy.lexFloor && gap>=policy.lexGap &&
    evidence>=policy.evidenceFloor)
  return {answer:lexical,route:neural?.id===lexical.id?'AGREEMENT':'LEXICAL_RESCUE'};
 return {answer:neural,route:neural?'CRE1':'ABSTAIN'};
}
export function hybridDecision(model,query,memories,trained,policy) {
 if(!(model instanceof RelationalEncoder)||!Array.isArray(memories)||memories.length>300)
  throw Error('Invalid CRE1 research query');
 return selectHybridView(prepareView(query,memories,trained,
   (q,text)=>model.score(q,text)),policy);
}

const grid=Object.freeze(
 [0.20,0.30,0.40,0.50].flatMap(lexFloor=>
 [0,0.05,0.10,0.18].flatMap(lexGap=>
 [0.8,1].map(evidenceFloor=>({lexFloor,lexGap,evidenceFloor}))))
);
export function outcomes(answers,split) {
 let seen=0,heldout=0,wrong=0,falseAnswers=0,positives=0;
 for(const q of split.questions) {
   const selected=answers(q.text);
   if(q.form==='heldout-form'){if(selected?.id===q.target)heldout++;}
   else if(selected?.id===q.target)seen++;
   if(selected&&selected.id!==q.target)wrong++;
   positives++;
 }
 for(const q of split.unanswerable)if(answers(q.text))falseAnswers++;
 return {seen,heldout,wrong,falseAnswers,positives,negatives:split.unanswerable.length};
}

/** Pre-train before DEV selection. Models & DEV data are not touched afterward. */
export function fitAndSelect({
 seeds=[1,2,3],trainConfig=FROZEN
}={}) {
 const data=buildData(),{curriculum}=files();
 const known=trainedVocabulary(curriculum.pairs,curriculum.memories);
 const models=seeds.map(seed=>{
   const model=new RelationalEncoder(seed);
   train(model,curriculum.pairs,curriculum.memories,
     {...trainConfig,seed:seed*7+1});
   return {seed,model};
 });
 const lexical=outcomes(q=>rankMemories(q,data.dev.memories,{limit:1})[0],data.dev);
 const devViews=models.map(({model})=>prepareQueryViews(model,data.dev,known));
 const baseline=devViews.map(views=>outcomes(q=>views.get(q).neural,data.dev));
 const bestPermittedFalse=Math.max(...baseline.map(x=>x.falseAnswers));
 const options=grid.map((policy,index)=>{
   const results=devViews.map(views=>outcomes(q=>
     selectHybridView(views.get(q),policy).answer,data.dev));
   const minCorrect=Math.min(...results.map(x=>x.seen));
   const maxFalse=Math.max(...results.map(x=>x.falseAnswers));
   const meanCorrect=results.reduce((a,x)=>a+x.seen,0)/results.length;
   const meanFalse=results.reduce((a,x)=>a+x.falseAnswers,0)/results.length;
   return {index,policy,results,minCorrect,maxFalse,meanCorrect,meanFalse,
     accepted:maxFalse<=bestPermittedFalse};
 });
 const feasible=options.filter(x=>x.accepted);
 // If nothing satisfies the conservative DEV false-answer cap, do not rescue.
 const best=feasible.sort((a,b)=>b.minCorrect-a.minCorrect||
   b.meanCorrect-a.meanCorrect||a.maxFalse-b.maxFalse||
   a.meanFalse-b.meanFalse||a.index-b.index)[0];
 return {
  models,known,policy:best?.policy??null,
  selection:{
    devFingerprint:fingerprint(data.dev),
    lexical,baseline,allowedFalse:bestPermittedFalse,
    selected:best?{policy:best.policy,minCorrect:best.minCorrect,
      maxFalse:best.maxFalse,meanCorrect:best.meanCorrect}:null,
    feasible:feasible.length,options:grid.length
  }
 };
}

export function researchAudit() {
 const data=buildData(),trained=fitAndSelect();
 const perSeed=trained.models.map(({seed,model})=>{
   const mem=data.test.memories;
   const views=prepareQueryViews(model,data.test,trained.known);
   const hybrid=trained.policy?outcomes(q=>
     selectHybridView(views.get(q),trained.policy).answer,data.test):null;
   const base=outcomes(q=>views.get(q).neural,data.test);
   return {seed,base,hybrid};
 });
 const lexical=outcomes(q=>rankMemories(q,data.test.memories,{limit:1})[0],data.test);
 // This TEST set has already been publicly examined. Not promotion quality!
 const meetsHistoricalGate=Boolean(trained.policy)&&perSeed.every(row=>
  row.hybrid.seen+row.hybrid.heldout>lexical.seen+lexical.heldout&&
  row.hybrid.heldout>=lexical.heldout&&
  row.hybrid.falseAnswers<=row.base.falseAnswers);
 return {schema:1,model:'CRE1_DEV_SELECTED_HYBRID_RESEARCH',
  selection:trained.selection,policy:trained.policy,
  testFingerprint:fingerprint(data.test),perSeed,lexical,
  meetsHistoricalGate,promotionEligible:false,
  warning:'TEST forms have previously been inspected. Requires a NEW untouched evaluation domain.'};
}
if(process.argv[1]?.endsWith('cre-hybrid-research.mjs')){
 const audit=researchAudit();
 console.log('DEV selected policy',JSON.stringify(audit.policy),'feasible',audit.selection.feasible);
 console.log('Historical TEST lexical',JSON.stringify(audit.lexical));
 for(const row of audit.perSeed)console.log('seed',row.seed,'CRE1',JSON.stringify(row.base),'hybrid',JSON.stringify(row.hybrid));
 console.log('historicalPass',audit.meetsHistoricalGate,'promotionEligible',audit.promotionEligible);
 if(process.argv[2])fs.writeFileSync(process.argv[2],JSON.stringify(audit,null,2)+'\n');
}
