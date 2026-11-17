import test from 'node:test';
import assert from 'node:assert/strict';
import {summarizeTransferScorecard} from './coin-wm-shadow-scorecard.mjs';

const SHA40='a'.repeat(40);
const SHA64='b'.repeat(64);
const ARMS=['ZERO_MEMORY','EPISODIC_RETRIEVAL','OPERATOR_ONLY','COIN_WM'];
function record(index,passedNeural=false){
  const run=passed=>({attempts:1,executedTests:3,failedTests:passed?0:1,passed});
  return {
    caseId:'sealed-case-'+index,family:'family-'+(index%5),
    sourceCommit:SHA40,taskSha256:SHA64,evidenceSha256:'c'.repeat(64),
    arms:Object.fromEntries(ARMS.map(n=>[n,run(n==='COIN_WM'&&passedNeural)]))
  };
}

test('five alleged successes cannot automatically promote model',()=>{
  const report=summarizeTransferScorecard(Array.from({length:5},(_,i)=>record(i,true)));
  assert.equal(report.total,5);
  assert.equal(report.solved.COIN_WM,5);
  assert.equal(report.solved.OPERATOR_ONLY,0);
  assert.equal(report.neuralVsOperatorOnlyDelta,5);
  assert.equal(report.promotionEligible,false);
  assert.equal(report.needsIndependentEvidenceVerification,true);
  assert.equal(report.status,'RESEARCH_SHADOW');
});

test('operator-only ablation reveals whether the neural head adds any value',()=>{
  const r=record(10,true);
  r.arms.OPERATOR_ONLY.passed=true;
  r.arms.OPERATOR_ONLY.failedTests=0;
  const report=summarizeTransferScorecard([r]);
  assert.equal(report.solved.COIN_WM,1);
  assert.equal(report.solved.OPERATOR_ONLY,1);
  assert.equal(report.neuralVsOperatorOnlyDelta,0);
  assert.equal(report.promotionEligible,false);
});

test('reject duplicate cases, fabricated evidence identifiers and zero-test outcomes',()=>{
  const r=record(11);
  assert.throws(()=>summarizeTransferScorecard([r,r]),/DUPLICATE_CASE/);
  assert.throws(()=>summarizeTransferScorecard([{...r,evidenceSha256:'invalid'}]),/INVALID_EVIDENCE/);
  const noTests=structuredClone(r);
  noTests.arms.COIN_WM.executedTests=0;
  assert.throws(()=>summarizeTransferScorecard([noTests]),/INVALID_EXECUTION/);
  const contradiction=structuredClone(r);
  contradiction.arms.COIN_WM.failedTests=0;
  assert.throws(()=>summarizeTransferScorecard([contradiction]),/INVALID_EXECUTION/);
  const overBudget=structuredClone(r);
  overBudget.arms.COIN_WM.attempts=3;
  assert.throws(()=>summarizeTransferScorecard([overBudget]),/ATTEMPT_BUDGET/);
});

test('larger scorecards remain explicitly non-causal, without invented significance',()=>{
  const report=summarizeTransferScorecard(Array.from({length:40},(_,i)=>record(i,i%2===0)));
  assert.equal(report.total,40);
  assert.equal(report.familyCount,5);
  assert.equal(report.solved.COIN_WM,20);
  assert.equal(report.promotionEligible,false);
  assert.equal(Object.hasOwn(report,'pValue'),false);
  assert.equal(Object.hasOwn(report,'independentVerified'),false);
});
