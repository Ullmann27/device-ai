import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {BacklogStore,sha256Hex} from './development-backlog.mjs';
import {localTeamleaderDriver} from './autonomous-teamleader.mjs';
import {executeInvestigation} from './backlog-investigator.mjs';
import {removeWorktree} from './repair-loop.mjs';

const nested=Boolean(process.env.CONTINUUM_REPAIR_DEPTH);
const taskId='inv-0000000000000001';
function fixture(passing=true){
 const base=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-investigation-recovery-'));
 const root=path.join(base,'source'),state=path.join(base,'state');
 fs.mkdirSync(path.join(root,'.github','continuum'),{recursive:true});
 fs.mkdirSync(path.join(root,'app','src','main','java','p'),{recursive:true});
 fs.writeFileSync(path.join(root,'app','src','main','java','p','Orphan.kt'),
  'package p\nclass Orphan\n');
 fs.writeFileSync(path.join(root,'.github','continuum','baseline.test.mjs'),
  "import test from 'node:test';\nimport assert from 'node:assert/strict';\n"+
  "test('real regression check',()=>assert.equal(2+2,"+(passing?'4':'5')+"));\n");
 const git=(...args)=>{
  const r=spawnSync('git',args,{cwd:root,encoding:'utf8',timeout:10000});
  assert.equal(r.status,0,r.stderr);return r.stdout.trim();
 };
 git('init','-q');git('add','.');
 git('-c','user.name=fixture','-c','user.email=fixture@example.invalid',
  'commit','-qm','investigation fixture');
 const head=git('rev-parse','HEAD'),file=path.join(state,'development-backlog.json');
 const store=new BacklogStore(file,{actor:'owner'});
 store.apply([
  {op:'propose',actor:'owner',at:1,task:{id:taskId,title:'Inspect orphan',
   kind:'INVESTIGATE',source:'code-analyzer',maxAttempts:2,
   subject:{finding:'UNREFERENCED',file:'app/src/main/java/p/Orphan.kt',symbol:'p.Orphan'}}},
  {op:'accept',actor:'owner',at:2,id:taskId}
 ]);
 const out=path.join(state,'reports','investigation.json');
 const cleanup=()=>{
  for(const line of git('worktree','list','--porcelain').split('\n')){
   if(line.startsWith('worktree ')&&line.slice(9)!==root)
    removeWorktree(root,line.slice(9));
  }
  fs.rmSync(base,{recursive:true,force:true});
 };
 return {root,state,file,store,head,out,git,cleanup};
}

for(const passing of [true,false])test(
 'process death after persisting investigation verdict cannot strand '+(passing?'green':'red')+' checks in VERIFYING',
 {skip:nested,timeout:60000},()=>{
 const f=fixture(passing);
 try{
  const script=`
   import {BacklogStore} from ${JSON.stringify(new URL('./development-backlog.mjs',import.meta.url).href)};
   import {executeInvestigation} from ${JSON.stringify(new URL('./backlog-investigator.mjs',import.meta.url).href)};
   const apply=BacklogStore.prototype.apply;
   BacklogStore.prototype.apply=function(ops,options){
    const saved=apply.call(this,ops,options);
    if(ops.some(op=>op.op==='submit'))process.exit(78);
    return saved;
   };
   executeInvestigation(JSON.parse(process.argv[2]));
  `;
  const env={...process.env};delete env.NODE_TEST_CONTEXT;
  const child=spawnSync(process.execPath,['--input-type=module','-e',script,
   path.join(f.state,'crash-injection.mjs'),
   JSON.stringify({root:f.root,file:f.file,out:f.out,base:f.head,taskId})],
   {encoding:'utf8',env,timeout:45000});
  assert.equal(child.status,78,child.stderr);
  const task=f.store.read().doc.tasks[0];
  assert.equal(task.state,passing?'DONE':'READY',
   'submission and its real test verdict must survive together');
  assert.equal(task.attempts,1);
  assert.equal(task.evidence.length,1);
  assert.equal(task.evidence[0].profiles[0].executed,1);
  assert.equal(task.evidence[0].profiles[0].passed,passing);
  assert.equal(task.evidence[0].commit,f.head);
  assert.equal(task.evidence[0].artifactSha256,sha256Hex(fs.readFileSync(f.out)));
  assert.equal(task.claim,null);
  assert.equal(f.git('status','--porcelain'),'');
 }finally{f.cleanup();}
});

test('scheduler recovers an expired investigation claim and honours the remaining attempt budget',
 async()=>{
 const f=fixture();
 try{
  const driver=await localTeamleaderDriver();
  f.store.apply([{op:'claim',actor:'worker',at:3,id:taskId,
   owner:'worker',token:'a'.repeat(32),leaseMs:60000}]);
  assert.equal(driver.readyInvestigation(f.root,f.state,f.head)?.id,taskId);
  assert.equal(f.store.read().doc.tasks[0].attempts,1);
  f.store.apply([{op:'claim',actor:'worker',at:70000,id:taskId,
   owner:'worker',token:'b'.repeat(32),leaseMs:60000}]);
  assert.equal(driver.readyInvestigation(f.root,f.state,f.head),null);
  assert.equal(f.store.read().doc.tasks[0].state,'FAILED');
  assert.equal(f.store.read().doc.tasks[0].attempts,2);
 }finally{f.cleanup();}
});

test('scheduler keeps a live investigation claim and its token intact',async()=>{
 const f=fixture();
 try{
  const token='c'.repeat(32);
  f.store.apply([{op:'claim',actor:'worker',at:Date.now(),id:taskId,
   owner:'worker',token,leaseMs:60000}]);
  const before=fs.readFileSync(f.file);
  const driver=await localTeamleaderDriver();
  assert.equal(driver.readyInvestigation(f.root,f.state,f.head),null);
  assert.deepEqual(fs.readFileSync(f.file),before);
  assert.equal(f.store.read().doc.tasks[0].claim.token,token);
 }finally{f.cleanup();}
});

test('direct investigation worker recovers an expired claim before selecting its exact task',
 {skip:nested,timeout:60000},()=>{
 const f=fixture();
 try{
  f.store.apply([{op:'claim',actor:'worker',at:3,id:taskId,
   owner:'worker',token:'a'.repeat(32),leaseMs:60000}]);
  const result=executeInvestigation({root:f.root,file:f.file,out:f.out,
   base:f.head,taskId});
  assert.equal(result.status,'DONE');
  assert.equal(f.store.read().doc.tasks[0].attempts,2);
  assert.equal(f.store.read().doc.tasks[0].state,'DONE');
 }finally{f.cleanup();}
});

for(const target of ['source report','symlink report','source backlog','symlink backlog'])test(
 'investigation rejects '+target+' before changing source or claim state',
 {skip:nested,timeout:60000},()=>{
 const f=fixture();
 try{
  const alias=path.join(f.state,'source-alias');
  fs.symlinkSync(f.root,alias,'dir');
  const destination=path.join(target.startsWith('symlink')?alias:f.root,'new','output.json');
  const options={root:f.root,file:f.file,out:f.out,base:f.head,taskId};
  options[target.endsWith('report')?'out':'file']=destination;
  assert.throws(()=>executeInvestigation(options),/outside the source checkout/);
  assert.equal(f.store.read().doc.tasks[0].state,'READY');
  assert.equal(f.store.read().doc.tasks[0].attempts,0);
  assert.equal(f.git('status','--porcelain'),'');
 }finally{f.cleanup();}
});
