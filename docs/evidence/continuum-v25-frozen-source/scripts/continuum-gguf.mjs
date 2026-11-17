/**
 * EXP-05/06: bounded GGUF v2/v3 admission. This reads data, never executes a
 * runtime, downloads a model, evaluates a chat template, or promotes facts.
 * A matching caller pin is file identity, not publisher authentication or a
 * guarantee that llama.cpp can safely load a model. Keep its runtime isolated.
 */
import {constants} from 'node:fs';
import {open, lstat, realpath} from 'node:fs/promises';
import {isAbsolute, normalize} from 'node:path';
import {createHash} from 'node:crypto';
import {performance} from 'node:perf_hooks';

export const GGUF_LIMITS=Object.freeze({
  maxFileBytes:2_147_483_648, maxHeaderBytes:33_554_432,
  maxTensors:100_000, maxMetadata:4096, maxStringBytes:262_144,
  maxArrayElements:1_000_000, maxTotalArrayElements:2_000_000
});
// ggml/include/ggml.h + ggml/src/ggml-common.h at the pinned engine in the
// evidence document. Other encodings deliberately fail closed.
const BLOCKS=new Map([
  [0,[1,4]],[1,[1,2]],[2,[32,18]],[3,[32,20]],[6,[32,22]],
  [7,[32,24]],[8,[32,34]],[9,[32,36]],[10,[256,84]],
  [11,[256,110]],[12,[256,144]],[13,[256,176]],
  [14,[256,210]],[15,[256,292]],[30,[1,2]]
]);
const SHA=/^[a-f0-9]{64}$/;
const META_KEY=/^(?!__proto__$|constructor$|prototype$)[A-Za-z0-9_.-]{1,256}$/;
const ARCH=/^[a-z][a-z0-9_]{0,63}$/;
const decoder=new TextDecoder('utf-8',{fatal:true});
function fail(code) {throw new Error(code);}
function integer(value, min, max, code) {
  if(!Number.isSafeInteger(value)||value<min||value>max) fail(code);
  return value;
}

/** Parse only the bounded header buffer; tensor bytes need not be in memory. */
export function inspectGgufHeader(bytes,{fileSizeBytes=bytes?.byteLength}={}) {
  if(!(bytes instanceof Uint8Array)) fail('GGUF_BUFFER');
  if(bytes.length<24) fail('GGUF_TRUNCATED');
  integer(fileSizeBytes,24,GGUF_LIMITS.maxFileBytes,'GGUF_FILE_LIMIT');
  if(bytes.length>GGUF_LIMITS.maxHeaderBytes) fail('GGUF_HEADER_LIMIT');
  if(bytes.length>fileSizeBytes) fail('GGUF_SIZE_MISMATCH');
  const view=new DataView(bytes.buffer,bytes.byteOffset,bytes.byteLength);
  let offset=0,totalArrayElements=0;
  function need(n) {
    if(!Number.isSafeInteger(n)||n<0||offset+n>bytes.length)
      fail(bytes.length<fileSizeBytes?'GGUF_HEADER_LIMIT':'GGUF_TRUNCATED');
  }
  function number(size,get) {need(size);const n=view[get](offset,true);offset+=size;return n;}
  const u32=()=>number(4,'getUint32');
  function safe64(signed=false) {
    need(8);const n=signed?view.getBigInt64(offset,true):view.getBigUint64(offset,true);offset+=8;
    if(n>BigInt(Number.MAX_SAFE_INTEGER)||n<BigInt(Number.MIN_SAFE_INTEGER)) fail('GGUF_INTEGER_RANGE');
    return Number(n);
  }
  function string(max=GGUF_LIMITS.maxStringBytes) {
    const n=safe64(); if(n>max) fail('GGUF_STRING_LIMIT'); need(n);
    let s;try{s=decoder.decode(bytes.subarray(offset,offset+n));}catch{fail('GGUF_UTF8');}
    offset+=n;return s;
  }
  function value(type) {
    switch(type) {
      case 0:return number(1,'getUint8');
      case 1:return number(1,'getInt8');
      case 2:return number(2,'getUint16');
      case 3:return number(2,'getInt16');
      case 4:return u32();
      case 5:return number(4,'getInt32');
      case 6:case 12:{
        const n=number(type===6?4:8,type===6?'getFloat32':'getFloat64');
        if(!Number.isFinite(n)) fail('GGUF_NONFINITE');return n;
      }
      case 7:{const n=number(1,'getUint8');if(n>1)fail('GGUF_BOOLEAN');return n===1;}
      case 8:return string();
      case 10:return safe64();
      case 11:return safe64(true);
      default:fail('GGUF_METADATA_TYPE');
    }
  }
  need(4);
  if(bytes[0]!==71||bytes[1]!==71||bytes[2]!==85||bytes[3]!==70) fail('GGUF_MAGIC');
  offset=4;const version=u32();
  if(version!==2&&version!==3) fail('GGUF_VERSION_OR_ENDIAN');
  const tensorCount=integer(safe64(),1,GGUF_LIMITS.maxTensors,'GGUF_TENSOR_COUNT');
  const metadataCount=integer(safe64(),1,GGUF_LIMITS.maxMetadata,'GGUF_METADATA_COUNT');
  const metadata=new Map(), arrays=new Map();
  for(let i=0;i<metadataCount;i++) {
    const key=string(256);
    if(!META_KEY.test(key))fail('GGUF_METADATA_KEY');
    if(metadata.has(key)||arrays.has(key))fail('GGUF_DUPLICATE_METADATA');
    const type=u32();
    if(type===9) {
      const itemType=u32();if(itemType===9)fail('GGUF_NESTED_ARRAY');
      if(itemType>12)fail('GGUF_METADATA_TYPE');
      const length=integer(safe64(),0,GGUF_LIMITS.maxArrayElements,'GGUF_ARRAY_LIMIT');
      totalArrayElements+=length;
      if(totalArrayElements>GGUF_LIMITS.maxTotalArrayElements)fail('GGUF_ARRAY_LIMIT');
      for(let j=0;j<length;j++)value(itemType);
      arrays.set(key,Object.freeze({type:itemType,length}));
    } else metadata.set(key,value(type));
  }
  const architecture=metadata.get('general.architecture');
  if(typeof architecture!=='string'||!ARCH.test(architecture))fail('GGUF_ARCHITECTURE');
  if(arrays.has('general.alignment'))fail('GGUF_ALIGNMENT');
  const alignment=metadata.has('general.alignment')?metadata.get('general.alignment'):32;
  integer(alignment,1,4096,'GGUF_ALIGNMENT');
  if((alignment&(alignment-1))!==0)fail('GGUF_ALIGNMENT');
  const contextLength=metadata.get(architecture+'.context_length')??null;
  if(arrays.has(architecture+'.context_length'))fail('GGUF_CONTEXT_LENGTH');
  if(contextLength!==null)integer(contextLength,1,1_048_576,'GGUF_CONTEXT_LENGTH');
  const hasChatTemplate=metadata.has('tokenizer.chat_template');
  if(arrays.has('tokenizer.chat_template'))fail('GGUF_CHAT_TEMPLATE');
  if(hasChatTemplate&&typeof metadata.get('tokenizer.chat_template')!=='string') fail('GGUF_CHAT_TEMPLATE');
  if(arrays.has('tokenizer.ggml.tokens')&&arrays.get('tokenizer.ggml.tokens').type!==8)fail('GGUF_TOKENIZER_TOKENS');
  const names=new Set(),ranges=[],types=new Map();let tensorBytes=0;
  for(let i=0;i<tensorCount;i++) {
    const name=string(256);
    if(!META_KEY.test(name))fail('GGUF_TENSOR_NAME');
    if(names.has(name))fail('GGUF_DUPLICATE_TENSOR');names.add(name);
    const ndim=integer(u32(),1,4,'GGUF_TENSOR_DIMENSION');
    const dims=[];let elements=1;
    for(let j=0;j<ndim;j++) {
      const dim=integer(safe64(),1,2**31,'GGUF_TENSOR_DIMENSION');dims.push(dim);
      elements*=dim;if(!Number.isSafeInteger(elements))fail('GGUF_TENSOR_SIZE');
    }
    const type=u32(),block=BLOCKS.get(type);
    if(!block)fail('GGUF_TENSOR_TYPE_UNSUPPORTED');
    if(dims[0]%block[0]!==0)fail('GGUF_QUANTIZED_ROW');
    const size=elements/block[0]*block[1];
    if(!Number.isSafeInteger(size)||size>fileSizeBytes)fail('GGUF_TENSOR_SIZE');
    const start=safe64();
    if(start%alignment!==0)fail('GGUF_TENSOR_ALIGNMENT');
    const end=start+size;
    if(!Number.isSafeInteger(end))fail('GGUF_TENSOR_SIZE');
    ranges.push({start,end});tensorBytes+=size;
    if(!Number.isSafeInteger(tensorBytes))fail('GGUF_TENSOR_SIZE');
    types.set(type,(types.get(type)||0)+1);
  }
  const dataOffset=Math.ceil(offset/alignment)*alignment;
  if(dataOffset>fileSizeBytes)fail('GGUF_TENSOR_BOUNDS');
  ranges.sort((a,b)=>a.start-b.start);
  let previousEnd=0;
  for(const r of ranges) {
    if(r.start<previousEnd)fail('GGUF_TENSOR_OVERLAP');
    if(r.end>fileSizeBytes-dataOffset)fail('GGUF_TENSOR_BOUNDS');previousEnd=r.end;
  }
  return Object.freeze({schemaVersion:1,status:'GGUF_STRUCTURALLY_BOUNDED',trusted:false,
    version,endian:'little',architecture,contextLength,tensorCount,metadataCount,
    headerBytes:offset,dataOffset,alignment,tensorBytes,fileSizeBytes,
    tokenizerTokens:arrays.get('tokenizer.ggml.tokens')?.length??null,hasChatTemplate,
    tensorTypes:Object.freeze([...types].map(([type,count])=>Object.freeze({type,count})))
  });
}

function validateProvenance(p) {
  if(!p||typeof p!=='object'||Array.isArray(p)||
    !/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(p.repository)||
    typeof p.revision!=='string'||!/^[a-f0-9]{40}$/.test(p.revision)||
    typeof p.filename!=='string'||!/^[A-Za-z0-9_.-]{1,180}\.gguf$/.test(p.filename)||
    typeof p.license!=='string'||!/^[A-Za-z0-9][A-Za-z0-9.+-]{0,63}$/.test(p.license)||
    typeof p.sha256!=='string'||!SHA.test(p.sha256)||
    !Number.isSafeInteger(p.sizeBytes)||p.sizeBytes<24||p.sizeBytes>GGUF_LIMITS.maxFileBytes) fail('GGUF_PROVENANCE');
  return Object.freeze({repository:p.repository,revision:p.revision,filename:p.filename,
    license:p.license,sizeBytes:p.sizeBytes,sha256:p.sha256});
}
const safeFile=s=>s.isFile()&&s.nlink===1n&&(s.mode&0o022n)===0n;
const sameFile=(a,b)=>a.dev===b.dev&&a.ino===b.ino&&a.size===b.size&&a.mtimeNs===b.mtimeNs&&a.ctimeNs===b.ctimeNs;

/** Hash and parse through one O_NOFOLLOW descriptor. No runtime is launched. */
export async function admitGgufFile({path,provenance,maxFileBytes=GGUF_LIMITS.maxFileBytes,
  timeoutMs=30_000,signal}={}) {
  const pin=validateProvenance(provenance);
  if(typeof path!=='string'||!isAbsolute(path)||normalize(path)!==path||!path.endsWith('.gguf'))fail('GGUF_PATH');
  integer(maxFileBytes,24,GGUF_LIMITS.maxFileBytes,'GGUF_FILE_LIMIT');
  integer(timeoutMs,100,120_000,'GGUF_TIMEOUT');
  const started=performance.now();
  function active(){if(signal?.aborted)fail('GGUF_ABORTED');if(performance.now()-started>timeoutMs)fail('GGUF_TIMEOUT');}
  active();const before=await lstat(path,{bigint:true});
  if(!safeFile(before)||(await realpath(path))!==path)fail('GGUF_UNSAFE_FILE');
  if(before.size>BigInt(maxFileBytes))fail('GGUF_FILE_LIMIT');
  if(before.size!==BigInt(pin.sizeBytes))fail('GGUF_SIZE_MISMATCH');
  const handle=await open(path,constants.O_RDONLY|constants.O_NOFOLLOW);
  try {
    const initial=await handle.stat({bigint:true});
    if(!safeFile(initial)||!sameFile(before,initial))fail('GGUF_FILE_CHANGED');
    const header=Buffer.alloc(Math.min(pin.sizeBytes,GGUF_LIMITS.maxHeaderBytes));
    let headRead=0;
    while(headRead<header.length) {
      active();const {bytesRead}=await handle.read(header,headRead,header.length-headRead,headRead);
      if(!bytesRead)fail('GGUF_TRUNCATED');headRead+=bytesRead;
    }
    const inspection=inspectGgufHeader(header,{fileSizeBytes:pin.sizeBytes});
    const hash=createHash('sha256'),chunk=Buffer.alloc(1_048_576);let position=0;
    while(position<pin.sizeBytes) {
      active();const {bytesRead}=await handle.read(chunk,0,Math.min(chunk.length,pin.sizeBytes-position),position);
      if(!bytesRead)fail('GGUF_TRUNCATED');hash.update(chunk.subarray(0,bytesRead));position+=bytesRead;
    }
    active();const final=await handle.stat({bigint:true});
    const current=await lstat(path,{bigint:true});
    if(!safeFile(final)||!sameFile(initial,final)||!sameFile(final,current))fail('GGUF_FILE_CHANGED');
    const sha256=hash.digest('hex');if(sha256!==pin.sha256)fail('GGUF_HASH_MISMATCH');
    return Object.freeze({schemaVersion:1,status:'GGUF_IDENTITY_MATCHED',trusted:false,
      executionAllowed:false,promoted:false,sha256,sizeBytes:position,provenance:pin,inspection,
      elapsedMs:Math.round(performance.now()-started)});
  } finally {await handle.close();}
}
