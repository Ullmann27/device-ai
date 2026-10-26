/**
 * Explicit, opt-in local scorecard export for the trusted Node worker only.
 * Stores a redacted ANALYSIS_ONLY scorecard, never source text, owner memory,
 * candidate execution traces, credentials, verified receipts or model weights.
 * Does not auto-run, alter the APK or grant any action.
 */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {ensureCcc1StateOutsideSource} from './ccc1-path-policy.mjs';
import {buildCcc1BottleneckScorecard} from './ccc1-bottleneck-scorecard.mjs';

const MAX_BYTES=16384;
const sha256=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');

function ownerDirectory(dir){
  const stat=fs.lstatSync(dir);
  if(!stat.isDirectory()||stat.isSymbolicLink()||fs.realpathSync(dir)!==dir)
    throw Error('EVIDENCE_DIRECTORY_NOT_CANONICAL');
  if((stat.mode&0o077)!==0)throw Error('EVIDENCE_DIRECTORY_PERMISSIONS');
  if(typeof process.getuid==='function'&&stat.uid!==process.getuid())
    throw Error('EVIDENCE_DIRECTORY_NOT_OWNER_CONTROLLED');
  return dir;
}

/**
 * Owner must pre-create a private 0700 stateDir/research-scorecards directory,
 * outside the checked-out source. No directories are created in this function.
 * Payload is a non-attested observation, never execution authority.
 */
export function exportCcc1BottleneckScorecard({observation,projectRoot,stateDir}={}){
  const scorecard=buildCcc1BottleneckScorecard(observation);
  const canonicalState=ensureCcc1StateOutsideSource(projectRoot,stateDir);
  if(fs.realpathSync(stateDir)!==canonicalState)throw Error('STATE_DIR_SYMLINK');
  ownerDirectory(canonicalState);
  const evidenceDir=ownerDirectory(path.join(canonicalState,'research-scorecards'));
  const payload=JSON.stringify(scorecard);
  const envelope=Object.freeze({schema:'continuum-bottleneck-observation-export',
    version:1,classification:'NON_ATTESTED_ANALYSIS_ONLY',
    scorecard,scorecardSha256:sha256(Buffer.from(payload,'utf8'))});
  const bytes=Buffer.from(JSON.stringify(envelope)+'\n','utf8');
  if(bytes.length>MAX_BYTES)throw Error('EVIDENCE_TOO_LARGE');
  const name=scorecard.experimentId+'-'+scorecard.caseId+'-'+sha256(bytes).slice(0,24)+'.json';
  const filename=path.join(evidenceDir,name);
  if(path.dirname(filename)!==evidenceDir)throw Error('EVIDENCE_PATH_ESCAPE');
  const flags=fs.constants.O_WRONLY|fs.constants.O_CREAT|
      fs.constants.O_EXCL|fs.constants.O_NOFOLLOW;
  if(!Number.isSafeInteger(fs.constants.O_NOFOLLOW)||fs.constants.O_NOFOLLOW===0)
    throw Error('NOFOLLOW_NOT_SUPPORTED');
  let fd=null,wrote=false;
  try{
    fd=fs.openSync(filename,flags,0o600);
    let offset=0;
    while(offset<bytes.length){
      const n=fs.writeSync(fd,bytes,offset,bytes.length-offset);
      if(n<=0)throw Error('EVIDENCE_WRITE_FAILED');
      offset+=n;
    }
    fs.fsyncSync(fd);
    wrote=true;
  }finally{
    if(fd!==null)fs.closeSync(fd);
    if(!wrote&&fd!==null){
      try{fs.unlinkSync(filename);}catch{}
    }
  }
  return Object.freeze({status:'ANALYSIS_ONLY_WRITTEN',
    filename,byteLength:bytes.length,envelopeSha256:sha256(bytes),
    learningEligible:false,promotionEligible:false});
}
