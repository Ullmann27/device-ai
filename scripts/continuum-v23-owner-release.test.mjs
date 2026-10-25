import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {execFileSync,spawnSync} from 'node:child_process';
import {dirname,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const source='d368220596912a2b3d0766b0bb3f324db60ec658';
const inventory='5628bef104921492d74cf6aaa7ee647018db050a';
const head=()=>execFileSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'}).trim();
const gate=env=>spawnSync(process.execPath,['scripts/continuum-v23-source-gate.mjs'],{
  cwd:root,encoding:'utf8',timeout:120000,
  env:{PATH:process.env.PATH,HOME:process.env.HOME,...env}
});
const dispatch=env=>spawnSync('bash',['scripts/render-mentor-preview.sh'],{
  cwd:root,encoding:'utf8',timeout:15000,
  env:{PATH:process.env.PATH,HOME:process.env.HOME,CONTINUUM_OWNER_RELEASE_VERSION:'23',...env}
});
test('owner release v23 keeps existing package, signer and version 23 only',()=>{
 const gradle=readFileSync(resolve(root,'app/build.gradle.kts'),'utf8');
 const shell=readFileSync(resolve(root,'scripts/render-mentor-owner-update-v23.sh'),'utf8');
 const router=readFileSync(resolve(root,'scripts/render-mentor-preview.sh'),'utf8');
 assert.match(gradle,/applicationIdSuffix = "\.mentorpaired"/);
 assert.match(gradle,/if \(deviceAiOwnerSignedUpdate\) \{[\s\S]*?versionCode = 23\s*versionName = "0\.3\.22"/);
 assert.match(shell,/versionCode='23' versionName='0\.3\.22'/);
 assert.match(shell,/versionCode='22' versionName='0\.3\.21'/);
 assert.match(shell,/expected_signer="2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc"/);
 assert.match(shell,/expected_previous_sha="f02130ab6ab733f458bef7e7c351702574040c7f7d065eea2965a2189d6319b0"/);
 assert.match(router,/CONTINUUM_OWNER_V23_APPROVED_SHA/);
 for(const n of [18,19,20,21,22,23])
   assert.match(router,new RegExp('^  '+n+'\\)','m'));
 for(const p of ['scripts/render-mentor-preview.sh','scripts/render-mentor-owner-update-v23.sh']){
   const pcheck=spawnSync('bash',['-n',p],{cwd:root,encoding:'utf8'});
   assert.equal(pcheck.status,0,pcheck.stderr);
 }
});
test('no missing, mismatched or partial v23 source approval is accepted',()=>{
 const empty=gate({});
 assert.equal(empty.status,69,empty.stderr);
 for(const env of [
   {CONTINUUM_OWNER_V23_APPROVED_SOURCE_SHA:source},
   {CONTINUUM_OWNER_V23_APPROVED_MANIFEST_BLOB:inventory},
   {CONTINUUM_OWNER_V23_APPROVED_SOURCE_SHA:source,CONTINUUM_OWNER_V23_APPROVED_MANIFEST_BLOB:inventory},
   {CONTINUUM_OWNER_V23_APPROVED_SOURCE_SHA:'a'.repeat(40),CONTINUUM_OWNER_V23_APPROVED_MANIFEST_BLOB:inventory,CONTINUUM_RELEASE_SHA:head(),CONTINUUM_OWNER_V23_APPROVED_SHA:head()},
   {CONTINUUM_OWNER_V23_APPROVED_SOURCE_SHA:source,CONTINUUM_OWNER_V23_APPROVED_MANIFEST_BLOB:inventory,CONTINUUM_RELEASE_SHA:head(),CONTINUUM_OWNER_V23_APPROVED_SHA:'f'.repeat(40)}
 ]){
   const r=gate(env);assert.equal(r.status,69,r.stderr);
   assert.match(r.stderr,/OWNER_V23_BLOCKED/);
 }
});
test('source manifest immutable blob matches pinned version and all entries',()=>{
 const inv=JSON.parse(readFileSync(resolve(root,'docs/release/continuum-0322-source-proposal-20261025.json'),'utf8'));
 assert.equal(inv.state,'SOURCE_PROPOSAL_NOT_OWNER_APPROVAL');
 assert.equal(inv.sourceHead,source);
 assert.equal(inv.versionCode,23);
 assert.equal(inv.versionName,'0.3.22');
 assert.equal(inv.package,'at.heinz.deviceai.mentorpaired');
 assert.equal(inv.files.length,961);
 assert.equal(new Set(inv.files.map(x=>x.path)).size,961);
 const blob=execFileSync('git',['hash-object','docs/release/continuum-0322-source-proposal-20261025.json'],{cwd:root,encoding:'utf8'}).trim();
 assert.equal(blob,inventory);
});
test('proposed source can be audited in CI simulation but is not owner consent or signing',()=>{
 const sha=head();
 const good=gate({CONTINUUM_OWNER_V23_APPROVED_SOURCE_SHA:source,CONTINUUM_OWNER_V23_APPROVED_MANIFEST_BLOB:inventory,CONTINUUM_RELEASE_SHA:sha,CONTINUUM_OWNER_V23_APPROVED_SHA:sha});
 assert.equal(good.status,0,good.stderr);
 assert.match(good.stdout,/OWNER_V23_SOURCE_VERIFIED/);
 const blocked=dispatch({CONTINUUM_RELEASE_SHA:sha,CONTINUUM_OWNER_V23_APPROVED_SHA:sha});
 assert.notEqual(blocked.status,0);
 assert.match(blocked.stderr,/CONTINUUM_RELEASE_SHA|CONTINUUM_ASTRA_UI_SHA|unbound|missing/i);
});
test('old release source approval cannot authorize v23',()=>{
 const old=gate({CONTINUUM_OWNER_V22_APPROVED_SHA:head(),CONTINUUM_OWNER_V22_APPROVED_SOURCE_SHA:source,CONTINUUM_OWNER_V22_APPROVED_MANIFEST_BLOB:inventory});
 assert.equal(old.status,69);
 assert.match(old.stderr,/OWNER_V23_BLOCKED/);
});
