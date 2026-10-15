import {analyzeRepository} from './code-analyzer.mjs';
import {findingTask,sha256Hex,singleLine,LIMITS} from './development-backlog.mjs';

/**
 * Offline backlog planner: turns structural findings into small, reviewable
 * investigation tasks. No AI API, no writes, and no claim that a finding is a bug.
 */
export function prioritizeFindings(report,limit=12) {
  if(!report||!Array.isArray(report.findings))throw Error('Invalid analysis report');
  const priorities={DEAD_CLUSTER:3,TEST_ONLY:2,UNREFERENCED:1};
  return report.findings.filter(f=>f&&typeof f.kind==='string')
    .map(f=>({kind:f.kind,symbol:String(f.symbol??f.name??'').slice(0,120),
      file:String(f.file??f.definedIn?.[0]??'').slice(0,250),
      confidence:f.confidence??'unknown',
      score:priorities[f.kind]??0,
      action:'INVESTIGATE_ONLY'}))
    .filter(f=>f.score>0)
    .sort((a,b)=>b.score-a.score||a.file.localeCompare(b.file)||a.symbol.localeCompare(b.symbol))
    .slice(0,Math.max(0,Math.min(30,limit)));
}
export function planBacklog(root,limit=12){
  const {index,...result}=analyzeRepository(root);
  return {schemaVersion:1,mode:'review-required',analyzedAt:new Date().toISOString(),
    tasks:prioritizeFindings(result,limit)};
}

// ---------------------------------------------------------------------------
// Persistent backlog (development-backlog.mjs): planner findings and repair
// sessions become operations with the shared rules; nothing here writes.
// ---------------------------------------------------------------------------

const PLANNED_KINDS=new Set(['DEAD_CLUSTER','TEST_ONLY','UNREFERENCED']);

/**
 * Operations that bring the backlog in line with an analysis report: new
 * findings are proposed (at most `limit` per run, highest priority first),
 * proposals of unchanged findings get their current priority, and proposals
 * whose finding disappeared are aborted with a reason. Accepted or rejected
 * tasks are never touched: that is a human decision.
 */
export function syncBacklogOps(doc,report,{at,actor='code-analyzer',limit=12}){
  if(!report||!Array.isArray(report.findings))throw Error('Invalid analysis report');
  const byId=new Map();
  for(const f of report.findings){
    if(!f||!PLANNED_KINDS.has(f.kind))continue;
    const task=findingTask({kind:f.kind,symbol:String(f.symbol??f.name??''),file:String(f.file??f.definedIn?.[0]??''),confidence:f.confidence});
    if(!byId.has(task.id))byId.set(task.id,task);
  }
  const known=new Map(doc.tasks.map(t=>[t.id,t]));
  const ops=[];let proposed=0;
  for(const task of [...byId.values()].sort((a,b)=>b.priority-a.priority||(a.id<b.id?-1:1))){
    const existing=known.get(task.id);
    if(!existing){
      if(proposed>=limit)continue;
      ops.push({op:'propose',actor,at,ifAbsent:true,task,reason:`finding ${task.subject.finding} (${task.subject.confidence} confidence)`});
      proposed++;
    }else if(existing.state==='PROPOSED'&&existing.priority!==task.priority){
      ops.push({op:'setPriority',actor,at,id:task.id,priority:task.priority});
    }
  }
  for(const t of doc.tasks)
    if(t.source==='code-analyzer'&&t.kind==='INVESTIGATE'&&t.state==='PROPOSED'&&!byId.has(t.id))
      ops.push({op:'abort',actor,at,id:t.id,reason:'finding no longer reported by code-analyzer'});
  return ops;
}

/** Stable task identity of a CI failure: the chain plus the set of diagnostics. */
export function repairTaskId(report){
  const prints=[...new Set((report?.baseline?.diagnostics??[]).filter(d=>d.file&&d.fingerprint).map(d=>d.fingerprint))].sort();
  const key=prints.length?report.chain+'\u0000'+prints.join(','):report.chain+'\u0000base\u0000'+report.base;
  return 'rep-'+sha256Hex(key).slice(0,16);
}

/**
 * Operations recording one repair session (repair-loop report) on its REPAIR
 * task: propose+accept (agent-owned CI repairs need no review to be attempted;
 * the result is still only a draft pull request), claim, then either submit +
 * complete with the evidence of the tests that verified the patch, or fail.
 * Every operation carries an opId derived from the session, so recording the
 * same report twice changes nothing. A task that is finished, failed for good,
 * rejected or held by another execution is not touched (`skipped` says why).
 */
export function repairSessionOps(doc,report,{actor='continuum-agent'}={}){
  if(!report||!/^[0-9a-f]{40}$/.test(report.base??''))throw Error('Repair report needs a full base commit');
  if(report.outcome==='NO_FAILURE')return {id:null,ops:[],skipped:'no failure reproduced'};
  const started=Date.parse(report.startedAt),finished=Date.parse(report.finishedAt??report.startedAt);
  if(!Number.isSafeInteger(started)||!Number.isSafeInteger(finished)||finished<started)throw Error('Repair report timestamps invalid');
  const id=repairTaskId(report);
  const session=sha256Hex(['repair-session',report.base,report.chain,report.startedAt,report.finishedAt??''].join('\u0000'));
  const opId=step=>`repair:${session.slice(0,24)}:${step}`;
  const token=session.slice(0,32);
  const first=(report.baseline?.diagnostics??[]).find(d=>d.file);
  const subject={base:report.base,chain:String(report.chain)};
  if(first){subject.diagnostic=first.fingerprint;subject.file=singleLine(first.file,LIMITS.subjectValue);if(Number.isSafeInteger(first.line))subject.line=first.line;}
  const title=singleLine(`Repair ${report.chain} failure at ${report.base.slice(0,12)}`+(first?`: ${first.kind} ${first.file}${first.line?':'+first.line:''}`:''),LIMITS.title);
  const leaseMs=Math.min(LIMITS.leaseMax,Math.max(LIMITS.leaseMin,finished-started+60_000));
  const existing=doc.tasks.find(t=>t.id===id);
  const recorded=(doc.recentOpIds??[]).includes(opId('claim'));
  if(existing&&!recorded&&!['PROPOSED','READY'].includes(existing.state))return {id,ops:[],skipped:`task ${id} is ${existing.state}`};
  const ops=[
    {op:'propose',actor,at:started,opId:opId('propose'),ifAbsent:true,reason:'CI failure reproduced by the repair loop',
      task:{id,title,kind:'REPAIR',source:'repair-loop',priority:900,maxAttempts:3,subject}}];
  if(!existing||existing.state==='PROPOSED')
    ops.push({op:'accept',actor,at:started,opId:opId('accept'),id,reason:'CI repairs are attempted automatically; results stay draft pull requests'});
  ops.push({op:'claim',actor,at:started,opId:opId('claim'),id,owner:actor,token,leaseMs});
  const attempt=report.verified&&(report.attempts??[]).find(a=>a.outcome==='VERIFIED'&&a.candidate===report.verified.candidate);
  if(attempt){
    const patch=report.verified.patchSha256;
    ops.push({op:'submit',actor,at:finished,opId:opId('submit'),id,token,artifact:{type:'PATCH',sha256:patch}});
    ops.push({op:'complete',actor,at:finished,opId:opId('complete'),id,evidence:{type:'TEST_RUN',taskId:id,submissionToken:token,artifactSha256:patch,
      chain:String(report.chain),commit:report.base,at:finished,by:actor,
      profiles:(attempt.tests??[]).map(t=>({profile:t.profile,passed:t.passed===true,executed:t.tests?.executed??0,failed:t.tests?.failed??0,skipped:t.tests?.skipped??0})),
      repair:{diagnostic:attempt.diagnostic,candidate:attempt.candidate,strategy:attempt.strategy,patchSha256:patch}}});
  }else{
    const tried=(report.attempts??[]).map(a=>a.strategy+'='+a.outcome).join(', ');
    ops.push({op:'fail',actor,at:finished,opId:opId('fail'),id,token,
      error:{code:/^[A-Z][A-Z0-9_]{1,47}$/.test(report.outcome??'')?report.outcome:'NO_VERIFIED_REPAIR',message:singleLine(tried?'tried '+tried:'no candidate',LIMITS.text)}});
  }
  return {id,ops,skipped:null};
}
