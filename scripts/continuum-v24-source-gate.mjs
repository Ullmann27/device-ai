#!/usr/bin/env node
// Owner v24 / 0.3.23 candidate source attestation. Never signs; fails closed.
import {execFileSync} from 'node:child_process';
import {readFileSync,lstatSync} from 'node:fs';
import {join,resolve,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const SOURCE='1ea194134ee540e99e2745dda439e440d245a2ac';
const MANIFEST='docs/release/continuum-0323-source-proposal-20261026.json';
const MANIFEST_BLOB='2d617e27eec2923437ed1e8a1f1cf73eac4aa7b9';
const PACKAGE='at.heinz.deviceai.mentorpaired';
const CERT='2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc';
const CODE=24, NAME='0.3.23',COUNT=1027;
const fail=x=>{process.stderr.write('OWNER_V24_BLOCKED: '+x+'\n');process.exit(69);};
const git=(...args)=>execFileSync('git',args,{cwd:root,encoding:'utf8',timeout:25000,stdio:['ignore','pipe','pipe']}).trim();
const included=p=>p.startsWith('app/src/')||p==='app/build.gradle.kts'||
  p==='app/proguard-rules.pro'||p.startsWith('backend/')||
  p.startsWith('.github/continuum/')||p.startsWith('scripts/')||
  p.startsWith('gradle/')||
  ['build.gradle.kts','settings.gradle.kts','gradle.properties'].includes(p);
const builderPaths=new Set(['scripts/render-mentor-preview.sh','scripts/render-mentor-owner-update-v24.sh','scripts/continuum-v24-source-gate.mjs','scripts/continuum-v24-owner-release.test.mjs']);
try{
  // Owner must explicitly approve the frozen source, inventory and exact
  // rebuilt release commit. A source proposal alone has no execution rights.
  if(process.env.CONTINUUM_OWNER_V24_APPROVED_SOURCE_SHA!==SOURCE||
     process.env.CONTINUUM_OWNER_V24_APPROVED_MANIFEST_BLOB!==MANIFEST_BLOB)
     fail('Owner source/inventory approval absent or mismatched');
  const head=git('rev-parse','HEAD');
  if(!/^[a-f0-9]{40}$/.test(head)||
     process.env.CONTINUUM_RELEASE_SHA!==head||
     process.env.CONTINUUM_OWNER_V24_APPROVED_SHA!==head)
     fail('Owner release builder commit approval absent or mismatched');
  if(git('hash-object','--',MANIFEST)!==MANIFEST_BLOB)
    fail('Approved inventory bytes changed');
  const j=JSON.parse(readFileSync(join(root,MANIFEST),'utf8'));
  if(j.schemaVersion!==1||j.state!=='SOURCE_PROPOSAL_NOT_OWNER_APPROVAL'||
     j.sourceHead!==SOURCE||j.package!==PACKAGE||j.versionCode!==CODE||
     j.versionName!==NAME||j.certificateSha256!==CERT||
     !Array.isArray(j.files)||j.files.length!==COUNT)
     fail('Approved source manifest schema/identity mismatch');
  const seen=new Set();
  for(const item of j.files){
    const p=item?.path,blob=item?.blob;
    if(typeof p!=='string'||!/^[A-Za-z0-9_.\/-]+$/.test(p)||
       p.startsWith('/')||p.split('/').includes('..')||
       !included(p)||seen.has(p)||!/^([a-f0-9]{40})$/.test(blob??''))
       fail('Invalid, duplicate or out-of-scope source entry');
    seen.add(p);
    if(builderPaths.has(p))continue; // also pinned by exact approved builder HEAD.
    let location=root;
    for(const [i,part] of p.split('/').entries()){
      location=join(location,part);
      const st=lstatSync(location);
      if(st.isSymbolicLink()||(i<p.split('/').length-1?!st.isDirectory():!st.isFile()))
        fail('Source redirection '+p);
    }
    if(git('hash-object','--',p)!==blob)fail('Altered approved source '+p);
  }
  const sourcePaths=git('ls-files','-z','--','app/src','app/build.gradle.kts',
    'app/proguard-rules.pro','backend','.github/continuum','scripts',
    'gradle','build.gradle.kts','settings.gradle.kts','gradle.properties')
      .split('\u0000').filter(Boolean).filter(included);
  for(const p of sourcePaths)
    if(!seen.has(p)&&!builderPaths.has(p))fail('New unattested source '+p);
  for(const p of seen)if(!sourcePaths.includes(p))fail('Missing approved source '+p);
  if(git('-c','core.fileMode=false','diff','--name-only','HEAD','--'))
    fail('Tracked contents differ from immutable release commit');
  if(git('ls-files','--others','--exclude-standard','--','app/src','backend',
      'scripts','.github/continuum'))
    fail('Untracked executable source');
  console.log('OWNER_V24_SOURCE_VERIFIED package='+PACKAGE+
    ' versionCode='+CODE+' files='+COUNT+' source='+SOURCE+' builder='+head);
}catch(e){fail(e instanceof Error?e.message:'Unknown source audit error');}
