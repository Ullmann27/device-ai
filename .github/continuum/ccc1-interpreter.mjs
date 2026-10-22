// STATUS 22.10.2026: UNFERTIG (WIP) – syntaktisch geprüft, noch ohne Tests; von keinem Produktivpfad importiert.
// CCC1 TaskInterpreter, GoalSpec, LanguageCore and the reference reasoner.
// - TaskInterpreter turns an owner GOAL into a GoalSpec: verifiable acceptance criteria (primitive input/output
//   examples), the only capability (node contract test), open questions and an explicit abstention when nothing is
//   verifiable. It never turns text into a tool operation.
// - LanguageCore is the seam to a language model: rank(goalText, candidates) -> scores. LexicalLanguageCore is the
//   deterministic baseline; ccc1-lm1-core.mjs connects LM1 (PR #75). The orchestrator works without any model.
// - Ccc1Reasoner proposes hypotheses "export E of module M satisfies the acceptance criteria", one TEST_REQUEST at
//   a time, critiques every receipt with exactly its verified verdict and revises after a refutation.
import fs from 'node:fs';
import path from 'node:path';
import {randomUUID} from 'node:crypto';
import {CAPABILITY,validCase} from './ccc1-envelope.mjs';
import {capabilityCheck} from './ccc1-gate.mjs';

const STOP=new Set(('der die das den dem des ein eine einen einem einer und oder aus auf für fuer mit von zu zum zur im in ist '+
 'sind wird werden soll sollen muss kann als wie wenn dann bei nach vor über ueber unter nicht nur auch es sie er man wir ich du '+
 'the a an of to for and or with from by is are be that this it as on at in into funktion function wert werte ergibt liefert gibt '+
 'zurück zurueck beispiel beispiele welche welcher welches finde prüfe pruefe belege zeige').split(/\s+/));

export function tokens(text){
 return String(text).replace(/([a-z0-9])([A-Z])/g,'$1 $2').replace(/[_\-./]/g,' ').toLowerCase().normalize('NFKD')
  .replace(/[̀-ͯ]/g,'').split(/[^a-z0-9]+/).filter(w=>w.length>1&&!STOP.has(w)).map(stem);
}
function stem(w){
 if(w.length<=5)return w;
 for(const s of ['ungen','ung','heit','keit','en','er','es','e','s','n'])if(w.endsWith(s)&&w.length-s.length>=4)return w.slice(0,-s.length);
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
function paramsAt(src,open){
 let depth=0;
 for(let i=open;i<src.length;i++){if(src[i]==='(')depth++;else if(src[i]===')'&&--depth===0)return src.slice(open+1,i);}
 return null;
}

/** Exported functions of `.github/continuum/*.mjs` (tests excluded) with arity bounds and their leading comment. */
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
    if(m[3]&&!/^\s*=>/.test(src.slice(open+text.length+2)))continue;
    params=splitParams(text);
   }
   const before=src.slice(0,m.index).split('\n');const comment=[];
   for(let i=before.length-2;i>=0&&i>=before.length-12;i--){
    const line=before[i].trim();
    if(line.startsWith('//')||line.startsWith('*')||line.startsWith('/**')||line.endsWith('*/'))comment.unshift(line.replace(/^\/\*\*?|\*\/$|^\*|^\/\//g,'').trim());
    else break;
   }
   out.push({module:'.github/continuum/'+file,export:name,required:params.filter(p=>!p.includes('=')&&!p.startsWith('...')).length,
    max:params.some(p=>p.startsWith('...'))?Infinity:params.length,description:comment.join(' ').slice(0,400)});
  }
 }
 return out;
}

const VALUE=String.raw`(-?\d+(?:\.\d+)?|"(?:[^"\\]|\\.)*"|'[^']*'|„[^“]*“|true|false|null|\[[^\]]*\])`;
const value=t=>t.startsWith("'")||t.startsWith('„')?t.slice(1,-1):JSON.parse(t);

/** Examples written in German goal text: "(2, 3) → 5", "aus "a" wird "A"", "für 4 ergibt sich 16". */
export function parseExamples(text){
 const out=[];
 for(const m of text.matchAll(new RegExp(String.raw`\(([^()]*)\)\s*(?:=|==|→|->|=>|ergibt|liefert)\s*`+VALUE,'g')))
  try{out.push({args:splitParams(m[1]).filter(Boolean).map(value),expected:value(m[2])});}catch{}
 for(const m of text.matchAll(new RegExp(String.raw`(?:aus|für|fuer|bei)\s+`+VALUE+String.raw`\s+(?:wird|werden|ergibt sich|ergibt|liefert sie|kommt)\s+`+VALUE,'gi')))
  try{out.push({args:[value(m[1])],expected:value(m[2])});}catch{}
 return out.slice(0,8);
}

/** TaskInterpreter: GOAL payload -> GoalSpec. Abstains instead of guessing when nothing is verifiable. */
export function interpretGoal(goal){
 const found=goal.examples?.length?goal.examples:parseExamples(goal.text);
 const acceptance=found.filter(validCase).slice(0,4);
 const openQuestions=[];
 if(!found.length)openQuestions.push('Welche Eingaben und erwarteten Ergebnisse belegen das Ziel?');
 else if(!acceptance.length)openQuestions.push('Die Beispiele müssen einfache Werte sein (Text, Zahl, Wahrheitswert).');
 const arity=acceptance[0]?.args.length??null;
 if(acceptance.some(c=>c.args.length!==arity))openQuestions.push('Alle Beispiele brauchen dieselbe Anzahl Argumente.');
 return {goal:goal.text,acceptance,capability:CAPABILITY,arity,openQuestions,abstain:openQuestions.length>0,
  reason:openQuestions.join(' ')||null};
}

/** Deterministic baseline LanguageCore: IDF-weighted stem matches between goal and candidate name/comment/module. */
export class LexicalLanguageCore{
 constructor(){this.id='lexical-v1';}
 async rank(goalText,candidates){
  const docs=candidates.map(c=>new Set(tokens(c.export+' '+c.description+' '+path.basename(c.module,'.mjs'))));
  const df=new Map();for(const d of docs)for(const t of d)df.set(t,(df.get(t)??0)+1);
  const idf=t=>Math.log(1+candidates.length/(df.get(t)??0.5));
  const goal=new Set(tokens(goalText));
  return docs.map(doc=>{
   let score=0;
   for(const g of goal){
    if(doc.has(g)){score+=idf(g);continue;}
    for(const t of doc)if(g.length>=4&&t.length>=4&&(t.startsWith(g)||g.startsWith(t))){score+=.6*idf(t);break;}
   }
   return score/Math.sqrt(1+doc.size);
  });
 }
}

const msg=(kind,payload,parentMessageId=null)=>({messageId:randomUUID(),parentMessageId,kind,payload});

export class Ccc1Reasoner{
 constructor({candidates,languageCore=new LexicalLanguageCore(),budgetMinutes=3}){
  this.candidates=candidates;this.core=languageCore;this.budgetMinutes=budgetMinutes;
 }
 async ranked(spec){
  const scores=await this.core.rank(spec.goal,this.candidates);
  const cases=JSON.stringify(spec.acceptance);
  return this.candidates.map((c,i)=>({c,i,s:scores[i]}))
   .filter(({c})=>spec.arity>=c.required&&spec.arity<=c.max&&capabilityCheck(c.module,c.export,cases).allowed)
   .sort((a,b)=>b.s-a.s||a.i-b.i);
 }
 async propose(view,spec,parent){
  const ranked=await this.ranked(spec);
  const tried=new Set(view.entries.filter(e=>e.kind==='HYPOTHESIS').map(e=>e.payload.module+'#'+e.payload.export));
  const next=ranked.find(r=>!tried.has(r.c.module+'#'+r.c.export));
  if(!next)return [msg('ABSTAIN',{reason:'Kein weiterer prüfbarer Export passt zu Ziel und Beispielen; ohne verifizierte Quittung wird nichts behauptet.'})];
  const top=ranked[0].s,second=ranked[1]?.s??0;
  const h=msg('HYPOTHESIS',{claim:`${next.c.export} aus ${next.c.module} erfüllt die ${spec.acceptance.length} Beispiel(e) des Ziels.`,
   module:next.c.module,export:next.c.export,basis:`Rang ${ranked.indexOf(next)+1} von ${ranked.length} prüfbaren Exporten, ${this.core.id}-Score ${next.s.toFixed(3)}.`,
   confidence:Math.max(0,Math.min(1,top>0?(next.s-second*.5)/top:0))},parent);
  return [h,
   msg('PLAN',{capability:CAPABILITY,steps:['Vertragstest aus den Beispielen erzeugen','im Worktree gegen die bestehende Suite prüfen',
    'unabhängig in zweitem Worktree wiederholen','Quittung prüfen und Hypothese bestätigen oder verwerfen']},h.messageId),
   msg('TEST_REQUEST',{hypothesis:h.messageId,module:next.c.module,export:next.c.export,cases:spec.acceptance,budgetMinutes:this.budgetMinutes},h.messageId)];
 }
 async turn(view){
  const spec=interpretGoal(view.goal);
  if(spec.abstain)return [msg('ABSTAIN',{reason:spec.reason})];
  const critiqued=new Set(view.entries.filter(e=>e.kind==='CRITIQUE').map(e=>e.payload.receipt));
  const open=view.entries.find(e=>e.kind==='RECEIPT'&&!critiqued.has(e.messageId));
  if(!open)return this.propose(view,spec,null);
  const request=view.entries.find(e=>e.messageId===open.payload.request);
  const hypothesis=view.entries.find(e=>e.messageId===request.payload.hypothesis);
  const v=open.payload.verdict;
  const critique=msg('CRITIQUE',{receipt:open.messageId,verdict:v,reason:v==='CONFIRMED'?
   'Die unabhängig wiederholte Prüfung hat bestanden; der Vertragstest liegt zur Prüfung durch den Besitzer bereit.':
   v==='REFUTED'?'Der aus den Beispielen erzeugte Vertragstest ist fehlgeschlagen: '+hypothesis.payload.export+' verhält sich anders.':
   'Die Quittung entscheidet nichts: '+String(open.payload.reasons?.[0]??open.payload.status).slice(0,300)},open.messageId);
  if(v==='CONFIRMED')return [critique];
  if(v==='INCONCLUSIVE'&&open.payload.execution==='INTERRUPTED'&&view.rounds<view.maxRounds)
   return [critique,msg('TEST_REQUEST',{...request.payload},hypothesis.messageId)];  // owner allowed the repetition
  if(v==='INCONCLUSIVE')return [critique,msg('ABSTAIN',{reason:'Infrastrukturproblem statt Befund; kein blinder Wiederholungsversuch.'})];
  if(view.rounds>=view.maxRounds)return [critique,msg('ABSTAIN',{reason:`Nach ${view.rounds} widerlegten Hypothesen ist das Rundenlimit erreicht.`})];
  const next=await this.propose(view,spec,null);
  if(next[0].kind==='ABSTAIN')return [critique,...next];
  return [critique,msg('REVISE',{hypothesis:hypothesis.messageId,change:'Verworfen; nächster Kandidat: '+next[0].payload.export},hypothesis.messageId),...next];
 }
}
