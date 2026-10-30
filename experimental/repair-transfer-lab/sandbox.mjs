/** Executes a project test file against a candidate version of ONE module, in a fresh temporary copy of exactly
 * the files the test needs (transitive relative imports + URL-referenced fixtures). Fresh Node processes, minimal
 * environment, hard timeout. Results are cached by content hash: the same source + test + selection is executed
 * once, every arm that would run it is charged the run and its measured time. Not an OS sandbox. */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
const sha=s=>createHash('sha256').update(s).digest('hex');
const PROBE=fileURLToPath(new URL('./rss-probe.mjs',import.meta.url));
const GROUP_RUN=fileURLToPath(new URL('./group-run.mjs',import.meta.url));
const IMPORT_RE=[/\bfrom\s*['"](\.{1,2}\/[^'"]+)['"]/g,/\bimport\s*['"](\.{1,2}\/[^'"]+)['"]/g,
 /\bimport\(\s*['"](\.{1,2}\/[^'"]+)['"]\s*\)/g,/new URL\(\s*['"](\.{1,2}\/[^'"]+)['"]/g];

/** Repo-relative files needed by `entry` (repo-relative), following relative imports transitively. */
export function closure(root,entry,limit=200) {
 const seen=new Set(),todo=[entry];
 while(todo.length){
  const rel=todo.pop();
  if(seen.has(rel))continue;
  const abs=path.join(root,rel);
  if(!fs.existsSync(abs)||!fs.statSync(abs).isFile())continue;
  seen.add(rel);
  if(seen.size>limit)throw Error('CLOSURE_TOO_LARGE');
  if(!/\.m?js$/.test(rel))continue;
  const src=fs.readFileSync(abs,'utf8');
  for(const re of IMPORT_RE)for(const m of src.matchAll(re)){
   const next=path.normalize(path.join(path.dirname(rel),m[1]));
   if(!next.startsWith('..'))todo.push(next);
  }
 }
 return [...seen].sort();
}

export function namePattern(names){
 return '^(?:'+names.map(n=>n.replace(/[.*+?^${}()|[\]\\]/g,'\\$&')).join('|')+')$';
}
/** Top-level test names in order, from a TAP log of a full passing run. */
export function topLevelNames(tap){
 return [...tap.matchAll(/^ok \d+ - (.*?)(?: # (?:SKIP|TODO).*)?$/gm)].map(m=>m[1]);
}

export class LabRunner {
 constructor({root,timeoutMs=20000}){this.root=root;this.timeoutMs=timeoutMs;this.cache=new Map();this.executions=0;this.closures=new Map();}
 files(testRel,extra=[]){
  const key=testRel+'\0'+extra.join('\0');
  if(!this.closures.has(key)){
   const set=new Set(closure(this.root,testRel));
   for(const dir of extra)for(const f of fs.readdirSync(path.join(this.root,dir),{recursive:true}))
    if(fs.statSync(path.join(this.root,dir,f)).isFile())set.add(path.join(dir,f));
   this.closures.set(key,[...set].sort());
  }
  return this.closures.get(key);
 }
 /** {passed, tests, pass, fail, ms, rssKb, log, cached} for test file testRel with moduleRel replaced by source. */
 run({testRel,moduleRel,source,names=null,extra=[]}) {
  const pattern=names?namePattern(names):null;
  const key=sha([testRel,moduleRel,sha(source),pattern??'*'].join('\0'));
  if(this.cache.has(key))return {...this.cache.get(key),cached:true};
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'repair-transfer-'));
  try{
   for(const rel of this.files(testRel,extra)){
    const target=path.join(dir,rel);fs.mkdirSync(path.dirname(target),{recursive:true});
    if(rel===moduleRel)fs.writeFileSync(target,source,{mode:0o600});else fs.copyFileSync(path.join(this.root,rel),target);
   }
   if(!this.files(testRel,extra).includes(moduleRel))throw Error('MODULE_NOT_IN_TEST_CLOSURE');
   const args=[GROUP_RUN,String(this.timeoutMs),'--test','--test-reporter=tap',...(pattern?['--test-name-pattern='+pattern]:[]),testRel];
   const t0=process.hrtime.bigint();
   const r=spawnSync(process.execPath,args,{cwd:dir,encoding:'utf8',timeout:this.timeoutMs+10000,maxBuffer:4_000_000,killSignal:'SIGKILL',
    env:{PATH:process.env.PATH??'',HOME:dir,TMPDIR:dir,LC_ALL:'C.UTF-8',NODE_OPTIONS:'--import='+PROBE}});
   const ms=Number(process.hrtime.bigint()-t0)/1e6;
   this.executions++;
   const log=(r.stdout??'')+(r.stderr??'');
   const num=k=>Number((new RegExp('^# '+k+' (\\d+)$','m').exec(r.stdout??'')??[])[1]??NaN);
   const tests=num('tests'),pass=num('pass'),fail=num('fail');
   const rssKb=Math.max(0,...[...log.matchAll(/LAB_MAXRSS_KB=(\d+)/g)].map(m=>Number(m[1])));
   // passed only if the process exited 0, ran >=1 test and the TAP counters agree (same rule as PR #243/#244 oracle)
   const passed=!r.signal&&!r.error&&r.status===0&&tests>0&&pass===tests&&fail===0;   // 124 = group killed on timeout
   const result={passed,status:r.signal||r.status===124?'TIMEOUT':r.error?'ERROR':r.status,tests,pass,fail,ms,rssKb,log:log.slice(0,6000)};
   this.cache.set(key,result);
   return {...result,cached:false};
  }finally{fs.rmSync(dir,{recursive:true,force:true});}
 }
}
