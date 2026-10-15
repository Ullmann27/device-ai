import test from 'node:test';
import assert from 'node:assert/strict';
import {auditRetention,INTENTS,SEMANTICS} from './contrastive-retention.mjs';
test('pretrained intent and semantic knowledge is measured across independent contrastive adaptation',()=>{
  const report=auditRetention();
  assert.deepEqual(report,auditRetention());
  assert.equal(report.summary.seeds,5);
  assert.equal(report.summary.evaluations,10);
  assert.equal(report.cases.length,5);
  assert.equal(report.recommendation,'REQUIRE_RETENTION_GATE_BEFORE_PROMOTION');
  assert.equal(INTENTS.length,6);
  assert.equal(SEMANTICS.length,6);
  for(const c of report.cases)for(const t of [c.intent,c.semantic]){
    assert.equal(t.total,6);
    assert.ok(Number.isFinite(t.beforeLoss)&&Number.isFinite(t.afterLoss));
    assert.ok(Number.isFinite(t.deltaLoss));
    assert.ok(t.beforeCorrect>=0&&t.beforeCorrect<=t.total);
    assert.ok(t.afterCorrect>=0&&t.afterCorrect<=t.total);
  }
});
