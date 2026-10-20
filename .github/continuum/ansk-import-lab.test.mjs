import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {runAnskImportLab} from './ansk-import-lab.mjs';
import {assertAnskSource} from './ansk-lab-contract.mjs';
import {acquireContinuumBuildLease} from './shared-build-lease.mjs';

const target='app/src/main/java/at/heinz/deviceai/learning/MutationProbe.kt';
const importFqn='java.nio.file.Files';
const script=fileURLToPath(new URL('./ansk-import-lab.mjs',import.meta.url));

function runGit(root,...args){
 const r=spawnSync('git',args,{cwd:root,encoding:'utf8',shell:false});
 if(r.status!==0)throw Error('Fixture git '+args[0]+': '+r.stderr);
 return r.stdout.trim();
}
function scratchRepo(fn){
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-ansk-safety-'));
 try{
  runGit(root,'init','-b','main','-q');
  runGit(root,'config','user.name','ANSK Test');
  runGit(root,'config','user.email','ansk@example.invalid');
  fs.writeFileSync(path.join(root,'README.md'),'ANSK git fixture\n');
  runGit(root,'add','README.md');
  runGit(root,'commit','-m','init fixture','-q');
  return fn(root);
 }finally{
  fs.rmSync(root,{recursive:true,force:true});
 }
}

test('lab rejects traversal and foreign file types before reading a Git checkout',()=>{
 const files=['../outside.kt','/tmp/outside.kt',
  'app/src/test/java/at/heinz/Bad.kt','.github/continuum/internal.mjs'];
 for(const sourceFile of files){
  assert.throws(()=>runAnskImportLab({root:'/unavailable',sourceFile,importFqn}),
   /ANSK_INVALID_KOTLIN_IMPORT_TARGET/);
 }
});

test('lab refuses to operate on production main even when git tree is clean',()=>{
 scratchRepo(root=>{
  assert.throws(()=>runAnskImportLab({root,sourceFile:target,importFqn}),
   /ANSK_REQUIRES_EXPERIMENTAL_BRANCH/);
  assert.equal(runGit(root,'status','--porcelain'),'');
  assert.equal(runGit(root,'symbolic-ref','--short','HEAD'),'main');
 });
});

test('lab refuses dirty checkout in an experimental branch and does not alter it',()=>{
 scratchRepo(root=>{
  runGit(root,'checkout','-b','astra/continuum-experiment-charter-20261019','-q');
  fs.appendFileSync(path.join(root,'README.md'),'uncommitted owner edits\n');
  const before=fs.readFileSync(path.join(root,'README.md'),'utf8');
  assert.throws(()=>runAnskImportLab({root,sourceFile:target,importFqn}),
   /ANSK_SOURCE_CHECKOUT_NOT_CLEAN/);
  assert.equal(fs.readFileSync(path.join(root,'README.md'),'utf8'),before);
 });
});

test('lab has a finite non-configurable research budget and no automatic background loop',()=>{
 for(const invalid of [-1,0,10,60_000-1,45*60_000+1]){
  assert.throws(()=>runAnskImportLab({
   root:'/irrelevant',sourceFile:target,importFqn,deadlineMs:invalid
  }),/ANSK_INVALID_TIME_BUDGET/);
 }
 assert.doesNotThrow(()=>assertAnskSource(target,importFqn));
});

test('command-line execution rejects unknown operations before touching the checkout',()=>{
 const r=spawnSync(process.execPath,[script,'--loop','--file',target,'--import',importFqn],
   {cwd:os.tmpdir(),encoding:'utf8',timeout:5000});
 assert.equal(r.status,2);
 assert.match(r.stderr,/ANSK_USAGE_ONCE_ONLY/);
 const dup=spawnSync(process.execPath,[script,'--once','--file',target,'--file',target,'--import',importFqn],
   {cwd:os.tmpdir(),encoding:'utf8',timeout:5000});
 assert.equal(dup.status,2);
 assert.match(dup.stderr,/ANSK_UNSUPPORTED_OR_DUPLICATE_ARGUMENT/);
});

test('research module never installs APK, promotes model or registers generated tools',()=>{
 const text=fs.readFileSync(script,'utf8');
 assert.match(text,/authority:'SHADOW_ONLY'/);
 assert.doesNotMatch(text,/installPackage|merge_pull_request|trainSgdStep|promoteModel/);
 assert.match(text,/createWorktree\(checkout.root,head\)/);
 assert.match(text,/forceRerun:true/g);
 assert.match(text,/modelTrainingAuthorized:false/);
});

test('lab cannot start a worktree or Gradle chain while another local worker holds the shared lease',()=>{
 scratchRepo(root=>{
  runGit(root,'checkout','-b','astra/continuum-experiment-charter-20261019','-q');
  const release=acquireContinuumBuildLease(root);
  try{
   assert.throws(()=>runAnskImportLab({root,sourceFile:target,importFqn}),
    /CONTINUUM_BUILD_LEASE_BUSY/);
   assert.equal(runGit(root,'status','--porcelain'),'');
  }finally{release();}
 });
});
