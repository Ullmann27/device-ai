import { test } from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import { score } from './cdx2-blind-score.mjs';
const sha = text => crypto.createHash('sha256').update(text, 'utf8').digest('hex');
const literal = '🧪 Anna ist in Graz.';
const source = { sourceId:'f:1',revision:7,sha256:sha(literal),text:literal };
const start = literal.indexOf('Graz'), expected = {text:'Graz',sourceId:'f:1',revision:7,sha256:source.sha256,start,endExclusive:start+4};
const tuple = { decision:'SPAN',...expected };
const cases = [{ id:'c1',split:'PRIMARY',sources:[source],expected },{id:'c2',split:'PRIMARY',sources:[source],expected:null}];
const rows = [{ id:'c1',RAW:tuple,VERIFIED:tuple,RULE:tuple },{id:'c2',RAW:{decision:'NOANSWER'},VERIFIED:{decision:'NOANSWER'},RULE:{decision:'NOANSWER'}}];
const run = () => score({cases},{cases:rows});
test('Exact UTF16 offsets and SHA bound source',()=>{const s=run().splits.PRIMARY.RAW;assert.equal(s.exactSupported,1);assert.equal(s.correctAbstentions,1);assert.equal(s.falseSourceAssignments,0)});
test('Wrong source is an unsupported claim and false assignment',()=>{const changed=structuredClone(rows);changed[0].RAW.sha256='0'.repeat(64);let x=score({cases},{cases:changed}).splits.PRIMARY.RAW;assert.equal(x.falseSourceAssignments,1);assert.equal(x.unsupportedPositive,1)});
test('Wrong Unicode offset is detected',()=>{const changed=structuredClone(rows);changed[0].RAW.start--;let x=score({cases},{cases:changed}).splits.PRIMARY.RAW;assert.equal(x.falseSourceAssignments,1)});
test('False abstention is not counted correct',()=>{const changed=structuredClone(rows);changed[0].RAW={decision:'NOANSWER'};let x=score({cases},{cases:changed}).splits.PRIMARY.RAW;assert.equal(x.falseAbstentions,1);assert.equal(x.exactSupported,0)});
test('Missing predictions are rejected even with plausible aggregate score',()=>{assert.throws(()=>score({cases},{cases:rows.slice(0,1)}),/Missing/)});
test('Duplicate or unexpected ids reject entire result',()=>{assert.throws(()=>score({cases},{cases:[rows[0],rows[0]]}),/Missing/);assert.throws(()=>score({cases},{cases:[rows[0],{...rows[1],id:'c3'}]}),/Missing/)});
test('Malformed empty replies never become safe abstentions',()=>{const changed=structuredClone(rows);changed[1].RAW={};let x=score({cases},{cases:changed}).splits.PRIMARY.RAW;assert.equal(x.malformed,1);assert.equal(x.correctAbstentions,0)});
test('Mutating frozen original invalidates whole run',()=>{const changed=structuredClone(cases);changed[0].sources[0].text+='!';assert.throws(()=>score({cases:changed},{cases:rows}),/integrity mismatch/)});
test('Source range cannot exceed original string',()=>{const changed=structuredClone(rows);changed[0].RAW.endExclusive=999;let x=score({cases},{cases:changed}).splits.PRIMARY.RAW;assert.equal(x.falseSourceAssignments,1)});
test('Confidence intervals are finite and no primary promotion for toy sample',()=>{const s=run().splits.PRIMARY.RAW;assert.ok(s.supportedWilson95.every(Number.isFinite));assert.equal(s.primaryGate,false)});

test('One hundred deterministic tuple mutations never pass as source-bound evidence', () => {
  for(let i=0;i<100;i++) {
    const changed=structuredClone(rows);
    const p=changed[0].RAW;
    switch(i%5) {
      case 0: p.text='fake'+i; break;
      case 1: p.sourceId='other'+i; break;
      case 2: p.revision=100+i; break;
      case 3: p.sha256='0'.repeat(64); break;
      case 4: p.endExclusive+=1+i%3; break;
    }
    const report=score({cases},{cases:changed}).splits.PRIMARY.RAW;
    assert.equal(report.exactSupported,0,'mutation '+i);
    assert.equal(report.falseSourceAssignments,1,'provenance mutation '+i);
    assert.equal(report.unsupportedPositive,1,'false positive '+i);
  }
});
