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

const HEAD=/^[0-9a-f]{40}$/;
const OUTCOMES=new Set([
 'RUNNING','NO_FAILURE','NO_CODE_DIAGNOSTIC','VERIFIED_PATCH_PENDING_REVIEW',
 'NO_VERIFIED_REPAIR','DONE_INVESTIGATION','CHECKS_FAILED','NO_READY_INVESTIGATION',
 'IDLE','INTERRUPTED','ERROR'
]);
const KINDS=new Set(['BASELINE_REPAIR','INVESTIGATE','IDLE']);
const MAX_EVENTS=96;
const MAX_BYTES=65000;
const hash=text=>crypto.createHash('sha256').update(text).digest('hex');
const dateOf=n=>new Date(n).toISOString();

export function initialLeaderState(){
 return {schema:'continuum-teamleader',version:1,events:[],attemptedHeads:[],integrity:null};
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
    s.integrity!==hash(JSON.stringify((({integrity,...data})=>data)(s))))
   throw Error('Invalid or corrupted Teamleader state');
 for(const e of s.events)if(!HEAD.test(e.head)||!KINDS.has(e.kind)||
   !OUTCOMES.has(e.outcome)||!Number.isSafeInteger(e.at)||
   e.at<0||!Number.isSafeInteger(e.elapsedMs)||e.elapsedMs<0||
   typeof e.detail!=='string'||e.detail.length>160||
   typeof e.id!=='string'||!/^tl-[0-9a-f]{20}$/.test(e.id))
  throw Error('Invalid Teamleader history event');
 return s;
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
  let ready=driver.readyInvestigation?.(root,stateDir,head)??null;
  if(ready && (ready.kind!=='INVESTIGATE'||ready.state!=='READY'||!/^inv-[0-9a-f]{16}$/.test(ready.id)))
   throw Error('Untrusted investigation task');
  let chosen=selectTeamleaderAction(state,head,ready);
  if(retry)chosen={kind:'BASELINE_REPAIR'};
  if(chosen.kind==='IDLE'){
   // Structural findings are only safe to auto-accept for read-only review.
   ready=driver.prepareSafeInvestigation?.(root,stateDir,head)??null;
   if(ready){
    if(ready.kind!=='INVESTIGATE'||ready.state!=='READY'||!/^inv-[0-9a-f]{16}$/.test(ready.id))
     throw Error('Unsafe auto-accepted task');
    chosen={kind:'INVESTIGATE',taskId:ready.id};
   }
  }
  const id='tl-'+hash(head+'\0'+chosen.kind+'\0'+started).slice(0,20);
  const run={id,at:started,head,kind:chosen.kind,outcome:'RUNNING',
    elapsedMs:0,detail:(chosen.taskId??'Starting safe bounded cycle').slice(0,160)};
  if(chosen.kind==='IDLE'){
   return {head,kind:chosen.kind,outcome:'IDLE',statePath};
  }
  if(chosen.kind==='BASELINE_REPAIR'){
   state=seal({...state,attemptedHeads:[...state.attemptedHeads.filter(h=>h!==head),head].slice(-64)});
  }
  state=append(state,run);
  writeAtomic(statePath,JSON.stringify(state)+'\n');
  let outcome='ERROR',detail='',artifact=null;
  try{
   const result=chosen.kind==='BASELINE_REPAIR'
    ?driver.repair(root,stateDir,head,{budgetMinutes})
    :driver.investigate(root,stateDir,head,chosen.taskId);
   if(chosen.kind==='BASELINE_REPAIR'){
    const map={NO_FAILURE:'NO_FAILURE',
     NO_CODE_DIAGNOSTIC:'NO_CODE_DIAGNOSTIC',
     VERIFIED:'VERIFIED_PATCH_PENDING_REVIEW',
     NO_VERIFIED_REPAIR:'NO_VERIFIED_REPAIR'};
    if(!Object.hasOwn(map,result?.outcome))throw Error('Unknown repair outcome');
    outcome=map[result.outcome];
    detail=String(result.verified?.strategy??result.outcome).slice(0,160);
   } else {
    const allowed={DONE:'DONE_INVESTIGATION',
     CHECKS_FAILED:'CHECKS_FAILED',
     NO_READY_INVESTIGATION:'NO_READY_INVESTIGATION'};
    if(!Object.hasOwn(allowed,result?.status))throw Error('Unknown investigation outcome');
    outcome=allowed[result.status];
    detail=String(result.staticResult??result.status).slice(0,160);
   }
   artifact=result.artifact??null;
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
  {syncBacklogOps}]=await Promise.all([
  import('./repair-loop.mjs'),import('./repair-ledger.mjs'),
  import('./development-backlog.mjs'),import('./code-analyzer.mjs'),
  import('./development-planner.mjs')
 ]);
 const fsApi=await import('node:fs');
 return {
  head(root){return git(root,['rev-parse','HEAD']).stdout.trim();},
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
    out,base:head,actor:'continuum-teamleader'});
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
   } finally {removeWorktree(root,wt);}
   fsApi.writeFileSync(path.join(out,'report.json'),JSON.stringify(result,null,2)+'\n');
   saveLedger(ledgerPath,recordSession(ledger,result,{trigger:'local-teamleader'}));
   return {...result,artifact:path.join(out,'report.json')};
  }
 };
}
