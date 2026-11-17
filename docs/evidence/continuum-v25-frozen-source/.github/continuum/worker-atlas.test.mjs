import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {ATLAS_RESEARCH_TOPICS,researchAtlasOnce,atlasFind,atlasStatus} from './worker-atlas.mjs';

function sandbox(){
 const base=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-atlas-test-'));
 const root=path.join(base,'checkout'),state=path.join(base,'isolated-index');
 fs.mkdirSync(root,{recursive:true});
 execFileSync('git',['init','-q'],{cwd:root});
 execFileSync('git',['config','user.name','Continuum Atlas Test'],{cwd:root});
 execFileSync('git',['config','user.email','test@example.invalid'],{cwd:root});
 fs.writeFileSync(path.join(root,'README.md'),'Evidence-only research\n');
 execFileSync('git',['add','.'],{cwd:root});
 execFileSync('git',['commit','-qm','immutable source'],{cwd:root});
 return {root,state,close:()=>fs.rmSync(base,{recursive:true,force:true})};
}
const sample=async()=>({
 summary:'Bibliographic lead only; never a verified statement.',
 citations:[
  {title:'Android Kotlin Security Architecture',url:'https://example.org/android-kotlin'},
  {title:'Cross Platform Build Engineering',url:'https://doi.org/10.1/example'}
 ]
});

test('Atlas only stores verified-structure public HTTPS references, no copied documents',async()=>{
 const w=sandbox();
 try{
  const first=await researchAtlasOnce({root:w.root,state:w.state,research:sample,
   now:()=>100000,minIntervalMs:60000});
  assert.equal(first.status,'RECORDED_LEADS');
  assert.equal(first.added,2);
  assert.equal(first.trainingApproved,false);
  const status=atlasStatus(w);
  assert.equal(status.references,2);
  assert.equal(status.approvedTrainingSpans,0);
  assert.equal(status.allSourcesUnverified,true);
  const local=atlasFind({...w,query:'Kotlin Android'});
  assert.equal(local.length,1);
  assert.equal(local[0].trust,'UNVERIFIED_WEB_LEAD');
  assert.equal(local[0].verified,false);
  const file=path.join(w.state,'continuum-atlas-v1.json');
  const saved=JSON.parse(fs.readFileSync(file,'utf8'));
  assert.equal(saved.entries[0].ownerConfirmed,false);
  assert.equal(saved.entries[0].head,status.head);
  assert.equal(fs.statSync(file).mode&0o077,0);
  assert.doesNotMatch(fs.readFileSync(file,'utf8'),/articleBody|api[_-]?key|password/i);
 }finally{w.close();}
});

test('Atlas enforces cooldown, one fixed topic per cycle and no arbitrary remote searches',async()=>{
 const w=sandbox();
 try{
  const q=[];
  const fetch=async input=>{q.push(input.query);return sample();};
  const first=await researchAtlasOnce({root:w.root,state:w.state,research:fetch,now:()=>600000,minIntervalMs:60000});
  assert.equal(first.topic,'android');
  assert.equal((await researchAtlasOnce({root:w.root,state:w.state,research:fetch,now:()=>600001,minIntervalMs:60000})).status,'COOLDOWN');
  assert.equal(q.length,1);
  const next=await researchAtlasOnce({root:w.root,state:w.state,research:fetch,now:()=>660001,minIntervalMs:60000});
  assert.equal(next.topic,'ios');
  assert.equal(q.length,2);
  assert.equal(q[0],ATLAS_RESEARCH_TOPICS[0].query);
  assert.equal(q[1],ATLAS_RESEARCH_TOPICS[1].query);
  assert.equal(atlasStatus(w).references,4);
 }finally{w.close();}
});

test('Atlas rejects malformed sources and never imports private, non-HTTPS or local references',async()=>{
 const w=sandbox();
 try{
  const bad=async()=>({citations:[
   {title:'Private network resource',url:'http://192.168.0.1/admin'},
   {title:'Metadata cloud service',url:'https://metadata.google.internal/data'},
   {title:'Embedded API credentials',url:'https://token@example.org/secret'},
   {title:'Explicit filesystem path',url:'file:///etc/passwd'},
   {title:'Real Public Article',url:'https://example.org/article'}
  ]});
  const res=await researchAtlasOnce({root:w.root,state:w.state,research:bad,now:()=>999999,minIntervalMs:60000});
  assert.equal(res.added,1);
  assert.equal(atlasStatus(w).references,1);
  assert.deepEqual(atlasFind({...w,query:'network resource'}),[]);
  assert.equal(atlasFind({...w,query:'Public Article'}).length,1);
 }finally{w.close();}
});

test('failed public research is persisted for cooldown, not converted to verified knowledge',async()=>{
 const w=sandbox();
 try{
  const res=await researchAtlasOnce({root:w.root,state:w.state,now:()=>222222,
   minIntervalMs:60000,research:async()=>{throw Error('Provider unavailable')}});
  assert.equal(res.status,'SOURCE_UNAVAILABLE');
  assert.equal(atlasStatus(w).references,0);
  assert.equal((await researchAtlasOnce({root:w.root,state:w.state,now:()=>222223,
   minIntervalMs:60000,research:sample})).status,'COOLDOWN');
 }finally{w.close();}
});

test('tampered index, dirty source, repository-located state and invalid queries fail closed',async()=>{
 const w=sandbox();
 try{
  await assert.rejects(()=>researchAtlasOnce({root:w.root,state:path.join(w.root,'data'),
   research:sample}),/outside repository/);
  await researchAtlasOnce({root:w.root,state:w.state,now:()=>111111,
   minIntervalMs:60000,research:sample});
  const file=path.join(w.state,'continuum-atlas-v1.json');
  const doc=JSON.parse(fs.readFileSync(file,'utf8'));
  doc.entries[0].trust='OWNER_CONFIRMED';
  fs.writeFileSync(file,JSON.stringify(doc));
  assert.throws(()=>atlasStatus(w),/ATLAS_INTEGRITY_REJECTED/);
  fs.unlinkSync(file);
  assert.throws(()=>atlasFind({...w,query:''}),/ATLAS_INVALID_QUERY/);
  fs.writeFileSync(path.join(w.root,'uncommitted'),'dirt');
  await assert.rejects(()=>researchAtlasOnce({root:w.root,state:w.state,
   research:sample}),/dirty checkout/);
 }finally{w.close();}
});
