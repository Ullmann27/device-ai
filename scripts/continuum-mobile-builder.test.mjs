import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { parseArgs, isTermuxArm64, doctor, build, readStatus } from './continuum-mobile-builder.mjs';

const HEAD='f'.repeat(40);
const SIGNER='a'.repeat(64);
function fixture() {
  const d=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-mobile-test-'));
  const root=path.join(d,'source'),stateDir=path.join(d,'state');
  fs.mkdirSync(path.join(root,'app/build/outputs/apk/debug'),{recursive:true});
  fs.writeFileSync(path.join(root,'settings.gradle.kts'),'rootProject.name = "test"');
  fs.writeFileSync(path.join(root,'app/build.gradle.kts'),'plugins {}');
  return {root,stateDir,artifact:path.join(root,'app/build/outputs/apk/debug/app-debug.apk')};
}
const ready={status:'READY_FOR_EXPERIMENTAL_BUILD',checks:{termuxArm64:true},bin:{git:'/fake/git',gradle:'/fake/gradle',aapt2:'/fake/aapt2',apksigner:'/fake/apksigner'}};
function mock(f,{dirty=false,fail=false,sign=true,commands=[]}={}) {
  return async (file,args)=>{
    commands.push({file,args});
    if(file==='/fake/git'&&args[0]==='rev-parse')return {ok:true,output:HEAD};
    if(file==='/fake/git'&&args[0]==='status')return {ok:true,output:dirty?' M app/build.gradle.kts':''};
    if(file==='/fake/gradle') {
      if(!fail)fs.writeFileSync(f.artifact,Buffer.alloc(8192,42));
      return {ok:!fail,code:fail?1:0,output:fail?'synthetic Gradle failure':'synthetic success'};
    }
    if(file==='/fake/apksigner')return {ok:sign,output:sign?'Signer #1 certificate SHA-256 digest: '+SIGNER:'invalid'};
    throw Error('Unexpected tool '+file);
  };
}
test('CLI supports only explicitly approved commands and flags',()=>{
  assert.equal(parseArgs(['doctor']).command,'doctor');
  assert.equal(parseArgs(['build','--offline']).offline,true);
  assert.throws(()=>parseArgs(['install']),/Usage/);
  assert.throws(()=>parseArgs(['build','--gradle-command','rm -rf']),/Unsupported/);
  assert.throws(()=>parseArgs(['build','--project','/a','--project','/b']),/Repeated/);
});
test('Android runner requires Termux ARM64',()=>{
  assert.equal(isTermuxArm64({PREFIX:'/data/data/com.termux/files/usr'},'arm64'),true);
  assert.equal(isTermuxArm64({PREFIX:'/data/data/com.termux/files/usr'},'x64'),false);
  assert.equal(isTermuxArm64({},'arm64'),false);
});
test('doctor without native toolchain reports unavailable',async()=>{
  const result=await doctor({env:{PATH:'/nonexistent',PREFIX:'/data/data/com.termux/files/usr'},arch:'arm64'});
  assert.equal(result.status,'UNAVAILABLE');
  assert.equal(result.checks.sdk36,false);
  assert.equal(result.checks.nativeAapt2,false);
});
test('state directory may not be inside source',async()=>{
  const f=fixture();
  await assert.rejects(build({project:f.root,stateDir:path.join(f.root,'state')}),/inside checkout/);
});
test('unavailable tools prevent Gradle invocation',async()=>{
  const f=fixture();let count=0;
  const result=await build({project:f.root,stateDir:f.stateDir,inspect:async()=>({status:'UNAVAILABLE',checks:{sdk36:false}}),run:async()=>{count++;throw Error('unexpected')}});
  assert.equal(result.status,'BLOCKED_MISSING_TOOLCHAIN');
  assert.equal(count,0);
});
test('uncommitted source blocks build before running Gradle',async()=>{
  const f=fixture(),commands=[];
  const result=await build({project:f.root,stateDir:f.stateDir,inspect:async()=>ready,run:mock(f,{dirty:true,commands})});
  assert.equal(result.status,'BLOCKED_UNCLEAN_SOURCE');
  assert.equal(commands.some(x=>x.file==='/fake/gradle'),false);
});
test('synthetic Gradle failure is not a successful build',async()=>{
  const f=fixture();
  const result=await build({project:f.root,stateDir:f.stateDir,inspect:async()=>ready,run:mock(f,{fail:true})});
  assert.equal(result.status,'FAILED_BUILD');
  assert.equal(readStatus(f.stateDir).status,'FAILED_BUILD');
});
test('unverified artifact is rejected and not published',async()=>{
  const f=fixture();
  const result=await build({project:f.root,stateDir:f.stateDir,inspect:async()=>ready,run:mock(f,{sign:false})});
  assert.equal(result.status,'FAILED_APK_SIGNATURE');
  assert.equal(fs.readdirSync(f.stateDir).some(x=>x.endsWith('.apk')),false);
});
test('synthetic signed output saves a distinct hash-tied copy; not proof of an on-device build',async()=>{
  const f=fixture(),commands=[];
  const result=await build({project:f.root,stateDir:f.stateDir,offline:true,inspect:async()=>ready,run:mock(f,{commands})});
  assert.equal(result.status,'EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED');
  assert.equal(result.signerSha256,SIGNER);
  assert.match(result.apkSha256,/^[0-9a-f]{64}$/);
  assert.equal(result.deviceInstallationVerified,false);
  assert.equal(result.productionReady,false);
  assert.equal(result.fullIndependentM4GatePassed,false);
  assert.equal(readStatus(f.stateDir).apkSha256,result.apkSha256);
  assert.equal(fs.existsSync(result.apk),true);
  assert.equal(result.apk!==f.artifact,true);
  const gradle=commands.find(x=>x.file==='/fake/gradle');
  assert.ok(gradle.args.includes('-Pandroid.aapt2FromMavenOverride=/fake/aapt2'));
  assert.ok(gradle.args.includes('-PdeviceAiMobileAidlFallback=true'));
  assert.ok(gradle.args.includes('--offline'));
  assert.ok(gradle.args.includes(':app:testDebugUnitTest'));
  assert.ok(gradle.args.includes(':app:lintDebug'));
  assert.ok(gradle.args.includes(':app:assembleDebug'));
});
