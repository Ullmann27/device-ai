import test from 'node:test';
import assert from 'node:assert/strict';
import {canonical,sha256Hex} from '../.github/continuum/development-backlog.mjs';
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
