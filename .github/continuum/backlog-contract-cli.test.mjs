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
 const kotlinDir=path.join(root,'app/src/main/java/p');
 fs.mkdirSync(kotlinDir,{recursive:true});
 fs.writeFileSync(path.join(kotlinDir,'MathApi.kt'),
  'package p\nclass MathApi {\n fun square(n: Int): Int = n*n\n}\n');
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
   {module:'.github/continuum/math-utils.mjs',export:'triple','cases-json':'x'.repeat(301)},
   {module:'.github/continuum/math-utils.mjs',export:'triple',
    'cases-json':'[{"args":[{"inject":"value"}],"expected":15}]'},
   {module:'.github/continuum/math-utils.mjs',export:'triple',
    'cases-json':'[{"args":[1],"expected":{"unsafe":"object"}}]'},
   {module:'.github/continuum/math-utils.mjs',export:'triple',
    'cases-json':JSON.stringify([{args:[1],expected:'x'.repeat(121)}])}
  ]){
   await assert.rejects(backlogCommand('propose-test',{file,...opts},common));
  }
  assert.equal(fs.existsSync(file),false,'invalid proposals should not create backlog files');
  assert.equal(f.call('status','--porcelain'),'');
 }finally{fs.rmSync(f.parent,{recursive:true,force:true});}
});


test('Kotlin IMPLEMENT CLI persists only scalar v2 owner oracle and requires separate owner acceptance',
 {skip:Boolean(process.env.CONTINUUM_REPAIR_DEPTH),timeout:30000},async()=>{
 const f=checkout();
 try{
  const file=path.join(f.parent,'state','implement.json');
  const args={
   file,id:'owner-kotlin-impl',path:'app/src/main/java/p/MathApi.kt',
   'class-name':'MathApi','method-name':'squareTwice',
   'delegate-name':'square','parameter-name':'value',type:'Int',depth:'2',
   'cases-json':'[{"input":2,"expected":16},{"input":3,"expected":81}]'
  };
  const common={root:f.root,log:()=>{}};
  await backlogCommand('propose-implement',args,common);
  let doc=new BacklogStore(file,{actor:'owner'}).read().doc;
  assert.equal(doc.tasks[0].kind,'IMPLEMENT');
  assert.equal(doc.tasks[0].state,'PROPOSED');
  assert.equal(doc.tasks[0].subject.depth,2);
  assert.equal(doc.tasks[0].subject.base,f.head);
  assert.equal(doc.tasks[0].subject.casesJson,args['cases-json']);
  assert.equal(nextEligible(doc,['IMPLEMENT'],['owner'],f.head,null,'EXPOSE_DELEGATE'),null);
  await backlogCommand('accept',{file,id:'owner-kotlin-impl'},common);
  doc=new BacklogStore(file,{actor:'owner'}).read().doc;
  assert.equal(nextEligible(doc,['IMPLEMENT'],['owner'],f.head,null,'EXPOSE_DELEGATE').id,
   'owner-kotlin-impl');
  assert.equal(f.call('status','--porcelain'),'');
 }finally{fs.rmSync(f.parent,{recursive:true,force:true});}
});

test('Kotlin IMPLEMENT CLI blocks unsupported types, paths and object-valued case specs',
 {skip:Boolean(process.env.CONTINUUM_REPAIR_DEPTH),timeout:30000},async()=>{
 const f=checkout();
 try{
  const file=path.join(f.parent,'state','rejected.json');
  const o={
   file,id:'owner-kotlin-impl',path:'app/src/main/java/p/MathApi.kt',
   'class-name':'MathApi','method-name':'squareTwice',
   'delegate-name':'square','parameter-name':'value',type:'Int',depth:'2',
   'cases-json':'[{"input":2,"expected":16}]'
  };
  for(const changes of [
   {type:'String'},{path:'../etc/passwd'},{depth:'300'},
   {'method-name':'square();'},
   {'cases-json':'[{"input":{"forged":1},"expected":16}]'},
   {'cases-json':'[{"input":2,"expected":"16"}]'}
  ])
   await assert.rejects(backlogCommand('propose-implement',
    {...o,...changes},{root:f.root,log:()=>{}}));
  assert.equal(fs.existsSync(file),false);
  assert.equal(f.call('status','--porcelain'),'');
 }finally{fs.rmSync(f.parent,{recursive:true,force:true});}
});
