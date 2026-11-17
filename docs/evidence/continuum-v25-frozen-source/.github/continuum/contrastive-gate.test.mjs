import test from 'node:test';
import assert from 'node:assert/strict';
import {assessContrastiveCandidate} from './contrastive-gate.mjs';
import {auditGeneralization} from './contrastive-generalization.mjs';
import {auditRetention} from './contrastive-retention.mjs';
function favorable(){
 const seeds=[11,73,191],domains=['a','b','c'],drops=[0.45,0.65];
 return {
  general:{schema:1,cases:seeds.flatMap(modelSeed=>domains.flatMap(domain=>
   drops.map(drop=>({modelSeed,domain,drop,beforeMRR:0.4,afterMRR:0.47,deltaMRR:0.07}))))},
  retention:{schema:1,cases:seeds.map(modelSeed=>({
   modelSeed,intent:{total:6,beforeCorrect:5,afterCorrect:5,beforeLoss:0.3,afterLoss:0.301},
   semantic:{total:6,beforeCorrect:5,afterCorrect:5,beforeLoss:0.3,afterLoss:0.301}
  }))}
 };
}
test('actual noisy-view experiments are not mistakenly promoted',()=>{
 const d=assessContrastiveCandidate(auditGeneralization(),auditRetention());
 assert.equal(d.status,'REJECTED');
 assert.equal(d.automaticPromotion,false);
 assert.ok(d.reasons.includes('HOLDOUT_GAIN_INSUFFICIENT'));
});
test('strong synthetic evidence yields review only, never automatic promotion',()=>{
 const {general,retention}=favorable(),d=assessContrastiveCandidate(general,retention);
 assert.equal(d.status,'REVIEW_REQUIRED');
 assert.equal(d.automaticPromotion,false);
 assert.deepEqual(d.reasons,[]);
});
test('failing protected tasks reject even if holdout scores rise',()=>{
 const {general,retention}=favorable();
 retention.cases[0].intent.afterCorrect=4;
 const d=assessContrastiveCandidate(general,retention);
 assert.equal(d.status,'REJECTED');
 assert.ok(d.reasons.includes('SKILL_ACCURACY_REGRESSION'));
});
test('malformed, duplicate and incomplete evidence fails closed',()=>{
 const {general,retention}=favorable();
 assert.equal(assessContrastiveCandidate(null,retention).status,'REJECTED');
 general.cases.push({...general.cases[0]});
 assert.ok(assessContrastiveCandidate(general,retention).reasons.includes('DUPLICATE_CASE'));
 general.cases.pop();general.cases[0].afterMRR=Infinity;
 assert.ok(assessContrastiveCandidate(general,retention).reasons.includes('INVALID_RETRIEVAL_DATA'));
});
