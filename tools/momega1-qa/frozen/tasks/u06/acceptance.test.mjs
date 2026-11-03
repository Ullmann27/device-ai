import test from 'node:test';
import assert from 'node:assert/strict';
import { run } from './subject.mjs';
test("Weighted measurement",()=>{
assert.equal(run([{value:4,weight:3},{value:10,weight:1}]),5.5); assert.equal(run([]),null); assert.equal(run([{value:3,weight:0}]),null);
});
