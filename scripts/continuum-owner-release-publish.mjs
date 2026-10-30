#!/usr/bin/env node
/**
 * Owner-authorized publication of an ALREADY locally signed and staged APK.
 * Publishes only two files to the existing Render static site's Git branch.
 * Never signs, installs, merges main, or accesses the P12 password/private key.
 * The Render service has autoDeploy=off; success here is NOT proof of availability.
 *
 * Usage:
 * node scripts/continuum-owner-release-publish.mjs <staging-dir> <exact-source-sha> --check
 * CONTINUUM_OWNER_RELEASE_PUBLISH_APPROVED_SHA=<exact-source-sha> \
 *   node scripts/continuum-owner-release-publish.mjs <staging-dir> <exact-source-sha> --publish
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {execFileSync,spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const ROOT=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const TARGET_BRANCH='release/continuum-owner-v23-candidate-20261025';
const PUBLIC_FOLDER='owner-v23-release-preflight/continuum-local';
const HOST='continuum-owner-v23-release-preflight.onrender.com';
const PACKAGE='at.heinz.deviceai.continuumlocal';
const CERT='6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23';
const SHA40=/^[a-f0-9]{40}$/;
const SHA64=/^[a-f0-9]{64}$/;
const fail=message=>{throw Error('CONTINUUM_PUBLISH_BLOCKED: '+message);};

function git(cwd,...args) {
  return execFileSync('git',args,{cwd,encoding:'utf8',timeout:120000,
    maxBuffer:1024*1024,env:{...process.env,GIT_TERMINAL_PROMPT:'0'}}).trim();
}
function regular(file) {
  const st=fs.lstatSync(file);
  if(!st.isFile()||st.isSymbolicLink())fail('UNSAFE_STAGED_FILE');
  return st;
}
function sha256(file) {
  const digest=createHash('sha256');
  const fd=fs.openSync(file,'r');
  try {
    const buf=Buffer.allocUnsafe(1024*1024);
    for(;;){const n=fs.readSync(fd,buf,0,buf.length,null);if(!n)break;digest.update(buf.subarray(0,n));}
  } finally {fs.closeSync(fd);}
  return digest.digest('hex');
}
function readJson(file) {
  regular(file);
  return JSON.parse(fs.readFileSync(file,'utf8'));
}
function assertSource(sha) {
  if(!SHA40.test(sha))fail('INVALID_SOURCE_SHA');
  if(!(process.env.PREFIX||'').startsWith('/data/data/com.termux/files/usr'))
    fail('FOLD7_TERMUX_REQUIRED');
  if(git(ROOT,'rev-parse','--show-toplevel')!==ROOT)fail('INCORRECT_GIT_ROOT');
  if(git(ROOT,'rev-parse','HEAD')!==sha)fail('SOURCE_HEAD_CHANGED');
  if(git(ROOT,'status','--porcelain','--untracked-files=normal'))fail('DIRTY_SOURCE');
  const branch=git(ROOT,'symbolic-ref','--quiet','--short','HEAD');
  if(/^(?:main|master|release|production|prod)(?:$|\/)/i.test(branch))
    fail('PROTECTED_SOURCE_BRANCH');
  const origin=git(ROOT,'remote','get-url','origin');
  if(!new Set([
    'https://github.com/Ullmann27/device-ai',
    'https://github.com/Ullmann27/device-ai.git',
    'git@github.com:Ullmann27/device-ai.git',
    'ssh://git@github.com/Ullmann27/device-ai.git'
  ]).has(origin))fail('UNTRUSTED_ORIGIN');
}
function verifyStaged(dir,sha,tmp) {
  const stagingRoot=path.resolve(os.homedir(),
    '.local/state/device-ai-continuum/owner-publication-staging');
  const staging=path.resolve(dir);
  if(!staging.startsWith(stagingRoot+path.sep) ||
     fs.lstatSync(staging).isSymbolicLink() ||
     !fs.lstatSync(staging).isDirectory())fail('UNTRUSTED_STAGING_DIRECTORY');
  const manifestPath=path.join(staging,'continuum-local/latest.json');
  const statusPath=path.join(staging,'STATUS.json');
  const manifest=readJson(manifestPath);
  const status=readJson(statusPath);
  const keys=['schema','channel','packageName','versionCode','versionName',
    'apkUrl','apkSha256','byteLength','sourceCommit','signerSha256','approved','notes'];
  if(Object.keys(manifest).sort().join(',')!==keys.sort().join(',') ||
     manifest.schema!==1 || manifest.channel!=='OWNER_RELEASE' ||
     manifest.packageName!==PACKAGE || manifest.signerSha256!==CERT ||
     manifest.approved!==true || manifest.sourceCommit!==sha ||
     !Number.isInteger(manifest.versionCode) ||
     manifest.versionCode<29 || manifest.versionCode>9999 ||
     manifest.versionName!=='0.3.'+(manifest.versionCode-1) ||
     !Number.isSafeInteger(manifest.byteLength) ||
     !SHA64.test(manifest.apkSha256) ||
     typeof manifest.notes!=='string'||manifest.notes.length>800)fail('INVALID_RELEASE_MANIFEST');
  const fileName='Continuum-Lokal-'+manifest.apkSha256+'.apk';
  if(manifest.apkUrl!=='https://'+HOST+'/continuum-local/'+fileName)
    fail('WRONG_UPDATE_CHANNEL');
  if(path.basename(staging)!==sha+'-v'+manifest.versionCode ||
     status.status!=='STAGED_NOT_PUBLISHED' ||
     status.sourceCommit!==sha ||
     status.versionCode!==manifest.versionCode ||
     status.apkSha256!==manifest.apkSha256 ||
     status.packageName!==PACKAGE || status.signerSha256!==CERT)
    fail('STAGING_RECEIPT_MISMATCH');
  const apk=path.join(staging,'continuum-local',fileName);
  const size=regular(apk).size;
  // GitHub enforces a 100 MiB blob limit. Fail before editing the release branch.
  if(size<1_000_000 || size>90_000_000 || size!==manifest.byteLength)
    fail('APKS_OVER_GITHUB_FILE_LIMIT_OR_WRONG_SIZE');
  if(sha256(apk)!==manifest.apkSha256)fail('STAGED_APK_HASH_MISMATCH');

  // Re-verify original v2 APK certificate, app/package/version and immutable
  // update URL using the existing local Android tools, not the JSON alone.
  const verified=path.join(tmp,'reverified-latest.json');
  const result=spawnSync(process.execPath,[
    path.join(ROOT,'scripts/continuum-owner-update-manifest.mjs'),
    apk,manifest.apkUrl,sha,verified
  ],{cwd:ROOT,encoding:'utf8',timeout:120000,maxBuffer:256*1024,shell:false});
  if(result.status!==0)fail('SIGNER_PACKAGE_OR_RELEASE_RECHECK_FAILED: '+
    (result.stderr||'').slice(0,140));
  const inspected=readJson(verified);
  if(JSON.stringify(inspected)!==JSON.stringify(manifest))
    fail('STAGED_MANIFEST_DIFFERS_FROM_VERIFIED_APK');
  return {manifest,apk,manifestPath,fileName};
}
function run(argv) {
  if(argv.length!==3||!['--check','--publish'].includes(argv[2]))
    fail('Usage: <staging-dir> <exact-source-sha> --check|--publish');
  const [staging,sha,action]=argv;
  assertSource(sha);
  if(action==='--publish' &&
     process.env.CONTINUUM_OWNER_RELEASE_PUBLISH_APPROVED_SHA!==sha)
    fail('EXACT_OWNER_PUBLISH_APPROVAL_REQUIRED');
  const tmp=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-owner-publish-'));
  let checkout=null;
  try {
    fs.chmodSync(tmp,0o700);
    const {manifest,apk,manifestPath,fileName}=verifyStaged(staging,sha,tmp);
    if(action==='--check') {
      process.stdout.write('OWNER_APK_VERIFIED_NOT_PUBLISHED source='+sha+
        ' versionCode='+manifest.versionCode+'\n');
      return;
    }

    // Only the dedicated existing Render publication branch can be changed.
    // A rejected non-fast-forward push fails closed; never force-push.
    git(ROOT,'fetch','--no-tags','origin',
      'refs/heads/'+TARGET_BRANCH+':refs/remotes/origin/'+TARGET_BRANCH);
    const base=git(ROOT,'rev-parse','refs/remotes/origin/'+TARGET_BRANCH);
    if(!SHA40.test(base))fail('INVALID_PUBLISH_BRANCH_HEAD');
    checkout=path.join(tmp,'release-worktree');
    git(ROOT,'worktree','add','--detach',checkout,base);
    const publicDir=path.join(checkout,PUBLIC_FOLDER);
    const former=path.join(publicDir,'latest.json');
    const old=readJson(former);
    if(old.schema!==1 ||
       !(old.available===false && Object.keys(old).length===2) &&
       !(old.approved===true && Number.isInteger(old.versionCode) &&
         old.versionCode<manifest.versionCode && old.signerSha256===CERT &&
         old.packageName===PACKAGE))
      fail('UNTRUSTED_OR_NOT_NEWER_RELEASE_CHANNEL');
    const publishedApk=path.join(publicDir,fileName);
    if(fs.existsSync(publishedApk))fail('IMMUTABLE_APK_ALREADY_EXISTS');
    fs.copyFileSync(apk,publishedApk,fs.constants.COPYFILE_EXCL);
    if(sha256(publishedApk)!==manifest.apkSha256)fail('PUBLISH_COPY_HASH_MISMATCH');
    const tempManifest=path.join(publicDir,'.latest.json.pending');
    fs.copyFileSync(manifestPath,tempManifest,fs.constants.COPYFILE_EXCL);
    fs.renameSync(tempManifest,former);
    const apkRel=PUBLIC_FOLDER+'/'+fileName;
    const manifestRel=PUBLIC_FOLDER+'/latest.json';
    git(checkout,'add','--',apkRel,manifestRel);
    const stagedFiles=git(checkout,'diff','--cached','--name-only').split('\n').sort();
    if(stagedFiles.join('\n')!==[apkRel,manifestRel].sort().join('\n'))
      fail('UNEXPECTED_PUBLISH_FILES');
    git(checkout,'commit','-m',
      'release(owner): publish originally signed Continuum Lokal '+manifest.versionName);
    git(checkout,'push','origin','HEAD:refs/heads/'+TARGET_BRANCH);
    process.stdout.write('OWNER_RELEASE_PUSHED_AWAITING_RENDER_DEPLOY versionCode='+
      manifest.versionCode+' sha256='+manifest.apkSha256+'\n');
  } finally {
    if(checkout) {
      try {git(ROOT,'worktree','remove','--force',checkout);} catch { /* remove temp only */ }
    }
    fs.rmSync(tmp,{recursive:true,force:true});
  }
}
try{run(process.argv.slice(2));}catch(e){
  process.stderr.write((e.message||String(e)).slice(0,300)+'\n');
  process.exitCode=69;
}
