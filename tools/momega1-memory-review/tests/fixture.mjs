// Independent review fixture. Every proof is SYNTHETIC CONTRACT_TEST, never inference.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {pathToFileURL} from 'node:url';
import {createHmac,randomBytes,randomUUID} from 'node:crypto';
export const root=process.env.MOMEGA1_CANDIDATE_ROOT;
if(!root)throw Error('MOMEGA1_CANDIDATE_ROOT required: extracted exact frozen candidate');
export const url=name=>pathToFileURL(path.join(root,'.github/continuum',name)).href;
export const {canonical,sha256,envelope}=await import(url('ccc1-envelope.mjs'));
export const {ExperienceLog,experienceOf,EXPERIENCE_LIMITS}=await import(url('ccc1-experience.mjs'));
export const {DurableTaskJournal}=await import(url('ccc1-journal.mjs'));
export const {taskFingerprint}=await import(url('coding-task.mjs'));
export const {jobTaskId}=await import(pathToFileURL(path.join(root,'scripts/continuum-mobile-coding.mjs')).href);
export const api=await import(url('ccc1-technical-recall.mjs'));
export const NOW=Date.UTC(2026,10,3),A='1'.repeat(64),B='2'.repeat(64),SOURCE='3'.repeat(40);
export const sign=(data,key)=>({...data,mac:createHmac('sha256',key).update('continuum-momega1-technical-proof-v1\0'+canonical(data)).digest('hex')});
export function writeRecords(file,records){
 let prevHash=null;
 fs.writeFileSync(file,records.map((record,sequence)=>{const data={sequence,prevHash,record};const row={...data,hash:sha256(canonical(data))};prevHash=row.hash;return JSON.stringify(row)+'\n';}).join(''));
}
export function fixture(t){
 const stateDir=fs.mkdtempSync(path.join(os.tmpdir(),'independent-memory-'));
 t.after(()=>fs.rmSync(stateDir,{recursive:true,force:true}));
 const key=randomBytes(32),taskId='t-9876543210abcdef',job=randomUUID();
 const task={schema:'continuum-coding-task',version:1,id:'review-fix-overflow',sourceCommit:SOURCE,kind:'BUGFIX',goal:'Retain precision when comparing counters',allowedPaths:['src/counter.mjs'],acceptance:['Large safe counters remain distinct'],chain:'node',limits:{maxModelCalls:2,maxContextChars:12000}};
 const modelTask={...task,id:jobTaskId(job)};
 const grantFile=path.join(stateDir,'coding-grants',task.id+'.json');fs.mkdirSync(path.dirname(grantFile));
 fs.writeFileSync(grantFile,JSON.stringify({task,ccc1TaskId:taskId,fingerprint:taskFingerprint(task)}));
 const binding={sessionId:randomUUID(),taskId,repoFullName:'review/counters',baseCommitSha:SOURCE,worktreeId:'review-synthetic',deadline:NOW+60000,maxRounds:2,maxCpuMillis:50000,maxMemoryBytes:100000000,permissions:['node-repair:node']};
 const journal=DurableTaskJournal.forTask(stateDir,taskId,key);
 const add=(sender,kind,payload,extra={})=>journal.append(envelope({binding,sender,kind,payload,sequence:journal.entries().length,...extra})).entry;
 add('OWNER','GOAL',{text:task.goal+' [task '+taskFingerprint(task)+']',repair:{chain:'node'}});
 const hypothesis=add('REASONER','HYPOTHESIS',{claim:'Preserve integer comparisons',repair:{chain:'node'},basis:'Synthetic counter fixture'});
 const request=add('REASONER','TEST_REQUEST',{hypothesis:hypothesis.messageId,repair:{chain:'node'},budgetMinutes:1});
 const receipt=add('VERIFIER','RECEIPT',{request:request.messageId,job,status:'PATCH_AVAILABLE',verdict:'CONFIRMED',reasons:[],receiptSha256:A,patchSha256:B,execution:'EXECUTED',cost:{cpuMillis:5}},{verificationReceiptId:A});
 add('REASONER','CRITIQUE',{receipt:receipt.messageId,verdict:'CONFIRMED',reason:'Synthetic review fixture'});
 const entries=journal.entries();
 const modelObservation={schema:'momega1-local-process-observation-v1',evidenceClass:'LOCAL_MODEL_HOST',executionId:'CONTRACT_TEST-no-inference',taskId:modelTask.id,sourceCommit:SOURCE,taskFingerprint:taskFingerprint(modelTask),pid:1234,exitCode:0,contextSha256:A,promptSha256:B,requestSha256:A,modelSha256:B,runtimeSha256:A,outputSha256:B,proposalSha256:A,isolation:{kind:'SYNTHETIC'}};
 const verification={schema:'momega1-coding-verification',version:1,evidenceClass:'HOST_INTEGRATION',verdict:'VERIFIED',taskFingerprint:taskFingerprint(modelTask),sourceCommit:SOURCE,chain:'node',patchSha256:B,proposalSha256:A,red:{profiles:[{identities:[B],exitCode:1}]},green:{profiles:[{identities:[B],exitCode:0}]},diagnostics:[{identity:B,message:'Synthetic inequality failure'}]};
 const reflection={summary:'Compare counters without narrowing integer precision.',limits:['Synthetic persistence fixture; no actual local-model evidence.'],rejectedAlternatives:['Floating point truncation']};
 const proof={schema:'momega1-technical-proof',version:1,kind:'TECHNICAL_EXPERIENCE_REVIEW_ONLY',evidenceClass:'LOCAL_MODEL_HOST',taskId,task,modelTask,taskFingerprint:taskFingerprint(task),repoFullName:binding.repoFullName,journalHead:entries.at(-1).hash,requestMessageId:request.messageId,receiptMessageId:receipt.messageId,receiptSha256:A,modelObservation,verification,reflection,observedAt:NOW,expiresAt:NOW+api.TECHNICAL_RECALL_LIMITS.maxAgeMs,resources:{cpuMillis:5,rounds:1}};
 const file=path.join(stateDir,'experience.jsonl');
 const record=()=>({...experienceOf(taskId,entries),technicalProof:sign(proof,key)});
 const publish=()=>writeRecords(file,[record()]);
 const options={stateDir,task,repoFullName:binding.repoFullName,key,now:NOW+1};
 const recall=(extra={})=>api.recallTechnicalExperiences({...options,...extra});
 const revoke=()=>api.revokeTechnicalExperience({...options,taskId,reason:'Independent review withdrawal'});
 return {stateDir,key,taskId,task,modelTask,modelObservation,verification,reflection,grantFile,journal,entries,add,file,proof,record,publish,options,recall,revoke};
}
