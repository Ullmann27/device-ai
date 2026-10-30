/**
 * Opt-in NVIDIA-hosted NIM benchmark for public, deterministic synthetic inputs.
 * NVIDIA free developer endpoint requires an owner-held API key. No owner memory,
 * repository contents, pricing API, endpoint discovery, cloud provisioning or
 * automatic retry. This is NOT the existing CCC1 coding provider.
 */
import crypto from 'node:crypto';

export const NVIDIA_NIM_ENDPOINT='https://integrate.api.nvidia.com/v1/chat/completions';
export const NVIDIA_NIM_MODELS=Object.freeze([
 'nvidia/nemotron-3.5-lightning-30b-a3b',
 'nvidia/nemotron-3-ultra-550b-a55b',
 'deepseek-ai/deepseek-v4.1-flash'
]);
const FAIL='NVIDIA_NIM_BENCHMARK_UNAVAILABLE';
const hash=s=>crypto.createHash('sha256').update(s,'utf8').digest('hex');

export function createSyntheticAggregate({rows=192}={}){
 if(!Number.isSafeInteger(rows)||rows<64||rows>256)throw Error('NVIDIA_SYNTHETIC_SIZE_INVALID');
 let result=0;
 const items=[];
 for(let i=1;i<=rows;i++){
  const bucket=i%7;
  const value=(17*i*i+31*i+23)%997;
  items.push(i+','+bucket+','+value);
  if(bucket===2||bucket===5)result=(result+value)%65521;
 }
 const prompt=[
  'Synthetic benchmark only; no personal information or secrets.',
  'For the following CSV with columns id,bucket,value, sum value where bucket is 2 or 5.',
  'Compute the sum modulo 65521. Respond with ONLY the base-10 integer, no prose.',
  'id,bucket,value',...items
 ].join('\n');
 return Object.freeze({schema:'continuum-synthetic-aggregate-v1',
  prompt,expected:result,rows,promptSha256:hash(prompt)});
}

async function boundedBody(response,maxBytes){
 if(!response?.body||typeof response.body.getReader!=='function')throw Error(FAIL);
 const reader=response.body.getReader();
 const chunks=[];
 let n=0;
 try{
  for(;;){
   const {done,value}=await reader.read();
   if(done)break;
   if(!(value instanceof Uint8Array))throw Error(FAIL);
   n+=value.byteLength;
   if(n>maxBytes)throw Error('NVIDIA_RESPONSE_TOO_LARGE');
   chunks.push(value);
  }
 }catch(e){await reader.cancel().catch(()=>{});throw e;}
 const bytes=new Uint8Array(n);
 let pos=0;for(const chunk of chunks){bytes.set(chunk,pos);pos+=chunk.byteLength;}
 return new TextDecoder('utf-8',{fatal:true}).decode(bytes);
}

export async function runNvidiaNimSyntheticBenchmark({
 apiKey,model=NVIDIA_NIM_MODELS[0],rows=192,maxTokens=1024,timeoutMs=60000,
 fetchImpl=globalThis.fetch,now=()=>Date.now()
}={}){
 if(typeof apiKey!=='string'||!/^nvapi-[A-Za-z0-9_-]{12,256}$/.test(apiKey))
  throw Error('NVIDIA_AUTH_REQUIRED');
 if(!NVIDIA_NIM_MODELS.includes(model)||!Number.isSafeInteger(maxTokens)||
    maxTokens<16||maxTokens>2048||!Number.isSafeInteger(timeoutMs)||
    timeoutMs<1000||timeoutMs>120000||typeof fetchImpl!=='function')
  throw Error('NVIDIA_BENCHMARK_CONFIG_INVALID');
 const task=createSyntheticAggregate({rows});
 const body=JSON.stringify({model,messages:[{role:'user',content:task.prompt}],
  temperature:0,max_tokens:maxTokens,stream:false});
 if(Buffer.byteLength(body,'utf8')>24000)throw Error('NVIDIA_REQUEST_TOO_LARGE');
 const controller=new AbortController();
 const timer=setTimeout(()=>controller.abort(),timeoutMs);
 const start=now();
 let response;
 try{
  response=await fetchImpl(NVIDIA_NIM_ENDPOINT,{
   method:'POST',redirect:'error',signal:controller.signal,
   headers:{'Authorization':'Bearer '+apiKey,
    'Content-Type':'application/json','Accept':'application/json'},
   body
  });
  if(!response?.ok)throw Error(FAIL+'_HTTP_'+String(response?.status??'UNKNOWN'));
  const output=await boundedBody(response,65536);
  let data;
  try{data=JSON.parse(output);}catch{throw Error('NVIDIA_RESPONSE_INVALID');}
  if(data?.model!==model||!Array.isArray(data.choices)||data.choices.length!==1||
     !data.choices[0]?.message||typeof data.choices[0].message.content!=='string'||
     data.choices[0].message.content.length>12000)
   throw Error('NVIDIA_RESPONSE_INVALID');
  const text=data.choices[0].message.content.trim();
  const parsed=/^\d{1,10}$/.test(text)?Number(text):null;
  const elapsedMs=Math.max(0,now()-start);
  const inputTokens=data.usage?.prompt_tokens,outputTokens=data.usage?.completion_tokens;
  const validTokenCount=x=>Number.isSafeInteger(x)&&x>=0&&x<=1000000;
  return Object.freeze({
   schema:'continuum-nvidia-nim-synthetic-result-v1',
   transport:'NVIDIA_HOSTED_API_HTTPS',model,
   benchmarkTaskSha256:task.promptSha256,rows:task.rows,
   independentlyVerified:parsed===task.expected,
   expected:task.expected,reportedInteger:parsed,
   elapsedMs,
   promptTokens:validTokenCount(inputTokens)?inputTokens:null,
   completionTokens:validTokenCount(outputTokens)?outputTokens:null,
   measuredEndToEndTokensPerSecond:validTokenCount(outputTokens)&&elapsedMs>0?
    Math.round(outputTokens*100000/elapsedMs)/100:null,
   verifiedPhysicalGpu:false,
   ownerPrivateDataSent:false,
   credentialsLogged:false,
   externalWrites:false,
   freeTierUsageNotGuaranteeOfUnlimitedAccess:true
  });
 }finally{clearTimeout(timer);}
}
