/** Explicitly started local owner service. runTeamleaderLoop remains the only
 * scheduler; runCodingTask remains the only coding execution path. */
import fs from 'node:fs';
import path from 'node:path';
import net from 'node:net';
import crypto from 'node:crypto';
import {fork,spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {setTimeout as delay} from 'node:timers/promises';
import {runTeamleaderLoop} from './local-teamleader-loop.mjs';
import {prepareCodingTask} from './coding-orchestrator.mjs';
import {abortTask} from './ccc1-worker.mjs';
import {profileStoreReader} from '../../tools/genesis-workspaces/adapter-bridge.mjs';
import {createCcc1BotAssignment} from './ccc1-bot-assignment.mjs';
import {loadCodingServiceConfig,readDeliveries,saveDeliveries,privateJson,writePrivateJson,privateDirectory,
 realDirectory,assertServiceSource,assertServiceArtifacts,reviewServiceTask,linuxProcessIdentity,sameLiveProcess,
 serviceEnvironment,SERVICE_ID,plainBytes} from './coding-service-store.mjs';
import {createCodingServiceReader,readServiceDescriptor,currentCanonicalTask} from './coding-service-reader.mjs';

const CHILD=fileURLToPath(new URL('./coding-service-worker.mjs',import.meta.url));
const CLI=fileURLToPath(new URL('../../scripts/continuum-coding-service.mjs',import.meta.url));
const activeDeliveries=new Set(['RESERVING','PENDING','DISPATCHED','CANCEL_REQUESTED','UNKNOWN']);
function exclusiveLock(config,name){
 const directory=path.join(config.stateDir,name),identity=linuxProcessIdentity(),nonce=crypto.randomBytes(16).toString('hex');
 for(let attempt=0;attempt<2;attempt++){
  try{
   fs.mkdirSync(directory,{mode:0o700});
   writePrivateJson(path.join(directory,'owner.json'),{process:identity,nonce},{exclusive:true});
   return ()=>{
    if(privateJson(path.join(directory,'owner.json')).nonce!==nonce)throw Error('SERVICE_LOCK_OWNER_CHANGED');
    fs.rmSync(directory,{recursive:true});
   };
  }catch(error){
   if(error.code!=='EEXIST')throw error;
   privateDirectory(directory);
   const old=privateJson(path.join(directory,'owner.json'));
   if(!old.process||!/^[a-f0-9]{32}$/.test(old.nonce??''))throw Error('SERVICE_LOCK_UNKNOWN_OWNER');
   if(sameLiveProcess(old.process))throw Error('SERVICE_BUSY');
   // An exclusive recovery marker serializes stale lock takeover. Unknown/torn
   // metadata is deliberately not removed automatically.
   const recovering=path.join(directory,'recovering');
   let fd;try{fd=fs.openSync(recovering,'wx',0o600);}catch{throw Error('SERVICE_BUSY');}
   fs.closeSync(fd);
   if(privateJson(path.join(directory,'owner.json')).nonce!==old.nonce||sameLiveProcess(old.process))throw Error('SERVICE_BUSY');
   const stale=directory+'.stale-'+nonce;fs.renameSync(directory,stale);fs.rmSync(stale,{recursive:true});
  }
 }
 throw Error('SERVICE_BUSY');
}
const transaction=(config,fn)=>{const release=exclusiveLock(config,'inbox.lock');try{return fn();}finally{release();}};
function currentConfig(config){
 const current=loadCodingServiceConfig(config.configPath);
 if(current.configSha256!==config.configSha256)throw Error('SERVICE_CONFIG_CHANGED');
 assertServiceSource(current);return current;
}
function requireProfiles(config,task,canonicalTaskId='t-0000000000000000'){
 return createCcc1BotAssignment({task,ccc1TaskId:canonicalTaskId,builderId:'bot-forge',verifierId:'bot-lint',reporterId:'bot-echo',
  profileStore:profileStoreReader(config.profileDirectory,{classpath:config.profileClasspath})});
}
function stateBytes(root){
 let total=0,count=0;
 const visit=file=>{
  if(++count>100000)throw Error('SERVICE_STATE_FILE_CAPACITY');
  const s=fs.lstatSync(file);
  if(s.isSymbolicLink())throw Error('SERVICE_STATE_SYMLINK');
  if(s.isDirectory())for(const name of fs.readdirSync(file))visit(path.join(file,name));
  else if(s.isFile()){if(s.nlink!==1)throw Error('SERVICE_STATE_HARDLINK');total+=s.size;}
 };
 visit(root);return total;
}
const STATE_BYTES_LIMIT=1024*1024*1024,TASK_RESERVE_BYTES=128*1024*1024;
export function prepareServiceTask({configPath,task}={}){
 const config=loadCodingServiceConfig(configPath),review=reviewServiceTask(config,task);
 requireProfiles(config,review.task);return review;
}
/** Callable only by the explicit local owner CLI/control socket. The review hash
 * binds a decision; model text and inbox files are not owner decisions. */
export function submitOwnerTask({configPath,task,approve}={}){
 const config=loadCodingServiceConfig(configPath),review=prepareServiceTask({configPath,task});
 if(typeof approve!=='string'||approve!==review.reviewSha256)throw Error('SERVICE_EXPLICIT_OWNER_REVIEW_REQUIRED');
 return transaction(config,()=>{
  currentConfig(config);const items=readDeliveries(config),known=items.find(i=>i.id===review.task.id);
  if(known){
   if(known.fingerprint!==review.taskFingerprint||known.reviewSha256!==approve)throw Error('SERVICE_TASK_ID_REUSED');
   return {taskId:known.id,canonicalTaskId:known.ccc1TaskId,delivery:known.delivery,duplicate:true,executed:false};
  }
  const pending=items.filter(i=>activeDeliveries.has(i.delivery)).length;
  if(pending>=config.limits.maxQueue)throw Error('SERVICE_QUEUE_FULL');
  if(items.length>=config.limits.maxRecords)throw Error('SERVICE_HISTORY_FULL');
  if(stateBytes(config.stateDir)+(pending+1)*TASK_RESERVE_BYTES>STATE_BYTES_LIMIT)throw Error('SERVICE_STORAGE_BUDGET_FULL');
  // Reserve first. A crash before canonical admission is UNKNOWN, not an implicit retry.
  const delivery={id:review.task.id,fingerprint:review.taskFingerprint,reviewSha256:approve,ccc1TaskId:null,
   delivery:'RESERVING',submittedAt:new Date().toISOString(),workerProcess:null};
  items.push(delivery);saveDeliveries(config,items);
  try{
   const grant=prepareCodingTask({project:config.project,stateDir:path.join(config.stateDir,'ccc1'),task:review.task});
   if(grant.replayed)throw Error('SERVICE_UNTRACKED_EXISTING_GRANT');
   delivery.ccc1TaskId=grant.ccc1TaskId;delivery.delivery='PENDING';saveDeliveries(config,items);
  }catch(error){delivery.delivery='UNKNOWN';delivery.failureCode=String(error.message).slice(0,160);saveDeliveries(config,items);throw error;}
  return {taskId:delivery.id,canonicalTaskId:delivery.ccc1TaskId,delivery:delivery.delivery,duplicate:false,executed:false};
 });
}
export function cancelOwnerTask({configPath,taskId}={}){
 if(!SERVICE_ID.test(taskId??''))throw Error('SERVICE_TASK_ID_INVALID');
 const config=loadCodingServiceConfig(configPath);
 return transaction(config,()=>{
  const items=readDeliveries(config),d=items.find(i=>i.id===taskId);if(!d)throw Error('SERVICE_TASK_NOT_FOUND');
  const prior=d.delivery;
  d.delivery='CANCEL_REQUESTED';d.cancelRequestedAt=new Date().toISOString();saveDeliveries(config,items);
  if(!d.ccc1TaskId){d.delivery='RETIRED';saveDeliveries(config,items);return {taskId,delivery:'RETIRED',canonicalState:'UNADMITTED_CANCELLED'};}
  const current=currentCanonicalTask(config,d);
  const cancellation=abortTask({project:config.project,stateDir:current.state,taskId:d.ccc1TaskId,reason:'Local owner coding-service cancellation'});
  if(cancellation.journal!=='WRITER_ACTIVE'&&!sameLiveProcess(d.workerProcess))d.delivery='RETIRED';
  saveDeliveries(config,items);
  return {taskId,delivery:d.delivery,priorDelivery:prior,...cancellation};
 });
}
function socketPath(config){const file=path.join(config.stateDir,'control.sock');if(Buffer.byteLength(file)>100)throw Error('SERVICE_SOCKET_PATH_TOO_LONG');return file;}
export async function serviceRequest({configPath,operation,payload={}}={}){
 const config=loadCodingServiceConfig(configPath),descriptor=readServiceDescriptor(config);
 if(!descriptor||!sameLiveProcess(descriptor.process))throw Error('SERVICE_NOT_RUNNING');
 const file=socketPath(config),s=fs.lstatSync(file);
 if(!s.isSocket()||s.uid!==process.getuid?.()||(s.mode&0o077)!==0||fs.realpathSync(file)!==file)throw Error('SERVICE_SOCKET_UNSAFE');
 const request=JSON.stringify({instanceId:descriptor.instanceId,operation,payload})+'\n';
 if(Buffer.byteLength(request)>32768)throw Error('SERVICE_REQUEST_TOO_LARGE');
 return new Promise((resolve,reject)=>{
  const client=net.createConnection(file);let text='',finished=false;
  const done=(error,value)=>{if(finished)return;finished=true;client.destroy();error?reject(error):resolve(value);};
  client.setTimeout(30000,()=>done(Error('SERVICE_CONTROL_TIMEOUT')));
  client.on('connect',()=>client.end(request));client.on('error',error=>done(error));
  client.on('data',bytes=>{text+=bytes.toString('utf8');if(Buffer.byteLength(text)>65536)done(Error('SERVICE_RESPONSE_TOO_LARGE'));});
  client.on('end',()=>{try{const reply=JSON.parse(text);if(reply.instanceId!==descriptor.instanceId)throw Error('SERVICE_INSTANCE_CHANGED');
   if(!reply.ok)throw Error(reply.error);done(null,reply.result);}catch(error){done(error);}});
 });
}
export async function startCodingService({configPath}={}){
 const config=loadCodingServiceConfig(configPath),prior=readServiceDescriptor(config);
 if(prior&&sameLiveProcess(prior.process))return {status:'ALREADY_RUNNING',...createCodingServiceReader({configPath}).getStatus()};
 const child=spawn(process.execPath,[CLI,'run','--config',configPath],{detached:true,stdio:'ignore',env:serviceEnvironment(config),shell:false});
 let exited=false;child.on('exit',()=>{exited=true;});child.unref();
 for(let n=0;n<300;n++){
  await delay(100);
  const descriptor=readServiceDescriptor(config);
  if(descriptor&&sameLiveProcess(descriptor.process)&&descriptor.process.pid===child.pid){
   await serviceRequest({configPath,operation:'ping'});
   return {status:'STARTED',...createCodingServiceReader({configPath}).getStatus()};
  }
  if(exited){
   const file=path.join(config.stateDir,'start-error.json');
   const failure=fs.existsSync(file)?privateJson(file):null;
   throw Error(failure?.code??'SERVICE_START_FAILED');
  }
 }
 throw Error('SERVICE_START_TIMEOUT_CHECK_STATUS');
}
export async function runCodingService({configPath}={}){
 const config=loadCodingServiceConfig(configPath);assertServiceArtifacts(config);
 const release=exclusiveLock(config,'service.lock');
 const instanceId=crypto.randomBytes(16).toString('hex'),controller=new AbortController();
 let server=null,active=null,wake=null,stopping=false,descriptor;
 const update=extra=>{
  descriptor={...descriptor,...extra};writePrivateJson(path.join(config.stateDir,'service.json'),descriptor);
 };
 const notify=()=>{wake?.();wake=null;};
 const stop=()=>{
  if(stopping)return;stopping=true;
  update({state:'STOPPING'});
  if(active){try{cancelOwnerTask({configPath,taskId:active.id});}catch(error){update({stopFailureCode:String(error.message).slice(0,160)});}}
  controller.abort();notify();
 };
 try{
  transaction(config,()=>{
   const items=readDeliveries(config);
   for(const d of items)if(['RESERVING','DISPATCHED','CANCEL_REQUESTED'].includes(d.delivery)){
    d.delivery='UNKNOWN';d.failureCode='SERVICE_PREVIOUS_DISPATCH_UNCERTAIN';
   }
   saveDeliveries(config,items);
   if(items.some(d=>d.workerProcess&&sameLiveProcess(d.workerProcess)))throw Error('SERVICE_ORPHAN_WORKER_ACTIVE');
  });
  const socket=socketPath(config);
  if(fs.existsSync(socket)){
   if(!fs.lstatSync(socket).isSocket())throw Error('SERVICE_SOCKET_UNSAFE');fs.unlinkSync(socket);
  }
  descriptor={schema:'continuum-coding-service-process',version:1,instanceId,configSha256:config.configSha256,
   process:linuxProcessIdentity(),state:'STARTING',activeTaskId:null,startedAt:new Date().toISOString(),cycles:0};
  server=net.createServer({allowHalfOpen:true},client=>{
   let buffer='',handled=false;client.setTimeout(5000,()=>client.destroy());
   const answer=value=>client.end(JSON.stringify({instanceId,...value})+'\n');
   client.on('error',()=>{});
   client.on('data',bytes=>{
    if(handled)return;buffer+=bytes.toString('utf8');
    if(Buffer.byteLength(buffer)>32768){handled=true;answer({ok:false,error:'SERVICE_REQUEST_TOO_LARGE'});return;}
    if(!buffer.includes('\n'))return;handled=true;
    try{
     const request=JSON.parse(buffer);
     if(request.instanceId!==instanceId)throw Error('SERVICE_INSTANCE_CHANGED');
     if(!request.payload||Array.isArray(request.payload)||typeof request.payload!=='object')throw Error('SERVICE_REQUEST_INVALID');
     let result;
     if(request.operation==='ping')result={state:descriptor.state};
     else if(request.operation==='stop'){stop();result={state:'STOPPING',activeTaskId:active?.id??null};}
     else if(request.operation==='submit'){
      if(stopping)throw Error('SERVICE_STOPPING');
      result=submitOwnerTask({configPath,task:request.payload.task,approve:request.payload.approve});notify();
     }else if(request.operation==='cancel'){result=cancelOwnerTask({configPath,taskId:request.payload.taskId});notify();}
     else throw Error('SERVICE_CONTROL_OPERATION_INVALID');
     answer({ok:true,result});
    }catch(error){answer({ok:false,error:String(error.message).slice(0,160)});}
   });
  });
  await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(socket,()=>{fs.chmodSync(socket,0o600);resolve();});});
  update({state:'IDLE'});
  process.on('SIGTERM',stop);process.on('SIGINT',stop);
  const wait=async()=>{
   if(controller.signal.aborted||readDeliveries(config).some(d=>d.delivery==='PENDING'))return;
   await new Promise(resolve=>{wake=resolve;if(controller.signal.aborted)notify();});
  };
  const work=async()=>{
   currentConfig(config);
   active=transaction(config,()=>{
    const items=readDeliveries(config),d=items.find(i=>i.delivery==='PENDING');
    if(!d)return null;
    try{
     const current=currentCanonicalTask(config,d,{requireCurrent:true});
     if(current.status.terminal){d.delivery='RETIRED';saveDeliveries(config,items);return null;}
     requireProfiles(config,current.task,d.ccc1TaskId);
    }catch(error){
     d.delivery='UNKNOWN';d.failureCode=String(error.message).slice(0,160);saveDeliveries(config,items);return null;
    }
    d.delivery='DISPATCHED';d.dispatchedAt=new Date().toISOString();d.instanceId=instanceId;
    saveDeliveries(config,items);return {...d};
   });
   if(!active){update({state:'IDLE',activeTaskId:null});return {outcome:'IDLE'};}
   update({state:'TASK_ACTIVE',activeTaskId:active.id});
   let childResult=null,childError=null;
   const child=fork(CHILD,[],{stdio:['ignore','ignore','ignore','ipc'],env:serviceEnvironment(config),execArgv:[]});
   let settled=false;
   const completed=new Promise(resolve=>{
    const finish=()=>{if(settled)return;settled=true;resolve();};
    child.on('error',error=>{childError=String(error.message).slice(0,160);finish();});
    child.on('message',message=>{
     if(message?.type==='ready'){
      try{
       const processIdentity=linuxProcessIdentity(child.pid);if(!processIdentity)throw Error('SERVICE_WORKER_NOT_LIVE');
       transaction(config,()=>{
        const items=readDeliveries(config),d=items.find(i=>i.id===active.id);
        if(d.delivery!=='DISPATCHED'||d.instanceId!==instanceId||stopping)throw Error('SERVICE_DISPATCH_REVOKED');
        d.workerProcess=processIdentity;saveDeliveries(config,items);active={...d};
       });
       child.send({type:'run',configPath,taskId:active.id,instanceId});
      }catch(error){childError=String(error.message).slice(0,160);child.disconnect();}
     }else if(message?.type==='result'&&message.taskId===active?.id){childResult=message;}
    });
    child.on('exit',(code,signal)=>{if(code!==0&&!childError)childError='SERVICE_WORKER_EXIT_'+(signal??code);finish();});
   });
   await completed;
   const taskId=active.id;
   transaction(config,()=>{
    const items=readDeliveries(config),d=items.find(i=>i.id===taskId);
    // A process exit, empty message or unverified success string is not a result.
    if(childResult?.ok===true){
     const current=currentCanonicalTask(config,d);
     const resultFile=path.join(config.stateDir,'public-evidence',d.id+'.json');
     const stored=privateJson(resultFile,65536);
     if(stored.journalHead!==current.entries.at(-1)?.hash)throw Error('SERVICE_RESULT_STALE');
     d.delivery=current.status.terminal?'RETIRED':'UNKNOWN';
    }else{d.delivery='UNKNOWN';d.failureCode=childResult?.error??childError??'SERVICE_WORKER_RESULT_MISSING';}
    d.finishedAt=new Date().toISOString();saveDeliveries(config,items);
   });
   if(stopping||readDeliveries(config).find(i=>i.id===taskId)?.cancelRequestedAt){
    try{cancelOwnerTask({configPath,taskId});}catch{}
   }
   active=null;update({state:stopping?'STOPPING':'IDLE',activeTaskId:null});
   return {outcome:childResult?.ok===true?'DONE_INVESTIGATION':'ERROR',detail:'CCC1 task '+taskId+'; inspect canonical result'};
  };
  await runTeamleaderLoop({work,intervalMinutes:1,maxCycles:config.limits.maxCycles,signal:controller.signal,wait,
   stateFile:path.join(config.stateDir,'sessions',instanceId+'.json'),sourceRoot:config.sourceRoot,
   onHeartbeat:heartbeat=>update({cycles:heartbeat.cycles,loopState:heartbeat.state})});
  update({state:'STOPPED',activeTaskId:null,stoppedAt:new Date().toISOString()});
  return {state:'STOPPED',instanceId};
 }finally{
  controller.abort();notify();process.removeListener('SIGTERM',stop);process.removeListener('SIGINT',stop);
  if(server)await new Promise(resolve=>server.close(resolve));
  const socket=socketPath(config);if(fs.existsSync(socket)&&fs.lstatSync(socket).isSocket())fs.unlinkSync(socket);
  release();
 }
}
