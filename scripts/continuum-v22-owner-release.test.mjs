import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { execFileSync, spawnSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const get=path=>readFileSync(resolve(root,path),'utf8');
const head=()=>execFileSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'}).trim();
const gate=env=>spawnSync(process.execPath,['scripts/continuum-v22-source-gate.mjs'],{
 cwd:root,encoding:'utf8',timeout:60000,env:{PATH:process.env.PATH,HOME:process.env.HOME,...env}
});

test('v22 is strictly an owner in-place update and leaves historical releases intact',()=>{
 const d=get('scripts/render-mentor-preview.sh');
 const recipe=get('scripts/render-mentor-owner-update-v22.sh');
 const gradle=get('app/build.gradle.kts');
 for(const n of [18,19,20,21,22]) assert.match(d,new RegExp('^  '+n+'\\)','m'));
 assert.match(d,/CONTINUUM_OWNER_V22_APPROVED_SHA/);
 assert.match(d,/exec bash scripts\/render-mentor-owner-update-v22\.sh/);
 assert.match(recipe,/-PdeviceAiMentorPreview=true -PdeviceAiOwnerSignedUpdate=true/);
 assert.match(recipe,/package: name='at\.heinz\.deviceai\.mentorpaired' versionCode='22' versionName='0\.3\.21'/);
 assert.match(recipe,/expected_signer="2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc"/);
 assert.match(recipe,/expected_previous_sha="c28597b7c28b495d36a161edc3b5bf938f8197a749b016bc6d2513807afa0b1f"/);
 assert.match(recipe,/versionCode='21' versionName='0\.3\.20'/);
 // The historical v22 script is retained, but its source approval never
 // grants permission for a later in-place owner update.
 // Current owner build keeps the historical default and explicitly opts into 26+.
 assert.match(gradle,/versionCode = continuumOwnerLocalVersion \?: 25/);
 assert.match(gradle,/versionName = if \(continuumOwnerLocalVersion != null\) "0\.3\.\$\{continuumOwnerLocalVersion - 1\}" else "0\.3\.24"/);
 for(const p of ['scripts/render-mentor-preview.sh','scripts/render-mentor-owner-update-v22.sh']){
  const r=spawnSync('bash',['-n',p],{cwd:root,encoding:'utf8',timeout:10000});
  assert.equal(r.status,0,r.stderr);
 }
});

test('missing or wrong exact SHA cannot reach owner signer',()=>{
 const args={CONTINUUM_OWNER_RELEASE_VERSION:'22'};
 for(const env of [
  {...args},
  {...args,CONTINUUM_RELEASE_SHA:head()},
  {...args,CONTINUUM_OWNER_V22_APPROVED_SHA:head()},
  {...args,CONTINUUM_OWNER_V22_APPROVED_SHA:'0'.repeat(40),CONTINUUM_RELEASE_SHA:'0'.repeat(40)}
 ]){
   const r=spawnSync('bash',['scripts/render-mentor-preview.sh'],{
      cwd:root,encoding:'utf8',timeout:10000,
      env:{PATH:process.env.PATH,HOME:process.env.HOME,...env}
   });
   assert.equal(r.status,69,r.stderr);
   assert.match(r.stderr,/OWNER_V22_BLOCKED/);
 }
});

test('a v22 source proposal is never mistaken for signed authorization',()=>{
 const no=gate({});
 assert.equal(no.status,69,no.stderr);
 assert.match(no.stderr,/OWNER_V22_BLOCKED/);
 const half=gate({CONTINUUM_OWNER_V22_APPROVED_SOURCE_SHA:'ec92490bbe7d75a2b9c16a47a94a1991f61837cc'});
 assert.equal(half.status,69,half.stderr);
 const wrong=gate({
  CONTINUUM_OWNER_V22_APPROVED_SOURCE_SHA:'ec92490bbe7d75a2b9c16a47a94a1991f61837cc',
  CONTINUUM_OWNER_V22_APPROVED_MANIFEST_BLOB:'0'.repeat(40),
  CONTINUUM_RELEASE_SHA:head(),CONTINUUM_OWNER_V22_APPROVED_SHA:head()
 });
 assert.equal(wrong.status,69,wrong.stderr);
});

test('exact approved source blob inventory is internally complete (CI simulation, NEVER signs)',()=>{
 const manifest=JSON.parse(get('docs/release/continuum-0321-source-proposal-20261024.json'));
 // Verify immutable historical owner source independently of shallow clone history.
 // This does NOT authorize any new v24 source or APK; it only authenticates v22.
 const historical=spawnSync(process.execPath,
  ['scripts/continuum-v22-historical-attestation.mjs'],{
   cwd:root,encoding:'utf8',timeout:30000
  });
 assert.equal(historical.status,0,historical.stderr);
 assert.match(historical.stdout,/OWNER_V22_HISTORICAL_TREE_VERIFIED/);
 const archived=JSON.parse(get('docs/release/continuum-v22-historical-git-tree-20261025.json'));
 const approved=new Map(archived.entries.filter(e=>e.type==='blob').map(e=>[e.path,e.sha]));
 const tree=ref=>new Map(execFileSync('git',['ls-tree','-r','-z',ref],{cwd:root,encoding:'utf8'})
  .split('\\0').filter(Boolean).map(line=>{const m=/^\\d+ blob ([a-f0-9]{40})\\t(.+)$/.exec(line);return m?[m[2],m[1]]:['',''];}));
 const current=tree('HEAD');
 assert.equal(manifest.files.length,927);
 for(const entry of manifest.files)assert.equal(approved.get(entry.path),entry.blob,entry.path);
 const r=gate({
  CONTINUUM_OWNER_V22_APPROVED_SOURCE_SHA:'ec92490bbe7d75a2b9c16a47a94a1991f61837cc',
  CONTINUUM_OWNER_V22_APPROVED_MANIFEST_BLOB:'36433bba220cb5706e286d8145df4c7a61734c0b',
  CONTINUUM_RELEASE_SHA:head(), CONTINUUM_OWNER_V22_APPROVED_SHA:head()
 });
 if(manifest.files.every(entry=>current.get(entry.path)===entry.blob)){
  assert.equal(r.status,0,r.stderr);
  assert.match(r.stdout,/OWNER_V22_SOURCE_VERIFIED/);
  assert.match(r.stdout,/files=927/);
 }else{
  // A development descendant must never reuse the historical owner's source approval.
  assert.equal(r.status,69,r.stderr);
  assert.match(r.stderr,/OWNER_V22_BLOCKED: Altered approved source/);
 }
});
