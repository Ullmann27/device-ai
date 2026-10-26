import test from 'node:test';
import assert from 'node:assert/strict';
import {createNeuralState,exportWeights,trainRound,ingestPublicSources} from './neural-core.mjs';
import {STEP_BUDGETS,REPLAY_ABLATIONS,characterizeStagnation,
 runShadowBudgetSweep} from './neural-shadow-budget-lab.mjs';

test('two short experiments preserve exact original model bytes, rounds and history',()=>{
 const original=createNeuralState(),initial=JSON.stringify(original),bytes=exportWeights(original);
 const result=runShadowBudgetSweep(original,{budgets:[250,500],replayAblations:[REPLAY_ABLATIONS[0]],now:42});
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

test('existing trainRound defaults are identical to explicit CONTROL policy',()=>{
 const state=createNeuralState();
 const original=trainRound(state,{steps:250,now:10});
 const explicit=trainRound(state,{steps:250,now:10,
   researchReplayEvery:8,researchReplayRate:.035});
 assert.equal(original.report.candidateHash,explicit.report.candidateHash);
 assert.equal(original.report.sourceUpdates,explicit.report.sourceUpdates);
 assert.equal(original.report.selectedSteps,explicit.report.selectedSteps);
 assert.equal(original.report.accepted,explicit.report.accepted);
 assert.deepEqual(exportWeights(original.state),exportWeights(explicit.state));
});
test('research-disabled public replay produces exactly zero public gradients',()=>{
 const source={source:'Wikipedia',url:'https://de.wikipedia.org/wiki/Forschung',
   title:'Wissenschaftliche Vergleichsstudie',
   text:'Diese Forschungszusammenfassung untersucht Unterschiede zwischen mehreren Lernverfahren und Validierungsmethoden.'};
 const seeded=ingestPublicSources(createNeuralState(),[source],100);
 assert.equal(seeded.replay.length,1);
 const trial=trainRound(seeded,{steps:250,now:100,researchReplayEvery:0});
 assert.equal(trial.report.sourceUpdates,0);
});

test('default research sweep is strictly bounded to nine non-promotable trials',()=>{
 const state=createNeuralState();
 const result=runShadowBudgetSweep(state,{budgets:[250]});
 assert.equal(result.profiles.length,3);
 assert.deepEqual(result.profiles.map(x=>x.policy),
   REPLAY_ABLATIONS.map(x=>x.name));
 assert.ok(result.profiles.every(x=>x.auditUsedForSelection===false));
 assert.equal(result.weightsWritten,false);
});
test('oversized ablation grid rejected before training',()=>{
 const original=createNeuralState(),before=JSON.stringify(original);
 assert.throws(()=>runShadowBudgetSweep(original,{budgets:[250,500,1000,2000]}),
   /INVALID_SHADOW_BUDGET/);
 assert.equal(JSON.stringify(original),before);
});
test('legacy model hash and every baseline byte survive research measurements',()=>{
 const original=createNeuralState(),snapshot=JSON.stringify(original),bytes=exportWeights(original);
 const trial=runShadowBudgetSweep(original,{budgets:[250],replayAblations:[REPLAY_ABLATIONS[2]]});
 assert.equal(trial.preservedBaseline,true);
 assert.equal(JSON.stringify(original),snapshot);
 assert.deepEqual(exportWeights(original),bytes);
 assert.ok(trial.profiles.every(row=>row.publicSourceUpdates===0));
});
