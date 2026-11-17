import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {createWorktree,removeWorktree,repairSession} from './repair-loop.mjs';

function git(root,...args){
 const result=spawnSync('git',args,{cwd:root,encoding:'utf8'});
 assert.equal(result.status,0,result.stderr);
 return result.stdout.trim();
}

function fixture(testContent){
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-repair-finalization-'));
 const out=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-repair-receipt-'));
 const source=path.join(root,'.github','continuum','example.test.mjs');
 fs.mkdirSync(path.dirname(source),{recursive:true});
 fs.writeFileSync(source,testContent);
 git(root,'init','-q');
 git(root,'add','.');
 git(root,'-c','user.name=Continuum Tests','-c','user.email=continuum@example.invalid',
  'commit','-qm','fixture');
 const head=git(root,'rev-parse','HEAD');
 return {root,out,head,clean:()=>{
  fs.rmSync(root,{recursive:true,force:true});
  fs.rmSync(out,{recursive:true,force:true});
 }};
}

const nested=Boolean(process.env.CONTINUUM_REPAIR_DEPTH);
test('actual repairSession finalizes both clean and non-actionable baselines',{skip:nested},()=>{
 for(const [content,outcome] of [
  ["import test from 'node:test';\ntest('passes',()=>{});\n",'NO_FAILURE'],
  ["import 'node:fs';\n",'NO_CODE_DIAGNOSTIC']
 ]){
  const f=fixture(content);
  let wt;
  try{
   wt=createWorktree(f.root,f.head);
   const report=repairSession({wt,base:f.head,chain:'node',out:f.out});
   assert.equal(report.outcome,outcome);
   assert.match(report.finishedAt,/^\d{4}-\d{2}-\d{2}T/);
   assert.ok(Date.parse(report.finishedAt)>=Date.parse(report.startedAt));
   assert.equal(report.verified,null);
  }finally{
   if(wt)removeWorktree(f.root,wt);
   f.clean();
  }
 }
});

test('repair module rejects poisoned nested-depth environment before session starts',()=>{
 const src=`import {repairSession} from ${JSON.stringify(new URL('./repair-loop.mjs',import.meta.url).href)}; repairSession({});`;
 for(const depth of ['-1','NaN','1e100']){
  const result=spawnSync(process.execPath,['--input-type=module','-e',src],{
   encoding:'utf8',timeout:10_000,env:{...process.env,CONTINUUM_REPAIR_DEPTH:depth}
  });
  assert.equal(result.status,1,result.stderr);
  assert.match(result.stderr,/Invalid CONTINUUM_REPAIR_DEPTH/);
 }
});
