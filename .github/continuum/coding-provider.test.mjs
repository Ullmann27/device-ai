import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';import os from 'node:os';import path from 'node:path';import {spawnSync} from 'node:child_process';
import {createCodingCandidateProvider} from './coding-provider.mjs';
const proposal={plan:['Inspect pages','Correct rounding'],hypothesis:'Integer rounding loses the final page',patches:[{file:'lib/pages.mjs',oldText:'a/b',newText:'(a+b-1)/b'}]};
function fixture(){
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'coding-provider-'));fs.mkdirSync(path.join(root,'lib'));fs.writeFileSync(path.join(root,'lib/pages.mjs'),'export function pages(a,b){return a/b;}\n');
 const git=(...args)=>{const p=spawnSync('git',args,{cwd:root,encoding:'utf8'});assert.equal(p.status,0,p.stderr);return p.stdout.trim();};
 git('init','-q');git('add','.');git('-c','user.name=test','-c','user.email=test@example.invalid','commit','-qm','source');
 const task={schema:'continuum-coding-task',version:1,id:'repair-pages',sourceCommit:git('rev-parse','HEAD'),kind:'BUGFIX',goal:'Fix paging',allowedPaths:['lib/pages.mjs'],acceptance:['Partial page counts'],chain:'node',limits:{maxModelCalls:2,maxContextChars:6000}};
 return {root,task};
}
test('provider binds task, retrieves source, retains bounded revision history and calls only fixed CLI',()=>{
 const {root,task}=fixture();let request;
 try{
  const provider=createCodingCandidateProvider({root,task,env:{CONTINUUM_LOCAL_BRAIN_API_KEY:'a'.repeat(40)},run:(cmd,args,opts)=>{
   assert.equal(cmd,process.execPath);assert.equal(args.at(-1),'coding');assert.equal(opts.shell,false);
   request=JSON.parse(opts.input);return {status:0,stdout:JSON.stringify({mode:'LOCAL_CODING_PATCH_PROPOSAL_V1',trusted:false,autoApply:false,proposal})};}});
  const result=provider({base:task.sourceCommit,chain:'node',deadline:Date.now()+10000,diagnostics:[],history:[{outcome:'FAILED_TESTS',hypothesis:'wrong sign'}]});
  assert.deepEqual(result.patches,proposal.patches);assert.equal(result.localRequests,1);
  assert.equal(result.context.commit,task.sourceCommit);assert.equal(Object.isFrozen(provider.taskSpec),true);
  assert.match(request.userPrompt,/wrong sign/);assert.match(request.userPrompt,/export function pages/);
  assert.throws(()=>provider({base:'f'.repeat(40),chain:'node',deadline:Date.now()+10000}),/BINDING/);
 }finally{fs.rmSync(root,{recursive:true,force:true});}
});


test('model child receives only local credentials and durable trace excludes raw source/model output',t=>{
 const {root,task}=fixture();
 const evidenceDir=fs.mkdtempSync(path.join(os.tmpdir(),'coding-private-evidence-'));
 t.after(()=>{fs.rmSync(root,{recursive:true,force:true});fs.rmSync(evidenceDir,{recursive:true,force:true});});
 const privateTask={...task,goal:'Fix paging OWNER_PRIVATE_CONTEXT_SENTINEL'};
 const privateProposal={...proposal,hypothesis:'PRIVATE_MODEL_ANSWER_SENTINEL'};
 const stdout=JSON.stringify({mode:'LOCAL_CODING_PATCH_PROPOSAL_V1',
  trusted:false,autoApply:false,proposal:privateProposal});
 let childEnv;
 const provider=createCodingCandidateProvider({
  root,task:privateTask,evidenceDir,
  env:{CONTINUUM_LOCAL_BRAIN_API_KEY:'a'.repeat(40),
   CONTINUUM_LOCAL_BRAIN_MODEL:'qwen2.5-coder-1.5b-instruct-q4_k_m',
   GITHUB_TOKEN:'GITHUB_SECRET_SENTINEL',RENDER_API_KEY:'RENDER_SECRET_SENTINEL',
   OPENAI_API_KEY:'OPENAI_SECRET_SENTINEL',HTTPS_PROXY:'https://proxy.invalid',
   NODE_OPTIONS:'--require /tmp/inject.js',NODE_TEST_CONTEXT:'sensitive'},
  run:(_binary,_args,options)=>{childEnv=options.env;return {status:0,stdout};}
 });
 const result=provider({base:task.sourceCommit,chain:'node',deadline:Date.now()+10_000});
 assert.equal(result.hypothesis,privateProposal.hypothesis);
 assert.deepEqual(Object.keys(childEnv).sort(),[
  'CONTINUUM_LOCAL_BRAIN_API_KEY','CONTINUUM_LOCAL_BRAIN_MODEL',
  'NODE_OPTIONS','NODE_USE_ENV_PROXY','NODE_V8_COVERAGE'
 ].sort());
 assert.equal(childEnv.CONTINUUM_LOCAL_BRAIN_API_KEY,'a'.repeat(40));
 assert.equal(childEnv.NODE_OPTIONS,'');
 const trace=JSON.parse(fs.readFileSync(path.join(evidenceDir,'request-1.json'),'utf8'));
 assert.equal(trace.schema,'continuum-coding-model-request');
 assert.equal(trace.rawContentStored,false);
 assert.equal(trace.responseBytes,Buffer.byteLength(stdout,'utf8'));
 assert.match(trace.promptSha256,/^[a-f0-9]{64}$/);
 assert.match(trace.responseSha256,/^[a-f0-9]{64}$/);
 const fileText=fs.readFileSync(path.join(evidenceDir,'request-1.json'),'utf8');
 for(const secret of ['OWNER_PRIVATE_CONTEXT_SENTINEL','PRIVATE_MODEL_ANSWER_SENTINEL',
  'GITHUB_SECRET_SENTINEL','RENDER_SECRET_SENTINEL','OPENAI_SECRET_SENTINEL','export function pages'])
  assert.equal(fileText.includes(secret),false);
 assert.equal(result.context.commit,task.sourceCommit);
});

test('malformed model name is rejected before invoking the local child',()=>{
 const {root,task}=fixture();let calls=0;
 try{
  const provider=createCodingCandidateProvider({root,task,env:{
   CONTINUUM_LOCAL_BRAIN_API_KEY:'a'.repeat(40),CONTINUUM_LOCAL_BRAIN_MODEL:'\u0000unsafe-model'
  },run:()=>{calls++;return {status:0,stdout:'{}'};}});
  assert.throws(()=>provider({base:task.sourceCommit,chain:'node',deadline:Date.now()+10_000}),
   /CODING_MODEL_CONFIG_INVALID/);
  assert.equal(calls,0);
  for(const controlName of ['unsafe\nmodel','unsafe\rmodel']){
   const providerWithControl=createCodingCandidateProvider({root,task,env:{
    CONTINUUM_LOCAL_BRAIN_API_KEY:'a'.repeat(40),CONTINUUM_LOCAL_BRAIN_MODEL:controlName
   },run:()=>{calls++;return {status:0,stdout:'{}'};}});
   assert.throws(()=>providerWithControl({base:task.sourceCommit,chain:'node',deadline:Date.now()+10_000}),
    /CODING_MODEL_CONFIG_INVALID/);
  }
  assert.equal(calls,0);
 }finally{fs.rmSync(root,{recursive:true,force:true});}
});
