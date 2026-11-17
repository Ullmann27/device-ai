import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const RUNNER=fileURLToPath(new URL('./continuum-m6-review-qa.sh',import.meta.url));
function fixture(t){
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-m6-qa-'));
 t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
 const root=path.join(dir,'source'),home=path.join(dir,'owner-home');
 fs.mkdirSync(path.join(root,'scripts'),{recursive:true});fs.mkdirSync(home);
 fs.copyFileSync(RUNNER,path.join(root,'scripts/continuum-m6-review-qa.sh'));
 const git=(...args)=>{
  const r=spawnSync('git',args,{cwd:root,encoding:'utf8'});
  assert.equal(r.status,0,r.stderr);return r.stdout.trim();
 };
 git('init','-q','-b','integration/continuum-m6-all-verified-20261025');
 git('add','.');git('-c','user.name=QA Fixture','-c','user.email=qa@example.invalid','commit','-qm','test source');
 const sha=git('rev-parse','HEAD');
 const run=(expected,extraEnv={})=>spawnSync('bash',[RUNNER],{
  cwd:root,encoding:'utf8',timeout:15000,
  env:{...process.env,HOME:home,CONTINUUM_M6_QA_EXPECTED_SHA:expected,...extraEnv}
 });
 return {root,home,sha,git,run};
}
test('M6 QA rejects unpinned, mismatched and dirty source before starting',t=>{
 const f=fixture(t);
 for(const sha of ['', 'f'.repeat(40)]){
  const result=f.run(sha);
  assert.equal(result.status,69,result.stderr);
  assert.match(result.stderr,/mismatched pinned source SHA/);
 }
 assert.equal(fs.existsSync(path.join(f.home,'.local')),false);
 fs.writeFileSync(path.join(f.root,'untracked.txt'),'not clean');
 const dirty=f.run(f.sha);
 assert.equal(dirty.status,69,dirty.stderr);
 assert.match(dirty.stderr,/source checkout must start clean/);
 assert.equal(fs.existsSync(path.join(f.home,'.local')),false);
});
test('M6 QA rejects another branch and preserves owner checkout',t=>{
 const f=fixture(t);f.git('checkout','-qb','unrelated-branch');
 const result=f.run(f.git('rev-parse','HEAD'));
 assert.equal(result.status,69,result.stderr);
 assert.match(result.stderr,/unexpected branch/);
 assert.equal(fs.existsSync(path.join(f.home,'.local')),false);
});
test('M6 QA allows an explicitly pinned research branch but never main',t=>{
 const f=fixture(t),branch='research/continuum-omega-fixture';f.git('checkout','-qb',branch);
 assert.equal(f.run(f.sha).status,69);
 const result=f.run(f.sha,{CONTINUUM_M6_QA_EXPECTED_BRANCH:branch});
 assert.equal(result.status,1); // reaches the real missing-source syntax failure
 assert.match(result.stdout,/syntax-continuum-local-brain/);
 f.git('checkout','-qb','main');
 assert.equal(f.run(f.sha,{CONTINUUM_M6_QA_EXPECTED_BRANCH:'main'}).status,69);
});
test('M6 QA writes fail-closed nonrelease evidence outside the source tree',t=>{
 const f=fixture(t);
 // The synthetic fixture deliberately lacks all production JS files; the
 // pinned first syntax gate must fail without claiming any checks passed.
 const result=f.run(f.sha);
 assert.equal(result.status,1,result.stderr);
 const report=path.join(f.home,'.local/state/device-ai-continuum/m6-review-qa',f.sha,'RESULT.txt');
 assert.equal(fs.existsSync(report),true);
 const body=fs.readFileSync(report,'utf8');
 assert.match(body,/owner_apk_signed=false/);
 assert.match(body,/release=false/);
 assert.match(body,/syntax-continuum-local-brain.mjs=FAIL\(exit=1\)/);
 assert.match(body,/exit_code=1/);
 assert.doesNotMatch(body,/CONTINUUM_M6_QA_CHECKS_PASSED/);
 assert.equal(f.git('status','--porcelain'),'');
 assert.equal(fs.existsSync(path.join(f.root,'app/build/outputs/apk')),false);
});
