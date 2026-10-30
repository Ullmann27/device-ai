import test from 'node:test';
import assert from 'node:assert/strict';
import {canStartShadowSelfPlay,mutateTypedPolicy,classifyIndependentEvidence,
  autopsyFromOutcome,summarizeExistingLedger,proposeEvolutionMacros,
  executeShadowEvolutionCycle,evolutionDigest,deriveShadowOperatorPriors} from './evolution-core.mjs';

const healthy = (id='test-domain') => ({id,policy:{sliceEndAdjustment:0,clampNonnegative:true},
  visible:[{input:{samples:[1,2,3],start:0,span:3,reserved:0,ceiling:9},expected:3}],
  blind:[{input:{samples:[1],start:0,span:1,reserved:0,ceiling:9},expected:1}]});
const ready={ownerOptIn:true,idle:true,charging:true,connectedToTrustedHost:true,
  activeWorker:false,batteryPercent:80,thermalHeadroom:.9,availableTimeMs:30000};
const candidate={chain:['correct-slice-end','clamp-negative-quota']};
function proof(task,candidate,{passed=true,exitCode=passed?0:1,executedTests=4,
  failedTests=passed?0:1,blindPass=passed,runnerError=false}={}){
  return {evaluator:'independent',taskHash:evolutionDigest(task),candidateHash:evolutionDigest(candidate),
    testSetHash:evolutionDigest(task.blind),executedTests,failedTests,
    exitCode,blindPass,runnerError,evidenceComplete:true};
}

test('shadow gate requires explicit opt-in, idle, charging, trusted host, no concurrent worker and thermal headroom',()=>{
  assert.equal(canStartShadowSelfPlay(ready).eligible,true);
  for(const [key,value] of Object.entries({ownerOptIn:false,idle:false,charging:false,
    connectedToTrustedHost:false,activeWorker:true,batteryPercent:39,thermalHeadroom:.49,availableTimeMs:4999})){
    assert.equal(canStartShadowSelfPlay({...ready,[key]:value}).eligible,false,key);
  }
});
test('mutation applies only to verified typed baseline, never arbitrary text substitutions',()=>{
  assert.deepEqual(mutateTypedPolicy(healthy(),'slice-boundary').policy,
    {sliceEndAdjustment:-1,clampNonnegative:true});
  assert.deepEqual(mutateTypedPolicy(healthy(),'negative-clamp').policy,
    {sliceEndAdjustment:0,clampNonnegative:false});
  assert.deepEqual(mutateTypedPolicy(healthy(),'combined').policy,
    {sliceEndAdjustment:-1,clampNonnegative:false});
  assert.throws(()=>mutateTypedPolicy({...healthy(),policy:{sliceEndAdjustment:-1,clampNonnegative:true}},'combined'),/UNVERIFIED/);
});
test('positive evidence requires all bound hashes, executed tests and complete blind check',()=>{
  const t=mutateTypedPolicy(healthy(),'combined');
  assert.equal(classifyIndependentEvidence(t,candidate,proof(t,candidate)).classification,'VERIFIED_EXPERIMENTAL');
  assert.equal(classifyIndependentEvidence(t,candidate,{...proof(t,candidate),taskHash:'0'.repeat(64)}).classification,'NEUTRAL');
  assert.equal(classifyIndependentEvidence(t,candidate,{...proof(t,candidate),candidateHash:'f'.repeat(64)}).classification,'NEUTRAL');
});
test('runner crash timeout and zero executed tests remain neutral, not a neural negative gradient',()=>{
  const t=mutateTypedPolicy(healthy(),'combined');
  for(const x of [null,proof(t,candidate,{runnerError:true}),
    {...proof(t,candidate),executedTests:0}, {...proof(t,candidate),timedOut:true}])
    assert.equal(classifyIndependentEvidence(t,candidate,x).classification,'NEUTRAL');
});
test('negative label requires actual red executed tests and a consistent failing exit code',()=>{
  const t=mutateTypedPolicy(healthy(),'combined');
  assert.equal(classifyIndependentEvidence(t,candidate,proof(t,candidate,{passed:false})).classification,'VERIFIED_NEGATIVE');
  assert.equal(classifyIndependentEvidence(t,candidate,proof(t,candidate,{passed:false,exitCode:127})).classification,'NEUTRAL');
});
test('autopsy contains evidence hashes, never source code or owner-authorized training flags',()=>{
  const t=mutateTypedPolicy(healthy(),'combined');
  const a=autopsyFromOutcome({task:t,candidate,strategyId:'bounded-composition',proof:proof(t,candidate,{passed:false})});
  assert.equal(a.classification,'VERIFIED_NEGATIVE');
  assert.equal(a.productionLearningAuthorized,false);
  assert.equal(a.neuralTrainingAuthorized,false);
  assert.equal(JSON.stringify(a).includes('samples'),false);
});
test('ledger summary reads existing version 2 evidence without modifying it',()=>{
  const ledger={schemaVersion:2,experiences:[],sessions:[],attempts:[
    {strategy:'JS_TYPO_REFERENCE',outcome:'FAILED_TESTS',tests:[{passed:false,evidenceComplete:true,total:2,executed:2,failed:1,skipped:0,exitCode:1}]},
    {strategy:'JS_TYPO_REFERENCE',outcome:'FAILED_TESTS',tests:[{passed:false,total:0,executed:0,failed:0,skipped:0,signal:'SIGKILL'}]},
    {strategy:'JS_TYPO_REFERENCE',outcome:'VERIFIED',patchSha256:'a'.repeat(64)}]};
  const before=JSON.stringify(ledger);
  const result=summarizeExistingLedger(ledger);
  assert.deepEqual(result.observations.map(a=>a.kind),['LEDGER_EXECUTED_FAILURE','NEUTRAL','LEDGER_VERIFIED_RECORD']);
  assert.equal(result.trainingAuthorized,false);
  assert.equal(JSON.stringify(ledger),before);
});
test('macro review requires distinct successful task IDs and never activates/deletes',()=>{
  const r={classification:'VERIFIED_EXPERIMENTAL',chain:['correct-slice-end','clamp-negative-quota']};
  assert.deepEqual(proposeEvolutionMacros([{...r,taskId:'alpha'},{...r,taskId:'alpha'}]),[]);
  const macros=proposeEvolutionMacros([{...r,taskId:'alpha'},{...r,taskId:'beta'}]);
  assert.equal(macros.length,1);
  assert.equal(macros[0].promoted,false);assert.equal(macros[0].deleteAllowed,false);
});
test('one finite self-play cycle with sealed holdout succeeds without model writes',()=>{
  const tasks=[healthy('task-one'),healthy('task-two'),healthy('task-three')];
  const seen=[];
  const out=executeShadowEvolutionCycle({snapshot:ready,baselineTasks:tasks,
    solver:(task)=>{seen.push(task.blind === task.visible);return candidate;},
    independentVerifier:(task,solved)=>proof(task,solved),cycleId:7});
  assert.equal(out.status,'FINISHED_SHADOW');
  assert.equal(out.verified,3); assert.equal(out.taskCount,3);
  assert.ok(seen.every(Boolean));
  assert.equal(out.macroCandidates.length,1);
  assert.equal(out.ntwTrainingAllowed,false);assert.equal(out.mainWriteAllowed,false);
});
test('one cycle does not run at all when owner conditions fail',()=>{
  let invoked=false;
  const result=executeShadowEvolutionCycle({snapshot:{...ready,activeWorker:true},baselineTasks:[healthy()],
    solver:()=>{invoked=true;return candidate;},independentVerifier:()=>null});
  assert.equal(result.status,'SKIPPED');assert.equal(invoked,false);
});
test('a solver exception does not masquerade as verified failure',()=>{
  const result=executeShadowEvolutionCycle({snapshot:ready,baselineTasks:[healthy()],
    solver:()=>{throw Error('executor unavailable');}, independentVerifier:()=>null});
  assert.equal(result.verified,0);assert.equal(result.negative,0);assert.equal(result.neutral,1);
});
test('strict cycle limits block unbounded self-improvement and excess tasks',()=>{
  assert.throws(()=>executeShadowEvolutionCycle({snapshot:ready,baselineTasks:[healthy()],
    solver:()=>candidate,independentVerifier:()=>null,cycleId:51}),/UNBOUNDED/);
  assert.throws(()=>executeShadowEvolutionCycle({snapshot:ready,baselineTasks:[healthy()],
    solver:()=>candidate,independentVerifier:()=>null,maxTasks:300}),/UNBOUNDED/);
});

test('negative gradient is only a shadow prior from single-operator verified records',()=>{
  const r=[{classification:'VERIFIED_EXPERIMENTAL',chain:['correct-slice-end']},
    {classification:'VERIFIED_NEGATIVE',chain:['clamp-negative-quota']},
    {classification:'NEUTRAL',chain:['correct-slice-end']},
    {classification:'VERIFIED_NEGATIVE',chain:['correct-slice-end','clamp-negative-quota']}];
  const p=deriveShadowOperatorPriors(r);
  assert.ok(p.priors['correct-slice-end']>p.priors['clamp-negative-quota']);
  assert.equal(p.sampleCounts['correct-slice-end'].failures,0);
  assert.equal(p.persisted,false);assert.equal(p.modelWeightsChanged,false);
});
