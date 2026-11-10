import test from 'node:test';
import assert from 'node:assert/strict';
import { AdmissionProbe, ARMS, WORKLOADS, execute, inputAt } from './compute.mjs';
import { EventSalienceProbe, EVENT_STREAMS, eventTrial } from './salience.mjs';
import { jobFor, referenceMatrix } from '../../tools/continuum-cognitive-pilots/verified-reuse-pilot.mjs';

for (const workload of WORKLOADS) test(`identical results: ${workload.name}`, () => {
  const count = 80;
  const results = ARMS.map(arm => execute(workload, arm, count));
  results.forEach(result => result.outputs.forEach((output, index) => {
    const { seed, size } = inputAt(workload, index);
    assert.equal(output, `${index}:${referenceMatrix(seed, size)}`);
  }));
  assert.deepEqual(results[0].outputs, results[2].outputs);
});

test('cheap repeats and unique/churn controls bypass reuse', () => {
  const cheap = { ...WORKLOADS[0], size: 1 };
  for (const workload of [cheap, WORKLOADS[2], WORKLOADS[3]]) {
    const result = execute(workload, 'adaptive', 80);
    assert.equal(result.kernelCalls, 80);
    assert.equal(result.admissions, 0);
    assert.equal(result.verifierCalls, 0);
  }
});

test('warmup state is not reused as measured state', () => {
  const result = execute(WORKLOADS[0], 'adaptive', 80);
  assert.equal(result.bypasses, 8);
  assert.equal(result.kernelCalls, 16);
  assert.equal(result.verifierCalls, 8);
  assert.equal(result.hits, 64);
});

test('scope/revision/epoch changes invalidate admission', () => {
  for (const field of ['tenantId', 'modelRevision', 'inputDigest', 'parametersDigest', 'policyRevision', 'memoryEpoch']) {
    const probe = new AdmissionProbe();
    const request = jobFor(2, 24, 0);
    assert.equal(probe.consider(request, 24), false);
    assert.equal(probe.consider({ ...request, id: 'different-request' }, 24), true);
    const changed = { ...request, [field]: field === 'memoryEpoch' ? 1 : 'changed' };
    assert.equal(probe.consider(changed, 24), false);
  }
});

test('admission identity retention stays bounded', () => {
  const probe = new AdmissionProbe();
  for (let i = 0; i < 1000; i++) probe.consider(jobFor(i, 24, i), 24);
  assert.equal(probe.retainedIdentities, 256);
  assert.equal(probe.consider(jobFor(0, 24, 1000), 24), false);
});

test('prediction residual and surprisal are distinct', () => {
  const result = new EventSalienceProbe().observe({ tick: 0, predicted: 0.95, observed: 0 });
  assert.equal(result.residual, 0.95);
  assert(Math.abs(result.surpriseNats + Math.log(0.05)) < 1e-12);
  assert.equal(result.wake, true);
  assert.equal(result.authority, 'ADVISORY_ONLY');
});

test('corrections survive a zero residual', () => {
  const result = new EventSalienceProbe().observe({ tick: 0, predicted: 1, observed: 1, correction: true });
  assert.equal(result.residual, 0);
  assert.equal(result.wake, true);
});

test('invalid events cannot mutate accumulator', () => {
  const probe = new EventSalienceProbe();
  probe.observe({ tick: 2, predicted: 0.9, observed: 1 });
  for (const event of [
    { tick: 1, predicted: 0.9, observed: 1 },
    { tick: 3, predicted: NaN, observed: 1 },
    { tick: 3, predicted: 1.1, observed: 1 },
    { tick: 3, predicted: 0.5, observed: 0.5 },
    { tick: 3, predicted: 0.5, observed: 1, correction: 'yes' },
  ]) assert.throws(() => probe.observe(event));
  const reference = new EventSalienceProbe();
  reference.observe({ tick: 2, predicted: 0.9, observed: 1 });
  assert.deepEqual(probe.observe({ tick: 3, predicted: 0.9, observed: 1 }),
    reference.observe({ tick: 3, predicted: 0.9, observed: 1 }));
});

test('decay and reset prevent repeated anomaly wake', () => {
  const probe = new EventSalienceProbe();
  assert.equal(probe.observe({ tick: 0, predicted: 1, observed: 0 }).wake, true);
  assert.equal(probe.observe({ tick: 1, predicted: 1, observed: 1 }).wake, false);
  assert.equal(probe.observe({ tick: 100, predicted: 0.5, observed: 1 }).wake, false);
});

test('negative holdout preserves accumulator false wakes', () => {
  const stream = EVENT_STREAMS.find(x => x.name === 'alternating-low-residual');
  assert(eventTrial(stream, 'accumulator').falseWakes > 0);
  assert.equal(eventTrial(stream, 'threshold').falseWakes, 0);
});

test('published event labels never reach the probe', () => {
  for (const stream of EVENT_STREAMS) {
    const flipped = { ...stream, events: stream.events.map(x => ({ ...x, expectedWake: !x.expectedWake })) };
    for (const arm of ['always', 'threshold', 'accumulator']) {
      assert.deepEqual(eventTrial(stream, arm).rows.map(x => x.wake), eventTrial(flipped, arm).rows.map(x => x.wake));
    }
  }
});
