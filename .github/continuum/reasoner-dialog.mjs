// Typed Reasoner <-> Coding-Agent dialog (LM1 milestone, PR #69 section 6.5).
// The reasoner only proposes: HYPOTHESIS, PLAN, TEST_REQUEST, CRITIQUE, REVISE, ABSTAIN.
// Only the owner starts a dialog (GOAL) and only this runner writes RECEIPTs: it executes a
// TEST_REQUEST through the EXISTING Android/Termux coding job (canonical backlog -> Teamleader ->
// isolated worktrees -> independent second run), re-verifies the job receipt against the real
// patch bytes and run evidence, and MACs the entry. Every entry is hash-chained in an append-only
// journal, so a dialog can be replayed and resumed after a crash; a resumed TEST_REQUEST reuses its
// job id and the coding job's replay protection guarantees nothing runs twice.
// Nothing here merges, pushes, installs or promotes anything: a confirmed hypothesis ends as a
// reviewable contract-test patch pending the owner's decision.
import fs from 'node:fs';
import path from 'node:path';
import {createHash,createHmac,randomBytes,randomUUID,timingSafeEqual} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {runMobileCodingJob,cancelMobileCodingJob,RECEIPT_SCHEMA,MODULE_RE,EXPORT_RE,MAX_CASES_JSON} from '../../scripts/continuum-mobile-coding.mjs';

export const DIALOG_SCHEMA='continuum-reasoner-dialog';
export const DIALOG_VERSION=1;
export const CAPABILITY='node-contract-test';
export const BOUNDARY='isolated git worktrees; source checkout unchanged; no merge, push, install or promotion';
export const TYPES=Object.freeze(['GOAL','HYPOTHESIS','PLAN','TEST_REQUEST','RECEIPT','CRITIQUE','REVISE','ABSTAIN']);
export const ROLE=Object.freeze({GOAL:'owner',RECEIPT:'worker',HYPOTHESIS:'reasoner',PLAN:'reasoner',
 TEST_REQUEST:'reasoner',CRITIQUE:'reasoner',REVISE:'reasoner',ABSTAIN:'reasoner'});
export const VERDICTS=Object.freeze(['CONFIRMED','REFUTED','INCONCLUSIVE']);
const MAX_TEXT=600,MAX_STEPS=8;

const str=(v,max=MAX_TEXT)=>typeof v==='string'&&v.length>0&&v.length<=max;
// Same value grammar as the contract operator (contract-test-template.mjs): 1..4 cases, at most 3 arguments,
// every argument and expected value a primitive (string ≤ 120 characters, finite number, boolean, null).
const primitive=v=>v===null||typeof v==='boolean'||typeof v==='number'&&Number.isFinite(v)||typeof v==='string'&&v.length<=120;
export const validCase=c=>keysExactly(c,['args','expected'])&&Array.isArray(c.args)&&c.args.length<=3&&
 c.args.every(primitive)&&Object.hasOwn(c,'expected')&&primitive(c.expected);
const keysExactly=(o,allowed)=>o&&typeof o==='object'&&!Array.isArray(o)&&
 Object.keys(o).every(k=>allowed.includes(k));

/** Strict body schema of each message type; returns a list of problems (empty = valid). */
export function validateBody(type,body){
 const p=[];
 const need=(ok,msg)=>{if(!ok)p.push(type+': '+msg);};
 switch(type){
 case 'GOAL':
  need(keysExactly(body,['text','examples']),'only text, examples');
  need(str(body?.text),'text 1..600 characters');
  need(body?.examples===undefined||Array.isArray(body.examples)&&body.examples.length<=8&&
   body.examples.every(c=>keysExactly(c,['args','expected'])&&Array.isArray(c.args)&&'expected' in c),'examples must be at most 8 {args:[...],expected}');
  break;
 case 'HYPOTHESIS':
  need(keysExactly(body,['claim','module','export','basis']),'only claim, module, export, basis');
  need(str(body?.claim),'claim 1..600 characters');
  need(MODULE_RE.test(body?.module??''),'module must be .github/continuum/<name>.mjs');
  need(EXPORT_RE.test(body?.export??''),'export must be an identifier');
  need(str(body?.basis),'basis 1..600 characters');
  break;
 case 'PLAN':
  need(keysExactly(body,['steps','capability']),'only steps, capability');
  need(Array.isArray(body?.steps)&&body.steps.length>=1&&body.steps.length<=MAX_STEPS&&body.steps.every(s=>str(s,200)),'1..8 steps of 1..200 characters');
  need(body?.capability===CAPABILITY,'capability must be '+CAPABILITY);
  break;
 case 'TEST_REQUEST':{
  need(keysExactly(body,['hypothesis','module','export','cases','budgetMinutes']),'only hypothesis, module, export, cases, budgetMinutes');
  need(Number.isSafeInteger(body?.hypothesis)&&body.hypothesis>=0,'hypothesis must reference a HYPOTHESIS entry');
  need(MODULE_RE.test(body?.module??''),'module must be .github/continuum/<name>.mjs');
  need(EXPORT_RE.test(body?.export??''),'export must be an identifier');
  const json=Array.isArray(body?.cases)?JSON.stringify(body.cases):'';
  need(Array.isArray(body?.cases)&&body.cases.length>=1&&body.cases.length<=4&&body.cases.every(validCase),
   '1..4 cases {args:[≤3 primitives],expected:primitive}');
  need(json.length>=2&&json.length<=MAX_CASES_JSON,'cases JSON must be 2..'+MAX_CASES_JSON+' characters');
  need(Number.isSafeInteger(body?.budgetMinutes)&&body.budgetMinutes>=1&&body.budgetMinutes<=30,'budgetMinutes 1..30');
  break;}
 case 'RECEIPT':
  need(keysExactly(body,['request','job','status','verdict','reasons','receiptSha256','patchSha256','evidenceSha256','runs','replayed']),'unexpected RECEIPT fields');
  need(VERDICTS.includes(body?.verdict),'verdict must be one of '+VERDICTS.join(', '));
  break;
 case 'CRITIQUE':
  need(keysExactly(body,['receipt','verdict','reason']),'only receipt, verdict, reason');
  need(Number.isSafeInteger(body?.receipt)&&body.receipt>=0,'receipt must reference a RECEIPT entry');
  need(VERDICTS.includes(body?.verdict),'verdict must be one of '+VERDICTS.join(', '));
  need(str(body?.reason),'reason 1..600 characters');
  break;
 case 'REVISE':
  need(keysExactly(body,['hypothesis','change']),'only hypothesis, change');
  need(Number.isSafeInteger(body?.hypothesis)&&body.hypothesis>=0,'hypothesis must reference a HYPOTHESIS entry');
  need(str(body?.change),'change 1..600 characters');
  break;
 case 'ABSTAIN':
  need(keysExactly(body,['reason']),'only reason');
  need(str(body?.reason),'reason 1..600 characters');
  break;
 default:p.push('unknown message type '+type);
 }
 return p;
}

/** Canonical JSON (sorted keys) so that hashes do not depend on property order. */
export function canonical(v){
 if(Array.isArray(v))return '['+v.map(canonical).join(',')+']';
 if(v&&typeof v==='object')return '{'+Object.keys(v).sort().map(k=>JSON.stringify(k)+':'+canonical(v[k])).join(',')+'}';
 return JSON.stringify(v);
}
const sha256=s=>createHash('sha256').update(s).digest('hex');

/** Append-only, hash-chained JSON-lines journal; worker entries carry an HMAC of their hash. */
export class DialogJournal{
 constructor(file,key){
  if(!Buffer.isBuffer(key)||key.length<32)throw Error('journal key must be at least 32 bytes');
  this.file=file;this.key=key;
 }
 entries(){
  if(!fs.existsSync(this.file))return [];
  const lines=fs.readFileSync(this.file,'utf8').split('\n').filter(Boolean);
  let prev='0'.repeat(64);
  return lines.map((line,seq)=>{
   const e=JSON.parse(line);
   const {hash,mac,...rest}=e;
   if(rest.seq!==seq||rest.prev!==prev)throw Error('journal chain broken at entry '+seq);
   if(sha256(canonical(rest))!==hash)throw Error('journal entry '+seq+' was modified');
   if(ROLE[rest.type]!==rest.role)throw Error('journal entry '+seq+' has the wrong role');
   if(rest.role==='worker'){
    const want=createHmac('sha256',this.key).update(hash).digest();
    if(typeof mac!=='string'||!/^[0-9a-f]{64}$/.test(mac)||!timingSafeEqual(Buffer.from(mac,'hex'),want))
     throw Error('journal entry '+seq+' is not a genuine worker receipt');
   }
   prev=hash;return e;
  });
 }
 append(role,type,body,binding=null){
  const list=this.entries();
  const rest={seq:list.length,prev:list.length?list.at(-1).hash:'0'.repeat(64),role,type,body,binding,at:new Date().toISOString()};
  const hash=sha256(canonical(rest));
  const entry={...rest,hash};
  if(role==='worker')entry.mac=createHmac('sha256',this.key).update(hash).digest('hex');
  fs.mkdirSync(path.dirname(this.file),{recursive:true,mode:0o700});
  fs.appendFileSync(this.file,JSON.stringify(entry)+'\n',{mode:0o600});
  return entry;
 }
}

/** Key stays in the runner's state directory; reasoners only see the message channel. */
export function dialogKey(stateDir){
 const file=path.join(stateDir,'dialog-key');
 fs.mkdirSync(stateDir,{recursive:true,mode:0o700});
 if(!fs.existsSync(file))fs.writeFileSync(file,randomBytes(32),{flag:'wx',mode:0o600});
 return fs.readFileSync(file);
}

/**
 * Independent check of a coding-job receipt against the order it must answer and the real artifacts:
 * CONFIRMED needs the verified status, matching job/commit/order, patch bytes with the stated SHA-256 and
 * three passing runs (baseline, candidate, independent) with executed tests. A failing contract test
 * (GENERATED_TESTS_FAILED) refutes the hypothesis; any other outcome is INCONCLUSIVE.
 */
export function verifyReceipt(receipt,{job,commit,module,exportName,casesJson}){
 const reasons=[];
 const r=receipt??{};
 if(r.schema!==RECEIPT_SCHEMA||r.version!==1)reasons.push('not a coding receipt');
 if(r.job!==job)reasons.push('receipt belongs to another job');
 if(r.commit!==commit)reasons.push('receipt is bound to another commit');
 if(r.approvedForMerge!==false||r.installed!==false||r.apk!==null||r.deviceVerified!==false)reasons.push('receipt claims merge, install or device verification');
 const orderOk=r.order&&r.order.module===module&&r.order.export===exportName&&r.order.casesSha256===sha256(casesJson);
 if(!orderOk)reasons.push('receipt answers a different order');
 if(reasons.length)return {verdict:'INCONCLUSIVE',reasons};
 if(r.status==='CODING_PATCH_VERIFIED_PENDING_REVIEW'){
  try{
   const bytes=fs.readFileSync(r.patch.path);
   if(sha256(bytes)!==r.patch.sha256||bytes.length!==r.patch.bytes)reasons.push('patch bytes do not match the receipt');
  }catch{reasons.push('patch file missing');}
  const runs=r.commands??[];
  for(const phase of ['baseline','candidate','independent']){
   const run=runs.find(x=>x.phase===phase);
   if(!run||run.exitCode!==0||run.passed!==true||!(run.executed>0)||run.failed!==0)reasons.push(phase+' run did not pass with executed tests');
  }
  const ex=r.evidence??{};
  if(!/^[0-9a-f]{64}$/.test(ex.verificationSha256??''))reasons.push('no verification evidence hash');
  if(!(ex.independentExecuted>=ex.candidateExecuted&&ex.candidateExecuted>=ex.baselineExecuted+ex.expectedNewTests))reasons.push('independent run executed fewer tests than required');
  return reasons.length?{verdict:'INCONCLUSIVE',reasons}:{verdict:'CONFIRMED',reasons:['contract test passed in candidate and independent worktree']};
 }
 if(r.status==='FAILED_CODING_CHECKS'&&/GENERATED_TESTS_FAILED/.test(r.failure?.message??''))
  return {verdict:'REFUTED',reasons:['the contract test generated from the cases failed']};
 return {verdict:'INCONCLUSIVE',reasons:['job ended '+r.status+(r.failure?.message?': '+String(r.failure.message).slice(0,200):'')]};
}

/** State of a dialog derived from its journal (used for decisions and for resuming). */
export function dialogState(entries){
 const of=t=>entries.filter(e=>e.type===t);
 const requests=of('TEST_REQUEST'),receipts=of('RECEIPT');
 const last=entries.at(-1);
 const pending=requests.find(q=>!receipts.some(r=>r.body.request===q.seq))??null;
 const critiques=of('CRITIQUE');
 const confirmed=critiques.find(c=>c.body.verdict==='CONFIRMED')??null;
 const abstained=of('ABSTAIN')[0]??null;
 return {entries,last,goal:of('GOAL')[0]??null,hypotheses:of('HYPOTHESIS'),requests,receipts,critiques,pending,
  rounds:requests.length,
  outcome:confirmed?'CONFIRMED_PENDING_OWNER_REVIEW':abstained?'ABSTAINED':null,
  uncritiqued:receipts.find(r=>!critiques.some(c=>c.body.receipt===r.seq))??null};
}

function headOf(project){
 const r=spawnSync('git',['rev-parse','HEAD'],{cwd:project,encoding:'utf8',timeout:15000,shell:false});
 if(r.status!==0)throw Error('project is not a git checkout');
 return r.stdout.trim();
}

/**
 * Runs (or resumes) one dialog until the reasoner confirms a hypothesis, abstains, breaks the protocol, or
 * the round limit is reached. `reasoner.turn(view)` returns the reasoner's next messages [{type, body}].
 * Every reasoner message is validated before it is journaled; a violation ends the dialog fail-closed.
 */
export async function runDialog({project,stateDir,goal,reasoner,maxRounds=3,budgetMinutes=3,runJob=runMobileCodingJob,
 cancelJob=cancelMobileCodingJob,maxTurns=12}){
 if(!Number.isSafeInteger(maxRounds)||maxRounds<1||maxRounds>8)throw Error('maxRounds 1..8');
 const root=fs.realpathSync(path.resolve(project));
 const journal=new DialogJournal(path.join(stateDir,'dialog.jsonl'),dialogKey(stateDir));
 const commit=headOf(root);
 const binding={commit,capability:CAPABILITY,boundary:BOUNDARY,maxRounds};
 if(!journal.entries().length){
  const problems=validateBody('GOAL',goal);
  if(problems.length)throw Error(problems.join('; '));
  journal.append('owner','GOAL',goal,binding);
 }
 const stop=(outcome,detail=null)=>({outcome,detail,state:dialogState(journal.entries())});
 for(let turn=0;turn<maxTurns;turn++){
  let s=dialogState(journal.entries());
  if(s.goal.binding.commit!==commit)return stop('BLOCKED_STALE_BASE','dialog bound to '+s.goal.binding.commit.slice(0,12));
  if(s.outcome)return stop(s.outcome);
  if(s.pending){
   // A decided request is final for the dialog: close its backlog task so that the Teamleader does not
   // retry a refuted hypothesis ahead of the next one (idempotent, so it also holds after a crash).
   for(const r of s.receipts)if(r.body.verdict!=='CONFIRMED')
    cancelJob({project:root,teamleaderState:path.join(stateDir,'teamleader'),job:r.body.job,reason:'Dialog: Hypothese entschieden ('+r.body.verdict+')'});
   // Execute (or resume) exactly the pending request; its job id was journaled before execution.
   const q=s.pending,order={module:q.body.module,exportName:q.body.export,casesJson:JSON.stringify(q.body.cases)};
   const receipt=await runJob({project:root,builderState:path.join(stateDir,'builder'),teamleaderState:path.join(stateDir,'teamleader'),
    job:q.binding.job,...order,budgetMinutes:q.body.budgetMinutes});
   const check=verifyReceipt(receipt,{job:q.binding.job,commit,...order});
   journal.append('worker','RECEIPT',{request:q.seq,job:q.binding.job,status:receipt.status,verdict:check.verdict,reasons:check.reasons,
    receiptSha256:sha256(JSON.stringify(receipt)),patchSha256:receipt.patch?.sha256??null,
    evidenceSha256:receipt.evidence?.verificationSha256??null,
    runs:(receipt.commands??[]).map(c=>({phase:c.phase,exitCode:c.exitCode,executed:c.executed,failed:c.failed})),
    replayed:receipt.replayed===true},{...binding,job:q.binding.job});
   continue;
  }
  const view=JSON.parse(JSON.stringify({goal:s.goal.body,entries:s.entries.map(({seq,role,type,body})=>({seq,role,type,body})),
   rounds:s.rounds,maxRounds,budgetMinutes,commit}));
  const messages=await reasoner.turn(view);
  if(!Array.isArray(messages)||!messages.length)return stop('PROTOCOL_VIOLATION','reasoner sent no message');
  for(const m of messages){
   s=dialogState(journal.entries());
   const problem=checkReasonerMessage(m,s,{maxRounds,budgetMinutes});
   if(problem)return stop(problem.startsWith('ROUND_LIMIT')?'ROUND_LIMIT_REACHED':'PROTOCOL_VIOLATION',problem);
   const extra=m.type==='TEST_REQUEST'?{...binding,job:randomUUID()}:null;
   journal.append('reasoner',m.type,m.body,extra);
   if(m.type==='TEST_REQUEST')break;    // backpressure: at most one outstanding request
  }
 }
 return stop('TURN_LIMIT_REACHED');
}

/** Protocol rules beyond the body schema; returns a problem text or null. */
export function checkReasonerMessage(m,s,{maxRounds,budgetMinutes}){
 if(!m||!TYPES.includes(m.type))return 'unknown message type';
 if(ROLE[m.type]!=='reasoner')return m.type+' may only be written by the '+ROLE[m.type];
 if(s.outcome)return 'the dialog has already ended ('+s.outcome+')';
 const problems=validateBody(m.type,m.body);
 if(problems.length)return problems.join('; ');
 if(s.pending)return 'a TEST_REQUEST is still waiting for its RECEIPT';
 const bySeq=seq=>s.entries.find(e=>e.seq===seq);
 if(s.uncritiqued&&m.type!=='CRITIQUE')return 'the RECEIPT '+s.uncritiqued.seq+' must be critiqued first';
 switch(m.type){
 case 'TEST_REQUEST':{
  if(s.rounds>=maxRounds)return 'ROUND_LIMIT: '+maxRounds+' requests used';
  const h=bySeq(m.body.hypothesis);
  if(h?.type!=='HYPOTHESIS')return 'TEST_REQUEST must reference a HYPOTHESIS';
  if(h.body.module!==m.body.module||h.body.export!==m.body.export)return 'TEST_REQUEST must test exactly its hypothesis';
  if(s.requests.some(q=>q.body.hypothesis===h.seq))return 'this hypothesis was already tested; REVISE it instead';
  if(m.body.budgetMinutes>budgetMinutes)return 'budget above the owner limit of '+budgetMinutes+' minutes';
  return null;}
 case 'CRITIQUE':{
  const r=bySeq(m.body.receipt);
  if(r?.type!=='RECEIPT')return 'CRITIQUE must reference a RECEIPT';
  if(r.body.verdict!==m.body.verdict)return 'CRITIQUE verdict '+m.body.verdict+' contradicts the verified receipt ('+r.body.verdict+')';
  return null;}
 case 'REVISE':
  return bySeq(m.body.hypothesis)?.type==='HYPOTHESIS'?null:'REVISE must reference a HYPOTHESIS';
 default:return null;
 }
}
