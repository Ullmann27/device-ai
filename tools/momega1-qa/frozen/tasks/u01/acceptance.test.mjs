import test from 'node:test';
import assert from 'node:assert/strict';
import { run } from './subject.mjs';
test("Unicode prefix",()=>{
assert.equal(run('A😀B',2),'A😀'); assert.equal(run('😀',0),''); assert.equal(run('abc',9),'abc');
});
