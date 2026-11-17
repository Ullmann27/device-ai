import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
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
  const lock=path.join(stateRoot,fs.readdirSync(stateRoot)[0]);
  fs.writeFileSync(lock,'{}');
  assert.throws(release,/CONTINUUM_BUILD_LEASE_OWNERSHIP_CHANGED/);
  assert.ok(fs.existsSync(lock));
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
