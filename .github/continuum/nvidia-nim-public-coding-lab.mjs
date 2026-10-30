/**
 * NVIDIA NIM PUBLIC SYNTHETIC CODING LAB (research-only).
 * Reuses the canonical coding-task/proposal schemas; never sends repository
 * sources, real owner memory, credentials or local logs to NVIDIA.
 * Candidate patches are DATA, never executed or promoted as CCC1 successes.
 */
import crypto from 'node:crypto';
import {validateCodingTask,validateCodingProposal,taskFingerprint} from './coding-task.mjs';
import {parseStrict} from './development-backlog.mjs';

const ENDPOINT='https://integrate.api.nvidia.com/v1/chat/completions';
const MODEL='nvidia/nemotron-3.5-lightning-30b-a3b';
const SHA=s=>crypto.createHash('sha256').update(s,'utf8').digest('hex');
const CASES=Object.freeze([
 Object.freeze({
  file:'lib/continuum-utf8.mjs',
  source:"export function withinBudget(text, maxBytes) {\n  return text.length <= maxBytes;\n}\n",
  gold:"export function withinBudget(text, maxBytes) {\n  return Buffer.byteLength(text, 'utf8') <= maxBytes;\n}\n",
  requirement:'UTF-8 byte length must be used; non-ASCII input must not bypass the byte limit.'
 }),
 Object.freeze({
  file:'lib/continuum-memory-time.mjs',
  source:"export function ownerFactAt(events, atMillis) {\n  const eligible = events.filter(e => e.validFrom <= atMillis);\n  return eligible.sort((a, b) => a.validFrom - b.validFrom)[0] ?? null;\n}\n",
  gold:"export function ownerFactAt(events, atMillis) {\n  const eligible = events.filter(e => e.validFrom <= atMillis && (e.revokedAt === null || e.revokedAt > atMillis));\n  return eligible.sort((a, b) => b.validFrom - a.validFrom)[0] ?? null;\n}\n",
  requirement:'At a historical timestamp, ignore already revoked facts and prefer the newest active fact; revokedAt is null or a timestamp.'
 }),
 Object.freeze({
  file:'lib/continuum-gpu-billing.mjs',
  source:"export function billedSeconds(runtimeSeconds, billingQuantumSeconds) {\n  return Math.floor(runtimeSeconds / billingQuantumSeconds) * billingQuantumSeconds;\n}\n",
  gold:"export function billedSeconds(runtimeSeconds, billingQuantumSeconds) {\n  return Math.ceil(runtimeSeconds / billingQuantumSeconds) * billingQuantumSeconds;\n}\n",
  requirement:'Billing must round UP to the next whole billing interval, never down.'
 })
]);
export const NVIDIA_CODING_LAB_MODEL=MODEL;
export const NVIDIA_CODING_LAB_ENDPOINT=ENDPOINT;

/** Frozen public fixtures. The synthetic zero-commit MUST NOT be used for CCC1. */
export function makeNvidiaPublicCodingChallenge(){
 const task=validateCodingTask({
  schema:'continuum-coding-task',version:1,id:'nvidia-public-repair-lab',
  sourceCommit:'0'.repeat(40),kind:'BUGFIX',
  goal:'Repair three independent public synthetic Continuum-like bugs without extra changes.',
  allowedPaths:CASES.map(c=>c.file),
  acceptance:CASES.map(c=>c.requirement),
  chain:'node',limits:{maxModelCalls:1,maxContextChars:6000}
 });
 const publicFiles=CASES.map(({file,source,requirement})=>({file,source,requirement}));
 const prompt=JSON.stringify({
  role:'public synthetic software repair benchmark; no owner or repository source',
  instructions:[
   'Analyze the three independent bugs and return ONLY one JSON object, no Markdown.',
   'JSON must have exactly the fields plan (array of strings), hypothesis (string), patches (array).',
   'Return exactly three patches, one for each listed file.',
   'Each patch must have exactly file, oldText and newText strings.',
   'oldText must reproduce the ENTIRE original source exactly, including trailing newline.',
   'newText must contain the ENTIRE replacement source and preserve unrelated code.',
   'Do not include command execution, dependency changes, permissions or extra files.'
  ],
  files:publicFiles
 });
 return Object.freeze({task,prompt,promptSha256:SHA(prompt),
  expectedFiles:Object.freeze(CASES.map(c=>c.file)),
  synthetic:true,containsOwnerData:false,sourceCommitIsReal:false});
}
export function evaluateNvidiaPublicCodingProposal(candidate){
 const challenge=makeNvidiaPublicCodingChallenge();
 let proposal;
 try{proposal=validateCodingProposal(candidate,challenge.task);}
 catch{return Object.freeze({schemaAccepted:false,allThreePresent:false,
   exactGoldenPatches:0,totalPatches:3,independentExecutionVerified:false,
   ccc1LearningEligible:false});}
 const patches=new Map(proposal.patches.map(p=>[p.file,p]));
 const allThreePresent=CASES.every(c=>patches.has(c.file));
 const exactGoldenPatches=CASES.filter(c=>{
  const p=patches.get(c.file);
  return p?.oldText===c.source&&p.newText===c.gold;
 }).length;
 return Object.freeze({schemaAccepted:true,allThreePresent,exactGoldenPatches,
  totalPatches:3,allExactGolden:allThreePresent&&exactGoldenPatches===3,
  independentExecutionVerified:false,ccc1LearningEligible:false});
}

async function readBoundedResponse(response,maxBytes){
 if(!response?.body?.getReader)throw Error('NVIDIA_RESPONSE_INVALID');
 const reader=response.body.getReader();
 const chunks=[];let size=0;
 try{
  for(;;){
   const {done,value}=await reader.read();
   if(done)break;
   if(!(value instanceof Uint8Array))throw Error('NVIDIA_RESPONSE_INVALID');
   size+=value.byteLength;
   if(size>maxBytes)throw Error('NVIDIA_RESPONSE_TOO_LARGE');
   chunks.push(value);
  }
 }catch(error){await reader.cancel().catch(()=>{});throw error;}
 const data=new Uint8Array(size);let i=0;
 for(const c of chunks){data.set(c,i);i+=c.length;}
 return new TextDecoder('utf-8',{fatal:true}).decode(data);
}

/** One opt-in network request. Returns scores only; never writes candidate code. */
export async function runNvidiaPublicCodingLab({
 apiKey,fetchImpl=globalThis.fetch,now=()=>Date.now(),timeoutMs=90000
}={}){
 if(typeof apiKey!=='string'||!/^nvapi-[A-Za-z0-9_-]{12,256}$/.test(apiKey))
  throw Error('NVIDIA_AUTH_REQUIRED');
 if(typeof fetchImpl!=='function'||!Number.isSafeInteger(timeoutMs)||
    timeoutMs<1000||timeoutMs>120000)throw Error('NVIDIA_CODING_LAB_CONFIG_INVALID');
 const challenge=makeNvidiaPublicCodingChallenge();
 const body=JSON.stringify({model:MODEL,temperature:0,
  chat_template_kwargs:{enable_thinking:false},max_tokens:4096,stream:false,
  messages:[{role:'system',content:'Respond with JSON only; do not output any private material.'},
   {role:'user',content:challenge.prompt}]
 });
 if(Buffer.byteLength(body,'utf8')>16000)throw Error('NVIDIA_REQUEST_TOO_LARGE');
 const controller=new AbortController();
 const timeout=setTimeout(()=>controller.abort(),timeoutMs);
 const start=now();
 try{
  const response=await fetchImpl(ENDPOINT,{method:'POST',redirect:'error',
   signal:controller.signal,headers:{
    Authorization:'Bearer '+apiKey,
    'Content-Type':'application/json',Accept:'application/json'
   },body});
  if(!response?.ok)throw Error('NVIDIA_ENDPOINT_UNAVAILABLE_HTTP_'+String(response?.status??'UNKNOWN'));
  const raw=await readBoundedResponse(response,90000);
  let packet;
  try{packet=parseStrict(raw);}catch{throw Error('NVIDIA_RESPONSE_INVALID');}
  if(packet?.choices?.[0]?.finish_reason==='length')throw Error('NVIDIA_RESPONSE_TRUNCATED');
  if(packet?.model!==MODEL||!Array.isArray(packet.choices)||packet.choices.length!==1||
     typeof packet.choices[0]?.message?.content!=='string')
   throw Error('NVIDIA_RESPONSE_INVALID');
  const answer=packet.choices[0].message.content.trim();
  if(Buffer.byteLength(answer,'utf8')>24000)throw Error('NVIDIA_RESPONSE_TOO_LARGE');
  let candidate;
  try{candidate=parseStrict(answer);}catch{throw Error('NVIDIA_PROPOSAL_FORMAT_INVALID');}
  const assessment=evaluateNvidiaPublicCodingProposal(candidate);
  const elapsedMs=Math.max(0,now()-start);
  const tokens=packet?.usage?.completion_tokens;
  const tokensValid=Number.isSafeInteger(tokens)&&tokens>=0&&tokens<=1000000;
  return Object.freeze({
   schema:'continuum-nvidia-public-coding-lab-result-v1',
   requestedModel:MODEL,endpoint:ENDPOINT,
   promptSha256:challenge.promptSha256,
   taskFingerprint:taskFingerprint(challenge.task),
   syntheticCases:3,...assessment,
   elapsedMs,
   completionTokens:tokensValid?tokens:null,
   measuredOutputTokensPerSecond:tokensValid&&elapsedMs>0?
    Math.round(tokens*100000/elapsedMs)/100:null,
   rawPromptStored:false,rawCandidateStored:false,
   ownerDataTransmitted:false,provisionsGpu:false,
   updatesLocalBrain:false,verifiedPhysicalGpu:false
  });
 }finally{clearTimeout(timeout);}
}