// Adapted from existing QA PR413 at 10f4339a760f8a531993598200b1c3299d3b2b97; reviewer-only new three-case A0.
// Independent acceptance driver. No injected provider, inferred answer or patch.
// Usage: node run-real-model-task.mjs SOURCE FULL_SHA FIXTURE_REPO NEW_EVIDENCE_DIR [--preflight]
import './verify-g04-freeze.mjs';
import {acceptanceClauses} from './task-contract.mjs';
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import {pathToFileURL,fileURLToPath} from 'node:url';

const digest=b=>createHash('sha256').update(b).digest('hex');
const [sourceArg,expectedSha,projectArg,outArg,mode]=process.argv.slice(2);
assert.match(expectedSha??'',/^[a-f0-9]{40}$/,'Full immutable source SHA required');
for(const p of [sourceArg,projectArg,outArg])assert.ok(path.isAbsolute(p??''),'Absolute paths required');
assert.ok(mode===undefined||mode==='--preflight'||mode==='--recall','Unknown mode');
const source=fs.realpathSync(sourceArg),project=fs.realpathSync(projectArg),out=path.resolve(outArg);
assert.ok(out!==source&&!out.startsWith(source+path.sep)&&out!==project&&!out.startsWith(project+path.sep),'Evidence must be outside source');
const binding=JSON.parse(fs.readFileSync(project+'.task.json','utf8'));
assert.match(binding.fixture,/^g04$/);
const ownRoot=path.dirname(fileURLToPath(import.meta.url));
assert.equal(digest(fs.readFileSync(path.join(ownRoot,'future/FROZEN-MANIFEST.json'))),'4df194f5f8d5ad7957bc050291aadd37327dc6fbccd6547fcb67a4985a5a9ec0');
const frozen=path.join(ownRoot,'future',binding.fixture);
const frozenMetadata=JSON.parse(fs.readFileSync(path.join(frozen,'task.json'),'utf8'));
const frozenTest=fs.readFileSync(path.join(frozen,'acceptance.fixture'),'utf8');
const materializedTest=path.join(project,'.github/continuum/frozen.test.mjs');
assert.equal(digest(fs.readFileSync(path.join(frozen,'subject.source'))),binding.sourceSha256);
assert.equal(digest(frozenTest),binding.frozenTestSha256);
assert.equal(fs.readFileSync(materializedTest,'utf8'),frozenTest);
assert.equal(digest(fs.readFileSync(materializedTest)),binding.materializedTestSha256);
assert.equal(digest(fs.readFileSync(path.join(project,'lib/subject.mjs'))),binding.sourceSha256);
function git(cwd,...args){const r=spawnSync('git',['-C',cwd,...args],{encoding:'utf8'});assert.equal(r.status,0,r.stderr);return r.stdout.trim();}
assert.equal(git(source,'rev-parse','HEAD'),expectedSha);
assert.equal(git(source,'status','--porcelain'),'','Integrated source must be clean');
assert.equal(git(project,'rev-parse','HEAD'),binding.task.sourceCommit);
assert.equal(git(project,'status','--porcelain'),'','Fixture must be clean');
const moduleAt=relative=>import(pathToFileURL(path.join(source,relative)).href);
const {validateCodingTask,taskFingerprint}=await moduleAt('.github/continuum/coding-task.mjs');
const task=validateCodingTask(binding.task);
assert.equal(task.id,'genesis-'+binding.fixture);
assert.equal(task.kind,'BUGFIX');
assert.equal(task.goal,frozenMetadata.requirements[0]);
assert.deepEqual(task.acceptance,acceptanceClauses(frozenMetadata.requirements));
assert.deepEqual(task.limits,{maxModelCalls:1,maxContextChars:8192});
assert.deepEqual(task.allowedPaths,['lib/subject.mjs']);
assert.equal(task.chain,'node');
const fingerprint=taskFingerprint(task),stateDir=path.join(out,'private-synthetic-state');
const recallControl='off';
const seedIdentity=null;
assert.ok(!process.env.MOMEGA_QA_INHERITED_STATE,'Inherited context forbidden in A0');
assert.ok(!process.env.MOMEGA_QA_TECHNICAL_RECALL || process.env.MOMEGA_QA_TECHNICAL_RECALL==='off','A0 recall is off');
if(mode==='--recall'){
 const {recallTechnicalExperiences}=await moduleAt('.github/continuum/ccc1-technical-recall.mjs');
 const {runtimeKey}=await moduleAt('.github/continuum/ccc1-journal.mjs');
 const {repositoryOf}=await moduleAt('.github/continuum/ccc1-worker.mjs');
 console.log(JSON.stringify(recallTechnicalExperiences({stateDir,task,project,repoFullName:repositoryOf(project),key:runtimeKey(stateDir)})));
 process.exit(0);
}
assert.ok(!fs.existsSync(out),'New evidence directory required; never silently rerun a consumed task');
fs.mkdirSync(out,{recursive:true,mode:0o700});
const write=(name,value)=>fs.writeFileSync(path.join(out,name),typeof value==='string'?value:JSON.stringify(value,null,2)+'\n',{mode:0o600});
const configKeys=['CONTINUUM_LOCAL_BRAIN_MODEL','CONTINUUM_LOCAL_BRAIN_RUNTIME','CONTINUUM_LOCAL_BRAIN_MODEL_FORMAT',
 'CONTINUUM_LOCAL_BRAIN_MODEL_SHA256','CONTINUUM_ALLOW_ALTERNATE_CODING_MODEL','CONTINUUM_LOCAL_BRAIN_MODEL_PATH',
 'CONTINUUM_LOCAL_BRAIN_MODEL_MANIFEST','CONTINUUM_LOCAL_BRAIN_RUNTIME_PATH','CONTINUUM_LOCAL_BRAIN_RUNTIME_SHA256',
 'CONTINUUM_LOCAL_BRAIN_RUNTIME_KIND','CONTINUUM_CODING_ISOLATION_CONFIG','GENESIS_PROFILE_STORE','GENESIS_HANDOFF_CLASSPATH'];
const configuration=Object.fromEntries(configKeys.filter(k=>process.env[k]!==undefined).map(k=>[k,process.env[k]]));
const fileHashes={};
for(const key of ['CONTINUUM_LOCAL_BRAIN_MODEL_PATH','CONTINUUM_LOCAL_BRAIN_RUNTIME_PATH','CONTINUUM_LOCAL_BRAIN_MODEL_MANIFEST','CONTINUUM_CODING_ISOLATION_CONFIG']){
 if(configuration[key])fileHashes[key]=digest(fs.readFileSync(configuration[key]));
}
const before={integratedSource:expectedSha,integratedTree:git(source,'rev-parse','HEAD^{tree}'),
 qaRunnerSha256:digest(fs.readFileSync(fileURLToPath(import.meta.url))),fixture:binding,
 taskFingerprint:fingerprint,fixtureTree:git(project,'rev-parse','HEAD^{tree}'),
 freezeManifestSha256:digest(fs.readFileSync(path.join(ownRoot,'future/FROZEN-MANIFEST.json'))),
 configuration,fileHashes,technicalRecallEnabled:recallControl==='on',inheritedState:seedIdentity,
 nodeVersion:process.version,startedAt:new Date().toISOString(),
 deviceStatus:'FOLD7_NOT_RUN',modelAnswersSupplied:false};
write('before.json',before);
const baseline=spawnSync(process.execPath,['--test','--test-reporter=tap','.github/continuum/frozen.test.mjs'],{cwd:project,encoding:'utf8',timeout:15000});
write('baseline.tap',baseline.stdout??'');write('baseline.stderr',baseline.stderr??'');
write('baseline.exit.json',{status:baseline.status,signal:baseline.signal,error:baseline.error?.message??null});
assert.equal(baseline.status,1,'Baseline must actually be RED');
assert.match(baseline.stdout,/ERR_ASSERTION/,'Baseline must fail an assertion');
if(mode==='--preflight'){
 write('outcome.json',{status:'PREFLIGHT_ONLY',modelCalls:0,grantCreated:false});
 console.log(JSON.stringify({status:'PREFLIGHT_ONLY',fixture:binding.fixture,out}));
 process.exit(0);
}
// Environment configuration is explicit; default provider remains the actual implementation.
assert.equal(configuration.CONTINUUM_LOCAL_BRAIN_MODEL_SHA256,'cc324af070c2ecbfd324a30884d2f951a7ff756aba85cb811a6ec436933bb046');
assert.equal(fileHashes.CONTINUUM_LOCAL_BRAIN_MODEL_PATH,configuration.CONTINUUM_LOCAL_BRAIN_MODEL_SHA256);
assert.equal(fileHashes.CONTINUUM_LOCAL_BRAIN_RUNTIME_PATH,configuration.CONTINUUM_LOCAL_BRAIN_RUNTIME_SHA256);
assert.ok(fileHashes.CONTINUUM_CODING_ISOLATION_CONFIG,'Owner isolation configuration required');
// Reviewer-only experiment guard: reserve one call before any grant, once for frozen g04.
// This guards this finite test experiment, not production scheduling or authorization.
assert.equal(expectedSha,'c5f9b06b02fbe8b80f9472a17784fb000812398c');
const ledger='/workspace/genesis-g04-experiment/reservations';
fs.mkdirSync(ledger,{recursive:true,mode:0o700});
const reserved=fs.readdirSync(ledger).filter(x=>/^g04\.json$/.test(x));
assert.ok(reserved.length<1,'One-call g04 experiment budget exhausted');
fs.writeFileSync(path.join(ledger,binding.fixture+'.json'),JSON.stringify({fixture:binding.fixture,source:expectedSha,out,maximumReservedCalls:1,at:new Date().toISOString()})+'\n',{flag:'wx',mode:0o600});
const started=Date.now(),usage=process.resourceUsage();
let outcome;
try{
 const {prepareCodingTask,runCodingTask}=await moduleAt('.github/continuum/coding-orchestrator.mjs');
 const {ownerCodingExecutor}=await moduleAt('scripts/continuum-typed-coding-bridge.mjs');
 const executor=ownerCodingExecutor({project,env:process.env});
 const grant=prepareCodingTask({project,stateDir,task});write('grant.json',grant);
 assert.equal(grant.fingerprint,fingerprint);assert.equal(grant.replayed,false);
 const {createCcc1BotAssignment}=await moduleAt('.github/continuum/ccc1-bot-assignment.mjs');
 const {profileStoreReader}=await moduleAt('tools/genesis-workspaces/adapter-bridge.mjs');
 const profileDirectory=process.env.GENESIS_PROFILE_STORE;
 assert.ok(path.isAbsolute(profileDirectory??'')&&fs.realpathSync(profileDirectory)===profileDirectory);
 assert.ok(!profileDirectory.startsWith(project+path.sep)&&!profileDirectory.startsWith(source+path.sep));
 const profileStore=profileStoreReader(profileDirectory);
 const selected=['bot-atlas','bot-forge','bot-lint','bot-mnemos','bot-echo'];
 write('actual-profiles-before.json',selected.map(id=>profileStore.get(id)));
 const botAssignment=createCcc1BotAssignment({task,ccc1TaskId:grant.ccc1TaskId,builderId:'bot-forge',verifierId:'bot-lint',reporterId:'bot-echo',profileStore});
 write('assignment.json',botAssignment);
 const result=await runCodingTask({project,stateDir,id:task.id,executor,
  executorAbortMode:'CHECKPOINT_BOUNDARY_ONLY',requireIsolation:true,requireLocalModelObservation:true,
  expectedTaskFingerprint:fingerprint,technicalRecallEnabled:recallControl==='on',botAssignment,env:process.env});
 write('result.json',result);
 write('actual-profiles-after.json',selected.map(id=>profileStore.get(id)));
 outcome={status:'OBSERVED_RESULT',resultStatus:result.status,technicalExperienceStatus:result.technicalExperienceStatus,
  technicalRecallCount:result.technicalRecallCount,solved:'REQUIRES_INDEPENDENT_EVIDENCE_REVIEW'};
}catch(error){outcome={status:'FAILED',error:{name:error.name,message:error.message,code:error.code??null},solved:false};write('error.stack.txt',error.stack??String(error));}
// A fresh Node process exercises persisted read-only recall, never repeats inference.
if(outcome.resultStatus==='PATCH_PENDING_OWNER_REVIEW'&&outcome.technicalExperienceStatus==='RECORDED_TECHNICAL_REVIEW_ONLY'){
 const recall=spawnSync(process.execPath,[fileURLToPath(import.meta.url),source,expectedSha,project,out,'--recall'],{encoding:'utf8',timeout:15000});
 write('restart-recall.stdout',recall.stdout??'');write('restart-recall.stderr',recall.stderr??'');
 write('restart-recall.exit.json',{status:recall.status,signal:recall.signal,error:recall.error?.message??null});
}
if(!fs.existsSync(path.join(out,'restart-recall.exit.json')))write('restart-recall.status.json',{status:'NOT_RUN',reason:'No verified successful technical experience; g04 restart recall is conditional on success.'});
const inventory=[];
function walk(dir){if(!fs.existsSync(dir))return;for(const e of fs.readdirSync(dir,{withFileTypes:true})){
 const file=path.join(dir,e.name),relative=path.relative(stateDir,file);
 // Never export runtime keys, lock files or symlink targets. This is synthetic state only.
 if(e.name.startsWith('.')||/key|secret|credential/i.test(e.name))continue;
 if(e.isDirectory())walk(file);else if(e.isFile())inventory.push({path:relative,bytes:fs.statSync(file).size,sha256:digest(fs.readFileSync(file))});
}}
walk(stateDir);write('state-evidence-inventory.json',inventory);
const requests=inventory.filter(x=>x.path.startsWith('coding-evidence/'+task.id+'/')&&/(^|\/)request-\d+\.json$/.test(x.path)).map(x=>({path:x.path,...JSON.parse(fs.readFileSync(path.join(stateDir,x.path),'utf8'))}));
write('model-requests.json',requests);
const after={integratedSource:git(source,'rev-parse','HEAD'),integratedStatus:git(source,'status','--porcelain'),
 fixtureSource:git(project,'rev-parse','HEAD'),fixtureStatus:git(project,'status','--porcelain'),
 testSha256:digest(fs.readFileSync(materializedTest)),finishedAt:new Date().toISOString(),elapsedMs:Date.now()-started,
 runnerResourceUsageBefore:usage,runnerResourceUsageAfter:process.resourceUsage(),
 resourceScope:'Node driver only; child runtime and Docker limits are recorded by their existing receipts.'};
write('after.json',after);
outcome.modelCallsReserved=requests.length;
outcome.sourceIdentityStable=after.integratedSource===expectedSha&&after.integratedStatus===''&&after.fixtureSource===task.sourceCommit&&after.fixtureStatus===''&&after.testSha256===binding.materializedTestSha256;
write('outcome.json',outcome);console.log(JSON.stringify({...outcome,fixture:binding.fixture,out}));
if(outcome.status==='FAILED'||!outcome.sourceIdentityStable)process.exitCode=1;
