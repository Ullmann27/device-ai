/** Trusted composition seam, not a JSON command or another registry.
 * A host/transport supplies read access to the EXISTING persistent profile store.
 * Profiles select identities only; the prepared owner task and canonical journal
 * still grant every execution. Missing Android transport remains unavailable. */
import {validateCodingTask,taskFingerprint} from './coding-task.mjs';
import {canonical,sha256Hex} from './development-backlog.mjs';

const ADMISSIONS=new WeakMap();
const ID=/^[a-z][a-z0-9-]{2,63}$/;
function profile(store,id){
 if(!ID.test(id??''))throw Error('BOT_ASSIGNMENT_ID_INVALID');
 const value=store.get(id);
 if(!value||value.agentId!==id||value.workspaceId!=='workspace-'+id)
  throw Error('BOT_ASSIGNMENT_PROFILE_UNAVAILABLE');
 const budget=value.resourceBudget;
 if(!budget||!Number.isSafeInteger(budget.maxModelCalls)||budget.maxModelCalls<0||budget.maxModelCalls>8||
    !Number.isSafeInteger(budget.deadlineMillis)||budget.deadlineMillis<1||budget.deadlineMillis>600_000)
  throw Error('BOT_ASSIGNMENT_PROFILE_BUDGET_UNAVAILABLE');
 return Object.freeze({agentId:id,persistentWorkspaceId:value.workspaceId,
  maxModelCalls:budget.maxModelCalls,deadlineMillis:budget.deadlineMillis});
}

/** Called by trusted application composition after owner task preparation, never
 * exposed as a model tool. get() must read the authoritative existing store. */
export function createCcc1BotAssignment({task,ccc1TaskId,builderId,verifierId,reporterId=null,profileStore}={}){
 const spec=validateCodingTask(task);
 if(!/^t-[a-f0-9]{16}$/.test(ccc1TaskId??'')||!profileStore||typeof profileStore.get!=='function')
  throw Error('BOT_ASSIGNMENT_AUTHORITY_UNAVAILABLE');
 if(builderId===verifierId)throw Error('BOT_ASSIGNMENT_INDEPENDENT_PRINCIPAL_REQUIRED');
 if(reporterId!==null&&[builderId,verifierId].includes(reporterId))throw Error('BOT_ASSIGNMENT_REPORTER_DISTINCT_REQUIRED');
 // Capture the trusted reader; replacing profileStore.get later cannot change the port.
 const store=Object.freeze({get:profileStore.get.bind(profileStore)});
 const builder=profile(store,builderId),verifier=profile(store,verifierId);
 const reporter=reporterId===null?null:profile(store,reporterId);
 if(spec.limits.maxModelCalls>builder.maxModelCalls)throw Error('BOT_ASSIGNMENT_MODEL_BUDGET_EXCEEDED');
 const binding=Object.freeze({schema:'continuum-bot-assignment',version:1,
  taskId:spec.id,ccc1TaskId,taskFingerprint:taskFingerprint(spec),sourceCommit:spec.sourceCommit,
  builder,verifier,reporter,expiresAt:Date.now()+Math.min(builder.deadlineMillis,verifier.deadlineMillis,reporter?.deadlineMillis??Infinity)});
 const assignment=Object.freeze({...binding,sha256:sha256Hex(canonical(binding))});
 ADMISSIONS.set(assignment,{store,binding});
 return assignment;
}

/** Process-private origin plus exact prepared-task identity; JSON copies cannot activate roles. */
export function assertCcc1BotAssignment(assignment,{task,ccc1TaskId,stage}={}){
 const admission=ADMISSIONS.get(assignment);
 if(!admission)throw Error('BOT_ASSIGNMENT_TRUSTED_ORIGIN_REQUIRED');
 const spec=validateCodingTask(task),binding=admission.binding;
 if(binding.taskId!==spec.id||binding.ccc1TaskId!==ccc1TaskId||
    binding.taskFingerprint!==taskFingerprint(spec)||binding.sourceCommit!==spec.sourceCommit)
  throw Error('BOT_ASSIGNMENT_TASK_MISMATCH');
 if(Date.now()>=binding.expiresAt)throw Error('BOT_ASSIGNMENT_EXPIRED');
 if(!['BUILD','VERIFY','REPORT'].includes(stage)||stage==='REPORT'&&binding.reporter===null)throw Error('BOT_ASSIGNMENT_STAGE_INVALID');
 // Re-resolve BOTH principals at every canonical checkpoint. Rename is harmless;
 // deletion or workspace reassignment invalidates this execution association.
 for(const value of [binding.builder,binding.verifier,binding.reporter].filter(Boolean)){
  const current=profile(admission.store,value.agentId);
  if(current.persistentWorkspaceId!==value.persistentWorkspaceId)throw Error('BOT_ASSIGNMENT_WORKSPACE_CHANGED');
  if(current.maxModelCalls<value.maxModelCalls||current.deadlineMillis<value.deadlineMillis)
   throw Error('BOT_ASSIGNMENT_PROFILE_BUDGET_CHANGED');
 }
 return Object.freeze({...stage==='BUILD'?binding.builder:stage==='VERIFY'?binding.verifier:binding.reporter,expiresAt:binding.expiresAt});
}
