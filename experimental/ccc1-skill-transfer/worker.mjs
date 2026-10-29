/** Reviewed experiment driver for the EXISTING repair engine; no model or new engine. */
import fs from 'node:fs';import path from 'node:path';import {spawnSync} from 'node:child_process';
import {createWorktree,removeWorktree,repairSession,runChain,git} from '../../.github/continuum/repair-loop.mjs';
import {createIsolatedExecutor} from '../../.github/continuum/isolated-executor.mjs';
import {loadLedger,saveLedger,emptyLedger,recordSession} from '../../.github/continuum/repair-ledger.mjs';
import {sha256} from '../../.github/continuum/repair-diagnostics.mjs';
import {applyEdits} from '../../.github/continuum/repair-strategies.mjs';
const tasks=JSON.parse(fs.readFileSync(new URL('./tasks.json',import.meta.url),'utf8'));
const [out,mode,id,ledgerFile]=process.argv.slice(2);
if(!path.isAbsolute(out??'')||!['train-broad','train-scoped','baseline','broad','scoped','misleading','forged-matching'].includes(mode))throw Error('INVALID_RESEARCH_ARGUMENTS');
fs.mkdirSync(out,{recursive:true});
const nodeHome=path.dirname(path.dirname(fs.realpathSync(process.execPath)));
const executor=createIsolatedExecutor({image:'sha256:8da84df25d28176be84164040cd45e128c6b4c6a80b7d7cbe17fe2b9f198efc2',nodeHome,memoryMb:512,pids:32,cpus:1,timeoutMs:30_000});
const scoped=['train-scoped','scoped','misleading','forged-matching'].includes(mode);
function runTask(task,ledger,maxCandidates,label){
 const dir=path.join(out,label);fs.mkdirSync(dir,{recursive:true});const repo=path.join(dir,'repo');fs.mkdirSync(path.join(repo,'.github/continuum'),{recursive:true});
 const rel='.github/continuum/'+task.file;
 fs.writeFileSync(path.join(repo,rel),task.source);
 fs.writeFileSync(path.join(repo,'.github/continuum/helper.mjs'),task.other);
 fs.writeFileSync(path.join(repo,'.github/continuum/check.test.mjs'),
  "import test from 'node:test';import assert from 'node:assert/strict';import {amount} from './"+task.file+"';\n"+
  "test('frozen acceptance',()=>assert.equal(amount(),"+task.expected+"));\n"+
  "test('repeated call',()=>assert.equal(amount(),"+task.expected+"));\n");
 git(repo,['init','-q','-b','research-fixture']);git(repo,['add','.']);git(repo,['-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','frozen public acceptance']);
 const base=git(repo,['rev-parse','HEAD']).stdout.trim(),wt=createWorktree(repo,base);
 const started=performance.now(),cpu=process.cpuUsage();let report,independent=null,independentPatch=null;
 try{
  report=repairSession({wt,base,chain:'node',out:path.join(dir,'primary'),ledger,maxCandidates,
   deadline:Date.now()+120_000,executor,contextualExperience:scoped});
  fs.writeFileSync(path.join(dir,'primary-report.json'),JSON.stringify(report,null,2)+'\n');
  if(report.verified){
   const fresh=createWorktree(repo,base);
   try{
    for(const [file,text] of applyEdits(f=>fs.readFileSync(path.join(fresh,f),'utf8'),report.attempts.find(a=>a.candidate===report.verified.candidate).edits))fs.writeFileSync(path.join(fresh,file),text);
    independentPatch=sha256(git(fresh,['diff','--binary',base]).stdout);
    independent=runChain('node',fresh,path.join(dir,'independent'),{deadline:Date.now()+60_000,executor});
   }finally{removeWorktree(repo,fresh);}
  }
 }finally{removeWorktree(repo,wt);}
 const verified=report.outcome==='VERIFIED'&&independent?.passed===true&&independentPatch===report.verified?.patchSha256;
 const consumed=process.cpuUsage(cpu);
 const result={task:task.id,mode,pid:process.pid,sourceCommit:base,sourceUnchanged:git(repo,['status','--porcelain']).stdout.trim()==='',
  verified,attempts:report.attempts.length,firstStrategy:report.attempts[0]?.strategy??null,strategies:report.attempts.map(a=>a.strategy),
  elapsedMs:performance.now()-started,driverCpuMicros:consumed.user+consumed.system,containerCpuMeasured:false,
  independentPatch,patchSha256:report.verified?.patchSha256??null,inference:report.inference,
  isolation:executor.policy,report,independent};
 fs.writeFileSync(path.join(dir,'result.json'),JSON.stringify(result,null,2)+'\n');
 return result;
}
let ledger=ledgerFile?loadLedger(ledgerFile):emptyLedger();let rows=[];
if(mode.startsWith('train-')){
 const trained=runTask(tasks.training,emptyLedger(),3,'A');rows.push(trained);
 if(!trained.verified)throw Error('TRAINING_NOT_INDEPENDENTLY_VERIFIED');
 ledger=recordSession(emptyLedger(),trained.report,{independentPass:trained.verified});
 saveLedger(path.join(out,'learned.json'),ledger);
 // Warm task never updates training data; fresh processes get only this A ledger.
 rows.push(runTask(tasks.evaluation[0],ledger,1,'warm-B1'));
}else{
 if(mode==='baseline')ledger=emptyLedger();
 if(mode==='misleading')ledger={...ledger,experiences:[...ledger.experiences,
  {kind:'REFERENCE_ERROR|JS_MISSING_IMPORT',successes:9999,failures:0},
  {kind:'REFERENCE_ERROR|JS_TYPO_REFERENCE|node.js.call',successes:0,failures:9999}]};
 if(mode==='forged-matching')ledger={...ledger,experiences:ledger.experiences.map(e=>e.kind.endsWith('|node.js.value')?{...e,successes:e.kind.includes('JS_MISSING_IMPORT')?9999:0,failures:e.kind.includes('JS_MISSING_IMPORT')?0:9999}:e)};
 const task=tasks.evaluation.find(t=>t.id===id);if(!task)throw Error('UNKNOWN_FROZEN_TASK');
 rows.push(runTask(task,ledger,1,id));
}
const summary={pid:process.pid,mode,ledgerBytes:Buffer.byteLength(JSON.stringify(ledger)),ledgerHash:sha256(JSON.stringify(ledger)),rows};
fs.writeFileSync(path.join(out,'summary.json'),JSON.stringify(summary,null,2)+'\n');
console.log(JSON.stringify({pid:process.pid,mode,rows:rows.map(({task,verified,attempts,firstStrategy})=>({task,verified,attempts,firstStrategy}))}));
