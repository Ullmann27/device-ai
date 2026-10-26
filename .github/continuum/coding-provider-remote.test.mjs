import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';import os from 'node:os';import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {createCodingCandidateProvider} from './coding-provider.mjs';
import {MODEL_ID,PINNED_REVISION} from '../../scripts/continuum-deepseek-tier-plan.mjs';

const KEY='remote_test_only_'+'a'.repeat(32);
const proposal={plan:['Inspect rounding'],hypothesis:'Partial pages need ceiling',
  patches:[{file:'lib/pages.mjs',oldText:'a/b',newText:'Math.ceil(a/b)'}]};
const approve=({disclosure})=>({schema:'CONTINUUM_REMOTE_MODEL_JOB_CONSENT_V1',
  requestSha256:disclosure.requestSha256,expiresAt:disclosure.deadline,
  allowExternal:true,allowRepositoryContext:true,allowOwnerMemory:false,allowSecrets:false,
  allowTraining:false,maxCostMicrousd:0});
function fixture(t){
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'coding-remote-source-'));
  const evidenceDir=fs.mkdtempSync(path.join(os.tmpdir(),'coding-remote-state-'));
  t.after(()=>{fs.rmSync(root,{recursive:true,force:true});fs.rmSync(evidenceDir,{recursive:true,force:true});});
  fs.mkdirSync(path.join(root,'lib'));fs.writeFileSync(path.join(root,'lib/pages.mjs'),'export const pages=(a,b)=>a/b;\n');
  const git=(...args)=>{const r=spawnSync('git',args,{cwd:root,encoding:'utf8'});assert.equal(r.status,0,r.stderr);return r.stdout.trim();};
  git('init','-q');git('add','.');git('-c','user.name=test','-c','user.email=test@example.invalid','commit','-qm','source');
  const task={schema:'continuum-coding-task',version:1,id:'repair-remote-pages',sourceCommit:git('rev-parse','HEAD'),kind:'BUGFIX',
    goal:'Fix private rounding PRIVATE_SOURCE_SENTINEL',allowedPaths:['lib/pages.mjs'],acceptance:['Partial pages count'],
    chain:'node',limits:{maxModelCalls:1,maxContextChars:6000}};
  const remoteTeacher={enabled:true,endpoint:'https://teacher.example.invalid/v1/chat/completions',approveRequest:approve};
  return {root,task,evidenceDir,remoteTeacher,env:{CONTINUUM_REMOTE_BRAIN_API_KEY:KEY}};
}

test('remote seam needs explicit config, server credential and durable canonical job evidence',t=>{
  const f=fixture(t);
  assert.throws(()=>createCodingCandidateProvider({...f,evidenceDir:null}),/REMOTE_DURABLE_EVIDENCE_REQUIRED/);
  assert.throws(()=>createCodingCandidateProvider({...f,env:{}}),/REMOTE_AUTH_REQUIRED/);
  assert.throws(()=>createCodingCandidateProvider({...f,remoteTeacher:{...f.remoteTeacher,enabled:false}}),/REMOTE_TEACHER_CONFIG_INVALID/);
});

test('declined or mismatched per-request consent consumes no reservation and starts no child',t=>{
  const f=fixture(t);let runs=0;
  for(const approveRequest of [()=>null,()=>true,({disclosure})=>({...approve({disclosure}),requestSha256:'c'.repeat(64)})]){
    const p=createCodingCandidateProvider({...f,remoteTeacher:{...f.remoteTeacher,approveRequest},run:()=>{runs++;}});
    assert.throws(()=>p({base:f.task.sourceCommit,chain:'node',deadline:Date.now()+10000}),/REMOTE_CONSENT_REQUIRED/);
  }
  assert.equal(runs,0);assert.deepEqual(fs.readdirSync(f.evidenceDir),[]);
});

test('approved remote proposal uses the fixed CLI and existing durable budget without leaking private contents',t=>{
  const f=fixture(t);let calls=0,approvals=0,childEnv;
  const remoteTeacher={...f.remoteTeacher,approveRequest:input=>{
    approvals++;assert.match(input.userPrompt,/PRIVATE_SOURCE_SENTINEL/);
    assert.equal(input.disclosure.jobId,f.task.id);assert.equal(input.disclosure.requestIndex,1);
    assert.equal(input.disclosure.modelId,MODEL_ID);assert.equal(input.disclosure.revision,PINNED_REVISION);
    assert.equal(fs.existsSync(path.join(f.evidenceDir,'request-1.json')),false);
    return approve(input);
  }};
  const run=(cmd,args,opts)=>{
    calls++;childEnv=opts.env;assert.equal(cmd,process.execPath);assert.equal(args.at(-1),'coding-remote');assert.equal(opts.shell,false);
    const input=JSON.parse(opts.input);
    assert.equal(input.request.sourceCommit,f.task.sourceCommit);assert.equal(input.request.requestIndex,1);
    const reservation=JSON.parse(fs.readFileSync(path.join(f.evidenceDir,'request-1.json'),'utf8'));
    assert.equal(reservation.status,'RESERVED');assert.equal(reservation.transport,'AUTHENTICATED_HTTPS_TEACHER');
    return {status:0,stdout:JSON.stringify({mode:'LOCAL_CODING_PATCH_PROPOSAL_V1',trusted:false,autoApply:false,proposal,
      transport:'AUTHENTICATED_HTTPS_TEACHER',modelId:MODEL_ID,modelRevision:PINNED_REVISION,
      requestSha256:input.consent.requestSha256,verifiedModelExecution:false})};
  };
  const options={...f,remoteTeacher,run,env:{...f.env,CONTINUUM_LOCAL_BRAIN_API_KEY:'LOCAL_SECRET_SENTINEL',
    GITHUB_TOKEN:'GITHUB_SECRET_SENTINEL',HTTPS_PROXY:'http://proxy.invalid:8080',
    NODE_EXTRA_CA_CERTS:'/approved/cert.pem',NODE_OPTIONS:'--require /untrusted.js'}};
  const p=createCodingCandidateProvider(options);
  const result=p({base:f.task.sourceCommit,chain:'node',deadline:Date.now()+10000});
  assert.deepEqual(result.patches,proposal.patches);assert.equal(result.localRequests,1);
  assert.equal(result.trace.verifiedModelExecution,false);assert.equal(result.trace.configuredModel,MODEL_ID);
  assert.equal(childEnv.CONTINUUM_REMOTE_BRAIN_API_KEY,KEY);
  assert.equal(childEnv.CONTINUUM_LOCAL_BRAIN_API_KEY,undefined);assert.equal(childEnv.GITHUB_TOKEN,undefined);
  assert.equal(childEnv.HTTPS_PROXY,'http://proxy.invalid:8080');assert.equal(childEnv.NODE_EXTRA_CA_CERTS,'/approved/cert.pem');
  assert.equal(childEnv.NODE_OPTIONS,'');assert.equal(childEnv.NODE_USE_ENV_PROXY,'1');
  const record=fs.readFileSync(path.join(f.evidenceDir,'request-1.json'),'utf8');
  for(const secret of [KEY,'PRIVATE_SOURCE_SENTINEL','export const pages','GITHUB_SECRET_SENTINEL','LOCAL_SECRET_SENTINEL'])
    assert.equal(record.includes(secret),false);
  assert.equal(JSON.parse(record).status,'RESPONDED');
  const resumed=createCodingCandidateProvider(options);
  assert.throws(()=>resumed({base:f.task.sourceCommit,chain:'node',deadline:Date.now()+10000}),/CODING_MODEL_BUDGET_EXHAUSTED/);
  assert.equal(calls,1);assert.equal(approvals,1);
});

test('remote CLI failure cannot silently invoke a local or another remote model',t=>{
  const f=fixture(t);let calls=0;
  const p=createCodingCandidateProvider({...f,run:()=>{calls++;return {status:1,
    stderr:'REMOTE_MODEL_UNAVAILABLE_OR_INVALID: REMOTE_AUTH_REJECTED PRIVATE_SENTINEL',stdout:''};}});
  assert.throws(()=>p({base:f.task.sourceCommit,chain:'node',deadline:Date.now()+10000}),error=>{
    assert.equal(error.code,'CODING_MODEL_UNAVAILABLE');assert.equal(error.message.includes('PRIVATE_SENTINEL'),false);return true;
  });
  assert.equal(calls,1);
});
