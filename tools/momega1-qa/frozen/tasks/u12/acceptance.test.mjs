import test from 'node:test';
import assert from 'node:assert/strict';
import { run } from './subject.mjs';
test("Cache freshness",()=>{
assert.equal(run({insertedAt:100,ttl:20},120),false); assert.equal(run({insertedAt:100,ttl:20},119),true); assert.equal(run({insertedAt:100,ttl:0},100),false);
});
