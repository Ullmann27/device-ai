import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { test } from 'node:test';
import { ComputeReuseBroker } from '../continuum-virtual-fabric/reuse-broker.mjs';
import { matrixWork } from '../continuum-virtual-fabric/reuse-experiment.mjs';
import { independentBroker, jobFor, referenceMatrix, runTrial } from './verified-reuse-pilot.mjs';

const sha = value => createHash('sha256').update(value).digest('hex');
const workload = { name: 'test', requests: 60, distinct: 3, size: 12, seedOffset: 7, epochEvery: 0 };

test('independent row-column oracle agrees, including a hand-computed 1x1 golden case', () => {
  assert.equal(referenceMatrix(7, 1), sha('[[7]]'));
  for (const seed of [0, 7, 16, 317, 1703, 2381, 100000]) {
    for (const size of [1, 12, 32]) assert.equal(referenceMatrix(seed, size), matrixWork(seed, size));
  }
});

test('self-consistent payload checksum is not mathematical verification', () => {
  const job = jobFor(7, 12, 0);
  const forged = { seed: 7, size: 12, value: 'incorrect' };
  const integrityOnly = new ComputeReuseBroker({
    verifyReceipt: (_, receipt) => sha(receipt.value) === receipt.outputSha256,
  });
  integrityOnly.submit(job);
  integrityOnly.complete(job.id, { ...forged, outputSha256: sha(forged.value) });
  const { broker } = independentBroker();
  broker.submit(job);
  assert.throws(() => broker.complete(job.id, forged), /not independently verified/);
  assert.equal(broker.snapshot().cachedResults, 0);
  broker.cancel(job.id);
});

test('independent verifier binds result to the exact input', () => {
  const { broker } = independentBroker();
  const job = jobFor(7, 12, 0);
  broker.submit(job);
  assert.throws(() => broker.complete(job.id, { seed: 8, size: 12, value: referenceMatrix(8, 12) }),
    /not independently verified/);
  assert.equal(broker.snapshot().cachedResults, 0);
});

test('same cache scope reuses, while each authority revision and tenant misses', () => {
  const { broker } = independentBroker();
  const job = jobFor(7, 12, 0);
  broker.submit(job);
  broker.complete(job.id, { seed: 7, size: 12, value: matrixWork(7, 12) });
  assert.equal(broker.submit({ ...job, id: 'hit' }).action, 'CACHE_HIT');
  for (const [index, changed] of [
    { memoryEpoch: 1 }, { tenantId: 'different' }, { policyRevision: sha('new-policy') },
    { modelRevision: sha('new-model') }, { parametersDigest: sha('new-parameters') },
  ].entries()) {
    assert.equal(broker.submit({ ...job, ...changed, id: `miss-${index}` }).action, 'EXECUTE');
  }
});

test('repeated tasks are exact with verification cost counted', () => {
  const direct = runTrial(workload, 'direct', 0);
  const reuse = runTrial(workload, 'reuse', 0);
  assert.equal(direct.outputSha256, reuse.outputSha256);
  assert.equal(reuse.kernelCalls, 3);
  assert.equal(reuse.verifierCalls, 3);
  assert.equal(reuse.broker.hit, 57);
  assert.equal(reuse.exactResults, 60);
});

test('unique inputs and no-reuse ablation compute and verify every request', () => {
  for (const result of [runTrial({ ...workload, distinct: 60 }, 'reuse', 0),
    runTrial(workload, 'no-reuse', 0)]) {
    assert.equal(result.kernelCalls, 60);
    assert.equal(result.verifierCalls, 60);
    assert.equal(result.broker.hit, 0);
  }
});

test('revision churn never resurrects stale cache entries', () => {
  const result = runTrial({ ...workload, epochEvery: 10 }, 'reuse', 0);
  assert.equal(result.kernelCalls, 18);
  assert.equal(result.verifierCalls, 18);
  assert.equal(result.broker.hit, 42);
});

test('invalid resource bounds fail explicitly', () => {
  for (const changed of [{ requests: 0 }, { requests: 3001 }, { distinct: 0 }, { distinct: 61 },
    { size: 33 }, { seedOffset: -1 }, { epochEvery: -1 }]) {
    assert.throws(() => runTrial({ ...workload, ...changed }, 'reuse', 0));
  }
  assert.throws(() => runTrial(workload, 'unknown', 0));
});
