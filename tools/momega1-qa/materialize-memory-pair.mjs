// Separate, prospectively frozen paired protocol. Original 12-task freeze is unchanged.
import './verify-freeze.mjs';
import fs from 'node:fs';import path from 'node:path';import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';import {fileURLToPath} from 'node:url';import {createHash} from 'node:crypto';
const base=process.argv[2],here=path.dirname(fileURLToPath(import.meta.url));
assert.ok(path.isAbsolute(base??'')&&!fs.existsSync(base),'New absolute destination required');
fs.mkdirSync(base,{recursive:true,mode:0o700});
const repository=path.join(base,'linear-repository');
const made=spawnSync(process.execPath,[path.join(here,'materialize-task.mjs'),'u02',repository],{encoding:'utf8'});
assert.equal(made.status,0,made.stderr);
fs.writeFileSync(path.join(base,'materialization.stdout'),made.stdout);
const earlier=JSON.parse(fs.readFileSync(repository+'.task.json','utf8'));
function git(...args){const r=spawnSync('git',['-C',repository,...args],{encoding:'utf8'});assert.equal(r.status,0,r.stderr);return r.stdout.trim();}
const frozen=path.join(here,'frozen/tasks/u12'),metadata=JSON.parse(fs.readFileSync(path.join(frozen,'task.json'),'utf8'));
const subject=fs.readFileSync(path.join(frozen,'subject.mjs')),originalTest=fs.readFileSync(path.join(frozen,'acceptance.test.mjs'),'utf8');
const acceptance=originalTest.replace("from './subject.mjs'","from '../../lib/subject.mjs'");
fs.writeFileSync(path.join(repository,'lib/subject.mjs'),subject);
fs.writeFileSync(path.join(repository,'.github/continuum/frozen.test.mjs'),acceptance);
git('add','lib/subject.mjs','.github/continuum/frozen.test.mjs');
git('-c','user.name=MOMEGA QA','-c','user.email=qa@example.invalid','commit','-qm','Frozen independent related synthetic bug u12');
const digest=b=>createHash('sha256').update(b).digest('hex');
const later={...earlier,fixture:'u12',task:{...earlier.task,id:'momega1-u12',sourceCommit:git('rev-parse','HEAD'),
 goal:metadata.requirements[0],acceptance:metadata.requirements},sourceSha256:digest(subject),
 frozenTestSha256:digest(originalTest),materializedTestSha256:digest(acceptance)};
fs.writeFileSync(repository+'.task.json',JSON.stringify(later,null,2)+'\n');
assert.equal(git('rev-parse','HEAD^'),earlier.task.sourceCommit,'Direct ancestry required');
const arms=[];
for(const [name,binding] of [['earlier-u02',earlier],['later-u12-memory-off',later],['later-u12-memory-on',later]]){
 const checkout=path.join(base,name);git('worktree','add','--detach',checkout,binding.task.sourceCommit);
 fs.writeFileSync(checkout+'.task.json',JSON.stringify(binding,null,2)+'\n');
 const red=spawnSync(process.execPath,['--test','--test-reporter=tap','.github/continuum/frozen.test.mjs'],{cwd:checkout,encoding:'utf8',timeout:15000});
 fs.writeFileSync(path.join(base,name+'.baseline.tap'),red.stdout??'');
 fs.writeFileSync(path.join(base,name+'.baseline.stderr'),red.stderr??'');
 assert.equal(red.status,1);assert.match(red.stdout,/ERR_ASSERTION/);
 arms.push({name,checkout,binding,baseline:{exit:red.status,tapSha256:digest(red.stdout),stderrSha256:digest(red.stderr??'')}});
}
const manifest={schema:'momega1-related-memory-operational-freeze-v1',createdAt:new Date().toISOString(),
 phase:'FROZEN_BEFORE_ANY_MODEL_EXPOSURE',originalManifestSha256:digest(fs.readFileSync(path.join(here,'FROZEN-MANIFEST.json'))),
 experimentBoundary:'Separate paired protocol. Original 12-task fixtures and original materialized SHAs remain unchanged.',
 history:{earlier:earlier.task.sourceCommit,later:later.task.sourceCommit,directParent:true},arms,
 controls:{sameLaterCommit:true,sameLaterSourceAndTests:true,sameModelRuntimeAndBudgetsRequired:true,
  perTaskLimits:later.task.limits,inheritedSyntheticState:'Both later arms must start from identical completed earlier-state snapshot; keys stay private.',
  differingTreatment:'Only independently accepted explicit technical-recall toggle. Interface currently NOT_YET_ACCEPTED.',
  noSolutionPatches:true,noTeacherAnswers:true,noModelExposure:true},
 admission:'Requires independently accepted genuine inference and canonical ancestry authorization; do not weaken proof, grant, revocation or workspace checks.',
 order:['earlier-u02','later-u12-memory-off','later-u12-memory-on'],
 success:'Measure outcomes and actual calls/time, including failure. Positive reuse requires genuine persisted earlier proof and authorized recalled item; no gain inferred from unrelated state.',
 deviceStatus:'FOLD7_NOT_RUN'};
fs.writeFileSync(path.join(base,'operational-manifest.json'),JSON.stringify(manifest,null,2)+'\n');
console.log(JSON.stringify({base,manifestSha256:digest(fs.readFileSync(path.join(base,'operational-manifest.json'))),history:manifest.history,baselineFailures:arms.length}));
