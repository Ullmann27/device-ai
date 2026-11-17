// CCC1 experience log: one record per finished task, derived only from the verified task journal (receipts come from
// the verifier, never from the reasoner). Hypotheses stay proposals; a record says which ones the independent contract
// test confirmed or refuted, which question was asked and what it cost, and which comparable candidates the probe could
// not run (a confirmation among the checkable candidates only). Append-only JSONL with a hash chain; a task is
// recorded once. This is the raw material for learning from verified results - nothing here trains or promotes.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {canonical} from './ccc1-envelope.mjs';
import {taskState} from './ccc1-orchestrator.mjs';

const sha=v=>createHash('sha256').update(typeof v==='string'?v:canonical(v)).digest('hex');

/** The record of a finished task, or null while it is still open. */
export function experienceOf(taskId,entries,{coreId=null,unchecked=[]}={}){
 const s=taskState(entries);
 if(!s.terminal)return null;
 const goal=entries.find(e=>e.kind==='GOAL');
 const byId=new Map(entries.map(e=>[e.messageId,e]));
 const hypotheses=entries.filter(e=>e.kind==='TEST_REQUEST').map(request=>{
  const receipt=entries.find(e=>e.kind==='RECEIPT'&&e.payload.request===request.messageId);
  const h=byId.get(request.payload.hypothesis);
  const repair=h?.payload.repair?.chain??request.payload.repair?.chain??null;
  return {module:repair?null:h?.payload.module??request.payload.module,export:repair?null:h?.payload.export??request.payload.export,
   ...(repair?{repair,patchSha256:receipt?.payload.patchSha256??null}:{}),
   verdict:receipt?.payload.verdict??'NO_RECEIPT',receiptSha256:receipt?.payload.receiptSha256??null};
 });
 const abstain=entries.find(e=>e.kind==='ABSTAIN')?.payload.reason??null;
 const confirmed=hypotheses.find(h=>h.verdict==='CONFIRMED')??null;
 return {schema:'continuum-ccc1-experience',version:1,taskId,goalSha256:sha(goal?.payload.text??''),
  baseCommitSha:goal?.baseCommitSha??null,outcome:s.state,coreId,
  question:abstain?.startsWith('Mehrdeutig')?abstain:null,abstainReason:abstain&&!abstain.startsWith('Mehrdeutig')?abstain:null,
  hypotheses,confirmed:confirmed?confirmed.repair?{repair:confirmed.repair,patchSha256:confirmed.patchSha256}:{module:confirmed.module,export:confirmed.export}:null,
  uncheckedAlternatives:unchecked.slice(0,10),rounds:s.rounds,cpuMillis:s.cpuMillis};
}

/** Hard bounds apply to the existing experience ledger, including revocation records. */
export const EXPERIENCE_LIMITS=Object.freeze({maxRecords:4096,maxBytes:16*1024*1024,maxRecordBytes:256*1024,lockWaitMs:2000});
const unsafe=()=>{throw Error('experience log unsafe path');};
function safeParent(file,{create=false}={}){
 const parent=path.dirname(path.resolve(file));
 if(create)fs.mkdirSync(parent,{recursive:true,mode:0o700});
 if(fs.existsSync(parent)&&fs.realpathSync(parent)!==parent)unsafe();
 if(fs.existsSync(file)&&fs.lstatSync(file).isSymbolicLink())unsafe();
}
function durability(file){
 if(fs.existsSync(file)){
  const fd=fs.openSync(file,fs.constants.O_RDONLY|fs.constants.O_NOFOLLOW);
  try{fs.fsyncSync(fd);}finally{fs.closeSync(fd);}
 }
 const fd=fs.openSync(path.dirname(path.resolve(file)),fs.constants.O_RDONLY|fs.constants.O_DIRECTORY);
 try{fs.fsyncSync(fd);}finally{fs.closeSync(fd);}
}

/** Append-only JSONL; one exclusive directory lease for the entire read/check/write/fsync transaction.
 * Crash leases intentionally fail closed. An operator must inspect uncertain writes before removing a lease.
 * Hashes detect damage; authenticity is supplied by the technical proof's runtime MAC, never by this chain.
 */
export class ExperienceLog{
 constructor(file){this.file=path.resolve(file);}
 entries(){
  safeParent(this.file);
  if(!fs.existsSync(this.file))return [];
  const fd=fs.openSync(this.file,fs.constants.O_RDONLY|fs.constants.O_NOFOLLOW);
  let text;
  try{
   const stat=fs.fstatSync(fd);
   if(!stat.isFile()||stat.size>EXPERIENCE_LIMITS.maxBytes)throw Error('experience log byte bound');
   const bytes=Buffer.alloc(stat.size);let offset=0;
   while(offset<bytes.length){const n=fs.readSync(fd,bytes,offset,bytes.length-offset,offset);if(!n)throw Error('experience log changed while reading');offset+=n;}
   if(fs.fstatSync(fd).size!==stat.size)throw Error('experience log changed while reading');
   text=bytes.toString('utf8');
   if(!Buffer.from(text).equals(bytes))throw Error('experience log damaged UTF-8');
  }finally{fs.closeSync(fd);}
  if(!text)return [];
  if(!text.endsWith('\n'))throw Error('experience log torn append; inspection required');
  const lines=text.slice(0,-1).split('\n');
  if(lines.length>EXPERIENCE_LIMITS.maxRecords)throw Error('experience log record bound');
  let prev=null;const seen=new Set();
  return lines.map((line,i)=>{
   if(Buffer.byteLength(line)+1>EXPERIENCE_LIMITS.maxRecordBytes)throw Error('experience record too large');
   let r;try{r=JSON.parse(line);}catch{throw Error('experience log damaged at line '+(i+1));}
   const {hash,...data}=r;
   if(data.sequence!==i||data.prevHash!==prev||hash!==sha(data)||
      typeof data.record?.taskId!=='string'||!data.record.taskId||seen.has(data.record.taskId))
    throw Error('experience log damaged sequence or record at line '+(i+1));
   seen.add(data.record.taskId);prev=hash;return r;
  });
 }
 append(record){
  if(!record||typeof record.taskId!=='string'||!record.taskId||record.taskId.length>200)throw Error('experience record task id invalid');
  // Serialize once so caller mutation and toJSON cannot change bytes after validation.
  const snapshot=JSON.parse(JSON.stringify(record));
  if(!snapshot||typeof snapshot.taskId!=='string'||!snapshot.taskId||snapshot.taskId.length>200)throw Error('experience record task id invalid');
  if(Buffer.byteLength(JSON.stringify(snapshot))+200>EXPERIENCE_LIMITS.maxRecordBytes)throw Error('experience record too large');
  safeParent(this.file,{create:true});
  const lock=this.file+'.lock',until=Date.now()+EXPERIENCE_LIMITS.lockWaitMs;
  for(;;){
   try{fs.mkdirSync(lock,{mode:0o700});break;}
   catch(e){if(e.code!=='EEXIST')throw e;if(Date.now()>=until)throw Error('experience log locked; inspect crashed writer before retry');Atomics.wait(new Int32Array(new SharedArrayBuffer(4)),0,0,10);}
  }
  try{
   const all=this.entries();
   if(all.some(r=>r.record.taskId===snapshot.taskId)){durability(this.file);return false;}
   if(all.length>=EXPERIENCE_LIMITS.maxRecords)throw Error('experience log record bound');
   const data={sequence:all.length,prevHash:all.at(-1)?.hash??null,record:snapshot};
   const line=JSON.stringify({...data,hash:sha(data)})+'\n';
   if(Buffer.byteLength(line)>EXPERIENCE_LIMITS.maxRecordBytes)throw Error('experience record too large');
   const fd=fs.openSync(this.file,fs.constants.O_WRONLY|fs.constants.O_APPEND|fs.constants.O_CREAT|fs.constants.O_NOFOLLOW,0o600);
   try{
    if(fs.fstatSync(fd).size+Buffer.byteLength(line)>EXPERIENCE_LIMITS.maxBytes)throw Error('experience log byte bound');
    const bytes=Buffer.from(line);let offset=0;
    while(offset<bytes.length){const n=fs.writeSync(fd,bytes,offset,bytes.length-offset);if(!n)throw Error('experience log incomplete append');offset+=n;}
    fs.fsyncSync(fd);
   }finally{fs.closeSync(fd);}
   durability(this.file);return true;
  }finally{fs.rmdirSync(lock);}
 }
}
