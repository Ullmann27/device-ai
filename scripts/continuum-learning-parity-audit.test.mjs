import test from 'node:test';
import assert from 'node:assert/strict';
import {compareContinuumLearningStates} from './continuum-learning-parity-audit.mjs';
const prior = {appId:'at.heinz.deviceai.mentorpaired',swb1Updates:331570,archiveSources:538,
  learningEventsToday:71,publicStudy:{enabled:true,registered:true}};
const newApp = {appId:'at.heinz.deviceai.continuumlocal',swb1Updates:4000,archiveSources:532,
  learningEventsToday:3,publicStudy:{enabled:false,registered:null}};
test('observed number comparison is deterministic without claiming intelligence',()=>{
  const result = compareContinuumLearningStates(prior,newApp);
  assert.equal(result.swb1UpdateDifference,-327570);
  assert.equal(result.archiveSourceDifference,-6);
  assert.equal(result.todayEventDifference,-68);
  assert.equal(result.holdoutComparable,false);
  assert.equal(result.verifiedFullMigration,false);
  assert.equal(result.verifiedGemmaInference,false);
  assert.equal(result.researchEnabledNew,false);
});
test('unknown values remain unknown, not zero',()=>{
  const r=compareContinuumLearningStates({appId:prior.appId},{appId:newApp.appId});
  assert.equal(r.swb1UpdateDifference,null);
  assert.equal(r.archiveSourceDifference,null);
  assert.equal(r.swb1WeightHashEqual,null);
  assert.equal(r.researchScheduledNew,null);
});
test('hash equality alone is insufficient to claim complete migration',()=>{
  const sha='a'.repeat(64);
  const r=compareContinuumLearningStates({...prior,swb1Sha256:sha},{...newApp,swb1Sha256:sha});
  assert.equal(r.swb1WeightHashEqual,true);
  assert.equal(r.verifiedModelWeightMigration,false);
  assert.equal(r.verifiedFullMigration,false);
});
test('invalid or confused app identities are rejected',()=>{
  assert.throws(()=>compareContinuumLearningStates(newApp,prior),/APP_ID_MISMATCH/);
  assert.throws(()=>compareContinuumLearningStates({...prior,swb1Updates:-1},newApp),/INVALID_/);
  assert.throws(()=>compareContinuumLearningStates({...prior,swb1Updates:1.5},newApp),/INVALID_/);
  assert.throws(()=>compareContinuumLearningStates({...prior,swb1Sha256:'fake'},newApp),/INVALID_/);
});
test('holdout quality only comparable on explicitly matching frozen benchmark ID and test size',()=>{
  const m={benchmarkId:'frozen-2026-v1',intentHoldout:{correct:7,total:10},semanticHoldout:{correct:5,total:10}};
  const yes=compareContinuumLearningStates({...prior,...m},{...newApp,...m,semanticHoldout:{correct:6,total:10}});
  assert.equal(yes.holdoutComparable,true);
  assert.equal(yes.holdoutCorrectDifference,1);
  const no=compareContinuumLearningStates({...prior,...m},{...newApp,...m,benchmarkId:'frozen-2026-v2'});
  assert.equal(no.holdoutComparable,false);
  assert.equal(no.holdoutCorrectDifference,null);
  const invalid=()=>compareContinuumLearningStates({...prior,...m,intentHoldout:{correct:11,total:10}},newApp);
  assert.throws(invalid,/INVALID_INTENT_HOLDOUT/);
});
