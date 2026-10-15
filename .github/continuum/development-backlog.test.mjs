import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {emptyBacklog,mergePlanner,addTask,changeStatus,transaction,readBacklog,listTasks} from './development-backlog.mjs';
import {command} from './developer-agent.mjs';
const directory=()=>fs.mkdtempSync(path.join(os.tmpdir(),'continuum-backlog-'));
test('planner merges actual kinds and preserves claimed status',()=>{
 let b=mergePlanner(emptyBacklog(),{tasks:[{kind:'DEAD_CLUSTER',symbol:'X',file:'x.kt'},{kind:'TEST_ONLY',symbol:'Y',file:'y.kt'}]});
 assert.equal(b.tasks.length,2);assert.equal(b.tasks[0].kind,'DEAD_CLUSTER');
 b=changeStatus(b,b.tasks[0].id,'CLAIMED',{owner:'test'});b=mergePlanner(b,{tasks:[{kind:'DEAD_CLUSTER',symbol:'X',file:'x.kt'}]});
 assert.equal(b.tasks[0].status,'CLAIMED');
});
test('manual task rejects duplicates',()=>{
 const t={title:'Add tests',file:'a.kt',benefit:90,risk:10,effort:20};const b=addTask(emptyBacklog(),t);
 assert.throws(()=>addTask(b,t),/exists/);
});
test('invalid transition and owner rejected',()=>{
 const b=addTask(emptyBacklog(),{title:'Task'});const id=b.tasks[0].id;
 assert.throws(()=>changeStatus(b,id,'CLAIMED'),/owner/);
 assert.throws(()=>changeStatus(b,id,'DONE'),/transition/);
});
test('atomic storage, checksum, and generation',()=>{
 const dir=directory();try{
 const f=path.join(dir,'state.json');const first=transaction(f,b=>addTask(b,{title:'Task'}));
 assert.equal(first.generation,1);assert.equal(readBacklog(f).tasks.length,1);
 assert.throws(()=>transaction(f,b=>b,{expectedGeneration:0}),/conflict/);
 fs.writeFileSync(f,fs.readFileSync(f,'utf8').replace('Task','Hacked'));
 assert.throws(()=>readBacklog(f),/checksum/);
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
});
test('lock fails closed without overwriting',()=>{
 const dir=directory();try{const f=path.join(dir,'state.json');fs.writeFileSync(f+'.lock','busy');
 assert.throws(()=>transaction(f,b=>b),/EEXIST/);
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
});
test('CLI persists tasks across invocations',async()=>{
 const dir=fs.mkdtempSync(path.join(process.cwd(),'continuum-backlog-cli-'));try{const opts={root:process.cwd(),log:()=>{}};
 const b=await command(['backlog-add','--out',path.basename(dir),'--title','CLI demo'],opts);
 assert.equal(b.tasks.length,1);
 assert.equal((await command(['backlog-list','--out',path.basename(dir)],opts)).length,1);
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
});

import {exportBridge,importBridge} from './development-backlog.mjs';
test('bridge validates round-trip and rejects corrupt byte payloads',()=>{
 const backlog=addTask(emptyBacklog(),{title:'Cross-runtime task',benefit:80,risk:15,effort:25});
 const bridge=exportBridge(backlog);
 assert.deepEqual(importBridge(bridge),backlog);
 const bad=JSON.parse(bridge);bad.sha256='f'.repeat(64);
 assert.throws(()=>importBridge(JSON.stringify(bad)),/checksum/);
 assert.throws(()=>importBridge('{"schemaVersion":2}'),/bridge/);
});
test('bridge CLI export and import preserve manually created task',async()=>{
 const dir=fs.mkdtempSync(path.join(process.cwd(),'continuum-backlog-bridge-'));
 const target=fs.mkdtempSync(path.join(process.cwd(),'continuum-backlog-bridge-target-'));
 try{
  const source=path.basename(dir), destination=path.basename(target);
  await command(['backlog-add','--out',source,'--title','Bridge CLI task'],{root:process.cwd(),log:()=>{}});
  const exp=await command(['backlog-export','--out',source],{root:process.cwd(),log:()=>{}});
  const imported=await command(['backlog-import','--out',destination,'--input',exp.destination],{root:process.cwd(),log:()=>{}});
  assert.equal(imported.tasks[0].title,'Bridge CLI task');
 }finally{fs.rmSync(dir,{recursive:true,force:true});fs.rmSync(target,{recursive:true,force:true});}
});

test('explicit recovery marks previously claimed tasks blocked for review',async()=>{
 const dir=fs.mkdtempSync(path.join(process.cwd(),'continuum-recovery-'));
 try{
  const out=path.basename(dir),opts={root:process.cwd(),log:()=>{}};
  const initial=await command(['backlog-add','--out',out,'--title','Interrupted task'],opts);
  await command(['backlog-claim','--out',out,'--approve','true','--owner','worker-1','--id',initial.tasks[0].id],opts);
  const recovered=await command(['backlog-recover','--out',out],opts);
  assert.equal(recovered.tasks[0].status,'BLOCKED');
  assert.match(recovered.tasks[0].notes[0],/previous claim/);
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
});
test('import rejects divergent task revisions instead of last-writer-wins',async()=>{
 const dir=fs.mkdtempSync(path.join(process.cwd(),'continuum-conflict-'));
 const other=fs.mkdtempSync(path.join(process.cwd(),'continuum-conflict-other-'));
 try {
  const opts={root:process.cwd(),log:()=>{}};
  const first=await command(['backlog-add','--out',path.basename(dir),'--title','Conflict example'],opts);
  const exported=await command(['backlog-export','--out',path.basename(dir)],opts);
  await command(['backlog-import','--out',path.basename(other),'--input',exported.destination],opts);
  await command(['backlog-claim','--out',path.basename(other),'--approve','true','--owner','reviewer','--id',first.tasks[0].id],opts);
  await command(['backlog-prioritize','--out',path.basename(dir),'--id',first.tasks[0].id,'--benefit','90'],opts);
  const otherExport=await command(['backlog-export','--out',path.basename(other)],opts);
  await assert.rejects(command(['backlog-import','--out',path.basename(dir),'--input',otherExport.destination],opts),/conflict/);
  assert.equal((await command(['backlog-list','--out',path.basename(dir)],opts))[0].status,'OPEN');
 } finally {fs.rmSync(dir,{recursive:true,force:true});fs.rmSync(other,{recursive:true,force:true});}
});
test('reimport of identical bridge does not duplicate tasks',async()=>{
 const dir=fs.mkdtempSync(path.join(process.cwd(),'continuum-idempotent-'));
 try {
  const opts={root:process.cwd(),log:()=>{}};
  await command(['backlog-add','--out',path.basename(dir),'--title','Repeat'],opts);
  const exported=await command(['backlog-export','--out',path.basename(dir)],opts);
  await command(['backlog-import','--out',path.basename(dir),'--input',exported.destination],opts);
  const result=await command(['backlog-import','--out',path.basename(dir),'--input',exported.destination],opts);
  assert.equal(result.tasks.length,1);
 } finally {fs.rmSync(dir,{recursive:true,force:true});}
});
