/** Opt-in real local-model experiment; fixed novel fixture, existing canonical pipeline. No mock inference. */
import fs from 'node:fs';import path from 'node:path';import {spawnSync} from 'node:child_process';
import {prepareCodingTask,runCodingTask} from '../.github/continuum/coding-orchestrator.mjs';
import {createIsolatedExecutor,loadIsolationConfig} from '../.github/continuum/isolated-executor.mjs';
import {sha256Hex} from '../.github/continuum/development-backlog.mjs';

const out=path.resolve(process.argv[2]??'');
if(!process.argv[2]||!process.env.CONTINUUM_LOCAL_BRAIN_API_KEY||!process.env.CONTINUUM_CODING_ISOLATION_CONFIG)
 throw Error('Usage: configure authenticated local model and external isolation config, then node scripts/continuum-coding-benchmark.mjs <NEW absolute external evidence directory>');
fs.mkdirSync(out,{recursive:false,mode:0o700});
const root=path.join(out,'source'),state=path.join(out,'state');
fs.mkdirSync(path.join(root,'lib'),{recursive:true});fs.mkdirSync(path.join(root,'.github/continuum'),{recursive:true});
const original=`export function countPages(items, pageSize) {
  if (!Number.isInteger(items) || items < 0 || !Number.isInteger(pageSize) || pageSize <= 0) {
    throw new RangeError('nonnegative integer items and positive integer page size required');
  }
  return Math.floor(items / pageSize);
}
`;
fs.writeFileSync(path.join(root,'lib/pagination.mjs'),original);
// Authored acceptance before inference. These tests are immutable to candidates.
fs.writeFileSync(path.join(root,'.github/continuum/pagination.test.mjs'),`import test from 'node:test';
import assert from 'node:assert/strict';
import {countPages} from '../../lib/pagination.mjs';
for(const [items,size,pages] of [[0,7,0],[1,7,1],[7,7,1],[8,7,2],[401,23,18],[46,23,2],[1024,100,11]])
 test('pages '+items+'/'+size,()=>assert.equal(countPages(items,size),pages));
for(const [items,size] of [[-1,7],[1,0],[1,-1],[1,0.5],[0.5,1]])
 test('invalid '+items+'/'+size,()=>assert.throws(()=>countPages(items,size),RangeError));
`);
const git=(...args)=>{const r=spawnSync('git',args,{cwd:root,encoding:'utf8',timeout:15000,shell:false});if(r.status!==0)throw Error(r.stderr);return r.stdout.trim();};
git('init','-q','-b','experiment/pagination');git('add','.');git('-c','user.name=Continuum Experiment','-c','user.email=continuum@users.noreply.github.com','commit','-qm','Freeze pagination acceptance before local inference');
const commit=git('rev-parse','HEAD');
const task={schema:'continuum-coding-task',version:1,id:'novel-pagination-20261024',sourceCommit:commit,kind:'BUGFIX',
 goal:'Fix page counting for a nonempty partial final page while preserving integer input validation.',
 allowedPaths:['lib/pagination.mjs'],acceptance:['Every item must fit in the counted pages.','Empty input needs zero pages.','Invalid inputs still throw RangeError.'],
 chain:'node',limits:{maxModelCalls:3,maxContextChars:8000}};
fs.writeFileSync(path.join(out,'task.json'),JSON.stringify(task,null,2)+'\n');
const prepared=prepareCodingTask({project:root,stateDir:state,task});
const executor=createIsolatedExecutor(loadIsolationConfig(process.env.CONTINUUM_CODING_ISOLATION_CONFIG,{sourceRoot:root}));
const started=Date.now(),before=process.resourceUsage();
const result=await runCodingTask({project:root,stateDir:state,id:task.id,executor,executorAbortMode:'CHECKPOINT_BOUNDARY_ONLY'});
const after=process.resourceUsage();
const summary={schema:'continuum-real-local-coding-experiment',version:1,
 implementationCommit:spawnSync('git',['rev-parse','HEAD'],{cwd:new URL('..',import.meta.url),encoding:'utf8'}).stdout.trim(),
 fixtureCommit:commit,fixtureNature:'newly authored application regression; not a blind model-training contamination guarantee',
 configuredModel:process.env.CONTINUUM_LOCAL_BRAIN_MODEL??'local',modelSha256:process.env.CONTINUUM_CODING_MODEL_SHA256??null,
 elapsedMs:Date.now()-started,parentCpuMicros:after.userCPUTime+after.systemCPUTime-before.userCPUTime-before.systemCPUTime,
 parentMaxRssKiB:after.maxRSS,modelResources:'server resource measurements recorded separately; parent RSS excludes inference server',
 originalSourceUnchanged:fs.readFileSync(path.join(root,'lib/pagination.mjs'),'utf8')===original&&git('status','--porcelain')==='',
 originalSourceSha256:sha256Hex(original),prepared,result,deviceVerified:false,remoteInference:false};
fs.writeFileSync(path.join(out,'result.json'),JSON.stringify(summary,null,2)+'\n');
process.stdout.write(JSON.stringify({status:result.status,elapsedMs:summary.elapsedMs,fixtureCommit:commit,evidence:path.join(out,'result.json')})+'\n');
if(result.status!=='PATCH_PENDING_OWNER_REVIEW')process.exitCode=1;
