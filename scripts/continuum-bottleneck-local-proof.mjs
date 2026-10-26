/** Explicit owner research command. Real existing Docker/CCC1, synthetic dev tasks only.
 * No neural training, owner memory, production source changes or external models.
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {pathToFileURL} from 'node:url';
import {fileURLToPath} from 'node:url';
import {ensureCcc1StateOutsideSource} from '../.github/continuum/ccc1-path-policy.mjs';
import {createIsolatedExecutor,loadIsolationConfig} from '../.github/continuum/isolated-executor.mjs';
import {prepareCodingTask} from '../.github/continuum/coding-orchestrator.mjs';
import {validateCodingTask} from '../.github/continuum/coding-task.mjs';
import {CoinWmV3ShadowModel} from '../.github/continuum/coin-wm-v3-shadow-model.mjs';
import {induceStructuredStatementOperator} from '../.github/continuum/structured-statement-translator.mjs';
import {diagnoseCcc1BottleneckCase} from '../.github/continuum/ccc1-bottleneck-diagnosis.mjs';
import {buildCcc1BottleneckScorecard} from '../.github/continuum/ccc1-bottleneck-scorecard.mjs';
import {runIndependentCcc1PatchVerification} from '../.github/continuum/ccc1-independent-verifier.mjs';

const sha=x=>crypto.createHash('sha256').update(x).digest('hex');
const cases=[{
 id:'sort',source:'function sortVisibleCatalog(items) { return items.sort((a, b) => a - b); }',
 fixed:'function sortVisibleCatalog(items) { return [...items].sort((a, b) => a - b); }',
 train:'function rankScores(scores) { return scores.sort((a, b) => a - b); }',
 trainFixed:'function rankScores(scores) { return [...scores].sort((a, b) => a - b); }',
 goal:'Sort numbers without changing the caller array.',
 tests:`test('sort order',()=>assert.deepEqual(Array.from(fn([3,1,2])),[1,2,3]));
test('preserve caller',()=>{const input=[3,1,2];fn(input);assert.deepEqual(input,[3,1,2]);});`,
 oracle:`test('oracle frozen negative duplicate numbers',()=>assert.deepEqual(Array.from(fn(Object.freeze([4,-2,4,0]))),[-2,0,4,4]));
test('oracle empty and singleton',()=>{assert.deepEqual(Array.from(fn([])),[]);const xs=[9];assert.notEqual(fn(xs),xs);});`
},{
 id:'pagination',source:'function selectTelemetryWindow(packets, batchNo, width) { const start = batchNo * width; return packets.slice(start, start + width); }',
 fixed:'function selectTelemetryWindow(packets, batchNo, width) { const start = (batchNo - 1) * width; return packets.slice(start, start + width); }',
 train:'function paginateCatalog(products, pageNo, pageSize) { const start = pageNo * pageSize; return products.slice(start, start + pageSize); }',
 trainFixed:'function paginateCatalog(products, pageNo, pageSize) { const start = (pageNo - 1) * pageSize; return products.slice(start, start + pageSize); }',
 goal:'Use one-based page numbers and preserve the caller array.',
 tests:`test('first page',()=>assert.deepEqual(fn([1,2,3,4],1,2),[1,2]));
test('second page',()=>assert.deepEqual(fn([1,2,3,4],2,2),[3,4]));`,
 oracle:`test('oracle renamed domain and partial last page',()=>assert.deepEqual(fn(['a','b','c','d','e'],3,2),['e']));
test('oracle end and immutable input',()=>{const xs=Object.freeze([1,2]);assert.deepEqual(fn(xs,5,2),[]);assert.deepEqual(fn(xs,1,1),[1]);});`
}];

function git(root,...args){
 const r=spawnSync('git',args,{cwd:root,encoding:'utf8',timeout:15000,shell:false});
 if(r.status!==0)throw Error('FIXTURE_GIT_FAILED: '+r.stderr.slice(0,300));
 return r.stdout.trim();
}

export function runDevelopmentBottleneckProof({isolationConfig,evidenceDir}={}){
 if(typeof evidenceDir!=='string'||!path.isAbsolute(evidenceDir)||fs.existsSync(evidenceDir))
  throw Error('NEW_EXTERNAL_EVIDENCE_DIRECTORY_REQUIRED');
 // Explicit config uses the existing validator and authentic branded executor.
 const source=fileURLToPath(new URL('..',import.meta.url));
 const output=ensureCcc1StateOutsideSource(source,evidenceDir);
 if(output!==path.resolve(evidenceDir))throw Error('CANONICAL_EVIDENCE_DIRECTORY_REQUIRED');
 const executor=createIsolatedExecutor(loadIsolationConfig(isolationConfig,{sourceRoot:source}));
 fs.mkdirSync(output,{mode:0o700});
 const operators=cases.map(c=>induceStructuredStatementOperator(c.id,c.train,c.trainFixed));
 const model=new CoinWmV3ShadowModel(operators.length,20261026);
 const results=[];
 for(const [index,c] of cases.entries()){
  const temporary=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-abc-proof-'));
  const project=path.join(temporary,'repo'),state=path.join(temporary,'state');
  fs.mkdirSync(path.join(project,'src'),{recursive:true});
  fs.mkdirSync(path.join(project,'.github/continuum'),{recursive:true});
  const file='src/'+c.id+'.mjs',fnName=/^function (\w+)/.exec(c.source)[1];
  const header=`import test from 'node:test';import assert from 'node:assert/strict';import fs from 'node:fs';import vm from 'node:vm';
const code=fs.readFileSync(new URL('../../${file}',import.meta.url),'utf8');
const fn=new vm.Script(code+';${fnName}').runInNewContext();\n`;
  try{
   fs.writeFileSync(path.join(project,file),c.source);
   fs.writeFileSync(path.join(project,'.github/continuum/regression.test.mjs'),header+c.tests+'\n');
   fs.writeFileSync(path.join(project,'.github/continuum/independent-oracle.test.mjs'),header+c.oracle+'\n');
   git(project,'init','-q','-b','research-development-fixture');git(project,'add','.');
   git(project,'-c','user.name=Continuum fixture','-c','user.email=fixture@example.invalid','commit','-qm','sealed public development source and two test profiles');
   git(project,'bundle','create',path.join(output,c.id+'-source.bundle'),'HEAD');
   const task=validateCodingTask({schema:'continuum-coding-task',version:1,id:'owner-development-'+c.id,
    sourceCommit:git(project,'rev-parse','HEAD'),kind:'BUGFIX',goal:c.goal,allowedPaths:[file],
    acceptance:['Both regression and separately authored behavior oracle must pass in two fresh worktrees.'],
    chain:'node',limits:{maxModelCalls:1,maxContextChars:2048}});
   const grant=prepareCodingTask({project,stateDir:state,task});
   const testManifest=Object.fromEntries(['regression.test.mjs','independent-oracle.test.mjs']
    .map(name=>[name,sha(fs.readFileSync(path.join(project,'.github/continuum',name)))]));
   const diagnosis=diagnoseCcc1BottleneckCase({experimentId:'NATIVE_ABC_20261026',caseId:c.id,
    typedTask:task,source:c.source,file,referenceCode:c.fixed,operators,referenceOperatorIndex:index,
    model,state16:new Float64Array(16),modelCheckpointSha256:sha(JSON.stringify(model)),
    testManifestSha256:sha(JSON.stringify(testManifest))});
   const arms=[];
   for(const a of diagnosis.arms){
    const result=a.candidate?runIndependentCcc1PatchVerification({typedTask:task,candidate:a.candidate,
     file,projectRoot:project,stateDir:state,executor,deadline:Date.now()+120000}):null;
    arms.push({variant:a.variant,translationStatus:a.translationStatus,selectedOperatorId:a.selectedOperatorId,
     operatorAttempts:a.operatorAttempts,candidateSha256:a.candidateSha256,result});
    // Secure each actual run before starting the next one.
    fs.writeFileSync(path.join(output,c.id+'-'+a.variant+'.json'),JSON.stringify(arms.at(-1),null,2)+'\n',{flag:'wx',mode:0o600});
    if(a.variant==='REFERENCE_PATCH'&&result?.status!=='DUAL_WORKTREE_CHECKS_PASS_REVIEW_REQUIRED')break;
   }
   if(git(project,'status','--porcelain'))throw Error('OWNER_FIXTURE_DRIFT');
   const row={caseId:c.id,task,grant,testManifest,scorecard:buildCcc1BottleneckScorecard(diagnosis),arms,
    sourceUnchanged:true,modelUpdates:model.updatesCount};
   fs.writeFileSync(path.join(output,c.id+'-manifest.json'),JSON.stringify(row,null,2)+'\n',{flag:'wx',mode:0o600});
   results.push(row);
  }finally{fs.rmSync(temporary,{recursive:true,force:true});}
 }
 const report={schema:'continuum-native-development-proof',version:1,node:process.version,
  status:'DEVELOPMENT_MEASUREMENT_REVIEW_REQUIRED',isolation:executor.policy,results,
  sealedHoldout:false,model:'ACTUAL_NTW1_UNTRAINED_ZERO_FEATURE_STATE',realLanguageModelInference:false,
  trainingPerformed:false,learningEligible:false,ownerMemoryWritten:false};
 fs.writeFileSync(path.join(output,'summary.json'),JSON.stringify(report,null,2)+'\n',{flag:'wx',mode:0o600});
 return report;
}

if(process.argv[1]&&import.meta.url===pathToFileURL(path.resolve(process.argv[1])).href){
 if(process.argv[2]!=='--owner-approved-development-fixtures'||process.argv.length!==5)
  throw Error('Usage: --owner-approved-development-fixtures /absolute/isolation.json /new/external/evidence-dir');
 const r=runDevelopmentBottleneckProof({isolationConfig:process.argv[3],evidenceDir:process.argv[4]});
 console.log(JSON.stringify(r));
}
