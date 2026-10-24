import test from 'node:test';
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {queryLocalBrain, queryLocalCodingBrain, checkLocalBrain} from './continuum-local-brain.mjs';

const SECRET='R'.repeat(48);
const SHA='a'.repeat(64);
const base={systemInstruction:'Du bist Continuum, kein Shell-Prozess.', userPrompt:'Erkläre Kotlin.',apiKey:SECRET};
const trusted={verified:true,source:'owner',sha256:SHA,text:'Kotlin ist eine Programmiersprache.'};

function fakeFetch(text='Guten Tag!', {status=200, onRequest=()=>{}}={}) {
  return async (url, options) => {
    onRequest(url,options);
    return new Response(JSON.stringify({choices:[{message:{content:text}}]}),
      {status,headers:{'content-type':'application/json'}});
  };
}

test('chat only contacts fixed localhost and returns text as untrusted data', async () => {
  let req;
  const result=await queryLocalBrain({...base,verifiedFacts:[trusted],fetchImpl:fakeFetch('Hallo!',{onRequest:(u,o)=>{req={u,o};}})});
  assert.equal(req.u,'http://127.0.0.1:8089/v1/chat/completions');
  assert.equal(req.o.headers.authorization,'Bearer '+SECRET);
  assert.equal(req.o.redirect,'error');
  assert.equal(JSON.parse(req.o.body).messages[0].content.includes('Kotlin ist'),true);
  assert.equal(result.text,'Hallo!');
  assert.equal(result.trusted,false);
});

test('rejects missing authentication, unverified facts and external configuration', async () => {
  const ignored=()=>{throw new Error('MUST NOT CONNECT');};
  await assert.rejects(queryLocalBrain({...base,apiKey:'',fetchImpl:ignored}),/LOCAL_AUTH_REQUIRED/);
  await assert.rejects(queryLocalBrain({...base,verifiedFacts:[{...trusted,verified:false}],fetchImpl:ignored}),/UNVERIFIED_FACT_REFUSED/);
  await assert.rejects(queryLocalBrain({...base,mode:'patch',allowedTrackedPaths:['../outside'],fetchImpl:ignored}),/UNSAFE_ALLOWED_PATH/);
  await assert.rejects(queryLocalBrain({...base,mode:'patch',allowedTrackedPaths:['scripts/run.mjs'],fetchImpl:ignored}),/UNSAFE_ALLOWED_PATH/);
  await assert.rejects(queryLocalBrain({...base,timeoutMs:600_000,fetchImpl:ignored}),/INVALID_LOCAL_TIMEOUT/);
});

test('patch proposal is schema constrained, validated and never applied', async () => {
  let request;
  const patch={file:'app/src/main/java/at/heinz/deviceai/Main.kt',oldText:'x=1',newText:'x=2'};
  const result=await queryLocalBrain({...base,mode:'patch',allowedTrackedPaths:[patch.file],
    fetchImpl:fakeFetch(JSON.stringify(patch),{onRequest:(u,o)=>{request=JSON.parse(o.body);}})});
  assert.equal(result.autoApply,false);
  assert.equal(result.trusted,false);
  assert.equal(result.patch.file,patch.file);
  assert.equal(request.response_format.type,'json_schema');
  assert.equal(request.response_format.json_schema.name,'continuum_bounded_patch_v1');
  assert.equal(request.response_format.json_schema.strict,true);
  assert.deepEqual(request.response_format.json_schema.schema.properties.file.enum,[patch.file]);
  assert.equal('schema' in request.response_format,false,'flat schema must not silently disable llama grammar');
  await assert.rejects(queryLocalBrain({...base,mode:'patch',allowedTrackedPaths:[patch.file],
    fetchImpl:fakeFetch(JSON.stringify({...patch,file:'app/../secrets'}))}),/LOCAL_PATCH_SCHEMA_REJECTED/);
  await assert.rejects(queryLocalBrain({...base,mode:'patch',allowedTrackedPaths:[patch.file],
    fetchImpl:fakeFetch(JSON.stringify({...patch,autoApply:true}))}),/LOCAL_PATCH_SCHEMA_REJECTED/);
});

test('server errors and overly long outputs fail closed', async () => {
  await assert.rejects(queryLocalBrain({...base,fetchImpl:fakeFetch('',{status:503})}),/LOCAL_HTTP_503/);
  await assert.rejects(queryLocalBrain({...base,fetchImpl:fakeFetch('x'.repeat(5001))}),/LOCAL_EMPTY_OR_OVERSIZED_OUTPUT/);
  const huge=async()=>new Response('x'.repeat(40000),{status:200});
  await assert.rejects(queryLocalBrain({...base,fetchImpl:huge}),/LOCAL_RESPONSE_TOO_LARGE/);
});

test('health never transmits authorization and discriminates loading/unavailable',async()=>{
  let options;
  const good=await checkLocalBrain({fetchImpl:async (_,o)=>{options=o;return Response.json({status:'ok'});}});
  assert.equal(good.ready,true);
  assert.equal(options.headers,undefined);
  const busy=await checkLocalBrain({fetchImpl:async()=>Response.json({error:'loading'},{status:503})});
  assert.equal(busy.status,'MODEL_LOADING');
  const missing=await checkLocalBrain({fetchImpl:async()=>{throw Error('unreachable');}});
  assert.equal(missing.ready,false);
});

test('actual Node HTTP loopback fixture exercises an end-to-end POST',async()=>{
  const server=createServer(async (req,res)=>{
    assert.equal(req.url,'/v1/chat/completions');
    assert.equal(req.headers.authorization,'Bearer '+SECRET);
    let body='';for await(const chunk of req)body+=chunk;
    assert.equal(JSON.parse(body).stream,false);
    res.writeHead(200,{'content-type':'application/json'});
    res.end(JSON.stringify({choices:[{message:{content:'Lokaler Test'}}]}));
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const port=server.address().port;
  const proxy=async(url,opts)=>fetch(url.replace('127.0.0.1:8089','127.0.0.1:'+port),opts);
  try{const answer=await queryLocalBrain({...base,fetchImpl:proxy});assert.equal(answer.text,'Lokaler Test');}
  finally{await new Promise(resolve=>server.close(resolve));}
});

test('local structured patch request sends nested schema to real loopback HTTP endpoint', async () => {
  const patch={file:'lib/maths.mjs',oldText:'export function sum(a,b){return a-b;}',
    newText:'export function sum(a,b){return a+b;}'};
  let observed=null;
  const server=createServer(async (req,res)=>{
    assert.equal(req.url,'/v1/chat/completions');
    assert.equal(req.headers.authorization,'Bearer '+SECRET);
    let raw='';for await(const part of req)raw+=part;
    const request=JSON.parse(raw);
    observed=request.response_format;
    if(observed?.type!=='json_schema'||observed?.json_schema?.schema?.properties?.file?.enum?.[0]!==patch.file){
      res.writeHead(400,{'content-type':'application/json'});
      res.end(JSON.stringify({error:'invalid schema wrapper'}));return;
    }
    res.writeHead(200,{'content-type':'application/json'});
    res.end(JSON.stringify({choices:[{message:{content:JSON.stringify(patch)}}]}));
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const port=server.address().port;
  try{
    const result=await queryLocalBrain({...base,mode:'patch',allowedTrackedPaths:[patch.file],
      fetchImpl:(url,opts)=>fetch(url.replace(':8089',':'+port),opts)});
    assert.equal(result.autoApply,false);
    assert.equal(result.trusted,false);
    assert.deepEqual(result.patch,patch);
    assert.equal(observed.json_schema.strict,true);
  }finally{await new Promise(resolve=>server.close(resolve));}
});

test('schema-compatible server response still cannot smuggle authority or unsafe file edits', async()=>{
  const patch={file:'lib/maths.mjs',oldText:'before',newText:'after'};
  for(const bad of [
    {...patch,autoApply:true},
    {...patch,file:'lib/unapproved.mjs'},
    {...patch,oldText:'before',newText:'before'}
  ]){
    await assert.rejects(queryLocalBrain({...base,mode:'patch',
      allowedTrackedPaths:['lib/maths.mjs'],fetchImpl:fakeFetch(JSON.stringify(bad))}),
    /LOCAL_PATCH_SCHEMA_REJECTED/);
  }
});

test('coding rejects same-text patches with deterministic digest, never discloses model output',async()=>{
 const raw=JSON.stringify({plan:['Fix pagination'],hypothesis:'Needs a changed return',patches:[
  {file:'lib/pages.mjs',oldText:'return a/b;',newText:'return a/b;'}]});
 const options={userPrompt:'Fix pagination',allowedTrackedPaths:['lib/pages.mjs'],apiKey:SECRET,fetchImpl:fakeFetch(raw)};
 const sha=(await import('node:crypto')).createHash('sha256').update(raw).digest('hex');
 await assert.rejects(queryLocalCodingBrain(options),error=>{
  assert.equal(error.message,'CODING_NOOP_PATCH '+sha);
  assert.equal(error.message.includes('return a/b'),false);
  return true;
 });
 await assert.rejects(queryLocalCodingBrain({...options,fetchImpl:fakeFetch('{not valid json')}),/CODING_RESPONSE_INVALID [a-f0-9]{64}/);
});
