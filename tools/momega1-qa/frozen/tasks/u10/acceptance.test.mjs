import test from 'node:test';
import assert from 'node:assert/strict';
import { run } from './subject.mjs';
test("Integer cents rounding",()=>{
assert.equal(run(-1500),-2); assert.equal(run(1500),2); assert.equal(run(-1499),-1); assert.equal(run(1499),1);
});
