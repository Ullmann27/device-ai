import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {queryLocalCodingBrain} from '../../scripts/continuum-local-brain.mjs';
import {createCodingCandidateProvider} from './coding-provider.mjs';
import {createWorktree,removeWorktree,repairSession} from './repair-loop.mjs';

const moduleText='export function distance(a,b){return a-b;}\n';
const digest=text=>createHash('sha256').update(text).digest('hex');
function fixture(){
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-rejected-coding-'));
 const root=path.join(dir,'repo'),state=path.join(dir,'state');
 fs.mkdirSync(path.join(root,'lib'),{recursive:true});
 fs.mkdirSync(path.join(root,'.github/continuum'),{recursive:true});
 fs.mkdirSync(state);
 fs.writeFileSync(path.join(root,'lib/math.mjs'),moduleText);
 fs.writeFileSync(path.join(root,'.github/continuum/spec.test.mjs'),
  "import test from 'node:test';import assert from 'node:assert/strict';import {distance} from '../../lib/math.mjs';\n"+
  "test('positive',()=>assert.equal(distance(8,3),5));\n"+
  "test('reverse',()=>assert.equal(distance(3,8),5));\n"+
  "test('negative',()=>assert.equal(distance(-9,-2),7));\n");
 const git=(...args)=>{const p=spawnSync('git',args,{cwd:root,encoding:'utf8',shell:false});
  assert.equal(p.status,0,p.stderr);return p.stdout.trim();};
 git('init','-q');git('add','.');git('-c','user.name=Test','-c','user.email=test@example.invalid','commit','-qm','bug baseline');
 const head=git('rev-parse','HEAD');
 const task=Object.freeze({schema:'continuum-coding-task',version:1,id:'rejection-repair',sourceCommit:head,kind:'BUGFIX',
  goal:'Fix distance',allowedPaths:Object.freeze(['lib/math.mjs']),
  acceptance:Object.freeze(['Positive distance when coordinates are reversed']),
  chain:'node',limits:Object.freeze({maxModelCalls:3,maxContextChars:6000})});
 return {dir,root,state,head,task};
}
function runSession(f,respond){
 const wt=createWorktree(f.root,f.head);
 try{
  const provider=Object.assign(respond,{taskSpec:f.task,allowedPaths:f.task.allowedPaths});
  return repairSession({wt,base:f.head,chain:'node',out:path.join(f.state,'report'),candidateProvider:provider});
 }finally{removeWorktree(f.root,wt);}
}
function validProposal(f){
 return {mode:'LOCAL_CODING_PATCH_PROPOSAL_V1',trusted:false,autoApply:false,localRequests:1,
  context:{commit:f.head,contextSha256:'a'.repeat(64),ranges:[]},
  plan:['Correct negative distance'],hypothesis:'Subtraction has the wrong sign for reversed positions',
  patches:[{file:'lib/math.mjs',oldText:'return a-b;',newText:'return a>b?a-b:b-a;'}]};
}
test('invalid model output carries SHA-256 only, not discarded raw source',async()=>{
 const p={plan:['Change sign'],hypothesis:'Wrong sign',
  patches:[{file:'lib/math.mjs',oldText:'return a-b;',newText:'return a-b;'}]};
 const raw=JSON.stringify(p);
 await assert.rejects(queryLocalCodingBrain({userPrompt:'Fix distance',allowedTrackedPaths:['lib/math.mjs'],
   apiKey:'A'.repeat(40),fetchImpl:async()=>Response.json({choices:[{message:{content:raw}}]})}),error=>{
  assert.equal(error.message,'CODING_RESPONSE_INVALID:'+digest(raw));
  assert.equal(error.message.includes('return a-b;'),false);
  return true;
 });
});
test('provider transports recognized rejection fingerprint without raw stderr',()=>{
 const f=fixture();
 try{
  const hash='d'.repeat(64);
  const provider=createCodingCandidateProvider({root:f.root,task:f.task,env:{CONTINUUM_LOCAL_BRAIN_API_KEY:'a'.repeat(40)},
   run:(cmd,args,opts)=>{
    assert.equal(cmd,process.execPath);assert.equal(args.at(-1),'coding');assert.equal(opts.shell,false);
    return {status:1,stderr:'LOCAL_MODEL_UNAVAILABLE_OR_INVALID: CODING_RESPONSE_INVALID:'+hash+'\n'};}});
  assert.throws(()=>provider({base:f.head,chain:'node',deadline:Date.now()+10000}),error=>{
   assert.equal(error.code,'CODING_RESPONSE_INVALID');assert.equal(error.responseSha256,hash);
   assert.equal(error.message,'CODING_RESPONSE_INVALID');return true;
  });
 }finally{fs.rmSync(f.dir,{recursive:true,force:true});}
});
test('rejection diagnostics and fingerprint reach the next model revision',()=>{
 const f=fixture();let requests=0;let second;
 try{
  const report=runSession(f,input=>{
   requests++;
   if(requests===1){
    const error=Error('CODING_RESPONSE_INVALID');error.code='CODING_RESPONSE_INVALID';
    error.responseSha256='b'.repeat(64);throw error;
   }
   second=input;return validProposal(f);
  });
  assert.equal(requests,2);assert.equal(report.outcome,'VERIFIED');
  assert.equal(report.inference.localRequests,2);
  assert.equal(report.inference.rejections.length,1);
  assert.equal(report.inference.rejections[0].responseSha256,'b'.repeat(64));
  assert.equal(report.inference.rejections[0].code,'CODING_RESPONSE_INVALID');
  assert.equal(second.history[0].outcome,'REJECTED_MODEL_OUTPUT');
  assert.equal(second.history[0].candidate,'b'.repeat(16));
  assert.equal(second.diagnostics[0].kind,'CODING_REJECTED_OUTPUT');
  assert.match(second.diagnostics[0].message,/unchanged/);
  assert.equal(fs.readFileSync(path.join(f.root,'lib/math.mjs'),'utf8'),moduleText);
 }finally{fs.rmSync(f.dir,{recursive:true,force:true});}
});
test('identical invalid model response stops after second rejection without running candidate checks',()=>{
 const f=fixture();let calls=0;
 try{
  const report=runSession(f,()=>{calls++;const e=Error('CODING_RESPONSE_INVALID');
   e.code='CODING_RESPONSE_INVALID';e.responseSha256='c'.repeat(64);throw e;});
  assert.equal(calls,2);assert.equal(report.outcome,'NO_VERIFIED_REPAIR');
  assert.equal(report.inference.localRequests,2);
  assert.equal(report.inference.rejections.length,2);
  assert.equal(report.inference.rejections[1].repeated,true);
  assert.equal(report.attempts.length,0);
  assert.equal(report.verified,null);
 }finally{fs.rmSync(f.dir,{recursive:true,force:true});}
});
test('unknown provider errors never leak their message to the durable report',()=>{
 const f=fixture();let calls=0;
 try{
  const report=runSession(f,()=>{calls++;throw Error('SECRET-TEST-SENTINEL:do-not-log');});
  assert.equal(calls,3);assert.equal(report.outcome,'NO_VERIFIED_REPAIR');
  assert.equal(report.inference.localRequests,3);
  assert.ok(report.inference.rejections.every(x=>x.code==='CODING_PROVIDER_ERROR'));
  assert.equal(JSON.stringify(report).includes('SECRET-TEST-SENTINEL'),false);
 }finally{fs.rmSync(f.dir,{recursive:true,force:true});}
});
