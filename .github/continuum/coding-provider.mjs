/** Generative candidate adapter for the canonical Repair Loop; no model-provided tool actions. */
import fs from 'node:fs';import path from 'node:path';import {spawnSync} from 'node:child_process';import {fileURLToPath} from 'node:url';
import {validateCodingTask,taskFingerprint,validateCodingProposal} from './coding-task.mjs';
import {buildRepositoryContext} from './coding-context.mjs';
import {sha256Hex,parseStrict} from './development-backlog.mjs';
import {describeDeepSeekCodingRequest,validateDeepSeekConsent} from '../../scripts/continuum-deepseek-bridge.mjs';
import {MODEL_ID,PINNED_REVISION} from '../../scripts/continuum-deepseek-tier-plan.mjs';
const CLI=fileURLToPath(new URL('../../scripts/continuum-local-brain-cli.mjs',import.meta.url));
function rejectedModelOutput(code,responseSha256=null) {
 const error=Error(code);error.code=code;
 if(typeof responseSha256==='string'&&/^[a-f0-9]{64}$/.test(responseSha256))error.responseSha256=responseSha256;
 return error;
}

export function createCodingCandidateProvider({root,task,env=process.env,run=spawnSync,evidenceDir=null,remoteTeacher=null}={}){
 const spec=validateCodingTask(task),fingerprint=taskFingerprint(spec);
 // Remote use is an explicit trusted-caller choice, never an environment-only fallback.
 if(remoteTeacher!==null){
  if(!remoteTeacher||remoteTeacher.enabled!==true||typeof remoteTeacher.endpoint!=='string'||
   typeof remoteTeacher.approveRequest!=='function')throw Error('REMOTE_TEACHER_CONFIG_INVALID');
  if(evidenceDir===null)throw Error('REMOTE_DURABLE_EVIDENCE_REQUIRED');
  if(!/^[A-Za-z0-9_-]{32,128}$/.test(env.CONTINUUM_REMOTE_BRAIN_API_KEY??''))throw Error('REMOTE_AUTH_REQUIRED');
 }else if(!/^[A-Za-z0-9_-]{32,128}$/.test(env.CONTINUUM_LOCAL_BRAIN_API_KEY??''))throw Error('LOCAL_AUTH_REQUIRED');
 const source=fs.realpathSync(root);
 let evidence=null;
 if(evidenceDir!==null){
  // Reject unsafe paths BEFORE creating any directory. Check an existing
  // ancestor as well, because an external symlink may point inside source.
  if(typeof evidenceDir!=='string'||!path.isAbsolute(evidenceDir))
   throw Error('CODING_EVIDENCE_UNSAFE');
  const requested=path.resolve(evidenceDir);
  if(requested===source||requested.startsWith(source+path.sep))
   throw Error('CODING_EVIDENCE_UNSAFE');
  let existing=requested;
  while(!fs.existsSync(existing)){
   const parent=path.dirname(existing);
   if(parent===existing)throw Error('CODING_EVIDENCE_UNSAFE');
   existing=parent;
  }
  const ancestor=fs.realpathSync(existing);
  if(ancestor===source||ancestor.startsWith(source+path.sep))
   throw Error('CODING_EVIDENCE_UNSAFE');
  fs.mkdirSync(requested,{recursive:true,mode:0o700});
  evidence=fs.realpathSync(requested);
  if(evidence===source||evidence.startsWith(source+path.sep)||evidence!==requested)
   throw Error('CODING_EVIDENCE_UNSAFE');
 }
 let calls=0;
 const priorRequests=()=>{
  if(!evidence)return [];
  if(fs.realpathSync(evidence)!==evidence||!fs.lstatSync(evidence).isDirectory())throw Error('CODING_EVIDENCE_UNSAFE');
  const names=fs.readdirSync(evidence).filter(name=>/^request-.*\.json$/.test(name)).sort();
  if(names.length>spec.limits.maxModelCalls)throw Error('CODING_EVIDENCE_INVALID');
  return names.map((name,index)=>{
   const file=path.join(evidence,name),stat=fs.lstatSync(file);
   if(name!==`request-${index+1}.json`||!stat.isFile()||stat.isSymbolicLink()||stat.size>8192)
    throw Error('CODING_EVIDENCE_INVALID');
   let record;try{record=parseStrict(fs.readFileSync(file,'utf8'));}catch{throw Error('CODING_EVIDENCE_INVALID');}
   if(record?.schema!=='continuum-coding-model-request'||record.version!==1||record.request!==index+1)
    throw Error('CODING_EVIDENCE_INVALID');
   if(record.taskId!==spec.id||record.taskFingerprint!==fingerprint||record.sourceCommit!==spec.sourceCommit)
    throw Error('CODING_EVIDENCE_BINDING_MISMATCH');
   return record;
  });
 };
 const writeTrace=(number,record,{reserve=false}={})=>{
  const file=path.join(evidence,`request-${number}.json`);
  if(reserve){
   // A process loss after this reservation consumes the request. Never infer
   // first and discover EEXIST afterwards, or reset the owner budget on resume.
   let fd;
   try{fd=fs.openSync(file,'wx',0o600);fs.writeFileSync(fd,JSON.stringify(record,null,2)+'\n');fs.fsyncSync(fd);}
   finally{if(fd!==undefined)fs.closeSync(fd);}
   const dir=fs.openSync(evidence,'r');try{fs.fsyncSync(dir);}finally{fs.closeSync(dir);}
  }else{
   const pending=file+'.tmp-'+process.pid;
   try{
    fs.writeFileSync(pending,JSON.stringify(record,null,2)+'\n',{flag:'wx',mode:0o600});
    fs.renameSync(pending,file);
   }finally{fs.rmSync(pending,{force:true});}
  }
 };
 const provider=({base,chain,deadline,diagnostics=[],history=[]}={})=>{
  if(base!==spec.sourceCommit||chain!==spec.chain)throw Error('CODING_TASK_BINDING_MISMATCH');
  if(evidence)calls=priorRequests().length;
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
  // The child receives only this transport's credentials. The HTTPS transport
  // also preserves the configured proxy and CA trust, without Git/owner secrets.
  const model=remoteTeacher?MODEL_ID:(env.CONTINUUM_LOCAL_BRAIN_MODEL??'local');
  // A model identifier is inert JSON data, not a CLI argument. Preserve
  // owner-chosen local model names (including spaces and longer identifiers).
  if(typeof model!=='string'||!model.trim()||model.length>128||
     /[\x00-\x1f\x7f]/.test(model))
   throw Error('CODING_MODEL_CONFIG_INVALID');
  const childEnv=remoteTeacher?{
   CONTINUUM_REMOTE_BRAIN_API_KEY:env.CONTINUUM_REMOTE_BRAIN_API_KEY,
   NODE_OPTIONS:'',NODE_V8_COVERAGE:'',NODE_USE_ENV_PROXY:'1'
  }:{
   CONTINUUM_LOCAL_BRAIN_API_KEY:env.CONTINUUM_LOCAL_BRAIN_API_KEY,
   CONTINUUM_LOCAL_BRAIN_MODEL:model,
   NODE_OPTIONS:'',NODE_V8_COVERAGE:'',NODE_USE_ENV_PROXY:'0'
  };
  if(remoteTeacher)for(const key of ['HTTP_PROXY','HTTPS_PROXY','ALL_PROXY','NO_PROXY',
    'http_proxy','https_proxy','all_proxy','no_proxy','NODE_EXTRA_CA_CERTS','SSL_CERT_FILE','SSL_CERT_DIR'])
    if(typeof env[key]==='string')childEnv[key]=env[key];
  if(evidence){calls=priorRequests().length;if(calls>=spec.limits.maxModelCalls)throw Error('CODING_MODEL_BUDGET_EXHAUSTED');}
  const number=calls+1,started=Date.now();
  let command='coding',input={userPrompt,allowedTrackedPaths:spec.allowedPaths,timeoutMs},remoteDisclosure=null;
  if(remoteTeacher){
   const request={schema:'CONTINUUM_REMOTE_CODING_REQUEST_V1',endpoint:remoteTeacher.endpoint,jobId:spec.id,
    sourceCommit:base,taskFingerprint:fingerprint,requestIndex:number,deadline:started+timeoutMs,
    userPrompt,allowedTrackedPaths:spec.allowedPaths};
   remoteDisclosure=describeDeepSeekCodingRequest(request);
   // The owner-facing integration must disclose the exact prompt, source and
   // privacy/budget manifest. A boolean opt-in cannot authorize another payload.
   const consent=remoteTeacher.approveRequest({disclosure:remoteDisclosure,userPrompt,
    allowedPaths:[...spec.allowedPaths]});
   validateDeepSeekConsent(consent,remoteDisclosure);
   command='coding-remote';input={request,consent};
  }
  calls=number;
  const trace={schema:'continuum-coding-model-request',version:1,taskId:spec.id,taskFingerprint:fingerprint,sourceCommit:base,
   request:number,startedAt:new Date(started).toISOString(),elapsedMs:0,
   configuredModel:model,verifiedModelExecution:false,contextSha256:context.contextSha256,
   promptSha256:sha256Hex(userPrompt),exitCode:null};
  if(remoteDisclosure)Object.assign(trace,{transport:'AUTHENTICATED_HTTPS_TEACHER',modelRevision:PINNED_REVISION,
   consentRequestSha256:remoteDisclosure.requestSha256,maxCostMicrousd:0});
  if(evidence)writeTrace(number,{...trace,status:'RESERVED',rawContentStored:false},{reserve:true});
  const response=run(process.execPath,[CLI,command],{shell:false,encoding:'utf8',input:JSON.stringify(input),
   env:childEnv,timeout:timeoutMs+500,maxBuffer:40_000});
  trace.elapsedMs=Date.now()-started;trace.exitCode=response.status??null;
  // Raw code context and model output remain transient. Diagnostic evidence
  // retains hashes and byte counts, never the potentially private contents.
  const responseText=String(response.stdout??'');
  if(evidence)writeTrace(number,{...trace,status:'RESPONDED',responseSha256:sha256Hex(responseText),
    responseBytes:Buffer.byteLength(responseText,'utf8'),rawContentStored:false});
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
  if(remoteDisclosure&&(result.transport!=='AUTHENTICATED_HTTPS_TEACHER'||result.modelId!==MODEL_ID||
   result.modelRevision!==PINNED_REVISION||result.requestSha256!==remoteDisclosure.requestSha256||result.verifiedModelExecution!==false))
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
