/** Generative candidate adapter for the canonical Repair Loop; no model-provided tool actions. */
import fs from 'node:fs';import path from 'node:path';import {spawnSync} from 'node:child_process';import {fileURLToPath} from 'node:url';
import {validateCodingTask,taskFingerprint,validateCodingProposal} from './coding-task.mjs';
import {buildRepositoryContext} from './coding-context.mjs';
import {sha256Hex} from './development-backlog.mjs';
const CLI=fileURLToPath(new URL('../../scripts/continuum-local-brain-cli.mjs',import.meta.url));
function rejectedModelOutput(code,responseSha256=null) {
 const error=Error(code);error.code=code;
 if(typeof responseSha256==='string'&&/^[a-f0-9]{64}$/.test(responseSha256))error.responseSha256=responseSha256;
 return error;
}

export function createCodingCandidateProvider({root,task,env=process.env,run=spawnSync,evidenceDir=null}={}){
 const spec=validateCodingTask(task),fingerprint=taskFingerprint(spec);
 if(!/^[A-Za-z0-9_-]{32,128}$/.test(env.CONTINUUM_LOCAL_BRAIN_API_KEY??''))throw Error('LOCAL_AUTH_REQUIRED');
 const source=fs.realpathSync(root);
 let evidence=null;
 if(evidenceDir!==null){
  fs.mkdirSync(evidenceDir,{recursive:true,mode:0o700});evidence=fs.realpathSync(evidenceDir);
  if(evidence===source||evidence.startsWith(source+path.sep)||evidence!==path.resolve(evidenceDir))throw Error('CODING_EVIDENCE_UNSAFE');
 }
 let calls=0;
 const provider=({base,chain,deadline,diagnostics=[],history=[]}={})=>{
  if(base!==spec.sourceCommit||chain!==spec.chain)throw Error('CODING_TASK_BINDING_MISMATCH');
  if(calls>=spec.limits.maxModelCalls)throw Error('CODING_MODEL_BUDGET_EXHAUSTED');
  if(!Number.isSafeInteger(deadline)||deadline-Date.now()<600)throw Error('CODING_DEADLINE_EXHAUSTED');
  const context=buildRepositoryContext({root:source,commit:base,allowedPaths:spec.allowedPaths,
   maxChars:spec.limits.maxContextChars,diagnostics,query:spec.goal,deadline});
  const userPrompt=JSON.stringify({task:{id:spec.id,fingerprint,kind:spec.kind,goal:spec.goal,acceptance:spec.acceptance},
   diagnostics:diagnostics.slice(0,8).map(d=>({file:String(d.file??'').slice(0,180),line:Number.isSafeInteger(d.line)?d.line:null,
    kind:String(d.kind??'').slice(0,80),message:String(d.message??'').slice(0,500)})),
   history:history.slice(-3).map(h=>({candidate:String(h.candidate??'').slice(0,64),outcome:String(h.outcome??'').slice(0,48),
    hypothesis:String(h.hypothesis??'').slice(0,600),diagnostic:String(h.diagnostic??'').slice(0,1000)})),context});
  if(userPrompt.length>28000)throw Error('CODING_PROMPT_BOUNDS');
  const timeoutMs=Math.min(60_000,deadline-Date.now()-500);
  if(timeoutMs<100)throw Error('CODING_DEADLINE_EXHAUSTED');
  // This local subprocess only needs its own bounded model configuration.
  // Never inherit GitHub, Render, owner-secret, proxy or tracing credentials.
  const model=env.CONTINUUM_LOCAL_BRAIN_MODEL??'local';
  if(typeof model!=='string'||model.length>100||!/^[A-Za-z0-9_.:/-]+$/.test(model))
   throw Error('CODING_MODEL_CONFIG_INVALID');
  const childEnv={
   CONTINUUM_LOCAL_BRAIN_API_KEY:env.CONTINUUM_LOCAL_BRAIN_API_KEY,
   CONTINUUM_LOCAL_BRAIN_MODEL:model,
   NODE_OPTIONS:'',NODE_V8_COVERAGE:'',NODE_USE_ENV_PROXY:'0'
  };
  const number=++calls,started=Date.now();
  const response=run(process.execPath,[CLI,'coding'],{shell:false,encoding:'utf8',input:JSON.stringify({userPrompt,allowedTrackedPaths:spec.allowedPaths,timeoutMs}),
   env:childEnv,timeout:timeoutMs+500,maxBuffer:40_000});
  const trace={schema:'continuum-coding-model-request',version:1,taskId:spec.id,taskFingerprint:fingerprint,sourceCommit:base,
   request:number,startedAt:new Date(started).toISOString(),elapsedMs:Date.now()-started,
   configuredModel:model,verifiedModelExecution:false,contextSha256:context.contextSha256,
   promptSha256:sha256Hex(userPrompt),exitCode:response.status??null};
  // Raw code context and model output remain transient. Diagnostic evidence
  // retains hashes and byte counts, never the potentially private contents.
  const responseText=String(response.stdout??'');
  if(evidence)fs.writeFileSync(path.join(evidence,`request-${number}.json`),
   JSON.stringify({...trace,responseSha256:sha256Hex(responseText),
    responseBytes:Buffer.byteLength(responseText,'utf8'),rawContentStored:false},null,2)+'\n',
   {flag:'wx',mode:0o600});
  if(response.status!==0||response.error||response.signal){
   // The trusted local CLI reports only a fixed rejection code and SHA-256,
   // never the unsafe model response, prompt, token or arbitrary stderr text.
   const rejected=/^LOCAL_MODEL_UNAVAILABLE_OR_INVALID: (CODING_RESPONSE_INVALID|CODING_NOOP_PATCH):([a-f0-9]{64})$/
    .exec(String(response.stderr??'').trim());
   if(rejected)throw rejectedModelOutput(rejected[1],rejected[2]);
   throw rejectedModelOutput('CODING_MODEL_UNAVAILABLE');
  }
  const responseSha256=sha256Hex(String(response.stdout??''));
  let result;try{result=JSON.parse(response.stdout);}catch{throw rejectedModelOutput('CODING_REPLY_INVALID',responseSha256);}
  if(result?.mode!=='LOCAL_CODING_PATCH_PROPOSAL_V1'||result.trusted!==false||result.autoApply!==false)
   throw rejectedModelOutput('CODING_REPLY_INVALID',responseSha256);
  let proposal;
  try{proposal=validateCodingProposal(result.proposal,spec);}
  catch{throw rejectedModelOutput('CODING_PROPOSAL_INVALID',responseSha256);}
  return {mode:result.mode,trusted:false,autoApply:false,...proposal,localRequests:1,
   context:{commit:base,contextSha256:context.contextSha256,ranges:context.ranges.map(({text,...r})=>r)},trace};
 };
 Object.defineProperties(provider,{taskSpec:{value:spec},allowedPaths:{value:spec.allowedPaths},taskFingerprint:{value:fingerprint}});
 return provider;
}
