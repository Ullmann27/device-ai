import test from 'node:test';
import assert from 'node:assert/strict';
import {strictRepairDepth, finishRepairReport, validateTriplePhaseSample} from './repair-cycle-safety.mjs';

test('repair depth is zero only when unset or precisely zero',()=>{
  assert.equal(strictRepairDepth(undefined),0);
  assert.equal(strictRepairDepth('0'),0);
  assert.equal(strictRepairDepth('1'),1);
  assert.equal(strictRepairDepth('42'),42);
});

test('repair depth denies malformed values that bypassed the positive-depth guard',()=>{
  for(const raw of ['', ' ', ' 0', '0 ', '-1', '-0', '+0', '-42', 'NaN', 'Infinity', '1e2', '0x1', '1.5', '1_0', '01', String(Number.MAX_SAFE_INTEGER+1)]){
    assert.throws(()=>strictRepairDepth(raw),/Invalid CONTINUUM_REPAIR_DEPTH/,raw);
  }
});

test('all bounded repair outcomes include a completion timestamp',()=>{
  for(const outcome of ['NO_FAILURE','NO_CODE_DIAGNOSTIC','VERIFIED','NO_VERIFIED_REPAIR']){
    const report={schemaVersion:1,base:'a'.repeat(40),startedAt:'2026-10-17T16:00:00.000Z',outcome:null};
    assert.strictEqual(finishRepairReport(report,outcome,'2026-10-17T16:00:01.000Z'),report);
    assert.equal(report.outcome,outcome);
    assert.equal(report.finishedAt,'2026-10-17T16:00:01.000Z');
  }
});


const MUTATION_FILE='app/src/main/java/at/heinz/deviceai/learning/MutationProbe.kt';
const sha='f'.repeat(64);
function profile({name='android-unit',passed=true,executed=7,failed=0,skipped=0,testProfile=true}={}){
 return {profile:name,passed,testProfile,exitCode:passed?0:1,
  tests:{executed,failed,skipped}};
}
function observation(profiles,diagnostics=[]){
 return {passed:profiles.every(p=>p.passed),results:profiles,diagnostics};
}
function fixture(){
 const baselineRun=observation([profile()]);
 const mutatedRun=observation([
  profile({passed:false,executed:7,failed:1})],[
  {kind:'UNRESOLVED_REFERENCE',symbol:'Files',file:MUTATION_FILE,
   message:"Unresolved reference 'Files'",line:8}
 ]);
 const candidateRuns=[
  {strategy:'KOTLIN_MISSING_IMPORT',candidateId:'a'.repeat(16),source:'local',
   primaryRun:observation([profile()]),independentRun:observation([profile()]),
   patchSha256:sha,independentPatchSha256:sha},
  {strategy:'KOTLIN_TYPO_REFERENCE',candidateId:'b'.repeat(16),source:'groq',
   primaryRun:observation([profile({passed:false,executed:7,failed:1})])}
 ];
 return {baselineRun,mutatedRun,candidateRuns,mutationFile:MUTATION_FILE};
}
test('triple-phase validator accepts structurally consistent green-red-green and a failed competing candidate',()=>{
 const sample=validateTriplePhaseSample(fixture());
 assert.equal(sample.valid,true);
 assert.equal(sample.authority,'SHADOW_ONLY',
  'Plain JSON evidence cannot authorize training or trusted ledger updates');
 assert.equal(sample.trainingTuple.winningStrategy,'KOTLIN_MISSING_IMPORT');
 assert.deepEqual(sample.trainingTuple.refutedStrategies,['KOTLIN_TYPO_REFERENCE']);
 assert.match(sample.trainingTuple.diagnosticQuery,/UNRESOLVED_REFERENCE Files/);
});

test('no baseline green or zero executed tests means no training tuple',()=>{
 for(const run of [observation([profile({passed:false,failed:1})]),
   observation([profile({executed:0})]),
   observation([profile({skipped:1})])]){
  const sample=fixture();sample.baselineRun=run;
  assert.deepEqual(validateTriplePhaseSample(sample),{
   valid:false,reason:'BASELINE_NOT_GREEN',trainingTuple:null,authority:'SHADOW_ONLY'});
 }
});

test('undetected mutation and mismatched diagnostic file are discarded',()=>{
 const clean=fixture();
 clean.mutatedRun=clean.baselineRun;
 assert.equal(validateTriplePhaseSample(clean).reason,'MUTATION_NOT_DETECTED_BY_TESTS');
 const missing=fixture();missing.mutatedRun.diagnostics=[];
 assert.equal(validateTriplePhaseSample(missing).reason,'NO_ACTIONABLE_DIAGNOSTIC_EXTRACTED');
 const wrong=fixture();wrong.mutatedRun.diagnostics[0].file='app/src/main/java/Other.kt';
 assert.equal(validateTriplePhaseSample(wrong).reason,'NO_ACTIONABLE_DIAGNOSTIC_EXTRACTED');
});

test('unsafe or non-Kotlin mutation targets cannot enter research data',()=>{
 const sample=fixture();sample.mutationFile='../passwd';
 assert.equal(validateTriplePhaseSample(sample).reason,'INVALID_MUTATION_FILE');
});

test('successful repair requires a separate matching second-run observation',()=>{
 const absent=fixture();delete absent.candidateRuns[0].independentRun;
 assert.equal(validateTriplePhaseSample(absent).reason,'INDEPENDENT_EVIDENCE_MISSING_OR_FAILED');
 const red=fixture();
 red.candidateRuns[0].independentRun=observation([profile({passed:false,failed:1})]);
 assert.equal(validateTriplePhaseSample(red).reason,'INDEPENDENT_EVIDENCE_MISSING_OR_FAILED');
 const mismatched=fixture();mismatched.candidateRuns[0].independentPatchSha256='c'.repeat(64);
 assert.equal(validateTriplePhaseSample(mismatched).reason,'INDEPENDENT_PATCH_MISMATCH');
});

test('fewer executed tests or new skipped tests in repaired candidate are rejected',()=>{
 for(const faulty of [
  profile({executed:6}),
  profile({skipped:1})
 ]){
  const sample=fixture();
  sample.candidateRuns[0].primaryRun=observation([faulty]);
  assert.equal(validateTriplePhaseSample(sample).reason,'PRIMARY_EVIDENCE_REGRESSION');
 }
 const second=fixture();
 second.candidateRuns[0].independentRun=observation([profile({executed:6})]);
 assert.equal(validateTriplePhaseSample(second).reason,'INDEPENDENT_EVIDENCE_MISSING_OR_FAILED');
});

test('a critic rejection without executed checks is NOT a refuted strategy',()=>{
 const sample=fixture();sample.candidateRuns[1].primaryRun=observation([
  profile({passed:false,executed:0,failed:0})]);
 assert.equal(validateTriplePhaseSample(sample).reason,'AMBIGUOUS_NEGATIVE');
});

test('compiler failures are acceptable as provisional negatives only with a localized diagnostic',()=>{
 const sample=fixture();
 sample.baselineRun=observation([
  profile({name:'android-compile',executed:0,testProfile:false}),
  profile()]);
 sample.mutatedRun=observation([
  profile({name:'android-compile',executed:0,passed:false,testProfile:false})],
  [{file:MUTATION_FILE,kind:'UNRESOLVED_REFERENCE',message:'Missing Files'}]);
 sample.candidateRuns[0].primaryRun=observation([
  profile({name:'android-compile',executed:0,testProfile:false}),profile()]);
 sample.candidateRuns[0].independentRun=observation([
  profile({name:'android-compile',executed:0,testProfile:false}),profile()]);
 sample.candidateRuns[1].primaryRun=observation([
  profile({name:'android-compile',executed:0,passed:false,testProfile:false})],
  [{file:MUTATION_FILE,kind:'UNRESOLVED_REFERENCE',message:'Still missing'}]);
 assert.equal(validateTriplePhaseSample(sample).valid,true);
 sample.candidateRuns[1].primaryRun.diagnostics=[];
 assert.equal(validateTriplePhaseSample(sample).reason,'AMBIGUOUS_NEGATIVE');
});

test('the selected winner must be unique, paired with a real negative',()=>{
 const winnerOnly=fixture();winnerOnly.candidateRuns=[winnerOnly.candidateRuns[0]];
 assert.equal(validateTriplePhaseSample(winnerOnly).reason,'MISSING_VERIFIED_WINNER_OR_REFUTED_NEGATIVE');
 const secondWinner=fixture();
 secondWinner.candidateRuns[1]={...secondWinner.candidateRuns[0],
  strategy:'JS_MISSING_IMPORT',candidateId:'b'.repeat(16)};
 assert.equal(validateTriplePhaseSample(secondWinner).reason,'MISSING_VERIFIED_WINNER_OR_REFUTED_NEGATIVE');
});

test('duplicate strategies, invalid candidate IDs or fabricated profile statuses are rejected',()=>{
 const repeated=fixture();
 repeated.candidateRuns[1].strategy=repeated.candidateRuns[0].strategy;
 assert.equal(validateTriplePhaseSample(repeated).reason,'INVALID_CANDIDATE_IDENTITY');
 const badId=fixture();badId.candidateRuns[0].candidateId='not-hex';
 assert.equal(validateTriplePhaseSample(badId).reason,'INVALID_CANDIDATE_IDENTITY');
 const falseGreen=fixture();
 falseGreen.candidateRuns[0].primaryRun.results[0].passed=true;
 falseGreen.candidateRuns[0].primaryRun.results[0].exitCode=1;
 assert.equal(validateTriplePhaseSample(falseGreen).reason,'INVALID_PRIMARY_EVIDENCE');
});

test('mismatched check chains and failing independently executed tests never pass',()=>{
 const sample=fixture();
 sample.candidateRuns[0].primaryRun=observation([profile({name:'node-continuum'})]);
 assert.equal(validateTriplePhaseSample(sample).reason,'INVALID_PRIMARY_EVIDENCE');
 const independence=fixture();
 independence.candidateRuns[0].independentRun=observation([profile({passed:false,failed:1})]);
 assert.equal(validateTriplePhaseSample(independence).reason,'INDEPENDENT_EVIDENCE_MISSING_OR_FAILED');
});

test('prototype does not mutate its input reports or train a model',()=>{
 const sample=fixture(),before=JSON.stringify(sample);
 const result=validateTriplePhaseSample(sample);
 assert.equal(JSON.stringify(sample),before);
 assert.equal(result.authority,'SHADOW_ONLY');
 assert.ok(!Object.prototype.hasOwnProperty.call(result,'independentPass'));
 assert.ok(!Object.prototype.hasOwnProperty.call(result,'promoted'));
});
