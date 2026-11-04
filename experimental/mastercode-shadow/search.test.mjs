import test from 'node:test';
import assert from 'node:assert/strict';
import {fixtures,sealedFixtures} from './fixtures.mjs';
import {SEARCH_OPERATOR_IDS,validateTask,applyOperator,runTypedModel,oracle,inspect,
  enumeratePrograms,applyProgram,solve,proposeMacro} from './search.mjs';
const [trainSlice,trainClamp,dev,holdout]=fixtures;
const priors={'correct-slice-end':0.5,'clamp-negative-quota':0.5};

test('training exposes two disjoint one-step capabilities',()=>{
  assert.deepEqual(SEARCH_OPERATOR_IDS,['correct-slice-end','clamp-negative-quota']);
  for(const t of [trainSlice,trainClamp]){
    assert.equal(inspect(t,t.policy).passed,false);
    assert.equal(solve(t,{maxDepth:1}).solved,true);
  }
});
test('demonstration double fault cannot be solved with only one atomic operator',()=>{
  const x=solve(holdout,{maxDepth:1,maxEvaluations:4});
  assert.equal(x.solved,false);
  assert.equal(x.evaluated,2);
});
test('bounded two-step composition repairs the demonstration double fault',()=>{
  const x=solve(holdout,{maxDepth:2,maxEvaluations:4});
  assert.equal(x.solved,true);assert.equal(x.chain.length,2);
  assert.equal(x.shadowOnly,true);assert.equal(x.promoted,false);
});
test('finite budget cannot silently turn an unsolved task into success',()=>{
  const x=solve(holdout,{maxDepth:2,maxEvaluations:1});
  assert.equal(x.solved,false);assert.equal(x.evaluated,1);
  assert.throws(()=>solve(holdout,{maxEvaluations:Infinity}),/BUDGET/);
});
test('model is a typed interpreter, not JavaScript or user-supplied shell',()=>{
  assert.throws(()=>runTypedModel({sliceEndAdjustment:0,clampNonnegative:true},
    {samples:['process.exit(0)'],start:0,span:1,reserved:0,ceiling:10}),/INVALID_INPUT/);
  assert.equal(runTypedModel({sliceEndAdjustment:0,clampNonnegative:true},
    {samples:[1,2],start:0,span:2,reserved:0,ceiling:3}),2);
});
test('blind oracle cases reject a candidate that only passes visible examples',()=>{
  const shallow={...holdout,visible:[{input:{samples:[1],start:9,span:1,reserved:1,ceiling:5},expected:0}]};
  assert.equal(inspect(shallow,shallow.policy,'visible').passed,false);
  assert.equal(inspect(shallow,applyOperator(shallow.policy,'clamp-negative-quota'),'visible').passed,true);
  assert.equal(inspect(shallow,applyOperator(shallow.policy,'clamp-negative-quota'),'blind').passed,false);
  assert.equal(solve(shallow,{maxDepth:1}).solved,false);
});
test('tampered expected output is rejected by an independent fixed specification',()=>{
  const bad={...holdout,blind:[{...holdout.blind[0],expected:999},...holdout.blind.slice(1)]};
  assert.throws(()=>inspect(bad,{sliceEndAdjustment:0,clampNonnegative:true},'blind'),/MALFORMED_TEST_ORACLE/);
});
test('no auto macro promotion even after an observed composition',()=>{
  const result=solve(dev,{maxDepth:2});
  assert.equal(result.solved,true);
  assert.equal(proposeMacro(dev,result).writeAllowed,false);
  assert.equal(proposeMacro(dev,result,{independentDevConfirmations:2}).status,'SHADOW_REVIEW_CANDIDATE');
  assert.equal(proposeMacro(dev,result,{independentDevConfirmations:2}).productionActivationAllowed,false);
});
test('invalid priors and oversized configurations fail closed',()=>{
  assert.throws(()=>enumeratePrograms({priors:{'correct-slice-end':1,'clamp-negative-quota':0}}),/INVALID_PRIORS/);
  assert.throws(()=>enumeratePrograms({priors,maxDepth:9}),/CONFIG/);
  assert.throws(()=>enumeratePrograms({priors,maxGas:0}),/CONFIG/);
  assert.throws(()=>applyProgram(holdout,['bad-command']),/INVALID_PROGRAM/);
});
test('operator score is deterministic and references finite known operators only',()=>{
  const x=enumeratePrograms({priors}),y=enumeratePrograms({priors});
  assert.deepEqual(x,y);assert.equal(x.length,4);
  assert.ok(x.every(r=>Number.isFinite(r.priorityCost)&&r.gas<=8));
});
test('source fixture seal is deterministic and malformed contracts fail',()=>{
  assert.match(sealedFixtures(),/^[0-9a-f]{64}$/);
  assert.equal(sealedFixtures(),sealedFixtures());
  assert.throws(()=>validateTask({...holdout,id:'../../escape'}),/INVALID_TYPED_TASK/);
  assert.throws(()=>validateTask({...holdout,visible:[]}),/INVALID_TYPED_TASK/);
});
test('toy compositions are not certified real-world or code safety',()=>{
  const r=solve(holdout,{maxDepth:2});
  const policy=applyProgram(holdout,r.chain);
  for(const tc of holdout.blind) assert.equal(runTypedModel(policy,tc.input),oracle(tc.input));
  assert.equal(r.promoted,false);
});
