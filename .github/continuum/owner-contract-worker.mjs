/**
 * M4.2 bounded TEST task: owner acceptance -> compiler-independent Node
 * contract synthesis -> worktree tests -> second independent worktree ->
 * reviewable binary Git patch -> canonical DONE evidence.
 *
 * Neither task text nor model output controls a shell command. The original
 * repository remains unchanged; tests execute in Git worktrees, not an OS jail.
 */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {
  BacklogStore,nextEligible,newToken,sha256Hex,singleLine
} from './development-backlog.mjs';
import {
  git,createWorktree,removeWorktree,runChain,boundedWorktreeFile
} from './repair-loop.mjs';
import {resolveOutputDestination} from './backlog-repair-worker.mjs';
import {proposeNodeContractTest} from './node-contract-synthesizer.mjs';

const HEAD=/^[0-9a-f]{40}$/;
const ID=/^[a-z][a-z0-9-]{2,63}$/;
const MODULE=/^\.github\/continuum\/[A-Za-z][A-Za-z0-9_-]{0,63}\.mjs$/;

export function verifyContractTestEvidence(baseline,candidate,independent,expectedNewTests){
 const count=r=>r?.results?.reduce((n,p)=>n+(p.tests?.executed??0),0)??0;
 const good=r=>r?.passed===true&&r.results?.length===1&&
  r.results[0].profile==='node-continuum'&&r.results[0].testProfile===true&&
  r.results[0].passed===true&&r.results[0].tests?.failed===0;
 const before=count(baseline),after=count(candidate),again=count(independent);
 return {passed:good(baseline)&&good(candidate)&&good(independent)&&
    Number.isSafeInteger(expectedNewTests)&&expectedNewTests>0&&
    before>0&&after>=before+expectedNewTests&&again>=after&&
    candidate.results[0].tests.skipped<=baseline.results[0].tests.skipped&&
    independent.results[0].tests.skipped<=candidate.results[0].tests.skipped,
   baselineExecuted:before,candidateExecuted:after,independentExecuted:again,
   expectedNewTests};
}

function profileEvidence(chain){
 return chain.results.map(r=>({
  profile:r.profile,passed:r.passed,
  executed:r.tests.executed,failed:r.tests.failed,skipped:r.tests.skipped
 }));
}
function safeRead(wt,rel){
 return fs.readFileSync(boundedWorktreeFile(wt,rel),'utf8');
}
function cleanDiff(wt,rel){
 const changed=git(wt,['status','--porcelain','--untracked-files=all']).stdout.trim();
 const permitted=' A '+rel;
 if(changed!==permitted.trim())
  throw Error('Worktree contains changes besides the new contract test: '+changed.slice(0,240));
}

export function executeOwnerContractTestTask({
 root,file,out,id=null,budgetMinutes=20,actor='continuum-contract-worker'
}={}){
 if(!root||!file||!out||!Number.isSafeInteger(budgetMinutes)||
    budgetMinutes<1||budgetMinutes>30||id!==null&&!ID.test(id))
  throw Error('Invalid root, file, out, task ID or budget');
 const source=fs.realpathSync(root);
 const backlog=resolveOutputDestination(file),outRoot=resolveOutputDestination(out);
 if([backlog,outRoot].some(x=>x===source||x.startsWith(source+path.sep))||
    backlog===outRoot||backlog.startsWith(outRoot+path.sep)||
    outRoot.startsWith(backlog+path.sep))
  throw Error('Contract state and reports must be outside source and separate');
 const head=git(source,['rev-parse','HEAD']).stdout.trim();
 if(!HEAD.test(head)||git(source,['status','--porcelain']).stdout.trim())
  throw Error('Expected a clean checkout with immutable Git HEAD');
 const store=new BacklogStore(backlog,{actor});
 if(store.read().doc.tasks.some(t=>t.kind==='TEST'&&t.source==='owner'&&
   t.state==='IN_PROGRESS'&&t.claim?.leaseUntil<Date.now()))
  store.apply([{op:'expire',actor,at:Date.now()}]);
 const task=nextEligible(store.read().doc,['TEST'],['owner'],head,id);
 if(!task)return {status:'NO_READY_TASK',head};
 if(task.kind!=='TEST'||task.source!=='owner'||task.state!=='READY'||
    !MODULE.test(task.subject?.modulePath??'')||
    task.subject.operation!=='ASSERT_NODE_EXPORT'||task.subject.chain!=='node')
  throw Error('Unrecognized accepted owner contract task');
 const token=newToken(),start=Date.now(),deadline=start+budgetMinutes*60_000;
 store.apply([{op:'claim',actor,at:start,id:task.id,owner:actor,token,leaseMs:7200000}]);
 const runDir=path.join(outRoot,task.id+'-'+head.slice(0,12)+'-'+
  Date.now()+'-'+crypto.randomBytes(4).toString('hex'));
 let candidateWt=null,verifyWt=null,result=null;
 try{
  fs.mkdirSync(runDir,{recursive:true,mode:0o700});
  candidateWt=createWorktree(source,head);
  const baseOut=path.join(runDir,'baseline');
  fs.mkdirSync(baseOut,{recursive:true,mode:0o700});
  const baseRun=runChain('node',candidateWt,baseOut,{deadline});
  if(!baseRun.passed||baseRun.results[0]?.tests.executed<=0)
   throw Error('BASELINE_NOT_GREEN: refusing to add tests to failing baseline');
  const modulePath=task.subject.modulePath;
  const contractPath=modulePath.replace(/\.mjs$/,'.contract.test.mjs');
  const existingContract=path.join(candidateWt,contractPath);
  if(fs.existsSync(existingContract)||fs.existsSync(path.dirname(existingContract))===false)
   throw Error('Contract test target already exists or has no existing parent');
  const sourceText=safeRead(candidateWt,modulePath);
  const proposed=proposeNodeContractTest({
    files:[{path:modulePath,content:sourceText}],task:{...task,state:'READY'}
  });
  if(!proposed.candidate||!proposed.candidate.critic?.approved)
   throw Error('SYNTHESIS_REJECTED: '+String(proposed.reason).slice(0,160));
  if(proposed.candidate.edits.length!==1||
     proposed.candidate.edits[0].file!==contractPath||
     proposed.candidate.edits[0].oldText!=='')
   throw Error('Generator proposed a non-contract edit');
  const spec=JSON.parse(task.subject.casesJson);
  // Exclusive creation fails closed if an unexpected or symlinked file exists.
  fs.writeFileSync(existingContract,proposed.candidate.edits[0].newText,{
   encoding:'utf8',flag:'wx',mode:0o600
  });
  const remaining=deadline-Date.now();
  if(remaining<=0)throw Error('DEADLINE_EXCEEDED');
  const syntax=spawnSync(process.execPath,['--check',existingContract],{
   cwd:candidateWt,encoding:'utf8',timeout:Math.min(30000,remaining),shell:false
  });
  if(syntax.status!==0||syntax.error||syntax.signal)
   throw Error('GENERATED_SYNTAX_FAILED: '+String(syntax.stderr).slice(-180));
  const canOut=path.join(runDir,'candidate');
  fs.mkdirSync(canOut,{recursive:true,mode:0o700});
  const candidateRun=runChain('node',candidateWt,canOut,{deadline});
  if(!candidateRun.passed)
   throw Error('GENERATED_TESTS_FAILED');
  git(candidateWt,['add','-N','--',contractPath]);
  cleanDiff(candidateWt,contractPath);
  const patch=git(candidateWt,['diff','--binary','--no-ext-diff','HEAD','--',contractPath]).stdout;
  if(!patch||!patch.includes('new file mode')||!patch.includes('diff --git '))
   throw Error('No valid new-file diff produced');
  const patchPath=path.join(runDir,'candidate.patch');
  fs.writeFileSync(patchPath,patch,{encoding:'utf8',mode:0o600});
  const patchSha256=sha256Hex(Buffer.from(patch,'utf8'));
  verifyWt=createWorktree(source,head);
  if(git(verifyWt,['apply','--check','--binary',patchPath],{allowFailure:true}).status!==0)
   throw Error('PATCH_DOES_NOT_APPLY');
  git(verifyWt,['apply','--binary',patchPath]);
  if(safeRead(verifyWt,contractPath)!==proposed.candidate.edits[0].newText)
   throw Error('INDEPENDENT_PATCH_BYTES_MISMATCH');
  const independentOut=path.join(runDir,'independent-check');
  fs.mkdirSync(independentOut,{recursive:true,mode:0o700});
  const independentRun=runChain('node',verifyWt,independentOut,{deadline});
  const proof=verifyContractTestEvidence(baseRun,candidateRun,independentRun,spec.length);
  const receipt={schema:'continuum-owner-test-evidence',version:1,
   head,taskId:task.id,strategy:proposed.candidate.strategy,patchSha256,
   checkedAt:new Date().toISOString(),...proof,
   profiles:profileEvidence(independentRun)};
  const receiptPath=path.join(independentOut,'verification.json');
  fs.writeFileSync(receiptPath,JSON.stringify(receipt,null,2)+'\n');
  if(!proof.passed)throw Error('INDEPENDENT_EVIDENCE_INSUFFICIENT');
  const now=Date.now();
  const evidence={type:'TEST_RUN',taskId:task.id,submissionToken:token,
   artifactSha256:patchSha256,chain:'node',commit:head,
   profiles:receipt.profiles,repair:null,at:now,by:actor};
  store.apply([
   {op:'submit',actor,at:now,id:task.id,token,
    artifact:{type:'PATCH',sha256:patchSha256}},
   {op:'complete',actor,at:now,id:task.id,evidence}
  ]);
  result={status:'DONE',head,taskId:task.id,patch:patchPath,patchSha256,
   receipt:receiptPath,proof,approvedForMerge:false};
  // Backlog DONE is the authoritative, atomic decision. The human-readable
  // summary is best-effort and may not roll a verified task back to FAILED.
  try{
   fs.writeFileSync(path.join(runDir,'task-result.json'),JSON.stringify(result,null,2)+'\n');
  }catch(writeError){
   result.reportWarning=singleLine(writeError.message,200);
  }
  return result;
 }catch(e){
  const state=store.read().doc.tasks.find(t=>t.id===task.id);
  if(state&&['IN_PROGRESS','VERIFYING'].includes(state.state)){
   try{
    store.apply([{op:'fail',actor,at:Date.now(),id:task.id,token,
     error:{code:'CONTRACT_CHECKS_FAILED',message:singleLine(e.message,400)}}]);
   }catch(failure){e.cause=failure;}
  }
  result={status:'FAILED',head,taskId:task.id,reason:singleLine(e.message,400),
   approvedForMerge:false};
  fs.mkdirSync(runDir,{recursive:true,mode:0o700});
  fs.writeFileSync(path.join(runDir,'task-result.json'),JSON.stringify(result,null,2)+'\n');
  return result;
 }finally{
  if(verifyWt)removeWorktree(source,verifyWt);
  if(candidateWt)removeWorktree(source,candidateWt);
 }
}
