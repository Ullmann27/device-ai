#!/usr/bin/env node
/** Separate, optional, locally served coding model endpoint.
 * Only loopback HTTP with explicit owner approval, and only synthetic fixtures.
 * It never launches model servers, fetches model weights or touches Git.
 */
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import {runSyntheticCodingTrial,inspectSyntheticWorkspace} from './continuum-coding-bridge.mjs';

const MAX_RESPONSE_BYTES=131072;
const MAX_REQUEST_BYTES=32768;
const REQUIRED_PATH='/v1/chat/completions';
const MODELS_PATH='/v1/models';
const LOCAL_ID=/^local\/[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/;
const ALLOWED_PORT_MIN=1024;
const ALLOWED_PORT_MAX=65535;
export function localEndpoint(raw) {
  if (typeof raw!=='string') throw Error('EXPLICIT_LOOPBACK_ENDPOINT_REQUIRED');
  // This is intentionally stricter than parsing arbitrary URLs: no DNS lookup,
  // credentials, query, fragments, IPv6 ambiguity, path traversal or TLS fallback.
  const m=/^http:\/\/127\.0\.0\.1:([0-9]{4,5})\/v1\/chat\/completions$/.exec(raw);
  if (!m) throw Error('LOOPBACK_ONLY_ENDPOINT_REQUIRED');
  const port=Number(m[1]);
  if (!Number.isSafeInteger(port) || port<ALLOWED_PORT_MIN || port>ALLOWED_PORT_MAX)
    throw Error('INVALID_LOOPBACK_PORT');
  return Object.freeze({url:'http://127.0.0.1:'+port+REQUIRED_PATH,
    modelsUrl:'http://127.0.0.1:'+port+MODELS_PATH});
}
export function localModelId(model) {
  if (typeof model!=='string' || !LOCAL_ID.test(model))
    throw Error('INVALID_LOCAL_MODEL_LABEL');
  return model;
}
async function boundedResponse(response) {
  if (response.redirected || response.status>=300 && response.status<400)
    throw Error('REDIRECT_REFUSED');
  if (!response.ok) throw Error('LOOPBACK_HTTP_'+response.status);
  const type=response.headers.get('content-type')||'';
  if (!/^application\/json(?:\s*;|\s*$)/i.test(type)) throw Error('JSON_REQUIRED');
  if (Number(response.headers.get('content-length')||0)>MAX_RESPONSE_BYTES)
    throw Error('RESPONSE_LIMIT');
  const chunks=[];let length=0;
  // Streamed reads let us abort a deceptive or endlessly streaming HTTP body.
  for await(const chunk of response.body) {
    length+=chunk.length;
    if (length>MAX_RESPONSE_BYTES) {await response.body.cancel().catch(()=>{});throw Error('RESPONSE_LIMIT');}
    chunks.push(chunk);
  }
  let data;
  try {data=JSON.parse(Buffer.concat(chunks,length).toString('utf8'));}
  catch {throw Error('INVALID_JSON_RESPONSE');}
  if (!data || typeof data!=='object' || Array.isArray(data)) throw Error('INVALID_JSON_RESPONSE');
  return data;
}
/** The listener must already be running: this module never starts a server. */
export function createSelfHostedTransport({endpoint,model,fetcher=fetch,timeoutMs=45000}={}) {
  const route=localEndpoint(endpoint),selected=localModelId(model);
  if (typeof fetcher!=='function') throw Error('FETCH_REQUIRED');
  if (!Number.isInteger(timeoutMs)||timeoutMs<1000||timeoutMs>90000)
    throw Error('INVALID_TIMEOUT');
  const exchange=async (url,options) => {
    const controller=new AbortController();
    const timer=setTimeout(()=>controller.abort(),timeoutMs);
    try {
      const response=await fetcher(url,{...options,signal:controller.signal,redirect:'manual',
        headers:{'content-type':'application/json','accept':'application/json'}});
      return await boundedResponse(response);
    } catch(e) {
      // Avoid echoing a hostile server's response, internal headers or paths.
      if (e.message && /^(?:LOOPBACK_HTTP_[0-9]{3}|REDIRECT_REFUSED|JSON_REQUIRED|RESPONSE_LIMIT|INVALID_JSON_RESPONSE)$/.test(e.message)) throw e;
      throw Error('LOCAL_MODEL_TRANSPORT_FAILED');
    } finally {clearTimeout(timer);}
  };
  let confirmed=false;
  const verifyServer=async () => {
    const info=await exchange(route.modelsUrl,{method:'GET'});
    if (!Array.isArray(info.data)||info.data.length>64||
      !info.data.some(x=>x&&typeof x==='object'&&x.id===selected))
      throw Error('LOCAL_MODEL_NOT_ADVERTISED');
    confirmed=true;
    return Object.freeze({modelId:selected,advertised:true,localEndpoint:route.url});
  };
  const transport=async ({model:requested,messages,tools}) => {
    if (requested!==selected) throw Error('MODEL_MISMATCH');
    if (!confirmed) await verifyServer();
    if (!Array.isArray(messages)||!Array.isArray(tools)) throw Error('REQUEST_INVALID');
    const request=JSON.stringify({model:selected,messages,tools,tool_choice:'auto',
      stream:false,temperature:0,max_tokens:1000});
    if (Buffer.byteLength(request,'utf8')>MAX_REQUEST_BYTES)
      throw Error('REQUEST_LIMIT');
    const body=await exchange(route.url,{method:'POST',body:request});
    if (!Array.isArray(body.choices)||body.choices.length<1||body.choices.length>3)
      throw Error('LOCAL_MODEL_RESPONSE_INVALID');
    // The local server may report its advertised model ID, but not a proof of
    // the actual binary/model weights loaded or whether the server is a proxy.
    if (body.model!==undefined && body.model!==selected)
      throw Error('MODEL_MISMATCH');
    return body;
  };
  return Object.freeze({verifyServer,transport,model:selected,endpoint:route.url});
}
export async function runSelfHostedTrial({workspace,endpoint,model,fetcher,timeoutMs}={}) {
  inspectSyntheticWorkspace(workspace);
  const server=createSelfHostedTransport({endpoint,model,fetcher,timeoutMs});
  await server.verifyServer();
  const result=await runSyntheticCodingTrial({workspace,model:server.model,
    transport:server.transport,transportKind:'SELF_HOSTED_LOOPBACK'});
  return Object.freeze({...result,modelAdvertisedByLocalServer:true,
    // We cannot independently inspect the runtime's loaded model weights.
    verifiedModelWeights:false,verifiedNoUpstreamProxy:false,
    cloudModelUsed:null,paidApiKeyRequired:false});
}
async function main() {
  if (process.argv.length!==4||process.argv[2]!=='--synthetic-trial')
    throw Error('USAGE: --synthetic-trial /public/fixture');
  if (process.env.CONTINUUM_SELFHOSTED_TRIAL_APPROVED!=='SYNTHETIC_ONLY')
    throw Error('SYNTHETIC_TRIAL_APPROVAL_REQUIRED');
  const model=localModelId(process.env.CONTINUUM_SELFHOSTED_MODEL);
  const endpoint=localEndpoint(process.env.CONTINUUM_SELFHOSTED_ENDPOINT).url;
  const result=await runSelfHostedTrial({workspace:process.argv[3],model,endpoint});
  const {proposal,...safe}=result;
  process.stdout.write(JSON.stringify({...safe,proposalReturned:typeof proposal==='string'},null,2)+'\n');
  if (!result.completed) process.exitCode=2;
}
if (process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
  main().catch(e=>{
    const message=e?.message;
    const safe=typeof message==='string' && /^[A-Z0-9_]+(?:_[0-9]{3})?$/.test(message) ? message : 'LOCAL_TRIAL_FAILED';
    process.stderr.write('CONTINUUM_LOCAL_CODER_BLOCKED='+safe+'\n');
    process.exitCode=2;
  });
}
