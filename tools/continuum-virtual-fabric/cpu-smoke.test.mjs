import test from 'node:test';
import assert from 'node:assert/strict';
import { runHundredCpuTasks, matrixChecksum } from './cpu-smoke.mjs';

test('100 deterministic CPU tasks execute on one actual reserved slot',()=>{
  const result=runHundredCpuTasks();
  assert.equal(result.prototypeCount,100);
  assert.equal(result.taskCount,100);
  assert.equal(result.peakRunning,1);
  assert.equal(result.snapshot.completed,100);
  assert.equal(result.snapshot.queued,0);
  assert.equal(result.snapshot.running,0);
  assert.equal(new Set(result.digests).size,100);
});
test('50 logical experts execute 50 real bounded CPU calculations',()=>{
  const result=runHundredCpuTasks({prototypeCount:50,taskCount:50});
  assert.equal(result.snapshot.logicalModels,50);
  assert.equal(result.snapshot.completed,50);
  assert.equal(result.peakRunning,1);
});
test('a deterministic matrix hash is stable',()=>{
  assert.equal(matrixChecksum(12),matrixChecksum(12));
  assert.match(matrixChecksum(13),/^[a-f0-9]{64}$/);
  assert.notEqual(matrixChecksum(13),matrixChecksum(12));
});
test('invalid computation size is rejected',()=>{
  assert.throws(()=>matrixChecksum(0,1000),/Invalid matrix size/);
  assert.throws(()=>runHundredCpuTasks({taskCount:101}),/Invalid task count/);
});
