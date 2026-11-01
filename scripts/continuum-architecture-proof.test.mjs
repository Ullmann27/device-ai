import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, rmSync, unlinkSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import {spawnSync} from 'node:child_process';
import { dirname, join } from 'node:path';
import { collectArchitectureProof, REQUIRED_JUNIT, REQUIRED_LOGS, parseNodeTestSummary } from './continuum-architecture-proof.mjs';

const SHA = 'b'.repeat(40);
const VERSION = 36;
const RUN_ID = '12345678-1234-4234-9234-123456789abc';
const TAP = ['TAP version 13','ok 1 - first','ok 2 - second','1..2','# tests 2',
  '# pass 2','# fail 0','# cancelled 0','# skipped 0','# todo 0',''].join('\n');
function fixture(t) {
  const root = mkdtempSync(join(tmpdir(), 'continuum-architecture-proof-'));
  t.after(() => rmSync(root, {recursive:true, force:true}));
  const put = (path, content) => {
    const absolute = join(root, path);
    mkdirSync(dirname(absolute), {recursive:true});
    writeFileSync(absolute, content);
    return absolute;
  };
  put('continuum-m8-render-qa/STATUS.txt', [
    'status=BUILD_AND_TEST_GREEN_UNSIGNED_NOT_INSTALLABLE',
    'source_commit=' + SHA,
    'run_id=' + RUN_ID,
    'package=at.heinz.deviceai.continuumlocal',
    'version_code=' + VERSION,
    'apk_signed=FALSE', 'apk_published=FALSE', ''
  ].join('\n'));
  for (const log of REQUIRED_LOGS)
    put('continuum-m8-render-qa/' + log,
      log.startsWith('GRADLE-') || log === 'ASSEMBLY.txt'
        ? 'BUILD SUCCESSFUL in 4m 42s\n' : TAP);
  put('continuum-m8-render-qa/APK-BADGING.txt',
    "package: name='at.heinz.deviceai.continuumlocal' versionCode='36' versionName='0.3.35'\n");
  for (const klass of REQUIRED_JUNIT) {
    put('app/build/test-results/testDebugUnitTest/TEST-' + klass + '.xml',
      '<testsuite name="' + klass + '" tests="3" skipped="0" failures="0" errors="0">' +
      Array.from({length:3},(_,i)=>'<testcase classname="'+klass+'" name="case'+i+'"/>').join('') + '</testsuite>');
  }
  put('app/build/outputs/apk/release/app-release-unsigned.apk', Buffer.alloc(1_000_001, 0x31));
  put('app/build/outputs/bundle/release/app-release.aab', Buffer.alloc(1_000_002, 0x32));
  return {root, put};
}
test('signed-release claims remain explicitly unverified after successful unsigned QA', t => {
  const f = fixture(t);
  const proof = collectArchitectureProof({root:f.root,sha:SHA,versionCode:VERSION,runId:RUN_ID});
  assert.equal(proof.sourceCommit,SHA);
  assert.deepEqual(proof.qa.unsignedApkIdentity, {packageName:'at.heinz.deviceai.continuumlocal',versionCode:36,versionName:'0.3.35'});
  assert.equal(proof.qa.unsignedOwnerApkAndAab,'ASSEMBLED_NOT_INSTALLABLE');
  assert.equal(proof.qa.evidenceBinding,'AUTOMATED_JUNIT_PASS_NOT_FACTUAL_TRUTH');
  assert.ok(proof.notYetProven.includes('ORIGINAL_OWNER_SIGNED_AND_PUBLISHED_APK'));
  assert.ok(proof.notYetProven.includes('SUPERINTELLIGENCE_OR_MODEL_PARITY'));
  assert.equal(Object.keys(proof.qa.junitSuites).length,REQUIRED_JUNIT.length);
  assert.match(proof.qa.unsignedApk.sha256,/^[a-f0-9]{64}$/);
});
test('wrong commit, version or release-claim metadata cannot pass', t => {
  const f=fixture(t);
  assert.throws(()=>collectArchitectureProof({root:f.root,sha:'a'.repeat(40),versionCode:36,runId:RUN_ID}),/STATUS mismatch/);
  assert.throws(()=>collectArchitectureProof({root:f.root,sha:SHA,versionCode:35,runId:RUN_ID}),/Invalid pinned/);
  f.put('continuum-m8-render-qa/STATUS.txt','status=SIGNED_STAGED_FOR_PINNED_HOST\n');
  assert.throws(()=>collectArchitectureProof({root:f.root,sha:SHA,versionCode:36,runId:RUN_ID}),/STATUS mismatch/);
});
test('missing test transcript and failed JUnit block the architectural receipt', t => {
  const f=fixture(t);
  unlinkSync(join(f.root,'continuum-m8-render-qa/TERMUX-RECOVERY-TESTS.txt'));
  assert.throws(()=>collectArchitectureProof({root:f.root,sha:SHA,versionCode:36,runId:RUN_ID}));
  f.put('continuum-m8-render-qa/TERMUX-RECOVERY-TESTS.txt',TAP);
  const klass = REQUIRED_JUNIT[0];
  f.put('app/build/test-results/testDebugUnitTest/TEST-'+klass+'.xml',
    '<testsuite name="'+klass+'" tests="3" skipped="0" failures="1" errors="0"/>');
  assert.throws(()=>collectArchitectureProof({root:f.root,sha:SHA,versionCode:36,runId:RUN_ID}),/Structured JUnit rejected/);
});
test('a failed Gradle transcript never becomes a proof',t=>{
  const f=fixture(t);
  f.put('continuum-m8-render-qa/ASSEMBLY.txt','BUILD FAILED\n');
  assert.throws(()=>collectArchitectureProof({root:f.root,sha:SHA,versionCode:36,runId:RUN_ID}),/Missing successful Gradle/);
});


test('rejects skipped required JUnit suite and spoofed test metadata',t=>{
  const f=fixture(t);
  const klass=REQUIRED_JUNIT[0];
  const file='app/build/test-results/testDebugUnitTest/TEST-'+klass+'.xml';
  const cases=Array.from({length:3},(_,i)=>'<testcase classname="'+klass+
    '" name="case'+i+'"><skipped/></testcase>').join('');
  f.put(file,'<testsuite name="'+klass+'" tests="3" skipped="3" failures="0" errors="0">'+cases+'</testsuite>');
  assert.throws(()=>collectArchitectureProof({root:f.root,sha:SHA,versionCode:VERSION,runId:RUN_ID}),/Structured JUnit rejected/);
});
test('rejects fullname and forged error attributes',t=>{
  const f=fixture(t), klass=REQUIRED_JUNIT[0];
  const file='app/build/test-results/testDebugUnitTest/TEST-'+klass+'.xml';
  const cases=Array.from({length:3},(_,i)=>'<testcase classname="'+klass+'" name="case'+i+'"/>').join('');
  f.put(file,'<testsuite fullname="'+klass+'" tests="3" skipped="0" failures="0" errors="0">'+cases+'</testsuite>');
  assert.throws(()=>collectArchitectureProof({root:f.root,sha:SHA,versionCode:VERSION,runId:RUN_ID}),/Structured JUnit rejected/);
  f.put(file,'<testsuite name="'+klass+'" tests="3" skipped="0" forged-failures="0" failures="1" errors="0">'+cases+'</testsuite>');
  assert.throws(()=>collectArchitectureProof({root:f.root,sha:SHA,versionCode:VERSION,runId:RUN_ID}),/Structured JUnit rejected/);
  f.put(file,'<testsuite name="'+klass+'" tests="3" skipped="0" forged-errors="0" failures="0" errors="1">'+cases+'</testsuite>');
  assert.throws(()=>collectArchitectureProof({root:f.root,sha:SHA,versionCode:VERSION,runId:RUN_ID}),/Structured JUnit rejected/);
});
test('rejects red or skipped Node TAP even with present reports',t=>{
  const f=fixture(t);
  f.put('continuum-m8-render-qa/NODE-TESTS.txt',TAP.replace('# fail 0','# fail 1'));
  assert.throws(()=>collectArchitectureProof({root:f.root,sha:SHA,versionCode:VERSION,runId:RUN_ID}),/Node TAP failed/);
  f.put('continuum-m8-render-qa/NODE-TESTS.txt',TAP.replace('# skipped 0','# skipped 1'));
  assert.throws(()=>collectArchitectureProof({root:f.root,sha:SHA,versionCode:VERSION,runId:RUN_ID}),/Node TAP failed/);
});


test('actual backend QA command emits machine-readable TAP on modern Node', t => {
  const root = mkdtempSync(join(tmpdir(), 'continuum-backend-reporter-'));
  t.after(() => rmSync(root, {recursive:true, force:true}));
  mkdirSync(join(root, 'test'));
  writeFileSync(join(root, 'package.json'), JSON.stringify({scripts:{test:'node --test test/*.test.js'}}));
  writeFileSync(join(root, 'test', 'minimal.test.js'), "require('node:test')('fixture',()=>{});\n");
  const script = readFileSync(new URL('./render-continuum-m8-qa.sh', import.meta.url), 'utf8');
  const command = /npm run check\r?\n\s+([^\r\n]+)/.exec(script)?.[1];
  assert.ok(command, 'backend test invocation exists');
  const env = {...process.env};
  delete env.NODE_TEST_CONTEXT;
  const run = spawnSync('bash', ['-c',command], {cwd:root,env,encoding:'utf8',timeout:10000});
  assert.equal(run.status,0,run.stderr);
  assert.deepEqual(parseNodeTestSummary(run.stdout),{tests:1,pass:1,fail:0,cancelled:0,skipped:0,todo:0});
});


test('actual APK identity must match source receipt instead of trusting STATUS metadata', t => {
  const f = fixture(t);
  for (const badging of [
    "package: name='at.heinz.deviceai.other' versionCode='36' versionName='0.3.35'\n",
    "package: name='at.heinz.deviceai.continuumlocal' versionCode='35' versionName='0.3.35'\n",
    "package: name='at.heinz.deviceai.continuumlocal' versionCode='36' versionName='0.3.34'\n",
    "not a package report\n",
    "package: forged-name='at.heinz.deviceai.continuumlocal' forged-versionCode='36' forged-versionName='0.3.35'\n",
    "package: name='at.heinz.deviceai.continuumlocal' name='other' versionCode='36' versionName='0.3.35'\n"
  ]) {
    f.put('continuum-m8-render-qa/APK-BADGING.txt',badging);
    assert.throws(() => collectArchitectureProof({root:f.root,sha:SHA,versionCode:VERSION,runId:RUN_ID}), /APK identity/);
  }
});
