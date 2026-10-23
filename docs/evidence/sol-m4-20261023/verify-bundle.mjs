// Audit this committed evidence bundle. No Gradle, installation, model or owner-key access.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import {getProjectPlan} from '../../../scripts/continuum-project-plan.mjs';
import {verifyOwnedProjectReceipt,readOwnedFile} from '../../../.github/continuum/owned-project-evidence.mjs';
import {runOwnedCommand} from '../../../scripts/continuum-owned-process.mjs';
const root=path.dirname(fileURLToPath(import.meta.url));
const sha=bytes=>createHash('sha256').update(bytes).digest('hex');
function executable(name) {
  for(const dir of (process.env.PATH||'').split(path.delimiter).filter(Boolean)) {
    const file=path.resolve(dir,name);
    try {fs.accessSync(file,fs.constants.X_OK);if(fs.statSync(file).isFile())return file;}catch{}
  }
  throw Error('Required SDK executable missing: '+name);
}
const record=process.argv.slice(2);
if(record.length>1||record.length===1&&record[0]!=='--record')throw Error('Usage: node verify-bundle.mjs [--record]');
const signer=executable('apksigner'),aapt=executable('aapt2');
for(const id of ['m4-notes','m4-counter']) {
  const saved=JSON.parse(readOwnedFile(root,id+'/receipt.json').bytes);
  const project=path.join(root,id,'source'),state=path.join(root,id,'state');
  // Only transport locations are rebased. The committed original receipt remains unchanged.
  const artifact=path.join(state,path.basename(saved.artifact));
  const receipt={...saved,project,artifact};
  const verdict=verifyOwnedProjectReceipt(receipt,{plan:getProjectPlan(id),sourceSha256:saved.sourceSha256,
    project,artifactRoot:state});
  if(verdict.verdict!=='CONFIRMED')throw Error(JSON.stringify({id,verdict}));
  const bytes=readOwnedFile(state,path.basename(artifact),64*1024*1024).bytes;
  const tmp=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-m4-independent-'));
  try {
    const staged=path.join(tmp,'verified.apk');fs.writeFileSync(staged,bytes,{flag:'wx',mode:0o600});
    const unchanged=()=>sha(readOwnedFile(tmp,'verified.apk',64*1024*1024).bytes)===saved.apkSha256;
    const cert=await runOwnedCommand(signer,['verify','--verbose','--print-certs',staged],{timeout:30000});
    if(!cert.ok||!unchanged()||
      /certificate SHA-256 digest:\s*([a-fA-F0-9]{64})/i.exec(cert.output)?.[1].toLowerCase()!==saved.signerSha256)
      throw Error('Independent signature check failed: '+id);
    const badging=await runOwnedCommand(aapt,['dump','badging',staged],{timeout:30000});
    if(!badging.ok||!unchanged()||/uses-permission:/.test(badging.output)||
      !badging.output.includes("package: name='"+saved.packageName+"' versionCode='1' versionName='0.1.0'"))
      throw Error('Independent package/permission check failed: '+id);
    if(sha(readOwnedFile(state,path.basename(artifact),64*1024*1024).bytes)!==saved.apkSha256)
      throw Error('Committed APK changed during independent check');
    const result={recipe:id,ccc1:verdict.verdict,independentSignature:true,independentPackage:true,
      noDeclaredPermissions:true,apkSha256:saved.apkSha256,signerSha256:saved.signerSha256,
      artifactBytes:bytes.length,installed:false,deviceVerified:false};
    if(record.length) {
      const dest=path.join(root,id,'independent');fs.mkdirSync(dest,{recursive:true});
      for(const [file,text] of [['apksigner.txt',cert.output],['badging.txt',badging.output],
        ['result.json',JSON.stringify(result,null,2)+'\n']])
        fs.writeFileSync(path.join(dest,file),text,{flag:'wx',mode:0o600});
    }
    console.log(JSON.stringify(result));
  } finally {fs.rmSync(tmp,{recursive:true,force:true});}
}
