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
  let outcome='ERROR',detail='';
  try{
   const result=await work();
   if(!result||typeof result.outcome!=='string')throw Error('Worker returned no outcome');
   outcome=result.outcome;
   detail=String(result.detail??'').slice(0,160);
   if(['ERROR','CHECKS_FAILED'].includes(outcome))throw Error(detail||outcome);
   streak=0;
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
   Math.min(MAX_FAILURE_BACKOFF_MS,
    settings.intervalMs*Math.pow(2,Math.min(streak-1,6)));
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
   // Abort is graceful; all other scheduler errors must surface rather
   // than being mislabeled as successful work.
   if(signal?.aborted)break;
   throw error;
  }
 }
 return {cycles,failures,lastOutcome,stoppedBySignal:Boolean(signal?.aborted)};
}
