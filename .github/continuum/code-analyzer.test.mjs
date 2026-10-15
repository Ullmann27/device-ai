import test from 'node:test';
import assert from 'node:assert/strict';
import {analyzeSources} from './code-analyzer.mjs';
test('finds candidate orphan but not referenced class',()=>{
 const result=analyzeSources([
  {path:'a.kt',content:'class Connected {}\nclass Orphan {}'},
  {path:'b.kt',content:'fun use() = Connected()'}
 ]);
 assert.ok(result.findings.some(f=>f.symbol==='Orphan'));
 assert.ok(!result.findings.some(f=>f.symbol==='Connected'));
});
test('ignores non-Kotlin sources',()=>{
 assert.equal(analyzeSources([{path:'x.txt',content:'class Missing {}'}]).declarations,0);
});
test('enforces source limit',()=>{
 assert.throws(()=>analyzeSources(Array.from({length:3001},(_,i)=>({path:i+'.kt',content:''}))));
});
