import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {verifyJUnitReport} from './m4-junit-evidence.mjs';

const report=(body,totals='<!-- tests 1 -->\n<!-- pass 1 -->\n<!-- fail 0 -->\n<!-- cancelled 0 -->\n<!-- skipped 0 -->\n<!-- todo 0 -->')=>
 '<?xml version="1.0"?><testsuites>'+body+'\n'+totals+'</testsuites>';

function withReport(xml,fn){
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-m4-evidence-'));
 try{const file=path.join(dir,'evidence.xml');fs.writeFileSync(file,xml);return fn(file,dir);}
 finally{fs.rmSync(dir,{recursive:true,force:true});}
}

test('M4 accepts only actually executed, successful Node testcases',()=>{
 withReport(report('<testcase name="successful acceptance" classname="test"/>'),file=>{
  const result=verifyJUnitReport(file);
  assert.equal(result.executed,1);
  assert.equal(result.failed,0);
 });
});

test('M4 refuses absent, malformed, empty, skipped, failing, or symlinked reports',()=>{
 const examples=[
  '', '<testsuites/>',
  report(''),
  report('<testcase name="unexecuted.test.mjs"/>'),
  report('<testcase name="skipped"><skipped/></testcase>','<!-- tests 1 -->\n<!-- pass 0 -->\n<!-- fail 0 -->\n<!-- cancelled 0 -->\n<!-- skipped 1 -->\n<!-- todo 0 -->'),
  report('<testcase name="failing"><failure message="x"/></testcase>','<!-- tests 1 -->\n<!-- pass 0 -->\n<!-- fail 1 -->\n<!-- cancelled 0 -->\n<!-- skipped 0 -->\n<!-- todo 0 -->'),
  report('<testcase name="ok"/>','<!-- tests 2 -->\n<!-- pass 2 -->\n<!-- fail 0 -->\n<!-- cancelled 0 -->\n<!-- skipped 0 -->\n<!-- todo 0 -->'),
 ];
 for(const xml of examples)withReport(xml,file=>assert.throws(()=>verifyJUnitReport(file),undefined,xml.slice(0,80)));
 withReport(report('<testcase name="ok"/>'),(file,dir)=>{
  const link=path.join(dir,'symlink.xml');fs.symlinkSync(file,link);
  assert.throws(()=>verifyJUnitReport(link));
  assert.throws(()=>verifyJUnitReport(path.join(dir,'missing.xml')));
 });
});

test('M4 verifies a real Node 22 JUnit reporter output',()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-m4-real-junit-'));
 try{
  const sample=path.join(dir,'fixture.test.mjs');
  const xml=path.join(dir,'result.xml');
  fs.writeFileSync(sample,"import test from 'node:test';\ntest('actually executed',()=>{});\n");
  const env={...process.env};
  delete env.NODE_TEST_CONTEXT;
  const result=spawnSync(process.execPath,['--test','--test-reporter=junit',
   '--test-reporter-destination='+xml,sample],{encoding:'utf8',timeout:10000,env});
  assert.equal(result.status,0,result.stderr);
  assert.equal(verifyJUnitReport(xml).executed,1);
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
});

test('M4 accepts genuine Node 22 nested test suites and excludes skipped cases',()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-m4-nested-junit-'));
 try{
  const sample=path.join(dir,'nested.test.mjs');
  const xml=path.join(dir,'result.xml');
  fs.writeFileSync(sample,`import test from 'node:test';
import assert from 'node:assert/strict';
test('parent', async t => {
 await t.test('child A',()=>assert.equal(2,2));
 await t.test('child B',()=>assert.equal(3,3));
});
test('ordinary',()=>assert.ok(true));
test.skip('skipped',()=>{});
test('outer',async t=>{await t.test('inner',async t=>{await t.test('leaf',()=>assert.ok(true));});});
`);
  const env={...process.env};
  delete env.NODE_TEST_CONTEXT;
  const run=spawnSync(process.execPath,['--test','--test-reporter=junit',
   '--test-reporter-destination='+xml,sample],{encoding:'utf8',timeout:10000,env});
  assert.equal(run.status,0,run.stderr);
  const result=verifyJUnitReport(xml);
  assert.equal(result.total,8,'Node reporter counts suite parent tests');
  assert.equal(result.executed,7);
  assert.equal(result.skipped,1);
  assert.equal(result.failed,0);
  const tampered=fs.readFileSync(xml,'utf8').replace('<!-- tests 8 -->','<!-- tests 9 -->')
    .replace('<!-- pass 7 -->','<!-- pass 8 -->');
  fs.writeFileSync(xml,tampered);
  assert.throws(()=>verifyJUnitReport(xml),/incomplete|inconsistent/);
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
});

test('M4 classifies genuine Node 22 TODO tests independently of skipped tests',()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-m4-todo-junit-'));
 try{
  const sample=path.join(dir,'todo.test.mjs');
  const xml=path.join(dir,'result.xml');
  fs.writeFileSync(sample,`import test from 'node:test';
test.todo('future');
test('normal',()=>{});
test('group',async t=>{
 await t.test('executed child',()=>{});
 await t.test('todo child',{todo:'next'},()=>{});
});
`);
  const env={...process.env};
  delete env.NODE_TEST_CONTEXT;
  const processResult=spawnSync(process.execPath,['--test','--test-reporter=junit',
   '--test-reporter-destination='+xml,sample],{encoding:'utf8',timeout:10000,env});
  assert.equal(processResult.status,0,processResult.stderr);
  const result=verifyJUnitReport(xml);
  assert.equal(result.total,5);
  assert.equal(result.executed,3);
  assert.equal(result.skipped,0);
  assert.equal(result.todo,2);
  fs.writeFileSync(xml,fs.readFileSync(xml,'utf8').replace('<!-- todo 2 -->','<!-- todo 0 -->'));
  assert.throws(()=>verifyJUnitReport(xml),/incomplete|inconsistent|todo/i);
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
});
