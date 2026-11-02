import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {androidEvidence,nodeReceiptEvidence,REQUIRED_ANDROID_SUITES} from './continuum-qa-evidence.mjs';

function fixture(t) {
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-junit-proof-'));
  t.after(()=>fs.rmSync(root,{recursive:true,force:true}));
  const dir=path.join(root,'app/build/test-results/testDebugUnitTest');
  fs.mkdirSync(dir,{recursive:true});
  const startedAtMillis=Date.now()-100;
  const xml=klass=>`<testsuite name="${klass}" tests="1" skipped="0" failures="0" errors="0"><testcase name="executed" classname="${klass}"/></testsuite>`;
  for(const klass of REQUIRED_ANDROID_SUITES)fs.writeFileSync(path.join(dir,'TEST-'+klass+'.xml'),xml(klass));
  return {root,dir,startedAtMillis,xml,file:path.join(dir,'TEST-'+REQUIRED_ANDROID_SUITES[0]+'.xml')};
}
test('fresh required suites prove actual execution and exact counts',t=>{
  const f=fixture(t),r=androidEvidence(f);
  assert.equal(r.status,'PASS',JSON.stringify(r));
  assert.equal(r.counts.executed,3);
  assert.equal(r.mandatory.length,3);
});
for(const kind of ['missing','empty','skipped','wrong-class','wrong-count','malformed','stale','future','symlink']) {
  test('required Android proof rejects '+kind,t=>{
    const f=fixture(t),klass=REQUIRED_ANDROID_SUITES[0];
    if(kind==='missing')fs.unlinkSync(f.file);
    if(kind==='empty')fs.writeFileSync(f.file,`<testsuite name="${klass}" tests="0" failures="0" errors="0" skipped="0"/>`);
    if(kind==='skipped')fs.writeFileSync(f.file,f.xml(klass).replace('skipped="0"','skipped="1"').replace('/></testsuite>','><skipped/></testcase></testsuite>'));
    if(kind==='wrong-class')fs.writeFileSync(f.file,f.xml(klass).replace('classname="'+klass+'"','classname="at.heinz.deviceai.Unrelated"'));
    if(kind==='wrong-count')fs.writeFileSync(f.file,f.xml(klass).replace('tests="1"','tests="2"'));
    if(kind==='malformed')fs.writeFileSync(f.file,f.xml(klass)+'<');
    if(kind==='stale')fs.utimesSync(f.file,new Date(0),new Date(0));
    if(kind==='future')fs.utimesSync(f.file,new Date(Date.now()+10000),new Date(Date.now()+10000));
    if(kind==='symlink') {fs.renameSync(f.file,f.file+'.outside');fs.symlinkSync(f.file+'.outside',f.file);}
    const r=androidEvidence(f);
    assert.equal(r.status,'FAIL',kind);
    assert.ok(r.failures.length>0);
  });
}

function receipt() {
  const now=Date.now();
  return {status:'PASS',profile:'full-node',sourceUnchanged:true,source:{commit:'a'.repeat(40)},
    startedAt:new Date(now-100).toISOString(),finishedAt:new Date(now).toISOString(),
    checks:[{id:'full-node',status:'PASS',code:0,terminationConfirmed:true,
      tests:{executed:1,evidenceComplete:true,failed:0}}]};
}
test('Node receipt requires exact current-source completed executed tests',()=>{
  const r=receipt(),from=Date.now()-1000;
  assert.equal(nodeReceiptEvidence(r,'a'.repeat(40),'full-node',from).status,'PASS');
  for(const mutate of [
    r=>{r.source.commit='b'.repeat(40);},r=>{r.sourceUnchanged=false;},
    r=>{r.profile='critical';},r=>{r.startedAt=new Date(0).toISOString();},
    r=>{r.finishedAt=new Date(Date.now()+10000).toISOString();},
    r=>{r.checks=[];},r=>{r.checks.push(r.checks[0]);},
    r=>{r.checks[0].terminationConfirmed=false;},r=>{r.checks[0].tests.executed=0;},
    r=>{r.checks[0].tests.failed=1;},r=>{r.checks[0].code=1;}
  ]) {const bad=structuredClone(r);mutate(bad);
    assert.equal(nodeReceiptEvidence(bad,'a'.repeat(40),'full-node',from).status,'FAIL');}
});
