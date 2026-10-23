// Execution-guided candidate ranking for the CCC1 reasoner (NF-5b; idea after execution-guided programming by
// example, Verbruggen et al., ICLR 2025). Before any coding job, the candidates are called on the goal's examples in
// a separate Node process per module under the permission model: read access only to that module and the child
// script, no file writes, no child processes, no workers, 3 s per module. Modules that import network modules are
// not probed at all (Node 22's permission model does not restrict the network). Candidates whose results equal every
// expected value rank first; the base ranking (lexical) orders everything else. The probe never confirms anything:
// a hypothesis is still decided only by the independent contract-test receipt.
// Threat model: the permission model checks paths, so a candidate can still write to descriptors that are already open
// in the child (found by this module's tests) and could manipulate its own ranking from inside the child. Both can
// only reorder hypotheses; confirmation stays with the contract test in an isolated worktree. Results are read only
// after a random per-probe marker, so text a candidate prints cannot pass as a result.
import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {randomBytes} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {LexicalLanguageCore,interpretGoal} from './ccc1-interpreter.mjs';

const CHILD=path.join(path.dirname(fileURLToPath(import.meta.url)),'behaviour-probe-child.mjs');
const NETWORK=/(?:from\s+|import\s*\(\s*|require\s*\(\s*)['"](?:node:)?(?:net|http|https|http2|dgram|tls|dns|child_process|worker_threads|cluster)['"]/;

/** Per candidate key `module#export`: {matched, total, errors, firstError} or {skipped}. */
export function probeCandidates({root,candidates,cases,timeoutMs=3000}){
 const out=new Map();
 if(!cases?.length)return out;
 const byModule=new Map();
 for(const c of candidates){if(!byModule.has(c.module))byModule.set(c.module,[]);byModule.get(c.module).push(c.export);}
 for(const [module,exports] of byModule){
  const file=path.resolve(root,module);
  const src=fs.existsSync(file)?fs.readFileSync(file,'utf8'):'';
  if(!src||NETWORK.test(src)){for(const e of exports)out.set(module+'#'+e,{skipped:src?'imports a network or process module':'missing'});continue;}
  // read access to the module's directory (sibling imports) and the child script; nothing may be written
  const nonce='probe-'+randomBytes(12).toString('hex')+':';
  const r=spawnSync(process.execPath,['--permission','--allow-fs-read='+path.dirname(file),'--allow-fs-read='+CHILD,CHILD],
   {input:JSON.stringify({file,exports,cases,nonce}),encoding:'utf8',timeout:timeoutMs,cwd:path.dirname(file),
    env:{PATH:process.env.PATH??'',HOME:path.dirname(file)}});
  let parsed=null;
  const at=(r.stdout??'').lastIndexOf(nonce);
  try{if(at>=0)parsed=JSON.parse(r.stdout.slice(at+nonce.length));}catch{}
  for(const e of exports)out.set(module+'#'+e,parsed?.[e]??{skipped:r.error?.code==='ETIMEDOUT'?'timeout':'probe failed'});
 }
 return out;
}

/** LanguageCore that puts candidates matching every example first, keeping the base order among equals. */
export class ProbeLanguageCore{
 constructor({root,base=new LexicalLanguageCore(),weight=100,timeoutMs=3000}){
  this.id='probe-v1';this.root=root;this.base=base;this.weight=weight;this.timeoutMs=timeoutMs;this.last=null;
 }
 async rank(goalText,candidates){
  const base=await this.base.rank(goalText,candidates);
  const spec=interpretGoal({text:goalText});
  const usable=candidates.filter(c=>spec.arity!==null&&spec.arity>=c.required&&spec.arity<=c.max);
  const probe=probeCandidates({root:this.root,candidates:usable,cases:spec.acceptance,timeoutMs:this.timeoutMs});
  this.last=probe;
  return candidates.map((c,i)=>{
   const p=probe.get(c.module+'#'+c.export);
   const full=p&&!p.skipped&&p.total>0&&p.matched===p.total;
   return base[i]+(full?this.weight:0)+(p&&!p.skipped&&p.total>0?p.matched/p.total:0);
  });
 }
}
