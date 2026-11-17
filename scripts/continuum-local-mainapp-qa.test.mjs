import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const shPath=path.join(root,'scripts/continuum-local-mainapp-qa.sh');
const qa=fs.readFileSync(shPath,'utf8');
const owner=fs.readFileSync(path.join(root,'scripts/continuum-local-owner-apk.sh'),'utf8');
const gradle=fs.readFileSync(path.join(root,'app/build.gradle.kts'),'utf8');

test('QA full script is syntactically valid Bash',()=>{
 const result=spawnSync('bash',['-n',shPath],{encoding:'utf8'});
 assert.equal(result.status,0,result.stderr);
});
test('QA CLI rejects unknown commands before Gradle',()=>{
 for(const args of [['install'],['doctor'],['qa'],['--unsafe'],['--offline','--install']]){
  const child=spawnSync('bash',[shPath,...args],{encoding:'utf8',timeout:5000});
  assert.equal(child.status,64,args.join(' ')+': '+child.stderr);
 }
});
test('QA rejects original signing file before startup',()=>{
 const env={...process.env,CONTINUUM_MENTOR_SIGNING_FILE:'/private/owner.p12'};
 delete env.CONTINUUM_MENTOR_SIGNING_PASSWORD;
 const child=spawnSync('bash',[shPath],{encoding:'utf8',timeout:5000,env});
 assert.equal(child.status,69,child.stderr);
 assert.match(child.stderr,/Never expose owner signing credentials to QA/);
});
test('QA rejects any owner password without leaking it',()=>{
 const env={...process.env,CONTINUUM_MENTOR_SIGNING_PASSWORD:'private-token-not-in-logs'};
 delete env.CONTINUUM_MENTOR_SIGNING_FILE;
 const child=spawnSync('bash',[shPath,'--offline'],{encoding:'utf8',timeout:5000,env});
 assert.equal(child.status,69);
 assert.doesNotMatch(child.stderr,/private-token-not-in-logs/);
});
test('QA and owner compile same main-app package',()=>{
 const id='at.heinz.deviceai.mentorpaired';
 assert.ok(qa.includes(id));
 assert.ok(owner.includes(id));
 assert.match(gradle,/applicationIdSuffix = "\.mentorpaired"/);
 assert.match(qa,/root\/app\/build\.gradle\.kts/);
});
test('QA never enables owner signing or synthesizes release key',()=>{
 assert.match(qa,/-PcontinuumOwnerLocalQa=true/);
 assert.doesNotMatch(qa,/-PdeviceAiOwnerSignedUpdate=true/);
 assert.doesNotMatch(qa,/keytool -genkeypair|apksigner sign/);
});
test('Gradle rejects mixed owner/QA configuration and leaked credentials',()=>{
 assert.match(gradle,/deviceAiMentorPreview && !deviceAiOwnerSignedUpdate/);
 assert.match(gradle,/mentorSigningPath\.isNullOrBlank\(\)/);
 assert.match(gradle,/mentorSigningPassword\.isNullOrBlank\(\)/);
 assert.match(gradle,/!continuumOwnerLocalQa/);
});
test('existing owner release must still verify original signing certificate',()=>{
 assert.match(owner,/expected_signer='2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc'/);
 assert.match(owner,/\[\[ "\$key_fp" == "\$expected_signer" \]\]/);
 assert.match(owner,/\[\[ "\$apk_fp" == "\$expected_signer" \]\]/);
 assert.match(qa,/\[\[ "\$actual_signer" != "\$owner_signer" \]\]/);
});
test('QA and owner versionCode contract equal 26+',()=>{
 assert.match(gradle,/\(deviceAiOwnerSignedUpdate \|\| continuumOwnerLocalQa\)/);
 assert.match(gradle,/continuumOwnerLocalVersion in 26\.\.9999/);
 assert.match(gradle,/versionCode = continuumOwnerLocalVersion \?: 25/);
 assert.match(qa,/CONTINUUM_OWNER_NEXT_VERSION_CODE/);
 assert.match(qa,/version_name="0\.3\.\$\(\(version - 1\)\)"/);
});
test('QA does not pretend the original model executed',()=>{
 assert.doesNotMatch(qa,/Gemma.*PASS|gemmaCalls=[1-9]|modelInferenceProven=true/);
 assert.match(qa,/CONTINUUM_MAINAPP_QA_NOT_OWNER_INSTALLABLE=TRUE/);
});
test('QA binds exact HEAD and rejects uncommitted modifications',()=>{
 assert.match(qa,/CONTINUUM_LOCAL_QA_APPROVED_SHA/);
 assert.match(qa,/--porcelain --untracked-files=normal/);
 assert.match(qa,/Source mutated during compilation/);
});
test('real Kotlin and Android JUnit, lint, assemble tasks executed',()=>{
 for(const name of [':app:testDebugUnitTest',':app:lintDebug',':app:compileDebugKotlin',':app:assembleDebug'])
  assert.ok(qa.includes(name),'Missing actual task '+name);
});
test('native Termux aapt2 and existing ARM64 AIDL fallback',()=>{
 assert.match(qa,/CONTINUUM_AAPT2_PATH:-\$PREFIX\/bin\/aapt2/);
 assert.match(qa,/deviceAiMobileAidlFallback=true/);
 assert.match(qa,/continuum-local-owner-apk\.sh" doctor/);
});
test('real APK certificate/package/version/hash checked before success',()=>{
 const sig=qa.indexOf('verify --verbose --print-certs');
 const badging=qa.indexOf('dump badging');
 const digest=qa.indexOf('hash="$(sha256sum');
 const success=qa.indexOf('CONTINUUM_MAINAPP_QA_PASSED');
 assert.ok(sig>0 && badging>sig && digest>badging && success>digest);
});
test('QA output explicitly cannot update existing owner app',()=>{
 assert.match(qa,/Continuum-MAINAPP-QA-DO-NOT-INSTALL-/);
 assert.match(qa,/owner_installable=FALSE/);
 assert.match(qa,/owner_signer=FALSE/);
 assert.doesNotMatch(qa,/adb install|pm install/);
});
test('Gradle never picks up owner config in QA mode',()=>{
 assert.match(gradle,/deviceAiMentorPreview && !continuumOwnerLocalQa &&/);
 assert.match(gradle,/continuumOwnerLocalQa -> "Continuum QA NICHT INSTALLIEREN"/);
});
test('no cloud build services, new model downloads or external paid AI',()=>{
 assert.doesNotMatch(qa,/gh workflow run|render\.com|github\.com|curl\s|wget\s/);
 assert.doesNotMatch(qa,/https?:\/\//);
});
