/** Read-only, bounded projection of the existing profile store and CCC1 journal.
 * No runtime key creation, journal repair, model call or work dispatch occurs here. */
import fs from 'node:fs';
import path from 'node:path';
import {createHmac,timingSafeEqual} from 'node:crypto';
import {loadCodingServiceConfig,readDeliveries,privateJson,plainBytes,realDirectory,sameLiveProcess,gitIdentity,SERVICE_ID,assertProfileBridge,hostExecutionBarrier} from './coding-service-store.mjs';
import {DurableTaskJournal} from './ccc1-journal.mjs';
import {taskState} from './ccc1-orchestrator.mjs';
import {taskFingerprint,validateCodingTask} from './coding-task.mjs';
import {sha256Hex,canonical} from './development-backlog.mjs';
import {verifyRepairReceipt} from './ccc1-verifier.mjs';
import {builderStateOf,jobIdFor} from './ccc1-recovery.mjs';
import {readReceipt} from '../../scripts/continuum-mobile-coding.mjs';
import {profileStoreReader} from '../../tools/genesis-workspaces/adapter-bridge.mjs';

const pageArgs=({cursor=null,limit=20}={})=>{
 if(cursor!==null&&!SERVICE_ID.test(cursor)||!Number.isSafeInteger(limit)||limit<1||limit>50)throw Error('SERVICE_PAGE_INVALID');
 return {cursor,limit};
};
export function readServiceDescriptor(config){
 const file=path.join(config.stateDir,'service.json');
 if(!fs.existsSync(file))return null;
 const value=privateJson(file,16384);
 if(value.schema!=='continuum-coding-service-process'||value.version!==1||!/^[a-f0-9]{32}$/.test(value.instanceId??'')||
   value.configSha256!==config.configSha256)throw Error('SERVICE_DESCRIPTOR_INVALID');
 return value;
}
export function currentCanonicalTask(config,delivery,{requireCurrent=false}={}){
 if(!delivery?.ccc1TaskId)throw Error('SERVICE_TASK_RESERVATION_INCOMPLETE');
 const state=path.join(config.stateDir,'ccc1');realDirectory(state);
 const grant=privateJson(path.join(state,'coding-grants',delivery.id+'.json'),16384);
 const task=validateCodingTask(grant.task);
 if(task.id!==delivery.id||taskFingerprint(task)!==delivery.fingerprint||grant.fingerprint!==delivery.fingerprint||
   grant.ccc1TaskId!==delivery.ccc1TaskId)throw Error('SERVICE_GRANT_CHANGED');
 const journalFile=path.join(state,'tasks',delivery.ccc1TaskId+'.jsonl');
 plainBytes(journalFile,4*1024*1024,{privateFile:true});
 const key=plainBytes(path.join(state,'ccc1-runtime.key'),32,{privateFile:true});
 if(key.length!==32)throw Error('SERVICE_JOURNAL_KEY_INVALID');
 const entries=new DurableTaskJournal(journalFile,key).entries(),goal=entries[0],status=taskState(entries);
 if(!goal||goal.taskId!==delivery.ccc1TaskId||goal.baseCommitSha!==task.sourceCommit||
   !goal.payload.text.includes('[task '+delivery.fingerprint+']')||goal.payload.repair?.chain!==task.chain||
   !goal.permissions.includes('node-repair:'+task.chain))throw Error('SERVICE_JOURNAL_BINDING_INVALID');
 const grantCurrent=goal.deadline>Date.now()&&!entries.some(e=>e.kind==='ABORT')&&
   !(status.approval?.requested&&status.approval.decision!=='GRANTED');
 let sourceCurrent=false;
 try{sourceCurrent=gitIdentity(config.project,{clean:true})===task.sourceCommit&&gitIdentity(config.sourceRoot,{clean:true})===config.sourceCommit;}catch{}
 if(requireCurrent&&!grantCurrent)throw Error('SERVICE_CURRENT_GRANT_REQUIRED');
 if(requireCurrent&&!sourceCurrent)throw Error('SERVICE_CURRENT_SOURCE_REQUIRED');
 return {task,grant,entries,status,grantCurrent,sourceCurrent,state};
}
function evidence(config,delivery,evidenceId){
 if(!['result','report'].includes(evidenceId))throw Error('SERVICE_EVIDENCE_ID_INVALID');
 const current=currentCanonicalTask(config,delivery,{requireCurrent:true});
 const result=privateJson(path.join(config.stateDir,'public-evidence',delivery.id+'.json'),65536);
 const {metricsMac,...metrics}=result;
 const expectedMac=createHmac('sha256',plainBytes(path.join(current.state,'ccc1-runtime.key'),32,{privateFile:true})).update(canonical(metrics)).digest('hex');
 if(!/^[a-f0-9]{64}$/.test(metricsMac??'')||!timingSafeEqual(Buffer.from(metricsMac,'hex'),Buffer.from(expectedMac,'hex')))
  throw Error('SERVICE_METRICS_AUTHENTICATION_FAILED');
 if(result.schema!=='continuum-service-result'||result.version!==1||result.taskId!==delivery.id||
   result.ccc1TaskId!==delivery.ccc1TaskId||result.taskFingerprint!==delivery.fingerprint||
   result.sourceCommit!==current.task.sourceCommit||result.journalHead!==current.entries.at(-1)?.hash)
  throw Error('SERVICE_RESULT_STALE');
 // Re-read canonical receipts; a public snapshot and a text status never attest execution.
 const receipts=[];
 for(const entry of current.entries.filter(e=>e.kind==='RECEIPT')){
  const request=current.entries.find(e=>e.kind==='TEST_REQUEST'&&e.messageId===entry.payload.request);
  if(!request||jobIdFor(delivery.ccc1TaskId,request.messageId)!==entry.payload.job)throw Error('SERVICE_RECEIPT_BINDING_INVALID');
  plainBytes(path.join(builderStateOf(current.state),'coding-jobs',entry.payload.job+'.json'),4*1024*1024,{privateFile:true});
  const receipt=readReceipt(builderStateOf(current.state),entry.payload.job);
  if(!receipt||sha256Hex(JSON.stringify(receipt))!==entry.payload.receiptSha256)throw Error('SERVICE_RECEIPT_CHANGED');
  if(entry.payload.verdict==='CONFIRMED'&&verifyRepairReceipt(receipt,{job:entry.payload.job,commit:current.task.sourceCommit,chain:current.task.chain}).verdict!=='CONFIRMED')
   throw Error('SERVICE_RECEIPT_NOT_CONFIRMED');
  receipts.push({jobId:entry.payload.job,sha256:entry.payload.receiptSha256,verdict:entry.payload.verdict,
   patchSha256:receipt.patch?.sha256??null});
 }
 if(current.status.state==='COMPLETED'&&!receipts.some(r=>r.verdict==='CONFIRMED'))throw Error('SERVICE_NO_CONFIRMED_RECEIPT');
 assertProfileBridge(config);
 const store=profileStoreReader(config.profileDirectory,{classpath:config.profileClasspath});
 for(const id of ['bot-forge','bot-lint','bot-echo'])if(store.get(id)?.workspaceId!=='workspace-'+id)throw Error('SERVICE_PROFILE_UNAVAILABLE');
 let content;
 if(evidenceId==='report'){
  if(!/^[a-f0-9]{64}$/.test(result.reportSha256??'')||!/^bot-report-[a-f0-9]{64}\.json$/.test(result.reportFile??''))throw Error('SERVICE_REPORT_UNAVAILABLE');
  const file=path.join(current.state,'coding-evidence',delivery.id,result.reportFile);
  const bytes=plainBytes(file,32768,{privateFile:true});
  if(sha256Hex(bytes)!==result.reportSha256)throw Error('SERVICE_REPORT_CHANGED');
  const report=JSON.parse(bytes);
  if(report.taskId!==delivery.id||report.canonicalTaskId!==delivery.ccc1TaskId||report.taskFingerprint!==delivery.fingerprint||
    report.sourceCommit!==current.task.sourceCommit||report.journalHead!==current.entries.at(-1).hash||report.reporterId!=='bot-echo')throw Error('SERVICE_REPORT_BINDING_INVALID');
  content={schema:'continuum-bot-result-report',version:1,reporterId:'bot-echo',taskId:delivery.id,canonicalTaskId:delivery.ccc1TaskId,
   sourceCommit:report.sourceCommit,journalHead:report.journalHead,status:current.status.state,
   interpretation:current.status.state==='COMPLETED'?'PATCH_PENDING_OWNER_REVIEW':current.status.terminal?'NO_CONFIRMED_REPAIR':'PENDING_OR_UNKNOWN',
   receipts,modelCalls:0,independentVerifier:false,modelInferenceAttestedByReport:false,deviceVerified:false,approvedForMerge:false};
 }else{
  const m=result.modelExecution,r=result.resourceUsage,integer=(v,max=1e12)=>Number.isSafeInteger(v)&&v>=0&&v<=max;
  if(!m||!r||!integer(m.verifiedCalls,current.task.limits.maxModelCalls)||!integer(m.acceptedProposals,m.verifiedCalls)||
    !integer(m.rejectedResponses,m.verifiedCalls)||m.acceptedProposals+m.rejectedResponses!==m.verifiedCalls||
    !m.identity||m.identity.modelSha256!==config.runtimeEnv.CONTINUUM_LOCAL_BRAIN_MODEL_SHA256||
    m.identity.runtimeSha256!==config.runtimeEnv.CONTINUUM_LOCAL_BRAIN_RUNTIME_SHA256||
    !/^[a-z0-9-]{1,160}$/.test(m.identity.reviewedPairId??'')||!integer(result.elapsedMs)||
    !integer(r.userCpuMicros)||!integer(r.systemCpuMicros)||!integer(r.maxRssKiB)||
    r.scope!=='NODE_CONTROL_PROCESS_NOT_NATIVE_CHILD_TOTAL'||!integer(result.technicalRecallCount,100)||
    !['NOT_RECORDED','RECORDED_TECHNICAL_REVIEW_ONLY','EXISTING_SUMMARY_NOT_UPGRADED','TECHNICAL_PROOF_UNAVAILABLE'].includes(result.technicalExperienceStatus))
   throw Error('SERVICE_METRICS_INVALID');
  content={schema:'continuum-service-result',version:1,taskId:delivery.id,canonicalTaskId:delivery.ccc1TaskId,
  taskFingerprint:delivery.fingerprint,sourceCommit:current.task.sourceCommit,journalHead:current.entries.at(-1).hash,
  status:current.status.state,resultStatus:current.status.state==='COMPLETED'?'PATCH_PENDING_OWNER_REVIEW':current.status.state,
  modelExecution:{verifiedCalls:m.verifiedCalls,acceptedProposals:m.acceptedProposals,rejectedResponses:m.rejectedResponses,
   identity:{modelSha256:m.identity.modelSha256,runtimeSha256:m.identity.runtimeSha256,reviewedPairId:m.identity.reviewedPairId}},
  technicalExperienceStatus:result.technicalExperienceStatus,technicalRecallCount:result.technicalRecallCount,
  elapsedMs:result.elapsedMs,resourceUsage:{scope:r.scope,userCpuMicros:r.userCpuMicros,systemCpuMicros:r.systemCpuMicros,maxRssKiB:r.maxRssKiB},
  receipts,environment:'HOST',deviceVerified:false,approvedForMerge:false};
 }
 return {taskId:delivery.id,evidenceId,sha256:sha256Hex(JSON.stringify(content)),mediaType:'application/json',content,environment:'HOST',deviceVerified:false};
}
export function createCodingServiceReader({configPath}={}){
 // Lazy load on EVERY call: replacing/revoking configuration never leaves retained read authority.
 const config=()=>loadCodingServiceConfig(configPath);
 return Object.freeze({
  getStatus(){
   const c=config(),d=readServiceDescriptor(c),items=readDeliveries(c),alive=d!==null&&sameLiveProcess(d.process);
   const orphan=items.some(item=>item.workerProcess&&sameLiveProcess(item.workerProcess)&&!alive);
   const active=Boolean(alive&&d.activeTaskId||orphan);
   let resourceReconciliationRequired=false;
   if(fs.existsSync(hostExecutionBarrier())){
    const barrier=privateJson(hostExecutionBarrier());
    resourceReconciliationRequired=barrier.status==='UNRESOLVED'||!sameLiveProcess(barrier.ownerProcess);
   }
   return {schema:'continuum-coding-service-status',version:1,
    state:resourceReconciliationRequired?'RESOURCE_RECONCILIATION_REQUIRED':alive?d.state:orphan?'ORPHAN_WORKER':'STOPPED',
    resourceReconciliationRequired,
    processVerified:alive,sourceCommit:c.sourceCommit,executionEnvironment:'HOST',deviceVerified:false,
    activeTaskId:alive?d.activeTaskId??null:null,queued:items.filter(i=>i.delivery==='PENDING').length,
    unknown:items.filter(i=>['UNKNOWN','RESERVING','DISPATCHED','CANCEL_REQUESTED'].includes(i.delivery)&&(!alive||i.delivery==='UNKNOWN')).length,
    capacity:c.limits.maxQueue,maxConcurrentInference:1,maxConcurrentInferenceScope:'ONE_SERVICE_PER_OS_USER',
    residentModelLoaded:false,modelLoaded:active||resourceReconciliationRequired?null:false,modelLoading:'ON_DEMAND_PER_TASK',
    inferenceObservation:resourceReconciliationRequired?'RESOURCE_TEARDOWN_UNCONFIRMED':active?'NATIVE_PHASE_NOT_OBSERVED_FROM_CONTROL_PROCESS':'NO_ACTIVE_TASK_IN_THIS_SERVICE',
    reviewedModel:c.runtimeEnv.CONTINUUM_LOCAL_BRAIN_MODEL,modelSha256:c.runtimeEnv.CONTINUUM_LOCAL_BRAIN_MODEL_SHA256,
    runtimeSha256:c.runtimeEnv.CONTINUUM_LOCAL_BRAIN_RUNTIME_SHA256,cloudModelCallsAllowed:false,
    cycles:alive?d.cycles??0:null,sessionLimit:c.limits.maxCycles,control:'LOCAL_OWNER_UNIX_SOCKET'};
  },
  listTasks(args={}){
   const {cursor,limit}=pageArgs(args),c=config(),all=readDeliveries(c).sort((a,b)=>a.id.localeCompare(b.id));
   const selected=all.filter(i=>cursor===null||i.id>cursor).slice(0,limit+1),hasMore=selected.length>limit;
   const items=selected.slice(0,limit).map(d=>{
    try{const current=currentCanonicalTask(c,d),recorded=fs.existsSync(path.join(c.stateDir,'public-evidence',d.id+'.json'));
     // A failed worker remains UNKNOWN. Its canonical negative report can be
     // inspected without claiming successful inference or releasing resources.
     const evidenceIds=!recorded?[]:d.delivery==='RETIRED'?['result','report']:
      d.delivery==='UNKNOWN'&&current.status.state==='ABSTAINED'&&current.grantCurrent&&current.sourceCurrent?['report']:[];
     return {taskId:d.id,canonicalTaskId:d.ccc1TaskId,delivery:d.delivery,
     state:current.status.state,terminal:current.status.terminal,sourceCurrent:current.sourceCurrent,grantCurrent:current.grantCurrent,
     sourceCommit:current.task.sourceCommit,taskFingerprint:d.fingerprint,goal:current.task.goal,
     builderId:'bot-forge',verifierId:'bot-lint',reporterId:'bot-echo',evidenceIds,
     failureCode:d.failureCode??null};}catch(error){return {taskId:d.id,canonicalTaskId:d.ccc1TaskId,delivery:d.delivery,state:'BLOCKED',
      terminal:false,evidenceIds:[],failureCode:String(error.message).slice(0,160)};}
   });
   return {items,nextCursor:hasMore?items.at(-1).taskId:null};
  },
  listAgents(args={}){
   const {cursor,limit}=pageArgs(args),c=config();assertProfileBridge(c);
   const store=profileStoreReader(c.profileDirectory,{classpath:c.profileClasspath});
   if(typeof store.page!=='function')throw Error('SERVICE_PROFILE_PAGE_BRIDGE_UNAVAILABLE');
   const page=store.page({afterAgentId:cursor,limit});
   const d=readServiceDescriptor(c),active=d&&sameLiveProcess(d.process)?d.activeTaskId:null;
   return {items:page.items.map(p=>({agentId:p.agentId,profileVersion:p.profileVersion,displayName:p.displayName,role:p.role,
    communicationStyle:p.communicationStyle,workspaceId:p.workspaceId,modelPolicy:p.modelPolicy,verifiedCapabilities:p.verifiedCapabilities??[],
    permissionScope:p.permissionScope??[],state:active&&['bot-forge','bot-lint','bot-echo'].includes(p.agentId)?'ASSIGNED_TO_ACTIVE_TASK':'REGISTERED',
    currentTaskId:active&&['bot-forge','bot-lint','bot-echo'].includes(p.agentId)?active:null,executionEnvironment:'HOST',
    modelActive:active&&['bot-forge','bot-lint','bot-echo'].includes(p.agentId)?null:false,
    activityDetail:'Canonical task records determine actual stage; a profile is not an active model'})),nextCursor:page.nextAfterId??null};
  },
  getEvidence({taskId,evidenceId}={}){
   if(!SERVICE_ID.test(taskId??''))throw Error('SERVICE_TASK_ID_INVALID');
   const c=config(),d=readDeliveries(c).find(i=>i.id===taskId);if(!d)throw Error('SERVICE_TASK_NOT_FOUND');
   return evidence(c,d,evidenceId);
  }
 });
}
