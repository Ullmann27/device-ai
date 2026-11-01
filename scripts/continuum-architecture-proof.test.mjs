import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, rmSync, unlinkSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { collectArchitectureProof, REQUIRED_JUNIT, REQUIRED_LOGS } from './continuum-architecture-proof.mjs';

const SHA = 'b'.repeat(40);
const VERSION = 36;
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
    'package=at.heinz.deviceai.continuumlocal',
    'version_code=' + VERSION,
    'apk_signed=FALSE', 'apk_published=FALSE', ''
  ].join('\n'));
  for (const log of REQUIRED_LOGS)
    put('continuum-m8-render-qa/' + log,
      log.startsWith('GRADLE-') || log === 'ASSEMBLY.txt'
        ? 'BUILD SUCCESSFUL in 4m 42s\n'
        : 'Test runner exited successfully\n');
  for (const klass of REQUIRED_JUNIT) {
    put('app/build/test-results/testDebugUnitTest/TEST-' + klass + '.xml',
      '<testsuite name="' + klass + '" tests="3" skipped="0" failures="0" errors="0"></testsuite>');
  }
  put('app/build/outputs/apk/release/app-release-unsigned.apk', Buffer.alloc(1_000_001, 0x31));
  put('app/build/outputs/bundle/release/app-release.aab', Buffer.alloc(1_000_002, 0x32));
  return {root, put};
}
test('signed-release claims remain explicitly unverified after successful unsigned QA', t => {
  const f = fixture(t);
  const proof = collectArchitectureProof({root:f.root,sha:SHA,versionCode:VERSION});
  assert.equal(proof.sourceCommit,SHA);
  assert.equal(proof.qa.unsignedOwnerApkAndAab,'ASSEMBLED_NOT_INSTALLABLE');
  assert.equal(proof.qa.evidenceBinding,'AUTOMATED_JUNIT_PASS_NOT_FACTUAL_TRUTH');
  assert.ok(proof.notYetProven.includes('ORIGINAL_OWNER_SIGNED_AND_PUBLISHED_APK'));
  assert.ok(proof.notYetProven.includes('SUPERINTELLIGENCE_OR_MODEL_PARITY'));
  assert.equal(Object.keys(proof.qa.junitSuites).length,4);
  assert.match(proof.qa.unsignedApk.sha256,/^[a-f0-9]{64}$/);
});
test('wrong commit, version or release-claim metadata cannot pass', t => {
  const f=fixture(t);
  assert.throws(()=>collectArchitectureProof({root:f.root,sha:'a'.repeat(40),versionCode:36}),/STATUS mismatch/);
  assert.throws(()=>collectArchitectureProof({root:f.root,sha:SHA,versionCode:35}),/Invalid pinned/);
  f.put('continuum-m8-render-qa/STATUS.txt','status=SIGNED_STAGED_FOR_PINNED_HOST\n');
  assert.throws(()=>collectArchitectureProof({root:f.root,sha:SHA,versionCode:36}),/STATUS mismatch/);
});
test('missing test transcript and failed JUnit block the architectural receipt', t => {
  const f=fixture(t);
  unlinkSync(join(f.root,'continuum-m8-render-qa/TERMUX-RECOVERY-TESTS.txt'));
  assert.throws(()=>collectArchitectureProof({root:f.root,sha:SHA,versionCode:36}));
  f.put('continuum-m8-render-qa/TERMUX-RECOVERY-TESTS.txt','pass\n');
  const klass = REQUIRED_JUNIT[0];
  f.put('app/build/test-results/testDebugUnitTest/TEST-'+klass+'.xml',
    '<testsuite name="'+klass+'" tests="3" failures="1" errors="0"/>');
  assert.throws(()=>collectArchitectureProof({root:f.root,sha:SHA,versionCode:36}),/not fully passing/);
});
test('a failed Gradle transcript never becomes a proof',t=>{
  const f=fixture(t);
  f.put('continuum-m8-render-qa/ASSEMBLY.txt','BUILD FAILED\n');
  assert.throws(()=>collectArchitectureProof({root:f.root,sha:SHA,versionCode:36}),/Missing successful Gradle/);
});
