import test from 'node:test';
import assert from 'node:assert/strict';
import { run } from './subject.mjs';
test("Explicit zero setting",()=>{
for(const v of [0,false,'']) assert.equal(run(v,9),v); assert.equal(run(null,9),9); assert.equal(run(undefined,9),9);
});
