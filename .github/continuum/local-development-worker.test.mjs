import test from 'node:test';
import assert from 'node:assert/strict';
import {evaluateCheckout,classifyNativeTestRun} from '../../scripts/continuum-local-development-worker.mjs';
const tap=parts=>'# tests '+parts.tests+'\n# pass '+parts.pass+
 '\n# fail '+parts.fail+'\n# cancelled '+parts.cancelled+
 '\n# skipped '+parts.skipped+'\n# todo '+parts.todo+'\n';
const ok={tests:3,pass:3,fail:0,cancelled:0,skipped:0,todo:0};
test('checks nonproductive clean branch and bounded verified inventory',()=>{
 const a=evaluateCheckout({branch:'copilot/pr-12-working-first-vertical-slice',
  dirty:false,tests:['neural-core.test.mjs','coding-task.test.mjs']});
 assert.deepEqual(a.tests,['coding-task.test.mjs','neural-core.test.mjs']);
});
test('refuses main, release, dirty and invalid inventories',()=>{
 for(const branch of ['main','production','release/v1','../main',''])
   assert.throws(()=>evaluateCheckout({branch,dirty:false,tests:['a.test.mjs']}),
     /UNSAFE_CHECKOUT/);
 assert.throws(()=>evaluateCheckout({branch:'dev',dirty:true,tests:['a.test.mjs']}),
   /UNSAFE_CHECKOUT/);
 for(const tests of [[],['../../evil.test.mjs'],['bad.exe']])
   assert.throws(()=>evaluateCheckout({branch:'dev',dirty:false,tests}),
     /UNSAFE_TEST_INVENTORY/);
});
test('real node TAP success does not imply a patch was approved',()=>{
 const result=classifyNativeTestRun({exitCode:0,stdout:tap(ok)});
 assert.equal(result.status,'ALL_EXECUTED_PASS');
 assert.equal(result.tests,3);
});
test('skipped and TODO mean incomplete quality gate',()=>{
 for(const variant of [{...ok,pass:2,skipped:1},{...ok,pass:2,todo:1}])
 assert.equal(classifyNativeTestRun({exitCode:0,stdout:tap(variant)}).status,
  'PASS_WITH_UNEXECUTED_CASES');
});
test('missing summary, timeout, failed case or invalid totals are never green',()=>{
 assert.equal(classifyNativeTestRun({exitCode:0,stdout:'ok 1 - incomplete'}).status,'FAILED');
 assert.equal(classifyNativeTestRun({exitCode:null,stdout:tap(ok),error:'SIGTERM'}).status,'FAILED');
 assert.equal(classifyNativeTestRun({exitCode:0,stdout:tap({...ok,pass:2,fail:1})}).status,'FAILED');
 assert.equal(classifyNativeTestRun({exitCode:0,stdout:tap({...ok,tests:4})}).status,'FAILED');
});
