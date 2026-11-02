import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {BacklogStore} from '../.github/continuum/development-backlog.mjs';
import {decodeLeaderState} from '../.github/continuum/autonomous-teamleader.mjs';
import {defaultTeamleaderStateDir} from '../.github/continuum/teamleader-cli.mjs';
import {runMobileCodingJob,cancelMobileCodingJob,jobTaskId,validateOrder} from './continuum-mobile-coding.mjs';
import {parseArgs} from './continuum-mobile-builder.mjs';

const nested=Boolean(process.env.CONTINUUM_REPAIR_DEPTH);
const BUILDER=fileURLToPath(new URL('./continuum-mobile-builder.mjs',import.meta.url));
const CASES=JSON.stringify([{args:[5],expected:15},{args:[8],expected:24}]);
const MODULE='.github/continuum/math-utils.mjs';

function fixture(){
 const home=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-mobile-coding-'));
 const root=path.join(home,'device-ai'),dir=path.join(root,'.github','continuum');
 fs.mkdirSync(dir,{recursive:true});
 fs.writeFileSync(path.join(dir,'math-utils.mjs'),'export function triple(n) { return n * 3; }\n');
 fs.writeFileSync(path.join(dir,'math-utils.test.mjs'),"import test from 'node:test';\n"+
  "import assert from 'node:assert/strict';\nimport {triple} from './math-utils.mjs';\n"+
  "test('baseline',()=>assert.equal(triple(2),6));\n");
 const git=(...args)=>{const r=spawnSync('git',args,{cwd:root,encoding:'utf8',timeout:15000});
  assert.equal(r.status,0,r.stderr);return r.stdout.trim();};
 git('init','-q');git('add','.');
 git('-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','seed');
 return {home,root,git,head:git('rev-parse','HEAD'),builderState:path.join(home,'builder-state')};
}
/** Exactly the argv the Android plan sends through Termux RUN_COMMAND (no shell). */
function termux(f,args){
 const env={...process.env,HOME:f.home};delete env.NODE_TEST_CONTEXT;
 const r=spawnSync(process.execPath,[BUILDER,...args,'--project',f.root,'--state-dir',f.builderState],
  {cwd:f.root,env,encoding:'utf8',timeout:170000});
 return {exitCode:r.status,receipt:r.stdout.trim()?JSON.parse(r.stdout):null,stderr:r.stderr};
}
const order=(job,cases=CASES)=>['code-test','--job',job,'--module',MODULE,'--export','triple','--cases-json',cases,'--budget-minutes','3'];

test('Android coding job runs the canonical owner TEST pipeline and returns a verifiable receipt',{skip:nested,timeout:180000},()=>{
 const f=fixture();
 try{
  const original=fs.readFileSync(path.join(f.root,MODULE));
  const job=crypto.randomUUID();
  const r=termux(f,order(job));
  assert.equal(r.exitCode,0,r.stderr);
  const q=r.receipt;
  assert.equal(q.schema,'continuum-mobile-coding-receipt');
  assert.equal(q.status,'CODING_PATCH_VERIFIED_PENDING_REVIEW',JSON.stringify(q.failure));
  assert.equal(q.job,job);assert.equal(q.taskId,jobTaskId(job));assert.equal(q.commit,f.head);
  assert.equal(q.teamleaderOutcome,'VERIFIED_PATCH_PENDING_REVIEW');
  assert.equal(q.approvedForMerge,false);assert.equal(q.installed,false);assert.equal(q.apk,null);assert.equal(q.deviceVerified,false);
  assert.match(q.toolchain.node,/^v\d+/);assert.match(q.toolchain.git,/^git version/);
  // the receipt points at real patch bytes
  const patch=fs.readFileSync(q.patch.path);
  assert.equal(crypto.createHash('sha256').update(patch).digest('hex'),q.patch.sha256);
  assert.equal(patch.length,q.patch.bytes);
  assert.match(patch.toString('utf8'),/new file mode/);
  // three real fixed-profile runs: baseline, candidate, independent second worktree
  assert.deepEqual(q.commands.map(c=>c.phase),['baseline','candidate','independent']);
  for(const c of q.commands){assert.equal(c.exitCode,0);assert.equal(c.passed,true);assert.ok(c.executed>0);assert.equal(c.failed,0);}
  assert.ok(q.evidence.candidateExecuted>=q.evidence.baselineExecuted+2);
  assert.ok(q.evidence.independentExecuted>=q.evidence.candidateExecuted);
  assert.match(q.evidence.verificationSha256,/^[0-9a-f]{64}$/);
  // canonical backlog: DONE with test evidence bound to the submitted patch
  const stateDir=defaultTeamleaderStateDir(fs.realpathSync(f.root),f.home);
  const task=new BacklogStore(path.join(stateDir,'development-backlog.json'),{actor:'t'}).read().doc.tasks.find(t=>t.id===q.taskId);
  assert.equal(task.state,'DONE');assert.equal(task.submission.artifact.sha256,q.patch.sha256);
  // the owner's checkout is untouched; the patch still applies for review
  assert.deepEqual(fs.readFileSync(path.join(f.root,MODULE)),original);
  assert.equal(f.git('status','--porcelain'),'');
  assert.equal(spawnSync('git',['apply','--check',q.patch.path],{cwd:f.root}).status,0);
  // replaying the same job id never executes again
  const again=termux(f,order(job));
  assert.equal(again.exitCode,0);assert.equal(again.receipt.replayed,true);
  assert.equal(again.receipt.patch.sha256,q.patch.sha256);
  const leader=decodeLeaderState(fs.readFileSync(path.join(stateDir,'teamleader-state.json')));
  assert.equal(leader.events.filter(e=>e.kind==='OWNER_TEST'&&e.outcome!=='RUNNING').length,1);
 }finally{fs.rmSync(f.home,{recursive:true,force:true});}
});

test('a wrong owner expectation is reported as a failed coding job without patch',{skip:nested,timeout:180000},()=>{
 const f=fixture();
 try{
  const r=termux(f,order(crypto.randomUUID(),JSON.stringify([{args:[5],expected:16}])));
  assert.equal(r.exitCode,2);
  assert.equal(r.receipt.status,'FAILED_CODING_CHECKS');
  assert.equal(r.receipt.patch,null);assert.equal(r.receipt.evidence,null);
  assert.equal(r.receipt.failure.code,'CONTRACT_CHECKS_FAILED');
  assert.equal(f.git('status','--porcelain'),'');
 }finally{fs.rmSync(f.home,{recursive:true,force:true});}
});

test('invalid orders, unclean checkouts, busy workers, cancel and stale bases fail closed',{skip:nested,timeout:60000},async()=>{
 const f=fixture();
 const teamleaderState=path.join(f.home,'tl-state');
 const backlog=path.join(teamleaderState,'development-backlog.json');
 const base={project:f.root,builderState:f.builderState,teamleaderState,module:MODULE,exportName:'triple',casesJson:CASES};
 try{
  assert.ok(validateOrder({job:'nope',module:'../x.mjs',exportName:'a b',casesJson:''}).length>=4);
  const invalid=await runMobileCodingJob({...base,job:crypto.randomUUID(),module:'.github/continuum/../../x.mjs'});
  assert.equal(invalid.status,'BLOCKED_INVALID_ORDER');assert.equal(fs.existsSync(backlog),false);
  fs.writeFileSync(path.join(f.root,'scratch.txt'),'uncommitted');
  assert.equal((await runMobileCodingJob({...base,job:crypto.randomUUID()})).status,'BLOCKED_UNCLEAN_SOURCE');
  fs.rmSync(path.join(f.root,'scratch.txt'));
  const job=crypto.randomUUID();
  const busy={head:()=>f.head,readyOwnerContractTask(){throw Error('Teamleader already running or lock requires recovery');}};
  const blocked=await runMobileCodingJob({...base,job,driver:busy});
  assert.equal(blocked.status,'BLOCKED_JOB_RUNNING');
  const store=new BacklogStore(backlog,{actor:'t'});
  assert.equal(store.read().doc.tasks.find(t=>t.id===jobTaskId(job)).state,'READY','order stays queued for a later run');
  // the base moved: the queued order is not run against a different commit
  fs.writeFileSync(path.join(f.root,'note.md'),'x\n');f.git('add','.');
  f.git('-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','move');
  assert.equal((await runMobileCodingJob({...base,job})).status,'BLOCKED_STALE_BASE');
  assert.equal(cancelMobileCodingJob({project:f.root,teamleaderState,job}).status,'CANCELLED');
  assert.equal((await runMobileCodingJob({...base,job})).status,'CANCELLED');
  assert.equal(cancelMobileCodingJob({project:f.root,teamleaderState,job:crypto.randomUUID()}).status,'BLOCKED_NO_SUCH_JOB');
 }finally{fs.rmSync(f.home,{recursive:true,force:true});}
});

test('builder CLI accepts coding values only for the fixed coding operations',()=>{
 const job=crypto.randomUUID();
 assert.equal(parseArgs(['code-test','--job',job,'--module',MODULE,'--export','triple','--cases-json',CASES,'--budget-minutes','5']).budgetMinutes,5);
 assert.throws(()=>parseArgs(['build','--job',job]),/Unsupported argument/);
 assert.throws(()=>parseArgs(['code-cancel','--job',job,'--module',MODULE]),/only --job/);
 assert.throws(()=>parseArgs(['code-test','--budget-minutes','5;rm']),/1..30/);
 assert.throws(()=>parseArgs(['code-shell']),/Usage/);
});
