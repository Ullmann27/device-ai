// CCC1 ReceiptVerifier: independent check of a coding-job receipt against the order it must answer and the real
// artifacts (patch bytes, run exit codes and counts, fixed allowlisted test profile, toolchain, job/commit/order
// binding). Only the verifier issues a verificationReceiptId (HMAC over request, receipt hash and verdict); a
// reasoner's statement is never a substitute. CONFIRMED needs the verified status and three passing runs;
// GENERATED_TESTS_FAILED refutes the hypothesis; anything else is INCONCLUSIVE.
import fs from 'node:fs';
import {createHmac,timingSafeEqual} from 'node:crypto';
import {RECEIPT_SCHEMA,REPAIR_PROFILE_COMMAND} from '../../scripts/continuum-mobile-coding.mjs';
import {canonical,sha256} from './ccc1-envelope.mjs';
export {verifyOwnedProjectReceipt} from './owned-project-evidence.mjs';

/** The only test command a coding receipt may report (fixed profile of the existing job). */
export const ALLOWED_PROFILE='node --test --test-reporter=junit .github/continuum/*.test.mjs (fixed profile node-continuum, isolated git worktree)';

const validCounts=x=>Number.isSafeInteger(x?.executed)&&x.executed>0&&
 Number.isSafeInteger(x.failed)&&x.failed>=0&&x.failed<=x.executed;
const completedPass=x=>validCounts(x)&&x.exitCode===0&&x.passed===true&&x.failed===0&&x.timedOut===false;
const completedFailure=x=>validCounts(x)&&x.passed===false&&x.failed>0&&
 Number.isSafeInteger(x.exitCode)&&x.exitCode>0&&x.timedOut===false;

export function verifyJobReceipt(receipt,{job,commit,module,exportName,casesJson}){
 const reasons=[];const r=receipt??{};
 if(r.schema!==RECEIPT_SCHEMA||r.version!==1)reasons.push('not a coding receipt');
 if(r.job!==job)reasons.push('receipt belongs to another job');
 if(r.commit!==commit)reasons.push('receipt is bound to another commit');
 if(r.approvedForMerge!==false||r.installed!==false||r.apk!==null||r.deviceVerified!==false)reasons.push('receipt claims merge, install or device verification');
 if(!(r.order&&r.order.module===module&&r.order.export===exportName&&r.order.casesSha256===sha256(casesJson)))reasons.push('receipt answers a different order');
 if(typeof r.toolchain?.node!=='string'||!/^v\d+/.test(r.toolchain.node))reasons.push('toolchain not reported');
 if(reasons.length)return {verdict:'INCONCLUSIVE',reasons};
 if(r.status==='CODING_PATCH_VERIFIED_PENDING_REVIEW'){
  try{
   const bytes=fs.readFileSync(r.patch.path);
   if(sha256(bytes)!==r.patch.sha256||bytes.length!==r.patch.bytes)reasons.push('patch bytes do not match the receipt');
  }catch{reasons.push('patch file missing');}
  const runs=Array.isArray(r.commands)?r.commands:[];
  const run=phase=>runs.find(x=>x?.phase===phase);
  if(runs.length!==3)reasons.push('expected exactly three runs');
  for(const phase of ['baseline','candidate','independent']){
   const x=run(phase);
   if(!completedPass(x))reasons.push(phase+' run did not pass with executed tests');
   else if(x.command!==ALLOWED_PROFILE)reasons.push(phase+' run used a command outside the allowlist');
  }
  const ex=r.evidence??{};
  if(!/^[0-9a-f]{64}$/.test(ex.verificationSha256??''))reasons.push('no verification evidence hash');
  // The ordered cases determine the required added tests, never a worker-supplied summary.
  let expectedNewTests=null;
  try{const cases=JSON.parse(casesJson);if(Array.isArray(cases)&&cases.length>0)expectedNewTests=cases.length;}catch{}
  if(expectedNewTests===null||ex.expectedNewTests!==expectedNewTests)reasons.push('expected new tests disagree with the ordered cases');
  for(const phase of ['baseline','candidate','independent'])
   if(ex[phase+'Executed']!==run(phase)?.executed)reasons.push(phase+' summary disagrees with executed command tests');
  if(!(run('candidate')?.executed>=run('baseline')?.executed+expectedNewTests&&
       run('independent')?.executed>=run('candidate')?.executed))reasons.push('independent run executed fewer tests than required');
  return reasons.length?{verdict:'INCONCLUSIVE',reasons}:{verdict:'CONFIRMED',reasons:['contract test passed in candidate and independent worktree']};
 }
 if(r.status==='FAILED_CODING_CHECKS'&&/GENERATED_TESTS_FAILED/.test(r.failure?.message??''))
  return {verdict:'REFUTED',reasons:['the contract test generated from the cases failed']};
 return {verdict:'INCONCLUSIVE',reasons:['job ended '+r.status+(r.failure?.message?': '+String(r.failure.message).slice(0,200):'')]};
}

/**
 * Independent check of a REPAIR receipt (capability node-repair): job, commit and chain binding; no merge/install
 * claims; for a verified repair the real patch bytes, a baseline that really failed, and a candidate plus a second
 * independent worktree that both passed the fixed chain with at least as many executed tests as the baseline.
 * NO_FAILURE_FOUND (the chain passed at the bound commit) and FAILED_REPAIR (no candidate survived) refute the
 * repair hypothesis; anything else is INCONCLUSIVE.
 */
export function verifyRepairReceipt(receipt,{job,commit,chain}){
 const reasons=[];const r=receipt??{};
 if(r.schema!==RECEIPT_SCHEMA||r.version!==1||r.operation!=='code-repair')reasons.push('not a repair receipt');
 if(r.job!==job)reasons.push('receipt belongs to another job');
 if(r.commit!==commit)reasons.push('receipt is bound to another commit');
 if(r.approvedForMerge!==false||r.installed!==false||r.apk!==null||r.deviceVerified!==false)reasons.push('receipt claims merge, install or device verification');
 if(r.order?.chain!==chain)reasons.push('receipt answers a different order');
 const command=Object.hasOwn(REPAIR_PROFILE_COMMAND,chain)?REPAIR_PROFILE_COMMAND[chain]:null;
 if(!command)reasons.push('unknown repair test chain');
 if(typeof r.toolchain?.node!=='string'||!/^v\d+/.test(r.toolchain.node))reasons.push('toolchain not reported');
 if(reasons.length)return {verdict:'INCONCLUSIVE',reasons};
 const runs=Array.isArray(r.commands)?r.commands:[];
 const run=phase=>runs.find(x=>x?.phase===phase);
 const baseline=run('baseline');
 if(r.status==='REPAIR_PATCH_VERIFIED_PENDING_REVIEW'){
  try{
   const bytes=fs.readFileSync(r.patch.path);
   if(sha256(bytes)!==r.patch.sha256||bytes.length!==r.patch.bytes||!bytes.length)reasons.push('patch bytes do not match the receipt');
  }catch{reasons.push('patch file missing');}
  if(runs.length!==3)reasons.push('expected exactly three runs');
  if(!completedFailure(baseline))
   reasons.push('baseline did not show a completed real test failure');
  for(const phase of ['candidate','independent']){
   const x=run(phase);
   if(!completedPass(x))reasons.push(phase+' run did not pass with executed tests');
  }
  for(const x of runs)if(x?.command!==command)reasons.push((x?.phase??'unknown')+' run used a command outside the allowlist');
  const ex=r.evidence??{};
  if(!/^[0-9a-f]{64}$/.test(ex.verificationSha256??''))reasons.push('no verification evidence hash');
  if(ex.evidenceSufficient!==true)reasons.push('the worker did not find the evidence sufficient');
  // Summary fields cannot substitute for the execution details. This is consistency checking,
  // not authentication of the worker or its test artifacts; independent execution remains required.
  for(const phase of ['baseline','candidate','independent'])
   if(ex[phase+'Executed']!==run(phase)?.executed)reasons.push(phase+' summary disagrees with executed command tests');
  if(ex.baselineFailed!==baseline?.failed)reasons.push('baseline summary disagrees with failing command tests');
  if(!(run('candidate')?.executed>=baseline?.executed&&run('independent')?.executed>=run('candidate')?.executed))
   reasons.push('the repaired chain lost tests between baseline, candidate and independent execution');
  return reasons.length?{verdict:'INCONCLUSIVE',reasons}:
   {verdict:'CONFIRMED',reasons:['a failing '+chain+' chain was repaired; the patch passed in the repair worktree and in an independent second worktree']};
 }
 if(r.status==='NO_FAILURE_FOUND'){
  if(runs.length===1&&completedPass(baseline)&&baseline.command===command)
   return {verdict:'REFUTED',reasons:['the '+chain+' chain passed at the bound commit ('+baseline.executed+' tests): nothing to repair']};
  return {verdict:'INCONCLUSIVE',reasons:['no passing baseline run behind NO_FAILURE_FOUND']};
 }
 if(r.status==='FAILED_REPAIR'&&runs.length===1&&completedFailure(baseline)&&baseline.command===command)
  return {verdict:'REFUTED',reasons:['no repair candidate passed independently ('+String(r.failure?.code??'NO_VERIFIED_REPAIR')+')']};
 return {verdict:'INCONCLUSIVE',reasons:['job ended '+r.status+(r.failure?.message?': '+String(r.failure.message).slice(0,200):'')]};
}

/** verificationReceiptId: only the verifier (runtime key) can produce it for this request, receipt and verdict. */
export const issueVerificationId=(key,{request,receiptSha256,verdict})=>createHmac('sha256',key).update(canonical({request,receiptSha256,verdict})).digest('hex');
export function checkVerificationId(key,id,fields){
 const want=Buffer.from(issueVerificationId(key,fields),'hex');
 return typeof id==='string'&&/^[0-9a-f]{64}$/.test(id)&&timingSafeEqual(Buffer.from(id,'hex'),want);
}
