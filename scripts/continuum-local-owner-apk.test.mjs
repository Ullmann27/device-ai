import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const command=path.join(root,'scripts/continuum-local-owner-apk.sh');
const script=fs.readFileSync(command,'utf8');
const gradle=fs.readFileSync(path.join(root,'app/build.gradle.kts'),'utf8');

test('owner-local builder is a syntactically valid executable Bash program',()=>{
 const result=spawnSync('bash',['-n',command],{encoding:'utf8'});
 assert.equal(result.status,0,result.stderr);
});
test('only explicit doctor and build commands accepted; no shell injection',()=>{
 for(const args of [[],['install'],['build','--unknown'],['doctor','--offline'],['build','--offline','--install']]){
   const child=spawnSync('bash',[command,...args],{encoding:'utf8',timeout:5000});
   assert.equal(child.status,64,child.stderr);
 }
});
test('main app update retains exact original application ID, checked not preview app',()=>{
 assert.match(gradle,/applicationIdSuffix = "\.mentorpaired"/);
 assert.match(script,/at\.heinz\.deviceai\.mentorpaired/);
 assert.match(script,/package: name='/);
 assert.doesNotMatch(script,/at\.heinz\.deviceai\.preview['"]/);
});
test('signed build requires original existing certificate AND pinned Git HEAD',()=>{
 assert.match(script,/2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc/);
 assert.match(script,/CONTINUUM_OWNER_LOCAL_APPROVED_SHA/);
 assert.match(script,/CONTINUUM_MENTOR_SIGNING_FILE/);
 assert.match(script,/CONTINUUM_MENTOR_SIGNING_PASSWORD/);
 assert.match(script,/keytool -list -v -storetype PKCS12/);
 assert.match(script,/\-storepass:env CONTINUUM_MENTOR_SIGNING_PASSWORD/);
 assert.match(script,/\[\[ "\$apk_fp" == "\$expected_signer" \]\]/);
});
test('supports updated owner version after code25 with no version downgrade',()=>{
 assert.match(gradle,/continuumOwnerLocalVersionCode/);
 assert.match(gradle,/continuumOwnerLocalVersion in 26\.\.9999/);
 assert.match(gradle,/continuumOwnerLocalVersionText == continuumOwnerLocalVersion\.toString\(\)/);
 assert.match(gradle,/versionCode = continuumOwnerLocalVersion \?: 25/);
 assert.match(script,/CONTINUUM_OWNER_NEXT_VERSION_CODE/);
 assert.match(script,/versionCode='\$owner_code'/);
});
test('runs genuine main-app Android JUnit, lint, compile and validates output',()=>{
 for(const task of [':app:testDebugUnitTest',':app:lintDebug',':app:assembleDebug'])
   assert.ok(script.includes(task),'Missing required '+task);
 assert.match(script,/apksigner/);
 assert.match(script,/verify --verbose --print-certs/);
 assert.match(script,/dump badging/);
 assert.match(script,/sha256sum/);
 assert.match(script,/SIGNED_NOT_DEVICE_INSTALLED/);
});
test('supports previously cached fully offline local builds',()=>{
 assert.match(script,/--offline/);
 assert.match(script,/flags\+=\(--offline\)/);
 assert.match(script,/deviceAiMobileAidlFallback=true/);
 assert.match(script,/android\.aapt2FromMavenOverride/);
});
test('never downloads model weights, calls Cloud CI or installs any APK',()=>{
 assert.doesNotMatch(script,/\b(?:curl|wget|gh|adb|pm|git push)\s/);
 assert.doesNotMatch(script,/https?:\/\//);
 assert.doesNotMatch(script,/\/data\/app\/.*\.apk/);
});
test('reports only after independently verifying signer, package and content checksum',()=>{
 const signer=script.indexOf('[[ "$apk_fp" == "$expected_signer" ]]');
 const badging=script.indexOf('dump badging');
 const checksum=script.indexOf('digest="$(sha256sum');
 const report=script.indexOf('CONTINUUM_OWNER_APK=%s');
 assert.ok(signer>=0 && badging>signer && checksum>badging && report>checksum);
});
