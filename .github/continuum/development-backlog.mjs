import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import crypto from 'node:crypto';

export const MAX_BYTES=2_000_000;
const STATUSES=new Set(['OPEN','CLAIMED','BLOCKED','DONE','REJECTED']);
const SOURCES=new Set(['PLANNER','MANUAL','AGENT']);
export const hash=value=>crypto.createHash('sha256').update(value).digest('hex');
const clean=(s,n=240)=>String(s??'').replace(/\s+/g,' ').trim().slice(0,n);
const score=x=>Math.min(100,Math.max(0,Number.isFinite(Number(x))?Math.round(Number(x)):0));
// Wire contract: UTF-8, sorted ASCII object keys, JSON strings, safe integers only.
// No floats, normalization, BOM, whitespace or platform-specific escaping.
export function canonical(x){
 if(x===null||typeof x==='boolean')return JSON.stringify(x);
 if(typeof x==='string'){if(!x.isWellFormed())throw Error('Invalid Unicode');return JSON.stringify(x);}
 if(typeof x==='number'&&Number.isSafeInteger(x))return String(x);
 if(Array.isArray(x))return '['+x.map(canonical).join(',')+']';
 if(x&&Object.getPrototypeOf(x)===Object.prototype)return '{'+Object.keys(x).sort().map(k=>{
  if(!/^[A-Za-z][A-Za-z0-9]*$/.test(k))throw Error('Invalid field');return JSON.stringify(k)+':'+canonical(x[k]);
 }).join(',')+'}';
 throw Error('Non-canonical value');
}
export const taskHash=t=>hash(canonical(t));
export const goalHash=t=>hash(canonical({kind:t.kind,file:t.file,symbol:t.symbol,baseCommit:t.baseCommit,criteria:t.criteria}));
export const priority=({benefit=0,risk=0,effort=0})=>Math.max(0,Math.round(score(benefit)*.65-score(risk)*.20-score(effort)*.15));
export const emptyBacklog=()=>({schemaVersion:2,generation:0,tasks:[]});
export function normalizeTask(raw,now=new Date().toISOString()){
 const title=clean(raw.title,160),file=clean(raw.file),kind=clean(raw.kind,70),symbol=clean(raw.symbol);
 if(!title)throw Error('Task title required');
 const benefit=score(raw.benefit),risk=score(raw.risk),effort=score(raw.effort);
 return {id:'task-'+hash(canonical([title.toLowerCase(),file,kind,symbol])).slice(0,20),revision:1,parentHash:null,
 title,file,kind,symbol,source:SOURCES.has(raw.source)?raw.source:'MANUAL',status:'OPEN',benefit,risk,effort,
 priority:priority({benefit,risk,effort}),owner:null,notes:[],evidence:(raw.evidence??[]).map(x=>clean(x)).slice(-12),
 createdAt:now,updatedAt:now,baseCommit:raw.baseCommit??'',
 criteria:raw.criteria??{type:'MANUAL_REVIEW',fingerprint:'',chain:''},receipt:null};
}
function str(x,n){return typeof x==='string'&&x.length<=n&&x.isWellFormed();}
export function validate(b){
 if(!b||b.schemaVersion!==2||!Number.isSafeInteger(b.generation)||b.generation<0||!Array.isArray(b.tasks)||b.tasks.length>500)throw Error('Invalid backlog');
 const ids=new Set();
 for(const t of b.tasks){
  if(!/^task-[a-f0-9]{20}$/.test(t.id)||ids.has(t.id)||!STATUSES.has(t.status)||!SOURCES.has(t.source))throw Error('Invalid task');ids.add(t.id);
  if(!Number.isSafeInteger(t.revision)||t.revision<1||!(t.parentHash===null||/^[a-f0-9]{64}$/.test(t.parentHash)))throw Error('Invalid revision');
  for(const k of ['benefit','risk','effort','priority'])if(!Number.isInteger(t[k])||t[k]<0||t[k]>100)throw Error('Invalid score');
  if(!str(t.title,160)||!t.title.trim())throw Error('Invalid title');
  for(const k of ['file','kind','symbol','createdAt','updatedAt','baseCommit'])if(!str(t[k],240))throw Error('Invalid '+k);
  if(t.baseCommit!==''&&!/^[a-f0-9]{40}$/.test(t.baseCommit))throw Error('Invalid base commit');
  if(!(t.owner===null||str(t.owner,80))||(t.status==='CLAIMED'&&!t.owner))throw Error('Invalid owner');
  for(const k of ['notes','evidence'])if(!Array.isArray(t[k])||t[k].length>12||t[k].some(x=>!str(x,240)))throw Error('Invalid notes');
  const c=t.criteria;
  if(!c||!['MANUAL_REVIEW','REPAIR_DIAGNOSTIC'].includes(c.type)||!str(c.fingerprint,64)||!str(c.chain,20))throw Error('Invalid criteria');
  if(c.type==='REPAIR_DIAGNOSTIC'&&(!/^[a-f0-9]{16}$/.test(c.fingerprint)||!['node','android','jvm'].includes(c.chain)||!t.baseCommit||!t.file))throw Error('Invalid repair criteria');
  if(t.receipt!==null)validateReceipt(t);
  if(t.status==='DONE'&&!t.receipt)throw Error('DONE requires verification receipt');
 }
 if(Buffer.byteLength(canonical(b))>MAX_BYTES)throw Error('Backlog too large');return b;
}
function validateReceipt(t){
 const r=t.receipt;
 if(!r||r.taskId!==t.id||r.goalHash!==goalHash(t)||r.baseCommit!==t.baseCommit||r.outcome!=='VERIFIED'||
 !Number.isSafeInteger(r.taskRevision)||r.taskRevision<1||r.taskRevision>=t.revision||
 !/^[a-f0-9]{64}$/.test(r.patchSha256)||!Number.isSafeInteger(r.tests)||r.tests<1||!str(r.strategy,100)||
 r.diagnostic!==t.criteria.fingerprint)throw Error('Invalid goal receipt');
}
function edit(t,fn){const parentHash=taskHash(t),revision=t.revision+1;fn(t);t.parentHash=parentHash;t.revision=revision;t.updatedAt=new Date().toISOString();}
export function addTask(b,input){const t=normalizeTask(input);if(b.tasks.some(x=>x.id===t.id))throw Error('Task already exists');if(b.tasks.length>=500)throw Error('Backlog full');b.tasks.push(t);return b;}
export function changeStatus(b,id,status,{owner,note}={}){
 const t=b.tasks.find(t=>t.id===id);if(!t)throw Error('Unknown task');
 const allowed={OPEN:['CLAIMED','BLOCKED','REJECTED'],CLAIMED:['OPEN','BLOCKED','REJECTED'],BLOCKED:['OPEN','REJECTED'],DONE:['OPEN'],REJECTED:['OPEN']};
 if(status==='DONE')throw Error('Invalid transition: DONE requires goal verification receipt');
 if(!allowed[t.status].includes(status))throw Error('Invalid transition');
 if(status==='CLAIMED'&&!clean(owner,80))throw Error('Claim owner required');
 edit(t,x=>{x.status=status;x.owner=status==='CLAIMED'?clean(owner,80):null;x.receipt=null;if(clean(note))x.notes=[...x.notes,clean(note)].slice(-12);});return b;
}
export function prioritize(b,id,values){const t=b.tasks.find(t=>t.id===id);if(!t)throw Error('Unknown task');edit(t,x=>{for(const k of ['benefit','risk','effort'])if(values[k]!==undefined)x[k]=score(values[k]);x.priority=priority(x);});return b;}
export const listTasks=(b,status)=>b.tasks.filter(t=>!status||t.status===status).sort((a,b)=>b.priority-a.priority||a.id.localeCompare(b.id));
export function mergePlanner(b,report){
 if(!Array.isArray(report?.tasks))throw Error('Invalid planner report');
 for(const item of report.tasks){const kind=clean(item.kind,70);const t=normalizeTask({title:`${kind}: ${clean(item.symbol)||clean(item.file)}`,file:item.file,symbol:item.symbol,kind,source:'PLANNER',benefit:kind==='DEAD_CLUSTER'?75:kind==='TEST_ONLY'?65:45,risk:20,effort:35,evidence:[item.action,item.confidence].map(x=>clean(x))});
 if(!b.tasks.some(x=>x.id===t.id)){if(b.tasks.length>=500)throw Error('Backlog full');b.tasks.push(t);}}
 b.tasks=listTasks(b);return b;
}
export function mergeBacklog(b,incoming,{resolve}={}){
 validate(b);validate(incoming);const draft=structuredClone(b),byId=new Map(draft.tasks.map(t=>[t.id,t]));
 for(const t of incoming.tasks){const old=byId.get(t.id);if(!old){byId.set(t.id,structuredClone(t));continue;}
 if(taskHash(t)===taskHash(old))continue;
 if(t.revision===old.revision+1&&t.parentHash===taskHash(old)){byId.set(t.id,structuredClone(t));continue;}
 if(resolve==='local')continue;
 if(resolve==='incoming'){const copy=structuredClone(t);copy.revision=Math.max(old.revision,t.revision)+1;copy.parentHash=taskHash(old);copy.updatedAt=new Date().toISOString();byId.set(t.id,copy);continue;}
 throw Error('Backlog import conflict for '+t.id+'; choose local or incoming explicitly');
 }
 draft.tasks=[...byId.values()];return validate(draft);
}
// Invoked only with the report produced by this process, never from imported JSON.
export function completeRepair(b,id,report){
 const t=b.tasks.find(t=>t.id===id),c=t?.criteria;
 if(!t||c.type!=='REPAIR_DIAGNOSTIC')throw Error('No task-specific goal criteria');
 const v=report.verified,a=report.attempts?.find(x=>x.candidate===v?.candidate&&x.outcome==='VERIFIED');
 if(t.status!=='CLAIMED'||report.outcome!=='VERIFIED'||report.base!==t.baseCommit||report.chain!==c.chain||
 report.baseline?.passed!==false||!report.baseline?.diagnostics?.some(d=>d.fingerprint===c.fingerprint&&d.file===t.file)||
 a?.diagnostic!==c.fingerprint||!v?.files?.includes(t.file)||!v.tests?.length||
 v.tests.some(x=>!x.passed||x.exitCode!==0||x.tests.failed!==0)||hash(v.patch)!==v.patchSha256)throw Error('Repair does not satisfy task goal');
 const tests=v.tests.reduce((n,x)=>n+x.tests.total,0);if(tests<1)throw Error('No test evidence');
 const receipt={taskId:id,taskRevision:t.revision,goalHash:goalHash(t),baseCommit:t.baseCommit,diagnostic:c.fingerprint,
 patchSha256:v.patchSha256,strategy:v.strategy,tests,outcome:'VERIFIED'};
 edit(t,x=>{x.status='DONE';x.owner=null;x.receipt=receipt;});return validate(b);
}
function bytes(text,limit){if(typeof text!=='string'||Buffer.byteLength(text)>limit)throw Error('Bridge oversized');return text;}
export function exportBridge(b){const payload=Buffer.from(canonical(validate(b)));return canonical({schemaVersion:2,format:'continuum-backlog-bridge',payloadBase64:payload.toString('base64'),sha256:hash(payload)})+'\n';}
export function importBridge(text){
 const e=JSON.parse(bytes(text,3_000_000));
 if(e.schemaVersion!==2||e.format!=='continuum-backlog-bridge'||typeof e.payloadBase64!=='string')throw Error('Invalid bridge');
 const p=Buffer.from(e.payloadBase64,'base64');
 if(p.length>MAX_BYTES||p.toString('base64')!==e.payloadBase64||hash(p)!==e.sha256)throw Error('Bridge checksum mismatch');
 const value=JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(p));
 if(canonical(value)!==p.toString('utf8'))throw Error('Non-canonical bridge');return validate(value);
}
function read(file){if(fs.statSync(file).size>3_000_000)throw Error('Backlog too large');const {checksum,...b}=JSON.parse(fs.readFileSync(file,'utf8'));if(checksum!==hash(canonical(b)))throw Error('Backlog checksum mismatch');return validate(b);}
export function readBacklog(file){if(fs.existsSync(file))return read(file);if(fs.existsSync(file+'.bak'))throw Error('Missing primary: explicit recovery required');return emptyBacklog();}
function atomic(file,data){const tmp=file+'.tmp-'+crypto.randomUUID();try{const fd=fs.openSync(tmp,'wx',0o600);try{fs.writeFileSync(fd,data);fs.fsyncSync(fd);}finally{fs.closeSync(fd);}fs.renameSync(tmp,file);const dirfd=fs.openSync(path.dirname(file),'r');try{fs.fsyncSync(dirfd);}finally{fs.closeSync(dirfd);}}finally{if(fs.existsSync(tmp))fs.unlinkSync(tmp);}}
function lock(file,fn){fs.mkdirSync(path.dirname(file),{recursive:true});const name=file+'.lock';let fd;try{fd=fs.openSync(name,'wx',0o600);fs.writeSync(fd,JSON.stringify({pid:process.pid,host:os.hostname()}));fs.fsyncSync(fd);return fn();}finally{if(fd!==undefined){fs.closeSync(fd);fs.unlinkSync(name);}}}
export function transaction(file,mutate,{expectedGeneration}={}){
 return lock(file,()=>{const current=readBacklog(file);if(expectedGeneration!==undefined&&current.generation!==expectedGeneration)throw Error('Backlog generation conflict');
 const draft=structuredClone(current),next=validate(mutate(draft)??draft);next.generation=current.generation+1;validate(next);
 if(fs.existsSync(file))atomic(file+'.bak',fs.readFileSync(file));
 atomic(file,JSON.stringify({...next,checksum:hash(canonical(next))},null,2)+'\n');return next;});
}
export function recoverBacklog(file){
 // Recovery is explicit; a lock is never considered stale merely because it is old.
 fs.mkdirSync(path.dirname(file),{recursive:true});const guard=file+'.recover';let fd;
 try{fd=fs.openSync(guard,'wx',0o600);
 if(fs.existsSync(file+'.lock')){const l=JSON.parse(fs.readFileSync(file+'.lock','utf8'));
 if(l.host!==os.hostname()||!Number.isInteger(l.pid)||l.pid<=0)throw Error('Unknown lock owner; manual review required');
 try{process.kill(l.pid,0);throw Error('Backlog lock owner is active');}catch(e){if(e.code!=='ESRCH')throw e;}
 fs.unlinkSync(file+'.lock');}
 return lock(file,()=>{
 let state;try{state=readBacklog(file);if(fs.existsSync(file))atomic(file+'.bak',fs.readFileSync(file));}catch(error){
  if(!fs.existsSync(file+'.bak'))throw error;state=read(file+'.bak');
  if(fs.existsSync(file))atomic(file+'.corrupt-'+crypto.randomUUID(),fs.readFileSync(file));
 }
 for(const t of state.tasks)if(t.status==='CLAIMED')changeStatus(state,t.id,'BLOCKED',{note:'Explicit recovery: previous claim requires review before restart'});
 state.generation++;validate(state);atomic(file,JSON.stringify({...state,checksum:hash(canonical(state))},null,2)+'\n');return state;
 });
 }finally{if(fd!==undefined){fs.closeSync(fd);fs.unlinkSync(guard);}}
}
