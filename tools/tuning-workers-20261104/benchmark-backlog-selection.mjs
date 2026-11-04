#!/usr/bin/env node
// Offline host comparison of the existing canonical backlog selector only.
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import crypto from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import {performance} from 'node:perf_hooks';
import {fileURLToPath} from 'node:url';
import * as candidate from '../../.github/continuum/development-backlog.mjs';

const base='e101e393dd6c80803ac9d9ac91fccd9812c4a18d';
const sourcePath='.github/continuum/development-backlog.mjs';
const root=fileURLToPath(new URL('../../',import.meta.url));
const baselineSource=execFileSync('git',['show',`${base}:${sourcePath}`],{cwd:root,encoding:'utf8',maxBuffer:1_000_000});
const baseline=await import('data:text/javascript;base64,'+Buffer.from(baselineSource).toString('base64'));
const hash=text=>crypto.createHash('sha256').update(text).digest('hex');
const sizes=[0,1,20,100,500],modes=['mixed-priority','ties','scoped'];
assert.equal(candidate.LIMITS.tasks,500);
const template=baseline.applyOp(baseline.emptyDocument(0,'benchmark'),{
  op:'propose',actor:'benchmark',at:1,
  task:{id:'task-template',title:'Synthetic selector input',kind:'TEST',source:'owner'}
}).doc.tasks[0];

function makeDocument(size,mode,seed=7){
  let state=seed>>>0;
  const random=()=>{state=(Math.imul(state,1664525)+1013904223)>>>0;return state;};
  const tasks=Array.from({length:size},(_,i)=>({
    ...structuredClone(template),id:'task-'+String(i).padStart(3,'0'),
    state:mode==='scoped'&&i%5===0?'PROPOSED':'READY',
    priority:mode==='ties'?500:random()%1001,
    createdAt:mode==='ties'?1:1+random()%7,updatedAt:1000,
    kind:['TEST','REPAIR','INVESTIGATE'][i%3],source:i%3===0?'cli':'owner',
    attempts:mode==='scoped'&&i%11===0?3:0,
    dependsOn:mode==='scoped'&&i>0&&i%13===0?['task-000']:[],
    subject:i%7===0?null:{base:i%4===0?'old':'head',operation:i%2===0?'OTHER':'ASSERT_NODE_EXPORT'}
  }));
  const doc={...baseline.emptyDocument(1000,'benchmark'),tasks};
  baseline.validateDocument(doc);candidate.validateDocument(doc);
  return doc;
}

function deepFreeze(value){
  if(value!==null&&typeof value==='object'){
    for(const child of Object.values(value))deepFreeze(child);
    Object.freeze(value);
  }
  return value;
}

let parityCases=0;
for(const size of sizes)for(const mode of modes)for(const seed of [7,113]){
  const doc=deepFreeze(makeDocument(size,mode,seed));
  const before=candidate.canonical(doc);
  const choices=[
    [null,[],['TEST'],['REPAIR','INVESTIGATE']],
    [null,[],['owner'],['cli']],
    [null,'head','old','missing'],
    [null,'task-000','task-'+String(Math.max(0,size-1)).padStart(3,'0'),'task-missing'],
    [null,'ASSERT_NODE_EXPORT','OTHER','MISSING']
  ];
  for(const kinds of choices[0])for(const sources of choices[1])for(const head of choices[2])
    for(const taskId of choices[3])for(const operation of choices[4]){
      const filters=[kinds,sources,head,taskId,operation];
      assert.strictEqual(candidate.nextEligible(doc,...filters),baseline.nextEligible(doc,...filters),
        JSON.stringify({size,mode,seed,filters}));
      parityCases++;
    }
  assert.equal(candidate.canonical(doc),before);
}

const metadata={base,sourcePath,baselineSourceSha256:hash(baselineSource),
  candidateSourceSha256:hash(fs.readFileSync(new URL('../../'+sourcePath,import.meta.url))),
  node:process.version,v8:process.versions.v8,platform:process.platform,arch:process.arch,
  cpuModel:os.cpus()[0]?.model,cpuCount:os.cpus().length,availableParallelism:os.availableParallelism(),
  parityCases,fixtureSizes:sizes,fixtureModes:modes,device:'host; Android/Fold7 not measured'};

if(process.argv.includes('--parity-only')){
  process.stdout.write(JSON.stringify({...metadata,status:'PARITY_PASS'},null,2)+'\n');
  process.exit(0);
}

const rounds=11,warmupIterations=5000,maxLoad=9;
const filtersFor=mode=>mode==='scoped'?[['TEST'],['owner'],'head',null,'ASSERT_NODE_EXPORT']:[];
const quantile=(values,q)=>[...values].sort((a,b)=>a-b)[Math.floor((values.length-1)*q)];
let sink=0;
function measure(fn,doc,filters,iterations){
  const start=performance.now();
  for(let i=0;i<iterations;i++){
    const selected=fn(doc,...filters);
    sink=(sink+(selected===null?0:selected.priority))|0;
  }
  return (performance.now()-start)*1e6/iterations;
}

function comparisonCounts(size,mode,filters){
  const doc=makeDocument(size,mode);
  let priorityReads=0;
  for(const task of doc.tasks){
    const priority=task.priority;
    Object.defineProperty(task,'priority',{enumerable:true,get(){priorityReads++;return priority;}});
  }
  const expected=baseline.nextEligible(doc,...filters),baselineComparisons=priorityReads/2;
  priorityReads=0;
  assert.strictEqual(candidate.nextEligible(doc,...filters),expected);
  const candidateComparisons=priorityReads/2;
  assert.ok(Number.isInteger(baselineComparisons)&&Number.isInteger(candidateComparisons));
  return {baselineComparisons,candidateComparisons};
}

const loadStart=os.loadavg(),startedAt=new Date().toISOString(),results=[];
for(const size of sizes)for(const mode of modes){
  const loadBefore=os.loadavg();
  assert.ok(loadBefore[0]<=maxLoad,`host load ${loadBefore[0]} exceeds recorded limit ${maxLoad}`);
  const doc=makeDocument(size,mode),filters=filtersFor(mode);
  const iterations=Math.max(2000,Math.floor(250000/Math.max(1,size)));
  for(let i=0;i<warmupIterations;i++){
    sink^=baseline.nextEligible(doc,...filters)?.priority??0;
    sink^=candidate.nextEligible(doc,...filters)?.priority??0;
  }
  const samples=[];
  for(let round=0;round<rounds;round++){
    const order=round%2===0?['baseline','candidate']:['candidate','baseline'];
    const sample={round,order};
    for(const version of order)sample[version]=measure(version==='baseline'?baseline.nextEligible:candidate.nextEligible,doc,filters,iterations);
    samples.push(sample);
  }
  const loadAfter=os.loadavg();
  assert.ok(loadAfter[0]<=maxLoad,`host load ${loadAfter[0]} exceeds recorded limit ${maxLoad}`);
  const baselineNs=samples.map(x=>x.baseline),candidateNs=samples.map(x=>x.candidate);
  results.push({size,mode,iterations,...comparisonCounts(size,mode,filters),
    baselineMedianNs:quantile(baselineNs,.5),candidateMedianNs:quantile(candidateNs,.5),
    pairedMedianSpeedup:quantile(samples.map(x=>x.baseline/x.candidate),.5),
    baselineP25Ns:quantile(baselineNs,.25),baselineP75Ns:quantile(baselineNs,.75),
    candidateP25Ns:quantile(candidateNs,.25),candidateP75Ns:quantile(candidateNs,.75),
    loadBefore,loadAfter,samples});
}
process.stdout.write(JSON.stringify({...metadata,status:'PASS',startedAt,finishedAt:new Date().toISOString(),
  rounds,warmupIterations,maxLoad,loadStart,loadEnd:os.loadavg(),timingUnit:'ns/selection',
  method:'identical validated fixtures; alternating baseline/candidate order; no IO in timed loop',
  limitations:'synthetic selector microbenchmark; excludes validation, persistence and actual worker work; shared host, not a Fold7 result',
  sink,results},null,2)+'\n');
