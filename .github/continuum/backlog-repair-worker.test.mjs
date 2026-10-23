import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawn,spawnSync} from 'node:child_process';
import {executeOwnerRepairTask,validateOwnerRepairTask,assessIndependentOwnerEvidence} from './backlog-repair-worker.mjs';
import {BacklogStore,emptyDocument,applyOps,nextEligible} from './development-backlog.mjs';
import {runChain} from './repair-loop.mjs';

const nested=Boolean(process.env.CONTINUUM_REPAIR_DEPTH);
function fixture(broken,{slowTest=false}={}){
 const base=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-owner-task-'));
 const root=path.join(base,'checkout'),state=path.join(base,'state');
 const dir=path.join(root,'.github','continuum');
 fs.mkdirSync(dir,{recursive:true});fs.mkdirSync(state);
 const marker=path.join(state,'slow-test-started');
 // a check that runs for a minute and records its process, so a cancel can be observed while it runs
 if(slowTest)fs.writeFileSync(path.join(dir,'slow.test.mjs'),
  "import test from 'node:test';\nimport fs from 'node:fs';\n"+
  `test('slow check',async()=>{fs.writeFileSync(${JSON.stringify(marker)},String(process.pid));`+
  'await new Promise(r=>setTimeout(r,60000));});\n');
 fs.writeFileSync(path.join(dir,'math.mjs'),
  'export function square(n){return n*n}\n'+
  'export function quad(n){return '+(broken?'sqare':'square')+'(square(n))}\n');
 fs.writeFileSync(path.join(dir,'math.test.mjs'),
  "import test from 'node:test';\nimport assert from 'node:assert/strict';\n"+
  "import {quad} from './math.mjs';\ntest('quad works',()=>assert.equal(quad(2),16));\n");
 const git=(...args)=>{
  const out=spawnSync('git',args,{cwd:root,encoding:'utf8',timeout:20000});
  assert.equal(out.status,0,out.stderr);return out.stdout.trim();
 };
 git('init','-q');git('add','.');
 git('-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','baseline');
 return {base,root,state,git,marker,head:git('rev-parse','HEAD')};
}
function accepted(store,f,base=f.head){
 store.apply([{op:'propose',actor:'owner',at:1,
  task:{id:'repair-owner-one',title:'Fix broken arithmetic',kind:'REPAIR',
   source:'owner',priority:900,subject:{base,chain:'node'}}},
  {op:'accept',actor:'owner',at:2,id:'repair-owner-one',reason:'explicit local authorization'}]);
}


test('owner independent evidence rejects silent test-count and skipped-test regressions',()=>{
 const profile=(executed,skipped=0)=>({
  profile:'node-continuum',passed:true,testProfile:true,
  tests:{executed,failed:0,skipped,passed:executed-skipped}
 });
 const baseline=[profile(12,0)];
 const check=(executed,skipped=0,passed=true)=>({
  passed,results:[profile(executed,skipped)]
 });
 const green=assessIndependentOwnerEvidence(baseline,check(12));
 assert.equal(green.passed,true);
 assert.equal(green.evidenceSufficient,true);
 assert.equal(green.actualTests,12);
 const fewer=assessIndependentOwnerEvidence(baseline,check(10));
 assert.equal(fewer.passed,false);
 assert.equal(fewer.evidenceSufficient,false);
 assert.ok(fewer.reasons.some(x=>x.includes('baseline had 12 passing')));
 const skipped=assessIndependentOwnerEvidence(baseline,check(12,1));
 assert.equal(skipped.passed,false);
 assert.ok(skipped.reasons.some(x=>x.includes('skips')));
 assert.equal(assessIndependentOwnerEvidence(baseline,check(12,0,false)).passed,false);
 assert.equal(assessIndependentOwnerEvidence(baseline,check(0)).passed,false);
 assert.throws(()=>assessIndependentOwnerEvidence(null,check(12)),/baseline/);
});

test('expired execution budget fails closed before launching fixed subprocesses',()=>{
 const out=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-deadline-'));
 try{
  assert.throws(()=>runChain('node',process.cwd(),out,{
    deadline:Date.now()-1
  }),/deadline exhausted/);
 }finally{fs.rmSync(out,{recursive:true,force:true});}
});

test('an unrelated automatic REPAIR task cannot starve an accepted owner task',()=>{
 const tasks=[
  {id:'repair-auto-one',title:'Automatic incident',kind:'REPAIR',
   source:'repair-loop',priority:999},
  {id:'repair-owner-one',title:'Owner command',kind:'REPAIR',
   source:'owner',priority:600}
 ];
 const ops=tasks.flatMap(task=>[
  {op:'propose',actor:'owner',at:1,task},
  {op:'accept',actor:'owner',at:2,id:task.id}
 ]);
 const doc=applyOps(emptyDocument(0,'owner'),ops).doc;
 assert.equal(nextEligible(doc,['REPAIR']).id,'repair-auto-one');
 assert.equal(nextEligible(doc,['REPAIR'],['owner']).id,'repair-owner-one');
});

test('eligible owner selection cannot be starved by stale HEAD or higher priority',()=>{
 const current='a'.repeat(40),old='b'.repeat(40);
 const tasks=[
  {id:'owner-old-one',title:'Old-head blocker',kind:'REPAIR',
   source:'owner',priority:999,subject:{base:old,chain:'node'}},
  {id:'owner-current-one',title:'Current-head task',kind:'REPAIR',
   source:'owner',priority:200,subject:{base:current,chain:'node'}},
  {id:'owner-current-two',title:'Explicit task ID',kind:'REPAIR',
   source:'owner',priority:100,subject:{base:current,chain:'node'}}
 ];
 const ops=tasks.flatMap(task=>[
  {op:'propose',actor:'owner',at:1,task},
  {op:'accept',actor:'owner',at:2,id:task.id}
 ]);
 const doc=applyOps(emptyDocument(0,'owner'),ops).doc;
 assert.equal(nextEligible(doc,['REPAIR'],['owner']).id,'owner-old-one');
 assert.equal(nextEligible(doc,['REPAIR'],['owner'],current).id,'owner-current-one');
 assert.equal(nextEligible(doc,['REPAIR'],['owner'],current,'owner-current-two').id,'owner-current-two');
 assert.equal(nextEligible(doc,['REPAIR'],['owner'],current,'owner-old-one'),null);
});

test('owner task executes red -> isolated repair -> independent green -> reviewable PATCH and DONE',
 {skip:nested,timeout:180000},()=>{
 const f=fixture(true);
 try{
  const file=path.join(f.state,'development-backlog.json');
  const store=new BacklogStore(file,{actor:'owner'});accepted(store,f);
  const original=fs.readFileSync(path.join(f.root,'.github/continuum/math.mjs'));
  const result=executeOwnerRepairTask({root:f.root,file,out:path.join(f.state,'reports'),budgetMinutes:2});
  assert.equal(result.status,'DONE');
  assert.equal(result.approvedForMerge,false);
  assert.equal(result.head,f.head);
  assert.ok(fs.existsSync(result.patch));
  const patch=fs.readFileSync(result.patch);
  assert.equal(crypto.createHash('sha256').update(patch).digest('hex'),result.patchSha256);
  const receipt=JSON.parse(fs.readFileSync(result.independentReceipt,'utf8'));
  assert.equal(receipt.head,f.head);
  assert.equal(receipt.taskId,'repair-owner-one');
  assert.equal(receipt.patchSha256,result.patchSha256);
  assert.equal(receipt.passed,true);
  assert.equal(receipt.evidenceSufficient,true);
  assert.deepEqual(receipt.reasons,[]);
  assert.ok(receipt.executedTests>0);
  assert.ok(receipt.profiles.every(p=>p.passed&&p.failed===0));
  assert.deepEqual(fs.readFileSync(path.join(f.root,'.github/continuum/math.mjs')),original);
  assert.equal(f.git('status','--porcelain'),'');
  const p=spawnSync('git',['apply','--check',result.patch],{cwd:f.root,encoding:'utf8'});
  assert.equal(p.status,0,p.stderr);
  const final=store.read().doc.tasks.find(t=>t.id==='repair-owner-one');
  assert.equal(final.state,'DONE');
  assert.ok(final.evidence.some(e=>e.profiles.some(p=>p.executed>0&&p.passed)));
  const baseline=JSON.parse(fs.readFileSync(result.report,'utf8'));
  assert.equal(baseline.baseline.passed,false);
  assert.equal(baseline.outcome,'VERIFIED');
  assert.equal(baseline.independent.passed,true);
  assert.equal(baseline.independent.evidenceSufficient,true);
  assert.deepEqual(baseline.independent.profiles,receipt.profiles);
  assert.equal(executeOwnerRepairTask({root:f.root,file,out:path.join(f.state,'reports')}).status,'NO_READY_TASK');
 }finally{fs.rmSync(f.base,{recursive:true,force:true});}
});

test('an owner cancel during the session stops at the next step boundary and is never DONE',
 {skip:nested,timeout:180000},()=>{
 const f=fixture(true);
 try{
  const file=path.join(f.state,'development-backlog.json');
  const store=new BacklogStore(file,{actor:'owner'});accepted(store,f);
  // the owner cancels while candidates are being prepared (the provider hook runs inside the session)
  const cancel=()=>{store.apply([{op:'abort',actor:'android-owner',at:Date.now(),id:'repair-owner-one',reason:'Owner cancelled on Android'}]);return null;};
  cancel.allowedPaths=['.github/continuum/math.mjs'];
  assert.throws(()=>executeOwnerRepairTask({root:f.root,file,out:path.join(f.state,'reports'),budgetMinutes:2,candidateProvider:cancel}),
   /TASK_ABORTED_BY_OWNER/);
  assert.equal(store.read().doc.tasks.find(t=>t.id==='repair-owner-one').state,'ABORTED');
  const [dir]=fs.readdirSync(path.join(f.state,'reports'));
  assert.deepEqual(fs.readdirSync(path.join(f.state,'reports',dir)),['node-junit.xml'],
   'only the baseline ran: no candidate test, no patch, no report, no independent run');
  assert.equal(f.git('status','--porcelain'),'');
  assert.equal(f.git('worktree','list').split('\n').length,1,'temporary worktrees are removed');
 }finally{fs.rmSync(f.base,{recursive:true,force:true});}
});

test('an owner cancel during a running check step ends that step within seconds and is never DONE',
 {skip:nested||(!['linux','android'].includes(process.platform)&&'/proc required'),timeout:180000},()=>{
 const f=fixture(true,{slowTest:true});
 try{
  const file=path.join(f.state,'development-backlog.json');
  const store=new BacklogStore(file,{actor:'owner'});accepted(store,f);
  // the owner cancels from another process (ccc1-abort via Termux) while the baseline check is running
  const script=`import fs from 'node:fs';import {BacklogStore} from ${JSON.stringify(new URL('./development-backlog.mjs',import.meta.url).href)};
const [file,marker]=process.argv.slice(1);const until=Date.now()+60000;
while(!fs.existsSync(marker)&&Date.now()<until)await new Promise(r=>setTimeout(r,25));
new BacklogStore(file,{actor:'android-owner'}).apply([{op:'abort',actor:'android-owner',at:Date.now(),id:'repair-owner-one',reason:'Owner cancelled on Android'}]);
fs.writeFileSync(marker+'.aborted',String(Date.now()));`;
  spawn(process.execPath,['--input-type=module','-e',script,file,f.marker],{detached:true,stdio:'ignore'}).unref();
  assert.throws(()=>executeOwnerRepairTask({root:f.root,file,out:path.join(f.state,'reports'),budgetMinutes:5}),
   /TASK_ABORTED_BY_OWNER/);
  const stoppedAfter=Date.now()-Number(fs.readFileSync(f.marker+'.aborted','utf8'));
  const pid=Number(fs.readFileSync(f.marker,'utf8'));
  let state=null;try{state=fs.readFileSync('/proc/'+pid+'/stat','utf8').split(') ')[1].split(' ')[0];}catch{}
  assert.ok(state===null||state==='Z','the 60 s test file process '+pid+' still runs: '+state);
  assert.ok(stoppedAfter<10000,'the running baseline ended '+stoppedAfter+' ms after the cancel, not after 60 s');
  assert.equal(store.read().doc.tasks.find(t=>t.id==='repair-owner-one').state,'ABORTED');
  const [dir]=fs.readdirSync(path.join(f.state,'reports'));
  const left=fs.readdirSync(path.join(f.state,'reports',dir));
  assert.ok(!left.includes('report.json')&&!left.includes('repair.patch')&&!left.includes('independent-check'),left.join(','));
  assert.equal(f.git('status','--porcelain'),'');
  assert.equal(f.git('worktree','list').split('\n').length,1,'temporary worktrees are removed');
 }finally{fs.rmSync(f.base,{recursive:true,force:true});}
});

test('passing fixture is not reported as successful code development',
 {skip:nested,timeout:180000},()=>{
 const f=fixture(false);
 try{
  const file=path.join(f.state,'development-backlog.json');
  const store=new BacklogStore(file,{actor:'owner'});accepted(store,f);
  const result=executeOwnerRepairTask({root:f.root,file,out:path.join(f.state,'reports'),budgetMinutes:2});
  assert.equal(result.status,'FAILED');assert.equal(result.reason,'NO_FAILURE');
  // maxAttempts defaults to 3: a single failure is retryable (READY), not terminal.
  assert.equal(store.read().doc.tasks[0].state,'READY');
  assert.equal(store.read().doc.tasks[0].attempts,1);
  const second=executeOwnerRepairTask({root:f.root,file,out:path.join(f.state,'reports'),budgetMinutes:2});
  assert.equal(second.status,'FAILED');assert.equal(second.reason,'NO_FAILURE');
  assert.equal(store.read().doc.tasks[0].state,'READY');
  assert.equal(store.read().doc.tasks[0].attempts,2);
  const third=executeOwnerRepairTask({root:f.root,file,out:path.join(f.state,'reports'),budgetMinutes:2});
  assert.equal(third.status,'FAILED');assert.equal(third.reason,'NO_FAILURE');
  const finalTask=store.read().doc.tasks[0];
  assert.equal(finalTask.state,'FAILED');
  assert.equal(finalTask.attempts,3);
  assert.equal(nextEligible(store.read().doc,['REPAIR'],['owner']),null);
  // Every attempt keeps immutable, independent evidence rather than clobbering an earlier report.
  assert.equal(fs.readdirSync(path.join(f.state,'reports')).length,3);
  assert.equal(f.git('status','--porcelain'),'');
 }finally{fs.rmSync(f.base,{recursive:true,force:true});}
});

test('stale owner claim is recovered after worker crash without faking success',
 {skip:nested,timeout:180000},()=>{
 const f=fixture(false);
 try{
  const file=path.join(f.state,'development-backlog.json');
  const store=new BacklogStore(file,{actor:'owner'});accepted(store,f);
  const expiredToken='a'.repeat(32);
  store.apply([{op:'claim',actor:'worker',at:3,id:'repair-owner-one',
    owner:'worker',token:expiredToken,leaseMs:60000}]);
  assert.equal(store.read().doc.tasks[0].state,'IN_PROGRESS');
  const result=executeOwnerRepairTask({
    root:f.root,file,out:path.join(f.state,'reports'),budgetMinutes:2
  });
  assert.equal(result.status,'FAILED');
  assert.equal(result.reason,'NO_FAILURE');
  const task=store.read().doc.tasks[0];
  assert.equal(task.attempts,2,'expired attempt and resumed attempt are both counted');
  assert.equal(task.state,'READY','failed retry remains available while budget remains');
  assert.equal(task.claim,null);
  assert.equal(f.git('status','--porcelain'),'');
 }finally{fs.rmSync(f.base,{recursive:true,force:true});}
});

test('unapproved task, incorrect HEAD, foreign source and unknown chain fail closed',
 {skip:nested,timeout:60000},()=>{
 const f=fixture(true);
 try{
  const file=path.join(f.state,'development-backlog.json');
  const store=new BacklogStore(file,{actor:'owner'});
  store.apply([{op:'propose',actor:'owner',at:1,task:{id:'repair-owner-one',title:'X',
   kind:'REPAIR',source:'owner',subject:{base:f.head,chain:'node'}}}]);
  assert.equal(executeOwnerRepairTask({root:f.root,file,out:path.join(f.state,'reports')}).status,'NO_READY_TASK');
  store.apply([{op:'accept',actor:'owner',at:2,id:'repair-owner-one'}]);
  assert.throws(()=>executeOwnerRepairTask({root:f.root,file,out:path.join(f.root,'reports')}),/outside/);
  const shortcut=path.join(f.state,'symlink-into-checkout');
  fs.symlinkSync(f.root,shortcut,'dir');
  assert.throws(()=>executeOwnerRepairTask({
    root:f.root,file,out:path.join(shortcut,'reports')
  }),/outside/,'symlinked artifact directory must not bypass checkout isolation');
  assert.throws(()=>executeOwnerRepairTask({
    root:f.root,file:path.join(shortcut,'backlog.json'),out:path.join(f.state,'reports')
  }),/outside/,'symlinked backlog directory must not bypass checkout isolation');
  assert.throws(()=>validateOwnerRepairTask({...store.read().doc.tasks[0],source:'cli'},f.head),/owner-accepted/);
  assert.throws(()=>validateOwnerRepairTask({...store.read().doc.tasks[0],subject:{base:f.head,chain:'evil'}},f.head),/owner-accepted/);
  assert.throws(()=>validateOwnerRepairTask(store.read().doc.tasks[0],'f'.repeat(40)),/owner-accepted/);
  assert.equal(store.read().doc.tasks[0].state,'READY');
  assert.equal(f.git('status','--porcelain'),'');
 }finally{fs.rmSync(f.base,{recursive:true,force:true});}
});

test('corrupted backlog never executes repair, and original Git checkout remains intact',
 {skip:nested,timeout:60000},()=>{
 const f=fixture(true);
 try{
  const file=path.join(f.state,'development-backlog.json');
  fs.writeFileSync(file,'{"schema":"continuum-development-backlog","forged":true}');
  assert.throws(()=>executeOwnerRepairTask({root:f.root,file,out:path.join(f.state,'reports')}));
  assert.equal(f.git('status','--porcelain'),'');
  assert.deepEqual(fs.readdirSync(f.state).sort(),['development-backlog.json']);
 }finally{fs.rmSync(f.base,{recursive:true,force:true});}
});
