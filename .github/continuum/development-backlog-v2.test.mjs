import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import * as b from './development-backlog.mjs';
import {command} from './developer-agent.mjs';
const temp=()=>fs.mkdtempSync(path.join(os.tmpdir(),'backlog-v2-'));

test('task revisions and direct parent digest permit sequential sync but reject divergent edits',()=>{
 const first=b.addTask(b.emptyBacklog(),{title:'Grüße 🧠',benefit:80});
 assert.equal(first.tasks[0].revision,1);
 const remote=structuredClone(first), id=first.tasks[0].id;
 b.changeStatus(remote,id,'BLOCKED',{note:'remote'});
 assert.equal(remote.tasks[0].revision,2);
 assert.equal(remote.tasks[0].parentHash,b.taskHash(first.tasks[0]));
 const merged=b.mergeBacklog(structuredClone(first),remote);
 assert.equal(merged.tasks[0].status,'BLOCKED');
 const local=structuredClone(first);b.prioritize(local,id,{benefit:99});
 assert.throws(()=>b.mergeBacklog(local,remote),/conflict/);
 const resolved=b.mergeBacklog(local,remote,{resolve:'incoming'});
 assert.equal(resolved.tasks[0].revision,3);
 assert.equal(resolved.tasks[0].status,'BLOCKED');
});
test('DONE cannot be set by status API or by an unrelated verified repair',()=>{
 const task=b.addTask(b.emptyBacklog(),{title:'New feature'}); const id=task.tasks[0].id;
 b.changeStatus(task,id,'CLAIMED',{owner:'test'});
 assert.throws(()=>b.changeStatus(task,id,'DONE'),/receipt|verification/);
 assert.throws(()=>b.completeRepair(task,id,{outcome:'VERIFIED'}),/goal|criteria/);
});
test('recovery preserves committed generation after crash, blocks claim and restores LKG only explicitly',()=>{
 const dir=temp(), file=path.join(dir,'backlog.json');
 try{
  let s=b.transaction(file,x=>b.addTask(x,{title:'Crash'}));
  s=b.transaction(file,x=>b.changeStatus(x,s.tasks[0].id,'CLAIMED',{owner:'dead'}));
  fs.writeFileSync(file+'.lock',JSON.stringify({pid:2147483647,host:os.hostname()}));
  assert.throws(()=>b.transaction(file,x=>x),/EEXIST|locked/);
  const recovered=b.recoverBacklog(file);
  assert.equal(recovered.tasks[0].status,'BLOCKED');
  assert.ok(recovered.generation>s.generation);
  fs.writeFileSync(file,'corrupt');
  assert.throws(()=>b.readBacklog(file));
  const restored=b.recoverBacklog(file);
  assert.equal(restored.tasks.length,1);
  assert.equal(restored.tasks[0].status,'BLOCKED');
  assert.ok(fs.readdirSync(dir).some(x=>x.includes('corrupt')));
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
});
test('recovery never steals a live lock and failed mutation preserves disk',()=>{
 const dir=temp(),file=path.join(dir,'b.json');try{
 b.transaction(file,x=>b.addTask(x,{title:'Keep'}));const before=fs.readFileSync(file,'utf8');
 fs.writeFileSync(file+'.lock',JSON.stringify({pid:process.pid,host:os.hostname()}));
 assert.throws(()=>b.recoverBacklog(file),/active|live/);
 fs.unlinkSync(file+'.lock');
 assert.throws(()=>b.transaction(file,x=>{x.tasks=[];throw Error('abort');}));
 assert.equal(fs.readFileSync(file,'utf8'),before);
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
});
test('CLI has real help, printed JSON, rejection/reopen, selection and exit codes',async()=>{
 const dir=temp();try{
 const cli=path.resolve('.github/continuum/developer-agent.mjs');
 const run=(...args)=>spawnSync(process.execPath,[cli,...args],{cwd:dir,encoding:'utf8'});
 assert.equal(run('--help').status,0);
 const created=run('backlog-add','--out','state','--title','CLI goal');assert.equal(created.status,0,created.stderr);
 const id=JSON.parse(created.stdout).tasks[0].id;
 assert.equal(run('backlog-select','--out','state','--id',id).status,0);
 assert.equal(run('backlog-reject','--out','state','--id',id).status,0);
 assert.equal(run('backlog-reopen','--out','state','--id',id).status,0);
 assert.equal(run('backlog-status','--out','state').status,0);
 assert.notEqual(run('backlog-update','--out','state','--id',id,'--status','DONE').status,0);
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
});

test('real CLI repair completes only the selected diagnostic goal and exports a correlated receipt',
 {skip:!!process.env.CONTINUUM_REPAIR_DEPTH}, async()=>{
 const dir=temp(),out=temp();const git=(...args)=>{const r=spawnSync('git',args,{cwd:dir,encoding:'utf8'});assert.equal(r.status,0,r.stderr);return r.stdout.trim();};
 try{
  fs.mkdirSync(path.join(dir,'.github/continuum'),{recursive:true});
  const source=path.join(dir,'.github/continuum/math.mjs');
  fs.writeFileSync(source,'export function square(x){return x*x;}\nexport function quad(x){return square(square(x));}\n');
  fs.writeFileSync(path.join(dir,'.github/continuum/math.test.mjs'),"import test from 'node:test';import assert from 'node:assert/strict';import {quad} from './math.mjs';test('quad',()=>assert.equal(quad(2),16));\n");
  git('init','-q');git('add','.');git('-c','user.name=t','-c','user.email=t@t','commit','-qm','clean');
  fs.writeFileSync(source,fs.readFileSync(source,'utf8').replace('return square(square','return sqare(square'));
  git('-c','user.name=t','-c','user.email=t@t','commit','-qam','fault');const base=git('rev-parse','HEAD');
  const {runChain}=await import('./repair-loop.mjs');const diagnostic=runChain('node',dir,out).diagnostics[0];
  const {backlogCommand}=await import('./development-backlog-cli.mjs');
  const context={root:dir,out,ledger:{attempts:[],experiences:[]},log:()=>{}};
  const added=await backlogCommand('backlog-add',{title:'Repair quad',kind:'TEST_FAILURE',file:diagnostic.file,base,fingerprint:diagnostic.fingerprint,chain:'node'},context);
  const id=added.tasks[0].id;
  await backlogCommand('backlog-claim',{id,owner:'tester',approve:'true'},context);
  const done=await backlogCommand('backlog-start',{id,owner:'tester',approve:'true',base,chain:'node'},context);
  assert.equal(done.task.status,'DONE');assert.equal(done.task.receipt.strategy,'JS_TYPO_REFERENCE');
  assert.equal(done.task.receipt.goalHash,b.goalHash(done.task));assert.equal(done.task.receipt.tests,1);
  assert.match(fs.readFileSync(source,'utf8'),/sqare/,'caller checkout untouched');
  assert.equal(git('status','--porcelain'),'');
  assert.equal(b.importBridge(b.exportBridge(b.readBacklog(path.join(out,'development-backlog.json')))).tasks[0].status,'DONE');
 }finally{fs.rmSync(dir,{recursive:true,force:true});fs.rmSync(out,{recursive:true,force:true});}
});
