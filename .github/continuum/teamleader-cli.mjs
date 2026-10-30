#!/usr/bin/env node
/** Continuum offline Teamleader CLI. No GitHub token or cloud endpoint. */
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import crypto from 'node:crypto';
import {runTeamleaderCycle,localTeamleaderDriver,decodeLeaderState,summarizeTeamleaderProgress} from './autonomous-teamleader.mjs';
import {resolveOutputDestination} from './backlog-repair-worker.mjs';
import {runTeamleaderLoop,validateLoopOptions,isFailedCycleOutcome,validateWorkerHeartbeat} from './local-teamleader-loop.mjs';
import {readLocalIntegrationStatus} from './local-integration-status.mjs';
import {buildTeamleaderHandoff} from './teamleader-review-handoff.mjs';
import {releaseDeadContinuumBuildLease} from './shared-build-lease.mjs';

export function parseTeamleaderArgs(args){
 const allowed=new Set(['--root','--state','--budget-minutes',
  '--once','--loop','--status','--retry','--interval-minutes','--max-cycles','--durable','--release-dead-lease']);
 const opts={once:false,loop:false,status:false,retry:false};
 for(let i=0;i<args.length;i++){
  const key=args[i];
  if(!allowed.has(key))throw Error('Unknown Teamleader option '+key);
  if(['--once','--loop','--status','--retry','--durable','--release-dead-lease'].includes(key)){
   const flag=key.slice(2).replace(/-([a-z])/g,(_,ch)=>ch.toUpperCase());
   if(opts[flag])throw Error('Duplicate option '+key);
   opts[flag]=true;
  }else{
   if(opts[key.slice(2).replace(/-([a-z])/g,(_,ch)=>ch.toUpperCase())])
    throw Error('Duplicate option '+key);
   const val=args[++i];
   if(!val||val.startsWith('--'))throw Error('Missing '+key+' argument');
   opts[key.slice(2).replace(/-([a-z])/g,(_,ch)=>ch.toUpperCase())]=val;
  }
 }
 if([opts.once,opts.loop,opts.status,opts.releaseDeadLease].filter(Boolean).length!==1)
  throw Error('Choose exactly one of --once, --loop or --status (or the owner step --release-dead-lease)');
 if(opts.retry&&!opts.once)throw Error('--retry requires --once');
 if(opts.durable&&(!opts.loop||opts.maxCycles===undefined))
  throw Error('--durable requires --loop with explicit --max-cycles');
 if(opts.budgetMinutes!==undefined&&
   (!/^\d+$/.test(opts.budgetMinutes)||Number(opts.budgetMinutes)<1||
    Number(opts.budgetMinutes)>30))throw Error('Budget 1..30 minutes required');
 if((opts.intervalMinutes!==undefined||opts.maxCycles!==undefined)&&!opts.loop)
  throw Error('Interval and cycle limit require --loop');
 if(opts.loop){
  if(opts.intervalMinutes!==undefined&&!/^\d+$/.test(opts.intervalMinutes))
   throw Error('Invalid --interval-minutes');
  if(opts.maxCycles!==undefined&&!/^\d+$/.test(opts.maxCycles))
   throw Error('Invalid --max-cycles');
  validateLoopOptions({
   intervalMinutes:Number(opts.intervalMinutes??60),
   maxCycles:opts.maxCycles===undefined?null:Number(opts.maxCycles)
  });
 }
 return opts;
}
/** Exit status for a single scheduled run; only known non-failure results are green. */
export function teamleaderExitCode(result){
 return isFailedCycleOutcome(result?.outcome)?1:0;
}
/** Default external state directory shared with the verified mobile coding receipt. */
export function defaultTeamleaderStateDir(root,home=os.homedir()){
 const suffix=crypto.createHash('sha256').update(root).digest('hex').slice(0,12);
 return path.join(home,'.local','state','device-ai-continuum',suffix);
}
export async function teamleaderMain(argv=process.argv.slice(2)){
 const opt=parseTeamleaderArgs(argv);
 const root=fs.realpathSync(path.resolve(opt.root??process.cwd()));
 const stateDir=resolveOutputDestination(opt.state??defaultTeamleaderStateDir(root));
 if(stateDir===root||stateDir.startsWith(root+path.sep))
  throw Error('State must remain outside the source checkout');
 if(opt.releaseDeadLease){
  // Owner step after a crash (e.g. Android killed Termux mid-repair): the shared build lease is never freed by age;
  // this removes it only if it names THIS host and a process that no longer exists. Anything else is refused.
  const result=releaseDeadContinuumBuildLease(root);
  process.stdout.write(JSON.stringify({mode:'OWNER_RELEASE_DEAD_BUILD_LEASE',...result},null,2)+'\n');
  if(!result.released&&result.reason!=='NO_LEASE')process.exitCode=1;
  return result;
 }
 if(opt.status){
  const statePath=path.join(stateDir,'teamleader-state.json');
  const state=decodeLeaderState(fs.existsSync(statePath)?fs.readFileSync(statePath):null);
  const lastCompleted=state.events.filter(x=>x.outcome!=='RUNNING').at(-1)??null;
  const progress=summarizeTeamleaderProgress(state);
  let workerHeartbeat=null;
  try{
   const hbPath=path.join(stateDir,'worker-heartbeat.json');
   if(fs.existsSync(hbPath)){
    const hb=JSON.parse(fs.readFileSync(hbPath,'utf8'));
    if(validateWorkerHeartbeat(hb)){
     const now=Date.now();
     const stale=hb.state==='RUNNING'
       ?(!Number.isSafeInteger(hb.startedAt)||now>hb.startedAt+2*60*60*1000)
       :hb.state==='WAITING'
         ?(!Number.isSafeInteger(hb.nextAttemptAt)||now>hb.nextAttemptAt+5*60*1000)
         :false;
     workerHeartbeat={...hb,stale,
      liveness:stale?'STALE_OR_SUSPENDED':'LAST_REPORTED_ONLY'};
    }else workerHeartbeat={state:'UNREADABLE'};
   }
  }catch{
   // A corrupt/partial heartbeat cannot corrupt or forge Teamleader evidence.
   workerHeartbeat={state:'UNREADABLE'};
  }
  // Append read-only evidence from ANSK, the shared lease and the Termux
  // builder without granting research receipts or stale reports any authority.
  const localIntegration=readLocalIntegrationStatus({root});
  const output={mode:'OFFLINE_TEAMLEADER',stateDir,processedHeads:state.attemptedHeads.length,
   progress,lastCompleted,workerHeartbeat,localIntegration,recent:state.events.slice(-12)};
  output.reviewHandoff=buildTeamleaderHandoff(output);
  process.stdout.write(JSON.stringify(output,null,2)+'\n');
  return output;
 }
 const driver=await localTeamleaderDriver();
 if(opt.loop){
  const controller=new AbortController();
  const stop=()=>controller.abort();
  process.once('SIGINT',stop);
  process.once('SIGTERM',stop);
  const heartbeatFile=path.join(stateDir,'worker-heartbeat.json');
  // Retry a transient baseline ERROR at most twice on the SAME Git HEAD.
  // Deterministic unrepairable failures wait for a real code change.
  let retryHead=null,retryAttempts=0;
  try{
   const result=await runTeamleaderLoop({
    work:()=>{
     const liveHead=driver.head(root);
     if(retryHead!==liveHead){retryHead=liveHead;retryAttempts=0;}
     const cycle=runTeamleaderCycle({root,stateDir,driver,
      budgetMinutes:Number(opt.budgetMinutes??20),
      retry:retryAttempts>0,log:console.log});
     if(cycle.kind==='BASELINE_REPAIR'){
      if(cycle.outcome==='ERROR'&&retryAttempts<2)retryAttempts++;
      else retryAttempts=0;
     }
     process.stdout.write(JSON.stringify(cycle)+'\n');
     return cycle;
    },
    intervalMinutes:Number(opt.intervalMinutes??60),
    maxCycles:opt.maxCycles===undefined?null:Number(opt.maxCycles),
    signal:controller.signal,log:console.log,
    // Opt-in checkpoint is outside Git and bound to a clean immutable HEAD.
    stateFile:opt.durable?path.join(stateDir,'teamleader-loop-checkpoint.json'):null,
    sourceRoot:opt.durable?root:null,
    onHeartbeat:heartbeat=>{
     fs.mkdirSync(stateDir,{recursive:true,mode:0o700});
     const tmp=heartbeatFile+'.tmp-'+process.pid;
     fs.writeFileSync(tmp,JSON.stringify(heartbeat)+'\n',{mode:0o600});
     fs.renameSync(tmp,heartbeatFile);
    }
   });
   process.stdout.write(JSON.stringify({mode:'OFFLINE_LOOP',...result})+'\n');
   if(result.failures>0&&!result.stoppedBySignal)process.exitCode=1;
   return result;
  }finally{
   process.off('SIGINT',stop);
   process.off('SIGTERM',stop);
  }
 }
 const result=runTeamleaderCycle({root,stateDir,driver,
  budgetMinutes:Number(opt.budgetMinutes??20),retry:opt.retry,log:console.log});
 process.stdout.write(JSON.stringify(result,null,2)+'\n');
 if(teamleaderExitCode(result))process.exitCode=1;
 return result;
}
if(process.argv[1]&&import.meta.url===new URL('file://'+process.argv[1]).href)
 teamleaderMain().catch(err=>{console.error('Teamleader stopped:',err.message);process.exitCode=1;});
