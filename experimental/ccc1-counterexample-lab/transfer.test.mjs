import test from 'node:test';
import assert from 'node:assert/strict';
import {novelFixtureSet,familyOf,NOVEL_FAMILIES} from './novel.mjs';
import {fixtureSet} from './fixtures.mjs';
import {sealFixtures} from './policy.mjs';
import {verifyWithinBudget,summarizeTransfer,ARMS} from './transfer-policy.mjs';

test('the novel cohort is sealed, disjoint from training/holdout and uses new names and forms',()=>{
 const novel=novelFixtureSet(),old=[...fixtureSet('training'),...fixtureSet('holdout')];
 assert.equal(novel.length,18);
 assert.equal(sealFixtures(novel),'5a306c87c02be2fe9fe2bfe8da9beb0e8711c9de1d339e5a280f2f281adcfe68');
 assert.equal(new Set([...novel,...old].map(t=>t.id)).size,novel.length+old.length);
 const oldText=old.map(t=>t.source).join('\n');
 for(const t of novel){
  assert.ok(familyOf(t.id));
  assert.ok(/\.v\+5|\(8,2\)/.test(t.source),'novel form');
  assert.ok(!oldText.includes(t.source.split('\n')[0]),'no training declaration reused');
 }
});
test('negative controls are right for neither candidate (checked by evaluating both candidates)',async()=>{
 const fs=await import('node:fs'),os=await import('node:os'),path=await import('node:path');
 const {pathToFileURL}=await import('node:url');
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'m3-neg-'));
 try{
  for(const t of novelFixtureSet()){
   const own=path.join(dir,t.id);fs.mkdirSync(own);   // one directory per task: ESM caches './helper.mjs' per URL
   fs.writeFileSync(path.join(own,'helper.mjs'),t.helper);
   const values=[];
   for(const [i,s] of ['JS_TYPO_REFERENCE','JS_MISSING_IMPORT'].entries()){
    const f=path.join(own,`candidate-${i}.mjs`);fs.writeFileSync(f,t.candidates[s]);
    try{values.push((await import(pathToFileURL(f).href)).amount());}catch{values.push(null);}
   }
   const right=values.filter(v=>v===t.expected).length;
   assert.equal(right,familyOf(t.id).startsWith('negative')?0:1,t.id+' '+JSON.stringify(values));
  }
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
});
test('verification stops at the first pass, respects the budget and never turns "not tried" into success',()=>{
 const calls=[];const run=s=>{calls.push(s);return s==='B';};
 assert.deepEqual(verifyWithinBudget(['A','B'],run,2),{passed:true,runs:2,strategy:'B'});
 assert.deepEqual(verifyWithinBudget(['A','B'],run,1),{passed:false,runs:1,strategy:null});
 assert.deepEqual(verifyWithinBudget([],run,1),{passed:null,runs:0,strategy:null});
 assert.throws(()=>verifyWithinBudget(['A'],()=>'yes',1),/ORACLE_NOT_BOOLEAN/);
 assert.throws(()=>verifyWithinBudget(['A'],run,9),/INVALID_VERIFY/);
});
test('a negative control reported as success aborts the summary',()=>{
 const rows=novelFixtureSet().map(t=>Object.fromEntries([['task',t.id],...ARMS.map(a=>[a,{passed:false,runs:1}])]));
 const s=summarizeTransfer(rows,familyOf);
 assert.equal(s.arms.baseline.failed,18);assert.equal(s.productionActivationAllowed,false);
 assert.deepEqual(Object.keys(s.byFamily).sort(),[...NOVEL_FAMILIES].sort());
 const neg=rows.findIndex(r=>r.task.startsWith('novel-negative'));
 rows[neg].guardedVerify={passed:true,runs:1};
 assert.throws(()=>summarizeTransfer(rows,familyOf),/NEGATIVE_CONTROL_REPORTED_SUCCESS/);
});
