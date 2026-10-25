import test from 'node:test';
import assert from 'node:assert/strict';
import {readBoundedOsvResponse} from './bounded-osv-response.mjs';
const mock=bytes=>({body:new ReadableStream({start(c){for(const b of bytes)c.enqueue(b);c.close();}})});
const enc=new TextEncoder();
test('small fragmented UTF-8 response is assembled exactly',async()=>{
 const source='{"results":[{"vulns":[]}],"note":"ä"}';const bytes=enc.encode(source);
 assert.equal(await readBoundedOsvResponse(mock([bytes.subarray(0,12),bytes.subarray(12)])),source);
});
test('rejects missing body stream rather than reading unbounded text',async()=>{
 await assert.rejects(readBoundedOsvResponse({text:async()=>'{"results":[]}' }),/OSV_RESPONSE_STREAM_REQUIRED/);
});
test('rejects a single overlarge chunk before retaining it',async()=>{
 await assert.rejects(readBoundedOsvResponse(mock([new Uint8Array(1_000_001)])),/OSV_RESPONSE_TOO_LARGE/);
});
test('rejects many chunks when they cross the bound',async()=>{
 await assert.rejects(readBoundedOsvResponse(mock(Array.from({length:101},()=>new Uint8Array(10_000)))),/OSV_RESPONSE_TOO_LARGE/);
});
test('rejects invalid UTF-8 without replacement characters',async()=>{
 await assert.rejects(readBoundedOsvResponse(mock([Uint8Array.of(0xc3,0x28)])),/OSV_RESPONSE_INVALID/);
});
test('rejects arbitrary stream objects and non-byte chunks',async()=>{
 await assert.rejects(readBoundedOsvResponse({body:{getReader:()=>({read:async()=>({done:false,value:'a'}),cancel:async()=>{}})}}),/OSV_RESPONSE_INVALID/);
});
test('response at exact maximum bound can still be read',async()=>{
 const bytes=new Uint8Array(1_000_000);bytes.fill(97);
 assert.equal((await readBoundedOsvResponse(mock([bytes]))).length,1_000_000);
});
