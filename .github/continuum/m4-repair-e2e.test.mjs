import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {runRepairE2e,verifyRepairE2eRecord,MANDATORY_REPAIR_CASES} from './m4-repair-e2e.mjs';

const nested=Boolean(process.env.CONTINUUM_REPAIR_DEPTH);

function mockCheckout(){
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-e2e-fixture-'));
 const evidence=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-e2e-evidence-'));
 const tests=path.join(root,'.github','continuum');
 fs.mkdirSync(tests,{recursive:true});
 const cases=MANDATORY_REPAIR_CASES.map(name=>'test('+JSON.stringify(name)+',()=>{});');
 fs.writeFileSync(path.join(tests,'repair-loop.test.mjs'),
  ["import test from 'node:test';","import assert from 'node:assert/strict';",
   "test('repair subprocess depth is absent',()=>assert.equal(process.env.CONTINUUM_REPAIR_DEPTH,undefined));",
   ...cases].join('\n')+'\n');
 fs.writeFileSync(path.join(tests,'repair-cycle-safety.integration.test.mjs'),
  "import test from 'node:test';\ntest('cycle finalized',()=>{});\n");
 return {root,evidence,clean(){
  fs.rmSync(root,{recursive:true,force:true});
  fs.rmSync(evidence,{recursive:true,force:true});
 }};
}

test('mandatory E2E cases execute in a separate Node process and produce a tamper-evident proof',{skip:nested},()=>{
 const f=mockCheckout();
 try{
  const report=runRepairE2e(f.root,f.evidence,Date.now()+15000);
  assert.equal(report.passed,true);
  assert.ok(report.executed>=4);
  assert.equal(report.mandatoryRepairs.length,3);
  assert.equal(verifyRepairE2eRecord(f.evidence,report),true);
  fs.appendFileSync(path.join(f.evidence,'repair-e2e-junit.xml'),'\n');
  assert.equal(verifyRepairE2eRecord(f.evidence,report),false);
 }finally{f.clean();}
});

test('missing, skipped or renamed mandatory E2E cases fail closed',{skip:nested},()=>{
 for(const mutation of ['removed','skipped','renamed']){
  const f=mockCheckout();
  try{
   const file=path.join(f.root,'.github/continuum/repair-loop.test.mjs');
   const original=fs.readFileSync(file,'utf8');
   const first='test('+JSON.stringify(MANDATORY_REPAIR_CASES[0])+',()=>{});';
   let changed;
   if(mutation==='removed')changed=original.replace(first,'');
   else if(mutation==='skipped')changed=original.replace(first,'test.skip('+JSON.stringify(MANDATORY_REPAIR_CASES[0])+',()=>{});');
   else changed=original.replace(MANDATORY_REPAIR_CASES[0],'not an actual mandatory repair');
   fs.writeFileSync(file,changed);
   assert.throws(()=>runRepairE2e(f.root,f.evidence,Date.now()+15000),
    /missing|skipped|mandatory/i,mutation);
  }finally{f.clean();}
 }
});

test('nested repair test processes cannot reset recursion depth',()=>{
 const f=mockCheckout();
 const original=process.env.CONTINUUM_REPAIR_DEPTH;
 try{
  process.env.CONTINUUM_REPAIR_DEPTH='1';
  assert.throws(()=>runRepairE2e(f.root,f.evidence,Date.now()+15000),/nested|depth/i);
 }finally{
  if(original===undefined)delete process.env.CONTINUUM_REPAIR_DEPTH;
  else process.env.CONTINUUM_REPAIR_DEPTH=original;
  f.clean();
 }
});

test('symlinked proof and forged result counters are rejected',{skip:nested},()=>{
 const f=mockCheckout();
 try{
  const report=runRepairE2e(f.root,f.evidence,Date.now()+15000);
  const proof=path.join(f.evidence,'repair-e2e-junit.xml');
  fs.renameSync(proof,proof+'.original');
  fs.symlinkSync(proof+'.original',proof);
  assert.equal(verifyRepairE2eRecord(f.evidence,report),false);
  fs.rmSync(proof);
  fs.renameSync(proof+'.original',proof);
  assert.equal(verifyRepairE2eRecord(f.evidence,{...report,sha256:'0'.repeat(64)}),false);
  assert.equal(verifyRepairE2eRecord(f.evidence,{...report,executed:999999}),false);
 }finally{f.clean();}
});
