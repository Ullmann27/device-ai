import test from 'node:test';
import assert from 'node:assert/strict';
import {RelationalEncoder,D,features} from './cre-encoder.mjs';
import {orderShadowFeatures,orderShadowEmbed,orderShadowAudit,ORDER_SHADOW_ID} from './cre-order-shadow.mjs';

const LEFT='AgentActivity ruft LearningCore im Hintergrund auf';
const RIGHT='LearningCore ruft AgentActivity im Hintergrund auf';

test('baseline CRE1 is invariant to reordered identical words',()=>{
 const a=features(LEFT),b=features(RIGHT);
 assert.deepEqual(a,b,'same lexical multiset must collide');
 const model=new RelationalEncoder(73);
 const before=orderShadowAudit(model,LEFT,RIGHT);
 assert.equal(before.schema,ORDER_SHADOW_ID);
 assert.ok(before.baselineDistance<1e-12);
 assert.ok(before.shadowDistance>1e-8);
 assert.equal(before.productionEnabled,false);
 assert.equal(before.checkpointCompatibleForInference,false);
});

test('directional shadow preserves normalized bounded features and same-text equality',()=>{
 const a=orderShadowFeatures(LEFT),b=orderShadowFeatures(RIGHT);
 assert.notDeepEqual(a,b,'reversed dependencies must have distinct sparse inputs');
 for(const x of [a,b]){
  assert.ok(x.length<=features(LEFT).length+100);
  assert.ok(x.every(f=>f.id>=0&&f.id<16384&&f.w>0));
  assert.ok(Math.abs(x.reduce((sum,f)=>sum+f.w,0)-1)<1e-12);
 }
 const model=new RelationalEncoder(27);
 const emb=orderShadowEmbed(model,LEFT,'m');
 assert.equal(emb.length,D);
 assert.ok(Math.abs(emb.reduce((sum,x)=>sum+x*x,0)-1)<1e-12);
 assert.deepEqual(emb,orderShadowEmbed(model,LEFT,'m'));
 assert.throws(()=>orderShadowEmbed(model,LEFT,'unauthorized'),/side/);
});

test('one-content-word input preserves the CRE1 lexical representation',()=>{
 const input='Graph';
 assert.deepEqual(orderShadowFeatures(input),features(input));
});

test('shadow cannot mutate or promote original CRE1 weights',()=>{
 const model=new RelationalEncoder(37);
 const before=model.save();
 orderShadowAudit(model,LEFT,RIGHT);
 assert.deepEqual(model.save(),before,'feature research must not mutate parameters');
});
