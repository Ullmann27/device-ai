#!/usr/bin/env node
// NF-5c new tasks, mined mechanically before any NF-5c study run (docs/CONTINUUM-NEURAL-FRONTIER-LAB-20261022.md,
// section 8e). Modules: every .github/continuum module at the pinned commit that passes the probe's static
// network/process check (the reasoner's own ccc1/behaviour-probe modules excluded). Gold functions: exports that were
// never a task or a known equivalent in NF-5/NF-5b, callable with one argument (fixed pool) or else two (fixed pair
// pool); each runs twice in the unchanged probe sandbox; eligible are deterministic exports with two different usable
// results. Examples are the first usable pool result and the next one that differs. Selection: seeded order, at most
// 12 tasks (all eligible; the first draft took one per module). Equivalents of a gold function: exports with identical results on the pool, on
// probeInputs(examples) and on 40 seeded random inputs - a wider input set than NF-5c itself tries.
// Iteration before any run (documented): unused modules with one argument only gave 1 eligible function of 117.
// Nothing here runs a coding job or a reasoner. Usage: node scripts/nf5c-task-miner.mjs <outFile> [commit]
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {indexExports} from '../.github/continuum/ccc1-interpreter.mjs';
import {observeOutputs,probeInputs} from '../.github/continuum/behaviour-probe.mjs';

const ROOT=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
export const COMMIT='9fb4361c';
/** Gold functions and known equivalents of the NF-5/NF-5b tasks (historical group); never new gold functions. */
export const USED_GOLDS=['development-backlog#sha256Hex','portable-subword#sha256','cre-encoder#sha256','repair-diagnostics#sha256',
 'development-backlog#clip','development-backlog#singleLine','development-backlog#repoPath','development-backlog#canonical',
 'capability-research#canonical','development-backlog#findingTaskId','kotlin-tokenizer#isKeyword','console-policy#isOwnerComment',
 'capability-research#contextCost','capability-research#digest','relational-memory-data#fingerprint','cre-encoder#bucket',
 'dialogue-retrieval#wordEvidence','cre-encoder#relationOf','cre-encoder#subjectOf'].map(k=>'.github/continuum/'+k.replace('#','.mjs#'));
const NETWORK=/(?:from\s+|import\s*\(\s*|require\s*\(\s*)['"](?:node:)?(?:net|http|https|http2|dgram|tls|dns|child_process|worker_threads|cluster)['"]/;
export const POOL=['abc','Hallo Welt','a-b-c','x_y','Ä','','42','der Hund läuft',0,1,2,7,42,-3,1.5,true,false,null].map(v=>[v]);
export const POOL2=[['abc',2],['Hallo Welt',5],['a-b-c',1],['x_y',0],['der Hund läuft',3],['abc','b'],['Hallo Welt','Welt'],
 ['a-b-c','-'],[2,3],[7,-3],[1.5,2],['abc',true]];
const usable=o=>o&&!o.skipped&&o.primitive===true&&!Object.is(o.json,-0);
const sig=o=>!o||o.skipped?'unobserved':'error' in o?'error:'+o.error:'value:'+o.value;

/** Copies all non-test modules of the pinned commit into a fresh fixture; returns its root. */
export function fixtureAt(commit,dir){
 const root=path.join(dir,'device-ai'),target=path.join(root,'.github','continuum');
 fs.mkdirSync(target,{recursive:true});
 const ls=spawnSync('git',['ls-tree','--name-only',commit+':.github/continuum'],{cwd:ROOT,encoding:'utf8'});
 if(ls.status!==0)throw Error(ls.stderr);
 for(const name of ls.stdout.split('\n').filter(n=>n.endsWith('.mjs')&&!n.endsWith('.test.mjs'))){
  const r=spawnSync('git',['show',commit+':.github/continuum/'+name],{cwd:ROOT,maxBuffer:1<<26});
  if(r.status!==0)throw Error('git show '+name);
  fs.writeFileSync(path.join(target,name),r.stdout);
 }
 return root;
}

function randomInputs(seed,n){
 let s=createHash('sha256').update(seed).digest().readUInt32BE(0)||1;
 const next=()=>{s^=s<<13;s>>>=0;s^=s>>>17;s^=s<<5;s>>>=0;return s;};
 const chars='abcxyzABCXYZ0123456789 -_.,:äöüß€/';
 return Array.from({length:n},(_,i)=>{
  const r=next();
  if(i%3===0)return [((r%2000)-1000)/(r%5===0?8:1)];
  return [Array.from({length:1+r%12},()=>chars[next()%chars.length]).join('')];
 });
}

async function main(){
 const out=path.resolve(process.argv[2]??'nf5c-tasks.json');
 const commit=process.argv[3]??COMMIT;
 const home=fs.mkdtempSync(path.join(os.tmpdir(),'nf5c-mine-'));
 const root=fixtureAt(commit,home);
 const all=indexExports(root);
 const modules=[...new Set(all.map(c=>c.module))].filter(m=>!/^(ccc1-|behaviour-probe)/.test(path.basename(m))&&
  !NETWORK.test(fs.readFileSync(path.join(root,m),'utf8')));
 const key=c=>c.module+'#'+c.export;
 const fresh=all.filter(c=>modules.includes(c.module)&&!USED_GOLDS.includes(key(c)));
 const one=fresh.filter(c=>c.required<=1&&c.max>=1),two=fresh.filter(c=>c.required<=2&&c.max>=2);
 const observe=(cs,pool)=>[observeOutputs({root,candidates:cs,inputs:pool,timeoutMs:20000}),observeOutputs({root,candidates:cs,inputs:pool,timeoutMs:20000})];
 const [a1,b1]=observe(one,POOL),[a2,b2]=observe(two,POOL2);
 const eligible=[];
 const consider=(c,pool,a,b)=>{
  const oa=a.get(key(c)),ob=b.get(key(c));
  if(!Array.isArray(oa)||!Array.isArray(ob)||JSON.stringify(oa)!==JSON.stringify(ob))return false;   // unobservable or nondeterministic
  const first=oa.findIndex(usable);
  if(first<0)return false;
  const second=oa.findIndex((o,k)=>k>first&&usable(o)&&o.value!==oa[first].value);
  if(second<0)return false;
  eligible.push({...c,arity:pool[0].length,examples:[first,second].map(k=>({args:pool[k],expected:oa[k].json}))});
  return true;
 };
 for(const c of fresh){
  if(one.includes(c)&&consider(c,POOL,a1,b1))continue;
  if(two.includes(c))consider(c,POOL2,a2,b2);
 }
 const callable=[...new Set([...one,...two])];
 const order=eligible.map(c=>({c,h:createHash('sha256').update('nf5c\0'+key(c)).digest('hex')})).sort((x,y)=>x.h.localeCompare(y.h)).map(x=>x.c);
 const picked=order.slice(0,12);   // all eligible functions (there are few), in seeded order
 // equivalents: identical results on the pool, on the NF-5c inputs and on 40 seeded random inputs
 const tasks=[];
 for(const c of picked){
  const pool=c.arity===1?POOL:POOL2;
  const random=randomInputs('nf5c-eq\0'+key(c),40);
  const wide=[...pool,...probeInputs(c.examples),...(c.arity===1?random:random.map((r,i)=>[r[0],pool[i%pool.length][1]]))];
  const peers=callable.filter(o=>o.required<=c.arity&&o.max>=c.arity);
  const obs=observeOutputs({root,candidates:peers,inputs:wide,timeoutMs:20000});
  const gold=obs.get(key(c));
  if(!Array.isArray(gold))throw Error('gold not observable on the wide inputs: '+key(c));
  const equivalent=peers.filter(o=>key(o)!==key(c)&&JSON.stringify((obs.get(key(o))??[]).map?.(sig))===JSON.stringify(gold.map(sig))).map(key);
  // other callables that also meet both examples (the ambiguity the examples leave open)
  const ex=observeOutputs({root,candidates:peers,inputs:c.examples.map(e=>e.args),timeoutMs:20000});
  const meets=peers.filter(o=>key(o)!==key(c)&&c.examples.every((e,j)=>{const r=ex.get(key(o))?.[j];return usable(r)&&JSON.stringify(r.json)===JSON.stringify(e.expected);})).map(key);
  tasks.push({gold:key(c),arity:c.arity,equivalent,alsoMeetExamples:meets,examples:c.examples,description:c.description});
 }
 const doc={commit,mined:new Date().toISOString(),modules:modules.length,callable:callable.length,eligible:eligible.length,
  pool:POOL,pool2:POOL2,tasks};
 fs.writeFileSync(out,JSON.stringify(doc,null,1)+'\n');
 console.log(JSON.stringify({commit,modules:modules.length,callable:callable.length,eligible:eligible.length,picked:tasks.length}));
 for(const t of tasks)console.log(t.gold,JSON.stringify(t.examples),'equivalent',t.equivalent.length,'alsoMeet',t.alsoMeetExamples.length);
 fs.rmSync(home,{recursive:true,force:true});
}
if(process.argv[1]===fileURLToPath(import.meta.url))await main();
