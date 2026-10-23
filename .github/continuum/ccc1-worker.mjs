// CCC1 worker cycle for the existing local loop (local-teamleader-loop.mjs runTeamleaderLoop): no second scheduler.
// Owner goals enter as durable CCC1 tasks (submitGoal); each cycle drives the oldest task that can make progress
// through the orchestrator once, records finished tasks in the experience log and answers in the loop's outcome
// vocabulary. The CCC1 outcome is always kept in the detail; nothing is reported as verified without a receipt.
//   COMPLETED -> VERIFIED_PATCH_PENDING_REVIEW   ABSTAINED/ABORTED -> DONE_INVESTIGATION
//   only owner decisions pending -> NO_READY_INVESTIGATION   nothing open -> IDLE
//   WAITING_FOR_WORKER, BLOCKED_WRITER_ACTIVE, TURN_LIMIT_REACHED -> INTERRUPTED (the loop backs off)
//   FAILED, STOPPED_CORRUPT_JOURNAL -> ERROR
// With indexOnIdle, a cycle without open tasks refreshes the code index in the state directory instead (cached per
// module, so an unchanged repository costs almost nothing); the outcome stays IDLE.
import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {startTask,runTask,taskState,ownerAction} from './ccc1-orchestrator.mjs';
import {Ccc1Reasoner,indexExports,interpretGoal,LexicalLanguageCore} from './ccc1-interpreter.mjs';
import {DisambiguatingProbeCore,ProbeLanguageCore} from './behaviour-probe.mjs';
import {DurableTaskJournal,JournalCorruption,runtimeKey} from './ccc1-journal.mjs';
import {ExperienceLog,experienceOf} from './ccc1-experience.mjs';
import {buildCodeIndex,saveCodeIndex} from './code-index.mjs';
import {assessCcc1Preflight} from './ccc1-local-preflight.mjs';
import {REPAIR_CAPABILITY} from './ccc1-envelope.mjs';
import {REPAIR_CHAINS,cancelMobileCodingJob,readReceipt} from '../../scripts/continuum-mobile-coding.mjs';
import {jobIdFor,teamleaderStateOf,builderStateOf} from './ccc1-recovery.mjs';
import {createHash} from 'node:crypto';

export const OUTCOME_FOR={COMPLETED:'VERIFIED_PATCH_PENDING_REVIEW',ABSTAINED:'DONE_INVESTIGATION',ABORTED:'DONE_INVESTIGATION',
 FAILED:'ERROR',STOPPED_CORRUPT_JOURNAL:'ERROR',WAITING_FOR_WORKER:'INTERRUPTED',BLOCKED_WRITER_ACTIVE:'INTERRUPTED',
 TURN_LIMIT_REACHED:'INTERRUPTED',WAITING_FOR_OWNER:'NO_READY_INVESTIGATION'};
const CORES={lexical:()=>new LexicalLanguageCore(),probe:root=>new ProbeLanguageCore({root}),
 disambiguate:root=>new DisambiguatingProbeCore({root})};

const REPO=/^[A-Za-z0-9][A-Za-z0-9-]{0,38}\/[A-Za-z0-9._-]{1,100}$/;

/**
 * The repository a task is bound to: owner/name of the project's `origin` remote (GitHub URL, HTTPS or SSH), or
 * local/<directory> for a checkout without one - never a placeholder such as owner/device-ai.
 */
export function repositoryOf(project){
 const r=spawnSync('git',['remote','get-url','origin'],{cwd:project,encoding:'utf8',timeout:15000});
 const m=r.status===0?/github\.com[:/]([^/\s]+\/[^/\s]+?)(?:\.git)?\/?$/.exec(r.stdout.trim()):null;
 const name=m?m[1]:'local/'+path.basename(path.resolve(project)).replace(/[^A-Za-z0-9._-]/g,'-');
 if(!REPO.test(name))throw Error('cannot derive the repository binding; pass repoFullName (owner/name)');
 return name;
}

/** Owner goal -> durable CCC1 task bound to the project's repository and current commit. */
export function submitGoal({project,stateDir,text,repoFullName=null,maxRounds=3,deadlineMinutes=24*60}){
 const repo=repoFullName??repositoryOf(project);
 if(!REPO.test(repo))throw Error('repoFullName must be owner/name');
 return startTask({project,stateDir,goal:{text},repoFullName:repo,maxRounds,deadlineMinutes});
}

/**
 * Owner repair order -> durable CCC1 task. The owner grants exactly node-repair:<chain>; the failure is found by
 * running the fixed chain in an isolated worktree, never guessed. CPU budget covers baseline, candidates and the
 * independent second run of a real suite.
 */
export function submitRepair({project,stateDir,chain,repoFullName=null,maxRounds=2,deadlineMinutes=24*60}){
 if(!REPAIR_CHAINS.includes(chain))throw Error('chain must be one of '+REPAIR_CHAINS.join(', '));
 const repo=repoFullName??repositoryOf(project);
 if(!REPO.test(repo))throw Error('repoFullName must be owner/name');
 return startTask({project,stateDir,goal:{text:`Repariere die fehlschlagenden Tests der Testkette ${chain}.`,repair:{chain}},
  repoFullName:repo,permissions:[REPAIR_CAPABILITY+':'+chain],maxRounds,deadlineMinutes,maxCpuMillis:3_600_000});
}

/**
 * Task compiler for a repair order: the read-only CCC1 preflight (isolated non-production branch, clean checkout,
 * state outside the source, required modules, Node version) and the fixed plan. Nothing runs and nothing is claimed.
 */
export function compileRepair({project,stateDir,chain}){
 const out={schema:'continuum-ccc1-compiled-goal',version:1,kind:'REPAIR',chain:REPAIR_CHAINS.includes(chain)?chain:null,
  status:null,question:null,preflight:null,plan:[]};
 if(!out.chain)return {...out,status:'NEEDS_OWNER',question:'Welche Testkette soll repariert werden? ('+REPAIR_CHAINS.join(', ')+')'};
 const pf=assessCcc1Preflight({project,stateDir});
 out.preflight={result:pf.result,sourceCommit:pf.sourceCommit,checkedBranch:pf.checkedBranch,findings:[...pf.findings]};
 if(pf.result!=='READY_TO_REQUEST_OWNER_REVIEW')return {...out,status:'BLOCKED',question:('Vorprüfung blockiert: '+pf.findings.join(', ')).slice(0,600)};
 return {...out,status:'READY',plan:['Testkette '+chain+' am Commit '+pf.sourceCommit.slice(0,12)+' im isolierten Worktree ausführen',
  'bei echtem Fehler: deterministische Reparaturkandidaten der bestehenden Pipeline prüfen',
  'Patch in zweitem unabhängigem Worktree wiederholen','Quittung durch den CCC1-Verifier prüfen','Patch nur zur Prüfung anbieten; kein Merge, keine Installation']};
}

/**
 * Task compiler: goal text -> typed GoalSpec plus a dry run of the reasoner's first step (sandboxed probe and
 * ambiguity check), without a task, a journal or a coding job. Advisory only: READY, AMBIGUOUS (with the owner question),
 * NO_MATCH (no checkable function meets every example) or NEEDS_OWNER (no usable examples); confirmation stays with
 * the contract test of a real task.
 */
export async function compileGoal({project,text}){
 const spec=interpretGoal({text});
 const out={schema:'continuum-ccc1-compiled-goal',version:1,status:null,arity:spec.arity,examples:spec.acceptance,
  question:null,matching:[],unchecked:[]};
 if(spec.abstain)return {...out,status:'NEEDS_OWNER',question:spec.reason};
 const core=new DisambiguatingProbeCore({root:project});
 const ranked=await new Ccc1Reasoner({candidates:indexExports(project),languageCore:core}).ranked(spec);
 const question=await core.clarify(spec,ranked.map(r=>r.c));
 out.matching=core.lastAmbiguity?.full??[];
 out.unchecked=core.lastAmbiguity?.unchecked??[];
 if(question)return {...out,status:'AMBIGUOUS',question};
 if(!out.matching.length)return {...out,status:'NO_MATCH',question:'Keine prüfbare Funktion erfüllt alle Beispiele.'};
 return {...out,status:'READY'};
}

/**
 * Owner cancel of a CCC1 task (Android "Abbrechen"). First the pending job's backlog task is aborted, so a worker that
 * has not claimed it never starts it and a running repair cannot complete it (its receipt becomes CANCELLED, which the
 * verifier never confirms). Then the owner ABORT is journaled. While a worker holds the writer lock the ABORT cannot be
 * written: the answer is WRITER_ACTIVE and the owner sends it again after the run; it is never reported as done.
 * An ended task is answered with its final state (re-running a finished order changes nothing).
 */
export function abortTask({project,stateDir,taskId,reason='Besitzer hat den Auftrag abgebrochen'}){
 const file=path.join(stateDir,'tasks',taskId+'.jsonl');
 if(!fs.existsSync(file))throw Error('CCC1_NO_SUCH_TASK: '+taskId);
 const read=()=>taskState(new DurableTaskJournal(file,runtimeKey(stateDir)).entries());
 const before=read();
 if(before.terminal)return {taskId,journal:'ALREADY_ENDED',state:before.state,job:null};
 let job=null;
 if(before.pending){
  const id=jobIdFor(taskId,before.pending.messageId);
  job={job:id,status:cancelMobileCodingJob({project,teamleaderState:teamleaderStateOf(stateDir),job:id,reason}).status};
 }
 try{ownerAction({stateDir,taskId,kind:'ABORT',payload:{reason,code:'OWNER'}});}
 catch(e){
  if(/another writer is active/.test(e.message))return {taskId,journal:'WRITER_ACTIVE',state:read().state,job};
  if(/already ended/.test(e.message))return {taskId,journal:'ALREADY_ENDED',state:read().state,job};
  throw e;
 }
 return {taskId,journal:'ABORTED',state:read().state,job};
}

const ORDER=/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
function writeAtomic(file,text){
 const tmp=file+'.'+process.pid+'.tmp';
 fs.writeFileSync(tmp,text,{mode:0o600});fs.renameSync(tmp,file);
}

/**
 * Idempotent owner submit. `order` is the Android request id: the first submit creates the task and every later one
 * with the same id answers with that task - also after it has ended - and never creates or runs a second one (a lost
 * return channel is retried safely). An open task with the same goal at the same commit is reused as well (a double
 * tap with a new id). A reservation without a task (the process died between the two writes) is reported, never
 * resolved silently. Returns {taskId, duplicate}.
 */
export function submitOnce({project,stateDir,order=null,chain=null,text=null,repoFullName=null,sourceCommit}){
 if(order!==null&&!ORDER.test(order))throw Error('CCC1_ORDER_INVALID: order must be a lowercase UUIDv4');
 if((chain===null)===(text===null))throw Error('exactly one of chain, text');
 const want=chain!==null?{repair:chain}:{text};
 const file=order&&path.join(stateDir,'orders',order+'.json');
 if(file&&fs.existsSync(file)){
  const known=JSON.parse(fs.readFileSync(file,'utf8'));
  if(JSON.stringify(known.goal)!==JSON.stringify(want))throw Error('CCC1_ORDER_REUSED: the order id belongs to another goal');
  if(!known.taskId)throw Error('CCC1_ORDER_UNRESOLVED: an earlier submit of this order was interrupted; check the task list');
  return {taskId:known.taskId,duplicate:true};
 }
 const same=t=>!t.terminal&&t.state!=='CORRUPT'&&t.baseCommitSha===sourceCommit&&(chain!==null?t.repair===chain:t.repair===null&&t.goal===text);
 const open=listTasks(stateDir).find(same);
 if(file){
  fs.mkdirSync(path.dirname(file),{recursive:true,mode:0o700});
  fs.writeFileSync(file,JSON.stringify({goal:want,taskId:null})+'\n',{flag:'wx',mode:0o600});   // reservation: one winner
 }
 const taskId=open?.taskId??(chain!==null?submitRepair({project,stateDir,chain,repoFullName}):submitGoal({project,stateDir,text,repoFullName}));
 if(file)writeAtomic(file,JSON.stringify({goal:want,taskId})+'\n');
 return {taskId,duplicate:Boolean(open)};
}

/**
 * What the owner reviews for a completed repair task: the patch the verifier confirmed, hashed again from disk now (a
 * changed or missing file is reported as not intact and never shown), with strategy and files from the job receipt and
 * a bounded text preview. Read-only: nothing is applied, merged or installed. null for every other task.
 */
export function patchForReview({stateDir,taskId,previewBytes=4000}){
 const entries=new DurableTaskJournal(path.join(stateDir,'tasks',taskId+'.jsonl'),runtimeKey(stateDir)).entries();
 const critique=entries.find(e=>e.kind==='CRITIQUE'&&e.payload.verdict==='CONFIRMED');
 const receipt=critique&&entries.find(e=>e.messageId===critique.payload.receipt&&e.kind==='RECEIPT');
 if(!receipt||!entries[0].payload.repair)return null;
 const stored=readReceipt(builderStateOf(stateDir),receipt.payload.job);
 const review={patchSha256:receipt.payload.patchSha256,path:stored?.patch?.path??null,bytes:null,intact:false,
  strategy:stored?.repair?.strategy??null,files:stored?.repair?.files??[],preview:null};
 try{
  const bytes=fs.readFileSync(review.path);
  review.bytes=bytes.length;
  review.intact=stored.patch.sha256===review.patchSha256&&createHash('sha256').update(bytes).digest('hex')===review.patchSha256;
  if(review.intact&&previewBytes>0)review.preview=bytes.subarray(0,previewBytes).toString('utf8');
 }catch{}
 return review;
}

/** All tasks of a state directory with their state, oldest first; damaged journals are listed, never repaired. */
export function listTasks(stateDir){
 const dir=path.join(stateDir,'tasks');
 if(!fs.existsSync(dir))return [];
 const key=runtimeKey(stateDir);
 return fs.readdirSync(dir).filter(f=>f.endsWith('.jsonl')).map(f=>{
  const taskId=f.slice(0,-6);
  try{
   const entries=new DurableTaskJournal(path.join(dir,f),key).entries();
   const s=taskState(entries);
   const goal=entries.find(e=>e.kind==='GOAL');
   return {taskId,state:s.state,terminal:s.terminal,waitingForOwner:Boolean(s.approval?.requested&&s.approval.decision===null),
    createdAt:entries[0]?.createdAt??null,goal:goal?.payload.text??'',repair:goal?.payload.repair?.chain??null,
    baseCommitSha:entries[0]?.baseCommitSha??null};
  }catch(e){
   if(e instanceof JournalCorruption)return {taskId,state:'CORRUPT',terminal:false,waitingForOwner:false,createdAt:null,goal:'',
    repair:null,baseCommitSha:null};
   throw e;
  }
 }).sort((a,b)=>String(a.createdAt).localeCompare(String(b.createdAt))||a.taskId.localeCompare(b.taskId));
}

/** One bounded cycle: drive one task, record finished ones, answer in the loop's vocabulary. */
export async function runCcc1Cycle({project,stateDir,core='disambiguate',maxTurns=24,signal=null,indexOnIdle=false}){
 if(!CORES[core])throw Error('unknown language core '+core);
 const log=new ExperienceLog(path.join(stateDir,'experience.jsonl'));
 const record=(taskId,coreId,unchecked=[])=>{
  const entries=new DurableTaskJournal(path.join(stateDir,'tasks',taskId+'.jsonl'),runtimeKey(stateDir)).entries();
  const r=experienceOf(taskId,entries,{coreId,unchecked});
  return r?log.append(r):false;
 };
 const tasks=listTasks(stateDir);
 for(const t of tasks.filter(t=>t.terminal))record(t.taskId,null);   // finished elsewhere (e.g. by the owner): record once
 const open=tasks.filter(t=>!t.terminal&&t.state!=='CORRUPT');
 const next=open.find(t=>!t.waitingForOwner);
 if(!next){
  if(tasks.some(t=>t.state==='CORRUPT'))return {outcome:'ERROR',detail:'CCC1: damaged task journal; inspect manually'};
  if(open.length)return {outcome:'NO_READY_INVESTIGATION',detail:`CCC1: ${open.length} task(s) wait for the owner`};
  if(!indexOnIdle||signal?.aborted)return {outcome:'IDLE',detail:'CCC1: no open task'};
  const index=buildCodeIndex({root:project,cacheFile:path.join(stateDir,'code-index-cache.json')});
  saveCodeIndex(stateDir,index);
  const s=index.stats;
  return {outcome:'IDLE',detail:`CCC1: no open task; code index ${s.exports} exports (${s.runnable} runnable), `+
   `${s.modulesProbed} module(s) probed, ${s.clusters} cluster(s), ${s.nearDuplicates} near duplicate(s)`};
 }
 if(signal?.aborted)return {outcome:'INTERRUPTED',detail:'CCC1: cancelled before the task ran'};
 const languageCore=CORES[core](project);
 const reasoner=new Ccc1Reasoner({candidates:indexExports(project),languageCore,budgetMinutes:3});
 const r=await runTask({project,stateDir,taskId:next.taskId,reasoner,maxTurns});
 const outcome=OUTCOME_FOR[r.outcome]??'ERROR';
 if(['COMPLETED','ABSTAINED','FAILED','ABORTED'].includes(r.outcome))record(next.taskId,languageCore.id,languageCore.lastAmbiguity?.unchecked??[]);
 return {outcome,ccc1:r.outcome,taskId:next.taskId,detail:`CCC1 ${r.outcome} task ${next.taskId.slice(0,12)}`+(r.reason?': '+r.reason:'')};
}
