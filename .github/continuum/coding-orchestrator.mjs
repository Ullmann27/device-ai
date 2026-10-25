/** Typed owner task adapter for existing CCC1 -> mobile job -> teamleader -> repair -> verifier. */
import fs from 'node:fs';import path from 'node:path';
import {validateCodingTask,taskFingerprint} from './coding-task.mjs';
import {createCodingCandidateProvider} from './coding-provider.mjs';
import {startTask,runTask} from './ccc1-orchestrator.mjs';
import {Ccc1Reasoner} from './ccc1-interpreter.mjs';
import {REPAIR_CAPABILITY} from './ccc1-envelope.mjs';
import {repositoryOf} from './ccc1-worker.mjs';
import {ensureCcc1StateOutsideSource} from './ccc1-path-policy.mjs';
import {runMobileRepairJob,jobTaskId,readReceipt} from '../../scripts/continuum-mobile-coding.mjs';
import {builderStateOf} from './ccc1-recovery.mjs';
import {localTeamleaderDriver} from './autonomous-teamleader.mjs';
import {git} from './repair-loop.mjs';
import {isIsolatedExecutor} from './isolated-executor.mjs';
import {ExperienceLog,experienceOf} from './ccc1-experience.mjs';

const ID=/^[a-z][a-z0-9-]{2,63}$/;
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
 const experience=experienceOf(grant.ccc1TaskId,result.entries??[],{coreId:'typed-coding-adapter-v1'});
 if(experience)new ExperienceLog(path.join(stateDir,'experience.jsonl')).append(experience);
 return {schema:'continuum-coding-task-result',version:1,id,ccc1TaskId:grant.ccc1TaskId,
  taskFingerprint:grant.fingerprint,sourceCommit:grant.task.sourceCommit,chain:grant.task.chain,
  status:result.outcome==='COMPLETED'?'PATCH_PENDING_OWNER_REVIEW':result.outcome,
  receipts,approvedForMerge:false,deviceVerified:false,neuralTraining:false};
}
