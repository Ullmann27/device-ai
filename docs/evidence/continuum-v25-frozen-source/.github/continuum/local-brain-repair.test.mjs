import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawn,spawnSync} from 'node:child_process';
import {once} from 'node:events';
import {createLocalBrainCandidateProvider} from './local-brain-repair.mjs';
import {createWorktree,removeWorktree,repairSession} from './repair-loop.mjs';
import {proposeCandidates} from './repair-strategies.mjs';
import {critique} from './repair-critic.mjs';

const env={...process.env,CONTINUUM_LOCAL_BRAIN_API_KEY:'A'.repeat(48)};
const patch={file:'lib/math.mjs',oldText:'return a-b;',newText:'return a>b?a-b:b-a;'};
const reply={mode:'LOCAL_LOOPBACK_PATCH_SHADOW',trusted:false,autoApply:false,patch};
const input={deadline:Date.now()+60_000,diagnostics:[{kind:'TEST_FAILURE',message:'expected distance',fingerprint:'a1b2'}],readFile:()=> 'export function distance(a,b){return a-b;}\n'};

test('local provider rejects unsafe scope and authentication before any subprocess',()=>{
  for(const file of ['../outside.js','scripts/private.mjs','app/src/test/Secret.kt','app/build.gradle.kts','app/src/main/ToolGate.kt','.github/continuum/local-brain-repair.mjs'])
    assert.throws(()=>createLocalBrainCandidateProvider({allowedPaths:[file],env}),/SCOPE_REJECTED/);
  assert.throws(()=>createLocalBrainCandidateProvider({allowedPaths:['lib/math.mjs'],env:{}}),/AUTH_REQUIRED/);
});

test('provider sends bounded source data only through a fixed non-shell child',()=>{
  let request;
  const provider=createLocalBrainCandidateProvider({allowedPaths:['lib/math.mjs'],env,
    run:(cmd,args,opts)=>{request={cmd,args,opts};return {status:0,stdout:JSON.stringify(reply)};}});
  assert.equal(provider(input).patch.newText,patch.newText);
  assert.equal(request.cmd,process.execPath);assert.equal(request.opts.shell,false);
  assert.equal(request.args.at(-1),'patch');
  assert.equal(request.opts.input.includes(env.CONTINUUM_LOCAL_BRAIN_API_KEY),false);
  assert.equal(request.opts.env.NODE_OPTIONS,'');
  const body=JSON.parse(request.opts.input);
  assert.deepEqual(body.allowedTrackedPaths,['lib/math.mjs']);
  assert.equal(Object.keys(body).includes('command'),false);
  assert.throws(()=>provider({...input,readFile:()=> 'x'.repeat(1401)}),/SOURCE_BOUNDS/);
  assert.throws(()=>provider({...input,deadline:Date.now()-1}),/DEADLINE/);
});

test('timeouts and a model changing its allowed file fail closed',()=>{
  for(const result of [{status:null,error:{code:'ETIMEDOUT'}},{status:0,stdout:'not JSON'},
    {status:0,stdout:JSON.stringify({...reply,patch:{...patch,file:'lib/other.mjs'}})}]) {
    const provider=createLocalBrainCandidateProvider({allowedPaths:['lib/math.mjs'],env,run:()=>result});
    assert.throws(()=>provider(input),/LOCAL_REPAIR_/);
  }
});

test('model-derived recipe replay retains its review and risk boundary',()=>{
  const d={file:'lib/math.mjs',kind:'TEST_FAILURE',fingerprint:'aabbcc'};
  const candidates=proposeCandidates([d],{allowLocalModelPatches:true,allowedLocalPaths:['lib/math.mjs'],readFile:()=> 'export function distance(a,b){return a-b;}\n',index:null,jsSiblings:new Map(),changedInBase:[],
    experience:[{outcome:'VERIFIED',strategy:'LOCAL_MODEL_PATCH',diagnostic:d.fingerprint,edits:[{...patch,oldText:'export function distance(a,b){return a-b;}\n',newText:'export function distance(a,b){return a>b?a-b:b-a;}\n'}]}]});
  assert.equal(candidates[0].strategy,'LOCAL_MODEL_PATCH');
  assert.equal(critique(candidates[0]).reviewRequired,true);
  assert.equal(critique({strategy:'LOCAL_MODEL_PATCH',edits:[{...patch,file:'.github/continuum/local-brain-repair.mjs'}]}).approved,false);
});

test('actual authenticated HTTP candidate is tested in an isolated Git worktree',
  {skip:Boolean(process.env.CONTINUUM_REPAIR_DEPTH)},async()=>{
    // Controlled protocol fixture, NOT llama.cpp or evidence of model skill.
    const server=spawn(process.execPath,['--input-type=module','-e',`
      import {createServer} from 'node:http';
      const s=createServer(async(req,res)=>{
        if(req.url!='/v1/chat/completions'||req.headers.authorization!=='Bearer '+process.env.TEST_AUTH){res.writeHead(401);res.end();return;}
        let raw='';for await(const b of req)raw+=b;
        const request=JSON.parse(raw);
        if(request.response_format?.type!=='json_schema'){res.writeHead(400);res.end();return;}
        res.setHeader('content-type','application/json');
        res.end(JSON.stringify({choices:[{message:{content:process.env.TEST_PATCH}}]}));
      });s.listen(8089,'127.0.0.1',()=>process.stdout.write('READY'));
    `],{env:{...process.env,TEST_AUTH:env.CONTINUUM_LOCAL_BRAIN_API_KEY,TEST_PATCH:JSON.stringify(patch)},stdio:['ignore','pipe','pipe']});
    let wt;const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-http-repair-'));
    const root=path.join(dir,'source'),out=path.join(dir,'evidence');
    try {
      await Promise.race([once(server.stdout,'data'),once(server,'exit').then(()=>{throw Error('Loopback fixture could not listen');})]);
      fs.mkdirSync(path.join(root,'lib'),{recursive:true});fs.mkdirSync(path.join(root,'.github/continuum'),{recursive:true});
      fs.writeFileSync(path.join(root,'lib/math.mjs'),'export function distance(a,b){return a-b;}\n');
      fs.writeFileSync(path.join(root,'.github/continuum/spec.test.mjs'),
        "import test from 'node:test';import assert from 'node:assert/strict';import {distance} from '../../lib/math.mjs';test('unfamiliar negative distance',()=>assert.equal(distance(-2,5),7));\n");
      const git=(...args)=>{const r=spawnSync('git',args,{cwd:root,encoding:'utf8'});assert.equal(r.status,0,r.stderr);return r.stdout.trim();};
      git('init','-q');git('add','.');git('-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','fault');
      const base=git('rev-parse','HEAD');wt=createWorktree(root,base);
      const candidateProvider=createLocalBrainCandidateProvider({allowedPaths:['lib/math.mjs'],env});
      const report=repairSession({wt,base,chain:'node',out,candidateProvider});
      assert.equal(report.outcome,'VERIFIED');
      assert.equal(report.verified.strategy,'LOCAL_MODEL_PATCH');
      assert.equal(report.inference.localRequests,1);
      assert.equal(report.inference.verifiedModelExecution,false);
      assert.equal(report.verified.tests[0].tests.executed,1);
      assert.equal(git('status','--porcelain'),'');
    }finally{
      if(wt)removeWorktree(root,wt);
      server.kill();if(server.exitCode===null)await once(server,'exit');
      fs.rmSync(dir,{recursive:true,force:true});
    }
  });
