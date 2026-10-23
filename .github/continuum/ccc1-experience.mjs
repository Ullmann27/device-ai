// CCC1 experience log: one record per finished task, derived only from the verified task journal (receipts come from
// the verifier, never from the reasoner). Hypotheses stay proposals; a record says which ones the independent contract
// test confirmed or refuted, which question was asked and what it cost. Append-only JSONL with a hash chain; a task is
// recorded once. This is the raw material for learning from verified results - nothing here trains or promotes.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {canonical} from './ccc1-envelope.mjs';
import {taskState} from './ccc1-orchestrator.mjs';

const sha=v=>createHash('sha256').update(typeof v==='string'?v:canonical(v)).digest('hex');

/** The record of a finished task, or null while it is still open. */
export function experienceOf(taskId,entries,{coreId=null}={}){
 const s=taskState(entries);
 if(!s.terminal)return null;
 const goal=entries.find(e=>e.kind==='GOAL');
 const byId=new Map(entries.map(e=>[e.messageId,e]));
 const hypotheses=entries.filter(e=>e.kind==='TEST_REQUEST').map(request=>{
  const receipt=entries.find(e=>e.kind==='RECEIPT'&&e.payload.request===request.messageId);
  const h=byId.get(request.payload.hypothesis);
  return {module:h?.payload.module??request.payload.module,export:h?.payload.export??request.payload.export,
   verdict:receipt?.payload.verdict??'NO_RECEIPT',receiptSha256:receipt?.payload.receiptSha256??null};
 });
 const abstain=entries.find(e=>e.kind==='ABSTAIN')?.payload.reason??null;
 const confirmed=hypotheses.find(h=>h.verdict==='CONFIRMED')??null;
 return {schema:'continuum-ccc1-experience',version:1,taskId,goalSha256:sha(goal?.payload.text??''),
  baseCommitSha:goal?.baseCommitSha??null,outcome:s.state,coreId,
  question:abstain?.startsWith('Mehrdeutig')?abstain:null,abstainReason:abstain&&!abstain.startsWith('Mehrdeutig')?abstain:null,
  hypotheses,confirmed:confirmed?{module:confirmed.module,export:confirmed.export}:null,rounds:s.rounds,cpuMillis:s.cpuMillis};
}

/** Append-only, hash-chained JSONL; append() is idempotent per task. */
export class ExperienceLog{
 constructor(file){this.file=file;}
 entries(){
  if(!fs.existsSync(this.file))return [];
  const lines=fs.readFileSync(this.file,'utf8').split('\n').filter(Boolean);
  let prev=null;
  return lines.map((line,i)=>{
   const r=JSON.parse(line);
   const {hash,...data}=r;
   if(data.prevHash!==prev||hash!==sha(data))throw Error('experience log damaged at line '+(i+1));
   prev=hash;
   return r;
  });
 }
 append(record){
  const all=this.entries();
  if(all.some(r=>r.record.taskId===record.taskId))return false;
  const data={sequence:all.length,prevHash:all.at(-1)?.hash??null,record};
  fs.mkdirSync(path.dirname(this.file),{recursive:true,mode:0o700});
  fs.appendFileSync(this.file,JSON.stringify({...data,hash:sha(data)})+'\n',{mode:0o600});
  return true;
 }
}
