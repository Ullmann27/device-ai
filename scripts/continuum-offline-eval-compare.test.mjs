import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {compareOfflineReports} from './continuum-offline-eval-compare.mjs';

const fixture = fs.readFileSync(new URL('../app/src/test/resources/continuum-eval/m3-de.tsv', import.meta.url), 'utf8');
const cases = fixture.split('\n').filter(x => x.trim() && !x.startsWith('#')).map(x => {
  const [id, category, , expected] = x.split('\t');
  return {id, category, expected};
});
function report(changes = {}) {
  return 'id\tcategory\tpass\texpected\tactual\n' + cases.map(c => {
    const row = {...c, pass: '1', actual: c.expected, ...changes[c.id]};
    return [row.id, row.category, row.pass, row.expected, row.actual].join('\t');
  }).join('\n') + '\n# TOTAL 999/999 harmful=0\n';
}
const compare = (baseline = report(), candidate = report(), data = fixture) =>
  compareOfflineReports({fixture: data, baseline, candidate});

test('unchanged complete frozen reports disclose actual counts and limits', () => {
  const result = compare();
  assert.equal(result.status, 'NO_REGRESSION_REPORTED');
  assert.equal(result.total.candidate.correct, cases.length);
  assert.equal(result.evidenceLevel, 'HOST_DETERMINISTIC_REPORTS_ONLY');
  assert.equal(result.blindHoldout, false);
  assert.equal(result.deviceVerified, false);
});
test('improved reported case is visible without pretending to run Gemma', () => {
  const result = compare(report({R01: {pass: '0', actual: 'NONE'}}));
  assert.equal(result.status, 'NO_REGRESSION_REPORTED');
  assert.deepEqual(result.improvements, ['R01']);
  assert.equal(result.total.deltaCorrect, 1);
});
test('aggregate gain cannot conceal a newly wrong case', () => {
  const result = compare(report({R01: {pass: '0'}, R02: {pass: '0'}}), report({R03: {pass: '0'}}));
  assert.equal(result.status, 'REGRESSION');
  assert.deepEqual(result.regressions, ['R03']);
  assert.equal(result.total.deltaCorrect, 1);
});
test('any reported harmful output blocks even unchanged parity', () => {
  const unsafe = report({R01: {pass: '0!', actual: 'REMEMBER'}});
  assert.equal(compare(unsafe, unsafe).status, 'UNSAFE');
});
test('changed expected answer is rejected', () => {
  assert.throws(() => compare(report(), report({R01: {expected: 'FORGED'}})), /metadata/);
});
test('changed category is rejected', () => {
  assert.throws(() => compare(report(), report({R01: {category: 'MEMORY'}})), /metadata/);
});
test('missing and duplicate case coverage is rejected', () => {
  const rows = report().split('\n');
  assert.throws(() => compare(report(), rows.filter(x => !x.startsWith('R01\t')).join('\n')), /coverage/);
  assert.throws(() => compare(report(), report() + rows[1] + '\n'), /duplicate/);
});
test('unknown identifiers and invalid statuses are rejected', () => {
  assert.throws(() => compare(report(), report({R01: {id: 'unknown'}})), /unknown/);
  assert.throws(() => compare(report(), report({R01: {pass: 'PASS'}})), /status/);
});
test('modified frozen input invalidates both reports', () => {
  assert.throws(() => compare(report(), report(), fixture.replace('Welche Apps', 'Was für Apps')), /fixture/);
});
test('empty or malformed reports fail closed', () => {
  assert.throws(() => compare('', report()), /header/);
  assert.throws(() => compare(report(), report().replace('id\tcategory', 'id\tother')), /header/);
  assert.throws(() => compare(report(), report({R01: {actual: 'bad\tfield'}})), /columns/);
});
test('result binds all input bytes and preserves per-category deltas', () => {
  const result = compare();
  assert.match(result.digests.fixture, /^[a-f0-9]{64}$/);
  assert.match(result.digests.baseline, /^[a-f0-9]{64}$/);
  assert.equal(result.digests.baseline, result.digests.candidate);
  assert.equal(Object.values(result.categories).reduce((n, x) => n + x.candidate.total, 0), cases.length);
});
