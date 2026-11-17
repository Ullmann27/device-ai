/** Real local TLS sockets + production CLI/provider; synthetic protocol reply, no model inference. */
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';import os from 'node:os';import path from 'node:path';
import https from 'node:https';
import {execFileSync,spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {describeDeepSeekCodingRequest,REMOTE_LIMITS} from './continuum-deepseek-bridge.mjs';
import {MODEL_ID,PINNED_REVISION} from './continuum-deepseek-tier-plan.mjs';

const KEY='local_tls_fixture_only_'+'a'.repeat(32);
const CLI=fileURLToPath(new URL('./continuum-local-brain-cli.mjs',import.meta.url));
const PROVIDER=new URL('../.github/continuum/coding-provider.mjs',import.meta.url).href;
const proposal={plan:['Inspect the integer rounding'],hypothesis:'A partial final page must count',
  patches:[{file:'lib/pages.mjs',oldText:'a/b',newText:'Math.ceil(a/b)'}]};
function approve(disclosure){return {schema:'CONTINUUM_REMOTE_MODEL_JOB_CONSENT_V1',
  requestSha256:disclosure.requestSha256,expiresAt:disclosure.deadline,allowExternal:true,
  allowRepositoryContext:true,allowOwnerMemory:false,allowSecrets:false,allowTraining:false,maxCostMicrousd:0};}
function child(args,input,cert,{key=KEY,trust=true}={}){
  return new Promise((resolve,reject)=>{
    const proc=spawn(process.execPath,args,{shell:false,timeout:15000,
      env:{CONTINUUM_REMOTE_BRAIN_API_KEY:key,NODE_OPTIONS:'',NODE_USE_ENV_PROXY:'1',
        ...(trust?{NODE_EXTRA_CA_CERTS:cert}:{}),NO_PROXY:'127.0.0.1,localhost'},stdio:['pipe','pipe','pipe']});
    let stdout='',stderr='';
    proc.stdout.on('data',x=>{stdout+=x;if(stdout.length>80000)proc.kill();});
    proc.stderr.on('data',x=>{stderr+=x;if(stderr.length>80000)proc.kill();});
    proc.once('error',reject);proc.once('close',(status,signal)=>resolve({status,signal,stdout,stderr}));
    proc.stdin.on('error',()=>{});proc.stdin.end(typeof input==='string'?input:JSON.stringify(input));
  });
}
async function fixture(t){
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'deepseek-tls-'));
  const cert=path.join(dir,'test-cert.pem'),key=path.join(dir,'test-key.pem');
  // Ephemeral test certificate/key are never committed or used outside loopback.
  execFileSync('openssl',['req','-x509','-newkey','rsa:2048','-sha256','-nodes','-keyout',key,'-out',cert,
    '-days','1','-subj','/CN=localhost','-addext','subjectAltName=DNS:localhost,IP:127.0.0.1'],{stdio:'ignore'});
  const records=[];let mode='ok';
  const server=https.createServer({key:fs.readFileSync(key),cert:fs.readFileSync(cert)},async(req,res)=>{
    const chunks=[];for await(const chunk of req)chunks.push(chunk);
    const raw=Buffer.concat(chunks).toString('utf8');
    records.push({method:req.method,url:req.url,auth:req.headers.authorization,raw,requestHash:req.headers['x-continuum-request-sha256']});
    res.setHeader('content-type','application/json');
    if(req.headers.authorization!=='Bearer '+KEY){res.writeHead(401);res.end('{"error":"PRIVATE_SERVER_SENTINEL"}');return;}
    if(mode==='stall')return;
    if(req.method==='GET'&&req.url==='/v1/continuum/capabilities'){
      res.end(JSON.stringify({schema:'CONTINUUM_MODEL_ENDPOINT_CAPABILITIES_V1',modelId:MODEL_ID,
        revision:mode==='wrong-revision'?'c'.repeat(40):PINNED_REVISION,noCharge:true,
        retention:'NONE',training:false,tools:false,...REMOTE_LIMITS}));return;
    }
    if(req.method==='POST'&&req.url==='/v1/chat/completions'){
      const input=JSON.parse(raw);
      res.end(JSON.stringify({model:MODEL_ID,model_revision:PINNED_REVISION,request_sha256:req.headers['x-continuum-request-sha256'],
        job_id:input.metadata.job_id,cost_microusd:0,usage:{completion_tokens:75},
        choices:[{finish_reason:'stop',message:{role:'assistant',content:JSON.stringify(proposal)}}]}));return;
    }
    res.writeHead(404);res.end('{}');
  });
  server.on('tlsClientError',()=>{});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  t.after(async()=>{server.closeAllConnections();await new Promise(resolve=>server.close(resolve));fs.rmSync(dir,{recursive:true,force:true});});
  return {dir,cert,records,endpoint:`https://127.0.0.1:${server.address().port}/v1/chat/completions`,setMode:value=>{mode=value;}};
}
const makeRequest=(endpoint,changes={})=>({schema:'CONTINUUM_REMOTE_CODING_REQUEST_V1',endpoint,
  jobId:'repair-tls-pages',taskFingerprint:'a'.repeat(64),sourceCommit:'b'.repeat(40),requestIndex:1,
  deadline:Date.now()+10000,userPrompt:'SYNTHETIC_LOCAL_PROTOCOL_FIXTURE',allowedTrackedPaths:['lib/pages.mjs'],...changes});

test('real TLS CLI authenticates, binds revision/request and refuses no consent, bad identity, auth, TLS or deadline',async t=>{
  const f=await fixture(t);
  const run=(r,options={},consent=approve(describeDeepSeekCodingRequest(r)))=>child([CLI,'coding-remote'],{request:r,consent},f.cert,options);
  let r=makeRequest(f.endpoint),out=await run(r);
  assert.equal(out.status,0,out.stderr);assert.equal(out.signal,null);
  const result=JSON.parse(out.stdout);assert.deepEqual(result.proposal,proposal);
  assert.equal(result.verifiedModelExecution,false);assert.equal(result.modelRevision,PINNED_REVISION);
  assert.deepEqual(f.records.map(x=>x.method),['GET','POST']);
  assert.equal(f.records[0].raw,'');assert.equal(f.records[1].auth,'Bearer '+KEY);
  assert.equal(f.records[1].requestHash,describeDeepSeekCodingRequest(r).requestSha256);

  const start=f.records.length;
  r=makeRequest(f.endpoint);out=await run(r,{},null);
  assert.equal(out.status,1);assert.match(out.stderr,/REMOTE_CONSENT_REQUIRED/);assert.equal(f.records.length,start);
  out=await child([CLI,'coding-remote'],'{"PRIVATE_INPUT_SENTINEL":',f.cert);
  assert.equal(out.status,1);assert.match(out.stderr,/REMOTE_REQUEST_INVALID/);
  assert.equal(out.stderr.includes('PRIVATE_INPUT_SENTINEL'),false);assert.equal(f.records.length,start);

  f.setMode('wrong-revision');r=makeRequest(f.endpoint);out=await run(r);
  assert.equal(out.status,1);assert.match(out.stderr,/REMOTE_CAPABILITY_MISMATCH/);
  assert.equal(f.records.length,start+1);assert.equal(f.records.at(-1).method,'GET');assert.equal(f.records.at(-1).raw,'');

  f.setMode('ok');r=makeRequest(f.endpoint);out=await run(r,{key:'wrong_key_'+'b'.repeat(32)});
  assert.equal(out.status,1);assert.match(out.stderr,/REMOTE_AUTH_REJECTED/);
  assert.equal(out.stderr.includes('PRIVATE_SERVER_SENTINEL'),false);
  const beforeUntrusted=f.records.length;
  r=makeRequest(f.endpoint);out=await run(r,{trust:false});
  assert.equal(out.status,1);assert.match(out.stderr,/REMOTE_UNAVAILABLE/);assert.equal(f.records.length,beforeUntrusted);

  f.setMode('stall');r=makeRequest(f.endpoint,{deadline:Date.now()+700});out=await run(r);
  assert.equal(out.status,1);assert.match(out.stderr,/REMOTE_DEADLINE_EXCEEDED/);assert.equal(out.signal,null);
  assert.equal(f.records.at(-1).method,'GET');assert.equal(f.records.at(-1).raw,'');
});

test('real existing provider -> fixed CLI -> authenticated TLS fixture -> canonical proposal and durable replay refusal',async t=>{
  const f=await fixture(t),root=path.join(f.dir,'source'),evidenceDir=path.join(f.dir,'state');
  fs.mkdirSync(path.join(root,'lib'),{recursive:true});fs.writeFileSync(path.join(root,'lib/pages.mjs'),'export const pages=(a,b)=>a/b;\n');
  const git=(...args)=>execFileSync('git',args,{cwd:root,encoding:'utf8',stdio:['ignore','pipe','pipe']}).trim();
  git('init','-q');git('add','.');git('-c','user.name=test','-c','user.email=test@example.invalid','commit','-qm','source');
  const task={schema:'continuum-coding-task',version:1,id:'repair-tls-pages',sourceCommit:git('rev-parse','HEAD'),kind:'BUGFIX',
    goal:'Correct the final partial page',allowedPaths:['lib/pages.mjs'],acceptance:['Partial pages count'],chain:'node',
    limits:{maxModelCalls:1,maxContextChars:6000}};
  const script=`import {createCodingCandidateProvider} from ${JSON.stringify(PROVIDER)};
let raw='';for await(const x of process.stdin)raw+=x;const input=JSON.parse(raw);
const approveRequest=({disclosure})=>({schema:'CONTINUUM_REMOTE_MODEL_JOB_CONSENT_V1',requestSha256:disclosure.requestSha256,
expiresAt:disclosure.deadline,allowExternal:true,allowRepositoryContext:true,allowOwnerMemory:false,allowSecrets:false,
allowTraining:false,maxCostMicrousd:0});
const options={...input,env:process.env,remoteTeacher:{enabled:true,endpoint:input.endpoint,approveRequest}};
const provider=createCodingCandidateProvider(options);
const result=provider({base:input.task.sourceCommit,chain:'node',deadline:Date.now()+10000});
let replay='';try{createCodingCandidateProvider(options)({base:input.task.sourceCommit,chain:'node',deadline:Date.now()+10000});}
catch(error){replay=error.message;}process.stdout.write(JSON.stringify({result,replay}));`;
  const out=await child(['--input-type=module','-e',script],{root,evidenceDir,task,endpoint:f.endpoint},f.cert);
  assert.equal(out.status,0,out.stderr);const {result,replay}=JSON.parse(out.stdout);
  assert.deepEqual(result.patches,proposal.patches);assert.equal(result.trusted,false);assert.equal(result.autoApply,false);
  assert.equal(result.trace.verifiedModelExecution,false);assert.equal(result.context.commit,task.sourceCommit);
  assert.equal(replay,'CODING_MODEL_BUDGET_EXHAUSTED');assert.equal(f.records.length,2);
  const record=fs.readFileSync(path.join(evidenceDir,'request-1.json'),'utf8');
  assert.equal(JSON.parse(record).status,'RESPONDED');assert.equal(JSON.parse(record).transport,'AUTHENTICATED_HTTPS_TEACHER');
  assert.equal(record.includes('export const pages'),false);assert.equal(record.includes(KEY),false);
  assert.equal(git('status','--porcelain'),'');
});
