/** Negative report contracts with real CCC1 journals, grants and report artifacts.
 * Docker's external command boundary is a failure-only double. No container,
 * local model, successful repair or device execution is attested by these tests. */
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import childProcess from 'node:child_process';
import {syncBuiltinESMExports} from 'node:module';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {prepareCodingTask,runCodingTask,codingTaskFailureResult} from './coding-orchestrator.mjs';
import {createCodingCandidateProvider} from './coding-provider.mjs';
import {createIsolatedExecutor} from './isolated-executor.mjs';
import {createCcc1BotAssignment} from './ccc1-bot-assignment.mjs';
import {DurableTaskJournal,runtimeKey,WriterLock} from './ccc1-journal.mjs';
import {taskState} from './ccc1-orchestrator.mjs';
import {sha256Hex,canonical} from './development-backlog.mjs';
import {createHmac} from 'node:crypto';
import {runCodingServiceTask} from './coding-service-worker.mjs';
import {LOCAL_MODEL_RUNTIME_CANDIDATES} from '../../scripts/continuum-local-model-pins.mjs';

const IMAGE='sha256:'+'a'.repeat(64);
// The fixed repair profile reruns this file in a child. Cases requiring their
// own repair attempt belong to the outer integration run; do not defeat DEPTH.
const repairIntegrationTest=(name,run)=>test(name,{skip:process.env.CONTINUUM_REPAIR_DEPTH?
 'Nested repair execution is forbidden by the existing recursion gate':false},run);
function fixture(t){
 const home=fs.mkdtempSync(path.join(os.tmpdir(),'coding-negative-report-'));
 t.after(()=>fs.rmSync(home,{recursive:true,force:true}));
 const project=path.join(home,'repo'),serviceState=path.join(home,'service'),stateDir=path.join(serviceState,'ccc1');
 fs.mkdirSync(path.join(serviceState,'public-evidence'),{recursive:true,mode:0o700});
 fs.mkdirSync(path.join(project,'.github','continuum'),{recursive:true});
 fs.mkdirSync(path.join(project,'lib'));
 fs.writeFileSync(path.join(project,'lib','offset.mjs'),'export function offset(a,b){return a-b;}\n');
 fs.writeFileSync(path.join(project,'.github','continuum','offset.test.mjs'),
  "import test from 'node:test';import assert from 'node:assert/strict';import {offset} from '../../lib/offset.mjs';test('offset',()=>assert.equal(offset(3,8),5));\n");
 const nativeSpawn=childProcess.spawnSync;
 const git=(...args)=>{
  const r=nativeSpawn('git',args,{cwd:project,encoding:'utf8',shell:false});
  assert.equal(r.status,0,r.stderr);return r.stdout.trim();
 };
 git('init','-q','-b','negative-report-fixture');git('add','.');
 git('-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','negative report fixture');
 const task={schema:'continuum-coding-task',version:1,id:'owner-negative-report',sourceCommit:git('rev-parse','HEAD'),
  kind:'BUGFIX',goal:'PRIVATE_OWNER_CANARY: repair offset symmetry',allowedPaths:['lib/offset.mjs'],
  acceptance:['offset(3,8) equals 5'],chain:'node',limits:{maxModelCalls:1,maxContextChars:6000}};
 const prepared=prepareCodingTask({project,stateDir,task});
 const profiles=new Map(['bot-forge','bot-lint','bot-echo'].map(agentId=>[agentId,{agentId,workspaceId:'workspace-'+agentId,
  resourceBudget:{maxModelCalls:1,deadlineMillis:120_000}}]));
 const assignment=createCcc1BotAssignment({task,ccc1TaskId:prepared.ccc1TaskId,
  builderId:'bot-forge',verifierId:'bot-lint',reporterId:'bot-echo',profileStore:{get:id=>profiles.get(id)}});
 let executionAttempts=0,providerCalls=0,onAttempt=()=>{};
 t.mock.method(childProcess,'spawnSync',(command,args,options)=>{
  if(command!=='docker')return nativeSpawn(command,args,options);
  assert.equal(args[0],'--host=unix:///var/run/docker.sock');
  if(args[1]==='image'&&args[2]==='inspect')return {status:0,stdout:IMAGE+'\n',stderr:''};
  if(args[1]==='run'){
   executionAttempts++;onAttempt();
   return {status:1,stdout:'',stderr:'Controlled unavailable runtime; no container started'};
  }
  if(args[1]==='container'&&args[2]==='inspect')
   return {status:1,stdout:'',stderr:'No such container: '+args[3]};
  assert.fail('Unexpected Docker operation: '+args.slice(1,3).join(' '));
 });
 syncBuiltinESMExports();
 t.after(()=>{t.mock.restoreAll();syncBuiltinESMExports();});
 const executor=createIsolatedExecutor({image:IMAGE,nodeHome:path.dirname(path.dirname(fs.realpathSync(process.execPath)))});
 const journal=DurableTaskJournal.forTask(stateDir,prepared.ccc1TaskId,runtimeKey(stateDir));
 const run=()=>runCodingTask({project,stateDir,id:task.id,executor,executorAbortMode:'CHECKPOINT_BOUNDARY_ONLY',
  requireLocalModelObservation:true,botAssignment:assignment,technicalRecallEnabled:false,env:{},
  providerFactory:({task:jobTask})=>Object.assign(()=>{providerCalls++;throw Error('UNEXPECTED_INFERENCE');},
   {taskSpec:jobTask,allowedPaths:jobTask.allowedPaths})});
 return {home,project,stateDir,serviceState,task,prepared,profiles,assignment,executor,journal,run,git,
  counts:()=>({executionAttempts,providerCalls}),onAttempt:callback=>{onAttempt=callback;}};
}
async function rejection(run){
 try{await run();assert.fail('Strict task must reject without verified model execution');}
 catch(error){assert.equal(error.message,'CODING_LOCAL_MODEL_OBSERVATION_REQUIRED');return error;}
}
function serviceAttempt(f){
 const delivery={id:f.task.id,ccc1TaskId:f.prepared.ccc1TaskId,fingerprint:f.prepared.fingerprint};
 const config={project:f.project,sourceRoot:f.project,sourceCommit:f.task.sourceCommit,stateDir:f.serviceState};
 return ()=>runCodingServiceTask({config,delivery,current:{task:f.task,state:f.stateDir},
  assignment:f.assignment,executor:f.executor,env:{}});
}

repairIntegrationTest('strict observation failure exposes the canonical negative Echo report without learning',async t=>{
 const f=fixture(t),error=await rejection(f.run);
 assert.equal(taskState(f.journal.entries()).state,'ABSTAINED');
 assert.equal(error.botReport?.status,'ABSTAINED');
 const bytes=fs.readFileSync(error.botReport.path),body=JSON.parse(bytes);
 assert.equal(sha256Hex(bytes),error.botReport.sha256);
 assert.equal(body.reporterId,'bot-echo');assert.equal(body.interpretation,'NO_CONFIRMED_REPAIR');
 assert.equal(body.journalHead,f.journal.entries().at(-1).hash);
 assert.equal(body.modelCalls,0);assert.equal(body.modelInferenceAttestedByReport,false);
 assert.equal(body.deviceVerified,false);assert.equal(body.approvedForMerge,false);
 assert.ok(body.receipts.length>0);assert.ok(body.receipts.every(r=>r.verdict!=='CONFIRMED'));
 assert.ok(!bytes.toString().includes('PRIVATE_OWNER_CANARY'));
 assert.equal(fs.existsSync(path.join(f.stateDir,'experience.jsonl')),false);
 assert.deepEqual(f.counts(),{executionAttempts:1,providerCalls:0});
 assert.equal(f.git('status','--porcelain'),'');
});

repairIntegrationTest('resuming a negative task reuses its report without inference, execution or journal changes',async t=>{
 const f=fixture(t),first=await rejection(f.run),journal=fs.readFileSync(f.journal.file);
 const bytes=fs.readFileSync(first.botReport.path),second=await rejection(f.run);
 assert.deepEqual(second.botReport,first.botReport);
 assert.deepEqual(fs.readFileSync(second.botReport.path),bytes);
 assert.deepEqual(fs.readFileSync(f.journal.file),journal);
 assert.deepEqual(f.counts(),{executionAttempts:1,providerCalls:0});
 assert.equal(fs.existsSync(path.join(f.stateDir,'experience.jsonl')),false);
});

repairIntegrationTest('a reporter removed during a failed attempt cannot receive a canonical report',async t=>{
 const f=fixture(t);f.onAttempt(()=>f.profiles.delete('bot-echo'));
 const error=await rejection(f.run);
 assert.deepEqual(error.botReport,{status:'BLOCKED',reason:'BOT_ASSIGNMENT_PROFILE_UNAVAILABLE',modelCalls:0});
 assert.equal(fs.existsSync(path.join(f.stateDir,'coding-evidence',f.task.id,'bot-report-'+f.assignment.sha256+'.json')),false);
 assert.equal(fs.existsSync(path.join(f.stateDir,'experience.jsonl')),false);
});

async function completeLegacyRepair(f){
 const completed=await runCodingTask({project:f.project,stateDir:f.stateDir,id:f.task.id,technicalRecallEnabled:false,env:{},
  providerFactory:({task})=>createCodingCandidateProvider({root:f.project,task,env:{},run:()=>({status:0,stdout:JSON.stringify({
   mode:'LOCAL_CODING_PATCH_PROPOSAL_V1',trusted:false,autoApply:false,proposal:{plan:['Correct the numeric fixture'],
    hypothesis:'Offset ordering is reversed',patches:[{file:'lib/offset.mjs',oldText:'a-b',newText:'a>b?a-b:b-a'}]}})})})});
 assert.equal(completed.status,'PATCH_PENDING_OWNER_REVIEW');
}
repairIntegrationTest('strict replay of an older completed repair cannot publish success without a current model observation',async t=>{
 const f=fixture(t);await completeLegacyRepair(f);
 const journal=fs.readFileSync(f.journal.file),experience=fs.readFileSync(path.join(f.stateDir,'experience.jsonl'));
 const error=await rejection(f.run);
 assert.deepEqual(error.botReport,{status:'BLOCKED',reason:'CODING_UNVERIFIED_SUCCESS_REPORT_BLOCKED',modelCalls:0});
 assert.equal(fs.existsSync(path.join(f.stateDir,'coding-evidence',f.task.id,'bot-report-'+f.assignment.sha256+'.json')),false);
 assert.deepEqual(fs.readFileSync(f.journal.file),journal);
 assert.deepEqual(fs.readFileSync(path.join(f.stateDir,'experience.jsonl')),experience);
 assert.deepEqual(f.counts(),{executionAttempts:0,providerCalls:0});
});

repairIntegrationTest('a competing writer cannot turn a strict observation failure into an older success report',async t=>{
 const f=fixture(t);await completeLegacyRepair(f);
 const journal=fs.readFileSync(f.journal.file),lock=new WriterLock(f.stateDir,f.journal.entries()[0].worktreeId);
 assert.equal(lock.acquire().acquired,true);
 try{
  const error=await rejection(f.run);
  assert.deepEqual(error.botReport,{status:'BLOCKED',reason:'CODING_NEGATIVE_REPORT_UNAVAILABLE',modelCalls:0});
  assert.equal(codingTaskFailureResult(error),null);
  assert.equal(fs.existsSync(path.join(f.stateDir,'coding-evidence',f.task.id,'bot-report-'+f.assignment.sha256+'.json')),false);
  assert.deepEqual(fs.readFileSync(f.journal.file),journal);
  assert.deepEqual(f.counts(),{executionAttempts:0,providerCalls:0});
 }finally{lock.release();}
});

test('service attempt persists the canonical negative report for owner lookup while rejecting',async t=>{
 const f=fixture(t),error=await rejection(serviceAttempt(f));
 const file=path.join(f.serviceState,'public-evidence',f.task.id+'.json');
 assert.equal(fs.existsSync(file),true,'A rejected canonical task still needs its owner-report lookup record');
 const {metricsMac,...snapshot}=JSON.parse(fs.readFileSync(file));
 assert.equal(createHmac('sha256',runtimeKey(f.stateDir)).update(canonical(snapshot)).digest('hex'),metricsMac);
 assert.equal(snapshot.resultStatus,'ABSTAINED');assert.equal(snapshot.taskFingerprint,f.prepared.fingerprint);
 assert.equal(snapshot.reportFile,path.basename(error.botReport.path));assert.equal(snapshot.reportSha256,error.botReport.sha256);
 assert.equal(snapshot.journalHead,f.journal.entries().at(-1).hash);
 assert.equal(snapshot.modelExecution.verifiedCalls,0);assert.equal(snapshot.modelExecution.identity,null);
 assert.equal(snapshot.technicalExperienceStatus,'NOT_RECORDED');assert.equal(snapshot.deviceVerified,false);
 assert.equal(snapshot.approvedForMerge,false);assert.equal(fs.statSync(file).mode&0o777,0o600);
 assert.equal(fs.existsSync(path.join(f.stateDir,'experience.jsonl')),false);
 assert.equal(codingTaskFailureResult(error).botReport,error.botReport,'The original admitted error is rethrown');
});

test('provider-shaped and JSON-copied errors cannot acquire canonical failure evidence',async t=>{
 const f=fixture(t),error=await rejection(f.run),genuine=codingTaskFailureResult(error);
 assert.equal(genuine.botReport,error.botReport);
 const copied=Object.assign(Error(error.message),JSON.parse(JSON.stringify(error)));
 assert.equal(codingTaskFailureResult(copied),null);
 assert.equal(codingTaskFailureResult({...error}),null);
 error.botReport={status:'COMPLETED',path:'/private/untrusted',sha256:'f'.repeat(64)};
 assert.equal(codingTaskFailureResult(error).botReport.status,'ABSTAINED');
 assert.throws(()=>{genuine.status='COMPLETED';},TypeError);
});

test('failure evidence persistence cannot replace the model error or overwrite a prior record',async t=>{
 const f=fixture(t),file=path.join(f.serviceState,'public-evidence',f.task.id+'.json');
 const previous=Buffer.from('{"priorEvidence":"must remain unchanged"}\n');
 fs.writeFileSync(file,previous,{mode:0o600});
 const error=await rejection(serviceAttempt(f));
 assert.deepEqual(fs.readFileSync(file),previous);
 assert.equal(codingTaskFailureResult(error).botReport.status,'ABSTAINED');
});

async function ownerReader(t,f,error){
 // Source binding is exercised against a clean exact-byte copy of these modules.
 // Model files are never opened; only the read-only owner evidence path runs here.
 const sourceRoot=fileURLToPath(new URL('../..',import.meta.url)),trustedRoot=path.join(f.home,'trusted-source');
 fs.mkdirSync(trustedRoot);
 for(const name of ['.github','scripts','tools'])fs.cpSync(path.join(sourceRoot,name),path.join(trustedRoot,name),{recursive:true});
 for(const args of [['init','-q','-b','owner-evidence-fixture'],['add','.'],
   ['-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','exact source evidence fixture']]){
  const r=childProcess.spawnSync('git',args,{cwd:trustedRoot,encoding:'utf8',shell:false});assert.equal(r.status,0,r.stderr);
 }
 const profiles=path.join(f.home,'profiles'),classes=path.join(f.home,'classes');
 fs.mkdirSync(profiles);fs.mkdirSync(classes);
 fs.writeFileSync(path.join(classes,'ContractOnly.class'),'external profile-port fixture; no JVM execution');
 const manifest=path.join(f.home,'model-manifest.json'),runtimeEnvPath=path.join(f.home,'runtime.json'),isolationConfig=path.join(f.home,'isolation.json');
 fs.writeFileSync(manifest,'{"contractFixture":true}');fs.writeFileSync(isolationConfig,'{}');
 const pin=LOCAL_MODEL_RUNTIME_CANDIDATES[0];
 fs.writeFileSync(runtimeEnvPath,JSON.stringify({CONTINUUM_LOCAL_BRAIN_MODEL:pin.modelRepository,
  CONTINUUM_LOCAL_BRAIN_MODEL_PATH:path.join(f.home,'not-loaded.gguf'),CONTINUUM_LOCAL_BRAIN_MODEL_MANIFEST:manifest,
  CONTINUUM_LOCAL_BRAIN_MODEL_FORMAT:'gguf',CONTINUUM_LOCAL_BRAIN_MODEL_SHA256:pin.modelSha256,
  CONTINUUM_LOCAL_BRAIN_RUNTIME:'llama.cpp',CONTINUUM_LOCAL_BRAIN_RUNTIME_KIND:'LLAMA_CPP',
  CONTINUUM_LOCAL_BRAIN_RUNTIME_PATH:path.join(f.home,'not-run'),CONTINUUM_LOCAL_BRAIN_RUNTIME_SHA256:pin.runtimeSha256,
  CONTINUUM_ALLOW_ALTERNATE_CODING_MODEL:'OWNER_APPROVED_ALTERNATE_GGUF'}));
 const {initializeCodingService,loadCodingServiceConfig,saveDeliveries}=await import(pathToFileURL(path.join(trustedRoot,'.github/continuum/coding-service-store.mjs')));
 const configPath=path.join(f.serviceState,'config.json');
 initializeCodingService({configPath,project:f.project,profileDirectory:profiles,profileClasspath:classes,runtimeEnvPath,isolationConfig});
 saveDeliveries(loadCodingServiceConfig(configPath),[{id:f.task.id,fingerprint:f.prepared.fingerprint,reviewSha256:'b'.repeat(64),
  ccc1TaskId:f.prepared.ccc1TaskId,delivery:'UNKNOWN',failureCode:error.message}]);
 const previousSpawn=childProcess.spawnSync;
 t.mock.method(childProcess,'spawnSync',(command,args,options)=>{
  if(command!=='java')return previousSpawn(command,args,options);
  assert.deepEqual(args.slice(0,6),['-Xmx128m','-cp',classes,'at.heinz.deviceai.chief.HandoffHostChecks','get',profiles]);
  return {status:0,stdout:JSON.stringify(f.profiles.get(args[6])??null),stderr:''};
 });
 syncBuiltinESMExports();
 const {createCodingServiceReader}=await import(pathToFileURL(path.join(trustedRoot,'.github/continuum/coding-service-reader.mjs')));
 return createCodingServiceReader({configPath});
}

test('owner lookup offers only the checked negative report for an unknown delivery',async t=>{
 const f=fixture(t),error=await rejection(serviceAttempt(f)),reader=await ownerReader(t,f,error);
 const item=reader.listTasks().items[0];
 assert.equal(item.delivery,'UNKNOWN');assert.equal(item.state,'ABSTAINED');
 assert.equal(item.sourceCurrent,true);assert.deepEqual(item.evidenceIds,['report']);
 const report=reader.getEvidence({taskId:f.task.id,evidenceId:'report'});
 assert.equal(report.content.status,'ABSTAINED');assert.equal(report.content.interpretation,'NO_CONFIRMED_REPAIR');
 assert.equal(report.content.modelInferenceAttestedByReport,false);assert.equal(report.content.approvedForMerge,false);
 assert.throws(()=>reader.getEvidence({taskId:f.task.id,evidenceId:'result'}),/SERVICE_METRICS_INVALID/);
 f.profiles.delete('bot-echo');
 assert.throws(()=>reader.getEvidence({taskId:f.task.id,evidenceId:'report'}),/SERVICE_PROFILE_UNAVAILABLE/);
});
