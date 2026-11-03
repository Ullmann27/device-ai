/** Correlation adapter for the existing CCC1 -> fixed-profile executor path.
 * The canonical owner journal and existing backlog claim remain the authorities. */
import fs from 'node:fs';import path from 'node:path';
import {DurableTaskJournal,runtimeKey} from './ccc1-journal.mjs';
import {pendingRequest,jobIdFor,recover,teamleaderStateOf} from './ccc1-recovery.mjs';
import {taskState} from './ccc1-orchestrator.mjs';
import {gateTestRequest} from './ccc1-gate.mjs';
import {ensureCcc1StateOutsideSource} from './ccc1-path-policy.mjs';
import {BacklogStore} from './development-backlog.mjs';
import {jobTaskId} from '../../scripts/continuum-mobile-coding.mjs';
import {isIsolatedExecutor} from './isolated-executor.mjs';

export function bindCcc1Workspace({executor,project,stateDir,taskId,job,workerId,identityGuard=null}){
 if(!isIsolatedExecutor(executor)||typeof executor.forWorkspace!=='function')throw Error('WORKSPACE_ISOLATION_REQUIRED');
 if(identityGuard!==null&&typeof identityGuard!=='function')throw Error('WORKSPACE_IDENTITY_GUARD_INVALID');
 if(!/^t-[a-f0-9]{16}$/.test(taskId??''))throw Error('WORKSPACE_TASK_INVALID');
 const state=ensureCcc1StateOutsideSource(project,stateDir);
 const journal=DurableTaskJournal.forTask(state,taskId,runtimeKey(state));
 const backlog=path.join(teamleaderStateOf(state),'development-backlog.json');
 let expected=null;
 function inspect(lease=null,{initial=false}={}){
  if(identityGuard!==null&&identityGuard()?.agentId!==workerId)throw Error('WORKSPACE_IDENTITY_CHANGED');
  // Never repair/truncate a torn journal here. Every profile reads current MACs,
  // task/revocation, pending request and recovery from the existing authorities.
  const entries=journal.entries(),goal=entries[0],request=pendingRequest(entries),status=taskState(entries);
  if(!goal||goal.taskId!==taskId||status.terminal||!request||!request.payload.repair||
     jobIdFor(taskId,request.messageId)!==job||
     (expected&&(goal.hash!==expected.goalHash||request.hash!==expected.requestHash)))
   throw Error('WORKSPACE_REQUEST_MISMATCH');
  const {report}=recover({project,stateDir:state,journal});
  if(report.cleanCheckout!==true||!report.headMatches||Date.now()>=goal.deadline)
   throw Error('WORKSPACE_RECOVERY_BLOCKED: '+report.allowedNext);
  const chain=request.payload.repair.chain;
  // Admission checks the whole requested budget. Later checkpoints consume the
  // original claim/request deadline; they never request a fresh full budget.
  const payload=initial?request.payload:{...request.payload,budgetMinutes:0};
  if(goal.payload.repair?.chain!==chain||
     (status.approval?.requested&&status.approval.decision!=='GRANTED')||
     gateTestRequest(payload,entries,{rounds:status.rounds-1,cpuMillis:status.cpuMillis}).decision!=='ALLOW')
   throw Error('WORKSPACE_CAPABILITY_DENIED');
  if(lease===null){
   if(report.allowedNext!=='EXECUTE_PENDING')throw Error('WORKSPACE_RECOVERY_BLOCKED: '+report.allowedNext);
  }else{
   if(report.pending?.status!=='INTERRUPTED'||path.resolve(lease.backlog)!==backlog||
      lease.id!==jobTaskId(job)||!/^[a-f0-9]{32,128}$/.test(lease.token??''))throw Error('WORKSPACE_CLAIM_MISMATCH');
   const stat=fs.lstatSync(backlog);
   if(!stat.isFile()||stat.isSymbolicLink()||fs.realpathSync(backlog)!==backlog)throw Error('WORKSPACE_CLAIM_PATH_UNSAFE');
   const task=new BacklogStore(backlog,{actor:'ccc1-workspace-observer'}).read().doc.tasks.find(t=>t.id===lease.id);
   if(task?.kind!=='REPAIR'||task.source!=='owner'||task.state!=='IN_PROGRESS'||
      task.subject?.base!==goal.baseCommitSha||task.subject?.chain!==chain||task.claim?.token!==lease.token||
      (lease.claimAt!==undefined&&task.claim.at!==lease.claimAt)||task.claim.leaseUntil<=Date.now())
    throw Error('WORKSPACE_CLAIM_MISMATCH');
   return {goal,request,chain,claimAt:task.claim.at,
    deadline:Math.min(goal.deadline,task.claim.leaseUntil,task.claim.at+request.payload.budgetMinutes*60_000)};
  }
  return {goal,request,chain};
 }
 const initial=inspect(null,{initial:true});
 expected={goalHash:initial.goal.hash,requestHash:initial.request.hash};
 const profiles=initial.chain==='node'?new Set(['node-continuum']):
  initial.chain==='android'?new Set(['android-compile','android-unit']):new Set();
 if(!profiles.size)throw Error('WORKSPACE_CHAIN_UNAVAILABLE');
 const binding={workerId,taskId,requestMessageId:initial.request.messageId,requestHash:initial.request.hash,
  sourceCommit:initial.goal.baseCommitSha,jobId:job};
 function guard(lease=null){
  return {
   before(options={}){
    const current=inspect(lease);
    if(lease===null)throw Error('WORKSPACE_CLAIM_REQUIRED');
    if(options.deadline!==null&&options.deadline!==undefined&&!Number.isSafeInteger(options.deadline))throw Error('WORKSPACE_DEADLINE_INVALID');
    const deadline=Math.min(current.deadline,options.deadline??Infinity);
    if(deadline<=Date.now())throw Error('WORKSPACE_DEADLINE_EXHAUSTED');
    return {deadline};
   },
   after(){inspect(lease);},
   forClaim(value){
    if(lease!==null||!value||typeof value!=='object'||Array.isArray(value)||
       Object.keys(value).sort().join(',')!=='backlog,id,token'||typeof value.backlog!=='string')throw Error('WORKSPACE_CLAIM_MISMATCH');
    const captured={...value},current=inspect(captured);captured.claimAt=current.claimAt;
    return guard(Object.freeze(captured));
   }
  };
 }
 return executor.forWorkspace(binding,profiles,guard());
}
