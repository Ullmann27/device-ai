import fs from 'node:fs';
import path from 'node:path';
import {randomBytes} from 'node:crypto';
import {mergeExperiences,repairExperienceKey} from './repair-experience.mjs';
import {sha256} from './repair-diagnostics.mjs';

/**
 * Persistent repair ledger: every attempt with diagnosis, strategy, minimal
 * hunks, test outcome, base commit, patch hash and confidence. Aggregated
 * strategy statistics reuse repair-experience.mergeExperiences, keyed by
 * "<diagnostic kind>|<strategy>", so the agent prefers what worked for this
 * kind of problem and never re-runs a candidate already proven to fail.
 */
export const MAX_ATTEMPTS=400;
const OUTCOMES=new Set(['VERIFIED','FAILED_TESTS','INSUFFICIENT_EVIDENCE','REJECTED_BY_CRITIC','NOT_APPLICABLE',
  'FAILED_SYNTAX','SYNTAX_CHECK_ERROR']);
const HEX=/^[0-9a-f]{1,64}$/;
const SHA256=/^[0-9a-f]{64}$/;
export const MAX_LEDGER_EDIT_CHARS=20000;

/**
 * An edit too large to keep (e.g. a whole-file revert of a big Kotlin file) is recorded by file, digests and lengths
 * only. Storing it verbatim would exceed the ledger limits and fail the whole session record; without its text it can
 * never be replayed from the ledger.
 */
function ledgerEdit(e){
  if(e.oldText.length+e.newText.length<=MAX_LEDGER_EDIT_CHARS)return e;
  return {file:e.file,oldText:'',newText:'',line:e.line??null,omitted:{oldSha256:sha256(e.oldText),
    newSha256:sha256(e.newText),oldLength:e.oldText.length,newLength:e.newText.length}};
}
const validOmission=e=>e.oldText===''&&e.newText===''&&SHA256.test(e.omitted?.oldSha256??'')&&
  SHA256.test(e.omitted.newSha256??'')&&Number.isSafeInteger(e.omitted.oldLength)&&Number.isSafeInteger(e.omitted.newLength)&&
  e.omitted.oldLength>=0&&e.omitted.newLength>=0&&e.omitted.oldLength+e.omitted.newLength>MAX_LEDGER_EDIT_CHARS;

export function emptyLedger(){return {schemaVersion:2,attempts:[],experiences:[],sessions:[]};}

export function verifyLedger(data){
  if(!data||data.schemaVersion!==2||!Array.isArray(data.attempts)||!Array.isArray(data.experiences)||!Array.isArray(data.sessions))
    throw Error('Invalid repair ledger');
  if(data.attempts.length>MAX_ATTEMPTS||data.sessions.length>200||data.experiences.length>100)throw Error('Repair ledger too large');
  for(const a of data.attempts){
    if(!a||!OUTCOMES.has(a.outcome)||typeof a.strategy!=='string'||a.strategy.length>80||!HEX.test(a.diagnostic??'')||!HEX.test(a.candidate??''))
      throw Error('Invalid ledger attempt');
    if(!Array.isArray(a.edits)||a.edits.length>3||a.edits.some(e=>typeof e.file!=='string'||e.file.includes('..')||typeof e.oldText!=='string'||typeof e.newText!=='string'||e.oldText.length+e.newText.length>MAX_LEDGER_EDIT_CHARS||
       e.omitted!==undefined&&!validOmission(e)))
      throw Error('Invalid ledger edits');
    if(a.syntaxReason!==undefined&&a.syntaxReason!==null&&
       (typeof a.syntaxReason!=='string'||!/^[A-Z][A-Z0-9_]{0,79}$/.test(a.syntaxReason)))
      throw Error('Invalid ledger syntax reason');
    if(a.taskFingerprint!==undefined&&!SHA256.test(a.taskFingerprint))throw Error('Invalid ledger task fingerprint');
    if(a.contextKey!==undefined&&!/^node\.js\.(?:call|value)$/.test(a.contextKey))throw Error('Invalid repair context');
  }
  for(const x of data.experiences)if(!x||typeof x.kind!=='string'||x.kind.length>80||!Number.isSafeInteger(x.successes)||!Number.isSafeInteger(x.failures)||x.successes<0||x.failures<0)
    throw Error('Invalid ledger experience');
  return data;
}

export function loadLedger(file){
  if(!file)return emptyLedger();
  let fd;
  try{fd=fs.openSync(file,fs.constants.O_RDONLY|fs.constants.O_NOFOLLOW|fs.constants.O_NONBLOCK);}
  catch(error){if(error.code==='ENOENT'){
    // A dangling link is an invalid checkpoint, never an empty learning history.
    try{fs.lstatSync(file);}catch(missing){if(missing.code==='ENOENT')return emptyLedger();throw missing;}
  }throw error;}
  try{
    const stat=fs.fstatSync(fd);
    if(!stat.isFile()||stat.size>4_000_000)throw Error('Invalid repair ledger file');
    const raw=fs.readFileSync(fd);
    if(raw.length>4_000_000)throw Error('Repair ledger too large');
    return verifyLedger(JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(raw)));
  }finally{fs.closeSync(fd);}
}

export function saveLedger(file,ledger){
  verifyLedger(ledger);
  const bytes=Buffer.from(JSON.stringify(ledger,null,1)+'\n');
  if(bytes.length>4_000_000)throw Error('Repair ledger too large');
  try{if(!fs.lstatSync(file).isFile())throw Error('Invalid repair ledger file');}
  catch(error){if(error.code!=='ENOENT')throw error;}
  const tmp=file+'.tmp-'+process.pid+'-'+randomBytes(8).toString('hex');
  let fd;
  try{
    fd=fs.openSync(tmp,'wx',0o600);fs.writeFileSync(fd,bytes);fs.fsyncSync(fd);
    fs.closeSync(fd);fd=undefined;
    fs.renameSync(tmp,file);
    const directory=fs.openSync(path.dirname(path.resolve(file)),fs.constants.O_RDONLY);
    try{fs.fsyncSync(directory);}finally{fs.closeSync(directory);}
  }finally{if(fd!==undefined)fs.closeSync(fd);fs.rmSync(tmp,{force:true});}
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
  // OS confinement protects owner state, but candidate-controlled runtime
  // reports are not an independent semantic oracle. Never train/replay broad
  // model code from a green local report, even if a caller passes a true flag.
  const isolatedReview=provisional&&['LOCAL_ISOLATED_PATCH','LOCAL_SCOPED_PATCH'].includes(report.verified?.strategy);
  const independentRejected=provisional&&meta.independentPass===false;
  // Bind the independent verifier's green verdict to exactly the winning
  // candidate and a digest. An unrelated VERIFIED trial is never a win.
  const winningAttempt=provisional&&(report.attempts??[]).some(a=>
    a.outcome==='VERIFIED'&&a.candidate===report.verified?.candidate&&
    a.strategy===report.verified?.strategy);
  const independentlyVerified=meta.independentPass===true&&winningAttempt&&
    SHA256.test(report.verified?.patchSha256??'');
  const independentPending=provisional&&!independentRejected&&
    (!independentlyVerified||isolatedReview);
  const acceptedPatch=provisional&&independentlyVerified&&!isolatedReview?report.verified:null;
  const finalOutcome=isolatedReview?'PENDING_OWNER_REVIEW':independentRejected?'NO_VERIFIED_REPAIR':
    independentPending?'PENDING_INDEPENDENT_VERIFICATION':report.outcome;
  const attempts=(report.attempts??[]).filter(a=>OUTCOMES.has(a.outcome)).map(a=>{
    if(a.outcome!=='VERIFIED')return a;
    if(independentRejected)return {...a,outcome:'INSUFFICIENT_EVIDENCE'};
    if(!acceptedPatch||a.candidate!==acceptedPatch.candidate||
       a.strategy!==acceptedPatch.strategy)return {...a,outcome:'NOT_APPLICABLE'};
    return a;
  }).map(a=>({
    at:report.finishedAt??new Date().toISOString(),baseCommit:report.base,chain:report.chain,
    ...(a.strategy==='LOCAL_SCOPED_PATCH'&&SHA256.test(report.coding?.taskFingerprint??'')?
      {taskFingerprint:report.coding.taskFingerprint}:{}),
    diagnostic:a.diagnostic,diagnosticKind:a.diagnosticKind,candidate:a.candidate,strategy:a.strategy,
    ...(a.contextKey!==undefined?{contextKey:a.contextKey}:{}),
    outcome:a.outcome,edits:(a.edits??[]).map(ledgerEdit),confidence:a.score??null,
    syntaxReason:a.syntax?.reason??a.syntaxReason??null,
    tests:(a.tests??[]).map(t=>({profile:t.profile,passed:t.passed,total:t.tests?.total??0,failed:t.tests?.failed??0,
      executed:t.tests?.executed??0,skipped:t.tests?.skipped??0,
      ...(t.tests?.evidenceComplete!==undefined?{evidenceComplete:t.tests.evidenceComplete}:{}),
      ...(t.signal!==undefined?{signal:t.signal}:{}),...(t.error!==undefined?{error:t.error}:{}),
      ...(t.exitCode!==undefined?{exitCode:t.exitCode}:{}),...(t.timedOut!==undefined?{timedOut:t.timedOut}:{})})),
    patchSha256:a.outcome==='VERIFIED'?acceptedPatch?.patchSha256??null:null,
    proposalCommit:a.outcome==='VERIFIED'?meta.proposalCommit??null:null,
    proposalBranch:a.outcome==='VERIFIED'?meta.branch??null:null,pullRequest:a.outcome==='VERIFIED'?meta.pullRequest??null:null}));
  next.attempts=[...next.attempts,...attempts].slice(-MAX_ATTEMPTS);
  // Critic rejections and checker errors say nothing about code correctness.
  // A parser rejection is negative evidence only if syntax was actually invalid.
  // Insufficient evidence counts against a strategy: it "passed" without proof.
  // A provisional successful session has NO independent classification yet.
  // Keep its diagnostic attempts for investigation, but do not train from
  // either an asserted win or its possibly stale failed-test labels.
  // A failed process or runner with zero executed failed tests is an
  // infrastructure incident, not a strategy-quality observation. Preserve its
  // attempt for audit, but never turn it into a negative learning signal.
  const hasExecutedFailedTests=a=>(a.tests??[]).some(t=>{
    if(t.passed!==false||t.evidenceComplete===false||t.signal||t.error||t.timedOut||
       t.exitCode!==undefined&&![0,1].includes(t.exitCode))return false;
    const total=t.total,executed=t.executed,failed=t.failed,skipped=t.skipped;
    return [total,executed,failed,skipped].every(Number.isSafeInteger)&&
      executed>0&&failed>0&&failed<=executed&&
      total>=executed&&total<=1_000_000&&
      skipped>=0&&skipped<=total-executed;
  });
  const learned=independentPending?[]:attempts.filter(a=>!['LOCAL_ISOLATED_PATCH','LOCAL_SCOPED_PATCH'].includes(a.strategy)&&(
    a.outcome==='VERIFIED'||a.outcome==='INSUFFICIENT_EVIDENCE'||
    (a.outcome==='FAILED_TESTS'&&hasExecutedFailedTests(a))||
    (a.outcome==='FAILED_SYNTAX'&&a.syntaxReason==='INVALID_JS_SYNTAX')));
  next.experiences=mergeExperiences(next.experiences,learned.map(a=>({
    kind:repairExperienceKey(a.diagnosticKind,a.strategy,a.contextKey??null),
    status:a.outcome==='VERIFIED'?'COMPILES':'REJECTED'
  })));
  next.sessions=[...next.sessions,{at:report.finishedAt??new Date().toISOString(),base:report.base,chain:report.chain,
    outcome:finalOutcome,diagnostics:(report.baseline?.diagnostics??[]).length,attempts:attempts.length,
    verifiedStrategy:acceptedPatch?.strategy??null,patchSha256:acceptedPatch?.patchSha256??null,
    trigger:meta.trigger??null}].slice(-200);
  return verifyLedger(next);
}
