import test from 'node:test';
import assert from 'node:assert/strict';
import {triageRun,summarize} from './developer-agent.mjs';
test('detects failed unit tests without reading or executing logs',()=>{
 const run={id:123,conclusion:'failure',head_sha:'a'.repeat(40),name:'Build'};
 const result=triageRun(run,[{name:'build',steps:[{name:'Run unit tests',conclusion:'failure'}]}]);
 assert.equal(result.category,'TEST_FAILURE');assert.equal(result.failedSteps.length,1);
});
test('successful and pending runs remain separate',()=>{
 const result=summarize([{id:1,conclusion:'success'},{id:2,conclusion:null}]);
 assert.equal(result.successes,1);assert.equal(result.pending,1);
});
test('invalid run IDs fail closed',()=>assert.throws(()=>triageRun({id:'not-a-number'})));
