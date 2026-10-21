import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {createWorktree,removeWorktree,repairSession} from './repair-loop.mjs';
import {executeOwnerRepairTask} from './backlog-repair-worker.mjs';
import {BacklogStore} from './development-backlog.mjs';

const nested=Boolean(process.env.CONTINUUM_REPAIR_DEPTH);
function fixture() {
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-local-candidate-'));
  const root=path.join(dir,'checkout'),state=path.join(dir,'state');
  fs.mkdirSync(path.join(root,'lib'),{recursive:true});
  fs.mkdirSync(path.join(root,'.github/continuum'),{recursive:true});
  fs.mkdirSync(state);
  fs.writeFileSync(path.join(root,'lib/math.mjs'),'export function distance(a,b){return a-b;}\n');
  fs.writeFileSync(path.join(root,'.github/continuum/spec.test.mjs'),
    "import test from 'node:test';import assert from 'node:assert/strict';import {distance} from '../../lib/math.mjs';\n"+
    "test('distance positive',()=>assert.equal(distance(8,3),5));\n"+
    "test('distance reverse',()=>assert.equal(distance(3,8),5));\n"+
    "test('negative positions',()=>assert.equal(distance(-9,-2),7));\n"+
    "test('identity',()=>assert.equal(distance(4,4),0));\n");
  const git=(...args)=>{const r=spawnSync('git',args,{cwd:root,encoding:'utf8'});assert.equal(r.status,0,r.stderr);return r.stdout.trim();};
  git('init','-q');git('add','.');git('-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','initial faulty implementation');
  return {dir,root,state,git,head:git('rev-parse','HEAD')};
}
const proposal=newText=>({mode:'LOCAL_LOOPBACK_PATCH_SHADOW',trusted:false,autoApply:false,
  patch:{file:'lib/math.mjs',oldText:'return a-b;',newText}});

test('opt-in local candidate uses the existing critic and real fixed tests',{skip:nested},()=>{
  const f=fixture();let wt;
  try {
    wt=createWorktree(f.root,f.head);
    const out=path.join(f.state,'candidate');
    const report=repairSession({wt,base:f.head,chain:'node',out,candidateProvider:()=>proposal('return a>b?a-b:b-a;')});
    assert.equal(report.outcome,'VERIFIED');
    assert.equal(report.verified.strategy,'LOCAL_MODEL_PATCH');
    assert.equal(report.verified.reviewRequired,true);
    assert.equal(report.verified.tests[0].tests.executed,4);
    assert.equal(f.git('status','--porcelain'),'');
    assert.equal(fs.readFileSync(path.join(f.root,'lib/math.mjs'),'utf8'),'export function distance(a,b){return a-b;}\n');
  } finally {if(wt)removeWorktree(f.root,wt);fs.rmSync(f.dir,{recursive:true,force:true});}
});

test('a plausible but overfitted local patch loses against negative holdouts',{skip:nested},()=>{
  const f=fixture();let wt;
  try {
    wt=createWorktree(f.root,f.head);
    const report=repairSession({wt,base:f.head,chain:'node',out:path.join(f.state,'bad'),
      candidateProvider:()=>proposal('return 5;')});
    assert.equal(report.outcome,'NO_VERIFIED_REPAIR');
    assert.equal(report.attempts.some(a=>a.strategy==='LOCAL_MODEL_PATCH'&&a.outcome==='FAILED_TESTS'),true);
    assert.equal(f.git('status','--porcelain'),'');
    assert.equal(fs.readFileSync(path.join(wt,'lib/math.mjs'),'utf8'),'export function distance(a,b){return a-b;}\n');
  } finally {if(wt)removeWorktree(f.root,wt);fs.rmSync(f.dir,{recursive:true,force:true});}
});

test('canonical owner backlog requires a fresh independent worktree for local candidates',{skip:nested},()=>{
  const f=fixture();
  try {
    const file=path.join(f.state,'backlog.json');const store=new BacklogStore(file,{actor:'owner'});
    store.apply([{op:'propose',actor:'owner',at:1,task:{id:'repair-local-distance',title:'Repair distance',kind:'REPAIR',source:'owner',priority:800,subject:{base:f.head,chain:'node'}}},
      {op:'accept',actor:'owner',at:2,id:'repair-local-distance'}]);
    const result=executeOwnerRepairTask({root:f.root,file,out:path.join(f.state,'artifacts'),
      candidateProvider:()=>proposal('return a>b?a-b:b-a;')});
    assert.equal(result.status,'DONE');
    const independent=JSON.parse(fs.readFileSync(result.independentReceipt,'utf8'));
    assert.equal(independent.passed,true);assert.equal(independent.executedTests,4);
    assert.equal(store.read().doc.tasks[0].state,'DONE');
    assert.equal(f.git('status','--porcelain'),'');
    assert.equal(result.approvedForMerge,false);
  } finally {fs.rmSync(f.dir,{recursive:true,force:true});}
});
