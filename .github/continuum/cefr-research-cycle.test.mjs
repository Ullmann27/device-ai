import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {runCefrResearchCycle} from './cefr-research-cycle.mjs';

const nested=Boolean(process.env.CONTINUUM_REPAIR_DEPTH);
const sha256=x=>crypto.createHash('sha256').update(x).digest('hex');
const holdout=[
 {query:'Module reference error in JavaScript',correctIndex:0,
  candidates:['restore known module export','remove failing test assertion']},
 {query:'Android Kotlin unresolved symbol error',correctIndex:1,
  candidates:['silence diagnostic and continue','add verified import for correct symbol']},
 {query:'Node test unexpectedly throws TypeError',correctIndex:0,
  candidates:['fix actual null dereference','remove the test directory']},
 {query:'Gradle dependency cannot be resolved',correctIndex:1,
  candidates:['disable the compile task','resolve pinned dependency version']}
];
function git(root,args){
 const r=spawnSync('git',args,{cwd:root,encoding:'utf8',timeout:12000});
 assert.equal(r.status,0,r.stderr);
 return r.stdout;
}
function fixture(){
 const base=fs.mkdtempSync(path.join(os.tmpdir(),'cefr-cycle-fixture-'));
 const repoRoot=path.join(base,'repo'),stateDir=path.join(base,'state');
 fs.mkdirSync(repoRoot);fs.mkdirSync(stateDir);
 const source=path.join(repoRoot,'backend/src/calc.mjs');
 fs.mkdirSync(path.dirname(source),{recursive:true});
 fs.mkdirSync(path.join(repoRoot,'.github/continuum'),{recursive:true});
 fs.writeFileSync(source,'export const value=()=>1;\n');
 fs.writeFileSync(path.join(repoRoot,'.github/continuum/calc.test.mjs'),
  "import test from 'node:test';\nimport assert from 'node:assert/strict';\n"+
  "import {value} from '../../backend/src/calc.mjs';\n"+
  "test('expected numeric value',()=>assert.equal(value(),2));\n");
 git(repoRoot,['init','-q']);git(repoRoot,['add','.']);
 git(repoRoot,['-c','user.name=Fixture','-c','user.email=fixture@example.invalid',
  'commit','-qm','initial']);
 const commit=git(repoRoot,['rev-parse','HEAD']).trim();
 fs.writeFileSync(source,'export const value=()=>2;\n');
 const patchText=git(repoRoot,['diff','--binary',commit]);
 git(repoRoot,['checkout','--','backend/src/calc.mjs']);
 const fingerprint='aabbccdd8899';
 const success={candidate:'c000d111',outcome:'VERIFIED',strategy:'JS_TYPO_REFERENCE',
  diagnostic:fingerprint,diagnosticKind:'REFERENCE_ERROR',description:'fix incorrect value',
  prior:0.6,edits:[{file:'backend/src/calc.mjs',
   oldText:'export const value=()=>1;',newText:'export const value=()=>2;'}],
  evidence:{sufficient:true,reasons:[]},
  tests:[{profile:'node-continuum',passed:true,testProfile:true,
   tests:{executed:1,failed:0,skipped:0}}]};
 const failed={candidate:'a000d222',outcome:'FAILED_TESTS',strategy:'JS_MISSING_IMPORT',
  diagnostic:fingerprint,diagnosticKind:'REFERENCE_ERROR',description:'import nonexistent function',
  tests:[{profile:'node-continuum',passed:false,testProfile:true,
   tests:{executed:1,failed:1}}]};
 const report={schemaVersion:1,base:commit,chain:'node',outcome:'VERIFIED',
  baseline:{passed:false,diagnostics:[{fingerprint,kind:'REFERENCE_ERROR',
   symbol:'value',file:'backend/src/calc.mjs',message:'value returns wrong result'}],
   results:[{profile:'node-continuum',passed:false,testProfile:true,
    tests:{executed:1,failed:1,skipped:0,passed:0}}]},
  attempts:[failed,success],verified:{candidate:success.candidate,strategy:success.strategy,
   description:success.description,files:['backend/src/calc.mjs'],
   tests:success.tests,patch:patchText,patchSha256:sha256(patchText)}};
 return {base,repoRoot,stateDir,report,patchText,dispose:()=>
  fs.rmSync(base,{recursive:true,force:true})};
}

test('research cycle verifies actual failing and repaired checkouts but defaults to no promotion',
 {skip:nested,timeout:60000},()=>{
 const f=fixture();
 try{
  const result=runCefrResearchCycle({...f,holdoutCases:holdout,
   deadline:Date.now()+35000});
  assert.equal(result.independent.passed,true);
  assert.equal(result.promoted,false);
  assert.equal(result.modelFile,'cre1-dev-repair-v1.cre1');
  assert.equal(git(f.repoRoot,['status','--porcelain']),'');
  assert.equal(git(f.repoRoot,['rev-parse','HEAD']).trim(),f.report.base);
  assert.equal(fs.existsSync(path.join(f.stateDir,'dev-cre1','cre1-dev-repair-v1.cre1')),false);
 }finally{f.dispose();}
});

test('a missing holdout does not even open an independent repair worktree',
 {skip:nested},()=>{
 const f=fixture();
 try{
  assert.throws(()=>runCefrResearchCycle({...f,holdoutCases:[],
   deadline:Date.now()+30000,promote:true}),/INVALID_HOLDOUT_CORPUS/);
  assert.equal(fs.readdirSync(f.stateDir).length,0);
 }finally{f.dispose();}
});

test('a forged patch hash cannot trigger neural promotion',
 {skip:nested,timeout:30000},()=>{
 const f=fixture();
 try{
  assert.throws(()=>runCefrResearchCycle({...f,holdoutCases:holdout,
   patchText:f.patchText+'forged',deadline:Date.now()+30000,promote:true}),/Patch hash/);
  assert.equal(fs.existsSync(path.join(f.stateDir,'dev-cre1','cre1-dev-repair-v1.cre1')),false);
 }finally{f.dispose();}
});
