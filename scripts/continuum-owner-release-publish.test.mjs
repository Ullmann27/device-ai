import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const script=fileURLToPath(new URL('./continuum-owner-release-publish.mjs',import.meta.url));
const source=readFileSync(script,'utf8');
const run=(args=[],override={})=>spawnSync(process.execPath,[script,...args],{
  encoding:'utf8',timeout:10000,
  env:{...process.env,PREFIX:'/not/termux',...override}
});

test('publishing fails closed for missing arguments or invalid subcommands',()=>{
  for(const args of [
    [],['/tmp/stage'],['/tmp/stage','a'.repeat(40)],
    ['/tmp/stage','a'.repeat(40),'--force'],
    ['/tmp/stage','a'.repeat(40),'--publish','extra']
  ]) {
    const result=run(args);
    assert.equal(result.status,69,args.join(' '));
    assert.match(result.stderr,/CONTINUUM_PUBLISH_BLOCKED/);
  }
});

test('no publication or signer access on an untrusted phone environment',()=>{
  const result=run(['/tmp/does-not-exist','a'.repeat(40),'--publish'],
    {CONTINUUM_OWNER_RELEASE_PUBLISH_APPROVED_SHA:'a'.repeat(40)});
  assert.equal(result.status,69);
  assert.match(result.stderr,/FOLD7_TERMUX_REQUIRED/);
});

test('invalid exact source SHA rejected before any network or filesystem changes',()=>{
  const result=run(['/tmp/does-not-exist','not-a-hash','--check']);
  assert.equal(result.status,69);
  assert.match(result.stderr,/INVALID_SOURCE_SHA/);
});

test('only the existing original-signer static release channel may be updated',()=>{
  assert.match(source,/release\/continuum-owner-v23-candidate-20261025/);
  assert.match(source,/owner-v23-release-preflight\/continuum-local/);
  assert.match(source,/continuum-owner-v23-release-preflight\.onrender\.com/);
  assert.match(source,/6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23/);
  assert.match(source,/refs\/heads\/.*TARGET_BRANCH/);
  assert.match(source,/git\(checkout,'push','origin','HEAD:refs\/heads\/'\+TARGET_BRANCH\)/);
  assert.doesNotMatch(source,/--force(?:-with-lease)?/);
});

test('pub requires reviewed grant and re-verifies original APK before writing a branch',()=>{
  assert.match(source,/CONTINUUM_OWNER_RELEASE_PUBLISH_APPROVED_SHA/);
  assert.match(source,/process\.env\.CONTINUUM_OWNER_RELEASE_PUBLISH_APPROVED_SHA!==sha/);
  assert.match(source,/continuum-owner-update-manifest\.mjs/);
  assert.match(source,/SIGNER_PACKAGE_OR_RELEASE_RECHECK_FAILED/);
  assert.match(source,/STAGING_RECEIPT_MISMATCH/);
  assert.match(source,/PUBLISH_COPY_HASH_MISMATCH/);
  assert.match(source,/UNTRUSTED_OR_NOT_NEWER_RELEASE_CHANNEL/);
  assert.match(source,/OWNER_RELEASE_PUSHED_AWAITING_RENDER_DEPLOY/);
  assert.ok(source.indexOf("verifyStaged(staging,sha,tmp)")<
    source.indexOf("git(ROOT,'fetch'"),'verify original signed APK before remote fetch');
});

test('never auto-sign, install, export key, or merge a production branch',()=>{
  assert.doesNotMatch(source,/\b(?:pm|adb)\s+(?:install|uninstall|clear)\b/);
  assert.doesNotMatch(source,/\bkeytool\b|\bstorepass\b|CONTINUUM_LOCAL_SIGNING_PASSWORD/);
  assert.doesNotMatch(source,/git\(.*'merge'|'push','origin','HEAD:refs\/heads\/main'/);
});
