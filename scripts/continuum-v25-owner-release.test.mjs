import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {spawnSync,execFileSync} from 'node:child_process';
import {resolve,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const read=p=>readFileSync(resolve(root,p),'utf8');
const head=()=>execFileSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'}).trim();
const SOURCE='c80b72537ab6bbca84687261ab1a385f9fb2788b', MANIFEST_BLOB='981133e7c17d5ff4fc0ee39c87ebf7811c098ec5', COUNT=1034;
const gate=vars=>spawnSync(process.execPath,['scripts/continuum-v25-source-gate.mjs'],{
 cwd:root,encoding:'utf8',timeout:120000,
 env:{PATH:process.env.PATH,HOME:process.env.HOME,...vars}
});
const approval=()=>({
 CONTINUUM_OWNER_V25_APPROVED_SOURCE_SHA:SOURCE,
 CONTINUUM_OWNER_V25_APPROVED_MANIFEST_BLOB:MANIFEST_BLOB,
 CONTINUUM_RELEASE_SHA:head(),
 CONTINUUM_OWNER_V25_APPROVED_SHA:head()
});
test('v25 same owner package and strictly newer version with signed v24 rollback',()=>{
 const gradle=read('app/build.gradle.kts');
 const signer=read('scripts/render-mentor-owner-update-v25.sh');
 const router=read('scripts/render-mentor-preview.sh');
 assert.match(gradle,/versionCode = 25\s*versionName = "0\.3\.24"/);
 assert.match(gradle,/applicationIdSuffix = "\.mentorpaired"/);
 assert.match(signer,/expected_signer="2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc"/);
 assert.match(signer,/expected_previous_sha="4ff06e079bc835a51777ac89a8773bb24d90bccda7fb37e0d0ce899163d02003"/);
 assert.match(signer,/"24\|0\.3\.23\|\$expected_previous_sha"/);
 assert.match(signer,/"23\|0\.3\.22\|\$expected_legacy_sha"/);
 assert.match(signer,/"22\|0\.3\.21\|\$expected_ancient_sha"/);
 assert.match(signer,/versionCode='25' versionName='0\.3\.24'/);
 assert.match(router,/CONTINUUM_OWNER_V25_APPROVED_SHA/);
 for(const p of ['scripts/render-mentor-preview.sh','scripts/render-mentor-owner-update-v25.sh']){
  const r=spawnSync('bash',['-n',p],{cwd:root,encoding:'utf8'});
  assert.equal(r.status,0,r.stderr);
 }
});
test('v25 manifest freezes only the revised coding features and unchanged UI sources',()=>{
 const m=JSON.parse(read('docs/release/continuum-0324-source-proposal-20261026.json'));
 assert.equal(m.schemaVersion,1);
 assert.equal(m.state,'SOURCE_PROPOSAL_NOT_OWNER_APPROVAL');
 assert.equal(m.sourceHead,SOURCE);
 assert.equal(m.package,'at.heinz.deviceai.mentorpaired');
 assert.equal(m.versionCode,25);
 assert.equal(m.versionName,'0.3.24');
 assert.equal(m.files.length,COUNT);
 assert.equal(m.certificateSha256,'2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc');
 const r=gate(approval());
 assert.equal(r.status,0,r.stderr);
 assert.match(r.stdout,/OWNER_V25_SOURCE_VERIFIED/);
});
test('v25 gate fails closed on absent old, or partial grants',()=>{
 for(const vars of [{}, {CONTINUUM_OWNER_V24_APPROVED_SHA:head()},
   {...approval(),CONTINUUM_OWNER_V25_APPROVED_SHA:'0'.repeat(40)},
   {...approval(),CONTINUUM_OWNER_V25_APPROVED_MANIFEST_BLOB:'0'.repeat(40)}]){
  const r=gate(vars);
  assert.equal(r.status,69,r.stderr);
  assert.match(r.stderr,/OWNER_V25_BLOCKED/);
 }
});
test('v25 QA signing is intentionally never an owner-app installer',()=>{
 const qa=read('scripts/continuum-owner-v24-build-qa.sh');
 assert.match(qa,/NONOWNER-QA-NOT-INSTALLABLE/);
 assert.match(qa,/owner_installable=FALSE/);
 assert.match(qa,/versionCode='25' versionName='0\.3\.24'/);
 assert.doesNotMatch(qa,/CONTINUUM_MENTOR_SIGNING_P12_BASE64/);
});
