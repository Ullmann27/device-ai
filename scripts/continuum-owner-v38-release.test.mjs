import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync,mkdtempSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {spawnSync} from 'node:child_process';
import {resolve,dirname,join} from 'node:path';
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
test('owner-approved release retains original signing and exact-SHA gates',()=>{
  assert.match(source,/channel:\s*'OWNER_RELEASE'/);
  assert.match(source,/approved:\s*true/);
  assert.doesNotMatch(source,/OWNER_RELEASE_CANDIDATE|SIGNED_STAGED_DEVICE_NOT_RUN/);
  assert.match(source,/CONTINUUM_RELEASE_APPROVED_SHA/);
  assert.match(source,/CONTINUUM_RELEASE_FREEZE_LIFTED_BY_OWNER/);
  assert.match(source,/CONTINUUM_LOCAL_SIGNING_P12_BASE64/);
  assert.match(source,/key_fingerprint.*expected_signer/);
  assert.match(source,/apk_signer.*expected_signer/);
});

test('generated live latest.json matches exact shipped Android owner-parser schema',()=>{
  const marker="node --input-type=module - \"$out/latest.json\" <<'NODE'\n";
  const pos=source.indexOf(marker);
  assert.ok(pos>=0,'release manifest node heredoc not found');
  const start=pos+marker.length;
  const end=source.indexOf("\nNODE\n",start);
  assert.ok(end>start,'release manifest generator terminator not found');
  const program=source.slice(start,end);
  const dir=mkdtempSync(join(tmpdir(),'continuum-live-catalog-'));
  try {
    const output=join(dir,'latest.json');
    const commit='d'.repeat(40);
    const digest='a'.repeat(64);
    const result=spawnSync(process.execPath,['--input-type=module','-',output],{
      cwd:root,encoding:'utf8',input:program,timeout:15000,
      env:{...process.env,CONTINUUM_RELEASE_SHA:commit,CONTINUUM_RELEASE_DIGEST:digest,
        CONTINUUM_RELEASE_SIZE:'54673371',CONTINUUM_RELEASE_VERSION_NAME:'0.3.37'}
    });
    assert.equal(result.status,0,result.stderr);
    const manifest=JSON.parse(readFileSync(output,'utf8'));
    const allowed=['schema','channel','packageName','versionCode','versionName','apkUrl',
      'apkSha256','byteLength','sourceCommit','signerSha256','approved','notes'];
    assert.deepEqual(Object.keys(manifest).sort(),allowed.sort(),
      'extra keys must not break the installed ContinuumOwnerUpdateCatalog parser');
    assert.equal(manifest.schema,1);
    assert.equal(manifest.channel,'OWNER_RELEASE');
    assert.equal(manifest.approved,true,'owner explicitly authorized this v38 update');
    assert.equal(manifest.packageName,'at.heinz.deviceai.continuumlocal');
    assert.equal(manifest.versionCode,38);
    assert.equal(manifest.versionName,'0.3.37');
    assert.equal(manifest.sourceCommit,commit);
    assert.equal(manifest.apkSha256,digest);
    assert.equal(manifest.byteLength,54673371);
    assert.equal(manifest.signerSha256,'6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23');
    assert.equal(manifest.apkUrl,'https://continuum-owner-v23-release-preflight.onrender.com/continuum-local/Continuum-Lokal-'+digest+'.apk');
  } finally { rmSync(dir,{recursive:true,force:true}); }
});
