import fs from 'node:fs';
import {mergeExperiences} from './repair-experience.mjs';

/**
 * Persistent repair ledger: every attempt with diagnosis, strategy, minimal
 * hunks, test outcome, base commit, patch hash and confidence. Aggregated
 * strategy statistics reuse repair-experience.mergeExperiences, keyed by
 * "<diagnostic kind>|<strategy>", so the agent prefers what worked for this
 * kind of problem and never re-runs a candidate already proven to fail.
 */
export const MAX_ATTEMPTS=400;
const OUTCOMES=new Set(['VERIFIED','FAILED_TESTS','INSUFFICIENT_EVIDENCE','REJECTED_BY_CRITIC','NOT_APPLICABLE']);
const HEX=/^[0-9a-f]{1,64}$/;

export function emptyLedger(){return {schemaVersion:2,attempts:[],experiences:[],sessions:[]};}

export function verifyLedger(data){
  if(!data||data.schemaVersion!==2||!Array.isArray(data.attempts)||!Array.isArray(data.experiences)||!Array.isArray(data.sessions))
    throw Error('Invalid repair ledger');
  if(data.attempts.length>MAX_ATTEMPTS||data.sessions.length>200||data.experiences.length>100)throw Error('Repair ledger too large');
  for(const a of data.attempts){
    if(!a||!OUTCOMES.has(a.outcome)||typeof a.strategy!=='string'||a.strategy.length>80||!HEX.test(a.diagnostic??'')||!HEX.test(a.candidate??''))
      throw Error('Invalid ledger attempt');
    if(!Array.isArray(a.edits)||a.edits.length>3||a.edits.some(e=>typeof e.file!=='string'||e.file.includes('..')||typeof e.oldText!=='string'||typeof e.newText!=='string'||e.oldText.length+e.newText.length>20000))
      throw Error('Invalid ledger edits');
  }
  for(const x of data.experiences)if(!x||typeof x.kind!=='string'||x.kind.length>80||!Number.isSafeInteger(x.successes)||!Number.isSafeInteger(x.failures)||x.successes<0||x.failures<0)
    throw Error('Invalid ledger experience');
  return data;
}

export function loadLedger(file){
  if(!file||!fs.existsSync(file))return emptyLedger();
  const raw=fs.readFileSync(file,'utf8');
  if(raw.length>4_000_000)throw Error('Repair ledger too large');
  return verifyLedger(JSON.parse(raw));
}

export function saveLedger(file,ledger){
  verifyLedger(ledger);
  const tmp=file+'.tmp-'+process.pid;
  fs.writeFileSync(tmp,JSON.stringify(ledger,null,1)+'\n');
  fs.renameSync(tmp,file);
}

/** Fold one session report into the ledger (returns a new object). */
export function recordSession(ledger,report,meta={}){
  const next=structuredClone(ledger);
  // Internal success is a PROPOSAL, never authorization to write a
  // positive experience. Only the trusted worker's independent second
  // worktree test can set independentPass===true.
  //
  // false: independently rejected -> negative evidence.
  // absent: no independent observation -> neither positive NOR negative
  //         training label (do not poison learning with an unknown outcome).
  const provisional=report.outcome==='VERIFIED';
  const independentRejected=provisional&&meta.independentPass===false;
  const independentPending=provisional&&meta.independentPass!==true&&
    meta.independentPass!==false;
  const acceptedPatch=provisional&&meta.independentPass===true?report.verified:null;
  const finalOutcome=independentRejected?'NO_VERIFIED_REPAIR':
    independentPending?'PENDING_INDEPENDENT_VERIFICATION':report.outcome;
  const attempts=(report.attempts??[]).filter(a=>OUTCOMES.has(a.outcome)).map(a=>{
    if(a.outcome==='VERIFIED'&&independentRejected)
      return {...a,outcome:'INSUFFICIENT_EVIDENCE'};
    if(a.outcome==='VERIFIED'&&independentPending)
      return {...a,outcome:'NOT_APPLICABLE'};
    return a;
  }).map(a=>({
    at:report.finishedAt??new Date().toISOString(),baseCommit:report.base,chain:report.chain,
    diagnostic:a.diagnostic,diagnosticKind:a.diagnosticKind,candidate:a.candidate,strategy:a.strategy,
    outcome:a.outcome,edits:a.edits,confidence:a.score??null,
    tests:(a.tests??[]).map(t=>({profile:t.profile,passed:t.passed,total:t.tests?.total??0,failed:t.tests?.failed??0,
      executed:t.tests?.executed??0,skipped:t.tests?.skipped??0})),
    patchSha256:a.outcome==='VERIFIED'?acceptedPatch?.patchSha256??null:null,
    proposalCommit:a.outcome==='VERIFIED'?meta.proposalCommit??null:null,
    proposalBranch:a.outcome==='VERIFIED'?meta.branch??null:null,pullRequest:a.outcome==='VERIFIED'?meta.pullRequest??null:null}));
  next.attempts=[...next.attempts,...attempts].slice(-MAX_ATTEMPTS);
  // Critic rejections say nothing about whether a strategy repairs code.
  // Insufficient evidence counts against a strategy: it "passed" without proof.
  // A provisional successful session has NO independent classification yet.
  // Keep its diagnostic attempts for investigation, but do not train from
  // either an asserted win or its possibly stale failed-test labels.
  const learned=independentPending?[]:attempts.filter(a=>
    ['VERIFIED','FAILED_TESTS','INSUFFICIENT_EVIDENCE'].includes(a.outcome));
  next.experiences=mergeExperiences(next.experiences,learned.map(a=>({
    kind:(a.diagnosticKind+'|'+a.strategy).slice(0,80),
    status:a.outcome==='VERIFIED'?'COMPILES':'REJECTED'
  })));
  next.sessions=[...next.sessions,{at:report.finishedAt??new Date().toISOString(),base:report.base,chain:report.chain,
    outcome:finalOutcome,diagnostics:(report.baseline?.diagnostics??[]).length,attempts:attempts.length,
    verifiedStrategy:acceptedPatch?.strategy??null,patchSha256:acceptedPatch?.patchSha256??null,
    trigger:meta.trigger??null}].slice(-200);
  return verifyLedger(next);
}
