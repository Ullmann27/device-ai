import test from 'node:test';
import assert from 'node:assert/strict';
import {auditGeneralization,SEEDS,DROPS,HOLDOUTS,TRAIN} from './contrastive-generalization.mjs';
test('multi-seed audit is reproducible, finite and separates its lexical corpora',()=>{
  const report=auditGeneralization();
  assert.deepEqual(report,auditGeneralization());
  assert.equal(report.cases.length,SEEDS.length*DROPS.length*Object.keys(HOLDOUTS).length);
  assert.equal(report.summary.caseCount,report.cases.length);
  assert.equal(report.summary.improved+report.summary.unchanged+report.summary.regressed,
    report.cases.length);
  assert.equal(report.recommendation,'NOT_APPROVED_FOR_AUTOMATIC_PROMOTION');
  const texts=[...TRAIN,...Object.values(HOLDOUTS).flat()];
  assert.equal(new Set(texts).size,texts.length);
  for(const c of report.cases){
    assert.ok(c.beforeMRR>0&&c.beforeMRR<=1&&c.afterMRR>0&&c.afterMRR<=1);
    assert.ok(Number.isFinite(c.deltaMRR));
    assert.ok(c.beforeTop1>=0&&c.beforeTop1<=c.total);
    assert.ok(c.afterTop1>=0&&c.afterTop1<=c.total);
  }
});
