import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {executeOwnerRepairTask,validateOwnerRepairTask} from './backlog-repair-worker.mjs';
import {BacklogStore,emptyDocument,applyOps,nextEligible} from './development-backlog.mjs';

const nested=Boolean(process.env.CONTINUUM_REPAIR_DEPTH);
function fixture(broken){
 const base=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-owner-task-'));
 const root=path.join(base,'checkout'),state=path.join(base,'state');
 const dir=path.join(root,'.github','continuum');
 fs.mkdirSync(dir,{recursive:true});fs.mkdirSync(state);
 fs.writeFileSync(path.join(dir,'math.mjs'),
  'export function square(n){return n*n}\n'+
  'export function quad(n){return '+(broken?'sqare':'square')+'(square(n))}\n');
 fs.writeFileSync(path.join(dir,'math.test.mjs'),
  "import test from 'node:test';\nimport assert from 'node:assert/strict';\n"+
  "import {quad} from './math.mjs';\ntest('quad works',()=>assert.equal(quad(2),16));\n");
 const git=(...args)=>{
  const out=spawnSync('git',args,{cwd:root,encoding:'utf8',timeout:20000});
  assert.equal(out.status,0,out.stderr);return out.stdout.trim();
 };
 git('init','-q');git('add','.');
 git('-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','baseline');
 return {base,root,state,git,head:git('rev-parse','HEAD')};
}
function accepted(store,f,base=f.head){
 store.apply([{op:'propose',actor:'owner',at:1,
  task:{id:'repair-owner-one',title:'Fix broken arithmetic',kind:'REPAIR',
   source:'owner',priority:900,subject:{base,chain:'node'}}},
  {op:'accept',actor:'owner',at:2,id:'repair-owner-one',reason:'explicit local authorization'}]);
}


test('an unrelated automatic REPAIR task cannot starve an accepted owner task',()=>{
 const tasks=[
  {id:'repair-auto-one',title:'Automatic incident',kind:'REPAIR',
   source:'repair-loop',priority:999},
  {id:'repair-owner-one',title:'Owner command',kind:'REPAIR',
   source:'owner',priority:600}
 ];
 const ops=tasks.flatMap(task=>[
  {op:'propose',actor:'owner',at:1,task},
  {op:'accept',actor:'owner',at:2,id:task.id}
 ]);
 const doc=applyOps(emptyDocument(0,'owner'),ops).doc;
 assert.equal(nextEligible(doc,['REPAIR']).id,'repair-auto-one');
 assert.equal(nextEligible(doc,['REPAIR'],['owner']).id,'repair-owner-one');
});

test('owner task executes red -> isolated repair -> independent green -> reviewable PATCH and DONE',
 {skip:nested,timeout:180000},()=>{
 const f=fixture(true);
 try{
  const file=path.join(f.state,'development-backlog.json');
  const store=new BacklogStore(file,{actor:'owner'});accepted(store,f);
  const original=fs.readFileSync(path.join(f.root,'.github/continuum/math.mjs'));
  const result=executeOwnerRepairTask({root:f.root,file,out:path.join(f.state,'reports'),budgetMinutes:2});
  assert.equal(result.status,'DONE');
  assert.equal(result.approvedForMerge,false);
  assert.equal(result.head,f.head);
  assert.ok(fs.existsSync(result.patch));
  const patch=fs.readFileSync(result.patch);
  assert.equal(crypto.createHash('sha256').update(patch).digest('hex'),result.patchSha256);
  assert.deepEqual(fs.readFileSync(path.join(f.root,'.github/continuum/math.mjs')),original);
  assert.equal(f.git('status','--porcelain'),'');
  const p=spawnSync('git',['apply','--check',result.patch],{cwd:f.root,encoding:'utf8'});
  assert.equal(p.status,0,p.stderr);
  const final=store.read().doc.tasks.find(t=>t.id==='repair-owner-one');
  assert.equal(final.state,'DONE');
  assert.ok(final.evidence.some(e=>e.profiles.some(p=>p.executed>0&&p.passed)));
  const baseline=JSON.parse(fs.readFileSync(result.report,'utf8'));
  assert.equal(baseline.baseline.passed,false);
  assert.equal(baseline.outcome,'VERIFIED');
  assert.equal(executeOwnerRepairTask({root:f.root,file,out:path.join(f.state,'reports')}).status,'NO_READY_TASK');
 }finally{fs.rmSync(f.base,{recursive:true,force:true});}
});

test('passing fixture is not reported as successful code development',
 {skip:nested,timeout:180000},()=>{
 const f=fixture(false);
 try{
  const file=path.join(f.state,'development-backlog.json');
  const store=new BacklogStore(file,{actor:'owner'});accepted(store,f);
  const result=executeOwnerRepairTask({root:f.root,file,out:path.join(f.state,'reports'),budgetMinutes:2});
  assert.equal(result.status,'FAILED');assert.equal(result.reason,'NO_FAILURE');
  assert.equal(store.read().doc.tasks[0].state,'FAILED');
  assert.equal(f.git('status','--porcelain'),'');
 }finally{fs.rmSync(f.base,{recursive:true,force:true});}
});

test('unapproved task, incorrect HEAD, foreign source and unknown chain fail closed',
 {skip:nested,timeout:60000},()=>{
 const f=fixture(true);
 try{
  const file=path.join(f.state,'development-backlog.json');
  const store=new BacklogStore(file,{actor:'owner'});
  store.apply([{op:'propose',actor:'owner',at:1,task:{id:'repair-owner-one',title:'X',
   kind:'REPAIR',source:'owner',subject:{base:f.head,chain:'node'}}}]);
  assert.equal(executeOwnerRepairTask({root:f.root,file,out:path.join(f.state,'reports')}).status,'NO_READY_TASK');
  store.apply([{op:'accept',actor:'owner',at:2,id:'repair-owner-one'}]);
  assert.throws(()=>executeOwnerRepairTask({root:f.root,file,out:path.join(f.root,'reports')}),/outside/);
  assert.throws(()=>validateOwnerRepairTask({...store.read().doc.tasks[0],source:'cli'},f.head),/owner-accepted/);
  assert.throws(()=>validateOwnerRepairTask({...store.read().doc.tasks[0],subject:{base:f.head,chain:'evil'}},f.head),/owner-accepted/);
  assert.throws(()=>validateOwnerRepairTask(store.read().doc.tasks[0],'f'.repeat(40)),/owner-accepted/);
  assert.equal(store.read().doc.tasks[0].state,'READY');
  assert.equal(f.git('status','--porcelain'),'');
 }finally{fs.rmSync(f.base,{recursive:true,force:true});}
});

test('corrupted backlog never executes repair, and original Git checkout remains intact',
 {skip:nested,timeout:60000},()=>{
 const f=fixture(true);
 try{
  const file=path.join(f.state,'development-backlog.json');
  fs.writeFileSync(file,'{"schema":"continuum-development-backlog","forged":true}');
  assert.throws(()=>executeOwnerRepairTask({root:f.root,file,out:path.join(f.state,'reports')}));
  assert.equal(f.git('status','--porcelain'),'');
  assert.deepEqual(fs.readdirSync(f.state).sort(),['development-backlog.json']);
 }finally{fs.rmSync(f.base,{recursive:true,force:true});}
});
