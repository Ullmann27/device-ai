// STATUS 22.10.2026: UNFERTIG (WIP) – syntaktisch geprüft, noch ohne Tests; von keinem Produktivpfad importiert.
// CCC1 DialogueOrchestrator: bounded state machine between a reasoner and the EXISTING coding Teamleader
// (code-test job: canonical backlog -> isolated worktrees -> independent second run -> receipt).
// The task state is derived from the DurableTaskJournal alone:
//   DRAFT (owner GOAL) -> VALIDATED (reasoner hypothesis) -> APPROVED (gated TEST_REQUEST) -> RUNNING / VERIFYING
//   (transient, inside execute) -> COMPLETED (CONFIRMED critique) | ABSTAINED | FAILED | ABORTED.
// The reasoner only proposes. Permissions come from the owner; receipts from the verifier; a critique that
// contradicts its receipt, an unauthorized request or a message while a request is pending ends the task as FAILED.
// Deadline, round limit, CPU budget and the commit binding are checked before every execution. Nothing here merges,
// pushes, installs or promotes: a confirmed hypothesis is a contract-test patch pending the owner's review.
import fs from 'node:fs';
import path from 'node:path';
import {randomUUID} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {runMobileCodingJob,cancelMobileCodingJob} from '../../scripts/continuum-mobile-coding.mjs';
import {envelope,envelopeProblems,newTaskId,sha256,CAPABILITY} from './ccc1-envelope.mjs';
import {DurableTaskJournal,WriterLock,runtimeKey} from './ccc1-journal.mjs';
import {gateTestRequest} from './ccc1-gate.mjs';
import {verifyJobReceipt,issueVerificationId} from './ccc1-verifier.mjs';
import {recover,jobIdFor,pendingRequest,builderStateOf,teamleaderStateOf} from './ccc1-recovery.mjs';

const FAILED_CODES=new Set(['CAPABILITY_DENIED','PROTOCOL_VIOLATION']);

/** Crash injection for the recovery experiments: CCC1_CRASH_AT=<label> and CCC1_CRASH_N=<occurrence>. */
const crashCounts=new Map();
export function crashPoint(label){
 if(process.env.CCC1_CRASH_AT!==label)return;
 const n=(crashCounts.get(label)??0)+1;crashCounts.set(label,n);
 if(n===Number(process.env.CCC1_CRASH_N??1))process.kill(process.pid,'SIGKILL');
}

/** CPU of this process and its waited-for children in ms (Linux /proc); null where not measurable. */
export function cpuMillis(){
 try{
  const f=fs.readFileSync('/proc/self/stat','utf8').split(') ')[1].split(' ');
  return (Number(f[11])+Number(f[12])+Number(f[13])+Number(f[14]))*10;   // utime stime cutime cstime at 100 Hz
 }catch{return null;}
}

/** Task state from the journal. */
export function taskState(entries){
 const of=k=>entries.filter(e=>e.kind===k);
 const abort=of('ABORT')[0];
 const confirmed=of('CRITIQUE').find(c=>c.payload.verdict==='CONFIRMED');
 const pending=pendingRequest(entries);
 const receipts=of('RECEIPT');
 const critiqued=new Set(of('CRITIQUE').map(c=>c.payload.receipt));
 const approvalFor=q=>{
  const req=entries.filter(e=>e.kind==='APPROVAL_REQUEST'&&e.payload.request===q?.messageId).at(-1);
  if(!req)return {requested:false};
  const answer=entries.find(e=>e.kind==='APPROVAL'&&e.payload.approvalRequest===req.messageId);
  return {requested:true,request:req,decision:answer?.payload.decision??null};
 };
 const state=abort?(FAILED_CODES.has(abort.payload.code)?'FAILED':'ABORTED'):confirmed?'COMPLETED':of('ABSTAIN').length?'ABSTAINED':
  pending?'APPROVED':of('HYPOTHESIS').length?'VALIDATED':'DRAFT';
 const cpu=receipts.reduce((n,r)=>n+(r.payload.cost?.cpuMillis??0),0);
 return {state,terminal:['COMPLETED','ABSTAINED','FAILED','ABORTED'].includes(state),pending,approval:pending?approvalFor(pending):null,
  rounds:of('TEST_REQUEST').length,cpuMillis:cpu,uncritiqued:receipts.find(r=>!critiqued.has(r.messageId))??null,receipts};
}

function head(project){
 const r=spawnSync('git',['rev-parse','HEAD'],{cwd:project,encoding:'utf8',timeout:15000,shell:false});
 if(r.status!==0)throw Error('project is not a git checkout');
 return r.stdout.trim();
}

/**
 * Owner starts a task: the GOAL binds repository, commit (current HEAD), worktree, deadline, rounds, budgets and
 * the permissions the owner grants. Returns the task id.
 */
export function startTask({project,stateDir,goal,repoFullName,worktreeId='main',permissions=[CAPABILITY],maxRounds=3,
 deadlineMinutes=30,maxCpuMillis=600_000,maxMemoryBytes=2_000_000_000,now=Date.now()}){
 const key=runtimeKey(stateDir);
 const taskId=newTaskId();
 const binding={sessionId:randomUUID(),taskId,repoFullName,baseCommitSha:head(project),worktreeId,deadline:now+deadlineMinutes*60_000,
  maxRounds,maxCpuMillis,maxMemoryBytes,permissions,checkpointHash:null};
 const env=envelope({binding,sender:'OWNER',kind:'GOAL',payload:goal,sequence:0});
 const problems=envelopeProblems(env);
 if(problems.length)throw Error('invalid goal: '+problems.join('; '));
 DurableTaskJournal.forTask(stateDir,taskId,key).append(env);
 return taskId;
}

function bindingOf(goal){
 const {sessionId,taskId,repoFullName,baseCommitSha,worktreeId,deadline,maxRounds,maxCpuMillis,maxMemoryBytes,permissions,checkpointHash}=goal;
 return {sessionId,taskId,repoFullName,baseCommitSha,worktreeId,deadline,maxRounds,maxCpuMillis,maxMemoryBytes,permissions,checkpointHash};
}

/** Owner decision on an APPROVAL_REQUEST, or an owner ABORT. Needs the worktree's writer lock. */
export function ownerAction({stateDir,taskId,kind,payload}){
 if(!['APPROVAL','ABORT'].includes(kind))throw Error('owner action must be APPROVAL or ABORT');
 const journal=DurableTaskJournal.forTask(stateDir,taskId,runtimeKey(stateDir));
 const entries=journal.entries();
 const lock=new WriterLock(stateDir,entries[0].worktreeId);
 if(!lock.acquire().acquired)throw Error('another writer is active on this worktree');
 try{
  const now=journal.entries();
  if(taskState(now).terminal)throw Error('the task has already ended ('+taskState(now).state+')');
  if(kind==='APPROVAL'){
   const req=now.find(e=>e.messageId===payload?.approvalRequest);
   if(req?.kind!=='APPROVAL_REQUEST')throw Error('APPROVAL must answer an APPROVAL_REQUEST');
   if(now.some(e=>e.kind==='APPROVAL'&&e.payload.approvalRequest===req.messageId))throw Error('this approval request was already answered');
  }
  return journal.append(envelope({binding:bindingOf(now[0]),sender:'OWNER',kind,payload,sequence:now.length,
   parentMessageId:kind==='APPROVAL'?payload.approvalRequest:null})).entry;
 }finally{lock.release();}
}

/** Protocol rules beyond the envelope schema for one reasoner message; returns a problem text or null. */
export function reasonerProblem(m,s,entries){
 if(s.terminal)return 'the task has already ended ('+s.state+')';
 if(s.pending)return 'a TEST_REQUEST is still waiting for its RECEIPT';
 const byId=id=>entries.find(e=>e.messageId===id);
 if(s.uncritiqued&&!(m.kind==='CRITIQUE'&&m.payload.receipt===s.uncritiqued.messageId))return 'RECEIPT '+s.uncritiqued.messageId+' must be critiqued first';
 switch(m.kind){
 case 'CRITIQUE':{
  const r=byId(m.payload.receipt);
  if(r?.kind!=='RECEIPT')return 'CRITIQUE must reference a RECEIPT';
  if(r.payload.verdict!==m.payload.verdict)return 'CRITIQUE verdict '+m.payload.verdict+' contradicts the verified receipt ('+r.payload.verdict+')';
  return null;}
 case 'TEST_REQUEST':{
  const h=byId(m.payload.hypothesis);
  if(h?.kind!=='HYPOTHESIS')return 'TEST_REQUEST must reference a HYPOTHESIS';
  if(h.payload.module!==m.payload.module||h.payload.export!==m.payload.export)return 'TEST_REQUEST must test exactly its hypothesis';
  const earlier=entries.filter(e=>e.kind==='TEST_REQUEST'&&e.payload.hypothesis===h.messageId);
  const interruptedOnly=earlier.every(q=>s.receipts.some(r=>r.payload.request===q.messageId&&r.payload.execution==='INTERRUPTED'));
  if(earlier.length&&!interruptedOnly)return 'this hypothesis was already tested; REVISE it instead';
  return null;}
 case 'REVISE':return byId(m.payload.hypothesis)?.kind==='HYPOTHESIS'?null:'REVISE must reference a HYPOTHESIS';
 default:return null;
 }
}

/**
 * Runs or resumes one task until it ends, waits for the owner, or the turn limit is reached. `reasoner.turn(view)`
 * gets a JSON view and returns messages [{messageId, parentMessageId?, kind, payload}].
 */
export async function runTask({project,stateDir,taskId,reasoner,runJob=runMobileCodingJob,cancelJob=cancelMobileCodingJob,
 maxTurns=16,now=()=>Date.now()}){
 const root=fs.realpathSync(path.resolve(project));
 const key=runtimeKey(stateDir);
 const journal=DurableTaskJournal.forTask(stateDir,taskId,key);
 const first=journal.entries({allowTorn:true})[0];
 if(!first)throw Error('unknown task '+taskId);
 const lock=new WriterLock(stateDir,first.worktreeId);
 const held=lock.acquire();
 if(!held.acquired)return {outcome:'BLOCKED_WRITER_ACTIVE',owner:held.owner};
 try{
  const {report}=recover({project:root,stateDir,journal});
  if(report.corruption)return {outcome:'STOPPED_CORRUPT_JOURNAL',report};
  const binding=bindingOf(journal.entries()[0]);
  const append=(sender,kind,payload,extra={})=>{
   const entries=journal.entries();
   crashPoint('append-'+kind);
   return journal.append({...envelope({binding,sender,kind,payload,sequence:entries.length,...extra}),...(extra.messageId?{messageId:extra.messageId}:{})},
    process.env.CCC1_CRASH_AT==='torn-'+kind?{onPartialWrite:()=>process.kill(process.pid,'SIGKILL')}:{}).entry;
  };
  const abort=(code,reason)=>append('TEAMLEADER','ABORT',{reason:String(reason).slice(0,600),code});
  for(let turn=0;turn<maxTurns;turn++){
   const entries=journal.entries();
   const s=taskState(entries);
   if(s.terminal)return {outcome:s.state,report,entries};
   if(head(root)!==binding.baseCommitSha){abort('STALE_BASE','checkout moved away from the bound commit '+binding.baseCommitSha.slice(0,12));continue;}
   if(now()>=binding.deadline){abort('DEADLINE','deadline passed');continue;}
   if(s.pending){
    const q=s.pending,job=jobIdFor(taskId,q.messageId);
    if(s.approval?.requested&&s.approval.decision===null)return {outcome:'WAITING_FOR_OWNER',report,entries};
    if(s.approval?.decision==='DENIED'){abort('APPROVAL_DENIED','the owner denied '+s.approval.request.payload.needs.join(', '));continue;}
    // decided jobs are final for this task: close them so the Teamleader does not retry a refuted hypothesis first
    for(const r of s.receipts)if(r.payload.verdict!=='CONFIRMED'&&r.payload.execution!=='INTERRUPTED')
     cancelJob({project:root,teamleaderState:teamleaderStateOf(stateDir),job:r.payload.job,reason:'CCC1: hypothesis decided ('+r.payload.verdict+')'});
    const {report:fresh}=recover({project:root,stateDir,journal});
    const status=fresh.pending?.status??'NOT_STARTED';
    const retry='retry-after-interruption:'+q.messageId;
    if(status==='INTERRUPTED'){
     const asked=entries.filter(e=>e.kind==='APPROVAL_REQUEST'&&e.payload.needs.includes(retry)).at(-1);
     const answer=asked&&entries.find(e=>e.kind==='APPROVAL'&&e.payload.approvalRequest===asked.messageId);
     if(!asked){append('TEAMLEADER','APPROVAL_REQUEST',{request:q.messageId,needs:[retry],
      reason:'Die Ausführung dieses Auftrags wurde unterbrochen. Eine Wiederholung ist nicht idempotent und braucht die Freigabe des Besitzers.'});continue;}
     if(!answer)return {outcome:'WAITING_FOR_OWNER',report:fresh,entries};
     if(answer.payload.decision!=='GRANTED'){abort('APPROVAL_DENIED','the owner did not allow repeating an interrupted execution');continue;}
     cancelJob({project:root,teamleaderState:teamleaderStateOf(stateDir),job,reason:'CCC1: interrupted execution closed by owner approval'});
     const fields={request:q.messageId,receiptSha256:sha256('interrupted\0'+job),verdict:'INCONCLUSIVE'};
     append('VERIFIER','RECEIPT',{...fields,job,status:'INTERRUPTED',reasons:['execution was interrupted; closed with owner approval, nothing claimed'],
      patchSha256:null,evidenceSha256:null,runs:[],replayed:false,execution:'INTERRUPTED',cost:{cpuMillis:0,wallMillis:0}},
      {parentMessageId:q.messageId,verificationReceiptId:issueVerificationId(key,fields)});
     continue;
    }
    if(s.cpuMillis>=binding.maxCpuMillis){abort('BUDGET','CPU budget used up');continue;}
    if(process.memoryUsage().rss>binding.maxMemoryBytes){abort('BUDGET','memory budget exceeded by the runtime process');continue;}
    const order={module:q.payload.module,exportName:q.payload.export,casesJson:JSON.stringify(q.payload.cases)};
    crashPoint('before-execute');
    const cpu0=cpuMillis(),t0=Date.now();
    const receipt=await runJob({project:root,builderState:builderStateOf(stateDir),teamleaderState:teamleaderStateOf(stateDir),job,...order,budgetMinutes:q.payload.budgetMinutes});
    const cpu1=cpuMillis();
    crashPoint('after-execute');
    const check=verifyJobReceipt(receipt,{job,commit:binding.baseCommitSha,...order});
    const fields={request:q.messageId,receiptSha256:sha256(JSON.stringify(receipt)),verdict:check.verdict};
    append('VERIFIER','RECEIPT',{...fields,job,status:String(receipt.status),reasons:check.reasons,patchSha256:receipt.patch?.sha256??null,
     evidenceSha256:receipt.evidence?.verificationSha256??null,
     runs:(receipt.commands??[]).map(c=>({phase:c.phase,exitCode:c.exitCode,executed:c.executed,failed:c.failed})),
     replayed:receipt.replayed===true,execution:receipt.replayed===true||status==='FINISHED_UNRECORDED'?'REPLAYED':'EXECUTED',
     cost:{cpuMillis:cpu0===null||cpu1===null?0:cpu1-cpu0,wallMillis:Date.now()-t0}},
     {parentMessageId:q.messageId,verificationReceiptId:issueVerificationId(key,fields)});
    crashPoint('after-receipt');
    continue;
   }
   if(s.approval?.requested)return {outcome:'WAITING_FOR_OWNER',report,entries};
   const view=JSON.parse(JSON.stringify({goal:entries[0].payload,state:s.state,rounds:s.rounds,maxRounds:binding.maxRounds,
    entries:entries.map(({messageId,sequence,sender,kind,payload})=>({messageId,sequence,sender,kind,payload}))}));
   const messages=await reasoner.turn(view);
   if(!Array.isArray(messages)||!messages.length){abort('PROTOCOL_VIOLATION','the reasoner sent no message');continue;}
   for(const m of messages){
    const now2=journal.entries(),s2=taskState(now2);
    if(s2.terminal)break;                                               // nothing after the end is journaled
    if(now2.some(e=>e.messageId===m?.messageId)){                       // duplicate delivery: applied once
     const dup=now2.find(e=>e.messageId===m.messageId);
     if(dup.sender==='REASONER'&&dup.kind===m.kind&&JSON.stringify(dup.payload)===JSON.stringify(m.payload))continue;
     abort('PROTOCOL_VIOLATION','conflicting duplicate message '+m.messageId);break;
    }
    const env={...envelope({binding,sender:'REASONER',kind:m?.kind,payload:m?.payload,sequence:now2.length,parentMessageId:m?.parentMessageId??null}),messageId:m?.messageId};
    const problems=envelopeProblems(env);
    const protocol=problems.length?problems.join('; '):reasonerProblem(m,s2,now2);
    if(protocol){abort('PROTOCOL_VIOLATION',protocol);break;}
    if(m.kind==='TEST_REQUEST'){
     const gate=gateTestRequest(m.payload,now2,{rounds:s2.rounds,cpuMillis:s2.cpuMillis,now:now()});
     if(gate.decision==='DENY'){
      const roundOrTime=gate.reasons.every(r=>/round limit|deadline|CPU budget|remaining time/.test(r));
      abort(roundOrTime?(gate.reasons.some(r=>/round/.test(r))?'ROUND_LIMIT':'BUDGET'):'CAPABILITY_DENIED',gate.reasons.join('; '));break;
     }
     crashPoint('reasoner-request');
     append('REASONER','TEST_REQUEST',m.payload,{messageId:m.messageId,parentMessageId:m.parentMessageId??null});
     if(gate.decision==='NEEDS_APPROVAL')append('TEAMLEADER','APPROVAL_REQUEST',{request:m.messageId,needs:gate.needs,
      reason:'Der Auftrag braucht eine Berechtigung, die das Ziel nicht enthält: '+gate.needs.join(', ')});
     break;                                                              // backpressure: one request at a time
    }
    append('REASONER',m.kind,m.payload,{messageId:m.messageId,parentMessageId:m.parentMessageId??null});
   }
  }
  return {outcome:'TURN_LIMIT_REACHED',report,entries:journal.entries()};
 }finally{lock.release();}
}
