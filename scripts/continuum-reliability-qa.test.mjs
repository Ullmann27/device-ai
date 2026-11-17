import test from 'node:test';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {assessNodeOutcome,renderReport,sameSource,GATES} from './continuum-reliability-qa.mjs';
const good={ok:true,code:0,terminationConfirmed:true,error:null,exitSignal:null};
const xml=body=>`<testsuites><testsuite name="suite"><testcase name="real assertion" classname="suite">${body}</testcase></testsuite></testsuites>`;
test('source binding includes branch-dependent gates even when HEAD and bytes stay identical',()=>{
  const before={commit:'a'.repeat(40),branch:'research/qa',treeDigest:'b'.repeat(64)};
  assert.equal(sameSource(before,{...before}),true);
  for(const field of ['commit','branch','treeDigest'])assert.equal(sameSource(before,{...before,[field]:'changed'}),false);
});
test('QA requires actual executed evidence and confirmed cleanup',()=>{
  assert.equal(assessNodeOutcome(good,xml('')).status,'PASS');
  for(const result of [{...good,terminationConfirmed:false},{...good,code:3},{...good,error:'TIMEOUT'},
    {...good,ok:false},{...good,exitSignal:'SIGTERM'}])assert.equal(assessNodeOutcome(result,xml('')).status,'FAIL');
  for(const data of ['', '<testsuites/>',xml('').replace('</testsuites>',''),xml('<failure message="bad"/>'),
    '<testsuite><testcase name="empty.test.mjs"/></testsuite>'])assert.equal(assessNodeOutcome(good,data).status,'FAIL');
  assert.equal(assessNodeOutcome(good,xml('<skipped/>')).status,'SKIP');
});
test('QA reports skips and failed assertions separately and keeps severity and device limits',()=>{
  const result=assessNodeOutcome(good,xml('<skipped/>'));
  assert.equal(result.tests.skipped,1);assert.equal(result.tests.executed,0);
  assert.equal(assessNodeOutcome(good,xml('<failure message="original failure"/>')).tests.failed,1);
  const md=renderReport({profile:'critical',status:'FAIL',source:{commit:'a'.repeat(40)},
    sourceUnchanged:true,environment:{node:'24',platform:'linux',arch:'x64'},checks:[{id:'process',severity:'P0',...result,code:0}]});
  assert.match(md,/P0 \| SKIP \| 0/);assert.match(md,/device\/model inference remain NOT_RUN/);
  assert.ok(GATES.find(g=>g.id==='process'));assert.ok(GATES.find(g=>g.id==='storage-agent'));
});
test('CLI lists a fixed command scope and refuses nesting without running tests',()=>{
  const script=fileURLToPath(new URL('./continuum-reliability-qa.mjs',import.meta.url));
  const listed=spawnSync(process.execPath,[script,'--list'],{encoding:'utf8',timeout:5000});
  assert.equal(listed.status,0);assert.equal(JSON.parse(listed.stdout).checks.length,4);
  const refused=spawnSync(process.execPath,[script,'critical','--out','/tmp/never-created-nested-qa'],{
    encoding:'utf8',timeout:5000,env:{...process.env,CONTINUUM_QA_DEPTH:'1'}});
  assert.equal(refused.status,1);assert.match(refused.stderr,/NESTED_QA_REFUSED/);
});
