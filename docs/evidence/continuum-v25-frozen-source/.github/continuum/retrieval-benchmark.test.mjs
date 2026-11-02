import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {runRetrievalBenchmark,loadBenchmark} from './retrieval-benchmark.mjs';
import {rankMemories} from './dialogue-retrieval.mjs';

test('published retrieval benchmark is reproducible from code and frozen data',()=>{
 const live=runRetrievalBenchmark();
 const published=JSON.parse(fs.readFileSync(new URL('../../docs/retrieval-benchmark.json',import.meta.url),'utf8'));
 assert.deepEqual(live,published);
});

test('adopted ranking beats the previous production ranking on DEV and on report-only TEST',()=>{
 const {methods:m}=runRetrievalBenchmark();
 assert.ok(m.adopted.dev.mrr>m.current.dev.mrr&&m.adopted.test.mrr>m.current.test.mrr);
 assert.ok(m.adopted.test.top1>m.current.test.top1);
 assert.ok(m.adopted.unknown.abstainWhenUnknown>m.current.unknown.abstainWhenUnknown,'must decline unknown topics more often');
 // the neural term adds nothing measurable on top of the adopted lexical ranking
 assert.deepEqual(m['gated-tfidf+dense'].test,m['gated-tfidf-wordgrams'].test);
});

test('benchmark queries never appear among candidate passages',()=>{
 const b=loadBenchmark(),P=new Set(b.retrieval.passages.map(p=>p.toLowerCase()));
 for(const q of [...b.retrieval.dev,...b.retrieval.test])assert.ok(!P.has(q.query.toLowerCase()));
});

test('retrieval requires word evidence and never invents a memory',()=>{
 const memories=[{id:'a',text:'Mein Forschungsprojekt Astrolux untersucht ein eigenes Wissensgebiet.'}];
 assert.equal(rankMemories('Was weißt du über Astrolux?',memories)[0].id,'a');
 assert.deepEqual(rankMemories('Was weißt du über Quantenorca?',memories),[]);
 assert.deepEqual(rankMemories('Hallo',memories),[]);
});
