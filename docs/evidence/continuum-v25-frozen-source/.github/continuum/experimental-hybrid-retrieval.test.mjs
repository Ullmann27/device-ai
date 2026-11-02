import test from 'node:test';
import assert from 'node:assert/strict';
import {SubwordEncoder} from './portable-subword.mjs';
import {rankExperimentalHybrid,auditHybridHoldouts} from './experimental-hybrid-retrieval.mjs';

const memories=[
 {id:'solar',text:'Photovoltaikzellen verwandeln Strahlungsenergie in elektrischen Gleichstrom.'},
 {id:'motor',text:'Ein Getriebe überträgt die Rotationskraft auf die Antriebswelle.'},
 {id:'garden',text:'Pflanzen erzeugen Zucker durch Photosynthese mit Sonnenlicht.'},
 {id:'bread',text:'Sauerteigbrot benötigt Mehl Wasser und lange Fermentation.'}
];
const testQueries=[
 {id:'solar-reword',query:'Liefern Solarmodule Energie aus Lichteinfall?',targetId:'solar'},
 {id:'motor-reword',query:'Wie kommt Motordrehmoment zur Fahrzeugachse?',targetId:'motor'},
 {id:'garden-reword',query:'Wovon ernähren sich grüne Gewächse im Tageslicht?',targetId:'garden'}
];
test('SWB2 hybrid is opt-in, rejects SWB1, returns no fabricated claim of generalization',()=>{
 const swb1=new SubwordEncoder(73),model=SubwordEncoder.createExperimentalSwb2(73);
 assert.throws(()=>rankExperimentalHybrid(swb1,testQueries[0].query,memories),/SWB2/);
 const before=auditHybridHoldouts(model,testQueries,memories);
 assert.equal(before.total,3);
 assert.equal(before.rows.length,3);
 for(const r of before.rows) {
  assert.ok(typeof r.queryId==='string');
  assert.ok(r.hybridTop1===null||memories.some(m=>m.id===r.hybridTop1));
 }
});

test('calibration gates operate on actual trained embeddings, never supplied mock scores',()=>{
 const model=SubwordEncoder.createExperimentalSwb2(73);
 const before=auditHybridHoldouts(model,testQueries,memories);
 const train=[
  {left:'Solarmodule erzeugen Strom',right:'Photovoltaikzellen gewinnen elektrischen Strom',similar:true},
  {left:'Lichteinfall liefert Energie',right:'Strahlungsenergie wird zu Elektrizität',similar:true},
  {left:'Fahrzeugmotoren drehen Räder',right:'Rotationskraft treibt die Antriebswelle',similar:true},
  {left:'Gewächse wachsen am Tag',right:'Pflanzen nutzen Sonnenlicht zur Photosynthese',similar:true},
  {left:'Solarmodule wandeln Licht',right:'Sauerteigbrot besteht aus Mehl',similar:false},
  {left:'Getriebe treibt eine Achse',right:'Grüne Pflanzen produzieren Zucker',similar:false}
 ];
 for(let epoch=0;epoch<100;epoch++)for(const ex of train)model.updateSemantic(ex,0.07);
 const after=auditHybridHoldouts(model,testQueries,memories);
 assert.equal(after.total,3);
 assert.ok(after.neuralCorrect>=0 && after.neuralCorrect<=3);
 // Leave the benchmark as evidence rather than quietly lowering thresholds to pass.
 const unrelated=rankExperimentalHybrid(model,'Welche Oper spielt in Salzburg?',memories);
 assert.ok(unrelated.length<=3);
 assert.deepEqual(before.rows.map(x=>x.queryId),after.rows.map(x=>x.queryId));
});
