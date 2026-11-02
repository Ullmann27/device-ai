import test from 'node:test';
import assert from 'node:assert/strict';
import {CoinWmV3ShadowModel} from './coin-wm-v3-shadow-model.mjs';
import {induceStructuredStatementOperator} from './structured-statement-translator.mjs';
import {validateCodingTask} from './coding-task.mjs';
import {diagnoseCcc1BottleneckCase} from './ccc1-bottleneck-diagnosis.mjs';
import {buildCcc1BottleneckScorecard} from './ccc1-bottleneck-scorecard.mjs';

const before='function rankScores(scores) { return scores.sort((a, b) => a - b); }';
const after='function rankScores(scores) { return [...scores].sort((a, b) => a - b); }';
const source='function sortVisible(items) { return items.sort((a, b) => a - b); }';
const reference='function sortVisible(items) { return [...items].sort((a, b) => a - b); }';
const sort=induceStructuredStatementOperator('sort',before,after);
const division=induceStructuredStatementOperator('divide',
 'function mean(total, count) { return total / count; }',
 'function mean(total, count) { if (count === 0) return 0; return total / count; }');
const typedTask=validateCodingTask({
 schema:'continuum-coding-task',version:1,id:'diagnostic-source-01',
 sourceCommit:'a'.repeat(40),kind:'BUGFIX',chain:'node',
 goal:'Sort without input mutation.',
 acceptance:['Existing owner tests must pass after independent execution.'],
 allowedPaths:['src/sort.mjs'],limits:{maxModelCalls:1,maxContextChars:2048}
});
function makeObservation(selectedIndex=0) {
 const model=new CoinWmV3ShadowModel(2);
 model.policyBias[0]=selectedIndex===0?12:-12;
 model.policyBias[1]=selectedIndex===1?12:-12;
 return diagnoseCcc1BottleneckCase({
  experimentId:'EXPERIMENT_01',caseId:'S01',typedTask,
  source,file:'src/sort.mjs',referenceCode:reference,
  operators:[sort,division],referenceOperatorIndex:0,model,
  state16:new Float64Array(16),modelCheckpointSha256:'b'.repeat(64),
  testManifestSha256:'c'.repeat(64)
 });
}
test('same unverified patch is not misclassified as a verified repair',()=>{
 const report=buildCcc1BottleneckScorecard(makeObservation(0));
 assert.equal(report.status,'ANALYSIS_ONLY');
 assert.equal(report.observationFinding,'BOTH_ARMS_SAME_UNVERIFIED_PATCH');
 assert.equal(report.top1OperatorId,'STRUCT_OP_sort');
 assert.equal(report.observedNeuralRankCalls,1);
 assert.equal(report.observedOperatorTranslationAttempts,2);
 assert.equal(report.observedExternalModelCalls,0);
 assert.equal(report.observedOsExecutionAttempts,0);
 assert.equal(report.independentVerification,'NOT_RUN');
 assert.equal(report.verifiedSuccess,false);
 assert.equal(report.learningEligible,false);
 assert.equal(report.optionalLargeSpecialist,'NOT_REQUESTED');
 assert.equal(report.checkpointAttested,false);
 assert.equal(report.testManifestAttested,false);
 assert.ok(Object.isFrozen(report));
 assert.ok(Object.isFrozen(report.arms));
});

test('wrong neural rank one reports local ranking review, not hidden rank two fallback',()=>{
 const report=buildCcc1BottleneckScorecard(makeObservation(1));
 assert.equal(report.observationFinding,'TOP1_NOT_TRANSLATABLE_WHILE_FORCED_TRANSLATES');
 assert.equal(report.nextLocalGate,'VERIFY_REFERENCE_THEN_REVIEW_NEURAL_RANKING');
 assert.equal(report.arms[2].selectedOperatorId,'STRUCT_OP_divide');
 assert.equal(report.arms[2].hasBoundedCandidate,false);
 assert.equal(report.arms[2].candidateSha256,null);
 assert.equal(report.observedOperatorTranslationAttempts,2);
 assert.equal(report.generalizationProven,false);
});

test('scorecard is redacted and cannot contain raw candidate source',()=>{
 const report=buildCcc1BottleneckScorecard(makeObservation(0));
 const json=JSON.stringify(report);
 assert.equal(json.includes('function sortVisible'),false);
 assert.equal(json.includes('return [...items]'),false);
 assert.equal(json.includes('testCommand'),false);
 assert.equal(json.includes('private'),false);
 assert.match(report.sourceCommit,/^[a-f0-9]{40}$/);
 assert.match(report.arms[0].candidateSha256,/^[a-f0-9]{64}$/);
});

test('untrusted serialized or fabricated observation does not gain a trusted origin',()=>{
 const real=makeObservation();
 const forged=JSON.parse(JSON.stringify(real));
 assert.throws(()=>buildCcc1BottleneckScorecard(forged),/AUTHENTIC_STAGE1_OBSERVATION_REQUIRED/);
 assert.throws(()=>buildCcc1BottleneckScorecard({...real}),/AUTHENTIC_STAGE1_OBSERVATION_REQUIRED/);
 assert.throws(()=>buildCcc1BottleneckScorecard(null),/AUTHENTIC_STAGE1_OBSERVATION_REQUIRED/);
});

test('the scorecard does not execute or train while summarizing',()=>{
 const o=makeObservation(0);
 const scorecard=buildCcc1BottleneckScorecard(o);
 assert.equal(scorecard.arms.length,3);
 for(const arm of scorecard.arms){
  assert.equal(typeof arm.candidate,'undefined');
  assert.equal(typeof arm.execute,'undefined');
 }
 assert.equal(scorecard.learningEligible,false);
 assert.equal(scorecard.promotionEligible,false);
});
