import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import {spawnSync} from 'node:child_process';
import {tmpdir} from 'node:os';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const source=fileURLToPath(new URL('./continuum-atomic-acceptance.sh',import.meta.url));
function execute(command,args,cwd,environment={}){
  return spawnSync(command,args,{cwd,encoding:'utf8',timeout:15000,
    env:{...process.env,...environment}});
}
function ensureSuccess(r,what){
  assert.equal(r.status,0,what+': '+r.stderr+' '+r.stdout);
  return r.stdout.trim();
}
async function fixture(t){
  const dir=await fs.mkdtemp(path.join(tmpdir(),'continuum-atomic-fixture-'));
  t.after(async()=>{await fs.rm(dir,{recursive:true,force:true});});
  const repo=path.join(dir,'repo');
  for(const sub of ['scripts','.github/continuum','app/build/test-results/testDebugUnitTest']){
    await fs.mkdir(path.join(repo,sub),{recursive:true});
  }
  await fs.copyFile(source,path.join(repo,'scripts/continuum-atomic-acceptance.sh'));
  await fs.writeFile(path.join(repo,'.gitignore'),'app/build/\n');
  // All following executables are intentionally synthetic; no Android SDK,
  // network, GitHub Actions, real owner keystore or Gradle are involved.
  await fs.writeFile(path.join(repo,'gradlew'),
    '#!/bin/sh\nif [ "$CONTINUUM_TEST_FAIL_GRADLE" = "yes" ]; then exit 1; fi\n'+
    'if [ "$CONTINUUM_TEST_FIXTURE_EMIT_JUNIT" = "yes" ]; then\n'+
    'mkdir -p app/build/test-results/testDebugUnitTest\n'+
    'for suite in at.heinz.deviceai.ToolExecutionStartLatchTest at.heinz.deviceai.ReadOnlyToolSizeRegressionTest at.heinz.deviceai.cognitive.LocalPromptFrameTest; do\n'+
    'if [ "$CONTINUUM_TEST_FIXTURE_OMIT_SUITE" = "yes" ] && [ "$suite" = "at.heinz.deviceai.cognitive.LocalPromptFrameTest" ]; then continue; fi\n'+
    'printf \'%s\\n\' \'<testsuite tests="1" failures="0"><testcase name="synthetic"/></testsuite>\' > "app/build/test-results/testDebugUnitTest/TEST-$suite.xml"\n'+
    'done\n'+
    'fi\nexit 0\n');
  await fs.writeFile(path.join(repo,'.github/continuum/repair-diagnostics.mjs'),[
    'import fs from "node:fs"; import path from "node:path";',
    'export function readJUnitDirectory(dir){',
    'const total=fs.existsSync(dir)?fs.readdirSync(dir).filter(x=>x.startsWith("TEST-")&&x.endsWith(".xml")).length:0;',
    'return {total,executed:total,passed:total,failed:0,',
    'skipped:0,placeholders:0,evidenceComplete:true};',
    '}'
  ].join('\n')+'\n');
  await fs.writeFile(path.join(repo,'scripts/continuum-reliability-qa.mjs'),[
    'import fs from "node:fs"; import path from "node:path";',
    'const out=process.argv.at(-1); fs.mkdirSync(out,{recursive:true});',
    'fs.writeFileSync(path.join(out,"report.json"),JSON.stringify({',
    'status:process.env.CONTINUUM_TEST_NODE_BAD==="yes"?"FAIL":"PASS",',
    'sourceUnchanged:true,source:{commit:process.env.CONTINUUM_TEST_NODE_WRONG_SHA==="yes"?"0".repeat(40):process.env.RECEIPT_EXPECTED_HEAD}}));'
  ].join('\n')+'\n');
  ensureSuccess(execute('git',['init','-q','-b','feature/qa-fixture'],repo),'init fixture');
  ensureSuccess(execute('git',['add','.'],repo),'add fixture');
  ensureSuccess(execute('git',['-c','user.email=fixture@example.invalid',
    '-c','user.name=Continuum QA Fixture','commit','-qm','synthetic fixture'],repo),'commit fixture');
  const sha=ensureSuccess(execute('git',['rev-parse','HEAD'],repo),'fixture sha');
  return {dir,repo,sha};
}
async function invoke(f,name,sha=f.sha,environment={}){
  const out=path.join(f.dir,'out-'+name);
  const result=execute('bash',[path.join(f.repo,'scripts/continuum-atomic-acceptance.sh'),
    '--expected-head',sha,'--out',out],f.repo,environment);
  let receipt=null;
  try{receipt=JSON.parse(await fs.readFile(path.join(out,'acceptance.json'),'utf8'));}
  catch(error){if(error.code!=='ENOENT')throw error;}
  return {result,receipt};
}

test('exact-SHA local QA wrapper refuses false evidence and protects source',async t=>{
  const f=await fixture(t);
  let r=await invoke(f,'wrong-head','0'.repeat(40));
  assert.equal(r.result.status,4);
  assert.match(r.result.stderr,/HEAD_MISMATCH/);
  assert.equal(r.receipt,null);

  ensureSuccess(execute('git',['branch','-m','main'],f.repo),'rename to protected');
  r=await invoke(f,'protected');
  assert.equal(r.result.status,4);
  assert.match(r.result.stderr,/PROTECTED_BRANCH_REFUSED/);
  ensureSuccess(execute('git',['branch','-m','feature/qa-fixture'],f.repo),'restore branch');

  await fs.writeFile(path.join(f.repo,'dirty.txt'),'dirty');
  r=await invoke(f,'dirty');
  assert.equal(r.result.status,4);
  assert.match(r.result.stderr,/DIRTY_SOURCE_REFUSED/);
  await fs.rm(path.join(f.repo,'dirty.txt'));

  r=await invoke(f,'gradle-error',f.sha,{CONTINUUM_TEST_FAIL_GRADLE:'yes'});
  assert.equal(r.result.status,1);
  assert.equal(r.receipt?.status,'FAIL');
  assert.equal(r.receipt?.checks[0].status,'FAIL');
  assert.equal(r.receipt?.checks[1].status,'NOT_RUN');

  r=await invoke(f,'empty-junit');
  assert.equal(r.result.status,1);
  assert.equal(r.receipt?.status,'FAIL');
  assert.equal(r.receipt?.checks[1].status,'FAIL');
  assert.equal(r.receipt?.checks[2].status,'NOT_RUN');

  const xml=path.join(f.repo,'app/build/test-results/testDebugUnitTest/TEST-synthetic.xml');
  await fs.writeFile(xml,'<testsuite tests="1" failures="0"><testcase name="synthetic"/></testsuite>');
  await fs.utimes(xml,new Date(0),new Date(0));
  r=await invoke(f,'stale-junit');
  assert.equal(r.result.status,1);
  assert.equal(r.receipt?.status,'FAIL');
  assert.equal(r.receipt?.checks[1].status,'FAIL');
  await fs.rm(xml); // Isolate the next case: fresh output but a missing mandatory suite.

  const newJunit={CONTINUUM_TEST_FIXTURE_EMIT_JUNIT:'yes'};
  r=await invoke(f,'missing-critical-suite',f.sha,{
    ...newJunit,CONTINUUM_TEST_FIXTURE_OMIT_SUITE:'yes'
  });
  assert.equal(r.result.status,1);
  assert.equal(r.receipt?.status,'FAIL');
  assert.equal(r.receipt?.checks[1].status,'FAIL');

  r=await invoke(f,'node-report-bad',f.sha,{...newJunit,CONTINUUM_TEST_NODE_BAD:'yes'});
  assert.equal(r.result.status,1);
  assert.equal(r.receipt?.status,'FAIL');
  assert.equal(r.receipt?.checks[2].status,'PASS');
  assert.equal(r.receipt?.checks[3].status,'FAIL');

  r=await invoke(f,'node-mismatched-source',f.sha,{...newJunit,CONTINUUM_TEST_NODE_WRONG_SHA:'yes'});
  assert.equal(r.result.status,1);
  assert.equal(r.receipt?.status,'FAIL');
  assert.equal(r.receipt?.checks[3].status,'FAIL');

  r=await invoke(f,'valid-synthetic',f.sha,newJunit);
  assert.equal(r.result.status,0,r.result.stderr);
  assert.equal(r.receipt?.status,'PASS');
  assert.equal(r.receipt?.sourceHead,f.sha);
  assert.equal(r.receipt?.checks.length,6);
  assert.ok(r.receipt.checks.every(x=>x.status==='PASS'));
  assert.equal(r.receipt.deviceVerified,false);
  assert.equal(r.receipt.apkProduced,false);
  assert.equal(r.receipt.signingPerformed,false);

  // Simulate a disk/receipt-writer failure after all test commands succeeded.
  // No durable acceptance receipt means the wrapper must return non-zero.
  const bin=path.join(f.dir,'fixture-bin');
  await fs.mkdir(bin);
  const nodeWrapper=path.join(bin,'node');
  await fs.writeFile(nodeWrapper,
    '#!/bin/sh\nif [ -n "$RECEIPT_OUTPUT" ]; then exit 9; fi\nexec "'+
      process.execPath+'" "$@"\n',{mode:0o700});
  r=await invoke(f,'receipt-write-failure',f.sha,{
    ...newJunit,PATH:bin+path.delimiter+process.env.PATH
  });
  assert.equal(r.result.status,1);
  assert.match(r.result.stderr,/ACCEPTANCE_RECEIPT_WRITE_FAILED/);
  assert.equal(r.receipt,null);
});
