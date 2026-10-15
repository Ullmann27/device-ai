import test from 'node:test';
import assert from 'node:assert/strict';
import {SubwordEncoder} from './portable-subword.mjs';
import {evaluateViews,trainContrastiveCandidate} from './contrastive-views.mjs';

const train = [
  'sonnenlicht bringt der pflanze energie',
  'motor getriebe bewegen das fahrzeug',
  'klavier gitarre und musik instrumente',
  'regen wolken feuchtigkeit wetter',
  'stern mond galaxie planeten',
  'backofen brot rezept und mehl'
];
const holdout = [
  'fluss wasser fließt durch ein tal',
  'arzt medizin hilft bei gesundheitsfragen',
  'computer tastatur monitor und bildschirm',
  'katze hund tiere auf dem bauernhof'
];

test('contrastive view scores are deterministic, bounded and do not need network', () => {
  const model = new SubwordEncoder(73);
  assert.deepEqual(evaluateViews(model,train),evaluateViews(model,train));
  const s=evaluateViews(model,train);
  assert.ok(s.reciprocalRank>=1/train.length && s.reciprocalRank<=1);
  assert.ok(s.top1>=0 && s.top1<=train.length);
  assert.throws(()=>evaluateViews(model,['same text','SAME TEXT']));
  assert.throws(()=>evaluateViews(model,train,{drop:1}));
});

test('train uses the existing encoder and does not mutate baseline', () => {
  const baseline = new SubwordEncoder(91);
  const saved=baseline.save();
  const result=trainContrastiveCandidate(baseline,train,holdout,{steps:350});
  assert.equal(result.steps,350);
  assert.equal(result.interrupted,false);
  assert.equal(result.candidate.updates,700);
  assert.deepEqual(baseline.save(),saved);
  assert.notDeepEqual(result.candidate.save(),saved);
  assert.ok(result.trainingAfter.reciprocalRank>=1/train.length);
  assert.ok(Number.isFinite(result.validationAfter.reciprocalRank));
  const repeat=trainContrastiveCandidate(baseline,train,holdout,{steps:350});
  assert.deepEqual(repeat.candidate.save(),result.candidate.save());
});

test('cancellation and invalid training settings fail safely', () => {
  const baseline=new SubwordEncoder(11),saved=baseline.save();
  let count=0;
  const result=trainContrastiveCandidate(baseline,train,holdout,
    {steps:100,cancelled:()=>++count>7});
  assert.equal(result.interrupted,true);
  assert.ok(result.steps<100);
  assert.deepEqual(baseline.save(),saved);
  assert.throws(()=>trainContrastiveCandidate(baseline,train,train));
  assert.throws(()=>trainContrastiveCandidate(baseline,train,holdout,{steps:2000}));
});
