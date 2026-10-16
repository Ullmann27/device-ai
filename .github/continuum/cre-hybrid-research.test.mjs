import test from 'node:test';
import assert from 'node:assert/strict';
import {hybridDecision,fitAndSelect,researchAudit,prepareQueryViews,selectHybridView} from './cre-hybrid-research.mjs';
import {RelationalEncoder} from './cre-encoder.mjs';

test('DEV-only selector produces bounded conservative policies and deterministic results',()=>{
 const a=fitAndSelect({seeds:[1]});
 const b=fitAndSelect({seeds:[1]});
 assert.deepEqual(a.selection,b.selection);
 assert.equal(a.selection.options,32);
 assert.ok(a.selection.feasible>=0&&a.selection.feasible<=32);
 assert.ok(a.selection.selected===null||a.selection.selected.maxFalse<=a.selection.allowedFalse);
});
test('hybrid does not add an unknown-name answer or bypass evidence',()=>{
 const m=new RelationalEncoder(42);
 const facts=[
  {id:'anna',text:'Anna wohnt in Linz.'},
  {id:'sophie',text:'Sophie wohnt in Graz.'}
 ];
 const policy={lexFloor:0,lexGap:0,evidenceFloor:0.8};
 const wordSet=new Set(['anna','sophie','wohnt','linz','graz','wo']);
 const result=hybridDecision(m,'Wo wohnt Sophie?',facts,wordSet,policy);
 assert.ok(result.answer===null || result.answer.id==='sophie');
 const unrelated=hybridDecision(m,'Was kostet der Mond?',facts,wordSet,policy);
 assert.equal(unrelated.answer,null);
});
test('historical TEST comparison reports measurements but never releases experimental model',()=>{
 const report=researchAudit();
 assert.equal(report.promotionEligible,false);
 assert.equal(report.perSeed.length,3);
 assert.equal(report.lexical.negatives,report.perSeed[0].base.negatives);
 assert.equal(report.selection.options,32);
 assert.equal(typeof report.meetsHistoricalGate,'boolean');
 assert.equal(report.untouchedChallenge.rows.length,3);
 assert.equal(report.untouchedChallenge.fingerprint.length,64);
 assert.equal(report.untouchedChallenge.lexical.positives,32);
 assert.equal(report.untouchedChallenge.lexical.negatives,12);
 assert.ok(report.untouchedChallenge.rows.every(x=>
   x.hybrid===null||(x.hybrid.positives===32&&x.hybrid.negatives===12)));
});

test('cached query views preserve uncached CRE1 retrieval decisions exactly',()=>{
 const m=new RelationalEncoder(17);
 const split={memories:[
  {id:'a',text:'Anna wohnt in Linz.'},
  {id:'b',text:'Sophie wohnt in Graz.'},
  {id:'c',text:'Die Katze liegt im Garten.'}
 ],questions:[{text:'Wo wohnt Sophie?',target:'b',form:'seen-form'}],
 unanswerable:[{text:'Was kostet eine Uhr?',target:null}]};
 const trained=new Set(['wohnt','anna','sophie','linz','graz']);
 const policy={lexFloor:0.25,lexGap:0.05,evidenceFloor:0.8};
 const views=prepareQueryViews(m,split,trained);
 for(const {text:q} of [...split.questions,...split.unanswerable]){
  const cache=selectHybridView(views.get(q),policy);
  const direct=hybridDecision(m,q,split.memories,trained,policy);
  assert.equal(cache.answer?.id??null,direct.answer?.id??null);
  assert.equal(cache.route,direct.route);
 }
});
