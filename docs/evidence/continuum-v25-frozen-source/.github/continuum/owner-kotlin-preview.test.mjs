import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {BacklogStore} from './development-backlog.mjs';
import {previewOwnerKotlinImplement} from './owner-kotlin-preview.mjs';

const noKotlin=Boolean(process.env.CONTINUUM_REPAIR_DEPTH)||
 spawnSync('kotlinc',['-version'],{encoding:'utf8',timeout:15000}).status!==0;
const target='app/src/main/java/p/MathApi.kt';

function fixture(casesJson='[{"input":2,"expected":16},{"input":3,"expected":81}]'){
 const home=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-owner-kotlin-'));
 const root=path.join(home,'repo');
 const state=path.join(home,'state');
 const dir=path.join(root,'app/src/main/java/p');
 fs.mkdirSync(dir,{recursive:true});fs.mkdirSync(state);
 fs.writeFileSync(path.join(dir,'MathApi.kt'),
  'package p\nclass MathApi {\n    fun square(n: Int): Int = n * n\n}\n');
 const git=(...args)=>{
  const r=spawnSync('git',args,{cwd:root,encoding:'utf8',timeout:15000});
  assert.equal(r.status,0,r.stderr);return r.stdout.trim();
 };
 git('init','-q');git('add','.');
 git('-c','user.name=fixture','-c','user.email=fixture@example.invalid',
  'commit','-qm','baseline');
 const head=git('rev-parse','HEAD');
 const file=path.join(state,'backlog.json'),store=new BacklogStore(file,{actor:'owner'});
 store.apply([
  {op:'propose',actor:'owner',at:1,task:{
   id:'owner-delegate-one',title:'Compute a twice-squared value',
   kind:'IMPLEMENT',source:'owner',priority:800,maxAttempts:2,
   subject:{base:head,chain:'android',operation:'EXPOSE_DELEGATE',
    file:target,className:'MathApi',methodName:'squareTwice',
    delegateName:'square',parameterName:'value',type:'Int',
    depth:2,casesJson}
  }},
  {op:'accept',actor:'owner',at:2,id:'owner-delegate-one'}
 ]);
 return {home,root,state,file,store,head,git};
}
const invoke=f=>previewOwnerKotlinImplement({
 root:f.root,file:f.file,out:path.join(f.state,'reports'),id:'owner-delegate-one'
});

test('owner Kotlin IMPLEMENT generates real reviewable Git patch with two independent JVM oracles',
 {skip:noKotlin,timeout:240000},()=>{
 const f=fixture();
 try{
  const original=fs.readFileSync(path.join(f.root,target),'utf8');
  const result=invoke(f);
  assert.equal(result.status,'VERIFIED_KOTLIN_PREVIEW');
  assert.equal(result.head,f.head);
  assert.equal(result.approvedForMerge,false);
  assert.equal(result.androidBuildVerified,false);
  assert.equal(result.backlogDone,false);
  const bytes=fs.readFileSync(result.patch);
  assert.equal(crypto.createHash('sha256').update(bytes).digest('hex'),result.patchSha256);
  const receipt=JSON.parse(fs.readFileSync(result.receipt,'utf8'));
  assert.equal(receipt.level,'KOTLIN_ORACLE_ONLY');
  assert.equal(receipt.first.passed,true);
  assert.equal(receipt.independent.passed,true);
  assert.equal(receipt.first.executed,2);
  assert.equal(receipt.independent.executed,2);
  assert.equal(receipt.backlogStateUnchanged,true);
  assert.equal(f.store.read().doc.tasks[0].state,'READY');
  assert.equal(f.git('status','--porcelain'),'');
  assert.equal(fs.readFileSync(path.join(f.root,target),'utf8'),original);
  const check=spawnSync('git',['apply','--check',result.patch],{
   cwd:f.root,encoding:'utf8',timeout:15000});
  assert.equal(check.status,0,check.stderr);
 }finally{fs.rmSync(f.home,{recursive:true,force:true});}
});

test('false owner output cannot create a verified Kotlin preview',
 {skip:noKotlin,timeout:180000},()=>{
 const f=fixture('[{"input":2,"expected":999}]');
 try{
  const result=invoke(f);
  assert.equal(result.status,'REJECTED');
  assert.match(result.reason,/OWNER_KOTLIN_ORACLE_FAILED/);
  assert.equal(f.store.read().doc.tasks[0].state,'READY');
  assert.equal(f.git('status','--porcelain'),'');
 }finally{fs.rmSync(f.home,{recursive:true,force:true});}
});

test('output symlink back into checkout is rejected before Kotlin compilation',
 {skip:Boolean(process.env.CONTINUUM_REPAIR_DEPTH)},()=>{
 const f=fixture();
 try{
  const alias=path.join(f.state,'back-to-repo');
  fs.symlinkSync(f.root,alias,'dir');
  assert.throws(()=>previewOwnerKotlinImplement({
    root:f.root,file:f.file,out:path.join(alias,'reports'),
    id:'owner-delegate-one'
  }),/external/);
  assert.equal(f.git('status','--porcelain'),'');
 }finally{fs.rmSync(f.home,{recursive:true,force:true});}
});
