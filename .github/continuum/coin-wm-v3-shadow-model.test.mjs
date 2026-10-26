import test from 'node:test';
import assert from 'node:assert/strict';
import {CoinWmV3ShadowModel} from './coin-wm-v3-shadow-model.mjs';

const s=(...on)=>Float64Array.from({length:16},(_,i)=>on.includes(i)?1:0);
const next=Float64Array.from({length:8},()=>0);

test('research-only model updates shared encoder but can never grant authority',()=>{
  const model=new CoinWmV3ShadowModel(4);
  assert.equal(model.status,'RESEARCH_SHADOW');
  assert.equal(model.promotionEligible,false);
  assert.equal(model.encoderWeightDriftL2(),0);
  const before=model.encoder.slice();
  for(let epoch=0;epoch<100;epoch++){
    for(let op=0;op<4;op++){
      model.trainResearchSample({
        state16:s(op,8+op,15),operatorIndex:op,success:true,observedNext8:next
      });
    }
  }
  assert.equal(model.updatesCount,400);
  assert.ok(model.encoderWeightDriftL2()>0);
  assert.notDeepEqual([...model.encoder],[...before]);
  assert.deepEqual(model.rank(s(0,8,15),4).map(x=>x.operatorIndex).sort(),[0,1,2,3]);
  assert.equal(model.promotionEligible,false);
});

test('research inputs must have complete finite shapes and capped update policy',()=>{
  const model=new CoinWmV3ShadowModel(2);
  const labels={state16:s(0),operatorIndex:0,success:true,observedNext8:next};
  const before=model.updatesCount;
  assert.throws(()=>model.trainResearchSample({...labels,state16:new Float64Array(15)}),/INVALID_STATE/);
  assert.throws(()=>model.trainResearchSample({...labels,operatorIndex:2}),/INVALID_OPERATOR/);
  assert.throws(()=>model.trainResearchSample({...labels,success:'verified'}),/INVALID_RESEARCH_LABEL/);
  assert.throws(()=>model.trainResearchSample({...labels,observedNext8:Float64Array.from({length:8},()=>NaN)}),/INVALID_RESEARCH_LABEL/);
  assert.throws(()=>model.trainResearchSample({...labels,learningRate:10}),/INVALID_HYPERPARAMETER/);
  assert.throws(()=>model.rank(s(0),0),/INVALID_ACTIVE_COUNT/);
  assert.equal(model.updatesCount,before);
});

test('state, operator and label scoring are deterministic without external model or sandbox',()=>{
  const a=new CoinWmV3ShadowModel(2),b=new CoinWmV3ShadowModel(2);
  const observations=[
    {state16:s(0,8),operatorIndex:0,success:true,observedNext8:next},
    {state16:s(2,9),operatorIndex:1,success:false,observedNext8:Float64Array.from({length:8},(_,i)=>i===2?1:0)}
  ];
  for(const row of observations){a.trainResearchSample(row);b.trainResearchSample(row);}
  assert.deepEqual(a.rank(s(0,8)),b.rank(s(0,8)));
  assert.equal(a.encoderWeightDriftL2(),b.encoderWeightDriftL2());
  a.consolidateEwcAnchors(2);
  assert.equal(a.anchorEnc.length,192);
  assert.equal(a.anchorPolicy[0].length,12);
  assert.equal(a.promotionEligible,false);
});

test('no untrusted-code executor, host primitives or production adapter is present',()=>{
  const model=new CoinWmV3ShadowModel(1);
  assert.equal(Object.hasOwn(model,'execute'),false);
  assert.equal(typeof model.execute,'undefined');
  assert.equal(Object.hasOwn(model,'grantPermission'),false);
  assert.equal(Object.hasOwn(model,'trainFromOwnerMemory'),false);
});
