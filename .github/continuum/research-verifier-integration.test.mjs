import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {BacklogStore} from './development-backlog.mjs';
import {executeOwnerRepairTask} from './backlog-repair-worker.mjs';

const nested=Boolean(process.env.CONTINUUM_REPAIR_DEPTH);
function fixture(t){
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-research-gate-'));t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
 const root=path.join(dir,'source'),state=path.join(dir,'state');
 fs.mkdirSync(path.join(root,'lib'),{recursive:true});fs.mkdirSync(path.join(root,'.github/continuum'),{recursive:true});fs.mkdirSync(state);
 fs.writeFileSync(path.join(root,'lib/math.mjs'),'export function distance(a,b){return a-b;}\n');
 fs.writeFileSync(path.join(root,'.github/continuum/math.test.mjs'),
  "import test from 'node:test';import assert from 'node:assert/strict';import {distance} from '../../lib/math.mjs';test('example',()=>assert.equal(distance(3,8),5));\n");
 const git=(...args)=>{const r=spawnSync('git',args,{cwd:root,encoding:'utf8'});assert.equal(r.status,0,r.stderr);return r.stdout.trim();};
 git('init','-q');git('add','.');git('-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','fixed weak test');
 const base=git('rev-parse','HEAD'),id='omega-repair-one',file=path.join(state,'backlog.json');
 const store=new BacklogStore(file,{actor:'owner'});store.apply([
  {op:'propose',actor:'owner',at:1,task:{id,title:'Repair distance',kind:'REPAIR',source:'owner',priority:800,subject:{base,chain:'node'}}},
  {op:'accept',actor:'owner',at:2,id}]);
 const calls=[];
 const candidateProvider=Object.assign(input=>{
  calls.push(input);return {mode:'LOCAL_CODING_PATCH_PROPOSAL_V1',trusted:false,autoApply:false,
   plan:['Repair within the existing function'],hypothesis:'The sign or a constant may explain the failure',
   patches:[{file:'lib/math.mjs',oldText:'return a-b;',newText:calls.length===1?'return 5;':'return a>b?a-b:b-a;'}],
   context:{commit:base,contextSha256:'a'.repeat(64),ranges:[]},localRequests:1};
 },{taskSpec:{schema:'continuum-coding-task',version:1,id,sourceCommit:base,kind:'BUGFIX',goal:'Repair distance',
  allowedPaths:['lib/math.mjs'],acceptance:['Nonnegative distance'],chain:'node',limits:{maxModelCalls:2,maxContextChars:4096}}});
 return {root,file,out:path.join(state,'reports'),base,calls,candidateProvider,git};
}
function gate(f,respond){
 const suiteSha256='c'.repeat(64);
 return Object.assign(request=>({schema:'continuum-candidate-verification',version:1,base:f.base,
  candidateSha256:request.candidateSha256,suiteSha256,status:'PASS',checks:1,counterexample:null,...respond(request)}),
 {suiteSha256,expectedChecks:1});
}
test('opt-in verifier rejects a test-passing constant and feeds the counterexample into the same repair loop',{skip:nested},t=>{
 const f=fixture(t);let checks=0;
 const candidateVerifier=gate(f,r=>{checks++;return r.sources[0].source.includes('return 5;')?
  {status:'FAIL',counterexample:{id:'identity',file:'lib/math.mjs',inputs:[2,2],actual:5,expected:0}}:{};});
 const result=executeOwnerRepairTask({...f,candidateVerifier});
 assert.equal(result.status,'DONE');assert.equal(f.calls.length,2);
 assert.equal(checks,3,'first veto, repaired candidate, independent worktree');
 assert.match(f.calls[1].diagnostics[0].message,/identity/);
 const report=JSON.parse(fs.readFileSync(result.report));
 assert.equal(report.attempts[0].outcome,'INSUFFICIENT_EVIDENCE');
 assert.equal(report.attempts[1].researchVerification.status,'PASS');
 assert.equal(report.independent.researchVerification.status,'PASS');
 assert.equal(f.git('status','--porcelain'),'');
});
test('independent research veto blocks completion even after primary tests passed',{skip:nested},t=>{
 const f=fixture(t);let n=0;
 const result=executeOwnerRepairTask({...f,candidateVerifier:gate(f,()=>++n===1?{}:{status:'ABSTAIN',checks:0})});
 assert.equal(result.status,'FAILED');assert.equal(result.reason,'INDEPENDENT_CHECK_FAILED');
 assert.equal(f.calls.length,1);
});
test('a throwing optional verifier exhausts the fixed proposal budget without publishing a patch',{skip:nested},t=>{
 const f=fixture(t),candidateVerifier=gate(f,()=>{throw Error('unavailable');});
 const result=executeOwnerRepairTask({...f,candidateVerifier});
 assert.equal(result.status,'FAILED');assert.equal(f.calls.length,2);
 const report=JSON.parse(fs.readFileSync(result.report));
 assert.equal(report.verified,null);assert.equal(report.outcome,'NO_VERIFIED_REPAIR');
});
