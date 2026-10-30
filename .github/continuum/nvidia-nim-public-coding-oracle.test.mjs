/**
 * Independent RED/GREEN research oracle for author-controlled PUBLIC fixtures.
 * NEVER evaluate candidate/model-supplied text here. Only the fixed, known
 * fixture strings authored in this test are compiled.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {makeNvidiaPublicCodingChallenge,evaluateNvidiaPublicCodingProposal}
 from './nvidia-nim-public-coding-lab.mjs';

const source=JSON.parse(makeNvidiaPublicCodingChallenge().prompt).files;
const replacements=[
 text=>text.replace('text.length <= maxBytes',"Buffer.byteLength(text, 'utf8') <= maxBytes"),
 text=>text.replace('e.validFrom <= atMillis','e.validFrom <= atMillis && (e.revokedAt === null || e.revokedAt > atMillis)')
   .replace('a.validFrom - b.validFrom','b.validFrom - a.validFrom'),
 text=>text.replace('Math.floor(runtimeSeconds','Math.ceil(runtimeSeconds')
];
const functions=['withinBudget','ownerFactAt','billedSeconds'];
const fixed=source.map((f,i)=>replacements[i](f.source));
const proposal={
 plan:['Repair public synthetic invariants using minimal changes.'],
 hypothesis:'Fix bytes, version validity, and billing roundup.',
 patches:source.map((f,i)=>({file:f.file,oldText:f.source,newText:fixed[i]}))
};
const prepare=(text,name)=>{
 // Only compile the fixed, authored fixture text above, NEVER model output.
 assert.match(text,/^export function [A-Za-z][A-Za-z0-9]*\(/);
 const code=text.replace(/^export /,'');
 return new vm.Script(code+'\n'+name+';', {filename:'public-fixture.mjs'})
  .runInNewContext({Buffer}, {timeout:1000});
};
const originals=source.map((f,i)=>prepare(f.source,functions[i]));
const repaired=fixed.map((s,i)=>prepare(s,functions[i]));
function seeded(seed){
 let state=seed>>>0;
 return ()=>{state=(1664525*state+1013904223)>>>0;return state};
}
test('public oracle cannot promote a model response to actual CCC1 success',()=>{
 const a=evaluateNvidiaPublicCodingProposal(proposal);
 assert.equal(a.allExactGolden,true);
 assert.equal(a.independentExecutionVerified,false);
 assert.equal(a.ccc1LearningEligible,false);
});
test('UTF-8 bytes: 1200 exact independent cases, baseline demonstrably RED',()=>{
 const rng=seeded(20261030);
 const parts=['a','Z','ä','€','😀','🧠','\u0000'];
 let red=0;
 for(let i=0;i<1200;i++){
  let text='';const n=rng()%9;
  for(let k=0;k<n;k++)text+=parts[rng()%parts.length];
  const budget=rng()%32;
  const expected=Buffer.byteLength(text,'utf8')<=budget;
  assert.equal(repaired[0](text,budget),expected);
  if(originals[0](text,budget)!==expected)red++;
 }
 assert.ok(red>0,'baseline must fail at least one UTF-8 byte-boundary case');
});
test('time-aware owner fact: 1200 cases, revoked and stale facts rejected',()=>{
 const rng=seeded(73124);
 let red=0;
 for(let i=0;i<1200;i++){
  const now=rng()%1000;
  const events=Array.from({length:1+rng()%8},(_,index)=>{
   const validFrom=rng()%1100,revokedAt=rng()%5===0?null:validFrom+(rng()%900);
   return {index,validFrom,revokedAt};
  });
  const expected=events.filter(e=>e.validFrom<=now&&(e.revokedAt===null||e.revokedAt>now))
   .sort((a,b)=>b.validFrom-a.validFrom)[0]??null;
  assert.deepEqual(repaired[1](events,now),expected);
  const got=originals[1](events,now);
  if((got?.index??null)!==(expected?.index??null))red++;
 }
 assert.ok(red>0,'baseline must mishandle at least one historic/revoked fact');
});
test('GPU billing quantum: 1200 cases, no underbilling after repair',()=>{
 const rng=seeded(9917);let red=0;
 for(let i=0;i<1200;i++){
  const seconds=1+rng()%86400,quantum=1+rng()%3600;
  const expected=Math.ceil(seconds/quantum)*quantum;
  assert.equal(repaired[2](seconds,quantum),expected);
  if(originals[2](seconds,quantum)!==expected)red++;
 }
 assert.ok(red>0,'baseline must fail at least one fractional billing quantum');
});