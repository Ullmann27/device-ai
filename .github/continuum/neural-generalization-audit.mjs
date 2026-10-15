// Frozen, research-only SWB2 holdout benchmark. A disappointing result is
// emitted unchanged: no synthetic semantic mocks and no automatic model promotion.
import fs from 'node:fs';
import {SubwordEncoder,sha256} from './portable-subword.mjs';
import {auditHybridHoldouts,rankExperimentalHybrid} from './experimental-hybrid-retrieval.mjs';
import {TRAIN,VALIDATION} from './neural-curriculum.mjs';
export const MEMORIES=Object.freeze([
 {id:'solar',text:'Photovoltaikzellen verwandeln Strahlungsenergie in elektrischen Gleichstrom.'},
 {id:'motor',text:'Ein Getriebe überträgt die Rotationskraft auf die Antriebswelle.'},
 {id:'garden',text:'Pflanzen erzeugen Zucker durch Photosynthese mit Sonnenlicht.'},
 {id:'bread',text:'Sauerteigbrot benötigt Mehl Wasser und lange Fermentation.'}
]);
export const HOLDOUTS=Object.freeze([
 {id:'solar-1',query:'Liefern Solarmodule Energie aus Lichteinfall?',targetId:'solar'},
 {id:'motor-1',query:'Wie gelangt das Drehmoment eines Fahrzeugmotors zu den Rädern?',targetId:'motor'},
 {id:'plants-1',query:'Wie versorgen grüne Gewächse sich bei Tageslicht?',targetId:'garden'},
 {id:'other-1',query:'Wann wird die Oper in Salzburg eröffnet?',targetId:null},
 {id:'other-2',query:'Wie teuer ist ein neues Bahnticket?',targetId:null}
]);
export const TRAINING=Object.freeze([
 {left:'Solarmodule erzeugen Strom',right:'Photovoltaikzellen gewinnen elektrischen Strom',similar:true},
 {left:'Lichteinfall liefert Energie',right:'Strahlungsenergie wird zu Elektrizität',similar:true},
 {left:'Fahrzeugmotoren drehen Räder',right:'Rotationskraft treibt die Antriebswelle',similar:true},
 {left:'Gewächse wachsen am Tag',right:'Pflanzen nutzen Sonnenlicht zur Photosynthese',similar:true},
 {left:'Solarmodule wandeln Licht',right:'Sauerteigbrot besteht aus Mehl',similar:false},
 {left:'Getriebe treibt eine Achse',right:'Grüne Pflanzen produzieren Zucker',similar:false}
]);
export function audit() {
 const trainTexts=new Set(TRAINING.flatMap(e=>[e.left,e.right]));
 const heldTexts=[...HOLDOUTS.map(x=>x.query),...MEMORIES.map(x=>x.text)];
 if(heldTexts.some(text=>trainTexts.has(text)))throw Error('Training/holdout overlap');
 const model=SubwordEncoder.createExperimentalSwb2(73);
 const intentBefore=model.evaluate(VALIDATION.intent);
 const scoresFor=m=>HOLDOUTS.map(c=>({id:c.id,scores:MEMORIES.map(x=>({id:x.id,semantic:Number(m.similarity(c.query,x.text).toFixed(6))}))}));
 const before=auditHybridHoldouts(model,HOLDOUTS,MEMORIES);
 const rawBefore=scoresFor(model);
 const initialHash=sha256(model.save());
 for(let epoch=0;epoch<120;epoch++){
  for(const e of TRAINING)model.updateSemantic(e,0.075);
  model.updateIntent(TRAIN.intent[epoch%TRAIN.intent.length],0.03);
 }
 const after=auditHybridHoldouts(model,HOLDOUTS,MEMORIES);
 const rawAfter=scoresFor(model);
 const intentAfter=model.evaluate(VALIDATION.intent);
 const negative=HOLDOUTS.filter(x=>x.targetId===null);
 const falsePositives=negative.filter(x=>rankExperimentalHybrid(model,x.query,MEMORIES,{limit:1}).length>0).length;
 return {
  schema:1,model:'SWB2_EXPERIMENTAL',seed:73,trainExamples:TRAINING.length,holdoutCount:HOLDOUTS.length,
  trainFingerprint:sha256(JSON.stringify(TRAINING)),holdoutFingerprint:sha256(JSON.stringify({MEMORIES,HOLDOUTS})),
  initialHash,trainedHash:sha256(model.save()),
  before,after,rawBefore,rawAfter,falsePositives,negativeQueries:negative.length,
  intentBefore,intentAfter,
  promotionEligible:after.neuralCorrect>before.neuralCorrect &&
    falsePositives===0 && intentAfter.correct>=intentBefore.correct
 };
}
if(process.argv[1]?.endsWith('neural-generalization-audit.mjs')){
 const result=audit(),json=JSON.stringify(result,null,2)+'\n';
 if(process.argv[2])fs.writeFileSync(process.argv[2],json);
 console.log('SWB2 heldout top1:',result.before.neuralCorrect,'->',result.after.neuralCorrect,
  'negative false positives:',result.falsePositives,'intent:',result.intentBefore.correct,'->',result.intentAfter.correct,
  'promotionEligible:',result.promotionEligible);
}
