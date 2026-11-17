import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const read=relative=>fs.readFileSync(new URL('../../'+relative,import.meta.url),'utf8');

test('v0.3.12 updates the installed Mentor Pair package rather than creating a new app',()=>{
  const gradle=read('app/build.gradle.kts');
  assert.match(gradle,/versionCode = if \(deviceAiFeaturePreview\) 9 else if \(deviceAiDesignPreview\) 7 else if \(deviceAiMentorPreview\) 15 else if \(deviceAiPreview\) 6 else 4/);
  assert.match(gradle,/else if \(deviceAiMentorPreview\) "0\.3\.12" else if \(deviceAiPreview\) "0\.3\.3"/);
  assert.match(gradle,/applicationIdSuffix = "\.mentorpaired"/);
  assert.match(gradle,/signingConfig = signingConfigs\.getByName\("mentorPreview"\)/);
});

test('existing-owner signing identity is mandatory before publishing update',()=>{
 const script=read('scripts/render-mentor-preview.sh');
 assert.match(script,/CONTINUUM_MENTOR_SIGNING_P12_BASE64:\?Persistent signing keystore is missing/);
 assert.match(script,/CONTINUUM_MENTOR_SIGNING_PASSWORD:\?Signing password is missing/);
 assert.match(script,/expected_signer="2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc"/);
 assert.match(script,/name='at\.heinz\.deviceai\.mentorpaired' versionCode='15' versionName='0\.3\.12'/);
 assert.match(script,/DeviceAI-Continuum-v0\.3\.12-\$\{apk_sha256\}\.apk/);
 assert.match(script,/sha256sum --check --status/);
 assert.match(script,/prior_apk="DeviceAI-Continuum-v0\.3\.11-/);
 assert.match(script,/prior_sha="e529374f9e4eb4bde149c156e1f217405500c7c9c88711efa1b437d63bd01b72"/);
 assert.doesNotMatch(script,/deviceAiPreview=true/);
 assert.match(script,/actual_branch" != "\$render_delivery_branch"/);
});

test('update retains package-local data and never runs installer commands',()=>{
 const gradle=read('app/build.gradle.kts');
 const manifest=read('app/src/main/AndroidManifest.xml');
 const script=read('scripts/render-mentor-preview.sh');
 assert.match(manifest,/android:allowBackup="false"/);
 assert.doesNotMatch(script,/adb install|pm install|cmd package install|git reset --hard|rm -rf .*learning/);
 assert.match(gradle,/deviceAiMentorPreview\) \{/);
});
