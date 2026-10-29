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
import {resolveOutputDestination} from './backlog-repair-worker.mjs';
import {acquireContinuumBuildLease} from './shared-build-lease.mjs';

const HEAD=/^[0-9a-f]{40}$/;
const OUTCOMES=new Set([
 'RUNNING','NO_FAILURE','NO_CODE_DIAGNOSTIC','VERIFIED_PATCH_PENDING_REVIEW',
 'NO_VERIFIED_REPAIR','DONE_INVESTIGATION','CHECKS_FAILED','NO_READY_INVESTIGATION',
 'IDLE','INTERRUPTED','ERROR'
]);
const KINDS=new Set(['BASELINE_REPAIR','OWNER_REPAIR','OWNER_TEST','INVESTIGATE','IDLE']);
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

/** Public proof must agree across overall outcome, candidate attempts and
 * role handoffs. Internal reports are retained solely for learning/audit.
 */
export function publicTeamleaderRepairReport(report){
 if(report?.outcome!=='VERIFIED'||report.independent?.passed===true)return report;
 return {...report,outcome:'NO_VERIFIED_REPAIR',verified:null,
  attempts:(report.attempts??[]).map(a=>a.outcome==='VERIFIED'?
   {...a,outcome:'INSUFFICIENT_EVIDENCE'}:a)};
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
 const sourceRoot=fs.existsSync(root)?fs.realpathSync(root):path.resolve(root);
 const storeRoot=resolveOutputDestination(stateDir);
 const statePath=path.join(storeRoot,'teamleader-state.json');
 if(storeRoot===sourceRoot||storeRoot.startsWith(sourceRoot+path.sep))
  throw Error('Store Teamleader state outside the repository');
 const started=now(),unlock=getLock(statePath,started);
 // The per-teamleader state lock is insufficient: ANSK and the Android
 // mobile builder must also acquire this same atomic checkout-local lease.
 let releaseBuildLease;
 try{releaseBuildLease=acquireContinuumBuildLease(sourceRoot);}
 catch(error){unlock();throw error;}
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
  const ownerTest=owner?null:(driver.readyOwnerContractTask?.(root,stateDir,head)??null);
  if(ownerTest && (ownerTest.kind!=='TEST'||ownerTest.source!=='owner'||
     ownerTest.state!=='READY'||!/^[a-z][a-z0-9-]{2,63}$/.test(ownerTest.id)||
     ownerTest.subject?.base!==head||ownerTest.subject?.chain!=='node'||
     ownerTest.subject?.operation!=='ASSERT_NODE_EXPORT'))
   throw Error('Untrusted owner TEST task');
  let ready=owner||ownerTest?null:(driver.readyInvestigation?.(root,stateDir,head)??null);
  if(ready && (ready.kind!=='INVESTIGATE'||ready.state!=='READY'||!/^inv-[0-9a-f]{16}$/.test(ready.id)))
   throw Error('Untrusted investigation task');
  let chosen=owner?{kind:'OWNER_REPAIR',taskId:owner.id}:
    ownerTest?{kind:'OWNER_TEST',taskId:ownerTest.id}:
    selectTeamleaderAction(state,head,ready);
  // Manual/transient baseline retry must not override newer authorized
  // owner work. A failed infrastructure run never grants more authority.
  if(retry&&!owner&&!ownerTest)chosen={kind:'BASELINE_REPAIR'};
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
  if(['BASELINE_REPAIR','OWNER_REPAIR'].includes(chosen.kind)){
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
      :chosen.kind==='OWNER_TEST'
        ?driver.ownerContractTask(root,stateDir,head,chosen.taskId,{budgetMinutes})
        :driver.investigate(root,stateDir,head,chosen.taskId);
   if(chosen.kind==='BASELINE_REPAIR'){
    const map={NO_FAILURE:'NO_FAILURE',
     NO_CODE_DIAGNOSTIC:'NO_CODE_DIAGNOSTIC',
     VERIFIED:'VERIFIED_PATCH_PENDING_REVIEW',
     NO_VERIFIED_REPAIR:'NO_VERIFIED_REPAIR'};
    if(!Object.hasOwn(map,result?.outcome))throw Error('Unknown repair outcome');
    outcome=result.outcome==='VERIFIED'&&result.independent?.passed!==true
     ?'NO_VERIFIED_REPAIR':map[result.outcome];
    detail=String(result.verified?.strategy??result.outcome).slice(0,160);
   } else if(chosen.kind==='OWNER_TEST'){
    if(result?.status==='DONE'){
      if(result.taskId!==chosen.taskId||result.approvedForMerge!==false||
         !/^[0-9a-f]{64}$/.test(result.patchSha256??'')||
         result.proof?.passed!==true||
         !(result.proof.baselineExecuted>0)||
         !(result.proof.candidateExecuted>result.proof.baselineExecuted)||
         !(result.proof.independentExecuted>=result.proof.candidateExecuted)||
         typeof result.receipt!=='string')
        throw Error('Owner TEST claimed DONE without independent contract evidence');
      outcome='VERIFIED_PATCH_PENDING_REVIEW';
      detail=('test '+chosen.taskId+' patch '+result.patchSha256.slice(0,12)).slice(0,160);
    }else if(result?.status==='FAILED'){
      outcome='CHECKS_FAILED';
      detail=String(result.reason??'Owner TEST checks failed').slice(0,160);
    }else if(result?.status==='NO_READY_TASK'){
      outcome='IDLE';
      detail='Owner TEST was claimed by another worker';
    }else throw Error('Unknown owner TEST task outcome');
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
   // The owner reviews the proposed *patch*. The independent receipt
   // remains available separately in the task's external evidence folder.
   artifact=chosen.kind==='OWNER_TEST'
     ?result.patch??result.receipt??null
     :result.artifact??result.report??null;
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
 }finally{
  try{releaseBuildLease();}finally{unlock();}
 }
}

/** No external services: actual git worktree, static analyzer and fixed tests. */
export async function localTeamleaderDriver({candidateProvider=null,executor=null,executorAbortMode=null,
 contextualExperience=false}={}){
 if(typeof contextualExperience!=='boolean')throw Error('Invalid contextual experience option');
 const [{git,createWorktree,removeWorktree,repairSession,runChain,evidenceSufficient},
  {loadLedger,recordSession,saveLedger},
  {BacklogStore,nextEligible},
  {analyzeRepository},
  {syncBacklogOps,repairSessionOps},
  {executeOwnerRepairTask},
  {executeOwnerContractTestTask},
  {observeCefrCandidates,DEV_CRE1_FILENAME},
  {loadCefrCheckpoint},
  {validateVerifiedReport},
  {auditCefrShadowWinner}]=await Promise.all([
  import('./repair-loop.mjs'),import('./repair-ledger.mjs'),
  import('./development-backlog.mjs'),import('./code-analyzer.mjs'),
  import('./development-planner.mjs'),import('./backlog-repair-worker.mjs'),
  import('./owner-contract-worker.mjs'),
  import('./cefr-repair-ranker.mjs'),import('./cefr-dev-checkpoint.mjs'),
  import('./repair-publish.mjs'),import('./cefr-shadow-benchmark.mjs')
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
   return nextEligible(store.read().doc,['REPAIR'],['owner'],head);
  },
  readyOwnerContractTask(root,stateDir,head){
   const store=new BacklogStore(path.join(stateDir,'development-backlog.json'),
     {actor:'continuum-teamleader'});
   const snapshot=store.read().doc;
   if(snapshot.tasks.some(t=>t.source==='owner'&&t.kind==='TEST'&&
        t.state==='IN_PROGRESS'&&t.claim?.leaseUntil<Date.now()))
     store.apply([{op:'expire',actor:'continuum-teamleader',at:Date.now()}]);
   return nextEligible(store.read().doc,['TEST'],['owner'],head,null,'ASSERT_NODE_EXPORT');
  },
  ownerContractTask(root,stateDir,head,taskId,{budgetMinutes}){
   return executeOwnerContractTestTask({
    root,file:path.join(stateDir,'development-backlog.json'),
    out:path.join(stateDir,'reports','owner-test'),id:taskId,budgetMinutes
   });
  },
  ownerRepair(root,stateDir,head,taskId,{budgetMinutes}){
   return executeOwnerRepairTask({
    root,file:path.join(stateDir,'development-backlog.json'),
    out:path.join(stateDir,'reports','owner-repair'),
    id:taskId,budgetMinutes,actor:'continuum-task-worker',candidateProvider,executor,executorAbortMode
   });
  },
  readyInvestigation(root,stateDir){
   const store=new BacklogStore(path.join(stateDir,'development-backlog.json'),
    {actor:'continuum-teamleader'});
   if(store.read().doc.tasks.some(t=>t.kind==='INVESTIGATE'&&
      t.state==='IN_PROGRESS'&&t.claim?.leaseUntil<Date.now()))
    store.apply([{op:'expire',actor:'continuum-teamleader',at:Date.now()}]);
   return nextEligible(store.read().doc,['INVESTIGATE']);
  },
  prepareSafeInvestigation(root,stateDir){
   // A Node-only owner repository has no Kotlin capability to discover.
   if(!fsApi.existsSync(path.join(root,'app/src')))return null;
   // JS-only and partial repositories have no Android source tree to inspect.
   // This is an idle discovery, not a failed repair or a reason to retry.
   if(!fsApi.existsSync(path.join(root,'app','src')))return null;
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
   const ledger=loadLedger(ledgerPath);
   const out=path.join(stateDir,'reports','repair-'+head.slice(0,12));
   fsApi.mkdirSync(out,{recursive:true});
   const wt=createWorktree(root,head);
   // Developer CRE1 lives outside the checkout and outside Android memory.
   // Corrupt checkpoints are reported as rejected shadow observations only.
   const checkpointFile=path.join(stateDir,'dev-cre1',DEV_CRE1_FILENAME);
   let shadowObserver=null;
   if(fsApi.existsSync(checkpointFile)){
    try{
     const {bytes}=loadCefrCheckpoint({stateDir,repoRoot:root});
     shadowObserver=input=>observeCefrCandidates({...input,checkpointBytes:bytes});
    }catch{
     shadowObserver=()=>{throw Error('INVALID_CEFR_DEV_CHECKPOINT');};
    }
   }
   let result;
   const deadline=Date.now()+budgetMinutes*60_000;
   try{
    result=repairSession({wt,base:head,chain:'node',out,ledger,
      maxCandidates:3,deadline,shadowObserver,executor,executorAbortMode,contextualExperience});
    // The first successful worktree is not an independent verdict.
    // Reapply the patch to a fresh second worktree and rerun the fixed checks.
    if(result.outcome==='VERIFIED'){
     let independent=null;
     try{
      const patchFile=path.join(out,'repair.patch');
      const patch=fsApi.readFileSync(patchFile,'utf8');
      validateVerifiedReport(result,patch);
      independent=createWorktree(root,head);
      git(independent,['apply','--check','--binary',patchFile]);
      git(independent,['apply','--binary',patchFile]);
      const secondOut=path.join(out,'independent-check');
      fsApi.mkdirSync(secondOut,{recursive:true,mode:0o700});
      const verified=runChain('node',independent,secondOut,{deadline,executor,executorAbortMode});
      const suff=evidenceSufficient(result.baseline.results,verified.results);
      const profiles=verified.results.map(r=>({
       profile:r.profile,passed:r.passed,executed:r.tests?.executed??0,
       failed:r.tests?.failed??0,skipped:r.tests?.skipped??0
      }));
      result.independent={passed:verified.passed&&suff.sufficient&&
       profiles.length>0&&profiles.every(r=>r.passed&&r.failed===0)&&
       profiles.some(r=>r.executed>0),
       profiles,evidenceSufficient:suff.sufficient};
     }catch(error){
      result.independent={passed:false,
       reason:String(error?.message??error).slice(0,160)};
     }finally{
      if(independent)removeWorktree(root,independent);
     }
    }

   } finally {removeWorktree(root,wt);}
   // This is a prospective RANKING proxy only. It does not execute the
   // hypothetical shadow order or authorize any model promotion.
   if(result.outcome==='VERIFIED'&&result.independent?.passed===true){
    const audit=auditCefrShadowWinner({report:result,independent:result.independent});
    if(audit.status==='RANKING_PROXY_ONLY')result.cefrShadowAudit=audit;
   }
   const publicReport=publicTeamleaderRepairReport(result);
   publicReport.handoffs=summarizeRepairHandoffs(publicReport);
   // A first-worktree patch must never become a DONE backlog task without
   // the independently verified second worktree. publicReport redacts it.
   if(publicReport.outcome==='VERIFIED'&&publicReport.independent?.passed!==true)
    throw Error('Refusing to record an unverified repair as completed');
   const backlog=new BacklogStore(path.join(stateDir,'development-backlog.json'),
    {actor:'continuum-teamleader'});
   const snapshot=backlog.read().doc;
   if(snapshot.tasks.some(t=>t.state==='IN_PROGRESS'&&t.claim?.leaseUntil<Date.now())){
    const expired=backlog.apply([{op:'expire',actor:'continuum-teamleader',at:Date.now()}],{stopOnError:true});
    if(expired.results.some(x=>x.ok!==true))throw Error('Failed to recover expired repair lease');
   }
   // Journal the independently checked result before a backlog DONE is possible.
   writeAtomic(path.join(out,'report.json'),JSON.stringify(publicReport,null,2)+'\n');
   const session=repairSessionOps(backlog.read().doc,publicReport,
    {actor:'continuum-teamleader'});
   let backlogSync={id:session.id,applied:0,status:session.skipped?'SKIPPED':'NO_OPERATIONS'};
   if(session.ops.length){
    const applied=backlog.apply(session.ops,{stopOnError:true});
    if(applied.results.length!==session.ops.length||
       applied.results.some(entry=>entry.ok!==true))
      throw Error('Repair backlog synchronization was rejected');
    backlogSync={id:session.id,applied:applied.results.length,status:'RECORDED'};
   }
   publicReport.backlogSync=backlogSync;
   const review=[
    '# Continuum repair review',
    '',
    'Base commit: '+head,
    'Outcome: '+publicReport.outcome,
    'Inference: '+(publicReport.inference?.mode??'DETERMINISTIC_ONLY'),
    'Model invocations: '+(publicReport.inference?.modelInvocations??0),
    'Patch SHA-256: '+(publicReport.verified?.patchSha256??'none'),
    'Independent check: '+(publicReport.independent?.passed===true?'PASSED':'NOT VERIFIED'),
    'Backlog: '+backlogSync.status,
    'Automatic merge: NEVER',
    ''
   ];
   writeAtomic(path.join(out,'review-summary.md'),review.join('\n'));
   writeAtomic(path.join(out,'report.json'),JSON.stringify(publicReport,null,2)+'\n');
   saveLedger(ledgerPath,recordSession(ledger,result,{
    trigger:'local-teamleader',
    independentPass:result.outcome==='VERIFIED'?result.independent?.passed===true:undefined
   }));
   return {...publicReport,artifact:path.join(out,'report.json')};
  }
 };
}
