// CCC1 EnvelopeV1: the one message format between owner, reasoner, the existing coding Teamleader and the
// verifier (docs/CONTINUUM-COGNITIVE-CONTINUITY-CCC1-20261022.md, section 2). Every field is checked strictly:
// unknown fields, versions, sender/kind pairs and payload shapes are rejected. Payloads are structured data,
// never shell text. Deviation from the specification, documented: the owner answers an APPROVAL_REQUEST with
// the additional kind APPROVAL (the specification names the request but not the answer).
import {createHash,randomUUID} from 'node:crypto';
import {MODULE_RE,EXPORT_RE,MAX_CASES_JSON} from '../../scripts/continuum-mobile-coding.mjs';

export const SCHEMA_VERSION=1;
export const SENDERS=Object.freeze(['OWNER','REASONER','TEAMLEADER','VERIFIER']);
export const KINDS=Object.freeze(['GOAL','HYPOTHESIS','PLAN','TEST_REQUEST','RECEIPT','CRITIQUE','REVISE','ABSTAIN',
 'APPROVAL_REQUEST','APPROVAL','ABORT']);
/** Who may send which kind. The reasoner can only propose; receipts come from the verifier alone. */
export const SENDER_KINDS=Object.freeze({
 OWNER:['GOAL','APPROVAL','ABORT'],
 REASONER:['HYPOTHESIS','PLAN','TEST_REQUEST','CRITIQUE','REVISE','ABSTAIN'],
 TEAMLEADER:['APPROVAL_REQUEST','ABORT'],
 VERIFIER:['RECEIPT']
});
export const VERDICTS=Object.freeze(['CONFIRMED','REFUTED','INCONCLUSIVE']);
/** ABORT reasons; CAPABILITY_DENIED and PROTOCOL_VIOLATION end a task as FAILED, all others as ABORTED. */
export const ABORT_CODES=Object.freeze(['OWNER','DEADLINE','BUDGET','ROUND_LIMIT','STALE_BASE','APPROVAL_DENIED','CAPABILITY_DENIED','PROTOCOL_VIOLATION']);
/** The only capability of the existing coding agent this protocol can request (owner contract test). */
export const CAPABILITY='node-contract-test';
const ENVELOPE_FIELDS=['schemaVersion','sessionId','taskId','messageId','sequence','parentMessageId','sender','kind','payload',
 'repoFullName','baseCommitSha','worktreeId','deadline','maxRounds','maxCpuMillis','maxMemoryBytes','permissions',
 'checkpointHash','createdAt','protocolHash','verificationReceiptId'];
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const TASK_ID=/^t-[0-9a-f]{16}$/;
const SHA=/^[0-9a-f]{40}$/;
const HEX64=/^[0-9a-f]{64}$/;
const REPO=/^[A-Za-z0-9_.-]{1,100}\/[A-Za-z0-9_.-]{1,100}$/;
const PERMISSION=/^node-contract-test(:\.github\/continuum\/[A-Za-z][A-Za-z0-9_-]{0,63}\.mjs)?$/;

const str=(v,max=600)=>typeof v==='string'&&v.length>0&&v.length<=max;
const keysExactly=(o,allowed)=>o!==null&&typeof o==='object'&&!Array.isArray(o)&&Object.keys(o).every(k=>allowed.includes(k));
const ref=v=>typeof v==='string'&&UUID.test(v);
/** Value grammar of the contract operator (contract-test-template.mjs): primitives only. */
const primitive=v=>v===null||typeof v==='boolean'||typeof v==='number'&&Number.isFinite(v)||typeof v==='string'&&v.length<=120;
export const validCase=c=>keysExactly(c,['args','expected'])&&Array.isArray(c.args)&&c.args.length<=3&&
 c.args.every(primitive)&&Object.hasOwn(c,'expected')&&primitive(c.expected);

/** Canonical JSON (sorted keys): hashes and MACs must not depend on property order. */
export function canonical(v){
 if(Array.isArray(v))return '['+v.map(canonical).join(',')+']';
 if(v&&typeof v==='object')return '{'+Object.keys(v).sort().map(k=>JSON.stringify(k)+':'+canonical(v[k])).join(',')+'}';
 return JSON.stringify(v);
}
export const sha256=s=>createHash('sha256').update(s).digest('hex');
/** Hash of the protocol definition; peers with a different contract are detected, not trusted. */
export const PROTOCOL_HASH=sha256(canonical({SCHEMA_VERSION,SENDERS,KINDS,SENDER_KINDS,VERDICTS,ABORT_CODES,CAPABILITY,ENVELOPE_FIELDS}));

/** Strict payload schema per kind; returns problems (empty = valid). */
export function payloadProblems(kind,p){
 const out=[];const need=(ok,msg)=>{if(!ok)out.push(kind+': '+msg);};
 switch(kind){
 case 'GOAL':
  need(keysExactly(p,['text','examples']),'only text, examples');
  need(str(p?.text),'text 1..600 characters');
  need(p?.examples===undefined||Array.isArray(p.examples)&&p.examples.length<=8&&p.examples.every(validCase),'examples: at most 8 primitive cases');
  break;
 case 'HYPOTHESIS':
  need(keysExactly(p,['claim','module','export','basis','confidence']),'only claim, module, export, basis, confidence');
  need(str(p?.claim)&&str(p?.basis),'claim and basis 1..600 characters');
  need(MODULE_RE.test(p?.module??'')&&EXPORT_RE.test(p?.export??''),'module .github/continuum/<name>.mjs and an export identifier');
  need(p?.confidence===undefined||typeof p.confidence==='number'&&p.confidence>=0&&p.confidence<=1,'confidence 0..1');
  break;
 case 'PLAN':
  need(keysExactly(p,['steps','capability']),'only steps, capability');
  need(Array.isArray(p?.steps)&&p.steps.length>=1&&p.steps.length<=8&&p.steps.every(s=>str(s,200)),'1..8 steps');
  need(p?.capability===CAPABILITY,'capability must be '+CAPABILITY);
  break;
 case 'TEST_REQUEST':{
  need(keysExactly(p,['hypothesis','module','export','cases','budgetMinutes']),'only hypothesis, module, export, cases, budgetMinutes');
  need(ref(p?.hypothesis),'hypothesis must be a message id');
  need(MODULE_RE.test(p?.module??'')&&EXPORT_RE.test(p?.export??''),'module and export');
  const json=Array.isArray(p?.cases)?JSON.stringify(p.cases):'';
  need(Array.isArray(p?.cases)&&p.cases.length>=1&&p.cases.length<=4&&p.cases.every(validCase),'1..4 primitive cases');
  need(json.length>=2&&json.length<=MAX_CASES_JSON,'cases JSON 2..'+MAX_CASES_JSON+' characters');
  need(Number.isSafeInteger(p?.budgetMinutes)&&p.budgetMinutes>=1&&p.budgetMinutes<=30,'budgetMinutes 1..30');
  break;}
 case 'RECEIPT':
  need(keysExactly(p,['request','job','status','verdict','reasons','receiptSha256','patchSha256','evidenceSha256','runs','replayed','execution','cost']),'unexpected RECEIPT fields');
  need(ref(p?.request),'request must be a message id');
  need(VERDICTS.includes(p?.verdict),'verdict');
  need(['EXECUTED','REPLAYED','INTERRUPTED','NOT_EXECUTED'].includes(p?.execution),'execution');
  break;
 case 'CRITIQUE':
  need(keysExactly(p,['receipt','verdict','reason']),'only receipt, verdict, reason');
  need(ref(p?.receipt)&&VERDICTS.includes(p?.verdict)&&str(p?.reason),'receipt id, verdict, reason');
  break;
 case 'REVISE':
  need(keysExactly(p,['hypothesis','change'])&&ref(p?.hypothesis)&&str(p?.change),'hypothesis id and change');
  break;
 case 'ABSTAIN':
  need(keysExactly(p,['reason'])&&str(p?.reason),'reason');
  break;
 case 'APPROVAL_REQUEST':
  need(keysExactly(p,['request','reason','needs'])&&ref(p?.request)&&str(p?.reason)&&Array.isArray(p?.needs)&&p.needs.length>=1&&
   p.needs.length<=8&&p.needs.every(n=>str(n,160)),'request id, reason, 1..8 needs');
  break;
 case 'APPROVAL':
  need(keysExactly(p,['approvalRequest','decision'])&&ref(p?.approvalRequest)&&['GRANTED','DENIED'].includes(p?.decision),'approvalRequest id and GRANTED/DENIED');
  break;
 case 'ABORT':
  need(keysExactly(p,['reason','code'])&&str(p?.reason)&&ABORT_CODES.includes(p?.code),'reason and code '+ABORT_CODES.join('|'));
  break;
 default:out.push('unknown kind '+kind);
 }
 return out;
}

/** Full envelope check (fields, version, ids, sender/kind, payload); journal-level rules live in the journal. */
export function envelopeProblems(e){
 const out=[];const need=(ok,msg)=>{if(!ok)out.push(msg);};
 if(!keysExactly(e,ENVELOPE_FIELDS))return ['unknown envelope fields: '+Object.keys(e??{}).filter(k=>!ENVELOPE_FIELDS.includes(k)).join(',')];
 need(e.schemaVersion===SCHEMA_VERSION,'unsupported schemaVersion '+e.schemaVersion);
 need(e.protocolHash===PROTOCOL_HASH,'foreign protocol hash');
 need(ref(e.sessionId)&&TASK_ID.test(e.taskId??'')&&ref(e.messageId),'session, task and message ids');
 need(Number.isSafeInteger(e.sequence)&&e.sequence>=0,'sequence');
 need(e.parentMessageId===null||ref(e.parentMessageId),'parentMessageId');
 need(SENDERS.includes(e.sender)&&KINDS.includes(e.kind),'sender and kind');
 need(SENDER_KINDS[e.sender]?.includes(e.kind),e.sender+' may not send '+e.kind);
 need(REPO.test(e.repoFullName??''),'repoFullName owner/name');
 need(SHA.test(e.baseCommitSha??''),'baseCommitSha must be a full commit id');
 need(typeof e.worktreeId==='string'&&/^[A-Za-z0-9_-]{1,64}$/.test(e.worktreeId),'worktreeId');
 need(Number.isSafeInteger(e.deadline)&&e.deadline>0,'deadline (epoch milliseconds)');
 need(Number.isSafeInteger(e.maxRounds)&&e.maxRounds>=1&&e.maxRounds<=8,'maxRounds 1..8');
 need(Number.isSafeInteger(e.maxCpuMillis)&&e.maxCpuMillis>0,'maxCpuMillis');
 need(Number.isSafeInteger(e.maxMemoryBytes)&&e.maxMemoryBytes>0,'maxMemoryBytes');
 need(Array.isArray(e.permissions)&&e.permissions.length<=16&&e.permissions.every(p=>PERMISSION.test(p)),'permissions');
 need(e.checkpointHash===null||HEX64.test(e.checkpointHash),'checkpointHash');
 need(typeof e.createdAt==='string'&&!Number.isNaN(Date.parse(e.createdAt)),'createdAt');
 need(e.verificationReceiptId===null||(e.kind==='RECEIPT'&&HEX64.test(e.verificationReceiptId)),'verificationReceiptId only on RECEIPT');
 need(e.kind!=='RECEIPT'||HEX64.test(e.verificationReceiptId??''),'RECEIPT needs a verificationReceiptId');
 out.push(...payloadProblems(e.kind,e.payload));
 return out;
}

/** Builds an envelope from the task binding of its GOAL; ids are generated, nothing is trusted from the payload. */
export function envelope({binding,sender,kind,payload,sequence,parentMessageId=null,verificationReceiptId=null,now=new Date()}){
 return {schemaVersion:SCHEMA_VERSION,sessionId:binding.sessionId,taskId:binding.taskId,messageId:randomUUID(),sequence,
  parentMessageId,sender,kind,payload,repoFullName:binding.repoFullName,baseCommitSha:binding.baseCommitSha,worktreeId:binding.worktreeId,
  deadline:binding.deadline,maxRounds:binding.maxRounds,maxCpuMillis:binding.maxCpuMillis,maxMemoryBytes:binding.maxMemoryBytes,
  permissions:[...binding.permissions],checkpointHash:binding.checkpointHash??null,createdAt:now.toISOString(),protocolHash:PROTOCOL_HASH,
  verificationReceiptId};
}

export const newTaskId=()=>'t-'+sha256(randomUUID()).slice(0,16);
