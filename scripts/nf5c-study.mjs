#!/usr/bin/env node
// NF-5c study (pre-registered in docs/CONTINUUM-NEURAL-FRONTIER-LAB-20261022.md, section 8e).
// Conditions: lexical (baseline), probe (NF-5b), disambiguate (NF-5c). Groups: bank (15 pairs), mined (real functions),
// adversarial (2 new goals) and historical (the 17 NF-5 tasks, comparison only; modules as in NF-5b at 14702b0d).
// Every claim is decided by the real coding job through the CCC1 runtime (contract test, isolated worktrees,
// independent second run); at most 3 rounds. An NF-5c question is answered by a simulated owner with the intended
// function's result on the asked input, and the task runs once more with that additional example.
// Usage: node scripts/nf5c-study.mjs <tasksJson> <outDir> [condition ...]
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {startTask,runTask} from '../.github/continuum/ccc1-orchestrator.mjs';
import {Ccc1Reasoner,indexExports,LexicalLanguageCore} from '../.github/continuum/ccc1-interpreter.mjs';
import {ProbeLanguageCore,DisambiguatingProbeCore,observeOutputs} from '../.github/continuum/behaviour-probe.mjs';
import {DurableTaskJournal,runtimeKey} from '../.github/continuum/ccc1-journal.mjs';
import {TASKS as HISTORICAL,MODULES as HIST_MODULES} from './nf5-ranker-study.mjs';
import {fixtureAt} from './nf5c-task-miner.mjs';
import {bankFixture} from './nf5c-tasks.mjs';

const ROOT=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const HIST_COMMIT='14702b0d';
const MINED_COMMIT='9fb4361c';
export const CONDITIONS=['lexical','probe','disambiguate'];
const lit=v=>typeof v==='string'?JSON.stringify(v):String(v);

function seal(root){
 fs.writeFileSync(path.join(root,'.github','continuum','study-baseline.test.mjs'),
  "import test from 'node:test';\nimport assert from 'node:assert/strict';\ntest('baseline',()=>assert.equal(1+1,2));\n");
 const git=(...a)=>{const r=spawnSync('git',a,{cwd:root,encoding:'utf8',timeout:60000});if(r.status!==0)throw Error(r.stderr);};
 git('init','-q');git('add','.');git('-c','user.name=study','-c','user.email=study@example.invalid','commit','-qm','study fixture');
 return root;
}
const FIXTURES={
 historical:home=>{
  const root=path.join(home,'device-ai'),dir=path.join(root,'.github','continuum');
  fs.mkdirSync(dir,{recursive:true});
  for(const m of HIST_MODULES){
   const r=spawnSync('git',['show',`${HIST_COMMIT}:.github/continuum/${m}.mjs`],{cwd:ROOT,maxBuffer:1<<26});
   if(r.status!==0)throw Error('git show '+m);
   fs.writeFileSync(path.join(dir,m+'.mjs'),r.stdout);
  }
  return root;
 },
 mined:home=>fixtureAt(MINED_COMMIT,home),
 bank:home=>bankFixture(home),
 adversarial:home=>bankFixture(home)
};

function coreFor(condition,root){
 if(condition==='lexical')return new LexicalLanguageCore();
 if(condition==='probe')return new ProbeLanguageCore({root});
 return new DisambiguatingProbeCore({root});
}

async function runOne(task,condition,goal){
 const home=fs.mkdtempSync(path.join(os.tmpdir(),'nf5c-'));
 process.env.HOME=home;                                     // worker leases and states stay inside the run
 const root=seal(FIXTURES[task.group](home));
 const state=path.join(home,'state');
 const taskId=startTask({project:root,stateDir:state,goal,repoFullName:'owner/device-ai',maxRounds:3,deadlineMinutes:20});
 const core=coreFor(condition,root);
 const reasoner=new Ccc1Reasoner({candidates:indexExports(root),languageCore:core,budgetMinutes:3});
 const t0=Date.now();
 const r=await runTask({project:root,stateDir:state,taskId,reasoner,maxTurns:24});
 const ms=Date.now()-t0;
 const entries=new DurableTaskJournal(path.join(state,'tasks',taskId+'.jsonl'),runtimeKey(state)).entries();
 const hyps=entries.filter(e=>e.kind==='HYPOTHESIS').map(e=>e.payload.module+'#'+e.payload.export);
 const receipts=entries.filter(e=>e.kind==='RECEIPT').map(e=>e.payload.verdict);
 const confirmedAt=receipts.indexOf('CONFIRMED');
 const confirmed=confirmedAt>=0?hyps[confirmedAt]:null;
 const reason=entries.find(e=>e.kind==='ABSTAIN')?.payload.reason??null;
 const question=reason?.startsWith('Mehrdeutig')?reason:null;
 const ambiguity=core.lastAmbiguity?{full:core.lastAmbiguity.full,ambiguous:core.lastAmbiguity.ambiguous,inputsTried:core.lastAmbiguity.inputsTried,
  input:core.lastAmbiguity.input??null,groups:core.lastAmbiguity.groups??null}:null;
 let answer=null;
 if(question&&task.gold){
  const g=ambiguity.groups.find(x=>x.candidates.includes(task.gold));
  if(g)answer={from:'question',usable:g.usable,expected:g.expected};
  else{
   const [module,name]=task.gold.split('#');
   const o=observeOutputs({root,candidates:[{module,export:name}],inputs:[ambiguity.input]}).get(task.gold);
   const at=Array.isArray(o)?o[0]:null;
   answer={from:'gold observed',usable:!!at?.primitive&&!Object.is(at.json,-0),expected:at?.json};
  }
 }
 fs.rmSync(home,{recursive:true,force:true});
 const ok=c=>c!==null&&(c===task.gold||(task.equivalent??[]).includes(c));
 return {outcome:r.outcome,hyps,receipts,confirmed,jobs:receipts.length,ms,entries:entries.length,question,ambiguity,answer,
  correctAt1:task.gold!==null&&confirmedAt===0&&ok(confirmed),correct:task.gold!==null&&ok(confirmed),
  unintended:confirmed!==null&&!ok(confirmed),
  questionHasGold:!!question&&!!task.gold&&!!ambiguity?.full?.some(k=>ok(k)),
  adversarialOk:task.gold===null?r.outcome==='ABSTAINED'&&confirmed===null:null};
}

async function main(){
 const tasksFile=process.argv[2];
 const out=path.resolve(process.argv[3]??'nf5c-study');
 const only=process.argv.slice(4);
 fs.mkdirSync(out,{recursive:true});
 const doc=JSON.parse(fs.readFileSync(tasksFile,'utf8'));
 const tasks=[...doc.bank,...doc.mined,...doc.adversarial,
  ...HISTORICAL.map(t=>({id:'hist-'+t.id,group:'historical',gold:t.gold,equivalent:t.equivalent,goal:t.goal}))];
 const rows=[];
 for(const condition of CONDITIONS){
  if(only.length&&!only.includes(condition))continue;
  for(const task of tasks){
   const r=await runOne(task,condition,task.goal);
   const row={condition,task:task.id,group:task.group,kind:task.kind??null,gold:task.gold,...r};
   if(condition==='disambiguate'&&r.question&&r.answer?.usable){
    const extra=` Außerdem (${r.ambiguity.input.map(lit).join(', ')}) → ${lit(r.answer.expected)}.`;
    const after=await runOne(task,condition,{text:task.goal.text+extra});
    row.after={extra,outcome:after.outcome,confirmed:after.confirmed,correct:after.correct,unintended:after.unintended,
     question:after.question,jobs:after.jobs,ms:after.ms};
   }
   rows.push(row);
   console.log(JSON.stringify(row));
  }
 }
 fs.writeFileSync(path.join(out,'nf5c-results.json'),JSON.stringify(rows,null,1));
 summarize(rows,doc);
}

export function summarize(rows,doc){
 const bankAmbiguous=new Set(doc.bank.filter(t=>t.kind==='ambiguous').map(t=>t.id));
 // a question is needed only when a non-equivalent function also meets the examples (pre-registered truth)
 const needs=new Map([...doc.bank,...doc.mined].map(t=>[t.id,t.alsoMeetExamples.some(k=>!t.equivalent.includes(k))]));
 for(const c of CONDITIONS){
  const cr=rows.filter(r=>r.condition===c);
  if(!cr.length)continue;
  for(const g of ['bank','mined','historical','adversarial']){
   const gr=cr.filter(r=>r.group===g);
   if(!gr.length)continue;
   const reg=gr.filter(r=>r.gold!==null),adv=gr.filter(r=>r.gold===null);
   const after=gr.filter(r=>r.after);
   console.log(`SUMMARY ${c} ${g}: tasks ${reg.length} correct@1 ${reg.filter(r=>r.correctAt1).length} correct ${reg.filter(r=>r.correct).length} `+
    `unintended ${gr.filter(r=>r.unintended).length} questions ${gr.filter(r=>r.question).length} (with gold ${gr.filter(r=>r.questionHasGold).length}) `+
    `useful ${reg.filter(r=>r.correct||r.questionHasGold).length} adversarialOk ${adv.filter(r=>r.adversarialOk).length}/${adv.length} `+
    `jobs ${gr.reduce((n,r)=>n+r.jobs,0)} ms ${gr.reduce((n,r)=>n+r.ms,0)}`+
    (after.length?` | after answer: ${after.length} reruns, correct ${after.filter(r=>r.after.correct).length}, unintended ${after.filter(r=>r.after.unintended).length}, asked again ${after.filter(r=>r.after.question).length}`:''));
  }
 }
 const d=rows.filter(r=>r.condition==='disambiguate');
 if(!d.length)return;
 const unintended=d.filter(r=>r.unintended).length+d.filter(r=>r.after?.unintended).length;
 const askedAmbiguous=d.filter(r=>bankAmbiguous.has(r.task)&&r.question).length;
 const unnecessary=d.filter(r=>r.question&&needs.has(r.task)&&!needs.get(r.task)).length;
 const answered=d.filter(r=>r.after&&r.group!=='historical');
 const resolved=answered.filter(r=>r.after.correct).length;
 const hist=d.filter(r=>r.group==='historical'&&r.gold!==null);
 const histUseful=hist.filter(r=>r.correct||r.questionHasGold).length;
 const adv=d.filter(r=>r.gold===null);
 console.log(`VERDICT H5c-1 no unintended confirmation (all groups, incl. after the answer): ${unintended===0?'SUPPORTED':'REFUTED'} (${unintended})`);
 console.log(`VERDICT H5c-2 asks in >= 12 of 13 ambiguous bank pairs: ${askedAmbiguous>=12?'SUPPORTED':'REFUTED'} (${askedAmbiguous}/13)`);
 console.log(`VERDICT H5c-3 at most 1 unnecessary question (bank + mined): ${unnecessary<=1?'SUPPORTED':'REFUTED'} (${unnecessary})`);
 console.log(`VERDICT H5c-4 >= 80 % of answered questions end correct (bank + mined): ${answered.length&&resolved>=.8*answered.length?'SUPPORTED':'REFUTED'} (${resolved}/${answered.length})`);
 console.log(`VERDICT H5c-5 historical comparison: useful ${histUseful}/14 (NF-5b correct@1 12), unintended ${hist.filter(r=>r.unintended).length}`);
 console.log(`CHECK adversarial abstentions ${adv.filter(r=>r.adversarialOk).length}/${adv.length}`);
}

if(process.argv[1]===fileURLToPath(import.meta.url))await main();
