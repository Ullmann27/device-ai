/** Reviewer-only actual approved-model probe. Never promotes app authority.
 * Captures only the frozen synthetic prompt and native synthetic-task output.
 */
import fs from 'node:fs';import path from 'node:path';import os from 'node:os';
import {spawn} from 'node:child_process';import {createHash} from 'node:crypto';import {pathToFileURL} from 'node:url';
const [target,runtimePath,expectedRuntimeHash,outDir]=process.argv.slice(2);
if(!target||!path.isAbsolute(runtimePath??'')||!/^[a-f0-9]{64}$/.test(expectedRuntimeHash??'')||!outDir)throw Error('USAGE target runtime expectedSHA outDir');
const {localModelSandboxArgs,localModelSeccompFilter,serializeLocalCodingPrompt,normalizeNativeCodingOutput,parseNativeGenerationCount}=await import(pathToFileURL(path.join(target,'scripts/continuum-local-model-observation.mjs')));
const {buildCodingRequest,parseCodingResponse}=await import(pathToFileURL(path.join(target,'scripts/continuum-local-brain.mjs')));
const frozen=JSON.parse(fs.readFileSync(path.join(import.meta.dirname,'blind-host-prompt.json'),'utf8'));
const modelPath='/workspace/momega1-local-model/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf';
const expectedModelHash='cc324af070c2ecbfd324a30884d2f951a7ff756aba85cb811a6ec436933bb046';
const sha=x=>createHash('sha256').update(x).digest('hex');
const statKey=s=>[s.dev,s.ino,s.size,s.mtimeNs,s.ctimeNs].map(String).join(':');
function openAndHash(p,expected,size){const before=fs.lstatSync(p,{bigint:true});if(!before.isFile()||before.nlink!==1n||(before.mode&0o022n)!==0n||before.uid!==BigInt(process.getuid())||fs.realpathSync(p)!==p)throw Error('UNSAFE_PIN');const fd=fs.openSync(p,fs.constants.O_RDONLY|fs.constants.O_NOFOLLOW);const st=fs.fstatSync(fd,{bigint:true});if(statKey(st)!==statKey(before)||size&&st.size!==BigInt(size))throw Error('PIN_CHANGED');const h=createHash('sha256'),b=Buffer.alloc(4*1024*1024);let at=0;for(;;){const n=fs.readSync(fd,b,0,b.length,at);if(!n)break;h.update(b.subarray(0,n));at+=n;}if(h.digest('hex')!==expected)throw Error('PIN_HASH_MISMATCH');return {fd,p,stat:statKey(st),sha256:expected,size:Number(st.size)};}
const model=openAndHash(modelPath,expectedModelHash,1117320768),runtime=openAndHash(runtimePath,expectedRuntimeHash);
const temp=fs.mkdtempSync(path.join(os.tmpdir(),'momega1-independent-real-'));fs.chmodSync(temp,0o700);
const {body,paths}=buildCodingRequest({userPrompt:frozen.userPrompt,allowedTrackedPaths:frozen.allowedTrackedPaths,model:'Qwen2.5-Coder-1.5B-Instruct-Q4_K_M'});
const schemaText=JSON.stringify(body.response_format.json_schema.schema);const schemaPath=path.join(temp,'schema');fs.writeFileSync(schemaPath,schemaText,{mode:0o600});const schemaFd=fs.openSync(schemaPath,'r');fs.unlinkSync(schemaPath);
const prompt=serializeLocalCodingPrompt(body,{modelSha256:expectedModelHash});const promptPath=path.join(temp,'prompt'),filterPath=path.join(temp,'filter');
fs.writeFileSync(promptPath,prompt,{mode:0o600});fs.writeFileSync(filterPath,localModelSeccompFilter(),{mode:0o600});
const promptFd=fs.openSync(promptPath,'r'),filterFd=fs.openSync(filterPath,'r');fs.unlinkSync(promptPath);fs.unlinkSync(filterPath);
fs.mkdirSync(outDir,{recursive:true,mode:0o700});
const args=['--as=4294967296','--cpu=120','--nofile=64','--fsize=1048576','--','/usr/bin/bwrap',...localModelSandboxArgs({schemaFd:7})];
let stdout=[],stderr=[],bytes=0,refusal=null,peakRssKiB=0,peakProcesses=0;const started=Date.now();
const child=spawn('/usr/bin/prlimit',args,{cwd:'/',env:{},shell:false,detached:true,stdio:['ignore','pipe','pipe',model.fd,runtime.fd,promptFd,filterFd,schemaFd]});
const kill=()=>{try{process.kill(-child.pid,'SIGKILL');}catch{}try{child.kill('SIGKILL');}catch{}};
const timer=setTimeout(()=>{refusal='INDEPENDENT_TIMEOUT';kill();},120000);
const monitor=setInterval(()=>{let queue=[child.pid],seen=new Set(),rss=0;while(queue.length&&seen.size<16){const pid=queue.pop();if(!pid||seen.has(pid))continue;seen.add(pid);try{const status=fs.readFileSync(`/proc/${pid}/status`,'utf8');rss+=Number(/VmRSS:\s+(\d+)/.exec(status)?.[1]??0);queue.push(...fs.readFileSync(`/proc/${pid}/task/${pid}/children`,'utf8').trim().split(/\s+/).map(Number).filter(Boolean));}catch{}}peakRssKiB=Math.max(peakRssKiB,rss);peakProcesses=Math.max(peakProcesses,seen.size);},50);
const collect=(parts,chunk)=>{bytes+=chunk.length;if(bytes>65536){refusal='INDEPENDENT_OUTPUT_LIMIT';kill();}else parts.push(chunk);};child.stdout.on('data',x=>collect(stdout,x));child.stderr.on('data',x=>collect(stderr,x));
let exitCode,signal,error;
try{({exitCode,signal}=await new Promise((resolve,reject)=>{child.once('error',reject);child.once('close',(exitCode,signal)=>resolve({exitCode,signal}));}));}catch(e){error=e.message;}
finally{clearTimeout(timer);clearInterval(monitor);}
const text=Buffer.concat(stdout).toString('utf8'),err=Buffer.concat(stderr).toString('utf8');
const receipt={schema:'momega1-independent-actual-native-host-v1',evidenceClass:'ACTUAL_NATIVE_RUN_PENDING_REVIEW',approvedModelSha256:model.sha256,runtimeSha256:runtime.sha256,modelBytes:model.size,runtimeBytes:runtime.size,promptSha256:sha(prompt),schemaSha256:sha(schemaText),nativeOutputTokens:parseNativeGenerationCount(err),stdoutSha256:sha(text),stderrSha256:sha(err),elapsedMs:Date.now()-started,exitCode,signal,error,refusal,peakRssKiB,peakProcesses,outputBytes:bytes,node:process.version,platform:process.platform,arch:process.arch};
try{for(const p of [model,runtime]){if(statKey(fs.fstatSync(p.fd,{bigint:true}))!==p.stat||statKey(fs.lstatSync(p.p,{bigint:true}))!==p.stat)throw Error('POST_RUN_PIN_CHANGED');const check=openAndHash(p.p,p.sha256,p.size);fs.closeSync(check.fd);}receipt.pinsStable=true;}catch(e){receipt.pinError=e.message;}
try{receipt.parsed=parseCodingResponse({choices:[{message:{content:normalizeNativeCodingOutput(text,{runtimeSha256:expectedRuntimeHash})}}]},paths);}catch(e){receipt.parseError=e.message;}
fs.writeFileSync(path.join(outDir,'native-schema.json'),schemaText,{mode:0o600});fs.writeFileSync(path.join(outDir,'native-prompt.txt'),prompt,{mode:0o600});fs.writeFileSync(path.join(outDir,'native-stdout.txt'),text,{mode:0o600});fs.writeFileSync(path.join(outDir,'native-stderr.txt'),err,{mode:0o600});fs.writeFileSync(path.join(outDir,'native-receipt.json'),JSON.stringify(receipt,null,2)+'\n',{mode:0o600});
for(const fd of [model.fd,runtime.fd,promptFd,filterFd,schemaFd])fs.closeSync(fd);fs.rmSync(temp,{recursive:true,force:true});
console.log(JSON.stringify({...receipt,parsed:receipt.parsed?'BOUNDED_PROPOSAL_PARSED':undefined}));
if(exitCode!==0||refusal||!receipt.pinsStable||receipt.parseError||!receipt.nativeOutputTokens)process.exitCode=1;
