import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';import os from 'node:os';import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {createIsolatedExecutor,validIsolationEvidence,isGvisorEvidence,loadIsolationConfig} from './isolated-executor.mjs';

// A fake docker CLI on PATH records every call; FAKE_DOCKER_STATE selects the daemon's runtimes and the runtime it
// reports after the run. It proves the adapter's control flow; it is no gVisor runtime test (see the selftest script).
const IMAGE='sha256:'+'c'.repeat(64);
const FAKE=`#!/usr/bin/env node
const fs=require('fs'),path=require('path');const S=process.env.FAKE_DOCKER_STATE;const a=process.argv.slice(3);
fs.appendFileSync(path.join(S,'calls.log'),JSON.stringify(a)+'\\n');
const c=f=>path.join(S,f);
if(a[0]==='image'){process.stdout.write(${JSON.stringify(IMAGE)});process.exit(0);}
if(a[0]==='info'){if(!fs.existsSync(c('runtimes.json')))process.exit(1);process.stdout.write(fs.readFileSync(c('runtimes.json')));process.exit(0);}
if(a[0]==='run'){const name=a[a.indexOf('--name')+1];fs.writeFileSync(c('container-'+name),'');
 process.stdout.write(JSON.stringify({schema:'continuum-isolated-profile-v1',profile:a[a.length-1],reports:[],logs:'ok',exitCode:0,error:null,signal:null}));process.exit(0);}
if(a[0]==='container'){const name=a[a.length-1],exists=fs.existsSync(c('container-'+name));
 if(a[1]==='inspect'&&a[2]==='--format'){process.stdout.write(fs.existsSync(c('observed'))?fs.readFileSync(c('observed'),'utf8'):'runc');process.exit(0);}
 if(a[1]==='inspect'){if(exists)process.exit(0);process.stderr.write('Error: No such container: '+name);process.exit(1);}
 if(a[1]==='rm'){fs.rmSync(c('container-'+name),{force:true});process.exit(0);}
 process.exit(0);}
process.exit(2);
`;

function setup({runtimes=null,observed=null}={}){
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-gvisor-test-'));
  const bin=path.join(dir,'bin'),state=path.join(dir,'state'),node=path.join(dir,'node'),repo=path.join(dir,'repo');
  for(const d of [bin,state,path.join(node,'bin'),repo])fs.mkdirSync(d,{recursive:true});
  fs.writeFileSync(path.join(bin,'docker'),FAKE,{mode:0o755});fs.writeFileSync(path.join(node,'bin','node'),'');
  if(runtimes)fs.writeFileSync(path.join(state,'runtimes.json'),JSON.stringify(runtimes));
  if(observed)fs.writeFileSync(path.join(state,'observed'),observed);
  fs.writeFileSync(path.join(repo,'a.mjs'),'export const a=1;\n');
  const git=(...x)=>assert.equal(spawnSync('git',x,{cwd:repo}).status,0);
  git('init','-q');git('add','.');git('-c','user.name=T','-c','user.email=t@e.invalid','commit','-qm','x');
  const saved={PATH:process.env.PATH,FAKE_DOCKER_STATE:process.env.FAKE_DOCKER_STATE};
  process.env.PATH=bin+path.delimiter+process.env.PATH;process.env.FAKE_DOCKER_STATE=state;
  const calls=()=>fs.existsSync(path.join(state,'calls.log'))?fs.readFileSync(path.join(state,'calls.log'),'utf8').trim().split('\n').map(l=>JSON.parse(l)):[];
  const close=()=>{process.env.PATH=saved.PATH;if(saved.FAKE_DOCKER_STATE===undefined)delete process.env.FAKE_DOCKER_STATE;else process.env.FAKE_DOCKER_STATE=saved.FAKE_DOCKER_STATE;
    fs.rmSync(dir,{recursive:true,force:true});};
  return {dir,state,node,repo,calls,close};
}
const unprivileged=(process.getuid?.()??0)>0;
const opts={skip:unprivileged?false:'the executor refuses uid 0; run as an unprivileged user (e.g. setpriv --reuid=65534)'};

test('gVisor requested but not registered: blocked before any container runs, no host fallback',opts,()=>{
  const f=setup({runtimes:{runc:{path:'runc'}}});
  try{
    assert.throws(()=>createIsolatedExecutor({image:IMAGE,nodeHome:f.node,runtime:'runsc'}),/ISOLATION_GVISOR_UNAVAILABLE/);
    assert.ok(!f.calls().some(c=>c[0]==='run'),'nothing was executed');
    fs.rmSync(path.join(f.state,'runtimes.json'));
    assert.throws(()=>createIsolatedExecutor({image:IMAGE,nodeHome:f.node,runtime:'runsc'}),/ISOLATION_GVISOR_UNAVAILABLE/);
  }finally{f.close();}
});

test('gVisor run: runsc plus every existing hardening flag, confirmed runtime in the evidence, full teardown',opts,()=>{
  const f=setup({runtimes:{runc:{path:'runc'},runsc:{path:'/usr/local/bin/runsc'}},observed:'runsc'});
  const stagesBefore=fs.readdirSync(os.tmpdir()).filter(n=>n.startsWith('continuum-os-sandbox-')).length;
  try{
    const ex=createIsolatedExecutor({image:IMAGE,nodeHome:f.node,runtime:'runsc',memoryMb:512,pids:64,cpus:1,timeoutMs:60000});
    const r=ex.execute('node-continuum',f.repo,path.join(f.dir,'out'));
    const run=f.calls().find(c=>c[0]==='run');
    for(const flag of ['--runtime=runsc','--network=none','--ipc=none','--read-only','--cap-drop=ALL','--security-opt=no-new-privileges:true',
      '--pids-limit=64','--memory=512m','--memory-swap=512m','--cpus=1','--ulimit=core=0:0'])assert.ok(run.includes(flag),flag);
    const mounts=run.filter((x,i)=>run[i-1]==='--mount');
    assert.ok(mounts.length>0&&mounts.every(m=>m.endsWith(',readonly')),'no writable host bind');
    const control=mounts.filter(m=>/,dst=\/control\//.test(m));
    assert.deepEqual(control.map(m=>m.match(/dst=([^,]+)/)[1]).sort(),['/control/kotlin-fixture.sh','/control/runner.mjs'],'only the two fixed control files');
    assert.ok(!mounts.filter(m=>!control.includes(m)).some(m=>/src=(\/root|\/home|\/etc)(,|\/)/.test(m)),'no private host directories');
    assert.equal(r.isolation.runtime,'runsc');assert.equal(isGvisorEvidence(r.isolation,'node-continuum'),true);
    assert.equal(fs.readdirSync(f.state).filter(n=>n.startsWith('container-')).length,0,'container removed');
    assert.equal(fs.readdirSync(os.tmpdir()).filter(n=>n.startsWith('continuum-os-sandbox-')).length,stagesBefore,'staging removed');
  }finally{f.close();}
});

test('a daemon that silently runs another runtime yields no gVisor evidence, and the container is still removed',opts,()=>{
  const f=setup({runtimes:{runsc:{path:'/usr/local/bin/runsc'}},observed:'runc'});
  try{
    const ex=createIsolatedExecutor({image:IMAGE,nodeHome:f.node,runtime:'runsc'});
    assert.throws(()=>ex.execute('node-continuum',f.repo,path.join(f.dir,'out')),/ISOLATION_RUNTIME_MISMATCH/);
    assert.equal(fs.readdirSync(f.state).filter(n=>n.startsWith('container-')).length,0);
  }finally{f.close();}
});

test('runtime policy and evidence: unknown runtimes rejected, old receipts stay valid, runc is no gVisor evidence',()=>{
  assert.throws(()=>createIsolatedExecutor({image:IMAGE,runtime:'kata'}),/ISOLATION_INVALID_POLICY/);
  const receipt={schema:'continuum-isolation-v1',image:IMAGE,network:'none',user:'1000:1000',readOnly:true,noNewPrivileges:true,
    capabilities:[],profile:'node-continuum',sourceSha256:'b'.repeat(64),untrustedReports:true,memoryMb:512,pids:32,cpus:1,timeoutMs:1000};
  assert.equal(validIsolationEvidence(receipt,'node-continuum'),true);
  assert.equal(isGvisorEvidence(receipt,'node-continuum'),false);
  assert.equal(validIsolationEvidence({...receipt,runtime:'evil'},'node-continuum'),false);
  assert.equal(isGvisorEvidence({...receipt,runtime:'runsc'},'node-continuum'),true);
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-gvisor-config-'));
  try{const file=path.join(dir,'c.json');fs.writeFileSync(file,JSON.stringify({image:IMAGE,runtime:'runsc'}));
    assert.equal(loadIsolationConfig(file).runtime,'runsc');}finally{fs.rmSync(dir,{recursive:true,force:true});}
});
