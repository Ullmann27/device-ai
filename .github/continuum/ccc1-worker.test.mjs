// CCC1 worker on the existing local loop: owner goals become durable tasks, cycles drive them with the real coding job,
// finished tasks are recorded once in a hash-chained experience log, outcomes map to the loop's vocabulary.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {submitGoal,listTasks,runCcc1Cycle,OUTCOME_FOR,compileGoal,repositoryOf} from './ccc1-worker.mjs';
import {ExperienceLog} from './ccc1-experience.mjs';
import {runTeamleaderLoop} from './local-teamleader-loop.mjs';
import {main as cli} from './ccc1-cli.mjs';

process.env.HOME=fs.mkdtempSync(path.join(os.tmpdir(),'ccc1-worker-home-'));
const nested=Boolean(process.env.CONTINUUM_REPAIR_DEPTH);
const TRIPLE='Finde die Funktion, die eine Zahl verdreifacht: (5) → 15 und (8) → 24.';
const CANON='Erzeuge die kanonische JSON-Schreibweise eines Werts: ("x") → "\\"x\\"", (5) → "5" und (true) → "true".';

function fixture(){
 const home=fs.mkdtempSync(path.join(os.tmpdir(),'ccc1-worker-'));
 const root=path.join(home,'device-ai'),dir=path.join(root,'.github','continuum');
 fs.mkdirSync(dir,{recursive:true});
 fs.writeFileSync(path.join(dir,'math-utils.mjs'),
  '/** Doubles a number. */\nexport function double(n) { return n * 2; }\n/** Triples a number. */\nexport function triple(n) { return n * 3; }\n');
 fs.writeFileSync(path.join(dir,'json-tools.mjs'),
  "/** Canonical JSON; integers only. */\nexport function canonStrict(v){if(typeof v==='number'&&!Number.isInteger(v))throw Error('only integers');return JSON.stringify(v);}\n"+
  "/** Canonical JSON of a value. */\nexport function canonLoose(v){return JSON.stringify(v);}\n");
 fs.writeFileSync(path.join(dir,'math-utils.test.mjs'),"import test from 'node:test';\nimport assert from 'node:assert/strict';\n"+
  "import {triple} from './math-utils.mjs';\ntest('baseline',()=>assert.equal(triple(2),6));\n");
 const git=(...a)=>{const r=spawnSync('git',a,{cwd:root,encoding:'utf8',timeout:15000});assert.equal(r.status,0,r.stderr);return r.stdout.trim();};
 // an isolated working branch: the CLI's CCC1 preflight refuses main/master/production checkouts
 git('init','-q','-b','ccc1-work');git('add','.');git('-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','seed');
 return {home,root,state:path.join(home,'ccc1-state'),git};
}

test('every orchestrator outcome maps to a known loop outcome; only real progress is not a failure',()=>{
 const known=new Set(['NO_FAILURE','NO_CODE_DIAGNOSTIC','VERIFIED_PATCH_PENDING_REVIEW','NO_VERIFIED_REPAIR','DONE_INVESTIGATION',
  'CHECKS_FAILED','NO_READY_INVESTIGATION','IDLE','INTERRUPTED','ERROR']);
 for(const o of ['COMPLETED','ABSTAINED','ABORTED','FAILED','STOPPED_CORRUPT_JOURNAL','WAITING_FOR_WORKER','BLOCKED_WRITER_ACTIVE',
  'TURN_LIMIT_REACHED','WAITING_FOR_OWNER'])assert.ok(known.has(OUTCOME_FOR[o]),o);
 assert.equal(OUTCOME_FOR.COMPLETED,'VERIFIED_PATCH_PENDING_REVIEW');
 assert.equal(OUTCOME_FOR.FAILED,'ERROR');
});

test('a submitted goal is confirmed by the real job, reported for review and recorded once',{skip:nested,timeout:240000},async()=>{
 const f=fixture();
 const taskId=submitGoal({project:f.root,stateDir:f.state,text:TRIPLE});
 assert.deepEqual(listTasks(f.state).map(t=>[t.taskId,t.state]),[[taskId,'DRAFT']]);
 const r=await runCcc1Cycle({project:f.root,stateDir:f.state});
 assert.equal(r.outcome,'VERIFIED_PATCH_PENDING_REVIEW',JSON.stringify(r));
 assert.equal(r.ccc1,'COMPLETED');
 const log=new ExperienceLog(path.join(f.state,'experience.jsonl'));
 const [entry]=log.entries();
 assert.equal(entry.record.outcome,'COMPLETED');
 assert.deepEqual(entry.record.confirmed,{module:'.github/continuum/math-utils.mjs',export:'triple'});
 assert.equal(entry.record.hypotheses.at(-1).verdict,'CONFIRMED');
 assert.match(entry.record.hypotheses.at(-1).receiptSha256,/^[0-9a-f]{64}$/);
 assert.equal(entry.record.baseCommitSha,f.git('rev-parse','HEAD'));
 assert.equal(entry.record.coreId,'probe-disambiguate-v2');
 assert.deepEqual(entry.record.uncheckedAlternatives,[],'every comparable candidate could be run');
 const idle=await runCcc1Cycle({project:f.root,stateDir:f.state});
 assert.equal(idle.outcome,'IDLE');
 assert.equal(log.entries().length,1,'a finished task is recorded once');
 assert.equal(f.git('status','--porcelain'),'','the checkout stays untouched');
});

test('an ambiguous goal becomes an owner question without a job, recorded as an investigation',{skip:nested,timeout:120000},async()=>{
 const f=fixture();
 submitGoal({project:f.root,stateDir:f.state,text:CANON});
 const r=await runCcc1Cycle({project:f.root,stateDir:f.state});
 assert.equal(r.outcome,'DONE_INVESTIGATION');
 assert.equal(r.ccc1,'ABSTAINED');
 const [entry]=new ExperienceLog(path.join(f.state,'experience.jsonl')).entries();
 assert.match(entry.record.question,/^Mehrdeutig/);
 assert.deepEqual(entry.record.hypotheses,[]);
 assert.equal(entry.record.confirmed,null);
});

test('the existing local loop drives CCC1 cycles with its durable checkpoint',{skip:nested,timeout:300000},async()=>{
 const f=fixture();
 submitGoal({project:f.root,stateDir:f.state,text:TRIPLE});
 submitGoal({project:f.root,stateDir:f.state,text:CANON});
 const beats=[];
 const summary=await runTeamleaderLoop({work:({signal})=>runCcc1Cycle({project:f.root,stateDir:f.state,signal}),
  intervalMinutes:1,maxCycles:3,wait:async()=>{},stateFile:path.join(f.state,'loop-checkpoint.json'),sourceRoot:f.root,
  onHeartbeat:h=>beats.push(h.lastOutcome)});
 assert.equal(summary.cycles,3);
 assert.equal(summary.failures,0);
 assert.deepEqual([...new Set(beats.filter(Boolean))],['VERIFIED_PATCH_PENDING_REVIEW','DONE_INVESTIGATION','IDLE']);
 assert.deepEqual(listTasks(f.state).map(t=>t.state).sort(),['ABSTAINED','COMPLETED']);
 assert.equal(new ExperienceLog(path.join(f.state,'experience.jsonl')).entries().length,2);
});

test('damaged journals are reported, never repaired, and stop the worker; the experience log detects edits',()=>{
 const f=fixture();
 const taskId=submitGoal({project:f.root,stateDir:f.state,text:TRIPLE});
 const file=path.join(f.state,'tasks',taskId+'.jsonl');
 fs.writeFileSync(file,fs.readFileSync(file,'utf8').replace('verdreifacht','vervierfacht'));
 assert.equal(listTasks(f.state)[0].state,'CORRUPT');
 return runCcc1Cycle({project:f.root,stateDir:f.state}).then(r=>{
  assert.equal(r.outcome,'ERROR');
  const log=new ExperienceLog(path.join(f.home,'exp.jsonl'));
  assert.equal(log.append({taskId:'a'}),true);
  assert.equal(log.append({taskId:'a'}),false);
  log.append({taskId:'b'});
  fs.writeFileSync(log.file,fs.readFileSync(log.file,'utf8').replace('"a"','"x"'));
  assert.throws(()=>log.entries(),/damaged/);
 });
});

/** The CLI's preflight requires the CCC1 runtime inside the project (as in the Termux checkout); copied and committed. */
function withCcc1Runtime(f){
 for(const name of ['ccc1-cli.mjs','ccc1-worker.mjs','ccc1-path-policy.mjs','local-teamleader-loop.mjs'])
  fs.copyFileSync(new URL('./'+name,import.meta.url),path.join(f.root,'.github','continuum',name));
 f.git('add','.');f.git('-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','ccc1 runtime');
 return f;
}

test('the CLI keeps its state outside the repository and lists tasks',async()=>{
 const f=withCcc1Runtime(fixture());
 const lines=[];
 await assert.rejects(cli(['status','--state',path.join(f.root,'state'),'--project',f.root],l=>lines.push(l)),/CCC1_STATE_INSIDE_SOURCE|outside the repository/);
 await cli(['submit','--project',f.root,'--state',f.state,'--goal',TRIPLE],l=>lines.push(l));
 await cli(['status','--state',f.state],l=>lines.push(l));
 assert.match(lines.at(-1),/^1 task\(s\), 0 experience record\(s\)$/);
 assert.match(lines.at(-2),/DRAFT/);
 await cli(['status','--state',f.state,'--format','json'],l=>lines.push(l));
 const s=JSON.parse(lines.at(-1));
 assert.equal(s.schema,'continuum-ccc1-status');
 assert.equal(s.tasks.length,1);
 assert.deepEqual({...s.tasks[0],taskId:null},{taskId:null,state:'DRAFT',waitingForOwner:false,goal:TRIPLE,question:null,confirmed:null,uncheckedAlternatives:0,review:null});
});

test('an idle cycle refreshes the code index only when asked, and probes again only what changed',async()=>{
 const f=fixture();
 assert.deepEqual(await runCcc1Cycle({project:f.root,stateDir:f.state}),{outcome:'IDLE',detail:'CCC1: no open task'});
 assert.ok(!fs.existsSync(path.join(f.state,'code-index.json')));
 const first=await runCcc1Cycle({project:f.root,stateDir:f.state,indexOnIdle:true});
 assert.equal(first.outcome,'IDLE');
 assert.match(first.detail,/code index 4 exports \(4 runnable\), 2 module\(s\) probed/);
 const index=JSON.parse(fs.readFileSync(path.join(f.state,'code-index.json'),'utf8'));
 assert.equal(index.schema,'continuum-code-index');
 assert.match((await runCcc1Cycle({project:f.root,stateDir:f.state,indexOnIdle:true})).detail,/0 module\(s\) probed/);
 assert.equal(f.git('status','--porcelain'),'','nothing is written into the repository');
});

test('the task compiler answers before any task or coding job: ready, ambiguous, no match, needs the owner',async()=>{
 const f=fixture();
 const ready=await compileGoal({project:f.root,text:TRIPLE});
 assert.equal(ready.status,'READY');
 assert.equal(ready.arity,1);
 assert.deepEqual(ready.matching,['.github/continuum/math-utils.mjs#triple']);
 const ambiguous=await compileGoal({project:f.root,text:CANON});
 assert.equal(ambiguous.status,'AMBIGUOUS');
 assert.match(ambiguous.question,/^Mehrdeutig/);
 assert.equal((await compileGoal({project:f.root,text:'Finde die Funktion: (5) → 17 und (8) → 26.'})).status,'NO_MATCH');
 const vague=await compileGoal({project:f.root,text:'Finde die Funktion, die Zahlen verdreifacht.'});
 assert.equal(vague.status,'NEEDS_OWNER');
 assert.ok(!fs.existsSync(f.state),'no task, no journal');
 assert.equal(f.git('status','--porcelain'),'');
 const lines=[];
 await cli(['compile','--project',f.root,'--goal',TRIPLE],l=>lines.push(l));
 assert.equal(JSON.parse(lines[0]).status,'READY');
});

test('tasks are bound to the real repository, never to a placeholder',()=>{
 const f=fixture();
 assert.equal(repositoryOf(f.root),'local/device-ai','a checkout without origin is named as local');
 const taskId=submitGoal({project:f.root,stateDir:f.state,text:TRIPLE});
 const goal=JSON.parse(fs.readFileSync(path.join(f.state,'tasks',taskId+'.jsonl'),'utf8').split('\n')[0]);
 assert.equal(JSON.stringify(goal).includes('"repoFullName":"local/device-ai"'),true);
 assert.equal(JSON.stringify(goal).includes('owner/device-ai'),false);
 for(const url of ['https://github.com/Ullmann27/device-ai','https://github.com/Ullmann27/device-ai.git','git@github.com:Ullmann27/device-ai.git']){
  spawnSync('git',['remote','remove','origin'],{cwd:f.root});
  f.git('remote','add','origin',url);
  assert.equal(repositoryOf(f.root),'Ullmann27/device-ai',url);
 }
 assert.throws(()=>submitGoal({project:f.root,stateDir:f.state,text:TRIPLE,repoFullName:'owner'}),/owner\/name/);
});

test('submit runs the CCC1 preflight: no task on a production branch, a dirty checkout or without the runtime',async()=>{
 const bare=fixture();
 await assert.rejects(cli(['submit','--project',bare.root,'--state',bare.state,'--repair','node'],()=>{}),/CCC1_REQUIRED_FILE_MISSING/);
 const f=withCcc1Runtime(fixture());
 f.git('checkout','-q','-b','main');
 await assert.rejects(cli(['submit','--project',f.root,'--state',f.state,'--repair','node'],()=>{}),/CCC1_BRANCH_NOT_ISOLATED/);
 f.git('checkout','-q','ccc1-work');
 fs.writeFileSync(path.join(f.root,'stray.txt'),'x');
 await assert.rejects(cli(['submit','--project',f.root,'--state',f.state,'--goal',TRIPLE],()=>{}),/CCC1_WORKTREE_NOT_CLEAN/);
 fs.rmSync(path.join(f.root,'stray.txt'));
 await assert.rejects(cli(['submit','--project',f.root,'--state',f.state,'--goal',TRIPLE,'--repair','node'],()=>{}),/exactly one of/);
 assert.equal(listTasks(f.state).length,0,'no task was created');
 const lines=[];
 await cli(['submit','--project',f.root,'--state',f.state,'--repair','node'],l=>lines.push(l));
 const submitted=JSON.parse(lines[0]);
 assert.equal(submitted.branch,'ccc1-work');
 assert.equal(submitted.sourceCommit,f.git('rev-parse','HEAD'));
 assert.deepEqual(listTasks(f.state).map(t=>[t.taskId,t.state]),[[submitted.taskId,'DRAFT']]);
 const compiled=[];
 await cli(['compile','--project',f.root,'--state',f.state,'--repair','node'],l=>compiled.push(l));
 assert.equal(JSON.parse(compiled[0]).status,'READY');
});
