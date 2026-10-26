import test from 'node:test';
import assert from 'node:assert/strict';
import {parseJUnit} from './repair-diagnostics.mjs';
import {assertCcc1TestEvidenceParity,assertCcc1WorktreePatchScope,assertCcc1RunMeasurement} from './ccc1-independent-verifier.mjs';
import {spawnSync} from 'node:child_process';
import {readJUnitDirectory} from './repair-diagnostics.mjs';
import {emptyLedger,recordSession} from './repair-ledger.mjs';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

const xml=(count,failed)=>'<testsuite>'+Array.from({length:count},(_,i)=>
 `<testcase name="case-${i}" classname="integrity">${failed?'<failure message="bad"/>':''}</testcase>`).join('')+'</testsuite>';
const run=tests=>({results:[{profile:'node-continuum',testProfile:true,tests}]});

test('diagnostic cap cannot truncate executed coverage or hide disappearing failed tests',()=>{
 const red=parseJUnit(xml(100,true),'/fixture');
 assert.equal(red.executed,100);
 assert.equal(red.failed,100);
 assert.equal(red.executedCaseHashes.length,100);
 assert.equal(red.diagnostics.length,60,'diagnostics remain bounded independently of evidence');
 const incompleteGreen=parseJUnit(xml(60,false),'/fixture');
 assert.throws(()=>assertCcc1TestEvidenceParity(run(red),run(incompleteGreen)),/EXECUTED_TEST_COVERAGE_REGRESSION/);
});

test('oversized XML cannot be silently treated as complete executed evidence',()=>{
 const oversized='<testsuite>'+xml(1,false)+' '.repeat(2_000_000)+'</testsuite>';
 assert.equal(parseJUnit(oversized,'/fixture').evidenceComplete,false);
});

test('parity rejects explicitly incomplete coverage even if identities and counts match',()=>{
 const red=parseJUnit(xml(3,true),'/fixture'),green=parseJUnit(xml(3,false),'/fixture');
 green.evidenceComplete=false;
 assert.throws(()=>assertCcc1TestEvidenceParity(run(red),run(green)),/TEST_COUNT_EVIDENCE_INVALID/);
});

test('JUnit directory cannot silently omit an oversized or linked report',t=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-junit-integrity-'));
 t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
 fs.writeFileSync(path.join(dir,'good.xml'),xml(1,false));
 fs.writeFileSync(path.join(dir,'large.xml'),' '.repeat(5_000_001));
 assert.equal(readJUnitDirectory(dir,'/fixture').evidenceComplete,false);
 fs.unlinkSync(path.join(dir,'large.xml'));
 fs.symlinkSync(path.join(dir,'good.xml'),path.join(dir,'alias.xml'));
 const report=readJUnitDirectory(dir,'/fixture');
 assert.equal(report.evidenceComplete,false);
 assert.equal(report.executed,1,'a symlink is never opened as another test report');
});

test('incomplete failing report cannot become negative strategy training evidence',()=>{
 const report={base:'a'.repeat(40),chain:'node',outcome:'NO_VERIFIED_REPAIR',
  attempts:[{candidate:'c'.repeat(16),diagnostic:'d'.repeat(16),diagnosticKind:'TEST_FAILURE',strategy:'REFERENCE_REPAIR',
   outcome:'FAILED_TESTS',edits:[],tests:[{profile:'node-continuum',passed:false,
    tests:{total:2,executed:2,failed:1,skipped:0,evidenceComplete:false}}]}]};
 const ledger=recordSession(emptyLedger(),report);
 assert.deepEqual(ledger.experiences,[]);
 assert.equal(ledger.attempts[0].tests[0].evidenceComplete,false);
});

test('a concurrent HEAD change cannot hide behind a one-file patch diff',t=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-head-integrity-'));
 t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
 const git=(...args)=>{const r=spawnSync('git',args,{cwd:dir,encoding:'utf8'});assert.equal(r.status,0,r.stderr);return r.stdout.trim();};
 git('init','-q');fs.writeFileSync(path.join(dir,'target.mjs'),'original');git('add','.');
 git('-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','original');
 const base=git('rev-parse','HEAD');
 fs.writeFileSync(path.join(dir,'other.txt'),'unapproved change');git('add','.');
 git('-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','concurrent host change');
 fs.writeFileSync(path.join(dir,'target.mjs'),'candidate');
 assert.throws(()=>assertCcc1WorktreePatchScope(dir,'target.mjs',base),/SOURCE_COMMIT_DRIFT/);
});

test('signal, process error or absent exit code cannot be a reproduced RED baseline',()=>{
 const base={profile:'node-continuum',testProfile:true,passed:false,exitCode:1,
  tests:{executed:1,failed:1,evidenceComplete:true},
  isolation:{schema:'continuum-isolation-v1',image:'sha256:'+'a'.repeat(64),network:'none',
   user:'1000:1000',readOnly:true,noNewPrivileges:true,capabilities:[],memoryMb:512,pids:32,cpus:1,
   timeoutMs:1000,profile:'node-continuum',sourceSha256:'b'.repeat(64),untrustedReports:true}};
 assert.equal(assertCcc1RunMeasurement({results:[base]}),base);
 for(const change of [{exitCode:null},{exitCode:127},{signal:'SIGKILL'},{signal:'SIGINT'},
  {error:'ENOBUFS'},{error:'ENOMEM'},{timedOut:true}]){
  assert.throws(()=>assertCcc1RunMeasurement({results:[{...base,...change}]}),/TEST_RUN_INTERRUPTED/);
 }
});

test('interrupted process metadata survives ledger sanitization and cannot train a negative',()=>{
 for(const termination of [{signal:'SIGKILL'},{error:'ENOBUFS'},{exitCode:null},{exitCode:127}]){
  const report={base:'a'.repeat(40),chain:'node',outcome:'NO_VERIFIED_REPAIR',
   attempts:[{candidate:'c'.repeat(16),diagnostic:'d'.repeat(16),diagnosticKind:'TEST_FAILURE',strategy:'REFERENCE_REPAIR',
    outcome:'FAILED_TESTS',edits:[],tests:[{profile:'node-continuum',passed:false,...termination,
     tests:{total:2,executed:2,failed:1,skipped:0,evidenceComplete:true}}]}]};
  const ledger=recordSession(emptyLedger(),report);
  assert.deepEqual(ledger.experiences,[],JSON.stringify(termination));
  for(const [key,value] of Object.entries(termination))assert.equal(ledger.attempts[0].tests[0][key],value);
 }
});
