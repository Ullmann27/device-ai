import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {
 parseWorkshopArgs,identifyWorkshops,workshopPaths,
 diagnoseWorkshop,readWorkshopStatus,executeWorkshop
} from './continuum-workshop.mjs';

function checkout(){
 const base=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-workshop-test-'));
 const root=path.join(base,'checkout');
 const state=path.join(base,'private-state');
 fs.mkdirSync(root,{recursive:true});
 execFileSync('git',['init','-q'],{cwd:root});
 execFileSync('git',['config','user.name','Continuum Test'],{cwd:root});
 execFileSync('git',['config','user.email','test@example.invalid'],{cwd:root});
 fs.writeFileSync(path.join(root,'README.md'),'Dedicated immutable source checkout\n');
 execFileSync('git',['add','README.md'],{cwd:root});
 execFileSync('git',['commit','-qm','baseline'],{cwd:root});
 return {base,root,state,cleanup:()=>fs.rmSync(base,{recursive:true,force:true})};
}

test('workshop accepts only explicit bounded modes and options',()=>{
 const opts=parseWorkshopArgs(['--mode','loop','--interval-minutes','15','--max-cycles','2']);
 assert.equal(opts.mode,'loop');assert.equal(opts.intervalMinutes,15);assert.equal(opts.maxCycles,2);
 for(const args of [
  ['--mode','merge'],['--mode','loop','--interval-minutes','0'],
  ['--mode','loop','--max-cycles','1001'],['--mode','once','--interval-minutes','5'],
  ['--mode','once','--budget-minutes','31'],['--root'],
  ['--state','/tmp/x','--state','/tmp/y'],['--','test'],
  ['--mode','doctor','--shell','rm -rf /']
 ])assert.throws(()=>parseWorkshopArgs(args));
});

test('capability discovery never authorizes unreviewed Flutter or Godot exports',()=>{
 const w=checkout();
 try{
  fs.mkdirSync(path.join(w.root,'app'),{recursive:true});
  fs.writeFileSync(path.join(w.root,'app/build.gradle.kts'),'');
  fs.writeFileSync(path.join(w.root,'settings.gradle.kts'),'');
  fs.writeFileSync(path.join(w.root,'package.json'),'{}');
  fs.writeFileSync(path.join(w.root,'pubspec.yaml'),'name: test\n');
  fs.mkdirSync(path.join(w.root,'lib'));
  fs.writeFileSync(path.join(w.root,'project.godot'),'');
  const c=identifyWorkshops(w.root,{gradle:true,node:true,androidSdk:true});
  assert.equal(c.find(x=>x.id==='android-gradle').executable,true);
  assert.equal(c.find(x=>x.id==='node').executable,true);
  for(const framework of ['flutter','godot']){
   const x=c.find(v=>v.id===framework);
   assert.equal(x.detected,true);
   assert.equal(x.executable,false);
   assert.equal(x.reason,'RECIPE_NOT_YET_VERIFIED');
  }
 }finally{w.cleanup();}
});

test('private worker state stays outside checkout and refuses a dirty source before a cycle',async()=>{
 const w=checkout();
 try{
  assert.throws(()=>workshopPaths(w.root,path.join(w.root,'state')));
  assert.throws(()=>workshopPaths(w.root,w.base));
  const x=workshopPaths(w.root,w.state);
  assert.match(x.head,/^[a-f0-9]{40}$/);
  const d=diagnoseWorkshop(x,{node:true,gradle:false,androidSdk:false});
  assert.equal(d.sourceClean,true);
  assert.equal(d.mainWritesAllowed,false);
  fs.writeFileSync(path.join(w.root,'untracked.txt'),'dirty');
  await assert.rejects(()=>executeWorkshop({
   ...parseWorkshopArgs(['--mode','once']),root:w.root,state:w.state
  }),/dirty source checkout/);
 }finally{w.cleanup();}
});

test('worker status does not hallucinate a running PC worker',()=>{
 const w=checkout();
 try{
  const x=workshopPaths(w.root,w.state);
  const initial=readWorkshopStatus(x);
  assert.equal(initial.heartbeatState,'NOT_CONNECTED');
  assert.equal(initial.automaticMerge,false);
  fs.mkdirSync(w.state,{recursive:true});
  fs.writeFileSync(path.join(w.state,'workshop-heartbeat.json'),'{"state":"RUNNING"}');
  assert.equal(readWorkshopStatus(x).heartbeatState,'CORRUPT');
 }finally{w.cleanup();}
});

test('real Teamleader FSM runs with an injected bounded safe driver, no merge',async()=>{
 const w=checkout();
 try{
  const head=workshopPaths(w.root,w.state).head;
  let repairs=0;
  const driverFactory=async()=>({
   head:()=>head,repair:()=>{repairs++;return {outcome:'NO_FAILURE'};}
  });
  const opts={...parseWorkshopArgs(['--mode','once']),root:w.root,state:w.state};
  const first=await executeWorkshop(opts,{driverFactory,
   research:async()=>({status:'OFFLINE_FIXTURE'})});
  assert.equal(first.outcome,'NO_FAILURE');
  assert.equal(first.automaticMerge,false);
  assert.equal(repairs,1);
  const second=await executeWorkshop(opts,{driverFactory,
   research:async()=>({status:'OFFLINE_FIXTURE'})});
  assert.equal(second.outcome,'IDLE');
  assert.equal(repairs,1);
  assert.equal(fs.readFileSync(path.join(w.root,'README.md'),'utf8'),
   'Dedicated immutable source checkout\n');
  const after=readWorkshopStatus(workshopPaths(w.root,w.state));
  assert.equal(after.progress.baselineClean,1);
  assert.equal(after.progress.currentlyUnfinished,0);
 }finally{w.cleanup();}
});

test('one-cycle cadence persists bounded heartbeat and never claims automatic merge',async()=>{
 const w=checkout();
 try{
  const head=workshopPaths(w.root,w.state).head;
  let executed=0;
  const res=await executeWorkshop({
   ...parseWorkshopArgs(['--mode','loop','--max-cycles','1']),
   root:w.root,state:w.state
  },{driverFactory:async()=>({
   head:()=>head,repair:()=>{executed++;return {outcome:'NO_FAILURE'};}
  }),research:async()=>({status:'OFFLINE_FIXTURE'})});
  assert.equal(executed,1);
  assert.equal(res.mode,'loop');assert.equal(res.cycles,1);
  assert.equal(res.automaticMerge,false);
  const status=readWorkshopStatus(workshopPaths(w.root,w.state));
  assert.equal(status.heartbeatState,'LIMIT_REACHED');
  assert.equal(status.heartbeat.lastOutcome,'NO_FAILURE');
  assert.equal(status.progress.baselineClean,1);
 }finally{w.cleanup();}
});


test('explicit virtual Atlas research mode cannot start arbitrary commands or merge code',async()=>{
 const w=checkout();
 try{
  let cycles=0;
  const result=await executeWorkshop({
   ...parseWorkshopArgs(['--mode','research']),root:w.root,state:w.state
  },{research:async({root,state})=>{
   assert.equal(root,w.root);
   assert.equal(state,w.state);
   cycles++;
   return {status:'RECORDED_LEADS',added:3,trainingApproved:false};
  }});
  assert.equal(cycles,1);
  assert.equal(result.status,'RECORDED_LEADS');
  assert.equal(result.trainingApproved,false);
  assert.equal(result.automaticMerge,false);
  assert.equal(readWorkshopStatus(workshopPaths(w.root,w.state)).atlas.references,0);
 }finally{w.cleanup();}
});
