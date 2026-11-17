import test from 'node:test';
import assert from 'node:assert/strict';
import {runReuseExperiment,matrixWork} from './reuse-experiment.mjs';

test('3000 recurring tasks are reduced to 30 real matrix computations',()=>{
  const r=runReuseExperiment({requests:3000,distinct:30});
  assert.equal(r.optimizedCalculations,30);
  assert.equal(r.avoidedCalculations,2970);
  assert.equal(r.workReductionRatio,100);
  assert.equal(r.broker.hit,2970);
  assert.equal(r.byteExactResults,true);
});
test('counterexample: 3000 distinct tasks require 3000 real computations',()=>{
  const r=runReuseExperiment({requests:3000,distinct:3000});
  assert.equal(r.optimizedCalculations,3000);
  assert.equal(r.avoidedCalculations,0);
  assert.equal(r.workReductionRatio,1);
  assert.equal(r.byteExactResults,true);
});
test('matrix kernel repeatability',()=>{
  assert.equal(matrixWork(5),matrixWork(5));
  assert.notEqual(matrixWork(5),matrixWork(6));
});
test('invalid workload requests rejected',()=>{
  assert.throws(()=>runReuseExperiment({requests:3000,distinct:3001}),/Invalid workload/);
});
