/** EXP-05/12: local process observation, not a model-quality or device attestation. */
import fs from 'node:fs';
import path from 'node:path';
import {spawn} from 'node:child_process';
import {createHash,createHmac} from 'node:crypto';
import {readPinnedGgufManifest} from './continuum-gguf-preflight.mjs';
import {admitGgufFile} from './continuum-gguf.mjs';
import {buildCodingRequest,parseCodingResponse} from './continuum-local-brain.mjs';
import {getReviewedLocalModelPair,getLocalModelResourceProfile,LOCAL_MODEL_RUNTIME_CANDIDATES} from './continuum-local-model-pins.mjs';
import {canonical} from '../.github/continuum/development-backlog.mjs';
import {requireCodingModelPreflight} from './continuum-m2-model-preflight.mjs';
const BWRAP='/usr/bin/bwrap',PRLIMIT='/usr/bin/prlimit';
const HEX=/^[a-f0-9]{64}$/;
const hash=x=>createHash('sha256').update(x).digest('hex');
const same=(a,b)=>a.dev===b.dev&&a.ino===b.ino&&a.size===b.size&&a.mtimeNs===b.mtimeNs&&a.ctimeNs===b.ctimeNs;
export function serializeLocalCodingPrompt(body,{modelSha256}={}){
 const schema='\nRequired output JSON schema:\n'+JSON.stringify(body.response_format.json_schema.schema);
 if(LOCAL_MODEL_RUNTIME_CANDIDATES.some(pin=>pin.modelSha256===modelSha256&&pin.modelRepository.startsWith('Qwen/Qwen2.5-Coder-')))
  return '<|im_start|>system\n'+body.messages[0].content+schema+'<|im_end|>\n<|im_start|>user\n'+body.messages[1].content+'<|im_end|>\n<|im_start|>assistant\n';
 return body.messages.map(m=>m.role.toUpperCase()+':\n'+m.content).join('\n\n')+schema+'\n\nASSISTANT:\n';
}
/** Pinned upstream completion emits this exact trailer; raw output stays hashed. */
export function normalizeNativeCodingOutput(raw,{runtimeSha256}={}){
 if(typeof raw!=='string')fail('LOCAL_RUNTIME_OUTPUT_INVALID');
 const known=LOCAL_MODEL_RUNTIME_CANDIDATES.some(pin=>pin.runtimeSha256===runtimeSha256&&pin.runtimeTarget==='llama-completion');
 return (known?raw.replace(/ \[end of text\](?:\r?\n)+\s*$/,''):raw).trim();
}
const fail=code=>{throw Error(code);};
const safe=s=>s.isFile()&&s.nlink===1n&&(s.mode&0o022n)===0n&&s.uid===BigInt(process.getuid());
function openPinned(file,maxBytes){
 if(typeof file!=='string'||!path.isAbsolute(file)||path.normalize(file)!==file)fail('BLOCKED_LOCAL_MODEL_UNAVAILABLE');
 let fd;
 try{
  const before=fs.lstatSync(file,{bigint:true});
  if(!safe(before)||fs.realpathSync(file)!==file||before.size<=0n||before.size>BigInt(maxBytes))fail('LOCAL_RUNTIME_UNSAFE_FILE');
  fd=fs.openSync(file,fs.constants.O_RDONLY|fs.constants.O_NOFOLLOW);
  const stat=fs.fstatSync(fd,{bigint:true});
  if(!safe(stat)||!same(before,stat))fail('LOCAL_RUNTIME_FILE_CHANGED');
  return {file,fd,stat};
 }catch(error){if(fd!==undefined)fs.closeSync(fd);throw error;}
}
function checkFile(p){
 const current=fs.lstatSync(p.file,{bigint:true}),fdStat=fs.fstatSync(p.fd,{bigint:true});
 if(!safe(current)||fs.realpathSync(p.file)!==p.file||!same(p.stat,current)||!same(p.stat,fdStat))fail('LOCAL_RUNTIME_FILE_CHANGED');
}
async function digestFile(p,active){
 const h=createHash('sha256'),chunk=Buffer.alloc(1024*1024);let offset=0;
 while(offset<Number(p.stat.size)){
  active();const n=fs.readSync(p.fd,chunk,0,Math.min(chunk.length,Number(p.stat.size)-offset),offset);
  if(!n)fail('LOCAL_RUNTIME_FILE_CHANGED');h.update(chunk.subarray(0,n));offset+=n;
  // Let deadlines, STOP and parent termination run between bounded reads.
  await new Promise(resolve=>setImmediate(resolve));
 }
 active();checkFile(p);return h.digest('hex');
}
function validateBinding(binding){
 const keys=['contextSha256','executionId','promptSha256','requestIndex','sourceCommit','taskFingerprint','taskId'];
 if(!binding||Object.keys(binding).sort().join(',')!==keys.sort().join(',')||
  typeof binding.taskId!=='string'||!/^[A-Za-z0-9_.:-]{1,160}$/.test(binding.taskId)||
  !/^[a-f0-9]{40}$/.test(binding.sourceCommit)||!HEX.test(binding.taskFingerprint)||
  !HEX.test(binding.contextSha256)||!HEX.test(binding.promptSha256)||!HEX.test(binding.executionId)||
  !Number.isSafeInteger(binding.requestIndex)||binding.requestIndex<1||binding.requestIndex>8)fail('LOCAL_EXECUTION_BINDING_INVALID');
}

/** Fixed namespace policy. Never mount the checkout, HOME, credentials or host network. */
export function localModelSandboxArgs({modelFd=3,runtimeFd=4,promptFd=5,seccompFd=6,schemaFd=null,noRepack=false}={}){
 const args=['--unshare-all','--cap-drop','ALL','--seccomp',String(seccompFd),'--die-with-parent','--new-session','--clearenv',
  '--ro-bind-fd',String(modelFd),'/model.gguf',
  '--ro-bind-fd',String(runtimeFd),'/runtime',
  '--ro-bind-data',String(promptFd),'/prompt.txt',
  '--proc','/proc','--dev','/dev','--tmpfs','/tmp','--dir','/usr','--dir','/usr/lib'];
 for(const lib of ['/lib','/lib64','/usr/lib','/usr/lib64'])if(fs.existsSync(lib))args.push('--ro-bind',lib,lib);
 if(schemaFd!==null)args.push('--ro-bind-data',String(schemaFd),'/schema.json');
 // No host executable other than the pinned runtime is made available.
 args.push('--chdir','/tmp','--setenv','HOME','/tmp','--setenv','LC_ALL','C','--',
  '/runtime','--model','/model.gguf','--file','/prompt.txt','--n-predict','1800',
  '--ctx-size','8192','--threads','2','--n-gpu-layers','0','--temp','0','--seed','1',
  '--no-display-prompt','--no-conversation','--no-warmup','--no-context-shift');
 if(schemaFd!==null)args.push('--json-schema-file','/schema.json');
 if(noRepack)args.push('--no-repack');
 return args;
}

/** Deny new processes; permit clone only for same-process native worker threads.
 * clone3 gets ENOSYS so libc can use the inspected clone flags. No x32 ABI. */
export function localModelSeccompFilter(arch=process.arch){
 const spec=arch==='x64'?{audit:0xc000003e,clone:56,fork:[57,58]}:
  arch==='arm64'?{audit:0xc00000b7,clone:220,fork:[]}:null;
 if(!spec)fail('BLOCKED_LOCAL_ISOLATION_UNAVAILABLE');
 const ins=[];const op=(code,jt,jf,k)=>ins.push({code,jt,jf,k});
 op(0x20,0,0,4);op(0x15,1,0,spec.audit);op(0x06,0,0,0x80000000);
 op(0x20,0,0,0);
 // Reject x32 syscall numbers before individual syscall comparisons.
 op(0x45,0,1,0x40000000);op(0x06,0,0,0x00050001);
 for(const nr of spec.fork){op(0x15,0,1,nr);op(0x06,0,0,0x00050001);}
 op(0x15,0,1,435);op(0x06,0,0,0x00050026);
 op(0x15,0,4,spec.clone);op(0x20,0,0,16);op(0x45,1,0,0x00010000);
 op(0x06,0,0,0x00050001);op(0x06,0,0,0x7fff0000);
 op(0x06,0,0,0x7fff0000);
 const buffer=Buffer.alloc(ins.length*8);ins.forEach((v,i)=>{buffer.writeUInt16LE(v.code,i*8);buffer[i*8+2]=v.jt;buffer[i*8+3]=v.jf;buffer.writeUInt32LE(v.k>>>0,i*8+4);});
 return buffer;
}

/** Native counter parser; prompt evaluation alone never counts as decoding. */
export function parseNativeGenerationCount(stderr){
 if(typeof stderr!=='string')return null;
 const match=/(?:^|\n)(?:(?:[0-9.]+\s+[A-Z]\s+)?(?:llama_perf_context_print|common_perf_print):\s*)?eval time\s*=\s*[0-9.]+\s*ms\s*\/\s*(\d+)\s*(?:runs|tokens)/.exec(stderr);
 const count=Number(match?.[1]);
 return Number.isSafeInteger(count)&&count>=1&&count<=1800?count:null;
}

/** Check the actual kernel group, never a caller-provided memory claim. The entire
 * provider/CLI/native job must share the private cgroup namespace root. Docker
 * must cap that job with --memory=8g --memory-swap=8g, without mounting its socket. */
export function assertLocalModelJobMemoryLimit(profile){
 if(!profile?.requiresJobMemoryLimit)return null;
 try{
  const group=fs.readFileSync('/proc/self/cgroup','utf8');
  if(group.trim()!=='0::/'||fs.readFileSync(`/proc/${process.ppid}/cgroup`,'utf8')!==group)throw Error();
  const root='/sys/fs/cgroup';
  if(fs.statfsSync(root).type!==0x63677270)throw Error();
  const read=name=>{const file=root+'/'+name,s=fs.lstatSync(file);
   if(!s.isFile()||s.uid!==0||(s.mode&0o022)!==0||fs.realpathSync(file)!==file)throw Error();
   return fs.readFileSync(file,'utf8').trim();};
  const max=read('memory.max'),swap=read('memory.swap.max');
  if(!/^[0-9]+$/.test(max)||BigInt(max)<=0n||BigInt(max)>BigInt(profile.maxJobMemoryBytes)||swap!=='0')throw Error();
  return Object.freeze({maxJobMemoryBytes:Number(max),maxJobSwapBytes:0,enforcement:'CGROUP_V2'});
 }catch{fail('BLOCKED_LOCAL_JOB_MEMORY_LIMIT');}
}
/** Bounded launcher; injection is explicitly CONTRACT_TEST and never branded. */
export async function launchLocalModel({fds,promptFd,jsonSchemaFd,timeoutMs,signal,resourceProfile=null,spawnImpl=spawn,bwrap=BWRAP}={}){
 if(!Number.isSafeInteger(timeoutMs)||timeoutMs<1||timeoutMs>300000)fail('INVALID_CODING_BOUNDS');
 if(signal?.aborted)fail('CODING_CANCELLED');
 assertLocalModelJobMemoryLimit(resourceProfile);
 let filterDir,filterFd;
 try{
  filterDir=fs.mkdtempSync('/tmp/continuum-seccomp-');fs.chmodSync(filterDir,0o700);
  const filterFile=path.join(filterDir,'filter');fs.writeFileSync(filterFile,localModelSeccompFilter(),{mode:0o600});
  filterFd=fs.openSync(filterFile,'r');fs.unlinkSync(filterFile);
  return await new Promise((resolve,reject)=>{
  let child,settled=false,timer,bytes=0;const out=[],err=[];
  const cleanup=()=>{clearTimeout(timer);signal?.removeEventListener('abort',cancel);};
  const kill=()=>{if(child?.pid){try{process.kill(-child.pid,'SIGKILL');}catch{}try{child.kill('SIGKILL');}catch{}}};
  // Reject only after the child has closed; kill all inherited stdout holders.
  let refusal=null;
  const cancel=()=>{refusal='CODING_CANCELLED';kill();};
  const finish=(error,value)=>{if(settled)return;settled=true;cleanup();if(error)reject(error);else resolve(value);};
  try{child=spawnImpl(PRLIMIT,['--as='+(resourceProfile?.nativeAddressSpaceBytes??4294967296),'--cpu='+Math.ceil(timeoutMs/1000),'--nofile=64','--fsize=1048576','--',bwrap,...localModelSandboxArgs({schemaFd:jsonSchemaFd===undefined?null:7,noRepack:resourceProfile?.disableWeightRepacking===true})],{shell:false,detached:true,
   cwd:'/',env:{},stdio:['ignore','pipe','pipe',fds[0],fds[1],promptFd,filterFd,...(jsonSchemaFd===undefined?[]:[jsonSchemaFd])]});}
  catch{finish(Error('BLOCKED_LOCAL_ISOLATION_UNAVAILABLE'));return;}
  timer=setTimeout(()=>{refusal='CODING_TIMEOUT';kill();},timeoutMs);
  signal?.addEventListener('abort',cancel,{once:true});if(signal?.aborted)cancel();
  child.once('error',()=>finish(Error('BLOCKED_LOCAL_ISOLATION_UNAVAILABLE')));
  const collect=(target,chunk)=>{bytes+=chunk.length;if(bytes>65536){refusal='LOCAL_RUNTIME_OUTPUT_LIMIT';kill();}else target.push(Buffer.from(chunk));};
  child.stdout.on('data',chunk=>collect(out,chunk));child.stderr.on('data',chunk=>collect(err,chunk));
  child.once('close',(code,term)=>{
   // A successful runtime must terminate normally and emit actual decoding stats.
   if(refusal){finish(Error(refusal));return;}
   if(code!==0||term){finish(Error('LOCAL_RUNTIME_PROCESS_FAILED'));return;}
   const stderr=Buffer.concat(err).toString('utf8');
   const outputTokens=parseNativeGenerationCount(stderr);
   if(outputTokens===null){finish(Error('LOCAL_RUNTIME_GENERATION_UNOBSERVED'));return;}
   let text;try{text=new TextDecoder('utf-8',{fatal:true}).decode(Buffer.concat(out));}catch{finish(Error('LOCAL_RUNTIME_OUTPUT_INVALID'));return;}
   finish(null,{text,outputTokens,pid:child.pid,exitCode:code,
    evidenceClass:spawnImpl===spawn&&bwrap===BWRAP?'HOST_INTEGRATION':'CONTRACT_TEST'});
  });
 });
 }finally{if(filterFd!==undefined)fs.closeSync(filterFd);if(filterDir)fs.rmSync(filterDir,{recursive:true,force:true});}
}

/** Default production path has no endpoint, download, cloud fallback or model-selected command. */
export async function queryPinnedLocalCoding({userPrompt,allowedTrackedPaths,timeoutMs=60000,binding,observationKey,env=process.env,signal}={}){
 const modelPath=env.CONTINUUM_LOCAL_BRAIN_MODEL_PATH,runtimePath=env.CONTINUUM_LOCAL_BRAIN_RUNTIME_PATH,
  manifestPath=env.CONTINUUM_LOCAL_BRAIN_MODEL_MANIFEST,runtimeHash=env.CONTINUUM_LOCAL_BRAIN_RUNTIME_SHA256;
 if(!modelPath||!runtimePath||!manifestPath||!HEX.test(runtimeHash??'')||
  !['LLAMA_CPP','CONTRACT_FIXTURE'].includes(env.CONTINUUM_LOCAL_BRAIN_RUNTIME_KIND))fail('BLOCKED_LOCAL_MODEL_UNAVAILABLE');
 if(!Number.isSafeInteger(timeoutMs)||timeoutMs<100||timeoutMs>300000)fail('INVALID_CODING_BOUNDS');
 validateBinding(binding);
 if(typeof observationKey!=='string'||!HEX.test(observationKey))fail('LOCAL_EXECUTION_BINDING_INVALID');
 if(binding.promptSha256!==hash(userPrompt))fail('LOCAL_EXECUTION_BINDING_INVALID');
 requireCodingModelPreflight({model:env.CONTINUUM_LOCAL_BRAIN_MODEL,runtime:env.CONTINUUM_LOCAL_BRAIN_RUNTIME,
  format:env.CONTINUUM_LOCAL_BRAIN_MODEL_FORMAT,sha256:env.CONTINUUM_LOCAL_BRAIN_MODEL_SHA256,
  alternateConsent:env.CONTINUUM_ALLOW_ALTERNATE_CODING_MODEL});
 const started=Date.now(),controller=new AbortController();
 const active=()=>{if(signal?.aborted||controller.signal.aborted)fail('CODING_CANCELLED');if(Date.now()-started>=timeoutMs)fail('CODING_TIMEOUT');};
 const abort=()=>controller.abort();signal?.addEventListener('abort',abort,{once:true});
 const stop=()=>controller.abort();process.once('SIGTERM',stop);process.once('SIGINT',stop);
 let model,runtime,promptFd,promptDir,jsonSchemaFd;
 try{
  active();const pin=await readPinnedGgufManifest(manifestPath);
  if(pin.sha256!==env.CONTINUUM_LOCAL_BRAIN_MODEL_SHA256||path.basename(modelPath)!==pin.filename)fail('LOCAL_RUNTIME_MODEL_PIN_MISMATCH');
  const resourceProfile=getLocalModelResourceProfile({provenance:pin,runtimeSha256:runtimeHash});
  const jobMemoryLimit=assertLocalModelJobMemoryLimit(resourceProfile);
  model=openPinned(modelPath,resourceProfile?.maxFileBytes??2147483648);runtime=openPinned(runtimePath,268435456);
  const magic=Buffer.alloc(4);fs.readSync(runtime.fd,magic,0,4,0);
  if(!magic.equals(Buffer.from([127,69,76,70]))||(runtime.stat.mode&0o111n)===0n)fail('LOCAL_RUNTIME_EXECUTABLE_INVALID');
  const actualRuntimeHash=await digestFile(runtime,active);
  if(actualRuntimeHash!==runtimeHash)fail('LOCAL_RUNTIME_HASH_MISMATCH');
  // Existing parser remains the only model-format gate. Both original descriptor
  // identity and bytes are checked again after decoding, including rename races.
  const admitted=await admitGgufFile({path:modelPath,provenance:pin,signal:controller.signal,
   timeoutMs:Math.min(120000,Math.max(100,timeoutMs-(Date.now()-started)))});
  checkFile(model);if(admitted.sha256!==pin.sha256)fail('LOCAL_RUNTIME_MODEL_PIN_MISMATCH');active();
  if(process.platform!=='linux'||!fs.existsSync(BWRAP)||!fs.existsSync(PRLIMIT))fail('BLOCKED_LOCAL_ISOLATION_UNAVAILABLE');
  for(const tool of [BWRAP,PRLIMIT]){
   const toolStat=fs.lstatSync(tool,{bigint:true});
   if(!toolStat.isFile()||toolStat.uid!==0n||(toolStat.mode&0o022n)!==0n)fail('BLOCKED_LOCAL_ISOLATION_UNAVAILABLE');
  }
  const {body,paths}=buildCodingRequest({userPrompt,allowedTrackedPaths,model:env.CONTINUUM_LOCAL_BRAIN_MODEL});
  const prompt=serializeLocalCodingPrompt(body,{modelSha256:pin.sha256});
  promptDir=fs.mkdtempSync('/tmp/continuum-model-');fs.chmodSync(promptDir,0o700);
  const promptFile=path.join(promptDir,'prompt.txt');fs.writeFileSync(promptFile,prompt,{mode:0o600,flag:'wx'});
  promptFd=fs.openSync(promptFile,fs.constants.O_RDONLY|fs.constants.O_NOFOLLOW);fs.unlinkSync(promptFile);
  const schemaFile=path.join(promptDir,'schema.json');
  const schemaText=JSON.stringify(body.response_format.json_schema.schema);
  fs.writeFileSync(schemaFile,schemaText,{mode:0o600,flag:'wx'});
  jsonSchemaFd=fs.openSync(schemaFile,fs.constants.O_RDONLY|fs.constants.O_NOFOLLOW);fs.unlinkSync(schemaFile);
  const execution=await launchLocalModel({fds:[model.fd,runtime.fd],promptFd,jsonSchemaFd,
   timeoutMs:Math.max(1,timeoutMs-(Date.now()-started)),signal:controller.signal,resourceProfile});active();
  if(await digestFile(model,active)!==pin.sha256||await digestFile(runtime,active)!==runtimeHash)fail('LOCAL_RUNTIME_FILE_CHANGED');
  let parsed,responseError=null;
  try{parsed=parseCodingResponse({choices:[{message:{content:normalizeNativeCodingOutput(execution.text,{runtimeSha256:runtimeHash})}}]},paths);}
  catch(error){responseError=error;}
  const reviewedPair=getReviewedLocalModelPair({modelSha256:pin.sha256,runtimeSha256:runtimeHash});
  const verifiedModelExecution=!!reviewedPair&&execution.evidenceClass==='HOST_INTEGRATION'&&
   env.CONTINUUM_LOCAL_BRAIN_RUNTIME_KIND==='LLAMA_CPP'&&pin.repository===reviewedPair.modelRepository&&
   pin.revision===reviewedPair.modelRevision&&pin.filename===reviewedPair.modelFilename&&
   pin.sizeBytes===reviewedPair.modelBytes&&pin.license===reviewedPair.modelLicense;
  const observation={schema:'momega1-local-process-observation-v1',
   evidenceClass:verifiedModelExecution?'LOCAL_MODEL_HOST':env.CONTINUUM_LOCAL_BRAIN_RUNTIME_KIND==='CONTRACT_FIXTURE'?'CONTRACT_TEST':execution.evidenceClass,
   verifiedModelExecution,reviewedPairId:verifiedModelExecution?reviewedPair.id:null,
   reviewedModelId:verifiedModelExecution?reviewedPair.modelRepository:null,
   reviewedRuntimeSourceCommit:verifiedModelExecution?reviewedPair.runtimeSourceCommit:null,
   reviewedModelRevision:verifiedModelExecution?reviewedPair.modelRevision:null,
   reviewedBuildEvidenceSha256:verifiedModelExecution?reviewedPair.buildEvidenceSha256:null,
   independentReviewEvidenceSha256:verifiedModelExecution?reviewedPair.independentReviewEvidenceSha256:null,...binding,
   requestSha256:hash(JSON.stringify(body)),runtimePromptSha256:hash(prompt),runtimeSchemaSha256:hash(schemaText),modelSha256:pin.sha256,runtimeSha256:runtimeHash,
   outputSha256:hash(execution.text),proposalSha256:parsed?hash(canonical(parsed.proposal)):null,
   responseStatus:responseError?'INVALID_MODEL_RESPONSE':'PROPOSAL',
   outputTokens:execution.outputTokens,pid:execution.pid,exitCode:execution.exitCode,
   isolation:'BWRAP_UNSHARE_ALL',...(jobMemoryLimit?{jobMemoryLimit,resourceProfileId:resourceProfile.id,nativeAddressSpaceBytes:resourceProfile.nativeAddressSpaceBytes,disableWeightRepacking:resourceProfile.disableWeightRepacking}:{}),elapsedMs:Date.now()-started};
  const observationMac=createHmac('sha256',Buffer.from(observationKey,'hex')).update(JSON.stringify(observation)).digest('hex');
  if(responseError){
   const errorCode=String(responseError.message).split(':')[0];
   responseError.modelInferenceEnvelope=Object.freeze({mode:'LOCAL_CODING_INFERENCE_FAILURE_V1',
    trusted:false,autoApply:false,errorCode,verifiedModelExecution,modelObservation:Object.freeze(observation),observationMac});
   throw responseError;
  }
  return {...parsed,verifiedModelExecution,modelObservation:observation,observationMac};
 }catch(error){
  const code=String(error?.message??'');
  if(/^(?:LOCAL_|BLOCKED_LOCAL_|GGUF_|CODING_|INVALID_|MODEL_|ALTERNATE_|EXPLICIT_|GEMMA_|UNSUPPORTED_)[A-Z0-9_:a-f]+$/.test(code))throw error;
  fail('BLOCKED_LOCAL_MODEL_UNAVAILABLE');
 }finally{
  signal?.removeEventListener('abort',abort);process.removeListener('SIGTERM',stop);process.removeListener('SIGINT',stop);
  if(jsonSchemaFd!==undefined)fs.closeSync(jsonSchemaFd);
  if(promptFd!==undefined)fs.closeSync(promptFd);if(promptDir)fs.rmSync(promptDir,{recursive:true,force:true});
  if(model)fs.closeSync(model.fd);if(runtime)fs.closeSync(runtime.fd);
 }
}
