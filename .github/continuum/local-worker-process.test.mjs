import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawn,spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {classifyNativeTestRun} from '../../scripts/continuum-local-development-worker.mjs';
import {processIdentity,workerLease} from '../../scripts/continuum-worker-lease.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const env=()=>{const e={...process.env};delete e.NODE_TEST_CONTEXT;return e;};
function fixture(){
 const top=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-worker-process-'));const repo=path.join(top,'repo');fs.mkdirSync(path.join(repo,'scripts'),{recursive:true});fs.mkdirSync(path.join(repo,'.github/continuum'),{recursive:true});
 for(const name of ['continuum-local-development-worker.mjs','continuum-worker-lease.mjs'])if(fs.existsSync(path.join(root,'scripts',name)))fs.copyFileSync(path.join(root,'scripts',name),path.join(repo,'scripts',name));
 fs.writeFileSync(path.join(repo,'.github/continuum/probe.test.mjs'),"import test from 'node:test';import assert from 'node:assert/strict';import fs from 'node:fs';test('controlled probe',async()=>{if(process.env.PROBE_SWITCH_FILE&&fs.existsSync(process.env.PROBE_SWITCH_FILE))throw Error('controlled cycle failure');if(process.env.PROBE_OUTPUT)console.log('x'.repeat(+process.env.PROBE_OUTPUT));if(process.env.PROBE_WAIT)await new Promise(r=>setTimeout(r,+process.env.PROBE_WAIT));assert.equal(process.env.PROBE_FAIL,'0');});\n");
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

test('the same live scheduler automatically continues from a failed cycle to a healthy cycle',{timeout:15000},async()=>{
 const f=fixture();let child;try{
  const fault=path.join(f.top,'injected-fault');fs.writeFileSync(fault,'controlled failure');
  child=spawn(process.execPath,[f.script,'--loop',f.out,...budget(f,{intervalMs:500})],{cwd:f.repo,env:{...env(),PROBE_FAIL:'0',PROBE_SWITCH_FILE:fault},stdio:'ignore'});
  const done=new Promise(resolve=>child.once('exit',resolve));
  await waitFor(()=>fs.existsSync(path.join(f.out,'latest.json')));
  assert.equal(JSON.parse(fs.readFileSync(path.join(f.out,'latest.json'))).status,'FAILED');
  fs.unlinkSync(fault);
  const exitCode=await done;
  assert.equal(exitCode,1,'a later successful cycle must not conceal an earlier failure');
  const receipts=fs.readdirSync(path.join(f.out,'cycles')).filter(x=>x.endsWith('.json')).sort().map(x=>JSON.parse(fs.readFileSync(path.join(f.out,'cycles',x))));
  assert.deepEqual(receipts.map(x=>x.status),['FAILED','ALL_EXECUTED_PASS']);assert.equal(receipts[0].sourceSha,receipts[1].sourceSha);assert.equal(receipts[0].inventoryHash,receipts[1].inventoryHash);
 }finally{child?.kill('SIGKILL');fs.rmSync(f.top,{recursive:true,force:true});}
});

test('finite scheduler returns zero only when its single full cycle passed',{timeout:12000},()=>{
 const f=fixture();
 try{
  const args=[f.script,'--loop',f.out,...budget(f,{maxCycles:1,intervalMs:100})];
  const run=probe=>spawnSync(process.execPath,args,{
   cwd:f.repo,env:{...env(),PROBE_FAIL:probe},encoding:'utf8',timeout:8000
  });
  const failed=run('1');
  assert.equal(failed.status,1,failed.stderr);
  assert.equal(JSON.parse(fs.readFileSync(path.join(f.out,'latest.json'),'utf8')).status,'FAILED');
  const passed=run('0');
  assert.equal(passed.status,0,passed.stderr);
  assert.equal(JSON.parse(fs.readFileSync(path.join(f.out,'latest.json'),'utf8')).status,'ALL_EXECUTED_PASS');
 }finally{fs.rmSync(f.top,{recursive:true,force:true});}
});

test('loss of the checkout lease cannot leave a green completion receipt',{timeout:12000},async()=>{
 const f=fixture();let child;try{
  child=spawn(process.execPath,[f.script,'--once',f.out],{cwd:f.repo,env:{...env(),PROBE_FAIL:'0',PROBE_WAIT:'1000'},stdio:'ignore'});
  const lock=path.join(f.repo,'.git','continuum-health-worker.json');
  await waitFor(()=>{try{return Boolean(JSON.parse(fs.readFileSync(lock)).child);}catch{return false;}});
  const old=JSON.parse(fs.readFileSync(lock));fs.writeFileSync(lock,JSON.stringify({...old,token:'different-owner-token'}));
  await new Promise(r=>child.once('exit',r));
  const report=JSON.parse(fs.readFileSync(path.join(f.out,'latest.json')));assert.equal(report.status,'FAILED');assert.equal(report.error,'WORKER_LEASE_LOST');
 }finally{child?.kill('SIGKILL');fs.rmSync(f.top,{recursive:true,force:true});}
});

test('evidence disk budget pauses before tests and preserves existing receipts',{timeout:12000},()=>{
 const f=fixture();try{
  const folder=path.join(f.out,'cycles');fs.mkdirSync(folder,{recursive:true});const old=path.join(folder,'previous.tap');fs.writeFileSync(old,Buffer.alloc(3*1024*1024,65));
  const r=run(f,{},budget(f,{maxEvidenceMb:4}));assert.equal(r.status,1);assert.match(r.stderr,/EVIDENCE_BUDGET_EXHAUSTED/);assert.equal(fs.statSync(old).size,3*1024*1024);assert.equal(fs.readdirSync(folder).length,1);
 }finally{fs.rmSync(f.top,{recursive:true,force:true});}
});

for(const stopMode of ['SIGTERM','TIMEOUT'])test(stopMode+' removes a descendant that ignores SIGTERM before restart',{timeout:20000},async()=>{
 const f=fixture();let child,descendant=null;
 const marker=path.join(f.top,'descendant.json');
 const stillRunning=()=>{
  if(!descendant||JSON.stringify(processIdentity(descendant.pid))!==JSON.stringify(descendant))return false;
  try{const stat=fs.readFileSync('/proc/'+descendant.pid+'/stat','utf8');return !['Z','X'].includes(stat.slice(stat.lastIndexOf(')')+2).split(' ')[0]);}catch{return false;}
 };
 try{
  const resistant="import fs from 'node:fs';import {processIdentity} from "+JSON.stringify(path.join(f.repo,'scripts/continuum-worker-lease.mjs'))+";process.on('SIGTERM',()=>{});fs.writeFileSync(process.env.PROBE_DESCENDANT_MARKER,JSON.stringify(processIdentity()));setInterval(()=>{},1000);";
  const probe="import test from 'node:test';import {spawn} from 'node:child_process';test('controlled descendant',async()=>{if(process.env.PROBE_DESCENDANT_MARKER){spawn(process.execPath,['--input-type=module','-e',"+JSON.stringify(resistant)+"],{stdio:'ignore'});await new Promise(()=>{});}});\n";
  fs.writeFileSync(path.join(f.repo,'.github/continuum/probe.test.mjs'),probe);
  for(const args of [['add','.'],['-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','resistant descendant fixture']])assert.equal(spawnSync('git',args,{cwd:f.repo}).status,0);
  child=spawn(process.execPath,[f.script,'--once',f.out,...budget(f,{timeoutMs:5000})],{cwd:f.repo,env:{...env(),PROBE_FAIL:'0',PROBE_DESCENDANT_MARKER:marker},stdio:'ignore'});
  const done=new Promise(resolve=>child.once('exit',(code,signal)=>resolve({code,signal})));
  await waitFor(()=>fs.existsSync(marker));
  descendant=JSON.parse(fs.readFileSync(marker,'utf8'));
  assert.ok(stillRunning(),'the resistant descendant must actually have started');
  if(stopMode==='SIGTERM')child.kill('SIGTERM');
  const ended=await done;
  assert.equal(ended.code,1);
  const report=JSON.parse(fs.readFileSync(path.join(f.out,'latest.json'),'utf8'));
  assert.equal(report.status,stopMode==='SIGTERM'?'INTERRUPTED':'FAILED');
  assert.equal(report.error,stopMode==='SIGTERM'?'OWNER_INTERRUPTED':'TIMEOUT');
  await waitFor(()=>!stillRunning(),3000);
  assert.equal(run(f).status,0,'a new cycle may start after the old descendant stopped');
 }finally{
  child?.kill('SIGKILL');
  if(stillRunning())try{process.kill(descendant.pid,'SIGKILL');}catch{}
  fs.rmSync(f.top,{recursive:true,force:true});
 }
});

/** WIP/NOT_RUN source checkpoint against PR218 0b2a9ddd; no process result is claimed. */
for(const stoppedBy of ['STOP_FILE','ABORTED_SIGNAL'])test(stoppedBy+' refuses a cycle before test intent or child launch',{timeout:12000},()=>{
 const f=fixture();
 try{
  fs.mkdirSync(f.out,{recursive:true});
  let result;
  if(stoppedBy==='STOP_FILE'){
   fs.writeFileSync(path.join(f.out,'STOP'),'owner stop');
   result=run(f);
  }else{
   const driver=path.join(f.top,'pre-aborted.mjs');
   fs.writeFileSync(driver,"import {runOneCycle} from "+JSON.stringify(f.script)+";const stop=new AbortController();stop.abort();await runOneCycle("+JSON.stringify(f.out)+",{signal:stop.signal});\n");
   result=spawnSync(process.execPath,[driver],{cwd:f.repo,env:env(),encoding:'utf8',timeout:10000});
  }
  assert.equal(result.status,1,result.stderr);
  assert.match(result.stderr,/OWNER_INTERRUPTED_BEFORE_START/);
  assert.equal(fs.existsSync(path.join(f.out,'cycles')),false,'no test evidence or completion may be fabricated for an unlaunched cycle');
  assert.equal(fs.existsSync(path.join(f.out,'running.json')),false);
  assert.equal(fs.existsSync(path.join(f.out,'latest.json')),false);
  assert.equal(fs.existsSync(path.join(f.repo,'.git','continuum-health-worker.json')),false,'pre-start cancellation must not acquire a checkout lease');
 }finally{fs.rmSync(f.top,{recursive:true,force:true});}
});

for(const entry of ['continuum-health-worker.json','continuum-health-worker.json.mutex'])test('dangling '+entry+' cannot create an external lease target',{timeout:12000},()=>{
 const f=fixture();
 try{
  const external=path.join(f.top,'must-not-be-created');
  const file=path.join(f.repo,'.git',entry);
  fs.symlinkSync(external,file);
  const result=run(f);
  assert.equal(result.status,1,result.stderr);
  assert.match(result.stderr,/UNSAFE_LEASE_PATH/);
  assert.equal(fs.existsSync(external),false,'lease and flock must not follow a dangling entry');
  assert.equal(fs.lstatSync(file).isSymbolicLink(),true,'refusal leaves the original entry intact');
  assert.equal(fs.existsSync(path.join(f.out,'running.json')),false);
  assert.equal(fs.existsSync(path.join(f.out,'latest.json')),false);
 }finally{fs.rmSync(f.top,{recursive:true,force:true});}
});

test('a dangling lease-directory parent is refused without creating its target',{timeout:12000},()=>{
 const f=fixture();
 try{
  const missing=path.join(f.top,'missing-git-parent');
  const parent=path.join(f.top,'git-parent-link');
  fs.symlinkSync(missing,parent);
  const driver=path.join(f.top,'dangling-parent.mjs');
  fs.writeFileSync(driver,"import {workerLease} from "+JSON.stringify(path.join(f.repo,'scripts/continuum-worker-lease.mjs'))+";workerLease("+JSON.stringify(parent)+");\n");
  const result=spawnSync(process.execPath,[driver],{cwd:f.repo,env:env(),encoding:'utf8',timeout:10000});
  assert.equal(result.status,1,result.stderr);
  assert.match(result.stderr,/ENOENT/);
  assert.equal(fs.existsSync(missing),false);
  assert.equal(fs.lstatSync(parent).isSymbolicLink(),true);
 }finally{fs.rmSync(f.top,{recursive:true,force:true});}
});

test('pre-start STOP preserves another live checkout lease',{timeout:12000},()=>{
 const f=fixture();let owner;
 try{
  owner=workerLease(path.join(f.repo,'.git'));
  const file=path.join(f.repo,'.git','continuum-health-worker.json');
  const before=fs.readFileSync(file,'utf8');
  fs.mkdirSync(f.out,{recursive:true});fs.writeFileSync(path.join(f.out,'STOP'),'owner stop');
  const result=run(f);
  assert.equal(result.status,1,result.stderr);
  assert.match(result.stderr,/OWNER_INTERRUPTED_BEFORE_START/);
  assert.equal(fs.readFileSync(file,'utf8'),before,'no acquire, child bind or release may alter the active owner');
  owner.check();
  assert.equal(fs.existsSync(path.join(f.out,'running.json')),false);
 }finally{owner?.release();fs.rmSync(f.top,{recursive:true,force:true});}
});

for(const mode of ['STOP_FILE','ABORTED_SIGNAL'])test(mode+' prevents loop lease acquisition and preserves a live owner',{timeout:12000},()=>{
 const f=fixture();let owner;
 try{
  owner=workerLease(path.join(f.repo,'.git'));
  const file=path.join(f.repo,'.git','continuum-health-worker.json');
  const before=fs.readFileSync(file,'utf8');
  fs.mkdirSync(f.out,{recursive:true});let result;
  if(mode==='STOP_FILE'){
   fs.writeFileSync(path.join(f.out,'STOP'),'owner stop');
   result=spawnSync(process.execPath,[f.script,'--loop',f.out],{cwd:f.repo,env:env(),encoding:'utf8',timeout:10000});
   assert.equal(result.status,1,result.stderr);
  }else{
   const driver=path.join(f.top,'loop-aborted.mjs');
   fs.writeFileSync(driver,"import assert from 'node:assert/strict';import {runWorker} from "+JSON.stringify(f.script)+";const stop=new AbortController();stop.abort();assert.deepEqual(await runWorker("+JSON.stringify(f.out)+",{signal:stop.signal}),{cycles:0,failures:0,interrupted:true});\n");
   result=spawnSync(process.execPath,[driver],{cwd:f.repo,env:env(),encoding:'utf8',timeout:10000});
   assert.equal(result.status,0,result.stderr);
  }
  assert.equal(fs.readFileSync(file,'utf8'),before);
  owner.check();
  assert.equal(fs.existsSync(path.join(f.out,'running.json')),false);
  assert.equal(fs.existsSync(path.join(f.out,'cycles')),false);
  assert.equal(fs.existsSync(path.join(f.out,'latest.json')),false);
 }finally{owner?.release();fs.rmSync(f.top,{recursive:true,force:true});}
});
