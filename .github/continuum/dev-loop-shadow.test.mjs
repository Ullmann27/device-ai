import test from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import {summarizeDevLoopShadow,runDevLoopShadow} from './dev-loop-shadow.mjs';

const patch='public synthetic patch\n';
const sha=crypto.createHash('sha256').update(patch).digest('hex');
function report(){
 const tests=[{profile:'node-continuum',passed:true,testProfile:true,exitCode:0,
   tests:{total:2,executed:2,failed:0,skipped:0,evidenceComplete:true}}];
 return {base:'a'.repeat(40),chain:'node',outcome:'VERIFIED',
  attempts:[{candidate:'ab',diagnostic:'cd',diagnosticKind:'REFERENCE_ERROR',strategy:'JS_TYPO_REFERENCE',
   critic:{approved:true},evidence:{sufficient:true},outcome:'VERIFIED',tests}],
  verified:{candidate:'ab',strategy:'JS_TYPO_REFERENCE',patch,patchSha256:sha,tests},
  independent:{passed:true,evidenceSufficient:true,
   profiles:[{profile:'node-continuum',passed:true,executed:2,failed:0,skipped:0}]}};
}

test('the real-report mirror computes bounded research diagnostics without creating learning authority',()=>{
 const r=report(),experiences=[{kind:'REFERENCE_ERROR|JS_MISSING_IMPORT',successes:0,failures:5}];
 const before=JSON.stringify({r,experiences});
 const s=summarizeDevLoopShadow({report:r,experiences});
 assert.equal(s.mode,'SHADOW');
 assert.equal(s.status,'OBSERVED');
 assert.equal(s.currentVerification,'SUMMARY_MATCH');
 assert.equal(s.executedPrimaryTests,2);
 assert.equal(s.executedIndependentTests,2);
 assert.equal(s.weakStrategyClasses,1);
 assert.equal(s.policyComparison,'ATTEMPTED_CANDIDATES_PROXY_ONLY');
 assert.equal(s.positiveLearningAllowed,false);
 assert.equal(s.strategyRankingChanged,false);
 assert.equal(JSON.stringify({r,experiences}),before);
 const raw=JSON.stringify(s);
 for(const privateText of [patch,sha,'REFERENCE_ERROR','JS_TYPO_REFERENCE','a'.repeat(40)])
  assert.equal(raw.includes(privateText),false);
});

test('stale, mismatched, empty and failed independent proofs cannot appear verified in the shadow',()=>{
 const changes=[
  r=>{delete r.independent},
  r=>{r.independent.passed=false},
  r=>{r.independent.profiles=[]},
  r=>{r.independent.profiles[0].executed=0},
  r=>{r.independent.profiles[0].failed=1},
  r=>{r.independent.evidenceSufficient=false},
  r=>{r.verified.candidate='ff'},
  r=>{r.verified.strategy='JS_MISSING_IMPORT'},
  r=>{r.verified.patchSha256='f'.repeat(64)},
  r=>{r.attempts[0].outcome='FAILED_TESTS'},
  r=>{r.attempts[0].critic.approved=false},
  r=>{r.attempts[0].tests[0].tests.evidenceComplete=false},
  r=>{r.outcome='NO_VERIFIED_REPAIR'}
 ];
 for(const change of changes){
  const r=structuredClone(report());change(r);
  const s=summarizeDevLoopShadow({report:r,experiences:[]});
  assert.equal(s.status,'ABSTAIN');
  assert.equal(s.currentVerification,'NOT_ESTABLISHED');
  assert.equal(s.positiveLearningAllowed,false);
 }
});

test('observer exceptions, injected promotion and malformed research output fail closed without mutating repair inputs',()=>{
 const r=report(),experiences=[{kind:'REFERENCE_ERROR|JS_TYPO_REFERENCE',successes:1,failures:0}];
 const before=JSON.stringify({r,experiences});
 for(const observer of [
  ()=>{throw Error('private path or prompt')},
  input=>{input.report.outcome='FAILED';input.experiences[0].successes=999;throw Error('changed copy')},
  input=>({...summarizeDevLoopShadow(input),positiveLearningAllowed:true}),
  ()=>({mode:'SHADOW',status:'private text'}),
  ()=>Promise.resolve({})
 ]){
  const s=runDevLoopShadow(observer,r,experiences);
  assert.equal(s.status,'ERROR');
  assert.equal(s.positiveLearningAllowed,false);
  assert.equal(s.strategyRankingChanged,false);
  assert.equal(JSON.stringify(s).includes('private'),false);
  assert.equal(JSON.stringify({r,experiences}),before);
 }
 assert.equal(runDevLoopShadow(null,r,experiences),null);
});

test('invalid or oversized observations do not start unbounded research work',()=>{
 for(const input of [
  {report:report(),experiences:Array(101).fill({kind:'K|S',successes:0,failures:4})},
  {report:{...report(),attempts:Array(13).fill(report().attempts[0])},experiences:[]},
  {report:report(),experiences:[{kind:'K|S',successes:NaN,failures:4}]},
  {report:report(),experiences:[{kind:'K|S',successes:1e12,failures:4}]}
 ])assert.equal(runDevLoopShadow(summarizeDevLoopShadow,input.report,input.experiences).status,'ERROR');
});
