#!/usr/bin/env node
/**
 * Continuum Android -> Termux fixed, owner-confirmed typed coding bridge.
 * Android invokes only this file via RUN_COMMAND. No shell interpretation,
 * remote calls, direct Git writes, merge, install, APK or model promotion.
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {canonical,sha256Hex,parseStrict} from '../.github/continuum/development-backlog.mjs';
import {validateCodingTask,taskFingerprint} from '../.github/continuum/coding-task.mjs';
import {prepareCodingTask,runCodingTask} from '../.github/continuum/coding-orchestrator.mjs';
import {loadIsolationConfig,createIsolatedExecutor} from '../.github/continuum/isolated-executor.mjs';
import {JOB_RE,jobTaskId,RECEIPT_SCHEMA,RECEIPT_VERSION} from './continuum-mobile-coding.mjs';

export const BRIDGE_SCHEMA='continuum-typed-coding-bridge';
export const BRIDGE_VERSION=1;
export const BRIDGE_LIMIT=12000;
const ID=/^[a-z][a-z0-9-]{2,63}$/;
const SHA=/^[a-f0-9]{64}$/;
const ERROR_CODES=new Set([
 'CODING_TASK_INVALID','CODING_TASK_STALE_SOURCE','CODING_TASK_DIRTY_SOURCE',
 'CODING_TASK_ID_REUSED','CODING_TASK_ID_INVALID','CODING_GRANT_UNSAFE',
 'CODING_GRANT_CORRUPT','CODING_CHAIN_BINDING_MISMATCH',
 'CCC1_PREFLIGHT_FAILED','CCC1_SOURCE_NOT_CLEAN','CODING_MODEL_UNAVAILABLE',
 'CODING_ISOLATION_REQUIRED','CODING_ISOLATION_UNAVAILABLE',
 'CODING_TASK_BINDING_MISMATCH'
]);
const DEFAULT_ROOT=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const DEFAULT_STATE=path.join(os.homedir(),'.local/state/device-ai-continuum/ccc1');
/** Only an owner-provisioned, external configuration may enable generated code.
 * The exact config is read using the existing isolation policy's symlink and
 * source-tree gates. There is no fallback to host/Termux shell execution. */
export function ownerCodingExecutor({project,env=process.env}={}){
 const configPath=env.CONTINUUM_CODING_ISOLATION_CONFIG;
 if(typeof configPath!=='string'||!path.isAbsolute(configPath)||configPath.length>1024)
  throw Error('CODING_ISOLATION_UNAVAILABLE');
 try{
  const config=loadIsolationConfig(configPath,{sourceRoot:project});
  return createIsolatedExecutor(config);
 }catch{
  throw Error('CODING_ISOLATION_UNAVAILABLE');
 }
}
function blocked(operation,id,code){
 return {schema:BRIDGE_SCHEMA,version:BRIDGE_VERSION,operation,id,
  status:'BLOCKED',failure:{code},approvedForMerge:false,installed:false,deviceVerified:false};
}
function exactEncoded(raw){
 if(typeof raw!=='string'||raw.length<4||raw.length>BRIDGE_LIMIT||!/^[A-Za-z0-9_-]+$/.test(raw))
  throw Error('CODING_BRIDGE_INPUT_INVALID');
 const bytes=Buffer.from(raw,'base64url');
 if(bytes.length<10||bytes.length>8192||bytes.toString('base64url')!==raw)
  throw Error('CODING_BRIDGE_INPUT_INVALID');
 const json=bytes.toString('utf8');
 if(!Buffer.from(json,'utf8').equals(bytes))throw Error('CODING_BRIDGE_INPUT_INVALID');
 return json;
}
export function decodePreparedTask(encoded,hash){
 if(!SHA.test(hash??''))throw Error('CODING_BRIDGE_BINDING_INVALID');
 const json=exactEncoded(encoded);
 const spec=validateCodingTask(parseStrict(json));
 if(canonical(spec)!==json||taskFingerprint(spec)!==hash)
  throw Error('CODING_BRIDGE_BINDING_INVALID');
 return spec;
}
export function fixedArgs(argv){
 const [op,...args]=argv;
 if(op==='prepare'&&args.length===2)return {op,encoded:args[0],hash:args[1]};
 if(op==='run'&&args.length===2&&ID.test(args[0])&&SHA.test(args[1]))
  return {op,id:args[0],hash:args[1]};
 throw Error('CODING_BRIDGE_COMMAND_INVALID');
}
/** Only a fully evidenced CCC1 review receipt may enter the Android success UI.
 * A string status, a patch SHA or an arbitrary truthy evidence object alone
 * must never suffice. The original immutable owner tests stay authoritative. */
export function verifiedCodingReviewReceipt(result){
 if(result?.status!=='PATCH_PENDING_OWNER_REVIEW'||
    !/^t-[a-f0-9]{16}$/.test(result.ccc1TaskId??'')||
    !/^[a-f0-9]{40}$/.test(result.sourceCommit??'')||
    !['node','android'].includes(result.chain)||
    !Array.isArray(result.receipts)||
    !Array.isArray(result.receiptJobs)||result.receiptJobs.length<1||
    result.receiptJobs.length>16||!result.receiptJobs.every(job=>typeof job==='string'&&JOB_RE.test(job))||
    new Set(result.receiptJobs).size!==result.receiptJobs.length)return null;
 for(const receipt of result.receipts){
  const evidence=receipt?.evidence;
  // Only a canonical repair-job receipt bound to the same task source and
  // chain may enter the owner-review UI; arbitrary lookalike JSON is not proof.
  if(receipt?.schema!==RECEIPT_SCHEMA||receipt.version!==RECEIPT_VERSION||
     !JOB_RE.test(receipt.job??'')||!result.receiptJobs.includes(receipt.job)||
     receipt.taskId!==jobTaskId(receipt.job)||
     receipt.order?.chain!==result.chain||
     receipt.operation!=='code-repair'||receipt.status!=='REPAIR_PATCH_VERIFIED_PENDING_REVIEW'||
     receipt.commit!==result.sourceCommit||receipt.backlog?.state!=='DONE'||
     receipt.approvedForMerge!==false||receipt.installed!==false||receipt.deviceVerified!==false||
     !SHA.test(receipt.patch?.sha256??'')||
     !SHA.test(evidence?.verificationSha256??'')||evidence.evidenceSufficient!==true||
     !Number.isSafeInteger(evidence.baselineExecuted)||evidence.baselineExecuted<1||
     !Number.isSafeInteger(evidence.baselineFailed)||evidence.baselineFailed<1||
     !Number.isSafeInteger(evidence.candidateExecuted)||evidence.candidateExecuted<1||
     !Number.isSafeInteger(evidence.independentExecuted)||
     evidence.independentExecuted<evidence.candidateExecuted)continue;
  const phases=receipt.commands;
  if(!Array.isArray(phases)||phases.length!==3||
     phases.map(p=>p?.phase).join(',')!=='baseline,candidate,independent')continue;
  const [baseline,candidate,independent]=phases;
  if(baseline.passed!==false||baseline.exitCode===0||baseline.timedOut===true||
     !Number.isSafeInteger(baseline.executed)||baseline.executed!==evidence.baselineExecuted||
     !Number.isSafeInteger(baseline.failed)||baseline.failed!==evidence.baselineFailed||
     baseline.failed<1||baseline.failed>baseline.executed)continue;
  if(![candidate,independent].every(p=>p.exitCode===0&&p.passed===true&&
     p.timedOut!==true&&Number.isSafeInteger(p.executed)&&p.executed>0&&p.failed===0))continue;
  if(candidate.executed!==evidence.candidateExecuted||
     independent.executed!==evidence.independentExecuted)continue;
  return receipt.patch.sha256;
 }
 return null;
}
/** Historic success is not evidence that its patch is still present.
 * Limit the read to the existing external Continuum state tree and inspect
 * actual bytes before claiming a patch is available for owner review. */
export function reviewPatchStillAvailable(result,hash,stateDir){
 if(!SHA.test(hash??'')||typeof stateDir!=='string')return false;
 try{
  const stateRoot=fs.realpathSync(path.dirname(path.resolve(stateDir)));
  const receipt=result?.receipts?.find(r=>r?.patch?.sha256===hash);
  const file=receipt?.patch?.path;
  if(typeof file!=='string'||file.length>2048||!path.isAbsolute(file))return false;
  const stat=fs.lstatSync(file);
  if(!stat.isFile()||stat.isSymbolicLink()||stat.size<1||stat.size>1024*1024||
     !Number.isSafeInteger(receipt.patch.bytes)||stat.size!==receipt.patch.bytes)return false;
  const real=fs.realpathSync(file);
  if(!real.startsWith(stateRoot+path.sep))return false;
  // Read the canonical path rather than resolving one name and reopening another.
  return sha256Hex(fs.readFileSync(real))===hash;
 }catch{return false;}
}
export async function executeTypedBridge(command,{
 project=DEFAULT_ROOT,stateDir=DEFAULT_STATE,
 prepare=prepareCodingTask,run=runCodingTask,
 isolationLoader=ownerCodingExecutor
}={}){
 const {op,id,hash,encoded}=command;
 if(op==='prepare'){
  let task;
  try{task=decodePreparedTask(encoded,hash);}catch{return blocked('prepare',null,'INVALID_TASK');}
  try{
   const r=prepare({project,stateDir,task});
   if(r.id!==task.id||r.fingerprint!==hash||typeof r.ccc1TaskId!=='string')
    return blocked('prepare',task.id,'PREPARATION_UNVERIFIED');
   return {schema:BRIDGE_SCHEMA,version:BRIDGE_VERSION,operation:'prepare',
    id:task.id,sourceCommit:task.sourceCommit,taskSha256:hash,ccc1TaskId:r.ccc1TaskId,
    replayed:r.replayed===true,status:'PREPARED_NOT_EXECUTED',
    approvedForMerge:false,installed:false,deviceVerified:false};
  }catch(e){return blocked('prepare',task.id,ERROR_CODES.has(e?.message)?e.message:'PREPARE_FAILED');}
 }
 if(op!=='run'||!ID.test(id??'')||!SHA.test(hash??''))
  return blocked('run',null,'INVALID_RUN_BINDING');
 try{
  // Run only a previously prepared grant; the orchestrator checks it against
  // the exact source SHA, allowed paths and currently active CCC1 journal.
  // Do not even invoke the CCC1/model worker if OS-level confinement is absent.
  // The worker independently rechecks the executor brand and abort mode.
  let executor;
  try{executor=await isolationLoader({project,stateDir});}
  catch{return blocked('run',id,'CODING_ISOLATION_UNAVAILABLE');}
  if(!executor)return blocked('run',id,'CODING_ISOLATION_UNAVAILABLE');
  const r=await run({project,stateDir,id,executor,
    executorAbortMode:'CHECKPOINT_BOUNDARY_ONLY',requireIsolation:true,
    expectedTaskFingerprint:hash});
  if(r.id!==id||r.taskFingerprint!==hash||!SHA.test(r.taskFingerprint))
   return blocked('run',id,'RUN_BINDING_MISMATCH');
  const verifiedPatch=verifiedCodingReviewReceipt(r);
  const reviewable=verifiedPatch!==null&&reviewPatchStillAvailable(r,verifiedPatch,stateDir);
  return {schema:BRIDGE_SCHEMA,version:BRIDGE_VERSION,operation:'run',
   id,sourceCommit:r.sourceCommit,taskSha256:hash,
   status:reviewable?'PATCH_PENDING_OWNER_REVIEW':'EXECUTION_REPORTED_UNVERIFIED',
   resultStatus:String(r.status).slice(0,64),
   patchSha256:reviewable?verifiedPatch:null,
   approvedForMerge:false,installed:false,deviceVerified:false};
 }catch(e){return blocked('run',id,ERROR_CODES.has(e?.message)?e.message:'RUN_FAILED');}
}
async function main(){
 let operation='unknown',id=null,result;
 try{
  const args=fixedArgs(process.argv.slice(2));
  operation=args.op;id=args.id??null;
  result=await executeTypedBridge(args);
 }catch{result=blocked(operation,id,'INVALID_COMMAND');}
 // Single small, data-only result through the existing one-shot Android callback.
 process.stdout.write(JSON.stringify(result)+'\n');
 if(result.status==='BLOCKED')process.exitCode=2;
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 main().catch(()=>{process.stdout.write(JSON.stringify(blocked('unknown',null,'BRIDGE_FAILED'))+'\n');process.exitCode=2;});
}
