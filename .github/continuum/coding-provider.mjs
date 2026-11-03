/** Generative candidate adapter for the canonical Repair Loop; no model-provided tool actions. */
import fs from 'node:fs';import path from 'node:path';import {spawnSync} from 'node:child_process';import {fileURLToPath} from 'node:url';
import {randomBytes,createHmac,timingSafeEqual} from 'node:crypto';
import {getReviewedLocalModelPair} from '../../scripts/continuum-local-model-pins.mjs';
import {serializeLocalCodingPrompt} from '../../scripts/continuum-local-model-observation.mjs';
import {buildCodingRequest} from '../../scripts/continuum-local-brain.mjs';
import {validateCodingTask,taskFingerprint,validateCodingProposal} from './coding-task.mjs';
import {buildRepositoryContext} from './coding-context.mjs';
import {sha256Hex,parseStrict,canonical} from './development-backlog.mjs';
import {describeDeepSeekCodingRequest,validateDeepSeekConsent,REMOTE_FAILURE_CODES,DEEPSEEK_OFFLINE_FALLBACK} from '../../scripts/continuum-deepseek-bridge.mjs';
import {MODEL_ID,PINNED_REVISION} from '../../scripts/continuum-deepseek-tier-plan.mjs';
const trustedObservations=new WeakSet();
export const isTrustedLocalModelObservation=observation=>!!observation&&trustedObservations.has(observation);
const CLI=fileURLToPath(new URL('../../scripts/continuum-local-brain-cli.mjs',import.meta.url));
function rejectedModelOutput(code,responseSha256=null) {
 const error=Error(code);error.code=code;
 if(typeof responseSha256==='string'&&/^[a-f0-9]{64}$/.test(responseSha256))error.responseSha256=responseSha256;
 return error;
}

export function createCodingCandidateProvider({root,task,env=process.env,run=spawnSync,evidenceDir=null,remoteTeacher=null,modelTimeoutMs=60_000,technicalRecall='',requireLocalModelObservation=false}={}){
 // Trusted caller budget, never an instruction supplied by a model. The task's
 // remaining overall deadline and durable call count still take precedence.
 if(!Number.isSafeInteger(modelTimeoutMs)||modelTimeoutMs<100||modelTimeoutMs>300_000)
  throw Error('CODING_MODEL_TIME_BUDGET_INVALID');
 if(typeof requireLocalModelObservation!=='boolean')throw Error('CODING_MODEL_OBSERVATION_OPTION_INVALID');
 const strictObservation=requireLocalModelObservation||env.CONTINUUM_MOMEGA1==='1';
 if(strictObservation&&remoteTeacher!==null)throw Error('CODING_LOCAL_MODEL_REQUIRED');
 if(typeof technicalRecall!=='string'||technicalRecall.length>4096||/[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]/.test(technicalRecall))
  throw Error('CODING_TECHNICAL_RECALL_INVALID');
 const spec=validateCodingTask(task),fingerprint=taskFingerprint(spec);
 // Retrieval contributes historical hints only. Reserve its serialized bytes
 // inside the existing context budget and preserve >=2048 source-context chars.
 let recallText=technicalRecall,recallData=null;
 const recallBudget=Math.max(0,spec.limits.maxContextChars-2048);
 while(recallText){
  const candidate={trusted:false,kind:'HISTORICAL_TECHNICAL_HINTS',text:recallText};
  if(JSON.stringify(candidate).length<=recallBudget){recallData=Object.freeze(candidate);break;}
  recallText=recallText.slice(0,Math.max(0,recallText.length-128));
 }
 const recallChars=recallData?JSON.stringify(recallData).length:0;
 // Remote use is an explicit trusted-caller choice, never an environment-only fallback.
 if(remoteTeacher!==null){
  if(!remoteTeacher||remoteTeacher.enabled!==true||typeof remoteTeacher.endpoint!=='string'||
   typeof remoteTeacher.approveRequest!=='function')throw Error('REMOTE_TEACHER_CONFIG_INVALID');
  if(evidenceDir===null)throw Error('REMOTE_DURABLE_EVIDENCE_REQUIRED');
  if(!/^[A-Za-z0-9_-]{32,128}$/.test(env.CONTINUUM_REMOTE_BRAIN_API_KEY??''))throw Error('REMOTE_AUTH_REQUIRED');
 }
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
   let fd;
   try{
    fd=fs.openSync(pending,'wx',0o600);
    fs.writeFileSync(fd,JSON.stringify(record,null,2)+'\n');fs.fsyncSync(fd);
    fs.closeSync(fd);fd=undefined;
    fs.renameSync(pending,file);
    const dir=fs.openSync(evidence,'r');try{fs.fsyncSync(dir);}finally{fs.closeSync(dir);}
   }finally{if(fd!==undefined)fs.closeSync(fd);fs.rmSync(pending,{force:true});}
  }
 };
 const provider=({base,chain,deadline,diagnostics=[],history=[]}={})=>{
  if(base!==spec.sourceCommit||chain!==spec.chain)throw Error('CODING_TASK_BINDING_MISMATCH');
  if(evidence)calls=priorRequests().length;
  if(calls>=spec.limits.maxModelCalls)throw Error('CODING_MODEL_BUDGET_EXHAUSTED');
  if(!Number.isSafeInteger(deadline)||deadline-Date.now()<600)throw Error('CODING_DEADLINE_EXHAUSTED');
  const context=buildRepositoryContext({root:source,commit:base,allowedPaths:spec.allowedPaths,
   maxChars:spec.limits.maxContextChars-recallChars,diagnostics,query:spec.goal,deadline});
  const userPrompt=JSON.stringify({...(recallData?{technicalRecall:recallData}:{}),task:{id:spec.id,fingerprint,kind:spec.kind,goal:spec.goal,acceptance:spec.acceptance},
   diagnostics:diagnostics.slice(0,8).map(d=>({file:String(d.file??'').slice(0,180),line:Number.isSafeInteger(d.line)?d.line:null,
    kind:String(d.kind??'').slice(0,80),message:String(d.message??'').slice(0,500)})),
   history:history.slice(-3).map(h=>({candidate:String(h.candidate??'').slice(0,64),outcome:String(h.outcome??'').slice(0,48),
    hypothesis:String(h.hypothesis??'').slice(0,600),diagnostic:String(h.diagnostic??'').slice(0,1000)})),context});
  if(userPrompt.length>28000)throw Error('CODING_PROMPT_BOUNDS');
  const timeoutMs=Math.min(remoteTeacher?60_000:modelTimeoutMs,deadline-Date.now()-500);
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
   ...(run===spawnSync?{}:{CONTINUUM_LOCAL_BRAIN_API_KEY:env.CONTINUUM_LOCAL_BRAIN_API_KEY}),
   CONTINUUM_LOCAL_BRAIN_MODEL:model,
   NODE_OPTIONS:'',NODE_V8_COVERAGE:'',NODE_USE_ENV_PROXY:'0'
  };
  // The fixed CLI validates these explicit owner claims before inference.
  // Dropping them made every approved GGUF task fail admission even when
  // configured correctly. No model selection or consent is invented here.
  if(!remoteTeacher)for(const key of ['CONTINUUM_LOCAL_BRAIN_RUNTIME','CONTINUUM_LOCAL_BRAIN_MODEL_FORMAT',
    'CONTINUUM_LOCAL_BRAIN_MODEL_SHA256','CONTINUUM_ALLOW_ALTERNATE_CODING_MODEL',
    'CONTINUUM_LOCAL_BRAIN_MODEL_PATH','CONTINUUM_LOCAL_BRAIN_MODEL_MANIFEST',
    'CONTINUUM_LOCAL_BRAIN_RUNTIME_PATH','CONTINUUM_LOCAL_BRAIN_RUNTIME_SHA256','CONTINUUM_LOCAL_BRAIN_RUNTIME_KIND'])
   if(typeof env[key]==='string')childEnv[key]=env[key];
  if(remoteTeacher)for(const key of ['HTTP_PROXY','HTTPS_PROXY','ALL_PROXY','NO_PROXY',
    'http_proxy','https_proxy','all_proxy','no_proxy','NODE_EXTRA_CA_CERTS','SSL_CERT_FILE','SSL_CERT_DIR'])
    if(typeof env[key]==='string')childEnv[key]=env[key];
  if(evidence){calls=priorRequests().length;if(calls>=spec.limits.maxModelCalls)throw Error('CODING_MODEL_BUDGET_EXHAUSTED');}
  const number=calls+1,started=Date.now();
  const observationKey=randomBytes(32).toString('hex');
  const binding={executionId:randomBytes(32).toString('hex'),taskId:spec.id,sourceCommit:base,taskFingerprint:fingerprint,
   requestIndex:number,contextSha256:context.contextSha256,promptSha256:sha256Hex(userPrompt)};
  let command='coding',input={userPrompt,allowedTrackedPaths:spec.allowedPaths,timeoutMs,binding,observationKey},remoteDisclosure=null;
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
   promptSha256:sha256Hex(userPrompt),modelTimeoutMs:timeoutMs,exitCode:null};
  if(recallData)Object.assign(trace,{technicalRecallSha256:sha256Hex(recallData.text),technicalRecallChars:recallData.text.length});
  if(remoteDisclosure)Object.assign(trace,{transport:'AUTHENTICATED_HTTPS_TEACHER',modelRevision:PINNED_REVISION,
   consentRequestSha256:remoteDisclosure.requestSha256,maxCostMicrousd:0});
  if(evidence)writeTrace(number,{...trace,status:'RESERVED',rawContentStored:false},{reserve:true});
  const response=run(process.execPath,[CLI,command],{shell:false,encoding:'utf8',input:JSON.stringify(input),
   env:childEnv,timeout:timeoutMs+500,maxBuffer:40_000});
  trace.elapsedMs=Date.now()-started;trace.exitCode=response.status??null;
  const remoteFailure=remoteTeacher&&/^REMOTE_MODEL_UNAVAILABLE_OR_INVALID: (REMOTE_[A-Z_]+)$/
   .exec(String(response.stderr??'').trim());
  const remoteFailureCode=REMOTE_FAILURE_CODES.includes(remoteFailure?.[1])?remoteFailure[1]:'REMOTE_UNAVAILABLE';
  if(remoteTeacher&&(response.status!==0||response.error||response.signal))trace.remoteFailureCode=remoteFailureCode;
  // Raw code context and model output remain transient. Diagnostic evidence
  // retains hashes and byte counts, never the potentially private contents.
  const responseText=String(response.stdout??'');
  const responseSha256=sha256Hex(responseText);
  // The reservation consumed a call even if the process died. Persist a typed
  // result without raw stderr/output so the next worker can diagnose it, but
  // never turn an unknown execution into a success or automatic retry.
  const finishTrace=(status,extras={})=>{
   if(evidence)writeTrace(number,{...trace,status,...extras,responseSha256,
    responseBytes:Buffer.byteLength(responseText,'utf8'),rawContentStored:false});
  };
  const attachObservation=error=>{
   if(trace.modelObservation)Object.assign(error,{modelObservation:trace.modelObservation,trace});
   return error;
  };
  const authenticateLocalResult=(wire,responseStatus)=>{
   const observation=wire?.modelObservation;
   const {body}=buildCodingRequest({userPrompt,allowedTrackedPaths:spec.allowedPaths,model});
   const pair=getReviewedLocalModelPair({modelSha256:env.CONTINUUM_LOCAL_BRAIN_MODEL_SHA256,
    runtimeSha256:env.CONTINUUM_LOCAL_BRAIN_RUNTIME_SHA256});
   const verified=!!pair&&env.CONTINUUM_LOCAL_BRAIN_RUNTIME_KIND==='LLAMA_CPP';
   const expected={...binding,requestSha256:sha256Hex(JSON.stringify(body)),runtimePromptSha256:sha256Hex(serializeLocalCodingPrompt(body)),
    modelSha256:env.CONTINUUM_LOCAL_BRAIN_MODEL_SHA256,runtimeSha256:env.CONTINUUM_LOCAL_BRAIN_RUNTIME_SHA256,
    proposalSha256:responseStatus==='PROPOSAL'&&wire.proposal?sha256Hex(canonical(wire.proposal)):null,
    responseStatus,verifiedModelExecution:verified,reviewedPairId:verified?pair.id:null};
   const expectedMac=observation&&createHmac('sha256',Buffer.from(observationKey,'hex')).update(JSON.stringify(observation)).digest('hex');
   if(!observation||observation.schema!=='momega1-local-process-observation-v1'||
    observation.evidenceClass!==(verified?'LOCAL_MODEL_HOST':'HOST_INTEGRATION')||observation.isolation!=='BWRAP_UNSHARE_ALL'||
    Object.entries(expected).some(([key,value])=>observation[key]!==value)||wire.verifiedModelExecution!==verified||
    !/^[a-f0-9]{64}$/.test(observation.outputSha256??'')||observation.exitCode!==0||
    !Number.isSafeInteger(observation.pid)||observation.pid<1||
    !Number.isSafeInteger(observation.outputTokens)||observation.outputTokens<1||observation.outputTokens>1800||
    !Number.isSafeInteger(observation.elapsedMs)||observation.elapsedMs<0||observation.elapsedMs>timeoutMs||
    !/^[a-f0-9]{64}$/.test(wire.observationMac??'')||
    !timingSafeEqual(Buffer.from(expectedMac,'hex'),Buffer.from(wire.observationMac,'hex'))){
    finishTrace('INVALID_OUTPUT',{failureKind:'MODEL_OBSERVATION_INVALID'});
    throw rejectedModelOutput('CODING_MODEL_OBSERVATION_INVALID');
   }
   if(run!==spawnSync){
    finishTrace('INVALID_OUTPUT',{failureKind:'CONTRACT_TRANSPORT_UNTRUSTED'});
    throw rejectedModelOutput('CODING_MODEL_OBSERVATION_UNTRUSTED');
   }
   trace.modelObservation=Object.freeze({...observation});trustedObservations.add(trace.modelObservation);
   trace.verifiedModelExecution=verified;
   return trace.modelObservation;
  };
  const failed=response.status!==0||response.error||response.signal;
  if(failed){
   let failureWire=null;
   if(!remoteTeacher){try{failureWire=JSON.parse(responseText);}catch{}}
   if(failureWire?.mode==='LOCAL_CODING_INFERENCE_FAILURE_V1'&&failureWire.trusted===false&&failureWire.autoApply===false&&
    ['CODING_RESPONSE_INVALID','CODING_NOOP_PATCH'].includes(failureWire.errorCode)){
    const observation=authenticateLocalResult(failureWire,'INVALID_MODEL_RESPONSE');
    finishTrace('INVALID_MODEL_RESPONSE',{failureKind:failureWire.errorCode});
    throw attachObservation(rejectedModelOutput(failureWire.errorCode,observation.outputSha256));
   }
   // Only exact codes from our fixed local CLI are diagnostic data. Never
   // persist untrusted stderr or treat an exit code as successful inference.
   const rejected=!remoteTeacher&&
    /^LOCAL_MODEL_UNAVAILABLE_OR_INVALID: (CODING_RESPONSE_INVALID|CODING_NOOP_PATCH):([a-f0-9]{64})$/
     .exec(String(response.stderr??'').trim());
   const localBlock=!remoteTeacher&&/^LOCAL_MODEL_UNAVAILABLE_OR_INVALID: (BLOCKED_LOCAL_[A-Z_]+)$/.exec(String(response.stderr??'').trim());
   const localTimeout=!remoteTeacher&&String(response.stderr??'').trim()==='LOCAL_MODEL_UNAVAILABLE_OR_INVALID: CODING_TIMEOUT';
   const failureKind=response.error?.code==='ETIMEDOUT'||localTimeout?'TIMEOUT':
    response.error?'PROCESS_ERROR':response.signal?'SIGNALLED':
    rejected?.[1]==='CODING_NOOP_PATCH'?'NOOP_PATCH':
    rejected?.[1]==='CODING_RESPONSE_INVALID'?'INVALID_MODEL_RESPONSE':'CHILD_EXIT_NONZERO';
   finishTrace('FAILED',{failureKind});
   if(remoteTeacher){
    const error=rejectedModelOutput('CODING_MODEL_UNAVAILABLE');
    error.remoteCode=remoteFailureCode;error.fallback=DEEPSEEK_OFFLINE_FALLBACK;throw error;
   }
   if(localBlock)throw rejectedModelOutput(localBlock[1]);
   if(localTimeout)throw rejectedModelOutput('CODING_MODEL_TIMEOUT');
   if(rejected)throw rejectedModelOutput(rejected[1],rejected[2]);
   throw rejectedModelOutput('CODING_MODEL_UNAVAILABLE');
  }
  let result;
  try{result=JSON.parse(responseText);}
  catch{
   finishTrace('INVALID_OUTPUT',{failureKind:'INVALID_JSON'});
   throw rejectedModelOutput('CODING_REPLY_INVALID',responseSha256);
  }
  if(result?.mode!=='LOCAL_CODING_PATCH_PROPOSAL_V1'||result.trusted!==false||result.autoApply!==false){
   finishTrace('INVALID_OUTPUT',{failureKind:'WIRE_SCHEMA_INVALID'});
   throw rejectedModelOutput('CODING_REPLY_INVALID',responseSha256);
  }
  if(remoteDisclosure&&(result.transport!=='AUTHENTICATED_HTTPS_TEACHER'||result.modelId!==MODEL_ID||
   result.modelRevision!==PINNED_REVISION||result.requestSha256!==remoteDisclosure.requestSha256||result.verifiedModelExecution!==false)){
   finishTrace('INVALID_OUTPUT',{failureKind:'REMOTE_IDENTITY_INVALID'});
   throw rejectedModelOutput('CODING_REPLY_INVALID',responseSha256);
  }
  if(!remoteTeacher){
   if(result.modelObservation!==undefined||result.verifiedModelExecution===true||run===spawnSync||strictObservation)
    authenticateLocalResult(result,'PROPOSAL');
   else trace.evidenceClass='CONTRACT_TEST';
  }
  let proposal;
  try{proposal=validateCodingProposal(result.proposal,spec);}
  catch{
   finishTrace('INVALID_OUTPUT',{failureKind:'PROPOSAL_REJECTED'});
   throw attachObservation(rejectedModelOutput('CODING_PROPOSAL_INVALID',responseSha256));
  }
  // Syntactically bounded proposal, not a verified code repair. The existing
  // branded CCC1 gate remains solely responsible for execution and proof.
  finishTrace('RESPONDED',{proposalAccepted:true});
  return {mode:result.mode,trusted:false,autoApply:false,...proposal,localRequests:1,
   context:{commit:base,contextSha256:context.contextSha256,ranges:context.ranges.map(({text,...r})=>r)},trace};
 };
 Object.defineProperties(provider,{taskSpec:{value:spec},allowedPaths:{value:spec.allowedPaths},taskFingerprint:{value:fingerprint}});
 return provider;
}
