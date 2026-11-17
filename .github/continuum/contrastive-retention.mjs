// Synthetic pre-training -> contrastive adaptation -> retention audit.
// Measures forgetting; never modifies an active model or authorizes promotion.
import fs from 'node:fs';
import {SubwordEncoder} from './portable-subword.mjs';
import {trainContrastiveCandidate} from './contrastive-views.mjs';
import {TRAIN,HOLDOUTS,SEEDS} from './contrastive-generalization.mjs';

export const INTENTS=[
 {text:'bitte erklaere mir dieses thema',label:1},
 {text:'kannst du mir die formel zeigen',label:1},
 {text:'hilf mir bei einer lernfrage',label:1},
 {text:'hallo guten morgen',label:0},
 {text:'servus bis bald',label:0},
 {text:'vielen dank auf wiedersehen',label:0}
];
export const SEMANTICS=[
 {left:'katze tier schnurrt',right:'eine katze ist ein tier',similar:true},
 {left:'regen wolke wasser',right:'wolken bringen regen',similar:true},
 {left:'musik klavier ton',right:'klavier erzeugt musik',similar:true},
 {left:'elektrischer motor',right:'wachsender baum',similar:false},
 {left:'stadt bus bahnhof',right:'sonne stern galaxie',similar:false},
 {left:'brot backen rezept',right:'schiff anker meer',similar:false}
];
const rnd=x=>Number(x.toFixed(12));
const details=(before,after)=>({
 beforeLoss:rnd(before.loss),afterLoss:rnd(after.loss),
 deltaLoss:rnd(after.loss-before.loss),
 beforeCorrect:before.correct,afterCorrect:after.correct,total:before.total
});
export function auditRetention(){
 const cases=[];
 for(const modelSeed of SEEDS){
   const baseline=new SubwordEncoder(modelSeed);
   // Represents previously trained skills before learning a new retrieval task.
   for(let step=0;step<240;step++)baseline.updateIntent(INTENTS[step%INTENTS.length],0.10);
   for(let step=0;step<240;step++)baseline.updateSemantic(SEMANTICS[step%SEMANTICS.length],0.08);
   const snapshot=baseline.save();
   const beforeIntent=baseline.evaluate(INTENTS);
   const beforeSemantic=baseline.evaluate(SEMANTICS);
   const trained=trainContrastiveCandidate(baseline,TRAIN,HOLDOUTS.ecology,
     {steps:280,seed:modelSeed*101+4,drop:0.65,rate:0.08});
   if(trained.interrupted || trained.steps!==280)throw Error('Incomplete contrastive adaptation');
   if(!baseline.save().equals(snapshot))throw Error('Pretrained baseline mutated');
   const afterIntent=trained.candidate.evaluate(INTENTS);
   const afterSemantic=trained.candidate.evaluate(SEMANTICS);
   cases.push({modelSeed,
     intent:details(beforeIntent,afterIntent),
     semantic:details(beforeSemantic,afterSemantic)});
 }
 const all=cases.flatMap(c=>[c.intent,c.semantic]);
 return {
  schema:1,method:'synthetic-pretrained-skills-retention',
  cases,summary:{
   seeds:SEEDS.length,
   evaluations:all.length,
   accuracyRegressions:all.filter(x=>x.afterCorrect<x.beforeCorrect).length,
   lossRegressions:all.filter(x=>x.deltaLoss>0.015).length,
   worstLossIncrease:rnd(Math.max(...all.map(x=>x.deltaLoss))),
   meanLossChange:rnd(all.reduce((a,x)=>a+x.deltaLoss,0)/all.length)
  },
  recommendation:'REQUIRE_RETENTION_GATE_BEFORE_PROMOTION',
  limitations:'Tiny synthetic old skills; behavior on owner memories is unmeasured.'
 };
}
if(process.argv[1]?.endsWith('contrastive-retention.mjs')){
 const report=auditRetention();
 console.log(JSON.stringify(report.summary,null,2));
 if(process.argv[2])fs.writeFileSync(process.argv[2],JSON.stringify(report,null,2)+'\n');
}
