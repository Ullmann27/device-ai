/** Private control-plane storage for the existing CCC1 worker. The delivery ledger
 * is an owner inbox, never a second task journal, scheduler or permission source. */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';
import {validateCodingTask,taskFingerprint} from './coding-task.mjs';
import {canonical,sha256Hex} from './development-backlog.mjs';
import {getReviewedLocalModelPair} from '../../scripts/continuum-local-model-pins.mjs';

export const SERVICE_SOURCE_ROOT=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
export const SERVICE_ID=/^[a-z][a-z0-9-]{2,63}$/;
const SHA=/^[a-f0-9]{64}$/;
const RUNTIME_KEYS=new Set(['CONTINUUM_LOCAL_BRAIN_MODEL','CONTINUUM_LOCAL_BRAIN_MODEL_PATH',
 'CONTINUUM_LOCAL_BRAIN_MODEL_MANIFEST','CONTINUUM_LOCAL_BRAIN_MODEL_FORMAT','CONTINUUM_LOCAL_BRAIN_MODEL_SHA256',
 'CONTINUUM_LOCAL_BRAIN_RUNTIME','CONTINUUM_LOCAL_BRAIN_RUNTIME_KIND','CONTINUUM_LOCAL_BRAIN_RUNTIME_PATH',
 'CONTINUUM_LOCAL_BRAIN_RUNTIME_SHA256','CONTINUUM_ALLOW_ALTERNATE_CODING_MODEL']);
export const inside=(root,file)=>file===root||file.startsWith(root+path.sep);
export function realDirectory(value){
 if(typeof value!=='string'||!path.isAbsolute(value)||path.resolve(value)!==value||value.includes('\0'))throw Error('SERVICE_PATH_INVALID');
 const s=fs.lstatSync(value);
 if(!s.isDirectory()||s.isSymbolicLink()||fs.realpathSync(value)!==value)throw Error('SERVICE_PATH_UNSAFE');
 return value;
}
export function privateDirectory(value,{create=false}={}){
 if(create)fs.mkdirSync(value,{recursive:true,mode:0o700});
 realDirectory(value);const s=fs.lstatSync(value);
 if(s.uid!==process.getuid?.()||(s.mode&0o077)!==0)throw Error('SERVICE_DIRECTORY_NOT_PRIVATE');
 return value;
}
export function plainBytes(file,max=65536,{privateFile=false}={}){
 if(typeof file!=='string'||!path.isAbsolute(file)||path.resolve(file)!==file)throw Error('SERVICE_PATH_INVALID');
 const fd=fs.openSync(file,fs.constants.O_RDONLY|fs.constants.O_NOFOLLOW);
 try{
  const s=fs.fstatSync(fd);
  if(!s.isFile()||s.nlink!==1||s.size>max||fs.realpathSync(file)!==file||
    privateFile&&(s.uid!==process.getuid?.()||(s.mode&0o077)!==0))throw Error('SERVICE_FILE_UNSAFE');
  const data=fs.readFileSync(fd);if(data.length>max)throw Error('SERVICE_FILE_TOO_LARGE');return data;
 }finally{fs.closeSync(fd);}
}
export const privateJson=(file,max=65536)=>JSON.parse(plainBytes(file,max,{privateFile:true}));
export function writePrivateJson(file,value,{exclusive=false}={}){
 privateDirectory(path.dirname(file));
 const bytes=Buffer.from(JSON.stringify(value)+'\n');
 if(bytes.length>4*1024*1024)throw Error('SERVICE_RECORD_TOO_LARGE');
 const target=exclusive?file:file+'.tmp-'+crypto.randomBytes(12).toString('hex');
 const fd=fs.openSync(target,'wx',0o600);
 try{fs.writeFileSync(fd,bytes);fs.fsyncSync(fd);}finally{fs.closeSync(fd);}
 if(!exclusive){
  if(fs.existsSync(file))plainBytes(file,4*1024*1024,{privateFile:true});
  fs.renameSync(target,file);
 }
 const directory=fs.openSync(path.dirname(file),'r');try{fs.fsyncSync(directory);}finally{fs.closeSync(directory);}
}
export function gitIdentity(root,{clean=false}={}){
 realDirectory(root);
 const run=args=>{const r=spawnSync('git',['-C',root,...args],{encoding:'utf8',shell:false,timeout:10000,maxBuffer:1024*1024});
  if(r.status!==0||r.error)throw Error('SERVICE_GIT_UNAVAILABLE');return r.stdout.trim();};
 const commit=run(['rev-parse','HEAD']);
 if(!/^[a-f0-9]{40}$/.test(commit))throw Error('SERVICE_SOURCE_INVALID');
 if(clean&&run(['status','--porcelain','--untracked-files=all']))throw Error('SERVICE_SOURCE_DIRTY');
 return commit;
}
export function profileClasspathDigest(classpath){
 const hash=crypto.createHash('sha256');let files=0,total=0;
 const add=(file,relative)=>{
  const s=fs.lstatSync(file);
  if(s.isSymbolicLink()||fs.realpathSync(file)!==file)throw Error('SERVICE_PROFILE_CLASSPATH_UNSAFE');
  if(s.isDirectory()){
   for(const name of fs.readdirSync(file).sort())add(path.join(file,name),relative+'/'+name);
  }else{
   if(!s.isFile()||s.nlink!==1||++files>100000||s.size>128*1024*1024||(total+=s.size)>512*1024*1024)throw Error('SERVICE_PROFILE_CLASSPATH_CAPACITY');
   hash.update(relative+'\0');hash.update(plainBytes(file,128*1024*1024));
  }
 };
 for(const item of classpath.split(path.delimiter))add(item,item);
 return hash.digest('hex');
}
function runtimeConfiguration(value){
 if(!value||Array.isArray(value)||typeof value!=='object'||Object.keys(value).length!==RUNTIME_KEYS.size||
   Object.keys(value).some(k=>!RUNTIME_KEYS.has(k)||typeof value[k]!=='string'||value[k].length>2048))throw Error('SERVICE_RUNTIME_CONFIG_INVALID');
 const pair=getReviewedLocalModelPair({modelSha256:value.CONTINUUM_LOCAL_BRAIN_MODEL_SHA256,runtimeSha256:value.CONTINUUM_LOCAL_BRAIN_RUNTIME_SHA256});
 if(!pair||value.CONTINUUM_LOCAL_BRAIN_MODEL!==pair.modelRepository||value.CONTINUUM_LOCAL_BRAIN_MODEL_FORMAT!=='gguf'||
   value.CONTINUUM_LOCAL_BRAIN_RUNTIME!=='llama.cpp'||value.CONTINUUM_LOCAL_BRAIN_RUNTIME_KIND!=='LLAMA_CPP'||
   value.CONTINUUM_ALLOW_ALTERNATE_CODING_MODEL!=='OWNER_APPROVED_ALTERNATE_GGUF')throw Error('SERVICE_REVIEWED_RUNTIME_REQUIRED');
 for(const k of ['CONTINUUM_LOCAL_BRAIN_MODEL_PATH','CONTINUUM_LOCAL_BRAIN_MODEL_MANIFEST','CONTINUUM_LOCAL_BRAIN_RUNTIME_PATH'])
  if(!path.isAbsolute(value[k])||path.resolve(value[k])!==value[k])throw Error('SERVICE_RUNTIME_PATH_INVALID');
 return {...value};
}
export function initializeCodingService({configPath,project,profileDirectory,profileClasspath,runtimeEnvPath,isolationConfig,
 maxQueue=8,maxRecords=1000,maxCycles=1000}={}){
 if(typeof configPath!=='string'||!path.isAbsolute(configPath)||path.resolve(configPath)!==configPath)throw Error('SERVICE_CONFIG_PATH_INVALID');
 const stateDir=path.dirname(configPath);
 for(const root of [realDirectory(project),realDirectory(profileDirectory),SERVICE_SOURCE_ROOT])
  if(inside(root,stateDir)||inside(stateDir,root))throw Error('SERVICE_STATE_OVERLAPS_SOURCE_OR_PROFILES');
 privateDirectory(stateDir,{create:true});
 const runtimeEnv=runtimeConfiguration(JSON.parse(plainBytes(runtimeEnvPath,16384)));
 const isolationConfigSha256=sha256Hex(plainBytes(isolationConfig,4096));
 const config={schema:'continuum-coding-service',version:1,sourceRoot:SERVICE_SOURCE_ROOT,
  sourceCommit:gitIdentity(SERVICE_SOURCE_ROOT),project,profileDirectory,profileClasspath,profileClasspathSha256:profileClasspathDigest(profileClasspath),runtimeEnv,
  modelManifestSha256:sha256Hex(plainBytes(runtimeEnv.CONTINUUM_LOCAL_BRAIN_MODEL_MANIFEST,65536)),isolationConfig,isolationConfigSha256,
  limits:{maxQueue,maxRecords,maxCycles},createdAt:new Date().toISOString()};
 validateConfig(config,stateDir);writePrivateJson(configPath,config,{exclusive:true});
 for(const name of ['ccc1','sessions','public-evidence'])privateDirectory(path.join(stateDir,name),{create:true});
 writePrivateJson(path.join(stateDir,'deliveries.json'),{schema:'continuum-owner-inbox',version:1,items:[]},{exclusive:true});
 return {configPath,sourceCommit:config.sourceCommit,maxQueue,maxRecords,maxCycles,status:'INITIALIZED',modelLoaded:false};
}
function validateConfig(c,stateDir){
 const keys=['schema','version','sourceRoot','sourceCommit','project','profileDirectory','profileClasspath','profileClasspathSha256','runtimeEnv','modelManifestSha256','isolationConfig','isolationConfigSha256','limits','createdAt'];
 if(!c||Object.keys(c).sort().join()!==keys.sort().join()||c.schema!=='continuum-coding-service'||c.version!==1||
   c.sourceRoot!==SERVICE_SOURCE_ROOT||!/^[a-f0-9]{40}$/.test(c.sourceCommit??''))throw Error('SERVICE_CONFIG_INVALID');
 for(const root of [realDirectory(c.sourceRoot),realDirectory(c.project),realDirectory(c.profileDirectory)])
  if(inside(root,stateDir)||inside(stateDir,root))throw Error('SERVICE_STATE_OVERLAPS_SOURCE_OR_PROFILES');
 if(!SHA.test(c.profileClasspathSha256??'')||typeof c.profileClasspath!=='string'||c.profileClasspath.length<1||c.profileClasspath.length>16000||
   c.profileClasspath.split(path.delimiter).some(p=>!path.isAbsolute(p)||p.includes('*')||p.includes('\0')))throw Error('SERVICE_PROFILE_CLASSPATH_INVALID');
 runtimeConfiguration(c.runtimeEnv);
 if(!SHA.test(c.modelManifestSha256??''))throw Error('SERVICE_MANIFEST_PIN_INVALID');
 if(!SHA.test(c.isolationConfigSha256??'')||!path.isAbsolute(c.isolationConfig??'')||
   inside(c.project,c.isolationConfig)||inside(c.sourceRoot,c.isolationConfig))throw Error('SERVICE_ISOLATION_CONFIG_INVALID');
 if(!c.limits||Object.keys(c.limits).sort().join()!=='maxCycles,maxQueue,maxRecords'||
   !Number.isSafeInteger(c.limits.maxQueue)||c.limits.maxQueue<1||c.limits.maxQueue>32||
   !Number.isSafeInteger(c.limits.maxRecords)||c.limits.maxRecords<c.limits.maxQueue||c.limits.maxRecords>10000||
   !Number.isSafeInteger(c.limits.maxCycles)||c.limits.maxCycles<1||c.limits.maxCycles>1000)throw Error('SERVICE_LIMIT_INVALID');
}
export function loadCodingServiceConfig(configPath){
 if(typeof configPath!=='string'||!path.isAbsolute(configPath)||path.resolve(configPath)!==configPath)throw Error('SERVICE_CONFIG_PATH_INVALID');
 const stateDir=privateDirectory(path.dirname(configPath)),config=privateJson(configPath);
 validateConfig(config,stateDir);
 return Object.freeze({...config,stateDir,configPath,configSha256:sha256Hex(canonical(config))});
}
export function assertServiceSource(config){
 if(gitIdentity(config.sourceRoot,{clean:true})!==config.sourceCommit)throw Error('SERVICE_SOURCE_CHANGED');
 if(sha256Hex(plainBytes(config.isolationConfig,4096))!==config.isolationConfigSha256)throw Error('SERVICE_ISOLATION_CONFIG_CHANGED');
}
export function assertProfileBridge(config){
 if(profileClasspathDigest(config.profileClasspath)!==config.profileClasspathSha256)throw Error('SERVICE_PROFILE_CLASSPATH_CHANGED');
}
export function assertServiceArtifacts(config){
 assertServiceSource(config);
 assertProfileBridge(config);
 if(sha256Hex(plainBytes(config.runtimeEnv.CONTINUUM_LOCAL_BRAIN_MODEL_MANIFEST,65536))!==config.modelManifestSha256)
  throw Error('SERVICE_MODEL_MANIFEST_CHANGED');
 for(const [fileKey,shaKey] of [['CONTINUUM_LOCAL_BRAIN_MODEL_PATH','CONTINUUM_LOCAL_BRAIN_MODEL_SHA256'],['CONTINUUM_LOCAL_BRAIN_RUNTIME_PATH','CONTINUUM_LOCAL_BRAIN_RUNTIME_SHA256']]){
  const file=config.runtimeEnv[fileKey],s=fs.lstatSync(file);
  if(!s.isFile()||s.isSymbolicLink()||s.nlink!==1||fs.realpathSync(file)!==file)throw Error('SERVICE_RUNTIME_FILE_UNSAFE');
  const fd=fs.openSync(file,fs.constants.O_RDONLY|fs.constants.O_NOFOLLOW),hash=crypto.createHash('sha256'),buffer=Buffer.alloc(1024*1024);
  try{let count;while((count=fs.readSync(fd,buffer,0,buffer.length,null))>0)hash.update(buffer.subarray(0,count));}finally{fs.closeSync(fd);}
  if(hash.digest('hex')!==config.runtimeEnv[shaKey])throw Error('SERVICE_RUNTIME_HASH_CHANGED');
 }
}
export function serviceEnvironment(config){
 // Deliberately omit inherited NODE_OPTIONS, cloud keys, remote teachers and agent-loop toggles.
 const env={PATH:process.env.PATH??'/usr/bin:/bin',LANG:'C.UTF-8',...config.runtimeEnv,
  CONTINUUM_CODING_ISOLATION_CONFIG:config.isolationConfig,CONTINUUM_MOMEGA1:'1'};
 for(const key of ['HOME','TMPDIR'])if(process.env[key])env[key]=process.env[key];
 return env;
}
export function readDeliveries(config){
 const value=privateJson(path.join(config.stateDir,'deliveries.json'),4*1024*1024);
 if(value.schema!=='continuum-owner-inbox'||value.version!==1||!Array.isArray(value.items)||value.items.length>config.limits.maxRecords)
  throw Error('SERVICE_INBOX_CORRUPT');
 const seen=new Set();
 for(const d of value.items){
  if(!SERVICE_ID.test(d.id??'')||seen.has(d.id)||!SHA.test(d.fingerprint??'')||!SHA.test(d.reviewSha256??'')||
    !['RESERVING','PENDING','DISPATCHED','RETIRED','CANCEL_REQUESTED','UNKNOWN'].includes(d.delivery)||
    d.ccc1TaskId!==null&&!/^t-[a-f0-9]{16}$/.test(d.ccc1TaskId??''))throw Error('SERVICE_INBOX_CORRUPT');
  seen.add(d.id);
 }
 return value.items;
}
export function saveDeliveries(config,items){writePrivateJson(path.join(config.stateDir,'deliveries.json'),{schema:'continuum-owner-inbox',version:1,items});}
export function reviewServiceTask(config,input){
 const task=validateCodingTask(input);
 assertServiceSource(config);
 if(gitIdentity(config.project,{clean:true})!==task.sourceCommit)throw Error('CODING_TASK_STALE_SOURCE');
 const fingerprint=taskFingerprint(task);
 return {schema:'continuum-owner-task-review',version:1,task,taskFingerprint:fingerprint,
  configSha256:config.configSha256,project:config.project,serviceSourceCommit:config.sourceCommit,
  reviewSha256:sha256Hex(canonical({config:config.configSha256,project:config.project,task:fingerprint,source:config.sourceCommit})),
  approvalMeaning:'One bounded local CCC1 attempt; no merge, publication, installation or network grant'};
}
export function linuxProcessIdentity(pid=process.pid){
 if(process.platform!=='linux'||!Number.isSafeInteger(pid)||pid<1)throw Error('SERVICE_LINUX_PROCESS_REQUIRED');
 try{
  const stat=fs.readFileSync('/proc/'+pid+'/stat','utf8'),fields=stat.slice(stat.lastIndexOf(')')+2).trim().split(/\s+/);
  if(fields[0]==='Z')return null;
  return {pid,startTicks:fields[19],bootId:fs.readFileSync('/proc/sys/kernel/random/boot_id','utf8').trim()};
 }catch(e){if(e.code==='ENOENT'||e.code==='ESRCH')return null;throw e;}
}
export function sameLiveProcess(identity){
 if(!identity||!Number.isSafeInteger(identity.pid)||identity.pid<1||!/^\d+$/.test(identity.startTicks??'')||
  !/^[a-f0-9-]{36}$/.test(identity.bootId??''))return false;
 const actual=linuxProcessIdentity(identity.pid);
 return actual!==null&&actual.startTicks===identity.startTicks&&actual.bootId===identity.bootId;
}
