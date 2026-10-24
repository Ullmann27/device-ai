import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {createWorktree,removeWorktree,repairSession} from './repair-loop.mjs';
import {executeOwnerRepairTask} from './backlog-repair-worker.mjs';
import {BacklogStore} from './development-backlog.mjs';
import {critique} from './repair-critic.mjs';
const nested=Boolean(process.env.CONTINUUM_REPAIR_DEPTH);
const moduleText='export function distance(a,b){return a-b;}\n';
function fixture({twoFiles=false}={}) {
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-scoped-repair-'));
 const root=path.join(dir,'checkout'),state=path.join(dir,'state');
 fs.mkdirSync(path.join(root,'lib'),{recursive:true});fs.mkdirSync(path.join(root,'.github/continuum'),{recursive:true});fs.mkdirSync(state);
 fs.writeFileSync(path.join(root,'lib/math.mjs'),moduleText);
 if(twoFiles)fs.writeFileSync(path.join(root,'lib/other.mjs'),moduleText);
 fs.writeFileSync(path.join(root,'.github/continuum/spec.test.mjs'),
  "import test from 'node:test';import assert from 'node:assert/strict';import {distance} from '../../lib/math.mjs';\n"+
  (twoFiles?"import {distance as other} from '../../lib/other.mjs';\n":'')+
  "test('positive',()=>assert.equal(distance(8,3),5));\ntest('reverse',()=>assert.equal(distance(3,8),5));\n"+
  "test('negative',()=>assert.equal(distance(-9,-2),7));\n"+
  (twoFiles?"test('second',()=>assert.equal(other(3,8),5));\n":''));
 const git=(...args)=>{const r=spawnSync('git',args,{cwd:root,encoding:'utf8'});assert.equal(r.status,0,r.stderr);return r.stdout.trim();};
 git('init','-q');git('add','.');git('-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','faulty baseline');
 return {dir,root,state,git,head:git('rev-parse','HEAD')};
}
function provider(f,respond,{allowedPaths=['lib/math.mjs'],calls=3,commit=f.head,id='coding-scoped-one',chain='node'}={}) {
 const taskSpec=Object.freeze({schema:'continuum-coding-task',version:1,id,sourceCommit:commit,kind:'BUGFIX',goal:'Repair distance',
  allowedPaths:Object.freeze(allowedPaths),acceptance:Object.freeze(['Distances are nonnegative']),chain,
  limits:Object.freeze({maxModelCalls:calls,maxContextChars:12000})});
 return Object.assign(respond,{taskSpec,allowedPaths:taskSpec.allowedPaths});
}
function proposal(f,newText,files=['lib/math.mjs']) {return {mode:'LOCAL_CODING_PATCH_PROPOSAL_V1',trusted:false,autoApply:false,
 plan:['Use absolute difference'],hypothesis:'The subtraction sign fails for reverse positions',
 patches:files.map(file=>({file,oldText:'return a-b;',newText})),
 context:{commit:f.head,contextSha256:'a'.repeat(64),ranges:[]},localRequests:1};}
function session(f,candidateProvider,extra={}) {
 const wt=createWorktree(f.root,f.head);
 try{return repairSession({wt,base:f.head,chain:'node',out:path.join(f.state,'report'),candidateProvider,...extra});}
 finally{removeWorktree(f.root,wt);}
}
function accepted(f) {
 const file=path.join(f.state,'backlog.json'),store=new BacklogStore(file,{actor:'owner'});
 store.apply([{op:'propose',actor:'owner',at:1,task:{id:'coding-scoped-one',title:'Repair distance',kind:'REPAIR',source:'owner',priority:800,subject:{base:f.head,chain:'node'}}},
 {op:'accept',actor:'owner',at:2,id:'coding-scoped-one'}]);return {file,store};
}
test('owner worker refuses a supplied untrusted executor before claim or checks',{skip:nested},()=>{
 const f=fixture();try{const {file,store}=accepted(f);let called=false;
 assert.throws(()=>executeOwnerRepairTask({root:f.root,file,out:path.join(f.state,'out'),executor:{execute(){called=true;}}}),/UNTRUSTED_ISOLATION_EXECUTOR/);
 assert.equal(called,false);assert.equal(store.read().doc.tasks[0].state,'READY');assert.equal(store.read().doc.tasks[0].attempts,0);
 assert.equal(fs.existsSync(path.join(f.state,'out')),false);
 }finally{fs.rmSync(f.dir,{recursive:true,force:true});}
});
test('scoped task identity and commit are checked before claiming the owner task',{skip:nested},()=>{
 for(const change of [{id:'coding-different-one'},{commit:'b'.repeat(40)},{chain:'android',allowedPaths:['app/src/main/java/pkg/Math.kt']}]) {
 const f=fixture();try{const {file,store}=accepted(f);let calls=0;
 const candidateProvider=provider(f,()=>{calls++;return proposal(f,'return a>b?a-b:b-a;');},change);
 assert.throws(()=>executeOwnerRepairTask({root:f.root,file,out:path.join(f.state,'out'),candidateProvider}),/CODING_TASK_BINDING_MISMATCH/);
 assert.equal(calls,0);assert.equal(store.read().doc.tasks[0].state,'READY');assert.equal(store.read().doc.tasks[0].attempts,0);
 }finally{fs.rmSync(f.dir,{recursive:true,force:true});}}
});
test('scoped revision receives actual failed diagnostics and bounded history then verifies independently',{skip:nested},()=>{
 const f=fixture();try{const {file,store}=accepted(f);const inputs=[];
 const candidateProvider=provider(f,input=>{inputs.push(input);return proposal(f,inputs.length===1?'return 5;':'return a>b?a-b:b-a;');});
 const result=executeOwnerRepairTask({root:f.root,file,out:path.join(f.state,'out'),candidateProvider});
 assert.equal(result.status,'DONE');assert.equal(inputs.length,2);
 assert.equal(inputs[1].history.length,1);assert.equal(inputs[1].history[0].outcome,'FAILED_TESTS');
 assert.match(inputs[1].history[0].candidate,/^[a-f0-9]{16}$/);assert.equal(inputs[1].history[0].hypothesis,'The subtraction sign fails for reverse positions');
 assert.notDeepEqual(inputs[1].diagnostics,inputs[0].diagnostics);
 const report=JSON.parse(fs.readFileSync(result.report,'utf8'));
 assert.equal(report.verified.strategy,'LOCAL_SCOPED_PATCH');assert.equal(report.inference.localRequests,2);assert.equal(report.inference.verifiedModelExecution,false);
 assert.equal(report.independent.passed,true);assert.equal(store.read().doc.tasks[0].state,'DONE');
 assert.equal(fs.readFileSync(path.join(f.root,'lib/math.mjs'),'utf8'),moduleText);
 }finally{fs.rmSync(f.dir,{recursive:true,force:true});}
});
test('repeated scoped patch is rejected without a second candidate check',{skip:nested},()=>{
 const f=fixture();try{let calls=0;const report=session(f,provider(f,()=>{calls++;return proposal(f,'return 5;');}));
 assert.equal(calls,3);assert.equal(report.outcome,'NO_VERIFIED_REPAIR');
 assert.equal(report.attempts.filter(a=>a.tests).length,1);assert.equal(report.inference.repeatedCandidates,2);
 assert.equal(report.inference.localRequests,3);
 }finally{fs.rmSync(f.dir,{recursive:true,force:true});}
});
test('scoped proposals cannot edit outside task grants or change context commit',{skip:nested},()=>{
 for(const kind of ['path','commit']) {const f=fixture({twoFiles:true});try{
 const report=session(f,provider(f,()=>{const p=proposal(f,'return a>b?a-b:b-a;',kind==='path'?['lib/other.mjs']:['lib/math.mjs']);if(kind==='commit')p.context.commit='b'.repeat(40);return p;},{calls:1}));
 assert.equal(report.outcome,'NO_VERIFIED_REPAIR');assert.equal(report.attempts.some(a=>a.tests),false);assert.equal(report.inference.candidateStatus,'UNAVAILABLE_OR_REJECTED');
 }finally{fs.rmSync(f.dir,{recursive:true,force:true});}}
});
test('scoped pure numeric changes can cover two files on the host',{skip:nested},()=>{
 const f=fixture({twoFiles:true});try{const report=session(f,provider(f,()=>proposal(f,'return a>b?a-b:b-a;',['lib/math.mjs','lib/other.mjs']),{allowedPaths:['lib/math.mjs','lib/other.mjs']}));
 assert.equal(report.outcome,'VERIFIED');assert.deepEqual(report.verified.files,['lib/math.mjs','lib/other.mjs']);assert.equal(report.verified.reviewRequired,true);
 }finally{fs.rmSync(f.dir,{recursive:true,force:true});}
});
test('general scoped source is blocked before executing candidate code without isolation',{skip:nested},()=>{
 const f=fixture();try{
 const report=session(f,provider(f,()=>proposal(f,'return String(a-b);'),{calls:1}));
 assert.equal(report.outcome,'NO_VERIFIED_REPAIR');assert.equal(report.attempts[0].outcome,'REJECTED_BY_EXECUTION_POLICY');assert.equal(report.attempts[0].tests,undefined);
 }finally{fs.rmSync(f.dir,{recursive:true,force:true});}
});
test('new coding and isolation control files stay protected by the critic',()=>{
 for(const file of ['.github/continuum/coding-provider.mjs','.github/continuum/coding-task.mjs','.github/continuum/isolated-runner.mjs'])
 assert.equal(critique({strategy:'LOCAL_SCOPED_PATCH',edits:[{file,oldText:'export const n=1;',newText:'export const n=2;'}]}).approved,false,file);
});

test('general scoped proposals cannot mutate test APIs or write their own reports',()=>{
 const oldText='export function clean(a){return a;}';
 for(const newText of [
  "import assert from 'node:assert/strict';assert.equal=()=>{};\n"+oldText,
  "import test from 'node:test';\n"+oldText,
  "import fs from 'node:fs';fs.writeFileSync('/tmp/TEST.xml','green');\n"+oldText,
  "const x=globalThis.process;\n"+oldText
 ])assert.equal(critique({strategy:'LOCAL_SCOPED_PATCH',edits:[{file:'lib/text.mjs',oldText,newText}]}).approved,false,newText);
});

test('scoped successes remain owner-review evidence without automatic learning',async()=>{
 const {emptyLedger,recordSession}=await import('./repair-ledger.mjs');
 const report={outcome:'VERIFIED',base:'a'.repeat(40),chain:'node',verified:{strategy:'LOCAL_SCOPED_PATCH',patchSha256:'b'.repeat(64)},
  attempts:[{outcome:'VERIFIED',strategy:'LOCAL_SCOPED_PATCH',candidate:'a'.repeat(16),diagnostic:'b'.repeat(16),diagnosticKind:'JS_ASSERTION',edits:[],tests:[]}]};
 const ledger=recordSession(emptyLedger(),report,{independentPass:true});
 assert.equal(ledger.sessions[0].outcome,'PENDING_OWNER_REVIEW');assert.deepEqual(ledger.experiences,[]);assert.equal(ledger.attempts[0].patchSha256,null);
});

test('scoped provider failures count against the fixed model-call budget',{skip:nested},()=>{
 const f=fixture();try{let calls=0;const report=session(f,provider(f,()=>{calls++;throw Error('transport unavailable');},{calls:2}));
 assert.equal(calls,2);assert.equal(report.inference.localRequests,2);assert.equal(report.outcome,'NO_VERIFIED_REPAIR');assert.deepEqual(report.attempts,[]);
 }finally{fs.rmSync(f.dir,{recursive:true,force:true});}
});
test('total scoped candidate limit bounds revisions even when model budget remains',{skip:nested},()=>{
 const f=fixture();try{let calls=0;const report=session(f,provider(f,()=>{calls++;return proposal(f,calls===1?'return 5;':'return a>b?a-b:b-a;');}),{maxCandidates:1});
 assert.equal(calls,1);assert.equal(report.coding.candidateCount,1);assert.equal(report.outcome,'NO_VERIFIED_REPAIR');
 }finally{fs.rmSync(f.dir,{recursive:true,force:true});}
});
test('local teamleader forwards the scoped provider through the canonical owner worker',{skip:nested},async()=>{
 const {localTeamleaderDriver}=await import('./autonomous-teamleader.mjs');
 const f=fixture();try{accepted(f);let calls=0;
 const driver=await localTeamleaderDriver({candidateProvider:provider(f,()=>{calls++;return proposal(f,'return a>b?a-b:b-a;');})});
 // The driver uses the existing canonical backlog filename.
 fs.renameSync(path.join(f.state,'backlog.json'),path.join(f.state,'development-backlog.json'));
 const result=driver.ownerRepair(f.root,f.state,f.head,'coding-scoped-one',{budgetMinutes:2});
 assert.equal(result.status,'DONE');assert.equal(calls,1);assert.equal(JSON.parse(fs.readFileSync(result.report,'utf8')).independent.passed,true);
 }finally{fs.rmSync(f.dir,{recursive:true,force:true});}
});

test('a no-op model rejection is passed as actionable feedback before second valid candidate',{skip:nested},()=>{
 const f=fixture();try{
  let calls=0;const digest='e'.repeat(64);const inputs=[];
  const candidateProvider=provider(f,input=>{
   inputs.push(input);calls++;
   if(calls===1){const error=Error('CODING_NOOP_PATCH');error.code='CODING_NOOP_PATCH';error.responseFingerprint=digest;throw error;}
   return proposal(f,'return a>b?a-b:b-a;');
  });
  const report=session(f,candidateProvider);
  assert.equal(report.outcome,'VERIFIED');
  assert.equal(calls,2);
  assert.equal(report.inference.localRequests,2);
  assert.equal(inputs[1].history.length,1);
  assert.equal(inputs[1].history[0].candidate,digest);
  assert.equal(inputs[1].history[0].outcome,'CODING_NOOP_PATCH');
  assert.match(inputs[1].history[0].diagnostic,/identical/);
  assert.equal(report.verified.reviewRequired,true);
  assert.equal(fs.readFileSync(path.join(f.root,'lib/math.mjs'),'utf8'),moduleText);
 }finally{fs.rmSync(f.dir,{recursive:true,force:true});}
});
test('repeated identical rejected model answer stops without starting third process',{skip:nested},()=>{
 const f=fixture();try{
  let calls=0;
  const candidateProvider=provider(f,()=>{
   calls++;const error=Error('CODING_NOOP_PATCH');error.code='CODING_NOOP_PATCH';
   error.responseFingerprint='e'.repeat(64);throw error;
  },{calls:3});
  const report=session(f,candidateProvider);
  assert.equal(calls,2);
  assert.equal(report.inference.localRequests,2);
  assert.equal(report.inference.repeatedCandidates,1);
  assert.equal(report.inference.candidateStatus,'REPEATED_INVALID_RESPONSE');
  assert.equal(report.outcome,'NO_VERIFIED_REPAIR');
  assert.equal(report.verified,null);
  assert.equal(fs.readFileSync(path.join(f.root,'lib/math.mjs'),'utf8'),moduleText);
 }finally{fs.rmSync(f.dir,{recursive:true,force:true});}
});
