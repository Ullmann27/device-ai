// NF-5c: candidates that meet every example but behave differently elsewhere lead to an owner question, never to a
// confirmation; the owner's answer as an additional example then confirms the intended function with the real job.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {probeInputs,observeOutputs,findAmbiguity,ambiguityQuestion,DisambiguatingProbeCore} from './behaviour-probe.mjs';
import {Ccc1Reasoner,indexExports,LexicalLanguageCore,interpretGoal} from './ccc1-interpreter.mjs';
import {startTask,runTask} from './ccc1-orchestrator.mjs';

process.env.HOME=fs.mkdtempSync(path.join(os.tmpdir(),'nf5c-home-'));
const nested=Boolean(process.env.CONTINUUM_REPAIR_DEPTH);
const GOAL='Erzeuge die kanonische JSON-Schreibweise eines Werts: ("x") → "\\"x\\"", (5) → "5" und (true) → "true".';
const SOURCE=[
 "/** Canonical JSON of a value; only integers are allowed as numbers. */",
 "export function canonStrict(v){if(typeof v==='number'&&!Number.isInteger(v))throw Object.assign(Error('only integers'),{code:'INVALID_FIELD'});return JSON.stringify(v);}",
 "/** Canonical JSON of a value. */",
 "export function canonLoose(v){return JSON.stringify(v);}",
 "/** Canonical JSON of a value, written differently. */",
 "export function canonSame(v){return JSON.stringify(v===undefined?null:v);}",
 "/** Quotes a text. */",
 "export function quote(s){return '\"'+String(s)+'\"';}",
 "/** Prints a fake result and returns nothing useful. */",
 "export function noisy(v){console.log('{\"noisy\":[{\"value\":\"1\"}]}');return 0;}",
 ''].join('\n');

function fixture(){
 const home=fs.mkdtempSync(path.join(os.tmpdir(),'nf5c-'));
 const root=path.join(home,'device-ai'),dir=path.join(root,'.github','continuum');
 fs.mkdirSync(dir,{recursive:true});
 fs.writeFileSync(path.join(dir,'json-tools.mjs'),SOURCE);
 fs.writeFileSync(path.join(dir,'json-tools.test.mjs'),"import test from 'node:test';\nimport assert from 'node:assert/strict';\n"+
  "import {canonLoose} from './json-tools.mjs';\ntest('baseline',()=>assert.equal(canonLoose(1),'1'));\n");
 const git=(...args)=>{const r=spawnSync('git',args,{cwd:root,encoding:'utf8',timeout:15000});assert.equal(r.status,0,r.stderr);return r.stdout.trim();};
 git('init','-q');git('add','.');git('-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','seed');
 return {home,root,state:path.join(home,'ccc1-state'),git};
}
const C=name=>({module:'.github/continuum/json-tools.mjs',export:name,required:1,max:1,description:''});
const examples=[{args:['x'],expected:'"x"'},{args:[5],expected:'5'},{args:[true],expected:'true'}];

test('further inputs are deterministic, primitive, new and bounded',()=>{
 const a=probeInputs(examples),b=probeInputs(examples);
 assert.deepEqual(a,b);
 assert.ok(a.length>10&&a.length<=48);
 for(const args of a){
  assert.equal(args.length,1);
  assert.ok(args.every(v=>v===null||['string','number','boolean'].includes(typeof v)));
  assert.ok(!examples.some(c=>JSON.stringify(c.args)===JSON.stringify(args)),'example inputs are not repeated');
 }
 assert.ok(a.some(([v])=>v===5.5),'a non-integer variant of the number example');
 assert.deepEqual(probeInputs([]),[]);
});

test('observations are typed and printed text cannot pass as one',()=>{
 const f=fixture();
 const o=observeOutputs({root:f.root,candidates:[C('canonLoose'),C('canonStrict'),C('noisy')],inputs:[[1],['1'],[1.5]]});
 assert.deepEqual(o.get('.github/continuum/json-tools.mjs#canonLoose').map(x=>x.value),['"1"','"\\"1\\""','"1.5"']);
 assert.equal(o.get('.github/continuum/json-tools.mjs#canonStrict')[2].error,'INVALID_FIELD');
 assert.deepEqual(o.get('.github/continuum/json-tools.mjs#noisy').map(x=>x.value),['0','0','0']);
});

test('candidates that differ beyond the examples are ambiguous; identical ones are not',()=>{
 const f=fixture();
 const a=findAmbiguity({root:f.root,candidates:[C('canonStrict'),C('canonLoose')],acceptance:examples});
 assert.equal(a.ambiguous,true);
 assert.equal(a.groups.length,2);
 assert.ok(a.groups.some(g=>g.result==='Fehler INVALID_FIELD'));
 assert.ok(a.groups.some(g=>g.usable),'the owner can answer with a usable expected value');
 const q=ambiguityQuestion(a);
 assert.match(q,/^Mehrdeutig: 2 Funktionen/);
 assert.ok(q.length<=600&&q.includes('canonStrict')&&q.includes('canonLoose'));
 const same=findAmbiguity({root:f.root,candidates:[C('canonLoose'),C('canonSame')],acceptance:examples});
 assert.equal(same.ambiguous,false);
 assert.ok(same.inputsTried>10);
});

test('the reasoner asks instead of proposing when the probe finds an ambiguity; lexical reasoning is unchanged',async()=>{
 const f=fixture();
 const view={goal:{text:GOAL},entries:[],rounds:0,maxRounds:3};
 const core=new DisambiguatingProbeCore({root:f.root});
 const asked=await new Ccc1Reasoner({candidates:indexExports(f.root),languageCore:core}).turn(view);
 assert.deepEqual(asked.map(m=>m.kind),['ABSTAIN']);
 assert.match(asked[0].payload.reason,/^Mehrdeutig/);
 assert.deepEqual(core.lastAmbiguity.full.map(k=>k.split('#')[1]).sort(),['canonLoose','canonSame','canonStrict']);
 const lexical=await new Ccc1Reasoner({candidates:indexExports(f.root),languageCore:new LexicalLanguageCore()}).turn(view);
 assert.equal(lexical[0].kind,'HYPOTHESIS');
 const answered={goal:{text:GOAL+' Außerdem (5.5) → "5.5".'},entries:[],rounds:0,maxRounds:3};
 const proposed=await new Ccc1Reasoner({candidates:indexExports(f.root),languageCore:new DisambiguatingProbeCore({root:f.root})}).turn(answered);
 assert.equal(proposed[0].kind,'HYPOTHESIS');
 assert.ok(['canonLoose','canonSame'].includes(proposed[0].payload.export),'the strict variant is ruled out by the answer');
});

test('end to end with the real coding job: question first, nothing confirmed; the answer confirms the intended function',
 {skip:nested,timeout:240000},async()=>{
 const f=fixture();
 const run=async text=>{
  const taskId=startTask({project:f.root,stateDir:f.state,goal:{text},repoFullName:'owner/device-ai',maxRounds:3,deadlineMinutes:10});
  const reasoner=new Ccc1Reasoner({candidates:indexExports(f.root),languageCore:new DisambiguatingProbeCore({root:f.root}),budgetMinutes:3});
  return runTask({project:f.root,stateDir:f.state,taskId,reasoner,maxTurns:24});
 };
 const first=await run(GOAL);
 assert.equal(first.outcome,'ABSTAINED');
 assert.deepEqual(first.entries.map(e=>e.kind),['GOAL','ABSTAIN']);
 assert.match(first.entries[1].payload.reason,/^Mehrdeutig/);
 const second=await run(GOAL+' Außerdem (5.5) → "5.5".');
 assert.equal(second.outcome,'COMPLETED');
 const receipt=second.entries.filter(e=>e.kind==='RECEIPT').at(-1);
 assert.equal(receipt.payload.verdict,'CONFIRMED');
 const hypothesis=second.entries.filter(e=>e.kind==='HYPOTHESIS').at(-1);
 assert.notEqual(hypothesis.payload.export,'canonStrict');
 assert.equal(f.git('status','--porcelain'),'','the source checkout stays untouched');
});

test('a comparable candidate the probe cannot run travels with the decision instead of disappearing',async()=>{
 const f=fixture();
 fs.writeFileSync(path.join(f.root,'.github','continuum','remote-json.mjs'),
  "import https from 'node:https';\n/** Canonical JSON of a value, checked remotely. */\nexport function canonRemote(v){return JSON.stringify(v);}\n");
 const remote={module:'.github/continuum/remote-json.mjs',export:'canonRemote',required:1,max:1,description:''};
 const candidates=[C('canonLoose'),remote];
 const spec=interpretGoal({text:GOAL});
 const flat=new DisambiguatingProbeCore({root:f.root,base:{id:'flat',rank:async(_g,cs)=>cs.map(()=>1)}});
 await flat.rank(GOAL,candidates);
 assert.equal(await flat.clarify(spec,candidates),null,'one checkable match: no question');
 assert.deepEqual(flat.lastAmbiguity.full,['.github/continuum/json-tools.mjs#canonLoose']);
 assert.deepEqual(flat.lastAmbiguity.unchecked,[{candidate:'.github/continuum/remote-json.mjs#canonRemote',reason:'imports a network or process module'}]);
 const ranked=new DisambiguatingProbeCore({root:f.root,base:{id:'ranked',rank:async(_g,cs)=>cs.map((_c,i)=>1-i)}});
 await ranked.rank(GOAL,candidates);
 await ranked.clarify(spec,candidates);
 assert.deepEqual(ranked.lastAmbiguity.unchecked,[],'a candidate the base ranking puts lower is not reported');
});
