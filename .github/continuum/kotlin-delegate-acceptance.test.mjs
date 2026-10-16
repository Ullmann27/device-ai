import test from 'node:test';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import {parseKotlinIntCases,assessKotlinDelegate} from './kotlin-delegate-acceptance.mjs';
import {proposeKotlinDelegate,proposeKotlinDelegateWithOracle} from './kotlin-delegate-synthesizer.mjs';

const target='app/src/main/java/p/MathApi.kt';
const original=[
 'package p','class MathApi {',
 '    fun square(n: Int): Int = n * n',
 '    fun description(): String = "squareTwice(2) remains a string"',
 '}',''
].join('\n');
const task={id:'owner-bounded-delegate',kind:'IMPLEMENT',source:'owner',state:'READY',
 subject:{operation:'EXPOSE_DELEGATE',file:target,className:'MathApi',
  methodName:'squareTwice',delegateName:'square',
  parameterName:'value',type:'Int',depth:2}};
const generated=()=>proposeKotlinDelegate({
 files:[{path:target,content:original}],task
}).candidate;
const executable=Boolean(process.env.CONTINUUM_REPAIR_DEPTH)||
 spawnSync('kotlinc',['-version'],{encoding:'utf8',timeout:15000}).status!==0;

test('strict Kotlin integer acceptance cases reject untrusted code-shaped JSON',()=>{
 assert.deepEqual(parseKotlinIntCases('[{"input":2,"expected":16}]'),
  [{input:2,expected:16}]);
 for(const value of [
  '[]','{}','not json',
  '[{"input":2,"expected":"16"}]',
  '[{"input":2,"expected":16,"extra":42}]',
  '[{"input":2,"expected":1000000001}]',
  '[{"input":1.5,"expected":2}]',
  '[{"input":{},"expected":4}]',
  '[{"input":2,"expected":16}]'.repeat(20)
 ])assert.equal(parseKotlinIntCases(value),null);
});

test('generated Kotlin code must compile RED before and GREEN after, then compute two expected outputs',
 {skip:executable,timeout:180000},()=>{
 const patch=generated();
 assert.ok(patch?.critic.approved);
 const response=proposeKotlinDelegateWithOracle({files:[{path:target,content:original}],
  task:{...task,subject:{...task.subject,
   casesJson:'[{"input":2,"expected":16},{"input":3,"expected":81}]'}}});
 assert.ok(response.candidate,'the accepted owner proposal must survive both gates');
 assert.equal(response.approvedForMerge,false);
 assert.equal(response.androidBuildVerified,false);
 const r=response.acceptance;
 assert.equal(r.passed,true,JSON.stringify(r));
 assert.equal(r.executed,2);
 assert.equal(r.before.exitCode,1);
 assert.equal(r.after.exitCode,0);
 assert.equal(r.actual.exitCode,0);
 assert.equal(r.androidBuildVerified,false);
});

test('wrong independent expected output rejects a compiling patch',
 {skip:executable,timeout:180000},()=>{
 const patch=generated();
 assert.ok(patch);
 const response=proposeKotlinDelegateWithOracle({
  files:[{path:target,content:original}],
  task:{...task,subject:{...task.subject,casesJson:'[{"input":2,"expected":999}]'}}
 });
 assert.equal(response.candidate,null);
 const r=response.acceptance;
 assert.equal(r.passed,false);
 assert.equal(r.reason,'CANDIDATE_BEHAVIOR_FAILED');
 assert.equal(r.before.exitCode,1);
 assert.equal(r.after.exitCode,0);
 assert.notEqual(r.actual.exitCode,0);
});

test('an arbitrary red baseline is never accepted as proof of the requested missing method',
 {skip:executable,timeout:180000},()=>{
 const r=assessKotlinDelegate({
  original:original.replace('n * n','UNKNOWN_SYMBOL'),
  generated:original.replace('n * n','UNKNOWN_SYMBOL').replace(
   '    fun description()',
   '    fun squareTwice(value: Int): Int = square(square(value))\n    fun description()'),
  className:'MathApi',methodName:'squareTwice',
  casesJson:'[{"input":2,"expected":16}]'
 });
 assert.equal(r.passed,false);
 assert.equal(r.reason,'BASELINE_NOT_SPECIFICALLY_RED');
});
