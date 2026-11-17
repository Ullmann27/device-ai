/** Explicit owner research, one fresh synthetic task, canonical pipeline only. */
import fs from 'node:fs';import path from 'node:path';import crypto from 'node:crypto';import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {prepareCodingTask,runCodingTask} from '../.github/continuum/coding-orchestrator.mjs';
import {createHarnessCodingCandidateProvider,inspectHarnessRuntime} from '../.github/continuum/harness-coding-provider.mjs';
import {createIsolatedExecutor,loadIsolationConfig} from '../.github/continuum/isolated-executor.mjs';
import {ensureCcc1StateOutsideSource} from '../.github/continuum/ccc1-path-policy.mjs';
import {runIndependentCcc1PatchVerification} from '../.github/continuum/ccc1-independent-verifier.mjs';
import {taskFingerprint} from '../.github/continuum/coding-task.mjs';
const source=fileURLToPath(new URL('..',import.meta.url)),sha=b=>crypto.createHash('sha256').update(b).digest('hex');
if(process.argv.length!==5)throw Error('Usage: node scripts/continuum-harness-coding-proof.mjs /NEW/external/evidence /isolation.json /reviewed/harness-runtime');
const out=ensureCcc1StateOutsideSource(source,path.resolve(process.argv[2]));
const runtimeDirectory=process.argv[4],runtime=inspectHarnessRuntime(runtimeDirectory);
const executor=createIsolatedExecutor(loadIsolationConfig(process.argv[3],{sourceRoot:source}));
const fixtureBytes=fs.readFileSync(new URL('../experimental/harness-fabric-20261027/coding-fixture.json',import.meta.url)),fixture=JSON.parse(fixtureBytes);
fs.mkdirSync(out,{mode:0o700});const root=path.join(out,'source'),stateDir=path.join(out,'state');
fs.mkdirSync(path.join(root,'lib'),{recursive:true});fs.mkdirSync(path.join(root,'.github/continuum'),{recursive:true});
fs.writeFileSync(path.join(root,'lib/ledger.mjs'),fixture.source);
fs.writeFileSync(path.join(root,'.github/continuum/regression.test.mjs'),fixture.regression);
fs.writeFileSync(path.join(root,'.github/continuum/independent.test.mjs'),fixture.independentOracle);
function git(...args){const r=spawnSync('git',args,{cwd:root,encoding:'utf8',timeout:15000});if(r.status!==0)throw Error('FIXTURE_GIT_FAILED');return r.stdout.trim();}
git('init','-q','-b','research/harness-signed-adjustment');git('add','.');git('-c','user.name=Continuum research','-c','user.email=fixture@example.invalid','commit','-qm','Freeze original fault and independent oracle before inference');
const task={schema:'continuum-coding-task',version:1,id:'harness-signed-adjustment-20261027',sourceCommit:git('rev-parse','HEAD'),kind:'BUGFIX',
 goal:'Apply a signed integer ledger adjustment to the balance correctly; preserve existing safe-integer input rejection.',allowedPaths:['lib/ledger.mjs'],
 acceptance:['Positive adjustments increase the balance; negative adjustments decrease it.','Zero preserves balance.','Non-safe-integer input must still throw RangeError.'],chain:'node',limits:{maxModelCalls:2,maxContextChars:4096}};
fs.writeFileSync(path.join(out,'task.json'),JSON.stringify(task,null,2)+'\n');git('bundle','create',path.join(out,'source.bundle'),'HEAD');
const prepared=prepareCodingTask({project:root,stateDir,task});let proposals=[];
const started=Date.now();
const result=await runCodingTask({project:root,stateDir,id:task.id,executor,executorAbortMode:'CHECKPOINT_BOUNDARY_ONLY',requireIsolation:true,
 expectedTaskFingerprint:taskFingerprint(task),providerFactory:options=>{
 const provider=createHarnessCodingCandidateProvider({...options,runtimeDirectory});
 const observed=input=>{const value=provider(input);proposals.push(value);return value;};
 for(const key of ['taskSpec','allowedPaths','taskFingerprint'])Object.defineProperty(observed,key,{value:provider[key]});return observed;
}});
let independent={status:'NOT_RUN_NO_VERIFIED_MODEL_CANDIDATE'};
if(result.status==='PATCH_PENDING_OWNER_REVIEW'&&proposals.length){
 const proposal=proposals.at(-1);let code=fixture.source;
 for(const p of proposal.patches){if(p.file!=='lib/ledger.mjs'||code.split(p.oldText).length!==2)throw Error('EXACT_MODEL_PATCH_BINDING_FAILED');code=code.replace(p.oldText,p.newText);}
 independent=runIndependentCcc1PatchVerification({typedTask:task,file:'lib/ledger.mjs',projectRoot:root,stateDir,executor,deadline:Date.now()+120000,
  candidate:{status:'CANDIDATE_UNVERIFIED',sourceCommit:task.sourceCommit,taskSha256:taskFingerprint(task),sourceSha256:sha(fixture.source),candidateSha256:sha(code),code}});
 for(const r of result.receipts??[])if(r.patch?.path)fs.copyFileSync(r.patch.path,path.join(out,'model-generated-review.patch'));
}
const report={schema:'continuum-harness-native-coding-proof-v1',implementationCommit:spawnSync('git',['rev-parse','HEAD'],{cwd:source,encoding:'utf8'}).stdout.trim(),
 fixtureSha256:sha(fixtureBytes),task,prepared,harness:runtime,configuredModel:process.env.CONTINUUM_LOCAL_BRAIN_MODEL,modelSha256:process.env.CONTINUUM_CODING_MODEL_SHA256??null,
 elapsedMs:Date.now()-started,proposals,result,independent,sourceUnchanged:git('status','--porcelain')===''&&fs.readFileSync(path.join(root,'lib/ledger.mjs'),'utf8')===fixture.source,
 externalInference:false,modelWeightsUpdated:false,productionApproved:false};
fs.writeFileSync(path.join(out,'result.json'),JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify({status:result.status,independent:independent.status,proposals:proposals.length,sourceUnchanged:report.sourceUnchanged,elapsedMs:report.elapsedMs}));
if(result.status!=='PATCH_PENDING_OWNER_REVIEW'||independent.status!=='DUAL_WORKTREE_CHECKS_PASS_REVIEW_REQUIRED')process.exitCode=1;
