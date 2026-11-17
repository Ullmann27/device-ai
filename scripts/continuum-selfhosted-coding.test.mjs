import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {createServer} from 'node:http';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {prepareSyntheticWorkspace,inspectSyntheticWorkspace} from './continuum-coding-bridge.mjs';
import {createSelfHostedTransport,runSelfHostedTrial,localEndpoint,localModelId} from './continuum-selfhosted-coding.mjs';
const MODEL='local/owned-coder';
const modulePath=fileURLToPath(new URL('./continuum-selfhosted-coding.mjs',import.meta.url));
async function fixture(action){
  const home=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-local-synthetic-'));
  const directory=path.join(home,'public-fixture');
  try {prepareSyntheticWorkspace(directory);return await action(directory,home);}
  finally {fs.rmSync(home,{recursive:true,force:true});}
}
async function server(handler,action){
  const http=createServer(handler);
  await new Promise((yes,no)=>http.once('error',no).listen(0,'127.0.0.1',yes));
  const address=http.address();
  assert.equal(address.address,'127.0.0.1');
  const endpoint='http://127.0.0.1:'+address.port+'/v1/chat/completions';
  try {return await action(endpoint);} finally {await new Promise((yes,no)=>http.close(err=>err?no(err):yes()));}
}
async function protocolFixture(action,{advertise=true,redirect=false,wrongModel=false,paid=false}={}){
  const observed=[];let step=0;
  return await server(async(req,res)=>{
    const parts=[];
    for await(const b of req)parts.push(b);
    const raw=Buffer.concat(parts).toString('utf8');
    observed.push({method:req.method,path:req.url,headers:req.headers,raw});
    if (req.url==='/v1/models' && req.method==='GET') {
      res.writeHead(200,{'content-type':'application/json'});
      res.end(JSON.stringify({data:advertise?[{id:MODEL}]:[{id:'unrelated'}]}));return;
    }
    if (req.url!=='/v1/chat/completions'||req.method!=='POST'){
      res.writeHead(404,{'content-type':'application/json'});res.end('{}');return;
    }
    if (redirect) {res.writeHead(307,{location:'https://example.invalid/steal'});res.end();return;}
    const request=JSON.parse(raw);
    assert.equal(request.model,MODEL);
    assert.equal(request.stream,false);
    assert.equal(request.tool_choice,'auto');
    assert.ok(Array.isArray(request.tools));
    const call=(name,args,id)=>({id,type:'function',function:{name,arguments:JSON.stringify(args)}});
    const tool_calls=step===0?[call('read_file',{path:'src/add.mjs'},'read-a'),call('read_file',{path:'test/add.test.mjs'},'read-test')]:
      step===1?[call('run_test',{},'test')]:null;
    step++;
    const message=tool_calls?{role:'assistant',content:null,tool_calls}:
      {role:'assistant',content:JSON.stringify({path:'src/add.mjs',old:'return Math.abs(a) + Math.abs(b);',replacement:'return a + b;'})};
    res.writeHead(200,{'content-type':'application/json'});
    res.end(JSON.stringify({model:wrongModel?'hidden-paid-model':MODEL,choices:[{message}],
      usage:{prompt_tokens:20,completion_tokens:5,...(paid?{cost:0.09}:{})}}));
  },endpoint=>action(endpoint,observed));
}
test('strict loopback allowlist refuses DNS, nonloopback, credentials and arbitrary URL',()=>{
  assert.deepEqual(localEndpoint('http://127.0.0.1:18080/v1/chat/completions'),{
    url:'http://127.0.0.1:18080/v1/chat/completions',modelsUrl:'http://127.0.0.1:18080/v1/models'});
  for(const invalid of ['http://localhost:18080/v1/chat/completions','http://0.0.0.0:18080/v1/chat/completions',
    'https://127.0.0.1:18080/v1/chat/completions','http://127.0.0.1:999/v1/chat/completions',
    'http://127.0.0.1:18080/other','http://user:key@127.0.0.1:18080/v1/chat/completions',
    'http://127.0.0.1:18080/v1/chat/completions?token=secret','http://127.0.0.2:18080/v1/chat/completions',
    'http://169.254.169.254:18080/v1/chat/completions',undefined]){
    assert.throws(()=>localEndpoint(invalid));
  }
});
test('local model identity is never passed as Fable/Astra',()=>{
  assert.equal(localModelId(MODEL),MODEL);
  for(const bad of ['claude-fable-5.1:free','gpt-6-astra:free','../etc/passwd','local/../../etc',
    'local/','local/Fable:free','local/with spaces',undefined])
    assert.throws(()=>localModelId(bad),/INVALID_LOCAL_MODEL_LABEL/);
});
test('real local HTTP protocol reads public files and executes baseline+retrospective patch tests',async()=>{
  await fixture(async directory=>{
    await protocolFixture(async(endpoint,observed)=>{
      const original=fs.readFileSync(path.join(directory,'src/add.mjs'),'utf8');
      const report=await runSelfHostedTrial({workspace:directory,endpoint,model:MODEL,timeoutMs:10000});
      assert.equal(report.completed,true);
      assert.equal(report.transportKind,'SELF_HOSTED_LOOPBACK');
      assert.equal(report.model,MODEL);
      assert.deepEqual(report.observedReadCalls,['src/add.mjs','test/add.test.mjs']);
      assert.equal(report.testResult.tests,3);
      assert.equal(report.testResult.pass,2);
      assert.equal(report.testResult.fail,1);
      assert.equal(report.proposalReview.testResult.pass,3);
      assert.equal(report.proposalReview.testResult.fail,0);
      assert.equal(report.verifiedModelWeights,false);
      assert.equal(report.verifiedNoUpstreamProxy,false);
      assert.equal(report.repositoryAccess,false);
      assert.equal(report.patchApplied,false);
      assert.equal(fs.readFileSync(path.join(directory,'src/add.mjs'),'utf8'),original);
      assert.deepEqual(observed.map(x=>[x.method,x.path]),[['GET','/v1/models'],['POST','/v1/chat/completions'],
        ['POST','/v1/chat/completions'],['POST','/v1/chat/completions']]);
      assert.ok(observed.every(x=>!x.headers.authorization));
      assert.equal(inspectSyntheticWorkspace(directory).root,directory);
    });
  });
});
test('local model GET /v1/models must advertise exact requested model',async()=>{
  await fixture(async directory=>{
    await protocolFixture(async endpoint=>{
      await assert.rejects(runSelfHostedTrial({workspace:directory,endpoint,model:MODEL}),/LOCAL_MODEL_NOT_ADVERTISED/);
    },{advertise:false});
  });
});
test('HTTP 307 cannot redirect even from 127.0.0.1 to remote network',async()=>{
  await fixture(async directory=>{
    await protocolFixture(async endpoint=>{
      await assert.rejects(runSelfHostedTrial({workspace:directory,endpoint,model:MODEL}),/REDIRECT_REFUSED/);
    },{redirect:true});
  });
});
test('server that reports another model is rejected before acceptance',async()=>{
  await fixture(async directory=>{
    await protocolFixture(async endpoint=>{
      await assert.rejects(runSelfHostedTrial({workspace:directory,endpoint,model:MODEL}),/MODEL_MISMATCH/);
    },{wrongModel:true});
  });
});
test('billable-use indication rejects even the local loopback mode',async()=>{
  await fixture(async directory=>{
    await protocolFixture(async endpoint=>{
      await assert.rejects(runSelfHostedTrial({workspace:directory,endpoint,model:MODEL}),/NONZERO_REPORTED_COST/);
    },{paid:true});
  });
});
test('missing approval makes local CLI exit before contacting or altering files',async()=>{
  await fixture(async directory=>{
    const before=inspectSyntheticWorkspace(directory);
    const result=spawnSync(process.execPath,[modulePath,'--synthetic-trial',directory],{
      encoding:'utf8',timeout:6000,env:{...process.env,
        CONTINUUM_SELFHOSTED_TRIAL_APPROVED:'',CONTINUUM_SELFHOSTED_MODEL:MODEL,
        CONTINUUM_SELFHOSTED_ENDPOINT:'http://127.0.0.1:18080/v1/chat/completions'}});
    assert.equal(result.status,2);
    assert.match(result.stderr,/SYNTHETIC_TRIAL_APPROVAL_REQUIRED/);
    assert.equal(inspectSyntheticWorkspace(directory).fixtureSha256,before.fixtureSha256);
  });
});
test('response size, content type and model parameter are bounded',async()=>{
  const real='http://127.0.0.1:18080/v1/chat/completions';
  const fake=(body,{type='application/json',status=200}={})=>async()=>new Response(body,{status,headers:{'content-type':type}});
  const errors=[
    [fake('{bad json'),/INVALID_JSON_RESPONSE/],
    [fake('{}',{type:'text/html'}),/JSON_REQUIRED/],
    [fake('x'.repeat(131073)),/RESPONSE_LIMIT/]
  ];
  for(const [fetcher,expected] of errors){
    const t=createSelfHostedTransport({endpoint:real,model:MODEL,fetcher});
    await assert.rejects(t.verifyServer(),expected);
  }
  const redirect=async()=>new Response('',{status:307,headers:{location:'https://other.example/'}});
  await assert.rejects(createSelfHostedTransport({endpoint:real,model:MODEL,fetcher:redirect}).verifyServer(),/REDIRECT_REFUSED/);
  const accepted=createSelfHostedTransport({endpoint:real,model:MODEL,fetcher:fake('{"data":[{"id":"local/owned-coder"}]}')});
  await accepted.verifyServer();
  await assert.rejects(accepted.transport({model:'local/other',messages:[],tools:[]}),/MODEL_MISMATCH/);
});
