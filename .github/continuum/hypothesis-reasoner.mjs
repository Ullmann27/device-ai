// Reference reasoner for the typed dialog (reasoner-dialog.mjs). It turns an owner goal with examples into
// ranked hypotheses "export E of module M satisfies these examples", has the coding agent test one at a time
// and revises after a refutation. It reads source files only to build its candidate index and never executes
// code itself; every claim it makes is decided by a worker RECEIPT. The ranking function is pluggable, so a
// learned scorer (LM1) can be compared against this lexical one without changing the protocol.
import fs from 'node:fs';
import path from 'node:path';
import {CAPABILITY,validCase,capabilityCheck} from './reasoner-dialog.mjs';

const STOP=new Set(('der die das den dem des ein eine einen einem einer und oder aus auf für fuer mit von zu zum zur im in ist '+
 'sind wird werden soll sollen muss kann eine einer als wie wenn dann bei nach vor über ueber unter nicht nur auch es sie er '+
 'man wir ich du the a an of to for and or with from by is are be that this it as on at in into funktion function wert werte '+
 'ergibt liefert gibt zurück zurueck beispiel beispiele welche welcher welches finde prüfe pruefe belege zeige').split(/\s+/));

/** Lowercase word stems of a text; camelCase and snake_case names are split into words. */
export function tokens(text){
 const words=String(text).replace(/([a-z0-9])([A-Z])/g,'$1 $2').replace(/[_\-./]/g,' ').toLowerCase()
  .normalize('NFKD').replace(/[\u0300-\u036f]/g,'').split(/[^a-z0-9]+/).filter(w=>w.length>1&&!STOP.has(w));
 return words.map(stem);
}
function stem(w){
 if(w.length<=5)return w;
 for(const suffix of ['ungen','ung','heit','keit','en','er','es','e','s','n'])
  if(w.endsWith(suffix)&&w.length-suffix.length>=4)return w.slice(0,-suffix.length);
 return w;
}

function splitParams(list){
 const out=[];let depth=0,cur='';
 for(const ch of list){
  if('([{'.includes(ch))depth++;else if(')]}'.includes(ch))depth--;
  if(ch===','&&depth===0){out.push(cur.trim());cur='';}else cur+=ch;
 }
 if(cur.trim())out.push(cur.trim());
 return out;
}

/** Parameter-list text starting at an opening parenthesis (balanced), or null. */
function paramsAt(src,open){
 let depth=0;
 for(let i=open;i<src.length;i++){
  if(src[i]==='(')depth++;else if(src[i]===')'&&--depth===0)return src.slice(open+1,i);
 }
 return null;
}

/**
 * Exported functions of `.github/continuum/*.mjs` (tests excluded): name, module, arity bounds and the comment
 * directly above the export as description.
 */
export function indexExports(project){
 const dir=path.join(project,'.github','continuum');
 const out=[];
 for(const file of fs.readdirSync(dir).filter(f=>f.endsWith('.mjs')&&!f.endsWith('.test.mjs')).sort()){
  const src=fs.readFileSync(path.join(dir,file),'utf8');
  const re=/^export\s+(?:async\s+)?function\s+([A-Za-z_]\w*)\s*(\()|^export\s+const\s+([A-Za-z_]\w*)\s*=\s*(?:async\s*)?(\(|([A-Za-z_]\w*)\s*=>)/gm;
  for(const m of src.matchAll(re)){
   const name=m[1]??m[3];
   let params;
   if(m[5])params=[m[5]];
   else{
    const open=m.index+m[0].length-1;
    const text=paramsAt(src,open);
    if(text===null)continue;
    if(m[3]&&!/^\s*=>/.test(src.slice(open+text.length+2)))continue;   // `const x=(…)` that is not an arrow function
    params=splitParams(text);
   }
   const rest=params.some(p=>p.startsWith('...'));
   const required=params.filter(p=>!p.includes('=')&&!p.startsWith('...')).length;
   const before=src.slice(0,m.index).split('\n');
   const comment=[];
   for(let i=before.length-2;i>=0&&i>=before.length-12;i--){
    const line=before[i].trim();
    if(line.startsWith('//')||line.startsWith('*')||line.startsWith('/**')||line.endsWith('*/'))comment.unshift(line.replace(/^\/\*\*?|\*\/$|^\*|^\/\//g,'').trim());
    else break;
   }
   out.push({module:'.github/continuum/'+file,export:name,required,max:rest?Infinity:params.length,description:comment.join(' ').slice(0,400)});
  }
 }
 return out;
}

const VALUE=String.raw`(-?\d+(?:\.\d+)?|"(?:[^"\\]|\\.)*"|'[^']*'|„[^“]*“|true|false|null|\[[^\]]*\])`;
function value(text){
 if(text.startsWith("'"))return text.slice(1,-1);
 if(text.startsWith('„'))return text.slice(1,-1);
 return JSON.parse(text);
}

/** Examples written in the goal text: "(2, 3) → 5", "aus "a" wird "A"", "für 4 ergibt sich 16". */
export function parseExamples(text){
 const out=[];
 const call=new RegExp(String.raw`\(([^()]*)\)\s*(?:=|==|→|->|=>|ergibt|liefert)\s*`+VALUE,'g');
 for(const m of text.matchAll(call)){
  try{out.push({args:splitParams(m[1]).filter(Boolean).map(value),expected:value(m[2])});}catch{}
 }
 const prose=new RegExp(String.raw`(?:aus|für|fuer|bei)\s+`+VALUE+String.raw`\s+(?:wird|werden|ergibt sich|ergibt|liefert sie|kommt)\s+`+VALUE,'gi');
 for(const m of text.matchAll(prose)){
  try{out.push({args:[value(m[1])],expected:value(m[2])});}catch{}
 }
 return out.slice(0,8);
}

/** Lexical relevance of a candidate for a goal: IDF-weighted stem matches, prefix matches count 0.6. */
export function lexicalScorer(candidates){
 const docs=candidates.map(c=>new Set(tokens(c.export+' '+c.description+' '+path.basename(c.module,'.mjs'))));
 const df=new Map();
 for(const d of docs)for(const t of d)df.set(t,(df.get(t)??0)+1);
 const idf=t=>Math.log(1+candidates.length/(df.get(t)??0.5));
 return (goalText,index)=>{
  const doc=docs[index];
  let score=0;
  for(const g of new Set(tokens(goalText))){
   if(doc.has(g)){score+=idf(g);continue;}
   for(const t of doc)if(g.length>=4&&t.length>=4&&(t.startsWith(g)||g.startsWith(t))){score+=.6*idf(t);break;}
  }
  return score/Math.sqrt(1+doc.size);
 };
}

export class HypothesisReasoner{
 /** @param candidates index from indexExports; @param scorer factory (candidates) -> (goalText, index) -> score */
 constructor({candidates,scorer=lexicalScorer,budgetMinutes=3}){
  this.candidates=candidates;this.score=scorer(candidates);this.budgetMinutes=budgetMinutes;
 }

 /** Candidates whose arity fits the examples, best first (stable order on ties). */
 ranked(goalText,examples){
  const n=examples[0]?.args.length??0;
  return this.candidates.map((c,i)=>({c,i,s:this.score(goalText,i)}))
   .filter(({c})=>examples.every(e=>e.args.length===n)&&n>=c.required&&n<=c.max)
   .sort((a,b)=>b.s-a.s||a.i-b.i);
 }

 /** HYPOTHESIS, PLAN and TEST_REQUEST for the best untried candidate; the runner appends them in this order. */
 propose(view,ranked,examples){
  const out=[];
  let cases=examples.filter(validCase).slice(0,4);
  if(!cases.length)return [{type:'ABSTAIN',body:{reason:'Die Beispiele sind keine einfachen Werte (Text, Zahl, Wahrheitswert); dafür gibt es keinen Vertragstest.'}}];
  while(cases.length>1&&JSON.stringify(cases).length>300)cases=cases.slice(0,-1);
  if(JSON.stringify(cases).length>300)return [{type:'ABSTAIN',body:{reason:'Die Beispiele sind für einen Testauftrag zu lang (über 300 Zeichen).'}}];
  const tried=new Set(view.entries.filter(e=>e.type==='HYPOTHESIS').map(e=>e.body.module+'#'+e.body.export));
  // only hypotheses the worker may test at all (its critic protects, e.g., its own control-plane code)
  const next=ranked.find(r=>!tried.has(r.c.module+'#'+r.c.export)&&capabilityCheck(r.c.module,r.c.export,JSON.stringify(cases)).allowed);
  if(!next)return [{type:'ABSTAIN',body:{reason:'Kein weiterer prüfbarer Export passt zu Ziel und Beispielen; ohne geprüfte Quittung wird nichts behauptet.'}}];
  const seq=view.entries.length;   // sequence number the HYPOTHESIS will receive
  const rank=ranked.indexOf(next)+1;
  out.push({type:'HYPOTHESIS',body:{claim:`${next.c.export} aus ${next.c.module} erfüllt die ${cases.length} Beispiel(e) des Ziels.`,
   module:next.c.module,export:next.c.export,basis:`Rang ${rank} von ${ranked.length} passenden Exporten (Score ${next.s.toFixed(3)}).`}});
  out.push({type:'PLAN',body:{capability:CAPABILITY,steps:['Vertragstest aus den Beispielen erzeugen','im Worktree gegen die bestehende Suite prüfen',
   'unabhängig in zweitem Worktree wiederholen','Quittung prüfen und Hypothese bestätigen oder verwerfen']}});
  out.push({type:'TEST_REQUEST',body:{hypothesis:seq,module:next.c.module,export:next.c.export,cases,budgetMinutes:this.budgetMinutes}});
  return out;
 }

 async turn(view){
  const goal=view.goal;
  const examples=goal.examples?.length?goal.examples:parseExamples(goal.text);
  const entries=view.entries;
  const receipts=entries.filter(e=>e.type==='RECEIPT');
  const critiqued=new Set(entries.filter(e=>e.type==='CRITIQUE').map(e=>e.body.receipt));
  const open=receipts.find(r=>!critiqued.has(r.seq));
  if(!examples.length)return [{type:'ABSTAIN',body:{reason:'Das Ziel enthält keine prüfbaren Beispiele; ohne Beispiele gibt es keinen Testauftrag.'}}];
  const ranked=this.ranked(goal.text,examples);
  if(!open)return this.propose(view,ranked,examples);
  const hypothesis=entries.filter(e=>e.type==='HYPOTHESIS').at(-1);
  const verdict=open.body.verdict;
  const critique={type:'CRITIQUE',body:{receipt:open.seq,verdict,reason:verdict==='CONFIRMED'?
   'Die unabhängig wiederholte Prüfung hat bestanden; der Vertragstest liegt zur Prüfung durch den Besitzer bereit.':
   verdict==='REFUTED'?'Der aus den Beispielen erzeugte Vertragstest ist fehlgeschlagen: '+hypothesis.body.export+' verhält sich anders.':
   'Die Quittung entscheidet nichts: '+String(open.body.reasons?.[0]??open.body.status).slice(0,300)}};
  if(verdict==='CONFIRMED')return [critique];
  if(verdict==='INCONCLUSIVE')return [critique,{type:'ABSTAIN',body:{reason:'Infrastrukturproblem statt Befund; kein blinder Wiederholungsversuch.'}}];
  if(view.rounds>=view.maxRounds)return [critique,{type:'ABSTAIN',body:{reason:`Nach ${view.rounds} widerlegten Hypothesen ist das Rundenlimit erreicht.`}}];
  // CRITIQUE and REVISE are appended first, so the new HYPOTHESIS gets the sequence number after them
  const rest=this.propose({...view,entries:[...entries,{type:'CRITIQUE'},{type:'REVISE'}]},ranked,examples);
  if(rest[0].type==='ABSTAIN')return [critique,...rest];
  return [critique,{type:'REVISE',body:{hypothesis:hypothesis.seq,change:'Verworfen; nächster Kandidat: '+rest[0].body.export+' ('+rest[0].body.basis+')'}},...rest];
 }
}
