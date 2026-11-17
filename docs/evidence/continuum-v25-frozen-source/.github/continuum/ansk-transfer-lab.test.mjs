import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {sha256} from './repair-diagnostics.mjs';
import {writeImmutableAnskRecipe,readImmutableAnskRecipe} from './ansk-recipe-store.mjs';
import {verifyAnskTransferArm} from './ansk-lab-contract.mjs';
import {parseAnskTransferArgs,runAnskTransferLab} from './ansk-transfer-lab.mjs';

const file='app/src/main/java/example/NewProbe.kt';
const source='package example\n\nimport java.nio.file.Files\nimport java.nio.file.Paths\n\nclass NewProbe {\n fun exists() = Files.exists(Paths.get("x"))\n}\n';
function receipt(){
 const body={schema:'continuum-ansk-shadow-recipe',version:1,authority:'SHADOW_ONLY',
  stage:'QUARANTINED_REVIEW_REQUIRED',head:'a'.repeat(40),
  sourceFile:'app/src/main/java/example/Origin.kt',sourceSha256:'b'.repeat(64),
  mutatedSha256:'c'.repeat(64),repairedSha256:'d'.repeat(64),editSha256:'e'.repeat(64),
  diagnosis:'UNRESOLVED_REFERENCE',strategy:'KOTLIN_MISSING_IMPORT',importFqn:'java.nio.file.Files',
  baselineExecuted:7,primaryExecuted:7,independentExecuted:7,
  attemptedStrategies:['KOTLIN_MISSING_IMPORT'],modelTrainingAuthorized:false,
  automaticToolRegistration:false,mainBranchWriteAuthorized:false};
 return {...body,recipeSha256:sha256(JSON.stringify(body))};
}
function git(root,...args){
 const r=spawnSync('git',args,{cwd:root,encoding:'utf8'});
 assert.equal(r.status,0,r.stderr);return r.stdout.trim();
}
function fixture(fn){
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'ansk-transfer-test-'));
 try{
  const root=path.join(dir,'repo'),home=path.join(dir,'home'),bin=path.join(dir,'bin');
  for(const p of [root,home,bin])fs.mkdirSync(p);
  git(root,'init','-q','-b','astra/continuum-experiment-charter-20261019');
  git(root,'config','user.name','Transfer Test');git(root,'config','user.email','test@example.invalid');
  fs.mkdirSync(path.dirname(path.join(root,file)),{recursive:true});
  fs.writeFileSync(path.join(root,file),source);
  git(root,'add','.');git(root,'commit','-qm','fixture');
  const store=path.join(home,'.local/state/device-ai-continuum/ansk-lab');
  fs.mkdirSync(store,{recursive:true});
  const recipe=receipt();writeImmutableAnskRecipe(store,recipe);
  // Explicit mocked toolchain: this exercises orchestration, NOT Kotlin.
  fs.writeFileSync(path.join(bin,'gradle'),`#!${process.execPath}
const fs=require('node:fs'),path=require('node:path');
const file=${JSON.stringify(file)},root=process.cwd(),args=process.argv.slice(2);
const log=process.env.TRANSFER_FIXTURE_LOG;
let calls=fs.existsSync(log)?JSON.parse(fs.readFileSync(log)): [];
calls.push({root,args});fs.writeFileSync(log,JSON.stringify(calls));
if(!args.includes('--rerun-tasks'))process.exit(8);
if(!fs.readFileSync(path.join(root,file),'utf8').includes('import java.nio.file.Files')){
 console.error('e: file://'+path.join(root,file)+':6:17 Unresolved reference: Files');process.exit(1);
}
if(args.includes(':app:testDebugUnitTest')){
 const units=calls.filter(c=>c.args.includes(':app:testDebugUnitTest')).length;
 const count=process.env.TRANSFER_FIXTURE_DROP==='yes'&&units===3?1:7;
 const dir=path.join(root,'app/build/test-results/testDebugUnitTest');fs.mkdirSync(dir,{recursive:true});
 fs.writeFileSync(path.join(dir,'TEST-fixture.xml'),'<testsuite name="fixture" tests="'+count+'">'+
 Array.from({length:count},(_,i)=>'<testcase classname="fixture" name="case'+i+'"/>').join('')+'</testsuite>');
}
`,{mode:0o700});
  const log=path.join(dir,'calls.json');
  const run=(extra=[],env={})=>spawnSync(process.execPath,[
   fileURLToPath(new URL('./ansk-transfer-lab.mjs',import.meta.url)),
   '--once','--file',file,'--recipe',recipe.recipeSha256,...extra
  ],{cwd:root,encoding:'utf8',timeout:30_000,
   env:{...process.env,HOME:home,PATH:bin+path.delimiter+process.env.PATH,
    TRANSFER_FIXTURE_LOG:log,...env}});
  fn({dir,root,store,recipe,run,log});
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
}

test('paired orchestration uses fresh serial worktrees, fixed reruns and write-once shadow reports (mock toolchain)',()=>{
 fixture(({root,run,log})=>{
  const before=git(root,'rev-parse','HEAD'),r=run();
  assert.equal(r.status,0,r.stderr+'\n'+r.stdout);
  const result=JSON.parse(r.stdout);
  assert.equal(result.status,'TRANSFER_VERIFIED_SHADOW_ONLY');
  assert.equal(result.comparison.sampleSizePerArm,1);
  assert.deepEqual(result.comparison.rows.map(a=>a.observedFailureRate),[0,0]);
  assert.ok(result.comparison.rows.every(a=>a.validationMs>0));
  assert.equal(result.modelTrainingAuthorized,false);
  assert.equal(result.contrastiveTupleAvailable,false);
  assert.equal(result.arms[0].patchSha256,result.arms[1].patchSha256);
  assert.equal(sha256(fs.readFileSync(result.reportPath)),result.reportSha256);
  assert.equal(fs.statSync(result.reportPath).mode&0o077,0);
  const calls=JSON.parse(fs.readFileSync(log));
  assert.equal(new Set(calls.map(c=>c.root)).size,5);
  assert.equal(calls.length,11); // green baseline, red compiler, four green runs
  assert.equal(calls.every(c=>c.args.includes('--rerun-tasks')),true);
  assert.equal(git(root,'rev-parse','HEAD'),before);
  assert.equal(git(root,'status','--porcelain'),'');
  assert.equal(git(root,'worktree','list','--porcelain').match(/^worktree /gm).length,1);
 });
});

test('independent coverage loss prevents verified transfer while still measuring control (mock toolchain)',()=>{
 fixture(({run})=>{
  const r=run([], {TRANSFER_FIXTURE_DROP:'yes'});
  assert.equal(r.status,2,r.stderr);
  const result=JSON.parse(r.stdout);
  assert.equal(result.status,'TRANSFER_COMPARISON_FAILED');
  assert.equal(result.arms[0].reason,'INDEPENDENT_NOT_GREEN');
  assert.equal(result.arms[1].verified,true);
  assert.deepEqual(result.comparison.rows.map(a=>a.observedFailureRate),[1,0]);
 });
});

test('control-first records execution order without consuming the recipe in control (mock toolchain)',()=>{
 fixture(({run})=>{
  const r=run(['--order','control-first']);assert.equal(r.status,0,r.stderr);
  assert.deepEqual(JSON.parse(r.stdout).arms.map(a=>a.name),['control','recipe']);
 });
});

test('missing/corrupt receipts and dirty source fail before any compiler invocation',()=>{
 fixture(({root,store,recipe,run,log})=>{
  fs.appendFileSync(path.join(root,file),'// owner edit\n');
  assert.match(run().stderr,/ANSK_SOURCE_CHECKOUT_NOT_CLEAN/);
  git(root,'checkout','--',file);
  fs.writeFileSync(path.join(store,'shadow-recipes',recipe.recipeSha256+'.json'),'{}');
  assert.match(run().stderr,/ANSK_INVALID_RECIPE_RECEIPT/);
  assert.equal(fs.existsSync(log),false);
 });
});

test('reader rejects symlinked, world-readable, oversized and misaddressed receipts',()=>{
 fixture(({store,recipe})=>{
  const target=path.join(store,'shadow-recipes',recipe.recipeSha256+'.json');
  assert.deepEqual(readImmutableAnskRecipe(store,recipe.recipeSha256),recipe);
  fs.chmodSync(target,0o644);
  assert.throws(()=>readImmutableAnskRecipe(store,recipe.recipeSha256),/UNSAFE_RECIPE_FILE/);
  fs.chmodSync(target,0o600);fs.writeFileSync(target,'x'.repeat(16_385));
  assert.throws(()=>readImmutableAnskRecipe(store,recipe.recipeSha256),/UNSAFE_RECIPE_FILE/);
  fs.unlinkSync(target);fs.symlinkSync('/dev/null',target);
  assert.throws(()=>readImmutableAnskRecipe(store,recipe.recipeSha256));
  assert.throws(()=>readImmutableAnskRecipe(store,'../bad'),/INVALID_RECIPE_ADDRESS/);
 });
});

test('CLI requires explicit one-shot parameters, never an arbitrary command',()=>{
 for(const args of [[],['--loop'],['--once'],['--once','--file','a','--file','b'],
  ['--once','--command','echo']])assert.throws(()=>parseAnskTransferArgs(args));
 assert.throws(()=>runAnskTransferLab({sourceFile:'../a.kt',recipeSha256:'a'.repeat(64)}),
  /INVALID_KOTLIN_IMPORT_TARGET/);
 assert.throws(()=>runAnskTransferLab({sourceFile:file,recipeSha256:'a'.repeat(64),order:'random'}),
  /INVALID_TRANSFER_ORDER/);
});

test('transfer gate rejects missing independent runs and unchanged mutation hashes',()=>{
 assert.equal(verifyAnskTransferArm().verified,false);
 const result=verifyAnskTransferArm({baselineSha256:'a'.repeat(64),
  mutatedSha256:'b'.repeat(64),primarySha256:'b'.repeat(64),independentSha256:'b'.repeat(64)});
 assert.equal(result.reason,'TRANSFER_SOURCE_HASH_MISMATCH');
});
