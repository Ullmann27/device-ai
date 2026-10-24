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
