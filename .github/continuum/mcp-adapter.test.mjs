import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';import os from 'node:os';import path from 'node:path';
import {createMcpSession,decideToolCall,withinScope} from './mcp-adapter.mjs';

// A local stdio MCP server stand-in: newline-delimited JSON-RPC, a few tools, one that hangs, one that floods.
const SERVER=`const rl=require('readline').createInterface({input:process.stdin});
const out=m=>process.stdout.write(JSON.stringify(m)+'\\n');
rl.on('line',l=>{const m=JSON.parse(l);if(m.id===undefined)return;
 if(m.method==='initialize')return out({jsonrpc:'2.0',id:m.id,result:{protocolVersion:'2025-06-18',capabilities:{tools:{}},serverInfo:{name:'fake'}}});
 if(m.method==='tools/list')return out({jsonrpc:'2.0',id:m.id,result:{tools:['read_page','click','delete_all','secret_tool','hang','flood'].map(name=>({name}))}});
 if(m.method==='tools/call'){const n=m.params.name;if(n==='hang')return;
  if(n==='flood')return out({jsonrpc:'2.0',id:m.id,result:{content:[{type:'text',text:'x'.repeat(50000)}]}});
  return out({jsonrpc:'2.0',id:m.id,result:{content:[{type:'text',text:n+':'+JSON.stringify(m.params.arguments)+':env='+(process.env.SECRET_TOKEN??'none')}]}});}
});`;
const allowlist={read_page:{risk:'READ_ONLY'},click:{risk:'WRITE'},delete_all:{risk:'DESTRUCTIVE'},hang:{risk:'READ_ONLY'},flood:{risk:'READ_ONLY'}};
const grant={ownerAuthenticated:true,sessionActive:true,devicePermissionGranted:true,
  allowedToolNames:['read_page','click','delete_all','hang','flood'],reusableConsentToolNames:['click']};

async function session(over={}){
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-mcp-'));const file=path.join(dir,'server.cjs');fs.writeFileSync(file,SERVER);
  const s=await createMcpSession({command:process.execPath,args:[file],cwd:dir,allowlist,grant,workspace:dir,limits:{callTimeoutMs:1500,maxCalls:3},...over});
  return {s,dir,end:async()=>{await s.close();fs.rmSync(dir,{recursive:true,force:true});}};
}
const code=async p=>{try{await p;return 'OK';}catch(e){return e.code??e.message;}};

test('decision table mirrors ContinuumDualControlPolicy.kt',()=>{
  const g=grant;
  assert.equal(decideToolCall({profile:'PROTECTED',name:'read_page',spec:allowlist.read_page,grant:g}),'ALLOW');
  assert.equal(decideToolCall({profile:'PROTECTED',name:'click',spec:allowlist.click,grant:g}),'REQUIRE_OWNER_REVIEW');
  assert.equal(decideToolCall({profile:'OWNER_AUTONOMOUS',name:'click',spec:allowlist.click,grant:g}),'ALLOW');
  assert.equal(decideToolCall({profile:'OWNER_AUTONOMOUS',name:'delete_all',spec:allowlist.delete_all,grant:g}),'REQUIRE_OWNER_REVIEW');
  assert.equal(decideToolCall({profile:'OWNER_AUTONOMOUS',name:'click',spec:allowlist.click,grant:{...g,devicePermissionGranted:false}}),'DENY');
  assert.equal(decideToolCall({profile:'OWNER_AUTONOMOUS',name:'click',spec:allowlist.click,grant:{...g,allowedToolNames:['read_page']}}),'DENY','revoked');
  assert.equal(decideToolCall({profile:'OWNER_AUTONOMOUS',name:'x',spec:null,grant:g}),'DENY');
  assert.equal(withinScope({path:'../etc/passwd'},'/w'),false);assert.equal(withinScope({path:'a/b.txt'},'/w'),true);
});

test('OWNER_AUTONOMOUS runs pre-authorised tools without asking; receipts record every call; no env secrets reach the server',async()=>{
  process.env.SECRET_TOKEN='do-not-leak';
  const {s,end}=await session({profile:'OWNER_AUTONOMOUS',confirm:async()=>{throw Error('must not ask');}});
  try{
    assert.deepEqual(s.tools().map(t=>t.name).sort(),['click','delete_all','flood','hang','read_page'],'non-allowlisted secret_tool hidden');
    const r=await s.call('click',{selector:'#ok'});
    assert.match(r.content[0].text,/^click:\{"selector":"#ok"\}:env=none$/);
    assert.equal(s.receipts()[0].outcome,'OK');assert.match(s.receipts()[0].resultSha256,/^[a-f0-9]{64}$/);
  }finally{delete process.env.SECRET_TOKEN;await end();}
});

test('negative cases: unknown tool, revoked permission, scope, missing device permission, declined approval, budget, size, timeout',async()=>{
  const a=await session({profile:'PROTECTED',confirm:async()=>false});
  try{
    assert.equal(await code(a.s.call('secret_tool')),'MCP_TOOL_UNKNOWN');
    assert.equal(await code(a.s.call('rm_rf')),'MCP_TOOL_UNKNOWN');
    assert.equal(await code(a.s.call('click',{selector:'#x'})),'MCP_OWNER_DECLINED');
    assert.equal(await code(a.s.call('read_page',{path:'../../etc/passwd'})),'MCP_SCOPE_EXCEEDED');
    assert.equal(await code(a.s.call('flood')),'MCP_RESULT_TOO_LARGE');
    assert.equal(await code(a.s.call('read_page',{path:'notes.txt'})),'OK');
    assert.equal(await code(a.s.call('read_page')),'OK');
    assert.equal(await code(a.s.call('read_page')),'MCP_BUDGET_EXHAUSTED');
  }finally{await a.end();}
  const b=await session({grant:{...grant,allowedToolNames:['read_page']}});
  try{assert.equal(await code(b.s.call('click')),'MCP_TOOL_DENIED','revoked');}finally{await b.end();}
  const c=await session({grant:{...grant,devicePermissionGranted:false}});
  try{assert.equal(await code(c.s.call('read_page')),'MCP_TOOL_DENIED','missing device permission');}finally{await c.end();}
  const d=await session();
  try{assert.equal(await code(d.s.call('hang')),'MCP_TIMEOUT');assert.equal(await code(d.s.call('read_page')),'MCP_SERVER_CLOSED','server killed after timeout');}
  finally{await d.end();}
});
