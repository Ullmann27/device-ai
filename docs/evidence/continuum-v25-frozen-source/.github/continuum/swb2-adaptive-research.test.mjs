import test from 'node:test';
import assert from 'node:assert/strict';
import {SubwordEncoder,MAGIC_SWB2} from './portable-subword.mjs';
import {trainSwb2Adaptive} from './swb2-adaptive-research.mjs';

test('SWB2 adaptive gradients reduce supervised loss and preserve source snapshot',()=>{
 const base=SubwordEncoder.createExperimentalSwb2(73);
 const snapshot=base.save();
 const pairs=[
  {left:'Solarmodule liefern Energie',right:'Photovoltaik wandelt Sonnenlicht in Strom',similar:true},
  {left:'Getriebe überträgt Motorleistung',right:'Drehmoment gelangt zur Achse',similar:true},
  {left:'Grüne Pflanzen erzeugen Zucker',right:'Photosynthese verwendet Sonnenlicht',similar:true},
  {left:'Solarmodule liefern Energie',right:'Sauerteigbrot benötigt Mehl',similar:false},
  {left:'Getriebe überträgt Motorleistung',right:'Pflanzen blühen im Frühling',similar:false}
 ];
 const r=trainSwb2Adaptive(base,pairs,{steps:300,rate:0.02});
 assert.equal(r.steps,300);
 assert.ok(r.after.loss<r.before.loss,'adaptive training should reduce supervised loss');
 assert.deepEqual(base.save(),snapshot);
 const out=r.candidate.save();
 assert.equal(out.length,32960);
 assert.equal(out.readInt32BE(0),MAGIC_SWB2);
 assert.deepEqual(SubwordEncoder.restore(out).save(),out);
 assert.ok(r.candidate.embeddings.every(x=>Number.isFinite(x)&&Math.abs(x)<=8));
});
