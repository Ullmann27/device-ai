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
import {startTask,runTask,taskState} from './ccc1-orchestrator.mjs';
import {Ccc1Reasoner,indexExports,interpretGoal,LexicalLanguageCore} from './ccc1-interpreter.mjs';
import {DisambiguatingProbeCore,ProbeLanguageCore} from './behaviour-probe.mjs';
import {DurableTaskJournal,JournalCorruption,runtimeKey} from './ccc1-journal.mjs';
import {ExperienceLog,experienceOf} from './ccc1-experience.mjs';
import {buildCodeIndex,saveCodeIndex} from './code-index.mjs';

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
   return {taskId,state:s.state,terminal:s.terminal,waitingForOwner:Boolean(s.approval?.requested&&s.approval.decision===null),
    createdAt:entries[0]?.createdAt??null,goal:entries.find(e=>e.kind==='GOAL')?.payload.text??''};
  }catch(e){
   if(e instanceof JournalCorruption)return {taskId,state:'CORRUPT',terminal:false,waitingForOwner:false,createdAt:null,goal:''};
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
