import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {scoutRequest,createScriptedBrain} from './coding-agent-loop.mjs';
import {createWorktree,removeWorktree} from './repair-loop.mjs';

test('the local agent ranks defensive sources and scans only allowed tracked code',()=>{
 const home=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-agent-risk-'));
 const root=path.join(home,'repo');fs.mkdirSync(root);
 const write=(p,s)=>{fs.mkdirSync(path.dirname(path.join(root,p)),{recursive:true});fs.writeFileSync(path.join(root,p),s);};
 write('src/network/handler.py',"import subprocess\nsubprocess.run(cmd, shell=True)\n");
 write('src/ui/button.mjs',"export const button = true;\n");
 write('secrets/key.mjs',"const secret='UNREADABLE-SENTINEL-KEY';\n");
 const git=(...args)=>{
  const r=spawnSync('git',args,{cwd:root,encoding:'utf8',shell:false});
  assert.equal(r.status,0,r.stderr);return r.stdout.trim();
 };
 let wt=null;
 try{
  git('init','-q');git('add','.');
  git('-c','user.name=Risk Test','-c','user.email=risk@example.invalid','commit','-qm','initial');
  const base=git('rev-parse','HEAD');wt=createWorktree(root,base);
  const seen=[];
  const result=scoutRequest({wt,base,request:'Investigate the network process execution risk',
   brain:createScriptedBrain([
    {tool:'rank_files',args:{}},
    {tool:'scan_sinks',args:{path:'src/network/handler.py'}},
    {tool:'scan_sinks',args:{path:'secrets/key.mjs'}},
    {tool:'propose_scope',args:{
     files:['src/network/handler.py'],acceptance:['process execution is independently tested'],kind:'BUGFIX'
    }}
   ]),onStep:step=>seen.push(step)});
  assert.equal(result.schema,'continuum-coding-agent-scope');
  assert.deepEqual(result.files,['src/network/handler.py']);
  assert.equal(result.commit,base);
  assert.match(seen[0].observation,/src\/network\/handler\.py/);
  assert.doesNotMatch(seen[0].observation,/secrets/);
  assert.match(seen[1].observation,/UNSAFE_SHELL_MODE/);
  assert.match(seen[2].observation,/Not a readable tracked file/);
  assert.ok(!seen.some(s=>String(s.observation??'').includes('UNREADABLE-SENTINEL-KEY')));
  assert.ok(!seen.some(s=>String(s.observation??'').includes('subprocess.run(cmd, shell=True)')),
   'the sink scanner reports rule IDs and lines, not source');
  assert.equal(git('status','--porcelain'),'', 'original working tree unchanged');
 }finally{
  if(wt)removeWorktree(root,wt);
  fs.rmSync(home,{recursive:true,force:true});
 }
});
