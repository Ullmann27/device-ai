import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {parseArxivAtom,recordKey,normalizeTitle,largestModel,classify,relevance,due,RadarStore,scan,report,proposals,
 markProposed,DEFAULT_TOPICS,CATEGORIES} from './research-radar.mjs';
import {BacklogStore} from './development-backlog.mjs';

// Synthetic feed in the arXiv Atom format (no real paper is claimed here).
const entry=(id,v,title,summary,published='2026-09-01T00:00:00Z')=>`<entry>
 <id>http://arxiv.org/abs/${id}v${v}</id><updated>${published}</updated><published>${published}</published>
 <title>${title}</title><summary>${summary}</summary>
 <author><name>A. Author</name></author><author><name>B. Author</name></author>
 <arxiv:doi xmlns:arxiv="http://arxiv.org/schemas/atom">10.1000/${id}</arxiv:doi>
 <category term="cs.LG" scheme="http://arxiv.org/schemas/atom"/><category term="cs.CL" scheme="http://arxiv.org/schemas/atom"/>
</entry>`;
const feed=(...entries)=>`<?xml version="1.0" encoding="UTF-8"?><feed xmlns="http://www.w3.org/2005/Atom">${entries.join('\n')}</feed>`;
const SMALL=entry('2609.00001',1,'Gated Delta Fast Weights on a CPU','We train a 125M parameter model with a delta rule memory on a laptop CPU &amp; measure associative recall.');
const SMALL_V2=entry('2609.00001',2,'Gated Delta Fast Weights on a CPU (revised)','We train a 125M parameter model with a delta rule memory on a laptop CPU.');
const BIG=entry('2609.00002',1,'Scaling Test-Time Memory','A 70B parameter language model pre-trained on 15T tokens with test-time memory on a GPU cluster.');
const PLAIN=entry('2609.00003',1,'Nested Learning for Continual Memory','We study consolidation and forgetting in nested optimisation.');
const temp=()=>fs.mkdtempSync(path.join(os.tmpdir(),'radar-'));

test('arXiv entries are parsed with versionless ids, authors, DOI and categories; ids are stable',()=>{
 const rs=parseArxivAtom(feed(SMALL,BIG,'<entry><id>http://example.org/x</id><title>no id</title></entry>'));
 assert.equal(rs.length,2,'an entry without an arXiv id is skipped');
 assert.deepEqual([rs[0].arxiv,rs[0].version,rs[0].authors.length,rs[0].doi],['2609.00001',1,2,'10.1000/2609.00001']);
 assert.match(rs[0].summary,/CPU & measure/,'entities decoded');
 assert.deepEqual(rs[0].categories,['cs.LG','cs.CL']);
 assert.equal(recordKey(rs[0]),'arxiv:2609.00001');
 assert.equal(recordKey({title:'  Gated   DELTA fast-weights!'}),recordKey({title:'gated delta fast weights'}));
 assert.equal(normalizeTitle('Über-Größe: Fast Weights'),'uber grosse fast weights');
});

test('categories follow transparent rules; BETTER and REPRODUCED only with recorded local evidence',()=>{
 const [small,big,plain]=parseArxivAtom(feed(SMALL,BIG,PLAIN));
 assert.equal(largestModel(small.summary),125e6);
 assert.equal(largestModel(big.summary),70e9);
 assert.equal(classify(small).category,'FEASIBLE');
 assert.match(classify(small).reasons.join(),/laptop|125M/);
 assert.equal(classify(big).category,'UNSUITABLE');
 assert.equal(classify(plain).category,'INTERESTING');
 assert.equal(classify(plain).preliminary,true);
 const better=new Map([['arxiv:2609.00001','docs/evidence/nf-20261022/nf2.txt']]);
 assert.deepEqual(classify(small,{better}),{category:'BETTER',reasons:['local NF result: docs/evidence/nf-20261022/nf2.txt'],preliminary:false});
 assert.ok(CATEGORIES.includes('REPRODUCED'));
 assert.ok(relevance(small,DEFAULT_TOPICS[1],Date.parse('2026-10-22'))>relevance(plain,DEFAULT_TOPICS[1],Date.parse('2026-10-22')));
});

test('the schedule runs nothing unless the owner enabled and approved it and a run is due',()=>{
 const now=Date.parse('2026-10-22T12:00:00Z');
 assert.equal(due({enabled:false},{},now).due,false);
 assert.match(due({enabled:true},{},now).reason,/not approved by the owner/);
 assert.equal(due({enabled:true,approvedBy:'owner'},{lastRun:null},now).due,true);
 assert.equal(due({enabled:true,approvedBy:'owner',intervalDays:7},{lastRun:'2026-10-20T12:00:00Z'},now).due,false);
 assert.equal(due({enabled:true,approvedBy:'owner',intervalDays:7},{lastRun:'2026-10-14T12:00:00Z'},now).due,true);
});

test('a scan deduplicates across topics and versions, waits between requests and survives network errors',async()=>{
 const dir=temp();
 const store=new RadarStore(path.join(dir,'radar.json'));
 const calls=[],sleeps=[];
 const responses=[feed(SMALL,BIG),feed(SMALL_V2,PLAIN),null];
 const fetchImpl=async url=>{calls.push(url);const body=responses[calls.length-1];
  return body===null?{ok:false,status:503,text:async()=>''}:{ok:true,status:200,text:async()=>body};};
 const run=await scan({store,topics:DEFAULT_TOPICS.slice(0,3),fetchImpl,sleep:async ms=>sleeps.push(ms),now:new Date('2026-10-22T12:00:00Z')});
 assert.equal(calls.length,3);
 assert.ok(calls.every(u=>u.startsWith('https://export.arxiv.org/api/query?search_query=')&&/max_results=25/.test(u)));
 assert.deepEqual(sleeps,[3000,3000],'polite pause between requests');
 assert.deepEqual(run.errors,[{topic:'nf3-adaptive-compute',error:'HTTP 503'}]);
 assert.equal(Object.keys(store.doc.records).length,3,'one record per paper');
 const rec=store.doc.records['arxiv:2609.00001'];
 assert.equal(rec.version,2);assert.match(rec.title,/revised/);
 assert.deepEqual(rec.topics,['nf1-memory','nf2-fast-weights']);
 assert.equal(rec.codeLicence,'unknown');
 store.save();
 const again=new RadarStore(path.join(dir,'radar.json'));
 assert.equal(again.doc.lastRun,'2026-10-22T12:00:00.000Z');
 assert.equal(again.doc.runs.length,1);
});

test('proposals only PROPOSE in the backlog, carry the six questions and are made once',async()=>{
 const dir=temp();
 const store=new RadarStore(path.join(dir,'radar.json'));
 store.upsert(parseArxivAtom(feed(SMALL,BIG,PLAIN)),DEFAULT_TOPICS[1],new Date('2026-10-22T12:00:00Z'));
 const ranked=report(store,{now:Date.parse('2026-10-22')});
 assert.equal(ranked[0].key,'arxiv:2609.00001');
 const ps=proposals(store,{limit:5,now:new Date('2026-10-22T12:00:00Z')});
 assert.deepEqual(ps.map(p=>p.key).sort(),['arxiv:2609.00001','arxiv:2609.00003'],'UNSUITABLE is never proposed');
 for(const p of ps){
  for(const q of ['1. **Mechanismus','2. **Bekannte Methode','3. **Was wäre','4. **Widerlegende Messung','5. **Kosten','6. **Abbruch'])assert.ok(p.markdown.includes(q));
  assert.match(p.markdown,/kein Code übernommen/);
 }
 const backlog=new BacklogStore(path.join(dir,'backlog.json'),{actor:'research-radar'});
 backlog.apply(ps.map(p=>({...p.op,actor:'research-radar',at:Date.now(),ifAbsent:true})),{stopOnError:false});
 const tasks=backlog.read().doc.tasks;
 assert.equal(tasks.length,2);
 assert.ok(tasks.every(t=>t.state==='PROPOSED'&&t.kind==='RESEARCH_PROPOSAL'&&t.source==='research-radar'));
 markProposed(store,ps.map(p=>p.key));
 assert.equal(proposals(store,{limit:5}).length,0,'each source is proposed once');
});
