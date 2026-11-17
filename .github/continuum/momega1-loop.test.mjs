import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import * as verifier from './ccc1-redgreen-verifier.mjs';
import {prepareCodingTask,runCodingTask} from './coding-orchestrator.mjs';
import {createCodingCandidateProvider} from './coding-provider.mjs';
const hash=b=>createHash('sha256').update(b).digest('hex');
const archive=(name,value)=>{const dir=process.env.MOMEGA1_LOOP_EVIDENCE_DIR;if(dir){fs.mkdirSync(dir,{recursive:true});fs.writeFileSync(path.join(dir,name+'.json'),JSON.stringify(value,null,2)+'\n');}};
const git=(root,...args)=>{const r=spawnSync('git',args,{cwd:root,encoding:'utf8'});assert.equal(r.status,0,r.stderr);return r.stdout.trim();};
function fixture(t,{stateful=false,coverageLoss=false}={}){
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'momega-loop-'));t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
 const root=path.join(dir,'repo');fs.mkdirSync(path.join(root,'lib'),{recursive:true});fs.mkdirSync(path.join(root,'.github/continuum'),{recursive:true});git(root,'init','-q','-b','momega-fixture');
 fs.writeFileSync(path.join(root,'lib/offset.mjs'),'export function offset(a,b){return a-b;}\n');
 fs.writeFileSync(path.join(root,'.github/continuum/offset.test.mjs'),`import test from 'node:test';import assert from 'node:assert/strict';import fs from 'node:fs';import {offset} from '../../lib/offset.mjs';
 test('offset',()=>{const actual=offset(3,8);${stateful?`const source=fs.readFileSync('lib/offset.mjs','utf8');if(source.includes('return b-a;')&&!fs.existsSync('build/baseline-marker'))assert.fail('fresh check rejects state-dependent patch');if(actual<0){fs.mkdirSync('build',{recursive:true});fs.writeFileSync('build/baseline-marker','fixture');}`:''}assert.equal(actual,5);});
 ${coverageLoss?`if(offset(3,8)<0)test('retained',()=>assert.ok(true));`:''}`);
 git(root,'add','.');git(root,'-c','user.name=fixture','-c','user.email=fixture@invalid','commit','-qm','baseline');
 const task={schema:'continuum-coding-task',version:1,id:'momega-offset',sourceCommit:git(root,'rev-parse','HEAD'),kind:'BUGFIX',goal:'Repair offset symmetry',allowedPaths:['lib/offset.mjs'],acceptance:['offset(3,8) must be 5'],chain:'node',limits:{maxModelCalls:2,maxContextChars:6000}};
 const patchFor=text=>{fs.writeFileSync(path.join(root,'lib/offset.mjs'),text);const patch=spawnSync('git',['diff','--binary','--full-index'],{cwd:root}).stdout;git(root,'checkout','--','lib/offset.mjs');const patchPath=path.join(dir,'fix.patch');fs.writeFileSync(patchPath,patch);return {patchPath,patchSha256:hash(patch)};};
 return {dir,root,task,stateDir:path.join(dir,'state'),patchFor};
}
test('MΩ canonical fresh verifier executes RED and GREEN and brands only its exact original evidence',{skip:!!process.env.CONTINUUM_REPAIR_DEPTH},t=>{
 const f=fixture(t);assert.equal(typeof verifier.verifyCodingCandidate,'function');
 const result=verifier.verifyCodingCandidate({repo:f.root,task:f.task,...f.patchFor('export function offset(a,b){return a>b?a-b:b-a;}\n'),proposalSha256:'a'.repeat(64),out:path.join(f.dir,'verification'),deadline:Date.now()+60000});
 archive('fresh-verifier',{task:f.task,verification:result});
 assert.equal(result.verdict,'VERIFIED',JSON.stringify(result));assert.equal(result.red.results[0].exitCode,1);assert.equal(result.green.results[0].exitCode,0);
 assert.equal(result.red.results[0].tests.caseIdentities.length,1);assert.equal(result.green.results[0].tests.caseIdentities.length,1);
 assert.equal(verifier.isTrustedCodingVerification(result),true);assert.equal(verifier.isTrustedCodingVerification(structuredClone(result)),false);
 assert.equal(git(f.root,'status','--porcelain'),'');assert.equal(git(f.root,'worktree','list','--porcelain').match(/^worktree /gm).length,1);
});
test('MΩ canonical fresh verifier rejects removal of a baseline identity',{skip:!!process.env.CONTINUUM_REPAIR_DEPTH},t=>{
 const f=fixture(t,{coverageLoss:true});assert.equal(typeof verifier.verifyCodingCandidate,'function');
 const result=verifier.verifyCodingCandidate({repo:f.root,task:f.task,...f.patchFor('export function offset(a,b){return a>b?a-b:b-a;}\n'),proposalSha256:'a'.repeat(64),out:path.join(f.dir,'verification'),deadline:Date.now()+60000});
 archive('identity-loss',{task:f.task,verification:result});
 assert.equal(result.verdict,'REJECTED');assert.ok(result.reasons.some(r=>r.includes('BASELINE_TEST_IDENTITY_MISSING')),JSON.stringify(result));
});
test('MΩ independent counterexample revises inside the same canonical task and model-call budget',{skip:!!process.env.CONTINUUM_REPAIR_DEPTH},async t=>{
 const f=fixture(t,{stateful:true});prepareCodingTask({project:f.root,stateDir:f.stateDir,task:f.task});
 const observations=[];
 const result=await runCodingTask({project:f.root,stateDir:f.stateDir,id:f.task.id,providerFactory:({task})=>{
  const provider=createCodingCandidateProvider({root:f.root,task,env:{CONTINUUM_LOCAL_BRAIN_API_KEY:'a'.repeat(40)},run:(_,__,options)=>{
   observations.push(JSON.parse(options.input));const fix=observations.length===1?'b-a':'a>b?a-b:b-a';
   return {status:0,stdout:JSON.stringify({mode:'LOCAL_CODING_PATCH_PROPOSAL_V1',trusted:false,autoApply:false,proposal:{plan:['Repair symmetry'],hypothesis:'Correct order handling',patches:[{file:'lib/offset.mjs',oldText:'a-b',newText:fix}]}})};
  }});return provider;
 }});
 const reports=[];
 const collect=dir=>{for(const e of fs.readdirSync(dir,{withFileTypes:true})){const file=path.join(dir,e.name);if(e.isDirectory())collect(file);else if(['report.json','verification.json','task-result.json'].includes(e.name))reports.push({path:path.relative(f.dir,file),content:JSON.parse(fs.readFileSync(file,'utf8'))});}};collect(f.stateDir);
 archive('canonical-feedback',{task:f.task,source:fs.readFileSync(path.join(f.root,'lib/offset.mjs'),'utf8'),tests:fs.readFileSync(path.join(f.root,'.github/continuum/offset.test.mjs'),'utf8'),result,reports,observations});
 assert.equal(observations.length,2,'independent failure must reach the next existing bounded provider call');
 assert.match(JSON.stringify(observations[1]),/INDEPENDENT|fresh check rejects/);
 assert.equal(result.status,'PATCH_PENDING_OWNER_REVIEW');assert.equal(result.approvedForMerge,false);
 assert.equal(git(f.root,'status','--porcelain'),'');

});
test('MΩ strict execution blocks before provider or missing journal when isolation is absent',async()=>{
 let calls=0;
 await assert.rejects(runCodingTask({project:'/does-not-exist',stateDir:'/does-not-exist-state',id:'momega-offset',env:{CONTINUUM_MOMEGA1:'1'},providerFactory:()=>{calls++;}}),/CODING_ISOLATION_REQUIRED/);
 assert.equal(calls,0);
});
test('MΩ model-observation gate rejects self-certified task-bound JSON',async()=>{
 const coding=await import('./coding-orchestrator.mjs');
 assert.equal(typeof coding.assertCodingModelObservationBinding,'function');
 assert.throws(()=>coding.assertCodingModelObservationBinding({task:{},raw:{trace:{modelObservation:{verified:true,evidenceClass:'LOCAL_MODEL_HOST'}}}}),/CODING_LOCAL_MODEL_OBSERVATION_REQUIRED/);
});
test('MΩ recall ablation accepts only a trusted Boolean option before any state access',async()=>{
 await assert.rejects(runCodingTask({project:'/does-not-exist',stateDir:'/does-not-exist-state',id:'momega-offset',
  technicalRecallEnabled:'false'}),/CODING_RECALL_OPTION_INVALID/);
});
test('MΩ rejected response cannot self-certify a genuine inference or manufacture call counts',async()=>{
 const coding=await import('./coding-orchestrator.mjs');
 assert.equal(typeof coding.assertCodingInferenceFailureBinding,'function');
 assert.throws(()=>coding.assertCodingInferenceFailureBinding({task:{},error:{code:'CODING_RESPONSE_INVALID',
  modelObservation:Object.freeze({verifiedModelExecution:true,responseStatus:'INVALID_MODEL_RESPONSE',proposalSha256:null})}}),
 /CODING_LOCAL_MODEL_OBSERVATION_REQUIRED/);
});
