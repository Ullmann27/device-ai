#!/usr/bin/env node
/**
 * Independent, read-only check of the existing Continuum Lokal Render release channel.
 * Never approves, signs, stages, publishes, installs or modifies an APK.
 * Verifies published bytes and metadata; the Android PackageInstaller and
 * ContinuumSignedUpdatePolicy still enforce the ORIGINAL signing certificate.
 */
import https from 'node:https';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';

export const CHANNEL_HOST = 'continuum-owner-v23-release-preflight.onrender.com';
export const CHANNEL_URL = 'https://' + CHANNEL_HOST + '/continuum-local/latest.json';
export const OWNER_PACKAGE = 'at.heinz.deviceai.continuumlocal';
export const OWNER_CERT = '6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23';
const KEYS = Object.freeze(['schema','channel','packageName','versionCode','versionName',
  'apkUrl','apkSha256','byteLength','sourceCommit','signerSha256','approved','notes']);
const HASH = /^[a-f0-9]{64}$/;
const COMMIT = /^[a-f0-9]{40}$/;
const MAX_MANIFEST_BYTES = 8192;
// Match the Android update policy.
const MAX_PUBLISHED_APK_BYTES = 2_000_000_000;

function fail(message) { throw new Error('CONTINUUM_RENDER_READBACK_FAILED: ' + message); }
function isExactKeys(record, keys) {
  return record !== null && typeof record === 'object' && !Array.isArray(record) &&
    Object.keys(record).length === keys.length && keys.every(k => Object.hasOwn(record,k));
}
function pinned(url) {
  if (url === CHANNEL_URL) return;
  if (typeof url !== 'string' || !/^https:\/\/continuum-owner-v23-release-preflight\.onrender\.com\/continuum-local\/Continuum-Lokal-[a-f0-9]{64}\.apk$/.test(url))
    fail('UNTRUSTED_REQUEST_URL');
}

/** HTTPS GET using only the hardcoded release host, without redirects. */
export function pinnedHttpsGet(url) {
  pinned(url);
  return new Promise((resolve,reject) => {
    const req=https.get(url,{
      headers:{'Accept-Encoding':'identity','Cache-Control':'no-cache'},
      timeout:25_000
    },res=>resolve({statusCode:res.statusCode,headers:res.headers,body:res}));
    req.once('error',reject);
    req.once('timeout',()=>req.destroy(new Error('HTTPS_TIMEOUT')));
  });
}

/** Streams the APK without storing it in memory; only manifests are buffered. */
async function receive(response, limit, bufferBody=false) {
  if (!response || response.statusCode !== 200) fail('HTTP_NOT_200');
  const encoding=response.headers?.['content-encoding'];
  if (encoding && encoding !== 'identity') fail('UNEXPECTED_CONTENT_ENCODING');
  const length=response.headers?.['content-length'];
  if (length !== undefined && (!/^(?:0|[1-9]\d*)$/.test(String(length)) ||
      !Number.isSafeInteger(Number(length)) || Number(length)>limit)) fail('INVALID_CONTENT_LENGTH');
  if (!response.body || typeof response.body[Symbol.asyncIterator] !== 'function')
    fail('INVALID_RESPONSE_STREAM');
  let count=0;
  const digest=createHash('sha256');
  const pieces=bufferBody?[]:null;
  for await (const part of response.body) {
    if (!Buffer.isBuffer(part) && typeof part !== 'string') fail('INVALID_RESPONSE_CHUNK');
    const bytes=Buffer.isBuffer(part)?part:Buffer.from(part,'utf8');
    count+=bytes.length;
    if (count>limit) fail('RESPONSE_EXCEEDS_LIMIT');
    digest.update(bytes);
    if (pieces) pieces.push(bytes);
  }
  if (length!==undefined && Number(length)!==count) fail('TRUNCATED_OR_WRONG_CONTENT_LENGTH');
  return {bytes:count,sha256:digest.digest('hex'),text:pieces?Buffer.concat(pieces).toString('utf8'):undefined};
}

function validateRelease(doc) {
  if (!isExactKeys(doc,KEYS) || doc.schema!==1 ||
      doc.channel!=='OWNER_RELEASE' || doc.packageName!==OWNER_PACKAGE ||
      doc.signerSha256!==OWNER_CERT || doc.approved!==true ||
      !Number.isSafeInteger(doc.versionCode) || doc.versionCode<29 || doc.versionCode>9999 ||
      doc.versionName!=='0.3.'+(doc.versionCode-1) ||
      !Number.isSafeInteger(doc.byteLength) || doc.byteLength<1_000_000 ||
      doc.byteLength>MAX_PUBLISHED_APK_BYTES ||
      typeof doc.apkSha256!=='string' || !HASH.test(doc.apkSha256) ||
      typeof doc.sourceCommit!=='string' || !COMMIT.test(doc.sourceCommit) ||
      typeof doc.notes!=='string' || doc.notes.length>800 ||
      /[\u0000-\u0009\u000b-\u001f\u007f]/.test(doc.notes)) fail('INVALID_RELEASE_METADATA');
  if (doc.apkUrl!=='https://'+CHANNEL_HOST+'/continuum-local/Continuum-Lokal-'+doc.apkSha256+'.apk')
    fail('APK_URL_NOT_PINNED_TO_SHA256');
  pinned(doc.apkUrl);
  return doc;
}

/**
 * Transport override exists only for isolated test fixtures. The default never
 * sends credentials, cannot follow redirects and is pinned to the existing host.
 */
export async function verifyPublishedRelease({transport=pinnedHttpsGet,verifyApk=true}={}) {
  if (typeof transport!=='function' || typeof verifyApk!=='boolean') fail('INVALID_INVOCATION');
  const manifest=await receive(await transport(CHANNEL_URL),MAX_MANIFEST_BYTES,true);
  if (manifest.bytes===0) fail('EMPTY_MANIFEST');
  let doc;
  try { doc=JSON.parse(manifest.text); }
  catch { fail('INVALID_JSON'); }
  if (isExactKeys(doc,['schema','available']) && doc.schema===1 && doc.available===false)
    return {status:'NO_RELEASE',manifestSha256:manifest.sha256,apkVerified:false};
  validateRelease(doc);
  if (!verifyApk) return {status:'MANIFEST_ONLY_APK_NOT_VERIFIED',versionCode:doc.versionCode,
    sourceCommit:doc.sourceCommit,manifestSha256:manifest.sha256,apkVerified:false};
  const apk=await receive(await transport(doc.apkUrl),MAX_PUBLISHED_APK_BYTES);
  if (apk.bytes!==doc.byteLength || apk.sha256!==doc.apkSha256)
    fail('PUBLISHED_APK_BYTES_OR_SHA256_MISMATCH');
  return {status:'APK_HASH_VERIFIED_CERT_NOT_VERIFIED',versionCode:doc.versionCode,
    sourceCommit:doc.sourceCommit,manifestSha256:manifest.sha256,
    apkSha256:apk.sha256,apkBytes:apk.bytes,apkVerified:true};
}

if (process.argv[1] && fileURLToPath(import.meta.url)===process.argv[1]) {
  if (process.argv.length!==2 && !(process.argv.length===3 && process.argv[2]==='--manifest-only')) {
    process.stderr.write('Usage: node scripts/continuum-owner-render-readback.mjs [--manifest-only]\n');
    process.exitCode=64;
  } else {
    verifyPublishedRelease({verifyApk:process.argv.length===2}).then(result=>{
      process.stdout.write(JSON.stringify(result)+'\n');
    }).catch(error=>{
      process.stderr.write(String(error.message||error).slice(0,240)+'\n');
      process.exitCode=69;
    });
  }
}
