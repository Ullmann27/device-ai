import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {spawnSync} from 'node:child_process';
import {resolve,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';

const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const filename='scripts/render-continuum-owner-v38-release.sh';
const source=readFileSync(resolve(root,filename),'utf8');
const run=(environment={})=>{
  const env={...process.env,...environment};
  delete env.CONTINUUM_RELEASE_FREEZE_LIFTED_BY_OWNER;
  return spawnSync('bash',[filename],{cwd:root,env,encoding:'utf8',timeout:15000});
};

test('v38 owner release recipe has valid shell syntax and original package identity',()=>{
  const syntax=spawnSync('bash',['-n',filename],{cwd:root,encoding:'utf8',timeout:15000});
  assert.equal(syntax.status,0,syntax.stderr);
  assert.match(source,/expected_package='at\.heinz\.deviceai\.continuumlocal'/);
  assert.match(source,/6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23/);
  assert.match(source,/versionCode: 38/);
  assert.match(source,/name !== '0\.3\.37'/);
  assert.match(source,/continuum-owner-v23-release-preflight\.onrender\.com/);
});
test('P0 freeze denies even an exact SHA without owner-specific freeze lift',()=>{
  const sha=spawnSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'});
  assert.equal(sha.status,0,sha.stderr);
  const p=run({CONTINUUM_RELEASE_APPROVED_SHA:sha.stdout.trim()});
  assert.equal(p.status,69,p.stderr);
  assert.match(p.stderr,/P0 release freeze active/);
  assert.doesNotMatch(p.stdout,/STAGED_FOR_PINNED_HOST/);
});
test('staged owner candidate is explicitly unapproved and device-unverified',()=>{
  assert.match(source,/approved:\s*false/);
  assert.match(source,/status:\s*'SIGNED_STAGED_DEVICE_NOT_RUN'/);
  assert.doesNotMatch(source,/approved:\s*true/);
  assert.match(source,/CONTINUUM_RELEASE_APPROVED_SHA/);
  assert.match(source,/CONTINUUM_RELEASE_FREEZE_LIFTED_BY_OWNER/);
  assert.match(source,/CONTINUUM_LOCAL_SIGNING_P12_BASE64/);
  assert.match(source,/key_fingerprint.*expected_signer/);
  assert.match(source,/apk_signer.*expected_signer/);
});
