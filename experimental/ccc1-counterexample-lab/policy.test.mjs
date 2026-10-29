import test from 'node:test';
import assert from 'node:assert/strict';
import {fixtureSet} from './fixtures.mjs';
import {sha256,sealFixtures,learnConservativeGuards,
 guardedShadowChoice,summarizeTrial} from './policy.mjs';

const sample=fixtureSet('training')[0];
const ranked=[{strategy:'JS_TYPO_REFERENCE'},{strategy:'JS_MISSING_IMPORT'}];
const obs=(t,s,passed)=>({task:t.id,contextKey:t.contextKey,feature:t.feature,
 strategy:s,sourceSha256:sha256(t.source),passed,exitCode:passed?0:1,
 executedTests:1,processExited:true,runId:sha256(t.id+'|'+s)});

test('sealed holdout stable under order, sensitive to tampering',()=>{
 const h=fixtureSet('holdout');
 assert.equal(sealFixtures(h),'23a3a3bbc3d4769ccaaa92d5a73c6032dd4beee631cde12b0768208932d30831');
 assert.equal(sealFixtures(h),sealFixtures([...h].reverse()));
 assert.notEqual(sealFixtures(h),sealFixtures(h.map((x,i)=>i===0?{...x,expected:x.expected+1}:x)));
 assert.throws(()=>sealFixtures([h[0],h[0],...h.slice(1)]),/INVALID_HOLDOUT/);
});
test('fixture feature cannot be forged contrary to helper exports',()=>{
 assert.throws(()=>sealFixtures(fixtureSet('holdout').map((x,i)=>i===0?
  {...x,feature:'BOTH_AVAILABLE'}:x)),/FORGED_SOURCE_FEATURE/);
});
test('fake, incomplete or mismatched oracle results fail closed',()=>{
 assert.throws(()=>learnConservativeGuards([{...obs(sample,'JS_TYPO_REFERENCE',true),executedTests:0}]),/ORACLE/);
 assert.throws(()=>learnConservativeGuards([{...obs(sample,'JS_TYPO_REFERENCE',true),exitCode:1}]),/ORACLE/);
 assert.throws(()=>learnConservativeGuards([obs(sample,'JS_TYPO_REFERENCE',true),
  obs(sample,'JS_TYPO_REFERENCE',true)]),/DUPLICATE/);
});
test('consistent paired evidence permits a shadow-only recommendation',()=>{
 const t=fixtureSet('training').slice(0,2);
 const records=t.flatMap(x=>[obs(x,'JS_TYPO_REFERENCE',true),obs(x,'JS_MISSING_IMPORT',false)]);
 const choice=guardedShadowChoice(t[0],ranked,learnConservativeGuards(records));
 assert.equal(choice.choice,'JS_TYPO_REFERENCE');
 assert.equal(choice.writeAllowed,false);assert.equal(choice.learningAllowed,false);
});
test('contradictory same-feature outcomes result in abstention',()=>{
 const t=fixtureSet('training').slice(0,2);
 const records=[obs(t[0],'JS_TYPO_REFERENCE',true),obs(t[0],'JS_MISSING_IMPORT',false),
  obs(t[1],'JS_TYPO_REFERENCE',false),obs(t[1],'JS_MISSING_IMPORT',true)];
 const choice=guardedShadowChoice(t[0],ranked,learnConservativeGuards(records));
 assert.equal(choice.choice,null);assert.equal(choice.reason,'CONTRADICTORY_COUNTEREXAMPLES');
});
test('unknown history abstains and invalid ranking is rejected',()=>{
 assert.equal(guardedShadowChoice(sample,ranked,{}).reason,'INSUFFICIENT_EVIDENCE');
 assert.throws(()=>guardedShadowChoice(sample,[ranked[0],ranked[0]],{}),/RANKING/);
});
test('holdout summary refuses changed identity and repeated cases',()=>{
 const h=fixtureSet('holdout'),digest=sealFixtures(h);
 const rows=h.map(t=>({task:t.id,baselinePassed:true,broadPassed:false,
  contextPassed:true,guardedPassed:null}));
 const s=summarizeTrial(rows,digest,digest);
 assert.equal(s.tasks,24);assert.equal(s.arms.guarded.abstentions,24);
 assert.equal(s.productionActivationAllowed,false);
 assert.throws(()=>summarizeTrial(rows,digest,sha256('changed')),/UNSEALED/);
 assert.throws(()=>summarizeTrial([rows[0],rows[0],...rows.slice(2)],digest,digest),/UNSEALED/);
});
