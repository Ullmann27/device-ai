/**
 * An inherited repair depth is a recursion boundary, not a loose numeric hint.
 * Invalid values must halt the worker instead of becoming NaN or a negative
 * number that bypasses the nested-session check.
 */
export function strictRepairDepth(raw){
 if(raw===undefined)return 0;
 if(typeof raw!=='string'||!/^(0|[1-9][0-9]*)$/.test(raw))
  throw Error('Invalid CONTINUUM_REPAIR_DEPTH');
 const depth=Number(raw);
 if(!Number.isSafeInteger(depth))throw Error('Invalid CONTINUUM_REPAIR_DEPTH');
 return depth;
}

/**
 * Research-only structural screening of a green -> red -> green repair trace.
 *
 * This accepts parsed runChain-style observations, NOT independently verified
 * execution. Callers can forge JS objects. The returned tuple MUST NOT be
 * persisted as trusted repair-ledger experience or train/promote CRE1 until a
 * separate trusted worktree-runner has bound every observation to real commands,
 * committed source hashes, and an independent second-worktree proof.
 */
export function validateTriplePhaseSample({
 baselineRun,mutatedRun,candidateRuns,mutationFile
}={}){
 const rejected=reason=>({valid:false,reason,trainingTuple:null,authority:'SHADOW_ONLY'});
 const int=n=>Number.isSafeInteger(n)&&n>=0;
 const safeName=v=>typeof v==='string'&&/^[A-Z][A-Z0-9_:]{1,79}$/.test(v);
 const suite=run=>{
  if(!run||!Array.isArray(run.results)||run.results.length===0||
     run.results.length>8||typeof run.passed!=='boolean')return null;
  const profiles=new Set();
  let executed=0;
  for(const p of run.results){
   if(!p||typeof p.profile!=='string'||!/^[a-z][a-z0-9-]{1,47}$/.test(p.profile)||
      profiles.has(p.profile)||typeof p.passed!=='boolean'||
      typeof p.testProfile!=='boolean'||!int(p.tests?.executed)||
      !int(p.tests?.failed)||!int(p.tests?.skipped)||
      !(p.exitCode===null||int(p.exitCode)))return null;
   profiles.add(p.profile);
   if(p.testProfile)executed+=p.tests.executed;
   if(p.passed&&(p.exitCode!==0||p.tests.failed!==0))return null;
   if(!p.passed&&p.exitCode===0&&p.tests.failed===0)return null;
  }
  if(run.passed!==run.results.every(p=>p.passed))return null;
  return {profiles:[...profiles],executed};
 };
 const baseline=suite(baselineRun);
 if(!baseline||!baselineRun.passed||baseline.executed<=0||
    baselineRun.results.some(p=>p.tests.failed!==0||p.tests.skipped!==0))
  return rejected('BASELINE_NOT_GREEN');
 const mutation=suite(mutatedRun);
 if(!mutation||mutatedRun.passed||
    mutation.profiles.some((p,i)=>baseline.profiles[i]!==p)||
    !mutatedRun.results.some(p=>!p.passed))
  return rejected('MUTATION_NOT_DETECTED_BY_TESTS');
 if(typeof mutationFile!=='string'||
    !/^app\/src\/main\/(?:java|kotlin)\/(?:[A-Za-z0-9_-]+\/)+[A-Za-z0-9_-]+\.kt$/.test(mutationFile))
  return rejected('INVALID_MUTATION_FILE');
 const diagnostics=mutatedRun.diagnostics;
 if(!Array.isArray(diagnostics))return rejected('NO_ACTIONABLE_DIAGNOSTIC_EXTRACTED');
 const diag=diagnostics.find(d=>d&&d.file===mutationFile&&
   typeof d.kind==='string'&&/^[A-Z][A-Z0-9_]{1,47}$/.test(d.kind)&&
   typeof d.message==='string'&&d.message.trim());
 if(!diag)return rejected('NO_ACTIONABLE_DIAGNOSTIC_EXTRACTED');
 if(!Array.isArray(candidateRuns)||candidateRuns.length<2||candidateRuns.length>12)
  return rejected('MISSING_VERIFIED_WINNER_OR_REFUTED_NEGATIVE');
 const winners=[],negatives=[],seen=new Set();
 for(const attempt of candidateRuns){
  if(!safeName(attempt?.strategy)||seen.has(attempt.strategy)||
     !['local','groq'].includes(attempt.source)||
     !/^[0-9a-f]{16}$/.test(attempt.candidateId??''))
   return rejected('INVALID_CANDIDATE_IDENTITY');
  seen.add(attempt.strategy);
  const primary=suite(attempt.primaryRun);
  if(!primary||primary.profiles.some((p,i)=>p!==baseline.profiles[i]))
   return rejected('INVALID_PRIMARY_EVIDENCE');
  if(!attempt.primaryRun.passed){
   // A failing compiler profile may run zero tests; a failing test profile
   // needs actually executed failing assertions. Neither is a global verdict
   // on the strategy, only on this exact candidate and source snapshot.
   const failedTest=attempt.primaryRun.results.some(p=>
     !p.passed&&p.testProfile&&p.tests.executed>0&&p.tests.failed>0);
   const failedCompiler=attempt.primaryRun.results.some(p=>
     !p.passed&&!p.testProfile&&p.exitCode!==0)&&
     Array.isArray(attempt.primaryRun.diagnostics)&&
     attempt.primaryRun.diagnostics.some(d=>d?.file===mutationFile&&
       typeof d.kind==='string'&&d.kind.length>0);
   if(!failedTest&&!failedCompiler)return rejected('AMBIGUOUS_NEGATIVE');
   negatives.push(attempt.strategy);
   continue;
  }
  if(primary.profiles.length!==baseline.profiles.length)
   return rejected('INVALID_PRIMARY_EVIDENCE');
  if(primary.executed<baseline.executed||
     attempt.primaryRun.results.some(p=>p.tests.skipped!==0))
   return rejected('PRIMARY_EVIDENCE_REGRESSION');
  const independent=suite(attempt.independentRun);
  if(!independent||!attempt.independentRun.passed||
     independent.executed<baseline.executed||
     independent.profiles.length!==baseline.profiles.length||
     independent.profiles.some((p,i)=>p!==baseline.profiles[i])||
     attempt.independentRun.results.some(p=>p.tests.skipped!==0))
   return rejected('INDEPENDENT_EVIDENCE_MISSING_OR_FAILED');
  if(!/^[0-9a-f]{64}$/.test(attempt.patchSha256??'')||
     attempt.patchSha256!==attempt.independentPatchSha256)
   return rejected('INDEPENDENT_PATCH_MISMATCH');
  winners.push(attempt.strategy);
 }
 if(winners.length!==1||negatives.length===0)
  return rejected('MISSING_VERIFIED_WINNER_OR_REFUTED_NEGATIVE');
 const query=[diag.kind,typeof diag.symbol==='string'?diag.symbol:'',
    diag.message.trim().slice(0,250)].filter(Boolean).join(' ');
 return {
  valid:true,reason:null,authority:'SHADOW_ONLY',
  // The validator is NOT a ledger writer, training oracle, or proof of origin.
  trainingTuple:{
   diagnosticKind:diag.kind,diagnosticQuery:query,
   winningStrategy:winners[0],refutedStrategies:negatives
  }
 };
}

/** Every exit path from a bounded session needs an auditable end timestamp. */
export function finishRepairReport(report,outcome,finishedAt=new Date().toISOString()){
 report.outcome=outcome;
 report.finishedAt=finishedAt;
 return report;
}
