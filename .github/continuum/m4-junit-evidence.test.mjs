import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {verifyJUnitReport} from './m4-junit-evidence.mjs';

const report=(body,totals='<!-- tests 1 -->\n<!-- pass 1 -->\n<!-- fail 0 -->\n<!-- cancelled 0 -->\n<!-- skipped 0 -->')=>
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
  report('<testcase name="skipped"><skipped/></testcase>','<!-- tests 1 -->\n<!-- pass 0 -->\n<!-- fail 0 -->\n<!-- cancelled 0 -->\n<!-- skipped 1 -->'),
  report('<testcase name="failing"><failure message="x"/></testcase>','<!-- tests 1 -->\n<!-- pass 0 -->\n<!-- fail 1 -->\n<!-- cancelled 0 -->\n<!-- skipped 0 -->'),
  report('<testcase name="ok"/>','<!-- tests 2 -->\n<!-- pass 2 -->\n<!-- fail 0 -->\n<!-- cancelled 0 -->\n<!-- skipped 0 -->'),
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
