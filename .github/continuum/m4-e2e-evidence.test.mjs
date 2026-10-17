import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import * as evidence from './m4-junit-evidence.mjs';

const required=[
 'end-to-end: injected fault is reproduced, repaired in isolation, verified and committed',
 'regression search reverts only the culprit hunk and keeps unrelated changes',
 'a module that crashes while loading is located and gets its missing import back'
];
const item=(name,body='')=>`<testcase classname="test" name="${name}">${body}</testcase>`;
function junit(items,{skip=0}={}){
 const pass=items.length-skip;
 return `<?xml version="1.0" encoding="utf-8"?>\n<testsuites>\n${items.join('\n')}\n<!-- tests ${items.length} -->\n<!-- pass ${pass} -->\n<!-- fail 0 -->\n<!-- cancelled 0 -->\n<!-- skipped ${skip} -->\n</testsuites>`;
}
function withReports(repairXml,fn){
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-verified-e2e-'));
 try{
  const files=['repair.xml','node.xml','backend.xml'].map(name=>path.join(dir,name));
  fs.writeFileSync(files[0],repairXml);
  fs.writeFileSync(files[1],junit([item('node suite completed')]));
  fs.writeFileSync(files[2],junit([item('backend suite completed')]));
  return fn(files);
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
}
test('M4 release gate requires the three actual repairs to have executed',()=>{
 withReports(junit(required.map(name=>item(name))),files=>{
  const result=evidence.verifyM4AcceptanceEvidence(...files);
  assert.equal(result.reports.length,3);
  assert.equal(result.mandatoryRepairs.length,3);
 });
});
test('M4 release gate refuses a missing repair regression despite otherwise green JUnit',()=>{
 withReports(junit(required.slice(1).map(name=>item(name))),files=>
  assert.throws(()=>evidence.verifyM4AcceptanceEvidence(...files),/mandatory|repair|incomplete/i));
});
test('M4 release gate refuses a skipped repair or a fake file-only pass',()=>{
 withReports(junit([
  item(required[0],'<skipped/>'),item(required[1]),item(required[2]),item('ordinary pass')
 ],{skip:1}),files=>
  assert.throws(()=>evidence.verifyM4AcceptanceEvidence(...files),/mandatory|skipped|repair/i));
 withReports(junit([item('repair-loop.test.mjs'),item('ordinary test')]),files=>
  assert.throws(()=>evidence.verifyM4AcceptanceEvidence(...files),/mandatory|repair|incomplete/i));
});
