/** A real deterministic reporting operation for the assigned persisted bot.
 * Rebuilds references from canonical records. No model call, new task state,
 * experience admission or independent-test claim is created by reporting. */
import fs from 'node:fs';import path from 'node:path';
import {assertCcc1BotAssignment} from './ccc1-bot-assignment.mjs';
import {validateCodingTask,taskFingerprint} from './coding-task.mjs';
import {DurableTaskJournal,runtimeKey} from './ccc1-journal.mjs';
import {taskState} from './ccc1-orchestrator.mjs';
import {builderStateOf,jobIdFor} from './ccc1-recovery.mjs';
import {ensureCcc1StateOutsideSource} from './ccc1-path-policy.mjs';
import {readReceipt} from '../../scripts/continuum-mobile-coding.mjs';
import {verifyRepairReceipt} from './ccc1-verifier.mjs';
import {canonical,sha256Hex} from './development-backlog.mjs';
import {git} from './repair-loop.mjs';

export function writeCcc1BotReport({assignment,task,ccc1TaskId,project,stateDir,readPreparedGrant}={}){
 const spec=validateCodingTask(task),fingerprint=taskFingerprint(spec);
 const reporter=assertCcc1BotAssignment(assignment,{task:spec,ccc1TaskId,stage:'REPORT'});
 if(typeof readPreparedGrant!=='function')throw Error('BOT_REPORT_CURRENT_GRANT_REQUIRED');
 const assertCurrentGrantAndSource=()=>{
  assertCcc1BotAssignment(assignment,{task:spec,ccc1TaskId,stage:'REPORT'});
  const grant=readPreparedGrant();
  if(grant.ccc1TaskId!==ccc1TaskId||grant.fingerprint!==fingerprint||taskFingerprint(grant.task)!==fingerprint)
   throw Error('BOT_REPORT_GRANT_CHANGED');
  if(git(project,['rev-parse','HEAD']).stdout.trim()!==spec.sourceCommit||git(project,['status','--porcelain']).stdout.trim())
   throw Error('BOT_REPORT_SOURCE_CHANGED');
 };
 assertCurrentGrantAndSource();
 const state=ensureCcc1StateOutsideSource(project,stateDir);
 const journal=DurableTaskJournal.forTask(state,ccc1TaskId,runtimeKey(state));
 const entries=journal.entries(),goal=entries[0],status=taskState(entries);
 if(!goal||goal.taskId!==ccc1TaskId||goal.baseCommitSha!==spec.sourceCommit||goal.deadline<=Date.now()||
    !goal.payload.text.includes('[task '+fingerprint+']')||goal.payload.repair?.chain!==spec.chain||
    !goal.permissions.includes('node-repair:'+spec.chain)||entries.some(e=>e.kind==='ABORT')||
    status.approval?.requested&&status.approval.decision!=='GRANTED')throw Error('BOT_REPORT_GRANT_REVOKED');
 const receipts=[];
 for(const entry of entries.filter(e=>e.kind==='RECEIPT')){
  const request=entries.find(e=>e.messageId===entry.payload.request&&e.kind==='TEST_REQUEST');
  if(!request||jobIdFor(ccc1TaskId,request.messageId)!==entry.payload.job)throw Error('BOT_REPORT_RECEIPT_BINDING');
  const receipt=readReceipt(builderStateOf(state),entry.payload.job);
  // An interrupted or absent artifact can be reported as uncertain, never confirmed.
  const matched=receipt!==null&&sha256Hex(JSON.stringify(receipt))===entry.payload.receiptSha256;
  if(entry.payload.verdict==='CONFIRMED'&&(!matched||verifyRepairReceipt(receipt,
      {job:entry.payload.job,commit:spec.sourceCommit,chain:spec.chain}).verdict!=='CONFIRMED'))
   throw Error('BOT_REPORT_CONFIRMED_RECEIPT_INVALID');
  receipts.push({jobId:entry.payload.job,requestMessageId:request.messageId,requestHash:request.hash,
   receiptMessageId:entry.messageId,receiptSha256:entry.payload.receiptSha256,verdict:entry.payload.verdict,
   execution:entry.payload.execution,artifactMatched:matched,attempt:matched?receipt.backlog?.attempts??null:null});
 }
 if(status.state==='COMPLETED'&&!receipts.some(r=>r.verdict==='CONFIRMED'&&r.artifactMatched))throw Error('BOT_REPORT_NO_CONFIRMED_RECEIPT');
 const body={schema:'continuum-bot-result-report',version:1,operation:'DETERMINISTIC_CANONICAL_REPORT',
  reporterId:reporter.agentId,persistentWorkspaceId:reporter.persistentWorkspaceId,
  assignmentSha256:assignment.sha256,taskId:spec.id,canonicalTaskId:ccc1TaskId,taskFingerprint:fingerprint,
  sourceCommit:spec.sourceCommit,journalHead:entries.at(-1).hash,status:status.state,receipts,
  interpretation:status.state==='COMPLETED'?'PATCH_PENDING_OWNER_REVIEW':status.terminal?'NO_CONFIRMED_REPAIR':'PENDING_OR_UNKNOWN',
  modelCalls:0,independentVerifier:false,executionEnvironment:'HOST',modelInferenceAttestedByReport:false,
  deviceVerified:false,approvedForMerge:false};
 const bytes=Buffer.from(canonical(body)+'\n');
 if(bytes.length>32_768)throw Error('BOT_REPORT_CAPACITY');
 const directory=path.join(state,'coding-evidence',spec.id);
 // Do not follow a planted symlink while creating the trusted evidence directory.
 let current=state;
 for(const part of ['coding-evidence',spec.id]){
  current=path.join(current,part);try{fs.mkdirSync(current,{mode:0o700});}catch(e){if(e.code!=='EEXIST')throw e;}
  if(!fs.lstatSync(current).isDirectory()||fs.lstatSync(current).isSymbolicLink()||fs.realpathSync(current)!==current)
   throw Error('BOT_REPORT_PATH_UNSAFE');
 }
 // Recheck current identities/expiry and canonical journal head immediately before writing.
 assertCurrentGrantAndSource();
 if(journal.entries().at(-1)?.hash!==body.journalHead||goal.deadline<=Date.now())throw Error('BOT_REPORT_JOURNAL_CHANGED');
 const file=path.join(directory,'bot-report-'+assignment.sha256+'.json');
 if(fs.existsSync(file)){
  if(!fs.lstatSync(file).isFile()||fs.lstatSync(file).isSymbolicLink()||!fs.readFileSync(file).equals(bytes))
   throw Error('BOT_REPORT_ARTIFACT_CHANGED');
 }else{
  const fd=fs.openSync(file,'wx',0o600);try{fs.writeFileSync(fd,bytes);fs.fsyncSync(fd);}finally{fs.closeSync(fd);}
  const dir=fs.openSync(directory,'r');try{fs.fsyncSync(dir);}finally{fs.closeSync(dir);}
 }
 const sha256=sha256Hex(bytes);
 if(sha256Hex(fs.readFileSync(file))!==sha256)throw Error('BOT_REPORT_ARTIFACT_CHANGED');
 assertCurrentGrantAndSource();
 if(journal.entries().at(-1)?.hash!==body.journalHead||goal.deadline<=Date.now())throw Error('BOT_REPORT_RESULT_STALE');
 return Object.freeze({reporterId:reporter.agentId,operation:body.operation,path:file,sha256,status:body.status,
  journalHead:body.journalHead,modelCalls:0,deviceVerified:false});
}
