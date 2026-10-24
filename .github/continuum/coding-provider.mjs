/** Generative candidate adapter for the canonical Repair Loop; no model-provided tool actions. */
import fs from 'node:fs';import path from 'node:path';import {spawnSync} from 'node:child_process';import {fileURLToPath} from 'node:url';
import {validateCodingTask,taskFingerprint,validateCodingProposal} from './coding-task.mjs';
import {buildRepositoryContext} from './coding-context.mjs';
import {sha256Hex} from './development-backlog.mjs';
const CLI=fileURLToPath(new URL('../../scripts/continuum-local-brain-cli.mjs',import.meta.url));

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
  const childEnv={...env,NODE_OPTIONS:'',NODE_V8_COVERAGE:'',NODE_USE_ENV_PROXY:'0'};delete childEnv.NODE_TEST_CONTEXT;
  const number=++calls,started=Date.now();
  const response=run(process.execPath,[CLI,'coding'],{shell:false,encoding:'utf8',input:JSON.stringify({userPrompt,allowedTrackedPaths:spec.allowedPaths,timeoutMs}),
   env:childEnv,timeout:timeoutMs+500,maxBuffer:40_000});
  const trace={schema:'continuum-coding-model-request',version:1,taskId:spec.id,taskFingerprint:fingerprint,sourceCommit:base,
   request:number,startedAt:new Date(started).toISOString(),elapsedMs:Date.now()-started,
   configuredModel:env.CONTINUUM_LOCAL_BRAIN_MODEL??'local',verifiedModelExecution:false,contextSha256:context.contextSha256,
   promptSha256:sha256Hex(userPrompt),exitCode:response.status??null};
  if(evidence){fs.writeFileSync(path.join(evidence,`request-${number}.json`),JSON.stringify({...trace,userPrompt,response:response.stdout??''},null,2)+'\n',{flag:'wx',mode:0o600});}
  // CLI stderr is never trusted as an action. Extract only a finite protocol code and
  // digest; the raw stderr, prompt and model output never enter repair feedback.
  const failure=(code,fingerprint=null)=>{
    const error=Error(code);error.code=code;
    if(/^[a-f0-9]{64}$/.test(fingerprint??''))error.responseFingerprint=fingerprint;
    return error;
  };
  if(response.status!==0||response.error||response.signal){
    const message=String(response.stderr??'').slice(0,400);
    const matched=/^LOCAL_MODEL_UNAVAILABLE_OR_INVALID: (CODING_NOOP_PATCH|CODING_RESPONSE_INVALID) ([a-f0-9]{64})\\s*$/m.exec(message);
    throw matched?failure(matched[1],matched[2]):failure('CODING_MODEL_UNAVAILABLE');
  }
  let result;try{result=JSON.parse(response.stdout);}catch{
    throw failure('CODING_REPLY_INVALID',sha256Hex(String(response.stdout??'')));
  }
  if(result?.mode!=='LOCAL_CODING_PATCH_PROPOSAL_V1'||result.trusted!==false||result.autoApply!==false)
    throw failure('CODING_REPLY_INVALID',sha256Hex(String(response.stdout??'')));
  let proposal;
  try{proposal=validateCodingProposal(result.proposal,spec);}
  catch{throw failure('CODING_PROPOSAL_INVALID',sha256Hex(String(response.stdout??'')));}
  return {mode:result.mode,trusted:false,autoApply:false,...proposal,localRequests:1,
   context:{commit:base,contextSha256:context.contextSha256,ranges:context.ranges.map(({text,...r})=>r)},trace};
 };
 Object.defineProperties(provider,{taskSpec:{value:spec},allowedPaths:{value:spec.allowedPaths},taskFingerprint:{value:fingerprint}});
 return provider;
}
