import test from 'node:test';
import assert from 'node:assert/strict';
import {auditParaphrases,QUESTIONS} from './retrieval-paraphrase.mjs';
import {HOLDOUTS} from './contrastive-generalization.mjs';
test('handwritten rewordings are separate and independently checked',()=>{
 for(const [domain,questions] of Object.entries(QUESTIONS)){
  const docs=HOLDOUTS[domain];
  assert.equal(questions.length,docs.length);
  for(const q of questions)assert.ok(!docs.includes(q));
 }
 const result=auditParaphrases();
 assert.deepEqual(result,auditParaphrases());
 assert.equal(result.records.length,90); // 5 seeds * 3 topics * 3 scorers * 2 states
 assert.equal(result.automaticPromotion,false);
 for(const p of ['lexical','legacy','cosine']){
  const summary=result.summaries[p];
  assert.equal(summary.queries,75);
  assert.ok(summary.afterMRR>=0&&summary.afterMRR<=1);
  assert.ok(summary.beforeMRR>=0&&summary.beforeMRR<=1);
  assert.ok(summary.abstentions>=0&&summary.abstentions<=75);
 }
});
