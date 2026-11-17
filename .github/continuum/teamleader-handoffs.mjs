/**
 * Independent, no-API agent handoff contract for the existing repair team.
 *
 * Unlike simulated group chats, these receipts describe work that the real
 * repair-loop already executed. The manager cannot claim a verified patch
 * without a critic verdict, a test result and a patch hash.
 *
 * Designed after public Swarm/AutoGen/OpenHands coordination patterns.
 * Does NOT import their SDKs, models, APIs or cloud services.
 */
import crypto from 'node:crypto';

const SHA=/^[0-9a-f]{40}$/;
const PATCH_SHA=/^[0-9a-f]{64}$/;
export const REPAIR_ROLES=Object.freeze({
 ANALYST:Object.freeze(['INSPECT_BASELINE']),
 CRITIC:Object.freeze(['REVIEW_CANDIDATE']),
 PATCHER:Object.freeze(['EDIT_ISOLATED_WORKTREE']),
 TESTER:Object.freeze(['RUN_FIXED_TEST_PROFILES']),
 TEAMLEADER:Object.freeze(['REVIEW_PROPOSAL'])
});
const OUTCOMES=new Set(['NO_FAILURE','NO_CODE_DIAGNOSTIC','VERIFIED','NO_VERIFIED_REPAIR']);
const CANDIDATES=new Set([
 'REJECTED_BY_CRITIC','NOT_APPLICABLE','FAILED_TESTS',
 'INSUFFICIENT_EVIDENCE','VERIFIED','FAILED_SYNTAX','SYNTAX_CHECK_ERROR'
]);
function evidenceId(value){
 return crypto.createHash('sha256').update(JSON.stringify(value)).digest('hex');
}
function message(role,event,data){
 if(!Object.hasOwn(REPAIR_ROLES,role))throw Error('Unregistered repair role');
 const payload=Object.freeze({role,event,...data});
 return Object.freeze({...payload,proof:evidenceId(payload)});
}

/**
 * Transform a real, completed repair-loop report into typed agent handoff
 * receipts. No role ever writes main or makes a cloud call.
 * Fail closed if a successful patch has no independently executed tests.
 */
export function summarizeRepairHandoffs(report){
 if(report?.schemaVersion!==1||!SHA.test(report.base??'')||
    !OUTCOMES.has(report.outcome)||
    !report.baseline||typeof report.baseline.passed!=='boolean'||
    !Array.isArray(report.baseline.results)||!Array.isArray(report.attempts)||
    report.attempts.length>32)
   throw Error('Invalid repair report for multi-agent handoff');
 const messages=[];
 let verified=0;
 messages.push(message('ANALYST','BASELINE_CHECKED',{
   passed:report.baseline.passed,
   profiles:report.baseline.results.length,
   diagnosed:Array.isArray(report.baseline.diagnostics)?report.baseline.diagnostics.length:0
 }));
 if(report.baseline.passed){
   if(report.outcome!=='NO_FAILURE'||report.attempts.length||report.verified)
     throw Error('Baseline passed but repair was reported');
 } else if(report.outcome==='NO_FAILURE'){
   throw Error('False clean baseline');
 }
 for(const a of report.attempts){
   if(!CANDIDATES.has(a?.outcome)||typeof a.strategy!=='string'||
      typeof a.candidate!=='string'||a.strategy.length>140||
      a.candidate.length>140||typeof a.critic?.approved!=='boolean')
     throw Error('Invalid specialist repair attempt');
   const candidateHash=evidenceId([a.candidate,a.strategy]).slice(0,16);
   messages.push(message('CRITIC','REVIEWED',{
     candidateHash,approved:a.critic.approved
   }));
   if(!a.critic.approved){
     if(a.outcome!=='REJECTED_BY_CRITIC'||a.tests)
       throw Error('Critic bypass in candidate');
     continue;
   }
   if(a.outcome==='REJECTED_BY_CRITIC')
     throw Error('Contradictory critic approval');
   if(a.outcome==='NOT_APPLICABLE'){
     messages.push(message('PATCHER','NOT_APPLICABLE',{candidateHash}));
     continue;
   }
   if(!Array.isArray(a.edits)||a.edits.length<1)
     throw Error('Verified candidate has no edits');
   messages.push(message('PATCHER','ISOLATED_EDIT_ATTEMPTED',{
     candidateHash,editedFiles:a.edits.length
   }));
   if(a.outcome==='FAILED_SYNTAX'||a.outcome==='SYNTAX_CHECK_ERROR'){
     // --check parses source without executing a test. Preserve this stop
     // distinctly so a rejected proposal cannot forge a passing test chain.
     const failedSyntax=a.outcome==='FAILED_SYNTAX';
     const reason=failedSyntax?a.syntax?.reason:a.syntaxReason;
     if(a.tests!==undefined||a.evidence!==undefined||
        (failedSyntax&&(a.syntax?.passed!==false||
         !['INVALID_JS_SYNTAX','SYNTAX_CHECK_INTERRUPTED'].includes(reason)||
         !a.edits.some(e=>e.file===a.syntax.failedFile)))||
        (!failedSyntax&&(a.syntax!==undefined||typeof reason!=='string'||
         !/^[A-Z][A-Z0-9_]{0,79}$/.test(reason))))
       throw Error('Invalid syntax gate evidence');
     messages.push(message('TESTER','JS_SYNTAX_GATE',{
       candidateHash,passed:false,executed:false,outcome:a.outcome,reason
     }));
     continue;
   }
   if(!Array.isArray(a.tests)||!a.tests.length||
      a.tests.some(t=>typeof t.passed!=='boolean'||typeof t.profile!=='string'))
     throw Error('Candidate test evidence missing');
   const allPassed=a.tests.every(t=>t.passed);
   const actuallyRan=a.tests.some(t=>t.tests?.executed>0);
   messages.push(message('TESTER','FIXED_TEST_CHAIN',{
     candidateHash,passed:allPassed,executed:actuallyRan,
     profiles:a.tests.length
   }));
   if(a.outcome==='VERIFIED'){
     if(!allPassed||!actuallyRan||a.evidence?.sufficient!==true)
       throw Error('Verified repair lacks passing executed tests');
     verified++;
   }
 }
 if(report.outcome==='VERIFIED'){
   const v=report.verified;
   // The patch SHA must refer to actual nonempty patch bytes. Otherwise a
   // green test run could be misreported as a verified code modification.
   if(verified!==1||!v||!PATCH_SHA.test(v.patchSha256??'')||
      typeof v.patch!=='string'||v.patch.length===0||
      crypto.createHash('sha256').update(v.patch).digest('hex')!==v.patchSha256||
      !Array.isArray(v.tests)||!v.tests.length||v.tests.some(t=>t.passed!==true)||
      !v.tests.some(t=>t.tests?.executed>0)||
      typeof v.reviewRequired!=='boolean')
     throw Error('Verified patch without full review evidence');
   messages.push(message('TEAMLEADER','PATCH_PENDING_REVIEW',{
     patchSha256:v.patchSha256,approvedForMerge:false
   }));
 } else {
   if(verified||report.verified)
     throw Error('Unapproved repair promoted to verified');
   messages.push(message('TEAMLEADER','NO_AUTOMATIC_MERGE',{
     outcome:report.outcome,approvedForMerge:false
   }));
 }
 if(messages.length>100)throw Error('Excessive multi-agent messages');
 const envelope={
   schema:'continuum-repair-handoffs',version:1,
   base:report.base,chain:report.chain,conclusion:report.outcome,
   messages
 };
 return Object.freeze({...envelope,integrity:evidenceId(envelope)});
}
export function verifyRepairHandoffs(envelope){
 if(envelope?.schema!=='continuum-repair-handoffs'||
    envelope.version!==1||!SHA.test(envelope.base??'')||
    !Array.isArray(envelope.messages)||envelope.messages.length>100||
    !PATCH_SHA.test(envelope.integrity??''))
   return false;
 const {integrity,messages,...rest}=envelope;
 if(evidenceId({...rest,messages})!==integrity)return false;
 return messages.every(m=>{
   if(!REPAIR_ROLES[m.role]||!PATCH_SHA.test(m.proof??''))return false;
   const {proof,...payload}=m;
   return evidenceId(payload)===proof;
 });
}
