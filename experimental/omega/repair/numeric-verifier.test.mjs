import test from 'node:test';
import assert from 'node:assert/strict';
import {createNumericVerifier} from './numeric-verifier.mjs';
import {runCandidateVerifier} from '../../../.github/continuum/repair-research-verifier.mjs';

const base='a'.repeat(40);
const suite=()=>({schema:'omega-numeric-suite',version:1,sourceCommit:base,file:'lib/math.mjs',exportName:'distance',arity:2,
 cases:[{id:'identity',args:[2,2],expected:0},{id:'reverse',args:[-4,3],expected:7}],
 relations:[{id:'symmetry',left:[2,7],right:[7,2],relation:'equal'},
  {id:'translation',left:[2,7],right:[12,17],relation:'equal'}]});
const check=(source,s=suite(),deadline=Date.now()+5000)=>runCandidateVerifier(createNumericVerifier(s),
 {base,deadline,sources:[{file:'lib/math.mjs',source}]});
test('independent examples and metamorphic relations reject constant pseudo-repairs',()=>{
 const r=check('export function distance(a,b){return 5;}');
 assert.equal(r.status,'FAIL');assert.equal(r.counterexample.id,'identity');assert.equal(r.counterexample.actual,5);
});
test('a bounded exact distance passes all four frozen checks reproducibly',()=>{
 const source='export function distance(a,b){return a>b?a-b:b-a;}';
 const a=check(source),b=check(source);
 assert.equal(a.status,'PASS');assert.equal(a.checks,4);assert.deepEqual(a,b);
});
test('metamorphic failure supplies the distinguishing pair',()=>{
 const s=suite();s.cases=[{id:'example',args:[3,8],expected:5}];
 const r=check('export function distance(a,b){return b-a;}',s);
 assert.equal(r.status,'FAIL');assert.equal(r.counterexample.id,'symmetry');
 assert.deepEqual(r.counterexample.inputs,[2,7,7,2]);
});
test('non-finite values, foreign exports and capability-bearing code never pass',()=>{
 for(const src of ['export function distance(a,b){return (a-b)/(a-b);}',
  'export function other(a,b){return 0;}',
  "export function distance(a,b){return process.exit(0);}",
  "import fs from 'node:fs';export function distance(a,b){return 0;}"])
  assert.notEqual(check(src).status,'PASS');
});
test('suite mutation, malformed cases, unbounded inputs and empty verification are rejected',()=>{
 const s=suite(),v=createNumericVerifier(s);s.cases[0].expected=5;
 const r=runCandidateVerifier(v,{base,deadline:Date.now()+5000,sources:[{file:'lib/math.mjs',source:'export function distance(a,b){return 5;}'}]});
 assert.equal(r.status,'FAIL');assert.equal(r.counterexample.expected,0);
 for(const change of [{cases:[],relations:[]},{cases:[{id:'bad',args:[Infinity,1],expected:0}]},
  {file:'.github/continuum/repair-loop.mjs'},{arity:0},{exportName:'process.exit'},
  {relations:[{id:'bad',left:[1],right:[2],relation:'equal'}]},
  {cases:Array.from({length:257},(_,i)=>({id:'n'+i,args:[0,0],expected:0}))}])
  assert.throws(()=>createNumericVerifier({...suite(),...change}),/OMEGA_SUITE_INVALID/);
 assert.equal(check('export function distance(a,b){return 0;}',suite(),Date.now()-1).status,'ABSTAIN');
});
