import path from 'node:path';
import fs from 'node:fs';
import {spawn} from 'node:child_process';
import {randomUUID} from 'node:crypto';

function identity(pid,onUnavailable) {
  try {
    const stat=fs.readFileSync('/proc/'+pid+'/stat','utf8');
    const parts=stat.slice(stat.lastIndexOf(')')+2).split(' ');
    return {pid,ppid:Number(parts[1]),group:Number(parts[2]),start:parts[19],state:parts[0]};
  } catch(error) {
    if(!['ENOENT','ESRCH'].includes(error.code))onUnavailable?.();
    return null;
  }
}
/**
 * Fixed builder tools only. Async cancellation/timeout terminates the POSIX process group.
 * `inheritOutput` passes stdout/stderr through unchanged (no capture, no output limit); `exitSignal` names the
 * signal that ended the direct child, if any.
 */
export async function runOwnedCommand(file,args,{cwd,env,timeout,signal,maxOutputBytes=512*1024,inheritOutput=false}={}) {
  if(typeof file!=='string'||!path.isAbsolute(file)||!Array.isArray(args)||
    !Number.isSafeInteger(timeout)||timeout<1||timeout>35*60*1000||
    !Number.isSafeInteger(maxOutputBytes)||maxOutputBytes<1||maxOutputBytes>2*1024*1024)
    return {ok:false,code:null,exitSignal:null,output:'',error:'INVALID_COMMAND',timedOut:false,cancelled:false};
  if(signal?.aborted)return {ok:false,code:null,exitSignal:null,output:'',error:'ABORTED',timedOut:false,cancelled:true};
  return new Promise(resolve=>{
    const grouped=process.platform!=='win32';
    const procPlatform=['linux','android'].includes(process.platform);
    // Fixed trusted tools inherit this identity across setsid(), launcher exits
    // and reparenting. It is lifecycle tracking, not a sandbox against hostile tools.
    const marker='CONTINUUM_OWNED_RUN_ID='+randomUUID();
    let child;
    try {child=spawn(file,args,{cwd,env:{...(env??process.env),CONTINUUM_OWNED_RUN_ID:marker.split('=')[1]},
      shell:false,detached:grouped,stdio:['ignore',...(inheritOutput?['inherit','inherit']:['pipe','pipe'])]});}
    catch {resolve({ok:false,code:null,exitSignal:null,output:'',error:'SPAWN_ERROR',timedOut:false,cancelled:false});return;}
    const chunks=[],owned=new Map();
    let bytes=0,reason=null,closed=false,resultCode=null,resultSignal=null,forceTimer=null,done=false,terminationConfirmed=true;
    let trackingComplete=procPlatform;
    const currentIdentity=pid=>identity(pid,()=>{trackingComplete=false;});
    // A descendant starts no earlier than the direct child. Older processes (init, the Termux app, this harness)
    // cannot carry this run's marker, so an unreadable one of them does not block a positive cleanup claim.
    let startFloor=Number(identity(process.pid)?.start??0);
    // Gradle creates a new session for its single-use daemon. Process-group
    // termination alone misses it. Track descendants with /proc start-time identity,
    // then also stop detached descendants before releasing the caller's lease.
    const remember=()=>{
      if(!procPlatform||!child.pid){trackingComplete=false;return;}
      trackingComplete=true;
      const root=currentIdentity(child.pid);
      // Bind the floor once to the original child. A recycled root PID must not
      // exclude surviving descendants born before its replacement. Signal checks
      // below still require the original (pid,start) identity.
      if(root&&!owned.has(root.pid)){
        owned.set(root.pid,root);startFloor=Math.max(startFloor,Number(root.start));
      }
      let pids;
      try {pids=fs.readdirSync('/proc').filter(p=>/^[0-9]+$/.test(p));}
      catch {trackingComplete=false;return;}
      if(pids.length>65536){trackingComplete=false;return;}
      const buffer=Buffer.alloc(1024*1024+1);
      for(const name of pids) {
        let fd;
        try {
          if(fs.statSync('/proc/'+name).uid!==process.getuid())continue;
          const p=currentIdentity(Number(name));
          if(!p||p.state==='Z'||Number(p.start)<startFloor)continue;
          fd=fs.openSync('/proc/'+name+'/environ','r');
          const n=fs.readSync(fd,buffer,0,buffer.length,null);
          if(n===buffer.length){trackingComplete=false;continue;}
          if(buffer.subarray(0,n).toString().split('\0').includes(marker)&&
            currentIdentity(p.pid)?.start===p.start)owned.set(p.pid,p);
        } catch(error) {
          // A process can disappear during the snapshot. An unreadable live
          // same-UID process prevents a positive cleanup claim (e.g. Android SELinux).
          if(!['ENOENT','ESRCH'].includes(error.code)&&currentIdentity(Number(name))?.state!=='Z')trackingComplete=false;
        } finally {if(fd!==undefined)fs.closeSync(fd);}
      }
    };
    const kill=kind=>{
      if(!child.pid)return;
      // ChildProcess retains its direct-child handle even when /proc access is
      // denied. Stop that child; retain the lease if descendants cannot be checked.
      if(!closed)try{child.kill(kind);}catch{}
      for(const p of [...owned.values()].reverse()) {
        if(currentIdentity(p.pid)?.start!==p.start)continue;
        try {if(p.group===p.pid)process.kill(-p.pid,kind);}catch {}
        try {process.kill(p.pid,kind);}catch {}
      }
      const root=owned.get(child.pid);
      try {
        if(!grouped)child.kill(kind);
        else if(root&&currentIdentity(root.pid)?.start===root.start)
          process.kill(-child.pid,kind);
      }catch {}
    };
    const descendantsTimer=setInterval(remember,1000);
    remember();
    const finish=()=>{
      if(done||!closed||forceTimer)return;
      done=true;clearTimeout(timer);clearInterval(descendantsTimer);signal?.removeEventListener('abort',abort);
      if(!terminationConfirmed&&!reason)reason='TERMINATION_UNCONFIRMED';
      resolve({ok:resultCode===0&&!reason&&terminationConfirmed,code:resultCode,exitSignal:resultSignal,
        output:Buffer.concat(chunks).toString('utf8'),
        error:reason,timedOut:reason==='TIMEOUT',cancelled:reason==='ABORTED',terminationConfirmed});
    };
    const forceAndConfirm=async()=>{
      const live=()=>[...owned.values()].some(p=>{
        const current=currentIdentity(p.pid);
        return current?.start===p.start&&current.state!=='Z';
      });
      const deadline=Date.now()+1500;
      let quiet=0;
      do {
        remember();kill('SIGKILL');
        quiet=live()?0:quiet+1;
        if(quiet>=2)break;
        await new Promise(wake=>setTimeout(wake,25));
      } while(Date.now()<deadline);
      const alive=live();
      terminationConfirmed=trackingComplete&&!alive;
      if(!closed) {
        // An inaccessible descendant can hold a pipe open. Return a bounded
        // unconfirmed result, instead of waiting forever for the close event.
        terminationConfirmed=false;closed=true;resultCode=child.exitCode;resultSignal=child.signalCode;
        child.stdout?.destroy();child.stderr?.destroy();child.unref();
      }
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
    for(const stream of inheritOutput?[]:[child.stdout,child.stderr])stream.on('data',chunk=>{
      const room=maxOutputBytes-bytes;
      if(room>0){const kept=chunk.subarray(0,room);chunks.push(Buffer.from(kept));bytes+=kept.length;}
      if(chunk.length>room)stop('OUTPUT_LIMIT');
    });
    child.on('error',()=>{if(!reason)reason='SPAWN_ERROR';});
    child.on('close',(code,exitSignal)=>{
      resultCode=code;resultSignal=exitSignal;closed=true;
      if(!reason&&!forceTimer) {
        remember();
        const remaining=[...owned.values()].some(p=>{
          const current=currentIdentity(p.pid);
          return p.pid!==child.pid&&current?.start===p.start&&current.state!=='Z';
        });
        terminationConfirmed=trackingComplete;
        if(remaining) {
          kill('SIGTERM');
          forceTimer=setTimeout(forceAndConfirm,250);
        }
      }
      finish();
    });
  });
}
