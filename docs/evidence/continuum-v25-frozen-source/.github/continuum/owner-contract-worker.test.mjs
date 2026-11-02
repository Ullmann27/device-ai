import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {BacklogStore} from './development-backlog.mjs';
import {executeOwnerContractTestTask,verifyContractTestEvidence} from './owner-contract-worker.mjs';

const nested=Boolean(process.env.CONTINUUM_REPAIR_DEPTH);
const TESTS=JSON.stringify([{args:[5],expected:15},{args:[8],expected:24}]);

function fixture(broken=false){
 const home=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-owner-test-'));
 const root=path.join(home,'repo'),external=path.join(home,'state');
 const dir=path.join(root,'.github','continuum');
 fs.mkdirSync(dir,{recursive:true});fs.mkdirSync(external);
 fs.writeFileSync(path.join(dir,'math-utils.mjs'),
  'export function triple(n) { return n * 3; }\n');
 fs.writeFileSync(path.join(dir,'math-utils.test.mjs'),
  "import test from 'node:test';\nimport assert from 'node:assert/strict';\n"+
  "import {triple} from './math-utils.mjs';\n"+
  "test('baseline',()=>assert.equal(triple(2),"+(broken?'7':'6')+"));\n");
 const git=(...args)=>{
  const r=spawnSync('git',args,{cwd:root,encoding:'utf8',timeout:15000});
  assert.equal(r.status,0,r.stderr);return r.stdout.trim();
 };
 git('init','-q');git('add','.');
 git('-c','user.name=fixture','-c','user.email=fixture@example.invalid',
  'commit','-qm','seed');
 return {home,root,external,git,head:git('rev-parse','HEAD')};
}
function accepted(f,casesJson=TESTS){
 const file=path.join(f.external,'development-backlog.json');
 const store=new BacklogStore(file,{actor:'owner'});
 store.apply([
  {op:'propose',actor:'owner',at:1,task:{
   id:'owner-node-contract',title:'Add regression contract for triple',kind:'TEST',
   source:'owner',priority:900,maxAttempts:3,
   subject:{base:f.head,chain:'node',operation:'ASSERT_NODE_EXPORT',
    modulePath:'.github/continuum/math-utils.mjs',
    exportName:'triple',casesJson}
  }},
  {op:'accept',actor:'owner',at:2,id:'owner-node-contract',
   reason:'owner approved only this bounded set of expected values'}
 ]);
 return {file,store};
}
function execute(f,file){
 return executeOwnerContractTestTask({
  root:f.root,file,out:path.join(f.external,'reports'),
  id:'owner-node-contract',budgetMinutes:2
 });
}

test('a complete M4 TEST task passes baseline, adds meaningful tests, independently checks patch and finishes DONE',
 {skip:nested,timeout:180000},()=>{
 const f=fixture();
 try{
  const {file,store}=accepted(f);
  const original=fs.readFileSync(path.join(f.root,'.github/continuum/math-utils.mjs'));
  const r=execute(f,file);
  assert.equal(r.status,'DONE',r.reason);
  assert.equal(r.approvedForMerge,false);
  assert.equal(r.taskId,'owner-node-contract');
  assert.equal(r.head,f.head);
  assert.ok(r.proof.baselineExecuted>0);
  assert.ok(r.proof.candidateExecuted>=r.proof.baselineExecuted+2);
  assert.ok(r.proof.independentExecuted>=r.proof.candidateExecuted);
  const patch=fs.readFileSync(r.patch);
  assert.equal(crypto.createHash('sha256').update(patch).digest('hex'),r.patchSha256);
  const receipt=JSON.parse(fs.readFileSync(r.receipt,'utf8'));
  assert.equal(receipt.passed,true);
  assert.equal(receipt.patchSha256,r.patchSha256);
  assert.equal(receipt.taskId,r.taskId);
  assert.equal(store.read().doc.tasks[0].state,'DONE');
  assert.ok(store.read().doc.tasks[0].evidence[0].profiles[0].executed>0);
  assert.deepEqual(fs.readFileSync(path.join(f.root,'.github/continuum/math-utils.mjs')),original);
  assert.equal(f.git('status','--porcelain'),'');
  assert.equal(fs.existsSync(path.join(f.root,'.github/continuum/math-utils.contract.test.mjs')),false);
  const check=spawnSync('git',['apply','--check',r.patch],{
   cwd:f.root,encoding:'utf8',timeout:10000});
  assert.equal(check.status,0,check.stderr);
  assert.equal(execute(f,file).status,'NO_READY_TASK');
 }finally{fs.rmSync(f.home,{recursive:true,force:true});}
});

test('a false owner-supplied expected value stays FAILED and never produces DONE',
 {skip:nested,timeout:180000},()=>{
 const f=fixture();
 try{
  const {file,store}=accepted(f,JSON.stringify([{args:[5],expected:999}]));
  const r=execute(f,file);
  assert.equal(r.status,'FAILED');
  assert.match(r.reason,/GENERATED_TESTS_FAILED/);
  assert.equal(store.read().doc.tasks[0].state,'READY');
  assert.equal(store.read().doc.tasks[0].attempts,1);
  assert.equal(f.git('status','--porcelain'),'');
  assert.equal(fs.existsSync(path.join(f.root,'.github/continuum/math-utils.contract.test.mjs')),false);
 }finally{fs.rmSync(f.home,{recursive:true,force:true});}
});

test('red baseline is not laundered into a DONE synthetic test job',
 {skip:nested,timeout:180000},()=>{
 const f=fixture(true);
 try{
  const {file,store}=accepted(f);
  const r=execute(f,file);
  assert.equal(r.status,'FAILED');
  assert.match(r.reason,/BASELINE_NOT_GREEN/);
  assert.equal(store.read().doc.tasks[0].state,'READY');
  assert.equal(f.git('status','--porcelain'),'');
 }finally{fs.rmSync(f.home,{recursive:true,force:true});}
});

test('synthetic fake test totals and non-increasing test counts cannot pass evidence gate',()=>{
 const run=(executed,passed=true,skipped=0)=>({passed,results:[{
  profile:'node-continuum',testProfile:true,passed,
  tests:{executed,failed:passed?0:1,skipped}
 }]});
 assert.equal(verifyContractTestEvidence(run(5),run(5),run(5),2).passed,false);
 assert.equal(verifyContractTestEvidence(run(5),run(7),run(6),2).passed,false);
 assert.equal(verifyContractTestEvidence(run(5),run(7),run(7,false),2).passed,false);
 assert.equal(verifyContractTestEvidence(run(5),run(7,true,1),run(7),2).passed,false);
 assert.equal(verifyContractTestEvidence(run(5),run(7),run(7),2).passed,true);
});

test('no unaccepted task is executed and paths cannot point back into source',
 {skip:nested,timeout:60000},()=>{
 const f=fixture();
 try{
  const file=path.join(f.external,'development-backlog.json');
  assert.equal(execute(f,file).status,'NO_READY_TASK');
  assert.throws(()=>executeOwnerContractTestTask({
   root:f.root,file,out:path.join(f.root,'out')
  }),/outside/);
  const alias=path.join(f.external,'alias');
  fs.symlinkSync(f.root,alias,'dir');
  assert.throws(()=>executeOwnerContractTestTask({
   root:f.root,file,out:path.join(alias,'out')
  }),/outside/);
  assert.equal(f.git('status','--porcelain'),'');
 }finally{fs.rmSync(f.home,{recursive:true,force:true});}
});
