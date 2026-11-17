// Independently preregistered before implementation. Synthetic MAC fixtures only.
// No model executable, inference, weights, holdout contents, network or source patch execution.
import test from 'node:test';
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
function setup(t,{historical='old',current='new',allowedPaths,remote=true}={}){
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
function positive(f){const r=f.restart();assert.equal(r.status,'OK');assert.equal(r.items.length,1,'eligible synthetic ancestor fixture must be recalled');return r.items[0];}
function empty(f,extra={}){assert.equal(f.restart(extra).items.length,0);}
test('L01 no project retains legacy exact-source positive behavior',t=>{const f=setup(t,{historical:'new'});const r=f.restart({project:null});assert.equal(r.items.length,1);});
test('L02 no project retains exact-source restriction for changed code',t=>{const f=setup(t);empty(f,{project:null});});
test('L03 direct Git ancestor after restart carries old/current source and lineage labels',t=>{const f=setup(t,{historical:'mid'});const item=positive(f);assert.equal(item.sourceCommit,f.midSha);assert.equal(item.currentSourceCommit,f.newSha);assert.deepEqual(item.sourceLineage,{kind:'GIT_ANCESTOR',historicalSourceCommit:f.midSha,currentSourceCommit:f.newSha,ancestor:true});assert.deepEqual(item.sourceFiles,['src/counter.mjs']);});
test('L04 more distant actual ancestor is eligible but never grants permission',t=>{const f=setup(t);positive(f);const r=f.restart();assert.match(r.text,/untrusted data/);assert.match(r.text,/no permission/);assert.equal('patch' in r.items[0],false);});
test('L05 supplied clean same-source project emits exact lineage labels',t=>{const f=setup(t,{historical:'new'});const item=positive(f);assert.deepEqual(item.sourceLineage,{kind:'EXACT_SOURCE',historicalSourceCommit:f.newSha,currentSourceCommit:f.newSha,ancestor:true});});
test('L06 project current HEAD must equal currently approved commit',t=>{const f=setup(t);git(f.project,'checkout','-q','--detach',f.midSha);empty(f);});
for(const mode of ['unstaged','staged','untracked'])test('L07 '+mode+' dirty project refuses ancestor reuse',t=>{const f=setup(t);if(mode==='untracked')fs.writeFileSync(path.join(f.project,'extra.txt'),'untracked');else{fs.appendFileSync(path.join(f.project,'src/counter.mjs'),'// changed\n');if(mode==='staged')git(f.project,'add','.');}empty(f);});
test('L08 supplied same-source project must still reject dirt',t=>{const f=setup(t,{historical:'new'});fs.appendFileSync(path.join(f.project,'src/counter.mjs'),'// changed\n');empty(f);});
test('L09 same commit objects with changed origin identity are refused',t=>{const f=setup(t);git(f.project,'remote','set-url','origin','https://github.com/other/counters.git');empty(f);});
test('L10 invalid project/non-repository cannot establish ancestry',t=>{const f=setup(t);empty(f,{project:f.stateDir});});
test('L11 nested directory is not the canonical project root',t=>{const f=setup(t);empty(f,{project:path.join(f.project,'src')});});
test('L12 historical descendant cannot be reused by an earlier current task',t=>{const f=setup(t,{historical:'new',current:'old'});git(f.project,'checkout','-q','--detach',f.oldSha);empty(f);});
test('L13 unrelated real history cannot be admitted from matching remote/path',t=>{const f=setup(t);git(f.project,'checkout','-q','--orphan','unrelated');git(f.project,'add','.');git(f.project,'commit','-qm','Synthetic unrelated root');const other=git(f.project,'rev-parse','HEAD');fs.unlinkSync(f.current.journal.file);fs.unlinkSync(f.current.file);f.current=request(f,{source:other});f.query.task=f.current.task;empty(f);});
test('L14 missing historical commit object refuses reuse',t=>{const f=setup(t);fs.unlinkSync(path.join(f.project,'.git','objects',f.oldSha.slice(0,2),f.oldSha.slice(2)));empty(f);});
test('L15 Git replace refs cannot supply or alter trusted ancestry',t=>{const f=setup(t);git(f.project,'replace',f.midSha,f.oldSha);empty(f);});
test('L16 legacy graft file cannot supply trusted ancestry',t=>{const f=setup(t);fs.writeFileSync(path.join(f.project,'.git','info','grafts'),f.newSha+' '+f.oldSha+'\n');empty(f);});
test('L17 incomplete shallow ancestry does not silently generalize',t=>{const f=setup(t);fs.writeFileSync(path.join(f.project,'.git','shallow'),f.newSha+'\n');empty(f);});
test('L18 canonical local basename identity permits same repository ancestry',t=>{const f=setup(t,{remote:false});const item=positive(f);assert.equal(item.repoFullName,'local/device-ai');});
test('L19 descendant commits never widen allowed path overlap',t=>{const f=setup(t,{allowedPaths:['src/other.mjs']});empty(f);});
test('L20 removed current grant blocks previously eligible ancestor context',t=>{const f=setup(t);positive(f);fs.unlinkSync(f.current.file);empty(f);});
test('L21 expired current journal deadline blocks previously eligible ancestor context',t=>{const f=setup(t);positive(f);empty(f,{now:f.current.goal.deadline+1});});
test('L22 canonical historical revocation remains authoritative over ancestor recall',t=>{const f=setup(t);positive(f);assert.equal(f.revoke(),true);writeRecords(f.file,[f.record()]);empty(f);});
test('L23 MAC substitution on ancestor proof remains rejected',t=>{const f=setup(t);positive(f);const r=f.record();r.technicalProof.modelObservation.modelSha256='9'.repeat(64);writeRecords(f.file,[r]);empty(f);});
test('L24 canonical current ABORT blocks previously eligible ancestor context',t=>{const f=setup(t);positive(f);f.current.journal.append(envelope({binding:f.current.binding,sender:'OWNER',kind:'ABORT',sequence:1,payload:{reason:'Withdraw current synthetic task',code:'OWNER'}}));empty(f);});
test('L25 linked clean worktree uses common real Git objects and exact same origin',t=>{const f=setup(t);const worktree=path.join(f.container,'linked');git(f.project,'worktree','add','-q','--detach',worktree,f.newSha);const r=f.restart({project:worktree});assert.equal(r.status,'OK');assert.equal(r.items.length,1);assert.equal(r.items[0].sourceLineage.kind,'GIT_ANCESTOR');});
