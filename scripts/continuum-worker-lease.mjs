/** Short OS-flock transactions for the existing local worker, not a new task store.
 * Linux/Termux capability required: flock plus own-process /proc identity.
 * No age-based lock theft, no guessing that a reused PID is the former owner. */
import fs from 'node:fs';
import os from 'node:os';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {resolve,join} from 'node:path';
import crypto from 'node:crypto';
const SELF=fileURLToPath(import.meta.url);
export function processIdentity(pid=process.pid){
 try {const stat=fs.readFileSync('/proc/'+pid+'/stat','utf8');const fields=stat.slice(stat.lastIndexOf(')')+2).split(' ');
  return {pid,start:fields[19],boot:fs.readFileSync('/proc/sys/kernel/random/boot_id','utf8').trim()};
 }catch{return null;}
}
const alive=id=>id && JSON.stringify(processIdentity(id.pid))===JSON.stringify(id);
const host=()=>crypto.createHash('sha256').update(os.hostname()).digest('hex');
function regular(file){if(fs.existsSync(file)&&!fs.lstatSync(file).isFile())throw Error('UNSAFE_LEASE_PATH');}
function transaction(action,file,payload){
 regular(file);const old=fs.existsSync(file)?JSON.parse(fs.readFileSync(file,'utf8')):null;
 if(action==='acquire'){
  if(old){
   if(old.host!==host()||!old.process?.start||!old.process?.boot)throw Error('UNVERIFIABLE_WORKER_LEASE');
   if(alive(old.process))throw Error('WORKER_ALREADY_RUNNING');
   // After SIGKILL, finish only a verified former child group before taking over.
   if(old.child && alive(old.child)){
    try{process.kill(-old.child.pid,'SIGKILL');}catch(e){if(e.code!=='ESRCH')throw e;}
   }else if(old.child && processIdentity(old.child.pid))throw Error('WORKER_CHILD_IDENTITY_CHANGED');
  }
  if(!payload.process?.start||!payload.process?.boot)throw Error('PROCESS_IDENTITY_UNAVAILABLE');
  fs.writeFileSync(file,JSON.stringify({...payload,host:host(),recovered:Boolean(old)}),{mode:0o600});
  return {recovered:Boolean(old)};
 }
 if(!old||old.token!==payload.token)throw Error('WORKER_LEASE_LOST');
 if(action==='child')fs.writeFileSync(file,JSON.stringify({...old,child:payload.child}),{mode:0o600});
 else if(action==='release')fs.unlinkSync(file);
 else throw Error('INVALID_LEASE_ACTION');
 return {};
}
export function workerLease(gitDirectory){
 const file=join(gitDirectory,'continuum-health-worker.json');const gate=file+'.mutex';
 regular(gate);const token=crypto.randomUUID();const identity=processIdentity();
 const act=(action,more={})=>{
  const r=spawnSync('flock',['-n','-E','73',gate,process.execPath,SELF,action,file,JSON.stringify({token,process:identity,...more})],{encoding:'utf8',timeout:5000,maxBuffer:8192,env:{...process.env,GIT_TERMINAL_PROMPT:'0'}});
  if(r.error)throw Error('WORKER_LOCK_BACKEND_UNAVAILABLE: '+r.error.code);
  if(r.status!==0)throw Error('WORKER_LOCK_REFUSED: '+(r.stderr||r.stdout).trim().slice(-200));
  return JSON.parse(r.stdout);
 };
 const info=act('acquire');return {recovered:info.recovered,child:pid=>act('child',{child:processIdentity(pid)}),release:()=>act('release')};
}
if(process.argv[1]&&resolve(process.argv[1])===SELF){
 try{const [action,file,json]=process.argv.slice(2);console.log(JSON.stringify(transaction(action,file,JSON.parse(json))));}
 catch(e){console.error(e.message);process.exitCode=1;}
}
