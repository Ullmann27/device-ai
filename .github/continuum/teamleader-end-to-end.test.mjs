import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {runTeamleaderCycle,localTeamleaderDriver,decodeLeaderState}
  from './autonomous-teamleader.mjs';
import {verifyRepairHandoffs} from './teamleader-handoffs.mjs';

const nested=Boolean(process.env.CONTINUUM_REPAIR_DEPTH);
function fixture({broken}) {
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-real-teamleader-'));
 const home=path.join(root,'source');
 const stateDir=path.join(root,'private-state');
 const folder=path.join(home,'.github','continuum');
 fs.mkdirSync(folder,{recursive:true});
 fs.writeFileSync(path.join(folder,'math.mjs'),broken
  ?"export function square(n){return n*n}\nexport function quad(n){return sqare(square(n))}\n"
  :"export function square(n){return n*n}\nexport function quad(n){return square(square(n))}\n");
 fs.writeFileSync(path.join(folder,'math.test.mjs'),
  "import test from 'node:test';\n"+
  "import assert from 'node:assert/strict';\n"+
  "import {quad} from './math.mjs';\n"+
  "test('quad regression',()=>assert.equal(quad(2),16));\n");
 const git=(...args)=>{
  const result=spawnSync('git',args,{cwd:home,encoding:'utf8',timeout:10000});
  assert.equal(result.status,0,'git '+args.join(' ')+' -> '+result.stderr);
  return result.stdout.trim();
 };
 git('init','-q');
 git('-c','user.name=continuum-test','-c','user.email=continuum-test@example.invalid',
   'add','.');
 git('-c','user.name=continuum-test','-c','user.email=continuum-test@example.invalid',
   'commit','-qm','isolated fixture');
 return {root,home,stateDir,git,head:git('rev-parse','HEAD')};
}
test('actual offline Teamleader repairs a broken Git fixture and retains only a reviewable patch',
 {skip:nested,timeout:180000},async()=>{
 const f=fixture({broken:true});
 try {
  const driver=await localTeamleaderDriver();
  const result=runTeamleaderCycle({root:f.home,stateDir:f.stateDir,driver,budgetMinutes:2});
  assert.equal(result.head,f.head);
  assert.equal(result.kind,'BASELINE_REPAIR');
  assert.equal(result.outcome,'VERIFIED_PATCH_PENDING_REVIEW',result.detail);
  assert.ok(result.artifact);
  const saved=JSON.parse(fs.readFileSync(result.artifact,'utf8'));
  assert.equal(saved.outcome,'VERIFIED');
  assert.equal(saved.baseline.passed,false);
  assert.equal(saved.verified.strategy,'JS_TYPO_REFERENCE');
  assert.ok(saved.verified.tests.some(t=>t.tests.executed>0&&t.passed));
  assert.equal(saved.handoffs.conclusion,'VERIFIED');
  assert.equal(verifyRepairHandoffs(saved.handoffs),true);
  assert.equal(saved.handoffs.messages.at(-1).approvedForMerge,false);
  const patch=fs.readFileSync(path.join(path.dirname(result.artifact),'repair.patch'),'utf8');
  assert.match(patch,/-export function quad\(n\)\{return sqare/);
  assert.match(patch,/\+export function quad\(n\)\{return square/);
  assert.match(fs.readFileSync(path.join(f.home,'.github/continuum/math.mjs'),'utf8'),
    /return sqare/,'source checkout must not be modified by autonomous repair');
  assert.equal(f.git('status','--porcelain'),'');
  const leader=decodeLeaderState(fs.readFileSync(result.statePath));
  assert.equal(leader.events.at(-1).outcome,'VERIFIED_PATCH_PENDING_REVIEW');
  const ledger=JSON.parse(fs.readFileSync(path.join(f.stateDir,'repair-ledger.json')));
  assert.ok(ledger.attempts.some(a=>a.outcome==='VERIFIED'));
  assert.equal(ledger.sessions.at(-1).outcome,'VERIFIED');
  assert.ok(fs.existsSync(path.join(f.stateDir,'teamleader-state.json')));
 } finally {fs.rmSync(f.root,{recursive:true,force:true});}
});
test('actual passing Git fixture generates no fake repair, patch, or changed source',
 {skip:nested,timeout:180000},async()=>{
 const f=fixture({broken:false});
 try {
  const driver=await localTeamleaderDriver();
  const result=runTeamleaderCycle({root:f.home,stateDir:f.stateDir,driver,budgetMinutes:2});
  assert.equal(result.outcome,'NO_FAILURE',result.detail);
  const saved=JSON.parse(fs.readFileSync(result.artifact,'utf8'));
  assert.equal(saved.baseline.passed,true);
  assert.equal(saved.verified,null);
  assert.ok(!fs.existsSync(path.join(path.dirname(result.artifact),'repair.patch')));
  assert.equal(saved.handoffs.messages.at(-1).event,'NO_AUTOMATIC_MERGE');
  assert.equal(f.git('status','--porcelain'),'');
 } finally {fs.rmSync(f.root,{recursive:true,force:true});}
});
