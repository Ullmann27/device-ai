// Derived verbatim from independently frozen lineage fixture, exporting setup only.
// Independently preregistered before implementation. Synthetic MAC fixtures only.
// No model executable, inference, weights, holdout contents, network or source patch execution.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {randomUUID} from 'node:crypto';
import {fixture,writeRecords,experienceOf,sign,DurableTaskJournal,envelope,taskFingerprint,api,url,NOW} from './fixture.mjs';
function git(project,...args){
 const r=spawnSync('git',['-c','user.name=Independent Synthetic Reviewer','-c','user.email=review@example.invalid',...args],{cwd:project,encoding:'utf8',timeout:10000,env:{...process.env,GIT_TERMINAL_PROMPT:'0',GIT_NO_LAZY_FETCH:'1',GIT_NO_REPLACE_OBJECTS:'1'}});
 assert.equal(r.status,0,`${args.join(' ')}: ${r.stderr}`);return r.stdout.trim();
}
function request(f,{source=f.newSha,allowedPaths=f.task.allowedPaths,repo='review/counters',id='new-authorized-counter'}={}){
 const task={...f.task,id,sourceCommit:source,allowedPaths,goal:'Compare a different counter algorithm after restart'},taskId='t-aaaaaaaaaaaaaaaa';
 const journal=DurableTaskJournal.forTask(f.stateDir,taskId,f.key);
 const binding={...f.entries[0],sessionId:randomUUID(),taskId,baseCommitSha:source,repoFullName:repo};
 const goal=journal.append(envelope({binding,sender:'OWNER',kind:'GOAL',sequence:0,payload:{text:task.goal+' [task '+taskFingerprint(task)+']',repair:{chain:'node'}}})).entry;
 const file=path.join(f.stateDir,'coding-grants',task.id+'.json');
 fs.writeFileSync(file,JSON.stringify({task,ccc1TaskId:taskId,fingerprint:taskFingerprint(task)}));
 return {task,taskId,journal,file,goal,binding};
}
export function setup(t,{historical='old',current='new',allowedPaths,remote=true}={}){
 const f=fixture(t),container=fs.mkdtempSync(path.join(os.tmpdir(),'memory-lineage-'));t.after(()=>fs.rmSync(container,{recursive:true,force:true}));
 const project=path.join(container,'device-ai');fs.mkdirSync(project);git(project,'init','-q','-b','review-fixture');
 if(remote)git(project,'remote','add','origin','https://github.com/review/counters.git');
 fs.mkdirSync(path.join(project,'src'));fs.writeFileSync(path.join(project,'src/counter.mjs'),'export const compare=(a,b)=>a-b;\n');git(project,'add','.');git(project,'commit','-qm','Synthetic historical source');const oldSha=git(project,'rev-parse','HEAD');
 fs.writeFileSync(path.join(project,'src/counter.mjs'),'export const compare=(a,b)=>a<b?-1:a>b?1:0;\n');git(project,'add','.');git(project,'commit','-qm','Synthetic related middle source');const midSha=git(project,'rev-parse','HEAD');
 fs.writeFileSync(path.join(project,'src/counter.mjs'),'export const compare=(a,b)=>a===b?0:(a<b?-1:1);\n');git(project,'add','.');git(project,'commit','-qm','Synthetic related current source');const newSha=git(project,'rev-parse','HEAD');
 const commits={old:oldSha,mid:midSha,new:newSha},repo=remote?'review/counters':'local/device-ai';
 f.task.sourceCommit=commits[historical];f.modelTask.sourceCommit=commits[historical];
 f.proof.taskFingerprint=taskFingerprint(f.task);f.proof.repoFullName=repo;
 f.modelObservation.sourceCommit=commits[historical];f.modelObservation.taskFingerprint=taskFingerprint(f.modelTask);
 f.verification.sourceCommit=commits[historical];f.verification.taskFingerprint=taskFingerprint(f.modelTask);
 fs.writeFileSync(f.grantFile,JSON.stringify({task:f.task,ccc1TaskId:f.taskId,fingerprint:taskFingerprint(f.task)}));
 fs.unlinkSync(f.journal.file);
 for(const previous of f.entries){const {hash,prevHash,mac,...entry}=previous;entry.baseCommitSha=commits[historical];entry.repoFullName=repo;if(entry.kind==='GOAL')entry.payload={...entry.payload,text:f.task.goal+' [task '+taskFingerprint(f.task)+']'};f.journal.append(entry);}
 f.entries=f.journal.entries();f.proof.journalHead=f.entries.at(-1).hash;
 f.record=()=>({...experienceOf(f.taskId,f.entries),technicalProof:sign(f.proof,f.key)});f.publish=()=>writeRecords(f.file,[f.record()]);f.publish();
 Object.assign(f,{project,container,oldSha,midSha,newSha,commits,repo});
 f.current=request(f,{source:commits[current],allowedPaths,repo});
 f.query={...f.options,task:f.current.task,repoFullName:repo,project};
 f.recallQuery=extra=>api.recallTechnicalExperiences({...f.query,...extra});
 f.restart=(extra={})=>{const code=`import fs from 'node:fs';import {recallTechnicalExperiences} from ${JSON.stringify(url('ccc1-technical-recall.mjs'))};const q=JSON.parse(fs.readFileSync(0,'utf8'));q.key=Buffer.from(q.key,'hex');console.log(JSON.stringify(recallTechnicalExperiences(q)));`;const r=spawnSync(process.execPath,['--input-type=module','-e',code],{input:JSON.stringify({...f.query,...extra,key:f.key.toString('hex')}),encoding:'utf8',timeout:10000,env:{...process.env,GIT_TERMINAL_PROMPT:'0'}});assert.equal(r.status,0,r.stderr);return JSON.parse(r.stdout);};
 return f;
}
