import test from 'node:test';
import assert from 'node:assert/strict';
import {knownFixes,safeReplace,validateInWorktree} from './repair-engine.mjs';
test('identifies known Kotlin set/list error without modifying source',()=>{
 const fixes=knownFixes('items.toSet().takeLast(3)');
 assert.equal(fixes.length,1);
 assert.equal(fixes[0].kind,'SET_TAKELAST');
});
test('does not suggest changes to unrelated source',()=>assert.deepEqual(knownFixes('items.toList().takeLast(3)'),[]));
test('rejects shell command injection',()=>assert.throws(()=>validateInWorktree('.', ['sh','-c','echo unsafe'])));
test('rejects path traversal',()=>assert.throws(()=>safeReplace('../x.kt','a','b')));
