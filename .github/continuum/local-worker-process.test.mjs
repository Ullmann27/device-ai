import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawn,spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {classifyNativeTestRun} from '../../scripts/continuum-local-development-worker.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const env=()=>{const e={...process.env};delete e.NODE_TEST_CONTEXT;return e;};
function fixture(){
 const top=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-worker-process-'));const repo=path.join(top,'repo');fs.mkdirSync(path.join(repo,'scripts'),{recursive:true});fs.mkdirSync(path.join(repo,'.github/continuum'),{recursive:true});
 for(const name of ['continuum-local-development-worker.mjs','continuum-worker-lease.mjs'])if(fs.existsSync(path.join(root,'scripts',name)))fs.copyFileSync(path.join(root,'scripts',name),path.join(repo,'scripts',name));
 fs.writeFileSync(path.join(repo,'.github/continuum/probe.test.mjs'),"import test from 'node:test';import assert from 'node:assert/strict';import fs from 'node:fs';test('controlled probe',async()=>{if(process.env.PROBE_OUTPUT)console.log('x'.repeat(+process.env.PROBE_OUTPUT));if(process.env.PROBE_WAIT)await new Promise(r=>setTimeout(r,+process.env.PROBE_WAIT));assert.equal(process.env.PROBE_FAIL,'0');});\n");
 for(const args of [['init','-q','-b','test/worker'],['add','.'],['-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','fixture']])assert.equal(spawnSync('git',args,{cwd:repo}).status,0);
 return {top,repo,script:path.join(repo,'scripts/continuum-local-development-worker.mjs'),out:path.join(top,'out')};
}
const waitFor=async(p,timeout=5000)=>{const until=Date.now()+timeout;while(!p()){if(Date.now()>until)throw Error('condition timeout');await new Promise(r=>setTimeout(r,25));}};
function run(f,extra={},args=[]){return spawnSync(process.execPath,[f.script,'--once',f.out,...args],{cwd:f.repo,env:{...env(),PROBE_FAIL:'0',...extra},encoding:'utf8',timeout:10000});}

test('actual worker captures native TAP counters and keeps immutable failed then successful receipts',()=>{
 const f=fixture();try{
  const red=run(f,{PROBE_FAIL:'1'});assert.equal(red.status,1,red.stderr);
  const failed=JSON.parse(fs.readFileSync(path.join(f.out,'latest.json')));assert.equal(failed.status,'FAILED');assert.equal(failed.failed,1);
  const green=run(f);assert.equal(green.status,0,green.stderr);const report=JSON.parse(fs.readFileSync(path.join(f.out,'latest.json')));
  assert.equal(report.status,'ALL_EXECUTED_PASS');assert.equal(report.tests,1);assert.equal(report.pass,1);assert.equal(report.weightsUpdated,false);
  assert.equal(fs.readdirSync(path.join(f.out,'cycles')).filter(x=>x.endsWith('.json')).length,2);
 }finally{fs.rmSync(f.top,{recursive:true,force:true});}
});
test('duplicate top-level native summaries are incomplete evidence',()=>{
 const one='# tests 1\n# pass 1\n# fail 0\n# cancelled 0\n# skipped 0\n# todo 0\n';
 assert.equal(classifyNativeTestRun({exitCode:0,stdout:one+one}).status,'FAILED');
});
test('SIGTERM during a real test stops children and permits a fresh process restart',{timeout:15000},async()=>{
 const f=fixture();let child;try{
  child=spawn(process.execPath,[f.script,'--once',f.out],{cwd:f.repo,env:{...env(),PROBE_FAIL:'0',PROBE_WAIT:'8000'},stdio:'ignore'});
  await waitFor(()=>fs.existsSync(path.join(f.out,'running.json')));
  const done=new Promise(resolve=>child.once('exit',(code,signal)=>resolve({code,signal})));child.kill('SIGTERM');await done;
  const stopped=JSON.parse(fs.readFileSync(path.join(f.out,'latest.json')));assert.equal(stopped.status,'INTERRUPTED');
  const retry=run(f);assert.equal(retry.status,0,retry.stderr);
 }finally{child?.kill('SIGKILL');fs.rmSync(f.top,{recursive:true,force:true});}
});

function budget(f,overrides={}){const file=path.join(f.top,'budget.json');fs.writeFileSync(file,JSON.stringify({timeoutMs:1000,intervalMs:3000,maxCycles:2,...overrides}));return ['--config',file];}
test('real timeout retains failure evidence and subsequent healthy cycle succeeds',{timeout:12000},()=>{
 const f=fixture();try{const r=run(f,{PROBE_WAIT:'8000'},budget(f));assert.equal(r.status,1);const bad=JSON.parse(fs.readFileSync(path.join(f.out,'latest.json')));assert.equal(bad.error,'TIMEOUT');assert.equal(run(f).status,0);}finally{fs.rmSync(f.top,{recursive:true,force:true});}
});
test('idle scheduler retains the lease and STOP cancels its wait',{timeout:12000},async()=>{
 const f=fixture();let child;try{
  child=spawn(process.execPath,[f.script,'--loop',f.out,...budget(f)],{cwd:f.repo,env:{...env(),PROBE_FAIL:'0'},stdio:'ignore'});
  await waitFor(()=>fs.existsSync(path.join(f.out,'latest.json')));
  const second=run({...f,out:path.join(f.top,'other-out')});assert.equal(second.status,1,'an idle scheduler still owns this checkout');
  const exited=new Promise(r=>child.once('exit',r));fs.writeFileSync(path.join(f.out,'STOP'),'owner stop');
  await Promise.race([exited,new Promise((_,reject)=>setTimeout(()=>reject(Error('STOP wait not cancelled')),1000))]);
 }finally{child?.kill('SIGKILL');fs.rmSync(f.top,{recursive:true,force:true});}
});
test('SIGKILL abandoned worker is recovered by identity and its residual test process is stopped',{timeout:12000},async()=>{
 const f=fixture();let child;try{
  child=spawn(process.execPath,[f.script,'--once',f.out],{cwd:f.repo,env:{...env(),PROBE_FAIL:'0',PROBE_WAIT:'8000'},stdio:'ignore'});
  const lock=path.join(f.repo,'.git','continuum-health-worker.json');
  await waitFor(()=>{try{return Boolean(JSON.parse(fs.readFileSync(lock)).child);}catch{return false;}});
  const ended=new Promise(r=>child.once('exit',r));child.kill('SIGKILL');await ended;
  const again=run(f);assert.equal(again.status,0,again.stderr);const report=JSON.parse(fs.readFileSync(path.join(f.out,'latest.json')));assert.equal(report.recoveredLease,true);
 }finally{child?.kill('SIGKILL');fs.rmSync(f.top,{recursive:true,force:true});}
});
test('excess test output is stopped and raw evidence storage stays bounded',{timeout:12000},()=>{
 const f=fixture();try{const r=run(f,{PROBE_OUTPUT:String(5*1024*1024)});assert.equal(r.status,1);const report=JSON.parse(fs.readFileSync(path.join(f.out,'latest.json')));assert.equal(report.error,'OUTPUT_LIMIT');const bytes=fs.readdirSync(path.join(f.out,'cycles')).filter(x=>x.endsWith('.tap')).reduce((n,x)=>n+fs.statSync(path.join(f.out,'cycles',x)).size,0);assert.ok(bytes<=2*1024*1024,bytes+' evidence bytes');}finally{fs.rmSync(f.top,{recursive:true,force:true});}
});
test('a redirected cycles directory never writes test output into source',{timeout:12000},()=>{
 const f=fixture();try{fs.mkdirSync(f.out,{recursive:true});fs.mkdirSync(path.join(f.repo,'leak'));fs.symlinkSync(path.join(f.repo,'leak'),path.join(f.out,'cycles'));const r=run(f);assert.equal(r.status,1);assert.deepEqual(fs.readdirSync(path.join(f.repo,'leak')),[]);}finally{fs.rmSync(f.top,{recursive:true,force:true});}
});
