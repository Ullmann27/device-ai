// CCC1 RecoveryManager: what a new process may do after an abrupt stop. It never assumes success and never repeats
// a non-idempotent step on its own. Order (specification section 3): repair a torn journal tail, verify the whole
// journal (chain, MACs, binding), reconcile repository and worktrees, then classify the open job of the pending
// TEST_REQUEST from the coding agent's own state:
//  NOT_STARTED          the job never claimed its backlog task -> may run now
//  FINISHED_UNRECORDED  the job finished (or failed) but its RECEIPT was not journaled -> replay, nothing is executed
//  INTERRUPTED          the job was claimed and is not finished -> no automatic repetition; owner approval needed
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {readReceipt,jobTaskId} from '../../scripts/continuum-mobile-coding.mjs';
import {BacklogStore} from './development-backlog.mjs';
import {JournalCorruption} from './ccc1-journal.mjs';

/** Deterministic UUIDv4-shaped job id of a TEST_REQUEST: a resumed request always maps to the same coding job. */
export function jobIdFor(taskId,requestMessageId){
 const h=createHash('sha256').update('ccc1-job\0'+taskId+'\0'+requestMessageId).digest('hex').split('');
 h[12]='4';h[16]='89ab'[parseInt(h[16],16)&3];
 const s=h.join('');
 return `${s.slice(0,8)}-${s.slice(8,12)}-${s.slice(12,16)}-${s.slice(16,20)}-${s.slice(20,32)}`;
}

export const builderStateOf=stateDir=>path.join(stateDir,'builder');
export const teamleaderStateOf=stateDir=>path.join(stateDir,'teamleader');

/** Status of a coding job from the coding agent's own receipt store and canonical backlog. */
export function jobStatus(stateDir,job){
 const stored=readReceipt(builderStateOf(stateDir),job);
 if(stored&&!stored.status.startsWith('BLOCKED_'))return 'FINISHED_UNRECORDED';
 const file=path.join(teamleaderStateOf(stateDir),'development-backlog.json');
 if(!fs.existsSync(file))return 'NOT_STARTED';
 const task=new BacklogStore(file,{actor:'ccc1-recovery'}).read().doc.tasks.find(t=>t.id===jobTaskId(job));
 if(!task||['PROPOSED','READY'].includes(task.state))return 'NOT_STARTED';
 if(['IN_PROGRESS','VERIFYING'].includes(task.state))return 'INTERRUPTED';
 return 'FINISHED_UNRECORDED';
}

function git(cwd,args){
 const r=spawnSync('git',args,{cwd,encoding:'utf8',timeout:20000,shell:false});
 return {status:r.status,out:(r.stdout??'').trim()};
}

/** Pending TEST_REQUEST: the newest one without a RECEIPT (at most one exists because of backpressure). */
export function pendingRequest(entries){
 const answered=new Set(entries.filter(e=>e.kind==='RECEIPT').map(e=>e.payload.request));
 return entries.filter(e=>e.kind==='TEST_REQUEST'&&!answered.has(e.messageId)).at(-1)??null;
}

/**
 * Recovery report of one task. Mutates only what is provably safe: truncates a torn last journal line and prunes
 * git worktree records whose directories no longer exist. Everything else is reported.
 */
export function recover({project,stateDir,journal}){
 const report={tornTail:null,corruption:null,headMatches:null,cleanCheckout:null,prunedWorktrees:0,extraWorktrees:[],
  pending:null,lastTrustedReceipt:null,allowedNext:null};
 report.tornTail=journal.truncateTornTail();
 let entries;
 try{entries=journal.entries();}catch(e){
  if(e instanceof JournalCorruption){report.corruption=e.message;report.allowedNext='STOP_CORRUPT_JOURNAL';return {report,entries:null};}
  throw e;
 }
 if(!entries.length){report.allowedNext='START';return {report,entries};}
 const goal=entries[0];
 const head=git(project,['rev-parse','HEAD']);
 report.headMatches=head.status===0&&head.out===goal.baseCommitSha;
 report.cleanCheckout=git(project,['status','--porcelain']).out==='';
 const before=git(project,['worktree','list','--porcelain']).out.split('\n').filter(l=>l.startsWith('worktree ')).length;
 git(project,['worktree','prune']);
 const after=git(project,['worktree','list','--porcelain']).out.split('\n').filter(l=>l.startsWith('worktree ')).map(l=>l.slice(9));
 report.prunedWorktrees=Math.max(0,before-after.length);
 const root=fs.realpathSync(project);
 report.extraWorktrees=after.filter(w=>{try{return fs.realpathSync(w)!==root;}catch{return true;}});
 report.lastTrustedReceipt=entries.filter(e=>e.kind==='RECEIPT').at(-1)?.messageId??null;
 const pending=pendingRequest(entries);
 if(pending){
  const job=jobIdFor(goal.taskId,pending.messageId);
  report.pending={request:pending.messageId,job,status:jobStatus(stateDir,job)};
 }
 report.allowedNext=!report.headMatches?'ABORT_STALE_BASE':
  report.pending?.status==='INTERRUPTED'?'REQUIRE_OWNER_APPROVAL':
  report.pending?.status==='FINISHED_UNRECORDED'?'RECORD_RECEIPT_BY_REPLAY':
  report.pending?'EXECUTE_PENDING':'ASK_REASONER';
 return {report,entries};
}
