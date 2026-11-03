import test from 'node:test';
import assert from 'node:assert/strict';
import { run } from './subject.mjs';
test("Deadline boundary",()=>{
assert.equal(run(42,42),false); assert.equal(run(41,42),true); assert.equal(run(43,42),false);
});
