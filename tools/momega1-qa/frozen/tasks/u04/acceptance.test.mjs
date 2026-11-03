import test from 'node:test';
import assert from 'node:assert/strict';
import { run } from './subject.mjs';
test("Numeric order",()=>{
const a=[10,2,-1,2]; assert.deepEqual(run(a),[-1,2,2,10]); assert.deepEqual(a,[10,2,-1,2]);
});
