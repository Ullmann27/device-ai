import test from 'node:test';
import assert from 'node:assert/strict';
import {counterexampleSelection,evidenceChoice,boundedContextSelection,runShadowStudy} from './continuum-capability-research.mjs';

test('held-out arithmetic examples expose overfitted candidates without executing model code',()=>{
 const candidates=[{id:'bad',run:Math.abs},{id:'good',run:x=>x*x}];
 const result=counterexampleSelection({candidates,training:[0,1],challenge:[2,-2],oracle:x=>x*x});
 assert.equal(result.baseline,'bad');assert.equal(result.counterexampleVerified,'good');
 assert.deepEqual(result.counterexamples,[2,-2]);
});

test('evidence filter abstains on unaudited or self-supported claims',()=>{
 assert.deepEqual(evidenceChoice([{value:'new',at:10,independent:0,verified:false},
   {value:'old',at:1,independent:2,verified:true}]),
 {baseline:'new',selected:'old',status:'INDEPENDENTLY_SUPPORTED'});
 assert.equal(evidenceChoice([{value:'x',at:1,independent:1,verified:true}]).status,'INSUFFICIENT_EVIDENCE');
});

test('context packer respects exact budget and never invents extra items',()=>{
 const items=[{id:'recent',tokens:4,priority:0,verified:false},
  {id:'older',tokens:3,priority:99,verified:true}];
 const r=boundedContextSelection({budget:4,items});
 assert.deepEqual(r.baseline,['recent']);assert.deepEqual(r.selected,['older']);
 assert.throws(()=>boundedContextSelection({budget:2,items}),/INVALID_CONTEXT_BUDGET/);
});

test('all three shadow experiments report independently scored frozen holdouts',()=>{
 const r=runShadowStudy();
 assert.equal(r.noModelExecution,true);
 assert.equal(r.candidateRepair.baselineCorrect,0);
 assert.equal(r.candidateRepair.selectedCorrect,4);
 assert.equal(r.evidence.baselineCorrect,0);
 assert.equal(r.evidence.selectedCorrect,3);
 assert.equal(r.context.baselineFullyCovered,0);
 assert.equal(r.context.selectedFullyCovered,2);
});
