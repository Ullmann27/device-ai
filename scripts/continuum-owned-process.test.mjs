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
