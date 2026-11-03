/** Correlation adapter for the existing CCC1 -> fixed-profile executor path.
 * Reads the existing verified journal/recovery; creates no state or grants. */
import {DurableTaskJournal,runtimeKey} from './ccc1-journal.mjs';
import {pendingRequest,jobIdFor,recover} from './ccc1-recovery.mjs';
import {taskState} from './ccc1-orchestrator.mjs';
import {gateTestRequest} from './ccc1-gate.mjs';
import {ensureCcc1StateOutsideSource} from './ccc1-path-policy.mjs';
import {isIsolatedExecutor} from './isolated-executor.mjs';

export function bindCcc1Workspace({executor,project,stateDir,taskId,job,workerId}){
 if(!isIsolatedExecutor(executor)||typeof executor.forWorkspace!=='function')throw Error('WORKSPACE_ISOLATION_REQUIRED');
 if(!/^t-[a-f0-9]{16}$/.test(taskId??''))throw Error('WORKSPACE_TASK_INVALID');
 const state=ensureCcc1StateOutsideSource(project,stateDir);
 const journal=DurableTaskJournal.forTask(state,taskId,runtimeKey(state));
 // Verification precedes recover: do not silently truncate a torn tail here.
 const entries=journal.entries(),goal=entries[0],request=pendingRequest(entries),status=taskState(entries);
 if(!goal||goal.taskId!==taskId||status.terminal||!request||
    !request.payload.repair||jobIdFor(taskId,request.messageId)!==job)
  throw Error('WORKSPACE_REQUEST_MISMATCH');
 const {report}=recover({project,stateDir:state,journal});
 if(report.allowedNext!=='EXECUTE_PENDING'||report.cleanCheckout!==true||Date.now()>=goal.deadline)
  throw Error('WORKSPACE_RECOVERY_BLOCKED: '+report.allowedNext);
 const chain=request.payload.repair.chain;
 if(goal.payload.repair?.chain!==chain||
    (status.approval?.requested&&status.approval.decision!=='GRANTED')||
    gateTestRequest(request.payload,entries,{rounds:status.rounds-1,cpuMillis:status.cpuMillis}).decision!=='ALLOW')
  throw Error('WORKSPACE_CAPABILITY_DENIED');
 const profiles=chain==='node'?new Set(['node-continuum']):
  chain==='android'?new Set(['android-compile','android-unit']):new Set();
 if(!profiles.size)throw Error('WORKSPACE_CHAIN_UNAVAILABLE');
 return executor.forWorkspace({workerId,taskId,requestMessageId:request.messageId,requestHash:request.hash,
  sourceCommit:goal.baseCommitSha,jobId:job},profiles);
}
