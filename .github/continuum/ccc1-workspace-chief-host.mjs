/** Fixed test-only host bridge. Synthetic fixture authority, never a production grant.
 * CLI verbs and worker indices are fixed; no model-selected shell or executable. */
import fs from 'node:fs';import path from 'node:path';import crypto from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {createIsolatedExecutor,validWorkspaceEvidence} from './isolated-executor.mjs';
import {bindCcc1Workspace} from './ccc1-workspace.mjs';
import {workspaceFixture} from './ccc1-workspace-fixture.mjs';
const [verb,home,index]=process.argv.slice(2);
if(!['prepare','run','cancel','active'].includes(verb)||!path.isAbsolute(home??'')||
   (verb!=='prepare'&&!['0','1'].includes(index)))throw Error('FIXED_HOST_HARNESS_ARGUMENTS');
const image='sha256:3f44842e8a052beda7e358b9fb8d8acc3e180a7f58aaceff7e1b49ad7f3d46e1';
const config={image,nodeHome:'/opt/codex/runtimes/codex-primary-runtime/dependencies/node',memoryMb:256,cpus:1,pids:64,timeoutMs:15000};
const manifest=path.join(home,'synthetic-fixture.json');
function docker(args){const env={...process.env};for(const k of ['DOCKER_HOST','DOCKER_CONTEXT','DOCKER_TLS','DOCKER_TLS_VERIFY','DOCKER_CERT_PATH'])delete env[k];
 const r=spawnSync('docker',['--host=unix:///var/run/docker.sock',...args],{env,encoding:'utf8',timeout:15000});
 if(r.status!==0)throw Error('HOST_HARNESS_DOCKER_FAILED: '+r.stderr.slice(-200));return r.stdout.trim();}
if(verb==='prepare'){
 if(fs.existsSync(manifest))throw Error('FIXTURE_ALREADY_PREPARED');
 const fixtures=['worker-alpha','worker-beta'].map(workerId=>{
  const testSource=`import test from 'node:test';import assert from 'node:assert/strict';import fs from 'node:fs';import net from 'node:net';
  test('Chief fixed Node workspace probe',async()=>{
   assert.equal(fs.readFileSync('/work/${workerId}.txt','utf8'),'${workerId}');
   assert.throws(()=>fs.readFileSync('/workspace/momega1-chief/AGENTS.md'),{code:'ENOENT'});
   assert.throws(()=>fs.writeFileSync('/work/${workerId}.txt','escape'),e=>['EROFS','EACCES'].includes(e.code));
   assert.equal(fs.readFileSync('/sys/fs/cgroup/memory.max','utf8').trim(),'268435456');
   assert.equal(fs.readFileSync('/sys/fs/cgroup/pids.max','utf8').trim(),'64');
   await new Promise((resolve,reject)=>{const s=net.connect({host:'1.1.1.1',port:443});s.setTimeout(1000);s.once('error',()=>resolve());s.once('connect',()=>{s.destroy();reject(Error('network escape'));});s.once('timeout',()=>{s.destroy();reject(Error('unconfirmed network block'));});});
   await new Promise(resolve=>setTimeout(resolve,3000));
  });`;
  const f=workspaceFixture(home,workerId,{testSource});
  const scope={workerId,taskId:f.taskId,sourceCommit:f.journal.entries()[0].baseCommitSha,requestMessageId:f.request.messageId,
   requestHash:f.request.hash,jobId:f.job,profile:'node-continuum',memoryMb:256,cpus:1,pids:64,timeoutMillis:15000};
  return {root:f.root,stateDir:f.stateDir,scope};
 });
 fs.writeFileSync(manifest,JSON.stringify(fixtures,null,2)+'\n',{flag:'wx',mode:0o600});
 process.stdout.write(JSON.stringify(fixtures.map(f=>f.scope))+'\n');
}else{
 const f=JSON.parse(fs.readFileSync(manifest,'utf8'))[Number(index)];
 if(verb==='run'){
  const executor=bindCcc1Workspace({executor:createIsolatedExecutor(config),project:f.root,stateDir:f.stateDir,
   taskId:f.scope.taskId,job:f.scope.jobId,workerId:f.scope.workerId});
  const result=executor.execute(f.scope.profile,f.root,path.join(home,'junit'));
  if(!validWorkspaceEvidence(result.isolation,f.scope.profile))throw Error('HOST_OBSERVATION_INVALID');
  const bytes=JSON.stringify(result,null,2)+'\n',file=path.join(home,'receipt-'+index+'.json');
  fs.writeFileSync(file,bytes,{flag:'wx'});
  process.stdout.write(JSON.stringify({scope:f.scope,workspaceId:result.isolation.workspaceId,
   evidenceSha256:crypto.createHash('sha256').update(bytes).digest('hex'),teardownConfirmed:result.isolation.teardownConfirmed,
   profileExitCode:result.status})+'\n');
 }else{
  // This label identifies only an already-derived canonical synthetic job.
  // Kill stops its fixed runner; the original executor remains responsible for
  // inspect/rm and records no success when its runner was killed.
  const ids=docker(['ps','--filter','label=continuum.workspace-job='+f.scope.jobId,'--format','{{.ID}}']).split('\n').filter(Boolean);
  if(verb==='cancel')for(const id of ids)docker(['kill',id]);
  process.stdout.write(JSON.stringify({active:ids.length,containerIds:ids})+'\n');
 }
}
