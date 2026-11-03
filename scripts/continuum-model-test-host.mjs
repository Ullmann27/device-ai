/** Test-only observation of real host prerequisites. Never grants runtime authority. */
import fs from 'node:fs';
import {spawnSync} from 'node:child_process';

export function inspectBoundedModelTestHost(){
 const unavailable=reason=>({available:false,reason});
 try{
  const root='/sys/fs/cgroup';
  if(fs.statfsSync(root).type!==0x63677270)return unavailable('cgroup v2 filesystem unavailable');
  const membership=fs.readFileSync('/proc/self/cgroup','utf8');
  if(membership.trim()!=='0::/')return unavailable('private cgroup root unavailable');
  if(fs.readFileSync('/proc/'+process.ppid+'/cgroup','utf8')!==membership)
   return unavailable('parent and job cgroups differ');
  const values={};
  for(const name of ['memory.max','memory.swap.max']){
   const file=root+'/'+name,stat=fs.lstatSync(file);
   if(!stat.isFile()||stat.uid!==0||(stat.mode&0o022)!==0||fs.realpathSync(file)!==file)
    return unavailable('kernel memory control has unsupported ownership or type');
   values[name]=fs.readFileSync(file,'utf8').trim();
  }
  const limit=values['memory.max'];
  if(!/^[0-9]+$/.test(limit)||BigInt(limit)<=0n||BigInt(limit)>8589934592n)
   return unavailable('positive memory limit at most 8 GiB unavailable');
  if(values['memory.swap.max']!=='0')return unavailable('zero-swap job bound unavailable');
  return {available:true,memoryBytes:Number(limit),swapBytes:0};
 }catch(error){
  if(['ENOENT','EACCES','EPERM','ENOTDIR'].includes(error.code))
   return unavailable('kernel prerequisite inaccessible: '+error.code);
  throw error;
 }
}

/** Independently probe OS prerequisites; never invoke the model launcher or reuse
 * its sandbox arguments. A recognized host denial is missing evidence, not PASS. */
export function inspectModelIsolationTestHost(){
 const inspect=file=>{
  try{
   const stat=fs.lstatSync(file);
   let executable=true;
   try{fs.accessSync(file,fs.constants.X_OK);}
   catch(error){if(!['EACCES','EPERM'].includes(error.code))throw error;executable=false;}
   return {file,present:true,executable,admitted:stat.isFile()&&stat.uid===0&&(stat.mode&0o022)===0,
    uid:stat.uid,mode:(stat.mode&0o777).toString(8)};
  }catch(error){
   if(['ENOENT','EACCES','EPERM','ENOTDIR'].includes(error.code))
    return {file,present:false,executable:false,admitted:false,reason:error.code};
   throw error;
  }
 };
 const bwrap=inspect('/usr/bin/bwrap'),prlimit=inspect('/usr/bin/prlimit');
 const tools=[bwrap,prlimit];
 const admissionFailures=tools.filter(tool=>!tool.admitted).map(tool=>
  tool.present?`${tool.file} fails root-owned regular non-writable tool policy (uid=${tool.uid}, mode=${tool.mode})`:
   `${tool.file} unavailable (${tool.reason})`);
 if(process.platform!=='linux')admissionFailures.unshift('Linux tool-admission platform unavailable');
 let namespace;
 if(!bwrap.executable){
  namespace={available:false,reason:'fixed /usr/bin/bwrap executable unavailable',probe:null};
 }else{
  // Harmless fixed executable, empty environment, no checkout/model/secret mount.
  // Missing probe dependencies and unfamiliar failures must fail the tests.
  fs.accessSync('/usr/bin/true',fs.constants.X_OK);
  const args=['--unshare-all','--cap-drop','ALL','--die-with-parent','--new-session','--clearenv'];
  for(const lib of ['/usr','/lib','/lib64'])if(fs.existsSync(lib))args.push('--ro-bind',lib,lib);
  args.push('--proc','/proc','--dev','/dev','--tmpfs','/tmp','--chdir','/tmp','--','/usr/bin/true');
  const result=spawnSync('/usr/bin/bwrap',args,{env:{},encoding:'utf8',timeout:5000,maxBuffer:8192});
  const probe={command:'/usr/bin/bwrap',args,status:result.status,signal:result.signal,
   error:result.error?.code??null,stdout:result.stdout??'',stderr:result.stderr??''};
  if(result.error||result.signal||probe.stdout!=='')
   throw Error('UNEXPECTED_ISOLATION_PROBE_FAILURE: '+JSON.stringify(probe));
  if(result.status===0){
   if(probe.stderr!=='')throw Error('UNEXPECTED_ISOLATION_PROBE_OUTPUT: '+JSON.stringify(probe));
   namespace={available:true,reason:null,probe};
  }else if(result.status===1&&
   /^bwrap: loopback: Failed to create NETLINK_ROUTE socket: Operation not permitted\n?$/.test(probe.stderr)){
   namespace={available:false,reason:probe.stderr.trim(),probe};
  }else{
   throw Error('UNEXPECTED_ISOLATION_PROBE_FAILURE: '+JSON.stringify(probe));
  }
 }
 const launcherAvailable=prlimit.executable&&bwrap.executable&&namespace.available;
 const launcherReason=!prlimit.executable?'fixed /usr/bin/prlimit executable unavailable':namespace.reason;
 const launcherFailure=prlimit.executable?'LOCAL_RUNTIME_PROCESS_FAILED':'BLOCKED_LOCAL_ISOLATION_UNAVAILABLE';
 const queryAvailable=admissionFailures.length===0&&launcherAvailable;
 return {tools,namespace,
  launcher:{available:launcherAvailable,reason:launcherAvailable?null:launcherReason,
   expectedFailure:launcherAvailable?null:launcherFailure},
  query:{available:queryAvailable,reason:queryAvailable?null:[...admissionFailures,...(!launcherAvailable?[launcherReason]:[])].join('; '),
   expectedFailure:queryAvailable?null:admissionFailures.length?'BLOCKED_LOCAL_ISOLATION_UNAVAILABLE':launcherFailure}};
}
