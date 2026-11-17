import test from 'node:test';
import assert from 'node:assert/strict';
import {CoinWmV3ShadowModel} from './coin-wm-v3-shadow-model.mjs';
const sample=()=>({state16:Float64Array.from({length:16},(_,i)=>i===0?1:0),operatorIndex:0,success:true,observedNext8:new Float64Array(8)});
const snapshot=m=>JSON.stringify(m,(_,v)=>v instanceof Float64Array?Array.from(v):v);

test('reject finite but unsafe feature magnitudes without mutation',()=>{
 const m=new CoinWmV3ShadowModel(2),before=snapshot(m),row=sample();
 row.state16.fill(Number.MAX_VALUE);
 assert.throws(()=>m.trainResearchSample(row),/INVALID_STATE/);
 assert.equal(snapshot(m),before);
});

test('reject negative transition target without mutation',()=>{
 const m=new CoinWmV3ShadowModel(2),before=snapshot(m),row=sample();
 row.observedNext8[0]=-1;
 assert.throws(()=>m.trainResearchSample(row),/INVALID_RESEARCH_LABEL/);
 assert.equal(snapshot(m),before);
});

test('reject transition target greater than one without mutation',()=>{
 const m=new CoinWmV3ShadowModel(2),before=snapshot(m),row=sample();
 row.observedNext8[0]=2;
 assert.throws(()=>m.trainResearchSample(row),/INVALID_RESEARCH_LABEL/);
 assert.equal(snapshot(m),before);
});

test('score refuses preexisting nonfinite model state',()=>{
 const m=new CoinWmV3ShadowModel(2);m.policy[0][0]=NaN;
 assert.throws(()=>m.score(sample().state16,0),/INVALID_MODEL_STATE/);
});

test('overflowing staged update leaves all live weights unchanged',()=>{
 const m=new CoinWmV3ShadowModel(2),row=sample();
 m.policy[0].fill(1e308);
 const before=snapshot(m);
 assert.throws(()=>m.trainResearchSample(row),/NUMERIC_OVERFLOW|INVALID_MODEL_STATE/);
 assert.equal(snapshot(m),before);
});

test('malformed transition head cannot partially update the model',()=>{
 const m=new CoinWmV3ShadowModel(2);m.transitions[0]=new Float64Array(1);
 const before=snapshot(m);
 assert.throws(()=>m.trainResearchSample(sample()),/INVALID_MODEL_STATE/);
 assert.equal(snapshot(m),before);
});

test('valid endpoint targets and research authority remain unchanged',()=>{
 const m=new CoinWmV3ShadowModel(2),row=sample();
 row.observedNext8.fill(1);m.trainResearchSample(row);
 assert.equal(m.updatesCount,1);assert.equal(m.promotionEligible,false);
 assert.ok(m.rank(row.state16).every(v=>Number.isFinite(v.score)));
});

test('invalid update counter fails closed without model mutation',()=>{
 const m=new CoinWmV3ShadowModel(2);m.updatesCount=-1;
 const before=snapshot(m);
 assert.throws(()=>m.trainResearchSample(sample()),/INVALID_MODEL_STATE/);
 assert.equal(snapshot(m),before);
});

test('overflow after intermediate policy updates rolls back the entire checkpoint',()=>{
 const m=new CoinWmV3ShadowModel(2);
 m.consolidateEwcAnchors();m.anchorEnc.fill(-1e308);m.fisherEnc.fill(1e308);
 const before=snapshot(m);
 assert.throws(()=>m.trainResearchSample(sample()),/NUMERIC_OVERFLOW|INVALID_MODEL_STATE/);
 assert.equal(snapshot(m),before);
});
