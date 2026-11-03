// CONTRACT_TEST: all observations/proofs below are synthetic, signed with a
// disposable fixture key. These tests never attest actual model inference.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawn,spawnSync} from 'node:child_process';
import {createHash,createHmac,randomBytes,randomUUID} from 'node:crypto';
import {envelope,canonical,sha256} from './ccc1-envelope.mjs';
import {DurableTaskJournal} from './ccc1-journal.mjs';
import {ExperienceLog,experienceOf} from './ccc1-experience.mjs';
import {taskFingerprint} from './coding-task.mjs';
import {jobTaskId} from '../../scripts/continuum-mobile-coding.mjs';
import {recordTechnicalExperience,recallTechnicalExperiences,revokeTechnicalExperience,TECHNICAL_RECALL_LIMITS} from './ccc1-technical-recall.mjs';
const H='a'.repeat(64),SOURCE='b'.repeat(40),NOW=Date.UTC(2026,10,3);
const sign=(p,key)=>({...p,mac:createHmac('sha256',key).update('continuum-momega1-technical-proof-v1\0'+canonical(p)).digest('hex')});
function fixture(t){
 const stateDir=fs.mkdtempSync(path.join(os.tmpdir(),'momega1-recall-'));t.after(()=>fs.rmSync(stateDir,{recursive:true,force:true}));
 const key=randomBytes(32),taskId='t-0123456789abcdef',job=randomUUID(),task={schema:'continuum-coding-task',version:1,
  id:'synthetic-original',sourceCommit:SOURCE,kind:'BUGFIX',goal:'Repair scoped arithmetic',allowedPaths:['src/math.mjs'],
  acceptance:['Negative values retain their sign'],chain:'node',limits:{maxModelCalls:2,maxContextChars:12000}};
 const modelTask={...task,id:jobTaskId(job)},fingerprint=taskFingerprint(task),modelFingerprint=taskFingerprint(modelTask);
 fs.mkdirSync(path.join(stateDir,'coding-grants'));fs.writeFileSync(path.join(stateDir,'coding-grants',task.id+'.json'),JSON.stringify({task,ccc1TaskId:taskId,fingerprint}));
 const binding={sessionId:randomUUID(),taskId,repoFullName:'synthetic/math',baseCommitSha:SOURCE,worktreeId:'synthetic',
  deadline:NOW+100000,maxRounds:2,maxCpuMillis:100000,maxMemoryBytes:100000000,permissions:['node-repair:node']};
 const journal=DurableTaskJournal.forTask(stateDir,taskId,key);
 const add=(sender,kind,payload,extra={})=>journal.append(envelope({binding,sender,kind,payload,sequence:journal.entries().length,...extra})).entry;
 add('OWNER','GOAL',{text:task.goal+' [task '+fingerprint+']',repair:{chain:'node'}});
 const hyp=add('REASONER','HYPOTHESIS',{claim:'Repair arithmetic',repair:{chain:'node'},basis:'Synthetic failing test'});
 const request=add('REASONER','TEST_REQUEST',{hypothesis:hyp.messageId,repair:{chain:'node'},budgetMinutes:1});
 const receipt=add('VERIFIER','RECEIPT',{request:request.messageId,job,status:'PATCH_AVAILABLE',verdict:'CONFIRMED',reasons:[],receiptSha256:H,
  patchSha256:H,execution:'EXECUTED',cost:{cpuMillis:12}},{verificationReceiptId:H});
 add('REASONER','CRITIQUE',{receipt:receipt.messageId,verdict:'CONFIRMED',reason:'Synthetic fixture only'});
 const entries=journal.entries(),modelObservation={schema:'momega1-local-process-observation-v1',evidenceClass:'LOCAL_MODEL_HOST',
  executionId:'synthetic-execution',taskId:modelTask.id,sourceCommit:SOURCE,taskFingerprint:modelFingerprint,requestIndex:1,
  contextSha256:H,promptSha256:H,requestSha256:H,modelSha256:H,runtimeSha256:H,outputSha256:H,proposalSha256:H,
  outputTokens:2,pid:123,exitCode:0,isolation:{kind:'SYNTHETIC_CONTRACT_FIXTURE'}};
 const verification={schema:'momega1-coding-verification',version:1,evidenceClass:'HOST_INTEGRATION',taskFingerprint:modelFingerprint,
  sourceCommit:SOURCE,chain:'node',patchSha256:H,proposalSha256:H,verdict:'VERIFIED',reasons:[],
  red:{profiles:[{identities:[H],exitCode:1}]},green:{profiles:[{identities:[H],exitCode:0}]},diagnostics:[{identity:H,message:'Synthetic failure'}]};
 const reflection={summary:'Only this synthetic arithmetic scope passed its checks.',limits:['Synthetic contract evidence only; no actual model inference.'],rejectedAlternatives:['Unbounded source edits were excluded.']};
 let proof={schema:'momega1-technical-proof',version:1,kind:'TECHNICAL_EXPERIENCE_REVIEW_ONLY',evidenceClass:'LOCAL_MODEL_HOST',
  taskId,task,modelTask,taskFingerprint:fingerprint,repoFullName:binding.repoFullName,journalHead:entries.at(-1).hash,
  requestMessageId:request.messageId,receiptMessageId:receipt.messageId,receiptSha256:H,modelObservation,verification,reflection,
  observedAt:NOW,expiresAt:NOW+TECHNICAL_RECALL_LIMITS.maxAgeMs,resources:{cpuMillis:12,rounds:1}};
 const file=path.join(stateDir,'experience.jsonl');
 const publish=(edit=p=>p,{signed=true}={})=>{
  fs.rmSync(file,{force:true});const data=edit(structuredClone(proof));
  new ExperienceLog(file).append({...experienceOf(taskId,entries),technicalProof:signed?sign(data,key):data});
 };
 const options={stateDir,task,repoFullName:binding.repoFullName,key,now:NOW+1};
 const recall=(extra={})=>recallTechnicalExperiences({...options,...extra});
 return {stateDir,key,taskId,task,modelTask,modelObservation,verification,reflection,proof,file,journal,entries,publish,options,recall};
}
test('CONTRACT_TEST signed fixture survives a genuine fresh Node process restart',t=>{
 const f=fixture(t);f.publish();assert.equal(f.recall().items.length,1);
 const module=new URL('./ccc1-technical-recall.mjs',import.meta.url).href;
 const source=`import fs from 'node:fs';import {recallTechnicalExperiences} from ${JSON.stringify(module)};const q=JSON.parse(fs.readFileSync(0,'utf8'));q.key=Buffer.from(q.key,'hex');process.stdout.write(JSON.stringify(recallTechnicalExperiences(q)));`;
 const r=spawnSync(process.execPath,['--input-type=module','-e',source],{input:JSON.stringify({...f.options,key:f.key.toString('hex')}),encoding:'utf8',timeout:10000});
 assert.equal(r.status,0,r.stderr);const result=JSON.parse(r.stdout);assert.equal(result.items.length,1);
 assert.equal(result.items[0].provenance.journalHead,f.entries.at(-1).hash);
 assert.match(result.text,/review only/);assert.equal(result.items[0].kind,'TECHNICAL_EXPERIENCE_REVIEW_ONLY');
 assert.equal('ownerConfirmed' in result.items[0],false);assert.equal('training' in result.items[0],false);
});
test('unbranded observations and copied success flags cannot be admitted',t=>{
 const f=fixture(t);assert.throws(()=>recordTechnicalExperience({...f.options,taskId:f.taskId,modelTask:f.modelTask,
  modelObservation:{...f.modelObservation,verifiedModelExecution:true},verification:f.verification,reflection:f.reflection}),/RUNTIME_OBSERVATION_REQUIRED/);
 assert.equal(fs.existsSync(f.file),false);
});
test('legacy successful terminal records do not become technical proof',t=>{
 const f=fixture(t);new ExperienceLog(f.file).append(experienceOf(f.taskId,f.entries));assert.deepEqual(f.recall().items,[]);
});
for(const [name,edit] of Object.entries({
 'source SHA':p=>{p.task.sourceCommit='c'.repeat(40);},'model/source':p=>{p.modelObservation.sourceCommit='c'.repeat(40);},
 'model/task':p=>{p.modelObservation.taskId='other-task';},'model/request':p=>{p.modelObservation.requestSha256='invalid';},
 'model/output':p=>{p.modelObservation.outputSha256='invalid';},'model/identity':p=>{p.modelObservation.modelSha256='invalid';},
 'model/proposal':p=>{p.modelObservation.proposalSha256='d'.repeat(64);},'verifier/patch':p=>{p.verification.patchSha256='d'.repeat(64);},
 'verifier/task':p=>{p.verification.taskFingerprint='d'.repeat(64);},'missing verification':p=>{p.verification=null;},
 'missing diagnostics':p=>{p.verification.diagnostics=[];},'missing RED':p=>{p.verification.red=null;},
 'receipt':p=>{p.receiptSha256='d'.repeat(64);},'journal head':p=>{p.journalHead='d'.repeat(64);},
 'permission promotion':p=>{p.kind='OWNER_CONFIRMED';},'evidence class':p=>{p.evidenceClass='FIRST_COGNITIVE_LOOP_VERIFIED';},
 'future time':p=>{p.observedAt=NOW+10;},'unbounded age':p=>{p.expiresAt++;}
}))test('CONTRACT_TEST replay rejects substituted '+name,t=>{
 const f=fixture(t);f.publish(p=>{edit(p);return p;});assert.equal(f.recall().items.length,0);
});
test('hash-chain recomputation does not replace the runtime proof MAC',t=>{
 const f=fixture(t);f.publish();const r=JSON.parse(fs.readFileSync(f.file));r.record.technicalProof.modelObservation.outputSha256='d'.repeat(64);
 const {hash,...data}=r;fs.writeFileSync(f.file,JSON.stringify({...data,hash:sha256(canonical(data))})+'\n');assert.equal(f.recall().items.length,0);
});
test('wrong runtime key cannot authenticate persisted proof or canonical journal',t=>{
 const f=fixture(t);f.publish();assert.equal(f.recall({key:randomBytes(32)}).items.length,0);
});
test('revocation persists and is consulted by the next fresh recall',t=>{
 const f=fixture(t);f.publish();assert.equal(revokeTechnicalExperience({...f.options,taskId:f.taskId,reason:'Withdraw synthetic evidence'}),true);
 assert.equal(f.recall().items.length,0);assert.equal(new ExperienceLog(f.file).entries().length,2);
});
test('forged revocations block recall instead of being silently ignored',t=>{
 const f=fixture(t);f.publish();new ExperienceLog(f.file).append({taskId:'revoke:'+f.taskId,revocation:{schema:'momega1-technical-revocation',targetTaskId:f.taskId,mac:H}});
 assert.equal(f.recall().status,'BLOCKED_REVOCATION_INVALID');assert.equal(f.recall().items.length,0);
});
test('deleted current canonical grant fails closed even with valid historical proof',t=>{
 const f=fixture(t);f.publish();fs.rmSync(path.join(f.stateDir,'coding-grants',f.task.id+'.json'));assert.equal(f.recall().items.length,0);
});
test('canonical journal corruption and torn experience tail fail closed',t=>{
 const f=fixture(t);f.publish();fs.appendFileSync(f.journal.file,'corrupt');assert.equal(f.recall().items.length,0);
 fs.appendFileSync(f.file,'{');assert.equal(f.recall().status,'BLOCKED_LEDGER_DAMAGED');
});
test('freshness and exact repository/source/file scope are enforced',t=>{
 const f=fixture(t);f.publish();
 for(const options of [{repoFullName:'other/repo'},{task:{...f.task,sourceCommit:'c'.repeat(40)}},
  {task:{...f.task,allowedPaths:['src/other.mjs']}},{now:f.proof.expiresAt}])assert.equal(f.recall(options).items.length,0);
});
test('no partial historical proof is emitted when it exceeds context budget',t=>{
 const f=fixture(t);f.publish();assert.equal(f.recall({maxChars:128}).text,'');assert.ok(f.recall().text.length<=4096);
 for(const options of [{maxChars:4097},{limit:4},{limit:0},{maxChars:127}])assert.throws(()=>f.recall(options),/BOUND_INVALID/);
});
test('real concurrent child writers preserve every task exactly once',async t=>{
 const f=fixture(t),module=new URL('./ccc1-experience.mjs',import.meta.url).href;
 const code=`import {ExperienceLog} from ${JSON.stringify(module)};const log=new ExperienceLog(process.argv[1]);for(let i=0;i<12;i++)log.append({taskId:process.argv[2]+'-'+i});`;
 await Promise.all(Array.from({length:6},(_,n)=>new Promise((resolve,reject)=>{
  const child=spawn(process.execPath,['--input-type=module','-e',code,f.file,String(n)],{stdio:['ignore','pipe','pipe']});let err='';child.stderr.on('data',b=>err+=b);child.on('error',reject);child.on('exit',status=>status===0?resolve():reject(Error(err)));
 })));
 const all=new ExperienceLog(f.file).entries();assert.equal(all.length,72);assert.equal(new Set(all.map(e=>e.record.taskId)).size,72);
 assert.equal(fs.existsSync(f.file+'.lock'),false);
});
test('a terminated process leaves an uncertain writer lease, never automatic stale recovery',t=>{
 const f=fixture(t);const source=`import fs from 'node:fs';fs.mkdirSync(process.argv[1]+'.lock');process.kill(process.pid,'SIGKILL');`;
 const result=spawnSync(process.execPath,['--input-type=module','-e',source,f.file]);assert.equal(result.signal,'SIGKILL');
 assert.throws(()=>new ExperienceLog(f.file).append({taskId:'next'}),/locked/);
});
