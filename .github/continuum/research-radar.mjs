// Continuum Research Radar (Ergebnis C): finds new publications for the Neural Frontier Lab, removes duplicates,
// stores the sources, assigns a transparent PRELIMINARY category and proposes - never executes - implementation
// attempts. Free source only (arXiv API, no key, polite 3 s between requests). Runs only when the owner enabled AND
// approved it and a run is due (default weekly); `scan --force` is the owner's manual run.
// Categories (owner wording):
//   INTERESTING  theoretisch interessant
//   REPRODUCED   bereits unabhängig reproduziert   - only with recorded evidence (reproduction paper or our own run)
//   FEASIBLE     auf lokaler Hardware praktisch umsetzbar - rule on sizes/hardware named in the abstract
//   BETTER       messbar besser als unsere Methode - ONLY from a linked local NF experiment, never from a paper's claim
//   UNSUITABLE   nicht geeignet oder derzeit zu teuer - rule on large-scale training named in the abstract
// Rights: ideas may inspire our own implementations; code is never copied without checking its licence. arXiv
// metadata does not carry the code licence, so every record starts with codeLicence 'unknown'.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';

export const SCHEMA='continuum-research-radar';
export const CATEGORIES=Object.freeze(['INTERESTING','REPRODUCED','FEASIBLE','BETTER','UNSUITABLE']);
export const API='https://export.arxiv.org/api/query';

/** Topics of the Neural Frontier Lab; `terms` weigh relevance (title counts twice). */
export const DEFAULT_TOPICS=Object.freeze([
 {id:'nf1-memory',experiment:'NF-1',query:'abs:"test-time memory" OR abs:"nested learning" OR abs:"continual learning" AND abs:"language model"',
  terms:['memory','test-time','continual','forgetting','consolidation','nested','titans','fast weights','replay']},
 {id:'nf2-fast-weights',experiment:'NF-2',query:'abs:"delta rule" OR abs:"fast weights" OR abs:"gated deltanet" OR abs:"linear attention"',
  terms:['delta','fast weight','linear attention','recurrent','state space','associative recall','gating']},
 {id:'nf3-adaptive-compute',experiment:'NF-3',query:'abs:"byte latent" OR abs:"dynamic patching" OR abs:"adaptive computation" OR abs:"mixture of depths"',
  terms:['byte','patch','entropy','adaptive','tokenizer-free','compute','early exit','depth']},
 {id:'nf4-sparse-events',experiment:'NF-4',query:'abs:"activation sparsity" OR abs:"spiking language model" OR abs:"sparse activation" AND abs:CPU',
  terms:['sparse','sparsity','spiking','relu','threshold','event','cpu','neuromorphic']},
 {id:'nf5-verified-coding',experiment:'NF-5',query:'abs:"program synthesis" AND abs:verifier OR abs:"execution feedback" AND abs:"small model"',
  terms:['verifier','execution','program synthesis','test','repair','agent','feedback','self-improvement']},
 {id:'cpu-efficiency',experiment:'all',query:'abs:"on-device" AND abs:"language model" AND abs:quantization',
  terms:['on-device','mobile','cpu','quantization','arm','edge','int8','4-bit']}
]);

const unescape=s=>s.replace(/&lt;/g,'<').replace(/&gt;/g,'>').replace(/&quot;/g,'"').replace(/&apos;/g,"'").replace(/&amp;/g,'&');
const tag=(xml,name)=>{const m=xml.match(new RegExp('<'+name+'(?:\\s[^>]*)?>([\\s\\S]*?)</'+name+'>'));return m?unescape(m[1]).replace(/\s+/g,' ').trim():null;};

/** arXiv Atom feed -> records. Tolerant: an entry without an arXiv id is skipped, never guessed. */
export function parseArxivAtom(xml){
 const out=[];
 for(const m of String(xml).matchAll(/<entry>([\s\S]*?)<\/entry>/g)){
  const e=m[1];
  const idUrl=tag(e,'id')??'';
  const id=idUrl.match(/arxiv\.org\/abs\/([^\s<]+?)(v(\d+))?$/);
  if(!id)continue;
  out.push({arxiv:id[1],version:id[3]?Number(id[3]):1,title:tag(e,'title')??'',summary:tag(e,'summary')??'',
   authors:[...e.matchAll(/<author>\s*<name>([\s\S]*?)<\/name>/g)].map(a=>unescape(a[1]).trim()).slice(0,20),
   published:tag(e,'published'),updated:tag(e,'updated'),doi:tag(e,'arxiv:doi'),
   categories:[...e.matchAll(/<category[^>]*term="([^"]+)"/g)].map(c=>c[1]),url:'https://arxiv.org/abs/'+id[1]});
 }
 return out;
}

export const normalizeTitle=t=>String(t).toLowerCase().replace(/ß/g,'ss').normalize('NFKD').replace(/[\u0300-\u036f]/g,'').replace(/[^a-z0-9]+/g,' ').trim();
/** Stable identity across versions and mirrors: arXiv id, else DOI, else the normalized title. */
export const recordKey=r=>r.arxiv?'arxiv:'+r.arxiv:r.doi?'doi:'+r.doi.toLowerCase():
 'title:'+createHash('sha256').update(normalizeTitle(r.title)).digest('hex').slice(0,24);

const PARAMS=/(\d+(?:\.\d+)?)\s*(billion|b|million|m)\b(?:\s*-?\s*parameters?|\s+params?)?/gi;
/** Largest model size named in the text, in parameters (null when none is named). */
export function largestModel(text){
 let best=null;
 for(const m of String(text).matchAll(PARAMS)){
  const unit=m[2].toLowerCase();
  const near=String(text).slice(m.index,m.index+40);
  if(!/param|model|llm|lm\b/i.test(near)&&!/(b|m)\b/.test(m[0]))continue;
  const n=Number(m[1])*(unit.startsWith('b')?1e9:1e6);
  if(best===null||n>best)best=n;
 }
 return best;
}

/** Preliminary category with the evidence for it; BETTER and REPRODUCED need recorded local evidence. */
export function classify(record,{better=new Map(),reproduced=new Map()}={}){
 const key=recordKey(record),text=record.title+' '+record.summary,reasons=[];
 if(better.has(key))return {category:'BETTER',reasons:['local NF result: '+better.get(key)],preliminary:false};
 if(reproduced.has(key))return {category:'REPRODUCED',reasons:['reproduction: '+reproduced.get(key)],preliminary:false};
 const size=largestModel(text);
 const heavy=/\b(gpu cluster|tpu pod|thousands of gpus|\d{3,}\s*(?:h100|a100|gpus)|trillion tokens|pre-?train(?:ing|ed)? on \d+(?:\.\d+)?t)\b/i.exec(text);
 if(heavy||size!==null&&size>=7e9){
  if(heavy)reasons.push('large-scale compute named: "'+heavy[0]+'"');
  if(size!==null&&size>=7e9)reasons.push('model size named: '+(size/1e9)+'B parameters');
  return {category:'UNSUITABLE',reasons,preliminary:true};
 }
 const light=/\b(on-device|mobile|smartphone|edge device|cpu|microcontroller|tinyml|single gpu|laptop|raspberry pi|arm)\b/i.exec(text);
 if(light||size!==null&&size<=1e9){
  if(light)reasons.push('local hardware named: "'+light[0]+'"');
  if(size!==null&&size<=1e9)reasons.push('model size named: '+(size/1e6)+'M parameters');
  return {category:'FEASIBLE',reasons,preliminary:true};
 }
 return {category:'INTERESTING',reasons:['matches a lab topic; no size or hardware statement found'],preliminary:true};
}

/** Relevance of a record for one topic: weighted term hits (title twice), plus up to 1 point for recency. */
export function relevance(record,topic,now=Date.now()){
 const title=record.title.toLowerCase(),body=record.summary.toLowerCase();
 let score=0;
 for(const t of topic.terms){if(title.includes(t))score+=2;if(body.includes(t))score+=1;}
 const ageDays=record.published?(now-Date.parse(record.published))/864e5:365;
 return score+Math.max(0,1-ageDays/365);
}

/** Owner-controlled schedule: nothing runs unless enabled, approved by the owner and due. */
export function due(config,state,now=Date.now()){
 if(config?.enabled!==true)return {due:false,reason:'radar disabled'};
 if(config?.approvedBy!=='owner')return {due:false,reason:'not approved by the owner'};
 const days=Number.isFinite(config.intervalDays)&&config.intervalDays>=1?config.intervalDays:7;
 const last=state?.lastRun?Date.parse(state.lastRun):0;
 if(now-last<days*864e5)return {due:false,reason:'next run after '+new Date(last+days*864e5).toISOString()};
 return {due:true,reason:'due'};
}

/** JSON store of all sources ever seen; atomic writes, no deletion. */
export class RadarStore{
 constructor(file){this.file=file;this.doc={schema:SCHEMA,version:1,lastRun:null,records:{},runs:[]};
  if(fs.existsSync(file)){const d=JSON.parse(fs.readFileSync(file,'utf8'));if(d.schema!==SCHEMA)throw Error('not a radar store');this.doc=d;}}
 upsert(records,topic,now=new Date()){
  let added=0,updated=0,duplicates=0;
  for(const r of records){
   const key=recordKey(r);const old=this.doc.records[key];
   if(!old){this.doc.records[key]={...r,key,topics:[topic.id],firstSeen:now.toISOString(),lastSeen:now.toISOString(),
    codeLicence:'unknown',proposed:false};added++;continue;}
   duplicates++;
   const next={...old,lastSeen:now.toISOString(),topics:[...new Set([...old.topics,topic.id])]};
   if(r.version>old.version){Object.assign(next,{version:r.version,title:r.title,summary:r.summary,updated:r.updated});updated++;}
   this.doc.records[key]=next;
  }
  return {added,updated,duplicates};
 }
 save(){
  fs.mkdirSync(path.dirname(this.file),{recursive:true});
  const tmp=this.file+'.tmp-'+process.pid;
  fs.writeFileSync(tmp,JSON.stringify(this.doc,null,1));fs.renameSync(tmp,this.file);
 }
}

/**
 * One scan over the topics. Network errors are recorded per topic and never abort the run. [fetchImpl] and [sleep]
 * are injectable (tests, offline workers).
 */
export async function scan({store,topics=DEFAULT_TOPICS,maxResults=25,fetchImpl=globalThis.fetch,sleep=ms=>new Promise(r=>setTimeout(r,ms)),now=new Date()}){
 const run={at:now.toISOString(),topics:[],errors:[]};
 for(const [i,topic] of topics.entries()){
  if(i>0)await sleep(3000);
  const url=API+'?search_query='+encodeURIComponent(topic.query)+'&sortBy=submittedDate&sortOrder=descending&max_results='+maxResults;
  try{
   const res=await fetchImpl(url,{signal:AbortSignal.timeout(20000),headers:{'user-agent':'continuum-research-radar/1 (owner-approved, weekly)'}});
   if(!res.ok)throw Error('HTTP '+res.status);
   const records=parseArxivAtom(await res.text());
   run.topics.push({id:topic.id,fetched:records.length,...store.upsert(records,topic,now)});
  }catch(e){run.errors.push({topic:topic.id,error:String(e.message).slice(0,200)});}
 }
 store.doc.lastRun=now.toISOString();
 store.doc.runs=[...store.doc.runs,run].slice(-52);
 return run;
}

/** Ranked view: category, best relevance over the record's topics, reasons. */
export function report(store,{topics=DEFAULT_TOPICS,evidence={},now=Date.now(),limit=20}={}){
 const byId=new Map(topics.map(t=>[t.id,t]));
 return Object.values(store.doc.records).map(r=>{
  const c=classify(r,evidence);
  const best=Math.max(...r.topics.map(t=>byId.has(t)?relevance(r,byId.get(t),now):0));
  return {key:r.key,title:r.title,url:r.url,topics:r.topics,relevance:Number(best.toFixed(2)),...c};
 }).sort((a,b)=>b.relevance-a.relevance||a.key.localeCompare(b.key)).slice(0,limit);
}

/**
 * Implementation proposals for the best FEASIBLE/INTERESTING records not yet proposed: a pre-registration skeleton
 * with the lab's six questions and a backlog operation that only PROPOSES (the owner accepts or rejects; the existing
 * Teamleader never picks this kind on its own). Nothing is executed here.
 */
export function proposals(store,{topics=DEFAULT_TOPICS,evidence={},limit=3,now=new Date()}={}){
 const picked=report(store,{topics,evidence,now:now.getTime(),limit:200})
  .filter(r=>['FEASIBLE','INTERESTING'].includes(r.category)&&!store.doc.records[r.key].proposed).slice(0,limit);
 return picked.map(r=>{
  const rec=store.doc.records[r.key];
  const topic=topics.find(t=>rec.topics.includes(t.id));
  const id='radar-'+createHash('sha256').update(r.key).digest('hex').slice(0,12);
  const markdown=['# Vorschlag '+id+': '+rec.title,'',`Quelle: ${rec.url} (Code-Lizenz: ${rec.codeLicence}; nur Idee als Anregung, kein Code übernommen)`,
   `Experiment: ${topic?.experiment??'—'} · vorläufige Einstufung: ${r.category} (${r.reasons.join('; ')})`,'',
   '1. **Mechanismus:** '+rec.summary.split(/(?<=\.)\s/)[0],
   '2. **Bekannte Methode mit ähnlichem Zweck:** die Quelle selbst; Vergleich mit der Lab-Baseline des Experiments.',
   '3. **Was wäre an unserer Variante anders:** vor dem Lauf auszufüllen (eigene Implementierung im LM1-Autodiff).',
   '4. **Widerlegende Messung:** VALIDATION-BPB bzw. Aufgabenerfolg nicht besser als die unveränderte Baseline bei gleichem Budget (Seeds 1–3, vollständige Trennung).',
   '5. **Kosten:** Parameter, ms/Token, Spitzen-RAM auf CPU messen; keine Annahmen aus dem Paper übernehmen.',
   '6. **Abbruch:** NaN/Instabilität, Budgetüberschreitung oder kein Vorteil nach dem vorab festgelegten Budget.'].join('\n');
  return {id,key:r.key,markdown,op:{op:'propose',task:{id,title:('Forschungsvorschlag: '+rec.title).slice(0,200),kind:'RESEARCH_PROPOSAL',
   source:'research-radar',priority:300,subject:{arxiv:rec.arxiv??'',experiment:topic?.experiment??''}},reason:'research radar '+now.toISOString().slice(0,10)}};
 });
}

export function markProposed(store,keys){for(const k of keys)if(store.doc.records[k])store.doc.records[k].proposed=true;}

async function main(argv){
 const args=Object.fromEntries(argv.slice(1).map(a=>a.startsWith('--')?a.slice(2).split('='):[a,true]).map(([k,v])=>[k,v??true]));
 const cmd=argv[0];
 const stateDir=args.state??path.join(process.env.HOME??'.', '.local/state/device-ai-continuum/research-radar');
 const config=args.config&&fs.existsSync(args.config)?JSON.parse(fs.readFileSync(args.config,'utf8')):{enabled:false};
 const store=new RadarStore(path.join(stateDir,'radar.json'));
 const evidence={better:new Map(Object.entries(config.better??{})),reproduced:new Map(Object.entries(config.reproduced??{}))};
 if(cmd==='tick'||cmd==='scan'){
  const d=due(config,store.doc);
  if(cmd==='tick'&&!d.due){console.log(JSON.stringify({ran:false,reason:d.reason}));return;}
  if(cmd==='scan'&&!args.force&&!d.due){console.log(JSON.stringify({ran:false,reason:d.reason+' (use --force for a manual owner run)'}));return;}
  const run=await scan({store,maxResults:Math.min(50,Number(config.maxResults??25))});
  store.save();console.log(JSON.stringify(run));return;
 }
 if(cmd==='report'){for(const r of report(store,{evidence}))console.log(`${r.relevance}\t${r.category}\t${r.key}\t${r.title}`);return;}
 if(cmd==='propose'){
  const ps=proposals(store,{evidence,limit:Number(args.limit??3)});
  fs.mkdirSync(path.join(stateDir,'proposals'),{recursive:true});
  for(const p of ps)fs.writeFileSync(path.join(stateDir,'proposals',p.id+'.md'),p.markdown+'\n');
  if(args.backlog){
   const {BacklogStore}=await import('./development-backlog.mjs');
   new BacklogStore(args.backlog,{actor:'research-radar'}).apply(ps.map(p=>({...p.op,actor:'research-radar',at:Date.now(),ifAbsent:true})),{stopOnError:false});
  }
  markProposed(store,ps.map(p=>p.key));store.save();
  console.log(JSON.stringify(ps.map(p=>({id:p.id,key:p.key}))));return;
 }
 console.log('usage: research-radar.mjs tick|scan [--force]|report|propose [--backlog=<file>] --config=<file> --state=<dir>');
}

if(import.meta.url===`file://${process.argv[1]}`)main(process.argv.slice(2)).catch(e=>{console.error(e.message);process.exit(1);});
