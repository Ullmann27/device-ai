// Android/Termux coding job (EXP-00 + M4.2): one owner-confirmed, bounded
// contract-test order from the Continuum Builder runs through the EXISTING
// canonical backlog and Teamleader (owner TEST task -> synthesis -> worktree
// tests -> second independent worktree -> patch). A repair order runs the
// EXISTING owner repair path the same way (owner REPAIR task -> fixed test
// chain in an isolated worktree -> deterministic repair strategies -> second
// independent worktree -> patch). This file only binds the fixed RUN_COMMAND
// channel to that pipeline and writes a machine-checkable receipt. No shell
// text, no model output, no Git writes, no install.
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
 'REPAIR_PATCH_VERIFIED_PENDING_REVIEW','NO_FAILURE_FOUND','FAILED_REPAIR',
 'BLOCKED_INVALID_ORDER','BLOCKED_UNCLEAN_SOURCE','BLOCKED_STALE_BASE',
 'BLOCKED_JOB_RUNNING','BLOCKED_OTHER_WORK_FIRST','BLOCKED_NO_SUCH_JOB','BLOCKED_ALREADY_FINAL'
]);
/** Fixed test chains an owner repair order may name (repair-loop CHAINS, without the combined stability chain). */
export const REPAIR_CHAINS=Object.freeze(['node','android','jvm']);
// The fixed profile that produced every counted test (see repair-loop PROFILES).
const PROFILE_COMMAND='node --test --test-reporter=junit .github/continuum/*.test.mjs (fixed profile node-continuum, isolated git worktree)';

/** Deterministic backlog task id of one Android job: replays map to the same task. */
export const jobTaskId=job=>'mob-'+sha256Hex('android-coding-job\0'+job).slice(0,16);

export function validateRepairOrder({job,chain}){
 const problems=[];
 if(typeof job!=='string'||!JOB_RE.test(job))problems.push('job must be a lowercase UUIDv4');
 if(!REPAIR_CHAINS.includes(chain))problems.push('chain must be one of '+REPAIR_CHAINS.join(', '));
 return problems;
}

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

const sumTests=(results,key)=>(results??[]).reduce((n,r)=>n+(Number(r?.tests?.[key])||0),0);

/**
 * Receipt of a finished owner repair, from the worker's own result files: the patch bytes, the failing baseline the
 * repair started from, the candidate that passed in the repair worktree and the independent second worktree.
 */
function completeRepair(receipt,{reportFile,chain}){
 if(typeof reportFile!=='string'||!fs.existsSync(reportFile))return receipt;
 const dir=path.dirname(reportFile);
 const read=f=>{try{return JSON.parse(fs.readFileSync(path.join(dir,f),'utf8'));}catch{return null;}};
 const report=read('report.json'),result=read('task-result.json');
 if(!report||!result)return receipt;
 const baseline=report.baseline?.results??[];
 const run=(phase,results,passed)=>({phase,exitCode:results.length&&results.every(r=>r.exitCode===0)?0:1,passed:passed===true,
  executed:sumTests(results,'executed'),failed:sumTests(results,'failed'),timedOut:results.some(r=>r.timedOut===true),
  command:REPAIR_PROFILE_COMMAND[chain]});
 receipt.repair={outcome:String(report.outcome??''),strategy:report.verified?.strategy??null,
  files:(report.verified?.files??[]).slice(0,8),diagnostics:Array.isArray(report.baseline?.diagnostics)?report.baseline.diagnostics.length:0,
  attempts:Array.isArray(report.attempts)?report.attempts.length:0};
 receipt.commands=[run('baseline',baseline,report.baseline?.passed)];
 if(result.status==='DONE'&&typeof result.patch==='string'&&fs.existsSync(result.patch)){
  const bytes=fs.readFileSync(result.patch);
  if(sha256Hex(bytes)===result.patchSha256)receipt.patch={sha256:result.patchSha256,path:result.patch,bytes:bytes.length};
  const candidate=Array.isArray(report.verified?.tests)?report.verified.tests:[];
  receipt.commands.push(run('candidate',candidate,candidate.length>0&&candidate.every(r=>r.passed===true)));
  const profiles=result.profiles??[];
  receipt.commands.push({phase:'independent',exitCode:profiles.length&&profiles.every(p=>p.passed&&p.failed===0)?0:1,
   passed:profiles.length>0&&profiles.every(p=>p.passed&&p.failed===0),executed:profiles.reduce((n,p)=>n+(p.executed||0),0),
   failed:profiles.reduce((n,p)=>n+(p.failed||0),0),timedOut:false,command:REPAIR_PROFILE_COMMAND[chain]});
  if(typeof result.independentReceipt==='string'&&fs.existsSync(result.independentReceipt)){
   const raw=fs.readFileSync(result.independentReceipt);const v=JSON.parse(raw.toString('utf8'));
   if(v.taskId===receipt.taskId&&v.patchSha256===result.patchSha256&&v.passed===true)
    receipt.evidence={verificationSha256:sha256Hex(raw),baselineFailed:sumTests(baseline,'failed'),baselineExecuted:sumTests(baseline,'executed'),
     candidateExecuted:sumTests(candidate,'executed'),independentExecuted:v.executedTests,evidenceSufficient:v.evidenceSufficient===true};
  }
 }else if(result.status==='FAILED'){
  receipt.failure={code:result.reason==='NO_FAILURE'?'NO_FAILURE':result.reason==='INDEPENDENT_CHECK_FAILED'?'CHECKS_FAILED':
   ['NO_CODE_DIAGNOSTIC','NO_VERIFIED_REPAIR'].includes(result.reason)?result.reason:'NO_VERIFIED_REPAIR',
   message:singleLine('Repair ended '+String(result.reason),300)};
 }
 return receipt;
}

/** The fixed command of each repair chain (repair-loop PROFILES); never taken from an order. */
export const REPAIR_PROFILE_COMMAND=Object.freeze({
 node:'node --test --test-reporter=junit .github/continuum/*.test.mjs (fixed profile node-continuum, isolated git worktree)',
 android:'gradle compile + unit tests (fixed profiles android-compile, android-unit, isolated git worktree)',
 jvm:'fixed profile jvm-harness, isolated git worktree'
});

/**
 * Run (or resume / replay) one owner repair order from Android: the fixed test chain of the checkout's HEAD runs in
 * an isolated worktree; if it fails, the EXISTING repair pipeline (owner REPAIR task, deterministic strategies,
 * independent second worktree) produces a candidate patch. Returns the receipt; refusals become BLOCKED_* receipts.
 */
export async function runMobileRepairJob({project,builderState,teamleaderState=null,job,chain,budgetMinutes=20,driver=null}){
 const problems=validateRepairOrder({job,chain});
 const root=fs.realpathSync(path.resolve(project));
 const commit=git(root,['rev-parse','HEAD']).stdout;
 const taskId=JOB_RE.test(job??'')?jobTaskId(job):null;
 const receipt=baseReceipt({job,operation:'code-repair',taskId,commit,root,order:null});
 receipt.order=problems.length?null:{chain};
 receipt.repair=null;
 if(problems.length){receipt.status='BLOCKED_INVALID_ORDER';receipt.failure={code:'INVALID_ORDER',message:problems.join('; ')};return receipt;}
 if(!Number.isSafeInteger(budgetMinutes)||budgetMinutes<1||budgetMinutes>30)throw Error('Budget 1..30 minutes required');
 const replay=readReceipt(builderState,job);
 if(replay&&replay.operation!=='code-repair'){
  receipt.status='BLOCKED_INVALID_ORDER';receipt.failure={code:'JOB_ID_REUSED',message:'job id already used for '+replay.operation};return receipt;
 }
 // Replay protection: a finished job is reported, never executed again.
 if(replay&&!replay.status.startsWith('BLOCKED_'))return {...replay,replayed:true};
 const stateDir=teamleaderState??defaultTeamleaderStateDir(root);
 const backlogFile=path.join(stateDir,'development-backlog.json');
 const store=new BacklogStore(backlogFile,{actor:'android-owner'});
 let task=store.read().doc.tasks.find(t=>t.id===taskId);
 if(task&&task.kind!=='REPAIR'){
  receipt.status='BLOCKED_INVALID_ORDER';receipt.failure={code:'JOB_ID_REUSED',message:'job id belongs to a '+task.kind+' task'};
  return writeReceipt(builderState,receipt);
 }
 if(!task){
  if(git(root,['status','--porcelain']).stdout){receipt.status='BLOCKED_UNCLEAN_SOURCE';
   receipt.failure={code:'UNCLEAN_SOURCE',message:'Termux checkout has uncommitted changes'};return writeReceipt(builderState,receipt);}
  const quiet={root,log:()=>{}};
  await backlogCommand('propose-repair',{file:backlogFile,actor:'android-owner',id:taskId,chain,
   title:singleLine('Android-Auftrag: Reparatur der Testkette '+chain,200)},quiet);
  await backlogCommand('accept',{file:backlogFile,actor:'android-owner',id:taskId,
   reason:'Owner confirmed Android repair job '+job},quiet);
  task=store.read().doc.tasks.find(t=>t.id===taskId);
 }
 if(task.subject?.base!==commit&&!['DONE','FAILED','ABORTED','REJECTED'].includes(task.state)){
  receipt.status='BLOCKED_STALE_BASE';
  receipt.failure={code:'STALE_BASE',message:'Order was bound to '+String(task.subject?.base).slice(0,12)+', checkout is at '+commit.slice(0,12)};
  return writeReceipt(builderState,receipt);
 }
 let reportFile=null;
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
  if(result.kind!=='OWNER_REPAIR'||result.taskId!==taskId){
   receipt.status='BLOCKED_OTHER_WORK_FIRST';
   receipt.failure={code:'OTHER_WORK_FIRST',message:'Teamleader cycle processed '+result.kind+' '+(result.taskId??'')};
   return writeReceipt(builderState,receipt);
  }
  reportFile=result.artifact;
  task=store.read().doc.tasks.find(t=>t.id===taskId);
 }else if(task.state==='IN_PROGRESS'){
  receipt.status='BLOCKED_JOB_RUNNING';
  receipt.failure={code:'JOB_RUNNING',message:'Lease active until '+new Date(task.claim.leaseUntil).toISOString()};
  return writeReceipt(builderState,receipt);
 }
 receipt.backlog={state:task.state,attempts:task.attempts,maxAttempts:task.maxAttempts,
  error:task.error?{code:task.error.code,message:task.error.message}:null};
 completeRepair(receipt,{reportFile,chain});
 if(task.state==='DONE'&&receipt.patch?.path&&receipt.evidence)receipt.status='REPAIR_PATCH_VERIFIED_PENDING_REVIEW';
 else if(task.state==='ABORTED')receipt.status='CANCELLED';
 else if(receipt.failure?.code==='NO_FAILURE'||task.error?.message==='NO_FAILURE')receipt.status='NO_FAILURE_FOUND';
 else{
  receipt.status='FAILED_REPAIR';
  receipt.failure=receipt.failure??receipt.backlog?.error??{code:'NOT_VERIFIED',message:'Task ended in '+task.state+' without verified evidence'};
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
