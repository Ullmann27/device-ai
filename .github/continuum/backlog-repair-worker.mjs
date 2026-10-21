/**
 * Owner-accepted REPAIR task -> isolated repair -> independent verification ->
 * canonical backlog DONE/FAILED. No LLM, network dependency or shell from text.
 * This is deliberately a bounded M4 repair worker, not a general code author.
 */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {
  BacklogStore, nextEligible, newToken, sha256Hex, singleLine
} from './development-backlog.mjs';
import {
  git, createWorktree, removeWorktree, repairSession, runChain, evidenceSufficient, CHAINS
} from './repair-loop.mjs';
import {emptyLedger, loadLedger, saveLedger, recordSession} from './repair-ledger.mjs';
import {summarizeRepairHandoffs, verifyRepairHandoffs} from './teamleader-handoffs.mjs';
import {validateVerifiedReport} from './repair-publish.mjs';

const COMMIT=/^[0-9a-f]{40}$/;
const TASK_ID=/^[a-z][a-z0-9-]{2,63}$/;

/**
 * Resolve an existing ancestor before creating output paths. path.resolve()
 * alone is insufficient: an existing symlinked directory can otherwise
 * redirect backlog writes and artifacts into the source checkout.
 */
export function resolveOutputDestination(input){
  let cursor=path.resolve(input);
  const missing=[];
  while(!fs.existsSync(cursor)){
    try{
      if(fs.lstatSync(cursor).isSymbolicLink())
        throw Error('Dangling symlink in output destination');
    }catch(e){
      if(e.code!=='ENOENT')throw e;
    }
    const parent=path.dirname(cursor);
    if(parent===cursor)throw Error('Cannot resolve output destination');
    missing.unshift(path.basename(cursor));
    cursor=parent;
  }
  return path.join(fs.realpathSync(cursor),...missing);
}

export function validateOwnerRepairTask(task,head){
  if(!task||task.kind!=='REPAIR'||task.state!=='READY'||
     !TASK_ID.test(task.id??'')||task.source!=='owner'||
     !COMMIT.test(task.subject?.base??'')||task.subject.base!==head||
     !Object.hasOwn(CHAINS,task.subject?.chain))
    throw Error('Expected owner-accepted READY REPAIR task pinned to local HEAD and a fixed test chain');
  return task;
}

/**
 * Independent owner repair verification. A passing green test signal is NOT
 * sufficient when a candidate silently executes fewer or skips more tests
 * than the baseline. Keep this pure for exact negative regression tests.
 */
export function assessIndependentOwnerEvidence(baselineResults,verified){
  if(!Array.isArray(baselineResults)||!Array.isArray(verified?.results))
    throw Error('Owner repair evidence must include baseline and verification profiles');
  const sufficient=evidenceSufficient(baselineResults,verified.results);
  const profiles=verified.results.map(r=>({
    profile:r.profile,passed:r.passed===true,executed:r.tests?.executed??0,
    failed:r.tests?.failed??0,skipped:r.tests?.skipped??0
  }));
  const actualTests=profiles.reduce((sum,p)=>sum+p.executed,0);
  const passed=verified.passed===true&&sufficient.sufficient&&actualTests>0&&
    profiles.every(p=>p.passed&&p.failed===0);
  return {passed,profiles,actualTests,evidenceSufficient:sufficient.sufficient,
    reasons:sufficient.reasons};
}

/**
 * Claims exactly one owner-accepted task. A claim token belongs to this attempt
 * and never appears in the resulting public report. Build/profile commands are
 * from repair-loop.mjs, NEVER from a task description or untrusted evidence.
 */
export function executeOwnerRepairTask({root,file,out,id=null,budgetMinutes=20,actor='continuum-task-worker',candidateProvider=null}={}){
  if(!root||!file||!out||!Number.isSafeInteger(budgetMinutes)||budgetMinutes<1||budgetMinutes>30)
    throw Error('Valid root, backlog file, out and budgetMinutes=1..30 required');
  if(id!==null&&!TASK_ID.test(id))throw Error('Invalid task ID');
  const source=fs.realpathSync(root);
  const backlogPath=resolveOutputDestination(file),outRoot=resolveOutputDestination(out);
  if([backlogPath,outRoot].some(p=>p===source||p.startsWith(source+path.sep)))
    throw Error('Backlog and artifacts must stay outside the source checkout');
  if(outRoot===path.dirname(backlogPath)||outRoot===backlogPath||
     backlogPath.startsWith(outRoot+path.sep))
    throw Error('Artifact directory must not overlap backlog storage');
  const head=git(source,['rev-parse','HEAD']).stdout.trim();
  if(!COMMIT.test(head))throw Error('Cannot identify immutable HEAD');
  if(git(source,['status','--porcelain']).stdout.trim())
    throw Error('Source checkout must be clean before any autonomous task');
  const store=new BacklogStore(backlogPath,{actor});
  // Recover stale claims left by a crashed worker. The shared backlog FSM
  // marks them retryable only while the configured attempt budget remains.
  const snapshot=store.read().doc;
  if(snapshot.tasks.some(t=>t.state==='IN_PROGRESS'&&
       t.claim?.leaseUntil<Date.now())){
    store.apply([{op:'expire',actor,at:Date.now()}]);
  }
  const eligible=nextEligible(store.read().doc,['REPAIR'],['owner'],head,id);
  if(!eligible)return {status:'NO_READY_TASK',head};
  const task=validateOwnerRepairTask(eligible,head);
  const token=newToken(),at=Date.now();
  const claimed=store.apply([{op:'claim',actor,at,id:task.id,owner:actor,
    token,leaseMs:2*60*60*1000}]);
  if(claimed.results[0]?.ok!==true)throw Error('Claim did not succeed');
  const reportDir=path.join(outRoot,task.id+'-'+head.slice(0,12)+'-'+
    Date.now()+'-'+crypto.randomBytes(4).toString('hex'));
  let wt=null,independent=null;
  let outcome=null;
  const deadline=Date.now()+budgetMinutes*60_000;
  try{
    fs.mkdirSync(reportDir,{recursive:true,mode:0o700});
    const ledgerFile=path.join(path.dirname(backlogPath),'repair-ledger.json');
    const ledger=fs.existsSync(ledgerFile)?loadLedger(ledgerFile):emptyLedger();
    wt=createWorktree(source,head);
    const report=repairSession({wt,base:head,chain:task.subject.chain,
      out:reportDir,ledger,maxCandidates:3,deadline,candidateProvider});
    report.handoffs=summarizeRepairHandoffs(report);
    if(!verifyRepairHandoffs(report.handoffs))throw Error('Invalid multi-agent handoff receipts');
    const writeReport=()=>{
      const target=path.join(reportDir,'report.json');
      const pending=target+'.tmp-'+process.pid+'-'+crypto.randomBytes(6).toString('hex');
      try{
        fs.writeFileSync(pending,JSON.stringify(report,null,2)+'\n',{flag:'wx',mode:0o600});
        fs.renameSync(pending,target);
      }finally{fs.rmSync(pending,{force:true});}
    };
    writeReport();
    if(report.outcome!=='VERIFIED'||!report.verified){
      outcome={status:'FAILED',head,taskId:task.id,reason:report.outcome,
        report:path.join(reportDir,'report.json')};
    }else{
      const patchFile=path.join(reportDir,'repair.patch');
      const patch=fs.readFileSync(patchFile);
      const patchHash=sha256Hex(patch);
      if(!patch.length||patchHash!==report.verified.patchSha256||
         patch.toString('utf8')!==report.verified.patch)
        throw Error('Repair patch and verified report disagree');
      validateVerifiedReport(report,patch.toString('utf8'));
      // A second checkout prevents accidental reuse of candidate test state.
      independent=createWorktree(source,head);
      const check=git(independent,['apply','--check','--binary',patchFile],{allowFailure:true});
      if(check.status!==0)throw Error('Verified patch cannot be applied cleanly to base');
      git(independent,['apply','--binary',patchFile]);
      const independentOut=path.join(reportDir,'independent-check');
      fs.mkdirSync(independentOut,{recursive:true,mode:0o700});
      const verified=runChain(task.subject.chain,independent,independentOut,{deadline});
      const assessment=assessIndependentOwnerEvidence(report.baseline.results,verified);
      const {profiles,actualTests}=assessment;
      const independentlyPassed=assessment.passed;
      report.independent={passed:independentlyPassed,profiles,
        evidenceSufficient:assessment.evidenceSufficient,reasons:assessment.reasons};
      // Persist the independent verdict before the backlog can become DONE.
      writeReport();
      const independentReceipt=path.join(independentOut,'verification.json');
      const receipt={
        schemaVersion:1,head,taskId:task.id,patchSha256:patchHash,
        checkedAt:new Date().toISOString(),passed:independentlyPassed,
        evidenceSufficient:assessment.evidenceSufficient,reasons:assessment.reasons,
        executedTests:actualTests,profiles
      };
      fs.writeFileSync(independentReceipt,JSON.stringify(receipt,null,2)+'\n');
      if(!independentlyPassed){
        outcome={status:'FAILED',head,taskId:task.id,reason:'INDEPENDENT_CHECK_FAILED',
          profiles,independentReceipt,report:path.join(reportDir,'report.json')};
      }else{
        const submittedAt=Date.now();
        const evidence={type:'TEST_RUN',taskId:task.id,submissionToken:token,
          artifactSha256:patchHash,chain:task.subject.chain,commit:head,
          profiles,repair:null,at:submittedAt,by:actor};
        // Submit and complete must be one transactional backlog update.
        // A process crash cannot strand a verified job in VERIFYING.
        const completion=store.apply([
          {op:'submit',actor,at:submittedAt,id:task.id,token,
            artifact:{type:'PATCH',sha256:patchHash}},
          {op:'complete',actor,at:submittedAt,id:task.id,evidence}
        ],{stopOnError:true});
        if(completion.results.length!==2||completion.results.some(x=>x.ok!==true))
          throw Error('Verified backlog completion was not committed');
        outcome={status:'DONE',head,taskId:task.id,patchSha256:patchHash,
          patch:patchFile,profiles,independentReceipt,
          report:path.join(reportDir,'report.json'),approvedForMerge:false};
      }
    }
    // Do not discard independent verification failures: record them as
    // negative strategy experience, never as successful repair examples.
    saveLedger(ledgerFile,recordSession(ledger,report,{
      trigger:'accepted-owner-task',independentPass:outcome.status==='DONE'
    }));
    if(outcome.status==='FAILED'){
      store.apply([{op:'fail',actor,at:Date.now(),id:task.id,token,
        error:{code:outcome.reason==='INDEPENDENT_CHECK_FAILED'?'CHECKS_FAILED':'NO_VERIFIED_REPAIR',
          message:singleLine(outcome.reason,400)}}]);
    }
    fs.writeFileSync(path.join(reportDir,'task-result.json'),JSON.stringify(outcome,null,2)+'\n');
    return outcome;
  }catch(error){
    // Preserve the original error if a concurrent actor already moved the task.
    const state=store.read().doc.tasks.find(t=>t.id===task.id);
    if(state&&['IN_PROGRESS','VERIFYING'].includes(state.state)){
      try{store.apply([{op:'fail',actor,at:Date.now(),id:task.id,token,
        error:{code:'TASK_EXECUTION_FAILED',message:singleLine(error.message,400)}}]);}
      catch(e){error.cause=e;}
    }
    throw error;
  }finally{
    if(independent)removeWorktree(source,independent);
    if(wt)removeWorktree(source,wt);
  }
}
