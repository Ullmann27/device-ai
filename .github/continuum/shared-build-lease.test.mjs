import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawn,spawnSync} from 'node:child_process';
import {setTimeout as pause} from 'node:timers/promises';
import {acquireContinuumBuildLease,releaseDeadContinuumBuildLease} from './shared-build-lease.mjs';

function fixture(action) {
 const directory=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-build-lease-'));
 const root=path.join(directory,'source'),stateRoot=path.join(directory,'leases');
 fs.mkdirSync(root);
 try{return action({root,stateRoot,directory});}
 finally{fs.rmSync(directory,{force:true,recursive:true});}
}
function child(root,stateRoot) {
 const script=[
  'import {acquireContinuumBuildLease as acquire} from '+JSON.stringify(new URL('./shared-build-lease.mjs',import.meta.url).href)+';',
  'try { acquire('+JSON.stringify(root)+', {stateRoot:'+JSON.stringify(stateRoot)+'}); process.exit(15); }',
  "catch (error) { process.exit(error.message === 'CONTINUUM_BUILD_LEASE_BUSY' ? 0 : 16); }"
 ].join('\n');
 return spawnSync(process.execPath,['--input-type=module','-e',script],
  {encoding:'utf8',timeout:5000});
}

test('one atomic lease excludes simultaneous workers and can be reacquired',()=>
 fixture(({root,stateRoot})=>{
  const release=acquireContinuumBuildLease(root,{stateRoot});
  assert.throws(()=>acquireContinuumBuildLease(root,{stateRoot}),/CONTINUUM_BUILD_LEASE_BUSY/);
  const other=child(root,stateRoot);
  assert.equal(other.status,0,other.stderr);
  release(); release();
  const next=acquireContinuumBuildLease(root,{stateRoot}); next();
 }));

test('an unclean process exit retains the lock and cannot be stolen by a timer',()=>
 fixture(({root,stateRoot})=>{
  const script='import {acquireContinuumBuildLease as acquire} from '+
   JSON.stringify(new URL('./shared-build-lease.mjs',import.meta.url).href)+';'+
   'acquire('+JSON.stringify(root)+',{stateRoot:'+JSON.stringify(stateRoot)+'});';
  const result=spawnSync(process.execPath,['--input-type=module','-e',script],
    {encoding:'utf8',timeout:5000});
  assert.equal(result.status,0,result.stderr);
  assert.throws(()=>acquireContinuumBuildLease(root,{stateRoot}),/CONTINUUM_BUILD_LEASE_BUSY/);
 }));

test('rejects symlinked state root and checkout-local state',()=>
 fixture(({root,directory})=>{
  assert.throws(()=>acquireContinuumBuildLease(root,{stateRoot:path.join(root,'locks')}),
   /CONTINUUM_BUILD_LEASE_INSIDE_CHECKOUT/);
  const real=path.join(directory,'real'),link=path.join(directory,'link');
  fs.mkdirSync(real);fs.symlinkSync(real,link,'dir');
  assert.throws(()=>acquireContinuumBuildLease(root,{stateRoot:link}),
   /CONTINUUM_BUILD_LEASE_UNSAFE_STATE/);
 }));

test('release never deletes a tampered lock',()=>
 fixture(({root,stateRoot})=>{
  const release=acquireContinuumBuildLease(root,{stateRoot});
  const lock=path.join(stateRoot,fs.readdirSync(stateRoot).find(file=>file.endsWith('.lock')));
  fs.writeFileSync(lock,'{}');
  assert.throws(release,/CONTINUUM_BUILD_LEASE_OWNERSHIP_CHANGED/);
  assert.ok(fs.existsSync(lock));
 }));

test('dead-holder recovery refuses nonregular or oversized locks before reading their content',()=>
 fixture(({root,stateRoot,directory})=>{
  acquireContinuumBuildLease(root,{stateRoot});
  const lock=path.join(stateRoot,fs.readdirSync(stateRoot).find(file=>file.endsWith('.lock')));
  fs.unlinkSync(lock);
  const target=path.join(directory,'public-sentinel');fs.writeFileSync(target,'unchanged');
  const originalRead=fs.readFileSync;
  for(const kind of ['link','directory','oversized']){
   if(kind==='link')fs.symlinkSync(target,lock);
   else if(kind==='directory')fs.mkdirSync(lock);
   else fs.writeFileSync(lock,'x'.repeat(4097));
   fs.readFileSync=(file,...args)=>{
    if(file===lock)throw Error('UNSAFE_CONTENT_READ');
    return originalRead(file,...args);
   };
   try{assert.equal(releaseDeadContinuumBuildLease(root,{stateRoot}).reason,'UNSAFE_LOCK');}
   finally{fs.readFileSync=originalRead;fs.rmSync(lock,{recursive:true,force:true});}
  }
  assert.equal(fs.readFileSync(target,'utf8'),'unchanged');
 }));

test('independent source roots have independent leases',()=>
 fixture(({root,stateRoot,directory})=>{
  const other=path.join(directory,'other');fs.mkdirSync(other);
  const releaseA=acquireContinuumBuildLease(root,{stateRoot});
  const releaseB=acquireContinuumBuildLease(other,{stateRoot});
  releaseB();releaseA();
 }));

test('only a provably dead holder on this host can be released, and only through the explicit owner step',()=>
 fixture(({root,stateRoot})=>{
  assert.equal(releaseDeadContinuumBuildLease(root,{stateRoot}).reason,'NO_LEASE');
  const release=acquireContinuumBuildLease(root,{stateRoot});
  assert.deepEqual(releaseDeadContinuumBuildLease(root,{stateRoot}).reason,'HOLDER_ALIVE','a live holder is never released');
  release();
  // a crashed worker: lock left behind by a process that no longer exists
  const dead=spawnSync(process.execPath,['--input-type=module','-e',
   'import {acquireContinuumBuildLease as a} from '+JSON.stringify(new URL('./shared-build-lease.mjs',import.meta.url).href)+';'+
   'a('+JSON.stringify(root)+',{stateRoot:'+JSON.stringify(stateRoot)+'});process.exit(0);'],{encoding:'utf8',timeout:5000});
  assert.equal(dead.status,0,dead.stderr);
  assert.throws(()=>acquireContinuumBuildLease(root,{stateRoot}),/CONTINUUM_BUILD_LEASE_BUSY/,'age or a dead holder alone frees nothing');
  const lock=fs.readdirSync(stateRoot).find(f=>f.endsWith('.lock'));
  const holder=JSON.parse(fs.readFileSync(path.join(stateRoot,lock),'utf8'));
  fs.writeFileSync(path.join(stateRoot,lock),JSON.stringify({...holder,hostname:'another-host'})+'\n');
  assert.equal(releaseDeadContinuumBuildLease(root,{stateRoot}).reason,'FOREIGN_OR_UNKNOWN_HOLDER');
  fs.writeFileSync(path.join(stateRoot,lock),JSON.stringify(holder)+'\n');
  const r=releaseDeadContinuumBuildLease(root,{stateRoot});
  assert.equal(r.released,true);assert.equal(r.reason,'HOLDER_DEAD');
  acquireContinuumBuildLease(root,{stateRoot})();
 }));

for(const interruption of [false,true])test(interruption?
 'SIGKILL during recovery releases its kernel guard while retaining the dead build lease':
 'concurrent dead-holder recovery cannot move a live successor and admit another build',
 {timeout:15000},async t=>{
 const directory=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-lease-recovery-race-'));
 const root=path.join(directory,'source'),stateRoot=path.join(directory,'leases');
 fs.mkdirSync(root);
 const moduleUrl=new URL('./shared-build-lease.mjs',import.meta.url).href;
 const dead=spawnSync(process.execPath,['--input-type=module','-e',
  'import {acquireContinuumBuildLease as acquire} from '+JSON.stringify(moduleUrl)+';'+
  'acquire('+JSON.stringify(root)+',{stateRoot:'+JSON.stringify(stateRoot)+'});'],
  {encoding:'utf8',timeout:5000});
 assert.equal(dead.status,0,dead.stderr);
 // Pause a real recoverer immediately before, then after, its rename syscall.
 // The original race lets another recoverer remove the old lock, then moves
 // a newly acquired live lock away while admitting a second active holder.
 const script=`
  import fs from 'node:fs';
  import {releaseDeadContinuumBuildLease as recover} from ${JSON.stringify(moduleUrl)};
  const directory=${JSON.stringify(directory)};
  const waitFor=name=>{
   const until=Date.now()+5000;
   while(!fs.existsSync(directory+'/'+name)){
    if(Date.now()>until)throw Error('Timed out waiting for '+name);
    Atomics.wait(new Int32Array(new SharedArrayBuffer(4)),0,0,5);
   }
  };
  const rename=fs.renameSync;
  fs.renameSync=(from,to)=>{
   fs.writeFileSync(directory+'/ready','1');waitFor('go');
   const result=rename(from,to);
   fs.writeFileSync(directory+'/moved','1');waitFor('finish');
   return result;
  };
  const result=recover(${JSON.stringify(root)},{stateRoot:${JSON.stringify(stateRoot)}});
  fs.writeFileSync(directory+'/result',JSON.stringify(result));
 `;
 const worker=spawn(process.execPath,['--input-type=module','-e',script],
  {stdio:['ignore','ignore','pipe']});
 let stderr='',releaseB,releaseC;
 worker.stderr.on('data',chunk=>stderr+=chunk);
 const exited=new Promise(resolve=>worker.once('exit',(code,signal)=>resolve({code,signal})));
 t.after(async()=>{
  if(worker.exitCode===null&&worker.signalCode===null)worker.kill('SIGKILL');
  await exited;
  for(const release of [releaseC,releaseB])try{release?.();}catch{}
  fs.rmSync(directory,{recursive:true,force:true});
 });
 const waitFor=async name=>{
  const until=Date.now()+5000;
  while(!fs.existsSync(path.join(directory,name))){
   assert.ok(Date.now()<until,'Timed out waiting for '+name+': '+stderr);
   await pause(5);
  }
 };
 await waitFor('ready');
 let firstRecovery;
 try{firstRecovery=releaseDeadContinuumBuildLease(root,{stateRoot});}
 catch(error){assert.match(error.message,/CONTINUUM_BUILD_LEASE_BUSY/);}
 if(interruption){
  assert.equal(firstRecovery,undefined,'a second recoverer cannot mutate the guarded lease');
  worker.kill('SIGKILL');
  assert.equal((await exited).signal,'SIGKILL');
  assert.throws(()=>acquireContinuumBuildLease(root,{stateRoot}),/CONTINUUM_BUILD_LEASE_BUSY/,
   'process death releases only the kernel transaction guard, never the retained build lease');
  assert.equal(releaseDeadContinuumBuildLease(root,{stateRoot}).reason,'HOLDER_DEAD');
  releaseB=acquireContinuumBuildLease(root,{stateRoot});
  releaseB();releaseB=null;
  return;
 }
 if(firstRecovery?.released)releaseB=acquireContinuumBuildLease(root,{stateRoot});
 else assert.throws(()=>acquireContinuumBuildLease(root,{stateRoot}),/CONTINUUM_BUILD_LEASE_BUSY/);
 fs.writeFileSync(path.join(directory,'go'),'1');
 await waitFor('moved');
 let secondBlocked=false;
 try{releaseC=acquireContinuumBuildLease(root,{stateRoot});}
 catch(error){assert.match(error.message,/CONTINUUM_BUILD_LEASE_BUSY/);secondBlocked=true;}
 fs.writeFileSync(path.join(directory,'finish'),'1');
 await waitFor('result');
 assert.equal(secondBlocked,true,'a paused recoverer must never expose a window for another build');
 assert.equal(firstRecovery,undefined,'a second recoverer cannot mutate the guarded lease');
 assert.equal(JSON.parse(fs.readFileSync(path.join(directory,'result'),'utf8')).reason,'HOLDER_DEAD');
 assert.equal((await exited).code,0,stderr);
 releaseB=acquireContinuumBuildLease(root,{stateRoot});
 assert.equal(child(root,stateRoot).status,0);
 releaseB();releaseB=null;
 });

test('missing flock refuses lease acquisition and dead-holder recovery without changing the lease',()=>
 fixture(({root,stateRoot,directory})=>{
  const moduleUrl=new URL('./shared-build-lease.mjs',import.meta.url).href;
  const script=`import {acquireContinuumBuildLease as acquire,releaseDeadContinuumBuildLease as recover}
    from ${JSON.stringify(moduleUrl)};
    try{({acquire,recover})[process.argv[1]](${JSON.stringify(root)},{stateRoot:${JSON.stringify(stateRoot)}});process.exitCode=15;}
    catch(error){if(error.message!=='CONTINUUM_BUILD_LEASE_LOCK_BACKEND_UNAVAILABLE')throw error;}`;
  const options={encoding:'utf8',timeout:5000,env:{...process.env,PATH:directory}};
  const refused=spawnSync(process.execPath,['--input-type=module','-e',script,'acquire'],options);
  assert.equal(refused.status,0,refused.stderr);
  assert.equal(fs.readdirSync(stateRoot).filter(file=>file.endsWith('.lock')).length,0);
  const dead=spawnSync(process.execPath,['--input-type=module','-e',
   'import {acquireContinuumBuildLease as acquire} from '+JSON.stringify(moduleUrl)+';'+
   'acquire('+JSON.stringify(root)+',{stateRoot:'+JSON.stringify(stateRoot)+'});'],
   {encoding:'utf8',timeout:5000});
  assert.equal(dead.status,0,dead.stderr);
  const lock=path.join(stateRoot,fs.readdirSync(stateRoot).find(file=>file.endsWith('.lock')));
  const before=fs.readFileSync(lock,'utf8');
  const recovery=spawnSync(process.execPath,['--input-type=module','-e',script,'recover'],options);
  assert.equal(recovery.status,0,recovery.stderr);
  assert.equal(fs.readFileSync(lock,'utf8'),before);
  assert.equal(releaseDeadContinuumBuildLease(root,{stateRoot}).reason,'HOLDER_DEAD');
 }));

test('a dangling transaction mutex cannot create or follow an external target',()=>
 fixture(({root,stateRoot,directory})=>{
  acquireContinuumBuildLease(root,{stateRoot})();
  const mutex=path.join(stateRoot,fs.readdirSync(stateRoot).find(file=>file.endsWith('.mutex')));
  fs.unlinkSync(mutex);
  const outside=path.join(directory,'outside');
  fs.symlinkSync(outside,mutex);
  assert.throws(()=>acquireContinuumBuildLease(root,{stateRoot}),{code:'ELOOP'});
  assert.equal(fs.existsSync(outside),false);
  assert.throws(()=>releaseDeadContinuumBuildLease(root,{stateRoot}),{code:'ELOOP'});
  assert.equal(fs.existsSync(outside),false);
 }));
