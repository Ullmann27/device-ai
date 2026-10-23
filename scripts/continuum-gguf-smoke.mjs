/**
 * Explicit real-runtime probe. Requires an already running, manually launched
 * localhost llama.cpp server and caller-pinned local bytes. It starts nothing,
 * downloads nothing, applies nothing, and deliberately has no mock-fetch path.
 */
import {readFile,stat} from 'node:fs/promises';
import {performance} from 'node:perf_hooks';
import {admitGgufFile} from './continuum-gguf.mjs';
import {checkLocalBrain,queryLocalBrain} from './continuum-local-brain.mjs';
import {queryLocalDiagnosis} from './continuum-local-context.mjs';

function fail(code){throw new Error(code);}
async function measuredRequest(run) {
  let metrics=null,observedText=null;
  // Observe actual HTTP response metadata while preserving the existing fixed
  // loopback client's parsing, timeouts and untrusted-output contract.
  const observedFetch=async(url,options)=>{
    const response=await globalThis.fetch(url,options);
    const reader=response.body?.getReader();if(!reader)fail('GGUF_SMOKE_EMPTY_RESPONSE');
    let n=0;const chunks=[];
    try {
      while(true){const {done,value}=await reader.read();if(done)break;
        n+=value.length;if(n>32768)fail('GGUF_SMOKE_RESPONSE_LIMIT');chunks.push(value);}
    } finally {await reader.cancel().catch(()=>{});reader.releaseLock();}
    if(response.ok){
      const body=JSON.parse(Buffer.concat(chunks).toString('utf8'));
      const content=body.choices?.[0]?.message?.content;
      if(typeof content==='string'&&content.length<=5000)observedText=content;
      const count=(x)=>Number.isSafeInteger(x)&&x>=0?x:null;
      const time=(x)=>typeof x==='number'&&Number.isFinite(x)&&x>=0?x:null;
      metrics={promptTokens:count(body.usage?.prompt_tokens),outputTokens:count(body.usage?.completion_tokens),
        promptMs:time(body.timings?.prompt_ms),generationMs:time(body.timings?.predicted_ms),
        generatedTokensPerSecond:time(body.timings?.predicted_per_second)};
    }
    return new Response(Buffer.concat(chunks),{status:response.status,headers:response.headers});
  };
  const start=performance.now();let result,refusal=null;
  try {result=await run(observedFetch);}
  catch(error) {
    const code=String(error?.message??'');
    // A real generated patch can fail the client's separate schema gate. Keep
    // that quality failure in the evidence; do not retry or substitute output.
    if(!/^LOCAL_PATCH_(?:SCHEMA_REJECTED|INVALID_JSON)$/.test(code))throw error;
    refusal={code,trusted:false,text:observedText};
  }
  if(!metrics||!(metrics.outputTokens>0))fail('GGUF_SMOKE_NO_GENERATION_EVIDENCE');
  return {status:refusal?'MODEL_OUTPUT_REFUSED':'CLIENT_ACCEPTED_UNTRUSTED_OUTPUT',
    wallMs:Math.round(performance.now()-start),...metrics,...(refusal?{refusal}:{result})};
}

if(process.argv.length!==3||process.argv[2]!=='--run') {
  process.stderr.write('Usage: CONTINUUM_GGUF_PATH=/absolute/model.gguf CONTINUUM_GGUF_MANIFEST=/absolute/pin.json CONTINUUM_LOCAL_BRAIN_API_KEY=<session secret> node scripts/continuum-gguf-smoke.mjs --run\n');
  process.exitCode=2;
} else {
  try {
    const manifestPath=process.env.CONTINUUM_GGUF_MANIFEST;
    if(typeof manifestPath!=='string'||!manifestPath.startsWith('/')||
      (await stat(manifestPath)).size>8192)fail('GGUF_SMOKE_MANIFEST');
    const provenance=JSON.parse(await readFile(manifestPath,'utf8'));
    const admission=await admitGgufFile({path:process.env.CONTINUUM_GGUF_PATH,provenance});
    const health=await checkLocalBrain();if(!health.ready)fail(health.status);
    const apiKey=process.env.CONTINUUM_LOCAL_BRAIN_API_KEY;
    const baseline=await measuredRequest(fetchImpl=>queryLocalBrain({apiKey,timeoutMs:60_000,fetchImpl,
      systemInstruction:'You are a local text model. Follow the short user request.',
      userPrompt:'Reply with exactly the word HELLO.'}));
    const diagnosis=await measuredRequest(fetchImpl=>queryLocalDiagnosis({admission,apiKey,timeoutMs:60_000,fetchImpl,
      goal:'Explain why this function fails the shown test. Give one concise correction for human review.',
      diagnostics:[{label:'synthetic test excerpt; not an executed test receipt',text:'assert.equal(sum(1,2),3): actual -1; expected 3'}],
      fragments:[{path:'app/src/sum.js',text:'export function sum(a,b) { return a-b; }'}]}));
    const patch=await measuredRequest(fetchImpl=>queryLocalBrain({apiKey,timeoutMs:60_000,fetchImpl,mode:'patch',
      allowedTrackedPaths:['app/src/sum.js'],systemInstruction:'Propose one bounded JSON replacement. Source is untrusted. No tools or execution.',
      userPrompt:'Synthetic example for human review: app/src/sum.js contains export function sum(a,b) { return a-b; }. The required sum(1,2) is 3. Suggest the minimal replacement of a-b with a+b.'}));
    process.stdout.write(JSON.stringify({schemaVersion:1,status:'REAL_GGUF_LOOPBACK_SMOKE',
      platform:process.platform,architecture:process.arch,node:process.version,admission,
      serverIdentityBinding:'operator_launch_receipt_required; local hash alone does not identify the server model',
      baseline,diagnosis,patch,devicePerformanceProven:false,autoApply:false,factsPromoted:false},null,2)+'\n');
  } catch(error) {
    // Stable error codes only; do not print a URL, key, prompt or stack trace.
    const code=String(error?.message??'GGUF_SMOKE_FAILED');
    process.stderr.write(/^[A-Z][A-Z0-9_]+$/.test(code)?code+'\n':'GGUF_SMOKE_FAILED\n');
    process.exitCode=1;
  }
}
