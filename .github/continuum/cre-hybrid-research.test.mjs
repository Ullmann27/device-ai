import test from 'node:test';
import assert from 'node:assert/strict';
import {hybridDecision,fitAndSelect,researchAudit} from './cre-hybrid-research.mjs';
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
});
