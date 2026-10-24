import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp, writeFile, chmod, rm, symlink, link} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {createHash} from 'node:crypto';
import {inspectGgufHeader, admitGgufFile} from './continuum-gguf.mjs';

const u32 = n => { const b=Buffer.alloc(4); b.writeUInt32LE(n); return b; };
const u64 = n => { const b=Buffer.alloc(8); b.writeBigUInt64LE(BigInt(n)); return b; };
const str = s => { const b=Buffer.from(s); return Buffer.concat([u64(b.length), b]); };
const scalar = (key,type,value) => Buffer.concat([str(key),u32(type),type===8?str(value):u32(value)]);
function fixture({version=3, fields=[], tensors=[{name:'token_embd.weight',dims:[4,4],type:0,offset:0}], dataBytes=64}={}) {
  const kv=[scalar('general.architecture',8,'llama'),scalar('llama.context_length',4,2048),...fields];
  const info=tensors.map(t => Buffer.concat([str(t.name),u32(t.dims.length),...t.dims.map(u64),u32(t.type),u64(t.offset)]));
  const header=Buffer.concat([Buffer.from('GGUF'),u32(version),u64(tensors.length),u64(kv.length),...kv,...info]);
  return Buffer.concat([header,Buffer.alloc((32-header.length%32)%32),Buffer.alloc(dataBytes)]);
}
const pin = bytes => ({repository:'owner/public-model',revision:'a'.repeat(40),filename:'model.gguf',license:'apache-2.0',
  sizeBytes:bytes.length,sha256:createHash('sha256').update(bytes).digest('hex')});

test('parses real GGUF v2/v3 structure and bounded tensor bytes',()=>{
  for(const version of [2,3]) {
    const bytes=fixture({version}); const report=inspectGgufHeader(bytes);
    assert.equal(report.version,version); assert.equal(report.architecture,'llama');
    assert.equal(report.contextLength,2048); assert.equal(report.tensorCount,1);
    assert.equal(report.tensorBytes,64); assert.equal(report.dataOffset%32,0);
    assert.equal(report.trusted,false);
  }
});

test('rejects magic, endian and unsupported versions',()=>{
  const bad=fixture(); bad.write('FUGG');
  assert.throws(()=>inspectGgufHeader(bad),/GGUF_MAGIC/);
  const be=fixture(); be.writeUInt32BE(3,4);
  assert.throws(()=>inspectGgufHeader(be),/GGUF_VERSION_OR_ENDIAN/);
  assert.throws(()=>inspectGgufHeader(fixture({version:1})),/GGUF_VERSION_OR_ENDIAN/);
});

test('rejects truncation and oversized counts before iteration or allocation',()=>{
  assert.throws(()=>inspectGgufHeader(Buffer.alloc(12)),/GGUF_TRUNCATED/);
  const b=fixture(); b.writeBigUInt64LE(2n**63n,8);
  assert.throws(()=>inspectGgufHeader(b),/GGUF_INTEGER_RANGE/);
  const c=fixture(); c.writeBigUInt64LE(100001n,8);
  assert.throws(()=>inspectGgufHeader(c),/GGUF_TENSOR_COUNT/);
  const d=fixture(); d.writeBigUInt64LE(4097n,16);
  assert.throws(()=>inspectGgufHeader(d),/GGUF_METADATA_COUNT/);
});

test('rejects duplicate metadata, prototype keys, invalid UTF-8 and unknown values',()=>{
  assert.throws(()=>inspectGgufHeader(fixture({fields:[scalar('general.architecture',8,'qwen2')]})),/GGUF_DUPLICATE_METADATA/);
  assert.throws(()=>inspectGgufHeader(fixture({fields:[scalar('__proto__',4,1)]})),/GGUF_METADATA_KEY/);
  assert.throws(()=>inspectGgufHeader(fixture({fields:[Buffer.concat([str('bad.type'),u32(99),u32(0)])]})),/GGUF_METADATA_TYPE/);
  const b=fixture(); b[32]=255;
  assert.throws(()=>inspectGgufHeader(b),/GGUF_UTF8/);
});

test('skips tokenizer arrays without retaining tokens or evaluating templates',()=>{
  const values=Buffer.concat([str('tokenizer.ggml.tokens'),u32(9),u32(8),u64(2),str('hello'),str('world')]);
  const bytes=fixture({fields:[values,scalar('tokenizer.chat_template',8,'{{ dangerous() }}')]});
  const r=inspectGgufHeader(bytes);
  assert.equal(r.tokenizerTokens,2); assert.equal(r.hasChatTemplate,true);
  assert.equal(JSON.stringify(r).includes('dangerous'),false);
  assert.equal(JSON.stringify(r).includes('hello'),false);
});

test('rejects nested arrays, enormous array/string lengths and invalid alignment',()=>{
  const array=(type,n)=>Buffer.concat([str('bad.array'),u32(9),u32(type),u64(n)]);
  assert.throws(()=>inspectGgufHeader(fixture({fields:[array(9,1)]})),/GGUF_NESTED_ARRAY/);
  assert.throws(()=>inspectGgufHeader(fixture({fields:[array(8,1000001)]})),/GGUF_ARRAY_LIMIT/);
  assert.throws(()=>inspectGgufHeader(fixture({fields:[Buffer.concat([str('bad.string'),u32(8),u64(2**32)])]})),/GGUF_STRING_LIMIT/);
  assert.throws(()=>inspectGgufHeader(fixture({fields:[scalar('general.alignment',4,3)]})),/GGUF_ALIGNMENT/);
  const alignmentArray=Buffer.concat([str('general.alignment'),u32(9),u32(4),u64(1),u32(32)]);
  assert.throws(()=>inspectGgufHeader(fixture({fields:[alignmentArray]})),/GGUF_ALIGNMENT/);
  const invalidTokens=Buffer.concat([str('tokenizer.ggml.tokens'),u32(9),u32(4),u64(1),u32(1)]);
  assert.throws(()=>inspectGgufHeader(fixture({fields:[invalidTokens]})),/GGUF_TOKENIZER_TOKENS/);
});

test('rejects wrong metadata scalar types, non-finite floats and booleans',()=>{
  assert.throws(()=>inspectGgufHeader(fixture({fields:[scalar('general.alignment',8,'32')]})),/GGUF_ALIGNMENT/);
  const badBool=Buffer.concat([str('bad.bool'),u32(7),Buffer.from([2])]);
  assert.throws(()=>inspectGgufHeader(fixture({fields:[badBool]})),/GGUF_BOOLEAN/);
  const inf=Buffer.alloc(4); inf.writeFloatLE(Infinity);
  assert.throws(()=>inspectGgufHeader(fixture({fields:[Buffer.concat([str('bad.float'),u32(6),inf])]})),/GGUF_NONFINITE/);
});

test('rejects tensor overflow, out-of-file ranges, overlap and misalignment',()=>{
  assert.throws(()=>inspectGgufHeader(fixture({dataBytes:63})),/GGUF_TENSOR_BOUNDS/);
  assert.throws(()=>inspectGgufHeader(fixture({tensors:[{name:'x',dims:[4],type:0,offset:1}]})),/GGUF_TENSOR_ALIGNMENT/);
  assert.throws(()=>inspectGgufHeader(fixture({tensors:[{name:'x',dims:[4],type:0,offset:0},{name:'y',dims:[4],type:0,offset:0}]})),/GGUF_TENSOR_OVERLAP/);
  assert.throws(()=>inspectGgufHeader(fixture({tensors:[{name:'x',dims:[2**30,2**30],type:0,offset:0}]})),/GGUF_TENSOR_SIZE/);
});

test('rejects unsupported quantization, malformed rows, duplicate tensor names and zero dimensions',()=>{
  assert.throws(()=>inspectGgufHeader(fixture({tensors:[{name:'x',dims:[4],type:999,offset:0}]})),/GGUF_TENSOR_TYPE_UNSUPPORTED/);
  assert.throws(()=>inspectGgufHeader(fixture({tensors:[{name:'x',dims:[4],type:12,offset:0}]})),/GGUF_QUANTIZED_ROW/);
  assert.throws(()=>inspectGgufHeader(fixture({tensors:[{name:'x',dims:[0],type:0,offset:0}]})),/GGUF_TENSOR_DIMENSION/);
  assert.throws(()=>inspectGgufHeader(fixture({tensors:[{name:'x',dims:[4],type:0,offset:0},{name:'x',dims:[4],type:0,offset:32}]})),/GGUF_DUPLICATE_TENSOR/);
});

test('computes quantized row storage from audited block sizes',()=>{
  const bytes=fixture({tensors:[{name:'x',dims:[256,2],type:12,offset:0}],dataBytes:288});
  assert.equal(inspectGgufHeader(bytes).tensorBytes,288);
});

test('rejects bounded header exhaustion and missing/unsafe architecture metadata',()=>{
  const bytes=fixture();
  assert.throws(()=>inspectGgufHeader(bytes.subarray(0,64),{fileSizeBytes:bytes.length}),/GGUF_HEADER_LIMIT/);
  const bad=fixture(); const at=bad.indexOf(Buffer.from('llama')); bad.write('!lama',at);
  assert.throws(()=>inspectGgufHeader(bad),/GGUF_ARCHITECTURE/);
});

test('file admission hashes actual bytes and records identity without model trust',async()=>{
  const dir=await mkdtemp(join(tmpdir(),'continuum-gguf-'));
  try {
    const bytes=fixture(), path=join(dir,'model.gguf'); await writeFile(path,bytes,{mode:0o600});
    const r=await admitGgufFile({path,provenance:pin(bytes)});
    assert.equal(r.status,'GGUF_IDENTITY_MATCHED'); assert.equal(r.sha256,pin(bytes).sha256);
    assert.equal(r.trusted,false); assert.equal(r.executionAllowed,false); assert.equal(r.promoted,false);
    assert.equal(r.inspection.tensorBytes,64);
    await assert.rejects(admitGgufFile({path,provenance:{...pin(bytes),sha256:'f'.repeat(64)}}),/GGUF_HASH_MISMATCH/);
    await assert.rejects(admitGgufFile({path,provenance:{...pin(bytes),sizeBytes:bytes.length+1}}),/GGUF_SIZE_MISMATCH/);
  } finally {await rm(dir,{recursive:true,force:true});}
});

test('admission rejects links, writable files, directories and malformed provenance',async()=>{
  const dir=await mkdtemp(join(tmpdir(),'continuum-gguf-'));
  try {
    const bytes=fixture(), path=join(dir,'model.gguf'); await writeFile(path,bytes,{mode:0o600});
    const alias=join(dir,'alias.gguf'); await symlink(path,alias);
    await assert.rejects(admitGgufFile({path:alias,provenance:pin(bytes)}),/GGUF_UNSAFE_FILE/);
    await chmod(path,0o666);
    await assert.rejects(admitGgufFile({path,provenance:pin(bytes)}),/GGUF_UNSAFE_FILE/);
    await chmod(path,0o600); await link(path,join(dir,'hard.gguf'));
    await assert.rejects(admitGgufFile({path,provenance:pin(bytes)}),/GGUF_UNSAFE_FILE/);
    await assert.rejects(admitGgufFile({path:dir,provenance:pin(bytes)}),/GGUF_PATH/);
    await assert.rejects(admitGgufFile({path,provenance:{...pin(bytes),revision:'main'}}),/GGUF_PROVENANCE/);
    await assert.rejects(admitGgufFile({path,provenance:{...pin(bytes),license:''}}),/GGUF_PROVENANCE/);
  } finally {await rm(dir,{recursive:true,force:true});}
});

test('file admission enforces caller resource and cancellation bounds',async()=>{
  const dir=await mkdtemp(join(tmpdir(),'continuum-gguf-'));
  try {
    const bytes=fixture(), path=join(dir,'model.gguf'); await writeFile(path,bytes,{mode:0o600});
    await assert.rejects(admitGgufFile({path,provenance:pin(bytes),maxFileBytes:100}),/GGUF_FILE_LIMIT/);
    await assert.rejects(admitGgufFile({path,provenance:pin(bytes),signal:AbortSignal.abort()}),/GGUF_ABORTED/);
  } finally {await rm(dir,{recursive:true,force:true});}
});
