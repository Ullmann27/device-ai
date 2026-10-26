/**
 * Research-only controlled SWB1 training-budget comparison.
 * All profiles start from exactly the same previously accepted weights.
 * Never returns a promotable model, updates a state file, or uses the report-only
 * AUDIT suite to select a training candidate.
 */
import {restoreNeuralState,trainRound,exportWeights} from './neural-core.mjs';
import {sha256} from './portable-subword.mjs';

export const STEP_BUDGETS=Object.freeze([250,500,1000,2000,4000,8000,12000]);
export const REPLAY_ABLATIONS=Object.freeze([
 Object.freeze({name:'CONTROL_EXISTING',researchReplayEvery:8,researchReplayRate:.035}),
 Object.freeze({name:'LOW_PUBLIC_REPLAY',researchReplayEvery:16,researchReplayRate:.015}),
 Object.freeze({name:'NO_PUBLIC_REPLAY',researchReplayEvery:0,researchReplayRate:.035})
]);
export function characterizeStagnation(input){
 const state=restoreNeuralState(input);
 const recent=state.history.slice(-10);
 const validationRegressions=recent.filter(x=>x.reason==='VALIDATION_REGRESSION').length;
 const semanticLost=recent.filter(x=>
   x.validationAfter?.semantic.correct < x.validationBefore?.semantic.correct).length;
 return Object.freeze({rounds:state.rounds,accepted:state.accepted,
   lastTenValidationRegressions:validationRegressions,
   lastTenSemanticRegressions:semanticLost,
   repeatsMayBeUnproductive:recent.length>=3&&validationRegressions>=3,
   noGeneralIntelligenceClaim:true});
}
export function runShadowBudgetSweep(input,{budgets=STEP_BUDGETS,
 replayAblations=REPLAY_ABLATIONS,now=1,maxMillis=30000,cancelled=()=>false}={}){
 if(!Array.isArray(budgets)||budgets.length<1||budgets.length>STEP_BUDGETS.length||
   budgets.some(x=>!STEP_BUDGETS.includes(x))||new Set(budgets).size!==budgets.length||
   !Number.isInteger(maxMillis)||maxMillis<1||maxMillis>30000 ||
   !Array.isArray(replayAblations)||replayAblations.length<1||
   replayAblations.length>REPLAY_ABLATIONS.length||
   replayAblations.some(x=>!REPLAY_ABLATIONS.some(allowed=>
     allowed.name===x?.name && allowed.researchReplayEvery===x.researchReplayEvery &&
     allowed.researchReplayRate===x.researchReplayRate))||
   new Set(replayAblations.map(x=>x.name)).size!==replayAblations.length)
   throw Error('INVALID_SHADOW_BUDGET');
 const initial=restoreNeuralState(input);
 const originalHash=initial.modelHash,rounds=initial.rounds,accepted=initial.accepted;
 const reports=[];
 for(const policy of replayAblations)for(const steps of budgets){
   if(cancelled())break;
   const {state,report}=trainRound(initial,{steps,now,maxMillis,cancelled,
     researchReplayEvery:policy.researchReplayEvery,
     researchReplayRate:policy.researchReplayRate});
   if(initial.modelHash!==originalHash||initial.rounds!==rounds||
       initial.accepted!==accepted||exportWeights(initial).equals(exportWeights(state))===false&&
       !report.accepted&&state.modelHash!==originalHash)
     throw Error('SHADOW_CHANGED_ORIGINAL_WEIGHTS');
   reports.push(Object.freeze({
     policy:policy.name,publicSourceUpdates:report.sourceUpdates,
     stepsRequested:steps,stepsCompleted:report.steps,selectedSteps:report.selectedSteps,
     wouldPassExistingGate:report.accepted,
     reason:report.reason,
     selectedCheckpointHash:report.accepted?report.candidateHash:null,
     intentBefore:report.validationBefore.intent.correct,
     intentAfter:report.validationAfter.intent.correct,
     semanticBefore:report.validationBefore.semantic.correct,
     semanticAfter:report.validationAfter.semantic.correct,
     auditIntentBefore:report.auditBefore.intent.correct,
     auditIntentAfter:report.auditAfter.intent.correct,
     auditSemanticBefore:report.auditBefore.semantic.correct,
     auditSemanticAfter:report.auditAfter.semantic.correct,
     auditUsedForSelection:report.auditUsedForSelection
   }));
 }
 if(initial.modelHash!==originalHash||sha256(exportWeights(initial))!==originalHash)
   throw Error('SHADOW_BASELINE_CHANGED');
 return Object.freeze({
   schema:'continuum-swb1-budget-shadow-v1',status:'RESEARCH_ONLY_NOT_PROMOTABLE',
   startingModelHash:originalHash,startingRounds:rounds,startingAccepted:accepted,
   preservedBaseline:true,auditSelectionForbidden:true,
   diagnosis:characterizeStagnation(initial),profiles:Object.freeze(reports),
   promoted:false,weightsWritten:false,requiresFreshUnseenHoldout:true
 });
}
