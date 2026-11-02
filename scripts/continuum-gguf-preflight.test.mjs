import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp, writeFile, rm, chmod, symlink, link, mkdir, readFile} from 'node:fs/promises';
import {spawnSync} from 'node:child_process';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {readPinnedGgufManifest, preflightPinnedGguf} from './continuum-gguf-preflight.mjs';

const pinned = Object.freeze({repository:'sample/owner-approved',revision:'a'.repeat(40),
  filename:'model.gguf',license:'Apache-2.0',sizeBytes:256,sha256:'b'.repeat(64)});
const goodAdmission = Object.freeze({status:'GGUF_IDENTITY_MATCHED',sha256:pinned.sha256,
  sizeBytes:pinned.sizeBytes,trusted:false,executionAllowed:false,
  inspection:{architecture:'llama'}});
async function withManifest(t) {
  const dir=await mkdtemp(join(tmpdir(),'continuum-gguf-pin-'));
  t.after(()=>rm(dir,{recursive:true,force:true}));
  const manifestPath=join(dir,'pin.json');
  await writeFile(manifestPath,JSON.stringify(pinned),{mode:0o600});
  return {dir,manifestPath,modelPath:join(dir,'model.gguf')};
}

test('valid canonical owner pin is read without persistence or mutation',async t=>{
  const f=await withManifest(t);
  assert.deepEqual(await readPinnedGgufManifest(f.manifestPath),pinned);
  let admitted=0;
  const receipt=await preflightPinnedGguf({...f,admit:async ({path,provenance})=>{
    admitted++;assert.equal(path,f.modelPath);assert.deepEqual(provenance,pinned);
    return goodAdmission;
  }});
  assert.equal(admitted,1);assert.equal(receipt.status,'GGUF_PIN_VERIFIED');
  assert.equal(receipt.modelSha256,pinned.sha256);
  assert.equal(receipt.modelPublisherAuthenticated,false);
  assert.equal(receipt.serverModelIdentityVerified,false);
  assert.equal(receipt.androidDeviceVerified,false);
});

test('manifest missing, symlinked, hardlinked or group-writable fails closed',async t=>{
  const f=await withManifest(t);
  await assert.rejects(readPinnedGgufManifest(join(f.dir,'absent.json')),/GGUF_PIN_UNAVAILABLE/);
  const sy=join(f.dir,'sy.json');await symlink(f.manifestPath,sy);
  await assert.rejects(readPinnedGgufManifest(sy),/GGUF_PIN_UNSAFE_FILE/);
  const hard=join(f.dir,'hard.json');await link(f.manifestPath,hard);
  await assert.rejects(readPinnedGgufManifest(f.manifestPath),/GGUF_PIN_UNSAFE_FILE/);
  await rm(hard);await chmod(f.manifestPath,0o666);
  await assert.rejects(readPinnedGgufManifest(f.manifestPath),/GGUF_PIN_UNSAFE_FILE/);
});

test('oversize, malformed JSON and all invalid manifest fields refuse',async t=>{
  const f=await withManifest(t);
  await writeFile(f.manifestPath,'x'.repeat(8193));
  await assert.rejects(readPinnedGgufManifest(f.manifestPath),/GGUF_PIN_UNSAFE_FILE/);
  await writeFile(f.manifestPath,'{not-json');
  await assert.rejects(readPinnedGgufManifest(f.manifestPath),/GGUF_PIN_JSON/);
  for (const change of [
    {revision:'main'}, {sha256:'0'.repeat(63)}, {repository:'../../bad'},
    {filename:'../../evil.gguf'}, {license:'bad license'}, {sizeBytes:23},
    {sizeBytes:2147483649}, {unexpected:'trusted'}, {filename:'evil.ggml'}
  ]) {
    await writeFile(f.manifestPath,JSON.stringify({...pinned,...change}));
    await assert.rejects(readPinnedGgufManifest(f.manifestPath),/GGUF_PIN_SCHEMA/);
  }
});

test('no pin or file-name mismatch never calls admission',async t=>{
  const f=await withManifest(t);let calls=0;
  const admit=async()=>{calls++;return goodAdmission;};
  await assert.rejects(preflightPinnedGguf({...f,modelPath:join(f.dir,'other.gguf'),admit}),/GGUF_PIN_FILENAME_MISMATCH/);
  await assert.rejects(preflightPinnedGguf({...f,modelPath:'model.gguf',admit}),/GGUF_PIN_FILENAME_MISMATCH/);
  await assert.rejects(preflightPinnedGguf({...f,admit:null}),/GGUF_PIN_VERIFIER_UNAVAILABLE/);
  assert.equal(calls,0);
});

test('admission must independently confirm bytes, identity and disabled execution',async t=>{
  const f=await withManifest(t);
  for (const bad of [null, {status:'GGUF_IDENTITY_MATCHED'},
    {...goodAdmission,status:'MODEL_LOADED'},
    {...goodAdmission,sha256:'c'.repeat(64)},
    {...goodAdmission,sizeBytes:1},
    {...goodAdmission,trusted:true},
    {...goodAdmission,executionAllowed:true},
    {...goodAdmission,inspection:{}}]) {
    await assert.rejects(preflightPinnedGguf({...f,admit:async()=>bad}),/GGUF_PIN_ADMISSION_UNCONFIRMED/);
  }
});

test('rejected model admission propagates and prints no false success receipt',async t=>{
  const f=await withManifest(t);
  await assert.rejects(preflightPinnedGguf({...f,admit:async()=>{throw Error('GGUF_HASH_MISMATCH');}}),/GGUF_HASH_MISMATCH/);
});

test('Termux launcher rejects missing pinned manifest before running even server --help',async t=>{
  const f=await withManifest(t);const bin=join(f.dir,'bin'),server=join(f.dir,'llama-server');
  await mkdir(bin);
  await writeFile(join(bin,'uname'),'#!/bin/sh\necho aarch64\n',{mode:0o700});
  await writeFile(server,'#!/bin/sh\necho SERVER_WAS_EXECUTED\n',{mode:0o700});
  const env={...process.env,PATH:bin+':'+process.env.PATH,HOME:f.dir,
    PREFIX:'/data/data/com.termux/files/usr',CONTINUUM_LLAMA_SERVER_BIN:server,
    CONTINUUM_GGUF_PATH:f.modelPath,CONTINUUM_GGUF_MANIFEST:''};
  await writeFile(f.modelPath,'GGUF-not-a-real-model',{mode:0o600});
  const script=new URL('./continuum-local-llama-termux.sh',import.meta.url);
  const result=spawnSync('bash',[script.pathname,'start'],{env,encoding:'utf8',timeout:5000});
  assert.equal(result.status,1,result.stderr);
  assert.match(result.stderr,/pinned owner GGUF manifest required/);
  assert.doesNotMatch(result.stdout+result.stderr,/SERVER_WAS_EXECUTED/);
  assert.equal((await readFile(server,'utf8')).includes('SERVER_WAS_EXECUTED'),true);
});
