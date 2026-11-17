import test from 'node:test';
import assert from 'node:assert/strict';
import {SubwordEncoder} from './portable-subword.mjs';

const SCALE=2.5;
const sigmoid=x=>x>=0?1/(1+Math.exp(-x)):Math.exp(x)/(1+Math.exp(x));
function loss(m,left,right,similar){
 const a=m.encode(left),b=m.encode(right);
 const dot=a.reduce((s,v,i)=>s+v*b[i],0);
 const p=Math.max(1e-12,Math.min(1-1e-12,sigmoid(SCALE*dot)));
 return similar?-Math.log(p):-Math.log(1-p);
}
function finiteGradient(model,example,index) {
 const epsilon=1e-5,original=model.embeddings[index];
 model.embeddings[index]=original+epsilon;
 const plus=loss(model,example.left,example.right,example.similar);
 model.embeddings[index]=original-epsilon;
 const minus=loss(model,example.left,example.right,example.similar);
 model.embeddings[index]=original;
 return (plus-minus)/(2*epsilon);
}
test('semantic optimizer agrees with finite-difference gradients on shared/overlapping buckets',()=>{
 const cases=[
  {left:'sonnenlicht pflanze energie',right:'sonnenlicht pflanze energie',similar:true},
  {left:'regen wolken wasser',right:'wolken feuchtigkeit regen',similar:true},
  {left:'motor getriebe fahrzeug',right:'stern galaxie mond',similar:false},
  {left:'ababa ababa',right:'ababa baba',similar:false}
 ];
 for(const [n,e] of cases.entries()){
  const model=new SubwordEncoder(73+n);
  const ids=[...new Set([...model.features(e.left),...model.features(e.right)])];
  const indices=[...new Set([ids[0],ids[Math.floor(ids.length/2)],ids.at(-1)].filter(x=>x!==undefined))]
    .flatMap(bucket=>[bucket*16,bucket*16+7,bucket*16+15]);
  const gradients=indices.map(i=>({index:i,value:finiteGradient(model,e,i)}));
  const rate=0.002,old=model.embeddings.slice();
  model.updateSemantic(e,rate);
  assert.equal(model.updates,1);
  for(const g of gradients){
    const observed=(old[g.index]-model.embeddings[g.index])/rate;
    assert.ok(Math.abs(observed-g.value)<3e-7,
      'case '+n+' weight '+g.index+' measured '+observed+' finite '+g.value);
  }
 }
});
test('semantic SGD moves candidate without touching old SWB1 snapshot',()=>{
 const baseline=new SubwordEncoder(73),saved=baseline.save();
 const candidate=SubwordEncoder.restore(saved);
 candidate.updateSemantic({left:'katze tier',right:'regen wolken',similar:false},0.08);
 assert.deepEqual(baseline.save(),saved);
 assert.notDeepEqual(candidate.save(),saved);
});
