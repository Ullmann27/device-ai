import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const ROOT=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const gradle=fs.readFileSync(path.join(ROOT,'app/build.gradle.kts'),'utf8');
const regression=fs.readFileSync(path.join(ROOT,'scripts/continuum-fusion-full-regression.sh'),'utf8');
const release=fs.readFileSync(path.join(ROOT,'scripts/continuum-fold7-standalone-apk.sh'),'utf8');
test('standalone QA is opt-in and explicitly exclusive with signed owner lane',()=>{
  assert.match(gradle,/val continuumLocalStandaloneQa\s*=\s*providers\.gradleProperty\("continuumLocalStandaloneQa"\)/);
  assert.match(gradle,/require\(!\(continuumLocalStandalone && continuumLocalStandaloneQa\)\)/);
});
test('source builds new continuumlocal app base ID under QA but uses a separate package suffix',()=>{
  assert.match(gradle,/applicationId = if \(continuumLocalStandalone \|\| continuumLocalStandaloneQa \|\| continuumLocalRenderUnsigned\) "at\.heinz\.deviceai\.continuumlocal"/);
  assert.match(gradle,/else if \(continuumLocalStandaloneQa\) \{[\s\S]{0,180}applicationIdSuffix = "\.qa"/);
  assert.match(gradle,/continuumLocalStandaloneQa -> "Continuum Lokal QA NICHT INSTALLIEREN"/);
});
test('owner and historical mentor signing credentials are prohibited for QA',()=>{
  const marker='require(!continuumLocalStandaloneQa ||';
  const start=gradle.indexOf(marker);
  assert.ok(start>=0);
  const contract=gradle.slice(start,start+600);
  for(const s of ['mentorSigningPath.isNullOrBlank()','mentorSigningPassword.isNullOrBlank()',
    'continuumLocalSigningPath.isNullOrBlank()','continuumLocalSigningPassword.isNullOrBlank()'])
    assert.ok(contract.includes(s),s);
});
test('QA rejects assembly/bundle or release tasks before Gradle runs them',()=>{
  assert.match(gradle,/gradle\.startParameter\.taskNames\.all/);
  assert.match(gradle,/setOf\("testDebugUnitTest", "lintDebug", "compileDebugKotlin"\)/);
  assert.doesNotMatch(gradle,/setOf\("testDebugUnitTest", "lintDebug", "compileDebugKotlin",\s*"assembleRelease"/);
});
test('full regression targets QA source with correct package and no owner signer',()=>{
  assert.match(regression,/-PdeviceAiMentorPreview=true\s+-PcontinuumLocalStandaloneQa=true/);
  assert.match(regression,/-PcontinuumStandaloneVersionCode=27/);
  assert.match(regression,/:app:testDebugUnitTest :app:lintDebug/);
  assert.doesNotMatch(regression,/\bkeytool -genkeypair\b/);
});
test('owner release still uses independently signed standalone build with original package',()=>{
  assert.match(release,/-PcontinuumLocalStandalone=true/);
  assert.match(release,/at\.heinz\.deviceai\.continuumlocal/);
  assert.match(gradle,/if \(continuumLocalStandalone\) \{[\s\S]{0,100}signingConfig = signingConfigs\.getByName\("continuumLocal"\)/);
});
