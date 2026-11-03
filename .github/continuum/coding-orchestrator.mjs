/** Typed owner task adapter for existing CCC1 -> mobile job -> teamleader -> repair -> verifier. */
import fs from 'node:fs';import path from 'node:path';
import {validateCodingTask,validateCodingProposal,taskFingerprint} from './coding-task.mjs';
import {canonical,sha256Hex} from './development-backlog.mjs';
import {createCodingCandidateProvider} from './coding-provider.mjs';
import * as codingRuntime from './coding-provider.mjs';
import {runtimeKey} from './ccc1-journal.mjs';
import {createAgentProviderFromEnv} from './coding-agent-loop.mjs';
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
import {bindCcc1Workspace} from './ccc1-workspace.mjs';
import {ExperienceLog,experienceOf} from './ccc1-experience.mjs';
import {assertCcc1BotAssignment} from './ccc1-bot-assignment.mjs';

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
function checkedTaskObservation(task,observation,seen){
 if(codingRuntime.isTrustedLocalModelObservation?.(observation)!==true)
  throw Error('CODING_LOCAL_MODEL_OBSERVATION_REQUIRED');
 // Process identity alone is insufficient. The reviewed runtime producer must
 // independently establish the stronger immutable execution observation.
 if(!Object.isFrozen(observation)||observation.verifiedModelExecution!==true)throw Error('BLOCKED_LOCAL_MODEL_UNAVAILABLE');
 const spec=validateCodingTask(task);
 if(observation.taskId!==spec.id||observation.taskFingerprint!==taskFingerprint(spec)||
    observation.sourceCommit!==spec.sourceCommit||
    seen.has(observation.executionId))throw Error('CODING_LOCAL_MODEL_OBSERVATION_REQUIRED');
 return observation;
}
/** Process-private runtime admission plus exact task/proposal binding. JSON flags grant nothing. */
export function assertCodingModelObservationBinding({task,raw,seen=new Set()}={}){
 const observation=checkedTaskObservation(task,raw?.trace?.modelObservation,seen);
 const proposal=validateCodingProposal({plan:raw.plan,hypothesis:raw.hypothesis,patches:raw.patches},validateCodingTask(task));
 if(observation.proposalSha256!==sha256Hex(canonical(proposal)))throw Error('CODING_LOCAL_MODEL_OBSERVATION_REQUIRED');
 seen.add(observation.executionId);return observation;
}
/** Records an observed failed inference, never an executable proposal or success. */
export function assertCodingInferenceFailureBinding({task,error,seen=new Set()}={}){
 const observation=checkedTaskObservation(task,error?.modelObservation,seen);
 if(!['CODING_RESPONSE_INVALID','CODING_NOOP_PATCH','CODING_PROPOSAL_INVALID'].includes(error?.code)||
    !(observation.responseStatus==='INVALID_MODEL_RESPONSE'&&observation.proposalSha256===null||
      observation.responseStatus==='PROPOSAL'&&/^[a-f0-9]{64}$/.test(observation.proposalSha256??'')))
  throw Error('CODING_LOCAL_MODEL_OBSERVATION_REQUIRED');
 seen.add(observation.executionId);return observation;
}
export async function runCodingTask({project,stateDir,id,executor=null,executorAbortMode=null,
 requireIsolation=false,requireLocalModelObservation=false,expectedTaskFingerprint=null,
 technicalRecallEnabled=true,botAssignment=null,
 providerFactory=options=>(options.env?.CONTINUUM_CODING_AGENT_LOOP==='1'?createAgentProviderFromEnv:createCodingCandidateProvider)(options),
 env=process.env}={}){
 if(typeof technicalRecallEnabled!=='boolean')throw Error('CODING_RECALL_OPTION_INVALID');
 // Android untrusted-model execution is never allowed to inherit the legacy
 // host fallback. A branded executor is required BEFORE journal/model access.
 const strictMomega=requireLocalModelObservation||env.CONTINUUM_MOMEGA1==='1'||botAssignment!==null;
 if(strictMomega&&env.CONTINUUM_CODING_AGENT_LOOP==='1')throw Error('CODING_ADMITTED_AGENT_UNAVAILABLE');
 if((requireIsolation||strictMomega) && (!isIsolatedExecutor(executor) || executorAbortMode!=='CHECKPOINT_BOUNDARY_ONLY'))
  throw Error('CODING_ISOLATION_REQUIRED');
 const grant=readGrant(grantFile(project,stateDir,id));
 // The grant file name, embedded task identity and journal identity must agree.
 if(grant.task.id!==id || !/^t-[a-f0-9]{16}$/.test(grant.ccc1TaskId))
  throw Error('CODING_GRANT_CORRUPT');
 const botIdentity=stage=>assertCcc1BotAssignment(botAssignment,{task:grant.task,ccc1TaskId:grant.ccc1TaskId,stage});
 if(botAssignment!==null){
  if(!isIsolatedExecutor(executor)||executorAbortMode!=='CHECKPOINT_BOUNDARY_ONLY')throw Error('CODING_ISOLATION_REQUIRED');
  botIdentity('BUILD');botIdentity('VERIFY');
 }
 // Check the owner's prepared-task SHA BEFORE CCC1 journal mutation, model
 // inference or running any untrusted candidate. Null preserves older callers.
 if(expectedTaskFingerprint!==null &&
    (!/^[a-f0-9]{64}$/.test(expectedTaskFingerprint) ||
     grant.fingerprint!==expectedTaskFingerprint))
  throw Error('CODING_TASK_BINDING_MISMATCH');
 if(git(project,['rev-parse','HEAD']).stdout.trim()!==grant.task.sourceCommit)throw Error('CODING_TASK_STALE_SOURCE');
 if(git(project,['status','--porcelain']).stdout.trim())throw Error('CODING_TASK_DIRTY_SOURCE');
 const receipts=[],technicalCandidates=[],seenObservations=new Set();
 let admittedModelCalls=0,modelAdmissionFailure=null;
 let verifiedInferenceCalls=0,rejectedModelResponses=0,modelIdentity=null;
 const observeInference=observation=>{
  const identity={modelSha256:observation.modelSha256,runtimeSha256:observation.runtimeSha256,
   reviewedPairId:observation.reviewedPairId};
  if(modelIdentity&&canonical(modelIdentity)!==canonical(identity))throw Error('CODING_LOCAL_MODEL_CHANGED');
  modelIdentity=identity;verifiedInferenceCalls++;
 };
 let technicalRecall={items:[],text:''},technicalExperienceStatus='NOT_RECORDED';
 let technical=null;
 try{
  technical=await import('./ccc1-technical-recall.mjs');
  if(technicalRecallEnabled)technicalRecall=technical.recallTechnicalExperiences({stateDir,project,
   task:grant.task,repoFullName:repositoryOf(project),key:runtimeKey(stateDir)});
 }catch{technicalRecall={items:[],text:''};}
 const result=await runTask({project,stateDir,taskId:grant.ccc1TaskId,
  reasoner:new Ccc1Reasoner({candidates:[],repairBudgetMinutes:20}),maxTurns:16,
  runRepairJob:async order=>{
   if(order.chain!==grant.task.chain)throw Error('CODING_CHAIN_BINDING_MISMATCH');
   const task=validateCodingTask({...grant.task,id:jobTaskId(order.job)});
   const evidenceDir=path.join(stateDir,'coding-evidence',grant.task.id,order.job);
   const jobExecutor=executor===null?null:bindCcc1Workspace({executor,project,stateDir,
    taskId:grant.ccc1TaskId,job:order.job,workerId:botAssignment===null?'typed-coding-worker':botIdentity('BUILD').agentId,
    identityGuard:botAssignment===null?null:()=>botIdentity('BUILD')});
   const verifierExecutor=botAssignment===null?null:bindCcc1Workspace({executor,project,stateDir,
    taskId:grant.ccc1TaskId,job:order.job,workerId:botIdentity('VERIFY').agentId,
    identityGuard:()=>botIdentity('VERIFY')});
   const supplied=providerFactory({root:project,task,env,evidenceDir,executor:jobExecutor,executorAbortMode,
     technicalRecall:technicalRecall.text,requireLocalModelObservation:strictMomega});
   if(strictMomega&&taskFingerprint(supplied.taskSpec)!==taskFingerprint(task))throw Error('CODING_TASK_BINDING_MISMATCH');
   const candidateProvider=Object.assign(input=>{
    let raw;
    try{raw=supplied(input);}catch(error){
     if(strictMomega&&error?.modelObservation){
      try{observeInference(assertCodingInferenceFailureBinding({task,error,seen:seenObservations}));rejectedModelResponses++;}
      catch(admission){modelAdmissionFailure=admission.message;throw admission;}
     }
     if(strictMomega&&verifiedInferenceCalls===0)modelAdmissionFailure=error?.code??modelAdmissionFailure;
     throw error;
    }
    if(strictMomega){
     try{observeInference(assertCodingModelObservationBinding({task,raw,seen:seenObservations}));admittedModelCalls++;}
     catch(error){modelAdmissionFailure=error.message;throw error;}
    }
    return raw;
   },{taskSpec:supplied.taskSpec,allowedPaths:supplied.allowedPaths,
     onVerifiedCandidate:entry=>technicalCandidates.push(entry)});
   const driver=await localTeamleaderDriver({candidateProvider,executor:jobExecutor,verifierExecutor,executorAbortMode});
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
 if(strictMomega&&modelAdmissionFailure==='CODING_LOCAL_MODEL_CHANGED')throw Error(modelAdmissionFailure);
 if(strictMomega&&verifiedInferenceCalls===0)
  throw Error(modelAdmissionFailure??'CODING_LOCAL_MODEL_OBSERVATION_REQUIRED');
 for(const candidate of technicalCandidates){
  try{
   if(!technical)continue;
   const recorded=technical.recordTechnicalExperience({stateDir,taskId:grant.ccc1TaskId,task:grant.task,
    ...candidate,key:runtimeKey(stateDir)});
   technicalExperienceStatus=recorded?'RECORDED_TECHNICAL_REVIEW_ONLY':'EXISTING_SUMMARY_NOT_UPGRADED';
  }catch{technicalExperienceStatus='TECHNICAL_PROOF_UNAVAILABLE';}
 }
 const experience=experienceOf(grant.ccc1TaskId,result.entries??[],{coreId:'typed-coding-adapter-v1'});
 if(experience)new ExperienceLog(path.join(stateDir,'experience.jsonl')).append(experience);
 return {schema:'continuum-coding-task-result',version:1,id,ccc1TaskId:grant.ccc1TaskId,
  taskFingerprint:grant.fingerprint,sourceCommit:grant.task.sourceCommit,chain:grant.task.chain,
  status:result.outcome==='COMPLETED'?'PATCH_PENDING_OWNER_REVIEW':result.outcome,
  receiptJobs,receipts,technicalExperienceStatus,technicalRecallCount:technicalRecall.items.length,
  ...(botAssignment===null?{}:{botAssignment:{sha256:botAssignment.sha256,builder:botAssignment.builder,
   verifier:botAssignment.verifier,status:'ASSIGNED_IDENTITIES_ONLY',executionEvidence:'FIXED_PROFILE_OBSERVATIONS'}}),
  ...(strictMomega?{modelExecution:{verifiedCalls:verifiedInferenceCalls,acceptedProposals:admittedModelCalls,
   rejectedResponses:rejectedModelResponses,identity:modelIdentity}}:{}),approvedForMerge:false,deviceVerified:false,neuralTraining:false};
}
