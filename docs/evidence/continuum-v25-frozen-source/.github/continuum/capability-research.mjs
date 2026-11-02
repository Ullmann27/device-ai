/** Bounded, reporting-only algorithms. No I/O, eval, training or promotion. */
import crypto from 'node:crypto';
import {SubwordEncoder} from './portable-subword.mjs';
import {rankMemories} from './dialogue-retrieval.mjs';
import {isLocalNumericPatch} from './local-numeric-patch.mjs';

export function canonical(value) {
  let nodes=0;
  const visit=(v,depth=0)=>{
    if(++nodes>100000||depth>24)throw Error('RESEARCH_VALUE_LIMIT');
    if(v===null||typeof v==='string'||typeof v==='boolean')return v;
    if(typeof v==='number'&&Number.isFinite(v))return v;
    if(Array.isArray(v))return v.map(x=>visit(x,depth+1));
    if(v&&typeof v==='object'&&[Object.prototype,null].includes(Object.getPrototypeOf(v)))
      return Object.fromEntries(Object.keys(v).sort().map(k=>[k,visit(v[k],depth+1)]));
    throw Error('RESEARCH_NON_JSON_VALUE');
  };
  return JSON.stringify(visit(value));
}
export const digest=value=>crypto.createHash('sha256').update(canonical(value)).digest('hex');
const copy=value=>JSON.parse(canonical(value));
const freeze=value=>{
  if(value&&typeof value==='object'){for(const v of Object.values(value))freeze(v);Object.freeze(value);}
  return value;
};
const BINARY=Object.freeze(['add','sub','min','max']);
const ARITY=Object.freeze({add:2,sub:2,min:2,max:2,abs:1,mul:2});

export function validateAst(ast) {
  let count=0;
  const check=(node,depth)=>{
    if(++count>63||depth>10)throw Error('RESEARCH_AST_LIMIT');
    if(node==='x'||node==='y'||(Number.isSafeInteger(node)&&Math.abs(node)<=16))return;
    if(!Array.isArray(node)||!Object.hasOwn(ARITY,node[0])||node.length!==ARITY[node[0]]+1)
      throw Error('RESEARCH_INVALID_AST');
    for(const child of node.slice(1))check(child,depth+1);
  };
  check(ast,0);return count;
}
function run(ast,x,y) {
  if(ast==='x')return x;if(ast==='y')return y;if(typeof ast==='number')return ast;
  const a=run(ast[1],x,y);if(ast[0]==='abs')return Math.abs(a);
  const b=run(ast[2],x,y);
  switch(ast[0]){case 'add':return a+b;case 'sub':return a-b;case 'min':return Math.min(a,b);
    case 'max':return Math.max(a,b);case 'mul':return a*b;default:throw Error('RESEARCH_INVALID_AST');}
}
export function evaluateAst(ast,input) {
  validateAst(ast);
  if(!Array.isArray(input)||input.length!==2||!input.every(x=>Number.isSafeInteger(x)&&Math.abs(x)<=1000))
    throw Error('RESEARCH_INVALID_INPUT');
  const value=run(ast,...input);
  if(!Number.isSafeInteger(value))throw Error('RESEARCH_ARITHMETIC_OVERFLOW');
  return value;
}
export function integerGrid(radius) {
  if(!Number.isSafeInteger(radius)||radius<1||radius>12)throw Error('RESEARCH_DOMAIN_LIMIT');
  const points=[];for(let x=-radius;x<=radius;x++)for(let y=-radius;y<=radius;y++)points.push([x,y]);
  return freeze(points);
}
export function shuffled(values,seed) {
  if(!Number.isSafeInteger(seed)||seed<0)throw Error('RESEARCH_INVALID_SEED');
  let s=seed>>>0;const out=[...values];
  for(let i=out.length-1;i>0;i--){s=(Math.imul(s,1664525)+1013904223)>>>0;const j=s%(i+1);[out[i],out[j]]=[out[j],out[i]];}
  return out;
}

/** Semantic deduplication depends on grammar inputs only, never target labels. */
export function createSynthesisGrammar({maxNodes=5,domain=integerGrid(4)}={}) {
  if(!Number.isSafeInteger(maxNodes)||maxNodes<1||maxNodes>5||domain.length>625||!domain.length)
    throw Error('RESEARCH_GRAMMAR_LIMIT');
  const bySize=[[],['x','y',-2,-1,0,1,2]],seen=new Set(),programs=[];
  let generated=0,interpreterEvaluations=0;
  for(let size=1;size<=maxNodes;size++){
    const raw=size===1?bySize[1]:[];
    if(size>1){
      for(const a of bySize[size-1])raw.push(['abs',a]);
      for(let l=1;l<size-1;l++)for(const a of bySize[l])for(const b of bySize[size-1-l])
        for(const op of BINARY)raw.push([op,a,b]);
    }
    const kept=[];
    for(const ast of raw){
      generated++;const predictions=domain.map(([x,y])=>run(ast,x,y));interpreterEvaluations+=domain.length;
      const signature=predictions.join(',');if(seen.has(signature))continue;seen.add(signature);
      const program=freeze({ast:copy(ast),size,predictions});programs.push(program);kept.push(program.ast);
    }
    bySize[size]=kept;
  }
  return freeze({version:1,domain:copy(domain),programs,generated,interpreterEvaluations,
    fingerprint:digest({maxNodes,domain,asts:programs.map(p=>p.ast)})});
}

/** Oracle capability is local to one task; no target AST or task family input. */
export function synthesize({grammar,oracle,mode='disagreement',seed=11,maxQueries=8}={}) {
  if(!grammar?.programs?.length||typeof oracle!=='function'||!['passive','disagreement'].includes(mode)||
    !Number.isSafeInteger(maxQueries)||maxQueries<0||maxQueries>8)throw Error('RESEARCH_SYNTHESIS_INPUT');
  let survivors=grammar.programs.map((_,i)=>i),candidateLookups=0,disagreementLookups=0;
  const asked=new Set(),trace=[];
  const acquire=index=>{
    const input=grammar.domain[index];const expected=oracle([...input]);
    if(!Number.isSafeInteger(expected))throw Error('RESEARCH_INVALID_ORACLE');
    const leader=survivors[0];asked.add(index);
    const counterexample=leader!==undefined&&grammar.programs[leader].predictions[index]!==expected;
    const before=survivors.length;
    survivors=survivors.filter(i=>{candidateLookups++;return grammar.programs[i].predictions[index]===expected;});
    trace.push({input:[...input],expected,counterexample,before,after:survivors.length});
  };
  for(const point of [[0,0],[1,1]]){
    const index=grammar.domain.findIndex(p=>p[0]===point[0]&&p[1]===point[1]);
    if(index<0)throw Error('RESEARCH_MISSING_SEED_INPUT');acquire(index);
  }
  const passive=shuffled(grammar.domain.map((_,i)=>i),seed);
  for(let n=0;n<maxQueries&&survivors.length>1;n++){
    let selected=-1;
    if(mode==='passive')selected=passive.find(i=>!asked.has(i))??-1;
    else {
      let best=-1;
      for(let i=0;i<grammar.domain.length;i++){
        if(asked.has(i))continue;const counts=new Map();
        for(const s of survivors){disagreementLookups++;const y=grammar.programs[s].predictions[i];counts.set(y,(counts.get(y)??0)+1);}
        // Gini disagreement, integer numerator avoids float tie instability.
        const score=survivors.length**2-[...counts.values()].reduce((a,c)=>a+c*c,0);
        if(score>best){best=score;selected=i;}
      }
      if(best<=0)break;
    }
    if(selected<0)break;acquire(selected);
  }
  const chosen=survivors[0];
  return {status:chosen===undefined?'ABSTAIN':'CANDIDATE_ONLY',
    ast:chosen===undefined?null:copy(grammar.programs[chosen].ast),oracleCalls:trace.length,
    acquisitions:Math.max(0,trace.length-2),candidateLookups,disagreementLookups,
    survivors:survivors.length,trace,verifiedBeyondQueryDomain:false,autoApplyAuthorized:false};
}

export function verifyProgram(ast,examples,{stopEarly=false}={}) {
  validateAst(ast);
  if(!Array.isArray(examples)||!examples.length||examples.length>625)throw Error('RESEARCH_TEST_LIMIT');
  let executed=0,errors=0,firstCounterexample=null;
  for(const ex of examples){
    if(!Number.isSafeInteger(ex.expected))throw Error('RESEARCH_INVALID_EXPECTATION');
    const actual=evaluateAst(ast,ex.input);executed++;
    if(actual!==ex.expected){errors++;firstCounterexample??={input:[...ex.input],expected:ex.expected,actual};if(stopEarly)break;}
  }
  return {passed:errors===0,executed,errors,firstCounterexample};
}

export function patchOperator(ast,{path=[],from,to}={}) {
  validateAst(ast);
  if(!BINARY.includes(from)||!BINARY.includes(to)||from===to||!Array.isArray(path)||path.length>10||
    !path.every(i=>i===1||i===2))throw Error('RESEARCH_INVALID_PATCH');
  const out=copy(ast);let at=out;
  for(const i of path){if(!Array.isArray(at)||i>=at.length)throw Error('RESEARCH_PATCH_PATH');at=at[i];}
  if(!Array.isArray(at)||at[0]!==from)throw Error('RESEARCH_PATCH_PRECONDITION');
  at[0]=to;return out;
}
export function operatorMutations(ast) {
  validateAst(ast);const out=[];
  const walk=(node,path)=>{
    if(!Array.isArray(node))return;
    if(BINARY.includes(node[0]))for(const to of BINARY)if(to!==node[0]){
      const patch={path,from:node[0],to};out.push({patch,ast:patchOperator(ast,patch)});
    }
    node.slice(1).forEach((child,i)=>walk(child,[...path,i+1]));
  };
  walk(ast,[]);return out;
}

export function assertDisjointSplits(training,holdout) {
  const keys=['id','family','seed'];
  for(const key of keys){const known=new Set(training.map(t=>t[key]));
    if(holdout.some(t=>known.has(t[key])))throw Error('RESEARCH_SPLIT_OVERLAP_'+key.toUpperCase());}
  const fingerprints=new Set(training.map(t=>digest({source:t.source,examples:t.examples})));
  if(holdout.some(t=>fingerprints.has(digest({source:t.source,examples:t.examples}))))
    throw Error('RESEARCH_SPLIT_CONTENT_ALIAS');
  if(training.some(t=>t.split!=='train')||holdout.some(t=>t.split==='train'))throw Error('RESEARCH_SPLIT_LABEL');
  return true;
}

/** Caller-owned training registry is the trust boundary, NOT a JSON receipt. */
export function createRepairMemory({trainingTasks,holdoutTasks=[]}={}) {
  if(!Array.isArray(trainingTasks)||trainingTasks.length<1||trainingTasks.length>64)throw Error('RESEARCH_MEMORY_LIMIT');
  assertDisjointSplits(trainingTasks,holdoutTasks);
  const registry=new Map(trainingTasks.map(t=>[t.id,freeze(copy(t))])),records=new Map(),revoked=new Set();
  let proofExecutions=0;
  if(registry.size!==trainingTasks.length)throw Error('RESEARCH_DUPLICATE_TASK_ID');
  const build=(task,patch)=>{
    const repaired=patchOperator(task.source,patch),before=verifyProgram(task.source,task.examples),after=verifyProgram(repaired,task.examples);
    proofExecutions+=before.executed+after.executed;
    if(before.passed||!after.passed)throw Error('RESEARCH_UNVERIFIED_MEMORY');
    const body={schema:'continuum-capability-repair-memory',version:1,authority:'SHADOW_ONLY',
      trainingOnly:true,provenance:{taskId:task.id,taskSnapshot:digest(task),family:task.family,seed:task.seed},
      hint:{from:patch.from,to:patch.to},witness:{source:task.source,repaired,path:patch.path,
        suiteHash:digest(task.examples),executed:after.executed,
        observedHash:digest(task.examples.map(e=>({input:e.input,actual:evaluateAst(repaired,e.input)})))},
      autoApplyAuthorized:false,modelTrainingAuthorized:false};
    proofExecutions+=task.examples.length;
    return freeze({...body,contentHash:digest(body)});
  };
  const validate=record=>{
    try{
      const {contentHash,...body}=record;
      if(digest(body)!==contentHash)return {valid:false,reason:'CONTENT_HASH_MISMATCH'};
      const task=registry.get(record.provenance?.taskId);
      if(!task)return {valid:false,reason:'UNREGISTERED_TRAINING_PROVENANCE'};
      const rebuilt=build(task,{...record.hint,path:record.witness?.path});
      if(canonical(rebuilt)!==canonical(record))return {valid:false,reason:'PROOF_OR_PROVENANCE_MISMATCH'};
      if(revoked.has(contentHash))return {valid:false,reason:'REVOKED'};
      return {valid:true,reason:null};
    }catch{return {valid:false,reason:'INVALID_MEMORY'};}
  };
  return Object.freeze({
    learn(taskId,patch){const task=registry.get(taskId);if(!task)throw Error('RESEARCH_TRAINING_PROVENANCE_REQUIRED');
      const record=build(task,patch);if(revoked.has(record.contentHash))throw Error('RESEARCH_REVOKED_MEMORY');
      records.set(record.contentHash,record);return copy(record);},
    import(record){const result=validate(record);if(result.valid)records.set(record.contentHash,freeze(copy(record)));return result;},
    validate,
    revoke(contentHash){if(!records.has(contentHash))return false;revoked.add(contentHash);return true;},
    active(ids=null){
      if(ids!==null&&(!Array.isArray(ids)||ids.length>128))throw Error('RESEARCH_CONTEXT_IDS');
      const order=ids??[...records.keys()].sort();
      // Registry and admitted records are deep-frozen private copies. Proofs
      // are executed on admission; current revocation is checked on every use.
      return order.map(id=>records.get(id)).filter(r=>r&&!revoked.has(r.contentHash)).map(copy);
    },
    statistics(){return {stored:records.size,revoked:revoked.size,proofExecutions,authority:'SHADOW_ONLY',writesPerformed:false};}
  });
}

/** Memory changes order only; EVERY candidate uses the same fresh verifier. */
export function repairTask(task,{memory=null,contextIds=null,maxAttempts=32}={}) {
  if(!Number.isSafeInteger(maxAttempts)||maxAttempts<1||maxAttempts>32)throw Error('RESEARCH_REPAIR_BUDGET');
  const canonicalCandidates=operatorMutations(task.source),hinted=[];
  if(memory)for(const record of memory.active(contextIds))for(const c of canonicalCandidates)
    if(c.patch.from===record.hint.from&&c.patch.to===record.hint.to)hinted.push({...c,memoryHash:record.contentHash});
  const seen=new Set(),trace=[];let executed=0;
  for(const c of [...hinted,...canonicalCandidates]){
    const id=digest(c.ast);if(seen.has(id))continue;seen.add(id);
    if(trace.length>=maxAttempts)break;
    const check=verifyProgram(c.ast,task.examples,{stopEarly:true});executed+=check.executed;
    trace.push({patch:c.patch,passed:check.passed,executed:check.executed,memoryHash:c.memoryHash??null});
    if(check.passed)return {status:'VERIFIED_FINITE_DOMAIN',ast:c.ast,patch:c.patch,attempts:trace.length,
      executed,trace,autoApplyAuthorized:false,independentWorktreeStillRequired:true};
  }
  return {status:'ABSTAIN',ast:null,patch:null,attempts:trace.length,executed,trace,
    autoApplyAuthorized:false,independentWorktreeStillRequired:true};
}

const dot=(a,b)=>a.reduce((s,x,i)=>s+x*b[i],0);
const normalizedCosine=(a,b)=>{const norm=Math.sqrt(dot(a,a)*dot(b,b));return norm?dot(a,b)/norm:0;};
const textTerms=text=>new Set(text.toLowerCase().match(/[\p{L}\p{N}]+/gu)??[]);
const overlap=(a,b)=>{let n=0;for(const t of a)if(b.has(t))n++;return n/(a.size+b.size-n||1);};
export const contextCost=text=>Math.max(1,Math.ceil(Buffer.byteLength(text,'utf8')/4));

/** Uses private restored encoder caches; caller-owned snapshot bytes never mutate. */
export function createFrozenCommittee(snapshotBytes) {
  const bytes=Buffer.from(snapshotBytes),encoder=SubwordEncoder.restore(bytes);
  if(encoder.version!==1)throw Error('RESEARCH_SWB1_REQUIRED');
  const snapshotHash=crypto.createHash('sha256').update(bytes).digest('hex');
  return Object.freeze({snapshotHash,updates:encoder.updates,
    select({query,records,budget=120,mode='committee'}={}){
      if(typeof query!=='string'||!query.trim()||query.length>1000||!Array.isArray(records)||records.length>128||
        new Set(records.map(r=>r.id)).size!==records.length||
        records.some(r=>typeof r.id!=='string'||typeof r.text!=='string'||!r.text.trim()||r.text.length>800||
          typeof r.strategy!=='string')||!Number.isSafeInteger(budget)||budget<1||budget>512||
        !['committee','lexical','swb1'].includes(mode))throw Error('RESEARCH_CONTEXT_INPUT');
      const lexical=rankMemories(query,records,{limit:records.length});
      const lexScores=new Map(lexical.map(r=>[r.id,r.score]));const q=encoder.encode(query);
      const neural=records.map(r=>({...r,neural:normalizedCosine(q,encoder.encode(r.text))}))
        .sort((a,b)=>b.neural-a.neural||a.id.localeCompare(b.id));
      const agreement=Boolean(lexical[0]&&neural[0]&&lexical[0].id===neural[0].id);
      const lexicalGap=(lexical[0]?.score??0)-(lexical[1]?.score??0);
      const neuralGap=(neural[0]?.neural??0)-(neural[1]?.neural??0);
      const confident=agreement&&lexicalGap>=0.08&&neuralGap>=0.04;
      const selected=[];let cost=0;
      const add=r=>{const next=contextCost(r.text);if(cost+next>budget)return false;
        selected.push(r);cost+=next;return true;};
      if(mode==='lexical'||mode==='swb1')for(const r of mode==='lexical'?lexical:neural)add(r);
      else if(confident&&contextCost(lexical[0].text)<=budget)add(lexical[0]);
      else {
        const candidates=neural.map(r=>({...r,terms:textTerms(r.text)}));
        while(true){
          const ranked=candidates.filter(r=>!selected.some(s=>s.id===r.id)&&cost+contextCost(r.text)<=budget).map(r=>{
            const redundancy=Math.max(0,...selected.map(s=>overlap(r.terms,textTerms(s.text))));
            const sameStrategy=selected.some(s=>s.strategy===r.strategy)?1:0;
            const relevance=0.5*(lexScores.get(r.id)??0)+0.5*((r.neural+1)/2);
            return {r,utility:(relevance-0.45*redundancy-0.45*sameStrategy)/contextCost(r.text)};
          }).sort((a,b)=>b.utility-a.utility||a.r.id.localeCompare(b.r.id));
          if(!ranked.length||ranked[0].utility<=0)break;add(ranked[0].r);
        }
      }
      const after=crypto.createHash('sha256').update(encoder.save()).digest('hex');
      if(after!==snapshotHash)throw Error('RESEARCH_FROZEN_SNAPSHOT_CHANGED');
      return {ids:selected.map(r=>r.id),cost,budget,agreement,confident,lexicalGap,neuralGap,
        route:mode==='committee'?(confident?'COMPACT_AGREEMENT':'DIVERSE_UNCERTAINTY'):mode.toUpperCase(),
        snapshotHash,modelTrainingAuthorized:false};
    }
  });
}

/** Restricted proposal adapter only: no files are created or code executed. */
export function numericResearchCandidate(sourceAst,proposedAst) {
  validateAst(sourceAst);validateAst(proposedAst);
  const emit=ast=>{
    if(typeof ast==='number'||typeof ast==='string')return String(ast);
    const a=emit(ast[1]);if(ast[0]==='abs')return `((${a})<0?-(${a}):(${a}))`;
    const b=emit(ast[2]);
    if(ast[0]==='min'||ast[0]==='max')return `((${a})${ast[0]==='min'?'<':'>'}(${b})?(${a}):(${b}))`;
    return `((${a})${{add:'+',sub:'-',mul:'*'}[ast[0]]}(${b}))`;
  };
  const module=ast=>`export function probe(x,y) { return ${emit(ast)}; }`;
  const edit={file:'.github/continuum/capability-research-probe.mjs',oldText:module(sourceAst),newText:module(proposedAst)};
  if(!isLocalNumericPatch(edit))throw Error('RESEARCH_NUMERIC_ADAPTER_REJECTED');
  return {strategy:'CAPABILITY_RESEARCH_NUMERIC',edits:[edit],authority:'SHADOW_ONLY',
    autoApplyAuthorized:false,verifiedByCanonicalRepairGate:false};
}
