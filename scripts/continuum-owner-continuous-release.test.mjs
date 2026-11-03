import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync,mkdtempSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {spawnSync} from 'node:child_process';
import {resolve,dirname,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {verifyOwnerCatalog} from './continuum-owner-catalog-contract.mjs';

const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const names=[
 'render-continuum-m8-owner-release.sh',
 'render-continuum-owner-continuous-release.sh',
 'render-continuum-owner-continuous-qa.sh'
];
const release=readFileSync(join(root,'scripts',names[1]),'utf8');
const entry=readFileSync(join(root,'scripts',names[0]),'utf8');
const qa=readFileSync(join(root,'scripts',names[2]),'utf8');
const parser=readFileSync(join(root,'app/src/main/java/at/heinz/deviceai/intelligence/ContinuumOwnerUpdateCatalog.kt'),'utf8');
const policy=readFileSync(join(root,'app/src/main/java/at/heinz/deviceai/intelligence/ContinuumSignedUpdatePolicy.kt'),'utf8');
const signer='6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23';

test('original publisher retains syntax and persistent code39+ entrypoint',()=>{
 for(const name of names){
  const run=spawnSync('bash',['-n',join(root,'scripts',name)],{encoding:'utf8'});
  assert.equal(run.status,0,name+': '+run.stderr);
 }
 assert.match(entry,/render-continuum-owner-continuous-release\.sh/);
});
test('all future releases require exact owner approval and original signer',()=>{
 for(const requirement of [
  'CONTINUUM_RELEASE_APPROVED_SHA','CONTINUUM_RELEASE_FREEZE_LIFTED_BY_OWNER',
  'CONTINUUM_LOCAL_SIGNING_P12_BASE64',
  'Only future code39+ owner updates accepted',
  'VersionCode must strictly increase above live owner release',
  'continuum-owner-catalog-contract.mjs',
  'CONTINUUM_OWNER_RENDER_SIGNED_STAGED_FOR_PINNED_HOST'
 ]) assert.ok(release.includes(requirement),requirement);
 assert.ok(release.includes(signer));
 assert.match(release,/apk_signer.*expected_signer/);
 assert.match(qa,/continuumUiWidgetTests=true/);
 assert.match(qa,/ShizukuBridgeLifecycleTest/);
 assert.match(qa,/HybridMemoryRetrieverTest/);
});
test('dynamic manifest is understood by the installed Android parser for code39,40,41',()=>{
 const marker="node --input-type=module - \"$out/latest.json\" <<'NODE'\n";
 const i=release.indexOf(marker);
 assert.ok(i>=0,'release manifest program missing');
 const j=release.indexOf('\nNODE\n',i+marker.length);
 assert.ok(j>i,'release manifest program unterminated');
 const program=release.slice(i+marker.length,j);
 for(const code of [39,40,41]){
  const dir=mkdtempSync(join(tmpdir(),'continuum-continuous-'));
  try{
   const path=join(dir,'latest.json'),digest='b'.repeat(64),sha='c'.repeat(40);
   const result=spawnSync(process.execPath,['--input-type=module','-',path],{
    encoding:'utf8',cwd:root,input:program,timeout:15000,
    env:{...process.env,CONTINUUM_RELEASE_SHA:sha,
      CONTINUUM_RELEASE_DIGEST:digest,CONTINUUM_RELEASE_SIZE:'55000000',
      CONTINUUM_RELEASE_VERSION_CODE:String(code),
      CONTINUUM_RELEASE_VERSION_NAME:'0.3.'+(code-1)}
   });
   assert.equal(result.status,0,result.stderr);
   const data=JSON.parse(readFileSync(path,'utf8'));
   assert.equal(data.versionCode,code);
   assert.equal(data.versionName,'0.3.'+(code-1));
   assert.equal(data.signerSha256,signer);
   assert.equal(data.approved,true);
   assert.deepEqual(verifyOwnerCatalog(JSON.stringify(data),parser,policy),
    {sourceCommit:sha,versionCode:code,apkSha256:digest,byteLength:55000000});
  }finally{rmSync(dir,{recursive:true,force:true});}
 }
});
test('without the owner freeze-lift no signer or host publish operation starts',()=>{
 const p=spawnSync('bash',['scripts/render-continuum-owner-continuous-release.sh'],
  {cwd:root,encoding:'utf8',timeout:15000,
   env:{...process.env,CONTINUUM_RELEASE_VERSION_CODE:'39',
    CONTINUUM_RELEASE_APPROVED_SHA:'',CONTINUUM_RELEASE_FREEZE_LIFTED_BY_OWNER:'',
    CONTINUUM_LOCAL_SIGNING_P12_BASE64:'',CONTINUUM_LOCAL_SIGNING_PASSWORD:''}});
 assert.equal(p.status,69,p.stderr);
 assert.match(p.stderr,/P0 release freeze active|Exact source SHA not approved/);
 assert.doesNotMatch(p.stdout,/STAGED_FOR_PINNED_HOST/);
});
