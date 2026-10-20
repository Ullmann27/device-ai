import {sha256} from './repair-diagnostics.mjs';

export const ANSK_SCHEMA='continuum-ansk-shadow-recipe';
export const ANSK_VERSION=1;

const SHA40=/^[0-9a-f]{40}$/;
const SHA64=/^[0-9a-f]{64}$/;
const SOURCE=/^app\/src\/main\/(?:java|kotlin)\/(?:[A-Za-z0-9_-]+\/)+[A-Za-z0-9_-]+\.kt$/;
const IMPORT=/^(?:[A-Za-z_][A-Za-z0-9_]*\.)+[A-Za-z_][A-Za-z0-9_]*$/;
const PROFILE_ORDER=['android-compile','android-unit'];

export function assertAnskSource(file,importFqn){
  if(typeof file!=='string'||!SOURCE.test(file)||file.includes('..')||
     typeof importFqn!=='string'||!IMPORT.test(importFqn))
    throw Error('ANSK_INVALID_KOTLIN_IMPORT_TARGET');
  return file;
}

/** Strictly the existing fixed Android chain; external JSON is not proof. */
export function measureAnskChain(result,{baseline=null,allowFailure=false}={}){
  if(!result||!Array.isArray(result.results)||result.results.length===0||
     result.results.length>PROFILE_ORDER.length||typeof result.passed!=='boolean')
    return {passed:false,reason:'INVALID_RUN'};
  const rows=result.results;
  if(rows.some((p,i)=>p?.profile!==PROFILE_ORDER[i]||
    typeof p.passed!=='boolean'||typeof p.testProfile!=='boolean'||
    p.testProfile!==(i===1)||!Number.isSafeInteger(p.tests?.executed)||
    !Number.isSafeInteger(p.tests?.failed)||!Number.isSafeInteger(p.tests?.skipped)||
    p.tests.executed<0||p.tests.failed<0||p.tests.skipped<0||
    p.tests.skipped>0||p.timedOut===true||
    (p.passed&&(p.tests.failed!==0||p.exitCode!==0))||
    (!p.passed&&p.exitCode===0&&p.tests.failed===0)))
    return {passed:false,reason:'INVALID_PROFILE'};
  if(result.passed!==rows.every(p=>p.passed))
    return {passed:false,reason:'INVALID_CHAIN_VERDICT'};
  if(allowFailure){
    if(result.passed||rows.every(p=>p.passed)||
       rows.length!==1||rows[0].profile!=='android-compile'||
       rows[0].passed!==false||rows[0].exitCode===0)
      return {passed:false,reason:'MUTATION_NOT_COMPILER_RED'};
    return {passed:true,reason:null};
  }
  if(!result.passed||rows.length!==PROFILE_ORDER.length||
     rows.some(p=>!p.passed)||rows[1].tests.executed<=0)
    return {passed:false,reason:'NOT_ALL_PROFILES_GREEN'};
  if(baseline&&
    rows.some((p,i)=>p.tests.executed<baseline.results[i].tests.executed||
      p.tests.skipped>baseline.results[i].tests.skipped))
    return {passed:false,reason:'EVIDENCE_REGRESSION'};
  return {passed:true,reason:null};
}

export function verifyAnskExperiment({
  head,sourceFile,importFqn,baselineSha256,mutatedSha256,
  primarySha256,independentSha256,patchSha256,
  baselineRun,mutatedRun,primaryRun,independentRun,
  diagnostic,strategiesAttempted,successfulStrategy
}={}){
  const fail=reason=>({verified:false,reason,authority:'SHADOW_ONLY',recipe:null});
  try{assertAnskSource(sourceFile,importFqn);}catch{return fail('INVALID_TARGET');}
  if(!SHA40.test(head??'')||
     ![baselineSha256,mutatedSha256,primarySha256,independentSha256,patchSha256]
      .every(s=>SHA64.test(s??''))||
     baselineSha256===mutatedSha256||primarySha256!==independentSha256)
    return fail('SOURCE_OR_PATCH_HASH_MISMATCH');
  if(measureAnskChain(baselineRun).passed!==true)return fail('BASELINE_NOT_GREEN');
  if(measureAnskChain(mutatedRun,{allowFailure:true}).passed!==true)
    return fail('MUTATION_NOT_COMPILER_RED');
  if(!diagnostic||diagnostic.file!==sourceFile||
     diagnostic.kind!=='UNRESOLVED_REFERENCE'||
     diagnostic.symbol!==importFqn.split('.').at(-1))
    return fail('NO_MATCHING_COMPILER_DIAGNOSTIC');
  if(measureAnskChain(primaryRun,{baseline:baselineRun}).passed!==true)
    return fail('PRIMARY_NOT_GREEN');
  if(measureAnskChain(independentRun,{baseline:baselineRun}).passed!==true)
    return fail('INDEPENDENT_NOT_GREEN');
  if(typeof successfulStrategy!=='string'||successfulStrategy!=='KOTLIN_MISSING_IMPORT'||
     !Array.isArray(strategiesAttempted)||strategiesAttempted.length<1||
     strategiesAttempted.length>3||!strategiesAttempted.includes(successfulStrategy)||
     new Set(strategiesAttempted).size!==strategiesAttempted.length)
    return fail('INVALID_REPAIR_STRATEGY');
  const recipe={
    schema:ANSK_SCHEMA,version:ANSK_VERSION,authority:'SHADOW_ONLY',
    stage:'QUARANTINED_REVIEW_REQUIRED',head,
    sourceFile,sourceSha256:baselineSha256,
    mutatedSha256,repairedSha256:primarySha256,
    editSha256:patchSha256,
    diagnosis:'UNRESOLVED_REFERENCE',
    strategy:'KOTLIN_MISSING_IMPORT',importFqn,
    baselineExecuted:baselineRun.results[1].tests.executed,
    primaryExecuted:primaryRun.results[1].tests.executed,
    independentExecuted:independentRun.results[1].tests.executed,
    attemptedStrategies:strategiesAttempted.slice(),
    modelTrainingAuthorized:false,automaticToolRegistration:false,
    mainBranchWriteAuthorized:false
  };
  return {verified:true,reason:null,authority:'SHADOW_ONLY',
    recipe:{...recipe,recipeSha256:sha256(JSON.stringify(recipe))}};
}
