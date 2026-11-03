// Materialize a frozen bug task for the EXISTING typed coding/CCC1 pipeline.
// No inference, provider, solution patch, approval or execution is performed here.
import './verify-freeze.mjs';
import fs from 'node:fs';import path from 'node:path';import {spawnSync} from 'node:child_process';import {createHash} from 'node:crypto';
const id=process.argv[2],destination=process.argv[3];
if(!/^u(?:0[1-9]|1[0-2])$/.test(id??'')||!destination||!path.isAbsolute(destination)||fs.existsSync(destination))throw Error('Use task u01..u12 and a new absolute destination');
const frozen=path.join(path.dirname(new URL(import.meta.url).pathname),'frozen','tasks',id);
const metadata=JSON.parse(fs.readFileSync(path.join(frozen,'task.json'),'utf8'));
const subject=fs.readFileSync(path.join(frozen,'subject.mjs'));
const originalTest=fs.readFileSync(path.join(frozen,'acceptance.test.mjs'),'utf8');
const token="from './subject.mjs'";
if(originalTest.split(token).length!==2)throw Error('Unexpected frozen test import');
const acceptance=originalTest.replace(token,"from '../../lib/subject.mjs'");
fs.mkdirSync(path.join(destination,'lib'),{recursive:true,mode:0o700});
fs.mkdirSync(path.join(destination,'.github/continuum'),{recursive:true,mode:0o700});
fs.writeFileSync(path.join(destination,'lib/subject.mjs'),subject);
fs.writeFileSync(path.join(destination,'.github/continuum/frozen.test.mjs'),acceptance);
function git(...args){const r=spawnSync('git',['-C',destination,...args],{encoding:'utf8'});if(r.status!==0)throw Error(r.stderr);return r.stdout.trim();}
git('init','-q');git('add','.');git('-c','user.name=MOMEGA QA','-c','user.email=qa@example.invalid','commit','-qm','Frozen independent synthetic bug '+id);
const sourceCommit=git('rev-parse','HEAD');
const task={schema:'continuum-coding-task',version:1,id:'momega1-'+id,sourceCommit,kind:'BUGFIX',goal:metadata.requirements[0],allowedPaths:['lib/subject.mjs'],acceptance:metadata.requirements,chain:'node',limits:{maxModelCalls:2,maxContextChars:8192}};
const digest=b=>createHash('sha256').update(b).digest('hex');
const binding={task,fixture:id,sourceSha256:digest(subject),frozenTestSha256:digest(originalTest),materializedTestSha256:digest(acceptance),transformation:'Only fixed relative import path changed; assertions and source bytes unchanged.',inferenceStatus:'NOT_RUN',ownerApproval:'NOT_GRANTED_BY_MATERIALIZER'};
// Metadata stays outside the committed source and cannot alter test discovery.
fs.writeFileSync(destination+'.task.json',JSON.stringify(binding,null,2)+'\n');
console.log(JSON.stringify({repository:destination,sourceCommit,taskFile:destination+'.task.json'}));
