#!/usr/bin/env node
/**
 * M8 Worker benchmark for the tool-calling coding agent loop (.github/continuum/coding-agent-loop.mjs).
 * Every task is a small repository with a real bug and real tests; the unchanged repairSession decides
 * (baseline must fail, the candidate must pass the node chain). Arms:
 *   --brain oracle   reference solutions as scripted actions: proves each task is solvable through the pipeline
 *   --brain chat     the owner's OpenAI-compatible brain (CONTINUUM_LOCAL_BRAIN_ENDPOINT/_MODEL/_API_KEY)
 *   --executor <isolation-config.json>   isolated executor for general edits (Docker); without it only edits in the
 *                    reviewed numeric grammar may run on the host and general tasks are BLOCKED_NO_ISOLATION.
 *   --validate       checks every task itself: baseline tests fail and the reference edits (trusted, written with the
 *                    task, not model output) pass, run directly with node --test
 *   --out <file.json> --only <id,...> --steps <n>
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {createAgentCandidateProvider,createScriptedBrain,createChatCompletionsBrain} from '../.github/continuum/coding-agent-loop.mjs';
import {createWorktree,removeWorktree,repairSession} from '../.github/continuum/repair-loop.mjs';
import {loadIsolationConfig,createIsolatedExecutor} from '../.github/continuum/isolated-executor.mjs';

const T="import test from 'node:test';import assert from 'node:assert/strict';\n";
const edit=(file,oldText,newText)=>({tool:'edit_file',args:{path:file,old_text:oldText,new_text:newText}});
const finish={tool:'finish',args:{plan:['Locate the failing behaviour','Apply the smallest fix'],hypothesis:'reference solution'}};
export const TASKS=Object.freeze([
  {id:'num-distance',grammar:'numeric',goal:'distance(a,b) must be non-negative for any argument order',
    files:{'lib/distance.mjs':'export function distance(a,b){return a-b;}\n'},
    test:"import {distance} from '../../lib/distance.mjs';\ntest('symmetric',()=>{assert.equal(distance(8,3),5);assert.equal(distance(3,8),5);});\n",
    oracle:[{tool:'read_file',args:{path:'lib/distance.mjs'}},edit('lib/distance.mjs','return a-b;','return a>b?a-b:b-a;'),finish]},
  {id:'num-clamp',grammar:'numeric',goal:'clamp(x,lo,hi) must return lo below the range and hi above it',
    files:{'lib/clamp.mjs':'export function clamp(x,lo,hi){return x<lo?hi:x>hi?lo:x;}\n'},
    test:"import {clamp} from '../../lib/clamp.mjs';\ntest('clamp',()=>{assert.equal(clamp(-1,0,10),0);assert.equal(clamp(11,0,10),10);assert.equal(clamp(5,0,10),5);});\n",
    oracle:[{tool:'search',args:{pattern:'clamp'}},edit('lib/clamp.mjs','x<lo?hi:x>hi?lo:x','x<lo?lo:x>hi?hi:x'),finish]},
  {id:'num-percent',grammar:'numeric',goal:'percent(part,total) must return the share of part in total in percent',
    files:{'lib/percent.mjs':'export function percent(part,total){return total/part*100;}\n'},
    test:"import {percent} from '../../lib/percent.mjs';\ntest('percent',()=>{assert.equal(percent(1,4),25);assert.equal(percent(3,3),100);});\n",
    oracle:[{tool:'read_file',args:{path:'lib/percent.mjs'}},edit('lib/percent.mjs','total/part*100','part/total*100'),finish]},
  {id:'gen-sum-off-by-one',grammar:'general',goal:'sum(values) must add every element of the array',
    files:{'lib/sum.mjs':'export function sum(values){\n  let total=0;\n  for(let i=0;i<values.length-1;i++)total+=values[i];\n  return total;\n}\n'},
    test:"import {sum} from '../../lib/sum.mjs';\ntest('sum',()=>{assert.equal(sum([1,2,3]),6);assert.equal(sum([]),0);});\n",
    oracle:[{tool:'read_file',args:{path:'lib/sum.mjs'}},edit('lib/sum.mjs','i<values.length-1','i<values.length'),{tool:'check',args:{}},finish]},
  {id:'gen-sort-numeric',grammar:'general',goal:'sortNumbers(list) must sort numbers ascending by value without changing the input',
    files:{'lib/sort.mjs':'export function sortNumbers(list){\n  return list.sort();\n}\n'},
    test:"import {sortNumbers} from '../../lib/sort.mjs';\ntest('numeric order',()=>{const input=[10,9,1];assert.deepEqual(sortNumbers(input),[1,9,10]);assert.deepEqual(input,[10,9,1]);});\n",
    oracle:[{tool:'search',args:{pattern:'sortNumbers'}},{tool:'read_file',args:{path:'lib/sort.mjs'}},
      edit('lib/sort.mjs','return list.sort();','return [...list].sort((a,b)=>a-b);'),{tool:'check',args:{}},finish]},
  {id:'gen-greeting-null',grammar:'general',goal:'greeting(name) must greet "Gast" when the name is missing or blank',
    files:{'lib/greeting.mjs':"export function greeting(name){\n  return 'Hallo '+name.trim()+'!';\n}\n"},
    test:"import {greeting} from '../../lib/greeting.mjs';\ntest('greeting',()=>{assert.equal(greeting(' Anna '),'Hallo Anna!');assert.equal(greeting(null),'Hallo Gast!');assert.equal(greeting('  '),'Hallo Gast!');});\n",
    oracle:[{tool:'read_file',args:{path:'lib/greeting.mjs'}},
      edit('lib/greeting.mjs',"return 'Hallo '+name.trim()+'!';","const clean=typeof name==='string'?name.trim():'';\n  return 'Hallo '+(clean||'Gast')+'!';"),{tool:'check',args:{}},finish]}
]);

function fixture(task){
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-agent-bench-'));
  const root=path.join(dir,'repo');
  const write=(file,text)=>{fs.mkdirSync(path.dirname(path.join(root,file)),{recursive:true});fs.writeFileSync(path.join(root,file),text);};
  for(const [file,text] of Object.entries(task.files))write(file,text);
  write(`.github/continuum/${task.id}.test.mjs`,T+task.test);
  const git=(...args)=>{const p=spawnSync('git',args,{cwd:root,encoding:'utf8',shell:false});if(p.status!==0)throw Error(p.stderr);return p.stdout.trim();};
  git('init','-q');git('add','.');git('-c','user.name=Bench','-c','user.email=bench@example.invalid','commit','-qm','task baseline');
  const head=git('rev-parse','HEAD');
  const spec={schema:'continuum-coding-task',version:1,id:task.id,sourceCommit:head,kind:'BUGFIX',goal:task.goal,
    allowedPaths:Object.keys(task.files),acceptance:['The task test file passes'],chain:'node',limits:{maxModelCalls:3,maxContextChars:12000}};
  return {dir,root,head,spec};
}

export function runBenchmark({brain='oracle',executor=null,only=null,maxSteps=24,env=process.env,log=()=>{}}={}){
  const results=[];
  for(const task of TASKS.filter(t=>!only||only.includes(t.id))){
    if(task.grammar==='general'&&!executor){results.push({id:task.id,grammar:task.grammar,outcome:'BLOCKED_NO_ISOLATION'});log(task.id,'BLOCKED_NO_ISOLATION');continue;}
    const f=fixture(task),wt=createWorktree(f.root,f.head);
    const started=Date.now();const steps=[];
    try{
      const makeBrain=()=>brain==='oracle'?createScriptedBrain(task.oracle):createChatCompletionsBrain({endpoint:env.CONTINUUM_LOCAL_BRAIN_ENDPOINT||undefined,
        model:env.CONTINUUM_LOCAL_BRAIN_MODEL||'local',apiKey:env.CONTINUUM_LOCAL_BRAIN_API_KEY||null});
      let current=makeBrain();
      // A fresh scripted brain per session revision; a model brain keeps no hidden state between revisions anyway.
      const provider=createAgentCandidateProvider({task:f.spec,brain:(...a)=>current(...a),limits:{maxSteps},
        onStep:s=>steps.push(s.tool)});
      const wrapped=Object.assign(input=>{current=makeBrain();return provider(input);},{taskSpec:provider.taskSpec,allowedPaths:provider.allowedPaths});
      const report=repairSession({wt,base:f.head,chain:'node',out:path.join(f.dir,'report'),candidateProvider:wrapped,executor});
      results.push({id:task.id,grammar:task.grammar,outcome:report.outcome,revisions:report.inference?.localRequests??null,
        steps:steps.length,tools:steps,lastReject:report.inference?.lastRejectCode??null,
        attempts:(report.attempts??[]).map(a=>a.outcome),ms:Date.now()-started});
      log(task.id,report.outcome);
    }finally{removeWorktree(f.root,wt);fs.rmSync(f.dir,{recursive:true,force:true});}
  }
  const count=o=>results.filter(r=>r.outcome===o).length;
  return {schema:'continuum-agent-benchmark',version:1,brain,isolated:Boolean(executor),tasks:results.length,
    verified:count('VERIFIED'),blocked:count('BLOCKED_NO_ISOLATION'),
    attempted:results.filter(r=>r.outcome!=='BLOCKED_NO_ISOLATION').length,results};
}

/** Task validity without any model: the baseline must fail and the trusted reference edits must pass. */
export function validateTasks(){
  return TASKS.map(task=>{
    const f=fixture(task);
    try{
      const run=()=>spawnSync(process.execPath,['--test',`.github/continuum/${task.id}.test.mjs`],{cwd:f.root,encoding:'utf8',timeout:60000}).status;
      const baseline=run();
      for(const a of task.oracle.filter(a=>a.tool==='edit_file')){
        const file=path.join(f.root,a.args.path),text=fs.readFileSync(file,'utf8');
        if(text.split(a.args.old_text).length!==2)return {id:task.id,valid:false,reason:'reference old_text not unique'};
        fs.writeFileSync(file,text.replace(a.args.old_text,()=>a.args.new_text));
      }
      const fixed=run();
      return {id:task.id,grammar:task.grammar,baselineFails:baseline!==0,referencePasses:fixed===0,valid:baseline!==0&&fixed===0};
    }finally{fs.rmSync(f.dir,{recursive:true,force:true});}
  });
}

if(process.argv[1]===fileURLToPath(import.meta.url)&&process.argv.includes('--validate')){
  const checks=validateTasks();
  process.stdout.write(JSON.stringify(checks)+'\n');
  process.exit(checks.every(c=>c.valid)?0:1);
}
if(process.argv[1]===fileURLToPath(import.meta.url)&&!process.argv.includes('--validate')){
  const arg=name=>{const i=process.argv.indexOf(name);return i>0?process.argv[i+1]:null;};
  const config=arg('--executor');
  const executor=config?createIsolatedExecutor(loadIsolationConfig(config,{sourceRoot:process.cwd()})):null;
  const summary=runBenchmark({brain:arg('--brain')??'oracle',executor,only:arg('--only')?.split(','),
    maxSteps:Number(arg('--steps')??24),log:(id,o)=>process.stderr.write(`${id}: ${o}\n`)});
  const text=JSON.stringify(summary,null,1);
  if(arg('--out'))fs.writeFileSync(arg('--out'),text+'\n');
  process.stdout.write(`brain=${summary.brain} isolated=${summary.isolated} verified=${summary.verified}/${summary.attempted} attempted, blocked=${summary.blocked}\n`);
}
