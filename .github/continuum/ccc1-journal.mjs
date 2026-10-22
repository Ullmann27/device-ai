// STATUS 22.10.2026: UNFERTIG (WIP) – syntaktisch geprüft, noch ohne Tests; von keinem Produktivpfad importiert.
// CCC1 DurableTaskJournal: the only state of a task (event sourcing, no second state file).
// One append-only JSON line per EnvelopeV1, written with fsync; monotonic sequence; every entry hash-chained to its
// predecessor; entries of trusted senders (OWNER, TEAMLEADER, VERIFIER) carry an HMAC of their hash with the runtime
// key that reasoners never receive. A duplicate messageId is applied once (identical content) or rejected
// (conflicting content). A torn last line from a crash during append is detected and can be truncated; damage
// anywhere else is reported as corruption and never "repaired". One writer per worktree via an exclusive lock file.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {createHmac,randomBytes,timingSafeEqual} from 'node:crypto';
import {canonical,sha256,envelopeProblems} from './ccc1-envelope.mjs';

const TRUSTED=new Set(['OWNER','TEAMLEADER','VERIFIER']);
const BINDING=['sessionId','taskId','repoFullName','baseCommitSha','worktreeId','deadline','maxRounds','maxCpuMillis',
 'maxMemoryBytes','permissions','checkpointHash','schemaVersion','protocolHash'];
const ZERO='0'.repeat(64);

/** 32-byte runtime key in the runtime's own state directory (0600); reasoners only ever see message views. */
export function runtimeKey(stateDir){
 const file=path.join(stateDir,'ccc1-runtime.key');
 fs.mkdirSync(stateDir,{recursive:true,mode:0o700});
 if(!fs.existsSync(file))try{fs.writeFileSync(file,randomBytes(32),{flag:'wx',mode:0o600});}catch(e){if(e.code!=='EEXIST')throw e;}
 const key=fs.readFileSync(file);
 if(key.length!==32)throw Error('runtime key damaged');
 return key;
}

export class JournalCorruption extends Error{}

export class DurableTaskJournal{
 constructor(file,key){
  if(!Buffer.isBuffer(key)||key.length!==32)throw Error('journal key must be 32 bytes');
  this.file=file;this.key=key;
 }
 static forTask(stateDir,taskId,key){return new DurableTaskJournal(path.join(stateDir,'tasks',taskId+'.jsonl'),key);}

 /** Raw lines; reports a torn tail (incomplete last line) separately from real corruption. */
 #lines(){
  if(!fs.existsSync(this.file))return {lines:[],torn:null};
  const text=fs.readFileSync(this.file,'utf8');
  if(!text)return {lines:[],torn:null};
  const parts=text.split('\n');
  const last=parts.pop();                    // '' when the file ends with a newline
  let torn=null;
  if(last!==''){torn={bytes:Buffer.byteLength(last),offset:Buffer.byteLength(text)-Buffer.byteLength(last)};}
  return {lines:parts,torn};
 }

 /** Verified entries. Throws JournalCorruption for anything but a torn tail; a torn tail throws unless allowTorn. */
 entries({allowTorn=false}={}){
  const {lines,torn}=this.#lines();
  if(torn&&!allowTorn)throw new JournalCorruption('torn last line ('+torn.bytes+' bytes) from an interrupted append; run recovery');
  let prev=ZERO;const seen=new Set();let first=null;
  return lines.map((line,seq)=>{
   let e;try{e=JSON.parse(line);}catch{throw new JournalCorruption('entry '+seq+' is not JSON');}
   const {prevHash,hash,mac,...env}=e;
   const problems=envelopeProblems(env);
   if(problems.length)throw new JournalCorruption('entry '+seq+': '+problems.join('; '));
   if(env.sequence!==seq||prevHash!==prev)throw new JournalCorruption('chain broken at entry '+seq);
   if(sha256(canonical({...env,prevHash}))!==hash)throw new JournalCorruption('entry '+seq+' was modified');
   if(TRUSTED.has(env.sender)){
    const want=createHmac('sha256',this.key).update(hash).digest();
    if(typeof mac!=='string'||!/^[0-9a-f]{64}$/.test(mac)||!timingSafeEqual(Buffer.from(mac,'hex'),want))
     throw new JournalCorruption('entry '+seq+' claims sender '+env.sender+' without a valid runtime MAC');
   }else if(mac!==undefined)throw new JournalCorruption('entry '+seq+' of '+env.sender+' must not carry a MAC');
   if(seen.has(env.messageId))throw new JournalCorruption('entry '+seq+' repeats message '+env.messageId);
   if(env.parentMessageId&&!seen.has(env.parentMessageId))throw new JournalCorruption('entry '+seq+' refers to an unknown parent');
   if(seq===0){
    if(env.sender!=='OWNER'||env.kind!=='GOAL')throw new JournalCorruption('a task journal starts with the owner GOAL');
    first=env;
   }else for(const k of BINDING)if(canonical(env[k])!==canonical(first[k]))throw new JournalCorruption('entry '+seq+' changes the task binding '+k);
   seen.add(env.messageId);prev=hash;
   return e;
  });
 }

 /** Removes an incomplete last line (crash during append). Returns what was removed, or null. */
 truncateTornTail(){
  const {torn}=this.#lines();
  if(!torn)return null;
  fs.truncateSync(this.file,torn.offset);
  return torn;
 }

 /**
  * Appends a validated envelope atomically (single write + fsync). Duplicate messageId: identical envelope -> the
  * existing entry (idempotent); different content -> error. Trusted senders get the runtime MAC.
  */
 append(env,{onPartialWrite=null}={}){
  const list=this.entries();
  const dup=list.find(x=>x.messageId===env.messageId);
  if(dup){
   const {prevHash,hash,mac,sequence,createdAt,...old}=dup;
   const {sequence:s2,createdAt:c2,...now}=env;
   if(canonical(old)===canonical(now))return {entry:dup,duplicate:true};
   throw Error('conflicting duplicate message '+env.messageId);
  }
  const record={...env,sequence:list.length};
  const problems=envelopeProblems(record);
  if(problems.length)throw Error('invalid envelope: '+problems.join('; '));
  const prevHash=list.length?list.at(-1).hash:ZERO;
  const hash=sha256(canonical({...record,prevHash}));
  const line=JSON.stringify({...record,prevHash,hash,...(TRUSTED.has(record.sender)?{mac:createHmac('sha256',this.key).update(hash).digest('hex')}:{})})+'\n';
  fs.mkdirSync(path.dirname(this.file),{recursive:true,mode:0o700});
  const fd=fs.openSync(this.file,'a',0o600);
  try{
   if(onPartialWrite){fs.writeSync(fd,line.slice(0,Math.floor(line.length/2)));fs.fsyncSync(fd);onPartialWrite();}
   fs.writeSync(fd,line);fs.fsyncSync(fd);
  }finally{fs.closeSync(fd);}
  return {entry:JSON.parse(line),duplicate:false};
 }
}

/** Exclusive writer lock per worktree; a lock of a dead process on this host is stale and is taken over. */
export class WriterLock{
 constructor(stateDir,worktreeId){this.file=path.join(stateDir,'locks',worktreeId+'.lock');this.held=false;}
 acquire(){
  fs.mkdirSync(path.dirname(this.file),{recursive:true,mode:0o700});
  const mine=JSON.stringify({pid:process.pid,host:os.hostname(),at:new Date().toISOString()});
  for(let attempt=0;attempt<2;attempt++){
   try{fs.writeFileSync(this.file,mine,{flag:'wx',mode:0o600});this.held=true;return {acquired:true,stale:attempt>0};}
   catch(e){
    if(e.code!=='EEXIST')throw e;
    let owner=null;try{owner=JSON.parse(fs.readFileSync(this.file,'utf8'));}catch{}
    if(owner&&owner.host===os.hostname()&&alive(owner.pid))return {acquired:false,owner};
    fs.rmSync(this.file,{force:true});       // stale: the writer died without releasing
   }
  }
  return {acquired:false,owner:null};
 }
 release(){if(this.held){fs.rmSync(this.file,{force:true});this.held=false;}}
}
function alive(pid){try{process.kill(pid,0);return true;}catch(e){return e.code==='EPERM';}}
