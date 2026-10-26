import test from 'node:test';
import assert from 'node:assert/strict';
import {createNeuralState,exportWeights} from './neural-core.mjs';
import {STEP_BUDGETS,characterizeStagnation,
 runShadowBudgetSweep} from './neural-shadow-budget-lab.mjs';

test('two short experiments preserve exact original model bytes, rounds and history',()=>{
 const original=createNeuralState(),initial=JSON.stringify(original),bytes=exportWeights(original);
 const result=runShadowBudgetSweep(original,{budgets:[250,500],now:42});
 assert.equal(result.status,'RESEARCH_ONLY_NOT_PROMOTABLE');
 assert.equal(result.promoted,false);assert.equal(result.weightsWritten,false);
 assert.equal(result.requiresFreshUnseenHoldout,true);
 assert.equal(result.auditSelectionForbidden,true);
 assert.deepEqual(result.profiles.map(x=>x.stepsRequested),[250,500]);
 assert.ok(result.profiles.every(x=>x.auditUsedForSelection===false));
 assert.equal(JSON.stringify(original),initial);
 assert.deepEqual(exportWeights(original),bytes);
 assert.ok(Object.isFrozen(result));assert.ok(Object.isFrozen(result.profiles));
});
test('malformed budgets fail before any research training',()=>{
 const s=createNeuralState();
 for(const budgets of [[],[123],[250,250],[...STEP_BUDGETS,250]]){
   assert.throws(()=>runShadowBudgetSweep(s,{budgets}),/INVALID_SHADOW_BUDGET/);
 }
});
test('cancellation leaves baseline unchanged',()=>{
 const s=createNeuralState(),before=JSON.stringify(s);
 const result=runShadowBudgetSweep(s,{budgets:[250],cancelled:()=>true});
 assert.equal(result.profiles.length,0);
 assert.equal(JSON.stringify(s),before);
});
test('repeated semantic regression is reported, never reclassified as success',()=>{
 const s=createNeuralState();s.rounds=12;s.accepted=2;
 s.history=Array.from({length:10},(_,i)=>({round:i+3,
   reason:'VALIDATION_REGRESSION',validationBefore:{semantic:{correct:13}},
   validationAfter:{semantic:{correct:11}}}));
 const diagnosis=characterizeStagnation(s);
 assert.equal(diagnosis.lastTenSemanticRegressions,10);
 assert.equal(diagnosis.lastTenValidationRegressions,10);
 assert.equal(diagnosis.repeatsMayBeUnproductive,true);
 assert.equal(diagnosis.accepted,2);
});
