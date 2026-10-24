// Child of behaviour-probe.mjs. Runs under the Node permission model (read-only file access to the repository, no file
// writes, no child processes, no workers) with network and signals refused below: imports the module, calls each
// requested export on the example arguments and reports, per export, how many results equal the expected values (mode
// "match") or every result itself, typed, so that candidates can be told apart (mode "outputs"). Input and output are
// JSON on stdin/stdout.
import net from 'node:net';
import tls from 'node:tls';
import dgram from 'node:dgram';
import dns from 'node:dns';
import http from 'node:http';
import https from 'node:https';
import http2 from 'node:http2';
import {syncBuiltinESMExports} from 'node:module';
import {pathToFileURL} from 'node:url';
import {isDeepStrictEqual} from 'node:util';

// Node 22's permission model does not restrict the network, and imports may now reach the whole repository, so every
// way the repository's code could open a socket or resolve a name throws ERR_PROBE_NETWORK before the module loads.
// Defence in depth against side effects of the owner's own code, not a sandbox for hostile code.
const denied=()=>{throw Object.assign(new Error('network access is disabled while probing'),{code:'ERR_PROBE_NETWORK'});};
for(const name of ['fetch','WebSocket','EventSource'])globalThis[name]=undefined;
net.Socket.prototype.connect=denied;net.Server.prototype.listen=denied;
net.connect=net.createConnection=tls.connect=dgram.createSocket=denied;
http.request=http.get=https.request=https.get=http2.connect=denied;
for(const api of [dns,dns.promises,dns.Resolver.prototype,dns.promises.Resolver.prototype])
 for(const k of Object.getOwnPropertyNames(api))if(k!=='constructor'&&k!=='Resolver'&&typeof api[k]==='function')api[k]=denied;
try{process.binding=denied;}catch{}
syncBuiltinESMExports();
// probe inputs are small numbers such as 1 or 7, so a candidate must never signal other processes; existence checks
// (signal 0) stay possible
const kill=process.kill.bind(process);
process.kill=(pid,signal)=>{
 if(signal===0)return kill(pid,0);
 throw Object.assign(new Error('signals are disabled while probing'),{code:'ERR_PROBE_SIGNAL'});
};

/** Primitive in the sense of a CCC1 acceptance case: usable as an expected value. */
const primitive=v=>v===null||typeof v==='boolean'||typeof v==='number'&&Number.isFinite(v)||typeof v==='string'&&v.length<=120;

/** Type-tagged, key-sorted rendering of any result, so that 1 and "1" or -0 and 0 never look alike. */
function show(v,depth=0){
 if(depth>6)return '…';
 if(v===null)return 'null';
 switch(typeof v){
  case 'undefined':return 'undefined';
  case 'number':return Object.is(v,-0)?'-0':String(v);
  case 'bigint':return v+'n';
  case 'string':return JSON.stringify(v);
  case 'boolean':return String(v);
  case 'function':return 'function';
  case 'symbol':return 'symbol';
 }
 if(Array.isArray(v))return '['+v.slice(0,40).map(x=>show(x,depth+1)).join(',')+(v.length>40?',…]':']');
 if(ArrayBuffer.isView(v))return 'bytes:'+Buffer.from(v.buffer,v.byteOffset,v.byteLength).toString('hex').slice(0,120);
 if(v instanceof Map||v instanceof Set)return v.constructor.name+show([...v],depth+1);
 return '{'+Object.keys(v).sort().slice(0,40).map(k=>JSON.stringify(k)+':'+show(v[k],depth+1)).join(',')+'}';
}

let input='';
for await(const chunk of process.stdin)input+=chunk;
const {file,exports,cases,nonce,mode='match'}=JSON.parse(input);
const result={};
let mod=null,importError=null;
try{mod=await import(pathToFileURL(file).href);}catch(e){importError=String(e?.code??e?.message??e).slice(0,120);}
for(const name of exports){
 const fn=mod?.[name];
 if(mode==='outputs'){
  result[name]=cases.map(c=>{
   if(typeof fn!=='function')return importError?{error:importError,unloadable:true}:{error:'not a function'};
   try{
    const out=fn(...c.args);
    if(out&&typeof out.then==='function'){out.catch?.(()=>{});return {error:'asynchronous result'};}
    const p=primitive(out);
    return p?{value:show(out).slice(0,300),primitive:true,json:out}:{value:show(out).slice(0,300),primitive:false};
   }catch(e){return {error:String(e?.code??e?.name??'Error').slice(0,60),message:String(e?.message??'').slice(0,80)};}
  });
  continue;
 }
 const r={matched:0,total:cases.length,errors:0,firstError:null};
 if(typeof fn!=='function'){r.errors=cases.length;r.firstError=importError??'not a function';if(importError)r.unloadable=true;result[name]=r;continue;}
 for(const c of cases){
  try{
   const out=fn(...c.args);
   if(out&&typeof out.then==='function'){r.errors++;r.firstError??='asynchronous result';out.catch?.(()=>{});continue;}
   if(isDeepStrictEqual(out,c.expected))r.matched++;
  }catch(e){r.errors++;r.firstError??=String(e?.code??e?.message??e).slice(0,120);}
 }
 result[name]=r;
}
// the parent takes only what follows the last nonce marker, so output printed by a candidate cannot pass as a result
process.stdout.write('\n'+nonce+JSON.stringify(result));
