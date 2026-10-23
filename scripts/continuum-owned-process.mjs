import path from 'node:path';
import fs from 'node:fs';
import {spawn} from 'node:child_process';

function identity(pid) {
  try {
    const stat=fs.readFileSync('/proc/'+pid+'/stat','utf8');
    const parts=stat.slice(stat.lastIndexOf(')')+2).split(' ');
    return {pid,ppid:Number(parts[1]),group:Number(parts[2]),start:parts[19],state:parts[0]};
  } catch {return null;}
}
/** Fixed builder tools only. Async cancellation/timeout terminates the POSIX process group. */
export async function runOwnedCommand(file,args,{cwd,env,timeout,signal,maxOutputBytes=512*1024}={}) {
  if(typeof file!=='string'||!path.isAbsolute(file)||!Array.isArray(args)||
    !Number.isSafeInteger(timeout)||timeout<1||timeout>35*60*1000||
    !Number.isSafeInteger(maxOutputBytes)||maxOutputBytes<1||maxOutputBytes>2*1024*1024)
    return {ok:false,code:null,output:'',error:'INVALID_COMMAND',timedOut:false,cancelled:false};
  if(signal?.aborted)return {ok:false,code:null,output:'',error:'ABORTED',timedOut:false,cancelled:true};
  return new Promise(resolve=>{
    const grouped=process.platform!=='win32';
    let child;
    try {child=spawn(file,args,{cwd,env,shell:false,detached:grouped,stdio:['ignore','pipe','pipe']});}
    catch {resolve({ok:false,code:null,output:'',error:'SPAWN_ERROR',timedOut:false,cancelled:false});return;}
    const chunks=[],owned=new Map();
    let bytes=0,reason=null,closed=false,resultCode=null,forceTimer=null,done=false,terminationConfirmed=true;
    // Gradle creates a new session for its single-use daemon. Process-group
    // termination alone misses it. Track descendants with Linux start-time identity,
    // then also stop detached descendants before releasing the caller's lease.
    const remember=()=>{
      if(process.platform!=='linux'||!child.pid)return;
      const root=identity(child.pid);if(root)owned.set(root.pid,root);
      let all;
      try {all=fs.readdirSync('/proc').filter(p=>/^[0-9]+$/.test(p)).slice(0,65536).map(p=>identity(Number(p))).filter(Boolean);}
      catch {return;}
      let changed=true;
      while(changed) {
        changed=false;
        for(const p of all) {
          const parent=owned.get(p.ppid);
          if(parent&&identity(parent.pid)?.start===parent.start&&!owned.has(p.pid)) {
            owned.set(p.pid,p);changed=true;
          }
        }
      }
    };
    const kill=kind=>{
      if(!child.pid)return;
      for(const p of [...owned.values()].reverse()) {
        if(identity(p.pid)?.start!==p.start)continue;
        try {if(p.group===p.pid)process.kill(-p.pid,kind);}catch {}
        try {process.kill(p.pid,kind);}catch {}
      }
      const root=owned.get(child.pid);
      try {
        if(!grouped)child.kill(kind);
        else if(process.platform!=='linux'||root&&identity(root.pid)?.start===root.start)
          process.kill(-child.pid,kind);
      }catch {}
    };
    const descendantsTimer=setInterval(remember,1000);
    remember();
    const finish=()=>{
      if(done||!closed||forceTimer)return;
      done=true;clearTimeout(timer);clearInterval(descendantsTimer);signal?.removeEventListener('abort',abort);
      resolve({ok:resultCode===0&&!reason,code:resultCode,output:Buffer.concat(chunks).toString('utf8'),
        error:reason,timedOut:reason==='TIMEOUT',cancelled:reason==='ABORTED',terminationConfirmed});
    };
    const forceAndConfirm=async()=>{
      remember();kill('SIGKILL');
      const live=()=>[...owned.values()].some(p=>{
        const current=identity(p.pid);
        return current?.start===p.start&&current.state!=='Z';
      });
      const deadline=Date.now()+1500;
      while(live()&&Date.now()<deadline)await new Promise(wake=>setTimeout(wake,10));
      terminationConfirmed=!live();
      if(!terminationConfirmed&&!reason)reason='TERMINATION_UNCONFIRMED';
      forceTimer=null;finish();
    };
    const stop=why=>{
      if(reason||done)return;
      reason=why;remember();kill('SIGTERM');
      // Keep this timer alive even if the direct child exits: descendants belong to the same lease.
      forceTimer=setTimeout(forceAndConfirm,250);
    };
    const abort=()=>stop('ABORTED');
    const timer=setTimeout(()=>stop('TIMEOUT'),timeout);
    signal?.addEventListener('abort',abort,{once:true});
    if(signal?.aborted)abort();
    for(const stream of [child.stdout,child.stderr])stream.on('data',chunk=>{
      const room=maxOutputBytes-bytes;
      if(room>0){const kept=chunk.subarray(0,room);chunks.push(Buffer.from(kept));bytes+=kept.length;}
      if(chunk.length>room)stop('OUTPUT_LIMIT');
    });
    child.on('error',()=>{if(!reason)reason='SPAWN_ERROR';});
    child.on('close',code=>{
      resultCode=code;closed=true;
      if(!reason&&!forceTimer) {
        remember();
        const remaining=[...owned.values()].some(p=>{
          const current=identity(p.pid);
          return p.pid!==child.pid&&current?.start===p.start&&current.state!=='Z';
        });
        if(remaining) {
          kill('SIGTERM');
          forceTimer=setTimeout(forceAndConfirm,250);
        }
      }
      finish();
    });
  });
}
