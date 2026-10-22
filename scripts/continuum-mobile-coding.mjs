// Android/Termux coding job (EXP-00 + M4.2): one owner-confirmed, bounded
// contract-test order from the Continuum Builder runs through the EXISTING
// canonical backlog and Teamleader (owner TEST task -> synthesis -> worktree
// tests -> second independent worktree -> patch). This file only binds the
// fixed RUN_COMMAND channel to that pipeline and writes a machine-checkable
// receipt. No shell text, no model output, no Git writes, no install.
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {spawnSync} from 'node:child_process';
import {createHash,randomUUID} from 'node:crypto';
import {BacklogStore,sha256Hex,singleLine} from '../.github/continuum/development-backlog.mjs';
import {backlogCommand} from '../.github/continuum/backlog-cli.mjs';
import {runTeamleaderCycle,localTeamleaderDriver} from '../.github/continuum/autonomous-teamleader.mjs';
import {defaultTeamleaderStateDir} from '../.github/continuum/teamleader-cli.mjs';

export const RECEIPT_SCHEMA='continuum-mobile-coding-receipt';
export const RECEIPT_VERSION=1;
export const JOB_RE=/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
// Same grammar as `backlog-cli propose-test`; the Kotlin plan mirrors it.
export const MODULE_RE=/^\.github\/continuum\/[A-Za-z][A-Za-z0-9_-]{0,63}\.mjs$/;
export const EXPORT_RE=/^[A-Za-z_][A-Za-z0-9_]{0,63}$/;
export const MAX_CASES_JSON=300;
export const STATUSES=Object.freeze([
 'CODING_PATCH_VERIFIED_PENDING_REVIEW','FAILED_CODING_CHECKS','CANCELLED',
 'BLOCKED_INVALID_ORDER','BLOCKED_UNCLEAN_SOURCE','BLOCKED_STALE_BASE',
 'BLOCKED_JOB_RUNNING','BLOCKED_OTHER_WORK_FIRST','BLOCKED_NO_SUCH_JOB','BLOCKED_ALREADY_FINAL'
]);
// The fixed profile that produced every counted test (see repair-loop PROFILES).
const PROFILE_COMMAND='node --test --test-reporter=junit .github/continuum/*.test.mjs (fixed profile node-continuum, isolated git worktree)';

/** Deterministic backlog task id of one Android job: replays map to the same task. */
export const jobTaskId=job=>'mob-'+sha256Hex('android-coding-job\0'+job).slice(0,16);

export function validateOrder({job,module,exportName,casesJson}){
 const problems=[];
 if(typeof job!=='string'||!JOB_RE.test(job))problems.push('job must be a lowercase UUIDv4');
 if(typeof module!=='string'||!MODULE_RE.test(module))problems.push('module must be .github/continuum/<name>.mjs');
 if(typeof exportName!=='string'||!EXPORT_RE.test(exportName))problems.push('export must be a JavaScript identifier');
 if(typeof casesJson!=='string'||casesJson.length<2||casesJson.length>MAX_CASES_JSON)problems.push('cases-json must be 2..300 characters');
 return problems;
}

function git(root,args){
 const r=spawnSync('git',args,{cwd:root,encoding:'utf8',timeout:30000,shell:false});
 return {status:r.status,stdout:(r.stdout??'').trim()};
}
function toolchain(root){
 const g=git(root,['--version']);
 return {node:process.version,platform:process.platform,arch:process.arch,
  git:g.status===0?g.stdout.slice(0,80):'UNAVAILABLE'};
}
function receiptDir(stateDir){return path.join(stateDir,'coding-jobs');}
function receiptPath(stateDir,job){return path.join(receiptDir(stateDir),job+'.json');}
function writeReceipt(stateDir,receipt){
 fs.mkdirSync(receiptDir(stateDir),{recursive:true,mode:0o700});
 const target=receiptPath(stateDir,receipt.job);
 const tmp=target+'.'+randomUUID()+'.tmp';
 fs.writeFileSync(tmp,JSON.stringify(receipt,null,2)+'\n',{flag:'wx',mode:0o600});
 fs.renameSync(tmp,target);
 return receipt;
}
/** A stored receipt is only replayed when it is well-formed and belongs to this job. */
export function readReceipt(stateDir,job){
 const file=receiptPath(stateDir,job);
 if(!fs.existsSync(file))return null;
 const r=JSON.parse(fs.readFileSync(file,'utf8'));
 if(r?.schema!==RECEIPT_SCHEMA||r.version!==RECEIPT_VERSION||r.job!==job||!STATUSES.includes(r.status))
  throw Error('Stored coding receipt is corrupt; inspect '+file);
 return r;
}

function baseReceipt({job,operation,taskId,commit,root,order}){
 return {schema:RECEIPT_SCHEMA,version:RECEIPT_VERSION,job,operation,taskId,commit,
  toolchain:toolchain(root),order:order?{module:order.module,export:order.exportName,
   casesSha256:sha256Hex(order.casesJson)}:null,
  status:null,teamleaderOutcome:null,patch:null,evidence:null,commands:[],failure:null,
  backlog:null,approvedForMerge:false,apk:null,installed:false,deviceVerified:false,
  createdAt:new Date().toISOString()};
}

/** Reconstruct the verified part of a receipt from the authoritative backlog and the worker's evidence file. */
function completeFromBacklog(receipt,{stateDir,taskId,artifact}){
 const task=new BacklogStore(path.join(stateDir,'development-backlog.json'),{actor:'android-coding-job'})
  .read().doc.tasks.find(t=>t.id===taskId);
 if(!task)return receipt;
 receipt.backlog={state:task.state,attempts:task.attempts,maxAttempts:task.maxAttempts,
  error:task.error?{code:task.error.code,message:task.error.message}:null};
 if(task.state==='DONE'){
  const sha=task.submission.artifact.sha256;
  const ev=task.evidence.find(e=>e.artifactSha256===sha&&e.submissionToken===task.submission.token);
  receipt.patch={sha256:sha,path:null,bytes:null};
  // The worker stores the patch next to its independent verification receipt.
  if(typeof artifact==='string'&&fs.existsSync(artifact)){
   const bytes=fs.readFileSync(artifact);
   if(sha256Hex(bytes)===sha)receipt.patch={sha256:sha,path:artifact,bytes:bytes.length};
  }
  const verification=receipt.patch.path?path.join(path.dirname(receipt.patch.path),'independent-check','verification.json'):null;
  if(verification&&fs.existsSync(verification)){
   const raw=fs.readFileSync(verification);const v=JSON.parse(raw.toString('utf8'));
   if(v.taskId===taskId&&v.patchSha256===sha&&v.passed===true){
    receipt.evidence={verificationSha256:sha256Hex(raw),baselineExecuted:v.baselineExecuted,
     candidateExecuted:v.candidateExecuted,independentExecuted:v.independentExecuted,expectedNewTests:v.expectedNewTests};
    receipt.commands=(v.runs??[]).map(r=>({...r,command:PROFILE_COMMAND}));
   }
  }
  if(!receipt.evidence&&ev)receipt.evidence={verificationSha256:null,
   independentExecuted:ev.profiles.reduce((n,p)=>n+p.executed,0),note:'from backlog evidence only'};
 }
 return receipt;
}

/**
 * Run (or resume / replay) one Android coding job. Returns the receipt; never throws for
 * expected refusals (they become BLOCKED_* receipts), only for corrupt local state.
 */
export async function runMobileCodingJob({project,builderState,teamleaderState=null,job,module,exportName,casesJson,
 budgetMinutes=20,driver=null}){
 const order={module,exportName,casesJson};
 const problems=validateOrder({job,...order});
 const root=fs.realpathSync(path.resolve(project));
 const commit=git(root,['rev-parse','HEAD']).stdout;
 const taskId=JOB_RE.test(job??'')?jobTaskId(job):null;
 const receipt=baseReceipt({job,operation:'code-test',taskId,commit,root,order:problems.length?null:order});
 if(problems.length){receipt.status='BLOCKED_INVALID_ORDER';receipt.failure={code:'INVALID_ORDER',message:problems.join('; ')};return receipt;}
 if(!Number.isSafeInteger(budgetMinutes)||budgetMinutes<1||budgetMinutes>30)throw Error('Budget 1..30 minutes required');
 const replay=readReceipt(builderState,job);
 // Replay protection: a finished job is reported, never executed again.
 if(replay&&!replay.status.startsWith('BLOCKED_'))return {...replay,replayed:true};
 const stateDir=teamleaderState??defaultTeamleaderStateDir(root);
 const backlogFile=path.join(stateDir,'development-backlog.json');
 const store=new BacklogStore(backlogFile,{actor:'android-owner'});
 let task=store.read().doc.tasks.find(t=>t.id===taskId);
 if(!task){
  if(git(root,['status','--porcelain']).stdout){receipt.status='BLOCKED_UNCLEAN_SOURCE';
   receipt.failure={code:'UNCLEAN_SOURCE',message:'Termux checkout has uncommitted changes'};return writeReceipt(builderState,receipt);}
  const quiet={root,log:()=>{}};
  await backlogCommand('propose-test',{file:backlogFile,actor:'android-owner',id:taskId,module,export:exportName,
   'cases-json':casesJson,title:singleLine('Android-Auftrag: Vertragstest für '+exportName,200)},quiet);
  await backlogCommand('accept',{file:backlogFile,actor:'android-owner',id:taskId,
   reason:'Owner confirmed Android coding job '+job},quiet);
  task=store.read().doc.tasks.find(t=>t.id===taskId);
 }
 if(task.subject?.base!==commit&&!['DONE','FAILED','ABORTED','REJECTED'].includes(task.state)){
  receipt.status='BLOCKED_STALE_BASE';
  receipt.failure={code:'STALE_BASE',message:'Order was bound to '+String(task.subject?.base).slice(0,12)+', checkout is at '+commit.slice(0,12)};
  return writeReceipt(builderState,receipt);
 }
 let artifact=null;
 if(task.state==='READY'||(task.state==='IN_PROGRESS'&&task.claim.leaseUntil<Date.now())){
  let result;
  try{
   result=runTeamleaderCycle({root,stateDir,driver:driver??await localTeamleaderDriver(),budgetMinutes});
  }catch(e){
   receipt.status='BLOCKED_JOB_RUNNING';
   receipt.failure={code:'WORKER_BUSY',message:singleLine(e.message,300)};
   return writeReceipt(builderState,receipt);
  }
  receipt.teamleaderOutcome=result.outcome;
  if(result.kind!=='OWNER_TEST'||result.taskId!==taskId){
   receipt.status='BLOCKED_OTHER_WORK_FIRST';
   receipt.failure={code:'OTHER_WORK_FIRST',message:'Teamleader cycle processed '+result.kind+' '+(result.taskId??'')};
   return writeReceipt(builderState,receipt);
  }
  artifact=result.artifact;
  task=store.read().doc.tasks.find(t=>t.id===taskId);
 }else if(task.state==='IN_PROGRESS'){
  receipt.status='BLOCKED_JOB_RUNNING';
  receipt.failure={code:'JOB_RUNNING',message:'Lease active until '+new Date(task.claim.leaseUntil).toISOString()};
  return writeReceipt(builderState,receipt);
 }
 completeFromBacklog(receipt,{stateDir,taskId,artifact});
 if(task.state==='DONE'&&receipt.evidence)receipt.status='CODING_PATCH_VERIFIED_PENDING_REVIEW';
 else if(task.state==='ABORTED')receipt.status='CANCELLED';
 else{
  receipt.status='FAILED_CODING_CHECKS';
  receipt.failure=receipt.backlog?.error??{code:'NOT_VERIFIED',message:'Task ended in '+task.state+' without verified evidence'};
 }
 return writeReceipt(builderState,receipt);
}

/** Owner cancel from Android: abort the job's backlog task unless it is already final.
 * The run receipt of the job is left untouched; the next `code-test` with the same job reports CANCELLED. */
export function cancelMobileCodingJob({project,teamleaderState=null,job,reason='Owner cancelled on Android'}){
 if(!JOB_RE.test(job??''))throw Error('job must be a lowercase UUIDv4');
 const root=fs.realpathSync(path.resolve(project));
 const stateDir=teamleaderState??defaultTeamleaderStateDir(root);
 const store=new BacklogStore(path.join(stateDir,'development-backlog.json'),{actor:'android-owner'});
 const taskId=jobTaskId(job);
 const task=store.read().doc.tasks.find(t=>t.id===taskId);
 const receipt=baseReceipt({job,operation:'code-cancel',taskId,commit:git(root,['rev-parse','HEAD']).stdout,root,order:null});
 if(!task){receipt.status='BLOCKED_NO_SUCH_JOB';return receipt;}
 if(!['DONE','ABORTED','REJECTED'].includes(task.state))
  store.apply([{op:'abort',actor:'android-owner',at:Date.now(),id:taskId,reason:singleLine(reason,400)}]);
 const now=store.read().doc.tasks.find(t=>t.id===taskId);
 receipt.backlog={state:now.state,attempts:now.attempts,maxAttempts:now.maxAttempts,error:null};
 receipt.status=now.state==='ABORTED'?'CANCELLED':'BLOCKED_ALREADY_FINAL';
 return receipt;
}

export function defaultBuilderState(){return path.join(os.homedir(),'.local/state/device-ai-continuum/mobile-builder');}
export const receiptDigest=r=>createHash('sha256').update(JSON.stringify(r)).digest('hex');
