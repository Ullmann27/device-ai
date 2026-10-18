import test from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import {RelationalEncoder} from './cre-encoder.mjs';
import {validateVerifiedReport} from './repair-publish.mjs';
import {rankCandidates} from './repair-loop.mjs';
import {trainCefrCandidateFromVerifiedReport,validateCefrHoldout,
 evaluateCefrHoldout,observeCefrCandidates} from './cefr-repair-ranker.mjs';

const sha256=x=>crypto.createHash('sha256').update(x).digest('hex');
const HOLDOUT=[
 {query:'Kotlin unresolved Files package java nio',candidates:['import java nio Files correctly','replace Files typo wrongly'],correctIndex:0},
 {query:'JavaScript missing closure export utils',candidates:['fix missing closing bracket','remove unit test'],correctIndex:0},
 {query:'Gradle build cannot resolve class Service',candidates:['add valid dependency declaration','delete package from source'],correctIndex:0},
 {query:'JVM test missing method expected actual',candidates:['restore function contract','revert unrelated file'],correctIndex:0}
];
function fixture(){
 const patchText='diff --git a/backend/src/math.mjs b/backend/src/math.mjs\n'+
  '--- a/backend/src/math.mjs\n+++ b/backend/src/math.mjs\n'+
  '@@ -1 +1 @@\n-export const result = bad(2);\n+export const result = good(2);\n';
 const failed={candidate:'1111aaaa',diagnostic:'0123456789abcdef',
  diagnosticKind:'REFERENCE_ERROR',strategy:'JS_MISSING_IMPORT',
  description:'wrong imported function',outcome:'FAILED_TESTS',
  edits:[{file:'backend/src/math.mjs',oldText:'bad(2)',newText:'fix(2)'}],
  tests:[{profile:'node-continuum',passed:false,testProfile:true,tests:{executed:3,failed:1}}]};
 const success={candidate:'2222bbbb',diagnostic:'0123456789abcdef',
  diagnosticKind:'REFERENCE_ERROR',strategy:'JS_TYPO_REFERENCE',
  description:'rename bad symbol to good function',outcome:'VERIFIED',score:0.8,
  edits:[{file:'backend/src/math.mjs',oldText:'bad(2)',newText:'good(2)'}],
  tests:[{profile:'node-continuum',passed:true,testProfile:true,
   tests:{executed:5,failed:0,skipped:0}}],
  evidence:{sufficient:true,reasons:[]}};
 const report={schemaVersion:1,base:'a'.repeat(40),chain:'node',
  outcome:'VERIFIED',baseline:{passed:false,diagnostics:[
   {fingerprint:'0123456789abcdef',kind:'REFERENCE_ERROR',symbol:'bad',
    file:'backend/src/math.mjs',message:'bad is not defined'}]},
  attempts:[failed,success],verified:{candidate:success.candidate,strategy:success.strategy,
   files:['backend/src/math.mjs'],patchSha256:sha256(patchText),
   description:success.description,tests:success.tests}};
 validateVerifiedReport(report,patchText);
 const trusted=()=>({passed:true,base:report.base,chain:report.chain,
  patchSha256:sha256(patchText),profiles:[{profile:'node-continuum',
   passed:true,failed:0,executed:5}]});
 return {report,patchText,trusted};
}

test('a report claiming VERIFIED cannot train without an independent worktree verifier',()=>{
 const before=new RelationalEncoder(42).save(),{report,patchText}=fixture();
 const result=trainCefrCandidateFromVerifiedReport({baselineBytes:before,
  report,patchText,holdoutCases:HOLDOUT});
 assert.equal(result.accepted,false);
 assert.equal(result.reason,'INDEPENDENT_VERIFIER_REQUIRED');
 assert.ok(result.activeBytes.equals(before));
});

test('a publisher-valid self-report is rejected if trusted independent verification fails',()=>{
 const before=new RelationalEncoder(42).save(),{report,patchText}=fixture();
 let calls=0;
 const result=trainCefrCandidateFromVerifiedReport({baselineBytes:before,
  report,patchText,holdoutCases:HOLDOUT,
  verifyIndependent:()=>{calls++;return {passed:true,base:report.base,
   chain:report.chain,patchSha256:'b'.repeat(64),
   profiles:[{profile:'node-continuum',passed:true,failed:0,executed:99}]};}});
 assert.equal(calls,1);
 assert.equal(result.accepted,false);
 assert.match(result.reason,/INDEPENDENT_VERIFICATION_FAILED/);
 assert.ok(result.activeBytes.equals(before));
});

test('tampered patch is rejected before independent checks',()=>{
 const before=new RelationalEncoder(73).save(),{report,patchText}=fixture();
 let calls=0;
 const outcome=trainCefrCandidateFromVerifiedReport({baselineBytes:before,report,
  patchText:patchText+'tampered',holdoutCases:HOLDOUT,
  verifyIndependent:()=>{calls++;throw Error('must not run');}});
 assert.equal(outcome.accepted,false);
 assert.equal(calls,0);
 assert.ok(outcome.activeBytes.equals(before));
});

test('empty and contaminated holdout corpora never allow training',()=>{
 const before=new RelationalEncoder(73).save(),{report,patchText,trusted}=fixture();
 assert.throws(()=>validateCefrHoldout([]),/INVALID_HOLDOUT_CORPUS/);
 assert.throws(()=>trainCefrCandidateFromVerifiedReport({baselineBytes:before,
  report,patchText,holdoutCases:[],verifyIndependent:trusted}),/INVALID_HOLDOUT_CORPUS/);
 const contaminated=[...HOLDOUT.slice(0,3),{query:'REFERENCE_ERROR bad backend/src/math.mjs bad is not defined',
  candidates:['winner strategy','failed strategy'],correctIndex:0}];
 const result=trainCefrCandidateFromVerifiedReport({baselineBytes:before,
  report,patchText,holdoutCases:contaminated,verifyIndependent:trusted});
 assert.equal(result.accepted,false);
 assert.equal(result.reason,'HOLDOUT_LEAKAGE');
 assert.ok(result.activeBytes.equals(before));
});

test('candidate-only training never changes baseline bytes or original optimizer state',()=>{
 const model=new RelationalEncoder(42),before=model.save();
 const {report,patchText,trusted}=fixture();
 const outcome=trainCefrCandidateFromVerifiedReport({baselineBytes:before,
  report,patchText,holdoutCases:HOLDOUT,verifyIndependent:trusted});
 assert.ok(before.equals(model.save()));
 assert.equal(model.adam,null);
 assert.equal(outcome.activeSha256,sha256(outcome.activeBytes));
 assert.ok(outcome.accepted||/REGRESSION|NO_TASK_SCORE_GAIN|NO_HOLDOUT_GENERALIZATION/.test(outcome.reason),
  outcome.reason);
 if(outcome.accepted){
  assert.ok(outcome.scoreAfter>outcome.scoreBefore);
  assert.ok(!outcome.activeBytes.equals(before));
  assert.ok(outcome.holdoutAfter.correct>=outcome.holdoutBefore.correct);
  assert.ok(outcome.holdoutAfter.meanMargin>outcome.holdoutBefore.meanMargin+1e-6||
   outcome.holdoutAfter.mrr>outcome.holdoutBefore.mrr+1e-8,
   'promotion must improve independent holdout performance');
 }
});

test('CEFR only observes: production ranking and irreversible safety tiers stay unchanged',()=>{
 const candidates=[
  {id:'rev',diagnostic:'x',diagnosticKind:'REFERENCE_ERROR',
   strategy:'REVERT_FILE_TO_PARENT',description:'revert whole file',prior:0.99},
  {id:'fix',diagnostic:'x',diagnosticKind:'REFERENCE_ERROR',
   strategy:'JS_TYPO_REFERENCE',description:'replace local symbol',prior:0.3},
  {id:'imp',diagnostic:'x',diagnosticKind:'REFERENCE_ERROR',
   strategy:'JS_MISSING_IMPORT',description:'add missing import',prior:0.7}
 ];
 const original=structuredClone(candidates);
 const expected=rankCandidates(candidates).map(x=>x.id);
 const bytes=new RelationalEncoder(42).save();
 const result=observeCefrCandidates({candidates,checkpointBytes:bytes});
 assert.equal(result.mode,'CEFR_DEV_SHADOW');
 assert.deepEqual(result.productionOrder.map(x=>x.id),expected);
 assert.equal(result.shadowOrder.at(-1),'rev');
 assert.deepEqual(candidates,original);
 assert.deepEqual(observeCefrCandidates({candidates}).shadowOrder,expected);
 assert.equal(observeCefrCandidates({candidates,checkpointBytes:Buffer.alloc(5)}).mode,
  'INVALID_DEV_CHECKPOINT');
});

test('holdout metric requires explicit independently labeled correctIndex',()=>{
 const model=new RelationalEncoder(7);
 const metric=evaluateCefrHoldout(model,HOLDOUT);
 assert.equal(metric.total,4);
 assert.ok(Number.isFinite(metric.mrr)&&metric.mrr>0&&metric.mrr<=1);
 assert.throws(()=>validateCefrHoldout(HOLDOUT.map(({correctIndex,...rest})=>rest)),
  /INVALID_HOLDOUT_CORPUS/);
});
