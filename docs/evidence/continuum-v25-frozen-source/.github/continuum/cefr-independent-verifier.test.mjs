import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {verifyIndependentCefrPatch} from './cefr-independent-verifier.mjs';
import {validateVerifiedReport} from './repair-publish.mjs';

const nested=Boolean(process.env.CONTINUUM_REPAIR_DEPTH);
const sha256=x=>crypto.createHash('sha256').update(x).digest('hex');
function git(cwd,args){
 const r=spawnSync('git',args,{cwd,encoding:'utf8',timeout:12000});
 assert.equal(r.status,0,r.stderr);
 return r.stdout;
}

function fixture(healthy=false){
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'cefr-independent-fixture-'));
 fs.mkdirSync(path.join(root,'backend/src'),{recursive:true});
 fs.mkdirSync(path.join(root,'.github/continuum'),{recursive:true});
 const source=path.join(root,'backend/src/calc.mjs');
 fs.writeFileSync(source,'export const calculate = () => '+(healthy?'2':'1')+';\n');
 fs.writeFileSync(path.join(root,'.github/continuum/calc.test.mjs'),
  "import test from 'node:test';\nimport assert from 'node:assert/strict';\n"+
  "import {calculate} from '../../backend/src/calc.mjs';\n"+
  "test('expected arithmetic',()=>assert.equal(calculate(),2));\n");
 git(root,['init','-q']);git(root,['add','.']);
 git(root,['-c','user.name=CEFR Fixture','-c','user.email=cefr@example.invalid',
  'commit','-qm','baseline']);
 const base=git(root,['rev-parse','HEAD']).trim();
 fs.writeFileSync(source,'export const calculate = () => '+(healthy?'3':'2')+';\n');
 const patchText=git(root,['diff','--binary',base]);
 git(root,['checkout','--','backend/src/calc.mjs']);
 const diagnostic='0123456789abcdef';
 const edits=[{file:'backend/src/calc.mjs',oldText:'export const calculate = () => 1;',
  newText:'export const calculate = () => 2;'}];
 const success={candidate:'aabbccddeeff',strategy:'JS_TYPO_REFERENCE',
  diagnostic,diagnosticKind:'REFERENCE_ERROR',description:'repair incorrect calculation',
  score:0.6,edits,outcome:'VERIFIED',
  evidence:{sufficient:true,reasons:[]},
  tests:[{profile:'node-continuum',passed:true,testProfile:true,
   tests:{executed:1,failed:0,skipped:0}}]};
 const negative={candidate:'fa11ed000001',strategy:'JS_TYPO_REFERENCE',
  diagnostic,diagnosticKind:'REFERENCE_ERROR',description:'wrong numeric value',
  edits:[{file:'backend/src/calc.mjs',oldText:'export const calculate = () => 1;',
   newText:'export const calculate = () => 0;'}],
  outcome:'FAILED_TESTS',
  tests:[{profile:'node-continuum',passed:false,testProfile:true,
   tests:{executed:1,failed:1,skipped:0}}]};
 const report={schemaVersion:1,base,chain:'node',outcome:'VERIFIED',
  baseline:{passed:false,diagnostics:[{fingerprint:diagnostic,kind:'REFERENCE_ERROR',
   message:'incorrect calculation',file:'backend/src/calc.mjs'}],
   results:[{profile:'node-continuum',passed:false,testProfile:true,
    tests:{executed:1,failed:1,skipped:0,passed:0}}]},
  attempts:[negative,success],verified:{candidate:success.candidate,strategy:success.strategy,
   files:['backend/src/calc.mjs'],patch:patchText,patchSha256:sha256(patchText),
   description:success.description,tests:success.tests}};
 validateVerifiedReport(report,patchText);
 return {root,base,report,patchText,clean:()=>fs.rmSync(root,{recursive:true,force:true})};
}

test('independent CEFR execution reproduces failure, applies patch, runs actual tests',
 {skip:nested,timeout:60000},()=>{
 const f=fixture();
 try{
  const evidence=verifyIndependentCefrPatch({root:f.root,report:f.report,
   patchText:f.patchText,deadline:Date.now()+30000});
  assert.equal(evidence.passed,true);
  assert.equal(evidence.base,f.base);
  assert.equal(evidence.patchSha256,sha256(f.patchText));
  assert.equal(evidence.profiles.length,1);
  assert.equal(evidence.profiles[0].profile,'node-continuum');
  assert.ok(evidence.profiles[0].executed>=1);
  assert.deepEqual(evidence.verifiedNegatives,['fa11ed000001']);
  assert.equal(git(f.root,['rev-parse','HEAD']).trim(),f.base);
  assert.equal(git(f.root,['status','--porcelain']),'');
 }finally{f.clean();}
});

test('a clean passing base cannot be called an independently reproduced repair',
 {skip:nested,timeout:60000},()=>{
 const f=fixture(true);
 try{
  assert.throws(()=>verifyIndependentCefrPatch({root:f.root,report:f.report,
   patchText:f.patchText,deadline:Date.now()+30000}),/BASELINE_FAILURE_NOT_REPRODUCED/);
  assert.equal(git(f.root,['status','--porcelain']),'');
 }finally{f.clean();}
});

test('modified patch bytes cannot authorize even the first test process',
 {skip:nested,timeout:30000},()=>{
 const f=fixture();
 try{
  assert.throws(()=>verifyIndependentCefrPatch({root:f.root,report:f.report,
   patchText:f.patchText+'#tampered\n',deadline:Date.now()+30000}),/Patch hash/);
  assert.equal(git(f.root,['status','--porcelain']),'');
 }finally{f.clean();}
});

test('a dirty source checkout or mismatched HEAD is rejected before execution',
 {skip:nested,timeout:30000},()=>{
 const f=fixture();
 try{
  fs.writeFileSync(path.join(f.root,'untracked'),'dirty');
  assert.throws(()=>verifyIndependentCefrPatch({root:f.root,report:f.report,
   patchText:f.patchText,deadline:Date.now()+30000}),/DIRTY_WORKTREE/);
  fs.unlinkSync(path.join(f.root,'untracked'));
  const forgery=structuredClone(f.report);
  forgery.base='a'.repeat(40);
  assert.throws(()=>verifyIndependentCefrPatch({root:f.root,report:forgery,
   patchText:f.patchText,deadline:Date.now()+30000}),/BASE_HEAD_MISMATCH/);
 }finally{f.clean();}
});

test('deadline and untrusted checkout paths are never accepted',
 {skip:nested},()=>{
 const f=fixture();
 try{
  assert.throws(()=>verifyIndependentCefrPatch({root:'relative',report:f.report,
   patchText:f.patchText,deadline:Date.now()+30000}),/INVALID_CEFR_EXECUTION_REQUEST/);
  assert.throws(()=>verifyIndependentCefrPatch({root:f.root,report:f.report,
   patchText:f.patchText,deadline:Date.now()-10}),/INVALID_CEFR_EXECUTION_REQUEST/);
 }finally{f.clean();}
});
