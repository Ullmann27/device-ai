#!/usr/bin/env node
// Code semantic index (Milestone E): what every export of .github/continuum does, grounded in execution instead of
// names. Per export: signature, doc comment, the tests that import its module, and a behaviour fingerprint - its
// type-tagged results on a fixed pool of primitive inputs (POOL_VERSION), run twice through the NF-5c probe (no file
// writes, no processes, no signals, no network) so that non-deterministic exports are recognised. Exports with the
// same fingerprint cannot be told apart by any example from the pool (clusters); exports that agree on almost every
// input are near duplicates. Both are where an owner goal is most likely ambiguous, and both are refactoring hints.
// Module results are cached by the SHA-256 of the module and of every repository file it imports (transitively), so a
// rebuild probes only what changed (and everything again when the pool or the probe sandbox changes). Nothing is
// written into the repository: the index and its cache live in the worker's state directory. The index describes; it
// never confirms, ranks or changes code by itself.
// Usage:
//   node .github/continuum/code-index.mjs build --project <repo> --state <dir>
//   node .github/continuum/code-index.mjs show  --state <dir>
import fs from 'node:fs';
import path from 'node:path';
import {builtinModules} from 'node:module';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {indexExports} from './ccc1-interpreter.mjs';
import {observeOutputs} from './behaviour-probe.mjs';

export const POOL_VERSION='pool-v1';
/** Inputs of one-argument exports; two-argument exports get every pair of POOL_2. Changing either needs a new version. */
export const POOL_1=Object.freeze(['','a','A','abc','Hallo Welt','a-b-c','Ä','€',' ','\n','1.5','-1','0','true','null',
 0,1,-1,2,7,1.5,-2.5,100,255,256,1e6,true,false,null]);
export const POOL_2=Object.freeze(['','a','abc','Hallo Welt',0,1,-1,2.5,true,null]);
const NEAR=0.9;

const sha=s=>createHash('sha256').update(s).digest('hex');
const HERE=path.dirname(fileURLToPath(import.meta.url));
/** Cached fingerprints are only valid for the same pool and the same probe sandbox. */
const CACHE_KEY=()=>POOL_VERSION+':'+sha(['behaviour-probe.mjs','behaviour-probe-child.mjs'].map(f=>fs.readFileSync(path.join(HERE,f),'utf8')).join('\0'));
const BUILTIN=new Set(builtinModules);
const SPECIFIER=/\b(?:import|export)\s[^'"`;]*?\bfrom\s*['"]([^'"]+)['"]|\bimport\s*\(\s*['"]([^'"]+)['"]\s*\)|^\s*import\s*['"]([^'"]+)['"]/gm;

/** Arity used for the fingerprint: the required arguments, or one for exports whose arguments are all optional. */
export function fingerprintArity(c){
 if(c.required>=1)return c.required;
 return c.max>=1?1:0;
}

export function standardInputs(arity){
 if(arity===0)return [[]];
 if(arity===1)return POOL_1.map(v=>[v]);
 if(arity===2)return POOL_2.flatMap(a=>POOL_2.map(b=>[a,b]));
 return [];
}

/** Repository files a module reaches through relative imports (itself included), plus the builtins and packages it names. */
export function importClosure(repo,file){
 const files=new Set(),builtins=new Set(),packages=new Set();
 const visit=f=>{
  if(files.has(f)||!f.startsWith(repo+path.sep)||!fs.existsSync(f))return;
  files.add(f);
  for(const m of fs.readFileSync(f,'utf8').matchAll(SPECIFIER)){
   const s=m[1]??m[2]??m[3];
   if(s.startsWith('./')||s.startsWith('../'))visit(path.resolve(path.dirname(f),s));
   else if(s.startsWith('node:')||BUILTIN.has(s.split('/')[0]))builtins.add(s.replace(/^node:/,''));
   else packages.add(s);
  }
 };
 visit(file);
 return {files:[...files].map(f=>path.relative(repo,f)).sort(),builtins:[...builtins].sort(),packages:[...packages].sort()};
}

const shown=o=>!o||o.skipped?'?':'error' in o?'!'+o.error:o.value;

/** Fingerprint of observations on the doubled pool: equal halves mean deterministic on the pool. */
export function fingerprintOf(arity,observations){
 if(!Array.isArray(observations))return {runnable:false,reason:observations?.skipped??'not probed'};
 const n=observations.length/2;
 const first=observations.slice(0,n),second=observations.slice(n);
 if(first.length&&first.every(o=>o.unloadable))return {runnable:false,reason:'not loadable: '+first[0].error};
 const errors=first.filter(o=>'error' in o).length;
 if(errors===n&&first.every(o=>/^(ERR_ACCESS_DENIED|ERR_PROBE_NETWORK|ERR_PROBE_SIGNAL)$/.test(o.error)))
  return {runnable:false,reason:'every call needs a refused capability ('+first[0].error+')'};
 const values=first.map(shown);
 return {runnable:true,reason:null,deterministic:values.every((v,i)=>v===shown(second[i])),errorRate:errors/n,
  fingerprint:sha(JSON.stringify([POOL_VERSION,arity,values])),values};
}

function readJson(file,fallback){try{return JSON.parse(fs.readFileSync(file,'utf8'));}catch{return fallback;}}
function writeJson(file,value){
 fs.mkdirSync(path.dirname(file),{recursive:true,mode:0o700});
 const tmp=file+'.'+process.pid+'.tmp';
 fs.writeFileSync(tmp,JSON.stringify(value),{mode:0o600});
 fs.renameSync(tmp,file);
}

/**
 * Builds the index of [root]; with [cacheFile], unchanged modules (same closure hash) are taken from the cache. Returns
 * {schema, version, poolVersion, exports, modules, clusters, nearDuplicates, stats}.
 */
export function buildCodeIndex({root,cacheFile=null,timeoutMs=5000}){
 const t0=Date.now();
 const repo=fs.realpathSync(path.resolve(root));
 const all=indexExports(repo);
 const testFiles=fs.readdirSync(path.join(repo,'.github','continuum')).filter(f=>f.endsWith('.test.mjs')).sort()
  .map(f=>path.join(repo,'.github','continuum',f));
 const testsOf=new Map();
 for(const t of testFiles)for(const f of importClosure(repo,t).files)
  if(f!==path.relative(repo,t)){if(!testsOf.has(f))testsOf.set(f,[]);testsOf.get(f).push(path.relative(repo,t));}
 const cacheKey=CACHE_KEY();
 const cache=cacheFile?readJson(cacheFile,{schema:'continuum-code-index-cache',cacheKey,entries:{}}):null;
 if(cache&&cache.cacheKey!==cacheKey){cache.cacheKey=cacheKey;cache.entries={};}
 const byModule=new Map();
 for(const c of all){if(!byModule.has(c.module))byModule.set(c.module,[]);byModule.get(c.module).push(c);}
 const modules=[],exports=[];
 let probed=0;
 for(const [module,candidates] of byModule){
  const closure=importClosure(repo,path.join(repo,module));
  const closureSha256=sha(JSON.stringify(closure.files.map(f=>[f,sha(fs.readFileSync(path.join(repo,f)))])));
  modules.push({module,closureSha256,files:closure.files.length,builtins:closure.builtins,packages:closure.packages,
   tests:testsOf.get(module)??[]});
  let entries=cache?.entries[closureSha256];
  if(!entries){
   probed++;
   entries={};
   const byArity=new Map();
   for(const c of candidates){const a=fingerprintArity(c);if(!byArity.has(a))byArity.set(a,[]);byArity.get(a).push(c);}
   for(const [arity,group] of byArity){
    const inputs=standardInputs(arity);
    if(!inputs.length){for(const c of group)entries[c.export]={arity,runnable:false,reason:'more than two arguments'};continue;}
    const obs=observeOutputs({root:repo,candidates:group,inputs:[...inputs,...inputs],timeoutMs});
    for(const c of group)entries[c.export]={arity,...fingerprintOf(arity,obs.get(module+'#'+c.export))};
   }
   if(cache)cache.entries[closureSha256]=entries;
  }
  for(const c of candidates)exports.push({module,export:c.export,required:c.required,max:Number.isFinite(c.max)?c.max:null,
   description:c.description,tests:testsOf.get(module)??[],...entries[c.export]});
 }
 if(cache){
  const live=new Set(modules.map(m=>m.closureSha256));
  for(const k of Object.keys(cache.entries))if(!live.has(k))delete cache.entries[k];
  writeJson(cacheFile,cache);
 }
 const comparable=exports.filter(e=>e.runnable&&e.deterministic&&e.errorRate<1);
 const groups=new Map();
 for(const e of comparable){if(!groups.has(e.fingerprint))groups.set(e.fingerprint,[]);groups.get(e.fingerprint).push(e.module+'#'+e.export);}
 const clusters=[...groups.values()].filter(g=>g.length>1).sort((a,b)=>b.length-a.length||a[0].localeCompare(b[0]));
 const nearDuplicates=[];
 for(let i=0;i<comparable.length;i++)for(let j=i+1;j<comparable.length;j++){
  const a=comparable[i],b=comparable[j];
  if(a.arity!==b.arity||a.fingerprint===b.fingerprint)continue;
  let same=0;for(let k=0;k<a.values.length;k++)if(a.values[k]===b.values[k])same++;
  const agreement=same/a.values.length;
  if(agreement>=NEAR)nearDuplicates.push({a:a.module+'#'+a.export,b:b.module+'#'+b.export,agreement:Math.round(agreement*1000)/1000});
 }
 nearDuplicates.sort((x,y)=>y.agreement-x.agreement||x.a.localeCompare(y.a)||x.b.localeCompare(y.b));
 for(const e of exports)delete e.values;
 const runnable=exports.filter(e=>e.runnable);
 return {schema:'continuum-code-index',version:1,poolVersion:POOL_VERSION,exports,modules,clusters,nearDuplicates,
  stats:{modules:modules.length,exports:exports.length,runnable:runnable.length,deterministic:runnable.filter(e=>e.deterministic).length,
   withTests:exports.filter(e=>e.tests.length).length,clusters:clusters.length,nearDuplicates:nearDuplicates.length,
   modulesProbed:probed,seconds:Math.round((Date.now()-t0)/100)/10}};
}

/** Writes the index next to its cache in the state directory (atomically, owner-only). */
export function saveCodeIndex(stateDir,index){
 const file=path.join(path.resolve(stateDir),'code-index.json');
 writeJson(file,index);
 return file;
}

function outside(project,stateDir){
 const p=fs.realpathSync(project),s=path.resolve(stateDir);
 if(s===p||s.startsWith(p+path.sep))throw Error('the index state must stay outside the repository');
}

export async function main(argv=process.argv.slice(2),out=console.log){
 const [command,...rest]=argv;
 const o={};
 for(let i=0;i<rest.length;i+=2){
  if(!rest[i]?.startsWith('--')||rest[i+1]===undefined)throw Error('expected --name value pairs');
  o[rest[i].slice(2)]=rest[i+1];
 }
 if(!o.state)throw Error('--state <dir> is required');
 const file=path.join(path.resolve(o.state),'code-index.json');
 if(command==='build'){
  if(!o.project)throw Error('build needs --project');
  outside(o.project,o.state);
  const index=buildCodeIndex({root:o.project,cacheFile:path.join(path.resolve(o.state),'code-index-cache.json')});
  saveCodeIndex(o.state,index);
  out(JSON.stringify(index.stats));
  return index;
 }
 if(command==='show'){
  const index=readJson(file,null);
  if(!index)throw Error('no index in '+o.state);
  out(JSON.stringify(index.stats));
  for(const c of index.clusters)out('cluster '+c.join(' = '));
  for(const d of index.nearDuplicates.slice(0,20))out(`near ${d.agreement} ${d.a} ~ ${d.b}`);
  return index;
 }
 throw Error('unknown command '+command);
}

if(process.argv[1]===fileURLToPath(import.meta.url))main().catch(e=>{console.error(String(e?.message??e));process.exitCode=1;});
