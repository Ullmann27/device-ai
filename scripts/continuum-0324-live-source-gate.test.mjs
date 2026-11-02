import test, {before, after} from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFileSync, writeFileSync, copyFileSync, mkdirSync, mkdtempSync, rmSync, unlinkSync, symlinkSync, lstatSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {spawnSync,execFileSync} from 'node:child_process';
import {resolve,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';

// PROTOCOL TEST ONLY. Supplying synthetic approval variables here never grants
// production signing, alters Render settings or touches owner credentials.
const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
let fixture;
const head=()=>git('rev-parse','HEAD');
const expectedSource='5270202e0699764b0eac5d4dd3e6a587b982ae05';
const expectedBlob='441e667cb1c8b48106e1227df0c50a37b5b53399';
const frozenPath='docs/evidence/continuum-v25-frozen-source';
const expectedFrozenTree='87e4c52821c4d5b5c61d4bab912b1dbe2aec1c25';
const path='docs/release/continuum-0324-live-source-proposal-20261027.json';
// Build an actual isolated Git tree from the exact historical commit and the
// original manifest. Never adapt the release gate or synthesize its blob hashes.
// A shallow clone must fetch the pinned commit before running this test.
const base=Object.fromEntries(Object.entries(process.env).filter(([name])=>
 !name.startsWith('CONTINUUM_') && !name.startsWith('GIT_') &&
 !['BASH_ENV','NODE_OPTIONS'].includes(name)));
Object.assign(base,{GIT_CONFIG_NOSYSTEM:'1',GIT_CONFIG_GLOBAL:'/dev/null',GIT_TERMINAL_PROMPT:'0'});
const git=(...args)=>execFileSync('git',args,{cwd:fixture,encoding:'utf8',env:base,stdio:['ignore','pipe','pipe']}).trim();
const rel='integration/continuum-owner-v26-live-candidate-20261027';
before(()=>{
 const manifest=JSON.parse(readFileSync(resolve(root,path),'utf8'));
 assert.equal(manifest.sourceHead,expectedSource);
 assert.equal(execFileSync('git',['hash-object','--',path],{cwd:root,encoding:'utf8',env:base}).trim(),expectedBlob);
 // Validate pathspecs before passing them to git/tar, including traversal and duplicates.
 const paths=manifest.files.map(item=>item.path);
 assert.equal(new Set(paths).size,1095);
 for(const p of paths)assert.match(p,/^(?!\/)(?!.*(?:^|\/)\.\.(?:\/|$))[A-Za-z0-9_.\/-]+$/);
 fixture=mkdtempSync(resolve(tmpdir(),'continuum-0324-protocol-'));
 const archive=resolve(fixture,'source.tar');
 try {
  execFileSync('git',['archive','--format=tar','--output='+archive,expectedSource,'--',...paths],
   {cwd:root,env:base,stdio:'pipe'});
  execFileSync('tar',['-xf',archive,'-C',fixture],{env:base,stdio:'pipe'});
  unlinkSync(archive);
 } catch(error) {
  // Render's authenticated checkout has no 'origin' and often no historical
  // objects. Reconstruct the SAME 1,095 files from immutable Git blob IDs
  // already audited against the original v25 tree. No generated dummy files.
  // A different frozen tree, missing file or changed blob fails this hook.
  const tree=execFileSync('git',['rev-parse','HEAD:'+frozenPath],
    {cwd:root,env:base,encoding:'utf8',stdio:'pipe'}).trim();
  assert.equal(tree,expectedFrozenTree,'The frozen historical Git tree changed');
  const tracked=execFileSync('git',['ls-files','-z','--',frozenPath],
    {cwd:root,env:base,encoding:'utf8',stdio:'pipe'}).split('\u0000').filter(Boolean);
  assert.equal(tracked.length,manifest.files.length,'Historical fixture has extra/missing files');
  for(const item of manifest.files) {
    const input=resolve(root,frozenPath,item.path);
    const stat=lstatSync(input);
    assert.ok(stat.isFile()&&!stat.isSymbolicLink(),'Not a regular historical source: '+item.path);
    const data=readFileSync(input);
    const digest=createHash('sha1').update('blob '+data.length+'\u0000').update(data).digest('hex');
    assert.equal(digest,item.blob,'Historical source bytes changed: '+item.path);
    const destination=resolve(fixture,item.path);
    mkdirSync(dirname(destination),{recursive:true});
    copyFileSync(input,destination);
  }
 }
 for(const p of [path,'scripts/continuum-0324-live-source-gate.mjs',
  'scripts/continuum-0324-live-source-gate.test.mjs','scripts/render-mentor-owner-update-v25-live.sh']){
  mkdirSync(dirname(resolve(fixture,p)),{recursive:true});copyFileSync(resolve(root,p),resolve(fixture,p));
 }
 execFileSync('git',['init','-q','--initial-branch='+rel,fixture],{env:base,stdio:'pipe'});
 git('add','-f','--all');
 git('-c','user.name=Continuum protocol test','-c','user.email=fixture@example.invalid',
  '-c','commit.gpgsign=false','commit','-q','-m','Synthetic fixture; not a release approval');
});
after(()=>{if(fixture)rmSync(fixture,{recursive:true,force:true});});
function run(vars={}){
 return spawnSync(process.execPath,['scripts/continuum-0324-live-source-gate.mjs'],{
  cwd:fixture,encoding:'utf8',timeout:120000,env:{...base,...vars}
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
  CONTINUUM_ASTRA_UI_SHA:'INTENTIONALLY_INVALID_POST_GATE_TRIPWIRE',
  CONTINUUM_MENTOR_SIGNING_P12_BASE64:'INTENTIONALLY_INVALID_NOT_A_PRIVATE_KEY',
  CONTINUUM_MENTOR_SIGNING_PASSWORD:'INTENTIONALLY_INVALID'
 };
 delete env.CONTINUUM_OWNER_0324_LIVE_APPROVED_SOURCE_SHA;
 delete env.CONTINUUM_OWNER_0324_LIVE_APPROVED_MANIFEST_BLOB;
 delete env.CONTINUUM_OWNER_0324_LIVE_APPROVED_BUILDER_SHA;
 delete env.CONTINUUM_OWNER_0324_LIVE_QA_STATUS_URL;
 delete env.CONTINUUM_OWNER_0324_LIVE_APPROVED_QA_STATUS_SHA256;
 const p=spawnSync('bash',['scripts/render-mentor-owner-update-v25-live.sh'],{
  cwd:fixture,encoding:'utf8',timeout:30000,env
 });
 assert.equal(p.status,69,p.stderr);
 assert.match(p.stderr,/Unapproved live code25 builder SHA/);
 assert.doesNotMatch(p.stdout,/VERIFIED .* OWNER IN-PLACE UPDATE/);
});
test('even with synthetic exact builder approval, missing owner source/manifest blocks signing',()=>{
 const env={...base,
  CONTINUUM_RELEASE_SHA:head(),
  CONTINUUM_OWNER_0324_LIVE_APPROVED_BUILDER_SHA:head(),
  CONTINUUM_ASTRA_UI_SHA:'INTENTIONALLY_INVALID_POST_GATE_TRIPWIRE',
  CONTINUUM_MENTOR_SIGNING_P12_BASE64:'INTENTIONALLY_INVALID',
  CONTINUUM_MENTOR_SIGNING_PASSWORD:'INTENTIONALLY_INVALID'
 };
 delete env.CONTINUUM_OWNER_0324_LIVE_APPROVED_SOURCE_SHA;
 delete env.CONTINUUM_OWNER_0324_LIVE_APPROVED_MANIFEST_BLOB;
 const p=spawnSync('bash',['scripts/render-mentor-owner-update-v25-live.sh'],{
  cwd:fixture,encoding:'utf8',timeout:30000,env
 });
 assert.equal(p.status,69,p.stderr);
 assert.match(p.stderr,/Live source inventory or exact owner authorization failed/);
});

test('frozen inventory rejects modified, missing, untracked and redirected sources',()=>{
 const sample='.github/continuum/android-always-on-wiring.test.mjs';
 const file=resolve(fixture,sample),original=readFileSync(file);
 try {
  writeFileSync(file,Buffer.concat([original,Buffer.from('\n// changed\n')]));
  let p=run(simulated());assert.equal(p.status,69,p.stderr);assert.match(p.stderr,/Altered approved source/);
  unlinkSync(file);
  p=run(simulated());assert.equal(p.status,69,p.stderr);assert.match(p.stderr,/ENOENT/);
  symlinkSync(resolve(fixture,'scripts/continuum-0324-live-source-gate.mjs'),file);
  p=run(simulated());assert.equal(p.status,69,p.stderr);assert.match(p.stderr,/Source redirection/);
 } finally {rmSync(file,{force:true});writeFileSync(file,original);}
 const extra=resolve(fixture,'scripts/unattested-release-fixture.mjs');
 try {
  writeFileSync(extra,'unattested');
  const p=run(simulated());assert.equal(p.status,69,p.stderr);assert.match(p.stderr,/Untracked executable source/);
 } finally {rmSync(extra,{force:true});}
});
test('changed inventory and newly tracked sources remain blocked',()=>{
 const manifestFile=resolve(fixture,path),original=readFileSync(manifestFile);
 try {
  writeFileSync(manifestFile,Buffer.concat([original,Buffer.from('\n')]));
  const p=run(simulated());assert.equal(p.status,69,p.stderr);assert.match(p.stderr,/Approved inventory bytes changed/);
 } finally {writeFileSync(manifestFile,original);}
 const extra='scripts/tracked-unattested-fixture.mjs',file=resolve(fixture,extra);
 try {
  writeFileSync(file,'unattested');git('add','-f','--',extra);
  const p=run(simulated());assert.equal(p.status,69,p.stderr);assert.match(p.stderr,/New unattested source/);
 } finally {git('reset','-q','HEAD','--',extra);rmSync(file,{force:true});}
});
test('unexpected branch is independently rejected by the unchanged signer',()=>{
 try {
  git('checkout','-q','-b','research/fixture-wrong-branch');
  const env={...base,CONTINUUM_RELEASE_SHA:head(),CONTINUUM_ASTRA_UI_SHA:'INTENTIONALLY_INVALID_POST_GATE_TRIPWIRE',
   CONTINUUM_MENTOR_SIGNING_P12_BASE64:'INTENTIONALLY_INVALID',CONTINUUM_MENTOR_SIGNING_PASSWORD:'INTENTIONALLY_INVALID'};
  const p=spawnSync('bash',['scripts/render-mentor-owner-update-v25-live.sh'],{cwd:fixture,encoding:'utf8',timeout:30000,env});
  assert.equal(p.status,69,p.stderr);assert.match(p.stderr,/Unexpected release branch/);
 } finally {git('checkout','-q',rel);}
});
