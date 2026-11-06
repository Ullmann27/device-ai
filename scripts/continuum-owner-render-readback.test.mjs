import test from 'node:test';
import assert from 'node:assert/strict';
import {Readable} from 'node:stream';
import {createHash} from 'node:crypto';
import {CHANNEL_URL, CHANNEL_HOST, OWNER_CERT, OWNER_PACKAGE,
  verifyPublishedRelease} from './continuum-owner-render-readback.mjs';

const apk=Buffer.alloc(1_000_000,77);
const digest=createHash('sha256').update(apk).digest('hex');
const url='https://'+CHANNEL_HOST+'/continuum-local/Continuum-Lokal-'+digest+'.apk';
const doc=()=>({schema:1,channel:'OWNER_RELEASE',packageName:OWNER_PACKAGE,
  versionCode:30,versionName:'0.3.29',apkUrl:url,apkSha256:digest,
  byteLength:apk.length,sourceCommit:'a'.repeat(40),signerSha256:OWNER_CERT,
  approved:true,notes:'Owner release'});
const reply=(data,code=200,headers={})=>({statusCode:code,headers,body:Readable.from([data])});
function mock(manifest,apkOverride=apk,options={}) {
  const requested=[];
  const transport=async target=>{
    requested.push(target);
    if(target===CHANNEL_URL)return reply(typeof manifest==='string'?manifest:JSON.stringify(manifest),
      options.manifestCode??200,options.manifestHeaders??{});
    if(target===url)return reply(apkOverride,options.apkCode??200,options.apkHeaders??{});
    throw Error('Test tried a non-approved URL: '+target);
  };
  return {transport,requested};
}

test('placeholder is accurately NO_RELEASE and never requests an APK',async()=>{
  const t=mock({schema:1,available:false});
  const out=await verifyPublishedRelease({transport:t.transport});
  assert.equal(out.status,'NO_RELEASE');
  assert.equal(out.apkVerified,false);
  assert.deepEqual(t.requested,[CHANNEL_URL]);
});
test('real approved bytes yield hash-only receipt, never certificate claim',async()=>{
  const t=mock(doc());
  const out=await verifyPublishedRelease({transport:t.transport});
  assert.equal(out.status,'APK_HASH_VERIFIED_CERT_NOT_VERIFIED');
  assert.equal(out.apkSha256,digest);
  assert.equal(out.apkBytes,apk.length);
  assert.deepEqual(t.requested,[CHANNEL_URL,url]);
});
test('manifest-only explicitly does not fetch or verify an APK',async()=>{
  const t=mock(doc());
  const out=await verifyPublishedRelease({transport:t.transport,verifyApk:false});
  assert.equal(out.status,'MANIFEST_ONLY_APK_NOT_VERIFIED');
  assert.deepEqual(t.requested,[CHANNEL_URL]);
});
test('stream an approved APK larger than the historical 90 MB cap',async()=>{
  const size=101_794_583;
  const chunk=Buffer.alloc(64*1024,77);
  function* chunks() {
    for(let remaining=size;remaining>0;remaining-=chunk.length)
      yield chunk.subarray(0,Math.min(remaining,chunk.length));
  }
  const hash=createHash('sha256');
  for(const part of chunks())hash.update(part);
  const largeDigest=hash.digest('hex');
  const largeUrl='https://'+CHANNEL_HOST+'/continuum-local/Continuum-Lokal-'+largeDigest+'.apk';
  const manifest={...doc(),byteLength:size,apkSha256:largeDigest,apkUrl:largeUrl};
  const requested=[];
  const transport=async target=>{
    requested.push(target);
    if(target===CHANNEL_URL)return reply(JSON.stringify(manifest));
    assert.equal(target,largeUrl);
    return {statusCode:200,headers:{'content-length':String(size)},body:Readable.from(chunks())};
  };
  const out=await verifyPublishedRelease({transport});
  assert.equal(out.status,'APK_HASH_VERIFIED_CERT_NOT_VERIFIED');
  assert.equal(out.apkBytes,size);
  assert.equal(out.apkSha256,largeDigest);
  assert.deepEqual(requested,[CHANNEL_URL,largeUrl]);
});
test('manifest-only accepts the existing Android 2 GB policy boundary',async()=>{
  const t=mock({...doc(),byteLength:2_000_000_000});
  const out=await verifyPublishedRelease({transport:t.transport,verifyApk:false});
  assert.equal(out.status,'MANIFEST_ONLY_APK_NOT_VERIFIED');
  assert.equal(out.apkVerified,false);
  assert.deepEqual(t.requested,[CHANNEL_URL]);
});
test('reject wrong signer, approval, schema, version and size before APK requests',async()=>{
  for (const patch of [{approved:false},{signerSha256:'b'.repeat(64)},
    {schema:'1'},{versionCode:30.5},{versionName:'0.3.30'},
    {byteLength:100},{byteLength:2_000_000_001},{sourceCommit:'not-a-commit'},
    {notes:42},{extra:'field'}]) {
    const t=mock({...doc(),...patch});
    await assert.rejects(verifyPublishedRelease({transport:t.transport}),/INVALID_RELEASE_METADATA/);
    assert.deepEqual(t.requested,[CHANNEL_URL]);
  }
});
test('reject a fake no-update manifest and malformed JSON',async()=>{
  for (const manifest of [{schema:'1',available:false},
    {schema:1,available:0},{schema:1,available:false,extra:true},'not JSON']) {
    const t=mock(manifest);
    await assert.rejects(verifyPublishedRelease({transport:t.transport}),
      /INVALID_RELEASE_METADATA|INVALID_JSON/);
    assert.deepEqual(t.requested,[CHANNEL_URL]);
  }
});
test('reject substituted APK URLs before network download',async()=>{
  for (const apkUrl of ['https://evil.example/continuum-local/Continuum-Lokal-'+digest+'.apk',
    url+'?redirect=1',url.replace('https://','http://'),url.replace(digest,'b'.repeat(64))]) {
    const t=mock({...doc(),apkUrl});
    await assert.rejects(verifyPublishedRelease({transport:t.transport}),/APK_URL_NOT_PINNED_TO_SHA256/);
    assert.deepEqual(t.requested,[CHANNEL_URL]);
  }
});
test('reject wrong APK bytes, response status, gzip and truncated HTTP length',async()=>{
  const cases=[
    mock(doc(),Buffer.alloc(apk.length,88)),
    mock(doc(),apk,{apkCode:302}),
    mock(doc(),apk,{apkHeaders:{'content-encoding':'gzip'}}),
    mock(doc(),apk,{apkHeaders:{'content-length':String(apk.length+1)}})
  ];
  for(const t of cases)await assert.rejects(verifyPublishedRelease({transport:t.transport}),
    /PUBLISHED_APK_BYTES_OR_SHA256_MISMATCH|HTTP_NOT_200|UNEXPECTED_CONTENT_ENCODING|TRUNCATED_OR_WRONG_CONTENT_LENGTH/);
});
test('reject an APK response above the Android policy byte limit',async()=>{
  const t=mock(doc(),apk,{apkHeaders:{'content-length':'2000000001'}});
  await assert.rejects(verifyPublishedRelease({transport:t.transport}),/INVALID_CONTENT_LENGTH/);
});
test('reject redirect and oversize manifest response',async()=>{
  for (const t of [mock(doc(),apk,{manifestCode:301}),
    mock('x'.repeat(8193)),mock(doc(),apk,{manifestHeaders:{'content-length':'999999'}})]) {
    await assert.rejects(verifyPublishedRelease({transport:t.transport}),
      /HTTP_NOT_200|RESPONSE_EXCEEDS_LIMIT|INVALID_CONTENT_LENGTH/);
  }
});
