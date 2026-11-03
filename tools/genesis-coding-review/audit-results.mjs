// Post-run independent consistency audit. Does not invoke a model, patch or test executor.
import fs from 'node:fs';import path from 'node:path';import assert from 'node:assert/strict';import {createHash} from 'node:crypto';import {spawnSync} from 'node:child_process';
import {DurableTaskJournal,runtimeKey} from '/workspace/genesis-a0-source/.github/continuum/ccc1-journal.mjs';
import {verifyRepairReceipt} from '/workspace/genesis-a0-source/.github/continuum/ccc1-verifier.mjs';
import {ExperienceLog} from '/workspace/genesis-a0-source/.github/continuum/ccc1-experience.mjs';
import {validWorkspaceEvidence} from '/workspace/genesis-a0-source/.github/continuum/isolated-executor.mjs';
const digest=b=>createHash('sha256').update(b).digest('hex');
const root='/workspace/genesis-a0-experiment',checks=[];let reserved=0,admitted=0;
for(const caseId of ['g01','g02','g03']){
 const run=path.join(root,'runs',caseId),dir=path.join(run,'evidence');
 const read=n=>JSON.parse(fs.readFileSync(path.join(dir,n),'utf8'));
 const before=read('before.json'),after=read('after.json'),result=read('result.json'),requests=read('model-requests.json');
 const state=path.join(dir,'private-synthetic-state'),entries=DurableTaskJournal.forTask(state,result.ccc1TaskId,runtimeKey(state)).entries();
 assert.equal(after.integratedSource,'fd743adce297c0958aadce1576b80580817837f0');assert.equal(after.integratedStatus,'');assert.equal(after.fixtureStatus,'');assert.equal(after.testSha256,before.fixture.materializedTestSha256);
 assert.equal(result.sourceCommit,before.fixture.task.sourceCommit);assert.equal(result.taskFingerprint,before.taskFingerprint);
 assert.equal(before.technicalRecallEnabled,false);assert.equal(before.inheritedState,null);
 assert.equal(entries[0].baseCommitSha,result.sourceCommit);assert.equal(entries[0].taskId,result.ccc1TaskId);
 assert.ok(requests.length<=2);reserved+=requests.length;
 const verdicts=result.receipts.map(receipt=>{
  assert.ok(result.receiptJobs.includes(receipt.job));assert.ok(entries.some(x=>x.kind==='RECEIPT'&&x.payload.job===receipt.job));
  for(const request of requests){assert.equal(request.sourceCommit,receipt.commit);assert.equal(request.taskId,receipt.taskId);
   if(request.verifiedModelExecution){const o=request.modelObservation;admitted++;assert.equal(o.taskId,request.taskId);assert.equal(o.sourceCommit,request.sourceCommit);assert.equal(o.taskFingerprint,request.taskFingerprint);assert.equal(o.requestIndex,request.request);assert.equal(o.contextSha256,request.contextSha256);assert.equal(o.promptSha256,request.promptSha256);assert.equal(o.modelSha256,before.configuration.CONTINUUM_LOCAL_BRAIN_MODEL_SHA256);assert.equal(o.runtimeSha256,before.configuration.CONTINUUM_LOCAL_BRAIN_RUNTIME_SHA256);assert.equal(o.isolation,'BWRAP_UNSHARE_ALL');assert.equal(o.exitCode,0);}
  }
  return {job:receipt.job,...verifyRepairReceipt(receipt,{job:receipt.job,commit:result.sourceCommit,chain:'node'})};
 });
 assert.equal(result.modelExecution.verifiedCalls,requests.filter(x=>x.verifiedModelExecution).length);
 const reports=[];function walk(d){for(const e of fs.readdirSync(d,{withFileTypes:true})){const p=path.join(d,e.name);if(e.isDirectory())walk(p);else if(e.name==='report.json')reports.push(JSON.parse(fs.readFileSync(p)));}}
 walk(path.join(state,'teamleader/reports'));
 const isolation=[];function inspect(x){if(!x||typeof x!=='object')return;for(const [k,v] of Object.entries(x)){if(k==='isolation'&&v?.schema==='continuum-isolation-v1'){assert.ok(validWorkspaceEvidence(v,v.profile));assert.equal(v.workspaceBinding.taskId,result.ccc1TaskId);assert.equal(v.workspaceBinding.sourceCommit,result.sourceCommit);assert.ok(result.receiptJobs.includes(v.workspaceBinding.jobId));isolation.push({workspaceId:v.workspaceId,jobId:v.workspaceBinding.jobId,sourceSha256:v.sourceSha256,containerId:v.observed.containerId,teardownConfirmed:v.teardownConfirmed});}else inspect(v);}}
 reports.forEach(inspect);assert.ok(isolation.length>=2);
 const memory=new ExperienceLog(path.join(state,'experience.jsonl')).entries();assert.equal(memory.length,1);assert.equal(memory[0].record.taskId,result.ccc1TaskId);assert.equal(result.technicalExperienceStatus,'NOT_RECORDED');
 const recallLines=fs.readFileSync(path.join(dir,'restart-recall.stdout'),'utf8').trim().split('\n');const recall=JSON.parse(recallLines.at(-1));assert.equal(read('restart-recall.exit.json').status,0);assert.equal(recall.status,'OK');assert.equal(recall.items.length,0);
 checks.push({caseId,status:result.status,reservedCalls:requests.length,admittedGenuineCalls:result.modelExecution.verifiedCalls,verdicts,journalVerified:true,journalEntries:entries.length,journalKinds:entries.map(e=>[e.sender,e.kind]),journalTerminal:entries.at(-1).payload,experienceRecords:memory.length,experienceOutcome:memory[0].record.outcome??memory[0].record.status??null,technicalExperienceStatus:result.technicalExperienceStatus,restartRecall:recall.items.length,sourceAndTestsUnchanged:true,isolation});
}
assert.ok(reserved<=6);const git=spawnSync('git',['-C','/workspace/genesis-a0-source','status','--porcelain'],{encoding:'utf8'});assert.equal(git.status,0);assert.equal(git.stdout,'');
console.log(JSON.stringify({schema:'genesis-a0-independent-audit-v1',source:'fd743adce297c0958aadce1576b80580817837f0',auditorSha256:digest(fs.readFileSync(new URL(import.meta.url))),reservedCalls:reserved,admittedGenuineCalls:admitted,confirmedRepairs:checks.flatMap(c=>c.verdicts).filter(v=>v.verdict==='CONFIRMED').length,checks},null,2));
