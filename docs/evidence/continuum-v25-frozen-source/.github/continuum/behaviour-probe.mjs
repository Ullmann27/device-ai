// Execution-guided candidate ranking for the CCC1 reasoner (NF-5b; idea after execution-guided programming by
// example, Verbruggen et al., ICLR 2025). Before any coding job, the candidates are called on the goal's examples in
// a separate Node process per module under the permission model: read access only to the repository and the child
// script, no file writes, no child processes, no workers, no signals to other processes, 256 MB heap, 3 s per module.
// Modules that import network modules are not probed at all (Node 22's permission model does not restrict the
// network), and in the child every network API throws ERR_PROBE_NETWORK, also for code reached through imports
// elsewhere in the repository. Candidates whose results equal every expected value rank first; the base ranking
// (lexical) orders everything else. The probe never confirms anything: a hypothesis is still decided only by the
// independent contract-test receipt.
// NF-5c: when several candidates meet every example, they are run on further primitive inputs derived from the
// examples (distinguishing inputs, as in oracle-guided synthesis, Jha et al., ICSE 2010). If their results differ,
// the reasoner asks the owner which result is wanted instead of letting the contract test confirm one of them.
// v2 (after the NF-5c study, which measured v1): read access covers the repository, because modules that import from
// elsewhere in it were invisible to the comparison (found in the first real worker run), and candidates the probe still
// cannot run are reported with the decision instead of being dropped silently.
// Threat model: the permission model checks paths, so a candidate can still write to descriptors that are already open
// in the child (found by this module's tests) and could manipulate its own ranking from inside the child. Both can
// only reorder hypotheses or raise a question; confirmation stays with the contract test in an isolated worktree.
// Results are read only after a random per-probe marker, so text a candidate prints cannot pass as a result.
import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {randomBytes} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {LexicalLanguageCore,interpretGoal} from './ccc1-interpreter.mjs';

const CHILD=path.join(path.dirname(fileURLToPath(import.meta.url)),'behaviour-probe-child.mjs');
const NETWORK=/(?:from\s+|import\s*\(\s*|require\s*\(\s*)['"](?:node:)?(?:net|http|https|http2|dgram|tls|dns|child_process|worker_threads|cluster)['"]/;
const key=c=>c.module+'#'+c.export;

/** Runs the child once per module; returns module#export -> child result, or {skipped} for modules not run. */
function runByModule({root,candidates,cases,timeoutMs,mode}){
 const out=new Map();
 const byModule=new Map();
 for(const c of candidates){if(!byModule.has(c.module))byModule.set(c.module,[]);byModule.get(c.module).push(c.export);}
 const repo=fs.realpathSync(path.resolve(root));
 for(const [module,exports] of byModule){
  const file=path.resolve(repo,module);
  if(!file.startsWith(repo+path.sep)){for(const e of exports)out.set(module+'#'+e,{skipped:'outside the repository'});continue;}
  const src=fs.existsSync(file)?fs.readFileSync(file,'utf8'):'';
  if(!src||NETWORK.test(src)){for(const e of exports)out.set(module+'#'+e,{skipped:src?'imports a network or process module':'missing'});continue;}
  // read access to the repository (imports anywhere in it) and the child script; nothing may be written
  const nonce='probe-'+randomBytes(12).toString('hex')+':';
  const r=spawnSync(process.execPath,['--permission','--allow-fs-read='+repo,'--allow-fs-read='+CHILD,'--max-old-space-size=256',CHILD],
   {input:JSON.stringify({file,exports,cases,nonce,mode}),encoding:'utf8',timeout:timeoutMs,cwd:path.dirname(file),
    env:{PATH:process.env.PATH??'',HOME:path.dirname(file)}});
  let parsed=null;
  const at=(r.stdout??'').lastIndexOf(nonce);
  try{if(at>=0)parsed=JSON.parse(r.stdout.slice(at+nonce.length));}catch{}
  for(const e of exports)out.set(module+'#'+e,parsed?.[e]??{skipped:r.error?.code==='ETIMEDOUT'?'timeout':'probe failed'});
 }
 return out;
}

/** Per candidate key `module#export`: {matched, total, errors, firstError} or {skipped}. */
export function probeCandidates({root,candidates,cases,timeoutMs=3000}){
 if(!cases?.length)return new Map();
 return runByModule({root,candidates,cases,timeoutMs,mode:'match'});
}

/** Per candidate key: one typed observation per input ({value, primitive, json?} or {error}), or {skipped}. */
export function observeOutputs({root,candidates,inputs,timeoutMs=3000}){
 if(!inputs?.length)return new Map();
 return runByModule({root,candidates,cases:inputs.map(args=>({args,expected:null})),timeoutMs,mode:'outputs'});
}

const STRINGS=['','a','A','abc','ABC','Hallo Welt','a-b-c','x_y','a.b','Ä','ß','€',' ','a b','\n','1.5','-1','0','true','null'];
const NUMBERS=[0,1,-1,2,7,1.5,-2.5,100,255,256,1e6,2147483648,Number.MAX_SAFE_INTEGER];
const isPrimitiveArg=v=>v===null||typeof v==='boolean'||typeof v==='number'&&Number.isFinite(v)||typeof v==='string'&&v.length<=120;

/**
 * Further primitive inputs for the arguments of [acceptance]: variants of the example values first, then standard
 * values of the same type, then other types; one argument position varies at a time. Deterministic, at most [max].
 */
export function probeInputs(acceptance,max=48){
 if(!acceptance?.length)return [];
 const arity=acceptance[0].args.length;
 const seen=new Set(acceptance.map(c=>JSON.stringify(c.args)));
 const out=[];
 const push=args=>{const k=JSON.stringify(args);if(args.every(isPrimitiveArg)&&!seen.has(k)){seen.add(k);out.push(args);}};
 for(let i=0;i<arity;i++){
  const ex=acceptance.map(c=>c.args[i]);
  const same=[];
  for(const v of ex){
   if(typeof v==='string')same.push(v.toUpperCase(),v.toLowerCase(),v+v,[...v].reverse().join(''),v+' ',' '+v,v+'\n',v.slice(1),v.slice(0,-1),v.replace(/[a-z]/i,'-'));
   if(typeof v==='number')same.push(v+1,v-1,v*2,-v,v+.5,0);
   if(typeof v==='boolean')same.push(!v);
  }
  const t=typeof ex[0];
  const standard=t==='string'?STRINGS:t==='number'?NUMBERS:t==='boolean'?[true,false]:[];
  const other=[...(t==='string'?NUMBERS.slice(0,6):STRINGS.slice(0,8)),true,false,null];
  for(const v of [...same,...standard,...other]){const args=[...acceptance[0].args];args[i]=v;push(args);}
 }
 return out.slice(0,max);
}

const sig=o=>!o||o.skipped?'unobserved':'error' in o?'error:'+o.error:'value:'+o.value;

/**
 * Do the [candidates] (all meeting every example) behave differently on further inputs? Returns the most useful
 * distinguishing input (every result usable as an expected value, else at least one, else any) with the groups of
 * candidates per result, or {ambiguous:false} when they agree on every input tried.
 */
export function findAmbiguity({root,candidates,acceptance,timeoutMs=3000,maxInputs=48}){
 const inputs=probeInputs(acceptance,maxInputs);
 if(candidates.length<2||!inputs.length)return {ambiguous:false,inputsTried:inputs.length};
 const obs=observeOutputs({root,candidates,inputs,timeoutMs});
 const found=[];
 inputs.forEach((args,k)=>{
  const groups=new Map();
  for(const c of candidates){
   const o=obs.get(key(c));
   const at=Array.isArray(o)?o[k]:o;
   const s=sig(at);
   if(!groups.has(s))groups.set(s,{result:at&&!at.skipped?('error' in at?'Fehler '+at.error:at.value):'nicht beobachtbar',
    expected:at?.primitive?at.json:undefined,usable:!!at?.primitive,candidates:[]});
   groups.get(s).candidates.push(key(c));
  }
  if(groups.size>1)found.push({input:args,groups:[...groups.values()]});
 });
 if(!found.length)return {ambiguous:false,inputsTried:inputs.length};
 const best=found.find(f=>f.groups.every(g=>g.usable))??found.find(f=>f.groups.some(g=>g.usable))??found[0];
 return {ambiguous:true,inputsTried:inputs.length,distinguishing:found.length,...best};
}

const short=k=>k.replace('.github/continuum/','');
const fmt=v=>typeof v==='string'?JSON.stringify(v):String(v);

/** The owner question for an ambiguity, within the 600 characters of an ABSTAIN reason. */
export function ambiguityQuestion(a){
 const parts=a.groups.slice(0,3).map(g=>`${g.result.slice(0,60)} (${g.candidates.slice(0,2).map(short).join(', ')}${g.candidates.length>2?' …':''})`);
 const text=`Mehrdeutig: ${a.groups.reduce((n,g)=>n+g.candidates.length,0)} Funktionen erfüllen alle Beispiele, verhalten sich aber verschieden. `+
  `Bei (${a.input.map(fmt).join(', ').slice(0,80)}) ergibt sich: ${parts.join('; ')}. Welches Ergebnis ist gewollt? Bis zur Antwort wird nichts bestätigt.`;
 return text.length<=600?text:text.slice(0,597)+'…';
}

/** LanguageCore that puts candidates matching every example first, keeping the base order among equals. */
export class ProbeLanguageCore{
 constructor({root,base=new LexicalLanguageCore(),weight=100,timeoutMs=3000}){
  this.id='probe-v1';this.root=root;this.base=base;this.weight=weight;this.timeoutMs=timeoutMs;this.last=null;
 }
 async rank(goalText,candidates){
  const base=await this.base.rank(goalText,candidates);
  this.lastBase=new Map(candidates.map((c,i)=>[key(c),base[i]]));
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

/** A probe result that says nothing about the candidate's behaviour: not run, not loadable, or every call refused. */
export function unrunnable(p){
 return !p||Boolean(p.skipped)||Boolean(p.unloadable)||
  p.total>0&&p.errors===p.total&&/^(ERR_ACCESS_DENIED|ERR_PROBE_NETWORK|ERR_PROBE_SIGNAL)/.test(p.firstError??'');
}

/**
 * NF-5c: the probe core plus a clarification step. Before the first hypothesis, the reasoner asks [clarify]; when
 * the checkable candidates meeting every example behave differently elsewhere, the answer is an owner question and
 * nothing is proposed or confirmed. [lastAmbiguity] keeps the evidence for reports, including the candidates the
 * probe could not run although the base ranking puts them at least as high as the best checked match (unchecked):
 * they cannot be compared, so they travel with the decision instead of disappearing.
 */
export class DisambiguatingProbeCore extends ProbeLanguageCore{
 constructor(options){super(options);this.id='probe-disambiguate-v2';this.lastAmbiguity=null;}
 async clarify(spec,candidates){
  const p=c=>this.last?.get(key(c));
  const full=candidates.filter(c=>{const r=p(c);return r&&!r.skipped&&r.total>0&&r.matched===r.total;});
  this.lastAmbiguity=null;
  if(!full.length)return null;
  const base=c=>this.lastBase?.get(key(c))??0;
  const best=Math.max(...full.map(base));
  const unchecked=candidates.filter(c=>unrunnable(p(c))&&base(c)>=best)
   .map(c=>({candidate:key(c),reason:String(p(c)?.skipped??p(c)?.firstError??'not probed').slice(0,120)}));
  if(full.length<2){this.lastAmbiguity={ambiguous:false,full:full.map(key),unchecked};return null;}
  const a=findAmbiguity({root:this.root,candidates:full,acceptance:spec.acceptance,timeoutMs:this.timeoutMs});
  this.lastAmbiguity={...a,full:full.map(key),unchecked};
  return a.ambiguous?ambiguityQuestion(a):null;
 }
}
