// Reproducible retrieval benchmark for the dialogue memory ranking (no network,
// no randomness). Data: app/src/main/resources/continuum/benchmark-v1.json —
// DEV queries may tune methods, TEST queries are report-only. Passages are
// candidate memories; each query is a German paraphrase of exactly one passage.
import fs from 'node:fs';
import {fileURLToPath} from 'node:url';
import {SubwordEncoder} from './portable-subword.mjs';

const ROOT=new URL('../../',import.meta.url);
export const loadBenchmark=()=>JSON.parse(fs.readFileSync(new URL('app/src/main/resources/continuum/benchmark-v1.json',ROOT),'utf8'));
export const trainedWeights=()=>Buffer.from(fs.readFileSync(new URL('app/src/test/resources/continuum-neural-swb1.base64',ROOT),'utf8').trim(),'base64');

import {contentWords,wordGrams,idfOf,cosine,wordEvidence,weigh,rankMemories,EVIDENCE_THRESHOLD} from './dialogue-retrieval.mjs';
export {contentWords,wordGrams,idfOf,cosine,wordEvidence};

/** Methods return, per query, scores for all passages (null = abstain). */
export const METHODS={
  // Production ranking BEFORE this change: word-overlap gate, overlap/|q| + 0.35*(neural-0.5).
  current:(passages,weights)=>{
    const ORIGINAL_STOP=new Set(['was','wie','weißt','über','und','noch','dazu','darüber','das','die','der','ist','ich','mir','mein','meine','du','bitte','kannst','erzähl','erkläre','welche','dieses','thema']);
    const terms=t=>new Set(String(t).toLowerCase().match(/[\p{L}\p{N}]{3,}/gu)?.filter(x=>!ORIGINAL_STOP.has(x))??[]);
    const model=SubwordEncoder.restore(weights);
    return q=>{
      const qs=terms(q);
      const ranked=passages.map((p,i)=>{const t=terms(p),o=[...qs].filter(x=>t.has(x)).length;return {i,o,score:o/Math.max(1,qs.size)+0.35*(model.similarity(q,p)-0.5)};})
        .filter(x=>x.o>0).sort((a,b)=>b.score-a.score||a.i-b.i).slice(0,3);
      const s=passages.map(()=>null);ranked.forEach((r,k)=>{s[r.i]=3-k;});return s;
    };
  },
  // Production ranking AFTER this change (dialogue-retrieval.rankMemories), top 3 as shown in chat.
  adopted:(passages,weights)=>{
    const model=SubwordEncoder.restore(weights);
    return q=>{
      const ranked=rankMemories(q,passages.map((text,i)=>({id:String(i).padStart(3,'0'),text})),{semantic:(a,b)=>model.similarity(a,b)});
      const s=passages.map(()=>null);ranked.forEach((r,k)=>{s[Number(r.id)]=3-k;});return s;
    };
  },
  // Exact ablation of the production ranking: identical lexical code, neural term fixed at 0.5.
  'current-without-neural':(passages)=>{
    const ORIGINAL_STOP=new Set(['was','wie','weißt','über','und','noch','dazu','darüber','das','die','der','ist','ich','mir','mein','meine','du','bitte','kannst','erzähl','erkläre','welche','dieses','thema']);
    const terms=t=>new Set(String(t).toLowerCase().match(/[\p{L}\p{N}]{3,}/gu)?.filter(x=>!ORIGINAL_STOP.has(x))??[]);
    return q=>{
      const qs=terms(q);
      const ranked=passages.map((p,i)=>{const t=terms(p),o=[...qs].filter(x=>t.has(x)).length;return {i,o,score:o/Math.max(1,qs.size)};})
        .filter(x=>x.o>0).sort((a,b)=>b.score-a.score||a.i-b.i).slice(0,3);
      const s=passages.map(()=>null);ranked.forEach((r,k)=>{s[r.i]=3-k;});return s;
    };
  },
  'bm25-words':(passages)=>{
    const docs=passages.map(contentWords),avg=docs.reduce((a,d)=>a+d.length,0)/docs.length,N=docs.length;
    const df=new Map();docs.forEach(d=>new Set(d).forEach(w=>df.set(w,(df.get(w)??0)+1)));
    return q=>docs.map(d=>{let s=0;for(const w of new Set(contentWords(q))){const f=d.filter(x=>x===w).length;if(!f)continue;
      const idf=Math.log(1+(N-(df.get(w)??0)+0.5)/((df.get(w)??0)+0.5));s+=idf*f*2.2/(f+1.2*(0.25+0.75*d.length/avg));}return s>0?s:null;});
  },
  'tfidf-wordgrams':(passages)=>{
    const idf=idfOf(passages,wordGrams),P=passages.map(p=>weigh(wordGrams(p),idf));
    return q=>{const v=weigh(wordGrams(q),idf);return P.map(p=>{const c=cosine(v,p);return c>0?c:null;});};
  },
  // Adopted: tf-idf word-grams ranked, but only memories with real word evidence qualify.
  'gated-tfidf-wordgrams':(passages)=>{
    const idf=idfOf(passages,wordGrams),P=passages.map(p=>weigh(wordGrams(p),idf));
    return q=>{const v=weigh(wordGrams(q),idf);return P.map((p,i)=>wordEvidence(q,passages[i])>=EVIDENCE_THRESHOLD?cosine(v,p):null);};
  },
  'gated-tfidf+dense':(passages,weights,lambda=0.1)=>{
    const base=METHODS['gated-tfidf-wordgrams'](passages),m=SubwordEncoder.restore(weights);
    return q=>base(q).map((s,i)=>s==null?null:s+lambda*(m.similarity(q,passages[i])-0.5));
  }
};

export function evaluate(scorer,queries,{removeTarget=false,passages}={}){
  let top1=0,mrr=0,recall3=0,answered=0,abstained=0,wrongHit=0;
  for(const q of queries){
    const s=scorer(q.query);
    if(removeTarget){ // the true memory is absent: correct behavior is to abstain
      const others=s.map((x,i)=>i===q.passage?null:x);
      if(others.every(x=>x==null))abstained++;else wrongHit++;continue;
    }
    if(s.every(x=>x==null))continue;
    answered++;
    const t=s[q.passage];if(t==null)continue;
    const rank=1+s.filter(x=>x!=null&&x>t).length;
    if(rank===1)top1++;if(rank<=3)recall3++;mrr+=1/rank;
  }
  const n=queries.length,r=x=>Number(x.toFixed(3));
  return removeTarget?{abstainWhenUnknown:r(abstained/n),falseHitWhenUnknown:r(wrongHit/n)}
    :{top1:r(top1/n),mrr:r(mrr/n),recallAt3:r(recall3/n),answered:r(answered/n)};
}

/** How often does the neural term change the top-3 answer set? */
export function neuralInfluence(passages,queries,weights){
  const withNeural=METHODS.current(passages,weights),without=METHODS['current-without-neural'](passages);
  const top=s=>s.map((x,i)=>[x,i]).filter(([x])=>x!=null).sort((a,b)=>b[0]-a[0]||a[1]-b[1]).slice(0,3).map(([,i])=>i).join(',');
  let changed=0;for(const q of queries)if(top(withNeural(q.query))!==top(without(q.query)))changed++;
  return Number((changed/queries.length).toFixed(3));
}

export function runRetrievalBenchmark(){
  const bench=loadBenchmark(),P=bench.retrieval.passages,weights=trainedWeights();
  const untrained=new SubwordEncoder().save();
  const rows={};
  for(const [name,factory] of Object.entries(METHODS)){
    const scorer=factory(P,weights);
    rows[name]={dev:evaluate(scorer,bench.retrieval.dev),test:evaluate(scorer,bench.retrieval.test),
      unknown:evaluate(scorer,[...bench.retrieval.dev,...bench.retrieval.test],{removeTarget:true,passages:P})};
  }
  const untrainedCurrent=METHODS.current(P,untrained);
  rows['current (untrained encoder)']={dev:evaluate(untrainedCurrent,bench.retrieval.dev),test:evaluate(untrainedCurrent,bench.retrieval.test)};
  return {benchmark:bench.name+'@'+bench.version,passages:P.length,devQueries:bench.retrieval.dev.length,testQueries:bench.retrieval.test.length,
    neuralInfluenceOnCurrentTop3:neuralInfluence(P,[...bench.retrieval.dev,...bench.retrieval.test],weights),methods:rows};
}

if(process.argv[1]===fileURLToPath(import.meta.url)){
  const result=runRetrievalBenchmark();
  if(process.argv.includes('--write'))fs.writeFileSync(new URL('docs/retrieval-benchmark.json',ROOT),JSON.stringify(result,null,1)+'\n');
  console.log('| Method | DEV top1 | DEV MRR | TEST top1 | TEST MRR | TEST R@3 | abstain if unknown |');
  console.log('|---|---|---|---|---|---|---|');
  for(const [name,r] of Object.entries(result.methods))console.log(`| ${name} | ${r.dev.top1} | ${r.dev.mrr} | ${r.test.top1} | ${r.test.mrr} | ${r.test.recallAt3} | ${r.unknown?.abstainWhenUnknown??'—'} |`);
  console.log('Neural term changes the current top-3 for',result.neuralInfluenceOnCurrentTop3,'of all queries');
}
