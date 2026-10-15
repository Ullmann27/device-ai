import test from 'node:test';
import assert from 'node:assert/strict';
import {SubwordEncoder, weightedBuckets, MAGIC_SWB1, MAGIC_SWB2, buckets} from './portable-subword.mjs';
import {TrainableContextDecoder} from './trainable-context-decoder.mjs';

const sigmoid=x=>x>=0?1/(1+Math.exp(-x)):Math.exp(x)/(1+Math.exp(x));
function lossIntent(m,e) {
 const p=Math.max(1e-12,Math.min(1-1e-12,m.intentScore(e.text)));
 return -(e.label*Math.log(p)+(1-e.label)*Math.log(1-p));
}
function lossSem(m,e) {
 const p=Math.max(1e-12,Math.min(1-1e-12,m.similarity(e.left,e.right)));
 return -(Number(e.similar)*Math.log(p)+(1-Number(e.similar))*Math.log(1-p));
}
function finite(m,e,index,sem) {
 const original=m.embeddings[index],epsilon=1e-6,fn=sem?lossSem:lossIntent;
 m.embeddings[index]=original+epsilon;const plus=fn(m,e);
 m.embeddings[index]=original-epsilon;const minus=fn(m,e);
 m.embeddings[index]=original;
 return (plus-minus)/(2*epsilon);
}

test('legacy SWB1 magic, features and snapshot roundtrip remain unchanged',()=>{
 const original=new SubwordEncoder(73);
 assert.equal(original.version,1);
 assert.deepEqual(original.features('pflanze sonnenlicht'),buckets('pflanze sonnenlicht'));
 const saved=original.save();
 assert.equal(saved.length,32960);
 assert.equal(saved.readInt32BE(0),MAGIC_SWB1);
 assert.deepEqual(SubwordEncoder.restore(saved).save(),saved);
 assert.equal(SubwordEncoder.restore(saved).intentScore('mein lernkern'),original.intentScore('mein lernkern'));
});

test('SWB2 snapshot explicitly versioned, normalized and not silently upgraded',()=>{
 const m=SubwordEncoder.createExperimentalSwb2(73);
 const ids=weightedBuckets('Solarmodule gewinnen elektrische Energie');
 assert.ok(ids.length>5 && ids.length<=128);
 assert.ok(Math.abs(ids.reduce((sum,b)=>sum+b.weight,0)-1)<1e-12);
 assert.equal(new Set(ids.map(x=>x.id)).size,ids.length);
 const raw=m.save();
 assert.equal(raw.length,32960);
 assert.equal(raw.readInt32BE(0),MAGIC_SWB2);
 const copy=SubwordEncoder.restore(raw);
 assert.equal(copy.version,2);
 assert.deepEqual(copy.save(),raw);
 const old=new SubwordEncoder(73);
 assert.equal(old.save().readInt32BE(0),MAGIC_SWB1);
 assert.notDeepEqual(m.encode('Solarmodule gewinnen elektrische Energie'),old.encode('Solarmodule gewinnen elektrische Energie'));
});

test('SWB2 intent and semantic gradients agree with central finite differences',()=>{
 const intent={text:'Solarmodule wandeln Licht in Strom um',label:1},sem={
  left:'Photovoltaik Solarmodule erzeugen Strom',right:'Motor Getriebe bewegen Räder',similar:false
 };
 for(const [e,isSem,seed] of [[intent,false,83],[sem,true,91]]){
  const m=SubwordEncoder.createExperimentalSwb2(seed);
  const ids=m.features(isSem?e.left:e.text).slice(0,4);
  const indexes=ids.flatMap(({id})=>[id*16,id*16+8,id*16+15]);
  const old=m.embeddings.slice();
  const numerics=indexes.map(i=>finite(m,e,i,isSem));
  const rate=0.005;
  if(isSem)m.updateSemantic(e,rate);else m.updateIntent(e,rate);
  indexes.forEach((i,j)=>{
   const actual=(old[i]-m.embeddings[i])/rate;
   assert.ok(Math.abs(actual-numerics[j])<4e-7,
    (isSem?'semantic':'intent')+' gradient '+i+': '+actual+' vs '+numerics[j]);
  });
 }
});

test('experimental neural token decoder has real loss updates, checkpoint and forbidden-token mask',()=>{
 const enc=SubwordEncoder.createExperimentalSwb2(73);
 const vocabulary=['lokale','architektur','verarbeitet','sprache','gerät','cloud','sicher'];
 const dec=new TrainableContextDecoder(enc,vocabulary);
 const q='Wie arbeitet die lokale Architektur?';
 const memory='Lokale Architektur verarbeitet Sprache sicher auf dem Gerät';
 const target=['lokale','architektur','verarbeitet','sprache','gerät'];
 const before=dec.sequenceLoss(q,memory,target);
 for(let i=0;i<90;i++)dec.trainSequence(q,memory,target,0.25);
 const after=dec.sequenceLoss(q,memory,target);
 assert.ok(after<before,'decoder must improve on explicitly trained sequence '+before+' -> '+after);
 const checkpoint=dec.save();
 assert.equal(checkpoint.updates,90);
 const copy=TrainableContextDecoder.restore(enc,checkpoint);
 assert.equal(copy.sequenceLoss(q,memory,target),after);
 const constrained=copy.generate(q,memory,{maxTokens:12,forbiddenTerms:['cloud','sicher']});
 assert.ok(constrained.tokens.every(t=>t!=='cloud'&&t!=='sicher'));
 const modified=SubwordEncoder.createExperimentalSwb2(75);
 assert.throws(()=>TrainableContextDecoder.restore(modified,checkpoint),/snapshot/);
});

test('separate positive and unrelated semantic examples provide measurable, explicitly experimental learning',()=>{
 const m=SubwordEncoder.createExperimentalSwb2(73);
 const pos={left:'Solarmodule auf dem Haus',right:'Photovoltaikzellen auf dem Dach',similar:true};
 const neg={left:'Solarmodule auf dem Haus',right:'Sauerteigbrot aus Mehl',similar:false};
 const before=lossSem(m,pos)+lossSem(m,neg);
 for(let epoch=0;epoch<220;epoch++){
  m.updateSemantic(pos,0.08);m.updateSemantic(neg,0.08);
 }
 const after=lossSem(m,pos)+lossSem(m,neg);
 assert.ok(after<before,'training should reduce supervised loss');
 // Holdout quality is a separate promotion gate; this test does not claim it.
 const heldOut=m.similarity('Energie aus Sonnenlicht','elektrischer Strom aus Solarzellen');
 assert.ok(Number.isFinite(heldOut)&&heldOut>=0&&heldOut<=1);
});
