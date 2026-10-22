// Dialogue memory retrieval shared by the GitHub core and mirrored exactly by
// Android ContinuumMemoryDialogue.rank (cross-runtime fixture
// app/src/test/resources/continuum-dialogue.properties).
//
// Method selected on retrieval-benchmark.mjs DEV split, reported once on TEST
// (docs/retrieval-benchmark.json): a memory qualifies only with real word
// evidence (exact word, tightly matched inflection/compound or trigram Dice >= 0.6) and is
// ranked by TF-IDF cosine over character 3-5 grams inside content words, with
// IDF learned from the current memory corpus. The neural similarity is still
// computed and reported but has no weight: on the benchmark it changed the
// top-3 order for 44% of queries without a consistent gain (noise tie-breaker).
export const STOP=new Set(['was','wie','weißt','über','und','noch','dazu','darüber','das','die','der','ist','ich','mir','mein','meine','du','bitte','kannst','erzähl','erkläre','welche','dieses','thema',
  'den','dem','des','ein','eine','einer','eines','einem','einen','mit','von','vom','zum','zur','für','auf','aus','bei','sich','sie','er','es','wir','ihr','sind','wird','werden','wurde','wurden','hat','haben','im','in','an','am','zu','oder','als','auch','nicht','nur','man','wo','wer','warum','wieso','weshalb','wann','woher','wohin','womit','woraus','worauf','gibt','kann','macht','dass','dabei','dadurch','deshalb','heute','meist','oft','etwa','viele','vielen','ganz','später','früher','etwas','mehr','erzähle','sag','sage','weiß','kennst','bekannt']);
export const EVIDENCE_THRESHOLD=0.6;
export const words=t=>(String(t).toLowerCase().match(/[\p{L}\p{N}]+/gu)??[]);
export const contentWords=t=>words(t).filter(w=>w.length>=3&&!STOP.has(w));
export function wordGrams(t){
  const m=new Map();
  for(const w of contentWords(t)){const p='^'+w+'$';for(let k=3;k<=5;k++)for(let i=0;i<=p.length-k;i++){const g=p.slice(i,i+k);m.set(g,(m.get(g)??0)+1);}}
  return m;
}
export function idfOf(docs,feature=wordGrams){
  const df=new Map();for(const d of docs)for(const g of feature(d).keys())df.set(g,(df.get(g)??0)+1);
  const N=docs.length,idf=new Map();for(const [g,c] of df)idf.set(g,Math.log((N+1)/(c+0.5)));
  return {get:g=>idf.get(g)??Math.log(N+1)};
}
export function weigh(counts,idf){const v=new Map();for(const [g,c] of counts)v.set(g,(1+Math.log(c))*idf.get(g));return v;}
export function cosine(a,b){let d=0,na=0,nb=0;for(const [g,x] of a){na+=x*x;const y=b.get(g);if(y)d+=x*y;}for(const y of b.values())nb+=y*y;return na&&nb?d/Math.sqrt(na*nb):0;}
const trigrams=w=>{const p='^'+w+'$',s=new Set();for(let i=0;i<=p.length-3;i++)s.add(p.slice(i,i+3));return s;};
export function wordEvidence(query,text){
  const q=contentWords(query),t=[...new Set(contentWords(text))];
  let best=0;
  for(const a of q)for(const b of t){
    if(a===b)return 1;
    // Alphanumeric identifiers such as Quantenkarussell987 require exact word evidence.
    if(/[0-9]/u.test(a)||/[0-9]/u.test(b))continue;
    const shorter=Math.min(a.length,b.length);
    let prefix=0;while(prefix<shorter&&a[prefix]===b[prefix])prefix++;
    if(shorter>=6&&Math.abs(a.length-b.length)<=3&&prefix>=shorter-2)best=Math.max(best,0.8);
    if(a.length>=5&&b.length>=a.length+3&&b.endsWith(a))best=Math.max(best,0.82);
    if(b.length>=5&&a.length>=b.length+3&&a.endsWith(b))best=Math.max(best,0.82);
    const A=trigrams(a),B=trigrams(b);let i=0;for(const x of A)if(B.has(x))i++;
    best=Math.max(best,2*i/(A.size+B.size));
  }
  return best;
}
/** Public citations require a complete long-term anchor, not generic compound tails.
 * Owner-confirmed memories preserve broader compound recognition. */
export function sourceTermEvidence(query,text){
  const terms=[...new Set(contentWords(query))];
  if(terms.length!==1||terms[0].length<12)return true;
  const needle=terms[0];
  return contentWords(text).some(word=>{
    if(word===needle)return true;
    if(/[0-9]/u.test(needle)||/[0-9]/u.test(word))return false;
    const shorter=Math.min(needle.length,word.length);
    let prefix=0;while(prefix<shorter&&needle[prefix]===word[prefix])prefix++;
    return shorter>=6&&Math.abs(needle.length-word.length)<=3&&prefix>=shorter-2;
  });
}
/** memories: [{id,text,...}]; semantic(query,text) optional, reported only. */
export function rankMemories(query,memories,{semantic=null,limit=3}={}){
  const q=String(query).slice(0,1000);
  const idf=idfOf(memories.map(m=>String(m.text).slice(0,1000)));
  const qv=weigh(wordGrams(q),idf);
  return memories.map(m=>{
    const text=String(m.text).slice(0,1000);
    if((m.kind==='SOURCE_TEXT'||m.type==='SOURCE_TEXT')&&!sourceTermEvidence(q,text))return null;
    const evidence=wordEvidence(q,text);
    if(evidence<EVIDENCE_THRESHOLD)return null;
    const score=cosine(qv,weigh(wordGrams(text),idf));
    if(!(score>0))return null;
    let sem=0.5;if(semantic)try{sem=semantic(q,text);}catch{}
    return {...m,evidence,score,semantic:sem};
  }).filter(Boolean).sort((a,b)=>b.score-a.score||String(a.id).localeCompare(String(b.id))).slice(0,limit);
}
