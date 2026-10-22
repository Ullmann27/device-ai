#!/usr/bin/env node
// NF-5 / NF-6a study (pre-registered in docs/CONTINUUM-NEURAL-FRONTIER-LAB-20261022.md, sections 7, 8 and 10.4).
// Tasks: the 8 regular and 3 adversarial goals of dialog pilot 2 plus 6 NEW goals written for this study from real
// Continuum functions (expected values taken from the functions). Every claim is decided by the real coding job
// through the CCC1 runtime (contract test, isolated worktrees, independent second run); 3 rounds per task.
// Conditions:
//   random   seeded hash ranking
//   lexical  LexicalLanguageCore (baseline)
//   docs     AssocLanguageCore learned from the owner's Markdown only (no task was ever seen)
//   gated    docs + learning from the VERIFIED receipts of all other tasks (leave-one-out; receipts of the docs run)
//   ungated  docs + learning from the first proposal of all other tasks as if it were true (no verification)
// Usage: node scripts/nf5-ranker-study.mjs <outDir> [condition ...]
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {startTask,runTask} from '../.github/continuum/ccc1-orchestrator.mjs';
import {Ccc1Reasoner,indexExports,LexicalLanguageCore} from '../.github/continuum/ccc1-interpreter.mjs';
import {AssocLanguageCore} from '../.github/continuum/assoc-language-core.mjs';
import {DurableTaskJournal,runtimeKey} from '../.github/continuum/ccc1-journal.mjs';
import {MODULES as PILOT_MODULES,TASKS as PILOT_TASKS} from './reasoner-dialog-pilot.mjs';

const ROOT=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const M=name=>'.github/continuum/'+name+'.mjs';
export const MODULES=[...PILOT_MODULES,'capability-research','cre-encoder','relational-memory-data'];
const SHA=[M('development-backlog')+'#sha256Hex',M('portable-subword')+'#sha256',M('cre-encoder')+'#sha256'];

/** New goals, written before any run of this study; expected values computed with the functions themselves. */
export const NEW_TASKS=[
 {id:'context-cost',gold:M('capability-research')+'#contextCost',equivalent:[],
  goal:{text:'Schätze, wie viele Kontext-Token ein Text kostet: ein Token je angefangene vier Bytes, mindestens eines: ("abc") → 1 und ("Hallo Welt") → 3.'}},
 {id:'json-digest',gold:M('capability-research')+'#digest',equivalent:[],
  goal:{text:'Berechne die Prüfsumme der kanonischen JSON-Darstellung eines Werts: ("abc") → "6cc43f858fbb763301637b5af970e2a46b46f461f27e5a0f41e009c59b827b25".'}},
 {id:'feature-bucket',gold:M('cre-encoder')+'#bucket',equivalent:[],
  goal:{text:'Weise einem Merkmalswort sein festes Streuwert-Fach zu: ("abc") → 10507 und ("fun") → 14430.'}},
 {id:'word-evidence',gold:M('dialogue-retrieval')+'#wordEvidence',equivalent:[],
  goal:{text:'Wie stark belegt ein Text die Wörter einer Frage? Ein gleiches Wort ergibt 1: ("Hallo Welt", "Welt") → 1 und ("Haus", "Baum") → 0.'}},
 {id:'memory-relation',gold:M('cre-encoder')+'#relationOf',equivalent:[],
  goal:{text:'Lies aus der Kennung einer Erinnerung die Beziehung, also den Teil nach dem letzten Bindestrich: ("alpha-berlin-capital") → "capital" und ("x-new-york-city") → "city".'}},
 {id:'memory-subject',gold:M('cre-encoder')+'#subjectOf',equivalent:[],
  goal:{text:'Lies aus der Kennung einer Erinnerung den Gegenstand zwischen erstem und letztem Bindestrich: ("alpha-berlin-capital") → "berlin" und ("x-new-york-city") → "new-york".'}}
];
/** Pilot tasks; identical SHA-256 implementations elsewhere count as correct for the checksum task. */
export const TASKS=[...PILOT_TASKS.map(t=>t.id==='sha256'?{...t,equivalent:SHA.filter(s=>s!==t.gold)}:t),...NEW_TASKS];
export const CONDITIONS=['random','lexical','docs','gated','ungated'];

function fixture(home){
 const root=path.join(home,'device-ai'),dir=path.join(root,'.github','continuum');
 fs.mkdirSync(dir,{recursive:true});
 for(const m of MODULES)fs.copyFileSync(path.join(ROOT,M(m)),path.join(root,M(m)));
 fs.writeFileSync(path.join(dir,'study-baseline.test.mjs'),"import test from 'node:test';\nimport assert from 'node:assert/strict';\n"+
  "import {sha256Hex} from './development-backlog.mjs';\ntest('baseline',()=>assert.equal(sha256Hex('').length,64));\n");
 const git=(...a)=>{const r=spawnSync('git',a,{cwd:root,encoding:'utf8',timeout:20000});if(r.status!==0)throw Error(r.stderr);return r.stdout.trim();};
 git('init','-q');git('add','.');git('-c','user.name=study','-c','user.email=study@example.invalid','commit','-qm','study fixture');
 return root;
}

export function docsTexts(){
 const out=[];
 for(const f of fs.readdirSync(path.join(ROOT,'docs')).filter(f=>f.endsWith('.md')).sort())out.push(fs.readFileSync(path.join(ROOT,'docs',f),'utf8'));
 for(const f of ['README.md','CODEX_START.md'])if(fs.existsSync(path.join(ROOT,f)))out.push(fs.readFileSync(path.join(ROOT,f),'utf8'));
 return out;
}

const hashCore=taskId=>({id:'random',async rank(_g,c){return c.map(x=>parseInt(createHash('sha256').update(taskId+'\0'+x.module+'#'+x.export).digest('hex').slice(0,8),16));}});

async function runOne(task,core){
 const home=fs.mkdtempSync(path.join(os.tmpdir(),'nf5-'));
 process.env.HOME=home;                                     // worker leases and states stay inside the run
 const root=fixture(home);
 const state=path.join(home,'state');
 const taskId=startTask({project:root,stateDir:state,goal:task.goal,repoFullName:'owner/device-ai',maxRounds:3,deadlineMinutes:20});
 const reasoner=new Ccc1Reasoner({candidates:indexExports(root),languageCore:core,budgetMinutes:3});
 const t0=Date.now();
 const r=await runTask({project:root,stateDir:state,taskId,reasoner,maxTurns:24});
 const entries=new DurableTaskJournal(path.join(state,'tasks',taskId+'.jsonl'),runtimeKey(state)).entries();
 const hyps=entries.filter(e=>e.kind==='HYPOTHESIS').map(e=>({module:e.payload.module,export:e.payload.export}));
 const receipts=entries.filter(e=>e.kind==='RECEIPT').map(e=>e.payload.verdict);
 const confirmedAt=receipts.indexOf('CONFIRMED');
 const confirmed=confirmedAt>=0?hyps[confirmedAt].module+'#'+hyps[confirmedAt].export:null;
 const ok=c=>c!==null&&(c===task.gold||task.equivalent.includes(c));
 fs.rmSync(home,{recursive:true,force:true});
 return {outcome:r.outcome,hyps,receipts,confirmed,jobs:receipts.length,ms:Date.now()-t0,entries:entries.length,
  correctAt1:task.gold!==null&&confirmedAt===0&&ok(confirmed),correctAt3:task.gold!==null&&ok(confirmed),
  unintended:confirmed!==null&&!ok(confirmed),adversarialOk:task.gold===null?r.outcome==='ABSTAINED'&&confirmed===null:null};
}

async function main(){
 const out=path.resolve(process.argv[2]??'nf5-study');
 const only=process.argv.slice(3);
 fs.mkdirSync(out,{recursive:true});
 const docs=docsTexts();
 const fresh=()=>{const c=new AssocLanguageCore();const s=c.learnFromDocs(docs);return [c,s];};
 const [, docStats]=fresh();
 console.log(JSON.stringify({docs:docs.length,...docStats,tasks:TASKS.length}));
 const rows=[];
 const docsRun=new Map();                                   // task id -> {hyps, receipts}
 for(const condition of CONDITIONS){
  if(only.length&&!only.includes(condition))continue;
  for(const task of TASKS){
   let core;
   if(condition==='random')core=hashCore(task.id);
   else if(condition==='lexical')core=new LexicalLanguageCore();
   else{
    [core]=fresh();
    if(condition!=='docs')for(const [id,run] of docsRun){
     if(id===task.id)continue;                              // leave-one-out: never the task's own outcome
     const other=TASKS.find(t=>t.id===id);
     if(condition==='gated')run.hyps.forEach((h,k)=>core.learnFromReceipt({goalText:other.goal.text,candidate:h,verdict:run.receipts[k]}));
     else if(run.hyps[0])core.learnFromProposal({goalText:other.goal.text,candidate:run.hyps[0]});
    }
   }
   const r=await runOne(task,core);
   if(condition==='docs')docsRun.set(task.id,{hyps:r.hyps,receipts:r.receipts});
   const row={condition,task:task.id,gold:task.gold,...r};
   rows.push(row);
   console.log(JSON.stringify(row));
  }
 }
 fs.writeFileSync(path.join(out,'nf5-results.json'),JSON.stringify(rows,null,1)+'\n');
 for(const c of CONDITIONS){
  const reg=rows.filter(r=>r.condition===c&&r.gold!==null),adv=rows.filter(r=>r.condition===c&&r.gold===null);
  if(!reg.length)continue;
  const newIds=new Set(NEW_TASKS.map(t=>t.id));
  console.log(`SUMMARY ${c}: correct@1 ${reg.filter(r=>r.correctAt1).length}/${reg.length} (new tasks ${reg.filter(r=>newIds.has(r.task)&&r.correctAt1).length}/${newIds.size}), `+
   `correct@3 ${reg.filter(r=>r.correctAt3).length}/${reg.length}, unintended ${rows.filter(r=>r.condition===c&&r.unintended).length}, `+
   `adversarial ok ${adv.filter(r=>r.adversarialOk).length}/${adv.length}, jobs ${rows.filter(r=>r.condition===c).reduce((n,r)=>n+r.jobs,0)}`);
 }
}
if(process.argv[1]===fileURLToPath(import.meta.url))await main();
