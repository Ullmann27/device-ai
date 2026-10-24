import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const newBuild = path.join(repo, 'scripts/render-mentor-owner-update-v18.sh');
const existingBuild = path.join(repo, 'scripts/render-mentor-owner-update-v17.sh');
const gradle = fs.readFileSync(path.join(repo,'app/build.gradle.kts'),'utf8');
const script = fs.readFileSync(newBuild,'utf8');

test('new same-package release script parses in bash', () => {
  const r=spawnSync('bash',['-n',newBuild],{encoding:'utf8',timeout:10000});
  assert.equal(r.status,0,r.stderr);
});
test('v18 release fails before any build without explicit owner approval', () => {
  const env={PATH:process.env.PATH,HOME:process.env.HOME};
  const r=spawnSync('bash',[newBuild],{cwd:repo,env,encoding:'utf8',timeout:10000});
  assert.notEqual(r.status,0);
  assert.match(r.stderr,/CONTINUUM_RELEASE_SHA|unbound variable/);
});
test('historical v18 script remains intact and current owner build advances to code22', () => {
  assert.match(gradle,/if \(deviceAiOwnerSignedUpdate\) \{\s*versionCode = 22\s*versionName = "0\.3\.21"/);
  assert.match(script,/versionCode='18' versionName='0\.3\.17'/);
  assert.match(script,/at\.heinz\.deviceai\.mentorpaired/);
  assert.match(script,/expected_signer="2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc"/);
});
test('Astra source and exact commit gates precede signing operations', () => {
  const signature=script.indexOf('base64 --decode');
  assert.ok(signature>0);
  for(const term of ['CONTINUUM_RELEASE_SHA','CONTINUUM_ASTRA_UI_SHA','merge-base --is-ancestor','core.fileMode=false diff --quiet','ContinuumCcc1Bridge.kt','ContinuumHologramRenderer.kt'])
    assert.ok(script.indexOf(term)>=0 && script.indexOf(term)<signature,term);
  assert.match(script, /APPROVED_RELEASE_SOURCES/);
  assert.match(script, /git hash-object --/);
});
test('existing signed owner 0.3.16 remains the immutable rollback, not the new output', () => {
  const old=fs.readFileSync(existingBuild,'utf8');
  assert.match(old,/versionCode='17' versionName='0\.3\.16'/);
  assert.match(script,/prior_code" == "17"/);
  assert.match(script,/v0\.3\.16-\$prior_sha\.apk/);
  assert.match(script,/sha256sum --check --status/);
  assert.match(script,/SIGNED_BUILD_VERIFIED_NOT_DEVICE_INSTALLED/);
  assert.match(script,/v0\.3\.17-\$apk_sha256\.apk/);
  assert.doesNotMatch(script,/version_code=17\\n/);
});