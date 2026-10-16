/**
 * Continuum Teamleader v1 — deterministic zero-API-cost autonomous worker.
 *
 * One scheduled cycle = a bounded unit of verified work. A crashed or
 * unverified cycle is never presented as a completed repair. It leaves the
 * main/development checkout untouched: repair uses isolated git worktrees,
 * verified patches are proposals only, and investigations are read-only.
 *
 * No LLM, cloud API, GitHub token, network fetch, paid service, or shell
 * command constructed from prompts is required.
 */
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import crypto from 'node:crypto';
import {executeInvestigation} from './backlog-investigator.mjs';
import {summarizeRepairHandoffs} from './teamleader-handoffs.mjs';

const HEAD=/^[0-9a-f]{40}$/;
const OUTCOMES=new Set([
 'RUNNING','NO_FAILURE','NO_CODE_DIAGNOSTIC','VERIFIED_PATCH_PENDING_REVIEW',
 'NO_VERIFIED_REPAIR','DONE_INVESTIGATION','CHECKS_FAILED','NO_READY_INVESTIGATION',
 'IDLE','INTERRUPTED','ERROR'
]);
const KINDS=new Set(['BASELINE_REPAIR','OWNER_REPAIR','INVESTIGATE','IDLE']);
const MAX_EVENTS=96;
const MAX_BYTES=65000;
const hash=text=>crypto.createHash('sha256').update(text).digest('hex');
const dateOf=n=>new Date(n).toISOString();

export function initialLeaderState(){
 return {schema:'continuum-teamleader',version:1,events:[],attemptedHeads:[],discoveryHeads:[],integrity:null};
}
function seal(s){
 const {integrity:_ignored,...data}=s;
 return {...data,integrity:hash(JSON.stringify(data))};
}
export function decodeLeaderState(bytes){
 if(!bytes)return initialLeaderState();
 if(bytes.length>MAX_BYTES)throw Error('Teamleader state exceeds limit');
 const s=JSON.parse(bytes.toString('utf8'));
 if(s?.schema!=='continuum-teamleader'||s.version!==1||
    !Array.isArray(s.events)||s.events.length>MAX_EVENTS||
    !Array.isArray(s.attemptedHeads)||s.attemptedHeads.length>64||
    s.attemptedHeads.some(h=>!HEAD.test(h))||
    (s.discoveryHeads!==undefined &&
     (!Array.isArray(s.discoveryHeads)||s.discoveryHeads.length>64||
      s.discoveryHeads.some(h=>!HEAD.test(h))))||
    s.integrity!==hash(JSON.stringify((({integrity,...data})=>data)(s))))
   throw Error('Invalid or corrupted Teamleader state');
 for(const e of s.events)if(!HEAD.test(e.head)||!KINDS.has(e.kind)||
   !OUTCOMES.has(e.outcome)||!Number.isSafeInteger(e.at)||
   e.at<0||!Number.isSafeInteger(e.elapsedMs)||e.elapsedMs<0||
   typeof e.detail!=='string'||e.detail.length>160||
   typeof e.id!=='string'||!/^tl-[0-9a-f]{20}$/.test(e.id))
  throw Error('Invalid Teamleader history event');
 return {...s,discoveryHeads:s.discoveryHeads??[]};
}
/** Only count events that a real worker persisted; never infer completed
 * code changes from successful analysis or the number of role messages.
 * Totals cover the bounded local event history, not all historical runs.
 */
export function summarizeTeamleaderProgress(state){
 const events=state.events??[];
 const ended=new Set(events.filter(e=>e.outcome!=='RUNNING').map(e=>e.id));
 const count=outcome=>events.filter(e=>e.outcome===outcome).length;
 return {
  schema:'continuum-teamleader-progress',
  version:1,
  windowEvents:events.length,
  checkedHeads:state.attemptedHeads.length,
  analyzedHeads:(state.discoveryHeads??[]).length,
  baselineClean:count('NO_FAILURE'),
  testFailuresDetected:count('NO_CODE_DIAGNOSTIC')+
   count('NO_VERIFIED_REPAIR')+count('VERIFIED_PATCH_PENDING_REVIEW'),
  independentlyVerifiedPatchProposals:count('VERIFIED_PATCH_PENDING_REVIEW'),
  completedInvestigations:count('DONE_INVESTIGATION'),
  investigationTestFailures:count('CHECKS_FAILED'),
  workerErrors:count('ERROR'),
  interrupted:count('INTERRUPTED'),
  currentlyUnfinished:events.filter(e=>e.outcome==='RUNNING'&&!ended.has(e.id)).length,
  lastOutcome:events.filter(e=>e.outcome!=='RUNNING').at(-1)?.outcome??null
 };
}

export function selectTeamleaderAction(state,head,readyTask=null){
 if(!HEAD.test(head))throw Error('Full immutable commit SHA required');
 // Finish incomplete work explicitly; do not re-run it against the same base.
 if(state.attemptedHeads.includes(head)||state.events.some(e=>e.head===head&&e.kind==='BASELINE_REPAIR'))
   return readyTask?{kind:'INVESTIGATE',taskId:readyTask.id}:{kind:'IDLE'};
 return {kind:'BASELINE_REPAIR'};
}
function writeAtomic(file,data){
 fs.mkdirSync(path.dirname(file),{recursive:true,mode:0o700});
 const pending=file+'.tmp-'+process.pid+'-'+crypto.randomBytes(4).toString('hex');
 try{
  fs.writeFileSync(pending,data,{flag:'wx',mode:0o600});
  fs.renameSync(pending,file);
 }finally{fs.rmSync(pending,{force:true});}
}
function append(state,event){
 return seal({...state,events:[...state.events,event].slice(-MAX_EVENTS)});
}
function getLock(file,now){
 fs.mkdirSync(path.dirname(file),{recursive:true,mode:0o700});
 const lock=file+'.lock';
 const payload=JSON.stringify({pid:process.pid,host:os.hostname(),at:now});
 const create=()=>fs.writeFileSync(lock,payload,{flag:'wx',mode:0o600});
 try{create();}
 catch(e){
  if(e.code!=='EEXIST')throw e;
  // Automatic crash recovery is safe only for a dead PID on THIS host.
  // If the process is alive or host/owner is unknown, fail closed.
  let previous;
  try{previous=JSON.parse(fs.readFileSync(lock,'utf8'));}
  catch{throw Error('Teamleader lock malformed; inspect manually');}
  if(previous.host!==os.hostname()||
     !Number.isSafeInteger(previous.pid)||previous.pid<=0||
     !Number.isSafeInteger(previous.at)||now-previous.at<5*60_000)
    throw Error('Teamleader already running or lock requires recovery');
  try{process.kill(previous.pid,0);throw Error('Teamleader still running');}
  catch(err){
   if(err.code!=='ESRCH')throw err;
  }
  // Rename the exact stale lock out of the way. Another runner may win the
  // race to create a fresh lock; the exclusive create then fails safely.
  const stale=lock+'.stale-'+crypto.randomBytes(6).toString('hex');
  try{
   // Re-check before takeover to avoid deleting a different owner's lock.
   if(fs.readFileSync(lock,'utf8')!==JSON.stringify(previous))
    throw Error('Teamleader lock changed while recovering');
   fs.renameSync(lock,stale);
  }catch(e){throw Error('Teamleader lock recovery conflict: '+e.message);}
  finally{fs.rmSync(stale,{force:true});}
  create();
 }
 return ()=>{
  // An older process must not remove a lock belonging to its successor.
  try{if(fs.readFileSync(lock,'utf8')===payload)fs.rmSync(lock,{force:true});}
  catch(e){if(e.code!=='ENOENT')throw e;}
 };
}

export function runTeamleaderCycle({
 root, stateDir, driver, now=Date.now, budgetMinutes=20, retry=false,log=()=>{}
}={}){
 if(!root||!stateDir||!driver)throw Error('root, stateDir and driver required');
 if(!Number.isSafeInteger(budgetMinutes)||budgetMinutes<1||budgetMinutes>30)
  throw Error('Teamleader budget must be 1..30 minutes');
 const statePath=path.join(path.resolve(stateDir),'teamleader-state.json');
 const sourceRoot=path.resolve(root),storeRoot=path.resolve(stateDir);
 if(storeRoot===sourceRoot||storeRoot.startsWith(sourceRoot+path.sep))
  throw Error('Store Teamleader state outside the repository');
 const started=now(),unlock=getLock(statePath,started);
 try{
  let state=decodeLeaderState(fs.existsSync(statePath)?fs.readFileSync(statePath):null);
  const head=driver.head(root);
  if(!HEAD.test(head))throw Error('Repository HEAD is not a full SHA');
  // Keep STARTED outcome on disk before work. A crash is not evidence of success.
  const finishedIds=new Set(state.events.filter(e=>e.outcome!=='RUNNING').map(e=>e.id));
  const incomplete=state.events.filter(e=>e.outcome==='RUNNING'&&!finishedIds.has(e.id)).at(-1);
  if(incomplete){
   state=append(state,{...incomplete,outcome:'INTERRUPTED',elapsedMs:0,
     detail:'Previous cycle ended without verified completion'});
   writeAtomic(statePath,JSON.stringify(state)+'\n');
  }
  const owner=driver.readyOwnerRepair?.(root,stateDir,head)??null;
  if(owner && (owner.kind!=='REPAIR'||owner.source!=='owner'||owner.state!=='READY'||
     !/^[a-z][a-z0-9-]{2,63}$/.test(owner.id)||owner.subject?.base!==head||
     !['node','android','jvm'].includes(owner.subject?.chain)))
    throw Error('Untrusted owner repair task');
  let ready=owner?null:(driver.readyInvestigation?.(root,stateDir,head)??null);
  if(ready && (ready.kind!=='INVESTIGATE'||ready.state!=='READY'||!/^inv-[0-9a-f]{16}$/.test(ready.id)))
   throw Error('Untrusted investigation task');
  let chosen=owner?{kind:'OWNER_REPAIR',taskId:owner.id}:
    selectTeamleaderAction(state,head,ready);
  if(retry)chosen={kind:'BASELINE_REPAIR'};
  if(chosen.kind==='IDLE' && !state.discoveryHeads.includes(head) &&
     typeof driver.prepareSafeInvestigation==='function'){
   // Source discovery is expensive. Evaluate each immutable HEAD only once,
   // including when the source contains no structural findings.
   // Persist intent BEFORE scanning: process death must not cause a hot loop.
   state=seal({...state,discoveryHeads:[
     ...state.discoveryHeads.filter(h=>h!==head),head].slice(-64)});
   writeAtomic(statePath,JSON.stringify(state)+'\n');
   try{
    // Only structural, read-only investigations may be auto-accepted.
    ready=driver.prepareSafeInvestigation(root,stateDir,head)??null;
    if(ready){
     if(ready.kind!=='INVESTIGATE'||ready.state!=='READY'||!/^inv-[0-9a-f]{16}$/.test(ready.id))
      throw Error('Unsafe auto-accepted task');
     chosen={kind:'INVESTIGATE',taskId:ready.id};
    }
   }catch(e){
    const detail=('Discovery failed: '+String(e?.message??e)).slice(0,160);
    const id='tl-'+hash(head+'\0DISCOVERY\0'+started).slice(0,20);
    state=append(state,{id,at:started,head,kind:'IDLE',outcome:'ERROR',
      elapsedMs:Math.max(0,now()-started),detail});
    writeAtomic(statePath,JSON.stringify(state)+'\n');
    return {head,kind:'IDLE',outcome:'ERROR',detail,statePath};
   }
  }
  const id='tl-'+hash(head+'\0'+chosen.kind+'\0'+started).slice(0,20);
  const run={id,at:started,head,kind:chosen.kind,outcome:'RUNNING',
    elapsedMs:0,detail:(chosen.taskId??'Starting safe bounded cycle').slice(0,160)};
  if(chosen.kind==='IDLE'){
   return {head,kind:chosen.kind,outcome:'IDLE',statePath};
  }
  if(chosen.kind==='BASELINE_REPAIR'||chosen.kind==='OWNER_REPAIR'){
   // An accepted owner repair already runs the fixed baseline: do not repeat
   // the same expensive chain as BASELINE_REPAIR on the next scheduled cycle.
   state=seal({...state,attemptedHeads:[...state.attemptedHeads.filter(h=>h!==head),head].slice(-64)});
  }
  state=append(state,run);
  writeAtomic(statePath,JSON.stringify(state)+'\n');
  let outcome='ERROR',detail='',artifact=null;
  try{
   const result=chosen.kind==='BASELINE_REPAIR'
    ?driver.repair(root,stateDir,head,{budgetMinutes})
    :chosen.kind==='OWNER_REPAIR'
      ?driver.ownerRepair(root,stateDir,head,chosen.taskId,{budgetMinutes})
      :driver.investigate(root,stateDir,head,chosen.taskId);
   if(chosen.kind==='BASELINE_REPAIR'){
    const map={NO_FAILURE:'NO_FAILURE',
     NO_CODE_DIAGNOSTIC:'NO_CODE_DIAGNOSTIC',
     VERIFIED:'VERIFIED_PATCH_PENDING_REVIEW',
     NO_VERIFIED_REPAIR:'NO_VERIFIED_REPAIR'};
    if(!Object.hasOwn(map,result?.outcome))throw Error('Unknown repair outcome');
    outcome=map[result.outcome];
    detail=String(result.verified?.strategy??result.outcome).slice(0,160);
   } else if(chosen.kind==='OWNER_REPAIR'){
    if(result?.status==='DONE'){
      if(!/^[0-9a-f]{64}$/.test(result.patchSha256??'')||
         result.taskId!==chosen.taskId||result.approvedForMerge!==false||
         !Array.isArray(result.profiles)||!result.profiles.length||
         result.profiles.some(p=>!p.passed||p.failed!==0)||
         !result.profiles.some(p=>p.executed>0))
        throw Error('Owner repair claimed DONE without independent test evidence');
      outcome='VERIFIED_PATCH_PENDING_REVIEW';
      detail=('owner '+chosen.taskId+' patch '+result.patchSha256.slice(0,12)).slice(0,160);
    }else if(result?.status==='FAILED'){
      outcome='NO_VERIFIED_REPAIR';
      detail=String(result.reason??'Owner repair failed').slice(0,160);
    }else throw Error('Unknown owner repair outcome');
   } else {
    const allowed={DONE:'DONE_INVESTIGATION',
     CHECKS_FAILED:'CHECKS_FAILED',
     NO_READY_INVESTIGATION:'NO_READY_INVESTIGATION'};
    if(!Object.hasOwn(allowed,result?.status))throw Error('Unknown investigation outcome');
    if(result.status==='DONE' && (!Number.isSafeInteger(result.executed) ||
       result.executed<1 || result.failed!==0 || result.taskId!==chosen.taskId))
      throw Error('Investigation reported DONE without matching executed checks');
    outcome=allowed[result.status];
    detail=String(result.staticResult??result.status).slice(0,160);
   }
   artifact=result.artifact??result.report??null;
   log('[teamleader] '+chosen.kind+' -> '+outcome);
  }catch(e){
   detail=(e?.name??'Error')+': '+String(e?.message??'unknown').slice(0,120);
   log('[teamleader] '+detail);
   outcome='ERROR';
  }
  const elapsed=Math.max(0,now()-started);
  state=append(state,{...run,outcome,elapsedMs:elapsed,detail:detail.slice(0,160)});
  writeAtomic(statePath,JSON.stringify(state)+'\n');
  return {head,kind:chosen.kind,outcome,detail,elapsedMs:elapsed,
    artifact,statePath,taskId:chosen.taskId??null};
 }finally{unlock();}
}

/** No external services: actual git worktree, static analyzer and fixed tests. */
export async function localTeamleaderDriver(){
 const [{git,createWorktree,removeWorktree,repairSession},
  {loadLedger,emptyLedger,recordSession,saveLedger},
  {BacklogStore,nextEligible},
  {analyzeRepository},
  {syncBacklogOps},
  {executeOwnerRepairTask}]=await Promise.all([
  import('./repair-loop.mjs'),import('./repair-ledger.mjs'),
  import('./development-backlog.mjs'),import('./code-analyzer.mjs'),
  import('./development-planner.mjs'),import('./backlog-repair-worker.mjs')
 ]);
 const fsApi=await import('node:fs');
 return {
  head(root){return git(root,['rev-parse','HEAD']).stdout.trim();},
  readyOwnerRepair(root,stateDir,head){
   const store=new BacklogStore(path.join(stateDir,'development-backlog.json'),
    {actor:'continuum-teamleader'});
   const snapshot=store.read().doc;
   if(snapshot.tasks.some(t=>t.source==='owner'&&t.kind==='REPAIR'&&
        t.state==='IN_PROGRESS'&&t.claim?.leaseUntil<Date.now()))
     store.apply([{op:'expire',actor:'continuum-teamleader',at:Date.now()}]);
   const next=nextEligible(store.read().doc,['REPAIR'],['owner']);
   return next?.subject?.base===head?next:null;
  },
  ownerRepair(root,stateDir,head,taskId,{budgetMinutes}){
   return executeOwnerRepairTask({
    root,file:path.join(stateDir,'development-backlog.json'),
    out:path.join(stateDir,'reports','owner-repair'),
    id:taskId,budgetMinutes,actor:'continuum-task-worker'
   });
  },
  readyInvestigation(root,stateDir){
   return nextEligible(new BacklogStore(path.join(stateDir,'development-backlog.json'),
    {actor:'continuum-teamleader'}).read().doc,['INVESTIGATE']);
  },
  prepareSafeInvestigation(root,stateDir){
   const backlog=new BacklogStore(path.join(stateDir,'development-backlog.json'),
    {actor:'continuum-teamleader'});
   const {index:_index,...report}=analyzeRepository(root);
   const doc=backlog.read().doc;
   const operations=syncBacklogOps(doc,report,{at:Date.now(),actor:'code-analyzer',limit:2});
   if(operations.length)backlog.apply(operations,{stopOnError:false});
   const current=backlog.read().doc;
   const task=current.tasks
    .filter(x=>x.kind==='INVESTIGATE'&&x.source==='code-analyzer'&&x.state==='PROPOSED')
    .sort((a,b)=>b.priority-a.priority||a.id.localeCompare(b.id))[0];
   if(!task)return null;
   backlog.apply([{op:'accept',actor:'continuum-teamleader',at:Date.now(),
    id:task.id,reason:'Only read-only static investigation; no code mutation'}]);
   return backlog.read().doc.tasks.find(t=>t.id===task.id);
  },
  investigate(root,stateDir,head,taskId){
   const out=path.join(stateDir,'reports','investigate-'+taskId+'.json');
   return executeInvestigation({root,file:path.join(stateDir,'development-backlog.json'),
    out,base:head,actor:'continuum-teamleader',taskId});
  },
  repair(root,stateDir,head,{budgetMinutes}){
   const ledgerPath=path.join(stateDir,'repair-ledger.json');
   const ledger=fsApi.existsSync(ledgerPath)?loadLedger(ledgerPath):emptyLedger();
   const out=path.join(stateDir,'reports','repair-'+head.slice(0,12));
   fsApi.mkdirSync(out,{recursive:true});
   const wt=createWorktree(root,head);
   let result;
   try{
    result=repairSession({wt,base:head,chain:'node',out,ledger,
      maxCandidates:3,deadline:Date.now()+budgetMinutes*60_000});
    // Role handoffs are receipts of REAL work done by the existing repair
    // pipeline. Never represent a proposed patch as completed or merged.
    result.handoffs=summarizeRepairHandoffs(result);
   } finally {removeWorktree(root,wt);}
   fsApi.writeFileSync(path.join(out,'report.json'),JSON.stringify(result,null,2)+'\n');
   saveLedger(ledgerPath,recordSession(ledger,result,{trigger:'local-teamleader'}));
   return {...result,artifact:path.join(out,'report.json')};
  }
 };
}
