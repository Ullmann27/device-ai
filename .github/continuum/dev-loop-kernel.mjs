import crypto from 'node:crypto';
import {rankCandidates,repairCandidateRiskTier} from './repair-loop.mjs';

/**
 * ARGON4 F3 · the core of a closed, measurable development loop (research prototype, pure functions, no I/O).
 *
 * It does not replace anything: the local worker still detects red tests, CCC1's repair-loop still proposes and
 * verifies patches, repair-ledger.mjs still records attempts. This kernel connects them with three missing pieces:
 *
 *  1. CYCLE STATE MACHINE with a hash-chained journal: every step needs evidence bound to the same base commit and
 *     patch digest; the agent can never target main, widen its capabilities, merge, or call its own test "independent".
 *  2. EXPERIMENT REGISTRY: a change to the agent's own strategy is a preregistered experiment with a sealed holdout,
 *     a frozen control policy, one TEST run and an exact paired test; negative results are kept.
 *  3. STRATEGY POLICY: Thompson sampling over Beta posteriors per "<diagnostic kind>|<strategy>", ALWAYS inside the
 *     existing non-destructive risk tiers, compared against the frozen Laplace ranking (repair-loop.rankCandidates).
 */

export const STATES=['QUEUED','DIAGNOSED','PLANNED','PATCHED','LOCAL_TESTED','INDEPENDENTLY_VERIFIED','CRITIQUED',
  'REVIEW_OPEN','OWNER_ACCEPTED','OWNER_REJECTED','FAILED','ABORTED','ROLLED_BACK'];
export const TERMINAL=new Set(['OWNER_ACCEPTED','OWNER_REJECTED','FAILED','ABORTED','ROLLED_BACK']);
const NEXT={
  QUEUED:['DIAGNOSED'],DIAGNOSED:['PLANNED','FAILED'],PLANNED:['PATCHED','FAILED'],PATCHED:['LOCAL_TESTED','ROLLED_BACK'],
  LOCAL_TESTED:['INDEPENDENTLY_VERIFIED','PATCHED','ROLLED_BACK','FAILED'],INDEPENDENTLY_VERIFIED:['CRITIQUED','ROLLED_BACK'],
  CRITIQUED:['REVIEW_OPEN','PATCHED','ROLLED_BACK'],REVIEW_OPEN:['OWNER_ACCEPTED','OWNER_REJECTED']
};
export const CAPABILITIES=new Set(['read_repo','write_worktree','run_tests','open_draft_pr','web_research_with_consent']);
const SAFE_BRANCH=/^(?!main$|master$|production$|release\/)[a-zA-Z0-9][a-zA-Z0-9/_-]{0,126}$/;
const SHA1=/^[0-9a-f]{40}$/, SHA256=/^[0-9a-f]{64}$/;
const sha=s=>crypto.createHash('sha256').update(s).digest('hex');
function deepCanonical(v){
  if(Array.isArray(v))return '['+v.map(deepCanonical).join(',')+']';
  if(v&&typeof v==='object')return '{'+Object.keys(v).filter(k=>v[k]!==undefined).sort()
    .map(k=>JSON.stringify(k)+':'+deepCanonical(v[k])).join(',')+'}';
  return JSON.stringify(v);
}

export function createCycle({id,base,branch,goal,capabilities,maxAttempts=4,deadlineMs}){
  if(typeof id!=='string'||!/^[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}$/.test(id))throw Error('INVALID_CYCLE_ID');
  if(!SHA1.test(base??''))throw Error('INVALID_BASE_COMMIT');
  if(!SAFE_BRANCH.test(branch??''))throw Error('PROTECTED_OR_INVALID_BRANCH');
  if(typeof goal!=='string'||goal.length<3||goal.length>2000)throw Error('INVALID_GOAL');
  if(!Array.isArray(capabilities)||capabilities.some(c=>!CAPABILITIES.has(c)))throw Error('UNKNOWN_CAPABILITY');
  if(!Number.isSafeInteger(maxAttempts)||maxAttempts<1||maxAttempts>16)throw Error('INVALID_BUDGET');
  if(!Number.isSafeInteger(deadlineMs)||deadlineMs<=0)throw Error('INVALID_DEADLINE');
  const genesis={seq:0,type:'CREATED',at:0,cycle:{id,base,branch,goal:sha(goal),capabilities:[...new Set(capabilities)].sort(),maxAttempts,deadlineMs}};
  const entry={...genesis,prev:'0'.repeat(64)};
  entry.hash=sha(deepCanonical({...entry,hash:undefined}));
  return {state:'QUEUED',id,base,branch,capabilities:genesis.cycle.capabilities,attempts:0,maxAttempts,deadlineMs,
    patchSha256:null,diagnosticKinds:[],strategy:null,localWorktree:null,verification:null,journal:[entry]};
}

/** One step. Pure: returns a new cycle or throws with a reason; never partially applies. */
export function applyEvent(cycle,event){
  if(TERMINAL.has(cycle.state))throw Error('CYCLE_ALREADY_TERMINAL');
  const {type,at,actor,evidence={}}=event;
  if(!Number.isSafeInteger(at)||at<lastAt(cycle))throw Error('NON_MONOTONIC_TIME');
  if(at>cycle.deadlineMs&&type!=='ABORT'&&type!=='ROLLBACK')throw Error('DEADLINE_EXCEEDED');
  if(actor!=='agent'&&actor!=='owner'&&actor!=='verifier')throw Error('UNKNOWN_ACTOR');
  if(evidence.capabilities!==undefined&&evidence.capabilities.some(c=>!cycle.capabilities.includes(c)))
    throw Error('CAPABILITY_ESCALATION_REFUSED');
  if(evidence.targetBranch!==undefined&&!SAFE_BRANCH.test(evidence.targetBranch))throw Error('PROTECTED_BRANCH_WRITE_REFUSED');
  const next=structuredClone(cycle);
  let to;
  switch(type){
    case 'ABORT': to='ABORTED';break;
    case 'ROLLBACK':
      if(evidence.restoredSha!==cycle.base)throw Error('ROLLBACK_NOT_TO_BASE');
      to='ROLLED_BACK';break;
    case 'DIAGNOSE':
      if(actor!=='agent'||evidence.baseSha!==cycle.base)throw Error('DIAGNOSIS_NOT_ON_BASE');
      if(!Array.isArray(evidence.kinds)||evidence.kinds.length===0||evidence.kinds.length>20)throw Error('DIAGNOSIS_WITHOUT_FINDINGS');
      next.diagnosticKinds=[...evidence.kinds].map(String).sort();to='DIAGNOSED';break;
    case 'PLAN':
      if(actor!=='agent'||typeof evidence.strategy!=='string'||!evidence.strategy)throw Error('PLAN_WITHOUT_STRATEGY');
      next.strategy=evidence.strategy;to='PLANNED';break;
    case 'PATCH':
      if(actor!=='agent'||!SHA256.test(evidence.patchSha256??''))throw Error('PATCH_WITHOUT_DIGEST');
      if(evidence.branch!==cycle.branch)throw Error('PATCH_ON_FOREIGN_BRANCH');
      if(!Number.isSafeInteger(evidence.files)||evidence.files<1||evidence.files>20)throw Error('PATCH_SIZE_OUT_OF_BOUNDS');
      if(cycle.attempts>=cycle.maxAttempts)throw Error('ATTEMPT_BUDGET_EXHAUSTED');
      next.attempts++;next.patchSha256=evidence.patchSha256;next.localWorktree=evidence.worktree;next.verification=null;to='PATCHED';break;
    case 'LOCAL_TESTS':
      requireTests(evidence.tests,false);
      if(evidence.patchSha256!==cycle.patchSha256)throw Error('TESTED_A_DIFFERENT_PATCH');
      next.localTests=evidence.tests;to='LOCAL_TESTED';break;
    case 'INDEPENDENT_VERIFY':
      // the second opinion must come from another checkout and another process identity, on the same base and patch
      if(actor!=='verifier')throw Error('SELF_VERIFICATION_REFUSED');
      if(cycle.localTests?.failed!==0)throw Error('LOCAL_TESTS_NOT_GREEN');
      if(!evidence.worktree||evidence.worktree===cycle.localWorktree)throw Error('VERIFIED_IN_THE_SAME_WORKTREE');
      if(evidence.baseSha!==cycle.base||evidence.patchSha256!==cycle.patchSha256)throw Error('VERIFIED_SOMETHING_ELSE');
      requireTests(evidence.tests,true);
      if(evidence.tests.failed!==0)throw Error('INDEPENDENT_TESTS_FAILED');
      next.verification={worktree:evidence.worktree,tests:evidence.tests};to='INDEPENDENTLY_VERIFIED';break;
    case 'CRITIQUE':
      if(typeof evidence.accepted!=='boolean')throw Error('CRITIQUE_WITHOUT_VERDICT');
      to=evidence.accepted?'CRITIQUED':'PATCHED';
      if(!evidence.accepted)next.verification=null;break;
    case 'OPEN_REVIEW':
      if(actor!=='agent'||!Number.isSafeInteger(evidence.pullRequest)||evidence.headPatchSha256!==cycle.patchSha256)throw Error('REVIEW_NOT_BOUND_TO_PATCH');
      if(!cycle.capabilities.includes('open_draft_pr'))throw Error('CAPABILITY_ESCALATION_REFUSED');
      next.pullRequest=evidence.pullRequest;to='REVIEW_OPEN';break;
    case 'OWNER_DECISION':
      if(actor!=='owner')throw Error('ONLY_THE_OWNER_DECIDES');
      to=evidence.accept===true?'OWNER_ACCEPTED':'OWNER_REJECTED';break;
    case 'MERGE': throw Error('AGENT_MERGE_REFUSED');
    case 'FAIL': to='FAILED';next.failure=String(evidence.reason??'UNSPECIFIED').slice(0,80);break;
    default: throw Error('UNKNOWN_EVENT');
  }
  if(!['ABORTED','ROLLED_BACK','FAILED'].includes(to)&&!(NEXT[cycle.state]??[]).includes(to))
    throw Error('ILLEGAL_TRANSITION_'+cycle.state+'_TO_'+to);
  next.state=to;
  const prev=cycle.journal.at(-1);
  const entry={seq:prev.seq+1,type,at,actor,evidence:summarize(evidence),state:to,prev:prev.hash};
  entry.hash=sha(deepCanonical({...entry,hash:undefined}));
  next.journal=[...cycle.journal,entry];
  return next;
}

function lastAt(cycle){return cycle.journal.at(-1).at;}

function requireTests(t,strict){
  if(!t||![t.total,t.executed,t.failed].every(Number.isSafeInteger)||t.executed<1||t.failed<0||t.failed>t.executed||t.total<t.executed)
    throw Error('TESTS_WITHOUT_EXECUTED_EVIDENCE');
  if(strict&&t.evidenceComplete!==true)throw Error('INCOMPLETE_TEST_EVIDENCE');
}

/** Evidence is journaled by digest and small scalars only; no logs, code or owner text enter the journal. */
function summarize(e){
  const out={};
  for(const [k,v] of Object.entries(e)){
    if(typeof v==='string')out[k]=v.length<=80?v:'sha256:'+sha(v);
    else if(typeof v==='number'||typeof v==='boolean')out[k]=v;
    else if(v&&typeof v==='object')out[k]='sha256:'+sha(deepCanonical(v));
  }
  return out;
}

/** Rebuild trust after a crash: verify the chain; tampering or a gap is detected, never silently repaired. */
export function verifyJournal(journal){
  if(!Array.isArray(journal)||journal.length===0||journal[0].type!=='CREATED')throw Error('JOURNAL_WITHOUT_GENESIS');
  for(let i=0;i<journal.length;i++){
    const e=journal[i];
    if(e.seq!==i)throw Error('JOURNAL_GAP');
    if(e.hash!==sha(deepCanonical({...e,hash:undefined})))throw Error('JOURNAL_TAMPERED');
    if(i>0&&e.prev!==journal[i-1].hash)throw Error('JOURNAL_CHAIN_BROKEN');
  }
  return {entries:journal.length,state:journal.at(-1).state??'QUEUED',head:journal.at(-1).hash};
}

/**
 * The only learning signal a cycle may produce, mirroring repair-ledger.recordSession: success only from an
 * independently verified, critic-accepted patch; failure only from executed failing tests; everything else
 * (infrastructure, aborts, owner rejections for scope) is neutral. Owner acceptance is reported separately.
 */
export function creditOutcome(cycle){
  if(!TERMINAL.has(cycle.state)&&cycle.state!=='REVIEW_OPEN')return null;
  const kind=(cycle.diagnosticKinds[0]??'UNKNOWN')+'|'+(cycle.strategy??'UNKNOWN');
  const verified=cycle.journal.some(e=>e.state==='INDEPENDENTLY_VERIFIED')&&cycle.journal.some(e=>e.state==='CRITIQUED');
  if(verified&&cycle.state!=='ROLLED_BACK')return {kind:kind.slice(0,80),status:'COMPILES'};
  const t=cycle.localTests;
  if(cycle.state==='FAILED'&&t&&t.executed>0&&t.failed>0&&t.evidenceComplete!==false)return {kind:kind.slice(0,80),status:'REJECTED'};
  return {kind:kind.slice(0,80),status:'NEUTRAL'};
}

// ---- strategy policy ---------------------------------------------------------------------------------------------

function mulberry32(seed){
  let a=seed>>>0;
  return ()=>{a=(a+0x6D2B79F5)>>>0;let t=a;t=Math.imul(t^(t>>>15),t|1);t^=t+Math.imul(t^(t>>>7),t|61);return((t^(t>>>14))>>>0)/4294967296;};
}
function gammaSample(k,rnd){            // Marsaglia-Tsang, k >= 1 (Beta(1+s,1+f) always qualifies)
  const d=k-1/3,c=1/Math.sqrt(9*d);
  for(;;){
    let x,v;
    do{const u1=rnd(),u2=rnd();x=Math.sqrt(-2*Math.log(u1||1e-12))*Math.cos(2*Math.PI*u2);v=1+c*x;}while(v<=0);
    v=v*v*v;const u=rnd();
    if(u<1-0.0331*x**4||Math.log(u)<0.5*x*x+d*(1-v+Math.log(v)))return d*v;
  }
}
export function betaSample(a,b,rnd){const x=gammaSample(a,rnd),y=gammaSample(b,rnd);return x/(x+y);}

/** Candidate order: risk tier first (safety constraint, never learned), then a Thompson draw per kind|strategy. */
export function thompsonRank(candidates,stats=[],failedIds=new Set(),seed=1){
  const rnd=mulberry32(seed),byKey=new Map(stats.map(s=>[s.kind,s]));
  return candidates.filter(c=>!failedIds.has(c.diagnostic+':'+c.id)).map(c=>{
    const s=byKey.get((c.diagnosticKind+'|'+c.strategy).slice(0,80));
    const draw=betaSample(1+(s?.successes??0),1+(s?.failures??0),rnd);
    return {...c,score:Number((0.6*draw+0.4*(c.prior??0.5)).toFixed(4))};
  }).sort((a,b)=>repairCandidateRiskTier(a.strategy)-repairCandidateRiskTier(b.strategy)||b.score-a.score||a.id.localeCompare(b.id));
}

/** The frozen control arm: today's Laplace ranking, imported unchanged from repair-loop.mjs. */
export const frozenRank=rankCandidates;

// ---- experiment registry -------------------------------------------------------------------------------------

export function emptyRegistry(){return {schemaVersion:1,experiments:{}};}

export function preregister(registry,{id,hypothesis,metric,control,candidate,holdoutTasks,alpha=0.05,minPairs=10}){
  if(!/^[A-Z0-9][A-Z0-9_-]{2,40}$/.test(id??''))throw Error('INVALID_EXPERIMENT_ID');
  if(registry.experiments[id])throw Error('ALREADY_PREREGISTERED');
  if(typeof hypothesis!=='string'||hypothesis.length<10||!metric||!control||!candidate||control===candidate)throw Error('INCOMPLETE_PREREGISTRATION');
  if(!Array.isArray(holdoutTasks)||holdoutTasks.length<minPairs||new Set(holdoutTasks).size!==holdoutTasks.length||holdoutTasks.some(t=>!SHA256.test(t)))
    throw Error('HOLDOUT_TOO_SMALL_OR_INVALID');
  if(!(alpha>0&&alpha<=0.1)||!Number.isSafeInteger(minPairs)||minPairs<5)throw Error('INVALID_DECISION_RULE');
  const sealed={id,hypothesis,metric,control,candidate,alpha,minPairs,holdoutSha256:sha([...holdoutTasks].sort().join('\n')),
    holdoutSize:holdoutTasks.length,status:'SEALED',result:null};
  sealed.sealSha256=sealOf(sealed);
  return {...registry,experiments:{...registry.experiments,[id]:sealed}};
}

/** The sealed part of a preregistration: changing any of it after sealing is detected. */
function sealOf(e){
  const {id,hypothesis,metric,control,candidate,alpha,minPairs,holdoutSha256,holdoutSize}=e;
  return sha(deepCanonical({id,hypothesis,metric,control,candidate,alpha,minPairs,holdoutSha256,holdoutSize}));
}

/** Exact one-sided McNemar test on discordant pairs: P(X >= b | n=b+c, p=1/2). */
export function mcnemarOneSided(b,c){
  const n=b+c;if(n===0)return 1;
  let p=0,coef=1;           // C(n,0)
  for(let k=0;k<=n;k++){if(k>0)coef=coef*(n-k+1)/k;if(k>=b)p+=coef;}
  return Math.min(1,p/2**n);
}

/** The single TEST run: pairs on exactly the sealed holdout; a second call is refused; the result is kept either way. */
export function recordHoldoutRun(registry,id,pairs){
  const e=registry.experiments[id];
  if(!e)throw Error('NOT_PREREGISTERED');
  if(e.status!=='SEALED')throw Error('HOLDOUT_ALREADY_USED');
  if(e.sealSha256!==sealOf(e))throw Error('PREREGISTRATION_MODIFIED');
  if(!Array.isArray(pairs)||sha(pairs.map(p=>p.task).sort().join('\n'))!==e.holdoutSha256)throw Error('NOT_THE_SEALED_HOLDOUT');
  if(pairs.some(p=>typeof p.control!=='boolean'||typeof p.candidate!=='boolean'))throw Error('INVALID_PAIR');
  const b=pairs.filter(p=>p.candidate&&!p.control).length,c=pairs.filter(p=>p.control&&!p.candidate).length;
  const pValue=mcnemarOneSided(b,c);
  const verdict=b+c<3?'INCONCLUSIVE':pValue<e.alpha&&b>c?'PASSED':'FAILED';
  const result={candidateWins:b,controlWins:c,ties:pairs.length-b-c,pValue:Number(pValue.toFixed(6)),verdict,
    controlRate:pairs.filter(p=>p.control).length/pairs.length,candidateRate:pairs.filter(p=>p.candidate).length/pairs.length};
  return {...registry,experiments:{...registry.experiments,[id]:{...e,status:'DONE',result}}};
}

/** New research questions from observed weaknesses in the CCC1 ledger's experiences (no automatic code change). */
export function mineWeaknesses(experiences,{minObservations=4,maxSuccessRate=0.34}={}){
  return experiences.filter(x=>x.successes+x.failures>=minObservations&&(x.successes+1)/(x.successes+x.failures+2)<=maxSuccessRate)
    .map(x=>{const [kind,strategy]=x.kind.split('|');
      return {id:('W-'+sha(x.kind).slice(0,8)).toUpperCase(),kind,strategy,observations:x.successes+x.failures,
        laplaceRate:Number(((x.successes+1)/(x.successes+x.failures+2)).toFixed(3)),
        question:`Repariert eine andere Strategie die Fehlerklasse ${kind} öfter als ${strategy}? `+
          'Prüfen nur als vorregistriertes Experiment mit versiegeltem Holdout.'};})
    .sort((a,b)=>a.laplaceRate-b.laplaceRate||a.kind.localeCompare(b.kind));
}

export const __test={deepCanonical,mulberry32};
