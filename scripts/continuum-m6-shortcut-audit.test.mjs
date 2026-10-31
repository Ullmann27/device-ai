import test from 'node:test';
import assert from 'node:assert/strict';
import { auditShortcut, keywordOnlyPrediction } from './continuum-m6-shortcut-audit.mjs';

const labels = ['SUPERSEDES_STATE', 'CORRECTS_EVENT', 'COEXISTS'];
const examples = [
  'Mira wohnt jetzt in Graz.',
  'Korrektur: Die Probe beginnt am Samstag.',
  'Mira hat gestern Wien besucht.'
];
function cases() {
  return Array.from({ length: 240 }, (_, i) => ({
    id: 'M6X-' + String(i + 1).padStart(3, '0'),
    expected: labels[Math.floor(i / 80)],
    after: examples[Math.floor(i / 80)],
    before: 'Do not inspect for shortcut',
    unrelated: 'Do not inspect for shortcut'
  }));
}
test('classifier works without the two evidence sentences or gold labels', () => {
  assert.deepEqual(examples.map(keywordOnlyPrediction), labels);
});
test('fake 240-case lexical shortcut cannot claim semantic generalization', () => {
  const report = auditShortcut(cases());
  assert.equal(report.correct, 240);
  assert.equal(report.generalization_proven, false);
  assert.match(report.warning, /PERFECT_LEXICAL_SHORTCUT/);
});
test('a cue-balanced negative defeats the trivial update rule', () => {
  const rows = cases();
  rows[200].after = 'Mira besucht jetzt die Stadt Wien, wohnt aber weiterhin in Graz.';
  const report = auditShortcut(rows);
  assert.equal(report.correct, 239);
  assert.deepEqual(report.mismatches, ['M6X-201']);
});
test('missing or mislabeled rows are rejected before statistics', () => {
  assert.throws(() => auditShortcut(cases().slice(0, 239)), /FROZEN_CASE_COUNT_INVALID/);
  const rows = cases(); rows[1].expected = 'HALLUCINATED';
  assert.throws(() => auditShortcut(rows), /INVALID_GOLD_CASE/);
});
test('changed IDs and class imbalance are rejected', () => {
  const rows = cases(); rows[1].id = 'M6X-999';
  assert.throws(() => auditShortcut(rows), /INVALID_GOLD_CASE/);
  const other = cases(); other[10].expected = 'COEXISTS';
  assert.throws(() => auditShortcut(other), /FROZEN_LABEL_IMBALANCE/);
});
