import test from 'node:test';
import assert from 'node:assert/strict';
import { run } from './subject.mjs';
test("First occurrence deduplication",()=>{
assert.deepEqual(run(['b','a','b','c','a']),['b','a','c']); assert.deepEqual(run([]),[]);
});
