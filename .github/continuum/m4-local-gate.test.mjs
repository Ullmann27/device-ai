import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {gateSummary,parseGateArgs,runM4Gate} from './m4-local-gate.mjs';

function fixture(broken=false){
 const base=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-gate-fixture-'));
 const root=path.join(base,'repo'),out=path.join(base,'private-proof');
 fs.mkdirSync(path.join(root,'.github/continuum'),{recursive:true});
 const source=path.join(root,'.github/continuum','math.test.mjs');
 fs.writeFileSync(source,[
  "import test from 'node:test';",
  "import assert from 'node:assert/strict';",
  "test('real local gate fixture',()=>assert.equal(2*3,"+(broken?'7':'6')+"));",
  ""
 ].join('\n'));
 const git=(...args)=>{
  const r=spawnSync('git',args,{cwd:root,encoding:'utf8',timeout:15000});
  assert.equal(r.status,0,r.stderr);
  return r.stdout.trim();
 };
 git('init','-q');
 git('add','.');
 git('-c','user.name=fixture','-c','user.email=fixture@example.invalid',
  'commit','-qm','fixed fixture');
 return {base,root,out,git,head:git('rev-parse','HEAD')};
}
const nested=Boolean(process.env.CONTINUUM_REPAIR_DEPTH);

test('gate cannot certify zero tests or failed APK as successful',()=>{
 const empty={passed:true,results:[{passed:true,testProfile:true,tests:{executed:0,failed:0}}]};
 assert.equal(gateSummary(empty,null,null,false).passed,false);
 const valid={passed:true,results:[{passed:true,testProfile:true,tests:{executed:4,failed:0}}]};
 assert.equal(gateSummary(valid,null,null,false).passed,true);
 assert.equal(gateSummary(valid,null,null,true).passed,false);
 assert.equal(gateSummary(valid,valid,{exitCode:1,timedOut:false},true).passed,false);
 assert.equal(gateSummary(valid,valid,{exitCode:0,timedOut:false},true).passed,true);
 assert.equal(gateSummary(valid,valid,{exitCode:0,timedOut:true},true).passed,false);
});

test('gate CLI rejects unknown arguments, duplicates, and invalid time budgets',()=>{
 assert.deepEqual(parseGateArgs(['--android','--budget-minutes','40']),
  {android:true,budgetMinutes:40});
 assert.throws(()=>parseGateArgs(['--budget-minutes','0']),/budget/i);
 assert.throws(()=>parseGateArgs(['--budget-minutes','hello']),/budget/i);
 assert.throws(()=>parseGateArgs(['--android','--android']),/duplicate/);
 assert.throws(()=>parseGateArgs(['--exec','rm']),/unknown/i);
});

test('real standalone Git fixture produces an immutable Node test proof',
 {skip:nested,timeout:180000},()=>{
 const f=fixture(false);
 try{
  const result=runM4Gate({root:f.root,out:f.out,budgetMinutes:2});
  assert.equal(result.head,f.head);
  assert.equal(result.summary.scope,'NODE_ONLY');
  assert.equal(result.summary.nodePassed,true);
  assert.ok(result.summary.executedNodeTests>=1);
  assert.equal(result.complete,true,JSON.stringify(result.errors));
  assert.ok(fs.existsSync(result.path));
  const report=JSON.parse(fs.readFileSync(result.path,'utf8'));
  assert.equal(report.head,f.head);
  assert.equal(report.reportSha256,result.reportSha256);
  assert.equal(f.git('status','--porcelain'),'');
  assert.equal(fs.existsSync(path.join(f.root,'gate-report.json')),false);
 }finally{fs.rmSync(f.base,{recursive:true,force:true});}
});

test('broken Git fixture stays red and still preserves a failure proof',
 {skip:nested,timeout:180000},()=>{
 const f=fixture(true);
 try{
  const result=runM4Gate({root:f.root,out:f.out,budgetMinutes:2});
  assert.equal(result.summary.scope,'NODE_ONLY');
  assert.equal(result.summary.nodePassed,false);
  assert.equal(result.complete,false);
  assert.ok(fs.existsSync(result.path));
  assert.equal(f.git('status','--porcelain'),'');
 }finally{fs.rmSync(f.base,{recursive:true,force:true});}
});

test('gate refuses dirty source and repository-contained proof output',
 {skip:nested,timeout:60000},()=>{
 const f=fixture(false);
 try{
  assert.throws(()=>runM4Gate({
   root:f.root,out:path.join(f.root,'private-proof')
  }),/outside/);
  fs.writeFileSync(path.join(f.root,'untracked.txt'),'dirty');
  assert.throws(()=>runM4Gate({root:f.root,out:f.out}),/dirty/);
 }finally{fs.rmSync(f.base,{recursive:true,force:true});}
});
