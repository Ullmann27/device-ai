import test from 'node:test';
import assert from 'node:assert/strict';
import {auditCefrShadowWinner,summarizeCefrShadowAudits} from './cefr-shadow-benchmark.mjs';

const base='a'.repeat(40),patch='b'.repeat(64),model='c'.repeat(64);
function make(){
 const report={outcome:'VERIFIED',base,
  verified:{candidate:'good',patchSha256:patch},
  cefrShadow:{mode:'CEFR_DEV_SHADOW',modelSha256:model,candidateCount:3,
   productionOrder:['bad','good','revert'],
   shadowOrder:['good','bad','revert']}};
 const independent={passed:true,profiles:[
  {profile:'node-continuum',passed:true,failed:0,executed:17}
 ]};
 return {report,independent};
}

test('only after independent worktree proof: evaluate actual winner in both candidate orders',()=>{
 const input=make(),before=structuredClone(input);
 const outcome=auditCefrShadowWinner(input);
 assert.equal(outcome.status,'RANKING_PROXY_ONLY');
 assert.equal(outcome.productionRank,2);
 assert.equal(outcome.shadowRank,1);
 assert.equal(outcome.winnerBetter,true);
 assert.equal(outcome.winnerWorse,false);
 assert.deepEqual(input,before,'audit must not mutate repair data');
});

test('no positive audit for self-reported patch, failed independent checks or zero tests',()=>{
 const original=make();
 for(const independent of [
  null,{passed:false,profiles:original.independent.profiles},
  {passed:true,profiles:[]},
  {passed:true,profiles:[{passed:true,failed:0,executed:0}]},
  {passed:true,profiles:[{passed:true,failed:1,executed:100}]},
  {passed:true,profiles:[{passed:false,failed:0,executed:100}]}
 ]){
  assert.equal(auditCefrShadowWinner({report:original.report,independent}).status,
   'NOT_INDEPENDENTLY_VERIFIED');
 }
});

test('invalid shadow, duplicated candidate or winner not observed is never an audit',()=>{
 const first=make();
 first.report.cefrShadow.mode='SHADOW_NO_MODEL';
 assert.equal(auditCefrShadowWinner(first).status,'NO_VALID_DEV_MODEL');
 const second=make();
 second.report.cefrShadow.shadowOrder=['good','good','revert'];
 assert.equal(auditCefrShadowWinner(second).status,'INVALID_SHADOW_RANKING');
 const third=make();
 third.report.verified.candidate='unseen';
 assert.equal(auditCefrShadowWinner(third).status,'WINNER_NOT_OBSERVED');
 const fourth=make();
 fourth.report.cefrShadow.modelSha256='wrong';
 assert.equal(auditCefrShadowWinner(fourth).status,'NO_VALID_DEV_MODEL');
});

test('cohort MRR is a ranking proxy and never grants an executable promotion',()=>{
 const one=auditCefrShadowWinner(make());
 const cases=[one,...['d','e','f'].map(c=>({...one,
  base:c.repeat(40),patchSha256:c.repeat(64),
  productionRank:1,shadowRank:2,winnerBetter:false,winnerWorse:true}))];
 const result=summarizeCefrShadowAudits(cases,{minimum:4});
 assert.equal(result.samples,4);
 assert.equal(result.status,'RANKING_PROXY_COHORT');
 assert.equal(result.improvedCases,1);
 assert.equal(result.worsenedCases,3);
 assert.equal(result.successRateMeasured,false);
 assert.equal(result.productionPromotionAuthorized,false);
 assert.ok(result.meanReciprocalRankDelta<0);
});

test('small cohort cannot be mistaken for a validated gain',()=>{
 const one=auditCefrShadowWinner(make());
 const result=summarizeCefrShadowAudits([one],{minimum:20});
 assert.equal(result.status,'INSUFFICIENT_INDEPENDENT_CASES');
 assert.equal(result.successRateMeasured,false);
});

test('duplicate data and mixed model versions fail closed',()=>{
 const a=auditCefrShadowWinner(make());
 assert.throws(()=>summarizeCefrShadowAudits([a,a],{minimum:4}),
  /DUPLICATE_CEFR_REPAIR_CASE/);
 const newVersion={...a,base:'d'.repeat(40),modelSha256:'e'.repeat(64)};
 assert.throws(()=>summarizeCefrShadowAudits([a,newVersion],{minimum:4}),
  /MIXED_CEFR_MODEL_GENERATIONS/);
});

test('malformed or tampered rank result cannot be counted as a proxy',()=>{
 const a=auditCefrShadowWinner(make());
 for(const mutation of [
  {productionRank:0},{shadowRank:4},{winnerBetter:false},
  {status:'PRODUCTION_READY'},{candidateCount:1}
 ])assert.throws(()=>summarizeCefrShadowAudits([{...a,...mutation}],{minimum:4}),
  /INVALID_CEFR_SHADOW_AUDIT/);
});

test('invalid cohort size rejects before calculating metrics',()=>{
 assert.throws(()=>summarizeCefrShadowAudits([],{minimum:0}),
  /INVALID_CEFR_SHADOW_COHORT/);
 assert.throws(()=>summarizeCefrShadowAudits([],{minimum:501}),
  /INVALID_CEFR_SHADOW_COHORT/);
});
