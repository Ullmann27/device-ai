#!/usr/bin/env node
/**
 * Explicit one-shot ANSK import-repair laboratory.
 *
 * Never writes to the source checkout, main, a GitHub branch, the repair
 * ledger, a model checkpoint or an Android APK. All code mutations are inside
 * managed temporary git worktrees. Real compiler and JUnit observations come
 * exclusively from the existing fixed Android repair check chain.
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {
 git,createWorktree,removeWorktree,resetWorktree,boundedWorktreeFile,
 runChain,checkCandidateKotlinSyntax,evidenceSufficient,rankCandidates
} from './repair-loop.mjs';
import {planInverseKotlinImportMutation,proposeCandidates,applyEdits} from './repair-strategies.mjs';
import {critique} from './repair-critic.mjs';
import {sha256} from './repair-diagnostics.mjs';
import {validateTriplePhaseSample} from './repair-cycle-safety.mjs';
import {assertAnskSource,measureAnskChain,verifyAnskExperiment} from './ansk-lab-contract.mjs';
import {acquireContinuumBuildLease} from './shared-build-lease.mjs';

const BRANCH='astra/continuum-experiment-charter-20261019';
const RUNTIME_BUDGET_MS=45*60_000;
const MAX_CANDIDATES=3;

function ensureCleanSource(root){
  const actual=fs.realpathSync(root);
  const checkout=git(actual,['rev-parse','--show-toplevel']).stdout.trim();
  if(fs.realpathSync(checkout)!==actual)throw Error('ANSK_ROOT_NOT_GIT_TOPLEVEL');
  const branch=git(actual,['symbolic-ref','--short','HEAD']).stdout.trim();
  if(branch!==BRANCH)throw Error('ANSK_REQUIRES_EXPERIMENTAL_BRANCH');
  const dirty=git(actual,['status','--porcelain','--untracked-files=normal']).stdout.trim();
  if(dirty)throw Error('ANSK_SOURCE_CHECKOUT_NOT_CLEAN');
  const head=git(actual,['rev-parse','HEAD']).stdout.trim();
  if(!/^[0-9a-f]{40}$/.test(head))throw Error('ANSK_INVALID_HEAD');
  return {root:actual,head};
}
function assertSourceUnchanged(root,head){
  const actual=ensureCleanSource(root);
  if(actual.head!==head)throw Error('ANSK_HEAD_CHANGED');
}

function stateRoot(root){
  const dir=path.join(os.homedir(),'.local','state','device-ai-continuum','ansk-lab');
  fs.mkdirSync(dir,{recursive:true,mode:0o700});
  const actual=fs.realpathSync(dir);
  if(actual===root||actual.startsWith(root+path.sep)||
     fs.lstatSync(dir).isSymbolicLink())
    throw Error('ANSK_UNSAFE_STATE_ROOT');
  return actual;
}

function readCode(wt,relative){
  const absolute=boundedWorktreeFile(wt,relative);
  if(fs.statSync(absolute).size>250_000)throw Error('ANSK_SOURCE_TOO_LARGE');
  return fs.readFileSync(absolute,'utf8');
}
function editInWorktree(wt,sourceFile,edits,expectedBeforeSha256){
  if(!Array.isArray(edits)||edits.length!==1||edits[0].file!==sourceFile)
    throw Error('ANSK_SINGLE_FILE_EDITS_ONLY');
  const source=readCode(wt,sourceFile);
  if(sha256(source)!==expectedBeforeSha256)throw Error('ANSK_STALE_SOURCE_EDIT');
  const newSource=applyEdits(p=>p===sourceFile?source:null,edits).get(sourceFile);
  if(typeof newSource!=='string'||newSource===source)throw Error('ANSK_EMPTY_EDIT');
  fs.writeFileSync(boundedWorktreeFile(wt,sourceFile),newSource,'utf8');
  const observed=readCode(wt,sourceFile);
  if(observed!==newSource)throw Error('ANSK_EDIT_DID_NOT_PERSIST');
  return sha256(observed);
}

function phaseDir(root,phase){
  const dir=path.join(root,phase);
  fs.mkdirSync(dir,{recursive:true,mode:0o700});
  return dir;
}

function diagnosticFor(result,sourceFile,symbol){
  return (result.diagnostics??[]).find(d=>
    d.file===sourceFile&&d.kind==='UNRESOLVED_REFERENCE'&&d.symbol===symbol)??null;
}

function writeImmutableRecipe(store,recipe){
  const json=JSON.stringify(recipe,null,2)+'\n';
  const dir=path.join(store,'shadow-recipes');
  fs.mkdirSync(dir,{recursive:true,mode:0o700});
  const target=path.join(dir,recipe.recipeSha256+'.json');
  const tmp=path.join(dir,'.'+crypto.randomBytes(12).toString('hex')+'.tmp');
  fs.writeFileSync(tmp,json,{flag:'wx',mode:0o600});
  try{
    // Hard-link creation is atomic and never overwrites a prior observation.
    try{fs.linkSync(tmp,target);}
    catch(error){
      if(error.code!=='EEXIST')throw error;
      if(fs.readFileSync(target,'utf8')!==json)
        throw Error('ANSK_RECIPE_HASH_COLLISION_OR_TAMPERING');
    }
  }finally{fs.rmSync(tmp,{force:true});}
  return target;
}

function fail(status,head,detail=null){
  return {schema:'continuum-ansk-lab-result',version:1,
    status,head,detail,authority:'SHADOW_ONLY',
    modelTrainingAuthorized:false,productionWriteAuthorized:false};
}

/**
 * Executes fixed checks in two independent managed worktrees.
 * Structural validator results do not authorize ledger writes or model training.
 */
export function runAnskImportLab({root,sourceFile,importFqn,deadlineMs=RUNTIME_BUDGET_MS}={}){
  assertAnskSource(sourceFile,importFqn);
  if(!Number.isSafeInteger(deadlineMs)||deadlineMs<60_000||
     deadlineMs>RUNTIME_BUDGET_MS)throw Error('ANSK_INVALID_TIME_BUDGET');
  const checkout=ensureCleanSource(root);
  const {head}=checkout;
  const store=stateRoot(checkout.root);
  // Best-effort collision guard for an already active local teamleader.
  // The ANSK lock protects lab-vs-lab races. Future Android dispatch must
  // hold a common atomic lease for all Gradle-capable workers.
  const leaderSuffix=sha256(checkout.root).slice(0,12);
  const leaderLock=path.join(os.homedir(),'.local','state',
    'device-ai-continuum',leaderSuffix,'teamleader-state.json.lock');
  if(fs.existsSync(leaderLock))throw Error('ANSK_TEAMLEADER_ALREADY_RUNNING');
  const lock=path.join(store,'ansk-once.lock');
  // A stale lock is not automatically stolen: an expired timer cannot prove
  // an old Gradle process has terminated. An operator must investigate first.
  const fd=fs.openSync(lock,'wx',0o600);
  // Atomic lease shared by ANSK, the local Teamleader and Termux builder.
  // On refusal, release this experiment's own lock without running Gradle.
  let releaseBuildLease;
  try{releaseBuildLease=acquireContinuumBuildLease(checkout.root);}
  catch(error){fs.closeSync(fd);fs.rmSync(lock,{force:true});throw error;}
  const deadline=Date.now()+deadlineMs;
  const runId=crypto.randomBytes(12).toString('hex');
  const out=path.join(store,'runs',runId);
  let primary=null,independent=null;
  try{
    fs.mkdirSync(out,{recursive:true,mode:0o700});
    primary=createWorktree(checkout.root,head);
    const original=readCode(primary,sourceFile);
    const plan=planInverseKotlinImportMutation({file:sourceFile,source:original,importFqn});
    if(!plan)return fail('NO_SAFE_MUTATION',head);
    const baselineRun=runChain('android',primary,phaseDir(out,'green0'),{deadline,forceRerun:true});
    if(!measureAnskChain(baselineRun).passed)
      return fail('BLOCKED_BASELINE_NOT_GREEN',head);

    const mutHash=editInWorktree(primary,sourceFile,[plan.mutation],plan.baselineSha256);
    if(mutHash!==plan.mutatedSha256)throw Error('ANSK_MUTATION_HASH_MISMATCH');
    const mutatedRun=runChain('android',primary,phaseDir(out,'red-mutation'),{deadline,forceRerun:true});
    const diagnostic=diagnosticFor(mutatedRun,sourceFile,plan.symbol);
    if(!measureAnskChain(mutatedRun,{allowFailure:true}).passed||!diagnostic)
      return fail('DISCARDED_MUTATION_NOT_REPRODUCED',head);

    const context={
      readFile:relative=>{
        try{return readCode(primary,relative);}catch{return null;}
      },
      index:null,jsSiblings:new Map(),changedInBase:[],experience:[]
    };
    const proposed=proposeCandidates(
      mutatedRun.diagnostics.filter(d=>d.file===sourceFile),context);
    const candidates=rankCandidates(proposed).filter(c=>
      c.diagnostic===diagnostic.fingerprint&&
      c.strategy==='KOTLIN_MISSING_IMPORT'&&
      Array.isArray(c.edits)&&c.edits.length===1&&
      c.edits[0].file===sourceFile&&
      c.edits[0].newText.includes('import '+importFqn)).slice(0,MAX_CANDIDATES);
    if(!candidates.length)return fail('NO_DETERMINISTIC_IMPORT_CANDIDATE',head);
    const attempted=[],observations=[];
    for(const candidate of candidates){
      if(Date.now()>=deadline)return fail('ANSK_DEADLINE_EXHAUSTED',head);
      const verdict=critique(candidate);
      if(!verdict.approved)continue;
      if(attempted.includes(candidate.strategy))continue;
      attempted.push(candidate.strategy);
      resetWorktree(primary,head);
      const reMutated=editInWorktree(primary,sourceFile,[plan.mutation],plan.baselineSha256);
      if(reMutated!==mutHash)throw Error('ANSK_NONDETERMINISTIC_MUTATION');
      const candidateHash=editInWorktree(primary,sourceFile,candidate.edits,mutHash);
      const syntax=checkCandidateKotlinSyntax(primary,[sourceFile],{base:head});
      if(!syntax.passed)continue;
      const primaryRun=runChain('android',primary,phaseDir(out,'primary-'+candidate.id),{deadline,forceRerun:true});
      const primaryEvidence=evidenceSufficient(baselineRun.results,primaryRun.results);
      const failedObs={strategy:candidate.strategy,candidateId:candidate.id,source:'local',primaryRun};
      if(!measureAnskChain(primaryRun,{baseline:baselineRun}).passed||
         !primaryEvidence.sufficient){
        observations.push(failedObs);
        continue;
      }
      independent=createWorktree(checkout.root,head);
      const beforeIndependent=readCode(independent,sourceFile);
      if(sha256(beforeIndependent)!==plan.baselineSha256)
        throw Error('ANSK_INDEPENDENT_BASELINE_CHANGED');
      const reMutatedIndependent=editInWorktree(independent,sourceFile,
        [plan.mutation],plan.baselineSha256);
      if(reMutatedIndependent!==mutHash)throw Error('ANSK_INDEPENDENT_MUTATION_MISMATCH');
      const independentHash=editInWorktree(independent,sourceFile,candidate.edits,mutHash);
      if(candidateHash!==independentHash)
        throw Error('ANSK_INDEPENDENT_PATCH_MISMATCH');
      const syntax2=checkCandidateKotlinSyntax(independent,[sourceFile],{base:head});
      if(!syntax2.passed)return fail('INDEPENDENT_SYNTAX_FAILED',head);
      const independentRun=runChain('android',independent,phaseDir(out,'independent-'+candidate.id),{deadline,forceRerun:true});
      const independentEvidence=evidenceSufficient(baselineRun.results,independentRun.results);
      if(!measureAnskChain(independentRun,{baseline:baselineRun}).passed||
         !independentEvidence.sufficient)
        return fail('INDEPENDENT_FULL_CHAIN_FAILED',head);
      assertSourceUnchanged(checkout.root,head);
      const patchSha256=sha256(JSON.stringify(candidate.edits));
      observations.push({...failedObs,independentRun,patchSha256,
        independentPatchSha256:patchSha256});
      const verified=verifyAnskExperiment({
        head,sourceFile,importFqn,baselineSha256:plan.baselineSha256,
        mutatedSha256:mutHash,primarySha256:candidateHash,
        independentSha256:independentHash,patchSha256,
        baselineRun,mutatedRun,primaryRun,independentRun,
        diagnostic,strategiesAttempted:attempted,
        successfulStrategy:candidate.strategy
      });
      if(!verified.verified)
        return fail('STRUCTURAL_TRIPLE_PHASE_REJECTED',head,verified.reason);
      const shadow=observations.length>=2?validateTriplePhaseSample({
        baselineRun,mutatedRun,candidateRuns:observations,mutationFile:sourceFile
      }):null;
      const receiptPath=writeImmutableRecipe(store,verified.recipe);
      return {...fail('TRIPLE_PHASE_VERIFIED_SHADOW_RECIPE',head),
        recipeSha256:verified.recipe.recipeSha256,
        candidateStrategy:candidate.strategy,
        baselineExecuted:verified.recipe.baselineExecuted,
        primaryExecuted:verified.recipe.primaryExecuted,
        independentExecuted:verified.recipe.independentExecuted,
        contrastiveTupleAvailable:shadow?.valid===true,
        receiptPath};
    }
    return fail('NO_INDEPENDENTLY_VERIFIED_CANDIDATE',head);
  }finally{
    let cleanupFailure=null;
    for(const wt of [independent,primary]){
      if(!wt)continue;
      try{removeWorktree(checkout.root,wt);}
      catch(err){cleanupFailure??=err;}
    }
    try{fs.closeSync(fd);}catch(error){cleanupFailure??=error;}
    // Refuse another overlapping experiment when cleanup itself is uncertain.
    // Locks remain for operator investigation after a failed worktree cleanup.
    if(!cleanupFailure){
      try{fs.rmSync(lock,{force:true});}
      catch(error){cleanupFailure??=error;}
    }
    try{releaseBuildLease();}catch(error){cleanupFailure??=error;}
    if(cleanupFailure)throw cleanupFailure;
  }
}

function parseArgs(argv){
  if(argv[0]!=='--once')throw Error('ANSK_USAGE_ONCE_ONLY');
  let sourceFile=null,importFqn=null;
  for(let i=1;i<argv.length;i++){
    const arg=argv[i];
    if(arg==='--file'&&sourceFile===null)sourceFile=argv[++i]??null;
    else if(arg==='--import'&&importFqn===null)importFqn=argv[++i]??null;
    else throw Error('ANSK_UNSUPPORTED_OR_DUPLICATE_ARGUMENT');
  }
  assertAnskSource(sourceFile,importFqn);
  return {root:process.cwd(),sourceFile,importFqn};
}
async function main(){
  const args=parseArgs(process.argv.slice(2));
  const result=runAnskImportLab(args);
  process.stdout.write(JSON.stringify(result,null,2)+'\n');
  if(result.status!=='TRIPLE_PHASE_VERIFIED_SHADOW_RECIPE')
    process.exitCode=2;
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))
  main().catch(error=>{
    process.stderr.write('ANSK research lab failed: '+String(error?.message??error)+'\n');
    process.exitCode=2;
  });
