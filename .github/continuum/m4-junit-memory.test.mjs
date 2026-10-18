import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {verifyJUnitXml,verifyJUnitReport,verifyM4AcceptanceEvidence,structuralJUnitMarkup} from './m4-junit-evidence.mjs';

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

test('comments cannot fabricate executed JUnit cases or parent suites',()=>{
 const totals=['tests 3','pass 3','fail 0','cancelled 0','skipped 0','todo 0']
  .map(item=>'<!-- '+item+' -->').join('\n');
 const fake='<?xml version="1.0"?><testsuites>\n<!--\n'+
  '<testcase name="fake A"/>\n<testcase name="fake B"/>\n'+
  '<testcase name="fake C"/>\n-->\n'+totals+'</testsuites>';
 assert.throws(()=>verifyJUnitXml(fake),/incomplete|inconsistent/);
 const real='<?xml version="1.0"?><testsuites><testcase name="actual"/>\n'+
  ['tests 1','pass 1','fail 0','cancelled 0','skipped 0','todo 0']
   .map(x=>'<!-- '+x+' -->').join('\n')+'</testsuites>';
 assert.equal(verifyJUnitXml(real).executed,1);
 assert.doesNotMatch(structuralJUnitMarkup(fake),/<testcase\b/);
});

test('CDATA and malformed comment delimiters cannot spoof executed cases',()=>{
 const totals=['tests 1','pass 1','fail 0','cancelled 0','skipped 0','todo 0']
  .map(item=>'<!-- '+item+' -->').join('\n');
 for(const body of ['<![CDATA[<testcase name="fake"/>]]>',
  '<!-- <testcase name="fake"/>', '<!DOCTYPE testsuites [<!ENTITY x "fake">]>']){
  const report='<?xml version="1.0"?><testsuites>'+body+totals+'</testsuites>';
  assert.throws(()=>verifyJUnitXml(report),/unsafe|malformed|incomplete|inconsistent/i);
 }
});

test('three-profile M4 acceptance reads its mandatory receipt only once',()=>{
 const source=['end-to-end: injected fault is reproduced, repaired in isolation, verified and committed',
  'regression search reverts only the culprit hunk and keeps unrelated changes',
  'a module that crashes while loading is located and gets its missing import back']
  .map(s=>'test('+JSON.stringify(s)+',()=>{});').join('\n')+'\n';
 fixture(source,(xml,repair)=>{
  const root=path.dirname(repair);
  const node=path.join(root,'node.xml');
  const backend=path.join(root,'backend.xml');
  fs.writeFileSync(node,xml);
  fs.writeFileSync(backend,xml);
  const old=fs.readFileSync;
  let repairReads=0;
  fs.readFileSync=function(...args){
   if(args[0]===repair)repairReads++;
   return old.apply(this,args);
  };
  try{
   const result=verifyM4AcceptanceEvidence(repair,node,backend);
   assert.equal(result.mandatoryRepairs.length,3);
  }finally{fs.readFileSync=old;}
  assert.equal(repairReads,1,'M4 acceptance must not re-read a swapped receipt');
 });
});

test('valid-looking JUnit testcase tags nested inside system-out are not real tests',()=>{
 const names=['end-to-end: injected fault is reproduced, repaired in isolation, verified and committed',
  'regression search reverts only the culprit hunk and keeps unrelated changes',
  'a module that crashes while loading is located and gets its missing import back'];
 const fake='<?xml version="1.0"?><testsuites><system-out>'+
  names.map(n=>'<testcase name="'+n+'"/>').join('')+'</system-out>'+
  ['tests 3','pass 3','fail 0','cancelled 0','skipped 0','todo 0']
   .map(n=>'<!-- '+n+' -->').join('')+'</testsuites>';
 assert.throws(()=>verifyJUnitXml(fake),/outside test containers/);
});

test('structural XML verifier rejects duplicate roots and mismatched closing tags',()=>{
 const summary=['tests 1','pass 1','fail 0','cancelled 0','skipped 0','todo 0']
  .map(n=>'<!-- '+n+' -->').join('');
 for(const bad of ['<testsuites><testcase name="t"/></testsuites><testsuites/>',
  '<testsuites><testcase name="t"/></testsuite>']){
  assert.throws(()=>verifyJUnitXml('<?xml version="1.0"?>'+bad+summary),/JUnit|Malformed/);
 }
});
