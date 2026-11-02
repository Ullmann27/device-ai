import test from 'node:test';
import assert from 'node:assert/strict';
import {runCandidateVerifier} from './repair-research-verifier.mjs';

const base='a'.repeat(40),suiteSha256='b'.repeat(64);
const sources=[{file:'lib/math.mjs',source:'export function f(a){return a;}'}];
const input=()=>({base,sources:structuredClone(sources),deadline:Date.now()+5000});
function verifier(change={}) {
 return Object.assign(request=>({schema:'continuum-candidate-verification',version:1,
  base:request.base,candidateSha256:request.candidateSha256,suiteSha256,
  status:'PASS',checks:2,counterexample:null,...change}),{suiteSha256,expectedChecks:2});
}
test('optional research verification leaves the default path unchanged',()=>{
 assert.equal(runCandidateVerifier(null,input()),null);
});
test('research verification requires exact source, suite and complete positive checks',()=>{
 assert.equal(runCandidateVerifier(verifier(),input()).status,'PASS');
 for(const change of [{base:'c'.repeat(40)},{candidateSha256:'c'.repeat(64)},
  {suiteSha256:'c'.repeat(64)},{checks:0},{checks:1},{checks:3},{status:true},
  {counterexample:{id:'contradiction'}}])
  assert.equal(runCandidateVerifier(verifier(change),input()).status,'ABSTAIN');
});
test('exceptions, promises and elapsed deadlines never turn into verification',()=>{
 for(const callback of [()=>{throw Error('private detail');},async()=>({}),
  r=>{r.sources[0].source='tampered';return {}; }]) {
  Object.assign(callback,{suiteSha256,expectedChecks:2});
  const before=input(),result=runCandidateVerifier(callback,before);
  assert.equal(result.status,'ABSTAIN');assert.deepEqual(before.sources,sources);
  assert.doesNotMatch(JSON.stringify(result),/private detail|tampered/);
 }
 assert.equal(runCandidateVerifier(verifier(),{...input(),deadline:Date.now()-1}).status,'ABSTAIN');
});
test('a numeric counterexample is bounded data and cannot carry arbitrary diagnostics',()=>{
 const counterexample={id:'negative',file:'lib/math.mjs',inputs:[-2,3],actual:-5,expected:5};
 const result=runCandidateVerifier(verifier({status:'FAIL',checks:1,counterexample}),input());
 assert.equal(result.status,'FAIL');assert.deepEqual(result.counterexample,counterexample);
 for(const c of [{...counterexample,actual:NaN},{...counterexample,file:'secrets.txt'},
  {...counterexample,inputs:[Infinity]},{...counterexample,command:'echo unsafe'},
  {...counterexample,id:'untrusted\ntext'}])
  assert.equal(runCandidateVerifier(verifier({status:'FAIL',checks:1,counterexample:c}),input()).status,'ABSTAIN');
});
