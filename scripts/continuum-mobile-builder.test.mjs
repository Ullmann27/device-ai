import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { parseArgs, isTermuxArm64, doctor, build, readStatus, failedJvmTests, parseSignerSha256, resolveTermuxEnvironment, copyVerifiedApkToOwnerDownloads } from './continuum-mobile-builder.mjs';
import { acquireContinuumBuildLease } from '../.github/continuum/shared-build-lease.mjs';

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
function mock(f,{dirty=false,fail=false,sign=true,signFormat='legacy',commands=[],changedHead=false,changedSource=false,junitFailure=false}={}) {
  let gradleFinished=false;
  return async (file,args)=>{
    commands.push({file,args});
    if(file==='/fake/git'&&args[0]==='rev-parse')return {ok:true,output:gradleFinished&&changedHead?'e'.repeat(40):HEAD};
    if(file==='/fake/git'&&args[0]==='status')return {ok:true,output:dirty||gradleFinished&&changedSource?' M app/build.gradle.kts':''};
    if(file==='/fake/gradle') {
      gradleFinished=true;
      if(!fail)fs.writeFileSync(f.artifact,Buffer.alloc(8192,42));
      if(fail&&junitFailure){
        const dir=path.join(f.root,'app/build/test-results/testDebugUnitTest');
        fs.mkdirSync(dir,{recursive:true});
        fs.writeFileSync(path.join(dir,'TEST-at.heinz.deviceai.learning.RetrievalBenchmarkTest.xml'),
          '<testsuite><testcase name="kotlinRankingMatchesCurrentFrozenFixture" classname="at.heinz.deviceai.learning.RetrievalBenchmarkTest"><failure message="expected 0.625">failed</failure></testcase><testcase name="passing" classname="Example"/></testsuite>');
      }
      return {ok:!fail,code:fail?1:0,output:fail?'synthetic Gradle failure':'synthetic success'};
    }
    if(file==='/fake/aapt2')return {ok:true,code:0,output:"package: name='at.heinz.deviceai.preview' versionCode='9' versionName='0.3.9'"};
    if(file==='/fake/apksigner')return {ok:sign,code:sign?0:1,output:sign?(signFormat==='modern'?'V2 Signer: ':'Signer #1 ')+'certificate SHA-256 digest: '+SIGNER:'DOES NOT VERIFY: Invalid APK signature'};
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
  assert.equal(result.packageName,'at.heinz.deviceai.preview');
  assert.equal(result.versionCode,9);
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

test('owner Android build automatically publishes a hash-checked APK in the dedicated Download folder',async()=>{
  const f=fixture(),home=path.dirname(f.root),downloads=path.join(home,'storage/downloads');
  fs.mkdirSync(downloads,{recursive:true});
  const env={PREFIX:'/data/data/com.termux/files/usr',HOME:home,PATH:'/usr/bin'};
  const commands=[];
  const result=await build({project:f.root,stateDir:f.stateDir,env,arch:'arm64',
    inspect:async()=>ready,run:mock(f,{commands})});
  assert.equal(result.status,'EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED');
  assert.equal(result.ownerInstallCopy.status,'COPIED');
  assert.equal(result.ownerInstallCopy.alreadyPresent,false);
  assert.equal(result.ownerInstallCopy.sha256,result.apkSha256);
  assert.equal(path.dirname(result.ownerInstallCopy.apk),path.join(downloads,'00-DEVICEAI-INSTALLATION'));
  assert.match(path.basename(result.ownerInstallCopy.apk),
    /^DeviceAI-Continuum-Preview-[0-9a-f]{7}-[0-9a-f]{12}\.apk$/);
  assert.equal(createHash('sha256').update(fs.readFileSync(result.ownerInstallCopy.apk)).digest('hex'),
    result.apkSha256);
  assert.equal(fs.existsSync(result.apk),true,'immutable private copy remains intact');
  assert.equal(commands.some(x=>/install|am|pm/.test(x.file)),false,'no package installer was called');

  const again=await build({project:f.root,stateDir:f.stateDir,env,arch:'arm64',
    inspect:async()=>ready,run:mock(f)});
  assert.equal(again.ownerInstallCopy.status,'COPIED');
  assert.equal(again.ownerInstallCopy.alreadyPresent,true);
  assert.equal(again.ownerInstallCopy.apk,result.ownerInstallCopy.apk);
});

test('owner file created during a publication race is never overwritten',async()=>{
  const f=fixture(),home=path.dirname(f.root),downloads=path.join(home,'storage/downloads');
  const folder=path.join(downloads,'00-DEVICEAI-INSTALLATION');
  fs.mkdirSync(folder,{recursive:true});
  const source=path.join(f.stateDir,'source-signed.apk');
  fs.mkdirSync(f.stateDir,{recursive:true});
  const bytes=Buffer.alloc(8192,42);
  fs.writeFileSync(source,bytes);
  const digest=createHash('sha256').update(bytes).digest('hex');
  const target=path.join(folder,'DeviceAI-Continuum-Preview-'+HEAD.slice(0,7)+'-'+digest.slice(0,12)+'.apk');
  const original=fs.copyFileSync;
  let raced=false;
  try {
    fs.copyFileSync=function(src,dst,flags) {
      if(dst===target && flags===fs.constants.COPYFILE_EXCL && !raced) {
        raced=true;
        fs.writeFileSync(dst,'OWNER_OR_OTHER_PROCESS_FILE');
      }
      return original.call(fs,src,dst,flags);
    };
    const report=await copyVerifiedApkToOwnerDownloads({
      apk:source,digest,commit:HEAD,project:f.root,
      env:{PREFIX:'/data/data/com.termux/files/usr',HOME:home,PATH:'/usr/bin'},arch:'arm64'
    });
    assert.equal(raced,true,'a competing owner write reached the publication boundary');
    assert.equal(report.status,'UNAVAILABLE');
    assert.equal(report.reason,'DESTINATION_BECAME_OCCUPIED');
    assert.equal(fs.readFileSync(target,'utf8'),'OWNER_OR_OTHER_PROCESS_FILE');
    assert.deepEqual(fs.readFileSync(source),bytes);
  } finally {
    fs.copyFileSync=original;
  }
});

test('a corrupted final Download copy is reported without deleting owner files',async()=>{
  const f=fixture(),home=path.dirname(f.root),downloads=path.join(home,'storage/downloads');
  const folder=path.join(downloads,'00-DEVICEAI-INSTALLATION');
  fs.mkdirSync(folder,{recursive:true});
  const source=path.join(f.stateDir,'source-signed.apk');
  fs.mkdirSync(f.stateDir,{recursive:true});
  const bytes=Buffer.alloc(8192,42);
  fs.writeFileSync(source,bytes);
  const digest=createHash('sha256').update(bytes).digest('hex');
  const target=path.join(folder,'DeviceAI-Continuum-Preview-'+HEAD.slice(0,7)+'-'+digest.slice(0,12)+'.apk');
  const original=fs.copyFileSync;
  try {
    fs.copyFileSync=function(src,dst,flags) {
      if(dst===target && flags===fs.constants.COPYFILE_EXCL) {
        fs.writeFileSync(dst,'UNEXPECTED_OWNER_FILE_AFTER_COPY');
        return;
      }
      return original.call(fs,src,dst,flags);
    };
    const report=await copyVerifiedApkToOwnerDownloads({
      apk:source,digest,commit:HEAD,project:f.root,
      env:{PREFIX:'/data/data/com.termux/files/usr',HOME:home,PATH:'/usr/bin'},arch:'arm64'
    });
    assert.equal(report.status,'UNAVAILABLE');
    assert.equal(report.reason,'FINAL_COPY_HASH_MISMATCH');
    assert.equal(fs.readFileSync(target,'utf8'),'UNEXPECTED_OWNER_FILE_AFTER_COPY');
    assert.deepEqual(fs.readFileSync(source),bytes);
  } finally {
    fs.copyFileSync=original;
  }
});

test('missing owner Download storage never fakes a published installer copy',async()=>{
  const f=fixture(),home=path.dirname(f.root);
  const result=await build({project:f.root,stateDir:f.stateDir,
    env:{PREFIX:'/data/data/com.termux/files/usr',HOME:home,PATH:'/usr/bin'},
    arch:'arm64',inspect:async()=>ready,run:mock(f)});
  assert.equal(result.status,'EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED');
  assert.deepEqual(result.ownerInstallCopy,{status:'UNAVAILABLE',reason:'TERMUX_STORAGE_NOT_CONFIGURED'});
  assert.equal(fs.existsSync(result.apk),true);
});

test('unsafe symlink install folder is rejected without modifying the source project',async()=>{
  const f=fixture(),home=path.dirname(f.root),downloads=path.join(home,'storage/downloads');
  fs.mkdirSync(downloads,{recursive:true});
  fs.symlinkSync(f.root,path.join(downloads,'00-DEVICEAI-INSTALLATION'));
  const result=await build({project:f.root,stateDir:f.stateDir,
    env:{PREFIX:'/data/data/com.termux/files/usr',HOME:home,PATH:'/usr/bin'},
    arch:'arm64',inspect:async()=>ready,run:mock(f)});
  assert.equal(result.status,'EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED');
  assert.equal(result.ownerInstallCopy.reason,'UNSAFE_INSTALL_FOLDER');
  assert.equal(fs.existsSync(result.apk),true);
  assert.equal(fs.existsSync(path.join(f.root,'DeviceAI-Continuum-Preview-'+HEAD.slice(0,7)+'.apk')),false);
});

test('pre-existing conflicting APK is never overwritten by automatic publication',async()=>{
  const f=fixture(),home=path.dirname(f.root),downloads=path.join(home,'storage/downloads');
  const folder=path.join(downloads,'00-DEVICEAI-INSTALLATION');
  fs.mkdirSync(folder,{recursive:true});
  const raw=Buffer.alloc(8192,42);
  const digest=createHash('sha256').update(raw).digest('hex');
  const target=path.join(folder,'DeviceAI-Continuum-Preview-'+HEAD.slice(0,7)+'-'+digest.slice(0,12)+'.apk');
  fs.writeFileSync(target,'OWNER_KEPT_FILE');
  const result=await build({project:f.root,stateDir:f.stateDir,
    env:{PREFIX:'/data/data/com.termux/files/usr',HOME:home,PATH:'/usr/bin'},
    arch:'arm64',inspect:async()=>ready,run:mock(f)});
  assert.equal(result.status,'EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED');
  assert.equal(result.ownerInstallCopy.reason,'EXISTING_APK_CONFLICT');
  assert.equal(fs.readFileSync(target,'utf8'),'OWNER_KEPT_FILE');
  assert.equal(fs.existsSync(result.apk),true);
});

test('rejected signature never generates an owner Download installation APK',async()=>{
  const f=fixture(),home=path.dirname(f.root),downloads=path.join(home,'storage/downloads');
  fs.mkdirSync(downloads,{recursive:true});
  const result=await build({project:f.root,stateDir:f.stateDir,
    env:{PREFIX:'/data/data/com.termux/files/usr',HOME:home,PATH:'/usr/bin'},
    arch:'arm64',inspect:async()=>ready,run:mock(f,{sign:false})});
  assert.equal(result.status,'FAILED_APK_SIGNATURE');
  assert.equal(fs.existsSync(path.join(downloads,'00-DEVICEAI-INSTALLATION')),false);
});

test('Gradle failure reports only fresh failing JVM tests, never a false build success',async()=>{
  const f=fixture();
  const result=await build({project:f.root,stateDir:f.stateDir,inspect:async()=>ready,run:mock(f,{fail:true,junitFailure:true})});
  assert.equal(result.status,'FAILED_BUILD');
  assert.equal(result.exitCode,1);
  assert.deepEqual(result.failedJvmTests,[{
    className:'at.heinz.deviceai.learning.RetrievalBenchmarkTest',
    testName:'kotlinRankingMatchesCurrentFrozenFixture'
  }]);
  assert.deepEqual(readStatus(f.stateDir).failedJvmTests,result.failedJvmTests);
});

test('JUnit diagnostics ignore stale files and symlinks',()=>{
  const f=fixture();
  const dir=path.join(f.root,'app/build/test-results/testDebugUnitTest');
  fs.mkdirSync(dir,{recursive:true});
  const filename='TEST-example.Case.xml',old=path.join(dir,filename);
  fs.writeFileSync(old,'<testsuite><testcase name="old" classname="Case"><failure>bad</failure></testcase></testsuite>');
  const start=Date.now();
  const past=new Date(start-60_000);fs.utimesSync(old,past,past);
  assert.deepEqual(failedJvmTests(f.root,start),[]);
  const data=path.join(dir,'outside.xml');fs.renameSync(old,data);
  fs.symlinkSync(data,old);
  assert.deepEqual(failedJvmTests(f.root,start),[]);
});

test('a changed HEAD during Gradle blocks APK publication',async()=>{
  const f=fixture();
  const result=await build({project:f.root,stateDir:f.stateDir,inspect:async()=>ready,run:mock(f,{changedHead:true})});
  assert.equal(result.status,'BLOCKED_SOURCE_CHANGED_DURING_BUILD');
  assert.equal(fs.readdirSync(f.stateDir).some(name=>name.endsWith('.apk')),false);
});

test('a dirty checkout produced during Gradle blocks APK publication',async()=>{
  const f=fixture();
  const result=await build({project:f.root,stateDir:f.stateDir,inspect:async()=>ready,run:mock(f,{changedSource:true})});
  assert.equal(result.status,'BLOCKED_SOURCE_CHANGED_DURING_BUILD');
  assert.equal(fs.readdirSync(f.stateDir).some(name=>name.endsWith('.apk')),false);
});

test('signature parser recognizes legacy and modern apksigner certificates only',()=>{
  assert.equal(parseSignerSha256('Signer #1 certificate SHA-256 digest: '+SIGNER),SIGNER);
  assert.equal(parseSignerSha256('V2 Signer: certificate SHA-256 digest: '+SIGNER),SIGNER);
  assert.equal(parseSignerSha256('V3.0 Signer: certificate SHA-256 digest: '+SIGNER),SIGNER);
  assert.equal(parseSignerSha256('Signer (minSdkVersion=24, maxSdkVersion=32) certificate SHA-256 digest: '+SIGNER),SIGNER);
  assert.equal(parseSignerSha256('Signer #1 public key SHA-256 digest: '+SIGNER),null);
  assert.equal(parseSignerSha256('Signer #1 certificate SHA-256 digest: '+'g'.repeat(64)),null);
});

test('modern apksigner result is accepted only after executable verification succeeds',async()=>{
  const f=fixture();
  const result=await build({project:f.root,stateDir:f.stateDir,inspect:async()=>ready,run:mock(f,{signFormat:'modern'})});
  assert.equal(result.status,'EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED');
  assert.equal(result.signerSha256,SIGNER);
  assert.equal(result.packageName,'at.heinz.deviceai.preview');
  assert.equal(result.versionCode,9);
});

test('genuine apksigner rejection persists diagnostic without publishing APK',async()=>{
  const f=fixture();
  const result=await build({project:f.root,stateDir:f.stateDir,inspect:async()=>ready,run:mock(f,{sign:false})});
  assert.equal(result.status,'FAILED_APK_SIGNATURE');
  assert.equal(result.signatureFailure,'SIGNATURE_VERIFICATION_FAILED');
  assert.equal(result.verifierExitCode,1);
  assert.match(result.verifierOutputTail,/DOES NOT VERIFY/);
  assert.equal(fs.readdirSync(f.stateDir).some(name=>name.endsWith('.apk')),false);
});

test('Termux RUN_COMMAND uses pinned on-device Java, Gradle, SDK and ARM64 aapt2 paths',()=>{
  const prefix='/data/data/com.termux/files/usr';
  const home='/data/data/com.termux/files/home';
  const javaHome=prefix+'/lib/jvm/java-17-openjdk';
  const sdk=home+'/android-sdk';
  const gradle=home+'/continuum-toolchains/gradle-8.11.1/bin/gradle';
  const aapt2=prefix+'/bin/aapt2';
  const present=new Set([javaHome+'/bin/java',sdk+'/platforms/android-36/android.jar',gradle,aapt2]);
  const result=resolveTermuxEnvironment({PREFIX:prefix,HOME:home,PATH:'/usr/bin'},'arm64',file=>present.has(file));
  assert.equal(result.JAVA_HOME,javaHome);
  assert.equal(result.ANDROID_HOME,sdk);
  assert.equal(result.ANDROID_SDK_ROOT,sdk);
  assert.equal(result.CONTINUUM_GRADLE_PATH,gradle);
  assert.equal(result.CONTINUUM_AAPT2_PATH,aapt2);
  assert.equal(result.PATH,javaHome+'/bin:'+prefix+'/bin:/usr/bin');
});

test('ambient Termux Java 21 is replaced by installed pinned Java 17 for RUN_COMMAND',()=>{
  const prefix='/data/data/com.termux/files/usr';
  const home='/data/data/com.termux/files/home';
  const java17=prefix+'/lib/jvm/java-17-openjdk';
  const java21=prefix+'/lib/jvm/java-21-openjdk';
  const original={PREFIX:prefix,HOME:home,JAVA_HOME:java21,
    PATH:java21+'/bin:'+prefix+'/bin:'+java17+'/bin'};
  const result=resolveTermuxEnvironment(original,'arm64',file=>file===java17+'/bin/java');
  assert.equal(result.JAVA_HOME,java17);
  assert.equal(result.PATH.split(':')[0],java17+'/bin');
  assert.equal(result.PATH.split(':').filter(dir=>dir===java17+'/bin').length,1);
  assert.equal(result.PATH.split(':').filter(dir=>dir===java21+'/bin').length,1);
  assert.equal(original.JAVA_HOME,java21);
});

test('real Samsung JAVA_HOME with trailing slash selects pinned Java 17',()=>{
  const prefix='/data/data/com.termux/files/usr';
  const home='/data/data/com.termux/files/home';
  const java17=prefix+'/lib/jvm/java-17-openjdk';
  const java21=prefix+'/lib/jvm/java-21-openjdk';
  const ambient={PREFIX:prefix,HOME:home,JAVA_HOME:java21+'/',
    PATH:java21+'/bin:'+prefix+'/bin'};
  const resolved=resolveTermuxEnvironment(ambient,'arm64',
    file=>file===java17+'/bin/java');
  assert.equal(resolved.JAVA_HOME,java17);
  assert.equal(resolved.PATH.split(':')[0],java17+'/bin');
  assert.equal(ambient.JAVA_HOME,java21+'/');
});

test('extra trailing separators do not prevent known Termux Java 21 normalization',()=>{
  const prefix='/data/data/com.termux/files/usr';
  const java17=prefix+'/lib/jvm/java-17-openjdk';
  const java21=prefix+'/lib/jvm/java-21-openjdk';
  const env={PREFIX:prefix,HOME:'/data/data/com.termux/files/home',
    JAVA_HOME:java21+'///',PATH:java21+'/bin'};
  const result=resolveTermuxEnvironment(env,'arm64',
    file=>file===java17+'/bin/java');
  assert.equal(result.JAVA_HOME,java17);
  assert.equal(result.PATH.split(':')[0],java17+'/bin');
});

test('Termux Java 21 remains a visible doctor failure if pinned Java 17 is absent',()=>{
  const prefix='/data/data/com.termux/files/usr';
  const java21=prefix+'/lib/jvm/java-21-openjdk';
  const result=resolveTermuxEnvironment({PREFIX:prefix,HOME:'/data/data/com.termux/files/home',
    JAVA_HOME:java21,PATH:java21+'/bin'},'arm64',()=>false);
  assert.equal(result.JAVA_HOME,java21);
  assert.equal(result.PATH.split(':')[0],java21+'/bin');
});

test('owner Java 17 is always first in PATH even if it appears after Java 21',()=>{
  const prefix='/data/data/com.termux/files/usr';
  const java17=prefix+'/lib/jvm/java-17-openjdk';
  const java21=prefix+'/lib/jvm/java-21-openjdk';
  const result=resolveTermuxEnvironment({PREFIX:prefix,HOME:'/data/data/com.termux/files/home',
    JAVA_HOME:java17,PATH:java21+'/bin:'+java17+'/bin:'+prefix+'/bin'},'arm64',()=>true);
  assert.equal(result.JAVA_HOME,java17);
  assert.equal(result.PATH.split(':')[0],java17+'/bin');
  assert.equal(result.PATH.split(':').filter(dir=>dir===java17+'/bin').length,1);
});

test('missing Termux tools remain unavailable; never synthesize nonexistent binaries',()=>{
  const prefix='/data/data/com.termux/files/usr';
  const home='/data/data/com.termux/files/home';
  const original={PREFIX:prefix,HOME:home,PATH:'/usr/bin'};
  const result=resolveTermuxEnvironment(original,'arm64',()=>false);
  assert.equal(result.JAVA_HOME,undefined);
  assert.equal(result.ANDROID_HOME,undefined);
  assert.equal(result.CONTINUUM_GRADLE_PATH,undefined);
  assert.equal(result.CONTINUUM_AAPT2_PATH,undefined);
  assert.equal(original.JAVA_HOME,undefined);
});

test('explicit owner toolchain settings are preserved, even when defaults exist',()=>{
  const prefix='/data/data/com.termux/files/usr';
  const home='/data/data/com.termux/files/home';
  const custom={PREFIX:prefix,HOME:home,PATH:'/my/bin',JAVA_HOME:'/my/jdk',
    ANDROID_HOME:'/my/sdk',CONTINUUM_GRADLE_PATH:'/my/gradle',CONTINUUM_AAPT2_PATH:'/my/aapt2'};
  const result=resolveTermuxEnvironment(custom,'arm64',()=>true);
  assert.equal(result.JAVA_HOME,'/my/jdk');
  assert.equal(result.ANDROID_HOME,'/my/sdk');
  assert.equal(result.ANDROID_SDK_ROOT,'/my/sdk');
  assert.equal(result.CONTINUUM_GRADLE_PATH,'/my/gradle');
  assert.equal(result.CONTINUUM_AAPT2_PATH,'/my/aapt2');
  assert.ok(result.PATH.includes('/my/bin'));
});

test('non-Termux and non-ARM64 environments do not receive Android paths',()=>{
  const base={PREFIX:'/data/data/com.termux/files/usr',HOME:'/data/data/com.termux/files/home',PATH:'/usr/bin'};
  for(const arch of ['x64','x86'])assert.deepEqual(resolveTermuxEnvironment(base,arch,()=>true),base);
  assert.deepEqual(resolveTermuxEnvironment({PREFIX:'/usr',HOME:'/tmp',PATH:'/usr/bin'},'arm64',()=>true),
    {PREFIX:'/usr',HOME:'/tmp',PATH:'/usr/bin'});
});

test('mobile build forwards resolved environment to doctor and Gradle without a shell',async()=>{
  const f=fixture();
  const prefix='/data/data/com.termux/files/usr';
  const runtime={PREFIX:prefix,HOME:f.root,PATH:'/usr/bin',JAVA_HOME:'/owner/jdk',
    ANDROID_HOME:'/owner/sdk',CONTINUUM_GRADLE_PATH:'/owner/gradle',CONTINUUM_AAPT2_PATH:'/owner/aapt2'};
  const actual=mock(f);
  let observedDoctor=false,observedGradle=false;
  const result=await build({project:f.root,stateDir:f.stateDir,env:runtime,arch:'arm64',
    inspect:async({env})=>{
      observedDoctor=true;
      assert.equal(env.JAVA_HOME,'/owner/jdk');
      assert.equal(env.ANDROID_HOME,'/owner/sdk');
      assert.ok(env.PATH.includes('/owner/jdk/bin'));
      return ready;
    },
    run:async(file,args,options)=>{
      if(file==='/fake/gradle'){
        observedGradle=true;
        assert.equal(options.env.JAVA_HOME,'/owner/jdk');
        assert.equal(options.env.ANDROID_HOME,'/owner/sdk');
        assert.equal(options.env.CONTINUUM_AAPT2_PATH,'/owner/aapt2');
      }
      return actual(file,args,options);
    }});
  assert.equal(observedDoctor,true);
  assert.equal(observedGradle,true);
  assert.equal(result.status,'EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED');
});

test('status CLI returns successful query exit for historical failed or blocked build reports',()=>{
  const f=fixture();
  fs.mkdirSync(f.stateDir,{recursive:true});
  const executable=fileURLToPath(new URL('./continuum-mobile-builder.mjs',import.meta.url));
  for(const status of ['FAILED_BUILD','FAILED_APK_SIGNATURE','BLOCKED_MISSING_TOOLCHAIN',
      'BLOCKED_SOURCE_CHANGED_DURING_BUILD','EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED']){
    const report={schemaVersion:1,status,commit:HEAD};
    const bytes=JSON.stringify(report)+'\n';
    fs.writeFileSync(path.join(f.stateDir,'latest-build.json'),bytes);
    const r=spawnSync(process.execPath,[executable,'status','--state-dir',f.stateDir],
      {encoding:'utf8',timeout:10000});
    assert.equal(r.status,0,'status CLI must not fail when reading '+status+': '+r.stderr);
    assert.equal(JSON.parse(r.stdout).status,status);
    assert.equal(fs.readFileSync(path.join(f.stateDir,'latest-build.json'),'utf8'),bytes,
      'status query must not modify persisted evidence');
  }
});

test('status CLI fails closed for corrupt report schema; missing report is a successful query',()=>{
  const f=fixture();
  const executable=fileURLToPath(new URL('./continuum-mobile-builder.mjs',import.meta.url));
  const query=()=>spawnSync(process.execPath,[executable,'status','--state-dir',f.stateDir],
    {encoding:'utf8',timeout:10000});
  const missing=query();
  assert.equal(missing.status,0);
  assert.equal(JSON.parse(missing.stdout).status,'NO_BUILD_REPORT');
  fs.mkdirSync(f.stateDir,{recursive:true});
  fs.writeFileSync(path.join(f.stateDir,'latest-build.json'),JSON.stringify({schemaVersion:99,status:'FAILED_BUILD'}));
  const corrupt=query();
  assert.notEqual(corrupt.status,0);
  assert.match(corrupt.stderr,/Unsupported report schema/);
});

test('a live ANSK or teamleader lease blocks the mobile APK builder before invoking tools',async()=>{
 const f=fixture();
 const release=acquireContinuumBuildLease(f.root);
 let calls=0;
 try{
  await assert.rejects(build({project:f.root,stateDir:f.stateDir,
   inspect:async()=>{calls++;throw Error('toolchain must not start');}}),
   /CONTINUUM_BUILD_LEASE_BUSY/);
  assert.equal(calls,0);
 }finally{release();}
});

test('a correctly signed APK for the wrong package cannot be published as a Continuum preview', async () => {
  const f=fixture();
  const base=mock(f);
  const run=async(file,args,opts)=>file==='/fake/aapt2'
    ? {ok:true,code:0,output:"package: name='at.heinz.deviceai' versionCode='12' versionName='0.3.9'"}
    : base(file,args,opts);
  const result=await build({project:f.root,stateDir:f.stateDir,inspect:async()=>ready,run});
  assert.equal(result.status,'FAILED_APK_IDENTITY');
  assert.equal(fs.readdirSync(f.stateDir).some(x=>x.endsWith('.apk')),false);
});

test('malformed or failed package inspection cannot publish an APK', async () => {
  for (const response of [{ok:false,code:1,output:''},{ok:true,code:0,output:'UNTRUSTED OUTPUT'}]) {
    const f=fixture();const base=mock(f);
    const result=await build({project:f.root,stateDir:f.stateDir,inspect:async()=>ready,
      run:async(file,args,opts)=>file==='/fake/aapt2'?response:base(file,args,opts)});
    assert.equal(result.status,'FAILED_APK_IDENTITY');
    assert.equal(result.apk,undefined);
  }
});
