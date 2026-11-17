import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {backlogCommand} from './backlog-cli.mjs';
import {loadBacklogFromBranch,saveBacklogToBranch,STATE_BRANCH} from './repair-publish.mjs';
import * as B from './development-backlog.mjs';

const nested=process.env.CONTINUUM_REPAIR_DEPTH?'skipped inside a repair session (no nested check chains)':false;

function repo(){
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'backlog-cli-'));
  const g=(...a)=>{const r=spawnSync('git',a,{cwd:dir,encoding:'utf8'});if(r.status!==0)throw Error(r.stderr);return r.stdout.trim();};
  fs.mkdirSync(path.join(dir,'.github/continuum'),{recursive:true});
  fs.writeFileSync(path.join(dir,'.github/continuum/math.mjs'),"export const square=x=>x+x;\n");
  fs.writeFileSync(path.join(dir,'.github/continuum/math.test.mjs'),"import test from 'node:test';\nimport assert from 'node:assert/strict';\nimport {square} from './math.mjs';\ntest('square',()=>assert.equal(square(3),9));\n");
  g('init','-q');g('-c','user.name=t','-c','user.email=t@t','add','.');g('-c','user.name=t','-c','user.email=t@t','commit','-qm','base');
  return {dir,g,base:g('rev-parse','HEAD')};
}
const silent=()=>{};

test('backlog CLI: a human task is DONE only after verify ran the fixed checks on the submitted patch',{skip:nested},async()=>{
  const {dir,g,base}=repo();
  const file=path.join(dir,'state','backlog.json');
  const opsFile=path.join(dir,'ops.json');
  try{
    fs.writeFileSync(opsFile,JSON.stringify([{op:'propose',actor:'heinz',at:1,task:{id:'task-square',title:'square() korrigieren',kind:'REPAIR',source:'cli'}}]));
    await backlogCommand('apply',{file,ops:opsFile},{root:dir,log:silent});
    await backlogCommand('accept',{file,id:'task-square'},{root:dir,log:silent});
    const {token}=await backlogCommand('claim',{file,owner:'claude'},{root:dir,log:silent});
    // the work: a patch, written next to the checkout
    fs.writeFileSync(path.join(dir,'.github/continuum/math.mjs'),"export const square=x=>x*x;\n");
    const patch=path.join(dir,'fix.patch');
    fs.writeFileSync(patch,g('diff','--binary')+'\n');
    g('checkout','--','.');
    await backlogCommand('submit',{file,id:'task-square',token,patch},{root:dir,log:silent});
    // a different file is not the submitted artifact
    const other=path.join(dir,'other.patch');fs.writeFileSync(other,'diff --git a/x b/x\n');
    await assert.rejects(backlogCommand('verify',{file,id:'task-square',base,chain:'node',patch:other},{root:dir,log:silent}),/not the submitted artifact/);
    const lines=[];
    const r=await backlogCommand('verify',{file,id:'task-square',base,chain:'node',patch},{root:dir,log:m=>lines.push(m)});
    assert.equal(r.results[0].ok,true,lines.join('\n'));
    const task=r.doc.tasks[0];
    assert.equal(task.state,'DONE');
    assert.deepEqual(task.evidence[0].profiles.map(p=>[p.profile,p.passed,p.executed,p.failed]),[['node-continuum',true,1,0]]);
    assert.equal(task.evidence[0].artifactSha256,B.sha256Hex(fs.readFileSync(patch)));
    assert.match(fs.readFileSync(path.join(dir,'.github/continuum/math.mjs'),'utf8'),/x\+x/,'the checkout itself is never modified');
    assert.equal(g('worktree','list','--porcelain').split('\n').filter(l=>l.startsWith('worktree ')).length,1,'no leftover worktree');
  }finally{fs.rmSync(dir,{recursive:true,force:true});}
});

test('backlog CLI: a patch that fails the checks records failing evidence and returns the task',{skip:nested},async()=>{
  const {dir,g,base}=repo();
  const file=path.join(dir,'backlog.json');
  try{
    await backlogCommand('apply',{file,ops:(fs.writeFileSync(path.join(dir,'ops.json'),JSON.stringify([
      {op:'propose',actor:'heinz',at:1,task:{id:'task-square',title:'square',kind:'REPAIR',source:'cli'}},{op:'accept',actor:'heinz',at:2,id:'task-square'}])),path.join(dir,'ops.json'))},{root:dir,log:silent});
    const {token}=await backlogCommand('claim',{file,id:'task-square',owner:'claude'},{root:dir,log:silent});
    fs.writeFileSync(path.join(dir,'.github/continuum/math.mjs'),"export const square=x=>x*4;\n");
    const patch=path.join(dir,'wrong.patch');fs.writeFileSync(patch,g('diff','--binary')+'\n');g('checkout','--','.');
    await backlogCommand('submit',{file,id:'task-square',token,patch},{root:dir,log:silent});
    const r=await backlogCommand('verify',{file,id:'task-square',base,chain:'node',patch},{root:dir,log:silent});
    const task=r.doc.tasks[0];
    assert.deepEqual([task.state,task.error.code,task.evidence[0].profiles[0].failed],['READY','CHECKS_FAILED',1]);
  }finally{fs.rmSync(dir,{recursive:true,force:true});}
});

test('backlog CLI rejects path traversal and unknown commands',async()=>{
  await assert.rejects(backlogCommand('show',{file:'../x.json'}),/without "\.\."/);
  await assert.rejects(backlogCommand('nope',{file:path.join(os.tmpdir(),'never-created-backlog.json')}),/Unknown backlog command/);
});

test('state branch: backlog is saved with compare-and-swap, reloaded, and unreadable state fails closed',()=>{
  const originDir=fs.mkdtempSync(path.join(os.tmpdir(),'backlog-origin-'));
  const {dir,g}=repo();
  try{
    spawnSync('git',['init','-q','--bare',originDir]);
    g('remote','add','origin',originDir);
    assert.deepEqual(loadBacklogFromBranch(dir),{doc:null,remoteHead:null},'a missing state branch is empty state');
    const propose={op:'propose',actor:'continuum-agent',at:1,task:{id:'task-one',title:'Eins',kind:'TEST',source:'cli'}};
    const saved=saveBacklogToBranch(dir,STATE_BRANCH,doc=>({doc:B.applyOp(doc,propose).doc,results:[]}));
    assert.deepEqual([saved.status,saved.revision],['SAVED',1]);
    const again=saveBacklogToBranch(dir,STATE_BRANCH,doc=>({doc,results:[]}));
    assert.equal(again.status,'UNCHANGED');
    const loaded=loadBacklogFromBranch(dir);
    assert.equal(loaded.doc.tasks[0].id,'task-one');
    const files=spawnSync('git',['ls-tree','--name-only',loaded.remoteHead],{cwd:dir,encoding:'utf8'}).stdout.trim().split('\n').sort();
    assert.deepEqual(files,['README.md','development-backlog.json']);
    assert.throws(()=>saveBacklogToBranch(dir,'main',d=>({doc:d})),/must be continuum-agent-state/);
    g('remote','set-url','origin',path.join(originDir,'does-not-exist'));
    assert.throws(()=>loadBacklogFromBranch(dir),/Cannot read state branch/,'unreachable state is an error, not "empty"');
  }finally{fs.rmSync(dir,{recursive:true,force:true});fs.rmSync(originDir,{recursive:true,force:true});}
});
