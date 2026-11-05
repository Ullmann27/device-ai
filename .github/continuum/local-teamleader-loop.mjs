/**
 * Offline cyclic worker cadence. The loop is deterministic and never treats
 * a failed cycle as success. Scheduling does not require GitHub Actions.
 *
 * The work callback is the existing bounded Teamleader cycle. The host owns
 * process signals, output paths and the immutable repository policy.
 */
import {setTimeout as sleep} from 'node:timers/promises';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import crypto from 'node:crypto';
import {spawnSync} from 'node:child_process';

export const DEFAULT_INTERVAL_MINUTES=60;
export const MAX_FAILURE_BACKOFF_MS=60*60*1000;
// Reject unknown or unfinished outcomes rather than reporting unverified work as success.
const COMPLETE_OUTCOMES=new Set([
 'NO_FAILURE','NO_CODE_DIAGNOSTIC','VERIFIED_PATCH_PENDING_REVIEW',
 'NO_VERIFIED_REPAIR','DONE_INVESTIGATION','CHECKS_FAILED',
 'NO_READY_INVESTIGATION','IDLE','INTERRUPTED','ERROR'
]);
const FAILED_OUTCOMES=new Set([
 'NO_CODE_DIAGNOSTIC','NO_VERIFIED_REPAIR','CHECKS_FAILED','INTERRUPTED','ERROR'
]);

/** Validate persisted worker status before presenting it as a genuine heartbeat. */
export function validateWorkerHeartbeat(hb){
 if(!hb||typeof hb!=='object'||Array.isArray(hb)||
    hb.schema!=='continuum-local-worker-heartbeat'||hb.version!==1||
    !['RUNNING','WAITING','STOPPED','LIMIT_REACHED','ERROR'].includes(hb.state)||
    !Number.isSafeInteger(hb.cycles)||hb.cycles<0||
    !Number.isSafeInteger(hb.failures)||hb.failures<0||hb.failures>hb.cycles||
    !Number.isSafeInteger(hb.failureStreak)||hb.failureStreak<0||hb.failureStreak>hb.failures||
    (hb.lastOutcome!==null&&!COMPLETE_OUTCOMES.has(hb.lastOutcome))||
    (hb.cycles===0)!==(hb.lastOutcome===null)||
    typeof hb.detail!=='string'||hb.detail.length>160||
    !Number.isSafeInteger(hb.startedAt)||hb.startedAt<0)
  return false;
 if(hb.state==='RUNNING')return hb.lastFinishedAt===null&&hb.nextAttemptAt===null;
 if(!Number.isSafeInteger(hb.lastFinishedAt)||hb.lastFinishedAt<hb.startedAt)
  return false;
 if(hb.state==='WAITING')return Number.isSafeInteger(hb.nextAttemptAt)&&
   hb.nextAttemptAt>hb.lastFinishedAt;
 return hb.nextAttemptAt===null;
}

/** Treat unknown, interrupted, and unverified results as failed work. */
export function isFailedCycleOutcome(outcome){
 return !COMPLETE_OUTCOMES.has(outcome)||FAILED_OUTCOMES.has(outcome);
}

export function validateLoopOptions({intervalMinutes=60,maxCycles=null}={}){
 if(!Number.isSafeInteger(intervalMinutes)||intervalMinutes<1||intervalMinutes>1440)
  throw Error('Local worker interval must be 1..1440 whole minutes');
 if(maxCycles!==null&&(!Number.isSafeInteger(maxCycles)||
   maxCycles<1||maxCycles>1000))
  throw Error('maxCycles must be 1..1000 or unspecified');
 return {intervalMs:intervalMinutes*60000,maxCycles};
}

const digest=value=>crypto.createHash('sha256').update(JSON.stringify(value)).digest('hex');
function readPrivate(file,limit=65536){
 const stat=fs.lstatSync(file);
 if(!stat.isFile()||stat.isSymbolicLink()||stat.size>limit)
  throw Error('Unsafe or oversized local loop checkpoint');
 return fs.readFileSync(file,'utf8');
}
function atomicCheckpoint(file,value){
 const temporary=file+'.tmp-'+crypto.randomBytes(12).toString('hex');
 try{
  const fd=fs.openSync(temporary,'wx',0o600);
  try{fs.writeFileSync(fd,JSON.stringify(value)+'\n');fs.fsyncSync(fd);}
  finally{fs.closeSync(fd);}
  fs.renameSync(temporary,file);
  const directory=fs.openSync(path.dirname(file),'r');
  try{fs.fsyncSync(directory);}finally{fs.closeSync(directory);}
 }finally{fs.rmSync(temporary,{force:true});}
}
async function sourceIdentity(root,sourcePreflight){
 const canonical=fs.realpathSync(root);
 // Trusted caller policy runs before Git can invoke configured status helpers.
 // A thrown/rejected check vetoes this source boundary, including initial open.
 if(sourcePreflight!==null)await sourcePreflight(canonical);
 // The scheduler must not let inherited Git trace destinations write files.
 const gitEnv={...process.env,GIT_TERMINAL_PROMPT:'0',GIT_OPTIONAL_LOCKS:'0'};
 for(const key of Object.keys(gitEnv))if(key.startsWith('GIT_TRACE'))delete gitEnv[key];
 const git=args=>{
  const result=spawnSync('git',['-C',canonical,...args],{
   encoding:'utf8',timeout:10_000,maxBuffer:2*1024*1024,shell:false,
   env:gitEnv
  });
  if(result.error||result.status!==0)throw Error('Cannot validate local loop source');
  return result.stdout;
 };
 const head=git(['rev-parse','HEAD']).trim();
 if(!/^[0-9a-f]{40}$/.test(head))throw Error('Local loop requires a full source HEAD');
 if(git(['status','--porcelain=v1','--untracked-files=all','-z']))
  throw Error('Local loop refuses a dirty source checkout');
 return {root:canonical,head};
}

/** Scheduler ownership is separate from the canonical per-cycle build lease.
 * A recovery marker lives INSIDE the old lock directory: only its exclusive
 * creator may rename that directory, even when several dead-PID recoverers race.
 * Unknown hosts, live PIDs and incomplete lock metadata fail closed.
 */
export function acquireLoopLock(file){
 const lock=file+'.lock';
 const payload=JSON.stringify({pid:process.pid,host:os.hostname(),
  nonce:crypto.randomBytes(16).toString('hex')});
 const create=()=>{
  fs.mkdirSync(lock,{mode:0o700});
  fs.writeFileSync(path.join(lock,'owner.json'),payload,{flag:'wx',mode:0o600});
 };
 try{create();}
 catch(error){
  if(error.code!=='EEXIST')throw error;
  if(!fs.lstatSync(lock).isDirectory()||fs.lstatSync(lock).isSymbolicLink())
   throw Error('Local loop lock unsafe; inspect manually');
  let previous,bytes;
  try{bytes=readPrivate(path.join(lock,'owner.json'),4096);previous=JSON.parse(bytes);}
  catch{throw Error('Local loop lock malformed; inspect manually');}
  if(previous.host!==os.hostname()||!Number.isSafeInteger(previous.pid)||previous.pid<1||
     !/^[0-9a-f]{32}$/.test(previous.nonce))
   throw Error('Local loop lock requires manual recovery');
  try{process.kill(previous.pid,0);throw Error('Local loop already running');}
  catch(error){if(error.code!=='ESRCH')throw error;}
  const marker=path.join(lock,'recovering');
  let ticket;
  try{ticket=fs.openSync(marker,'wx',0o600);}
  catch{throw Error('Local loop recovery busy; inspect manually if retained');}
  const identity=fs.fstatSync(ticket);fs.closeSync(ticket);
  const stale=lock+'.stale-'+crypto.randomBytes(12).toString('hex');
  try{
   if(readPrivate(path.join(lock,'owner.json'),4096)!==bytes)
    throw Error('Local loop lock changed during recovery');
   fs.renameSync(lock,stale);
  }finally{
   // Only remove the marker this contender created, never a successor's.
   try{const current=fs.lstatSync(marker);
    if(current.ino===identity.ino&&current.dev===identity.dev)fs.unlinkSync(marker);
   }catch(error){if(error.code!=='ENOENT')throw error;}
  }
  fs.rmSync(stale,{recursive:true,force:true});
  try{create();}catch{throw Error('Local loop recovery lost to another scheduler');}
 }
 return ()=>{
  if(readPrivate(path.join(lock,'owner.json'),4096)!==payload)
   throw Error('Local loop lock ownership changed');
  fs.rmSync(lock,{recursive:true});
 };
}

async function openCheckpoint(stateFile,sourceRoot,settings,sourcePreflight){
 if(settings.maxCycles===null)throw Error('Durable local loop requires a finite cycle budget');
 if(typeof sourceRoot!=='string'||typeof stateFile!=='string')
  throw Error('Durable local loop needs stateFile and sourceRoot');
 const source=await sourceIdentity(sourceRoot,sourcePreflight);
 const requested=path.resolve(stateFile);
 fs.mkdirSync(path.dirname(requested),{recursive:true,mode:0o700});
 const file=path.join(fs.realpathSync(path.dirname(requested)),path.basename(requested));
 if(file===source.root||file.startsWith(source.root+path.sep))
  throw Error('Local loop checkpoint must remain outside source checkout');
 const release=acquireLoopLock(file);
 try{
  let saved=null;
  if(fs.existsSync(file)){
   try{
    saved=JSON.parse(readPrivate(file));
    const {integrity,...data}=saved;
    if(saved.schema!=='continuum-local-loop-checkpoint'||saved.version!==1||
      !/^[0-9a-f]{32}$/.test(saved.runId)||integrity!==digest(data)||
      !validateWorkerHeartbeat(saved.heartbeat)||saved.heartbeat.cycles>settings.maxCycles||
      (saved.heartbeat.state==='RUNNING'&&saved.heartbeat.cycles>=settings.maxCycles))
     throw Error('Invalid checkpoint');
   }catch{throw Error('Corrupt local loop checkpoint; inspect manually');}
   if(JSON.stringify(saved.settings)!==JSON.stringify(settings))
    throw Error('Local loop checkpoint settings/budget differ; use a new session');
   if(saved.source?.head!==source.head||saved.source?.root!==source.root)
    throw Error('Local loop checkpoint source HEAD/root differ; use a new session');
  }
  const runId=saved?.runId??crypto.randomBytes(16).toString('hex');
  return {saved,release,source,
   cycleId:number=>'lc-'+digest({runId,number,source}).slice(0,24),
   checkSource:async()=>{
    if(JSON.stringify(await sourceIdentity(sourceRoot,sourcePreflight))!==JSON.stringify(source))
     throw Error('Local loop source HEAD/root changed during session');
   },
   save:heartbeat=>{
    const data={schema:'continuum-local-loop-checkpoint',version:1,
     runId,source,settings,heartbeat};
    atomicCheckpoint(file,{...data,integrity:digest(data)});
   }
  };
 }catch(error){release();throw error;}
}

export async function runTeamleaderLoop({
 work,intervalMinutes=DEFAULT_INTERVAL_MINUTES,maxCycles=null,
 signal=null,wait=null,onHeartbeat=()=>{},log=()=>{},
 stateFile=null,sourceRoot=null,sourcePreflight=null,now=Date.now
}={}){
 if(typeof work!=='function')throw Error('Local worker needs a real bounded cycle');
 if(sourcePreflight!==null&&typeof sourcePreflight!=='function')
  throw Error('Local loop source preflight must be a function');
 const settings=validateLoopOptions({intervalMinutes,maxCycles});
 const store=stateFile===null?null:await openCheckpoint(stateFile,sourceRoot,settings,sourcePreflight);
 const pause=wait??((ms,abortSignal)=>sleep(ms,null,{signal:abortSignal}));
 let heartbeat=store?.saved?.heartbeat??null;
 let cycles=heartbeat?.cycles??0,failures=heartbeat?.failures??0;
 let streak=heartbeat?.failureStreak??0,lastOutcome=heartbeat?.lastOutcome??null;
 const emit=async value=>{
  store?.save(value);heartbeat=value;
  await onHeartbeat(value);
 };
 const summary=()=>({cycles,failures,lastOutcome,stoppedBySignal:Boolean(signal?.aborted),
  state:heartbeat?.state??'STOPPED',resumed:Boolean(store?.saved)});
 const pauseMs=()=>streak===0?settings.intervalMs:Math.max(settings.intervalMs,
  Math.min(MAX_FAILURE_BACKOFF_MS,settings.intervalMs*Math.pow(2,Math.min(streak-1,6))));
 const waitForNext=async(resuming=false)=>{
  const delay=resuming?Math.max(0,heartbeat.nextAttemptAt-now()):
   heartbeat.nextAttemptAt-heartbeat.lastFinishedAt;
  try{if(delay>0)await pause(delay,signal);}
  catch(error){
   if(!signal?.aborted){
    await emit({...heartbeat,state:'ERROR',nextAttemptAt:null,
     detail:('Scheduler wait failed: '+String(error?.message??error)).slice(0,160)});
    throw error;
   }
  }
  if(signal?.aborted)await emit({...heartbeat,state:'STOPPED',nextAttemptAt:null});
 };
 try{
 if(heartbeat?.state==='RUNNING'){
  // A durable intent consumed one slot even if it crashed before producing
  // results. Canonical Teamleader/repair recovery owns the job itself.
  cycles++;failures++;streak++;lastOutcome='INTERRUPTED';
  const finished=Math.max(now(),heartbeat.startedAt);
  await emit({...heartbeat,cycles,failures,failureStreak:streak,lastOutcome,
   detail:'Previous cycle ended without verified completion',lastFinishedAt:finished,
   state:cycles>=maxCycles?'LIMIT_REACHED':'WAITING',
   nextAttemptAt:cycles>=maxCycles?null:finished+pauseMs()});
 }
 if(heartbeat&&['STOPPED','LIMIT_REACHED','ERROR'].includes(heartbeat.state)){
  if(heartbeat.state==='ERROR')throw Error('Local loop checkpoint is terminal ERROR; inspect before a new session');
  await onHeartbeat(heartbeat);return summary();
 }
 if(signal?.aborted){
  const at=now();
  await emit(heartbeat?{...heartbeat,state:'STOPPED',nextAttemptAt:null}:{
   schema:'continuum-local-worker-heartbeat',version:1,cycles,failures,
   failureStreak:streak,lastOutcome,detail:'Stopped before cycle started',
   startedAt:at,lastFinishedAt:at,state:'STOPPED',nextAttemptAt:null
  });
  return summary();
 }
 if(heartbeat?.state==='WAITING')await waitForNext(true);
 while(!signal?.aborted&&(maxCycles===null||cycles<maxCycles)){
  try{if(store)await store.checkSource();}catch(error){
   if(heartbeat)await emit({...heartbeat,state:'ERROR',nextAttemptAt:null,
    detail:String(error?.message??error).slice(0,160)});
   throw error;
  }
  const startedAt=now();
  // Persist RUNNING before entering a potentially expensive Gradle/Node
  // test chain. A stale RUNNING heartbeat does not imply success.
  await emit({
   schema:'continuum-local-worker-heartbeat',version:1,
   cycles,failures,failureStreak:streak,lastOutcome,
   detail:'Cycle started; verification pending',startedAt,
   lastFinishedAt:null,state:'RUNNING',nextAttemptAt:null
  });
  let outcome='ERROR',detail='',sourceError=false;
  try{
   let result,workError;
   try{if(!signal?.aborted)result=await work({signal,cycleNumber:cycles+1,
    cycleId:store?.cycleId(cycles+1)??null,sourceHead:store?.source.head??null});}
   catch(error){workError=error;}
   try{if(store)await store.checkSource();}catch(error){sourceError=true;throw error;}
   if(signal?.aborted)result={outcome:'INTERRUPTED',detail:'Cycle cancelled; verification not accepted'};
   else if(workError)throw workError;
   if(!result||!COMPLETE_OUTCOMES.has(result.outcome))
    throw Error('Worker returned unknown or unfinished outcome');
   outcome=result.outcome;
   detail=String(result.detail??'').slice(0,160);
   if(isFailedCycleOutcome(outcome)){
    failures++;
    streak++;
    log('[local-loop] cycle failed: '+(detail||outcome));
   }else streak=0;
  }catch(error){
   outcome='ERROR';
   detail=String(error?.message??error).slice(0,160);
   failures++;
   streak++;
   log('[local-loop] cycle failed: '+detail);
  }
  cycles++;
  lastOutcome=outcome;
  const lastFinishedAt=Math.max(now(),startedAt);
  // Retry failures with exponential backoff, but do not spin on repeated
  // broken checkouts or unavailable SDKs. Normal cycles run hourly by default.
  const last=maxCycles!==null&&cycles>=maxCycles;
  await emit({
   schema:'continuum-local-worker-heartbeat',version:1,
   cycles,failures,failureStreak:streak,lastOutcome:outcome,
   detail,startedAt,lastFinishedAt,
   state:sourceError?'ERROR':signal?.aborted?'STOPPED':last?'LIMIT_REACHED':'WAITING',
   nextAttemptAt:last||signal?.aborted||sourceError?null:lastFinishedAt+pauseMs()
  });
  if(last||signal?.aborted||sourceError)break;
  await waitForNext();
 }
 return summary();
 }finally{store?.release();}
}
