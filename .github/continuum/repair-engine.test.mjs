import test from 'node:test';
import assert from 'node:assert/strict';
import {knownFixes} from './repair-engine.mjs';
test('identifies known Kotlin set/list error without modifying source',()=>{
 const fixes=knownFixes('items.toSet().takeLast(3)');
 assert.equal(fixes.length,1);
 assert.equal(fixes[0].kind,'SET_TAKELAST');
});
test('does not suggest changes to unrelated source',()=>assert.deepEqual(knownFixes('items.toList().takeLast(3)'),[]));
test('rule table is immutable',()=>{const f=knownFixes('a.toSet().takeLast(1)');f[0].to='x';assert.equal(knownFixes('a.toSet().takeLast(1)')[0].to,'.toSet().toList().takeLast(');});
