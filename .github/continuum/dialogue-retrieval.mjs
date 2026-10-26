// Dialogue memory retrieval shared by the GitHub core and mirrored exactly by
// Android ContinuumMemoryDialogue.rank (cross-runtime fixture
// app/src/test/resources/continuum-dialogue.properties).
//
// Method selected on retrieval-benchmark.mjs DEV split, reported once on TEST
// (docs/retrieval-benchmark.json): a memory qualifies only with real word
// evidence (exact word, shared 5-letter prefix or trigram Dice >= 0.6) and is
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
    if(a.length>=5&&b.length>=5&&a.slice(0,5)===b.slice(0,5))best=Math.max(best,0.8);
    if(a.length>=5&&b.length>=a.length+3&&b.endsWith(a))best=Math.max(best,0.82);
    if(b.length>=5&&a.length>=b.length+3&&a.endsWith(b))best=Math.max(best,0.82);
    const A=trigrams(a),B=trigrams(b);let i=0;for(const x of A)if(B.has(x))i++;
    best=Math.max(best,2*i/(A.size+B.size));
  }
  return best;
}
/** Conservative, source-only lexical anchor gate. This does not prove factual truth.
 * The extra distinctive-term policy applies only to source observations.
 * A source has to contain ALL distinctive terms of the question. Long technical
 * terms and exact letter-digit identifiers are mandatory; if there are none,
 * short domain words are anchored rather than silently bypassed. */
const SOURCE_QUERY_OPERATORS=new Set(["analysiere","analysieren","bedeuten","bedeutet","beschreibe","beschreiben","beschreibst","charakterisiere","charakterisieren","darstelle","darstellen","definiere","definieren","definiert","erkläre","erklären","erklärst","erläutere","erläutern","erläuterst","erzaehle","funktionieren","funktionierend","funktioniert","funktionierte","funktionierten","nenne","nennen","unterscheide","unterscheiden","unterscheidet","untersuche","untersuchen","vergleiche","vergleichen","zeige","zeigen","zusammenfassen","zusammenfasst","zusammengefasst"]);
const SOURCE_INFLECTION_TAILS=["","e","n","s","en","er","es","em"];
const SOURCE_DIGIT=/\p{Nd}/u;
const SOURCE_LETTER=/\p{L}/u;

// Preserve original identifier spelling before contentWords drops short numbers.
// Every explicit ID must occur in the same candidate; this is not entailment.
function identifierEvidence(query,text){
  const identifiers=value=>[...new Set((value.normalize('NFC').toLowerCase()
    .match(/[\p{L}\p{N}]+(?:[-._][\p{L}\p{N}]+)*/gu)??[])
    .filter(token=>SOURCE_LETTER.test(token)&&SOURCE_DIGIT.test(token)))];
  const required=identifiers(query);
  const present=new Set(identifiers(text));
  return required.every(id=>present.has(id));
}

// Bounded subject/relation eligibility, following the anchored CSX1 baseline
// and LCT1's exact subject+relation requirement. This does not establish truth
// or activate either research reasoner. Other question forms keep their policy.
const LOCATION_VERBS='wohnt|wohnen|lebt|leben|arbeitet|arbeiten';
const LOCATION_PREPOSITIONS='in|im|bei|am|an|auf';
const LOCATION_QUESTION=new RegExp(`^wo (${LOCATION_VERBS}) (.{1,160}?)\\s*[?!.]*// Dialogue memory retrieval shared by the GitHub core and mirrored exactly by
// Android ContinuumMemoryDialogue.rank (cross-runtime fixture
// app/src/test/resources/continuum-dialogue.properties).
//
// Method selected on retrieval-benchmark.mjs DEV split, reported once on TEST
// (docs/retrieval-benchmark.json): a memory qualifies only with real word
// evidence (exact word, shared 5-letter prefix or trigram Dice >= 0.6) and is
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
    if(a.length>=5&&b.length>=5&&a.slice(0,5)===b.slice(0,5))best=Math.max(best,0.8);
    if(a.length>=5&&b.length>=a.length+3&&b.endsWith(a))best=Math.max(best,0.82);
    if(b.length>=5&&a.length>=b.length+3&&a.endsWith(b))best=Math.max(best,0.82);
    const A=trigrams(a),B=trigrams(b);let i=0;for(const x of A)if(B.has(x))i++;
    best=Math.max(best,2*i/(A.size+B.size));
  }
  return best;
}
/** Conservative, source-only lexical anchor gate. This does not prove factual truth.
 * The extra distinctive-term policy applies only to source observations.
 * A source has to contain ALL distinctive terms of the question. Long technical
 * terms and exact letter-digit identifiers are mandatory; if there are none,
 * short domain words are anchored rather than silently bypassed. */
const SOURCE_QUERY_OPERATORS=new Set(["analysiere","analysieren","bedeuten","bedeutet","beschreibe","beschreiben","beschreibst","charakterisiere","charakterisieren","darstelle","darstellen","definiere","definieren","definiert","erkläre","erklären","erklärst","erläutere","erläutern","erläuterst","erzaehle","funktionieren","funktionierend","funktioniert","funktionierte","funktionierten","nenne","nennen","unterscheide","unterscheiden","unterscheidet","untersuche","untersuchen","vergleiche","vergleichen","zeige","zeigen","zusammenfassen","zusammenfasst","zusammengefasst"]);
const SOURCE_INFLECTION_TAILS=["","e","n","s","en","er","es","em"];
const SOURCE_DIGIT=/\p{Nd}/u;
,'u');
const LOCATION_CLAIM=new RegExp(`^(.{1,120}?) (${LOCATION_VERBS}) (.{1,180})// Dialogue memory retrieval shared by the GitHub core and mirrored exactly by
// Android ContinuumMemoryDialogue.rank (cross-runtime fixture
// app/src/test/resources/continuum-dialogue.properties).
//
// Method selected on retrieval-benchmark.mjs DEV split, reported once on TEST
// (docs/retrieval-benchmark.json): a memory qualifies only with real word
// evidence (exact word, shared 5-letter prefix or trigram Dice >= 0.6) and is
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
    if(a.length>=5&&b.length>=5&&a.slice(0,5)===b.slice(0,5))best=Math.max(best,0.8);
    if(a.length>=5&&b.length>=a.length+3&&b.endsWith(a))best=Math.max(best,0.82);
    if(b.length>=5&&a.length>=b.length+3&&a.endsWith(b))best=Math.max(best,0.82);
    const A=trigrams(a),B=trigrams(b);let i=0;for(const x of A)if(B.has(x))i++;
    best=Math.max(best,2*i/(A.size+B.size));
  }
  return best;
}
/** Conservative, source-only lexical anchor gate. This does not prove factual truth.
 * The extra distinctive-term policy applies only to source observations.
 * A source has to contain ALL distinctive terms of the question. Long technical
 * terms and exact letter-digit identifiers are mandatory; if there are none,
 * short domain words are anchored rather than silently bypassed. */
const SOURCE_QUERY_OPERATORS=new Set(["analysiere","analysieren","bedeuten","bedeutet","beschreibe","beschreiben","beschreibst","charakterisiere","charakterisieren","darstelle","darstellen","definiere","definieren","definiert","erkläre","erklären","erklärst","erläutere","erläutern","erläuterst","erzaehle","funktionieren","funktionierend","funktioniert","funktionierte","funktionierten","nenne","nennen","unterscheide","unterscheiden","unterscheidet","untersuche","untersuchen","vergleiche","vergleichen","zeige","zeigen","zusammenfassen","zusammenfasst","zusammengefasst"]);
const SOURCE_INFLECTION_TAILS=["","e","n","s","en","er","es","em"];
const SOURCE_DIGIT=/\p{Nd}/u;
,'u');
const LOCATION_INVERTED=new RegExp(`^(?:${LOCATION_PREPOSITIONS}) (.{1,160}?) (${LOCATION_VERBS}) (.{1,120})// Dialogue memory retrieval shared by the GitHub core and mirrored exactly by
// Android ContinuumMemoryDialogue.rank (cross-runtime fixture
// app/src/test/resources/continuum-dialogue.properties).
//
// Method selected on retrieval-benchmark.mjs DEV split, reported once on TEST
// (docs/retrieval-benchmark.json): a memory qualifies only with real word
// evidence (exact word, shared 5-letter prefix or trigram Dice >= 0.6) and is
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
    if(a.length>=5&&b.length>=5&&a.slice(0,5)===b.slice(0,5))best=Math.max(best,0.8);
    if(a.length>=5&&b.length>=a.length+3&&b.endsWith(a))best=Math.max(best,0.82);
    if(b.length>=5&&a.length>=b.length+3&&a.endsWith(b))best=Math.max(best,0.82);
    const A=trigrams(a),B=trigrams(b);let i=0;for(const x of A)if(B.has(x))i++;
    best=Math.max(best,2*i/(A.size+B.size));
  }
  return best;
}
/** Conservative, source-only lexical anchor gate. This does not prove factual truth.
 * The extra distinctive-term policy applies only to source observations.
 * A source has to contain ALL distinctive terms of the question. Long technical
 * terms and exact letter-digit identifiers are mandatory; if there are none,
 * short domain words are anchored rather than silently bypassed. */
const SOURCE_QUERY_OPERATORS=new Set(["analysiere","analysieren","bedeuten","bedeutet","beschreibe","beschreiben","beschreibst","charakterisiere","charakterisieren","darstelle","darstellen","definiere","definieren","definiert","erkläre","erklären","erklärst","erläutere","erläutern","erläuterst","erzaehle","funktionieren","funktionierend","funktioniert","funktionierte","funktionierten","nenne","nennen","unterscheide","unterscheiden","unterscheidet","untersuche","untersuchen","vergleiche","vergleichen","zeige","zeigen","zusammenfassen","zusammenfasst","zusammengefasst"]);
const SOURCE_INFLECTION_TAILS=["","e","n","s","en","er","es","em"];
const SOURCE_DIGIT=/\p{Nd}/u;
,'u');
const LOCATION_TAIL=new RegExp(`^(?:seit [\\p{L}\\p{N} ]{1,40} )?(?:${LOCATION_PREPOSITIONS}) [\\p{L}\\p{N}][\\p{L}\\p{N} ._'’"-]{0,159}// Dialogue memory retrieval shared by the GitHub core and mirrored exactly by
// Android ContinuumMemoryDialogue.rank (cross-runtime fixture
// app/src/test/resources/continuum-dialogue.properties).
//
// Method selected on retrieval-benchmark.mjs DEV split, reported once on TEST
// (docs/retrieval-benchmark.json): a memory qualifies only with real word
// evidence (exact word, shared 5-letter prefix or trigram Dice >= 0.6) and is
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
    if(a.length>=5&&b.length>=5&&a.slice(0,5)===b.slice(0,5))best=Math.max(best,0.8);
    if(a.length>=5&&b.length>=a.length+3&&b.endsWith(a))best=Math.max(best,0.82);
    if(b.length>=5&&a.length>=b.length+3&&a.endsWith(b))best=Math.max(best,0.82);
    const A=trigrams(a),B=trigrams(b);let i=0;for(const x of A)if(B.has(x))i++;
    best=Math.max(best,2*i/(A.size+B.size));
  }
  return best;
}
/** Conservative, source-only lexical anchor gate. This does not prove factual truth.
 * The extra distinctive-term policy applies only to source observations.
 * A source has to contain ALL distinctive terms of the question. Long technical
 * terms and exact letter-digit identifiers are mandatory; if there are none,
 * short domain words are anchored rather than silently bypassed. */
const SOURCE_QUERY_OPERATORS=new Set(["analysiere","analysieren","bedeuten","bedeutet","beschreibe","beschreiben","beschreibst","charakterisiere","charakterisieren","darstelle","darstellen","definiere","definieren","definiert","erkläre","erklären","erklärst","erläutere","erläutern","erläuterst","erzaehle","funktionieren","funktionierend","funktioniert","funktionierte","funktionierten","nenne","nennen","unterscheide","unterscheiden","unterscheidet","untersuche","untersuchen","vergleiche","vergleichen","zeige","zeigen","zusammenfassen","zusammenfasst","zusammengefasst"]);
const SOURCE_INFLECTION_TAILS=["","e","n","s","en","er","es","em"];
const SOURCE_DIGIT=/\p{Nd}/u;
,'u');
const LOCATION_NEGATION=/(?:^|[^\p{L}\p{N}])(?:nicht|kein(?:e|en|em|er|es)?|weder|nirgends)(?=$|[^\p{L}\p{N}])/u;
// Spell out JavaScript whitespace so JVM regex defaults cannot bypass the gate.
const LOCATION_SPACE=/[\u0009-\u000d\u0020\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000\ufeff]+/gu;
function locationQuestionEvidence(query,text){
  const normalize=value=>value.normalize('NFC').toLowerCase().replace(LOCATION_SPACE,' ').trim();
  const question=LOCATION_QUESTION.exec(normalize(query));
  if(!question)return true;
  const subjects=value=>{
    const names=value.split(/\s*,\s*|\s+und\s+/u).map(part=>part.trim()
      .replace(/^["“”'‘’]+|["“”'‘’]+$/gu,'').replace(/^(?:der )?roboter /u,'')
      .replace(/^["“”'‘’]+|["“”'‘’]+$/gu,'').trim());
    if(names.some(name=>!name||name.length>120||
      !/^[\p{L}\p{N}][\p{L}\p{N} ._'’-]*$/u.test(name)||
      /(?:^| )(?:was|wie|warum|wieso|weshalb|darüber|dazu|damit|weiter)(?:$| )/u.test(name)))return null;
    return new Set(names);
  };
  const requested=subjects(question[2]);
  // Resolved follow-ups and other unsupported query syntax are outside this gate.
  if(!requested)return true;
  const relation=verb=>verb.startsWith('arbeit')?'work':'residence';
  const supports=(phrase,verb,place)=>{
    const claimed=subjects(phrase);
    return claimed&&relation(verb)===relation(question[1])&&!LOCATION_NEGATION.test(place)&&
      [...requested].every(name=>claimed.has(name));
  };
  const claim=normalize(text).replace(/[.!?]+$/u,'').trim();
  const direct=LOCATION_CLAIM.exec(claim);
  if(direct&&LOCATION_TAIL.test(direct[3])&&supports(direct[1],direct[2],direct[3]))return true;
  const inverted=LOCATION_INVERTED.exec(claim);
  if(inverted&&supports(inverted[3],inverted[2],inverted[1]))return true;
  const home=/^(?:das )?zuhause von (.{1,120}?) ist (.{1,180})$/u.exec(claim);
  if(home&&LOCATION_TAIL.test(home[2])&&supports(home[1],'wohnt',home[2]))return true;
  const moved=/^(.{1,120}?) ist nach (.{1,160}) gezogen$/u.exec(claim);
  return !!(moved&&supports(moved[1],'wohnt',moved[2]));
}

/** Directional compound policy: "Schlüssel" may occur in "Haustürschlüssel",
 * but "Fehlerrückführung" is NOT evidenced by the generic "Führung". */
export function sourceWordMatch(needle,word) {
  if(needle===word)return true;
  if(SOURCE_DIGIT.test(needle)||SOURCE_DIGIT.test(word))return false;
  for(const na of SOURCE_INFLECTION_TAILS)for(const wb of SOURCE_INFLECTION_TAILS){
    if(na.length===0&&wb.length===0)continue;
    if(!needle.endsWith(na)||!word.endsWith(wb))continue;
    const baseA=needle.slice(0,needle.length-na.length);
    const baseB=word.slice(0,word.length-wb.length);
    if(baseA.length>=6&&baseA===baseB)return true;
  }
  return needle.length>=8 && word.length>=needle.length+3 && word.endsWith(needle);
}
export function sourceTermEvidence(query,text){
  if(typeof query!=="string"||typeof text!=="string"||query.length>4096||text.length===0)return false;
  if(!identifierEvidence(query,text)||!locationQuestionEvidence(query,text))return false;
  const terms=[...new Set(contentWords(query.normalize("NFC")))]
    .filter(term=>!SOURCE_QUERY_OPERATORS.has(term));
  const source=[...new Set(contentWords(text.normalize("NFC")))];
  if(terms.length===0||source.length===0)return false;
  const hard=terms.filter(term=>term.length>=12||
    (term.length>=4&&SOURCE_LETTER.test(term)&&SOURCE_DIGIT.test(term)));
  // For short questions, never consider a shared 5-letter prefix proof of the topic.
  const anchors=hard.length?hard:terms.filter(term=>term.length>=4);
  if(anchors.length===0)return false;
  return anchors.every(needle=>source.some(word=>sourceWordMatch(needle,word)));
}
/** memories: [{id,text,...}]; semantic(query,text) optional, reported only. */
export function rankMemories(query,memories,{semantic=null,limit=3}={}){
  const q=String(query).slice(0,1000);
  const idf=idfOf(memories.map(m=>String(m.text).slice(0,1000)));
  const qv=weigh(wordGrams(q),idf);
  return memories.map(m=>{
    const text=String(m.text).slice(0,1000);
    if(!identifierEvidence(q,text)||!locationQuestionEvidence(q,text))return null;
    if((m.kind==='SOURCE_TEXT'||m.type==='SOURCE_TEXT')&&!sourceTermEvidence(q,text))return null;
    const evidence=wordEvidence(q,text);
    if(evidence<EVIDENCE_THRESHOLD)return null;
    const score=cosine(qv,weigh(wordGrams(text),idf));
    if(!(score>0))return null;
    let sem=0.5;if(semantic)try{sem=semantic(q,text);}catch{}
    return {...m,evidence,score,semantic:sem};
  }).filter(Boolean).sort((a,b)=>b.score-a.score||String(a.id).localeCompare(String(b.id))).slice(0,limit);
}
