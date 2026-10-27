import test from 'node:test';
import assert from 'node:assert/strict';
import {TRAIN,VALIDATION,AUDIT,ANDROID_VALIDATION} from './neural-curriculum.mjs';
import {TRANSFER_SUITE_V1} from './neural-fresh-transfer-20261026.mjs';

const normalize = s => s.toLowerCase().replace(/[^\p{L}\p{N}]+/gu,' ').trim();
test('transfer inventory is balanced, frozen, and not a training or selection fixture',()=>{
 assert.equal(TRANSFER_SUITE_V1.intent.length,20);
 assert.equal(TRANSFER_SUITE_V1.semantic.length,32);
 assert.equal(TRANSFER_SUITE_V1.intent.filter(x=>x.label===0).length,10);
 assert.equal(TRANSFER_SUITE_V1.intent.filter(x=>x.label===1).length,10);
 assert.equal(TRANSFER_SUITE_V1.semantic.filter(x=>x.similar).length,16);
 assert.equal(TRANSFER_SUITE_V1.semantic.filter(x=>!x.similar).length,16);
 assert.equal(Object.isFrozen(TRANSFER_SUITE_V1),true);
 assert.equal(Object.isFrozen(TRANSFER_SUITE_V1.intent),true);
 assert.equal(Object.isFrozen(TRANSFER_SUITE_V1.semantic),true);
});
test('transfer phrases do not duplicate pre-existing train/validation/audit samples',()=>{
 const baseline=new Set([TRAIN,VALIDATION,AUDIT,ANDROID_VALIDATION].flatMap(set=>
  [...set.intent.map(x=>normalize(x.text)),
   ...set.semantic.flatMap(x=>[normalize(x.left),normalize(x.right)])]));
 const target=[...TRANSFER_SUITE_V1.intent.map(x=>x.text),
  ...TRANSFER_SUITE_V1.semantic.flatMap(x=>[x.left,x.right])];
 for(const phrase of target)assert.equal(baseline.has(normalize(phrase)),false,phrase);
});
test('semantic positive-negative pairs share the same premise but have distinct conclusions',()=>{
 const positive=TRANSFER_SUITE_V1.semantic.filter(x=>x.similar);
 const negative=TRANSFER_SUITE_V1.semantic.filter(x=>!x.similar);
 for(let i=0;i<positive.length;i++){
  assert.equal(positive[i].left,negative[i].left);
  assert.notEqual(normalize(positive[i].right),normalize(negative[i].right));
  assert.ok(positive[i].right.length>8&&negative[i].right.length>8);
 }
 const unique=new Set(TRANSFER_SUITE_V1.semantic.map(x=>
  normalize(x.left)+'\u0000'+normalize(x.right)));
 assert.equal(unique.size,TRANSFER_SUITE_V1.semantic.length);
});
