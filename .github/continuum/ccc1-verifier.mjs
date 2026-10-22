// STATUS 22.10.2026: UNFERTIG (WIP) – syntaktisch geprüft, noch ohne Tests; von keinem Produktivpfad importiert.
// CCC1 ReceiptVerifier: independent check of a coding-job receipt against the order it must answer and the real
// artifacts (patch bytes, run exit codes and counts, fixed allowlisted test profile, toolchain, job/commit/order
// binding). Only the verifier issues a verificationReceiptId (HMAC over request, receipt hash and verdict); a
// reasoner's statement is never a substitute. CONFIRMED needs the verified status and three passing runs;
// GENERATED_TESTS_FAILED refutes the hypothesis; anything else is INCONCLUSIVE.
import fs from 'node:fs';
import {createHmac,timingSafeEqual} from 'node:crypto';
import {RECEIPT_SCHEMA} from '../../scripts/continuum-mobile-coding.mjs';
import {canonical,sha256} from './ccc1-envelope.mjs';

/** The only test command a coding receipt may report (fixed profile of the existing job). */
export const ALLOWED_PROFILE='node --test --test-reporter=junit .github/continuum/*.test.mjs (fixed profile node-continuum, isolated git worktree)';

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
  const runs=r.commands??[];
  if(runs.length!==3)reasons.push('expected exactly three runs');
  for(const phase of ['baseline','candidate','independent']){
   const run=runs.find(x=>x.phase===phase);
   if(!run||run.exitCode!==0||run.passed!==true||!(run.executed>0)||run.failed!==0||run.timedOut)reasons.push(phase+' run did not pass with executed tests');
   else if(run.command!==ALLOWED_PROFILE)reasons.push(phase+' run used a command outside the allowlist');
  }
  const ex=r.evidence??{};
  if(!/^[0-9a-f]{64}$/.test(ex.verificationSha256??''))reasons.push('no verification evidence hash');
  if(!(ex.independentExecuted>=ex.candidateExecuted&&ex.candidateExecuted>=ex.baselineExecuted+ex.expectedNewTests))reasons.push('independent run executed fewer tests than required');
  return reasons.length?{verdict:'INCONCLUSIVE',reasons}:{verdict:'CONFIRMED',reasons:['contract test passed in candidate and independent worktree']};
 }
 if(r.status==='FAILED_CODING_CHECKS'&&/GENERATED_TESTS_FAILED/.test(r.failure?.message??''))
  return {verdict:'REFUTED',reasons:['the contract test generated from the cases failed']};
 return {verdict:'INCONCLUSIVE',reasons:['job ended '+r.status+(r.failure?.message?': '+String(r.failure.message).slice(0,200):'')]};
}

/** verificationReceiptId: only the verifier (runtime key) can produce it for this request, receipt and verdict. */
export const issueVerificationId=(key,{request,receiptSha256,verdict})=>createHmac('sha256',key).update(canonical({request,receiptSha256,verdict})).digest('hex');
export function checkVerificationId(key,id,fields){
 const want=Buffer.from(issueVerificationId(key,fields),'hex');
 return typeof id==='string'&&/^[0-9a-f]{64}$/.test(id)&&timingSafeEqual(Buffer.from(id,'hex'),want);
}
