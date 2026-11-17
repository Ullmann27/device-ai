// Fixed synthetic-corpus, multiple-seed generalization audit.
// Does not promote weights, download data or claim real-world improvement.
import fs from 'node:fs';
import {SubwordEncoder} from './portable-subword.mjs';
import {trainContrastiveCandidate,evaluateViews} from './contrastive-views.mjs';
export const TRAIN=[
 'sonnenlicht verwandelt pflanzen in chemische energie',
 'das getriebe uebertraegt drehmoment vom motor',
 'klavier noten rhythmen bilden eine melodie',
 'wolken kondensieren wasserdampf zu regen',
 'galaxien enthalten sterne staub und gas',
 'backen mischt mehl wasser und hefe',
 'metalle dehnen sich bei temperaturerhoehung aus',
 'mathematische gleichungen verbinden variablen'
];
export const HOLDOUTS={
 ecology:[
  'wurzel zieht naehrstoffe aus dem erdboden',
  'baeume produzieren sauerstoff durch photosynthese',
  'insekten bestaeuben pflanzen und blueten',
  'pilze zersetzen abgestorbene organische stoffe',
  'voegel bauen nester fuer ihren nachwuchs'
 ],
 communication:[
  'postbote verteilt briefe an wohnadressen',
  'funkwellen transportieren digitale nachrichten',
  'bibliotheken stellen geschriebene buecher bereit',
  'untertitel machen gesprochene worte lesbar',
  'telefongespraeche verbinden menschen auf distanz'
 ],
 geometry:[
  'dreiecke besitzen drei ecken und seiten',
  'kreisradius misst die distanz zum mittelpunkt',
  'quader besitzen sechs rechteckige flaechen',
  'winkel beschreiben die oeffnung zweier linien',
  'spiegelung erzeugt ein symmetrisches bild'
 ]
};
export const SEEDS=[11,73,191,337,557];
export const DROPS=[0.45,0.65,0.8];
const rounded=x=>Number(x.toFixed(12));
const summarize=xs=>({mean:rounded(xs.reduce((a,b)=>a+b,0)/xs.length),
  min:rounded(Math.min(...xs)),max:rounded(Math.max(...xs))});
export function auditGeneralization(){
 const cases=[],training=[];
 for(const modelSeed of SEEDS){
  const baseline=new SubwordEncoder(modelSeed),beforeBytes=baseline.save();
  // The selected validation corpus has no label or training overlap.
  const trained=trainContrastiveCandidate(baseline,TRAIN,HOLDOUTS.ecology,
    {steps:280,seed:modelSeed*101+4,drop:0.65,rate:0.08});
  if(trained.interrupted||trained.steps!==280)throw Error('Incomplete training');
  if(!baseline.save().equals(beforeBytes))throw Error('Baseline model mutated');
  training.push(rounded(trained.trainingAfter.reciprocalRank-trained.trainingBefore.reciprocalRank));
  for(const [name,items] of Object.entries(HOLDOUTS))for(const drop of DROPS){
   const viewSeed=131+Math.round(drop*100)+modelSeed*7;
   const before=evaluateViews(baseline,items,{seed:viewSeed,drop});
   const after=evaluateViews(trained.candidate,items,{seed:viewSeed,drop});
   cases.push({modelSeed,domain:name,drop,
     beforeMRR:rounded(before.reciprocalRank),
     afterMRR:rounded(after.reciprocalRank),
     deltaMRR:rounded(after.reciprocalRank-before.reciprocalRank),
     beforeTop1:before.top1,afterTop1:after.top1,total:after.total});
  }
 }
 const held=cases.map(c=>c.deltaMRR);
 return {
  schema:1,method:'synthetic-multiseed-masked-retrieval',
  modelSeeds:SEEDS,dropRates:DROPS,
  cases,summary:{caseCount:cases.length,
    trainingMrrChange:summarize(training),holdoutMrrChange:summarize(held),
    improved:cases.filter(c=>c.deltaMRR>0).length,
    unchanged:cases.filter(c=>c.deltaMRR===0).length,
    regressed:cases.filter(c=>c.deltaMRR<0).length},
  recommendation:'NOT_APPROVED_FOR_AUTOMATIC_PROMOTION',
  limitations:'Synthetic data and small holdouts; no real-world retrieval or knowledge improvement proved.'
 };
}
if(process.argv[1]?.endsWith('contrastive-generalization.mjs')){
 const result=auditGeneralization();
 console.log(JSON.stringify(result.summary,null,2));
 if(process.argv[2])fs.writeFileSync(process.argv[2],JSON.stringify(result,null,2)+'\n');
}
