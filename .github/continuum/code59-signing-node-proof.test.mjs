import test from 'node:test';
import assert from 'node:assert/strict';
import {selectChecks} from '../../tools/code55-apk/code59-node-proof.mjs';

const report = () => ({checks: [{id: 'full-node', status: 'PASS'},
  ...['gradle', 'lint', 'apk', 'fold7', 'model-inference'].map(id =>
    ({id, severity: 'P0', status: 'NOT_RUN', reason: 'OUTSIDE_SELECTED_HOST_SCOPE'}))]});

test('actual Node report keeps unrelated device gates unclaimed', () => {
  assert.deepEqual(selectChecks(report(), ['full-node']), [{id: 'full-node', status: 'PASS'}]);
});
test('missing executed profile cannot become a passing proof', () => {
  const value = report(); value.checks.shift();
  assert.throws(() => selectChecks(value, ['full-node']), /proof rejected/);
});
test('duplicate profile cannot replace a required scope declaration', () => {
  const value = report(); value.checks[1] = value.checks[0];
  assert.throws(() => selectChecks(value, ['full-node']), /proof rejected/);
});
test('invented device success cannot be attributed to Node', () => {
  const value = report(); value.checks.find(c => c.id === 'fold7').status = 'PASS';
  assert.throws(() => selectChecks(value, ['full-node']), /proof rejected/);
});
test('unknown or failed scope is not silently omitted', () => {
  for (const change of [c => { c.id = 'unknown'; }, c => { c.status = 'FAIL'; },
    c => { c.reason = 'CANCELLED'; }]) {
    const value = report(); change(value.checks[1]);
    assert.throws(() => selectChecks(value, ['full-node']), /proof rejected/);
  }
});
test('extra failed check remains a rejection', () => {
  const value = report(); value.checks.push({id: 'unexpected', status: 'FAIL'});
  assert.throws(() => selectChecks(value, ['full-node']), /proof rejected/);
});
