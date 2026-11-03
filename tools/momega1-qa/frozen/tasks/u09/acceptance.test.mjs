import test from 'node:test';
import assert from 'node:assert/strict';
import { run } from './subject.mjs';
test("Immutable queue",()=>{
const a={priority:2},b={priority:1},q=[a,b]; const result=run(q); assert.deepEqual(result,[b,a]); assert.deepEqual(q,[a,b]); assert.equal(result[0],b);
});
