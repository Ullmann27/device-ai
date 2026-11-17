/** Developer-only CRE1 checkpoint storage, independent of Android owner memory.
 * All state stays outside the checkout and promotion is compare-and-swap.
 */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {RelationalEncoder} from './cre-encoder.mjs';
import {DEV_CRE1_FILENAME} from './cefr-repair-ranker.mjs';

const sha256=x=>crypto.createHash('sha256').update(x).digest('hex');
const HASH=/^[a-f0-9]{64}$/;

export function resolveCefrCheckpoint(stateDir,repoRoot){
 if(!path.isAbsolute(stateDir)||!path.isAbsolute(repoRoot))
  throw Error('CEFR roots must be absolute');
 const repository=fs.realpathSync(repoRoot);
 const state=fs.realpathSync(stateDir);
 if(state===repository||state.startsWith(repository+path.sep)||
    repository.startsWith(state+path.sep))
  throw Error('Developer CRE1 state must be outside the repository tree');
 if(!fs.lstatSync(stateDir).isDirectory()||fs.lstatSync(stateDir).isSymbolicLink())
  throw Error('Unsafe developer state directory');
 const directory=path.join(state,'dev-cre1');
 if(!fs.existsSync(directory))fs.mkdirSync(directory,{mode:0o700});
 if(!fs.lstatSync(directory).isDirectory()||fs.lstatSync(directory).isSymbolicLink())
  throw Error('Unsafe developer checkpoint directory');
 return path.join(directory,DEV_CRE1_FILENAME);
}

function pathEntryExists(file){
 try{fs.lstatSync(file);return true;}
 catch(error){if(error?.code==='ENOENT')return false;throw error;}
}

/** Read only an ordinary, bounded file under a no-follow descriptor.
 * Recovery may read corrupted model bytes to verify their exact SHA, but
 * regular model loading still requires a valid CRE1 snapshot.
 */
function readRawCheckpoint(file){
 const stat=fs.lstatSync(file);
 if(!stat.isFile()||stat.isSymbolicLink()||stat.size<1||stat.size>16*1024*1024)
  throw Error('Unsafe developer CRE1 checkpoint');
 const fd=fs.openSync(file,fs.constants.O_RDONLY|fs.constants.O_NOFOLLOW);
 try{
  const opened=fs.fstatSync(fd);
  if(opened.dev!==stat.dev||opened.ino!==stat.ino||
     !opened.isFile()||opened.size!==stat.size)
   throw Error('Developer checkpoint changed during open');
  const bytes=fs.readFileSync(fd);
  if(bytes.length!==opened.size)throw Error('Developer checkpoint changed during read');
  return bytes;
 }finally{fs.closeSync(fd);}
}
function readVerified(file){
 const bytes=readRawCheckpoint(file);
 RelationalEncoder.restore(bytes);
 return bytes;
}

export function loadCefrCheckpoint({stateDir,repoRoot}){
 const file=resolveCefrCheckpoint(stateDir,repoRoot);
 if(!pathEntryExists(file))return {file,bytes:null,sha256:null};
 const bytes=readVerified(file);
 return {file,bytes,sha256:sha256(bytes)};
}

/** No learning is persisted unless the caller supplies a promoted candidate
 * and the unchanged SHA of the currently stored checkpoint.
 * This lock serializes cooperating workers; directory ACLs remain mandatory.
 */
export function promoteCefrCheckpoint({stateDir,repoRoot,promotion,expectedSha256=null}){
 if(promotion?.accepted!==true||!Buffer.isBuffer(promotion.activeBytes)||
    !HASH.test(promotion.activeSha256??'')||
    sha256(promotion.activeBytes)!==promotion.activeSha256)
  throw Error('Refusing unapproved developer CRE1 promotion');
 RelationalEncoder.restore(promotion.activeBytes);
 const file=resolveCefrCheckpoint(stateDir,repoRoot);
 const lock=file+'.lock';
 let lockFd,temporary=null;
 try{
  lockFd=fs.openSync(lock,fs.constants.O_WRONLY|fs.constants.O_CREAT|fs.constants.O_EXCL,0o600);
  const old=pathEntryExists(file)?readVerified(file):null;
  const current=old==null?null:sha256(old);
  if(current!==expectedSha256)throw Error('Developer CRE1 checkpoint changed concurrently');
  // Persist a verified last-known-good snapshot BEFORE changing the active model.
  // The prior active generation can then be restored after a bad promotion.
  if(old!==null){
   const lkg=file+'.lkg';
   if(pathEntryExists(lkg))readVerified(lkg);
   const backupTmp=lkg+'.tmp-'+crypto.randomBytes(8).toString('hex');
   let backupFd;
   try{
    backupFd=fs.openSync(backupTmp,fs.constants.O_WRONLY|fs.constants.O_CREAT|fs.constants.O_EXCL,0o600);
    fs.writeFileSync(backupFd,old);
    fs.fsyncSync(backupFd);
   }finally{if(backupFd!==undefined)fs.closeSync(backupFd);}
   try{fs.renameSync(backupTmp,lkg);}
   catch(error){fs.rmSync(backupTmp,{force:true});throw error;}
  }
  temporary=file+'.tmp-'+crypto.randomBytes(8).toString('hex');
  const fd=fs.openSync(temporary,fs.constants.O_WRONLY|fs.constants.O_CREAT|fs.constants.O_EXCL,0o600);
  try{fs.writeFileSync(fd,promotion.activeBytes);fs.fsyncSync(fd);}
  finally{fs.closeSync(fd);}
  fs.renameSync(temporary,file);
  temporary=null;
  return {file,sha256:promotion.activeSha256};
 }finally{
  if(temporary)fs.rmSync(temporary,{force:true});
  if(lockFd!==undefined){fs.closeSync(lockFd);fs.rmSync(lock,{force:true});}
 }
}


/** Explicit operator rollback: restore only a validated LKG snapshot under the
 * same exclusive lock and a byte-exact expected active model hash.
 * Never silently recover a different or concurrently updated generation.
 */
export function restoreCefrLastKnownGood({stateDir,repoRoot,expectedSha256}){
 if(!HASH.test(expectedSha256??''))throw Error('INVALID_CEFR_ROLLBACK_HASH');
 const file=resolveCefrCheckpoint(stateDir,repoRoot);
 const lock=file+'.lock';
 let fd,tmp=null;
 try{
  fd=fs.openSync(lock,fs.constants.O_WRONLY|fs.constants.O_CREAT|fs.constants.O_EXCL,0o600);
  const previous=readVerified(file+'.lkg');
  if(!pathEntryExists(file))throw Error('Cannot roll back a missing active checkpoint');
  const current=readRawCheckpoint(file);
  if(sha256(current)!==expectedSha256)
   throw Error('CEFR_ROLLBACK_EXPECTED_SHA_MISMATCH');
  tmp=file+'.restore-'+crypto.randomBytes(8).toString('hex');
  const out=fs.openSync(tmp,fs.constants.O_WRONLY|fs.constants.O_CREAT|fs.constants.O_EXCL,0o600);
  try{fs.writeFileSync(out,previous);fs.fsyncSync(out);}
  finally{fs.closeSync(out);}
  fs.renameSync(tmp,file);
  tmp=null;
  return {file,sha256:sha256(previous),restored:true};
 }finally{
  if(tmp)fs.rmSync(tmp,{force:true});
  if(fd!==undefined){fs.closeSync(fd);fs.rmSync(lock,{force:true});}
 }
}
