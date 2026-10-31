import test from 'node:test';
import assert from 'node:assert/strict';
import {gitBlobSha,parseJsonl,scoreHoldout} from './continuum-ewm-v2-holdout-score.mjs';
const sha='a'.repeat(40);
function fixture(predict=label=>label){
 const labels=['SUPERSEDES_STATE','CORRECTS_EVENT','COEXISTS'];
 const gold=Array.from({length:240},(_,i)=>({id:'M6X-'+String(i+1).padStart(3,'0'),expected:labels[Math.floor(i/80)]}));
 const receipts=gold.map((r,i)=>({
  id:r.id,candidate_sha:sha,expected_label:r.expected,predicted_label:predict(r.expected,i),
  reasons:'Independent runtime observation',before_current:false,after_current:true,unrelated_fact_kept:true
 }));
 return {gold,receipts};
}
test('240 correct labels PASS and precision/recall/F1 are all 1',()=>{
 const {gold,receipts}=fixture(),r=scoreHoldout(gold,receipts,sha);
 assert.equal(r.status,'PASS');
 assert.equal(r.positive.precision,1);
 assert.equal(r.positive.recall,1);
 assert.equal(r.macro_f1,1);
 assert.equal(r.per_label.CORRECTS_EVENT.precision,1);
});
test('20 false positives fail 0.95 precision threshold',()=>{
 const {gold,receipts}=fixture((label,i)=>i>=160&&i<180?'SUPERSEDES_STATE':label);
 const r=scoreHoldout(gold,receipts,sha);
 assert.equal(r.status,'FAIL');assert.equal(r.positive.predicted,180);
 assert.ok(r.positive.precision<0.95);
});
test('no positive predictions yield undefined precision, not fabricated 1.0',()=>{
 const {gold,receipts}=fixture(()=>'COEXISTS'),r=scoreHoldout(gold,receipts,sha);
 assert.equal(r.status,'FAIL');assert.equal(r.positive.precision,null);
});
test('missing prediction fails before scoring',()=>{
 const {gold,receipts}=fixture();receipts.pop();
 assert.throws(()=>scoreHoldout(gold,receipts,sha),/RECEIPT_COUNT_MISMATCH/);
});
test('duplicate ID replacing another cannot bypass completeness',()=>{
 const {gold,receipts}=fixture();receipts[239].id=receipts[0].id;
 assert.throws(()=>scoreHoldout(gold,receipts,sha),/DUPLICATE_RECEIPT_ID/);
});
test('unknown ID fails even with full record count',()=>{
 const {gold,receipts}=fixture();receipts[40].id='M6X-999';
 assert.throws(()=>scoreHoldout(gold,receipts,sha),/UNKNOWN_RECEIPT_ID/);
});
test('tampered expected label is rejected',()=>{
 const {gold,receipts}=fixture();receipts[0].expected_label='COEXISTS';
 assert.throws(()=>scoreHoldout(gold,receipts,sha),/EXPECTED_LABEL_MISMATCH/);
});
test('different candidate model SHAs cannot be mixed',()=>{
 const {gold,receipts}=fixture();receipts[25].candidate_sha='b'.repeat(40);
 assert.throws(()=>scoreHoldout(gold,receipts,sha),/CANDIDATE_MISMATCH/);
});
test('unrelated fact loss fails even at perfect precision',()=>{
 const {gold,receipts}=fixture();receipts[10].unrelated_fact_kept=false;
 const r=scoreHoldout(gold,receipts,sha);
 assert.equal(r.positive.precision,1);
 assert.equal(r.status,'FAIL');
 assert.equal(r.unrelated_fact_violations,1);
});
test('absent state-inspection flag cannot become success',()=>{
 const {gold,receipts}=fixture();delete receipts[0].unrelated_fact_kept;
 assert.throws(()=>scoreHoldout(gold,receipts,sha),/INVALID_FLAG/);
});
test('missing explanation is rejected',()=>{
 const {gold,receipts}=fixture();receipts[18].reasons='';
 assert.throws(()=>scoreHoldout(gold,receipts,sha),/MISSING_EXPLANATION/);
});
test('fully wrong predictions fail',()=>{
 const {gold,receipts}=fixture(label=>label==='COEXISTS'?'SUPERSEDES_STATE':'COEXISTS');
 const r=scoreHoldout(gold,receipts,sha);
 assert.equal(r.status,'FAIL');assert.equal(r.positive.tp,0);
});
test('JSONL parsing rejects blank and malformed lines',()=>{
 assert.throws(()=>parseJsonl('{"a":1}\n\n{"a":2}\n','R'),/BLANK_LINE/);
 assert.throws(()=>parseJsonl('{bad}','R'),/INVALID_JSON/);
});
test('git blob identity is computed using length+NUL header',()=>{
 assert.equal(gitBlobSha(Buffer.from('test\n')),'9daeafb9864cf43055ae93beb0afd6c7d144bfa4');
});
test('full 40-digit commit SHA required',()=>{
 const {gold,receipts}=fixture();
 assert.throws(()=>scoreHoldout(gold,receipts,'shortsha'),/INVALID_CANDIDATE_SHA/);
});
