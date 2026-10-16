import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {backlogCommand} from './backlog-cli.mjs';
import {BacklogStore,nextEligible} from './development-backlog.mjs';

function checkout(){
 const parent=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-test-propose-'));
 const root=path.join(parent,'code');
 const dir=path.join(root,'.github','continuum');
 fs.mkdirSync(dir,{recursive:true});
 fs.writeFileSync(path.join(dir,'math-utils.mjs'),
  'export function triple(n){return n*3;}\n');
 const call=(...args)=>{
  const r=spawnSync('git',args,{cwd:root,encoding:'utf8',timeout:15000});
  assert.equal(r.status,0,r.stderr);return r.stdout.trim();
 };
 call('init','-q');call('add','.');
 call('-c','user.name=fixture','-c','user.email=fixture@example.invalid',
  'commit','-qm','snapshot');
 return {parent,root,head:call('rev-parse','HEAD'),call};
}

test('public CLI can propose then accept a task conforming to the Kotlin/Node scalar subject schema',
 {skip:Boolean(process.env.CONTINUUM_REPAIR_DEPTH),timeout:30000},async()=>{
 const f=checkout();
 try{
  const file=path.join(f.parent,'state','backlog.json');
  const common={root:f.root,log:()=>{}};
  await backlogCommand('propose-test',{
   file,id:'owner-triple-contract',
   module:'.github/continuum/math-utils.mjs',
   export:'triple','cases-json':'[{"args":[5],"expected":15}]'
  },common);
  let doc=new BacklogStore(file,{actor:'owner'}).read().doc;
  assert.equal(doc.tasks.length,1);
  assert.equal(doc.tasks[0].state,'PROPOSED');
  assert.equal(doc.tasks[0].kind,'TEST');
  assert.equal(doc.tasks[0].subject.base,f.head);
  assert.equal(doc.tasks[0].subject.casesJson,'[{"args":[5],"expected":15}]');
  await backlogCommand('accept',{file,id:'owner-triple-contract'},common);
  doc=new BacklogStore(file,{actor:'owner'}).read().doc;
  assert.equal(nextEligible(doc,['TEST'],['owner'],f.head,null,'ASSERT_NODE_EXPORT').id,
   'owner-triple-contract');
  assert.equal(f.call('status','--porcelain'),'');
 }finally{fs.rmSync(f.parent,{recursive:true,force:true});}
});

test('CLI refuses noncanonical array objects and uncontrolled shell-shaped operations',
 {skip:Boolean(process.env.CONTINUUM_REPAIR_DEPTH),timeout:30000},async()=>{
 const f=checkout();
 try{
  const file=path.join(f.parent,'state','backlog.json');
  const common={root:f.root,log:()=>{}};
  for(const opts of [
   {module:'../shell.mjs',export:'triple','cases-json':'[{"args":[5],"expected":15}]'},
   {module:'.github/continuum/math-utils.mjs',export:'triple','cases-json':'{"bad":true}'},
   {module:'.github/continuum/math-utils.mjs',export:'triple','cases-json':'[]'},
   {module:'.github/continuum/math-utils.mjs',export:'triple','cases-json':'x'.repeat(301)}
  ]){
   await assert.rejects(backlogCommand('propose-test',{file,...opts},common));
  }
  assert.equal(fs.existsSync(file),false,'invalid proposals should not create backlog files');
  assert.equal(f.call('status','--porcelain'),'');
 }finally{fs.rmSync(f.parent,{recursive:true,force:true});}
});
