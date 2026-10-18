import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {verifyJUnitXml,verifyJUnitReport} from './m4-junit-evidence.mjs';

function fixture(code,fn){
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-m4-xml-memory-'));
 try{
  const source=path.join(dir,'sample.test.mjs');
  const report=path.join(dir,'report.xml');
  fs.writeFileSync(source,"import test from 'node:test';\n"+code);
  const environment={...process.env};
  delete environment.NODE_TEST_CONTEXT;
  const run=spawnSync(process.execPath,['--test','--test-reporter=junit',
   '--test-reporter-destination='+report,source],
   {cwd:dir,encoding:'utf8',timeout:10000,env:environment});
  assert.equal(run.status,0,run.stderr);
  fn(fs.readFileSync(report,'utf8'),report);
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
}

test('in-memory and file JUnit parsers agree on a real Node 22 report',()=>{
 fixture("test('real evidence',()=>{});\n",(xml,file)=>{
  const memory=verifyJUnitXml(xml);
  const disk=verifyJUnitReport(file);
  const {file:actualPath,...fromDisk}=disk;
  assert.equal(actualPath,file);
  assert.deepEqual(memory,fromDisk);
  assert.equal(memory.executed,1);
 });
});

test('in-memory JUnit parsing handles nested and skipped Node reporter cases',()=>{
 fixture("test('parent',async t=>{await t.test('child',()=>{});});\ntest.skip('skipped',()=>{});\n",(xml,file)=>{
  const memory=verifyJUnitXml(xml);
  const {file:ignored,...disk}=verifyJUnitReport(file);
  assert.deepEqual(memory,disk);
  assert.equal(memory.groupCount,1);
  assert.equal(memory.skipped,1);
  assert.equal(memory.executed,2);
 });
});

test('malformed totals and Buffer input are rejected consistently',()=>{
 const xml='<?xml version="1.0"?><testsuites><testcase name="ok"/>'+[
  '<!-- tests 1 -->','<!-- pass 1 -->','<!-- fail 0 -->',
  '<!-- cancelled 0 -->','<!-- skipped 0 -->','<!-- todo 0 -->'
 ].join('\n')+'</testsuites>';
 assert.equal(verifyJUnitXml(xml).executed,1);
 assert.throws(()=>verifyJUnitXml(xml.replace('<!-- pass 1 -->','<!-- pass 0 -->')));
 assert.throws(()=>verifyJUnitXml(Buffer.from(xml)));
 assert.throws(()=>verifyJUnitXml('<testsuites/>'));
});
