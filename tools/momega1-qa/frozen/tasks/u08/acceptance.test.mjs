import test from 'node:test';
import assert from 'node:assert/strict';
import { run } from './subject.mjs';
test("UTC month",()=>{
assert.equal(run(Date.UTC(2024,1,29)),'2024-02-29'); assert.equal(run(Date.UTC(2025,11,31,23,59)),'2025-12-31');
});
