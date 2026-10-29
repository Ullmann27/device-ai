import crypto from 'node:crypto';
import {mineWeaknesses,thompsonRank,frozenRank} from './dev-loop-kernel.mjs';

const TOKEN=/^[A-Z][A-Z0-9_]{0,79}$/;
const HEX=/^[0-9a-f]{1,64}$/;
const count=n=>Number.isSafeInteger(n)&&n>=0&&n<=1_000_000;
const digest=s=>crypto.createHash('sha256').update(s).digest('hex');
const empty=status=>({schema:'continuum-dev-loop-shadow',version:1,mode:'SHADOW',status,
 currentVerification:'NOT_ESTABLISHED',attemptedCandidates:0,executedPrimaryTests:0,executedIndependentTests:0,
 weakStrategyClasses:0,policyComparison:'NO_COMPARISON',thompsonFirstMatchesControl:null,
 positiveLearningAllowed:false,strategyRankingChanged:false});

function primaryTests(profiles){
 if(!Array.isArray(profiles)||profiles.length<1||profiles.length>16)return null;
 let executed=0;
 for(const p of profiles){
  if(p.passed!==true||p.exitCode!==0||p.error||p.signal||p.timedOut)return null;
  if(p.testProfile===false)continue;
  const t=p.tests;
  if(!t||![t.total,t.executed,t.failed,t.skipped].every(count)||t.evidenceComplete!==true||
    t.failed!==0||t.total<t.executed||t.skipped>t.total-t.executed)return null;
  executed+=t.executed;
 }
 return executed>0&&count(executed)?executed:null;
}

function independentTests(independent){
 if(independent?.passed!==true||independent.evidenceSufficient!==true||
   !Array.isArray(independent.profiles)||independent.profiles.length<1||independent.profiles.length>16)return null;
 let executed=0;
 for(const p of independent.profiles){
  if(p.passed!==true||![p.executed,p.failed,p.skipped].every(count)||p.failed!==0)return null;
  executed+=p.executed;
 }
 return executed>0&&count(executed)?executed:null;
}

/** Read-only F3 observer of existing CCC1 reports, never a verifier, queue, journal or learner.
 * A SUMMARY_MATCH means fields in the worker's report agree; it does not authenticate a process or test oracle.
 * The real worker still executes independent checks and recordSession remains the only learning path.
 */
export function summarizeDevLoopShadow({report,experiences=[]}){
 if(!report||!Array.isArray(report.attempts)||report.attempts.length>12||
   !Array.isArray(experiences)||experiences.length>100||experiences.some(s=>
    typeof s?.kind!=='string'||s.kind.length>80||s.kind.split('|').some(k=>!TOKEN.test(k)&&!/^node\.js\.(call|value)$/.test(k))||
    !count(s.successes)||!count(s.failures)))throw Error('INVALID_DEV_LOOP_SHADOW_INPUT');
 const summary=empty('ABSTAIN');
 summary.attemptedCandidates=report.attempts.length;
 summary.weakStrategyClasses=mineWeaknesses(experiences).length;
 // The report retains tried candidates, not the complete proposal set or original priors.
 // A constant-prior comparison on that subset is explicitly a proxy, never a quality measurement.
 const candidates=report.attempts.filter(a=>HEX.test(a?.candidate??'')&&HEX.test(a?.diagnostic??'')&&
   TOKEN.test(a?.strategy??'')&&TOKEN.test(a?.diagnosticKind??'')).map(a=>({id:a.candidate,
    diagnostic:a.diagnostic,diagnosticKind:a.diagnosticKind,strategy:a.strategy,prior:0.5}));
 if(candidates.length&&new Set(candidates.map(c=>c.id)).size===candidates.length){
  const broad=experiences.filter(s=>s.kind.split('|').length===2);
  const control=frozenRank(candidates,broad);
  const proposed=thompsonRank(candidates,broad,new Set(),1);
  summary.policyComparison='ATTEMPTED_CANDIDATES_PROXY_ONLY';
  summary.thompsonFirstMatchesControl=proposed[0]?.id===control[0]?.id;
 }
 const accepted=report.verified;
 const winners=accepted?report.attempts.filter(a=>a.candidate===accepted.candidate&&a.strategy===accepted.strategy&&
  a.outcome==='VERIFIED'&&a.critic?.approved===true&&a.evidence?.sufficient===true):[];
 const primary=winners.length===1?primaryTests(winners[0].tests):null;
 const independent=independentTests(report.independent);
 if(report.outcome==='VERIFIED'&&/^[0-9a-f]{40}$/.test(report.base??'')&&
   typeof accepted?.patch==='string'&&accepted.patch.length>0&&accepted.patch.length<=1_000_000&&
   /^[0-9a-f]{64}$/.test(accepted.patchSha256??'')&&digest(accepted.patch)===accepted.patchSha256&&
   primary!==null&&primaryTests(accepted.tests)===primary&&independent!==null){
  summary.status='OBSERVED';summary.currentVerification='SUMMARY_MATCH';
  summary.executedPrimaryTests=primary;summary.executedIndependentTests=independent;
 }
 return summary;
}

/** Independent copies and an exact scalar allowlist keep observer faults out of the repair and ledger. */
export function runDevLoopShadow(observer,report,experiences){
 if(observer===null)return null;
 try{
  if(typeof observer!=='function')throw Error('INVALID_DEV_LOOP_SHADOW_OBSERVER');
  const s=observer({report:structuredClone(report),experiences:structuredClone(experiences)});
  if(s?.schema!=='continuum-dev-loop-shadow'||s.version!==1||s.mode!=='SHADOW'||
    !['OBSERVED','ABSTAIN'].includes(s.status)||!['SUMMARY_MATCH','NOT_ESTABLISHED'].includes(s.currentVerification)||
    ![s.attemptedCandidates,s.executedPrimaryTests,s.executedIndependentTests,s.weakStrategyClasses].every(count)||
    s.attemptedCandidates>12||s.weakStrategyClasses>100||
    !['NO_COMPARISON','ATTEMPTED_CANDIDATES_PROXY_ONLY'].includes(s.policyComparison)||
    !(s.thompsonFirstMatchesControl===null||typeof s.thompsonFirstMatchesControl==='boolean')||
    s.positiveLearningAllowed!==false||s.strategyRankingChanged!==false)throw Error('INVALID_DEV_LOOP_SHADOW_OUTPUT');
  return {schema:s.schema,version:s.version,mode:s.mode,status:s.status,currentVerification:s.currentVerification,
   attemptedCandidates:s.attemptedCandidates,executedPrimaryTests:s.executedPrimaryTests,
   executedIndependentTests:s.executedIndependentTests,weakStrategyClasses:s.weakStrategyClasses,
   policyComparison:s.policyComparison,thompsonFirstMatchesControl:s.thompsonFirstMatchesControl,
   positiveLearningAllowed:false,strategyRankingChanged:false};
 }catch{return empty('ERROR');}
}
