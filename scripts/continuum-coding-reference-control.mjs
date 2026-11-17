/** Explicit research control for the published local coding benchmark.
 * This patch is PREPROGRAMMED, NOT MODEL GENERATED. No inference or training.
 * Reuses the benchmark's actual owner-bound task and branded Docker executor.
 */
import fs from 'node:fs';import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {createIsolatedExecutor,loadIsolationConfig} from '../.github/continuum/isolated-executor.mjs';
import {taskFingerprint,validateCodingTask} from '../.github/continuum/coding-task.mjs';
import {runIndependentCcc1PatchVerification} from '../.github/continuum/ccc1-independent-verifier.mjs';
import {ensureCcc1StateOutsideSource} from '../.github/continuum/ccc1-path-policy.mjs';

const root=fileURLToPath(new URL('..',import.meta.url));
if(process.argv.length!==4)throw Error('Usage: node scripts/continuum-coding-reference-control.mjs /existing/external/benchmark-directory /external/isolation.json');
const directory=ensureCcc1StateOutsideSource(root,path.resolve(process.argv[2]));
const task=validateCodingTask(JSON.parse(fs.readFileSync(path.join(directory,'task.json'),'utf8')));
if(task.allowedPaths.join(',')!=='lib/pagination.mjs'||task.goal!=='Fix page counting for a nonempty partial final page while preserving integer input validation.')throw Error('NOT_THE_PUBLISHED_BENCHMARK');
const project=path.join(directory,'source'),stateDir=path.join(directory,'state');
const original=fs.readFileSync(path.join(project,task.allowedPaths[0]),'utf8');
if(original.split('return Math.floor(items / pageSize);').length!==2)throw Error('REFERENCE_SOURCE_MISMATCH');
const code=original.replace('return Math.floor(items / pageSize);','return Math.ceil(items / pageSize);');
const sha=value=>createHash('sha256').update(value).digest('hex');
const executor=createIsolatedExecutor(loadIsolationConfig(process.argv[3],{sourceRoot:root}));
const result=runIndependentCcc1PatchVerification({typedTask:task,file:task.allowedPaths[0],projectRoot:project,stateDir,executor,
 candidate:{status:'CANDIDATE_UNVERIFIED',sourceCommit:task.sourceCommit,taskSha256:taskFingerprint(task),sourceSha256:sha(original),candidateSha256:sha(code),code},deadline:Date.now()+120000});
const report={schema:'continuum-preprogrammed-reference-control-v1',modelGenerated:false,inferenceCalls:0,training:false,
 task,referenceCode:code,executorPolicy:executor.policy,result};
fs.writeFileSync(path.join(directory,'reference-control.json'),JSON.stringify(report,null,2)+'\n',{flag:'wx',mode:0o600});
console.log(JSON.stringify({modelGenerated:false,status:result.status,reason:result.reason}));
if(result.status!=='DUAL_WORKTREE_CHECKS_PASS_REVIEW_REQUIRED')process.exitCode=1;
