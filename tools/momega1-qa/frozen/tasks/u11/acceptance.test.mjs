import test from 'node:test';
import assert from 'node:assert/strict';
import { run } from './subject.mjs';
test("Half-open overlap",()=>{
assert.equal(run([0,5],[5,8]),false); assert.equal(run([0,6],[5,8]),true); assert.equal(run([4,4],[0,8]),false); assert.equal(run([9,12],[0,8]),false);
});
