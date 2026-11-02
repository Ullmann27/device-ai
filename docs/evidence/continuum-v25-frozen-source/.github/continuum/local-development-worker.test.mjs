import test from 'node:test';
import assert from 'node:assert/strict';
import {evaluateCheckout,classifyNativeTestRun} from '../../scripts/continuum-local-development-worker.mjs';
const tap=parts=>'# tests '+parts.tests+'\n# pass '+parts.pass+
 '\n# fail '+parts.fail+'\n# cancelled '+parts.cancelled+
 '\n# skipped '+parts.skipped+'\n# todo '+parts.todo+'\n';
const ok={tests:3,pass:3,fail:0,cancelled:0,skipped:0,todo:0};
test('checks nonproductive clean branch and bounded verified inventory',()=>{
 const a=evaluateCheckout({branch:'copilot/pr-12-working-first-vertical-slice',
  dirty:false,tests:['neural-core.test.mjs','coding-task.test.mjs']});
 assert.deepEqual(a.tests,['coding-task.test.mjs','neural-core.test.mjs']);
});
test('refuses main, release, dirty and invalid inventories',()=>{
 for(const branch of ['main','production','release/v1','../main',''])
   assert.throws(()=>evaluateCheckout({branch,dirty:false,tests:['a.test.mjs']}),
     /UNSAFE_CHECKOUT/);
 assert.throws(()=>evaluateCheckout({branch:'dev',dirty:true,tests:['a.test.mjs']}),
   /UNSAFE_CHECKOUT/);
 for(const tests of [[],['../../evil.test.mjs'],['bad.exe']])
   assert.throws(()=>evaluateCheckout({branch:'dev',dirty:false,tests}),
     /UNSAFE_TEST_INVENTORY/);
});
test('real node TAP success does not imply a patch was approved',()=>{
 const result=classifyNativeTestRun({exitCode:0,stdout:tap(ok)});
 assert.equal(result.status,'ALL_EXECUTED_PASS');
 assert.equal(result.tests,3);
});
test('skipped and TODO mean incomplete quality gate',()=>{
 for(const variant of [{...ok,pass:2,skipped:1},{...ok,pass:2,todo:1}])
 assert.equal(classifyNativeTestRun({exitCode:0,stdout:tap(variant)}).status,
  'PASS_WITH_UNEXECUTED_CASES');
});
test('missing summary, timeout, failed case or invalid totals are never green',()=>{
 assert.equal(classifyNativeTestRun({exitCode:0,stdout:'ok 1 - incomplete'}).status,'FAILED');
 assert.equal(classifyNativeTestRun({exitCode:null,stdout:tap(ok),error:'SIGTERM'}).status,'FAILED');
 assert.equal(classifyNativeTestRun({exitCode:0,stdout:tap({...ok,pass:2,fail:1})}).status,'FAILED');
 assert.equal(classifyNativeTestRun({exitCode:0,stdout:tap({...ok,tests:4})}).status,'FAILED');
});

test('native worker refuses untracked test injection and symlinked test entrypoints',async()=>{
 const fs=await import('node:fs');const os=await import('node:os');const path=await import('node:path');const {spawnSync}=await import('node:child_process');const {fileURLToPath}=await import('node:url');
 const source=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');const top=fs.mkdtempSync(path.join(os.tmpdir(),'worker-inventory-'));const repo=path.join(top,'repo');
 try{
  fs.mkdirSync(path.join(repo,'scripts'),{recursive:true});fs.mkdirSync(path.join(repo,'.github/continuum'),{recursive:true});
  for(const name of ['continuum-local-development-worker.mjs','continuum-worker-lease.mjs'])fs.copyFileSync(path.join(source,'scripts',name),path.join(repo,'scripts',name));
  const good=path.join(repo,'.github/continuum/good.test.mjs');fs.writeFileSync(good,"import test from 'node:test';test('tracked case',()=>{});\n");
  for(const args of [['init','-q','-b','test/inventory'],['add','.'],['-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','fixture']])assert.equal(spawnSync('git',args,{cwd:repo}).status,0);
  const marker=path.join(top,'injection-executed');const evil=path.join(repo,'.github/continuum/injected.test.mjs');
  fs.writeFileSync(evil,"import fs from 'node:fs';fs.writeFileSync("+JSON.stringify(marker)+",'bad');throw Error('untracked test injection');\n");
  const env={...process.env};delete env.NODE_TEST_CONTEXT;
  const run=()=>spawnSync(process.execPath,[path.join(repo,'scripts/continuum-local-development-worker.mjs'),'--once',path.join(top,'out')],{cwd:repo,env,encoding:'utf8',timeout:10000});
  const allowed=run();assert.equal(allowed.status,0,allowed.stderr);assert.equal(fs.existsSync(marker),false);
  fs.unlinkSync(good);fs.symlinkSync(evil,good);const denied=run();assert.equal(denied.status,1);assert.match(denied.stderr,/UNSAFE_TEST_FILE_TYPE/);assert.equal(fs.existsSync(marker),false);
 }finally{fs.rmSync(top,{recursive:true,force:true});}
});
