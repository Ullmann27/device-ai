/** Typed owner task adapter for existing CCC1 -> mobile job -> teamleader -> repair -> verifier. */
import fs from 'node:fs';import path from 'node:path';
import {validateCodingTask,taskFingerprint} from './coding-task.mjs';
import {createCodingCandidateProvider} from './coding-provider.mjs';
import {startTask,runTask,taskState} from './ccc1-orchestrator.mjs';
import {Ccc1Reasoner} from './ccc1-interpreter.mjs';
import {REPAIR_CAPABILITY} from './ccc1-envelope.mjs';
import {repositoryOf} from './ccc1-worker.mjs';
import {ensureCcc1StateOutsideSource} from './ccc1-path-policy.mjs';
import {runMobileRepairJob,jobTaskId,readReceipt} from '../../scripts/continuum-mobile-coding.mjs';
import {builderStateOf,jobIdFor} from './ccc1-recovery.mjs';
import {DurableTaskJournal} from './ccc1-journal.mjs';
import {localTeamleaderDriver} from './autonomous-teamleader.mjs';
import {git} from './repair-loop.mjs';
import {isIsolatedExecutor} from './isolated-executor.mjs';
import {ExperienceLog,experienceOf} from './ccc1-experience.mjs';

const ID=/^[a-z][a-z0-9-]{2,63}$/;
export const CODING_PHASES=Object.freeze(['TASK_ACCEPTED','CHECKING_BASELINE','BASELINE_FAILED','BASELINE_PASSED',
 'MODEL_WORKING','PLAN_CREATED','PATCH_PROPOSED','PROPOSAL_REJECTED','CHECKING_PATCH','CHECK_FAILED',
 'CORRECTING','PATCH_CHECKS_PASSED','CHECKING_INDEPENDENT','INDEPENDENT_VERIFIED','INDEPENDENT_FAILED','BLOCKED']);
function grantFile(project,stateDir,id){
 if(!ID.test(id??''))throw Error('CODING_TASK_ID_INVALID');
 const state=ensureCcc1StateOutsideSource(project,stateDir);
 return path.join(state,'coding-grants',id+'.json');
}
function readGrant(file){
 const stat=fs.lstatSync(file);
 if(!stat.isFile()||stat.isSymbolicLink()||fs.realpathSync(file)!==file||stat.size>16000)throw Error('CODING_GRANT_UNSAFE');
 const grant=JSON.parse(fs.readFileSync(file,'utf8'));
 const task=validateCodingTask(grant.task);
 if(taskFingerprint(task)!==grant.fingerprint||typeof grant.ccc1TaskId!=='string')throw Error('CODING_GRANT_CORRUPT');
 return {...grant,task};
}
/** Read-only view of the SAME grant, authenticated CCC1 journal and canonical
 * report. Phase means last observed, never a heartbeat or execution authority.
 * This deliberately does not call runTask/recover or create a runtime key.
 */
export function readCodingTaskStatus({project,stateDir,id,expectedTaskFingerprint}={}){
 const grant=readGrant(grantFile(project,stateDir,id));
 if(grant.task.id!==id||grant.fingerprint!==expectedTaskFingerprint)throw Error('CODING_TASK_BINDING_MISMATCH');
 if(!/^t-[a-f0-9]{16}$/.test(grant.ccc1TaskId))throw Error('CODING_GRANT_CORRUPT');
 const safeRead=(file,limit)=>{
  const s=fs.lstatSync(file);
  if(!s.isFile()||s.isSymbolicLink()||fs.realpathSync(file)!==file||s.size>limit)throw Error('CODING_EVIDENCE_UNSAFE');
  return fs.readFileSync(file);
 };
 const key=safeRead(path.join(stateDir,'ccc1-runtime.key'),32);
 const journalFile=path.join(stateDir,'tasks',grant.ccc1TaskId+'.jsonl');safeRead(journalFile,2_000_000);
 const entries=new DurableTaskJournal(journalFile,key).entries();
 if(entries[0]?.baseCommitSha!==grant.task.sourceCommit)throw Error('CODING_TASK_BINDING_MISMATCH');
 const state=taskState(entries),receiptJobs=[...new Set(entries.filter(e=>e.kind==='RECEIPT').map(e=>e.payload.job).filter(Boolean))];
 const receipts=receiptJobs.map(job=>readReceipt(builderStateOf(stateDir),job)).filter(Boolean);
 const jobs=entries.filter(e=>e.kind==='TEST_REQUEST').map(e=>jobIdFor(grant.ccc1TaskId,e.messageId));
 const reportRoot=path.join(stateDir,'teamleader/reports/owner-repair');let progress=[];
 if(fs.existsSync(reportRoot)){
  if(fs.realpathSync(reportRoot)!==reportRoot)throw Error('CODING_EVIDENCE_UNSAFE');
  for(const job of jobs){
   const prefix=jobTaskId(job)+'-'+grant.task.sourceCommit.slice(0,12)+'-';
   const names=fs.readdirSync(reportRoot).filter(n=>n.startsWith(prefix)&&/^mob-[a-f0-9]{16}-[a-f0-9]{12}-\d{13}-[a-f0-9]{8}$/.test(n)).sort();
   if(names.length>32)throw Error('CODING_EVIDENCE_INVALID');
   for(const name of names){
    const file=path.join(reportRoot,name,'report.json');if(!fs.existsSync(file))continue;
    const r=JSON.parse(safeRead(file,2_000_000));
    if(r.base!==grant.task.sourceCommit||r.coding?.taskFingerprint!==taskFingerprint({...grant.task,id:jobTaskId(job)}))continue;
    if(!Array.isArray(r.progress)||r.progress.length>48||r.progress.some(p=>!CODING_PHASES.includes(p.phase)||
      typeof p.at!=='string'||!/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d\.\d{3}Z$/.test(p.at)||
      !Number.isSafeInteger(p.attempt)||p.attempt<0||p.attempt>3))throw Error('CODING_EVIDENCE_INVALID');
    progress=r.progress.map(({phase,at,attempt})=>({phase,at,attempt}));
   }
  }
 }
 const phase=state.terminal&&state.state!=='COMPLETED'?'BLOCKED':progress.at(-1)?.phase??'TASK_ACCEPTED';
 return {schema:'continuum-coding-task-result',version:1,id,ccc1TaskId:grant.ccc1TaskId,
  taskFingerprint:grant.fingerprint,sourceCommit:grant.task.sourceCommit,chain:grant.task.chain,
  status:state.state==='COMPLETED'?'PATCH_PENDING_OWNER_REVIEW':state.state,
  phase,progress,observationOnly:true,receiptJobs,receipts,approvedForMerge:false,deviceVerified:false,neuralTraining:false};
}
export function prepareCodingTask({project,stateDir,task}){
 const spec=validateCodingTask(task),fingerprint=taskFingerprint(spec);
 if(git(project,['rev-parse','HEAD']).stdout.trim()!==spec.sourceCommit)throw Error('CODING_TASK_STALE_SOURCE');
 if(git(project,['status','--porcelain']).stdout.trim())throw Error('CODING_TASK_DIRTY_SOURCE');
 const file=grantFile(project,stateDir,spec.id);
 if(fs.existsSync(file)){
  const known=readGrant(file);
  if(known.fingerprint!==fingerprint)throw Error('CODING_TASK_ID_REUSED');
  return {id:spec.id,ccc1TaskId:known.ccc1TaskId,fingerprint,replayed:true};
 }
 fs.mkdirSync(path.dirname(file),{recursive:true,mode:0o700});
 if(fs.realpathSync(path.dirname(file))!==path.dirname(file))throw Error('CODING_GRANT_UNSAFE');
 // Exclusive reservation: a crash before journal creation needs explicit inspection, never silent resubmission.
 const fd=fs.openSync(file,'wx',0o600);
 try{
  fs.writeFileSync(fd,JSON.stringify({task:spec,fingerprint,ccc1TaskId:null})+'\n');fs.fsyncSync(fd);
  const ccc1TaskId=startTask({project,stateDir,repoFullName:repositoryOf(project),
   goal:{text:(spec.goal.slice(0,500)+' [task '+fingerprint+']'),repair:{chain:spec.chain}},
   permissions:[REPAIR_CAPABILITY+':'+spec.chain],maxRounds:1,deadlineMinutes:30,maxCpuMillis:1_800_000});
  const content=JSON.stringify({task:spec,fingerprint,ccc1TaskId})+'\n';
  fs.ftruncateSync(fd,0);fs.writeSync(fd,content,0,'utf8');fs.fsyncSync(fd);
  return {id:spec.id,ccc1TaskId,fingerprint,replayed:false};
 }finally{fs.closeSync(fd);}
}
export async function runCodingTask({project,stateDir,id,executor=null,executorAbortMode=null,
 requireIsolation=false,expectedTaskFingerprint=null,
 providerFactory=options=>createCodingCandidateProvider(options),env=process.env}={}){
 // Android untrusted-model execution is never allowed to inherit the legacy
 // host fallback. A branded executor is required BEFORE journal/model access.
 if(requireIsolation && (!isIsolatedExecutor(executor) || executorAbortMode!=='CHECKPOINT_BOUNDARY_ONLY'))
  throw Error('CODING_ISOLATION_REQUIRED');
 const grant=readGrant(grantFile(project,stateDir,id));
 // The grant file name, embedded task identity and journal identity must agree.
 if(grant.task.id!==id || !/^t-[a-f0-9]{16}$/.test(grant.ccc1TaskId))
  throw Error('CODING_GRANT_CORRUPT');
 // Check the owner's prepared-task SHA BEFORE CCC1 journal mutation, model
 // inference or running any untrusted candidate. Null preserves older callers.
 if(expectedTaskFingerprint!==null &&
    (!/^[a-f0-9]{64}$/.test(expectedTaskFingerprint) ||
     grant.fingerprint!==expectedTaskFingerprint))
  throw Error('CODING_TASK_BINDING_MISMATCH');
 if(git(project,['rev-parse','HEAD']).stdout.trim()!==grant.task.sourceCommit)throw Error('CODING_TASK_STALE_SOURCE');
 if(git(project,['status','--porcelain']).stdout.trim())throw Error('CODING_TASK_DIRTY_SOURCE');
 const receipts=[];
 const result=await runTask({project,stateDir,taskId:grant.ccc1TaskId,
  reasoner:new Ccc1Reasoner({candidates:[],repairBudgetMinutes:20}),maxTurns:16,
  runRepairJob:async order=>{
   if(order.chain!==grant.task.chain)throw Error('CODING_CHAIN_BINDING_MISMATCH');
   const task=validateCodingTask({...grant.task,id:jobTaskId(order.job)});
   const evidenceDir=path.join(stateDir,'coding-evidence',grant.task.id,order.job);
   const candidateProvider=providerFactory({root:project,task,env,evidenceDir});
   const driver=await localTeamleaderDriver({candidateProvider,executor,executorAbortMode});
   const receipt=await runMobileRepairJob({...order,driver});receipts.push(receipt);return receipt;
  }});
 for(const entry of result.entries??[]){
  if(entry.kind!=='RECEIPT'||receipts.some(r=>r.job===entry.payload.job))continue;
  const receipt=readReceipt(builderStateOf(stateDir),entry.payload.job);if(receipt)receipts.push(receipt);
 }
 // Match future owner-review receipts to jobs in this task's validated CCC1 journal.
 const receiptJobs=[...new Set((result.entries??[])
  .filter(entry=>entry.kind==='RECEIPT'&&typeof entry.payload?.job==='string')
  .map(entry=>entry.payload.job))];
 const experience=experienceOf(grant.ccc1TaskId,result.entries??[],{coreId:'typed-coding-adapter-v1'});
 if(experience)new ExperienceLog(path.join(stateDir,'experience.jsonl')).append(experience);
 return {schema:'continuum-coding-task-result',version:1,id,ccc1TaskId:grant.ccc1TaskId,
  taskFingerprint:grant.fingerprint,sourceCommit:grant.task.sourceCommit,chain:grant.task.chain,
  status:result.outcome==='COMPLETED'?'PATCH_PENDING_OWNER_REVIEW':result.outcome,
  receiptJobs,receipts,approvedForMerge:false,deviceVerified:false,neuralTraining:false};
}
