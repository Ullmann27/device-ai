import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const runner=fileURLToPath(new URL('./continuum-model-center-qa.sh',import.meta.url));
const branch='feature/continuum-model-center-selected-models-20261025';

function fixture(t){
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-model-center-qa-'));
  t.after(()=>fs.rmSync(root,{recursive:true,force:true}));
  fs.mkdirSync(path.join(root,'scripts'));
  fs.copyFileSync(runner,path.join(root,'scripts/continuum-model-center-qa.sh'));
  fs.writeFileSync(path.join(root,'.gitignore'),'.continuum-render-build/\ncontinuum-model-center-qa/\n');
  const git=(...args)=>{
    const r=spawnSync('git',args,{cwd:root,encoding:'utf8'});
    assert.equal(r.status,0,r.stderr);return r.stdout.trim();
  };
  git('init','-q','-b',branch);git('add','.');
  git('-c','user.name=QA Fixture','-c','user.email=qa@example.invalid','commit','-qm','fixture');
  // Only native Java execution is replaced. All Git and shell validation is real.
  // Reaching Java proves the source gate accepted; stop before downloads/builds.
  const java=path.join(root,'.continuum-render-build/jdk17/bin/java');
  fs.mkdirSync(path.dirname(java),{recursive:true});
  fs.writeFileSync(java,'#!/bin/sh\necho QA_JAVA_SENTINEL\nexit 73\n',{mode:0o700});
  const sha=git('rev-parse','HEAD');
  const run=(pin,extra={})=>{
    const env={...process.env,...extra};
    delete env.CONTINUUM_QA_SOURCE_SHA;
    delete env.CONTINUUM_MODEL_CENTER_QA_SHA;
    if(pin!==undefined)env.CONTINUUM_MODEL_CENTER_QA_SHA=pin;
    return spawnSync('bash',[path.join(root,'scripts/continuum-model-center-qa.sh')],{
      cwd:root,encoding:'utf8',timeout:10000,env
    });
  };
  return {root,sha,git,run};
}

test('model-center QA rejects absent, malformed and wrong pins before toolchain side effects',t=>{
  const f=fixture(t);
  for(const pin of [undefined,'','abc','f'.repeat(40)]){
    const r=f.run(pin);
    assert.equal(r.status,69,`${String(pin)}: ${r.stdout}${r.stderr}`);
    assert.match(r.stderr,/source SHA/);
    assert.doesNotMatch(r.stdout,/QA_JAVA_SENTINEL/);
    assert.equal(fs.existsSync(path.join(f.root,'continuum-model-center-qa')),false);
  }
});

test('model-center QA accepts its exact pin without an unrelated SHA variable',t=>{
  const f=fixture(t),r=f.run(f.sha);
  assert.equal(r.status,73,r.stdout+r.stderr);
  assert.match(r.stdout,new RegExp('CONTINUUM_QA_SOURCE='+f.sha));
  assert.match(r.stdout,/QA_JAVA_SENTINEL/);
  assert.doesNotMatch(r.stdout,/QA_PASSED/);
});

test('model-center QA refuses dirty source even when HEAD matches',t=>{
  const f=fixture(t);
  for(const file of ['scripts/continuum-model-center-qa.sh','untracked.txt']){
    fs.appendFileSync(path.join(f.root,file),'\n# unverified source\n');
    const r=f.run(f.sha);
    assert.equal(r.status,69,r.stdout+r.stderr);
    assert.match(r.stderr,/clean/);
    assert.doesNotMatch(r.stdout,/QA_JAVA_SENTINEL/);
    if(file==='untracked.txt')fs.unlinkSync(path.join(f.root,file));
    else f.git('restore',file);
  }
});

test('model-center QA rejects another branch without changing its checkout',t=>{
  const f=fixture(t);f.git('switch','-qc','unrelated-branch');
  const r=f.run(f.sha);
  assert.equal(r.status,69,r.stdout+r.stderr);
  assert.match(r.stderr,/branch/);
  assert.equal(f.git('status','--porcelain'),'');
  assert.doesNotMatch(r.stdout,/QA_JAVA_SENTINEL/);
});
