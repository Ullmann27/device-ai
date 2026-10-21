import test from 'node:test';
import assert from 'node:assert/strict';
import {isLocalNumericPatch} from './local-numeric-patch.mjs';
import {critique} from './repair-critic.mjs';
import {proposeCandidates} from './repair-strategies.mjs';

const oldText='export function distance(a,b){return a-b;}\n';
const good='export function distance(a,b){return a>b?a-b:b-a;}\n';
const edit=newText=>({file:'lib/math.mjs',oldText,newText});

test('restricted numeric language accepts arithmetic, comparisons, conditions and bounded parameters',()=>{
  for(const expression of ['a>b?a-b:b-a','(a-b)*(a-b)','a===b?0:(a>b?a-b:b-a)','-a + +b','a<=b&&a!==0?1.5:0']) {
    assert.equal(isLocalNumericPatch(edit('export function distance(a,b){return '+expression+';}')),true,expression);
  }
  assert.equal(critique({strategy:'LOCAL_MODEL_PATCH',edits:[edit(good)]}).reviewRequired,true);
});

test('model code cannot acquire file, network, environment or test-mutation capabilities',()=>{
  const forbidden=[
    "import {writeFileSync} from 'node:fs';writeFileSync('/tmp/owner-checkpoint','overwritten');"+good,
    "import assert from 'node:assert/strict';assert.equal=()=>{};"+good,
    'export function distance(a,b){return process.env.SECRET;}',
    'export function distance(a,b){return fetch(a);}',
    'export function distance(a,b){return a.constructor.constructor(b)();}',
    'export function distance(a,b){return a["constructor"](b);}',
    'export function distance(a,b){return (globalThis.x=1);}',
    'export function distance(a,b){while(true){}return 5;}',
    'export function distance(a,b){return a++;}',
    'export function distance(a,b){return a++ + b;}',
    'export function distance(a,b){return a-- - b;}',
    'export function distance(a,b){return `hello`;}',
    'export function distance(a,b){return a / /injected/;}',
    'export function renamed(a,b){return a-b;}',
    'export function distance(process,b){return process-b;}',
    good+'console.log("outside");',
    good+'export function another(){return 0;}'
  ];
  for(const candidate of forbidden){
    assert.equal(isLocalNumericPatch(edit(candidate)),false,candidate);
    assert.equal(critique({strategy:'LOCAL_MODEL_PATCH',edits:[edit(candidate)]}).approved,false,candidate);
  }
});

test('model recipes never replay in deterministic mode and retain review after explicit opt-in',()=>{
  const d={file:'lib/math.mjs',kind:'TEST_FAILURE',fingerprint:'aabbcc'};
  const ctx={readFile:()=>oldText,index:null,jsSiblings:new Map(),changedInBase:[],
    experience:[{outcome:'VERIFIED',strategy:'LOCAL_MODEL_PATCH',diagnostic:d.fingerprint,edits:[edit(good)]}]};
  assert.equal(proposeCandidates([d],ctx).length,0);
  assert.equal(proposeCandidates([d],{...ctx,allowLocalModelPatches:true,allowedLocalPaths:['lib/other.mjs']}).length,0);
  const replay=proposeCandidates([d],{...ctx,allowLocalModelPatches:true,allowedLocalPaths:['lib/math.mjs']});
  assert.equal(proposeCandidates([d],{...ctx,allowLocalModelPatches:true,allowedLocalPaths:['lib/math.mjs'],
    readFile:()=> 'export const addedContext=1;\n'+oldText}).length,0,'replay must revalidate the actual whole module');
  assert.equal(replay.length,1);assert.equal(replay[0].strategy,'LOCAL_MODEL_PATCH');
  assert.equal(critique(replay[0]).approved,true);assert.equal(critique(replay[0]).reviewRequired,true);
});

test('small expression limits and malformed grammar fail closed',()=>{
  for(const expr of ['','a +','(a+b','a ? b','9'.repeat(400),'('.repeat(20)+'a'+')'.repeat(20),Array(100).fill('a').join('+')])
    assert.equal(isLocalNumericPatch(edit('export function distance(a,b){return '+expr+';}')),false,expr);
  assert.equal(isLocalNumericPatch({...edit(good),file:'lib/Math.kt'}),false);
  assert.equal(isLocalNumericPatch({...edit(good),oldText:'return a-b;'}),false,'critic requires full-file context');
});
