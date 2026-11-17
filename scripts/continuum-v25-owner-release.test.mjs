import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {spawnSync,execFileSync} from 'node:child_process';
import {resolve,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';

const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const read=p=>readFileSync(resolve(root,p),'utf8');
const git=(...args)=>execFileSync('git',args,{cwd:root,encoding:'utf8'}).trim();
const SOURCE='db23e9ce06a0a54cb487b6bf25521906e0c9b312';
const MANIFEST_BLOB='017adf6d43bb604578721e820151bd60056ad361';
const COUNT=1034;
const head=()=>git('rev-parse','HEAD');
const gate=vars=>spawnSync(process.execPath,['scripts/continuum-v25-source-gate.mjs'],{
  cwd:root,encoding:'utf8',timeout:120000,
  env:{PATH:process.env.PATH,HOME:process.env.HOME,...vars}
});
const historicalApproval=()=>({
  CONTINUUM_OWNER_V25_APPROVED_SOURCE_SHA:SOURCE,
  CONTINUUM_OWNER_V25_APPROVED_MANIFEST_BLOB:MANIFEST_BLOB,
  CONTINUUM_RELEASE_SHA:head(),
  CONTINUUM_OWNER_V25_APPROVED_SHA:head()
});

/** The v25 mentorpaired release is immutable history, NOT the current owner-local app. */
test('v25 rollback builder remains a fixed, separately signed historical mentorpaired route',()=>{
  const signer=read('scripts/render-mentor-owner-update-v25.sh');
  const router=read('scripts/render-mentor-preview.sh');
  assert.match(signer,/signed_release_ref="release\/continuum-owner-v25-candidate-20261026"/);
  assert.doesNotMatch(signer,/signed_release_ref="release\/continuum-owner-v24-candidate-20261026"/);
  assert.match(signer,/expected_signer="2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc"/);
  assert.match(signer,/expected_previous_sha="4ff06e079bc835a51777ac89a8773bb24d90bccda7fb37e0d0ce899163d02003"/);
  for(const prior of ['24|0.3.23|$expected_previous_sha','23|0.3.22|$expected_legacy_sha',
    '22|0.3.21|$expected_ancient_sha']) assert.ok(signer.includes('"'+prior+'"'));
  assert.match(signer,/versionCode='25' versionName='0\.3\.24'/);
  assert.match(router,/CONTINUUM_OWNER_V25_APPROVED_SHA/);
  for(const p of ['scripts/render-mentor-preview.sh','scripts/render-mentor-owner-update-v25.sh']){
    const result=spawnSync('bash',['-n',p],{cwd:root,encoding:'utf8'});
    assert.equal(result.status,0,result.stderr);
  }
});

test('frozen v25 source proposal and blob are unchanged; they cannot authorize the current APK',()=>{
  const manifestPath='docs/release/continuum-0324-source-proposal-20261026.json';
  const m=JSON.parse(read(manifestPath));
  assert.equal(git('hash-object','--',manifestPath),MANIFEST_BLOB,'immutable historical inventory');
  assert.equal(m.schemaVersion,1);
  assert.equal(m.state,'SOURCE_PROPOSAL_NOT_OWNER_APPROVAL');
  assert.equal(m.sourceHead,SOURCE);
  assert.equal(m.package,'at.heinz.deviceai.mentorpaired');
  assert.equal(m.versionCode,25);
  assert.equal(m.versionName,'0.3.24');
  assert.equal(m.files.length,COUNT);
  assert.equal(m.certificateSha256,'2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc');
  // This modern integration HEAD has a different app/package/source inventory.
  // Historical approval variables MUST NOT make it pass the v25 release gate.
  assert.notEqual(head(),SOURCE);
  const rejected=gate(historicalApproval());
  assert.equal(rejected.status,69,rejected.stderr);
  assert.match(rejected.stderr,/OWNER_V25_BLOCKED/);
});

test('v25 gate fails closed for absent, old or partial grants',()=>{
  for(const vars of [{},{CONTINUUM_OWNER_V24_APPROVED_SHA:head()},
    {...historicalApproval(),CONTINUUM_OWNER_V25_APPROVED_SHA:'0'.repeat(40)},
    {...historicalApproval(),CONTINUUM_OWNER_V25_APPROVED_MANIFEST_BLOB:'0'.repeat(40)}]){
    const result=gate(vars);
    assert.equal(result.status,69,result.stderr);
    assert.match(result.stderr,/OWNER_V25_BLOCKED/);
  }
});

test('historical QA has a separate package/test certificate; active standalone keeps original identity',()=>{
  const qa=read('scripts/continuum-owner-v24-build-qa.sh');
  assert.match(qa,/NONOWNER-QA-NOT-INSTALLABLE/);
  assert.match(qa,/owner_installable=FALSE/);
  assert.match(qa,/versionCode='25' versionName='0\.3\.24'/);
  assert.doesNotMatch(qa,/CONTINUUM_MENTOR_SIGNING_P12_BASE64/);
  const gradle=read('app/build.gradle.kts');
  const manifest=read('scripts/continuum-owner-update-manifest.mjs');
  assert.ok(gradle.includes('continuumLocalStandalone'));
  assert.ok(gradle.includes('"at.heinz.deviceai.continuumlocal"'));
  assert.ok(gradle.includes('signingConfigs.getByName("continuumLocal")'));
  assert.ok(gradle.includes('applicationIdSuffix = ".qa"'));
  assert.ok(manifest.includes("const PACKAGE = 'at.heinz.deviceai.continuumlocal'"));
  assert.ok(manifest.includes("const CERT = '6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23'"));
});
