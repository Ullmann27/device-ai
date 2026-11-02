import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {runOwnedCommand} from './continuum-owned-process.mjs';

test('bounded process executes argv directly and reports its actual exit',async()=>{
  const r=await runOwnedCommand(process.execPath,['-e','console.log(process.argv[1]);process.exitCode=3','$(id);no-shell'],
    {env:process.env,timeout:2000});
  assert.equal(r.ok,false);assert.equal(r.code,3);assert.match(r.output,/\$\(id\);no-shell/);
});
test('timeout kills the process group and never reports success',async()=>{
  const start=Date.now();
  const r=await runOwnedCommand(process.execPath,['-e','process.on("SIGTERM",()=>{});setInterval(()=>{},1000)'],
    {env:process.env,timeout:150});
  assert.equal(r.ok,false);assert.equal(r.timedOut,true);
  assert.ok(Date.now()-start<3000);
});
test('owner cancellation is asynchronous and cannot become a successful exit',async()=>{
  const controller=new AbortController();
  const pending=runOwnedCommand(process.execPath,['-e','setInterval(()=>{},1000)'],
    {env:process.env,timeout:2000,signal:controller.signal});
  setTimeout(()=>controller.abort(),100);
  const r=await pending;
  assert.equal(r.ok,false);assert.equal(r.cancelled,true);assert.equal(r.timedOut,false);
});
test('the signal that ended the direct child is reported, an exit code is not',async()=>{
  const killed=await runOwnedCommand(process.execPath,['-e','process.kill(process.pid,"SIGKILL")'],{env:process.env,timeout:5000});
  assert.equal(killed.ok,false);assert.equal(killed.code,null);assert.equal(killed.exitSignal,'SIGKILL');
  const exited=await runOwnedCommand(process.execPath,['-e','process.exitCode=4'],{env:process.env,timeout:5000});
  assert.equal(exited.code,4);assert.equal(exited.exitSignal,null);
});
test('oversized process logs are bounded and fail closed',async()=>{
  const r=await runOwnedCommand(process.execPath,['-e','process.stdout.write("x".repeat(1000000))'],
    {env:process.env,timeout:2000,maxOutputBytes:4096});
  assert.equal(r.ok,false);assert.equal(r.error,'OUTPUT_LIMIT');
  assert.ok(Buffer.byteLength(r.output)<=4096);
});
test('cancellation also stops a child that detached like the Gradle daemon',async t=>{
  if(process.platform!=='linux'){t.skip('Linux /proc identity check');return;}
  const childCode='process.on("SIGTERM",()=>{});setInterval(()=>{},1000)';
  const code='const{spawn}=require("node:child_process");const p=spawn(process.execPath,["-e",'+
    JSON.stringify(childCode)+
    '],{detached:true,stdio:"ignore"});console.log(p.pid);setInterval(()=>{},1000)';
  const r=await runOwnedCommand(process.execPath,['-e',code],{env:process.env,timeout:350});
  const pid=Number(r.output.trim());
  assert.ok(Number.isSafeInteger(pid)&&pid>1);
  t.after(()=>{try{process.kill(pid,'SIGKILL');}catch{}});
  let state=null;try{state=fs.readFileSync('/proc/'+pid+'/stat','utf8').split(') ')[1].split(' ')[0];}catch{}
  assert.ok(state===null||state==='Z','detached child still running: '+state);
});
for (const platform of ['linux','android']) {
  test('inherited run identity catches a rapidly orphaned detached child on '+platform,async t=>{
    if(!['linux','android'].includes(process.platform)){t.skip('/proc required');return;}
    const descriptor=Object.getOwnPropertyDescriptor(process,'platform');
    Object.defineProperty(process,'platform',{value:platform,configurable:true});
    t.after(()=>Object.defineProperty(process,'platform',descriptor));
    const childCode='process.on("SIGTERM",()=>{});setInterval(()=>{},1000)';
    const code='const{spawn}=require("node:child_process");const p=spawn(process.execPath,["-e",'+
      JSON.stringify(childCode)+'],{detached:true,stdio:"ignore"});console.log(p.pid);p.unref()';
    const r=await runOwnedCommand(process.execPath,['-e',code],{env:process.env,timeout:2000});
    const pid=Number(r.output.trim());
    assert.ok(Number.isSafeInteger(pid)&&pid>1);
    t.after(()=>{try{process.kill(pid,'SIGKILL');}catch{}});
    let state=null;try{state=fs.readFileSync('/proc/'+pid+'/stat','utf8').split(') ')[1].split(' ')[0];}catch{}
    assert.ok(state===null||state==='Z','orphan still running: '+state);
    assert.equal(r.terminationConfirmed,true);
  });
}
test('an unreadable process older than the run (container init, Termux app) does not block confirmed cleanup',async t=>{
  if(!['linux','android'].includes(process.platform)){t.skip('/proc required');return;}
  const open=fs.openSync;
  fs.openSync=(file,...args)=>{
    if(String(file)==='/proc/1/environ'||String(file)==='/proc/'+process.pid+'/environ')
      throw Object.assign(Error('denied'),{code:'EACCES'});
    return open(file,...args);
  };
  t.after(()=>{fs.openSync=open;});
  const r=await runOwnedCommand(process.execPath,['-e','console.log("done")'],{env:process.env,timeout:2000});
  assert.equal(r.ok,true);assert.equal(r.terminationConfirmed,true);
});
test('an unreadable process younger than the run blocks a confirmed cleanup',async t=>{
  if(!['linux','android'].includes(process.platform)){t.skip('/proc required');return;}
  const open=fs.openSync;
  fs.openSync=(file,...args)=>{
    if(/^\/proc\/[0-9]+\/environ$/.test(String(file)))throw Object.assign(Error('denied'),{code:'EACCES'});
    return open(file,...args);
  };
  t.after(()=>{fs.openSync=open;});
  // the direct child leaves a detached grandchild behind whose identity cannot be read: it might be ours
  const code='const{spawn}=require("node:child_process");const p=spawn(process.execPath,["-e","setTimeout(()=>{},3000)"],'+
    '{detached:true,stdio:"ignore"});console.log(p.pid);p.unref()';
  const r=await runOwnedCommand(process.execPath,['-e',code],{env:process.env,timeout:2000});
  t.after(()=>{try{process.kill(Number(r.output.trim()),'SIGKILL');}catch{}});
  assert.equal(r.ok,false);assert.equal(r.terminationConfirmed,false);
  assert.equal(r.error,'TERMINATION_UNCONFIRMED');
});
test('unreadable proc stat is not confused with an exited process',async t=>{
  const read=fs.readFileSync;
  fs.readFileSync=(file,...args)=>{
    if(/^\/proc\/[0-9]+\/stat$/.test(String(file)))throw Object.assign(Error('denied'),{code:'EACCES'});
    return read(file,...args);
  };
  t.after(()=>{fs.readFileSync=read;});
  const r=await runOwnedCommand(process.execPath,['-e','console.log("done")'],{env:process.env,timeout:2000});
  assert.equal(r.ok,false);assert.equal(r.terminationConfirmed,false);
});
test('denied proc access still stops its direct child and returns a bounded unconfirmed timeout',async t=>{
  const read=fs.readFileSync;
  fs.readFileSync=(file,...args)=>{
    if(/^\/proc\/[0-9]+\/stat$/.test(String(file)))throw Object.assign(Error('denied'),{code:'EACCES'});
    return read(file,...args);
  };
  t.after(()=>{fs.readFileSync=read;});
  const start=Date.now();
  const r=await runOwnedCommand(process.execPath,['-e','setInterval(()=>{},1000)'],{env:process.env,timeout:100});
  assert.ok(Date.now()-start<3000);
  assert.equal(r.ok,false);assert.equal(r.timedOut,true);assert.equal(r.terminationConfirmed,false);
});

test('a reused root PID cannot hide an earlier detached descendant from cleanup',async t=>{
  if(process.platform!=='linux'){t.skip('Linux /proc identity check');return;}
  const read=fs.readFileSync;
  let rootFile=null,rootStat=null,replacementSeen=false;
  const kill=process.kill,signals=[];
  process.kill=(pid,...args)=>{if(replacementSeen)signals.push(pid);return kill(pid,...args);};
  t.after(()=>{process.kill=kill;});
  fs.readFileSync=(file,...args)=>{
    let value;
    try {value=read(file,...args);} catch(error) {
      if(String(file)!==rootFile||error.code!=='ENOENT')throw error;
      replacementSeen=true;
      // Model PID reuse after the direct child exits: the replacement has a later start time.
      const end=rootStat.lastIndexOf(')')+2,parts=rootStat.slice(end).split(' ');
      parts[0]='S';parts[19]=String(Number(parts[19])+1000000);
      return rootStat.slice(0,end)+parts.join(' ');
    }
    if(!rootFile&&/^\/proc\/[0-9]+\/stat$/.test(String(file))){
      const parts=String(value).slice(String(value).lastIndexOf(')')+2).split(' ');
      if(Number(parts[1])===process.pid){rootFile=String(file);rootStat=String(value);}
    }
    return value;
  };
  t.after(()=>{fs.readFileSync=read;});
  const code='const{spawn}=require("node:child_process");const p=spawn(process.execPath,["-e","setInterval(()=>{},1000)"],{detached:true,stdio:"ignore"});console.log(p.pid);p.unref();';
  const result=await runOwnedCommand(process.execPath,['-e',code],{timeout:5000});
  const pid=Number(result.output.trim());
  t.after(()=>{try{process.kill(pid,'SIGKILL');}catch{}});
  assert.ok(rootFile,'root identity was observed before synthetic reuse');
  assert.ok(replacementSeen,'replacement identity was actually injected');
  const rootPid=Number(rootFile.split('/')[2]);
  assert.ok(!signals.includes(rootPid)&&!signals.includes(-rootPid),'never signal the replacement PID or group');
  let state=null;try{state=read('/proc/'+pid+'/stat','utf8').split(') ')[1].split(' ')[0];}catch{}
  assert.ok(state===null||state==='Z','detached descendant must be stopped after root PID reuse');
});

for(const permanent of [false,true])test('incomplete proc scans are retried within the cleanup bound: '+(permanent?'persistent':'transient'),async t=>{
  if(process.platform!=='linux'){t.skip('Linux /proc identity check');return;}
  const list=fs.readdirSync;let scans=0;
  fs.readdirSync=(file,...args)=>{
    if(file==='/proc'&&(++scans<=2||permanent))throw Object.assign(Error('synthetic scan denial'),{code:'EACCES'});
    return list(file,...args);
  };
  t.after(()=>{fs.readdirSync=list;});
  const started=Date.now();
  const result=await runOwnedCommand(process.execPath,['-e','process.exit(0)'],{timeout:5000});
  assert.ok(scans>2,'retry the incomplete snapshot instead of immediately releasing the caller');
  assert.equal(result.terminationConfirmed,!permanent);assert.equal(result.ok,!permanent);
  assert.equal(result.code,0);assert.ok(Date.now()-started<3000,'bounded cleanup even without proc access');
  if(permanent)assert.equal(result.error,'TERMINATION_UNCONFIRMED');
});
