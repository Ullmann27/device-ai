import test from 'node:test';
import assert from 'node:assert/strict';
import {auditRetrievalAlignment} from './retrieval-alignment.mjs';

test('audits the actual lexical production retriever versus two neural scores',()=>{
  const report=auditRetrievalAlignment();
  assert.deepEqual(report,auditRetrievalAlignment());
  assert.equal(report.schema,1);
  assert.deepEqual(report.policies,['legacy','cosine','lexical']);
  assert.equal(report.cases.length,135); // 5 seeds × 3 domains × 3 masks × 3 scorers
  assert.equal(report.automatedPromotion,false);
  for(const policy of report.policies){
    const s=report.summaries[policy];
    assert.equal(s.cases,45);
    assert.equal(s.cases,s.improved+s.regressed+s.unchanged);
    for(const metric of ['meanMRRBefore','meanMRRAfter'])
      assert.ok(Number.isFinite(s[metric]) && s[metric]>=0&&s[metric]<=1);
  }
  assert.equal(report.unknownQueryControls.length,15);
  for(const x of report.unknownQueryControls)
    assert.equal(x.lexicalHits,0,'Unknown nonsense must not select a remembered fact');
});
