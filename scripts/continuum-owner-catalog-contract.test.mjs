import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {verifyOwnerCatalog} from './continuum-owner-catalog-contract.mjs';

const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const parser=readFileSync(resolve(root,'app/src/main/java/at/heinz/deviceai/intelligence/ContinuumOwnerUpdateCatalog.kt'),'utf8');
const policy=readFileSync(resolve(root,'app/src/main/java/at/heinz/deviceai/intelligence/ContinuumSignedUpdatePolicy.kt'),'utf8');
const digest='a'.repeat(64), commit='b'.repeat(40);
const owner={
  schema:1, channel:'OWNER_RELEASE', packageName:'at.heinz.deviceai.continuumlocal',
  versionCode:38, versionName:'0.3.37',
  apkUrl:'https://continuum-owner-v23-release-preflight.onrender.com/continuum-local/Continuum-Lokal-'+digest+'.apk',
  apkSha256:digest, byteLength:54673371, sourceCommit:commit,
  signerSha256:'6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23',
  approved:true, notes:'Vom Besitzer freigegeben.'
};
const validate=(value)=>verifyOwnerCatalog(JSON.stringify(value),parser,policy);
test('pinned Android app accepts exact signed and approved owner-release schema',()=>{
  assert.deepEqual(validate(owner),{sourceCommit:commit,versionCode:38,apkSha256:digest,byteLength:54673371});
});
for(const [name,changes] of [
  ['additional status metadata',{status:'SIGNED_STAGED_DEVICE_NOT_RUN'}],
  ['candidate channel',{channel:'OWNER_RELEASE_CANDIDATE'}],
  ['not approved',{approved:false}],
  ['incorrect original signer',{signerSha256:'f'.repeat(64)}],
  ['fractional version',{versionCode:38.5}],
  ['wrong APK URL',{apkUrl:'https://example.net/test.apk'}],
  ['wrong size',{byteLength:54673371.5}],
  ['mismatched APK sha',{apkSha256:'c'.repeat(64)}],
  ['wrong versionName',{versionName:'0.3.36'}]
]){
  test('owner-channel refuses '+name,()=>assert.throws(()=>validate({...owner,...changes}),/CONTINUUM_OWNER_CATALOG_BLOCKED/));
}
test('missing required field fails closed',()=>{
  const missing={...owner};delete missing.apkSha256;
  assert.throws(()=>validate(missing),/SCHEMA_KEYS_DIFFER_FROM_ANDROID/);
});
