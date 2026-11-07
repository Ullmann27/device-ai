/**
 * Bounded post-research CRE1 shadow worker.
 * Research metadata can trigger inspection, never become training labels.
 * Only separately supplied, license-reviewed, original document spans qualify.
 */
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import {RelationalEncoder} from './cre-encoder.mjs';
import {runResearchLearningCycle} from './research-learning-cycle.mjs';

const sha=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
const MAX_SOURCE_BYTES=4*1024*1024;
const MAX_BUDGET_MS=120_000;
function readJsonBounded(file,maxBytes){
  const info=fs.lstatSync(file);
  if(!info.isFile()||info.isSymbolicLink()||info.size>maxBytes)
    throw Error('Shadow evidence file unsafe or over budget');
  return JSON.parse(fs.readFileSync(file,'utf8'));
}
function writeSynced(file,bytes){
  const temp=file+'.tmp-'+process.pid+'-'+crypto.randomBytes(6).toString('hex');
  let fd;
  try{
    fd=fs.openSync(temp,'wx',0o600);
    const data=Buffer.from(bytes);
    let offset=0;
    while(offset<data.length){
      const wrote=fs.writeSync(fd,data,offset,data.length-offset,offset);
      if(wrote<1)throw Error('Short shadow checkpoint write');
      offset+=wrote;
    }
    fs.fsyncSync(fd);
    fs.closeSync(fd);fd=null;
    fs.renameSync(temp,file);
    // Best effort on filesystems which expose directory fsync.
    try{
      const dirfd=fs.openSync(path.dirname(file),'r');
      try{fs.fsyncSync(dirfd)}finally{fs.closeSync(dirfd)}
    }catch{/* File sync and rename already completed. */}
  }finally{
    if(fd!==undefined&&fd!==null)fs.closeSync(fd);
    try{fs.unlinkSync(temp)}catch(e){if(e.code!=='ENOENT')throw e}
  }
}
function safeDir(directory){
  fs.mkdirSync(directory,{recursive:true,mode:0o700});
  const info=fs.lstatSync(directory);
  if(!info.isDirectory()||info.isSymbolicLink())throw Error('Unsafe shadow directory');
}
function sameFrozenE(before,after){
  const a=RelationalEncoder.restore(before),b=RelationalEncoder.restore(after);
  return Buffer.from(a.E.buffer,a.E.byteOffset,a.E.byteLength)
    .equals(Buffer.from(b.E.buffer,b.E.byteOffset,b.E.byteLength));
}

export function runPostResearchShadowCycle({
  researchResult,stateDir,sourcePath=null,now=Date.now,
  budgetMs=30_000,trainingOptions={}
}={}){
  if(researchResult?.success!==true)return {status:'SKIPPED_NO_NEW_RESEARCH'};
  if(typeof stateDir!=='string'||!stateDir.trim())throw Error('Shadow research directory required');
  if(!Number.isInteger(budgetMs)||budgetMs<500||budgetMs>MAX_BUDGET_MS)
    throw Error('Invalid shadow cycle deadline');
  const directory=path.resolve(stateDir);
  const input=sourcePath?path.resolve(sourcePath):path.join(directory,'approved-extractive-spans.json');
  if(path.dirname(input)!==directory)throw Error('Source bundle must live in the isolated CRE1 research directory');
  if(!fs.existsSync(input))return {status:'AWAITING_VERIFIED_ORIGINAL_DOCUMENTS'};
  safeDir(directory);
  const incoming=fs.lstatSync(input);
  if(!incoming.isFile()||incoming.isSymbolicLink()||incoming.size>MAX_SOURCE_BYTES)
    throw Error('Unsafe or oversized original document bundle');
  const lockFile=path.join(directory,'research-shadow.lock');
  let lock;
  try{
    lock=fs.openSync(lockFile,'wx',0o600);
  }catch(e){
    if(e.code==='EEXIST')return {status:'LOCKED_REQUIRES_REVIEW'};
    throw e;
  }
  const token=crypto.randomBytes(16).toString('hex');
  const lockInode=fs.fstatSync(lock).ino;
  try{
    fs.writeSync(lock,JSON.stringify({pid:process.pid,token,at:now()})+'\n');
    fs.fsyncSync(lock);
    const sourceBytes=fs.readFileSync(input);
    if(sourceBytes.length>MAX_SOURCE_BYTES)throw Error('Shadow original evidence exceeds budget');
    const sourceHash=sha(sourceBytes);
    const checkpoint=path.join(directory,'cre1-research-v1.cre1');
    const reportPath=path.join(directory,'shadow-audit.json');
    if(fs.existsSync(reportPath)){
      const previous=readJsonBounded(reportPath,64_000);
      if(previous.sourceSha256===sourceHash&&previous.status!=='ERROR')
        return {status:'UNCHANGED_VERIFIED_CORPUS',sourceSha256:sourceHash};
    }
    const inputData=JSON.parse(sourceBytes.toString('utf8'));
    if(!Array.isArray(inputData)||inputData.length>512)
      throw Error('Expected a bounded array of independently verified source spans');
    const baseline=fs.existsSync(checkpoint)
      ?fs.readFileSync(checkpoint):new RelationalEncoder(73).save();
    RelationalEncoder.restore(baseline);
    const started=now();
    const result=runResearchLearningCycle({
      baselineBytes:baseline,spans:inputData,now:started,
      trainingOptions:{...trainingOptions,deadlineAt:Date.now()+budgetMs}
    });
    if(now()-started>budgetMs)throw Error('Shadow cycle exceeded time budget');
    if(result.accepted){
      if(!result.shadowModelBytes||!sameFrozenE(baseline,result.shadowModelBytes))
        throw Error('Frozen CRE1 base embeddings changed during shadow learning');
      if(fs.existsSync(checkpoint))writeSynced(checkpoint+'.lkg',baseline);
      writeSynced(checkpoint,result.shadowModelBytes);
    }
    const report={
      schema:'continuum-cre1-shadow-audit',version:1,
      at:now(),status:result.accepted?'SHADOW_ACCEPTED':'SHADOW_REJECTED',
      reason:result.reason,sourceSha256:sourceHash,
      trainCount:result.trainCount??0,auditCount:result.auditCount??0,
      modelSha256:result.modelSha256??sha(baseline),
      canaries:result.canaries??null,
      siblings:result.siblings??null,
      metrics:result.shadowMetrics??null,
      authority:'NONE',productionPromotion:false
    };
    writeSynced(reportPath,Buffer.from(JSON.stringify(report,null,2)+'\n','utf8'));
    return {status:report.status,sourceSha256:sourceHash,report,
      checkpointWritten:result.accepted};
  }finally{
    try{
      const found=fs.lstatSync(lockFile);
      const current=fs.readFileSync(lockFile,'utf8');
      if(found.ino===lockInode&&current.includes(token))fs.unlinkSync(lockFile);
    }catch(e){if(e.code!=='ENOENT')throw e}
    fs.closeSync(lock);
  }
}
