/** Research-only genuine CCC1 dual-worktree proof. No arbitrary runner, shell,
 * model training, ledger write or permission promotion can be supplied.
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import {validateCodingTask,taskFingerprint,isCodingPath} from './coding-task.mjs';
import {ensureCcc1StateOutsideSource} from './ccc1-path-policy.mjs';
import {isIsolatedExecutor,validIsolationEvidence} from './isolated-executor.mjs';
import {git,createWorktree,removeWorktree,runChain,evidenceSufficient,boundedWorktreeFile} from './repair-loop.mjs';

const HEX64=/^[a-f0-9]{64}$/;
const sha256=v=>crypto.createHash('sha256').update(v).digest('hex');
const isTestFile=x=>/(^|\/)(test|tests|__tests__|specs|fixtures)(\/|$)|(?:^|[-_.])(?:test|spec)\.[cm]?[jt]s$|(?:Test|Spec)\.kt$/i.test(x);
export const CCC1_RESEARCH_OUTCOMES=Object.freeze({
  PASS:'DUAL_WORKTREE_CHECKS_PASS_REVIEW_REQUIRED',
  INTERRUPTED:'INTERRUPTED_NO_LEARNING',
  REJECTED:'REJECTED_NO_LEARNING',
  FAILED:'CHECKS_FAILED_NO_LEARNING'
});
function report(status,reason,ids={},before=null,after=null,fresh=null){
  const slim=v=>Object.freeze((v?.results??[]).map(t=>Object.freeze({
    profile:t.profile,passed:t.passed,executed:t.tests?.executed??0,
    failed:t.tests?.failed??0,skipped:t.tests?.skipped??0
  })));
  return Object.freeze({status,reason,verifiedSuccess:false,learningEligible:false,
    promotionEligible:false,ownerReviewRequired:true,...ids,
    before:slim(before),after:slim(after),fresh:slim(fresh)});
}
function testDigests(wt){
  const names=git(wt,['ls-files','-z']).stdout.split('\0').filter(Boolean).filter(isTestFile);
  if(!names.length||names.length>5000)throw Error('TEST_SET_INVALID');
  return new Map(names.map(name=>{
    const filename=boundedWorktreeFile(wt,name),stat=fs.lstatSync(filename);
    if(!stat.isFile()||stat.isSymbolicLink()||stat.size>2000000)throw Error('TEST_FILE_UNSAFE');
    return [name,sha256(fs.readFileSync(filename))];
  }));
}
function testsUnchanged(wt,expected){
  const actual=testDigests(wt);
  if(actual.size!==expected.size)throw Error('TEST_SET_CHANGED');
  for(const [name,hash] of expected)if(actual.get(name)!==hash)throw Error('TEST_FILE_TAMPERED');
}
function clean(wt,base){
  if(git(wt,['rev-parse','HEAD']).stdout.trim()!==base)throw Error('SOURCE_COMMIT_DRIFT');
  if(git(wt,['status','--porcelain','--untracked-files=no']).stdout.trim())throw Error('WORKTREE_NOT_CLEAN');
}
function changedOnly(wt,file){
  const changed=git(wt,['diff','--name-only','-z','HEAD']).stdout.split('\0').filter(Boolean);
  if(changed.length!==1||changed[0]!==file||
     git(wt,['diff','--cached','--name-only','-z']).stdout.trim())throw Error('PATCH_SCOPE_INVALID');
}
function measured(run){
  if(run?.results?.length!==1)throw Error('TEST_RUN_INCOMPLETE');
  const t=run.results[0];
  if(t.profile!=='node-continuum'||!validIsolationEvidence(t.isolation,t.profile))
    throw Error('ISOLATION_EVIDENCE_MISSING');
  if(!Number.isSafeInteger(t.tests?.executed)||t.tests.executed<1||
     !Number.isSafeInteger(t.tests?.failed)||t.tests.failed<0||
     t.timedOut||t.stoppedByOwner||[137,143].includes(t.exitCode))
    throw Error('TEST_RUN_INTERRUPTED');
  return t;
}
/**
 * Strict suite-coverage parity for the same fixed CCC1 test profile.
 * A green exit code is insufficient if tests vanish between RED and GREEN.
 * This compares structured evidence, not test authenticity; the OS executor,
 * immutable test sources and owner grant remain separate mandatory gates.
 */
export function assertCcc1TestEvidenceParity(baselineRun,candidateRun){
  const baseline=baselineRun?.results?.[0];
  const candidate=candidateRun?.results?.[0];
  if(baselineRun?.results?.length!==1||candidateRun?.results?.length!==1||
     baseline?.profile!=='node-continuum'||candidate?.profile!==baseline.profile||
     baseline.testProfile!==true||candidate.testProfile!==true)
    throw Error('TEST_PROFILE_PARITY_INVALID');
  const required=['executed','failed','skipped','passed','total','placeholders'];
  for(const evidence of [baseline.tests,candidate.tests]){
    if(!evidence||required.some(k=>!Number.isSafeInteger(evidence[k])||evidence[k]<0)||
       evidence.executed<1||evidence.passed+evidence.failed!==evidence.executed)
      throw Error('TEST_COUNT_EVIDENCE_INVALID');
  }
  const identities=run=>run.tests.executedCaseHashes;
  const baseIds=identities(baseline),candidateIds=identities(candidate);
  for(const [test,ids] of [[baseline,baseIds],[candidate,candidateIds]]){
    if(!Array.isArray(ids)||ids.length!==test.tests.executed||
       ids.some(id=>typeof id!=='string'||!HEX64.test(id))||
       new Set(ids).size!==ids.length)
      throw Error('EXECUTED_TEST_IDENTITIES_INCOMPLETE');
  }
  const candidates=new Set(candidateIds);
  if(baseIds.some(id=>!candidates.has(id)))
    throw Error('EXECUTED_TEST_IDENTITY_MISSING');
  if(candidate.tests.executed<baseline.tests.executed)
    throw Error('EXECUTED_TEST_COVERAGE_REGRESSION');
  if(candidate.tests.skipped!==baseline.tests.skipped)
    throw Error('SKIPPED_TEST_SET_CHANGED');
  if(baseline.tests.skipped>0){
    const digest=baseline.tests.skippedCaseDigest;
    if(typeof digest!=='string'||!HEX64.test(digest)||
       digest!==candidate.tests.skippedCaseDigest)
      throw Error('SKIPPED_TEST_IDENTITY_CHANGED');
  }
  return Object.freeze({
    profile:baseline.profile,baselineExecuted:baseline.tests.executed,
    candidateExecuted:candidate.tests.executed,skipped:baseline.tests.skipped
  });
}
function apply(wt,file,originalDigest,text){
  const full=boundedWorktreeFile(wt,file),current=fs.readFileSync(full);
  if(sha256(current)!==originalDigest)throw Error('SOURCE_BYTES_CHANGED');
  if(current.toString('utf8')===text)throw Error('NO_OP_PATCH');
  fs.writeFileSync(full,text);
  changedOnly(wt,file);
}
/** Data binding only. Never grants execution permission or verifies a patch. */
export function validateCcc1CandidateBinding({typedTask,candidate,file}={}){
  const task=validateCodingTask(typedTask);
  if(task.chain!=='node'||task.kind!=='BUGFIX'||task.allowedPaths.length!==1||
     task.allowedPaths[0]!==file||!isCodingPath(file)||isTestFile(file))
    throw Error('UNSUPPORTED_SCOPE');
  if(candidate?.status!=='CANDIDATE_UNVERIFIED'||
     candidate.sourceCommit!==task.sourceCommit||
     candidate.taskSha256!==taskFingerprint(task)||
     !HEX64.test(candidate.sourceSha256??'')||
     !HEX64.test(candidate.candidateSha256??'')||
     candidate.sourceSha256===candidate.candidateSha256||
     typeof candidate.code!=='string'||candidate.code.length<1||candidate.code.length>16000||
     sha256(candidate.code)!==candidate.candidateSha256)
    throw Error('CANDIDATE_IDENTITY_MISMATCH');
  const immutableCandidate=Object.freeze({
    status:'CANDIDATE_UNVERIFIED',sourceCommit:candidate.sourceCommit,
    taskSha256:candidate.taskSha256,sourceSha256:candidate.sourceSha256,
    candidateSha256:candidate.candidateSha256,code:candidate.code
  });
  return Object.freeze({task,candidate:immutableCandidate,file});
}
export function validateCcc1ResearchRequest({typedTask,candidate,file,executor}={}){
  if(!isIsolatedExecutor(executor))throw Error('BRANDED_OS_EXECUTOR_REQUIRED');
  return validateCcc1CandidateBinding({typedTask,candidate,file});
}
/** Read the existing canonical CCC1 owner-approved task grant, not a model claim.
 * stateDir must be the trusted worker's own configured state directory.
 */
export function verifyCcc1OwnerGrant({projectRoot,stateDir,typedTask}={}){
  const task=validateCodingTask(typedTask);
  const state=ensureCcc1StateOutsideSource(projectRoot,stateDir);
  const dir=path.join(state,'coding-grants');
  if(fs.realpathSync(dir)!==dir)throw Error('OWNER_GRANT_DIR_UNSAFE');
  const file=path.join(dir,task.id+'.json');
  const stat=fs.lstatSync(file);
  if(!stat.isFile()||stat.isSymbolicLink()||stat.size>16000||
    fs.realpathSync(file)!==file)throw Error('OWNER_GRANT_FILE_UNSAFE');
  const stored=JSON.parse(fs.readFileSync(file,'utf8'));
  if(stored?.task?.id!==task.id||
    stored.fingerprint!==taskFingerprint(task)||
    !/^t-[a-f0-9]{16}$/.test(stored.ccc1TaskId??'')||
    taskFingerprint(validateCodingTask(stored.task))!==stored.fingerprint)
    throw Error('OWNER_GRANT_MISMATCH');
  return Object.freeze({taskId:task.id,fingerprint:stored.fingerprint,ccc1TaskId:stored.ccc1TaskId});
}
export function runIndependentCcc1PatchVerification({typedTask,candidate,file,projectRoot,stateDir,executor,deadline=Date.now()+900000}={}){
  const {task,candidate:approved}=validateCcc1ResearchRequest({typedTask,candidate,file,executor});
  if(!Number.isSafeInteger(deadline)||deadline<=Date.now()||deadline>Date.now()+1200000)
    throw Error('INVALID_DEADLINE');
  const project=fs.realpathSync(projectRoot);
  // Exact same owner-bound grant format as coding-orchestrator.mjs.
  verifyCcc1OwnerGrant({projectRoot:project,stateDir,typedTask:task});
  if(!fs.statSync(project).isDirectory()||
     git(project,['rev-parse','HEAD']).stdout.trim()!==task.sourceCommit||
     git(project,['status','--porcelain','--untracked-files=no']).stdout.trim())
    throw Error('UNSAFE_SOURCE_CHECKOUT');
  const ids={sourceCommit:task.sourceCommit,taskSha256:approved.taskSha256,
    sourceSha256:approved.sourceSha256,candidateSha256:approved.candidateSha256};
  let primary=null,fresh=null,out=null,red=null,green=null,second=null;
  try{
    primary=createWorktree(project,task.sourceCommit);
    ensureCcc1StateOutsideSource(project,primary);
    clean(primary,task.sourceCommit);
    if(sha256(fs.readFileSync(boundedWorktreeFile(primary,file)))!==approved.sourceSha256)
      throw Error('SOURCE_DIGEST_MISMATCH');
    const testSet=testDigests(primary);
    out=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-independent-verify-'));
    red=runChain('node',primary,out,{deadline,executor});
    testsUnchanged(primary,testSet);clean(primary,task.sourceCommit);
    const before=measured(red);
    if(before.passed||before.tests.failed<1)
      return report(CCC1_RESEARCH_OUTCOMES.REJECTED,'NO_REPRODUCED_BASELINE_FAILURE',ids,red);
    apply(primary,file,approved.sourceSha256,approved.code);
    green=runChain('node',primary,out,{deadline,executor});
    testsUnchanged(primary,testSet);changedOnly(primary,file);
    const primaryEvidence=measured(green);
    assertCcc1TestEvidenceParity(red,green);
    if(!primaryEvidence.passed||!evidenceSufficient(red.results,green.results).sufficient)
      return report(CCC1_RESEARCH_OUTCOMES.FAILED,'CANDIDATE_NOT_GREEN',ids,red,green);
    fresh=createWorktree(project,task.sourceCommit);
    ensureCcc1StateOutsideSource(project,fresh);
    clean(fresh,task.sourceCommit);testsUnchanged(fresh,testSet);
    apply(fresh,file,approved.sourceSha256,approved.code);
    second=runChain('node',fresh,out,{deadline,executor});
    testsUnchanged(fresh,testSet);changedOnly(fresh,file);
    const verified=measured(second),first=measured(green);
    assertCcc1TestEvidenceParity(red,second);
    assertCcc1TestEvidenceParity(green,second);
    if(!verified.passed||!evidenceSufficient(red.results,second.results).sufficient||
       verified.tests.executed<first.tests.executed||verified.tests.skipped>first.tests.skipped)
      return report(CCC1_RESEARCH_OUTCOMES.FAILED,'FRESH_RUN_NOT_GREEN',ids,red,green,second);
    return report(CCC1_RESEARCH_OUTCOMES.PASS,'DUAL_RUN_REVIEW_REQUIRED',ids,red,green,second);
  }catch(error){
    return report(CCC1_RESEARCH_OUTCOMES.INTERRUPTED,
      /^(SOURCE_|PATCH_|TEST_SET_CHANGED|TEST_FILE_TAMPERED|NO_OP)/.test(error.message)
        ?'INTEGRITY_ABORT':'INFRASTRUCTURE_OR_EXECUTION_ABORT',
      ids,red,green,second);
  }finally{
    const errors=[];
    for(const wt of [fresh,primary])if(wt)try{removeWorktree(project,wt);}catch(e){errors.push(e);}
    if(out)try{fs.rmSync(out,{recursive:true,force:true});}catch(e){errors.push(e);}
    if(errors.length)throw new AggregateError(errors,'WORKTREE_TEARDOWN_FAILED');
  }
}
