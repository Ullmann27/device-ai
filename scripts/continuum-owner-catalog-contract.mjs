#!/usr/bin/env node
/**
 * Validates the public latest.json against the *installed application's*
 * strict Kotlin update-catalog contract before Render stages the release.
 * Release metadata cannot weaken package/signature/HTTPS verification.
 */
import {readFileSync} from 'node:fs';
import {resolve, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';

const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const host='continuum-owner-v23-release-preflight.onrender.com';
const pkg='at.heinz.deviceai.continuumlocal';
const signer='6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23';
const sha40=/^[a-f0-9]{40}$/, sha64=/^[a-f0-9]{64}$/;
const fieldSource=resolve(root,'app/src/main/java/at/heinz/deviceai/intelligence/ContinuumOwnerUpdateCatalog.kt');
const policySource=resolve(root,'app/src/main/java/at/heinz/deviceai/intelligence/ContinuumSignedUpdatePolicy.kt');
const err=(why)=>{throw Error('CONTINUUM_OWNER_CATALOG_BLOCKED: '+why);};

export function verifyOwnerCatalog(raw,parserKotlin,policyKotlin) {
  if(typeof raw!=='string'||Buffer.byteLength(raw,'utf8')<1||Buffer.byteLength(raw,'utf8')>8192)
    err('MANIFEST_SIZE');
  if(!parserKotlin.includes('const val MAX_JSON_BYTES = 8192')) err('UNREVIEWED_PARSER_LIMIT');
  const allowed=parserKotlin.match(/private val allowedKeys = setOf\(([\s\S]*?)\)/);
  if(!allowed)err('PARSER_SCHEMA_NOT_FOUND');
  const keys=[...allowed[1].matchAll(/"([A-Za-z][A-Za-z0-9]*)"/g)].map(x=>x[1]);
  if(keys.length!==12||new Set(keys).size!==12)err('UNEXPECTED_PARSER_SCHEMA');
  if(!policyKotlin.includes('release.channel != "OWNER_RELEASE"')||
     !policyKotlin.includes('!release.explicitlyApproved')||
     !policyKotlin.includes('ORIGINAL_SIGNER_SHA256'))err('UNREVIEWED_POLICY');
  let o;
  try {o=JSON.parse(raw);} catch {err('INVALID_JSON');}
  if(o===null||typeof o!=='object'||Array.isArray(o))err('INVALID_OBJECT');
  const actual=Object.keys(o).sort();
  if(JSON.stringify(actual)!==JSON.stringify(keys.sort()))err('SCHEMA_KEYS_DIFFER_FROM_ANDROID');
  if(o.schema!==1||!Number.isInteger(o.schema))err('INVALID_SCHEMA');
  if(o.channel!=='OWNER_RELEASE'||o.approved!==true)err('RELEASE_NOT_OWNER_APPROVED');
  for(const k of ['channel','packageName','versionName','apkUrl','apkSha256','sourceCommit','signerSha256','notes'])
    if(typeof o[k]!=='string')err('INVALID_FIELD_TYPE_'+k);
  if(o.packageName!==pkg||o.signerSha256!==signer)err('ORIGINAL_PACKAGE_OR_SIGNER_MISMATCH');
  if(!Number.isSafeInteger(o.versionCode)||o.versionCode<29||o.versionCode>9999||
     o.versionName!=='0.3.'+(o.versionCode-1))err('VERSION_MISMATCH');
  if(!Number.isSafeInteger(o.byteLength)||o.byteLength<1_000_000||o.byteLength>2_000_000_000)
    err('INVALID_APK_SIZE');
  if(!sha40.test(o.sourceCommit)||!sha64.test(o.apkSha256))err('INVALID_SHA');
  if(o.notes.length>800||[...o.notes].some(ch=>ch.codePointAt(0)<32&&ch!=='\n'))
    err('INVALID_NOTES');
  const expected='https://'+host+'/continuum-local/Continuum-Lokal-'+o.apkSha256+'.apk';
  if(o.apkUrl!==expected)err('UNTRUSTED_OR_MUTABLE_DOWNLOAD_URL');
  return Object.freeze({sourceCommit:o.sourceCommit,versionCode:o.versionCode,apkSha256:o.apkSha256,byteLength:o.byteLength});
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)){
  try{
    if(process.argv.length!==3)err('USAGE_EXPECTS_ONE_MANIFEST_PATH');
    const outcome=verifyOwnerCatalog(readFileSync(resolve(process.argv[2]),'utf8'),
      readFileSync(fieldSource,'utf8'),readFileSync(policySource,'utf8'));
    process.stdout.write('CONTINUUM_OWNER_CATALOG_ANDROID_COMPATIBLE source='+outcome.sourceCommit+
      ' versionCode='+outcome.versionCode+' apkSha256='+outcome.apkSha256+'\n');
  }catch(e){process.stderr.write(String(e.message||e).slice(0,350)+'\n');process.exitCode=69;}
}
