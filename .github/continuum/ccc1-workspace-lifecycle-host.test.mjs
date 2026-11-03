import test from 'node:test';import assert from 'node:assert/strict';
import fs from 'node:fs';import os from 'node:os';import path from 'node:path';
import {createIsolatedExecutor} from './isolated-executor.mjs';
import {bindCcc1Workspace} from './ccc1-workspace.mjs';
import {workspaceFixture} from './ccc1-workspace-fixture.mjs';
import {localTeamleaderDriver} from './autonomous-teamleader.mjs';
import {runMobileRepairJob} from '../../scripts/continuum-mobile-coding.mjs';
import {jobStatus} from './ccc1-recovery.mjs';

const config={image:'sha256:3f44842e8a052beda7e358b9fb8d8acc3e180a7f58aaceff7e1b49ad7f3d46e1',
 nodeHome:'/opt/codex/runtimes/codex-primary-runtime/dependencies/node',memoryMb:256,pids:64,cpus:1,timeoutMs:10000};
test('HOST_INTEGRATION: actual canonical worker claim admits RED, repaired GREEN and independent Docker runs',
 {skip:process.env.CONTINUUM_WORKSPACE_HOST_PROOF!=='1',timeout:60000},async()=>{
 const home=fs.mkdtempSync(path.join(os.tmpdir(),'workspace-canonical-repair-'));
 try{
  const workerId='worker-canonical',root=path.join(home,workerId),dir=path.join(root,'.github/continuum');fs.mkdirSync(dir,{recursive:true});
  const good='/** Slug of a title for file names. */\nexport function slug(title){\n  return title.trim().toLowerCase().replace(/\\s+/g,"-");\n}\n';
  fs.writeFileSync(path.join(dir,'text-utils.mjs'),good);
  const initial=workspaceFixture(home,workerId,{testSource:"import test from 'node:test';import assert from 'node:assert/strict';import {slug} from './text-utils.mjs';test('slug lower case',()=>assert.equal(slug(' Hallo Welt '),'hallo-welt'));"});
  fs.writeFileSync(path.join(dir,'text-utils.mjs'),good.replace('toLowerCase','toUpperCase'));
  const f=workspaceFixture(home,workerId);
  const input={executor:createIsolatedExecutor(config),project:f.root,stateDir:f.stateDir,taskId:f.taskId,job:f.job,workerId};
  const bound=bindCcc1Workspace(input);
  assert.throws(()=>bound.execute('node-continuum',root,path.join(home,'forbidden')),/CLAIM_REQUIRED/);
  const driver=await localTeamleaderDriver({executor:bound,executorAbortMode:'CHECKPOINT_BOUNDARY_ONLY'});
  const receipt=await runMobileRepairJob({project:root,builderState:path.join(f.stateDir,'builder'),teamleaderState:path.join(f.stateDir,'teamleader'),
   job:f.job,chain:'node',budgetMinutes:1,driver});
  const out=process.env.CONTINUUM_WORKSPACE_EVIDENCE_DIR;
  if(out){fs.mkdirSync(out,{recursive:true});fs.writeFileSync(path.join(out,'canonical-repair-receipt.json'),JSON.stringify(receipt,null,2)+'\n');
   const reports=path.join(f.stateDir,'teamleader/reports/owner-repair');
   for(const folder of fs.readdirSync(reports)){const report=path.join(reports,folder,'report.json');if(fs.existsSync(report))fs.copyFileSync(report,path.join(out,'canonical-repair-report.json'));}}
  assert.equal(receipt.status,'REPAIR_PATCH_VERIFIED_PENDING_REVIEW',JSON.stringify(receipt));
  assert.ok(receipt.commands.find(c=>c.phase==='baseline').failed>0);
  assert.equal(receipt.commands.find(c=>c.phase==='candidate').failed,0);
  assert.equal(receipt.commands.find(c=>c.phase==='independent').failed,0);
  assert.equal(jobStatus(f.stateDir,f.job),'FINISHED_UNRECORDED');
  assert.throws(()=>bindCcc1Workspace(input),/RECORD_RECEIPT_BY_REPLAY/);
  const replay=await runMobileRepairJob({project:root,builderState:path.join(f.stateDir,'builder'),teamleaderState:path.join(f.stateDir,'teamleader'),job:f.job,chain:'node',budgetMinutes:1,driver});
  assert.equal(replay.replayed,true);
 }finally{fs.rmSync(home,{recursive:true,force:true});}
});

test('HOST_INTEGRATION: live canonical lease clamps the real Docker timeout',
 {skip:process.env.CONTINUUM_WORKSPACE_HOST_PROOF!=='1',timeout:15000},async()=>{
 const home=fs.mkdtempSync(path.join(os.tmpdir(),'workspace-lease-timeout-'));
 const realNow=Date.now;
 try{
  const {claimWorkspaceFixture}=await import('./ccc1-workspace-fixture.mjs');
  const {BacklogStore}=await import('./development-backlog.mjs');
  const {spawnSync}=await import('node:child_process');
  const f=workspaceFixture(home,'worker-deadline',{testSource:"import test from 'node:test';test('bounded slow profile',async()=>new Promise(resolve=>setTimeout(resolve,3000)));"});
  const bound=bindCcc1Workspace({executor:createIsolatedExecutor(config),project:f.root,stateDir:f.stateDir,taskId:f.taskId,job:f.job,workerId:f.workerId});
  const claim=claimWorkspaceFixture(f),active=bound.forClaim(claim);
  const task=new BacklogStore(claim.backlog).read().doc.tasks.find(t=>t.id===claim.id);
  Date.now=()=>task.claim.leaseUntil-400;
  const started=process.hrtime.bigint();
  assert.throws(()=>active.execute('node-continuum',f.root,path.join(home,'evidence')),/ISOLATION_EXECUTION_FAILED/);
  const elapsedMs=Number(process.hrtime.bigint()-started)/1e6;
  assert.ok(elapsedMs<2500,'live lease remainder, not ten-second executor cap, bounds actual run');
  Date.now=realNow;
  const env={...process.env};for(const key of ['DOCKER_HOST','DOCKER_CONTEXT','DOCKER_TLS','DOCKER_TLS_VERIFY','DOCKER_CERT_PATH'])delete env[key];
  const listed=spawnSync('docker',['--host=unix:///var/run/docker.sock','ps','-a','--filter','label=continuum.workspace-job='+f.job,'--format','{{.ID}}'],{encoding:'utf8',env});
  assert.equal(listed.status,0);assert.equal(listed.stdout.trim(),'');
  const out=process.env.CONTINUUM_WORKSPACE_EVIDENCE_DIR;
  if(out)fs.writeFileSync(path.join(out,'lease-timeout.json'),JSON.stringify({controlledClock:true,remainderMs:400,configuredTimeoutMs:10000,elapsedMs,teardownConfirmed:true},null,2)+'\n');
 }finally{Date.now=realNow;fs.rmSync(home,{recursive:true,force:true});}
});

test('HOST_INTEGRATION: in-flight owner revocation suppresses the completed profile result at checkpoint',
 {skip:process.env.CONTINUUM_WORKSPACE_HOST_PROOF!=='1',timeout:15000},async()=>{
 const home=fs.mkdtempSync(path.join(os.tmpdir(),'workspace-inflight-revocation-'));
 try{
  const {claimWorkspaceFixture}=await import('./ccc1-workspace-fixture.mjs');
  const {spawn}=await import('node:child_process');
  const f=workspaceFixture(home,'worker-revocation',{testSource:"import test from 'node:test';test('in-flight bounded profile',async()=>new Promise(resolve=>setTimeout(resolve,1500)));"});
  const bound=bindCcc1Workspace({executor:createIsolatedExecutor(config),project:f.root,stateDir:f.stateDir,taskId:f.taskId,job:f.job,workerId:f.workerId});
  const claim=claimWorkspaceFixture(f),active=bound.forClaim(claim);
  // Trusted test control, never model shell text: another process invokes the
  // existing canonical backlog abort while the synchronous Docker step runs.
  const source="import {BacklogStore} from "+JSON.stringify(new URL('./development-backlog.mjs',import.meta.url).href)+
   ";setTimeout(()=>new BacklogStore("+JSON.stringify(claim.backlog)+").apply([{op:'abort',actor:'owner',at:Date.now(),id:"+JSON.stringify(claim.id)+",reason:'host revocation probe'}]),400);";
  const child=spawn(process.execPath,['--input-type=module','-e',source],{stdio:'ignore'});
  const closed=new Promise((resolve,reject)=>{child.once('error',reject);child.once('exit',(code,signal)=>resolve({code,signal}));});
  assert.throws(()=>active.execute('node-continuum',f.root,path.join(home,'evidence')),/WORKSPACE_CLAIM_MISMATCH/);
  assert.deepEqual(await closed,{code:0,signal:null});
  const out=process.env.CONTINUUM_WORKSPACE_EVIDENCE_DIR;
  if(out)fs.writeFileSync(path.join(out,'inflight-revocation.json'),JSON.stringify({cancellation:'CHECKPOINT_BOUNDARY_ONLY',revokedDuringProfile:true,profileResultAdmitted:false},null,2)+'\n');
 }finally{fs.rmSync(home,{recursive:true,force:true});}
});
