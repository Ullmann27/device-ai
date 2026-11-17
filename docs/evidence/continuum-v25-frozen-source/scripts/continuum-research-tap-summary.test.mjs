import test from 'node:test';
import assert from 'node:assert/strict';
import {parseNodeTapSummary} from './continuum-research-tap-summary.mjs';
const output=(counts={})=>{
 const data={tests:3,pass:3,fail:0,cancelled:0,skipped:0,todo:0,...counts};
 return `TAP version 13\nok 1 - fine\n# internal nested tests 99\n1..3\n${Object.entries(data).map(([k,v])=>`# ${k} ${v}`).join('\n')}\n# duration_ms 18\n`;
};
test('strict full coverage passes',()=>assert.deepEqual(parseNodeTapSummary(output()),
 {tests:3,pass:3,fail:0,cancelled:0,skipped:0,todo:0,status:'ALL_EXECUTED_PASS'}));
test('skipped tests explicitly prevent complete status',()=>assert.equal(
 parseNodeTapSummary(output({pass:2,skipped:1})).status,'PASS_WITH_UNEXECUTED_CASES'));
test('todo tests explicitly prevent complete status',()=>assert.equal(
 parseNodeTapSummary(output({pass:2,todo:1})).status,'PASS_WITH_UNEXECUTED_CASES'));
test('failed and cancelled runs never pass even if caller reported success',()=>{
 for(const changes of [{pass:2,fail:1},{pass:2,cancelled:1}])
 assert.throws(()=>parseNodeTapSummary(output(changes)),/NODE_TAP_EXECUTION_FAILED/);
});
test('missing or corrupt totals are blocked',()=>{
 assert.throws(()=>parseNodeTapSummary('ok 1 - this is not a full summary'),/NODE_TAP_SUMMARY_INCOMPLETE/);
 assert.throws(()=>parseNodeTapSummary(output({tests:5})),/NODE_TAP_TOTAL_MISMATCH/);
 assert.throws(()=>parseNodeTapSummary(output({pass:0,skipped:3})),/NODE_TAP_TOTAL_MISMATCH/);
 assert.throws(()=>parseNodeTapSummary(output().replace('# todo 0','')),/NODE_TAP_SUMMARY_INCOMPLETE/);
});
test('reported numbers cannot exceed JavaScript safe integer range',()=>{
 assert.throws(()=>parseNodeTapSummary(output().replace('# tests 3','# tests 999999999999999999999')),/NODE_TAP_COUNT_INVALID/);
});
