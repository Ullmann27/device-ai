/** Explicit opt-in real Docker proof. No pull/build/download or model inference. */
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';import os from 'node:os';import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {Worker,isMainThread,parentPort,workerData} from 'node:worker_threads';
import {createIsolatedExecutor,isIsolatedExecutor,validWorkspaceEvidence} from './isolated-executor.mjs';
import {bindCcc1Workspace} from './ccc1-workspace.mjs';
import {workspaceFixture,claimWorkspaceFixture} from './ccc1-workspace-fixture.mjs';
import {BacklogStore} from './development-backlog.mjs';
import {jobTaskId} from '../../scripts/continuum-mobile-coding.mjs';

const IMAGE='sha256:3f44842e8a052beda7e358b9fb8d8acc3e180a7f58aaceff7e1b49ad7f3d46e1';
const config={image:IMAGE,nodeHome:'/opt/codex/runtimes/codex-primary-runtime/dependencies/node',memoryMb:256,pids:64,cpus:1,timeoutMs:15000};
function docker(args){const env={...process.env};for(const k of ['DOCKER_HOST','DOCKER_CONTEXT','DOCKER_TLS','DOCKER_TLS_VERIFY','DOCKER_CERT_PATH'])delete env[k];
 return spawnSync('docker',['--host=unix:///var/run/docker.sock',...args],{encoding:'utf8',env,timeout:15000});}

if(!isMainThread){
 try{
  const executor=createIsolatedExecutor(config);
  const scoped=bindCcc1Workspace({...workerData,project:workerData.root,executor});
  parentPort.postMessage({ready:true,branded:isIsolatedExecutor(scoped)});
  const active=scoped.forClaim(claimWorkspaceFixture(workerData));
  const result=active.execute('node-continuum',workerData.root,workerData.out);
  parentPort.postMessage({result});
 }catch(error){parentPort.postMessage({error:error.stack});}
}else{
 test('HOST_INTEGRATION: two canonical worker jobs run in separate confined Docker workspaces',
  {skip:process.env.CONTINUUM_WORKSPACE_HOST_PROOF!=='1',timeout:60000},async()=>{
  const home=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-workspaces-host-'));
  const out=process.env.CONTINUUM_WORKSPACE_EVIDENCE_DIR??path.join(home,'evidence');
  fs.mkdirSync(out,{recursive:true});
  const workers=[];
  try{
   const fixtures=['worker-alpha','worker-beta'].map(id=>workspaceFixture(home,id));
   for(const [i,f] of fixtures.entries()){
    const other=fixtures[1-i];
    const source=`import test from 'node:test';import assert from 'node:assert/strict';import fs from 'node:fs';import net from 'node:net';
    test('actual filesystem namespace, no network and cgroup limits',async()=>{
     assert.equal(fs.readFileSync('/work/${f.workerId}.txt','utf8'),'${f.workerId}');
     fs.writeFileSync('/scratch/${f.workerId}.txt','private workspace');
     for(const p of ['/work/${other.workerId}.txt','${other.root}/${other.workerId}.txt','/workspace/momega1-chief/AGENTS.md','/var/run/docker.sock']){
      assert.throws(()=>fs.readFileSync(p),{code:'ENOENT'});
      assert.throws(()=>fs.writeFileSync(p,'cross-write'),e=>['EROFS','ENOENT','EACCES'].includes(e.code));
     }
     assert.throws(()=>fs.readFileSync('/scratch/${other.workerId}.txt'),{code:'ENOENT'});
     fs.writeFileSync('/scratch/${other.workerId}.txt','private alias, cannot mutate peer namespace');
     assert.throws(()=>fs.writeFileSync('/work/${f.workerId}.txt','mutated'),e=>['EROFS','EACCES'].includes(e.code));
     assert.equal(fs.readFileSync('/sys/fs/cgroup/memory.max','utf8').trim(),'268435456');
     assert.equal(fs.readFileSync('/sys/fs/cgroup/pids.max','utf8').trim(),'64');
     const [quota,period]=fs.readFileSync('/sys/fs/cgroup/cpu.max','utf8').trim().split(/\\s+/).map(Number);
     assert.equal(quota/period,1);
     await new Promise((resolve,reject)=>{const s=net.connect({host:'1.1.1.1',port:443});s.setTimeout(1000);s.once('connect',()=>{s.destroy();reject(Error('network escaped'));});s.once('error',e=>{assert.ok(['ENETUNREACH','EHOSTUNREACH','EACCES','EPERM'].includes(e.code),e.code);resolve();});s.once('timeout',()=>{s.destroy();reject(Error('network not explicitly blocked'));});});
     await new Promise(resolve=>setTimeout(resolve,3000));
     assert.equal(fs.readFileSync('/scratch/${f.workerId}.txt','utf8'),'private workspace');
    });`;
    fs.writeFileSync(path.join(f.root,'.github/continuum/isolation.test.mjs'),source);
    f.git('add','.');f.git('-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','add fixed-profile adversarial probes');
    // Recreate journal at this exact committed source; fixture tasks are synthetic, not owner grants.
    fs.rmSync(f.stateDir,{recursive:true,force:true});
    const {startTask}=await import('./ccc1-orchestrator.mjs');
    const {DurableTaskJournal,runtimeKey}=await import('./ccc1-journal.mjs');
    const {envelope}=await import('./ccc1-envelope.mjs');
    const {jobIdFor}=await import('./ccc1-recovery.mjs');
    f.taskId=startTask({project:f.root,stateDir:f.stateDir,repoFullName:'local/workspace-fixture',goal:{text:'synthetic host fixture',repair:{chain:'node'}},permissions:['node-repair:node']});
    f.journal=DurableTaskJournal.forTask(f.stateDir,f.taskId,runtimeKey(f.stateDir));const goal=f.journal.entries()[0];
    const h=f.journal.append(envelope({binding:goal,sender:'REASONER',kind:'HYPOTHESIS',payload:{claim:'probe sandbox',repair:{chain:'node'},basis:'fixture'},sequence:1})).entry;
    f.request=f.journal.append(envelope({binding:goal,sender:'REASONER',kind:'TEST_REQUEST',payload:{hypothesis:h.messageId,repair:{chain:'node'},budgetMinutes:1},sequence:2})).entry;
    f.job=jobIdFor(f.taskId,f.request.messageId);
   }
   const executor=createIsolatedExecutor(config),f=fixtures[0];
   const input={executor,project:f.root,stateDir:f.stateDir,taskId:f.taskId,job:f.job,workerId:f.workerId};
   assert.throws(()=>bindCcc1Workspace({...input,job:fixtures[1].job}),/REQUEST_MISMATCH/);
   const scoped=bindCcc1Workspace(input);assert.equal(isIsolatedExecutor(scoped),true);
   assert.throws(()=>scoped.execute('node-backend',f.root,out),/WORKSPACE_PROFILE_DENIED/);
   assert.throws(()=>scoped.execute('node-continuum',fixtures[1].root,out),/CLAIM_REQUIRED/);
   const timeoutBase=bindCcc1Workspace({...input,executor:createIsolatedExecutor({...config,timeoutMs:1000})});
   const raw=fs.readFileSync(f.journal.file,'utf8');fs.appendFileSync(f.journal.file,'{"torn":');
   assert.throws(()=>bindCcc1Workspace(input),/torn last line/);fs.writeFileSync(f.journal.file,raw);
   const results=fixtures.map(f=>new Promise((resolve,reject)=>{
    const w=new Worker(new URL(import.meta.url),{workerData:{root:f.root,stateDir:f.stateDir,taskId:f.taskId,job:f.job,workerId:f.workerId,out}});workers.push(w);
    w.on('message',m=>{if(m.error)reject(Error(m.error));if(m.ready)assert.equal(m.branded,true);if(m.result)resolve(m.result);});
    w.on('error',reject);w.on('exit',code=>{if(code!==0)reject(Error('worker exit '+code));});
   }));
   let simultaneous=[];
   for(let n=0;n<40;n++){
    // Other independent suites may start containers concurrently. Observe only these two jobs.
    const ids=fixtures.flatMap(f=>{
     const listed=docker(['ps','--filter','label=continuum.workspace-job='+f.job,'--format','{{.ID}}']);
     assert.equal(listed.status,0,listed.stderr);
     return listed.stdout.trim().split('\n').filter(Boolean);
    });
    if(ids.length>=2){simultaneous=ids;break;}
    await new Promise(resolve=>setTimeout(resolve,100));
   }
   const done=await Promise.all(results);assert.ok(simultaneous.length>=2,'two real containers observed simultaneously');
   assert.notEqual(done[0].isolation.workspaceId,done[1].isolation.workspaceId);
   assert.notEqual(done[0].isolation.sourceSha256,done[1].isolation.sourceSha256);
   for(const [i,r] of done.entries()){
    assert.equal(r.status,0,r.stdout);assert.equal(r.isolation.teardownConfirmed,true);assert.equal(validWorkspaceEvidence(r.isolation,'node-continuum'),true);
    assert.equal(r.isolation.workspaceBinding.workerId,fixtures[i].workerId);
    assert.equal(r.isolation.workspaceBinding.requestHash,fixtures[i].request.hash);
    assert.ok(simultaneous.some(id=>r.isolation.observed.containerId.startsWith(id)));
    const gone=docker(['container','inspect',r.isolation.workspaceId]);assert.notEqual(gone.status,0);
    assert.equal(fs.readFileSync(path.join(fixtures[i].root,fixtures[i].workerId+'.txt'),'utf8'),fixtures[i].workerId);
    fs.writeFileSync(path.join(out,fixtures[i].workerId+'-receipt.json'),JSON.stringify(r,null,2)+'\n');
   }
   // A hanging fixed profile is killed at the configured outer deadline and
   // receives no success receipt; inspect only this fixture job after cleanup.
   const beforeTimeout=docker(['ps','-a','--filter','label=continuum.workspace-job='+f.job,'--format','{{.ID}}']);
   assert.equal(beforeTimeout.status,0,beforeTimeout.stderr);
   assert.equal(beforeTimeout.stdout.trim(),'','completed fixture job must already be gone');
   const existingClaim=new BacklogStore(path.join(f.stateDir,'teamleader/development-backlog.json')).read().doc.tasks.find(t=>t.id===jobTaskId(f.job));
   const shortScoped=timeoutBase.forClaim({backlog:path.join(f.stateDir,'teamleader/development-backlog.json'),id:existingClaim.id,token:existingClaim.claim.token});
   const timeoutStart=Date.now();
   assert.throws(()=>shortScoped.execute('node-continuum',f.root,out),/ISOLATION_EXECUTION_FAILED/);
   const timeoutElapsedMs=Date.now()-timeoutStart;
   assert.ok(timeoutElapsedMs<10000,'outer time cap bounded the three-second fixture');
   const afterTimeout=docker(['ps','-a','--filter','label=continuum.workspace-job='+f.job,'--format','{{.ID}}']);
   assert.equal(afterTimeout.status,0,afterTimeout.stderr);assert.equal(afterTimeout.stdout.trim(),'','timed-out fixture job must leave no container');
   // Recovery authority remains the existing canonical backlog/journal.
   assert.throws(()=>bindCcc1Workspace(input),/RECOVERY_BLOCKED: REQUIRE_OWNER_APPROVAL/);
   fs.writeFileSync(path.join(out,'summary.json'),JSON.stringify({schema:'momega1-workspaces-host-v1',simultaneous,
    containerIds:done.map(r=>r.isolation.observed.containerId),fixedProfile:'node-continuum',
    runtime:'runc',actualModelInference:false,deviceVerified:false,interruptedReplayBlocked:true,timeoutEnforced:{limitMs:1000,elapsedMs:timeoutElapsedMs,teardownConfirmed:true}},null,2)+'\n');
  }finally{await Promise.all(workers.map(w=>w.terminate()));fs.rmSync(home,{recursive:true,force:true});}
 });
}
