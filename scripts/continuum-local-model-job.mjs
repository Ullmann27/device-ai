/** Fixed owner-approved 7B model-job transport. The Docker client is trusted host
 * control code; neither its socket nor credentials enter the model container. */
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';
import {randomBytes} from 'node:crypto';
const ROOT=fileURLToPath(new URL('../',import.meta.url));
const POLICY=fileURLToPath(new URL('./continuum-local-model-outer-seccomp.json',import.meta.url));
export const LOCAL_MODEL_JOB_IMAGE='sha256:3f44842e8a052beda7e358b9fb8d8acc3e180a7f58aaceff7e1b49ad7f3d46e1';
export const LOCAL_MODEL_JOB_NODE_ARGS=Object.freeze(['--max-old-space-size=256','--max-semi-space-size=16']);
const MODEL='509287f78cb4d4cf6b3843734733b914b2c158e43e22a7f4bf5e963800894d3c';
const RUNTIME='c2c13a2a27c5a85137b6d083074fa83e0d990db129e715f8452352e3730cbf5a';
export function requiresPinnedModelJob(env){return env.CONTINUUM_LOCAL_BRAIN_MODEL_SHA256===MODEL&&env.CONTINUUM_LOCAL_BRAIN_RUNTIME_SHA256===RUNTIME&&env.CONTINUUM_LOCAL_BRAIN_RUNTIME_KIND==='LLAMA_CPP';}
export function runPinnedModelJob({cli,command,options}){
 if(!requiresPinnedModelJob(options.env)||command!=='coding'||cli!==path.join(ROOT,'scripts/continuum-local-brain-cli.mjs'))throw Error('BLOCKED_LOCAL_JOB_CONFIGURATION');
 const name='continuum-model-'+randomBytes(16).toString('hex');
 const args=['run','--interactive','--rm','--pull=never','--init','--name',name,'--network=none','--ipc=none',
  '--memory=7g','--memory-swap=7g','--cpus=2','--pids-limit=64','--cap-drop=ALL',
  '--security-opt=no-new-privileges:true','--security-opt=systempaths=unconfined','--security-opt=seccomp='+POLICY,'--user='+process.getuid()+':'+process.getgid(),
  '--entrypoint=/node/bin/node','--read-only','--tmpfs','/tmp:rw,nosuid,nodev,size=128m'];
 const mount=(src,dst=src)=>{if(typeof src!=='string'||!path.isAbsolute(src)||src.includes(',')||src.includes('\n'))throw Error('BLOCKED_LOCAL_JOB_CONFIGURATION');
  args.push('--mount',`type=bind,src=${src},dst=${dst},readonly`);};
 for(const lib of ['/usr','/lib','/lib64'])if(fs.existsSync(lib))mount(fs.realpathSync(lib),lib);
 mount(path.dirname(path.dirname(process.execPath)),'/node');
 mount(path.join(ROOT,'scripts'),'/source/scripts');mount(path.join(ROOT,'.github/continuum'),'/source/.github/continuum');
 // Only the three explicitly configured files enter; no parent directory, HOME,
 // checkout, Docker socket or host environment is mounted into this job.
 for(const key of ['CONTINUUM_LOCAL_BRAIN_MODEL_PATH','CONTINUUM_LOCAL_BRAIN_MODEL_MANIFEST','CONTINUUM_LOCAL_BRAIN_RUNTIME_PATH']){
  const file=options.env[key],s=fs.lstatSync(file);
  if(!s.isFile()||s.nlink!==1||s.uid!==process.getuid()||(s.mode&0o022)!==0||fs.realpathSync(file)!==file)throw Error('BLOCKED_LOCAL_JOB_CONFIGURATION');
  mount(file);
 }
 for(const key of ['CONTINUUM_LOCAL_BRAIN_MODEL','CONTINUUM_LOCAL_BRAIN_RUNTIME','CONTINUUM_LOCAL_BRAIN_MODEL_FORMAT','CONTINUUM_LOCAL_BRAIN_MODEL_SHA256','CONTINUUM_ALLOW_ALTERNATE_CODING_MODEL','CONTINUUM_LOCAL_BRAIN_MODEL_PATH','CONTINUUM_LOCAL_BRAIN_MODEL_MANIFEST','CONTINUUM_LOCAL_BRAIN_RUNTIME_PATH','CONTINUUM_LOCAL_BRAIN_RUNTIME_SHA256','CONTINUUM_LOCAL_BRAIN_RUNTIME_KIND'])args.push('--env',key+'='+options.env[key]);
 args.push(LOCAL_MODEL_JOB_IMAGE,...LOCAL_MODEL_JOB_NODE_ARGS,'/source/'+path.relative(ROOT,cli),command);
 const config=fs.mkdtempSync('/tmp/continuum-model-docker-');fs.chmodSync(config,0o700);
 fs.writeFileSync(path.join(config,'config.json'),'{}',{mode:0o600});
 const fixed=['--host=unix:///var/run/docker.sock','--config='+config];
 try{return spawnSync('/usr/local/bin/docker',[...fixed,...args],{...options,env:{},timeout:options.timeout});}
 finally{spawnSync('/usr/local/bin/docker',[...fixed,'rm','--force',name],{env:{},encoding:'utf8',timeout:5000,maxBuffer:4096});fs.rmSync(config,{recursive:true,force:true});}
}
