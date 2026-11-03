import test from 'node:test';
import assert from 'node:assert/strict';
import { run } from './subject.mjs';
test("Path membership",()=>{
assert.equal(run('/work/app','/work/application/key'),false); assert.equal(run('/work/app','/work/app/a'),true); assert.equal(run('/work/app','/work/app'),true); assert.equal(run('/','/tmp'),true);
});
