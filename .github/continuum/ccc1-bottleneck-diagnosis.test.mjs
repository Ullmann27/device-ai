import test from 'node:test';
import assert from 'node:assert/strict';
import {diagnoseCcc1BottleneckCase} from './ccc1-bottleneck-diagnosis.mjs';
import {validateCodingTask} from './coding-task.mjs';
import {CoinWmV3ShadowModel} from './coin-wm-v3-shadow-model.mjs';
import {induceStructuredStatementOperator} from './structured-statement-translator.mjs';

const source='function sortVisibleCatalog(items) { return items.sort((a, b) => a - b); }';
const fixed='function sortVisibleCatalog(items) { return [...items].sort((a, b) => a - b); }';
const sort=induceStructuredStatementOperator('sort',
 'function rankScores(scores) { return scores.sort((a, b) => a - b); }',
 'function rankScores(scores) { return [...scores].sort((a, b) => a - b); }');
const divide=induceStructuredStatementOperator('divide',
 'function averageOrderValue(totalRevenue, orderCount) { return totalRevenue / orderCount; }',
 'function averageOrderValue(totalRevenue, orderCount) { if (orderCount === 0) return 0; return totalRevenue / orderCount; }');
const task=validateCodingTask({
 schema:'continuum-coding-task',version:1,id:'diagnosis-sort-01',
 sourceCommit:'a'.repeat(40),kind:'BUGFIX',chain:'node',
 goal:'Prevent array input mutation in sorted catalog display.',
 acceptance:['The fixed Node test profile must pass after owner verification.'],
 allowedPaths:['src/sort.mjs'],limits:{maxModelCalls:1,maxContextChars:2048}
});
function args(model){return {experimentId:'DIAG-001',caseId:'SORT-001',typedTask:task,
 source,file:'src/sort.mjs',referenceCode:fixed,operators:[sort,divide],
 referenceOperatorIndex:0,model,state16:new Float64Array(16),
 modelCheckpointSha256:'b'.repeat(64),testManifestSha256:'c'.repeat(64)};}
function modelFavoring(index){const model=new CoinWmV3ShadowModel(2);
 model.policyBias[0]=index===0?12:-12;model.policyBias[1]=index===1?12:-12;
 return model;}

test('three arms share source binding but never claim executed or verified fixes',()=>{
 const model=modelFavoring(0),r=diagnoseCcc1BottleneckCase(args(model));
 assert.equal(r.arms.length,3);
 assert.deepEqual(r.arms.map(a=>a.variant),['REFERENCE_PATCH','GIVEN_OPERATOR','NEURAL_TOP1']);
 for(const a of r.arms){assert.equal(a.candidate.code,fixed);assert.equal(a.verifiedSuccess,false);
  assert.equal(a.learningEligible,false);assert.equal(a.baselineStatus,'NOT_RUN');
  assert.equal(a.primaryStatus,'NOT_RUN');assert.equal(a.freshStatus,'NOT_RUN');
  assert.equal(a.independentOracleStatus,'NOT_RUN');}
 assert.equal(r.arms[2].selectedOperatorId,'STRUCT_OP_sort');
 assert.equal(r.arms[2].selectedRank,1);assert.equal(r.arms[2].operatorAttempts,1);
 assert.equal(r.checkpointAttested,false);assert.equal(r.testManifestAttested,false);
 assert.equal(r.status,'RESEARCH_SHADOW');assert.ok(Object.isFrozen(r));
 assert.ok(Object.isFrozen(r.arms));assert.equal(model.updatesCount,0);
});

test('neural pass@1 does not fall back to a later applicable operator',()=>{
 const model=modelFavoring(1),r=diagnoseCcc1BottleneckCase(args(model));
 assert.equal(r.rawRanking[0].operatorId,'STRUCT_OP_divide');
 assert.equal(r.arms[1].candidate.code,fixed);
 assert.equal(r.arms[2].candidate,null);
 assert.equal(r.arms[2].translationStatus,'NOT_APPLICABLE');
 assert.equal(r.arms[2].selectedOperatorId,'STRUCT_OP_divide');
 assert.equal(r.arms[2].selectedRank,1);assert.equal(r.arms[2].operatorAttempts,1);
});

test('forged operator, mismatched paths and unsupported manifests fail closed',()=>{
 const model=modelFavoring(0),v=args(model);
 assert.throws(()=>diagnoseCcc1BottleneckCase({...v,operators:[sort,
  {id:'STRUCT_OP_forged',kind:'STATEMENT_ATOMIC_REWRITE',applyToCode(){throw Error('NEVER');}}]}),
  /OPERATOR_SET_INVALID/);
 assert.throws(()=>diagnoseCcc1BottleneckCase({...v,testManifestSha256:'invalid'}),
  /EXPERIMENT_MANIFEST_INVALID/);
 assert.throws(()=>diagnoseCcc1BottleneckCase({...v,file:'test/malicious.test.mjs'}),
  /UNSUPPORTED_SCOPE/);
 assert.throws(()=>diagnoseCcc1BottleneckCase({...v,referenceCode:source}),
  /REFERENCE_CANDIDATE_INVALID/);
});

test('ranking cannot mutate research model parameters during a diagnosis',()=>{
 const model=modelFavoring(0),original=model.rank.bind(model);
 model.rank=(...params)=>{model.policyBias[0]+=0.1;return original(...params);};
 assert.throws(()=>diagnoseCcc1BottleneckCase(args(model)),/MODEL_MUTATED_DURING_DIAGNOSIS/);
});
