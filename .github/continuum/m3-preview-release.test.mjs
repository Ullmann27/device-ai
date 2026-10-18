import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const read=file=>fs.readFileSync(path.join(root,file),'utf8');
const gate=read('.github/continuum/m4-local-gate.mjs');
const wrapper=read('scripts/local-m4-android.sh');
const release=read('scripts/astra-max-execute.sh');
const gradle=read('app/build.gradle.kts');

test('M4 quality gate certifies the preview variant and lint in one Gradle execution',()=>{
  assert.match(gate,/spawnSync\('gradle',\[':app:lintDebug',':app:assembleDebug',\s*'-PdeviceAiPreview=true'/);
  assert.match(gate,/sha256ApkFile\(destination\)/);
  assert.match(gate,/report\.apk\.artifactSha256=integrity\.artifactSha256/);
  assert.match(gradle,/if \(deviceAiPreview\) applicationIdSuffix = "\.preview"/);
});

test('M4 wrapper emits a proof location only after independent release verification',()=>{
  const verified=wrapper.indexOf("result.releaseReady!==true");
  const proofMarker=wrapper.indexOf("CONTINUUM_VERIFIED_M4_REPORT=%s");
  assert.ok(verified>0 && proofMarker>verified);
  assert.match(wrapper,/M4 proof verifier did not confirm release readiness for this commit/);
});

test('Android release uses precisely the certified APK, never silently rebuilds another',()=>{
  const section=release.split('  android-release)\n')[1]?.split('\n    ;;')[0];
  assert.ok(section,'Expected exactly one android-release case');
  assert.doesNotMatch(section,/^\s*(?:gradle|\.\/gradlew)\b/m);
  assert.match(section,/bash scripts\/local-m4-android\.sh "\$head"/);
  assert.match(section,/verifyReleaseProofFile\(proofFile,\{expectedHead\}\)/);
  assert.match(section,/sha256ApkFile\(artifact\)/);
  assert.match(section,/actual\.artifactSha256!==report\.apk\.artifactSha256/);
  assert.match(section,/VERIFIED_PREVIEW_SHA256=/);
  assert.match(section,/INSTALL_ON_DEVICE=NOT_YET_VERIFIED/);
});

test('Both production launchers pass shell syntax checks',()=>{
  for(const script of ['scripts/astra-max-execute.sh','scripts/local-m4-android.sh']){
    const result=spawnSync('bash',['-n',path.join(root,script)],{
      cwd:root,encoding:'utf8',timeout:10_000,shell:false
    });
    assert.ifError(result.error);
    assert.equal(result.status,0,script+': '+result.stderr);
  }
});
