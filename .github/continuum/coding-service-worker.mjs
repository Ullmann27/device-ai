/** Fixed trusted child entrypoint. No shell command, provider injection or remote
 * teacher can be supplied by an inbox task. Parent persists intent before GO. */
import path from 'node:path';
import {createHmac} from 'node:crypto';
import {canonical} from './development-backlog.mjs';
import {runtimeKey} from './ccc1-journal.mjs';
import {loadCodingServiceConfig,readDeliveries,assertServiceArtifacts,serviceEnvironment,writePrivateJson} from './coding-service-store.mjs';
import {currentCanonicalTask,readServiceDescriptor} from './coding-service-reader.mjs';
import {createCcc1BotAssignment} from './ccc1-bot-assignment.mjs';
import {profileStoreReader} from '../../tools/genesis-workspaces/adapter-bridge.mjs';
import {runCodingTask} from './coding-orchestrator.mjs';
import {ownerCodingExecutor} from '../../scripts/continuum-typed-coding-bridge.mjs';

function errorCode(error){const value=String(error?.code??error?.message??'SERVICE_WORKER_FAILED');return /^[A-Z][A-Z0-9_]{2,100}$/.test(value)?value:'SERVICE_WORKER_FAILED';}
export async function executeServiceDelivery({configPath,taskId,instanceId}={}){
 const config=loadCodingServiceConfig(configPath);assertServiceArtifacts(config);
 const delivery=readDeliveries(config).find(item=>item.id===taskId),processRecord=readServiceDescriptor(config);
 if(!delivery||delivery.delivery!=='DISPATCHED'||delivery.instanceId!==instanceId||
   delivery.workerProcess?.pid!==process.pid||processRecord?.instanceId!==instanceId||processRecord.activeTaskId!==taskId)
  throw Error('SERVICE_DISPATCH_NOT_CURRENT');
 const current=currentCanonicalTask(config,delivery,{requireCurrent:true});
 if(current.status.terminal)throw Error('SERVICE_TASK_ALREADY_ENDED');
 const env=serviceEnvironment(config),executor=ownerCodingExecutor({project:config.project,env});
 const assignment=createCcc1BotAssignment({task:current.task,ccc1TaskId:delivery.ccc1TaskId,
  builderId:'bot-forge',verifierId:'bot-lint',reporterId:'bot-echo',
  profileStore:profileStoreReader(config.profileDirectory,{classpath:config.profileClasspath})});
 const started=Date.now(),before=process.resourceUsage();
 const result=await runCodingTask({project:config.project,stateDir:current.state,id:taskId,executor,
  executorAbortMode:'CHECKPOINT_BOUNDARY_ONLY',requireIsolation:true,requireLocalModelObservation:true,
  expectedTaskFingerprint:delivery.fingerprint,technicalRecallEnabled:true,botAssignment:assignment,env});
 const after=process.resourceUsage(),final=currentCanonicalTask(config,delivery),report=result.botReport;
 const observation=result.modelExecution??{};
 const identity=observation.identity;
 const snapshot={schema:'continuum-service-result',version:1,taskId,ccc1TaskId:delivery.ccc1TaskId,
  taskFingerprint:delivery.fingerprint,sourceCommit:current.task.sourceCommit,journalHead:final.entries.at(-1).hash,
  resultStatus:result.status,modelExecution:{verifiedCalls:observation.verifiedCalls??0,
   acceptedProposals:observation.acceptedProposals??0,rejectedResponses:observation.rejectedResponses??0,
   identity:identity?{modelSha256:identity.modelSha256,runtimeSha256:identity.runtimeSha256,reviewedPairId:identity.reviewedPairId}:null},
  technicalExperienceStatus:result.technicalExperienceStatus,technicalRecallCount:result.technicalRecallCount,
  elapsedMs:Date.now()-started,resourceUsage:{scope:'NODE_CONTROL_PROCESS_NOT_NATIVE_CHILD_TOTAL',
   userCpuMicros:after.userCPUTime-before.userCPUTime,systemCpuMicros:after.systemCPUTime-before.systemCPUTime,maxRssKiB:after.maxRSS},
  reportFile:report?.path?path.basename(report.path):null,reportSha256:report?.sha256??null,
  environment:'HOST',deviceVerified:false,approvedForMerge:false};
 // Reuse the existing trusted CCC1 key. These are runner metrics, not a second
 // task outcome or verifier receipt; current canonical records remain required.
 const metricsMac=createHmac('sha256',runtimeKey(current.state)).update(canonical(snapshot)).digest('hex');
 writePrivateJson(path.join(config.stateDir,'public-evidence',taskId+'.json'),{...snapshot,metricsMac},{exclusive:true});
 return {type:'result',taskId,ok:true};
}
// Importing this module cannot start work; the fixed IPC child must receive one
// parent-issued GO, after its process identity has been durably recorded.
if(process.send){
 let consumed=false;
 process.once('disconnect',()=>{if(!consumed)process.exit(1);});
 process.on('message',async message=>{
  if(consumed||message?.type!=='run')return;consumed=true;
  let response;
  try{response=await executeServiceDelivery(message);}
  catch(error){response={type:'result',taskId:message.taskId,ok:false,error:errorCode(error)};}
  if(process.connected)process.send(response,()=>{process.disconnect();process.exitCode=response.ok?0:1;});
  else process.exitCode=1;
 });
 process.send({type:'ready'});
}
