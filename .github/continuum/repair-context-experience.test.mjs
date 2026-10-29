import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';import os from 'node:os';import path from 'node:path';
import {proposeCandidates} from './repair-strategies.mjs';
import {rankCandidates} from './repair-loop.mjs';
import {emptyLedger,recordSession,saveLedger,loadLedger,verifyLedger} from './repair-ledger.mjs';
import {repairExperienceKey} from './repair-experience.mjs';

function candidates(source,enabled=true,chain='node') {
 const file='.github/continuum/amount.mjs',symbol='count';
 const diagnostic={kind:'REFERENCE_ERROR',symbol,file,line:2,col:1,fingerprint:'abcd'};
 return proposeCandidates([diagnostic],{chain,contextualExperience:enabled,
  readFile:f=>f===file?source:null,jsSiblings:new Map([['.github/continuum/helper.mjs','export const count=50;']]),experience:[]});
}
test('real generated candidates carry source-derived call/value preconditions only on opt-in node chain',()=>{
 for(const [expression,role] of [['count+5','value'],['count(5)','call']]){
  const source='const counts=2;\nexport function amount(){return '+expression+';}\n';
  const scoped=candidates(source);
  assert.ok(scoped.length>=2);assert.ok(scoped.every(c=>c.contextKey==='node.js.'+role));
  assert.ok(candidates(source,false).every(c=>c.contextKey===undefined));
  assert.ok(candidates(source,true,'android').every(c=>c.contextKey===undefined));
 }
});
test('a source location missing the reference does not invent a context',()=>{
 assert.ok(candidates('const total=2;\nexport function amount(){return total;}').every(c=>c.contextKey===undefined));
});
test('commented and optional calls never inherit value-reference history',()=>{
 for(const expression of ['count /* note */ (5)','count?.(5)','count // note\n (5)']){
  const source='const counts=x=>x;\nexport function amount(){return '+expression+';}\n';
  const result=candidates(source);
  assert.ok(result.length>0);assert.ok(result.every(c=>c.contextKey==='node.js.call'),expression);
 }
});
test('bounded or unsupported call suffixes remain unknown instead of becoming value history',()=>{
 for(const expression of ['count'+' '.repeat(4096)+'(5)','count`text`','count /*'+'x'.repeat(4096)+'*/(5)']){
  const source='const counts=x=>x;\nexport function amount(){return '+expression+';}\n';
  assert.ok(candidates(source).every(c=>c.contextKey===undefined));
 }
});
test('call candidates ignore value and broad contradictory histories',()=>{
 const source='function counts(x){return x+1;}\nexport function amount(){return count(5);}\n';
 const input=candidates(source),neutral=rankCandidates(input).map(c=>c.id);
 const stats=[{kind:'REFERENCE_ERROR|JS_TYPO_REFERENCE',successes:9999,failures:0},
  {kind:repairExperienceKey('REFERENCE_ERROR','JS_TYPO_REFERENCE','node.js.value'),successes:9999,failures:0}];
 assert.deepEqual(rankCandidates(input,stats).map(c=>c.id),neutral);
});
test('contextual ledger is backward compatible and retains matching independent winner only',()=>{
 const attempt={candidate:'ab12',diagnostic:'cd34',diagnosticKind:'REFERENCE_ERROR',strategy:'JS_TYPO_REFERENCE',
  contextKey:'node.js.value',outcome:'VERIFIED',edits:[]};
 const report={base:'a'.repeat(40),chain:'node',outcome:'VERIFIED',verified:{candidate:attempt.candidate,strategy:attempt.strategy,patchSha256:'b'.repeat(64)},attempts:[attempt]};
 assert.equal(recordSession(emptyLedger(),report).experiences.length,0);
 const accepted=recordSession(emptyLedger(),report,{independentPass:true});
 assert.equal(accepted.schemaVersion,2);
 assert.equal(accepted.experiences[0].kind,'REFERENCE_ERROR|JS_TYPO_REFERENCE|node.js.value');
 assert.equal(accepted.attempts[0].contextKey,'node.js.value');
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'context-ledger-'));
 try{
  const file=path.join(dir,'ledger.json');saveLedger(file,accepted);
  assert.deepEqual(loadLedger(file),accepted);
  const legacy={...report,attempts:[{...attempt}]};delete legacy.attempts[0].contextKey;
  const old=recordSession(emptyLedger(),legacy,{independentPass:true});saveLedger(file,old);
  assert.equal(loadLedger(file).experiences[0].kind,'REFERENCE_ERROR|JS_TYPO_REFERENCE');
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
 assert.throws(()=>verifyLedger({...accepted,attempts:[{...accepted.attempts[0],contextKey:'arbitrary-script'}]}),/context/);
});
test('counterexamples affect only their context and do not lower whole-file revert safety',()=>{
 assert.throws(()=>repairExperienceKey('x','y','unknown'),/context/);
 const cs=[{id:'a',diagnosticKind:'REFERENCE_ERROR',strategy:'JS_MISSING_IMPORT',prior:.6,contextKey:'node.js.value'},
  {id:'b',diagnosticKind:'REFERENCE_ERROR',strategy:'JS_TYPO_REFERENCE',prior:.325,contextKey:'node.js.value'},
  {id:'c',diagnosticKind:'REFERENCE_ERROR',strategy:'REVERT_FILE_TO_PARENT',prior:1}];
 const stats=[{kind:'REFERENCE_ERROR|JS_MISSING_IMPORT|node.js.value',successes:0,failures:1},
  {kind:'REFERENCE_ERROR|JS_TYPO_REFERENCE|node.js.value',successes:1,failures:0}];
 assert.deepEqual(rankCandidates(cs,stats).map(c=>c.id),['b','a','c']);
});
