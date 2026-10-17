/**
 * Offline cyclic worker cadence. The loop is deterministic and never treats
 * a failed cycle as success. Scheduling does not require GitHub Actions.
 *
 * The work callback is the existing bounded Teamleader cycle. The host owns
 * process signals, output paths and the immutable repository policy.
 */
import {setTimeout as sleep} from 'node:timers/promises';

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

export async function runTeamleaderLoop({
 work,intervalMinutes=DEFAULT_INTERVAL_MINUTES,maxCycles=null,
 signal=null,wait=null,onHeartbeat=()=>{},log=()=>{}
}={}){
 if(typeof work!=='function')throw Error('Local worker needs a real bounded cycle');
 const settings=validateLoopOptions({intervalMinutes,maxCycles});
 const pause=wait??((ms,abortSignal)=>sleep(ms,null,{signal:abortSignal}));
 let cycles=0,failures=0,streak=0,lastOutcome=null;
 while(!signal?.aborted&&(maxCycles===null||cycles<maxCycles)){
  const startedAt=Date.now();
  // Persist RUNNING before entering a potentially expensive Gradle/Node
  // test chain. A stale RUNNING heartbeat does not imply success.
  onHeartbeat({
   schema:'continuum-local-worker-heartbeat',version:1,
   cycles,failures,failureStreak:streak,lastOutcome,
   detail:'Cycle started; verification pending',startedAt,
   lastFinishedAt:null,state:'RUNNING',nextAttemptAt:null
  });
  let outcome='ERROR',detail='';
  try{
   const result=await work();
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
  const lastFinishedAt=Date.now();
  // Retry failures with exponential backoff, but do not spin on repeated
  // broken checkouts or unavailable SDKs. Normal cycles run hourly by default.
  const pauseMs=streak===0?settings.intervalMs:
   Math.max(settings.intervalMs,
    Math.min(MAX_FAILURE_BACKOFF_MS,
     settings.intervalMs*Math.pow(2,Math.min(streak-1,6))));
  const last=maxCycles!==null&&cycles>=maxCycles;
  const heartbeat={
   schema:'continuum-local-worker-heartbeat',version:1,
   cycles,failures,failureStreak:streak,lastOutcome:outcome,
   detail,startedAt,lastFinishedAt,
   state:last?'LIMIT_REACHED':signal?.aborted?'STOPPED':'WAITING',
   nextAttemptAt:last||signal?.aborted?null:lastFinishedAt+pauseMs
  };
  onHeartbeat(heartbeat);
  if(last||signal?.aborted)break;
  try{
   await pause(pauseMs,signal);
  }catch(error){
   // A failed scheduler wait terminates the loop; do not leave a WAITING
   // heartbeat that advertises a retry which will never happen.
   if(!signal?.aborted){
    onHeartbeat({...heartbeat,state:'ERROR',
     detail:('Scheduler wait failed: '+String(error?.message??error)).slice(0,160),
     nextAttemptAt:null});
    throw error;
   }
  }
  if(signal?.aborted){
   onHeartbeat({...heartbeat,state:'STOPPED',nextAttemptAt:null});
   break;
  }
 }
 return {cycles,failures,lastOutcome,stoppedBySignal:Boolean(signal?.aborted)};
}
