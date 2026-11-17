// Adapted from existing QA PR413 at 10f4339a760f8a531993598200b1c3299d3b2b97; reviewer-only new three-case A0.
// Materialize a frozen bug task for the EXISTING typed coding/CCC1 pipeline.
// No inference, provider, solution patch, approval or execution is performed here.
import './verify-known-u03.mjs';
import {acceptanceClauses} from './task-contract.mjs';
import fs from 'node:fs';import path from 'node:path';import {spawnSync} from 'node:child_process';import {createHash} from 'node:crypto';
const id=process.argv[2],destination=process.argv[3];
if(!/^u03$/.test(id??'')||!destination||!path.isAbsolute(destination)||fs.existsSync(destination))throw Error('Use known task u03 and a new absolute destination');
const frozen=path.join(path.dirname(new URL(import.meta.url).pathname),'known-u03');
const metadata=JSON.parse(fs.readFileSync(path.join(frozen,'task.json'),'utf8'));
const subject=fs.readFileSync(path.join(frozen,'subject.source'));
const originalTest=fs.readFileSync(path.join(frozen,'acceptance.fixture'),'utf8');
const token="from './subject.mjs'";if(originalTest.split(token).length!==2)throw Error('Frozen import mismatch');
const acceptance=originalTest.replace(token,"from '../../lib/subject.mjs'");
fs.mkdirSync(path.join(destination,'lib'),{recursive:true,mode:0o700});
fs.mkdirSync(path.join(destination,'.github/continuum'),{recursive:true,mode:0o700});
fs.writeFileSync(path.join(destination,'lib/subject.mjs'),subject);
fs.writeFileSync(path.join(destination,'.github/continuum/frozen.test.mjs'),acceptance);
function git(...args){const r=spawnSync('git',['-C',destination,...args],{encoding:'utf8'});if(r.status!==0)throw Error(r.stderr);return r.stdout.trim();}
git('init','-q');git('add','.');git('-c','user.name=GENESIS QA','-c','user.email=qa@example.invalid','commit','-qm','Frozen independent synthetic bug '+id);
const sourceCommit=git('rev-parse','HEAD');
const task={schema:'continuum-coding-task',version:1,id:'genesis-known-'+id,sourceCommit,kind:'BUGFIX',goal:metadata.requirements[0],allowedPaths:['lib/subject.mjs'],acceptance:acceptanceClauses(metadata.requirements),chain:'node',limits:{maxModelCalls:1,maxContextChars:8192}};
const digest=b=>createHash('sha256').update(b).digest('hex');
const binding={task,fixture:id,sourceSha256:digest(subject),frozenTestSha256:digest(originalTest),materializedTestSha256:digest(acceptance),transformation:'Only fixed relative import changed; source/assertions unchanged. Known original u03, no new generalization claim.',inferenceStatus:'NOT_RUN',ownerApproval:'NOT_GRANTED_BY_MATERIALIZER'};
// Metadata stays outside the committed source and cannot alter test discovery.
fs.writeFileSync(destination+'.task.json',JSON.stringify(binding,null,2)+'\n');
console.log(JSON.stringify({repository:destination,sourceCommit,taskFile:destination+'.task.json'}));
