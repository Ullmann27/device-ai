#!/usr/bin/env node
// NF-5c task set, frozen before any NF-5c study run (docs/CONTINUUM-NEURAL-FRONTIER-LAB-20261022.md, section 8e).
// - bank: 15 pairs of plausible implementations in scripts/nf5c-bank (13 that differ at edge cases, 2 that behave
//   alike). Gold of a pair: a seeded coin flip. Examples: the first two inputs of the type pool on which both members
//   give the same usable result, with different results. Goal texts are neutral between the two members.
// - mined: the real functions found by scripts/nf5c-task-miner.mjs, with goal texts written for them.
// - adversarial: 2 new goals that must end in an abstention.
// Equivalents (what counts as correct besides the gold): exports with identical results on the type pool, on
// probeInputs(examples) and on 40 seeded random inputs - a wider set than NF-5c itself tries.
// Usage: node scripts/nf5c-tasks.mjs <minedJson> <outFile>
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {indexExports} from '../.github/continuum/ccc1-interpreter.mjs';
import {observeOutputs,probeInputs} from '../.github/continuum/behaviour-probe.mjs';

const HERE=path.dirname(fileURLToPath(import.meta.url));
export const BANK_DIR=path.join(HERE,'nf5c-bank');
export const STRING_POOL=['abc','Hallo Welt','a-b-c','x_y','Ä','','42','der Hund läuft','7'];
export const NUMBER_POOL=[0,1,2,7,42,-3,1.5,1024,2048,4096,.25,.5];
export const BANK=[
 ['text-tools','trimSpaces','trimBlank','ambiguous','string','Entferne den Leerraum am Anfang und Ende eines Textes'],
 ['text-tools','slugAscii','slugLetters','ambiguous','string','Mache aus einem Titel einen Slug für Links: klein geschrieben, Wörter mit Bindestrichen verbunden'],
 ['text-tools','capitalizeFirst','capitalizeWord','ambiguous','string','Schreibe ein Wort mit großem Anfangsbuchstaben'],
 ['text-tools','countWordsSplit','countWordsTokens','ambiguous','string','Zähle die Wörter eines Satzes'],
 ['text-tools','initialsUpper','initialsAsIs','ambiguous','string','Bilde die Initialen eines Namens'],
 ['text-tools','keyLower','keySnake','ambiguous','string','Normalisiere einen Schlüssel für die Suche'],
 ['text-tools','upperText','upperConcat','equivalent','string','Schreibe einen Wert in Großbuchstaben'],
 ['number-tools','roundHalfUp','roundHalfEven','ambiguous','number','Runde eine Zahl auf eine ganze Zahl'],
 ['number-tools','percentRound','percentFloor','ambiguous','number','Rechne einen Anteil in ganze Prozent um'],
 ['number-tools','isPositive','isNotNegative','ambiguous','number','Prüfe, ob eine Zahl positiv ist'],
 ['number-tools','kilobytesRound','kilobytesCeil','ambiguous','number','Rechne eine Byte-Zahl in Kilobyte um'],
 ['number-tools','squareMul','squarePow','equivalent','number','Berechne das Quadrat einer Zahl'],
 ['id-tools','parseWholeStrict','parseWholeLoose','ambiguous','string','Lies eine ganze Zahl aus einem Text'],
 ['id-tools','ticketPad','ticketPadAbs','ambiguous','number','Formatiere eine Ticketnummer vierstellig mit führenden Nullen'],
 ['id-tools','lastCharAt','lastCharSafe','ambiguous','string','Gib das letzte Zeichen eines Textes zurück']
].map(([module,a,b,kind,type,goal])=>({module:'.github/continuum/'+module+'.mjs',a,b,kind,type,goal}));
/** Goal texts for the mined real functions (written after mining, before any study run). */
export const MINED_GOALS={
 '.github/continuum/development-backlog.mjs#integrityOf':'Berechne die Integritätsprüfsumme eines Backlog-Dokuments',
 '.github/continuum/repair-strategies.mjs#insertImport':'Füge einer Quelldatei eine Import-Zeile hinzu',
 '.github/continuum/research-radar.mjs#normalizeTitle':'Normalisiere einen Titel für den Dublettenvergleich',
 '.github/continuum/repair-strategies.mjs#editDistance':'Berechne den Bearbeitungsabstand zwischen zwei Texten'
};
export const ADVERSARIAL=[
 {id:'adv5c-wrong-example',goal:{text:'Wandle einen Text in eine Prüfsumme um: ("abc") → "xyz".'}},
 {id:'adv5c-no-example',goal:{text:'Finde die Funktion, die eine Zahl auf ganze Zahlen rundet.'}}
];

// -0 does not survive the JSON transport from the probe child; its typed rendering does
const usable=o=>o&&!o.skipped&&o.primitive===true&&o.value!=='-0';
const sig=o=>!o||o.skipped?'unobserved':'error' in o?'error:'+o.error:'value:'+o.value;
const lit=v=>typeof v==='string'?JSON.stringify(v):String(v);
export const goalText=(intent,examples)=>`${intent}: ${examples.map(e=>`(${e.args.map(lit).join(', ')}) → ${lit(e.expected)}`).join(' und ')}.`;

export function bankFixture(dir){
 const root=path.join(dir,'device-ai'),target=path.join(root,'.github','continuum');
 fs.mkdirSync(target,{recursive:true});
 for(const f of fs.readdirSync(BANK_DIR).filter(f=>f.endsWith('.mjs')))fs.copyFileSync(path.join(BANK_DIR,f),path.join(target,f));
 return root;
}

function randomInputs(seed,n,type){
 let s=createHash('sha256').update(seed).digest().readUInt32BE(0)||1;
 const next=()=>{s^=s<<13;s>>>=0;s^=s>>>17;s^=s<<5;s>>>=0;return s;};
 const chars='abcxyzABCXYZ0123456789 -_.,:äöüß€/\t';
 return Array.from({length:n},()=>{
  const r=next();
  return type==='number'?[((r%4000)-2000)/(r%3===0?8:r%3===1?1:100)]:[Array.from({length:1+r%12},()=>chars[next()%chars.length]).join('')];
 });
}

/** Equivalents of gold among peers, and peers that also meet the examples. */
function judge(root,peers,goldKey,examples,pool,seed,type){
 const key=c=>c.module+'#'+c.export;
 const wide=[...pool,...probeInputs(examples),...randomInputs(seed,40,type)];
 const obs=observeOutputs({root,candidates:peers,inputs:wide,timeoutMs:20000});
 const gold=obs.get(goldKey);
 if(!Array.isArray(gold))throw Error('gold not observable: '+goldKey);
 const equivalent=peers.filter(o=>key(o)!==goldKey&&JSON.stringify((obs.get(key(o))??[]).map?.(sig))===JSON.stringify(gold.map(sig))).map(key);
 const ex=observeOutputs({root,candidates:peers,inputs:examples.map(e=>e.args),timeoutMs:20000});
 const meets=peers.filter(o=>key(o)!==goldKey&&examples.every((e,j)=>{const r=ex.get(key(o))?.[j];return usable(r)&&JSON.stringify(r.json)===JSON.stringify(e.expected);})).map(key);
 return {equivalent,alsoMeetExamples:meets};
}

async function main(){
 const mined=JSON.parse(fs.readFileSync(process.argv[2],'utf8'));
 const out=path.resolve(process.argv[3]??'nf5c-tasks.json');
 const home=fs.mkdtempSync(path.join(os.tmpdir(),'nf5c-tasks-'));
 const root=bankFixture(home);
 const peers=indexExports(root).filter(c=>c.required<=1&&c.max>=1);
 const bank=[];
 for(const p of BANK){
  const pool=(p.type==='number'?NUMBER_POOL:STRING_POOL).map(v=>[v]);
  const cand=[p.a,p.b].map(e=>({module:p.module,export:e,required:1,max:1,description:''}));
  const obs=observeOutputs({root,candidates:cand,inputs:pool,timeoutMs:20000});
  const [oa,ob]=cand.map(c=>obs.get(c.module+'#'+c.export));
  const agree=pool.map((_,k)=>usable(oa[k])&&usable(ob[k])&&oa[k].value===ob[k].value);
  const first=agree.indexOf(true);
  const second=agree.findIndex((ok,k)=>ok&&k>first&&oa[k].value!==oa[first].value);
  if(first<0||second<0)throw Error('no two agreeing examples for '+p.a+'/'+p.b);
  const examples=[first,second].map(k=>({args:pool[k],expected:oa[k].json}));
  const flip=createHash('sha256').update('nf5c-gold\0'+p.module+'#'+p.a+'|'+p.b).digest()[0]%2;
  const gold=p.module+'#'+(p.kind==='equivalent'||flip===0?p.a:p.b);
  const id='bank-'+(p.kind==='equivalent'?'eq-':'')+p.a.replace(/[A-Z].*$/,'').toLowerCase()+'-'+p.module.split('/').pop().replace('.mjs','');
  bank.push({id,group:'bank',kind:p.kind,pair:[p.a,p.b],gold,examples,goal:{text:goalText(p.goal,examples)},
   ...judge(root,peers,gold,examples,pool,'nf5c-eq\0'+gold,p.type)});
 }
 const minedTasks=mined.tasks.map(t=>({id:'mined-'+t.gold.split('#')[1],group:'mined',kind:'real',gold:t.gold,examples:t.examples,
  goal:{text:goalText(MINED_GOALS[t.gold]??(()=>{throw Error('no goal text for '+t.gold);})(),t.examples)},
  equivalent:t.equivalent,alsoMeetExamples:t.alsoMeetExamples}));
 const doc={frozen:new Date().toISOString(),minedFrom:{commit:mined.commit,eligible:mined.eligible},bank,mined:minedTasks,
  adversarial:ADVERSARIAL.map(a=>({...a,group:'adversarial',gold:null,equivalent:[]}))};
 const text=JSON.stringify(doc,null,1)+'\n';
 fs.writeFileSync(out,text);
 console.log('sha256',createHash('sha256').update(text).digest('hex'));
 for(const t of [...bank,...minedTasks])console.log(t.id.padEnd(30),t.gold.split('/').pop().padEnd(40),'| '+t.goal.text.slice(0,110),'| eq',t.equivalent.length,'meet',t.alsoMeetExamples.length);
 fs.rmSync(home,{recursive:true,force:true});
}
if(process.argv[1]===fileURLToPath(import.meta.url))await main();
