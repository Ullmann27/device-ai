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
const expectedSource='39008eaffb42a092b40aebd295df6b66f2cada61';
const expectedBlob='402e8e24cb514627802ce7cf5b64db52b989ec7c';
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
