import test from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import {CoinWmV3ShadowModel} from './coin-wm-v3-shadow-model.mjs';
import {induceStructuredStatementOperator,isStructuredStatementOperator} from './structured-statement-translator.mjs';
import {proposeShadowStructuredCandidates} from './coin-wm-v3-structured-shadow.mjs';

const sort=induceStructuredStatementOperator('sort',
 'function rankScores(scores) { return scores.sort((a, b) => a - b); }',
 'function rankScores(scores) { return [...scores].sort((a, b) => a - b); }');
const divide=induceStructuredStatementOperator('divide',
 'function averageOrderValue(totalRevenue, orderCount) { return totalRevenue / orderCount; }',
 'function averageOrderValue(totalRevenue, orderCount) { if (orderCount === 0) return 0; return totalRevenue / orderCount; }');
const source=['function sortVisibleCatalog(items) {',
 '  const lastIdx = items.length - 1;',
 '  const copyCheck = items.slice(0, lastIdx + 1);',
 '  return items.sort((a, b) => a - b);',
 '}'].join('\n');
const state=Float64Array.from({length:16},(_,i)=>i===4||i===11||i===15?1:0);
const sourceCommit='a'.repeat(40),taskSha256='b'.repeat(64);

test('NTW1 shadow ranking produces unverified atomic candidate without executing it',()=>{
 const model=new CoinWmV3ShadowModel(2);
 const result=proposeShadowStructuredCandidates({
   model,state16:state,operators:[sort,divide],source,sourceCommit,taskSha256,maxCandidates:2
 });
 assert.equal(result.status,'RESEARCH_SHADOW');
 assert.equal(result.promotionEligible,false);
 assert.equal(result.needsIndependentExecution,true);
 assert.equal(result.sourceCommit,sourceCommit);
 assert.equal(result.taskSha256,taskSha256);
 assert.equal(result.sourceSha256,crypto.createHash('sha256').update(source,'utf8').digest('hex'));
 assert.equal(result.candidates.length,1);
 assert.equal(result.candidates[0].operatorId,'STRUCT_OP_sort');
 assert.equal(result.candidates[0].status,'CANDIDATE_UNVERIFIED');
 assert.equal(result.candidates[0].candidateSha256,crypto.createHash('sha256')
   .update(result.candidates[0].code,'utf8').digest('hex'));
 assert.match(result.candidates[0].code,/return \[\.\.\.items\]\.sort/);
 assert.equal(result.candidates[0].code.includes('return items].sort'),false);
 assert.ok(Object.isFrozen(result));
 assert.ok(Object.isFrozen(result.candidates[0]));
});

test('forged operator cannot run as injected executable callback',()=>{
 const model=new CoinWmV3ShadowModel(1);
 let invoked=0;
 const forged=Object.freeze({
   id:'STRUCT_OP_fake',
   applyToCode(){invoked++;throw Error('SHOULD_NOT_EXECUTE');}
 });
 assert.equal(isStructuredStatementOperator(forged),false);
 assert.equal(isStructuredStatementOperator(sort),true);
 assert.throws(()=>proposeShadowStructuredCandidates({
   model,state16:state,operators:[forged],source,sourceCommit,taskSha256,maxCandidates:1
 }),/OPERATOR_NOT_STRUCTURED/);
 assert.equal(invoked,0);
});

test('invalid ranker, invalid state and budgets fail before any candidate',()=>{
 const model=new CoinWmV3ShadowModel(2);
 assert.throws(()=>proposeShadowStructuredCandidates({
   model:{status:'RESEARCH_SHADOW',promotionEligible:false,rank(){}},
   state16:state,operators:[sort,divide],source,sourceCommit,taskSha256
 }),/RESEARCH_MODEL_REQUIRED/);
 assert.throws(()=>proposeShadowStructuredCandidates({
   model,state16:state,operators:[sort],source,sourceCommit,taskSha256
 }),/OPERATOR_SET_INVALID/);
 assert.throws(()=>proposeShadowStructuredCandidates({
   model,state16:state,operators:[sort,divide],source,sourceCommit,taskSha256,maxCandidates:3
 }),/CANDIDATE_BUDGET/);
 assert.throws(()=>proposeShadowStructuredCandidates({
   model,state16:new Float64Array(3),operators:[sort,divide],source,sourceCommit,taskSha256
 }),/INVALID_STATE/);
});

test('a research patch cannot be detached from its approved source identity',()=>{
 const model=new CoinWmV3ShadowModel(2);
 assert.throws(()=>proposeShadowStructuredCandidates({
   model,state16:state,operators:[sort,divide],source,sourceCommit:'c'.repeat(39),taskSha256
 }),/TASK_SOURCE_IDENTITY_REQUIRED/);
 assert.throws(()=>proposeShadowStructuredCandidates({
   model,state16:state,operators:[sort,divide],source,sourceCommit,taskSha256:'invalid'
 }),/TASK_SOURCE_IDENTITY_REQUIRED/);
});
