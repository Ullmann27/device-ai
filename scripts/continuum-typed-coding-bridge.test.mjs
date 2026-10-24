import test from 'node:test';
import assert from 'node:assert/strict';
import {canonical,sha256Hex} from '../.github/continuum/development-backlog.mjs';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {runCodingTask} from '../.github/continuum/coding-orchestrator.mjs';
import {createCodingCandidateProvider} from '../.github/continuum/coding-provider.mjs';
import {decodePreparedTask,fixedArgs,executeTypedBridge,BRIDGE_SCHEMA} from './continuum-typed-coding-bridge.mjs';

const SOURCE='a'.repeat(40);
function task(){return {
 schema:'continuum-coding-task',version:1,id:'job-bridge-test',
 sourceCommit:SOURCE,kind:'BUGFIX',goal:'Fix positive distance',
 allowedPaths:['lib/math.mjs'],acceptance:['Expected distance is correct'],
 chain:'node',limits:{maxModelCalls:2,maxContextChars:8192}
};}
function prepareArgs(t=task()){
 const json=canonical(t);
 return {op:'prepare',encoded:Buffer.from(json,'utf8').toString('base64url'),hash:sha256Hex(json)};
}
test('fixed command grammar rejects arbitrary CLI paths, shell and unbound run',()=>{
 assert.deepEqual(fixedArgs(['run','job-bridge-test','a'.repeat(64)]),{op:'run',id:'job-bridge-test',hash:'a'.repeat(64)});
 for(const bad of [['run','job-bridge-test'],['run','../../etc/passwd','a'.repeat(64)],['prepare','payload'],
  ['prepare','a','sha','--root','/tmp'],['execute','ls','-la'],['run','job-bridge-test','a'.repeat(64),'more']])
  assert.throws(()=>fixedArgs(bad),/CODING_BRIDGE_COMMAND_INVALID/);
});
test('strict canonical JSON and SHA binding prevent duplicate-key/changed-authorization replay',()=>{
 const args=prepareArgs();
 assert.equal(decodePreparedTask(args.encoded,args.hash).id,'job-bridge-test');
 assert.throws(()=>decodePreparedTask(args.encoded,'b'.repeat(64)),/CODING_BRIDGE_BINDING_INVALID/);
 const differentlyFormatted=Buffer.from(JSON.stringify(task()),'utf8').toString('base64url');
 assert.throws(()=>decodePreparedTask(differentlyFormatted,sha256Hex(JSON.stringify(task()))),/CODING_BRIDGE_BINDING_INVALID/);
 const malformed='{"schema":"continuum-coding-task","schema":"overridden"}';
 assert.throws(()=>decodePreparedTask(Buffer.from(malformed).toString('base64url'),sha256Hex(malformed)));
 for(const invalid of ['x','AA=A','/etc/passwd','e'.repeat(12001)])
  assert.throws(()=>decodePreparedTask(invalid,args.hash));
});
test('prepared confirmation is separate from execution; same bound task is idempotent',async()=>{
 const a=prepareArgs(),calls=[];
 const prepare=({task})=>{calls.push(task.id);return {id:task.id,ccc1TaskId:'t-'+'a'.repeat(16),fingerprint:a.hash,replayed:calls.length>1};};
 const one=await executeTypedBridge(a,{prepare});
 const two=await executeTypedBridge(a,{prepare});
 assert.equal(one.schema,BRIDGE_SCHEMA);assert.equal(one.status,'PREPARED_NOT_EXECUTED');
 assert.equal(one.deviceVerified,false);assert.equal(one.approvedForMerge,false);
 assert.equal(two.replayed,true);assert.deepEqual(calls,['job-bridge-test','job-bridge-test']);
});
test('run requires saved exact fingerprint and cannot claim installation',async()=>{
 const args={op:'run',id:'job-bridge-test',hash:'b'.repeat(64)};
 const result=await executeTypedBridge(args,{run:async()=>({
  id:args.id,taskFingerprint:args.hash,sourceCommit:SOURCE,status:'FAILED',
  receipts:[{status:'REPAIR_PATCH_VERIFIED_PENDING_REVIEW'}]
 })});
 assert.equal(result.status,'EXECUTION_REPORTED_UNVERIFIED');
 assert.equal(result.deviceVerified,false);
 const mismatch=await executeTypedBridge(args,{run:async()=>({
  id:args.id,taskFingerprint:'c'.repeat(64),sourceCommit:SOURCE,status:'COMPLETED',receipts:[]
 })});
 assert.equal(mismatch.status,'BLOCKED');assert.equal(mismatch.failure.code,'RUN_BINDING_MISMATCH');
});
test('failed host runtime redacts unexpected exception text',async()=>{
 const args=prepareArgs();
 const result=await executeTypedBridge(args,{prepare:()=>{throw Error('UNTRUSTED-SENSITIVE-DATA');}});
 assert.equal(result.status,'BLOCKED');
 assert.equal(result.failure.code,'PREPARE_FAILED');
 assert.equal(JSON.stringify(result).includes('UNTRUSTED-SENSITIVE-DATA'),false);
 const r=await executeTypedBridge({op:'run',id:'job-bridge-test',hash:'b'.repeat(64)},
  {run:async()=>{throw Error('UNTRUSTED-SENSITIVE-DATA');}});
 assert.equal(r.failure.code,'RUN_FAILED');
 assert.equal(JSON.stringify(r).includes('UNTRUSTED-SENSITIVE-DATA'),false);
});

test('actual CCC1 prepare and isolated coding repair are accessible through the typed Termux boundary',
 {skip:!!process.env.CONTINUUM_REPAIR_DEPTH},async()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'typed-android-ccc1-'));
 const root=path.join(dir,'repo'),state=path.join(dir,'state');
 fs.mkdirSync(path.join(root,'lib'),{recursive:true});
 fs.mkdirSync(path.join(root,'.github/continuum'),{recursive:true});
 fs.writeFileSync(path.join(root,'lib/offset.mjs'),'export function offset(a,b){return a-b;}\\n');
 fs.writeFileSync(path.join(root,'.github/continuum/offset.test.mjs'),
  "import test from 'node:test';import assert from 'node:assert/strict';import {offset} from '../../lib/offset.mjs';test('offset',()=>assert.equal(offset(3,8),5));\\n");
 const git=(...args)=>{const p=spawnSync('git',args,{cwd:root,encoding:'utf8'});
  assert.equal(p.status,0,p.stderr);return p.stdout.trim();};
 try{
  git('init','-q','-b','coding-android-fixture');git('add','.');
  git('-c','user.name=Fixture','-c','user.email=fixture@example.invalid','commit','-qm','broken baseline');
  const spec={...task(),id:'job-real-bridge',sourceCommit:git('rev-parse','HEAD'),allowedPaths:['lib/offset.mjs'],
   goal:'Fix offset symmetry',acceptance:['offset(3,8) equals five']};
  const args=prepareArgs(spec);
  const prep=await executeTypedBridge(args,{project:root,stateDir:state});
  assert.equal(prep.status,'PREPARED_NOT_EXECUTED');
  assert.match(prep.ccc1TaskId,/^t-[a-f0-9]{16}$/);
  const replay=await executeTypedBridge(args,{project:root,stateDir:state});
  assert.equal(replay.replayed,true);
  let calls=0;
  const completed=await executeTypedBridge({op:'run',id:spec.id,hash:args.hash},{
   project:root,stateDir:state,
   run:params=>runCodingTask({...params,providerFactory:({task:boundTask})=>
    createCodingCandidateProvider({root,task:boundTask,env:{CONTINUUM_LOCAL_BRAIN_API_KEY:'a'.repeat(40)},
     run:()=>{calls++;return {status:0,stdout:JSON.stringify({
      mode:'LOCAL_CODING_PATCH_PROPOSAL_V1',trusted:false,autoApply:false,
      proposal:{plan:['Fix negative ordering'],hypothesis:'Negative ordering is wrong',
       patches:[{file:'lib/offset.mjs',oldText:'a-b',newText:'a>b?a-b:b-a'}]}
     })};}})})
  });
  assert.equal(calls,1);
  assert.equal(completed.status,'PATCH_PENDING_OWNER_REVIEW');
  assert.match(completed.patchSha256,/^[a-f0-9]{64}$/);
  assert.equal(completed.installed,false);
  assert.equal(completed.approvedForMerge,false);
  assert.equal(git('status','--porcelain'),'');
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
});
