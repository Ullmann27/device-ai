import test from 'node:test';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';

const command=fileURLToPath(new URL('./continuum-owner-release-stage.mjs',import.meta.url));
const run=(...args)=>spawnSync(process.execPath,[command,...args],
  {encoding:'utf8',timeout:12000,env:{...process.env,PREFIX:'/data/data/not-termux'}});
const source=readFileSync(command,'utf8');

test('missing arguments never stage or publish a release',()=>{
  const result=run();
  assert.equal(result.status,69);
  assert.match(result.stderr,/Usage/);
});
test('invalid commit identity is rejected before any credential use',()=>{
  const result=run('/tmp/never-was-an-apk.apk','not-a-commit');
  assert.equal(result.status,69);
  assert.match(result.stderr,/INVALID_SOURCE_SHA/);
});
test('only the original owner Termux execution context may stage a signed APK',()=>{
  const result=run('/tmp/never-was-an-apk.apk','a'.repeat(40));
  assert.equal(result.status,69);
  assert.match(result.stderr,/FOLD7_TERMUX_REQUIRED/);
});
test('staging never signs, installs, exports keys or publishes a binary',()=>{
  assert.doesNotMatch(source,/\bpm\s+install\b|\badb\s+install\b|\bkeytool\s+-genkeypair\b/);
  assert.doesNotMatch(source,/fetch\(|https\.request\(|http\.request\(/);
  assert.match(source,/OWNER_RELEASE/);
  assert.match(source,/continuum-owner-update-manifest\.mjs/);
  assert.match(source,/LOCAL_BUILD_RECEIPT_MISMATCH/);
  assert.match(source,/STAGED_NOT_PUBLISHED/);
  assert.match(source,/CONTINUUM_SIGNED_RELEASE_STAGED_NOT_PUBLISHED/);
});
