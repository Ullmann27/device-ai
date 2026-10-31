import test from 'node:test';
import assert from 'node:assert/strict';
import {CANDIDATE_SHA,encodeProbeInput,observationsToReceipts}
  from './continuum-ewm-v2-run-jvm.mjs';

const gold=[
 {id:'M6X-001',before:'Mira wohnt in Wien.',after:'Mira wohnt jetzt in Graz.',
  unrelated:'Miras Fahrrad ist grün.',expected:'SUPERSEDES_STATE'},
 {id:'M6X-002',before:'Die Probe ist am Freitag.',after:'Korrektur: am Samstag.',
  unrelated:'Die Probe findet im Saal statt.',expected:'CORRECTS_EVENT'},
 {id:'M6X-003',before:'Anna wohnt in Graz.',after:'Anna besuchte Wien.',
  unrelated:'Eine Lampe ist gelb.',expected:'COEXISTS'}
];
const line=(id,edge,before,after,unrelated,reason='PRODUCTION_GRAPH:edges_from_after=before')=>
 [id,edge,before,after,unrelated,Buffer.from(reason,'utf8').toString('base64')].join('\t');
const normal=()=>[
 line('M6X-001','1','false','true','true'),
 line('M6X-002','1','false','true','true'),
 line('M6X-003','0','true','true','true','PRODUCTION_GRAPH:edges_from_after=')
].join('\n')+'\n';

test('input to JVM contains facts only, never gold decisions or expected classes',()=>{
 const s=encodeProbeInput(gold);
 assert.equal(s.trimEnd().split('\n').length,3);
 assert.ok(!s.includes('SUPERSEDES_STATE')&&!s.includes('CORRECTS_EVENT')&&!s.includes('COEXISTS'));
 assert.equal(s.trimEnd().split('\n')[0].split('\t').length,4);
});
test('Unicode fact input is lossless after base64 encoding',()=>{
 const s=encodeProbeInput(gold).split('\n')[0].split('\t');
 assert.equal(Buffer.from(s[2],'base64').toString('utf8'),'Mira wohnt jetzt in Graz.');
 assert.equal(Buffer.from(s[3],'base64').toString('utf8'),'Miras Fahrrad ist grün.');
});
test('real JVM edge classifications are scored without using gold as predictions',()=>{
 const rows=observationsToReceipts(normal(),gold);
 assert.equal(rows.length,3);
 assert.deepEqual(rows.map(x=>x.predicted_label),['SUPERSEDES_STATE','SUPERSEDES_STATE','COEXISTS']);
 assert.deepEqual(rows.map(x=>x.expected_label),['SUPERSEDES_STATE','CORRECTS_EVENT','COEXISTS']);
 assert.ok(rows.every(x=>x.positive_subtype==='NOT_EMITTED_BY_PRODUCT'));
 assert.equal(rows[1].candidate_sha,CANDIDATE_SHA);
});
test('lost unrelated fact is retained as observed false; not silently normalized',()=>{
 const lines=normal().trimEnd().split('\n');
 lines[0]=line('M6X-001','1','false','true','false');
 const rows=observationsToReceipts(lines.join('\n')+'\n',gold);
 assert.equal(rows[0].unrelated_fact_kept,false);
});
test('missing JVM output fails closed',()=>{
 assert.throws(()=>observationsToReceipts(normal().trimEnd().split('\n').slice(0,2).join('\n'),gold),/JVM_RESULT_COUNT_MISMATCH/);
});
test('changed JVM case order fails closed',()=>{
 const lines=normal().trimEnd().split('\n');
 [lines[0],lines[1]]=[lines[1],lines[0]];
 assert.throws(()=>observationsToReceipts(lines.join('\n'),gold),/JVM_RESULT_ORDER_MISMATCH/);
});
test('invalid binary edge cannot become positive',()=>{
 const lines=normal().trimEnd().split('\n');
 lines[0]=line('M6X-001','yes','false','true','true');
 assert.throws(()=>observationsToReceipts(lines.join('\n'),gold),/JVM_INVALID_EDGE/);
});
test('missing graph-state flags are rejected',()=>{
 const lines=normal().trimEnd().split('\n');
 lines[0]=line('M6X-001','1','false','not-measured','true');
 assert.throws(()=>observationsToReceipts(lines.join('\n'),gold),/JVM_INVALID_STATE_FLAG/);
});
test('reason must originate from pinned production-graph reporter',()=>{
 const lines=normal().trimEnd().split('\n');
 lines[0]=line('M6X-001','1','false','true','true','This is not observed graph output');
 assert.throws(()=>observationsToReceipts(lines.join('\n'),gold),/JVM_INVALID_REASON/);
});
test('candidate commit identity is pinned and full length',()=>{
 assert.match(CANDIDATE_SHA,/^[a-f0-9]{40}$/);
});
