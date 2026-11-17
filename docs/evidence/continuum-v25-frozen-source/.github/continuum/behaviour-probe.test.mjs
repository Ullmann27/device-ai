import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import net from 'node:net';
import http from 'node:http';
import {spawn} from 'node:child_process';
import {probeCandidates,ProbeLanguageCore} from './behaviour-probe.mjs';

function fixture(){
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'probe-'));
 const dir=path.join(root,'.github','continuum');
 fs.mkdirSync(dir,{recursive:true});
 const put=(name,src)=>fs.writeFileSync(path.join(dir,name),src);
 put('pure.mjs','export function double(x){return x*2;}\nexport function upper(s){return String(s).toUpperCase();}\n');
 put('sibling.mjs',"import {double} from './pure.mjs';\nexport function quad(x){return double(double(x));}\n");
 put('writer.mjs',"import fs from 'node:fs';\nexport function save(name){fs.writeFileSync(name,'x');return name*2;}\n");
 put('spawner.mjs',"import {execSync} from 'node:child_process';\nexport function run(x){return execSync('echo '+x).toString();}\n");
 put('loop.mjs','export function spin(x){while(true){}}\n');
 put('noisy.mjs',`export function claim(x){console.log('{"claim":{"matched":9,"total":9}}');return -1;}\nexport function triple(x){return x*3;}\n`);
 // imports from elsewhere in the repository, one of them with network use the static check of the candidate cannot see
 fs.mkdirSync(path.join(root,'scripts'));
 fs.writeFileSync(path.join(root,'scripts','helper.mjs'),'export function inc(x){return x+1;}\n');
 fs.writeFileSync(path.join(root,'scripts','net-helper.mjs'),"import http from 'node:http';\nimport net from 'node:net';\n"+
  "import {request} from 'node:https';\nexport function ping(port){http.get('http://127.0.0.1:'+port+'/');return 'sent';}\n"+
  "export function dial(port){net.connect(port,'127.0.0.1');return 'sent';}\nexport function secure(port){request('https://127.0.0.1:'+port+'/');return 'sent';}\n");
 put('uses-scripts.mjs',"import {inc} from '../../scripts/helper.mjs';\nexport function plusTwo(x){return inc(inc(x));}\n");
 put('signal.mjs','export function stop(pid){process.kill(pid,"SIGTERM");return "stopped";}\nexport function alive(pid){process.kill(pid,0);return "alive";}\n');
 put('indirect-net.mjs',"import {ping,dial,secure} from '../../scripts/net-helper.mjs';\n"+
  'export function viaHttp(p){return ping(p);}\nexport function viaNet(p){return dial(p);}\nexport function viaNamed(p){return secure(p);}\n');
 return root;
}
const C=(module,name)=>({module:'.github/continuum/'+module,export:name,required:1,max:1,description:''});

test('candidates are called on the examples in a separate process; siblings import, results are compared exactly',()=>{
 const root=fixture();
 const r=probeCandidates({root,candidates:[C('pure.mjs','double'),C('pure.mjs','upper'),C('sibling.mjs','quad')],
  cases:[{args:[2],expected:4},{args:[5],expected:10}]});
 assert.deepEqual(r.get('.github/continuum/pure.mjs#double'),{matched:2,total:2,errors:0,firstError:null});
 assert.equal(r.get('.github/continuum/pure.mjs#upper').matched,0);
 assert.equal(r.get('.github/continuum/sibling.mjs#quad').matched,0,'quad(2) = 8, not 4: executed, not guessed');
 assert.equal(r.get('.github/continuum/sibling.mjs#quad').errors,0,'the sibling import works with directory read access');
});

test('a candidate that writes is stopped by the permission model and leaves no file behind',()=>{
 const root=fixture();
 const r=probeCandidates({root,candidates:[C('writer.mjs','save')],cases:[{args:['written-by-probe'],expected:0}]}).get('.github/continuum/writer.mjs#save');
 assert.equal(r.matched,0);
 assert.equal(r.errors,1);
 assert.match(r.firstError,/ERR_ACCESS_DENIED/);
 for(const where of [root,path.join(root,'.github','continuum'),process.cwd(),os.tmpdir()])
  assert.ok(!fs.existsSync(path.join(where,'written-by-probe')),'nothing written in '+where);
});

test('process-spawning modules are not probed, and a hanging candidate only costs its own module',()=>{
 const root=fixture();
 const t0=Date.now();
 const r=probeCandidates({root,candidates:[C('spawner.mjs','run'),C('loop.mjs','spin'),C('pure.mjs','double')],
  cases:[{args:[3],expected:6}],timeoutMs:1500});
 assert.equal(r.get('.github/continuum/spawner.mjs#run').skipped,'imports a network or process module');
 assert.equal(r.get('.github/continuum/loop.mjs#spin').skipped,'timeout');
 assert.equal(r.get('.github/continuum/pure.mjs#double').matched,1);
 assert.ok(Date.now()-t0<10000);
});

test('the probe core ranks a candidate that meets every example first and keeps the base order otherwise',async()=>{
 const root=fixture();
 const candidates=[C('pure.mjs','upper'),C('noisy.mjs','claim'),C('sibling.mjs','quad'),C('pure.mjs','double')];
 const core=new ProbeLanguageCore({root,base:{id:'flat',rank:async(_g,cs)=>cs.map((_c,i)=>1-i*.1)}});
 const scores=await core.rank('Verdopple die Zahl: (2) → 4 und (5) → 10.',candidates);
 const order=candidates.map((c,i)=>({c,s:scores[i]})).sort((a,b)=>b.s-a.s).map(x=>x.c.export);
 assert.equal(order[0],'double');
 assert.deepEqual(order.slice(1),['upper','claim','quad']);
 const none=await core.rank('Ein Ziel ohne Beispiele',candidates);
 assert.deepEqual(none,[1,.9,.8,.7].map(x=>x),'no examples: the base ranking is unchanged');
});

test('text a candidate prints cannot pass as a probe result',()=>{
 const root=fixture();
 const r=probeCandidates({root,candidates:[C('noisy.mjs','claim'),C('noisy.mjs','triple')],cases:[{args:[2],expected:6}]});
 assert.equal(r.get('.github/continuum/noisy.mjs#claim').matched,0,'its printed fake result is ignored');
 assert.equal(r.get('.github/continuum/noisy.mjs#triple').matched,1,'the module result itself is still read');
});

test('a module that imports from elsewhere in the repository is probed: read access covers the repository',()=>{
 const root=fixture();
 const r=probeCandidates({root,candidates:[C('uses-scripts.mjs','plusTwo')],cases:[{args:[1],expected:3},{args:[5],expected:7}]});
 assert.deepEqual(r.get('.github/continuum/uses-scripts.mjs#plusTwo'),{matched:2,total:2,errors:0,firstError:null});
});

test('network use reached through repository imports is refused at run time and never reaches the network',async()=>{
 const root=fixture();
 let connections=0;
 const server=net.createServer(s=>{connections++;s.destroy();});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const port=server.address().port;
 const r=probeCandidates({root,candidates:['viaHttp','viaNet','viaNamed'].map(e=>C('indirect-net.mjs',e)),cases:[{args:[port],expected:'sent'}]});
 await new Promise(r=>setTimeout(r,300));
 for(const e of ['viaHttp','viaNet','viaNamed']){
  const p=r.get('.github/continuum/indirect-net.mjs#'+e);
  assert.equal(p.matched,0,e);
  assert.equal(p.firstError,'ERR_PROBE_NETWORK',e);
 }
 assert.equal(connections,0,'no probe connection reached the local server');
 // control: the server does count a real connection
 await new Promise(r=>http.get('http://127.0.0.1:'+port+'/').on('error',()=>r()).on('close',r));
 await new Promise(r=>setTimeout(r,100));
 server.close();
 assert.equal(connections,1);
});

test('a module path outside the repository is not probed',()=>{
 const root=fixture();
 const r=probeCandidates({root,candidates:[{module:'../outside.mjs',export:'x',required:1,max:1,description:''}],cases:[{args:[1],expected:1}]});
 assert.equal(r.get('../outside.mjs#x').skipped,'outside the repository');
});

test('a candidate cannot signal other processes; existence checks still work',async()=>{
 const root=fixture();
 const victim=spawn(process.execPath,['-e','setTimeout(()=>{},20000)'],{stdio:'ignore'});
 try{
  const r=probeCandidates({root,candidates:[C('signal.mjs','stop'),C('signal.mjs','alive')],cases:[{args:[victim.pid],expected:'stopped'}]});
  assert.equal(r.get('.github/continuum/signal.mjs#stop').firstError,'ERR_PROBE_SIGNAL');
  assert.equal(r.get('.github/continuum/signal.mjs#alive').errors,0,'signal 0 is an existence check');
  await new Promise(r=>setTimeout(r,200));
  assert.equal(victim.exitCode,null,'the other process is still running');
  assert.equal(victim.signalCode,null);
 }finally{victim.kill();}
});
