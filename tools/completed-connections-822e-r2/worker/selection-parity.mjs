#!/usr/bin/env node
// R1 parity cases from #511, rebound to the frozen C1 input. No benchmark code.
// Source: tools/tuning-workers-20261104/benchmark-backlog-selection.mjs at 73b64ec299fd314dfdf07835fc7d8fc0d74b51a2.
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import crypto from 'node:crypto';
import fs from 'node:fs';
import {fileURLToPath} from 'node:url';
import * as candidate from '../../../.github/continuum/development-backlog.mjs';

const base='3510c26110098b42d46bb3b7788027fa7aed59e4';
const sourcePath='.github/continuum/development-backlog.mjs';
// The blob is shared by the local and published C1 trees even if commit IDs differ.
const baselineSourceBlob='64f798d5cf86ec7b541bd5135972f30fef1be746';
const root=fileURLToPath(new URL('../../../',import.meta.url));
const baselineSource=execFileSync('git',['show',baselineSourceBlob],{cwd:root,encoding:'utf8',maxBuffer:1_000_000});
const baseline=await import('data:text/javascript;base64,'+Buffer.from(baselineSource).toString('base64'));
const hash=text=>crypto.createHash('sha256').update(text).digest('hex');
const sizes=[0,1,20,100,500],modes=['mixed-priority','ties','scoped','scoped-no-match'];
assert.equal(candidate.LIMITS.tasks,500);
const template=baseline.applyOp(baseline.emptyDocument(0,'benchmark'),{
  op:'propose',actor:'benchmark',at:1,
  task:{id:'task-template',title:'Synthetic selector input',kind:'TEST',source:'owner'}
}).doc.tasks[0];

function makeDocument(size,mode,seed=7){
  let state=seed>>>0;
  const random=()=>{state=(Math.imul(state,1664525)+1013904223)>>>0;return state;};
  const scoped=mode.startsWith('scoped');
  const tasks=Array.from({length:size},(_,i)=>({
    ...structuredClone(template),id:'task-'+String(i).padStart(3,'0'),
    state:scoped&&i%5===0?'PROPOSED':'READY',
    priority:mode==='ties'?500:random()%1001,
    createdAt:mode==='ties'?1:1+random()%7,updatedAt:1000,
    kind:['TEST','REPAIR','INVESTIGATE'][i%3],source:i%(mode==='scoped-no-match'?3:4)===0?'cli':'owner',
    attempts:scoped&&i%11===0?3:0,
    dependsOn:scoped&&i>0&&i%13===0?['task-000']:[],
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

const metadata={base,sourcePath,baselineSourceBlob,baselineSourceSha256:hash(baselineSource),
  candidateSourceSha256:hash(fs.readFileSync(new URL('../../../'+sourcePath,import.meta.url))),
  harnessSha256:hash(fs.readFileSync(fileURLToPath(import.meta.url))),
  node:process.version,v8:process.versions.v8,platform:process.platform,arch:process.arch,
  parityCases,fixtureSizes:sizes,fixtureModes:modes,device:'host; Android/Fold7 not measured'};

assert.equal(parityCases,40960);
process.stdout.write(JSON.stringify({...metadata,status:'PARITY_PASS'},null,2)+'\n');
