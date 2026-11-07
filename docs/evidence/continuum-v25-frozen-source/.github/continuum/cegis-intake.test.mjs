import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {inspectCegisIntake} from './cegis-intake.mjs';

function git(root,...args){
 const r=spawnSync('git',args,{cwd:root,encoding:'utf8'});
 assert.equal(r.status,0,r.stderr);
 return r.stdout.trim();
}
function fixture(mutate=()=>{}){
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-cegis-intake-'));
 git(root,'init','-q');
 fs.mkdirSync(path.join(root,'.github/continuum'),{recursive:true});
 fs.writeFileSync(path.join(root,'.github/continuum/repair-loop.mjs'),'export const guard = true;\n');
 git(root,'add','.');
 git(root,'-c','user.name=Fixture','-c','user.email=fixture@example.invalid','commit','-qm','base');
 const base=git(root,'rev-parse','HEAD');
 for(let i=0;i<5;i++){
  fs.writeFileSync(path.join(root,'.github/continuum','sample-'+i+'.mjs'),'export const value = '+i+';\n');
  fs.writeFileSync(path.join(root,'.github/continuum','sample-'+i+'.test.mjs'),'import test from "node:test";\ntest("unit",()=>{});\n');
 }
 fs.mkdirSync(path.join(root,'docs'));
 fs.writeFileSync(path.join(root,'docs','CEGIS.md'),'Documentation\n');
 mutate(root);
 git(root,'add','.');
 git(root,'-c','user.name=Fixture','-c','user.email=fixture@example.invalid','commit','-qm','candidate');
 const candidate=git(root,'rev-parse','HEAD');
 git(root,'reset','--hard','-q',base);
 return {root,base,candidate,dispose:()=>fs.rmSync(root,{recursive:true,force:true})};
}

test('accepts Sol handoff shape but never executes source or updates Git HEAD',()=>{
 const x=fixture();try{
  const result=inspectCegisIntake(x.root,x.base,x.candidate);
  assert.equal(result.files.length,11);
  assert.equal(result.syntaxChecked,10);
  assert.equal(result.testsExecuted,0);
  assert.equal(result.approved,false);
  assert.equal(git(x.root,'rev-parse','HEAD'),x.base);
 }finally{x.dispose();}
});

test('rejects existing repair-core changes',()=>{
 const x=fixture(root=>fs.appendFileSync(path.join(root,'.github/continuum/repair-loop.mjs'),'// modified\n'));
 try{assert.throws(()=>inspectCegisIntake(x.root,x.base,x.candidate),/eleven|protected/)}finally{x.dispose();}
});

test('rejects symlink, executable modules and syntax errors',()=>{
 for(const reason of ['symlink','executable','syntax']){
  const x=fixture(root=>{
   const file=path.join(root,'.github/continuum/sample-0.mjs');
   if(reason==='symlink'){fs.unlinkSync(file);fs.symlinkSync('sample-1.mjs',file);}
   if(reason==='executable')fs.chmodSync(file,0o755);
   if(reason==='syntax')fs.writeFileSync(file,'export function (\n');
  });
  try{assert.throws(()=>inspectCegisIntake(x.root,x.base,x.candidate))}finally{x.dispose();}
 }
});

test('rejects unexpected file and unclean baseline',()=>{
 const x=fixture(root=>fs.writeFileSync(path.join(root,'bad.js'),'process.exit(1)'));
 try{assert.throws(()=>inspectCegisIntake(x.root,x.base,x.candidate),/eleven/)}finally{x.dispose();}
 const y=fixture();try{
  fs.writeFileSync(path.join(y.root,'untracked'),'dirty');
  assert.throws(()=>inspectCegisIntake(y.root,y.base,y.candidate),/clean/);
 }finally{y.dispose();}
});
