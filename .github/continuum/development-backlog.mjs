import crypto from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

/**
 * Persistent development backlog shared by the Node agent, the CLI and the
 * Android app. app/src/main/java/at/heinz/deviceai/cognitive/DevelopmentBacklog.kt
 * implements the SAME document format and state rules; both run the shared
 * conformance vectors in app/src/test/resources/continuum/backlog/.
 *
 * Document: canonical JSON (sorted keys, no whitespace, integers only, UTF-8)
 * plus "integrity" = SHA-256 of the canonical document without that field.
 * The hash detects corruption; it is not a signature and proves no authorship.
 *
 * State changes happen only through operations (applyOp). Invariants checked
 * on every load and after every operation: a task is DONE only with test
 * evidence that names the task, the submitted artifact and its submission
 * token, with every check passed and tests really executed.
 */
export const SCHEMA='continuum-development-backlog';
export const SCHEMA_VERSION=2;
export const STATES=Object.freeze(['PROPOSED','READY','IN_PROGRESS','VERIFYING','BLOCKED','DONE','FAILED','ABORTED','REJECTED']);
export const OPS=Object.freeze(['propose','accept','reject','claim','claimNext','renew','submit','complete','fail','expire','abort','retry','setPriority']);
export const ARTIFACT_TYPES=Object.freeze(['PATCH','PROPOSAL','REPORT']);
export const TEST_PROFILES=Object.freeze(['node-continuum','android-unit','jvm-harness']);
export const LIMITS=Object.freeze({tasks:500,history:40,evidence:8,dependsOn:16,profiles:8,title:200,text:400,
  subjectKeys:12,subjectValue:300,fileBytes:4_000_000,maxAttempts:20,attempts:1000,priority:1000,
  leaseMin:60_000,leaseMax:86_400_000,depth:16,count:10_000_000,recentOps:256});
const TERMINAL=new Set(['DONE','ABORTED','REJECTED']);
const DEPENDENCY_FAILED=new Set(['FAILED','ABORTED','REJECTED']);
const HISTORY_OPS=new Set([...OPS,'reconcile','migrate']);
const MAX_SAFE=Number.MAX_SAFE_INTEGER;

const ID_RE=/^[a-z][a-z0-9-]{2,63}$/;
const ACTOR_RE=/^[A-Za-z0-9][A-Za-z0-9._@:-]{0,63}$/;
const KIND_RE=/^[A-Z][A-Z0-9_]{1,31}$/;
const SOURCE_RE=/^[a-z][a-z0-9-]{1,31}$/;
const PROFILE_RE=/^[a-z][a-z0-9-]{1,47}$/;
const CODE_RE=/^[A-Z][A-Z0-9_]{1,47}$/;
const STRATEGY_RE=/^[A-Z][A-Z0-9_:]{1,79}$/;
const SUBJECT_KEY_RE=/^[a-zA-Z][a-zA-Z0-9]{0,31}$/;
const TOKEN_RE=/^[0-9a-f]{32}$/;
const SHA_RE=/^[0-9a-f]{64}$/;
const HEX16_RE=/^[0-9a-f]{16}$/;
const COMMIT_RE=/^[0-9a-f]{40}$/;
const OP_ID_RE=/^[A-Za-z0-9][A-Za-z0-9._:-]{7,79}$/;
const CONTROL_RE=/[\u0000-\u001f\u007f]/;
const CONTROL_ALL=/[\u0000-\u001f\u007f]/g;
const LONE_SURROGATE=/[\uD800-\uDBFF](?![\uDC00-\uDFFF])|(?<![\uD800-\uDBFF])[\uDC00-\uDFFF]/;

export class BacklogError extends Error{
  constructor(code,message){super(code+': '+message);this.code=code;this.detail=message;}
}
const fail=(code,message)=>{throw new BacklogError(code,message);};

// ---------------------------------------------------------------------------
// Canonical JSON and strict parsing (mirrors learning/StrictJson.kt)
// ---------------------------------------------------------------------------
const isObject=v=>v!==null&&typeof v==='object'&&!Array.isArray(v);

/** Truncate to at most n UTF-16 units without splitting a surrogate pair. */
export function clip(value,n){
  const r=String(value).slice(0,n);
  return /[\uD800-\uDBFF]$/.test(r)?r.slice(0,-1):r;
}
/** Single-line text: control characters become spaces, then clip. */
export const singleLine=(value,n)=>clip(String(value).replace(CONTROL_ALL,' '),n);

export function canonical(value,depth=0){
  if(depth>64)fail('INVALID_FIELD','nesting too deep');
  if(value===null)return 'null';
  if(value===true)return 'true';
  if(value===false)return 'false';
  if(typeof value==='number'){
    if(!Number.isSafeInteger(value))fail('INVALID_FIELD','only integers within ±2^53-1 are allowed');
    return String(value);
  }
  if(typeof value==='string'){
    if(LONE_SURROGATE.test(value))fail('INVALID_FIELD','lone surrogate in string');
    return JSON.stringify(value);
  }
  if(Array.isArray(value))return '['+value.map(v=>canonical(v,depth+1)).join(',')+']';
  if(isObject(value)){
    const keys=Object.keys(value).sort();
    return '{'+keys.map(k=>{
      if(value[k]===undefined)fail('INVALID_FIELD','undefined value for '+k);
      return canonical(k,depth+1)+':'+canonical(value[k],depth+1);
    }).join(',')+'}';
  }
  fail('INVALID_FIELD','unsupported value type '+typeof value);
}

export const sha256Hex=data=>crypto.createHash('sha256').update(data).digest('hex');

/** RFC 8259 reader: duplicate keys, non-integers, lone surrogates and trailing data are rejected. */
export function parseStrict(text){
  let i=0;
  const err=m=>fail('CORRUPT',m+' (offset '+i+')');
  const ws=()=>{while(i<text.length&&(text[i]===' '||text[i]==='\n'||text[i]==='\r'||text[i]==='\t'))i++;};
  const literal=(word,result)=>{if(!text.startsWith(word,i))err('invalid literal');i+=word.length;return result;};
  function value(depth){
    if(depth>LIMITS.depth)err('JSON nesting too deep');
    if(i>=text.length)err('unexpected end');
    const c=text[i];
    if(c==='{')return object(depth+1);
    if(c==='[')return array(depth+1);
    if(c==='"')return string();
    if(c==='t')return literal('true',true);
    if(c==='f')return literal('false',false);
    if(c==='n')return literal('null',null);
    return number();
  }
  function object(depth){
    i++;const out={};let n=0;ws();
    if(text[i]==='}'){i++;return out;}
    for(;;){
      ws();if(text[i]!=='"')err('object key expected');
      const key=string();
      if(Object.prototype.hasOwnProperty.call(out,key))err('duplicate key '+JSON.stringify(key.slice(0,40)));
      ws();if(text[i]!==':')err("':' expected");i++;ws();
      Object.defineProperty(out,key,{value:value(depth),enumerable:true,writable:true,configurable:true});
      if(++n>4096)err('object too large');
      ws();
      if(text[i]===','){i++;continue;}
      if(text[i]==='}'){i++;return out;}
      err("',' or '}' expected");
    }
  }
  function array(depth){
    i++;const out=[];ws();
    if(text[i]===']'){i++;return out;}
    for(;;){
      ws();out.push(value(depth));
      if(out.length>4096)err('array too large');
      ws();
      if(text[i]===','){i++;continue;}
      if(text[i]===']'){i++;return out;}
      err("',' or ']' expected");
    }
  }
  function string(){
    i++;let out='';
    for(;;){
      if(i>=text.length)err('unterminated string');
      const c=text[i++];
      if(c==='"')break;
      if(c==='\\'){
        if(i>=text.length)err('unterminated escape');
        const e=text[i++];
        if(e==='"'||e==='\\'||e==='/')out+=e;
        else if(e==='b')out+='\b';else if(e==='f')out+='\f';else if(e==='n')out+='\n';
        else if(e==='r')out+='\r';else if(e==='t')out+='\t';
        else if(e==='u'){
          const hex=text.slice(i,i+4);if(!/^[0-9a-fA-F]{4}$/.test(hex))err('invalid unicode escape');
          out+=String.fromCharCode(parseInt(hex,16));i+=4;
        }else err('invalid escape');
      }else if(c<' ')err('control character in string');
      else out+=c;
    }
    if(LONE_SURROGATE.test(out))err('lone surrogate');
    return out;
  }
  function number(){
    const start=i;
    if(text[i]==='-')i++;
    if(!(text[i]>='0'&&text[i]<='9'))err('invalid JSON value');
    if(text[i]==='0'){i++;if(text[i]>='0'&&text[i]<='9')err('leading zero');}
    else while(text[i]>='0'&&text[i]<='9')i++;
    if(text[i]==='.'||text[i]==='e'||text[i]==='E')err('only integers are allowed');
    const literalText=text.slice(start,i);
    if(literalText.length>64)err('number too long');
    const n=Number(literalText);
    if(!Number.isSafeInteger(n))err('integer outside ±2^53-1');
    return n===0?0:n;
  }
  ws();const result=value(0);ws();
  if(i!==text.length)err('trailing content after JSON value');
  return result;
}

// ---------------------------------------------------------------------------
// Validation
// ---------------------------------------------------------------------------
const int=(v,min,max)=>Number.isSafeInteger(v)&&v>=min&&v<=max;
const text=(v,max,allowEmpty=false)=>typeof v==='string'&&v.length<=max&&(allowEmpty||v.length>0)&&!CONTROL_RE.test(v);
function check(ok,code,message){if(!ok)fail(code,message);}
function validSubject(s){
  if(s===null)return true;
  if(!isObject(s)||Object.keys(s).length>LIMITS.subjectKeys)return false;
  return Object.entries(s).every(([k,v])=>SUBJECT_KEY_RE.test(k)&&(typeof v==='boolean'||int(v,-MAX_SAFE,MAX_SAFE)||text(v,LIMITS.subjectValue,true)));
}

export function validateEvidence(e,code='INVALID_FIELD'){
  check(isObject(e),code,'evidence must be an object');
  check(e.type==='TEST_RUN',code,'evidence type must be TEST_RUN');
  check(typeof e.taskId==='string'&&ID_RE.test(e.taskId),code,'evidence taskId invalid');
  check(typeof e.submissionToken==='string'&&TOKEN_RE.test(e.submissionToken),code,'evidence submissionToken invalid');
  check(typeof e.artifactSha256==='string'&&SHA_RE.test(e.artifactSha256),code,'evidence artifactSha256 invalid');
  check(typeof e.chain==='string'&&SOURCE_RE.test(e.chain),code,'evidence chain invalid');
  check(typeof e.commit==='string'&&COMMIT_RE.test(e.commit),code,'evidence commit invalid');
  check(int(e.at,0,MAX_SAFE),code,'evidence at invalid');
  check(typeof e.by==='string'&&ACTOR_RE.test(e.by),code,'evidence by invalid');
  check(Array.isArray(e.profiles)&&e.profiles.length>=1&&e.profiles.length<=LIMITS.profiles,code,'evidence needs 1..8 profiles');
  for(const p of e.profiles){
    check(isObject(p)&&typeof p.profile==='string'&&PROFILE_RE.test(p.profile)&&typeof p.passed==='boolean'&&
      int(p.executed,0,LIMITS.count)&&int(p.failed,0,LIMITS.count)&&int(p.skipped,0,LIMITS.count),code,'evidence profile invalid');
  }
  if(e.repair!==null){
    const r=e.repair;
    check(isObject(r)&&typeof r.diagnostic==='string'&&HEX16_RE.test(r.diagnostic)&&typeof r.candidate==='string'&&HEX16_RE.test(r.candidate)&&
      typeof r.strategy==='string'&&STRATEGY_RE.test(r.strategy)&&typeof r.patchSha256==='string'&&SHA_RE.test(r.patchSha256),code,'evidence repair reference invalid');
  }
  return e;
}

/** Why `e` does not prove `task` done (empty = sufficient). */
export function evidenceProblems(task,e){
  const problems=[];
  if(e.taskId!==task.id)problems.push('evidence belongs to another task');
  const s=task.submission;
  if(!s)problems.push('task has no submission');
  else{
    if(e.submissionToken!==s.token)problems.push('evidence does not match the submission');
    if(e.artifactSha256!==s.artifact.sha256)problems.push('evidence tested a different artifact');
    if(e.at<s.at)problems.push('evidence predates the submission');
  }
  if(e.profiles.some(p=>!p.passed||p.failed>0))problems.push('a check failed');
  if(e.profiles.some(p=>TEST_PROFILES.includes(p.profile)&&p.executed<=0))problems.push('test profile without executed tests');
  if(e.profiles.reduce((a,p)=>a+p.executed,0)<=0)problems.push('no executed tests');
  return problems;
}

function validateTask(t){
  const c='CORRUPT';
  check(isObject(t),c,'task must be an object');
  check(typeof t.id==='string'&&ID_RE.test(t.id),c,'task id invalid');
  const where=' (task '+t.id+')';
  check(text(t.title,LIMITS.title),c,'title invalid'+where);
  check(typeof t.kind==='string'&&KIND_RE.test(t.kind),c,'kind invalid'+where);
  check(typeof t.source==='string'&&SOURCE_RE.test(t.source),c,'source invalid'+where);
  check(STATES.includes(t.state),c,'state invalid'+where);
  check(int(t.priority,0,LIMITS.priority),c,'priority invalid'+where);
  check(Array.isArray(t.dependsOn)&&t.dependsOn.length<=LIMITS.dependsOn,c,'dependsOn invalid'+where);
  t.dependsOn.forEach((d,k)=>check(typeof d==='string'&&ID_RE.test(d)&&d!==t.id&&(k===0||t.dependsOn[k-1]<d),c,'dependsOn must be sorted, unique task ids'+where));
  check(int(t.attempts,0,LIMITS.attempts)&&int(t.maxAttempts,1,LIMITS.maxAttempts),c,'attempt budget invalid'+where);
  if(t.claim!==null){
    const k=t.claim;
    check(isObject(k)&&typeof k.owner==='string'&&ACTOR_RE.test(k.owner)&&typeof k.token==='string'&&TOKEN_RE.test(k.token)&&
      int(k.at,0,MAX_SAFE)&&int(k.leaseUntil,0,MAX_SAFE)&&k.leaseUntil>k.at,c,'claim invalid'+where);
  }
  check((t.state==='IN_PROGRESS')===(t.claim!==null),c,'claim must exist exactly while IN_PROGRESS'+where);
  if(t.submission!==null){
    const s=t.submission;
    check(isObject(s)&&typeof s.token==='string'&&TOKEN_RE.test(s.token)&&typeof s.by==='string'&&ACTOR_RE.test(s.by)&&int(s.at,0,MAX_SAFE)&&
      isObject(s.artifact)&&ARTIFACT_TYPES.includes(s.artifact.type)&&typeof s.artifact.sha256==='string'&&SHA_RE.test(s.artifact.sha256),c,'submission invalid'+where);
  }
  check((t.state==='VERIFYING'||t.state==='DONE')===(t.submission!==null),c,'submission must exist exactly while VERIFYING or DONE'+where);
  check(Array.isArray(t.evidence)&&t.evidence.length<=LIMITS.evidence,c,'evidence list invalid'+where);
  t.evidence.forEach(e=>validateEvidence(e,c));
  if(t.state==='DONE')check(t.evidence.some(e=>evidenceProblems(t,e).length===0),c,'DONE without matching test evidence'+where);
  check(Array.isArray(t.history)&&t.history.length<=LIMITS.history,c,'history invalid'+where);
  for(const h of t.history){
    check(isObject(h)&&int(h.at,0,MAX_SAFE)&&typeof h.by==='string'&&ACTOR_RE.test(h.by)&&HISTORY_OPS.has(h.op)&&
      (h.from===null||STATES.includes(h.from))&&STATES.includes(h.to)&&text(h.reason,LIMITS.text,true),c,'history entry invalid'+where);
  }
  check(int(t.historyDropped,0,MAX_SAFE)&&int(t.evidenceDropped,0,MAX_SAFE),c,'dropped counters invalid'+where);
  if(t.error!==null){
    const e=t.error;
    check(isObject(e)&&typeof e.code==='string'&&CODE_RE.test(e.code)&&text(e.message,LIMITS.text,true)&&int(e.at,0,MAX_SAFE),c,'error invalid'+where);
  }
  check(int(t.createdAt,0,MAX_SAFE)&&int(t.updatedAt,t.createdAt,MAX_SAFE),c,'timestamps invalid'+where);
  check(validSubject(t.subject),c,'subject invalid'+where);
}

export function validateDocument(doc){
  const c='CORRUPT';
  check(isObject(doc),c,'document must be an object');
  check(doc.schema===SCHEMA,c,'not a development backlog');
  if(Number.isSafeInteger(doc.schemaVersion)&&doc.schemaVersion>SCHEMA_VERSION)fail('UNSUPPORTED_VERSION','schemaVersion '+doc.schemaVersion+' is newer than '+SCHEMA_VERSION);
  check(doc.schemaVersion===SCHEMA_VERSION,c,'schemaVersion must be '+SCHEMA_VERSION);
  check(int(doc.revision,0,MAX_SAFE),c,'revision invalid');
  check(int(doc.updatedAt,0,MAX_SAFE),c,'updatedAt invalid');
  check(typeof doc.updatedBy==='string'&&ACTOR_RE.test(doc.updatedBy),c,'updatedBy invalid');
  check(!('integrity' in doc)||(typeof doc.integrity==='string'&&SHA_RE.test(doc.integrity)),c,'integrity invalid');
  if('recentOpIds' in doc){
    const r=doc.recentOpIds;
    check(Array.isArray(r)&&r.length<=LIMITS.recentOps&&r.every(x=>typeof x==='string'&&OP_ID_RE.test(x))&&new Set(r).size===r.length,c,'recentOpIds invalid');
  }
  check(Array.isArray(doc.tasks)&&doc.tasks.length<=LIMITS.tasks,c,'tasks invalid');
  doc.tasks.forEach((t,k)=>{validateTask(t);check(k===0||doc.tasks[k-1].id<t.id,c,'tasks must be sorted by id and unique');});
  const byId=new Map(doc.tasks.map(t=>[t.id,t]));
  for(const t of doc.tasks)for(const d of t.dependsOn)check(byId.has(d),c,'unknown dependency '+d+' of '+t.id);
  // dependency cycles (iterative DFS, deterministic order)
  const color=new Map();
  for(const start of doc.tasks){
    if(color.get(start.id))continue;
    const stack=[[start.id,0]];color.set(start.id,1);
    while(stack.length){
      const top=stack[stack.length-1],deps=byId.get(top[0]).dependsOn;
      if(top[1]<deps.length){
        const next=deps[top[1]++],state=color.get(next);
        if(state===1)fail(c,'dependency cycle through '+next);
        if(!state){color.set(next,1);stack.push([next,0]);}
      }else{color.set(top[0],2);stack.pop();}
    }
  }
  canonical(doc);
  return doc;
}

// ---------------------------------------------------------------------------
// Encoding, integrity, migration
// ---------------------------------------------------------------------------
const withoutIntegrity=doc=>{const {integrity,...rest}=doc;return rest;};
export const integrityOf=doc=>sha256Hex(Buffer.from(canonical(withoutIntegrity(doc)),'utf8'));

export function emptyDocument(at,actor){
  return {schema:SCHEMA,schemaVersion:SCHEMA_VERSION,revision:0,updatedAt:at,updatedBy:actor,tasks:[]};
}

/** Canonical bytes of a validated document including its integrity hash. */
export function encodeDocument(doc){
  validateDocument(doc);
  const body=withoutIntegrity(doc);
  return Buffer.from(canonical({...body,integrity:integrityOf(body)})+'\n','utf8');
}

const decoder=new TextDecoder('utf-8',{fatal:true,ignoreBOM:true});

/**
 * Bytes -> verified document. Schema version 1 (development-planner output)
 * is migrated in memory when allowed; the result says so explicitly.
 */
export function decodeDocument(bytes,{migrate=null}={}){
  if(bytes.length>LIMITS.fileBytes)fail('CORRUPT','backlog larger than '+LIMITS.fileBytes+' bytes');
  let textValue;
  try{textValue=decoder.decode(bytes);}catch{fail('CORRUPT','invalid UTF-8');}
  const doc=parseStrict(textValue);
  if(isObject(doc)&&doc.schema===undefined&&doc.schemaVersion===1){
    if(!migrate)fail('UNSUPPORTED_VERSION','schemaVersion 1 requires migration');
    return {doc:migrateV1(doc,migrate),migratedFrom:1};
  }
  validateDocument(doc);
  check(typeof doc.integrity==='string',"CORRUPT",'integrity missing');
  if(integrityOf(doc)!==doc.integrity)fail('INTEGRITY_MISMATCH','content does not match its integrity hash');
  return {doc,migratedFrom:null};
}

// Maps, not object literals: a finding kind like "constructor" must not hit Object.prototype.
const CONFIDENCE_BONUS=new Map([['high',30],['medium',20],['low',10]]);
const FINDING_SCORE=new Map([['DEAD_CLUSTER',3],['TEST_ONLY',2],['UNREFERENCED',1]]);
/** Planner paths are relative to app/src; backlog paths are repository-relative. */
export const repoPath=file=>/^(main|test|androidTest)\//.test(file)?'app/src/'+file:file;
export const findingTaskId=(kind,file,symbol)=>'inv-'+sha256Hex(kind+'\u0000'+file+'\u0000'+symbol).slice(0,16);
export function findingPriority(kind,confidence){
  return Math.min(LIMITS.priority,(FINDING_SCORE.get(kind)??0)*100+(CONFIDENCE_BONUS.get(confidence)??0));
}
export function findingTask(f){
  const file=singleLine(repoPath(String(f.file??'')),LIMITS.subjectValue),symbol=singleLine(f.symbol??'',LIMITS.subjectValue);
  const shortName=symbol.split('.').pop()||symbol;
  const subject={finding:singleLine(f.kind,40),symbol,file,confidence:singleLine(f.confidence??'unknown',40)};
  return {id:findingTaskId(String(f.kind),file,symbol),title:singleLine(`${f.kind}: ${shortName} (${file})`,LIMITS.title),
    kind:'INVESTIGATE',source:'code-analyzer',priority:findingPriority(f.kind,f.confidence),dependsOn:[],maxAttempts:3,subject};
}

/** schemaVersion 1 (development-planner) -> 2; nothing of the input is dropped. */
export function migrateV1(v1,{at,actor}){
  check(Array.isArray(v1.tasks)&&v1.tasks.length<=LIMITS.tasks,'CORRUPT','v1 tasks invalid');
  let doc=emptyDocument(at,actor);
  const extra={};
  for(const [k,v] of Object.entries(v1))if(k!=='tasks'&&k!=='schemaVersion'){
    check(typeof v==='string'||int(v,-MAX_SAFE,MAX_SAFE)||typeof v==='boolean','CORRUPT','v1 field '+k+' cannot be migrated');
    check(SUBJECT_KEY_RE.test(k),'CORRUPT','v1 field name '+k+' cannot be migrated');extra[k]=v;
  }
  let duplicates=0;
  for(const raw of v1.tasks){
    check(isObject(raw)&&typeof raw.kind==='string'&&typeof raw.symbol==='string'&&typeof raw.file==='string'&&
      (raw.confidence==null||typeof raw.confidence==='string'||typeof raw.confidence==='boolean'||int(raw.confidence,-MAX_SAFE,MAX_SAFE)),'CORRUPT','v1 task invalid');
    const task=findingTask(raw);
    for(const [k,v] of Object.entries(raw))if(!['kind','symbol','file','confidence'].includes(k)){
      check((typeof v==='string'&&text(v,LIMITS.subjectValue,true))||int(v,-MAX_SAFE,MAX_SAFE)||typeof v==='boolean','CORRUPT','v1 task field '+k+' cannot be migrated');
      check(SUBJECT_KEY_RE.test(k),'CORRUPT','v1 task field name '+k+' cannot be migrated');
      task.subject[k]=v;
    }
    if(doc.tasks.some(t=>t.id===task.id)){duplicates++;continue;}
    doc=applyOp(doc,{op:'propose',actor,at,task,reason:'migrated from schemaVersion 1'}).doc;
    const t=doc.tasks.find(x=>x.id===task.id);t.history[t.history.length-1].op='migrate';
  }
  return {...doc,migratedFrom:{schemaVersion:1,...extra,duplicateTasks:duplicates}};
}

// ---------------------------------------------------------------------------
// Operations: the only way to change state (same rules in DevelopmentBacklog.kt)
// ---------------------------------------------------------------------------
function requireTask(doc,id){
  check(typeof id==='string'&&ID_RE.test(id),'INVALID_FIELD','task id invalid');
  const t=doc.tasks.find(x=>x.id===id);
  if(!t)fail('NOT_FOUND','task '+id);
  return t;
}
function record(task,op,to,reason){
  task.history.push({at:op.at,by:op.actor,op:op.op,from:task.state,to,reason:singleLine(reason,LIMITS.text)});
  while(task.history.length>LIMITS.history){task.history.shift();task.historyDropped++;}
  task.state=to;task.updatedAt=Math.max(task.updatedAt,op.at);
}
const requireReason=op=>{check(text(op.reason,LIMITS.text),'INVALID_FIELD','reason required (1..400 characters)');return op.reason;};
const requireToken=v=>{check(typeof v==='string'&&TOKEN_RE.test(v),'INVALID_FIELD','token must be 32 hex characters');return v;};
const requireLease=v=>{check(int(v,LIMITS.leaseMin,LIMITS.leaseMax),'INVALID_FIELD','leaseMs must be 60000..86400000');return v;};
const depsDone=(doc,t)=>t.dependsOn.every(d=>doc.tasks.find(x=>x.id===d)?.state==='DONE');
const eligible=(doc,t)=>t.state==='READY'&&depsDone(doc,t)&&t.attempts<t.maxAttempts;

function claimTask(doc,t,op){
  if(t.state!=='READY')fail('INVALID_TRANSITION',t.id+' is '+t.state+', not READY');
  if(!depsDone(doc,t))fail('DEPENDENCY_NOT_DONE',t.id+' depends on unfinished tasks');
  if(t.attempts>=t.maxAttempts)fail('BUDGET_EXHAUSTED',t.id+' used '+t.attempts+' of '+t.maxAttempts+' attempts');
  check(typeof op.owner==='string'&&ACTOR_RE.test(op.owner),'INVALID_FIELD','owner invalid');
  const token=requireToken(op.token),lease=requireLease(op.leaseMs);
  record(t,op,'IN_PROGRESS','claimed by '+op.owner);
  t.claim={owner:op.owner,token,at:op.at,leaseUntil:op.at+lease};
  t.attempts++;t.error=null;
}
function releaseAfterFailure(t,op,error,reason){
  const exhausted=t.attempts>=t.maxAttempts;
  record(t,op,exhausted?'FAILED':'READY',reason+(exhausted?' (attempt budget exhausted)':''));
  t.claim=null;t.submission=null;t.error=error;
}
function appendEvidence(t,e){
  t.evidence.push(e);
  while(t.evidence.length>LIMITS.evidence){t.evidence.shift();t.evidenceDropped++;}
}

const HANDLERS={
  propose(doc,op){
    const p=op.task;
    check(isObject(p),'INVALID_FIELD','task required');
    check(typeof p.id==='string'&&ID_RE.test(p.id),'INVALID_FIELD','task id must match '+ID_RE);
    if(doc.tasks.some(t=>t.id===p.id)){
      if(op.ifAbsent===true)return {changed:false,id:p.id,existed:true};
      fail('DUPLICATE_ID',p.id);
    }
    if(doc.tasks.length>=LIMITS.tasks)fail('LIMIT','backlog holds at most '+LIMITS.tasks+' tasks');
    check(text(p.title,LIMITS.title),'INVALID_FIELD','title must be 1..200 characters without control characters');
    check(typeof p.kind==='string'&&KIND_RE.test(p.kind),'INVALID_FIELD','kind invalid');
    check(typeof p.source==='string'&&SOURCE_RE.test(p.source),'INVALID_FIELD','source invalid');
    const priority=p.priority??500,maxAttempts=p.maxAttempts??3,rawDeps=p.dependsOn??[],subject=p.subject??null;
    check(int(priority,0,LIMITS.priority),'INVALID_FIELD','priority must be 0..1000');
    check(int(maxAttempts,1,LIMITS.maxAttempts),'INVALID_FIELD','maxAttempts must be 1..20');
    check(Array.isArray(rawDeps)&&rawDeps.every(d=>typeof d==='string'&&ID_RE.test(d)),'INVALID_FIELD','dependsOn must be task ids');
    const dependsOn=[...new Set(rawDeps)].sort();
    check(dependsOn.length<=LIMITS.dependsOn,'INVALID_FIELD','at most '+LIMITS.dependsOn+' dependencies');
    check(validSubject(subject),'INVALID_FIELD','subject must be a flat object of up to 12 strings, integers or booleans');
    for(const d of dependsOn)if(!doc.tasks.some(t=>t.id===d))fail('UNKNOWN_DEPENDENCY',d);
    const reason=op.reason==null?'':op.reason;
    check(text(reason,LIMITS.text,true),'INVALID_FIELD','reason invalid');
    const task={id:p.id,title:p.title,kind:p.kind,source:p.source,state:'PROPOSED',priority,dependsOn,attempts:0,maxAttempts,
      claim:null,submission:null,evidence:[],evidenceDropped:0,history:[],historyDropped:0,error:null,createdAt:op.at,updatedAt:op.at,subject};
    task.history.push({at:op.at,by:op.actor,op:'propose',from:null,to:'PROPOSED',reason});
    doc.tasks.push(task);doc.tasks.sort((a,b)=>a.id<b.id?-1:a.id>b.id?1:0);
    validateTask(task);
    return {changed:true,id:task.id};
  },
  accept(doc,op){
    const t=requireTask(doc,op.id);
    if(t.state!=='PROPOSED')fail('INVALID_TRANSITION',t.id+' is '+t.state+', not PROPOSED');
    record(t,op,'READY',op.reason==null?'accepted':requireReason(op));
    return {changed:true,id:t.id};
  },
  reject(doc,op){
    const t=requireTask(doc,op.id);
    if(!['PROPOSED','READY','BLOCKED','FAILED'].includes(t.state))fail('INVALID_TRANSITION',t.id+' is '+t.state);
    record(t,op,'REJECTED',requireReason(op));t.claim=null;t.submission=null;
    return {changed:true,id:t.id};
  },
  claim(doc,op){const t=requireTask(doc,op.id);claimTask(doc,t,op);return {changed:true,id:t.id};},
  claimNext(doc,op){
    const kinds=op.kinds??null;
    check(kinds===null||(Array.isArray(kinds)&&kinds.length<=16&&kinds.every(k=>typeof k==='string'&&KIND_RE.test(k))),'INVALID_FIELD','kinds invalid');
    const t=doc.tasks.filter(x=>eligible(doc,x)&&(!kinds||kinds.includes(x.kind)))
      .sort((a,b)=>b.priority-a.priority||a.createdAt-b.createdAt||(a.id<b.id?-1:1))[0];
    if(!t)fail('NO_ELIGIBLE_TASK','no READY task with finished dependencies and remaining budget');
    claimTask(doc,t,op);
    return {changed:true,id:t.id};
  },
  renew(doc,op){
    const t=requireTask(doc,op.id);
    if(t.state!=='IN_PROGRESS')fail('INVALID_TRANSITION',t.id+' is '+t.state);
    if(t.claim.token!==requireToken(op.token))fail('TOKEN_MISMATCH','claim belongs to another execution');
    if(op.at>t.claim.leaseUntil)fail('LEASE_EXPIRED','lease ended at '+t.claim.leaseUntil);
    t.claim.leaseUntil=op.at+requireLease(op.leaseMs);t.updatedAt=Math.max(t.updatedAt,op.at);
    return {changed:true,id:t.id};
  },
  submit(doc,op){
    const t=requireTask(doc,op.id);
    if(t.state!=='IN_PROGRESS')fail('INVALID_TRANSITION',t.id+' is '+t.state);
    if(t.claim.token!==requireToken(op.token))fail('TOKEN_MISMATCH','claim belongs to another execution');
    if(op.at>t.claim.leaseUntil)fail('LEASE_EXPIRED','lease ended at '+t.claim.leaseUntil);
    const a=op.artifact;
    check(isObject(a)&&ARTIFACT_TYPES.includes(a.type)&&typeof a.sha256==='string'&&SHA_RE.test(a.sha256),'INVALID_FIELD','artifact must be {type,sha256}');
    record(t,op,'VERIFYING','submitted '+a.type+' '+a.sha256.slice(0,12));
    t.submission={token:t.claim.token,by:op.actor,at:op.at,artifact:{type:a.type,sha256:a.sha256}};t.claim=null;
    return {changed:true,id:t.id};
  },
  complete(doc,op){
    const t=requireTask(doc,op.id);
    if(t.state!=='VERIFYING')fail('INVALID_TRANSITION',t.id+' is '+t.state+', not VERIFYING');
    const e=validateEvidence(op.evidence);
    const problems=evidenceProblems(t,e);
    if(problems.length)fail('EVIDENCE_REJECTED',problems.join('; '));
    appendEvidence(t,e);
    record(t,op,'DONE','verified by '+e.profiles.map(p=>p.profile+' '+p.executed+' tests').join(', '));
    t.error=null;
    return {changed:true,id:t.id};
  },
  fail(doc,op){
    const t=requireTask(doc,op.id);
    const token=requireToken(op.token);
    if(t.state==='IN_PROGRESS'){if(t.claim.token!==token)fail('TOKEN_MISMATCH','claim belongs to another execution');}
    else if(t.state==='VERIFYING'){if(t.submission.token!==token)fail('TOKEN_MISMATCH','submission belongs to another execution');}
    else fail('INVALID_TRANSITION',t.id+' is '+t.state);
    const er=op.error;
    check(isObject(er)&&typeof er.code==='string'&&CODE_RE.test(er.code)&&text(er.message,LIMITS.text,true),'INVALID_FIELD','error must be {code,message}');
    if(op.evidence!=null){
      const e=validateEvidence(op.evidence);
      check(e.taskId===t.id,'INVALID_FIELD','evidence belongs to another task');
      appendEvidence(t,e);
    }
    releaseAfterFailure(t,op,{code:er.code,message:er.message,at:op.at},er.code);
    return {changed:true,id:t.id};
  },
  expire(doc,op){
    const expired=[];
    for(const t of doc.tasks){
      if(t.state!=='IN_PROGRESS'||op.at<=t.claim.leaseUntil)continue;
      const owner=t.claim.owner;
      releaseAfterFailure(t,op,{code:'LEASE_EXPIRED',message:'lease of '+owner+' expired',at:op.at},'LEASE_EXPIRED');
      expired.push(t.id);
    }
    return {changed:expired.length>0,expired};
  },
  abort(doc,op){
    const t=requireTask(doc,op.id);
    if(TERMINAL.has(t.state))fail('INVALID_TRANSITION',t.id+' is '+t.state);
    record(t,op,'ABORTED',requireReason(op));t.claim=null;t.submission=null;
    return {changed:true,id:t.id};
  },
  retry(doc,op){
    const t=requireTask(doc,op.id);
    if(t.state!=='FAILED')fail('INVALID_TRANSITION',t.id+' is '+t.state+', not FAILED');
    record(t,op,'READY',requireReason(op));t.attempts=0;t.error=null;
    return {changed:true,id:t.id};
  },
  setPriority(doc,op){
    const t=requireTask(doc,op.id);
    if(TERMINAL.has(t.state))fail('INVALID_TRANSITION',t.id+' is '+t.state);
    check(int(op.priority,0,LIMITS.priority),'INVALID_FIELD','priority must be 0..1000');
    if(op.priority===t.priority)return {changed:false,id:t.id};
    record(t,op,t.state,'priority '+t.priority+' -> '+op.priority);t.priority=op.priority;
    return {changed:true,id:t.id};
  }
};

/** READY tasks with a failed dependency become BLOCKED, and back once it recovers. */
function reconcile(doc,op){
  const byId=new Map(doc.tasks.map(t=>[t.id,t]));
  for(const t of doc.tasks){
    const broken=t.dependsOn.find(d=>DEPENDENCY_FAILED.has(byId.get(d).state));
    if(t.state==='READY'&&broken)record(t,{...op,op:'reconcile'},'BLOCKED','dependency '+broken+' is '+byId.get(broken).state);
    else if(t.state==='BLOCKED'&&!broken)record(t,{...op,op:'reconcile'},'READY','dependencies recovered');
  }
}

/**
 * Apply one operation. Returns {doc, result}; `doc` is a new object (the input
 * is never mutated) or the same object when nothing changed. Throws
 * BacklogError with a stable code; then nothing changed. An operation with an
 * `opId` that was already applied is a reported no-op (exactly-once op logs).
 */
export function applyOp(doc,op){
  check(isObject(op)&&OPS.includes(op.op),'INVALID_OP','unknown operation '+String(op?.op).slice(0,40));
  check(typeof op.actor==='string'&&ACTOR_RE.test(op.actor),'INVALID_FIELD','actor invalid');
  check(int(op.at,0,MAX_SAFE),'INVALID_FIELD','at must be a non-negative integer (epoch ms)');
  if(op.opId!=null){
    check(typeof op.opId==='string'&&OP_ID_RE.test(op.opId),'INVALID_FIELD','opId must match '+OP_ID_RE);
    if((doc.recentOpIds??[]).includes(op.opId))return {doc,result:{changed:false,duplicate:true}};
  }
  const next=structuredClone(doc);
  const result=HANDLERS[op.op](next,op);
  if(!result.changed)return {doc,result};
  reconcile(next,op);
  if(op.opId!=null){
    next.recentOpIds=[...(doc.recentOpIds??[]),op.opId];
    while(next.recentOpIds.length>LIMITS.recentOps)next.recentOpIds.shift();
  }
  next.revision=doc.revision+1;next.updatedAt=op.at;next.updatedBy=op.actor;
  delete next.integrity;
  validateDocument(next);
  return {doc:next,result};
}

/** Apply several operations; every rejection is reported, never dropped silently. */
export function applyOps(doc,ops,{stopOnError=true}={}){
  let current=doc;const results=[];
  for(const [index,op] of ops.entries()){
    try{const r=applyOp(current,op);current=r.doc;results.push({index,ok:true,...r.result});}
    catch(e){
      if(!(e instanceof BacklogError))throw e;
      results.push({index,ok:false,code:e.code,message:e.detail});
      if(stopOnError)fail(e.code,'operation '+index+' ('+String(op?.op)+'): '+e.detail);
    }
  }
  return {doc:current,results};
}

/** Canonical READY-task selection; optional immutable HEAD and task-id scoping
 * avoid starvation by unrelated owner tasks pinned to older revisions.
 * No mutation: eligibility and deterministic priority ordering are unchanged. */
export function nextEligible(doc,kinds=null,sources=null,base=null,taskId=null,operation=null){
  return doc.tasks.filter(x=>eligible(doc,x)&&(!kinds||kinds.includes(x.kind))&&
    (!sources||sources.includes(x.source))&&
    (base===null||x.subject?.base===base)&&
    (taskId===null||x.id===taskId)&&
    (operation===null||x.subject?.operation===operation))
    .sort((a,b)=>b.priority-a.priority||a.createdAt-b.createdAt||(a.id<b.id?-1:1))[0]??null;
}

export const newToken=()=>crypto.randomBytes(16).toString('hex');

// ---------------------------------------------------------------------------
// File store: cross-process lock, atomic replace, backup, explicit recovery
// ---------------------------------------------------------------------------
const sleepMs=ms=>Atomics.wait(new Int32Array(new SharedArrayBuffer(4)),0,0,ms);

function fsyncDirectory(dir){
  let fd;try{fd=fs.openSync(dir,'r');fs.fsyncSync(fd);}catch{/* not supported on every platform */}finally{if(fd!==undefined)fs.closeSync(fd);}
}

function writeSynced(file,bytes){
  const fd=fs.openSync(file,'wx',0o600);
  try{fs.writeSync(fd,bytes,0,bytes.length,0);fs.fsyncSync(fd);}finally{fs.closeSync(fd);}
}

export class BacklogStore{
  constructor(file,{actor='cli',clock=Date.now,staleLockMs=60_000,waitMs=10_000}={}){
    check(typeof actor==='string'&&ACTOR_RE.test(actor),'INVALID_FIELD','actor invalid');
    this.file=path.resolve(file);this.actor=actor;this.clock=clock;this.staleLockMs=staleLockMs;this.waitMs=waitMs;
    this.lockFile=this.file+'.lock';this.backupFile=this.file+'.bak';
  }

  /** Read with recovery but without modifying files. */
  read(){return this.#load(false);}

  #decodeFile(file){
    const bytes=fs.readFileSync(file);
    return {...decodeDocument(bytes,{migrate:{at:this.clock(),actor:this.actor}}),bytes};
  }

  #load(repair){
    const exists=fs.existsSync(this.file);
    if(exists){
      try{const r=this.#decodeFile(this.file);return {doc:r.doc,bytes:r.bytes,migratedFrom:r.migratedFrom,recovered:null,exists:true};}
      catch(e){
        if(!(e instanceof BacklogError)||e.code==='UNSUPPORTED_VERSION')throw e;
        const backup=this.#tryBackup();
        if(!backup)fail(e.code,'backlog unreadable and no valid backup: '+e.detail);
        let quarantined=null;
        if(repair){quarantined=this.file+'.corrupt-'+this.clock()+'-'+crypto.randomBytes(3).toString('hex');fs.renameSync(this.file,quarantined);}
        return {doc:backup.doc,bytes:null,migratedFrom:backup.migratedFrom,exists:true,
          recovered:{from:'backup',reason:e.code+': '+e.detail,quarantined}};
      }
    }
    const backup=this.#tryBackup();
    if(backup)return {doc:backup.doc,bytes:null,migratedFrom:backup.migratedFrom,exists:false,recovered:{from:'backup',reason:'backlog file missing',quarantined:null}};
    return {doc:emptyDocument(this.clock(),this.actor),bytes:null,migratedFrom:null,recovered:null,exists:false};
  }

  #tryBackup(){
    if(!fs.existsSync(this.backupFile))return null;
    try{return this.#decodeFile(this.backupFile);}catch(e){if(e instanceof BacklogError)return null;throw e;}
  }

  #acquire(){
    const token=newToken();
    const body=Buffer.from(canonical({owner:this.actor,pid:process.pid,host:os.hostname().replace(/[^\w.-]/g,'').slice(0,64)||'host',token,at:this.clock()})+'\n','utf8');
    const deadline=Date.now()+this.waitMs;
    for(let round=0;;round++){
      try{writeSynced(this.lockFile,body);return token;}catch(e){if(e.code!=='EEXIST')throw e;}
      let st;try{st=fs.statSync(this.lockFile);}catch(e){if(e.code==='ENOENT')continue;throw e;}
      if(Date.now()-st.mtimeMs>this.staleLockMs){
        // Age alone is NOT proof of a dead process. In particular, a Gradle
        // task can outlive the default lease. Never steal its live local lock.
        // A foreign host is unverifiable, so fail closed rather than inventing
        // cross-machine liveness from an unrelated local PID.
        let owner=null;
        try{owner=JSON.parse(fs.readFileSync(this.lockFile,'utf8'));}catch{/* legacy/corrupt lock */}
        const host=os.hostname().replace(/[^\w.-]/g,'').slice(0,64)||'host';
        let live=false,foreign=false;
        if(owner&&typeof owner==='object'&&typeof owner.host==='string'){
          foreign=owner.host!==host;
          if(!foreign&&Number.isSafeInteger(owner.pid)&&owner.pid>0){
            try{process.kill(owner.pid,0);live=true;}
            catch(e){if(e.code==='EPERM')live=true;else if(e.code!=='ESRCH')live=true;}
          }
        }
        if(!live&&!foreign){
          // Check that the inode which looked stale is the inode we moved.
          // An intervening writer might have released/recreated the lock.
          const aside=this.lockFile+'.stale-'+token;
          try{fs.renameSync(this.lockFile,aside);}catch(e){if(e.code==='ENOENT')continue;throw e;}
          const moved=fs.statSync(aside);
          if(moved.dev!==st.dev||moved.ino!==st.ino||
              Date.now()-moved.mtimeMs<=this.staleLockMs){
            try{fs.linkSync(aside,this.lockFile);}catch{/* successor holds lock */}
            fs.rmSync(aside,{force:true});
            // Do not blindly retry: ambiguous ownership must fail closed.
            fail('LOCK_TIMEOUT','backlog lock ownership changed while reclaiming');
          }
          fs.rmSync(aside,{force:true});
          continue;
        }
      }
      if(Date.now()>deadline)fail('LOCK_TIMEOUT','backlog is locked by another process: '+this.lockFile);
      sleepMs(Math.min(200,20+round*10));
    }
  }

  #holds(token){
    try{return fs.readFileSync(this.lockFile,'utf8').includes('"token":"'+token+'"');}catch(e){if(e.code==='ENOENT')return false;throw e;}
  }

  #release(token){if(this.#holds(token))fs.rmSync(this.lockFile,{force:true});}

  #cleanupInterrupted(){
    const dir=path.dirname(this.file),base=path.basename(this.file);
    for(const name of fs.readdirSync(dir)){
      if(!name.startsWith(base+'.tmp-')&&!name.startsWith(base+'.bak.tmp-'))continue;
      const full=path.join(dir,name);
      try{if(Date.now()-fs.statSync(full).mtimeMs>this.staleLockMs)fs.rmSync(full,{force:true});}catch{/* vanished */}
    }
  }

  /**
   * Read-modify-write under the cross-process lock. `mutate(doc)` returns the
   * next document (e.g. via applyOps). Fails with CONFLICT when
   * expectedRevision is given and differs, or when the file changed although
   * we held the lock (lock broken as stale). Nothing is written then.
   */
  update(mutate,{expectedRevision=null}={}){
    fs.mkdirSync(path.dirname(this.file),{recursive:true});
    const token=this.#acquire();
    try{
      this.#cleanupInterrupted();
      const current=this.#load(true);
      if(expectedRevision!==null&&current.doc.revision!==expectedRevision)
        fail('CONFLICT','expected revision '+expectedRevision+', found '+current.doc.revision);
      const next=mutate(current.doc);
      const bytes=encodeDocument(next);
      // a migrated, recovered or new backlog is always written; otherwise only real changes
      const fresh=current.migratedFrom!==null||current.recovered!==null||!current.exists;
      const same=current.bytes!==null&&bytes.equals(current.bytes);
      if(same&&!fresh)return {doc:current.doc,written:false,recovered:null,migratedFrom:null};
      if(!fresh&&next.revision<=current.doc.revision)fail('CONFLICT','a changed document must have a higher revision');
      const tmp=this.file+'.tmp-'+process.pid+'-'+crypto.randomBytes(4).toString('hex');
      writeSynced(tmp,bytes);
      try{
        if(!this.#holds(token))fail('LOCK_LOST','the lock was taken over; nothing written');
        // compare-and-swap: the file must still be what we read
        const now=fs.existsSync(this.file)?fs.readFileSync(this.file):null;
        if(current.bytes&&(!now||!now.equals(current.bytes)))fail('CONFLICT','backlog changed concurrently; nothing written');
        if(!current.bytes&&now&&current.recovered===null&&current.exists===false)fail('CONFLICT','backlog was created concurrently; nothing written');
        if(current.migratedFrom===1&&now&&!fs.existsSync(this.file+'.v1.json'))fs.copyFileSync(this.file,this.file+'.v1.json');
        if(now&&current.recovered===null){
          const bakTmp=this.file+'.bak.tmp-'+process.pid+'-'+crypto.randomBytes(4).toString('hex');
          writeSynced(bakTmp,now);fs.renameSync(bakTmp,this.backupFile);
        }
        fs.renameSync(tmp,this.file);
        fsyncDirectory(path.dirname(this.file));
      }finally{fs.rmSync(tmp,{force:true});}
      const written=decodeDocument(fs.readFileSync(this.file)).doc;
      return {doc:written,written:true,recovered:current.recovered,migratedFrom:current.migratedFrom};
    }finally{this.#release(token);}
  }

  /** Apply operations atomically (stopOnError) or leniently with per-op results. */
  apply(ops,{expectedRevision=null,stopOnError=true}={}){
    let results=[];
    const r=this.update(doc=>{const out=applyOps(doc,ops,{stopOnError});results=out.results;return out.doc;},{expectedRevision});
    return {...r,results};
  }
}
