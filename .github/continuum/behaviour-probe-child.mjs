// Child of behaviour-probe.mjs. Runs under the Node permission model (read-only file access to one module, no file
// writes, no child processes, no workers): imports the module, calls each requested export on the example arguments
// and reports, per export, how many results equal the expected values. Input and output are JSON on stdin/stdout.
import {pathToFileURL} from 'node:url';
import {isDeepStrictEqual} from 'node:util';

for(const name of ['fetch','WebSocket','EventSource'])globalThis[name]=undefined;   // network is excluded by the parent's static check; this removes the globals as well

let input='';
for await(const chunk of process.stdin)input+=chunk;
const {file,exports,cases,nonce}=JSON.parse(input);
const result={};
let mod=null,importError=null;
try{mod=await import(pathToFileURL(file).href);}catch(e){importError=String(e?.code??e?.message??e).slice(0,120);}
for(const name of exports){
 const r={matched:0,total:cases.length,errors:0,firstError:null};
 const fn=mod?.[name];
 if(typeof fn!=='function'){r.errors=cases.length;r.firstError=importError??'not a function';result[name]=r;continue;}
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
