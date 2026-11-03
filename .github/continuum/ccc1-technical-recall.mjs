/** Bounded historical technical evidence in the existing CCC1 experience ledger.
 * Runtime and independent-verifier observations are admitted only by their private
 * process brands. A runtime-key MAC preserves that admission across restart. The
 * runtime key and app-private state remain trust boundaries; this is never owner
 * knowledge, training, permission, a verified skill or scientific model acceptance.
 */
import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {createHmac,timingSafeEqual,randomBytes} from 'node:crypto';
import {canonical,sha256} from './ccc1-envelope.mjs';
import {DurableTaskJournal} from './ccc1-journal.mjs';
import {experienceOf,ExperienceLog} from './ccc1-experience.mjs';
import {validateCodingTask,taskFingerprint} from './coding-task.mjs';
import {jobTaskId} from '../../scripts/continuum-mobile-coding.mjs';
import * as runtime from './coding-provider.mjs';
import * as verifier from './ccc1-redgreen-verifier.mjs';

export const TECHNICAL_RECALL_LIMITS=Object.freeze({maxItems:3,maxChars:4096,maxAgeMs:30*86400000,maxProofBytes:180000,maxLineageChecks:8,maxGitCalls:32,maxGitMillis:10000});
const DOMAIN='continuum-momega1-technical-proof-v1\0';
const HEX=/^[a-f0-9]{64}$/;
const TASK=/^t-[a-f0-9]{16}$/;
const fail=code=>{throw Error(code);};
const hash=v=>sha256(canonical(v));
const keyOk=key=>Buffer.isBuffer(key)&&key.length===32;
const mac=(data,key)=>{if(!keyOk(key))fail('TECHNICAL_RUNTIME_KEY_REQUIRED');return createHmac('sha256',key).update(DOMAIN+canonical(data)).digest('hex');};
function authentic(proof,key){
 if(!proof||!keyOk(key)||typeof proof.mac!=='string'||!HEX.test(proof.mac))return false;
 const {mac:given,...data}=proof;
 return timingSafeEqual(Buffer.from(given,'hex'),Buffer.from(mac(data,key),'hex'));
}
const sign=(data,key)=>({...data,mac:mac(data,key)});
function safeRead(file,max){
 const full=path.resolve(file),s=fs.lstatSync(full);
 if(!s.isFile()||s.isSymbolicLink()||fs.realpathSync(full)!==full||s.size>max)fail('TECHNICAL_STATE_UNSAFE');
 return JSON.parse(fs.readFileSync(full,'utf8'));
}
function grantOf(stateDir,task,taskId,key,{allowRevoked=false,allowLease=false}={}){
 const file=path.join(stateDir,'coding-grants',task.id+'.json');
 if(!allowLease&&fs.existsSync(file+'.technical-revoke.lock'))fail('TECHNICAL_GRANT_WITHDRAWAL_IN_PROGRESS');
 const stored=safeRead(file,16000);
 if(stored.ccc1TaskId!==taskId||stored.fingerprint!==taskFingerprint(task)||
    taskFingerprint(stored.task)!==stored.fingerprint)fail('TECHNICAL_GRANT_MISMATCH');
 const revocation=stored.technicalExperienceRevocation;
 if(Object.hasOwn(stored,'technicalExperienceRevocation')){
  if(!authentic(revocation,key)||revocation.schema!=='momega1-technical-revocation'||revocation.version!==1||
     revocation.targetTaskId!==taskId||revocation.taskFingerprint!==stored.fingerprint||
     !Number.isSafeInteger(revocation.observedAt)||!HEX.test(revocation.journalHead??''))fail('TECHNICAL_GRANT_REVOCATION_INVALID');
  if(!allowRevoked)fail('TECHNICAL_GRANT_REVOKED');
 }
 return stored;
}
function journalOf(stateDir,taskId,key){
 if(!TASK.test(taskId??''))fail('TECHNICAL_TASK_ID_INVALID');
 const file=path.join(stateDir,'tasks',taskId+'.jsonl');
 // The canonical reader verifies envelope/hash/MAC/binding; reject path aliases first.
 const stat=fs.lstatSync(file);
 if(!stat.isFile()||stat.isSymbolicLink()||fs.realpathSync(file)!==path.resolve(file)||stat.size>1024*1024)
  fail('TECHNICAL_JOURNAL_UNSAFE');
 return DurableTaskJournal.forTask(stateDir,taskId,key).entries();
}
function terminalBinding(entries,task){
 const goal=entries[0],summary=experienceOf(goal?.taskId,entries);
 if(!goal||summary?.outcome!=='COMPLETED'||goal.baseCommitSha!==task.sourceCommit||
    !goal.payload.text.includes('[task '+taskFingerprint(task)+']')||goal.payload.repair?.chain!==task.chain)
  fail('TECHNICAL_TERMINAL_PROOF_REQUIRED');
 const receipt=entries.find(e=>e.kind==='RECEIPT'&&e.sender==='VERIFIER'&&e.payload.verdict==='CONFIRMED'&&
  e.payload.execution==='EXECUTED'&&HEX.test(e.payload.patchSha256??'')&&HEX.test(e.payload.receiptSha256??''));
 const request=entries.find(e=>e.messageId===receipt?.payload.request&&e.kind==='TEST_REQUEST');
 if(!receipt||!request||request.payload.repair?.chain!==task.chain||
    !entries.some(e=>e.kind==='CRITIQUE'&&e.payload.verdict==='CONFIRMED'&&e.payload.receipt===receipt.messageId))fail('TECHNICAL_VERIFIER_RECEIPT_REQUIRED');
 return {goal,summary,receipt,request};
}
function reflectionOf(input){
 const text=(v,n)=>typeof v==='string'&&v.trim()&&v.length<=n&&!/[\x00-\x1f\x7f]/.test(v);
 if(!input||!text(input.summary,500)||!Array.isArray(input.limits)||input.limits.length<1||input.limits.length>4||
    input.limits.some(x=>!text(x,200))||!Array.isArray(input.rejectedAlternatives)||input.rejectedAlternatives.length>4||
    input.rejectedAlternatives.some(x=>!text(x,200)))fail('TECHNICAL_REFLECTION_REQUIRED');
 return {summary:input.summary,limits:[...input.limits],rejectedAlternatives:[...input.rejectedAlternatives]};
}
function checkProofBindings(proof,entries,task){
 const {goal,receipt,request}=terminalBinding(entries,task);
 const modelTask=validateCodingTask(proof.modelTask),model=proof.modelObservation,v=proof.verification;
 const same={...modelTask,id:task.id};
 if(taskFingerprint(same)!==taskFingerprint(task)||modelTask.id!==jobTaskId(receipt.payload.job)||
    proof.taskFingerprint!==taskFingerprint(task)||proof.repoFullName!==goal.repoFullName||
    proof.taskId!==goal.taskId||proof.journalHead!==entries.at(-1).hash||
    proof.receiptMessageId!==receipt.messageId||proof.requestMessageId!==request.messageId||
    proof.receiptSha256!==receipt.payload.receiptSha256||
    model?.taskFingerprint!==taskFingerprint(modelTask)||model.sourceCommit!==task.sourceCommit||model.taskId!==modelTask.id||
    model.schema!=='momega1-local-process-observation-v1'||model.evidenceClass!=='LOCAL_MODEL_HOST'||
    model.verifiedModelExecution!==true||
    model.exitCode!==0||!Number.isSafeInteger(model.pid)||model.pid<1||
    typeof model.executionId!=='string'||!model.executionId||model.executionId.length>200||
    ['contextSha256','promptSha256','requestSha256','modelSha256','runtimeSha256','outputSha256','proposalSha256'].some(k=>!HEX.test(model[k]??''))||
    v?.schema!=='momega1-coding-verification'||v.version!==1||v.verdict!=='VERIFIED'||
    v.evidenceClass!=='HOST_INTEGRATION'||v.taskFingerprint!==model.taskFingerprint||v.sourceCommit!==task.sourceCommit||
    v.chain!==task.chain||v.patchSha256!==receipt.payload.patchSha256||v.proposalSha256!==model.proposalSha256||
    !v.red||!v.green||!Array.isArray(v.diagnostics)||!v.diagnostics.length)
  fail('TECHNICAL_BINDING_MISMATCH');
 if(proof.evidenceClass!=='LOCAL_MODEL_HOST'||proof.kind!=='TECHNICAL_EXPERIENCE_REVIEW_ONLY'||
    proof.schema!=='momega1-technical-proof'||proof.version!==1)fail('TECHNICAL_PROOF_CLASS_INVALID');
 reflectionOf(proof.reflection);
}

/** Called by the trusted worker before the legacy terminal-summary append.
 * JSON copies and self-certified flags cannot replace either live observation brand.
 */
export function recordTechnicalExperience({stateDir,taskId,task,modelTask,modelObservation,verification,reflection,key,now=Date.now()}={}){
 const spec=validateCodingTask(task);grantOf(stateDir,spec,taskId,key);
 if(runtime.isTrustedLocalModelObservation?.(modelObservation)!==true)fail('TECHNICAL_RUNTIME_OBSERVATION_REQUIRED');
 // A private process brand proves execution identity, not that a pinned binary
 // actually performed neural inference. Only the reviewed runtime producer can
 // assert the stronger observation; process-only attempts remain unverified.
 if(!Object.isFrozen(modelObservation)||modelObservation.verifiedModelExecution!==true)
  fail('TECHNICAL_MODEL_EXECUTION_PROOF_UNAVAILABLE');
 if(verifier.isTrustedCodingVerification?.(verification)!==true)fail('TECHNICAL_INDEPENDENT_VERIFICATION_REQUIRED');
 if(!Number.isSafeInteger(now)||now<0)fail('TECHNICAL_TIME_INVALID');
 const entries=journalOf(stateDir,taskId,key),{goal,summary,receipt,request}=terminalBinding(entries,spec);
 const data={schema:'momega1-technical-proof',version:1,kind:'TECHNICAL_EXPERIENCE_REVIEW_ONLY',evidenceClass:'LOCAL_MODEL_HOST',
  taskId,task:spec,modelTask:validateCodingTask(modelTask),taskFingerprint:taskFingerprint(spec),repoFullName:goal.repoFullName,
  journalHead:entries.at(-1).hash,requestMessageId:request.messageId,receiptMessageId:receipt.messageId,
  receiptSha256:receipt.payload.receiptSha256,modelObservation,verification,reflection:reflectionOf(reflection),
  observedAt:now,expiresAt:now+TECHNICAL_RECALL_LIMITS.maxAgeMs,
  resources:{cpuMillis:summary.cpuMillis,rounds:summary.rounds}};
 checkProofBindings(data,entries,spec);
 // The ledger persists JSON, which omits optional undefined object properties
 // and converts sparse array slots to null. Authenticate that exact snapshot,
 // after checking the original live producer brands, so restart sees identical
 // proof bytes. Never repair or re-sign an existing unauthenticated record.
 let snapshot;
 try{snapshot=JSON.parse(JSON.stringify(data));}catch{fail('TECHNICAL_PROOF_NOT_SERIALIZABLE');}
 checkProofBindings(snapshot,entries,spec);
 if(Buffer.byteLength(JSON.stringify(snapshot))>TECHNICAL_RECALL_LIMITS.maxProofBytes)fail('TECHNICAL_PROOF_TOO_LARGE');
 return new ExperienceLog(path.join(stateDir,'experience.jsonl')).append({...summary,technicalProof:sign(snapshot,key)});
}

function durableGrant(file){
 const fd=fs.openSync(file,fs.constants.O_RDONLY|fs.constants.O_NOFOLLOW);
 try{fs.fsyncSync(fd);}finally{fs.closeSync(fd);}
 const dir=fs.openSync(path.dirname(file),fs.constants.O_RDONLY|fs.constants.O_DIRECTORY);
 try{fs.fsyncSync(dir);}finally{fs.closeSync(dir);}
}

/** The EXISTING canonical grant is the withdrawal authority. Its ancillary
 * MACed field survives experience-ledger truncation, rewrite and capacity limits.
 * Grants are created once, before task execution; existing runtime consumers only
 * read a final grant. Only completed canonical tasks may acquire this RMW lease.
 * Future grant mutators must honor the same lease. No positive consent is reset.
 */
export function revokeTechnicalExperience({stateDir,taskId,task,key,reason,now=Date.now()}={}){
 if(!TASK.test(taskId??'')||typeof reason!=='string'||!reason.trim()||reason.length>300||!Number.isSafeInteger(now)||now<0)
  fail('TECHNICAL_REVOCATION_INVALID');
 if(!keyOk(key))fail('TECHNICAL_RUNTIME_KEY_REQUIRED');
 const spec=validateCodingTask(task),file=path.resolve(stateDir,'coding-grants',spec.id+'.json');
 // Refuse aliases before the first mutation. A missing/finalizing grant is never
 // reconstructed from a possibly damaged or attacker-rehashed experience line.
 safeRead(file,16000);
 const lock=file+'.technical-revoke.lock',until=Date.now()+2000;
 for(;;){
  try{fs.mkdirSync(lock,{mode:0o700});break;}
  catch(error){if(error.code!=='EEXIST')throw error;if(Date.now()>=until)fail('TECHNICAL_GRANT_WITHDRAWAL_LOCKED');Atomics.wait(new Int32Array(new SharedArrayBuffer(4)),0,0,10);}
 }
 let proof,isNew=false;
 try{
  const original=fs.readFileSync(file),identity=fs.lstatSync(file,{bigint:true});
  const stored=grantOf(stateDir,spec,taskId,key,{allowRevoked:true,allowLease:true});
  const entries=journalOf(stateDir,taskId,key);terminalBinding(entries,spec);
  proof=stored.technicalExperienceRevocation;
  if(!proof){
   proof=sign({schema:'momega1-technical-revocation',version:1,targetTaskId:taskId,taskFingerprint:taskFingerprint(spec),
    journalHead:entries.at(-1).hash,reason,observedAt:now},key);
   const bytes=Buffer.from(JSON.stringify({...stored,technicalExperienceRevocation:proof})+'\n');
   if(bytes.length>16000)fail('TECHNICAL_GRANT_TOO_LARGE');
   const temp=file+'.withdraw-'+process.pid+'-'+randomBytes(8).toString('hex');
   let fd;
   try{
    fd=fs.openSync(temp,fs.constants.O_WRONLY|fs.constants.O_CREAT|fs.constants.O_EXCL|fs.constants.O_NOFOLLOW,0o600);
    fs.writeFileSync(fd,bytes);fs.fsyncSync(fd);fs.closeSync(fd);fd=undefined;
    const current=fs.lstatSync(file,{bigint:true});
    if(current.isSymbolicLink()||current.ino!==identity.ino||current.dev!==identity.dev||current.size!==identity.size||
       current.mtimeNs!==identity.mtimeNs||current.ctimeNs!==identity.ctimeNs||!fs.readFileSync(file).equals(original))
     fail('TECHNICAL_GRANT_CHANGED_DURING_WITHDRAWAL');
    fs.renameSync(temp,file);isNew=true;
   }finally{if(fd!==undefined)fs.closeSync(fd);fs.rmSync(temp,{force:true});}
  }
  // Also retry this barrier for an already committed anchor after uncertain EIO.
  durableGrant(file);
 }finally{fs.rmdirSync(lock);}
 // The tombstone is an optional audit mirror in the SAME existing ledger. Its
 // failure cannot undo the already durable canonical withdrawal or overflow the
 // ledger's bounds. Report the mirror limitation without logging private reason.
 try{new ExperienceLog(path.join(stateDir,'experience.jsonl')).append({taskId:'revoke:'+taskId,revocation:proof});}
 catch{process.emitWarning('Technical experience withdrawal committed; experience ledger mirror unavailable.',{code:'TECHNICAL_REVOCATION_MIRROR_UNAVAILABLE'});}
 return isNew;
}

function requestingTaskAuthorized(stateDir,task,repoFullName,key,now){
 const raw=safeRead(path.join(stateDir,'coding-grants',task.id+'.json'),16000);
 if(!TASK.test(raw.ccc1TaskId??''))fail('TECHNICAL_REQUEST_GRANT_INVALID');
 grantOf(stateDir,task,raw.ccc1TaskId,key);
 const entries=journalOf(stateDir,raw.ccc1TaskId,key),goal=entries[0];
 if(!goal||goal.repoFullName!==repoFullName||goal.baseCommitSha!==task.sourceCommit||goal.deadline<=now||
    goal.payload.repair?.chain!==task.chain||!goal.payload.text.includes('[task '+taskFingerprint(task)+']')||
    !goal.permissions.includes('node-repair:'+task.chain)||entries.some(e=>e.kind==='ABORT'))
  fail('TECHNICAL_REQUEST_NOT_AUTHORIZED');
}

/** Optional trusted-project provenance. All Git operations are fixed local
 * read commands. Actual checkout identity and real commit ancestry are observed;
 * they establish historical lineage only, never semantic validity or permission.
 */
function projectLineage(project,sourceCommit,repoFullName){
 if(typeof project!=='string'||!project||project.length>4096||/[\x00-\x1f\x7f]/.test(project))fail('TECHNICAL_PROJECT_INVALID');
 const root=fs.realpathSync(project),deadline=performance.now()+TECHNICAL_RECALL_LIMITS.maxGitMillis;
 let calls=0,checks=0;const cache=new Map();
 const git=(args,{allowed=[0],discoverGraft=false}={})=>{
  const remaining=Math.floor(deadline-performance.now());
  if(remaining<1||++calls>TECHNICAL_RECALL_LIMITS.maxGitCalls)fail('TECHNICAL_PROJECT_BUDGET');
  const env={PATH:process.env.PATH,LC_ALL:'C',GIT_TERMINAL_PROMPT:'0',GIT_CONFIG_NOSYSTEM:'1',GIT_CONFIG_GLOBAL:'/dev/null',
   GIT_CONFIG_SYSTEM:'/dev/null',GIT_OPTIONAL_LOCKS:'0',GIT_NO_LAZY_FETCH:'1',GIT_NO_REPLACE_OBJECTS:'1',GIT_ATTR_NOSYSTEM:'1'};
  // rev-parse --git-path honors GIT_GRAFT_FILE; discover the real path without
  // the override, then disable graft interpretation for every graph query.
  if(!discoverGraft)env.GIT_GRAFT_FILE='/dev/null';
  const r=spawnSync('git',['--no-optional-locks','-c','core.fsmonitor=false','-c','core.untrackedCache=false',
   '-c','core.hooksPath=/dev/null','-C',root,...args],{encoding:'utf8',shell:false,timeout:Math.min(3000,remaining),maxBuffer:65536,env});
  if(r.error||r.signal||!allowed.includes(r.status))fail('TECHNICAL_PROJECT_GIT_UNAVAILABLE');
  return {status:r.status,text:r.stdout.trim()};
 };
 if(fs.realpathSync(git(['rev-parse','--show-toplevel']).text)!==root)fail('TECHNICAL_PROJECT_ROOT_MISMATCH');
 const assertCurrent=()=>{
  if(git(['rev-parse','--verify','HEAD^{commit}']).text!==sourceCommit||
     git(['status','--porcelain=v1','--untracked-files=all','--ignore-submodules=none']).text)fail('TECHNICAL_PROJECT_STALE_OR_DIRTY');
  const remote=git(['remote','get-url','origin'],{allowed:[0,2]});
  const match=remote.status===0?/^(?:https:\/\/github\.com\/|git@github\.com:|ssh:\/\/(?:git@)?github\.com\/)([A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+?)(?:\.git)?\/?$/.exec(remote.text):null;
  const repo=match?match[1]:'local/'+path.basename(root).replace(/[^A-Za-z0-9._-]/g,'-');
  if(repo!==repoFullName)fail('TECHNICAL_PROJECT_REPOSITORY_MISMATCH');
  if(git(['for-each-ref','--format=%(refname)','refs/replace']).text)fail('TECHNICAL_PROJECT_REPLACEMENTS_REFUSED');
  const graft=path.resolve(root,git(['rev-parse','--git-path','info/grafts'],{discoverGraft:true}).text);
  try{fs.lstatSync(graft);fail('TECHNICAL_PROJECT_GRAFTS_REFUSED');}catch(error){if(error.code!=='ENOENT')throw error;}
 };
 assertCurrent();
 if(git(['cat-file','-t',sourceCommit]).text!=='commit')fail('TECHNICAL_CURRENT_COMMIT_UNAVAILABLE');
 return {assertCurrent,lineage(old){
  if(old===sourceCommit)return {kind:'EXACT_SOURCE',historicalSourceCommit:old,currentSourceCommit:sourceCommit,ancestor:true};
  if(cache.has(old))return cache.get(old);
  if(++checks>TECHNICAL_RECALL_LIMITS.maxLineageChecks)return null;
  let lineage=null;
  try{
   if(git(['cat-file','-t',old]).text==='commit'&&git(['merge-base','--is-ancestor',old,sourceCommit],{allowed:[0,1]}).status===0)
    lineage={kind:'GIT_ANCESTOR',historicalSourceCommit:old,currentSourceCommit:sourceCommit,ancestor:true};
  }catch{/* Missing/incomplete local history fails closed; never fetch it. */}
  cache.set(old,lineage);return lineage;
 }};
}

/** Every call rereads persisted records, canonical grants and MAC-verified journals.
 * Without a trusted project, exact source scope is retained. Project-backed Git
 * ancestry adds historical provenance only, never semantic or owner-fact inference.
 */
export function recallTechnicalExperiences({stateDir,task,repoFullName,key,now=Date.now(),limit=3,maxChars=4096,project=null}={}){
 const spec=validateCodingTask(task);
 if(!keyOk(key))fail('TECHNICAL_RUNTIME_KEY_REQUIRED');
 if(!Number.isSafeInteger(now)||now<0||!Number.isSafeInteger(limit)||limit<1||limit>TECHNICAL_RECALL_LIMITS.maxItems||
    !Number.isSafeInteger(maxChars)||maxChars<128||maxChars>TECHNICAL_RECALL_LIMITS.maxChars)fail('TECHNICAL_RECALL_BOUND_INVALID');
 const result={schema:'momega1-technical-recall',version:1,status:'OK',items:[],text:''};
 try{requestingTaskAuthorized(stateDir,spec,repoFullName,key,now);}
 catch{result.status='BLOCKED_REQUEST_NOT_AUTHORIZED';return result;}
 let sourceProject=null;
 if(project!==null)try{sourceProject=projectLineage(project,spec.sourceCommit,repoFullName);}
 catch{result.status='BLOCKED_PROJECT_LINEAGE_UNAVAILABLE';return result;}
 let records;
 try{records=new ExperienceLog(path.join(stateDir,'experience.jsonl')).entries().map(x=>x.record);}
 catch{result.status='BLOCKED_LEDGER_DAMAGED';return result;}
 const revoked=new Set();
 for(const record of records){
  if(!record.revocation)continue;
  if(!authentic(record.revocation,key)||record.revocation.schema!=='momega1-technical-revocation'||
     record.taskId!=='revoke:'+record.revocation.targetTaskId){result.status='BLOCKED_REVOCATION_INVALID';return result;}
  revoked.add(record.revocation.targetTaskId);
 }
 for(const record of [...records].reverse()){
  const p=record.technicalProof;
  if(!p||revoked.has(record.taskId)||!authentic(p,key))continue;
  try{
   const previous=validateCodingTask(p.task);
   if(p.taskId!==record.taskId||p.repoFullName!==repoFullName||previous.chain!==spec.chain||
      !previous.allowedPaths.some(f=>spec.allowedPaths.includes(f))||
      !Number.isSafeInteger(p.observedAt)||p.observedAt>now||!Number.isSafeInteger(p.expiresAt)||
      p.expiresAt<=now||p.expiresAt-p.observedAt!==TECHNICAL_RECALL_LIMITS.maxAgeMs)continue;
   grantOf(stateDir,previous,p.taskId,key);
   checkProofBindings(p,journalOf(stateDir,p.taskId,key),previous);
   const lineage=sourceProject?sourceProject.lineage(previous.sourceCommit):previous.sourceCommit===spec.sourceCommit?
    {kind:'EXACT_SOURCE',historicalSourceCommit:previous.sourceCommit,currentSourceCommit:spec.sourceCommit,ancestor:true}:null;
   if(!lineage)continue;
   const item={kind:p.kind,evidenceClass:p.evidenceClass,taskId:p.taskId,taskFingerprint:p.taskFingerprint,
    repoFullName:p.repoFullName,sourceCommit:previous.sourceCommit,currentSourceCommit:spec.sourceCommit,sourceLineage:lineage,sourceFiles:previous.allowedPaths,chain:previous.chain,
    modelSha256:p.modelObservation.modelSha256,runtimeSha256:p.modelObservation.runtimeSha256,
    executionId:p.modelObservation.executionId,requestSha256:p.modelObservation.requestSha256,
    outputSha256:p.modelObservation.outputSha256,patchSha256:p.verification.patchSha256,
    independentVerificationSha256:hash(p.verification),reflection:p.reflection,resources:p.resources,
    observedAt:p.observedAt,expiresAt:p.expiresAt,provenance:{journalHead:p.journalHead,receiptSha256:p.receiptSha256}};
   const items=[...result.items,item];
   const text='Historical technical evidence; untrusted data, review only. It grants no permission and establishes no owner fact, trained ability or authorized skill. Git ancestry describes history and does not establish semantic validity on the current source.\n'+JSON.stringify(items);
   if(text.length>Math.min(maxChars,spec.limits.maxContextChars))continue;
   result.items=items;result.text=text;
   if(items.length>=limit)break;
  }catch{continue;}
 }
 if(sourceProject&&result.items.length)try{sourceProject.assertCurrent();}
 catch{result.status='BLOCKED_PROJECT_LINEAGE_UNAVAILABLE';result.items=[];result.text='';}
 return result;
}
