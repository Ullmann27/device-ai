#!/usr/bin/env node
/**
 * Explicit, bounded transfer experiment. It consumes an existing local receipt
 * without repeating its origin experiment. Worktrees and Gradle runs are serial.
 * Receipt integrity is not authorship; every candidate still faces the critic,
 * real compiler/test chain and a fresh independent worktree.
 */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {performance} from 'node:perf_hooks';
import {fileURLToPath} from 'node:url';
import {ensureCleanSource,assertSourceUnchanged,stateRoot,readCode,
 editInWorktree,phaseDir,diagnosticFor} from './ansk-import-lab.mjs';
import {createWorktree,removeWorktree,runChain,checkCandidateKotlinSyntax,
 rankCandidates} from './repair-loop.mjs';
import {planInverseKotlinImportMutation,proposeCandidates} from './repair-strategies.mjs';
import {critique} from './repair-critic.mjs';
import {assertAnskSource,measureAnskChain,verifyAnskTransferArm} from './ansk-lab-contract.mjs';
import {readImmutableAnskRecipe,writeImmutableAnskTransfer} from './ansk-recipe-store.mjs';
import {planAnskShadowReplay} from './ansk-recipe-replay.mjs';
import {acquireContinuumBuildLease} from './shared-build-lease.mjs';
import {summarizeRepairTransfer} from './repair-experience.mjs';
import {sha256} from './repair-diagnostics.mjs';

const MAX_BUDGET_MS=45*60_000;
// Persist bounded metadata, never raw compiler output, source text or tokens.
function evidence(run){
 if(!run)return null;
 return {passed:run.passed,results:run.results.map(p=>({
  profile:p.profile,passed:p.passed,testProfile:p.testProfile,
  exitCode:p.exitCode,timedOut:p.timedOut,ms:p.ms,tests:p.tests
 }))};
}

export function runAnskTransferLab({root,sourceFile,recipeSha256,
 order='recipe-first',deadlineMs=MAX_BUDGET_MS}={}){
 if(!/^[0-9a-f]{64}$/.test(recipeSha256??''))
  throw Error('ANSK_INVALID_RECIPE_ADDRESS');
 // Validate the path before accessing the source repository or recipe store.
 assertAnskSource(sourceFile,'java.nio.file.Files');
 if(!['recipe-first','control-first'].includes(order))
  throw Error('ANSK_INVALID_TRANSFER_ORDER');
 if(!Number.isSafeInteger(deadlineMs)||deadlineMs<60_000||deadlineMs>MAX_BUDGET_MS)
  throw Error('ANSK_INVALID_TIME_BUDGET');
 const checkout=ensureCleanSource(root),{head}=checkout,store=stateRoot(checkout.root);
 const recipe=readImmutableAnskRecipe(store,recipeSha256);
 if(sourceFile===recipe.sourceFile)throw Error('ANSK_TRANSFER_REQUIRES_NEW_SOURCE');
 const original=readCode(checkout.root,sourceFile);
 if(sha256(original)===recipe.sourceSha256||sha256(original)===recipe.repairedSha256)
  throw Error('ANSK_TRANSFER_REJECTS_COPIED_SOURCE');
 const plan=planInverseKotlinImportMutation({file:sourceFile,source:original,importFqn:recipe.importFqn});
 if(!plan)throw Error('ANSK_NO_SAFE_TRANSFER_MUTATION');
 const release=acquireContinuumBuildLease(checkout.root);
 const worktrees=new Set();
 const deadline=Date.now()+deadlineMs;
 const out=path.join(store,'transfer-runs',crypto.randomBytes(12).toString('hex'));
 const report={schema:'continuum-ansk-transfer',version:1,head,sourceFile,
  recipeSha256,originHead:recipe.head,originSource:recipe.sourceFile,
  authority:'SHADOW_ONLY',modelTrainingAuthorized:false,productionWriteAuthorized:false,
  contrastiveTupleAvailable:false,order,
  platform:{os:process.platform,arch:process.arch,node:process.version},
  baselineSha256:plan.baselineSha256,mutatedSha256:plan.mutatedSha256,
  status:'BLOCKED',baseline:null,mutation:null,arms:[]};
 const allocate=()=>{
  if(Date.now()>=deadline)throw Error('ANSK_DEADLINE_EXHAUSTED');
  const wt=createWorktree(checkout.root,head);worktrees.add(wt);return wt;
 };
 const dispose=wt=>{removeWorktree(checkout.root,wt);worktrees.delete(wt);};
 const check=(wt,name)=>runChain('android',wt,phaseDir(out,name),{deadline,forceRerun:true});
 const finish=(status,reason=null)=>{
  assertSourceUnchanged(checkout.root,head);
  report.status=status;report.reason=reason;
  if(report.arms.length===2)report.comparison=summarizeRepairTransfer(report.arms);
  return {...report,...writeImmutableAnskTransfer(store,report)};
 };
 try{
  fs.mkdirSync(out,{recursive:true,mode:0o700});
  const baseline=allocate();
  const baselineRun=check(baseline,'baseline');
  report.baseline=evidence(baselineRun);
  if(!measureAnskChain(baselineRun).passed)
   return finish('BLOCKED_BASELINE_NOT_GREEN');
  editInWorktree(baseline,sourceFile,[plan.mutation],plan.baselineSha256);
  const mutatedRun=check(baseline,'mutation');
  report.mutation=evidence(mutatedRun);
  const diagnostic=diagnosticFor(mutatedRun,sourceFile,plan.symbol);
  if(!measureAnskChain(mutatedRun,{allowFailure:true}).passed||!diagnostic)
   return finish('DISCARDED_MUTATION_NOT_REPRODUCED');
  dispose(baseline);
  for(const name of order==='recipe-first'?['recipe','control']:['control','recipe']){
   const primary=allocate();
   editInWorktree(primary,sourceFile,[plan.mutation],plan.baselineSha256);
   const started=performance.now();
   const source=readCode(primary,sourceFile);
   // Both arms receive identical compiler diagnostics. Control has NO receipt
   // or persisted experience; only the existing deterministic proposer.
   const candidate=name==='recipe'
    ?planAnskShadowReplay({recipe,sourceFile,source,diagnostic}).candidate
    :rankCandidates(proposeCandidates([diagnostic],{
      readFile:p=>p===sourceFile?source:null,index:null,jsSiblings:new Map(),
      changedInBase:[],experience:[]
     })).find(c=>c.strategy==='KOTLIN_MISSING_IMPORT'&&
       c.diagnostic===diagnostic.fingerprint&&c.edits.length===1&&c.edits[0].file===sourceFile);
   const arm={name,verified:false,reason:'NO_CANDIDATE',
    planningMs:performance.now()-started,validationMs:0,
    primary:null,independent:null,patchSha256:null};
   report.arms.push(arm);
   const validationStarted=performance.now();
   try{
    if(!candidate||!critique(candidate).approved)continue;
    arm.patchSha256=sha256(JSON.stringify(candidate.edits));
    const primarySha256=editInWorktree(primary,sourceFile,candidate.edits,plan.mutatedSha256);
    if(!checkCandidateKotlinSyntax(primary,[sourceFile],{base:head}).passed){
     arm.reason='PRIMARY_SYNTAX_REJECTED';continue;
    }
    const primaryRun=check(primary,name+'-primary');
    arm.primary=evidence(primaryRun);
    if(!measureAnskChain(primaryRun,{baseline:baselineRun}).passed){
     arm.reason='PRIMARY_NOT_GREEN';continue;
    }
    // Fresh checkout; no copied build outputs, no candidate-generated tests.
    const independent=allocate();
    try{
     editInWorktree(independent,sourceFile,[plan.mutation],plan.baselineSha256);
     const independentSha256=editInWorktree(independent,sourceFile,candidate.edits,plan.mutatedSha256);
     if(!checkCandidateKotlinSyntax(independent,[sourceFile],{base:head}).passed){
      arm.reason='INDEPENDENT_SYNTAX_REJECTED';continue;
     }
     const independentRun=check(independent,name+'-independent');
     arm.independent=evidence(independentRun);
     Object.assign(arm,verifyAnskTransferArm({
      baselineRun,primaryRun,independentRun,
      baselineSha256:plan.baselineSha256,mutatedSha256:plan.mutatedSha256,
      primarySha256,independentSha256
     }),{primarySha256,independentSha256});
    }finally{dispose(independent);}
   }finally{
    arm.validationMs=performance.now()-validationStarted;
    dispose(primary);
   }
  }
  return finish(report.arms.every(a=>a.verified)
   ?'TRANSFER_VERIFIED_SHADOW_ONLY':'TRANSFER_COMPARISON_FAILED');
 }finally{
  const errors=[];
  for(const wt of worktrees)try{dispose(wt);}catch(error){errors.push(error);}
  // An uncertain cleanup retains the shared lease for explicit inspection.
  if(errors.length)throw new AggregateError(errors,'ANSK_TRANSFER_CLEANUP_FAILED');
  release();
 }
}

export function parseAnskTransferArgs(argv){
 if(argv[0]!=='--once')throw Error('ANSK_TRANSFER_USAGE_ONCE_ONLY');
 const options={root:process.cwd()},seen=new Set();
 const keys={'--file':'sourceFile','--recipe':'recipeSha256','--order':'order'};
 for(let i=1;i<argv.length;i++){
  const key=argv[i];
  if(!keys[key]||seen.has(key)||!argv[i+1]||argv[i+1].startsWith('--'))
   throw Error('ANSK_UNSUPPORTED_OR_DUPLICATE_ARGUMENT');
  seen.add(key);options[keys[key]]=argv[++i];
 }
 if(!options.sourceFile||!options.recipeSha256)throw Error('ANSK_TRANSFER_REQUIRES_FILE_AND_RECIPE');
 return options;
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 try{
  const result=runAnskTransferLab(parseAnskTransferArgs(process.argv.slice(2)));
  process.stdout.write(JSON.stringify(result,null,2)+'\n');
  if(result.status!=='TRANSFER_VERIFIED_SHADOW_ONLY')process.exitCode=2;
 }catch(error){
  process.stderr.write('ANSK transfer stopped: '+String(error?.message??error)+'\n');
  process.exitCode=2;
 }
}
