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
test('host with existing incompatible Java detects JDK17 requirement',()=>{
  const home=process.env.JAVA_HOME;
  if(!home || !fs.existsSync(path.join(home,'bin','javac')))return;
  const r=inspectHostEnvironment({JAVA_HOME:home});
  assert.equal(r.hostReadyToAttemptQa,false);
  assert.ok(r.blockers.some(x=>x.startsWith('JDK')||x.startsWith('SDK')));
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
test('refuses symlinked release source',()=>fixture(({root,head})=>{
  const file=path.join(root,'scripts/continuum-fold7-standalone-apk.sh');fs.unlinkSync(file);fs.symlinkSync('/etc/passwd',file);
  assert.throws(()=>inspectReleaseCheckout(root,head),/SOURCE_SYMLINK/);
}));
test('refuses parent directory rather than exact repository root',()=>fixture(({root,head})=>{
  assert.throws(()=>inspectReleaseCheckout(path.dirname(root),head));
}));
