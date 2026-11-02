import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawn,spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {BacklogStore} from './development-backlog.mjs';

const WATCHDOG=fileURLToPath(new URL('./step-watchdog.mjs',import.meta.url));
const BACKLOG_MODULE=new URL('./development-backlog.mjs',import.meta.url).href;
const proc=['linux','android'].includes(process.platform);

function backlog(t){
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-step-watch-'));
  t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
  const file=path.join(dir,'development-backlog.json');
  const store=new BacklogStore(file,{actor:'owner'});
  store.apply([{op:'propose',actor:'owner',at:1,task:{id:'repair-watched',title:'Watched repair',kind:'REPAIR',
    source:'owner',priority:900,subject:{base:'0'.repeat(40),chain:'node'}}},
  {op:'accept',actor:'owner',at:2,id:'repair-watched',reason:'explicit local authorization'}]);
  return {dir,file,store,receipt:path.join(dir,'stopped.json')};
}
const abort=store=>store.apply([{op:'abort',actor:'android-owner',at:Date.now(),id:'repair-watched',reason:'Owner cancelled'}]);
const watched=(b,timeoutMs,args,{cwd}={})=>spawnSync(process.execPath,
  [WATCHDOG,b.file,'repair-watched',b.receipt,String(timeoutMs),String(process.pid),'--',process.execPath,...args],
  {cwd,timeout:timeoutMs,encoding:'utf8',maxBuffer:1024*1024});
const state=pid=>{try{return fs.readFileSync('/proc/'+pid+'/stat','utf8').split(') ')[1].split(' ')[0];}catch{return null;}};
const gone=pid=>[null,'Z'].includes(state(pid));
/** A command that records its pid (and a detached grandchild's), then runs until it is stopped. */
const lingering=marker=>['-e',`const{spawn}=require("node:child_process");
const g=spawn(process.execPath,["-e","process.on('SIGTERM',()=>{});setInterval(()=>{},1000)"],{detached:true,stdio:"ignore"});
process.on("SIGTERM",()=>{});
require("node:fs").writeFileSync(${JSON.stringify(marker)},process.pid+" "+g.pid);setInterval(()=>{},1000)`];
/** Applies the owner abort from another process once the marker exists, like ccc1-abort from Termux. */
function abortOnceStarted(b,marker){
  const script=`import fs from 'node:fs';import {BacklogStore} from ${JSON.stringify(BACKLOG_MODULE)};
const [file,marker]=process.argv.slice(1);const until=Date.now()+60000;
while(!fs.existsSync(marker)&&Date.now()<until)await new Promise(r=>setTimeout(r,25));
new BacklogStore(file,{actor:'android-owner'}).apply([{op:'abort',actor:'android-owner',at:Date.now(),id:'repair-watched',reason:'Owner cancelled'}]);
fs.writeFileSync(marker+'.aborted',String(Date.now()));`;
  spawn(process.execPath,['--input-type=module','-e',script,b.file,marker],{detached:true,stdio:'ignore'}).unref();
}

test('a watched step passes streams, exit code and ending signal through unchanged',{skip:!proc&&'/proc required'},t=>{
  const b=backlog(t);
  const args=['-e','process.stdout.write("out\\u00e4\\n".repeat(3));process.stderr.write("err\\n");process.exitCode=3'];
  const direct=spawnSync(process.execPath,args,{encoding:'utf8'});
  const r=watched(b,20000,args);
  assert.deepEqual([r.status,r.signal,r.stdout,r.stderr],[direct.status,direct.signal,direct.stdout,direct.stderr]);
  assert.equal(r.status,3);
  assert.equal(fs.existsSync(b.receipt),false,'no receipt without an abort');
  for(const sig of ['SIGKILL','SIGTERM']){
    const killed=watched(b,20000,['-e',`process.kill(process.pid,"${sig}")`]);
    assert.equal(killed.signal,sig);assert.equal(killed.status,null);
  }
});

test('a spawnSync timeout still reads as a timeout and also ends the detached grandchild',{skip:!proc&&'/proc required'},t=>{
  const b=backlog(t);
  const marker=path.join(b.dir,'started');
  const started=Date.now();
  const r=watched(b,1500,lingering(marker));
  assert.ok(Date.now()-started<8000,'bounded although the command ignores SIGTERM');
  assert.equal(r.error?.code,'ETIMEDOUT');
  const [pid,grandchild]=fs.readFileSync(marker,'utf8').split(' ').map(Number);
  t.after(()=>{for(const p of [pid,grandchild])try{process.kill(p,'SIGKILL');}catch{}});
  assert.ok(gone(pid)&&gone(grandchild),'command and its detached grandchild are stopped');
  assert.equal(fs.existsSync(b.receipt),false,'a timeout is no owner stop');
});

test('an owner abort ends a running step and its detached grandchild within seconds',{skip:!proc&&'/proc required'},t=>{
  const b=backlog(t);
  const marker=path.join(b.dir,'started');
  abortOnceStarted(b,marker);
  const r=watched(b,60000,lingering(marker));
  const returned=Date.now();
  const [pid,grandchild]=fs.readFileSync(marker,'utf8').split(' ').map(Number);
  t.after(()=>{for(const p of [pid,grandchild])try{process.kill(p,'SIGKILL');}catch{}});
  const abortedAt=Number(fs.readFileSync(marker+'.aborted','utf8'));
  assert.ok(returned-abortedAt<6000,'stopped '+(returned-abortedAt)+' ms after the abort, not after 60 s');
  assert.equal(r.error,undefined,'not a timeout');assert.equal(r.status,1);
  assert.deepEqual(Object.keys(JSON.parse(fs.readFileSync(b.receipt,'utf8'))).sort(),
    ['schemaVersion','stoppedAt','taskId','terminationConfirmed']);
  assert.equal(JSON.parse(fs.readFileSync(b.receipt,'utf8')).terminationConfirmed,true);
  assert.ok(gone(pid)&&gone(grandchild),'command and its detached grandchild are stopped');
});

test('a task aborted before the step starts never runs the command',{skip:!proc&&'/proc required'},t=>{
  const b=backlog(t);abort(b.store);
  const marker=path.join(b.dir,'started');
  const r=watched(b,20000,['-e',`require("node:fs").writeFileSync(${JSON.stringify(marker)},"ran")`]);
  assert.equal(r.status,1);
  assert.equal(fs.existsSync(marker),false);
  assert.equal(JSON.parse(fs.readFileSync(b.receipt,'utf8')).taskId,'repair-watched');
});

test('a watched step stops when the waiting parent is gone',{skip:!proc&&'/proc required'},async t=>{
  const b=backlog(t);
  const marker=path.join(b.dir,'started');
  // the parent starts the watchdog and dies while the step runs, like a worker killed by Android
  const parent=`const{spawn}=require("node:child_process");const fs=require("node:fs");
spawn(process.execPath,[${JSON.stringify(WATCHDOG)},${JSON.stringify(b.file)},"repair-watched",${JSON.stringify(b.receipt)},
"60000",String(process.pid),"--",...${JSON.stringify([process.execPath,...lingering(marker)])}],{stdio:"ignore"}).unref();
const until=Date.now()+20000;const wait=()=>fs.existsSync(${JSON.stringify(marker)})||Date.now()>until?process.exit(0):setTimeout(wait,25);wait();`;
  spawnSync(process.execPath,['-e',parent],{timeout:30000});
  const until=Date.now()+20000;
  const [pid,grandchild]=fs.readFileSync(marker,'utf8').split(' ').map(Number);
  t.after(()=>{for(const p of [pid,grandchild])try{process.kill(p,'SIGKILL');}catch{}});
  while(!(gone(pid)&&gone(grandchild))&&Date.now()<until)await new Promise(r=>setTimeout(r,50));
  assert.ok(gone(pid)&&gone(grandchild),'orphaned step is stopped');
  assert.equal(fs.existsSync(b.receipt),false,'no owner stop is claimed');
});

test('the watchdog refuses incomplete or relative arguments',t=>{
  const b=backlog(t);
  const me=String(process.pid);
  for(const args of [[b.file,'repair-watched',b.receipt,'1000',me,'--','node','-e','1'],
    ['relative.json','repair-watched',b.receipt,'1000',me,'--',process.execPath],
    [b.file,'Bad Id',b.receipt,'1000',me,'--',process.execPath],
    [b.file,'repair-watched',b.receipt,'0',me,'--',process.execPath],
    [b.file,'repair-watched',b.receipt,'1000','1','--',process.execPath],
    [b.file,'repair-watched',b.receipt,'1000',me,process.execPath]]){
    const r=spawnSync(process.execPath,[WATCHDOG,...args],{encoding:'utf8',timeout:20000});
    assert.equal(r.status,2,args.join(' '));assert.match(r.stderr,/invalid arguments/);
  }
});

test('an abort watch ends a running check profile and the chain never uses its result',
  {skip:(process.env.CONTINUUM_REPAIR_DEPTH?'no nested suite runs':false)||(!proc&&'/proc required'),timeout:120000},async t=>{
  const {runChain}=await import('./repair-loop.mjs');
  const b=backlog(t);
  const wt=path.join(b.dir,'wt'),out=path.join(b.dir,'out'),marker=path.join(b.dir,'started');
  fs.mkdirSync(path.join(wt,'.github/continuum'),{recursive:true});fs.mkdirSync(out);
  fs.writeFileSync(path.join(wt,'.github/continuum/slow.test.mjs'),
    "import test from 'node:test';import fs from 'node:fs';\n"+
    `test('slow',async()=>{fs.writeFileSync(${JSON.stringify(marker)},String(process.pid));`+
    'await new Promise(r=>setTimeout(r,60000));});\n');
  const abortWatch={backlog:b.file,taskId:'repair-watched'};
  assert.throws(()=>runChain('node',wt,out,{abortWatch:{...abortWatch,backlog:'relative.json'}}),/Invalid abort watch/);
  abortOnceStarted(b,marker);
  const steps=[];
  // a checkpoint that does not look at the backlog: the stopped step must still never count
  assert.throws(()=>runChain('node',wt,out,{abortWatch,checkpoint:step=>{steps.push(step);}}),/CHECK_STEP_STOPPED_BY_OWNER/);
  const returned=Date.now();
  const pid=Number(fs.readFileSync(marker,'utf8'));
  t.after(()=>{try{process.kill(pid,'SIGKILL');}catch{}});
  assert.ok(returned-Number(fs.readFileSync(marker+'.aborted','utf8'))<6000,'not after the 60 s test');
  assert.deepEqual(steps,['profile:node-continuum','checked:node-continuum']);
  assert.ok(gone(pid),'the test file process is stopped');
});
