#!/usr/bin/env node
// CCC1 owner/worker CLI: submit a goal, list tasks, run one cycle or a bounded loop on the EXISTING local loop
// (local-teamleader-loop.mjs). No network, no GitHub token, no shell built from text; state lives outside the
// repository. A confirmed task is a contract-test patch pending the owner's review - nothing is merged or installed.
// Usage:
//   node .github/continuum/ccc1-cli.mjs compile  --project <repo> --goal "<goal with examples>"   (dry run, no state)
//   node .github/continuum/ccc1-cli.mjs submit   --project <repo> --state <dir> --goal "<goal with examples>"
//   node .github/continuum/ccc1-cli.mjs status   --state <dir> [--format json]
//   node .github/continuum/ccc1-cli.mjs run-once --project <repo> --state <dir> [--core disambiguate|probe|lexical] [--index-on-idle true]
//   node .github/continuum/ccc1-cli.mjs loop     --project <repo> --state <dir> --cycles <n> [--interval <minutes>] [--index-on-idle true]
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {submitGoal,listTasks,runCcc1Cycle,compileGoal} from './ccc1-worker.mjs';
import {ExperienceLog} from './ccc1-experience.mjs';
import {runTeamleaderLoop} from './local-teamleader-loop.mjs';
import {ensureCcc1StateOutsideSource} from './ccc1-path-policy.mjs';

export function parseArgs(argv){
 const [command,...rest]=argv;
 const o={command};
 for(let i=0;i<rest.length;i+=2){
  if(!rest[i]?.startsWith('--')||rest[i+1]===undefined)throw Error('expected --name value pairs');
  o[rest[i].slice(2)]=rest[i+1];
 }
 return o;
}

function outside(project,stateDir){
 ensureCcc1StateOutsideSource(project,stateDir);
}

/**
 * One-line machine-readable status for the app (via the Termux bridge): every task with its state and, once finished,
 * what the experience record says - the owner question, the confirmed export (a contract-test patch pending review)
 * and how many comparable candidates could not be checked. Texts are bounded; nothing here is a command.
 */
export function statusJson(tasks,experience){
 const records=new Map(experience.map(e=>[e.record.taskId,e.record]));
 return {schema:'continuum-ccc1-status',version:1,experienceRecords:experience.length,tasks:tasks.map(t=>{
  const r=records.get(t.taskId);
  return {taskId:t.taskId,state:t.state,waitingForOwner:t.waitingForOwner,goal:t.goal.slice(0,200),
   question:(r?.question??r?.abstainReason??null)?.slice(0,600)??null,
   confirmed:r?.confirmed?`${r.confirmed.module}#${r.confirmed.export}`:null,
   uncheckedAlternatives:r?.uncheckedAlternatives?.length??0};
 })};
}

export async function main(argv=process.argv.slice(2),out=console.log){
 const o=parseArgs(argv);
 if(o.command==='compile'){
  if(!o.project||!o.goal)throw Error('compile needs --project and --goal');
  out(JSON.stringify(await compileGoal({project:o.project,text:o.goal})));
  return;
 }
 if(!o.state)throw Error('--state <dir> is required');
 if(o.project)outside(o.project,o.state);
 switch(o.command){
  case 'submit':{
   if(!o.project||!o.goal)throw Error('submit needs --project and --goal');
   out(JSON.stringify({taskId:submitGoal({project:o.project,stateDir:o.state,text:o.goal})}));
   return;
  }
  case 'status':{
   const tasks=listTasks(o.state);
   const experience=new ExperienceLog(path.join(o.state,'experience.jsonl')).entries();
   const unchecked=new Map(experience.map(e=>[e.record.taskId,e.record.uncheckedAlternatives?.length??0]));
   if(o.format==='json'){out(JSON.stringify(statusJson(tasks,experience)));return;}
   for(const t of tasks){
    const u=unchecked.get(t.taskId);
    out(`${t.taskId.slice(0,12)} ${t.state.padEnd(11)} ${t.waitingForOwner?'wartet auf Besitzer ':''}${u?`(${u} nicht prüfbare Alternative(n)) `:''}${t.goal.slice(0,90)}`);
   }
   out(`${tasks.length} task(s), ${experience.length} experience record(s)`);
   return;
  }
  case 'run-once':{
   if(!o.project)throw Error('run-once needs --project');
   out(JSON.stringify(await runCcc1Cycle({project:o.project,stateDir:o.state,core:o.core??'disambiguate',indexOnIdle:o['index-on-idle']==='true'})));
   return;
  }
  case 'loop':{
   if(!o.project||!o.cycles)throw Error('loop needs --project and --cycles');
   const controller=new AbortController();
   for(const sig of ['SIGINT','SIGTERM'])process.once(sig,()=>controller.abort());
   const summary=await runTeamleaderLoop({
    work:({signal})=>runCcc1Cycle({project:o.project,stateDir:o.state,core:o.core??'disambiguate',signal,indexOnIdle:o['index-on-idle']==='true'}),
    intervalMinutes:Number(o.interval??60),maxCycles:Number(o.cycles),signal:controller.signal,
    stateFile:path.join(path.resolve(o.state),'loop-checkpoint.json'),sourceRoot:o.project,
    onHeartbeat:h=>out(JSON.stringify({state:h.state,cycles:h.cycles,lastOutcome:h.lastOutcome,detail:h.detail})),
    log:m=>console.error(m)});
   out(JSON.stringify(summary));
   return;
  }
  default:throw Error('unknown command '+o.command);
 }
}

if(process.argv[1]===fileURLToPath(import.meta.url))main().catch(e=>{console.error(String(e?.message??e));process.exitCode=1;});
