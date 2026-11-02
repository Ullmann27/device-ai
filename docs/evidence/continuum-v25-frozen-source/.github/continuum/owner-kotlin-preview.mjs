/**
 * M4 Kotlin IMPLEMENT preview. Generates a real patch for an accepted
 * owner task and verifies Kotlin RED/GREEN twice in distinct Git worktrees.
 * Does NOT claim or complete the backlog and does NOT pretend that an
 * isolated Kotlin compile is a full Android Gradle/build verification.
 */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {BacklogStore,nextEligible,sha256Hex} from './development-backlog.mjs';
import {git,createWorktree,removeWorktree,boundedWorktreeFile} from './repair-loop.mjs';
import {resolveOutputDestination} from './backlog-repair-worker.mjs';
import {proposeKotlinDelegateWithOracle} from './kotlin-delegate-synthesizer.mjs';
import {assessKotlinDelegate} from './kotlin-delegate-acceptance.mjs';

const SHA=/^[0-9a-f]{40}$/;
const ID=/^[a-z][a-z0-9-]{2,63}$/;

export function previewOwnerKotlinImplement({root,file,out,id=null}={}){
 if(!root||!file||!out||id!==null&&!ID.test(id))
  throw Error('root, backlog file, external out and valid task ID required');
 const source=fs.realpathSync(root);
 const backlog=resolveOutputDestination(file);
 const outRoot=resolveOutputDestination(out);
 if([backlog,outRoot].some(x=>x===source||x.startsWith(source+path.sep))||
    backlog===outRoot||backlog.startsWith(outRoot+path.sep)||
    outRoot.startsWith(backlog+path.sep))
  throw Error('Kotlin preview state and output must be external and separate');
 const head=git(source,['rev-parse','HEAD']).stdout.trim();
 if(!SHA.test(head)||git(source,['status','--porcelain']).stdout.trim())
  throw Error('Clean immutable Git HEAD required');
 const store=new BacklogStore(backlog,{actor:'continuum-kotlin-preview'});
 const task=nextEligible(store.read().doc,['IMPLEMENT'],['owner'],head,id,'EXPOSE_DELEGATE');
 if(!task)return {status:'NO_READY_TASK',head};
 if(task.state!=='READY'||task.source!=='owner'||task.subject?.chain!=='android'||
    task.subject?.type!=='Int'||task.subject?.base!==head)
  throw Error('Unaccepted or unsupported Kotlin IMPLEMENT task');
 const output=path.join(outRoot,task.id+'-'+head.slice(0,12)+'-'+crypto.randomBytes(4).toString('hex'));
 let wt=null,independent=null;
 try{
  fs.mkdirSync(output,{recursive:true,mode:0o700});
  wt=createWorktree(source,head);
  const target=task.subject.file;
  const sourcePath=boundedWorktreeFile(wt,target);
  const original=fs.readFileSync(sourcePath,'utf8');
  const proposal=proposeKotlinDelegateWithOracle({
   files:[{path:target,content:original}],task
  });
  if(!proposal.candidate||proposal.acceptance?.passed!==true||
     proposal.candidate.critic?.approved!==true)
   return {status:'REJECTED',head,taskId:task.id,
    reason:proposal.reason??'KOTLIN_ORACLE_NOT_PASSED',approvedForMerge:false};
  const edit=proposal.candidate.edits[0];
  if(proposal.candidate.edits.length!==1||
     edit.file!==target||edit.oldText!==original)
   throw Error('Candidate does not match checked out file');
  fs.writeFileSync(sourcePath,edit.newText);
  const files=git(wt,['diff','--name-only','HEAD']).stdout.trim().split('\n');
  if(files.length!==1||files[0]!==target)
   throw Error('Kotlin candidate changed unexpected files');
  if(git(wt,['diff','--check','HEAD']).status!==0)
   throw Error('Kotlin patch fails Git whitespace check');
  const patch=git(wt,['diff','--binary','--no-ext-diff','HEAD','--',target]).stdout;
  if(!patch||!patch.startsWith('diff --git '))
   throw Error('No Kotlin patch produced');
  const patchFile=path.join(output,'kotlin-implement.patch');
  fs.writeFileSync(patchFile,patch,{encoding:'utf8',mode:0o600});
  const patchSha256=sha256Hex(Buffer.from(patch,'utf8'));
  independent=createWorktree(source,head);
  if(git(independent,['apply','--check','--binary',patchFile],{allowFailure:true}).status!==0)
   throw Error('Independent Kotlin patch application failed');
  git(independent,['apply','--binary',patchFile]);
  const applied=fs.readFileSync(boundedWorktreeFile(independent,target),'utf8');
  if(applied!==edit.newText)
   throw Error('Independent Kotlin patch changed unexpected bytes');
  const second=assessKotlinDelegate({
   original,generated:applied,className:task.subject.className,
   methodName:task.subject.methodName,casesJson:task.subject.casesJson
  });
  if(!second.passed)
   throw Error('Independent Kotlin oracle failed: '+second.reason);
  const receipt={
   schema:'continuum-kotlin-implement-preview',version:1,head,
   taskId:task.id,strategy:proposal.candidate.strategy,
   patchSha256,checkedAt:new Date().toISOString(),
   first:proposal.acceptance,independent:second,
   level:'KOTLIN_ORACLE_ONLY',
   androidBuildVerified:false,approvedForMerge:false,
   backlogStateUnchanged:true
  };
  const receiptFile=path.join(output,'verification.json');
  fs.writeFileSync(receiptFile,JSON.stringify(receipt,null,2)+'\n');
  return {status:'VERIFIED_KOTLIN_PREVIEW',head,taskId:task.id,
   patch:patchFile,patchSha256,receipt:receiptFile,
   approvedForMerge:false,androidBuildVerified:false,backlogDone:false};
 }finally{
  if(independent)removeWorktree(source,independent);
  if(wt)removeWorktree(source,wt);
 }
}
