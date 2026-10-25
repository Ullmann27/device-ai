import '../.github/continuum/typed-android-coding-wiring.test.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import {canonical,sha256Hex} from '../.github/continuum/development-backlog.mjs';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {runCodingTask} from '../.github/continuum/coding-orchestrator.mjs';
import {jobTaskId,RECEIPT_SCHEMA,RECEIPT_VERSION} from './continuum-mobile-coding.mjs';
import {createCodingCandidateProvider} from '../.github/continuum/coding-provider.mjs';
import {decodePreparedTask,fixedArgs,executeTypedBridge,ownerCodingExecutor,verifiedCodingReviewReceipt,BRIDGE_SCHEMA} from './continuum-typed-coding-bridge.mjs';

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
 const result=await executeTypedBridge(args,{isolationLoader:()=>({mock:true}),run:async()=>({
  id:args.id,taskFingerprint:args.hash,sourceCommit:SOURCE,status:'FAILED',
  receipts:[{status:'REPAIR_PATCH_VERIFIED_PENDING_REVIEW'}]
 })});
 assert.equal(result.status,'EXECUTION_REPORTED_UNVERIFIED');
 assert.equal(result.deviceVerified,false);
 const mismatch=await executeTypedBridge(args,{isolationLoader:()=>({mock:true}),run:async()=>({
  id:args.id,taskFingerprint:'c'.repeat(64),sourceCommit:SOURCE,status:'COMPLETED',receipts:[]
 })});
 assert.equal(mismatch.status,'BLOCKED');assert.equal(mismatch.failure.code,'RUN_BINDING_MISMATCH');
 const receiptJob='11111111-1111-4111-8111-111111111111';
 const trustedReceipt={
  schema:RECEIPT_SCHEMA,version:RECEIPT_VERSION,job:receiptJob,taskId:jobTaskId(receiptJob),
  order:{chain:'node'},approvedForMerge:false,installed:false,deviceVerified:false,
  operation:'code-repair',status:'REPAIR_PATCH_VERIFIED_PENDING_REVIEW',
  commit:SOURCE,backlog:{state:'DONE'},patch:{sha256:'d'.repeat(64)},
  evidence:{verificationSha256:'e'.repeat(64),evidenceSufficient:true,
   baselineExecuted:3,baselineFailed:1,candidateExecuted:3,independentExecuted:3},
  commands:[{phase:'baseline',executed:3,failed:1,exitCode:1,passed:false},
   {phase:'candidate',executed:3,failed:0,exitCode:0,passed:true},
   {phase:'independent',executed:3,failed:0,exitCode:0,passed:true}]
 };
 const reviewResult={id:args.id,taskFingerprint:args.hash,sourceCommit:SOURCE,chain:'node',
  ccc1TaskId:'t-'+'f'.repeat(16),status:'PATCH_PENDING_OWNER_REVIEW',
  receiptJobs:[receiptJob],receipts:[trustedReceipt]};
 const verified=await executeTypedBridge(args,{isolationLoader:()=>({mock:true}),run:async()=>reviewResult});
 assert.equal(verified.status,'PATCH_PENDING_OWNER_REVIEW');
 assert.equal(verified.patchSha256,'d'.repeat(64));
 assert.equal(verified.approvedForMerge,false);assert.equal(verified.deviceVerified,false);
 // A correctly shaped receipt from a different CCC1 request must never be
 // promoted just because commit, chain and test counts happen to match.
 assert.equal(verifiedCodingReviewReceipt({...reviewResult,receiptJobs:[]}),null);
 assert.equal(verifiedCodingReviewReceipt({...reviewResult,receiptJobs:['22222222-2222-4222-8222-222222222222']}),null);
 assert.equal(verifiedCodingReviewReceipt({...reviewResult,receiptJobs:[receiptJob,receiptJob]}),null);
 assert.equal(verifiedCodingReviewReceipt({...reviewResult,receiptJobs:[receiptJob,'outside-uuid']}),null);
 const mismatchedJob='22222222-2222-4222-8222-222222222222';
 assert.equal(verifiedCodingReviewReceipt({...reviewResult,receipts:[
  {...trustedReceipt,job:mismatchedJob,taskId:jobTaskId(mismatchedJob)}
 ]}),null);
 const forged={...reviewResult,receipts:[{...trustedReceipt,evidence:{verificationSha256:'e'.repeat(64)}}]};
 assert.equal(verifiedCodingReviewReceipt(forged),null);
 assert.equal(verifiedCodingReviewReceipt({...reviewResult,receipts:[{...trustedReceipt,commit:'b'.repeat(40)}]}),null);
 // A believable patch hash is insufficient when the job identity, test
 // baseline, selected chain or independent execution counts disagree.
 for(const forgedReceipt of [
  {...trustedReceipt,schema:'arbitrary-receipt'},
  {...trustedReceipt,job:'not-a-v4-uuid'},
  {...trustedReceipt,taskId:'mob-'+'0'.repeat(16)},
  {...trustedReceipt,order:{chain:'android'}},
  {...trustedReceipt,approvedForMerge:true},
  {...trustedReceipt,evidence:{...trustedReceipt.evidence,baselineExecuted:0}},
  {...trustedReceipt,evidence:{...trustedReceipt.evidence,baselineFailed:2}},
  {...trustedReceipt,evidence:{...trustedReceipt.evidence,independentExecuted:4}},
  {...trustedReceipt,commands:[{...trustedReceipt.commands[0],passed:true},...trustedReceipt.commands.slice(1)]},
  {...trustedReceipt,commands:[trustedReceipt.commands[0],
   {...trustedReceipt.commands[1],executed:2},trustedReceipt.commands[2]]}
 ])assert.equal(verifiedCodingReviewReceipt({...reviewResult,receipts:[forgedReceipt]}),null);
 assert.equal(verifiedCodingReviewReceipt({...reviewResult,chain:'android'}),null);
 assert.equal(verifiedCodingReviewReceipt({...result,receipts:[{...trustedReceipt,commands:trustedReceipt.commands.slice(0,2)}]}),null);
 const falsePositive=await executeTypedBridge(args,{isolationLoader:()=>({mock:true}),run:async()=>forged});
 assert.equal(falsePositive.status,'EXECUTION_REPORTED_UNVERIFIED');
 assert.equal(falsePositive.patchSha256,null);
});
test('archived real Qwen repair receipt stays reviewable only with explicit bound chain',()=>{
 const archive=new URL('../docs/evidence/sol-autonomous-coding-20261024/first-model-run/result.json',import.meta.url);
 const evidence=JSON.parse(fs.readFileSync(archive,'utf8'));
 assert.equal(evidence.schema,'continuum-real-local-coding-experiment');
 assert.equal(evidence.result.status,'PATCH_PENDING_OWNER_REVIEW');
 const original=evidence.result.receipts[0];
 assert.equal(original.order.chain,'node');
 assert.equal(original.evidence.baselineFailed,4);
 assert.equal(original.evidence.baselineExecuted,12);
 assert.equal(original.evidence.candidateExecuted,12);
 assert.equal(original.evidence.independentExecuted,12);
 // The historical record predates the current explicit result.chain field.
 // Never infer it in production. Only the test supplies the known job order.
 assert.equal(verifiedCodingReviewReceipt(evidence.result),null);
 // This archived record predates receiptJobs; only this test binds its recorded job explicitly.
 const pinned={...evidence.result,chain:original.order.chain,receiptJobs:[original.job]};
 assert.equal(verifiedCodingReviewReceipt(pinned),original.patch.sha256);
 for(const mutation of [
  {...original,order:{chain:'android'}},
  {...original,taskId:'mob-'+'f'.repeat(16)},
  {...original,evidence:{...original.evidence,candidateExecuted:11}},
  {...original,commands:[...original.commands.slice(0,2),{...original.commands[2],failed:1}]}
 ])assert.equal(verifiedCodingReviewReceipt({...pinned,receipts:[mutation]}),null);
});

test('failed host runtime redacts unexpected exception text',async()=>{
 const args=prepareArgs();
 const result=await executeTypedBridge(args,{prepare:()=>{throw Error('UNTRUSTED-SENSITIVE-DATA');}});
 assert.equal(result.status,'BLOCKED');
 assert.equal(result.failure.code,'PREPARE_FAILED');
 assert.equal(JSON.stringify(result).includes('UNTRUSTED-SENSITIVE-DATA'),false);
 const r=await executeTypedBridge({op:'run',id:'job-bridge-test',hash:'b'.repeat(64)},
  {isolationLoader:()=>({mock:true}),run:async()=>{throw Error('UNTRUSTED-SENSITIVE-DATA');}});
 assert.equal(r.failure.code,'RUN_FAILED');
 assert.equal(JSON.stringify(r).includes('UNTRUSTED-SENSITIVE-DATA'),false);
});

test('actual CCC1 preparation and execution through typed Termux boundary preserve fail-closed verification',
 {skip:!!process.env.CONTINUUM_REPAIR_DEPTH},async()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'typed-android-ccc1-'));
 const root=path.join(dir,'repo'),state=path.join(dir,'state');
 fs.mkdirSync(path.join(root,'lib'),{recursive:true});
 fs.mkdirSync(path.join(root,'.github/continuum'),{recursive:true});
 fs.writeFileSync(path.join(root,'lib/offset.mjs'),'export function offset(a,b){return a-b;}\n');
 fs.writeFileSync(path.join(root,'.github/continuum/offset.test.mjs'),
  "import test from 'node:test';import assert from 'node:assert/strict';import {offset} from '../../lib/offset.mjs';test('offset',()=>assert.equal(offset(3,8),5));\n");
 // The synthetic fixture must be executable source, not literal backslash-n text.
 for(const fixture of ['lib/offset.mjs','.github/continuum/offset.test.mjs']){
  const syntax=spawnSync(process.execPath,['--check',path.join(root,fixture)],{encoding:'utf8'});
  assert.equal(syntax.status,0,syntax.stderr);
 }
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
  let modelCalls=0;
  const completed=await executeTypedBridge({op:'run',id:spec.id,hash:args.hash},{
   project:root,stateDir:state,
   isolationLoader:()=>{throw Error('ISOLATION_UNAVAILABLE');},
   run:async()=>{modelCalls++;throw Error('UNSAFE_MODEL_INVOKED');}
  });
  assert.equal(completed.status,'BLOCKED');
  assert.equal(completed.failure.code,'CODING_ISOLATION_UNAVAILABLE');
  assert.equal(modelCalls,0,'missing sandbox must never start untrusted model code');
  await assert.rejects(runCodingTask({project:root,stateDir:state,id:spec.id,
   requireIsolation:true}),/CODING_ISOLATION_REQUIRED/);
  assert.equal(fs.existsSync(path.join(state,'coding-grants',spec.id+'.json')),true);
  // Wrong owner-provided hash must be rejected while the journal and model
  // invocation remain untouched. This check does not need a local sandbox.
  await assert.rejects(runCodingTask({project:root,stateDir:state,id:spec.id,
   expectedTaskFingerprint:'e'.repeat(64),
   providerFactory:()=>{modelCalls++;throw Error('MODEL_MUST_NOT_RUN');}
  }),/CODING_TASK_BINDING_MISMATCH/);
  assert.equal(modelCalls,0,'wrong SHA must never call the model');
  const grantPath=path.join(state,'coding-grants',spec.id+'.json');
  const originalGrant=fs.readFileSync(grantPath,'utf8');
  const damaged=JSON.parse(originalGrant);
  damaged.ccc1TaskId='t-not-a-valid-id';
  fs.writeFileSync(grantPath,JSON.stringify(damaged));
  await assert.rejects(runCodingTask({project:root,stateDir:state,id:spec.id,
   expectedTaskFingerprint:args.hash,
   providerFactory:()=>{modelCalls++;throw Error('MODEL_MUST_NOT_RUN');}
  }),/CODING_GRANT_CORRUPT/);
  fs.writeFileSync(grantPath,originalGrant);
  fs.appendFileSync(path.join(root,'lib/offset.mjs'),'// local uncommitted change\n');
  await assert.rejects(runCodingTask({project:root,stateDir:state,id:spec.id,
   expectedTaskFingerprint:args.hash,
   providerFactory:()=>{modelCalls++;throw Error('MODEL_MUST_NOT_RUN');}
  }),/CODING_TASK_DIRTY_SOURCE/);
  assert.equal(modelCalls,0,'corrupt grant and dirty checkout must never call model');
  fs.writeFileSync(path.join(root,'lib/offset.mjs'),'export function offset(a,b){return a-b;}\n');
  assert.equal(git('status','--porcelain'),'');
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
});

test('trusted isolation configuration is mandatory and unsafe paths are rejected',()=>{
 assert.throws(()=>ownerCodingExecutor({project:'/tmp',env:{}}),/CODING_ISOLATION_UNAVAILABLE/);
 assert.throws(()=>ownerCodingExecutor({project:'/tmp',env:{
  CONTINUUM_CODING_ISOLATION_CONFIG:'./relative/config.json'
 }}),/CODING_ISOLATION_UNAVAILABLE/);
 assert.throws(()=>ownerCodingExecutor({project:'/tmp',env:{
  CONTINUUM_CODING_ISOLATION_CONFIG:'/nonexistent/continuum/owner-config.json'
 }}),/CODING_ISOLATION_UNAVAILABLE/);
});
test('sandbox absence blocks before any entrusted run callback and does not claim verified repair',async()=>{
 let callbacks=0;
 const request={op:'run',id:'job-bridge-test',hash:'c'.repeat(64)};
 const result=await executeTypedBridge(request,{
  isolationLoader:()=>{throw Error('CONFIDENTIAL_DO_NOT_LEAK');},
  run:async()=>{callbacks++;return {status:'PATCH_PENDING_OWNER_REVIEW'};}
 });
 assert.equal(callbacks,0);
 assert.equal(result.status,'BLOCKED');
 assert.equal(result.failure.code,'CODING_ISOLATION_UNAVAILABLE');
 assert.equal(JSON.stringify(result).includes('CONFIDENTIAL_DO_NOT_LEAK'),false);
});
test('the protected runner receives an explicit strict-isolation requirement',async()=>{
 const args={op:'run',id:'job-bridge-test',hash:'d'.repeat(64)};
 let params;
 const r=await executeTypedBridge(args,{
  isolationLoader:()=>({dummy:'test fixture only'}),
  run:async options=>{params=options;return {
   id:args.id,sourceCommit:SOURCE,taskFingerprint:args.hash,
   status:'ABSTAINED',receipts:[]};}
 });
 assert.equal(params.requireIsolation,true);
 assert.equal(params.executorAbortMode,'CHECKPOINT_BOUNDARY_ONLY');
 assert.equal(params.expectedTaskFingerprint,args.hash);
 assert.equal(r.status,'EXECUTION_REPORTED_UNVERIFIED');
 assert.equal(r.patchSha256,null);
});
