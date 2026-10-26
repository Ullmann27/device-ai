import test from 'node:test';
import assert from 'node:assert/strict';
import {parseJUnit} from './repair-diagnostics.mjs';
import {assertCcc1TestEvidenceParity} from './ccc1-independent-verifier.mjs';
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
