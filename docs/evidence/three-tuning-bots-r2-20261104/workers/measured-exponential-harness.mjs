#!/usr/bin/env node
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import crypto from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import {performance} from 'node:perf_hooks';
import {fileURLToPath} from 'node:url';
import * as candidate from '../../.github/continuum/development-backlog.mjs';
import {makeDependencyDocument,setTaskState,deepFreeze,completeTaskOperations,sizes,modes} from './fixtures.mjs';
import {positionCases} from './position-cases.mjs';

const logicalBase='db3295d51c769afdaf809ac1dfae2ada84a3274f';
const publicBase='04b46f125d1086e6ea540d3c68700407f340f607';
const expectedTree='d753188fe0e67f6c6f098db6b7073ea199e05530';
const expectedSource='d0cb68413ff89063b685395c0e633a10c04f038e8329658c085ce0c8748f3a1d';
const sourcePath='.github/continuum/development-backlog.mjs';
const root=fileURLToPath(new URL('../../',import.meta.url));
const git=(...args)=>execFileSync('git',args,{cwd:root,encoding:'utf8',stdio:['ignore','pipe','pipe'],maxBuffer:1_000_000});
const hash=value=>crypto.createHash('sha256').update(value).digest('hex');
let resolvedCommit;
for(const commit of [publicBase,logicalBase]){
  try{git('cat-file','-e',`${commit}^{commit}`);resolvedCommit=commit;break;}catch{}
}
assert.ok(resolvedCommit,'Fetch the public baseline commit before running this comparison');
assert.equal(git('rev-parse',`${resolvedCommit}^{tree}`).trim(),expectedTree,'baseline tree');
const baselineSource=git('show',`${resolvedCommit}:${sourcePath}`);
assert.equal(hash(baselineSource),expectedSource,'baseline source');
const baseline=await import('data:text/javascript;base64,'+Buffer.from(baselineSource).toString('base64'));
const modeFilters=mode=>mode==='none-no-match'?[['UNKNOWN']]:[];

let parityCases=0,mutationCases=0,operationCases=0,positionBoundaryCases=0;
for(const size of sizes)for(const mode of modes)for(const seed of [7,113]){
  const doc=deepFreeze(makeDependencyDocument(size,mode,seed));
  baseline.validateDocument(doc);
  const before=candidate.canonical(doc);
  const filters=[[],[[]],[['TEST']],[null,['owner']],[null,null,'head'],
    [null,null,null,'task-000'],[null,null,null,'task-'+String(Math.max(0,size-1)).padStart(3,'0')],
    [null,null,null,'task-missing'],[null,null,null,null,'OTHER'],
    [['TEST'],['owner'],'head',null,'ASSERT_NODE_EXPORT'],[null,[]],modeFilters(mode)];
  for(const filter of filters){
    assert.strictEqual(candidate.nextEligible(doc,...filter),baseline.nextEligible(doc,...filter),
      JSON.stringify({size,mode,seed,filter}));
    parityCases++;
  }
  assert.equal(candidate.canonical(doc),before);
}
for(const size of [20,100,500]){
  const doc=makeDependencyDocument(size,'one-last');
  const last=doc.tasks.length-1;
  for(const state of ['PROPOSED','READY','IN_PROGRESS','VERIFYING','DONE','PROPOSED','DONE']){
    setTaskState(doc.tasks[last],state);
    baseline.validateDocument(doc);candidate.validateDocument(doc);
    assert.strictEqual(candidate.nextEligible(doc),baseline.nextEligible(doc));mutationCases++;
  }
  const replacement=setTaskState(structuredClone(doc.tasks[last]),'PROPOSED');
  doc.tasks[last]=replacement;candidate.validateDocument(doc);
  assert.strictEqual(candidate.nextEligible(doc),baseline.nextEligible(doc));mutationCases++;
  for(const task of doc.tasks)if(task.state==='READY')task.attempts=task.maxAttempts;
  setTaskState(replacement,'DONE');candidate.validateDocument(doc);
  assert.strictEqual(candidate.nextEligible(doc),baseline.nextEligible(doc));mutationCases++;
}
const propose=(id,extra={})=>({op:'propose',actor:'test',at:1,
  task:{id,title:id,kind:'TEST',source:'owner',...extra}});
const operations=[propose('task-zulu'),propose('task-alpha',{priority:1000,dependsOn:['task-zulu']}),
  propose('task-middle'),{...propose('task-zulu'),ifAbsent:true},
  {op:'accept',actor:'test',at:2,id:'task-zulu'},{op:'accept',actor:'test',at:2,id:'task-alpha'},
  {op:'claim',actor:'test',at:3,id:'task-alpha',owner:'test',token:'d'.repeat(32),leaseMs:60_000},
  ...completeTaskOperations('task-zulu'),
  {op:'claimNext',actor:'test',at:20,owner:'test',token:'d'.repeat(32),leaseMs:60_000,kinds:['TEST']},
  {op:'renew',actor:'test',at:21,id:'task-alpha',token:'e'.repeat(32),leaseMs:60_000},
  {op:'complete',actor:'test',at:22,id:'task-alpha',evidence:{}},
  {op:'abort',actor:'test',at:23,id:'task-alpha',reason:'synthetic stop'},
  {op:'claimNext',actor:'test',at:24,owner:'test',token:'f'.repeat(32),leaseMs:60_000}];
assert.deepEqual(candidate.applyOps(candidate.emptyDocument(0,'test'),operations,{stopOnError:false}),
  baseline.applyOps(baseline.emptyDocument(0,'test'),operations,{stopOnError:false}));
operationCases+=operations.length;
const legacy={schemaVersion:1,tasks:[
  {kind:'UNREFERENCED',symbol:'Zulu',file:'main/Z.kt'},
  {kind:'DEAD_CLUSTER',symbol:'Alpha',file:'main/A.kt'}
]};
const oldMigrated=baseline.migrateV1(legacy,{at:1,actor:'test'}),newMigrated=candidate.migrateV1(legacy,{at:1,actor:'test'});
assert.deepEqual(newMigrated,oldMigrated);operationCases++;
for(const invalid of ['cycle','missing','unsorted']){
  const doc=makeDependencyDocument(20,'none');
  if(invalid==='cycle'){doc.tasks[0].dependsOn=[doc.tasks[1].id];doc.tasks[1].dependsOn=[doc.tasks[0].id];}
  else if(invalid==='missing')doc.tasks[0].dependsOn=['task-missing'];
  else doc.tasks.reverse();
  const failure=api=>{try{api.validateDocument(doc);return null;}catch(e){return {name:e.name,code:e.code,message:e.message};}};
  assert.deepEqual(failure(candidate),failure(baseline));assert.notEqual(failure(candidate),null);operationCases++;
}

for(const {size,index,doc,selected} of positionCases()){
  baseline.validateDocument(doc);
  const before=candidate.canonical(doc);
  assert.strictEqual(candidate.nextEligible(doc),baseline.nextEligible(doc),`${size}/${index}/parity`);
  assert.strictEqual(candidate.nextEligible(doc),selected,`${size}/${index}/expected`);
  assert.equal(candidate.canonical(doc),before);
  positionBoundaryCases++;
}

const metadata={logicalBase,publicBase,resolvedCommit,baselineTree:expectedTree,sourcePath,
  baselineSourceSha256:hash(baselineSource),candidateSourceSha256:hash(fs.readFileSync(new URL('../../'+sourcePath,import.meta.url))),
  harnessSha256:hash(fs.readFileSync(fileURLToPath(import.meta.url))),fixturesSha256:hash(fs.readFileSync(new URL('./fixtures.mjs',import.meta.url))),
  positionCasesSha256:hash(fs.readFileSync(new URL('./position-cases.mjs',import.meta.url))),
  node:process.version,v8:process.versions.v8,platform:process.platform,arch:process.arch,
  cpuModel:os.cpus()[0]?.model,cpuCount:os.cpus().length,availableParallelism:os.availableParallelism(),
  parityCases,mutationCases,operationCases,positionBoundaryCases,fixtureSizes:sizes,fixtureModes:modes,device:'host; Android/Fold7 not measured'};
if(process.argv.includes('--parity-only')){
  process.stdout.write(JSON.stringify({...metadata,status:'PARITY_PASS'},null,2)+'\n');process.exit(0);
}

function workCounts(doc,filters){
  doc=structuredClone(doc);
  let ids=0,states=[];
  for(const task of doc.tasks){
    const id=task.id,state=task.state;
    Object.defineProperty(task,'id',{enumerable:true,get(){ids++;return id;}});
    Object.defineProperty(task,'state',{enumerable:true,get(){states.push(id);return state;}});
  }
  const oldResult=baseline.nextEligible(doc,...filters),oldIds=ids,oldStates=states;
  ids=0;states=[];
  assert.strictEqual(candidate.nextEligible(doc,...filters),oldResult);
  assert.deepEqual(states,oldStates,'dependency/current-task state read sequence');
  return {baselineTaskIdReads:oldIds,candidateTaskIdReads:ids,stateReads:states.length,stateReadSequenceEqual:true};
}

function batchSize(doc){
  const byId=new Map(doc.tasks.map((task,i)=>[task.id,{task,index:i}]));
  let cost=doc.tasks.length;
  for(const task of doc.tasks)if(task.state==='READY')for(const id of task.dependsOn){
    const dependency=byId.get(id);cost+=dependency.index+1;
    if(dependency.task.state!=='DONE')break;
  }
  return Math.max(8,Math.min(20_000,Math.floor(200_000/Math.max(1,cost))));
}
const rounds=9,maxLoad=9,results=[],loadStart=os.loadavg(),startedAt=new Date().toISOString();
const quantile=(values,q)=>[...values].sort((a,b)=>a-b)[Math.floor((values.length-1)*q)];
let sink=0;
function measure(fn,doc,filters,iterations){
  const start=performance.now();
  for(let i=0;i<iterations;i++)sink=(sink+(fn(doc,...filters)?.priority??0))|0;
  return (performance.now()-start)*1e6/iterations;
}
for(const size of sizes)for(const mode of modes){
  const loadBefore=os.loadavg();assert.ok(loadBefore[0]<=maxLoad,`load ${loadBefore[0]} exceeds ${maxLoad}`);
  const doc=makeDependencyDocument(size,mode),filters=modeFilters(mode),iterations=batchSize(doc);
  const warmupIterations=Math.min(2000,iterations);
  for(let i=0;i<warmupIterations;i++){
    sink^=baseline.nextEligible(doc,...filters)?.priority??0;sink^=candidate.nextEligible(doc,...filters)?.priority??0;
  }
  const samples=[];
  for(let round=0;round<rounds;round++){
    const order=round%2===0?['baseline','candidate']:['candidate','baseline'],sample={round,order};
    for(const version of order)sample[version]=measure(version==='baseline'?baseline.nextEligible:candidate.nextEligible,doc,filters,iterations);
    samples.push(sample);
  }
  const loadAfter=os.loadavg();assert.ok(loadAfter[0]<=maxLoad,`load ${loadAfter[0]} exceeds ${maxLoad}`);
  const old=samples.map(s=>s.baseline),next=samples.map(s=>s.candidate);
  results.push({size,mode,iterations,warmupIterations,baselineMedianNs:quantile(old,.5),candidateMedianNs:quantile(next,.5),
    pairedMedianSpeedup:quantile(samples.map(s=>s.baseline/s.candidate),.5),
    baselineP25Ns:quantile(old,.25),baselineP75Ns:quantile(old,.75),candidateP25Ns:quantile(next,.25),candidateP75Ns:quantile(next,.75),
    loadBefore,loadAfter,samples});
}
// Accessor instrumentation happens only after all timings, so it cannot change
// the preceding measurements' property access feedback.
for(const result of results)Object.assign(result,workCounts(makeDependencyDocument(result.size,result.mode),modeFilters(result.mode)));
process.stdout.write(JSON.stringify({...metadata,status:'PASS',startedAt,finishedAt:new Date().toISOString(),
  rounds,maxLoad,loadStart,loadEnd:os.loadavg(),timingUnit:'ns/selection',sink,
  method:'identical validated inputs, alternating paired rounds; all work counting after timing',
  limitations:'synthetic host selector microbenchmark, no IO/validation in timed loop; shared host; no allocation/device/whole-worker claim',results},null,2)+'\n');
