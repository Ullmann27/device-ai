#!/usr/bin/env node
// Pilot study of the typed Reasoner<->Coding-Agent dialog (LM1 milestone). Pre-registered: tasks, conditions
// and metrics below were committed before the single run. Each task is a German owner goal with examples whose
// expected values were taken from the real functions; the reasoner has to find the export among all exports of
// thirteen real, dependency-free Continuum modules copied into a fixture repository, and every claim is decided
// by a real coding job (contract test, isolated worktrees, independent second run).
// Conditions: lexical ranking vs a seeded random ranking, both with the bounded loop (3 rounds); the single-pass
// result of each condition is its first round (same first hypothesis), so no extra jobs are needed.
// Usage: node scripts/reasoner-dialog-pilot.mjs <outDir>
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {runDialog} from '../.github/continuum/reasoner-dialog.mjs';
import {HypothesisReasoner,indexExports,lexicalScorer} from '../.github/continuum/hypothesis-reasoner.mjs';

const ROOT=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
export const MODULES=['development-backlog','kotlin-tokenizer','console-policy','dialogue-retrieval','portable-subword',
 'contract-test-template','local-numeric-patch','baby-core','world-model','cefr-shadow-benchmark','contrastive-gate',
 'repair-experience','repair-diagnostics'];
const M=name=>'.github/continuum/'+name+'.mjs';

/** gold: the intended export; equivalent: exports that behave identically on the examples (reported, not counted wrong). */
export const TASKS=[
 {id:'sha256',gold:M('development-backlog')+'#sha256Hex',equivalent:[],
  goal:{text:'Berechne die SHA-256-Prüfsumme eines Textes als Hex-Zeichenkette: ("abc") → "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad".'}},
 {id:'clip',gold:M('development-backlog')+'#clip',equivalent:[M('development-backlog')+'#singleLine'],
  goal:{text:'Kürze einen Text auf höchstens die angegebene Zeichenzahl: ("abcdefghij", 4) → "abcd" und ("kurz", 10) → "kurz".'}},
 {id:'single-line',gold:M('development-backlog')+'#singleLine',equivalent:[],
  goal:{text:'Mache aus einem Text eine einzige Zeile, Zeilenumbrüche und Tabulatoren werden zu Leerzeichen, die Länge wird begrenzt: ("eins\\nzwei", 20) → "eins zwei" und ("a\\n b\\tc", 10) → "a  b c".'}},
 {id:'repo-path',gold:M('development-backlog')+'#repoPath',equivalent:[],
  goal:{text:'Ergänze bei Quellpfaden, die mit main/, test/ oder androidTest/ beginnen, das Präfix app/src/: ("main/x.kt") → "app/src/main/x.kt" und ("docs/a.md") → "docs/a.md".'}},
 {id:'canonical',gold:M('development-backlog')+'#canonical',equivalent:[],
  goal:{text:'Erzeuge die kanonische JSON-Schreibweise eines Werts: ("x") → "\\"x\\"", (5) → "5" und (true) → "true".'}},
 {id:'finding-id',gold:M('development-backlog')+'#findingTaskId',equivalent:[],
  goal:{text:'Bilde die stabile Kennung einer Befund-Aufgabe aus Art, Datei und Symbol: ("BUG", "a.kt", "f") → "inv-555558a2e60d805d" und ("STYLE", "b.mjs", "g") → "inv-e5e8a11011e1226b".'}},
 {id:'kotlin-keyword',gold:M('kotlin-tokenizer')+'#isKeyword',equivalent:[],
  goal:{text:'Prüfe, ob ein Wort ein reserviertes Kotlin-Schlüsselwort ist: ("fun") → true, ("when") → true und ("banana") → false.'}},
 {id:'owner-account',gold:M('console-policy')+'#isOwnerComment',equivalent:[],
  goal:{text:'Entscheide, ob ein Konto der Besitzer eines Repositorys ist, ohne auf Groß- und Kleinschreibung zu achten: ("acme/werkzeug", "acme") → true, ("acme/werkzeug", "ACME") → true und ("acme/werkzeug", "fremd") → false.'}},
 // adversarial: the only correct outcome is an abstention without any confirmation
 {id:'adv-wrong-example',gold:null,equivalent:[],goal:{text:'Berechne die SHA-256-Prüfsumme eines Textes: ("abc") → "123".'}},
 {id:'adv-no-example',gold:null,equivalent:[],goal:{text:'Finde die Funktion, die Kotlin-Schlüsselwörter erkennt.'}},
 {id:'adv-non-primitive',gold:null,equivalent:[],goal:{text:'Zerlege einen Text in Wörter: ("a b") → ["a","b"].'}}
];

const randomScorer=taskId=>candidates=>(_text,i)=>parseInt(createHash('sha256').update(taskId+'\0'+candidates[i].module+'#'+candidates[i].export).digest('hex').slice(0,8),16);
export const CONDITIONS=[{name:'lexical',scorer:()=>lexicalScorer},{name:'random',scorer:randomScorer}];

function fixture(home){
 const root=path.join(home,'device-ai'),dir=path.join(root,'.github','continuum');
 fs.mkdirSync(dir,{recursive:true});
 for(const m of MODULES)fs.copyFileSync(path.join(ROOT,M(m)),path.join(root,M(m)));
 fs.writeFileSync(path.join(dir,'pilot-baseline.test.mjs'),"import test from 'node:test';\nimport assert from 'node:assert/strict';\n"+
  "import {sha256Hex} from './development-backlog.mjs';\ntest('baseline',()=>assert.equal(sha256Hex('').length,64));\n");
 const git=(...a)=>{const r=spawnSync('git',a,{cwd:root,encoding:'utf8',timeout:20000});if(r.status!==0)throw Error(r.stderr);return r.stdout.trim();};
 git('init','-q');git('add','.');git('-c','user.name=pilot','-c','user.email=pilot@example.invalid','commit','-qm','pilot fixture');
 return root;
}

async function main(){
 const out=path.resolve(process.argv[2]??'reasoner-dialog-pilot');
 fs.mkdirSync(out,{recursive:true});
 const rows=[];
 for(const condition of CONDITIONS)for(const task of TASKS){
  const home=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-pilot-'));
  const root=fixture(home);
  const candidates=indexExports(root);
  const reasoner=new HypothesisReasoner({candidates,scorer:condition.scorer(task.id),budgetMinutes:3});
  const start=Date.now();
  const r=await runDialog({project:root,stateDir:path.join(home,'state'),goal:task.goal,reasoner,maxRounds:3,budgetMinutes:3});
  const hyps=r.state.hypotheses.map(h=>h.body.module+'#'+h.body.export);
  const verdicts=r.state.receipts.map(x=>x.body.verdict);
  const confirmedAt=verdicts.indexOf('CONFIRMED');
  const confirmed=confirmedAt>=0?hyps[confirmedAt]:null;
  const row={condition:condition.name,task:task.id,gold:task.gold,outcome:r.outcome,detail:r.detail,candidates:candidates.length,
   hypotheses:hyps,verdicts,confirmed,rounds:r.state.rounds,ms:Date.now()-start,
   correctAt1:task.gold!==null&&confirmedAt===0&&(confirmed===task.gold||task.equivalent.includes(confirmed)),
   correctAt3:task.gold!==null&&confirmed!==null&&(confirmed===task.gold||task.equivalent.includes(confirmed)),
   unintended:confirmed!==null&&confirmed!==task.gold&&!task.equivalent.includes(confirmed),
   adversarialOk:task.gold===null?r.outcome==='ABSTAINED'&&confirmed===null:null};
  rows.push(row);
  console.log(JSON.stringify(row));
  fs.cpSync(path.join(home,'state','dialog.jsonl'),path.join(out,`${condition.name}-${task.id}.dialog.jsonl`));
  fs.rmSync(home,{recursive:true,force:true});
 }
 fs.writeFileSync(path.join(out,'pilot-results.json'),JSON.stringify(rows,null,1)+'\n');
 for(const c of CONDITIONS){
  const reg=rows.filter(r=>r.condition===c.name&&r.gold!==null),adv=rows.filter(r=>r.condition===c.name&&r.gold===null);
  console.log(`SUMMARY ${c.name}: correct@1 ${reg.filter(r=>r.correctAt1).length}/${reg.length}, correct@3 ${reg.filter(r=>r.correctAt3).length}/${reg.length}, `+
   `unintended ${rows.filter(r=>r.condition===c.name&&r.unintended).length}, adversarial ok ${adv.filter(r=>r.adversarialOk).length}/${adv.length}, `+
   `jobs ${rows.filter(r=>r.condition===c.name).reduce((n,r)=>n+r.rounds,0)}, seconds ${(rows.filter(r=>r.condition===c.name).reduce((n,r)=>n+r.ms,0)/1000).toFixed(0)}`);
 }
}
if(process.argv[1]===fileURLToPath(import.meta.url))await main();
