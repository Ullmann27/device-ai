/** EXP-02: provenance-bound, non-authoritative local sandbox run evidence.
 * All candidate output remains untrusted; this is NOT a repair verdict.
 * Usage: node continuum-isolation-receipt.mjs /absolute/stage /absolute/stage/candidate.mjs
 */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const runner=fileURLToPath(new URL('./continuum-isolated-node.sh',import.meta.url));
const sha=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
const MAX_SOURCE=16_384;

export function runIsolatedReceipt({stage,candidate,timeoutMs=20_000}={}){
  const started=process.hrtime.bigint();
  const id=crypto.randomBytes(16).toString('hex');
  let sourceDigest=null,runnerDigest=null;
  let result={status:'REJECTED',reason:'INVALID_INPUT',exitCode:null,signal:null,
    stdoutSha256:null,stderrSha256:null,stdoutBytes:0,stderrBytes:0};
  try{
    if(!Number.isSafeInteger(timeoutMs)||timeoutMs<1000||timeoutMs>30_000 ||
       typeof stage!=='string'||typeof candidate!=='string'||
       !path.isAbsolute(stage)||!path.isAbsolute(candidate))throw Error('INVALID_ARGUMENTS');
    const dirInfo=fs.lstatSync(stage),srcInfo=fs.lstatSync(candidate);
    if(!dirInfo.isDirectory()||dirInfo.isSymbolicLink()||!srcInfo.isFile()||srcInfo.isSymbolicLink()||
       srcInfo.size<1||srcInfo.size>MAX_SOURCE)throw Error('UNSAFE_INPUT');
    const stageReal=fs.realpathSync(stage),fileReal=fs.realpathSync(candidate);
    if(path.dirname(fileReal)!==stageReal||path.extname(fileReal)!=='.mjs'||
       dirInfo.uid!==process.getuid()||srcInfo.uid!==process.getuid())
       throw Error('UNSAFE_SCOPE');
    const source=fs.readFileSync(fileReal);
    if(source.length!==srcInfo.size)throw Error('SOURCE_CHANGED');
    sourceDigest=sha(source);
    runnerDigest=sha(fs.readFileSync(runner));
    const env={PATH:path.dirname(process.execPath)+':/usr/sbin:/usr/bin:/sbin:/bin',HOME:'/nonexistent',
      LC_ALL:'C',CONTINUUM_ISOLATION_STAGE:stageReal};
    const child=spawnSync('/bin/bash',[runner,fileReal],{
      shell:false,encoding:'buffer',timeout:timeoutMs,maxBuffer:64*1024,env,
      stdio:['ignore','pipe','pipe']
    });
    const out=Buffer.isBuffer(child.stdout)?child.stdout:Buffer.alloc(0);
    const err=Buffer.isBuffer(child.stderr)?child.stderr:Buffer.alloc(0);
    const changed=sha(fs.readFileSync(fileReal))!==sourceDigest||
      sha(fs.readFileSync(runner))!==runnerDigest;
    const exitCode=Number.isSafeInteger(child.status)?child.status:null;
    const signal=typeof child.signal==='string'?child.signal.slice(0,16):null;
    result={status:changed?'REJECTED':exitCode===0&&!child.error&&!signal?
        'EXECUTED_UNTRUSTED':'UNVERIFIED_FAILURE',
      reason:changed?'SOURCE_CHANGED':child.error?'SPAWN_OR_LIMIT_ERROR':
        exitCode===0&&!signal?'EXIT_ZERO_UNTRUSTED':'NONZERO_OR_SIGNAL',
      exitCode,signal,stdoutSha256:sha(out),stderrSha256:sha(err),
      stdoutBytes:out.length,stderrBytes:err.length};
  }catch(error){
    result.reason=['INVALID_ARGUMENTS','UNSAFE_INPUT','UNSAFE_SCOPE','SOURCE_CHANGED'].includes(error?.message)?
      error.message:'IO_OR_CAPABILITY_FAILURE';
  }
  const elapsedMs=Number((process.hrtime.bigint()-started)/1_000_000n);
  const receipt={schema:'continuum-isolation-receipt-v1',runId:id,
    harnessSha256:runnerDigest,candidateSha256:sourceDigest,
    ...result,elapsedMs,verified:false,reviewRequired:true,autoApply:false,
    platform:process.platform,independentAttestation:false};
  return Object.freeze({...receipt,recordSha256:sha(Buffer.from(JSON.stringify(receipt)))});
}

if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
  const receipt=runIsolatedReceipt({stage:process.argv[2],candidate:process.argv[3]});
  process.stdout.write(JSON.stringify(receipt)+'\n');
  if(receipt.status!=='EXECUTED_UNTRUSTED')process.exitCode=1;
}
