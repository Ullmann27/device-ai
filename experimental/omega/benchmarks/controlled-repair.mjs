/** Controlled proposal replay, not model inference or a blind holdout.
 * The same frozen stream and max two proposals serve both arms. Evaluation
 * cases are never passed to the provider or online verifier. */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {BacklogStore} from '../../../.github/continuum/development-backlog.mjs';
import {executeOwnerRepairTask} from '../../../.github/continuum/backlog-repair-worker.mjs';
import {runCandidateVerifier} from '../../../.github/continuum/repair-research-verifier.mjs';
import {createNumericVerifier} from '../repair/numeric-verifier.mjs';

const tasks=Object.freeze([
 {id:'distance',old:'a-b',correct:'a>b?a-b:b-a',example:[3,8],expected:5,oracle:(a,b)=>Math.abs(a-b)},
 {id:'minimum',old:'a>b?a:b',correct:'a<b?a:b',example:[3,8],expected:3,oracle:Math.min},
 {id:'maximum',old:'a<b?a:b',correct:'a>b?a:b',example:[3,8],expected:8,oracle:Math.max},
 {id:'correct-first',old:'a-b',correct:'a>b?a-b:b-a',example:[3,8],expected:5,oracle:(a,b)=>Math.abs(a-b)},
 {id:'budget-exhausted',old:'a-b',correct:'a>b?a-b:b-a',example:[3,8],expected:5,oracle:(a,b)=>Math.abs(a-b)}
]);
// Distinct finite input sets; synthetic evaluator split, NOT unseen task families.
const ONLINE=[[0,0],[-4,2],[9,-2]];
const EVALUATION=[[-17,11],[4,4],[25,-9],[-8,-21],[100,3],[0,-19]];
const git=(root,...args)=>{
 const r=spawnSync('git',args,{cwd:root,encoding:'utf8',env:{...process.env,GIT_AUTHOR_DATE:'2026-10-25T00:00:00Z',GIT_COMMITTER_DATE:'2026-10-25T00:00:00Z'}});
 if(r.status!==0)throw Error(`git ${args[0]}: ${r.stderr}`);return r.stdout.trim();
};
function suite(base,t,pairs){return {schema:'omega-numeric-suite',version:1,sourceCommit:base,
 file:'lib/math.mjs',exportName:'solve',arity:2,
 cases:pairs.map((args,i)=>({id:`case-${i}`,args,expected:t.oracle(...args)})),
 relations:[{id:'symmetry',left:pairs[0],right:[...pairs[0]].reverse(),relation:'equal'}]};}

export function runTrial(t,arm,repeat){
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-omega-bench-'));
 try{
  const root=path.join(dir,'source'),state=path.join(dir,'state');
  fs.mkdirSync(path.join(root,'lib'),{recursive:true});fs.mkdirSync(path.join(root,'.github/continuum'),{recursive:true});fs.mkdirSync(state);
  const oldSource=`export function solve(a,b){return ${t.old};}\n`;
  fs.writeFileSync(path.join(root,'lib/math.mjs'),oldSource);
  fs.writeFileSync(path.join(root,'.github/continuum/math.test.mjs'),
   `import test from 'node:test';import assert from 'node:assert/strict';import {solve} from '../../lib/math.mjs';test('fixed example',()=>assert.equal(solve(${t.example}),${t.expected}));\n`);
  git(root,'init','-q');git(root,'add','.');git(root,'-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm',t.id);
  const base=git(root,'rev-parse','HEAD'),id='omega-replay',file=path.join(state,'backlog.json');
  const store=new BacklogStore(file,{actor:'owner'});store.apply([
   {op:'propose',actor:'owner',at:1,task:{id,title:t.id,kind:'REPAIR',source:'owner',priority:800,subject:{base,chain:'node'}}},
   {op:'accept',actor:'owner',at:2,id}]);
  let calls=0;const proposed=[];
  const candidateProvider=Object.assign(()=>{
   const expression=t.id==='correct-first'||calls>0&&t.id!=='budget-exhausted'?t.correct:
    calls===0?String(t.expected):`${t.expected}+0*a`;
   calls++;proposed.push(expression);
   return {mode:'LOCAL_CODING_PATCH_PROPOSAL_V1',trusted:false,autoApply:false,
    plan:['Apply the next frozen proposal'],hypothesis:'Controlled candidate replay',
    patches:[{file:'lib/math.mjs',oldText:`return ${t.old};`,newText:`return ${expression};`}],
    context:{commit:base,contextSha256:'a'.repeat(64),ranges:[]},localRequests:1};
  },{taskSpec:{schema:'continuum-coding-task',version:1,id,sourceCommit:base,kind:'BUGFIX',goal:`Repair ${t.id}`,
   allowedPaths:['lib/math.mjs'],acceptance:['Match the task definition'],chain:'node',limits:{maxModelCalls:2,maxContextChars:4096}}});
  const online=createNumericVerifier(suite(base,t,ONLINE)),cpu=process.cpuUsage(),start=performance.now();
  const result=executeOwnerRepairTask({root,file,out:path.join(state,'reports'),candidateProvider,
   ...(arm==='vhsr'?{candidateVerifier:online}:{})});
  const wallMs=performance.now()-start,cpuUsed=process.cpuUsage(cpu);
  const report=JSON.parse(fs.readFileSync(result.report));
  let evaluation=null;
  if(result.status==='DONE'){
   // Apply the actual recorded patch in a fresh tree; do not score the provider's intention.
   const target=path.join(dir,'scoring');git(root,'worktree','add','--detach',target,base);
   const patch=path.join(dir,'candidate.patch');fs.writeFileSync(patch,report.verified.patch);git(target,'apply','--',patch);
   evaluation=runCandidateVerifier(createNumericVerifier(suite(base,t,EVALUATION)),{base,
    sources:[{file:'lib/math.mjs',source:fs.readFileSync(path.join(target,'lib/math.mjs'),'utf8')}],deadline:Date.now()+5000});
  }
  return {task:t.id,arm,repeat,base,status:result.status,reason:result.reason??null,
   modelCalls:0,proposalCalls:calls,proposalBudget:2,proposed,
   accepted:result.status==='DONE',correct:evaluation?.status==='PASS',
   falseAcceptance:result.status==='DONE'&&evaluation?.status==='FAIL',
   evaluation,onlineSuiteSha256:online.suiteSha256,
   counterexamples:report.attempts.filter(a=>a.researchVerification?.status==='FAIL').length,
   attempts:report.attempts.map(a=>({outcome:a.outcome,verification:a.researchVerification??null})),
   independent:report.independent??null,wallMs,parentCpuMs:(cpuUsed.user+cpuUsed.system)/1000,
   parentMaxRssKiB:process.resourceUsage().maxRSS,
   sourceClean:git(root,'status','--porcelain')===''};
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
}
export function benchmark({repetitions=2}={}){
 if(!Number.isInteger(repetitions)||repetitions<1||repetitions>3)throw Error('REPETITION_LIMIT');
 const rows=[];
 for(let n=0;n<repetitions;n++)for(const t of tasks)for(const arm of n%2?['vhsr','baseline']:['baseline','vhsr'])rows.push(runTrial(t,arm,n));
 const summary=Object.fromEntries(['baseline','vhsr'].map(arm=>{
  const r=rows.filter(x=>x.arm===arm);
  return [arm,{trials:r.length,accepted:r.filter(x=>x.accepted).length,correct:r.filter(x=>x.correct).length,
   falseAcceptance:r.filter(x=>x.falseAcceptance).length,abstained:r.filter(x=>!x.accepted).length,
   proposalCalls:r.reduce((s,x)=>s+x.proposalCalls,0),modelCalls:0,
   counterexamples:r.reduce((s,x)=>s+x.counterexamples,0),wallMs:r.reduce((s,x)=>s+x.wallMs,0)}];
 }));
 return {schema:'omega-controlled-replay',version:1,node:process.version,platform:process.platform,
  sourceCommit:git(fileURLToPath(new URL('../../..',import.meta.url)),'rev-parse','HEAD'),
  limitations:['Synthetic proposals; no real model inference','Same-author fixed tasks; not a blind generalization holdout',
   'Equal proposal ceiling, additional verifier compute; no compute-efficiency claim',
   'Parent CPU/RSS only; child aggregate CPU/memory and GPU utilization NOT_MEASURED',
   'Finite examples and relations are not proofs; repeat trials are not independent task samples'],summary,rows};
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 process.stdout.write(JSON.stringify(benchmark(),null,2)+'\n');
}
