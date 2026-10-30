/** OS confinement for existing fixed test profiles. Docker is optional capacity;
 * unavailable capability never falls back to executing untrusted code on host. */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const SOCKET='unix:///var/run/docker.sock';
const IMAGE=/^sha256:[a-f0-9]{64}$/;
const PROFILES=new Set(['node-continuum','node-backend','android-compile','android-unit','android-build','kotlin-fixture']);
const CONTROL=fileURLToPath(new URL('./isolated-profile-runner.mjs',import.meta.url));
const KOTLIN_HARNESS=fileURLToPath(new URL('../../scripts/continuum-isolation-kotlin-fixture.sh',import.meta.url));
const INSTANCES=new WeakSet();
export function isIsolatedExecutor(value){return INSTANCES.has(value);}
/** A consistency check for receipts, not remote attestation or a test oracle. */
export function validIsolationEvidence(value,profile){
  return value?.schema==='continuum-isolation-v1'&&IMAGE.test(value.image??'')&&value.network==='none'&&
    /^[1-9][0-9]*:[1-9][0-9]*$/.test(value.user??'')&&value.readOnly===true&&value.noNewPrivileges===true&&
    Array.isArray(value.capabilities)&&value.capabilities.length===0&&value.profile===profile&&
    /^[a-f0-9]{64}$/.test(value.sourceSha256??'')&&value.untrustedReports===true&&
    Number.isSafeInteger(value.memoryMb)&&value.memoryMb>=256&&value.memoryMb<=6144&&
    Number.isSafeInteger(value.pids)&&value.pids>=16&&value.pids<=256&&
    Number.isSafeInteger(value.cpus)&&value.cpus>=1&&value.cpus<=4&&
    Number.isSafeInteger(value.timeoutMs)&&value.timeoutMs>=1000&&value.timeoutMs<=300_000&&
    (value.runtime===undefined||RUNTIMES.has(value.runtime));
}
/** Evidence that the profile ran under gVisor (runtime confirmed by docker inspect after the run). */
export function isGvisorEvidence(value,profile){return validIsolationEvidence(value,profile)&&value.runtime==='runsc';}
const CONFIG_KEYS=new Set(['image','nodeHome','jdkHome','gradleHome','androidHome','kotlinHome','gradleModules','timeoutMs','memoryMb','pids','cpus','runtime']);
// OCI runtimes: runc (Docker default) or runsc (gVisor: a user-space kernel between the sandbox and the host kernel).
const RUNTIMES=new Set(['runc','runsc']);
/** Owner-controlled capability configuration, never a task/model-provided command. */
export function loadIsolationConfig(file,{sourceRoot}={}){
  const absolute=path.resolve(file),stat=fs.lstatSync(absolute);
  if(!stat.isFile()||stat.isSymbolicLink()||fs.realpathSync(absolute)!==absolute||stat.size>4096)
    throw Error('ISOLATION_UNSAFE_CONFIG');
  if(sourceRoot){const source=fs.realpathSync(sourceRoot);if(absolute===source||absolute.startsWith(source+path.sep))throw Error('ISOLATION_CONFIG_MUST_BE_EXTERNAL');}
  const config=JSON.parse(fs.readFileSync(absolute,'utf8'));
  if(!config||Array.isArray(config)||typeof config!=='object'||Object.keys(config).some(k=>!CONFIG_KEYS.has(k)))
    throw Error('ISOLATION_UNKNOWN_CONFIG_OPTION');
  return config;
}
function cleanDockerEnv(){
  const env={...process.env};
  for(const key of ['DOCKER_HOST','DOCKER_CONTEXT','DOCKER_TLS','DOCKER_TLS_VERIFY','DOCKER_CERT_PATH'])delete env[key];
  return env;
}
function docker(args,options={}){
  return spawnSync('docker',['--host='+SOCKET,...args],{encoding:'utf8',env:cleanDockerEnv(),
    timeout:15_000,killSignal:'SIGKILL',maxBuffer:8*1024*1024,shell:false,...options});
}
function ordinaryDirectory(value){
  if(typeof value!=='string'||!path.isAbsolute(value)||!/^[A-Za-z0-9_./-]+$/.test(value))throw Error('ISOLATION_UNSAFE_PATH');
  const real=fs.realpathSync(value);
  if(!fs.lstatSync(value).isDirectory()||fs.lstatSync(value).isSymbolicLink())throw Error('ISOLATION_UNSAFE_DIRECTORY');
  return real;
}
function snapshot(root,target){
  const git=spawnSync('git',['ls-files','--stage','-z'],{cwd:root,encoding:'utf8',shell:false});
  if(git.status!==0)throw Error('ISOLATION_SOURCE_NOT_GIT');
  let total=0;const hash=crypto.createHash('sha256');
  for(const entry of git.stdout.split('\0').filter(Boolean)){
    const match=/^(100644|100755) [a-f0-9]{40} 0\t(.+)$/.exec(entry);
    if(!match)throw Error('ISOLATION_SYMLINK_OR_UNMERGED_SOURCE');
    const relative=match[2];
    if(relative.startsWith('/')||relative.split('/').some(x=>x==='..'||x==='.'||!x)||/[\0\r\n\\]/.test(relative))throw Error('ISOLATION_UNSAFE_SOURCE_PATH');
    const file=path.join(root,relative),stat=fs.lstatSync(file);
    if(!stat.isFile()||stat.isSymbolicLink()||fs.realpathSync(file)!==file||stat.size>64*1024*1024)throw Error('ISOLATION_UNSAFE_SOURCE');
    total+=stat.size;if(total>256*1024*1024)throw Error('ISOLATION_SOURCE_TOO_LARGE');
    const bytes=fs.readFileSync(file);hash.update(JSON.stringify([relative,crypto.createHash('sha256').update(bytes).digest('hex')])+'\n');
    const dest=path.join(target,relative);fs.mkdirSync(path.dirname(dest),{recursive:true});
    fs.writeFileSync(dest,bytes,{mode:0o444,flag:'wx'});
  }
  return hash.digest('hex');
}
const bind=(src,dst)=>['--mount','type=bind,src='+src+',dst='+dst+',readonly'];
export function createIsolatedExecutor({image,nodeHome,jdkHome=null,gradleHome=null,androidHome=null,kotlinHome=null,gradleModules=null,
  timeoutMs=180_000,memoryMb=4096,pids=128,cpus=2,runtime='runc'}={}){
  if(!IMAGE.test(image??'')||!Number.isSafeInteger(timeoutMs)||timeoutMs<1000||timeoutMs>300_000||
     !Number.isSafeInteger(memoryMb)||memoryMb<256||memoryMb>6144||!Number.isSafeInteger(pids)||pids<16||pids>256||
     !Number.isSafeInteger(cpus)||cpus<1||cpus>4||!RUNTIMES.has(runtime))throw Error('ISOLATION_INVALID_POLICY');
  const uid=process.getuid?.();const gid=process.getgid?.();
  if(!Number.isSafeInteger(uid)||uid<1||!Number.isSafeInteger(gid)||gid<1)throw Error('ISOLATION_UNPRIVILEGED_UID_REQUIRED');
  const tools={node:ordinaryDirectory(nodeHome)};
  for(const [name,value] of Object.entries({jdk:jdkHome,gradle:gradleHome,android:androidHome,kotlin:kotlinHome}))
    if(value!==null)tools[name]=ordinaryDirectory(value);
  const dependencies=gradleModules===null?null:ordinaryDirectory(gradleModules);
  // Mount concrete public toolchain roots, never a broad owner/home/workspace
  // directory. The administrator remains responsible for toolchain provenance.
  for(const [name,relative] of Object.entries({node:'bin/node',jdk:'bin/java',gradle:'bin/gradle',kotlin:'bin/kotlinc',android:'build-tools'})){
    if(!tools[name])continue;
    const entry=path.join(tools[name],relative);
    if(!fs.existsSync(entry)||!fs.realpathSync(entry).startsWith(tools[name]+path.sep)||fs.existsSync(path.join(tools[name],'.git')))
      throw Error('ISOLATION_INVALID_TOOLCHAIN_ROOT');
  }
  if(dependencies&&path.basename(dependencies)!=='modules-2')throw Error('ISOLATION_INVALID_DEPENDENCY_CACHE');
  const available=docker(['image','inspect',image,'--format','{{.Id}}']);
  if(available.status!==0||available.stdout.trim()!==image)throw Error('ISOLATION_UNAVAILABLE');
  if(runtime==='runsc'){
    // gVisor is used only when the daemon reports a registered runsc runtime; otherwise block, never fall back.
    const info=docker(['info','--format','{{json .Runtimes}}']);let runtimes=null;
    try{runtimes=JSON.parse(info.stdout);}catch{}
    if(info.status!==0||!runtimes||typeof runtimes!=='object'||Array.isArray(runtimes)||!Object.hasOwn(runtimes,'runsc'))
      throw Error('ISOLATION_GVISOR_UNAVAILABLE');
  }
  const executor={
    policy:Object.freeze({schema:'continuum-isolation-v1',image,network:'none',user:uid+':'+gid,
      readOnly:true,noNewPrivileges:true,capabilities:[],memoryMb,pids,cpus,timeoutMs,runtime}),
    execute(profile,root,out,{deadline=null}={}){
      if(!PROFILES.has(profile))throw Error('ISOLATION_UNKNOWN_PROFILE');
      const source=ordinaryDirectory(root);
      if(profile.startsWith('android-')&&(!tools.jdk||!tools.gradle||!tools.android||!dependencies))throw Error('ISOLATION_ANDROID_TOOLCHAIN_UNAVAILABLE');
      if(profile==='kotlin-fixture'&&(!tools.jdk||!tools.kotlin))throw Error('ISOLATION_KOTLIN_TOOLCHAIN_UNAVAILABLE');
      const remaining=Math.min(timeoutMs,deadline===null?timeoutMs:deadline-Date.now());
      if(remaining<1)throw Error('ISOLATION_DEADLINE_EXHAUSTED');
      // Container output never has a writable host bind. tmpfs dies with this generation.
      const stage=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-os-sandbox-'));
      fs.chmodSync(stage,0o755);const tree=path.join(stage,'source');fs.mkdirSync(tree,{mode:0o755});
      let sourceSha256;
      try{sourceSha256=snapshot(source,tree);}catch(error){fs.rmSync(stage,{recursive:true,force:true});throw error;}
      for(const dir of ['app/build','.gradle','build'])fs.mkdirSync(path.join(tree,dir),{recursive:true});
      const name='continuum-'+crypto.randomBytes(12).toString('hex');
      const args=['run',...(runtime==='runsc'?['--runtime=runsc']:[]),'--entrypoint=/usr/bin/env','--name',name,'--network=none','--ipc=none','--cgroupns=private',
        '--user='+uid+':'+gid,'--read-only','--cap-drop=ALL','--security-opt=no-new-privileges:true',
        '--pids-limit='+pids,'--memory='+memoryMb+'m','--memory-swap='+memoryMb+'m','--cpus='+cpus,
        '--ulimit=nofile=256:256','--ulimit=core=0:0',
        '--tmpfs=/tmp:rw,noexec,nosuid,nodev,size=64m,mode=1777',
        '--tmpfs=/scratch:rw,nosuid,nodev,size=2048m,mode=1777',
        '--tmpfs=/work/app/build:rw,nosuid,nodev,size=768m,mode=1777',
        '--tmpfs=/work/.gradle:rw,nosuid,nodev,size=128m,mode=1777',
        '--tmpfs=/work/build:rw,nosuid,nodev,size=256m,mode=1777',
        ...bind(tree,'/work'),...bind('/usr/bin','/usr/bin'),...bind('/usr/bin','/bin'),
        ...bind('/usr/lib','/usr/lib'),...bind('/lib','/lib'),...bind('/lib64','/lib64'),
        ...bind(CONTROL,'/control/runner.mjs'),...bind(KOTLIN_HARNESS,'/control/kotlin-fixture.sh')];
      for(const [key,value] of Object.entries(tools))args.push(...bind(value,'/tools/'+key));
      if(dependencies)args.push(...bind(dependencies,'/deps/modules-2'));
      args.push(image,'-i','HOME=/scratch','PATH=/tools/node/bin:/usr/bin:/bin',
        '/tools/node/bin/node','/control/runner.mjs',profile);
      let result,observedRuntime=null;
      try{
        result=docker(args,{timeout:remaining,maxBuffer:8*1024*1024});
      }finally{
        try{
          const exists=docker(['container','inspect',name]);
          if(exists.status===0){
            observedRuntime=String(docker(['container','inspect','--format','{{.HostConfig.Runtime}}',name]).stdout??'').trim();
            docker(['container','kill',name]);
            const removed=docker(['container','rm',name]);
            if(removed.status!==0)throw Error('ISOLATION_TEARDOWN_FAILED');
          }else if(exists.error||exists.signal||!String(exists.stderr).includes('No such container: '+name)&&
                   !String(exists.stderr).includes('No such object: '+name)){
            throw Error('ISOLATION_TEARDOWN_UNCONFIRMED');
          }
        }finally{fs.rmSync(stage,{recursive:true,force:true});}
      }
      // The daemon must confirm the runtime that actually ran; a silently substituted runtime is no gVisor evidence.
      if(runtime==='runsc'&&observedRuntime!=='runsc')throw Error('ISOLATION_RUNTIME_MISMATCH');
      let payload;
      try{payload=JSON.parse(result.stdout);}catch{throw Error('ISOLATION_EXECUTION_FAILED: '+String(result.stderr).slice(-350));}
      if(result.status!==0||result.error||result.signal||payload.schema!=='continuum-isolated-profile-v1'||
         payload.profile!==profile||!Array.isArray(payload.reports)||payload.reports.length>400||
         payload.reports.some(x=>typeof x!=='string'||x.length>2*1024*1024)||
         payload.reports.reduce((n,x)=>n+x.length,0)>6*1024*1024||typeof payload.logs!=='string'||payload.logs.length>100_000||
         !(payload.exitCode===null||(Number.isSafeInteger(payload.exitCode)&&payload.exitCode>=0&&payload.exitCode<=255))||
         !(payload.error===null||typeof payload.error==='string')||!(payload.signal===null||typeof payload.signal==='string'))
        throw Error('ISOLATION_INVALID_RESULT');
      const evidence=path.join(path.resolve(out),'isolated-'+crypto.randomBytes(10).toString('hex'));
      fs.mkdirSync(evidence,{recursive:true,mode:0o700});
      const junitFiles=payload.reports.map((xml,i)=>{
        const file=path.join(evidence,'TEST-'+i+'.xml');fs.writeFileSync(file,xml,{flag:'wx',mode:0o600});return file;
      });
      return {status:payload.exitCode,error:payload.error,signal:payload.signal,stdout:payload.logs,stderr:'',
        ms:0,junitFiles,isolation:{...executor.policy,sourceSha256,profile,untrustedReports:true}};
    }
  };
  INSTANCES.add(executor);return Object.freeze(executor);
}
