/** Explicit real-kernel proof. Run with a trusted external toolchain JSON. */
import fs from 'node:fs';import os from 'node:os';import path from 'node:path';
import assert from 'node:assert/strict';import {spawnSync} from 'node:child_process';
import {createServer} from 'node:net';
import {createIsolatedExecutor} from '../.github/continuum/isolated-executor.mjs';
const config=JSON.parse(fs.readFileSync(process.argv[2],'utf8'));
const executor=createIsolatedExecutor(config);
const base=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-kernel-proof-'));
const source=path.join(base,'source'),out=path.join(base,'out'),owner=path.join(base,'owner-private');
fs.mkdirSync(path.join(source,'.github/continuum'),{recursive:true});fs.writeFileSync(owner,'OWNER_STATE_PRESERVED');
const server=createServer(socket=>socket.end('HOST_ONLY'));
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const port=server.address().port;
function commit(files){
  for(const [file,text] of Object.entries(files)){const p=path.join(source,file);fs.mkdirSync(path.dirname(p),{recursive:true});fs.writeFileSync(p,text);}
  for(const args of [['init','-q'],['add','.'],['-c','user.name=proof','-c','user.email=proof@example.invalid','commit','-qm','proof source']]){
    const r=spawnSync('git',args,{cwd:source,encoding:'utf8'});if(r.status!==0)throw Error('Proof source setup failed');
  }
}
const testSource=`import test from 'node:test';import assert from 'node:assert/strict';import fs from 'node:fs';import net from 'node:net';import {spawn,spawnSync} from 'node:child_process';
 test('owner files, credentials and source are outside granted rights',()=>{
  assert.throws(()=>fs.readFileSync(${JSON.stringify(owner)}));
  assert.throws(()=>fs.writeFileSync(${JSON.stringify(owner)},'CORRUPT'));
  assert.throws(()=>fs.writeFileSync('/work/forbidden','CORRUPT'));
  assert.equal(process.env.CONTINUUM_OWNER_CANARY,undefined);
  assert.throws(()=>fs.readFileSync('/var/run/docker.sock'));
 });
 test('no capabilities or privilege escalation, finite process and memory budgets',()=>{
  assert.notEqual(process.getuid(),0);assert.throws(()=>process.setuid(0));
  const status=fs.readFileSync('/proc/self/status','utf8');assert.match(status,/CapEff:\\s+0+\\n/);assert.match(status,/NoNewPrivs:\\s+1/);
  assert.equal(fs.readFileSync('/sys/fs/cgroup/pids.max','utf8').trim(),${JSON.stringify(String(config.pids??128))});
  assert.equal(Number(fs.readFileSync('/sys/fs/cgroup/memory.max','utf8')),${config.memoryMb??4096}*1024*1024);
  assert.notEqual(spawnSync('/usr/bin/unshare',['--mount','/bin/true']).status,0);
 });
 test('host loopback and host process namespace are inaccessible',async()=>{
  const devices=fs.readFileSync('/proc/net/dev','utf8');assert.doesNotMatch(devices,/eth[0-9]+:/);
  assert.throws(()=>fs.readFileSync('/proc/${process.pid}/cmdline'));
  await new Promise((resolve,reject)=>{const socket=net.connect({host:'127.0.0.1',port:${port}});
    socket.on('connect',()=>{socket.destroy();reject(Error('HOST_NETWORK_EXPOSED'));});socket.on('error',()=>resolve());socket.setTimeout(500,()=>{socket.destroy();resolve();});});
 });
 test('kernel pids limit actually rejects excess child creation',async()=>{
  let denied=0;const children=[];const waits=[];
  try{for(let i=0;i<160;i++){const child=spawn('/usr/bin/sleep',['10']);children.push(child);waits.push(new Promise(resolve=>{child.on('error',()=>{denied++;resolve();});child.on('close',resolve);}));}
    await new Promise(resolve=>setTimeout(resolve,100));assert.ok(denied>0);
  }finally{for(const child of children)child.kill();await Promise.all(waits);}
 });`;
try{
  process.env.CONTINUUM_OWNER_CANARY='never-forward-this-value';
  commit({'.github/continuum/proof.test.mjs':testSource});
  const result=executor.execute('node-continuum',source,out);
  assert.equal(result.status,0,result.stdout);assert.equal(fs.readFileSync(owner,'utf8'),'OWNER_STATE_PRESERVED');
  const proof={schema:'continuum-real-isolation-proof-v1',policy:executor.policy,node:{passed:true,tests:4,sourceSha256:result.isolation.sourceSha256},ownerUnchanged:true,kotlin:null,timeout:null};
  if(config.kotlinHome&&config.jdkHome){
    commit({'src/Main.kt':`import java.nio.file.Files\nimport java.nio.file.Path\nfun main(){\n val words=listOf("Beta", "alpha", "BETA").map{it.lowercase()}.distinct().sorted()\n check(words==listOf("alpha","beta"))\n check(System.getenv("CONTINUUM_OWNER_CANARY")==null)\n check(!Files.exists(Path.of(${JSON.stringify(owner)})))\n println("KOTLIN_ISOLATION_PASS")\n}\n`});
    const k=executor.execute('kotlin-fixture',source,out);assert.equal(k.status,0,k.stdout);assert.match(k.stdout,/KOTLIN_ISOLATION_PASS/);proof.kotlin={passed:true,sourceSha256:k.isolation.sourceSha256};
  }
  commit({'.github/continuum/proof.test.mjs':"import test from 'node:test';test('runaway',()=>{while(true){}});"});
  const timed=createIsolatedExecutor({...config,timeoutMs:1000});
  const started=Date.now();
  assert.throws(()=>timed.execute('node-continuum',source,out),/ISOLATION_/);
  const elapsedMs=Date.now()-started;assert.ok(elapsedMs<8000,'Timeout must kill the client and its container without manual intervention');
  proof.timeout={passed:true,containerRemoved:true,elapsedMs};
  assert.equal(fs.readFileSync(owner,'utf8'),'OWNER_STATE_PRESERVED');
  process.stdout.write(JSON.stringify(proof,null,2)+'\n');
}finally{delete process.env.CONTINUUM_OWNER_CANARY;await new Promise(resolve=>server.close(resolve));}
