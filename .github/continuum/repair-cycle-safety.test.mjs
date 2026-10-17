import test from 'node:test';
import assert from 'node:assert/strict';
import {strictRepairDepth, finishRepairReport} from './repair-cycle-safety.mjs';

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
