import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {planReviewedLocalContext} from './continuum-reviewed-context-shadow.mjs';
const sha=x=>createHash('sha256').update(x,'utf8').digest('hex');
const source=(text,overrides={})=>({
  id:'review-a',kind:'SOURCE_TEXT',text,sha256:sha(text),reviewed:true,revoked:false,...overrides
});
test('reviewed lexical source is only an untrusted, byte-bounded model suggestion',()=>{
  const text='Der lokale Dienst X99 verwendet Port 8443. Besitzergeprüfte Unterlagen.';
  const r=planReviewedLocalContext({userQuery:'Port Dienst X99',candidatePassages:[source(text)]});
  assert.equal(r.status,'UNTRUSTED_CONTEXT_CANDIDATES');
  assert.equal(r.selectedCount,1);
  assert.equal(r.mayClaimVerifiedAnswer,false);
  assert.equal(r.mayRunTools,false);
  assert.ok(r.selectedExcerptBytes<=600);
  assert.equal(r.envelope.sources[0].sourceSha256,sha(text));
});
test('rejects unreviewed, revoked, corrupted, and unanchored candidates',()=>{
  const s=source('Der lokale Dienst X99 verwendet Port 8443. Dokumentation.');
  const variants=[{reviewed:false},{revoked:true},{sha256:'f'.repeat(64)},{text:'Anderes Thema ohne technische Begriffe.'}];
  for(const modified of variants){
    const r=planReviewedLocalContext({userQuery:'Port Dienst X99',candidatePassages:[{...s,...modified}]});
    assert.equal(r.status,'ABSTAIN_NO_REVIEWED_LEXICAL_MATCH');
    assert.equal(r.selectedCount,0);
  }
});
test('limits excerpt bytes and rejects oversized or malformed inputs',()=>{
  const text='Der lokale Dienst X99 verwendet Port 8443. '+'ä'.repeat(500);
  const r=planReviewedLocalContext({userQuery:'Port Dienst X99',candidatePassages:
    [source(text),source(text,{id:'review-b'}),source(text,{id:'review-c'})]});
  assert.ok(r.selectedExcerptBytes<=600);assert.ok(r.selectedCount<=3);
  assert.throws(()=>planReviewedLocalContext({userQuery:'',candidatePassages:[]}),/CONTEXT_QUERY_INVALID/);
  assert.throws(()=>planReviewedLocalContext({userQuery:'Port X99',candidatePassages:[],maxContextBytes:900}),/CONTEXT_LIMITS_INVALID/);
  assert.throws(()=>planReviewedLocalContext({userQuery:'Port X99',candidatePassages:new Array(65)}),/CONTEXT_CANDIDATES_INVALID/);
});
