import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {spawnSync,execFileSync} from 'node:child_process';
import {dirname,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const read=path=>readFileSync(resolve(root,path),'utf8');
const head=()=>execFileSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'}).trim();
const approvedSource='1ea194134ee540e99e2745dda439e440d245a2ac';
const inventory='2d617e27eec2923437ed1e8a1f1cf73eac4aa7b9';
function gate(vars={}){
 return spawnSync(process.execPath,['scripts/continuum-v24-source-gate.mjs'],{
  cwd:root,encoding:'utf8',timeout:120000,
  env:{PATH:process.env.PATH,HOME:process.env.HOME,...vars}
 });
}
function dispatch(vars={}){
 return spawnSync('bash',['scripts/render-mentor-preview.sh'],{
  cwd:root,encoding:'utf8',timeout:15000,
  env:{PATH:process.env.PATH,HOME:process.env.HOME,
   CONTINUUM_OWNER_RELEASE_VERSION:'24',...vars}
 });
}

test('v24 remains one in-place owner package with exact older certificate and rollback',()=>{
 const gradle=read('app/build.gradle.kts');
 const router=read('scripts/render-mentor-preview.sh');
 const shell=read('scripts/render-mentor-owner-update-v24.sh');
 assert.match(gradle,/if \(deviceAiOwnerSignedUpdate\) \{[\s\S]*?versionCode = 25\s*versionName = "0\.3\.24"/);
 assert.match(gradle,/applicationIdSuffix = "\.mentorpaired"/);
 assert.match(router,/CONTINUUM_OWNER_V24_APPROVED_SHA/);
 assert.match(router,/CONTINUUM_OWNER_RELEASE_VERSION:-18/);
 for(const number of [18,19,20,21,22,24])
  assert.match(router,new RegExp('^  '+number+'\\)','m'));
 assert.match(shell,/expected_signer="2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc"/);
 assert.match(shell,/expected_previous_sha="1df868434643cfe28181c73526d0b5d0f4ced3d8017312cccb06292a5b1195e8"/);
 assert.match(shell,/expected_legacy_sha="f02130ab6ab733f458bef7e7c351702574040c7f7d065eea2965a2189d6319b0"/);
 assert.match(shell,/versionCode='\$rollback_code' versionName='\$rollback_version'/);
 assert.match(shell,/for rollback in/);
 assert.match(shell,/versionCode='24' versionName='0\.3\.23'/);
 assert.match(shell,/"23\|0\.3\.22\|\$expected_previous_sha"/);
 assert.match(shell,/"22\|0\.3\.21\|\$expected_legacy_sha"/);
 for(const script of ['scripts/render-mentor-preview.sh','scripts/render-mentor-owner-update-v24.sh']){
  const check=spawnSync('bash',['-n',script],{cwd:root,encoding:'utf8',timeout:10000});
  assert.equal(check.status,0,check.stderr);
 }
});

test('v24 dispatcher rejects missing, mismatched and legacy owner grants',()=>{
 for(const vars of [
  {},
  {CONTINUUM_RELEASE_SHA:head()},
  {CONTINUUM_OWNER_V24_APPROVED_SHA:head()},
  {CONTINUUM_OWNER_V24_APPROVED_SHA:'0'.repeat(40),CONTINUUM_RELEASE_SHA:'0'.repeat(40)},
  {CONTINUUM_OWNER_V23_APPROVED_SHA:head(),CONTINUUM_RELEASE_SHA:head()}
 ]){
  const r=dispatch(vars);
  assert.equal(r.status,69,r.stderr);
  assert.match(r.stderr,/OWNER_V24_BLOCKED/);
 }
});

test('v24 source gate rejects partial, mismatched and old-version approval',()=>{
 for(const vars of [
  {},
  {CONTINUUM_OWNER_V24_APPROVED_SOURCE_SHA:approvedSource},
  {CONTINUUM_OWNER_V24_APPROVED_MANIFEST_BLOB:inventory},
  {CONTINUUM_OWNER_V23_APPROVED_SOURCE_SHA:approvedSource,
   CONTINUUM_OWNER_V23_APPROVED_MANIFEST_BLOB:inventory,
   CONTINUUM_RELEASE_SHA:head(),CONTINUUM_OWNER_V23_APPROVED_SHA:head()},
  {CONTINUUM_OWNER_V24_APPROVED_SOURCE_SHA:'0'.repeat(40),
   CONTINUUM_OWNER_V24_APPROVED_MANIFEST_BLOB:inventory,
   CONTINUUM_RELEASE_SHA:head(),CONTINUUM_OWNER_V24_APPROVED_SHA:head()}
 ]){
  const r=gate(vars);
  assert.equal(r.status,69,r.stderr);
  assert.match(r.stderr,/OWNER_V24_BLOCKED/);
 }
});

test('v24 historical inventory remains intact but cannot approve the new v25 files',()=>{
 const data=JSON.parse(read('docs/release/continuum-0323-source-proposal-20261026.json'));
 assert.equal(data.state,'SOURCE_PROPOSAL_NOT_OWNER_APPROVAL');
 assert.equal(data.sourceHead,approvedSource);
 assert.equal(data.versionCode,24);
 assert.equal(data.versionName,'0.3.23');
 assert.equal(data.files.length,1027);
 const r=gate({
  CONTINUUM_OWNER_V24_APPROVED_SOURCE_SHA:approvedSource,
  CONTINUUM_OWNER_V24_APPROVED_MANIFEST_BLOB:inventory,
  CONTINUUM_RELEASE_SHA:head(),
  CONTINUUM_OWNER_V24_APPROVED_SHA:head()
 });
 assert.equal(r.status,69,r.stderr);
 assert.match(r.stderr,/OWNER_V24_BLOCKED/);
});

test('QA builder always uses a deliberately different certificate, not the owner key',()=>{
 const script=read('scripts/continuum-owner-v24-build-qa.sh');
 assert.match(script,/qa-only\.p12/);
 assert.match(script,/NONOWNER-QA-NOT-INSTALLABLE/);
 assert.match(script,/owner_installable=FALSE/);
 assert.match(script,/apksigner.*verify/);
 assert.match(script,/versionCode='25' versionName='0\.3\.24'/);
 assert.doesNotMatch(script,/CONTINUUM_MENTOR_SIGNING_P12_BASE64/);
});
