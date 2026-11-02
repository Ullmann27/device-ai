/**
 * Abort watch for one fixed check command of the repair loop (`run()` in repair-loop.mjs). The loop waits in
 * spawnSync and cannot react to an owner abort while a check step runs; this small process can. It runs the
 * command through runOwnedCommand (own process group, tracked descendants), polls the watched backlog task and
 * stops the step as soon as that task is ABORTED, or when the waiting parent is gone.
 *
 * For the parent nothing else changes: stdout/stderr are inherited byte for byte, the exit code or the ending
 * signal of the command is passed through, and the SIGTERM of a spawnSync timeout stops the whole step before the
 * command's own outcome is passed on, so it still reads as a timeout. A stop by abort is reported only through the
 * receipt file (created exclusively), never through output or an exit code a check could also produce.
 *
 * argv: <backlog file> <task id> <receipt file> <timeout ms> <parent pid> -- <absolute command> [args...]
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {BacklogStore} from './development-backlog.mjs';
import {runOwnedCommand} from '../../scripts/continuum-owned-process.mjs';

const POLL_MS=500;
const MAX_OWNED_MS=35*60*1000;
const TASK_ID=/^[a-z][a-z0-9-]{2,63}$/;

const [backlog,taskId,receipt,timeoutText,parentText,separator,command,...args]=process.argv.slice(2);
const timeout=Number(timeoutText),parent=Number(parentText);
if(typeof backlog!=='string'||!path.isAbsolute(backlog)||!TASK_ID.test(taskId??'')||
   typeof receipt!=='string'||!path.isAbsolute(receipt)||!Number.isSafeInteger(timeout)||timeout<1||
   !Number.isSafeInteger(parent)||parent<2||separator!=='--'||typeof command!=='string'||!path.isAbsolute(command)){
  process.stderr.write('continuum-step-watchdog: invalid arguments\n');
  process.exit(2);
}

const store=new BacklogStore(backlog,{actor:'continuum-step-watchdog'});
// An unreadable backlog never stops a running step; the parent's checkpoint after the step still decides.
const aborted=()=>{
  try{return store.read().doc.tasks.find(t=>t.id===taskId)?.state==='ABORTED';}catch{return false;}
};
const controller=new AbortController();
let stopReason=null;
const stop=why=>{if(!stopReason){stopReason=why;controller.abort();}};
for(const name of ['SIGTERM','SIGINT','SIGHUP'])process.on(name,()=>stop(name));
// The parent's pid comes from the parent itself: a parent that died before this process started is noticed too.
const watch=()=>{
  if(process.ppid!==parent)stop('PARENT_GONE');
  else if(aborted())stop('TASK_ABORTED');
};
watch();
const poll=setInterval(watch,POLL_MS);
// The parent's spawnSync timeout comes first; this bound only matters if its SIGTERM never arrives.
const result=await runOwnedCommand(command,args,{cwd:process.cwd(),env:process.env,
  timeout:Math.min(MAX_OWNED_MS,timeout+60_000),signal:controller.signal,inheritOutput:true});
clearInterval(poll);

/** Ends this process the way the command ended, so the parent's spawnSync reads the same status. */
const endBy=name=>{
  process.removeAllListeners(name);
  process.kill(process.pid,name);
  // A signal Node keeps ignored (SIGPIPE) cannot end the process; report it like a shell does.
  setTimeout(()=>process.exit(128+(os.constants.signals[name]??0)),2000);
};
if(stopReason==='TASK_ABORTED'){
  try{
    fs.writeFileSync(receipt,JSON.stringify({schemaVersion:1,taskId,stoppedAt:new Date().toISOString(),
      terminationConfirmed:result.terminationConfirmed===true})+'\n',{flag:'wx',mode:0o600});
  }finally{process.exit(1);}
}else if(stopReason==='PARENT_GONE')process.exit(1);
else if(Number.isInteger(result.code))process.exit(result.code);
else if(result.exitSignal)endBy(result.exitSignal);
else if(stopReason)endBy(stopReason);
else process.exit(result.error==='SPAWN_ERROR'?127:1);
