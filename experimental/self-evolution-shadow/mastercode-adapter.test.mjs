import test from 'node:test';
import assert from 'node:assert/strict';
import { emptyLedger } from '../../.github/continuum/repair-ledger.mjs';
import { runMastercodeSelfPlayOnce, reviewVerifiedCcc1Ledger } from './mastercode-adapter.mjs';

const ready = Object.freeze({
  ownerOptIn: true, idle: true, charging: true, connectedToTrustedHost: true,
  activeWorker: false, batteryPercent: 75, thermalHeadroom: 0.8, availableTimeMs: 30_000
});

test('MASTER-EXP adapter invokes real bounded composition and independent blind check', () => {
  const result = runMastercodeSelfPlayOnce(ready);
  assert.equal(result.status, 'FINISHED_SHADOW');
  assert.equal(result.taskCount, 3);
  assert.equal(result.verified, 3);
  assert.equal(result.negative, 0);
  assert.equal(result.neutral, 0);
  assert.equal(result.mainWriteAllowed, false);
  assert.equal(result.ntwTrainingAllowed, false);
  assert.equal(result.pruningAllowed, false);
});
test('gating prevents even an experiment when the existing worker is busy', () => {
  const result = runMastercodeSelfPlayOnce({...ready, activeWorker: true});
  assert.equal(result.status, 'SKIPPED');
  assert.equal(result.records.length, 0);
});
test('adapter rejects unbounded cycles and cannot open a perpetual worker', () => {
  assert.throws(()=>runMastercodeSelfPlayOnce(ready, {cycleId:51}), /UNBOUNDED/);
  assert.throws(()=>runMastercodeSelfPlayOnce(ready, {maxEvaluations:100}), /UNBOUNDED/);
});
test('CCC1 ledger is verified with existing authoritative validator and summarized read-only', () => {
  const ledger=emptyLedger();
  const original=JSON.stringify(ledger);
  const result=reviewVerifiedCcc1Ledger(ledger);
  assert.deepEqual(result.observations,[]);
  assert.equal(result.readOnly,true);
  assert.equal(result.trainingAuthorized,false);
  assert.equal(JSON.stringify(ledger),original);
  assert.throws(()=>reviewVerifiedCcc1Ledger({...ledger,schemaVersion:999}),/Invalid repair ledger/);
});
