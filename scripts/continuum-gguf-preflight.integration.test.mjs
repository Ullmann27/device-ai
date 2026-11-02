/**
 * Real CLI boundary: use the production GGUF parser and hash a complete small
 * structural fixture. The fixture is NOT a trained model; server is a stub.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp, writeFile, chmod, rm, mkdir, readFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {createHash} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const cli=fileURLToPath(new URL('./continuum-gguf-preflight.mjs',import.meta.url));
const launcher=fileURLToPath(new URL('./continuum-local-llama-termux.sh',import.meta.url));
const u32=n=>{const b=Buffer.alloc(4);b.writeUInt32LE(n);return b;};
const u64=n=>{const b=Buffer.alloc(8);b.writeBigUInt64LE(BigInt(n));return b;};
const str=s=>{const b=Buffer.from(s,'utf8');return Buffer.concat([u64(b.length),b]);};
const prop=(key,type,value)=>Buffer.concat([str(key),u32(type),type===8?str(value):u32(value)]);
function structuralGguf(){
  const tensor=Buffer.concat([str('token_embd.weight'),u32(2),u64(4),u64(4),u32(0),u64(0)]);
  const properties=[prop('general.architecture',8,'llama'),prop('llama.context_length',4,2048)];
  const header=Buffer.concat([Buffer.from('GGUF'),u32(3),u64(1),u64(properties.length),...properties,tensor]);
  return Buffer.concat([header,Buffer.alloc((32-header.length%32)%32),Buffer.alloc(64)]);
}
const sha=buf=>createHash('sha256').update(buf).digest('hex');
async function fixture(t){
  const dir=await mkdtemp(join(tmpdir(),'continuum-gguf-cli-'));
  t.after(()=>rm(dir,{recursive:true,force:true}));
  const model=join(dir,'model.gguf'),manifest=join(dir,'pin.json');
  const bytes=structuralGguf();
  const pin={repository:'owner/approved',revision:'a'.repeat(40),filename:'model.gguf',
    license:'Apache-2.0',sizeBytes:bytes.length,sha256:sha(bytes)};
  await writeFile(model,bytes,{mode:0o600});
  await writeFile(manifest,JSON.stringify(pin),{mode:0o600});
  return {dir,model,manifest,bytes,pin};
}
function command(f,env={}){
  return spawnSync(process.execPath,[cli,'--verify'],{encoding:'utf8',timeout:5000,
    env:{...process.env,CONTINUUM_GGUF_PATH:f.model,CONTINUUM_GGUF_MANIFEST:f.manifest,...env}});
}
test('real full GGUF admission: exact bytes -> identity receipt, no runtime authority',async t=>{
  const f=await fixture(t),result=command(f);
  assert.equal(result.status,0,result.stderr);
  const data=JSON.parse(result.stdout);
  assert.equal(data.status,'GGUF_PIN_VERIFIED');
  assert.equal(data.modelSha256,f.pin.sha256);
  assert.equal(data.modelBytes,f.bytes.length);
  assert.equal(data.architecture,'llama');
  assert.equal(data.serverModelIdentityVerified,false);
  assert.equal(data.androidDeviceVerified,false);
  assert.equal(data.modelPublisherAuthenticated,false);
});
test('real full admission rejects modified tensor bytes under unchanged pin',async t=>{
  const f=await fixture(t);
  const broken=Buffer.from(f.bytes);broken[broken.length-1]^=1;
  await writeFile(f.model,broken);
  const result=command(f);
  assert.equal(result.status,1,result.stdout);
  assert.match(result.stderr,/GGUF_HASH_MISMATCH/);
  assert.doesNotMatch(result.stdout,/GGUF_PIN_VERIFIED/);
});
test('malformed GGUF with self-consistent SHA is rejected structurally',async t=>{
  const f=await fixture(t),broken=Buffer.from(f.bytes);
  broken.write('BAD!',0);
  await writeFile(f.model,broken);
  await writeFile(f.manifest,JSON.stringify({...f.pin,sha256:sha(broken)}));
  const result=command(f);
  assert.equal(result.status,1,result.stdout);
  assert.match(result.stderr,/GGUF_MAGIC/);
});
test('altered manifest size and missing manifest do not admit',async t=>{
  const f=await fixture(t);
  await writeFile(f.manifest,JSON.stringify({...f.pin,sizeBytes:f.bytes.length+1}));
  const changed=command(f);
  assert.equal(changed.status,1);
  assert.match(changed.stderr,/GGUF_SIZE_MISMATCH/);
  const missing=command(f,{CONTINUUM_GGUF_MANIFEST:join(f.dir,'missing.json')});
  assert.equal(missing.status,1);
  assert.match(missing.stderr,/GGUF_PIN_UNAVAILABLE/);
});
test('simulated Termux actually executes preflight before touching even server --help',async t=>{
  const f=await fixture(t),bin=join(f.dir,'bin'),log=join(f.dir,'server.log');
  await mkdir(bin);
  await writeFile(join(bin,'uname'),'#!/bin/sh\necho aarch64\n',{mode:0o700});
  const server=join(f.dir,'llama-server');
  await writeFile(server,'#!/bin/sh\necho "$1" >> "$SERVER_LOG"\nif [ "$1" = "--help" ]; then echo "--api-key-file --no-cache-idle-slots --cache-ram --no-cache-prompt"; exit 0; fi\necho "FAKE_SERVER_NOT_A_MODEL"\n',{mode:0o700});
  const env={...process.env,PATH:bin+':'+process.env.PATH,BASH_ENV:'',ENV:'',HOME:f.dir,
    PREFIX:'/data/data/com.termux/files/usr',CONTINUUM_LLAMA_SERVER_BIN:server,
    CONTINUUM_GGUF_PATH:f.model,CONTINUUM_GGUF_MANIFEST:f.manifest,SERVER_LOG:log};
  const run=()=>spawnSync('bash',[launcher,'start'],{encoding:'utf8',timeout:5000,env});
  const ok=run();
  assert.equal(ok.status,0,ok.stderr);
  assert.match(ok.stdout,/GGUF_PIN_VERIFIED/);
  assert.match(ok.stdout,/FAKE_SERVER_NOT_A_MODEL/);
  assert.match(await readFile(log,'utf8'),/--help\n-m\n/);
  await writeFile(log,'');
  const bad=Buffer.from(f.bytes);bad[bad.length-1]^=1;
  await writeFile(f.model,bad);
  const rejected=run();
  assert.equal(rejected.status,1,rejected.stdout);
  assert.match(rejected.stderr,/GGUF_HASH_MISMATCH/);
  assert.equal(await readFile(log,'utf8'),'');
});
