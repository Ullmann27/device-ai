#!/usr/bin/env node
/**
 * Stage a previously owner-signed Continuum APK for the existing Render HTTPS
 * update channel. This program never signs, uploads, installs or publishes.
 * It is a fixed step for the future approved local release worker.
 * Usage: node scripts/continuum-owner-release-stage.mjs <signed-apk> <exact-source-sha>
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {execFileSync, spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const ROOT=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const HOST='continuum-owner-v23-release-preflight.onrender.com';
const ORIGINAL='6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23';
const err=m=>{throw Error('CONTINUUM_STAGE_REJECTED: '+m);};
const SHA=/^[a-f0-9]{40}$/;
const HASH=/^[a-f0-9]{64}$/;
function git(...args) {
  return execFileSync('git',args,{cwd:ROOT,encoding:'utf8',maxBuffer:2048*1024,timeout:15000}).trim();
}
function sha256(file) {
  const hash=createHash('sha256'),fd=fs.openSync(file,'r');
  try {
    const block=Buffer.allocUnsafe(1024*1024);
    while(true){const read=fs.readSync(fd,block,0,block.length,null);if(!read)break;hash.update(block.subarray(0,read));}
  }finally{fs.closeSync(fd);}
  return hash.digest('hex');
}
function getReceipt(file){
  const out=new Map();
  for(const line of fs.readFileSync(file,'utf8').split(/\r?\n/)){
    const idx=line.indexOf('=');
    if(idx>0&&!out.has(line.slice(0,idx)))out.set(line.slice(0,idx),line.slice(idx+1));
  }
  return out;
}
function run(apk,head) {
  if(process.argv.length!==4)err('Usage: <already-signed-apk> <exact-commit-sha>');
  if(!SHA.test(head))err('INVALID_SOURCE_SHA');
  if(!(process.env.PREFIX||'').startsWith('/data/data/com.termux/files/usr'))err('FOLD7_TERMUX_REQUIRED');
  if(git('rev-parse','HEAD')!==head)err('UNREVIEWED_SOURCE_SHA');
  if(git('status','--porcelain','--untracked-files=normal')!=='')err('DIRTY_SOURCE');
  const provided=path.resolve(apk);
  if(fs.lstatSync(provided).isSymbolicLink())err('APK_SYMLINK');
  const absolute=fs.realpathSync(provided);
  const meta=fs.statSync(absolute);
  if(!meta.isFile()||meta.size<1_000_000||meta.size>2_000_000_000)err('APK_BYTES');
  const digest=sha256(absolute);
  if(!HASH.test(digest))err('APK_DIGEST');
  const name='Continuum-Lokal-'+digest+'.apk';
  const url='https://'+HOST+'/continuum-local/'+name;
  const stagingRoot=path.join(os.homedir(),'.local','state','device-ai-continuum','owner-publication-staging');
  fs.mkdirSync(stagingRoot,{recursive:true,mode:0o700});
  if(fs.lstatSync(stagingRoot).isSymbolicLink())err('STAGING_SYMLINK');
  fs.chmodSync(stagingRoot,0o700);
  const temporary=fs.mkdtempSync(path.join(stagingRoot,'pending-'));
  let completed=false;
  try{
    fs.chmodSync(temporary,0o700);
    const inputManifest=path.join(temporary,'manifest.json');
    const verifier=spawnSync(process.execPath,
      [path.join(ROOT,'scripts','continuum-owner-update-manifest.mjs'),absolute,url,head,inputManifest],
      {cwd:ROOT,encoding:'utf8',timeout:60000,maxBuffer:256*1024,env:process.env,shell:false});
    if(verifier.status!==0)err('ORIGINAL_CERT_OR_APK_PRECHECK_FAILED: '+(verifier.stderr||'').slice(0,180));
    const manifest=JSON.parse(fs.readFileSync(inputManifest,'utf8'));
    if(manifest.sourceCommit!==head||manifest.apkSha256!==digest||
      manifest.apkUrl!==url||manifest.packageName!=='at.heinz.deviceai.continuumlocal'||
      manifest.signerSha256!==ORIGINAL||manifest.channel!=='OWNER_RELEASE'||
      manifest.approved!==true||manifest.byteLength!==meta.size)err('MANIFEST_MISMATCH');
    const receipt=path.join(os.homedir(),'.local','state','device-ai-continuum',
      'standalone-release','BUILD-INFO-'+head+'-'+manifest.versionCode+'.txt');
    if(fs.lstatSync(receipt).isSymbolicLink())err('RECEIPT_SYMLINK');
    const evidence=getReceipt(receipt);
    if(evidence.get('source_sha')!==head||evidence.get('apk_sha256')!==digest||
      evidence.get('signer_sha256')!==ORIGINAL||
      evidence.get('version_code')!==String(manifest.versionCode))err('LOCAL_BUILD_RECEIPT_MISMATCH');
    const target=path.join(stagingRoot,head+'-v'+manifest.versionCode);
    if(fs.existsSync(target))err('STAGE_ALREADY_EXISTS');
    const publicDir=path.join(temporary,'continuum-local');
    fs.mkdirSync(publicDir,{mode:0o700});
    const copied=path.join(publicDir,name);
    fs.copyFileSync(absolute,copied,fs.constants.COPYFILE_EXCL);
    if(sha256(copied)!==digest||fs.statSync(copied).size!==meta.size)err('COPY_CORRUPTED');
    fs.renameSync(inputManifest,path.join(publicDir,'latest.json'));
    fs.writeFileSync(path.join(temporary,'STATUS.json'),JSON.stringify({
      status:'STAGED_NOT_PUBLISHED',sourceCommit:head,versionCode:manifest.versionCode,
      apkSha256:digest,signerSha256:ORIGINAL,packageName:manifest.packageName
    },null,2)+'\n',{flag:'wx',mode:0o600});
    fs.renameSync(temporary,target);
    completed=true;
    process.stdout.write('CONTINUUM_SIGNED_RELEASE_STAGED_NOT_PUBLISHED='+target+'\n');
  }finally{
    if(!completed)fs.rmSync(temporary,{recursive:true,force:true});
  }
}
try{run(process.argv[2],process.argv[3]);}catch(e){
  process.stderr.write((e.message||String(e)).slice(0,350)+'\n');process.exitCode=69;
}
