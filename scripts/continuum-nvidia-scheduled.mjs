/**
 * Owner-approved 10-minute GitHub Actions scheduler: write-ahead reservation
 * on a dedicated research ledger branch BEFORE one NVIDIA API request.
 * No code execution from model output. No automatic patch or main merge.
 */
import fs from 'node:fs';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import {buildUltraResearchPacket,runUltraMasterReview,OWNER_CONSENT} from './continuum-nvidia-ultra-review.mjs';
import {createFollowupPacket,checkCycleBudget} from './continuum-neuro-agent.mjs';
import {claimReservedExecution} from './continuum-nvidia-execution-claim.mjs';
export const LEDGER_BRANCH='research/continuum-nvidia-automation-ledger-20261030';
export const LEDGER_PATH='docs/research/neuro-agent/nvidia-scheduled-ledger.json';
export const MAX_PER_UTC_DAY=3, MIN_INTERVAL_MS=4*3600*1000, MAX_LIFETIME=100;
const stop=s=>{throw Error(s);};
export function validateLedger(x){
 if(!x||x.schema!=='continuum-nvidia-schedule-ledger'||x.version!==1||
 !Array.isArray(x.reservations)||x.reservations.length>MAX_LIFETIME||
 x.reservations.some((r,i)=>!r||typeof r.runId!=='string'||!/^\d{1,30}$/.test(r.runId)||
 !Number.isSafeInteger(r.atMillis)||r.atMillis<0||r.sequence!==i||
 typeof r.utcDate!=='string'||r.utcDate!==new Date(r.atMillis).toISOString().slice(0,10)||
 (r.claimedAtMillis!==undefined&&(!Number.isSafeInteger(r.claimedAtMillis)||
  r.claimedAtMillis<r.atMillis||r.claimedAtMillis-r.atMillis>30*60_000))))
  stop('LEDGER_INVALID');
 if(new Set(x.reservations.map(r=>r.runId)).size!==x.reservations.length)stop('LEDGER_DUPLICATES');
 for(let i=1;i<x.reservations.length;i++)
  if(x.reservations[i].atMillis<x.reservations[i-1].atMillis)stop('LEDGER_TIME_ORDER_INVALID');
 return x;
}
export function decideReservation(ledger,{runId,nowMs}={}){
 validateLedger(ledger);
 if(typeof runId!=='string'||!/^\d{1,30}$/.test(runId)||
 !Number.isSafeInteger(nowMs)||nowMs<0)stop('RESERVATION_INVALID');
 if(ledger.reservations.some(r=>r.runId===runId))
  return Object.freeze({allowed:false,reason:'RUN_ALREADY_RESERVED'});
 const budget=checkCycleBudget(ledger.reservations.map(r=>({utcDate:r.utcDate,startedAtMs:r.atMillis})),
  nowMs,{daily:MAX_PER_UTC_DAY,total:MAX_LIFETIME,minimumMs:MIN_INTERVAL_MS});
 if(!budget.allowed)return Object.freeze({allowed:false,reason:budget.reason});
 const sequence=ledger.reservations.length;
 const reservation=Object.freeze({runId,atMillis:nowMs,
  utcDate:new Date(nowMs).toISOString().slice(0,10),sequence});
 return Object.freeze({allowed:true,sequence,ledger:{...ledger,
  reservations:[...ledger.reservations,reservation]}});
}
function api({owner,repo,token,fetchImpl=globalThis.fetch}){
 if(!/^[A-Za-z0-9_.-]{1,100}$/.test(owner)||
 !/^[A-Za-z0-9_.-]{1,100}$/.test(repo)||
 typeof token!=='string'||token.length<20||
 typeof fetchImpl!=='function')stop('GITHUB_AUTH_INVALID');
 const base='https://api.github.com/repos/'+owner+'/'+repo+'/contents/'+LEDGER_PATH;
 return async (method,body)=>{
  const url=base+(method==='GET'?'?ref='+encodeURIComponent(LEDGER_BRANCH):'');
  const r=await fetchImpl(url,{method,redirect:'error',headers:{
   Authorization:'Bearer '+token,Accept:'application/vnd.github+json',
   'X-GitHub-Api-Version':'2022-11-28','Content-Type':'application/json'
  },...(body?{body:JSON.stringify(body)}:{})});
  if(!r?.ok)stop('GITHUB_LEDGER_HTTP_'+String(r?.status??'UNKNOWN'));
  return await r.json();
 };
}
export async function reserveGithubBudget({repoFull,token,runId,
 nowMs=Date.now(),fetchImpl=globalThis.fetch}={}){
 const [owner,repo]=String(repoFull).split('/');
 const request=api({owner,repo,token,fetchImpl});
 const original=await request('GET');
 if(typeof original?.sha!=='string'||!/^[0-9a-f]{40}$/.test(original.sha)||
 original.encoding!=='base64'||typeof original.content!=='string')
  stop('GITHUB_LEDGER_INVALID');
 let ledger;
 try{ledger=JSON.parse(Buffer.from(original.content.replace(/\s+/g,''),'base64').toString('utf8'));}
 catch{stop('GITHUB_LEDGER_INVALID');}
 const decision=decideReservation(ledger,{runId,nowMs});
 if(!decision.allowed)return decision;
 const content=Buffer.from(JSON.stringify(decision.ledger,null,2)+'\n').toString('base64');
 await request('PUT',{message:'research(neuro): reserve NVIDIA scheduled request '+decision.sequence,
  content,sha:original.sha,branch:LEDGER_BRANCH});
 return Object.freeze({allowed:true,sequence:decision.sequence,reason:'RESERVED_DURABLY'});
}
/**
 * Compare-and-swap a single-use execution claim onto the dedicated ledger
 * branch BEFORE any model API request. A failed GET/PUT or SHA conflict is
 * fail-closed. This protects against unreserved calls and duplicate execution
 * attempts, but cannot recover a provider call after a process crash.
 */
export async function claimGithubExecution({repoFull,token,runId,sequence,
 nowMs=Date.now(),fetchImpl=globalThis.fetch}={}){
 const [owner,repo]=String(repoFull).split('/');
 const request=api({owner,repo,token,fetchImpl});
 const original=await request('GET');
 if(typeof original?.sha!=='string'||!/^[0-9a-f]{40}$/.test(original.sha)||
    original.encoding!=='base64'||typeof original.content!=='string')
   stop('GITHUB_LEDGER_INVALID');
 let ledger;
 try{ledger=JSON.parse(Buffer.from(original.content.replace(/\s+/g,''),'base64').toString('utf8'));}
 catch{stop('GITHUB_LEDGER_INVALID');}
 const claimed=claimReservedExecution(validateLedger(ledger),{runId,sequence,nowMs});
 const content=Buffer.from(JSON.stringify(claimed,null,2)+'\n').toString('base64');
 await request('PUT',{message:'research(neuro): claim NVIDIA execution '+sequence,
  content,sha:original.sha,branch:LEDGER_BRANCH});
 return Object.freeze({claimed:true,sequence});
}

export async function executeReservedResearch({sequence,apiKey,outputDir,repoRoot,
 repoFull,token,runId,nowMs=Date.now(),fetchImpl=globalThis.fetch,
 callReview=runUltraMasterReview}={}){
 if(!Number.isSafeInteger(sequence)||sequence<0||sequence>=MAX_LIFETIME)stop('SEQUENCE_INVALID');
 if(!outputDir||typeof outputDir!=='string'||!path.isAbsolute(outputDir))stop('OUTPUT_DIR_INVALID');
 if(!/^nvapi-[A-Za-z0-9_-]{12,256}$/.test(apiKey??''))stop('NVIDIA_AUTH_REQUIRED');
 await claimGithubExecution({repoFull,token,runId,sequence,nowMs,fetchImpl});
 const packet=createFollowupPacket(buildUltraResearchPacket(repoRoot),sequence);
 return callReview({packet,apiKey,consent:OWNER_CONSENT,outputDirectory:outputDir,root:repoRoot});
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 try{
  if(process.argv[2]==='--reserve'){
   const result=await reserveGithubBudget({repoFull:process.env.GITHUB_REPOSITORY,
    token:process.env.GITHUB_TOKEN,runId:process.env.GITHUB_RUN_ID});
   if(!process.env.GITHUB_OUTPUT)stop('GITHUB_OUTPUT_REQUIRED');
   fs.appendFileSync(process.env.GITHUB_OUTPUT,
    'allowed='+String(result.allowed)+'\n'+
    'sequence='+(result.allowed?result.sequence:'')+'\n');
   process.stdout.write(JSON.stringify({allowed:result.allowed,reason:result.reason??null})+'\n');
  }else if(process.argv[2]==='--execute'){
   const out=await executeReservedResearch({sequence:Number(process.env.CONTINUUM_RESERVED_SEQUENCE),
    apiKey:process.env.NVIDIA_API_KEY,outputDir:process.env.CONTINUUM_RESEARCH_OUTPUT_DIR,
    repoFull:process.env.GITHUB_REPOSITORY,token:process.env.GITHUB_TOKEN,
    runId:process.env.GITHUB_RUN_ID,
    repoRoot:path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..')});
   process.stdout.write(JSON.stringify({status:out.status,
    responseSha256:out.responseSha256,elapsedMs:out.elapsedMs,
    reportBytes:out.reportBytes,testsExecuted:out.testsExecuted,
    ownerReviewRequired:out.ownerReviewRequired})+'\n');
  }else stop('USAGE_RESERVE_OR_EXECUTE');
 }catch(e){
  const msg=String(e?.message??'');
  const safe=/^(GITHUB_LEDGER_HTTP_\d{3}|GITHUB_LEDGER_INVALID|NVIDIA_AUTH_REQUIRED|SEQUENCE_INVALID|OUTPUT_DIR_INVALID|USAGE_RESERVE_OR_EXECUTE|GITHUB_OUTPUT_REQUIRED|LEDGER_INVALID|LEDGER_DUPLICATES|LEDGER_TIME_ORDER_INVALID|RESERVATION_INVALID|EXECUTION_CLAIM_INVALID|RESERVATION_NOT_FOUND|RESERVATION_ALREADY_CLAIMED|RESERVATION_EXPIRED)$/.test(msg)?msg:'SAFE_FAILURE';
  process.stderr.write('SCHEDULED_NVIDIA_RUN_STOPPED: '+safe+'\n');
  process.exitCode=2;
 }
}