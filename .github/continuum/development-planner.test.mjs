import test from 'node:test';
import assert from 'node:assert/strict';
import {prioritizeFindings,planBacklog} from './development-planner.mjs';
test('prioritizes high-impact integration findings deterministically',()=>{
 const report={findings:[
  {kind:'UNREFERENCED',symbol:'A',file:'a.kt'},
  {kind:'TEST_ONLY',symbol:'B',file:'b.kt'},
  {kind:'DEAD_CLUSTER',symbol:'C',file:'c.kt'},
  {kind:'SOMETHING_ELSE',symbol:'D',file:'d.kt'}
 ]};
 assert.deepEqual(prioritizeFindings(report).map(x=>x.symbol),['C','B','A']);
 assert.ok(prioritizeFindings(report).every(x=>x.action==='INVESTIGATE_ONLY'));
});
test('rejects malformed findings input',()=>assert.throws(()=>prioritizeFindings({})));
test('creates project backlog without altering sources',()=>{
 const result=planBacklog(process.cwd(),3);
 assert.equal(result.schemaVersion,1);
 assert.equal(result.mode,'review-required');
 assert.ok(result.tasks.length<=3);
});
