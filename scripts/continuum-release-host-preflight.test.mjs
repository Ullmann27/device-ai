import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {evaluateReleaseSource,inspectReleaseCheckout,inspectHostEnvironment} from './continuum-release-host-preflight.mjs';
const SHA='c'.repeat(40);
const source=()=>({gradle:'continuumLocalStandaloneQa applicationIdSuffix = ".qa" "at.heinz.deviceai.continuumlocal" continuumLocalSigningPath.isNullOrBlank()',
  runner:'-PcontinuumLocalStandaloneQa=true -PcontinuumStandaloneVersionCode=27 :app:testDebugUnitTest :app:lintDebug',
  release:'-PcontinuumLocalStandalone=true at.heinz.deviceai.continuumlocal',branch:'research/proof',
  origin:'https://github.com/Ullmann27/device-ai.git',sha:SHA,expectedSha:SHA,dirty:false});
const denied=(patch,reason)=>assert.ok(evaluateReleaseSource({...source(),...patch}).blockers.includes(reason));
test('valid sources grant only permission to attempt the QA build',()=>{
  const x=evaluateReleaseSource(source());assert.equal(x.sourceAdmission,true);assert.deepEqual(x.blockers,[]);
});
test('wrong exact source HEAD fails',()=>denied({expectedSha:'a'.repeat(40)},'EXACT_HEAD_MISMATCH'));
test('dirty checkout fails',()=>denied({dirty:true},'UNCLEAN_CHECKOUT'));
test('main, production and release branch variants fail',()=>{
  for(const branch of ['main','production','release/owner','master','prod/dev'])denied({branch},'PROTECTED_BRANCH');
});
test('untrusted or missing remote fails',()=>denied({origin:'https://example.invalid/device-ai'},'UNTRUSTED_GIT_REMOTE'));
test('old app package fails',()=>denied({gradle:'applicationId="at.heinz.deviceai"'},'STANDALONE_QA_SOURCE_MISSING'));
test('QA without sign exclusion fails',()=>denied({gradle:'continuumLocalStandaloneQa applicationIdSuffix = ".qa" "at.heinz.deviceai.continuumlocal"'},'STANDALONE_QA_SOURCE_MISSING'));
test('runner without actual QA flag fails',()=>denied({runner:'-PdeviceAiMentorPreview=true :app:testDebugUnitTest :app:lintDebug'},'STANDALONE_QA_RUNNER_MISSING'));
test('wrong signing release lane fails',()=>denied({release:'-PdeviceAiMentorPreview=true'},'ORIGINAL_RELEASE_ROUTE_MISSING'));
test('host without JDK17, SDK36, Gradle, AAPT2 is blocked without downloads',()=>{
  const r=inspectHostEnvironment({});assert.equal(r.hostReadyToAttemptQa,false);
  assert.equal(r.androidCompilationExecuted,false);assert.equal(r.apkSigned,false);
  for(const name of ['EXPLICIT_JDK_NOT_FOUND','SDK36_NOT_FOUND','GRADLE8111_NOT_FOUND','NATIVE_AAPT2_NOT_FOUND'])
    assert.ok(r.blockers.includes(name),name);
});
function toolFixture(t){
 const home=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-tool-profile-')),bin=path.join(home,'bin'),sdk=path.join(home,'sdk');
 t.after(()=>fs.rmSync(home,{recursive:true,force:true}));
 fs.mkdirSync(bin);fs.mkdirSync(path.join(sdk,'platforms','android-36'),{recursive:true});
 const jar=path.join(sdk,'platforms','android-36','android.jar'),gradle=path.join(bin,'gradle'),aapt2=path.join(bin,'aapt2');
 fs.writeFileSync(jar,'fixture only');fs.writeFileSync(aapt2,'#!/bin/sh\nexit 0\n',{mode:0o700});
 fs.writeFileSync(path.join(bin,'java'),'#!/bin/sh\nprintf \'openjdk version "17.0.1"\\n\' >&2\n',{mode:0o700});
 fs.writeFileSync(path.join(bin,'javac'),'#!/bin/sh\nprintf \'javac 17.0.1\\n\'\n',{mode:0o700});
 const version=output=>fs.writeFileSync(gradle,"#!/bin/sh\ncat <<'GRADLE_FIXTURE'\n"+output+'\nGRADLE_FIXTURE\n',{mode:0o700});
 version('Gradle 8.11.1');
 const env={JAVA_HOME:home,PATH:process.env.PATH,ANDROID_HOME:sdk,CONTINUUM_GRADLE_PATH:gradle,CONTINUUM_AAPT2_PATH:aapt2};
 return {env,jar,aapt2,version};
}
test('Gradle admission requires one exact stable version line',t=>{
 const f=toolFixture(t);
 for(const output of ['Gradle 8.11.1','-----\r\nGradle 8.11.1\r\n-----']){
  f.version(output);assert.equal(inspectHostEnvironment(f.env).hostReadyToAttemptQa,true,output);
 }
 for(const output of ['Gradle 8.11.10','Gradle 8.11.1-rc-1','Gradle 8.11.1-nightly','Unavailable: Gradle 8.11.1',
  'Gradle 8.11.2\nGradle 8.11.1','Gradle 8.11.1\nGradle 8.11.1']){
  f.version(output);const result=inspectHostEnvironment(f.env);
  assert.equal(result.hostReadyToAttemptQa,false,output);assert.ok(result.blockers.includes('GRADLE8111_INVALID'));
 }
});
test('SDK platform admission requires a nonempty regular file',t=>{
 const f=toolFixture(t);assert.equal(inspectHostEnvironment(f.env).hostReadyToAttemptQa,true);
 fs.unlinkSync(f.jar);fs.mkdirSync(f.jar);
 assert.ok(inspectHostEnvironment(f.env).blockers.includes('SDK36_NOT_FOUND'));
 fs.rmdirSync(f.jar);fs.writeFileSync(f.jar,'');
 assert.ok(inspectHostEnvironment(f.env).blockers.includes('SDK36_NOT_FOUND'));
 fs.unlinkSync(f.jar);fs.symlinkSync(f.jar,f.jar);
 assert.ok(inspectHostEnvironment(f.env).blockers.includes('SDK36_NOT_FOUND'));
});
test('AAPT2 admission requires a nonempty executable regular file',t=>{
 const f=toolFixture(t);assert.equal(inspectHostEnvironment(f.env).hostReadyToAttemptQa,true);
 fs.chmodSync(f.aapt2,0o600);
 assert.ok(inspectHostEnvironment(f.env).blockers.includes('NATIVE_AAPT2_NOT_FOUND'));
 fs.chmodSync(f.aapt2,0o700);fs.writeFileSync(f.aapt2,'');
 assert.ok(inspectHostEnvironment(f.env).blockers.includes('NATIVE_AAPT2_NOT_FOUND'));
 fs.unlinkSync(f.aapt2);fs.mkdirSync(f.aapt2);
 const result=inspectHostEnvironment(f.env);assert.ok(result.blockers.includes('NATIVE_AAPT2_NOT_FOUND'));
 assert.equal(result.androidCompilationExecuted,false);assert.equal(result.androidTestExecuted,false);assert.equal(result.apkSigned,false);
});
test('host with existing incompatible Java detects JDK17 requirement',()=>{
  const home=process.env.JAVA_HOME;
  if(!home || !fs.existsSync(path.join(home,'bin','javac')))return;
  const r=inspectHostEnvironment({JAVA_HOME:home});
  assert.equal(r.hostReadyToAttemptQa,false);
  assert.ok(r.blockers.some(x=>x.startsWith('JDK')||x.startsWith('SDK')));
});
test('host probes receive the explicit environment without mutating process.env',t=>{
  const home=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-preflight-env-'));
  t.after(()=>fs.rmSync(home,{recursive:true,force:true}));
  const bin=path.join(home,'bin'),sdk=path.join(home,'sdk');
  fs.mkdirSync(bin);fs.mkdirSync(path.join(sdk,'platforms','android-36'),{recursive:true});
  fs.writeFileSync(path.join(sdk,'platforms','android-36','android.jar'),'fixture only');
  const guard='#!/bin/sh\n[ -n "$JAVA_HOME" ] && [ "$PATH" = "$JAVA_HOME/bin" ] || exit 71\n';
  for(const [name,version] of [['java','printf \'openjdk version "17.0.1"\\n\' >&2'],
    ['javac','printf \'javac 17.0.1\\n\''],['gradle','printf \'Gradle 8.11.1\\n\'']]){
    fs.writeFileSync(path.join(bin,name),guard+`printf '%s\\n' '${name}' >> "$JAVA_HOME/probes.log"\n`+version+'\n',{mode:0o700});
  }
  const aapt2=path.join(bin,'aapt2');fs.writeFileSync(aapt2,'#!/bin/sh\nexit 0\n',{mode:0o700});
  const env={JAVA_HOME:home,PATH:bin,ANDROID_HOME:sdk,CONTINUUM_GRADLE_PATH:path.join(bin,'gradle'),CONTINUUM_AAPT2_PATH:aapt2};
  const before={...process.env},input={...env},r=inspectHostEnvironment(env);
  assert.deepEqual(r.blockers,[]);assert.equal(r.hostReadyToAttemptQa,true);
  assert.equal(fs.readFileSync(path.join(home,'probes.log'),'utf8'),'java\njavac\ngradle\n');
  assert.equal(r.androidCompilationExecuted,false);assert.equal(r.androidTestExecuted,false);assert.equal(r.apkSigned,false);
  assert.deepEqual({...process.env},before);assert.deepEqual(env,input);
});
function fixture(work){
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-preflight-repo-'));
  const git=(...args)=>{const x=spawnSync('git',args,{cwd:root,encoding:'utf8'});assert.equal(x.status,0,x.stderr);return x.stdout.trim();};
  try {
    const write=(p,content)=>{const dest=path.join(root,p);fs.mkdirSync(path.dirname(dest),{recursive:true});fs.writeFileSync(dest,content);};
    const s=source();write('app/build.gradle.kts',s.gradle);
    write('scripts/continuum-fusion-full-regression.sh',s.runner);
    write('scripts/continuum-fold7-standalone-apk.sh',s.release);
    git('init','-q');git('checkout','-qb','research/synthetic');git('config','user.name','Fixture');
    git('config','user.email','fixture@example.invalid');git('remote','add','origin','https://github.com/Ullmann27/device-ai.git');
    git('add','.');git('commit','-qm','initial public-only fixture');
    return work({root,git,write,head:git('rev-parse','HEAD')});
  } finally {fs.rmSync(root,{recursive:true,force:true});}
}
test('read-only admission accepts a clean synthetic Git checkout with exact SHA',()=>fixture(({root,head})=>{
  const a=inspectReleaseCheckout(root,head);assert.equal(a.sourceAdmission,true);
  assert.equal(a.blockers.length,0);
}));
test('read-only admission reports dirty synthetic checkout',()=>fixture(({root,head,write})=>{
  write('app/build.gradle.kts','changed');
  const a=inspectReleaseCheckout(root,head);assert.ok(a.blockers.includes('UNCLEAN_CHECKOUT'));
}));
test('release source audit refuses fsmonitor before Git status executes it',()=>fixture(({root,git,write})=>{
 const marker=path.join(root,'monitor-ran'),hook=path.join(root,'hooks','audit-monitor.sh');
 write('hooks/audit-monitor.sh','#!/bin/sh\nprintf ran > "'+marker+'"\n');fs.chmodSync(hook,0o700);
 git('add','.');git('commit','-qm','monitor fixture');const head=git('rev-parse','HEAD');
 git('config','core.fsmonitor',hook);
 let error;try{inspectReleaseCheckout(root,head);}catch(value){error=value;}
 assert.equal(fs.existsSync(marker),false,'read-only audit executed fsmonitor');
 assert.match(error?.message??'',/DISCOVERY_FSMONITOR_UNSUPPORTED/);
}));
test('release source audit refuses clean filters without touching their marker',()=>fixture(({root,git,write})=>{
 write('.gitattributes','app/build.gradle.kts filter=audit\n');git('add','.');git('commit','-qm','filter attributes');
 const head=git('rev-parse','HEAD'),marker=path.join(root,'filter-ran');
 git('config','filter.audit.clean',`sh -c 'printf ran > "${marker}"; cat'`);
 const file=path.join(root,'app','build.gradle.kts'),future=new Date(Date.now()+2000);fs.utimesSync(file,future,future);
 let error;try{inspectReleaseCheckout(root,head);}catch(value){error=value;}
 assert.equal(fs.existsSync(marker),false,'read-only audit executed clean filter');
 assert.match(error?.message??'',/DISCOVERY_CLEAN_FILTER_UNSUPPORTED/);
}));
test('release source audit refuses current worktree gitlinks',()=>fixture(({root,git,head})=>{
 git('update-index','--add','--cacheinfo',`160000,${head},vendor`);git('commit','-qm','gitlink fixture');
 assert.throws(()=>inspectReleaseCheckout(root,git('rev-parse','HEAD')),/DISCOVERY_SUBMODULE_WORKTREE_UNSUPPORTED/);
}));
test('refuses symlinked release source',()=>fixture(({root,head})=>{
  const file=path.join(root,'scripts/continuum-fold7-standalone-apk.sh');fs.unlinkSync(file);fs.symlinkSync('/etc/passwd',file);
  assert.throws(()=>inspectReleaseCheckout(root,head),/SOURCE_SYMLINK/);
}));
test('refuses parent directory rather than exact repository root',()=>fixture(({root,head})=>{
  assert.throws(()=>inspectReleaseCheckout(path.dirname(root),head));
}));
