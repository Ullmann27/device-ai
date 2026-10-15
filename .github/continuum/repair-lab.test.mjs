import test from 'node:test';
import assert from 'node:assert/strict';
import {chooseCandidates,applyCandidate,runLab} from './repair-lab.mjs';
test('offline agent identifies deterministic repair',()=>{
 const found=chooseCandidates([{path:'main.kt',content:'val recent = items.toSet().takeLast(3)'}]);
 assert.equal(found.length,1);
 assert.equal(applyCandidate('val recent = items.toSet().takeLast(3)',found[0]),
   'val recent = items.toSet().toList().takeLast(3)');
});
test('ambiguous replacements are rejected',()=>{
 const c={from:'x',to:'y'};
 assert.throws(()=>applyCandidate('xx',c));
});
test('dry run leaves repository unchanged',()=>{
 const result=runLab(process.cwd(),{execute:false});
 assert.equal(result.appliedToRepository,false);
 assert.equal(result.mode,'dry-run');
});
