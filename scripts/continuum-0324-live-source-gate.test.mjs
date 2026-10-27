import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {spawnSync,execFileSync} from 'node:child_process';
import {resolve,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';

// PROTOCOL TEST ONLY. Supplying synthetic approval variables here never grants
// production signing, alters Render settings or touches owner credentials.
const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const head=()=>execFileSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'}).trim();
const expectedSource='5270202e0699764b0eac5d4dd3e6a587b982ae05';
const expectedBlob='441e667cb1c8b48106e1227df0c50a37b5b53399';
const path='docs/release/continuum-0324-live-source-proposal-20261027.json';
const key=['CONTINUUM_OWNER_0324_LIVE_APPROVED_SOURCE_SHA',
 'CONTINUUM_OWNER_0324_LIVE_APPROVED_MANIFEST_BLOB',
 'CONTINUUM_OWNER_0324_LIVE_APPROVED_BUILDER_SHA','CONTINUUM_RELEASE_SHA'];
const base=Object.fromEntries(Object.entries(process.env).filter(([name])=>!key.includes(name)&&
 !name.startsWith('CONTINUUM_MENTOR_SIGNING_')));
function run(vars={}){
 return spawnSync(process.execPath,['scripts/continuum-0324-live-source-gate.mjs'],{
  cwd:root,encoding:'utf8',timeout:120000,env:{...base,...vars}
 });
}
const simulated=()=>({
 CONTINUUM_OWNER_0324_LIVE_APPROVED_SOURCE_SHA:expectedSource,
 CONTINUUM_OWNER_0324_LIVE_APPROVED_MANIFEST_BLOB:expectedBlob,
 CONTINUUM_OWNER_0324_LIVE_APPROVED_BUILDER_SHA:head(),
 CONTINUUM_RELEASE_SHA:head()
});
test('live inventory is explicitly NOT owner release approval',()=>{
 const p=JSON.parse(readFileSync(resolve(root,path),'utf8'));
 assert.equal(p.state,'SOURCE_PROPOSAL_NOT_OWNER_APPROVAL');
 assert.equal(p.sourceHead,expectedSource);
 assert.equal(p.files.length,1095);
 assert.equal(p.package,'at.heinz.deviceai.mentorpaired');
 assert.equal(p.versionCode,25);
 assert.equal(p.versionName,'0.3.24');
 assert.equal(p.certificateSha256,'2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc');
 assert.equal(new Set(p.files.map(x=>x.path)).size,p.files.length);
});
test('without owner consent the signed-release inventory gate fails closed',()=>{
 const p=run();
 assert.equal(p.status,69,p.stderr);
 assert.match(p.stderr,/OWNER_0324_LIVE_BLOCKED/);
});
test('partial, wrong or stale source and manifest approval fails closed',()=>{
 const good=simulated();
 for(const vars of [
  {CONTINUUM_OWNER_0324_LIVE_APPROVED_SOURCE_SHA:expectedSource},
  {...good,CONTINUUM_OWNER_0324_LIVE_APPROVED_SOURCE_SHA:'0'.repeat(40)},
  {...good,CONTINUUM_OWNER_0324_LIVE_APPROVED_MANIFEST_BLOB:'0'.repeat(40)},
  {...good,CONTINUUM_OWNER_0324_LIVE_APPROVED_BUILDER_SHA:'0'.repeat(40)},
  {...good,CONTINUUM_RELEASE_SHA:'0'.repeat(40)}
 ]){
  const p=run(vars);
  assert.equal(p.status,69,p.stderr);
  assert.match(p.stderr,/OWNER_0324_LIVE_BLOCKED/);
 }
});
test('synthetic exact approval exercises inventory verifier but authorizes NO release',()=>{
 // This is a local deterministic PROTOCOL fixture with synthetic variables.
 // It cannot read/store a signing P12 and does not invoke Gradle or Render.
 const result=run(simulated());
 assert.equal(result.status,0,result.stderr);
 assert.match(result.stdout,/OWNER_0324_LIVE_SOURCE_VERIFIED/);
});

test('live signing recipe is syntactically valid but does not self-authorize an APK',()=>{
 const p=spawnSync('bash',['-n','scripts/render-mentor-owner-update-v25-live.sh'],{
  cwd:root,encoding:'utf8',timeout:30000,env:base
 });
 assert.equal(p.status,0,p.stderr);
 const script=readFileSync(resolve(root,'scripts/render-mentor-owner-update-v25-live.sh'),'utf8');
 assert.match(script,/scripts\/continuum-0324-live-source-gate\.mjs/);
 assert.match(script,/CONTINUUM_OWNER_0324_LIVE_APPROVED_QA_STATUS_SHA256/);
 assert.match(script,/CONTINUUM_OWNER_0324_LIVE_APPROVED_BUILDER_SHA/);
 assert.match(script,/CONTINUUM_MENTOR_SIGNING_P12_BASE64/);
 assert.match(script,/2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc/);
 assert.doesNotMatch(script,/node scripts\/continuum-v25-source-gate\.mjs/);
});
test('live signer refuses even a matching branch and dummy keystore without owner approvals',()=>{
 const env={...base,
  CONTINUUM_RELEASE_SHA:head(),
  CONTINUUM_ASTRA_UI_SHA:'811803138c86a5c1a7a2a2b9cd196580a574a4a1',
  CONTINUUM_MENTOR_SIGNING_P12_BASE64:'INTENTIONALLY_INVALID_NOT_A_PRIVATE_KEY',
  CONTINUUM_MENTOR_SIGNING_PASSWORD:'INTENTIONALLY_INVALID'
 };
 delete env.CONTINUUM_OWNER_0324_LIVE_APPROVED_SOURCE_SHA;
 delete env.CONTINUUM_OWNER_0324_LIVE_APPROVED_MANIFEST_BLOB;
 delete env.CONTINUUM_OWNER_0324_LIVE_APPROVED_BUILDER_SHA;
 delete env.CONTINUUM_OWNER_0324_LIVE_QA_STATUS_URL;
 delete env.CONTINUUM_OWNER_0324_LIVE_APPROVED_QA_STATUS_SHA256;
 const p=spawnSync('bash',['scripts/render-mentor-owner-update-v25-live.sh'],{
  cwd:root,encoding:'utf8',timeout:30000,env
 });
 assert.equal(p.status,69,p.stderr);
 assert.match(p.stderr,/Unapproved live code25 builder SHA/);
 assert.doesNotMatch(p.stdout,/VERIFIED .* OWNER IN-PLACE UPDATE/);
});
test('even with synthetic exact builder approval, missing owner source/manifest blocks signing',()=>{
 const env={...base,
  CONTINUUM_RELEASE_SHA:head(),
  CONTINUUM_OWNER_0324_LIVE_APPROVED_BUILDER_SHA:head(),
  CONTINUUM_ASTRA_UI_SHA:'811803138c86a5c1a7a2a2b9cd196580a574a4a1',
  CONTINUUM_MENTOR_SIGNING_P12_BASE64:'INTENTIONALLY_INVALID',
  CONTINUUM_MENTOR_SIGNING_PASSWORD:'INTENTIONALLY_INVALID'
 };
 delete env.CONTINUUM_OWNER_0324_LIVE_APPROVED_SOURCE_SHA;
 delete env.CONTINUUM_OWNER_0324_LIVE_APPROVED_MANIFEST_BLOB;
 const p=spawnSync('bash',['scripts/render-mentor-owner-update-v25-live.sh'],{
  cwd:root,encoding:'utf8',timeout:30000,env
 });
 assert.equal(p.status,69,p.stderr);
 assert.match(p.stderr,/Live source inventory or exact owner authorization failed/);
});
