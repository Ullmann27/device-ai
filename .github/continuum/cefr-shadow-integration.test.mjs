import test from 'node:test';
import assert from 'node:assert/strict';
import {rankCandidates,runRepairShadowObserver} from './repair-loop.mjs';

const candidateSet=[
 {id:'first',diagnostic:'a',diagnosticKind:'REFERENCE_ERROR',
  strategy:'JS_TYPO_REFERENCE',prior:0.5},
 {id:'second',diagnostic:'a',diagnosticKind:'REFERENCE_ERROR',
  strategy:'JS_MISSING_IMPORT',prior:0.4},
 {id:'revert',diagnostic:'a',diagnosticKind:'REFERENCE_ERROR',
  strategy:'REVERT_FILE_TO_PARENT',prior:1}
];
function run(observer){
 const candidates=structuredClone(candidateSet);
 const ranked=rankCandidates(candidates);
 return {candidates,ranked,result:runRepairShadowObserver(observer,candidates,ranked,
  [],new Set(),[{kind:'REFERENCE_ERROR',fingerprint:'a'}])};
}

test('neural CEFR shadow may rerank within tiers without changing real production order',()=>{
 const state=run(input=>({mode:'CEFR_DEV_SHADOW',modelSha256:'f'.repeat(64),
  shadowOrder:['second','first','revert']}));
 assert.deepEqual(state.ranked.map(x=>x.id),['first','second','revert']);
 assert.deepEqual(state.result.shadowOrder,['second','first','revert']);
 assert.deepEqual(state.result.productionOrder,['first','second','revert'],
  'immutable production ranking remains available for prospectively audited outcomes');
 assert.equal(state.result.reordered,true);
 assert.equal(state.result.modelSha256,'f'.repeat(64));
 assert.deepEqual(state.candidates,candidateSet);
});

test('a corrupted shadow rank cannot elevate destructive full-file revert',()=>{
 const state=run(()=>({mode:'CEFR_DEV_SHADOW',shadowOrder:['revert','first','second']}));
 assert.equal(state.result.mode,'CEFR_SHADOW_REJECTED');
 assert.deepEqual(state.result.shadowOrder,state.ranked.map(x=>x.id));
 assert.deepEqual(state.result.productionOrder,state.ranked.map(x=>x.id));
});

test('observer exceptions, duplicates and invented candidates fail closed',()=>{
 for(const observe of [
  ()=>{throw Error('observer died')},
  ()=>({shadowOrder:['first','first','revert']}),
  ()=>({shadowOrder:['first','second','invented']}),
  ()=>({shadowOrder:null})
 ]){
  const state=run(observe);
  assert.equal(state.result.mode,'CEFR_SHADOW_REJECTED');
  assert.deepEqual(state.result.shadowOrder,state.ranked.map(x=>x.id));
  assert.deepEqual(state.candidates,candidateSet);
 }
});

test('shadow receives cloned inputs and cannot poison real proposal edits or ledger stats',()=>{
 const state=run(input=>{
  input.candidates[0].edits=[{file:'main',oldText:'',newText:''}];
  input.stats.push({kind:'FAKE',successes:1000,failures:0});
  input.diagnostics[0].kind='FAKE';
  input.failedIds.add('bad:xx');
  return {mode:'CEFR_DEV_SHADOW',shadowOrder:['first','second','revert']};
 });
 assert.deepEqual(state.candidates,candidateSet);
 assert.equal(state.result.mode,'CEFR_DEV_SHADOW');
});

test('absent observer changes no report contract',()=>{
 const state=run(null);
 assert.equal(state.result,null);
});
