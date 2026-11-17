// Keep opt-in research regressions in the existing M6 Node test discovery.
import '../experimental/omega/repair/numeric-verifier.test.mjs';
import '../experimental/omega/benchmarks/controlled-repair.test.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import {critique} from '../.github/continuum/repair-critic.mjs';
test('model patches cannot rewrite OMEGA evaluators or benchmark definitions',()=>{
 for(const file of ['experimental/omega/repair/numeric-worker.mjs','experimental/omega/benchmarks/tasks.json']){
  const result=critique({strategy:'REPAIR',edits:[{file,oldText:'return 0;',newText:'return 1;'}]});
  assert.equal(result.approved,false,file);
  assert.ok(result.reasons.some(r=>r.includes('protected path')),file);
 }
});
