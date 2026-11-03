import test from 'node:test';import assert from 'node:assert/strict';
import fs from 'node:fs';import os from 'node:os';import path from 'node:path';import {pathToFileURL} from 'node:url';
const target=process.env.WORKSPACE_REVIEW_TARGET;if(!target)throw Error('WORKSPACE_REVIEW_TARGET required');
const module=async name=>import(pathToFileURL(path.join(target,'.github/continuum',name)));
const {createIsolatedExecutor,validWorkspaceEvidence}=await module('isolated-executor.mjs');
const {bindCcc1Workspace}=await module('ccc1-workspace.mjs');
const {workspaceFixture}=await module('ccc1-workspace-fixture.mjs');
const {ownerAction}=await module('ccc1-orchestrator.mjs');
const {BacklogStore}=await module('development-backlog.mjs');
const {jobTaskId}=await import(pathToFileURL(path.join(target,'scripts/continuum-mobile-coding.mjs')));
const config={image:'sha256:3f44842e8a052beda7e358b9fb8d8acc3e180a7f58aaceff7e1b49ad7f3d46e1',nodeHome:'/opt/codex/runtimes/codex-primary-runtime/dependencies/node',memoryMb:256,pids:64,cpus:1,timeoutMs:5000};
const source="import test from 'node:test';import assert from 'node:assert/strict';test('independent claimed fixed profile',()=>assert.equal(2+2,4));";
function fixture(run){
 const home=fs.mkdtempSync(path.join(os.tmpdir(),'independent-claim-'));
 try{
  const f=workspaceFixture(home,'worker-review',{testSource:source}),out=path.join(home,'evidence');
  const input={executor:createIsolatedExecutor(config),project:f.root,stateDir:f.stateDir,taskId:f.taskId,job:f.job,workerId:f.workerId};
  const scoped=bindCcc1Workspace(input),backlog=path.join(f.stateDir,'teamleader/development-backlog.json'),id=jobTaskId(f.job),token='a'.repeat(32),at=Date.now();
  const store=new BacklogStore(backlog,{actor:'independent-review'});
  const ops=[{op:'propose',actor:'independent-review',at,task:{id,title:'independent owner repair fixture',kind:'REPAIR',source:'owner',subject:{base:f.journal.entries()[0].baseCommitSha,chain:'node'}}},{op:'accept',actor:'independent-review',at:at+1,id},{op:'claim',actor:'independent-review',at:at+2,id,owner:'independent-review',token,leaseMs:60000}];
  store.apply(ops);const claim={backlog,id,token};
  return run({...f,home,out,input,scoped,store,claim,at});
 }finally{fs.rmSync(home,{recursive:true,force:true});}
}
const execute=f=>f.scoped.forClaim(f.claim).execute('node-continuum',f.root,f.out);
function passed(r){assert.equal(r.status,0);assert.equal(validWorkspaceEvidence(r.isolation,'node-continuum'),true);console.log(JSON.stringify({status:r.status,isolation:r.isolation}));}
test('unclaimed scoped executor always refuses execution',()=>fixture(f=>assert.throws(()=>f.scoped.execute('node-continuum',f.root,f.out))));
test('canonical live claim permits multiple fixed verification profiles',()=>fixture(f=>{const active=f.scoped.forClaim(f.claim);const a=active.execute('node-continuum',f.root,f.out),b=active.execute('node-continuum',f.root,f.out);passed(a);passed(b);assert.notEqual(a.isolation.workspaceId,b.isolation.workspaceId);}));
test('wrong claim token cannot activate execution',()=>fixture(f=>assert.throws(()=>f.scoped.forClaim({...f.claim,token:'b'.repeat(32)}))));
test('wrong canonical job cannot activate execution',()=>fixture(f=>assert.throws(()=>f.scoped.forClaim({...f.claim,id:'owner-repair-other'}))));
test('copied backlog outside canonical state cannot activate execution',()=>fixture(f=>{const other=path.join(f.home,'copied-backlog.json');fs.copyFileSync(f.claim.backlog,other);assert.throws(()=>f.scoped.forClaim({...f.claim,backlog:other}));}));
test('claim activated before canonical owner ABORT cannot execute',()=>fixture(f=>{const active=f.scoped.forClaim(f.claim);ownerAction({stateDir:f.stateDir,taskId:f.taskId,kind:'ABORT',payload:{code:'OWNER',reason:'independent revoke'}});assert.throws(()=>active.execute('node-continuum',f.root,f.out));}));
test('claim activated before MAC corruption cannot execute',()=>fixture(f=>{const active=f.scoped.forClaim(f.claim);fs.writeFileSync(f.journal.file,fs.readFileSync(f.journal.file,'utf8').replace('synthetic isolation','tampered isolation'));assert.throws(()=>active.execute('node-continuum',f.root,f.out));}));
test('claim activated before canonical source mutation cannot execute',()=>fixture(f=>{const active=f.scoped.forClaim(f.claim);fs.writeFileSync(path.join(f.root,'worker-review.txt'),'modified');assert.throws(()=>active.execute('node-continuum',f.root,f.out));}));
test('claim activated before backlog abort cannot execute or reactivate',()=>fixture(f=>{const active=f.scoped.forClaim(f.claim);f.store.apply([{op:'abort',actor:'independent-review',at:Date.now()+5,id:f.claim.id,reason:'independent stop'}]);assert.throws(()=>active.execute('node-continuum',f.root,f.out));assert.throws(()=>f.scoped.forClaim(f.claim));}));
test('controlled-clock expired lease cannot execute',()=>fixture(f=>{const active=f.scoped.forClaim(f.claim),realNow=Date.now;try{Date.now=()=>f.at+61000;assert.throws(()=>active.execute('node-continuum',f.root,f.out));}finally{Date.now=realNow;}}));
test('controlled-clock expired goal cannot execute even if lease were extended',()=>fixture(f=>{const active=f.scoped.forClaim(f.claim),realNow=Date.now;try{Date.now=()=>f.journal.entries()[0].deadline+1;assert.throws(()=>active.execute('node-continuum',f.root,f.out));}finally{Date.now=realNow;}}));
test('claim cannot expand fixed chain or introduce arbitrary shell profile',()=>fixture(f=>{const active=f.scoped.forClaim(f.claim);for(const profile of ['node-backend','sh -c echo escaped','android-build'])assert.throws(()=>active.execute(profile,f.root,f.out));}));
