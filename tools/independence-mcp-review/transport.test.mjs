import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import {pathToFileURL} from 'node:url';
import {WireClient,send,toolResult,denied} from './http-probe.mjs';

const candidate=path.resolve(process.env.CONTINUUM_MCP_CANDIDATE??'.');
const {startCodingMcpServer,createCodingMcpToken,MCP_PROTOCOL_VERSION}=
  await import(pathToFileURL(path.join(candidate,'.github/continuum/coding-mcp-server.mjs')));
const tools=['get_status','list_agents','list_tasks','get_evidence'];
const LEAK='independent-private-canary-4418-DO-NOT-PUBLISH';

function fixtureReader(){
  const calls=[];let generation=1;
  return {calls,setGeneration:value=>{generation=value;},
    getStatus:async()=>{calls.push('getStatus');return {schema:'independent-status-fixture',generation,status:'STOPPED'};},
    listAgents:async args=>{calls.push(['listAgents',args]);return {items:[{agentId:'bot-forge',displayName:'FORGE'}],nextCursor:null};},
    listTasks:async args=>{calls.push(['listTasks',args]);return {items:[{taskId:'review-task',state:'BLOCKED'}],nextCursor:null};},
    getEvidence:async args=>{calls.push(['getEvidence',args]);const content={status:'UNVERIFIED'};
      return {...args,sha256:crypto.createHash('sha256').update(JSON.stringify(content)).digest('hex'),
        mediaType:'application/json',content,environment:'HOST',deviceVerified:false};}
  };
}
async function withServer(fn,{reader=fixtureReader(),limits={},...options}={}){
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-mcp-review-'));fs.chmodSync(root,0o700);
  const tokenFile=path.join(root,'mcp.token');
  await createCodingMcpToken({tokenFile});
  const token=fs.readFileSync(tokenFile,'utf8').trim();
  let server;
  try{
    server=await startCodingMcpServer({reader,tokenFile,host:'127.0.0.1',port:0,limits,...options});
    const client=new WireClient(server.url,token,{version:MCP_PROTOCOL_VERSION});
    await fn({root,tokenFile,token,server,reader,client});
  }finally{await server?.close();fs.rmSync(root,{recursive:true,force:true});}
}
function rejected(reply,label){assert.ok(denied(reply),label+': '+reply.status+' '+reply.text.slice(0,200));}
function validRpc(reply){assert.equal(reply.status,200);assert.match(reply.headers['content-type'],/^application\/json/);assert.equal(reply.json?.jsonrpc,'2.0');assert.ok(reply.json?.result);}

test('P01/P03/P04: actual HTTP initialization, notification, exact read-only discovery and calls',async()=>{
  await withServer(async({client,reader})=>{
    const init=await client.ready();validRpc(init);
    assert.equal(init.json.result.protocolVersion,'2025-06-18');
    assert.ok(init.json.result.capabilities.tools);
    for(const capability of ['sampling','elicitation','tasks'])assert.equal(init.json.result.capabilities[capability],undefined);
    const listed=await client.rpc('tools/list');validRpc(listed);
    assert.deepEqual(listed.json.result.tools.map(t=>t.name).sort(),tools.toSorted());
    for(const t of listed.json.result.tools){assert.equal(t.inputSchema.type,'object');assert.equal(t.inputSchema.additionalProperties,false);}
    validRpc(await client.call('get_status'));
    validRpc(await client.call('list_agents',{limit:1}));
    validRpc(await client.call('list_tasks',{limit:1}));
    validRpc(await client.call('get_evidence',{taskId:'review-task',evidenceId:'result'}));
    assert.equal(reader.calls.length,4);
  });
});

test('P02: unknown initialization version negotiates supported; invalid subsequent version400',async()=>{
  await withServer(async({client,reader})=>{
    const init=await client.initialize('2099-12-31');validRpc(init);
    assert.equal(init.json.result.protocolVersion,'2025-06-18');
    await client.raw({jsonrpc:'2.0',method:'notifications/initialized'});
    const reply=await client.rpc('tools/list',{}, {headers:{'MCP-Protocol-Version':'not-supported'}});
    assert.equal(reply.status,400);assert.equal(reader.calls.length,0);
  });
});

test('A01/A02/A03/A04: bearer checked every request; body/query/session never credentials',async()=>{
  await withServer(async({client,reader,server,token})=>{
    await client.ready();
    const body={jsonrpc:'2.0',id:40,method:'tools/call',params:{name:'get_status',arguments:{}}};
    const publicHeaders={'Content-Type':'application/json','Accept':'application/json, text/event-stream',
      'MCP-Protocol-Version':MCP_PROTOCOL_VERSION,'MCP-Session-Id':client.session};
    for(const headers of [publicHeaders,{...publicHeaders,Authorization:'Bearer wrong'},
      {...publicHeaders,Authorization:'Basic '+token},{...publicHeaders,Authorization:'Bearer '}]){
      const reply=await send(server.url,{headers,body});assert.equal(reply.status,401);assert.ok(!reply.text.includes(token));
    }
    rejected(await send(server.url,{headers:{...publicHeaders,Authorization:['Bearer '+token,'Bearer wrong']},body}),'duplicate authorization');
    const query=await send(server.url+'?access_token='+token,{headers:publicHeaders,body});rejected(query,'query-only token');
    const embedded=await send(server.url,{headers:publicHeaders,body:{...body,access_token:token,role:'OWNER'}});
    assert.equal(embedded.status,401);assert.equal(reader.calls.length,0);
  });
});

test('A05/A06: cross-origin and rebinding names cannot access local read state',async()=>{
  await withServer(async({client,reader,server})=>{
    await client.ready();assert.equal(new URL(server.url).hostname,'127.0.0.1');
    for(const origin of ['https://attacker.invalid','null','http://127.0.0.1.attacker.invalid',
      'http://localhost.attacker.invalid','http://127.0.0.1:1','https://127.0.0.1']){
      const reply=await client.rpc('tools/call',{name:'get_status',arguments:{}},{headers:{Origin:origin}});
      rejected(reply,'origin '+origin);assert.notEqual(reply.headers['access-control-allow-origin'],'*');
    }
    for(const host of ['attacker.invalid','127.0.0.1.attacker.invalid','localhost.attacker.invalid','127.0.0.1:1']){
      const reply=await client.rpc('tools/call',{name:'get_status',arguments:{}},{headers:{Host:host,
        Forwarded:'host=127.0.0.1','X-Forwarded-Host':new URL(server.url).host,'X-Forwarded-For':'127.0.0.1'}});
      rejected(reply,'Host '+host);
    }
    assert.equal(reader.calls.length,0);
  });
});

test('P05/P07/A07: no writable/dynamic tool route and no actor-name authorization',async()=>{
  await withServer(async({client,reader,server})=>{
    await client.ready();
    for(const method of ['PUT','PATCH']){const r=await client.raw({submit:true},{method});assert.equal(r.status,405);}
    const get=await send(server.url,{method:'GET',headers:client.headers({'Accept':'text/event-stream'})});assert.equal(get.status,405);
    const wrong=await client.rpc('tools/list',{}, {url:new URL('/owner/submit',server.url)});rejected(wrong,'wrong endpoint');
    for(const name of ['submit','cancel','start','stop','read_file','constructor','__proto__','toString','GET_STATUS','get_status\u0000']){
      rejected(await client.call(name),'forged tool '+name);
    }
    for(const args of [{role:'OWNER'},{agentId:'jarvis'},{permissionScope:['ALL']},{token:'owner'},
      JSON.parse('{"__proto__":{"role":"OWNER"}}'),{path:'/etc/passwd'}]){
      rejected(await client.call('get_status',args),'extra privileged arguments');
    }
    for(const method of ['tasks/create','tools/execute','__proto__','constructor'])rejected(await client.rpc(method),'unknown method');
    assert.equal(reader.calls.length,0);
  });
});

test('P06: malformed JSON and invalid RPC objects reject without crashing or reading',async()=>{
  await withServer(async({client,reader})=>{
    await client.ready();
    for(const body of ['{', 'null','[]','[{}]','true','"tools/list"',{},
      {jsonrpc:'1.0',id:1,method:'tools/list'},{jsonrpc:'2.0',id:[],method:'tools/list'},
      {jsonrpc:'2.0',id:1},{jsonrpc:'2.0',id:1,method:42}])rejected(await client.raw(body),'invalid RPC');
    validRpc(await client.rpc('tools/list'));assert.equal(reader.calls.length,0);
  });
});

test('P08/P09/A04: cancellation notifications do not cancel tasks; unknown sessions deny',async()=>{
  await withServer(async({client,reader})=>{
    await client.ready();
    const cancel=await client.raw({jsonrpc:'2.0',method:'notifications/cancelled',params:{requestId:98765,reason:'review'}});
    assert.equal(cancel.status,202);assert.equal(cancel.text,'');
    const unknown=await client.rpc('tools/list',{}, {headers:{'MCP-Session-Id':'not-an-active-session'}});assert.equal(unknown.status,404);
    const without=client.headers();delete without['MCP-Session-Id'];
    rejected(await client.raw({jsonrpc:'2.0',id:8,method:'tools/list'},{replaceHeaders:true,headers:without}),'missing session');
    validRpc(await client.rpc('tools/list'));assert.equal(reader.calls.length,0);
  });
});

test('B01: transport media/body limits before reads; valid request still works',async()=>{
  await withServer(async({client,reader})=>{
    await client.ready();
    const args={name:'get_status',arguments:{}};
    for(const headers of [{'Content-Type':'text/plain'},{'Content-Type':'application/x-www-form-urlencoded'},
      {'Accept':'text/plain'}])rejected(await client.rpc('tools/call',args,{headers}),'media type');
    rejected(await client.raw(' '.repeat(2049)),'body limit');
    validRpc(await client.rpc('tools/list'));assert.equal(reader.calls.length,0);
  },{limits:{maxBodyBytes:2048}});
});

test('B02/B04: bounded page arguments, forged cursors and unknown fields',async()=>{
  await withServer(async({client,reader})=>{
    await client.ready();
    for(const name of ['list_agents','list_tasks']){
      for(const limit of [0,-1,1.5,1000000,'1',null])rejected(await client.call(name,{limit}),'page limit '+JSON.stringify(limit));
      for(const cursor of ['../../secret','unknown-cursor',crypto.randomBytes(24).toString('base64url')]){
        rejected(await client.call(name,{cursor,limit:1}),'cursor');
      }
      rejected(await client.call(name,{limit:1,permissionScope:'OWNER'}),'page extra field');
    }
    assert.equal(reader.calls.length,0);
  });
});

test('D04: evidence selectors cannot be paths or caller-selected permission',async()=>{
  await withServer(async({client,reader})=>{
    await client.ready();
    for(const evidenceId of ['../secret','/etc/passwd','..%2fsecret','a/b','a\\b','a\u0000b','']){
      rejected(await client.call('get_evidence',{taskId:'review-task',evidenceId}),'evidence path');
    }
    rejected(await client.call('get_evidence',{taskId:'review-task',evidenceId:'result',role:'OWNER'}),'evidence role');
    assert.equal(reader.calls.length,0);
  });
});

test('B06: backend errors are sanitized instead of publishing key/path/debug details',async()=>{
  const reader=fixtureReader();reader.getStatus=async()=>{throw Error(LEAK+' /private/runtime.key');};
  await withServer(async({client})=>{
    await client.ready();const reply=await client.call('get_status');rejected(reply,'backend error');
    assert.ok(!reply.text.includes(LEAK));assert.ok(!reply.text.includes('/private/runtime.key'));
    validRpc(await client.rpc('tools/list'));
  },{reader});
});

test('B06: oversized and hung canonical views have hard response/time bounds',async()=>{
  const reader=fixtureReader();reader.getStatus=async()=>({data:'x'.repeat(4096)});
  await withServer(async({client})=>{
    await client.ready();const reply=await client.call('get_status');rejected(reply,'output bound');assert.ok(reply.bytes<4096);
  },{reader,limits:{maxResultBytes:2048}});
  reader.getStatus=()=>new Promise(()=>{});
  await withServer(async({client})=>{
    await client.ready();const start=performance.now();rejected(await client.call('get_status'),'timeout');
    assert.ok(performance.now()-start<2000);validRpc(await client.rpc('tools/list'));
  },{reader,limits:{callTimeoutMs:100}});
});

test('B05: bounded session admission and rate limit',async()=>{
  await withServer(async({client,server,token})=>{
    await client.ready();const b=new WireClient(server.url,token);await b.ready();
    const c=new WireClient(server.url,token);rejected(await c.initialize(),'session limit');
  },{limits:{maxSessions:2}});
  await withServer(async({client})=>{
    await client.ready();let throttled=false;
    for(let i=0;i<10;i++){const r=await client.rpc('tools/list');if(r.status===429)throttled=true;}
    assert.ok(throttled,'request rate cap not enforced');
  },{limits:{maxRequestsPerMinute:6}});
});

test('D01: each call obtains the fresh read projection',async()=>{
  await withServer(async({client,reader})=>{
    await client.ready();const a=toolResult(await client.call('get_status'));reader.setGeneration(2);
    const b=toolResult(await client.call('get_status'));assert.equal(a.generation,1);assert.equal(b.generation,2);
    assert.equal(reader.calls.length,2);
  });
});

test('B03/B04: real pages are bounded, cursor-bound to tool and session, no duplicate identities',async()=>{
  const reader=fixtureReader();
  const ids=['bot-atlas','bot-forge','bot-lint','bot-mnemos'];
  reader.listAgents=async({cursor,limit})=>{
    const offset=cursor===null?0:ids.indexOf(cursor)+1;
    const page=ids.slice(offset,offset+limit);
    return {items:page.map(agentId=>({agentId})),nextCursor:offset+limit<ids.length?page.at(-1):null};
  };
  await withServer(async({client,server,token})=>{
    await client.ready();const first=toolResult(await client.call('list_agents',{limit:2}));
    assert.deepEqual(first.items.map(x=>x.agentId),ids.slice(0,2));assert.ok(first.nextCursor);
    rejected(await client.call('list_tasks',{cursor:first.nextCursor,limit:2}),'cross-collection cursor');
    const b=new WireClient(server.url,token);await b.ready();
    rejected(await b.call('list_agents',{cursor:first.nextCursor,limit:2}),'cross-session cursor');
    const last=toolResult(await client.call('list_agents',{cursor:first.nextCursor,limit:2}));
    assert.deepEqual([...first.items,...last.items].map(x=>x.agentId),ids);assert.equal(last.nextCursor,null);
  },{reader});
});

test('A08/P09: bearer rotation and deletion invalidate existing sessions',async()=>{
  await withServer(async({client,tokenFile,reader})=>{
    await client.ready();const replacement=crypto.randomBytes(32).toString('hex');
    fs.writeFileSync(tokenFile,replacement+'\n',{mode:0o600});
    assert.equal((await client.call('get_status')).status,401);
    client.token=replacement;assert.equal((await client.call('get_status')).status,404);
    client.session=null;await client.ready();validRpc(await client.call('get_status'));
    const ended=await client.raw(undefined,{method:'DELETE'});assert.equal(ended.status,204);
    assert.equal((await client.call('get_status')).status,404);assert.equal(reader.calls.length,1);
  });
});

test('B01/P06: explicit null arguments and malformed UTF-8 cannot become valid calls',async()=>{
  await withServer(async({client,reader})=>{
    await client.ready();
    for(const name of tools)rejected(await client.rpc('tools/call',{name,arguments:null}),'explicit null args');
    const payload=Buffer.concat([Buffer.from('{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{"x":"'),
      Buffer.from([0xc3,0x28]),Buffer.from('"}}')]);
    rejected(await client.raw(payload),'malformed utf8');assert.equal(reader.calls.length,0);
  });
});

test('B05/P08: simultaneous reads are bounded; cancellation ends only that read',async()=>{
  const reader=fixtureReader();let entered;
  const started=new Promise(resolve=>{entered=resolve;});let aborted=false;
  reader.getStatus=(_args,{signal})=>new Promise((_resolve,reject)=>{
    entered();signal.addEventListener('abort',()=>{aborted=true;reject(Error('cancelled'));},{once:true});
  });
  await withServer(async({client,server,token})=>{
    await client.ready();const b=new WireClient(server.url,token);await b.ready();
    const pending=client.call('get_status');const requestId=client.id;await started;
    assert.equal((await b.call('get_status')).status,429);
    const cancel=await client.raw({jsonrpc:'2.0',method:'notifications/cancelled',params:{requestId}});
    assert.equal(cancel.status,202);rejected(await pending,'cancelled read');assert.equal(aborted,true);
    validRpc(await b.rpc('tools/list'));assert.equal(server.getMetrics().activeReads,0);
  },{reader,limits:{maxConcurrentReads:1}});
});

test('D08: MCP rejects mismatched evidence content hash instead of forwarding it',async()=>{
  const reader=fixtureReader();const original=reader.getEvidence;
  reader.getEvidence=async args=>({...await original(args),sha256:'0'.repeat(64)});
  await withServer(async({client})=>{
    await client.ready();rejected(await client.call('get_evidence',{taskId:'review-task',evidenceId:'result'}),'invalid evidence hash');
  },{reader});
});

test('A08/P09: server restart drops old sessions while the owner credential remains usable',async()=>{
  await withServer(async({client,server,reader,tokenFile,token})=>{
    await client.ready();const oldSession=client.session;await server.close();
    const next=await startCodingMcpServer({reader,tokenFile,host:'127.0.0.1',port:0});
    try{
      const resumed=new WireClient(next.url,token);resumed.session=oldSession;
      assert.equal((await resumed.call('get_status')).status,404);
      resumed.session=null;await resumed.ready();validRpc(await resumed.call('get_status'));
    }finally{await next.close();}
  });
});

test('B05/P09: idle sessions expire and exhausted per-session read budget does not reset by new request IDs',async()=>{
  await withServer(async({client})=>{
    await client.ready();await new Promise(resolve=>setTimeout(resolve,80));
    assert.equal((await client.call('get_status')).status,404);
  },{limits:{sessionIdleMs:50}});
  await withServer(async({client,reader})=>{
    await client.ready();validRpc(await client.call('get_status'));
    assert.equal((await client.call('get_status')).status,429);assert.equal(reader.calls.length,1);
  },{limits:{maxCallsPerSession:1}});
});

test('P09: unknown GET sessions return404 before the unsupported-SSE405 response',async()=>{
  await withServer(async({client,server})=>{
    await client.ready();
    const valid=await send(server.url,{method:'GET',headers:client.headers({'Accept':'text/event-stream'})});assert.equal(valid.status,405);
    const unknown=await send(server.url,{method:'GET',headers:client.headers({'Accept':'text/event-stream','MCP-Session-Id':'no-such-session'})});assert.equal(unknown.status,404);
    await client.raw(undefined,{method:'DELETE'});
    const expired=await send(server.url,{method:'GET',headers:client.headers({'Accept':'text/event-stream'})});assert.equal(expired.status,404);
  });
});
