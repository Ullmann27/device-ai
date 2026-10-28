#!/usr/bin/env node
/** Read-only, fail-closed host admission for the new Continuum Lokal QA.
 * NEVER attempts a build, signs an APK, installs tools, fetches code or changes git.
 */
import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const ORIGINS=new Set(['https://github.com/Ullmann27/device-ai',
 'https://github.com/Ullmann27/device-ai.git',
 'git@github.com:Ullmann27/device-ai.git',
 'ssh://git@github.com/Ullmann27/device-ai.git']);
const PROTECTED=/^(?:main|master|prod|production|release)(?:$|\/)/i;
const safePath=(root,rel)=>{
  let p=root;
  for(const part of rel.split('/')){
    p=path.join(p,part);
    const stat=fs.lstatSync(p);
    if(stat.isSymbolicLink())throw Error('SOURCE_SYMLINK');
  }
  const st=fs.statSync(p);
  if(!st.isFile()||st.size>150000||st.size===0)throw Error('SOURCE_NOT_REGULAR');
  return fs.readFileSync(p,'utf8');
};
function git(root,args){
  const r=spawnSync('git',args,{cwd:root,encoding:'utf8',timeout:5000,maxBuffer:8192,
    env:{PATH:process.env.PATH||'/usr/bin:/bin',HOME:process.env.HOME||'/tmp',GIT_TERMINAL_PROMPT:'0'},
    shell:false});
  if(r.status!==0||r.error)throw Error('GIT_READ_ONLY_FAILED');
  return r.stdout.trim();
}
export function evaluateReleaseSource({gradle,runner,release,branch,origin,sha,expectedSha,dirty}) {
  const blockers=[];
  if(!/^[a-f0-9]{40}$/.test(expectedSha||'')||sha!==expectedSha)blockers.push('EXACT_HEAD_MISMATCH');
  if(typeof branch!=='string'||!branch||PROTECTED.test(branch))blockers.push('PROTECTED_BRANCH');
  if(!ORIGINS.has(origin))blockers.push('UNTRUSTED_GIT_REMOTE');
  if(dirty!==false)blockers.push('UNCLEAN_CHECKOUT');
  if(typeof gradle!=='string'||!gradle.includes('continuumLocalStandaloneQa')||
    !gradle.includes('applicationIdSuffix = ".qa"')||
    !gradle.includes('"at.heinz.deviceai.continuumlocal"')||
    !gradle.includes('continuumLocalSigningPath.isNullOrBlank()'))blockers.push('STANDALONE_QA_SOURCE_MISSING');
  if(typeof runner!=='string'||!runner.includes('-PcontinuumLocalStandaloneQa=true')||
    !runner.includes('-PcontinuumStandaloneVersionCode=27')||
    !runner.includes(':app:testDebugUnitTest')||!runner.includes(':app:lintDebug'))
    blockers.push('STANDALONE_QA_RUNNER_MISSING');
  if(typeof release!=='string'||!release.includes('-PcontinuumLocalStandalone=true')||
    !release.includes('at.heinz.deviceai.continuumlocal'))blockers.push('ORIGINAL_RELEASE_ROUTE_MISSING');
  return Object.freeze({sourceAdmission:!blockers.length,blockers});
}
export function inspectReleaseCheckout(root,expectedSha) {
  const base=path.resolve(root);
  if(!fs.statSync(base).isDirectory()||fs.lstatSync(base).isSymbolicLink()||
    fs.realpathSync(base)!==base)throw Error('UNSAFE_CHECKOUT_PATH');
  if(path.resolve(git(base,['rev-parse','--show-toplevel']))!==base)
    throw Error('INCORRECT_GIT_ROOT');
  return evaluateReleaseSource({
    gradle:safePath(base,'app/build.gradle.kts'),
    runner:safePath(base,'scripts/continuum-fusion-full-regression.sh'),
    release:safePath(base,'scripts/continuum-fold7-standalone-apk.sh'),
    branch:git(base,['symbolic-ref','--quiet','--short','HEAD']),
    origin:git(base,['remote','get-url','origin']),
    sha:git(base,['rev-parse','HEAD']),expectedSha,
    dirty:git(base,['status','--porcelain','--untracked-files=normal'])!==''
  });
}
export function inspectHostEnvironment(env=process.env) {
  const blockers=[];
  if(!process.versions.node || ![20,22,24].includes(Number(process.versions.node.split('.')[0])))
    blockers.push('UNSUPPORTED_NODE_LTS');
  if(!env.JAVA_HOME||!fs.existsSync(path.join(env.JAVA_HOME,'bin','javac')))
    blockers.push('EXPLICIT_JDK_NOT_FOUND');
  else {
    const java=spawnSync(path.join(env.JAVA_HOME,'bin','java'),['-version'],{encoding:'utf8',timeout:5000});
    const javac=spawnSync(path.join(env.JAVA_HOME,'bin','javac'),['-version'],{encoding:'utf8',timeout:5000});
    if(java.status!==0||!/version "17\./.test(java.stderr)||javac.status!==0||!/javac 17\./.test(javac.stdout+ ' '+javac.stderr))
      blockers.push('JDK17_REQUIRED');
  }
  const sdk=env.ANDROID_HOME || env.ANDROID_SDK_ROOT;
  if(!sdk||!fs.existsSync(path.join(sdk,'platforms','android-36','android.jar')))
    blockers.push('SDK36_NOT_FOUND');
  const gradle=env.CONTINUUM_GRADLE_PATH;
  if(!gradle||!fs.existsSync(gradle))blockers.push('GRADLE8111_NOT_FOUND');
  else {
    const x=spawnSync(gradle,['--version'],{encoding:'utf8',timeout:6000,maxBuffer:20000});
    if(x.status!==0||!x.stdout.includes('Gradle 8.11.1'))blockers.push('GRADLE8111_INVALID');
  }
  const aapt2=env.CONTINUUM_AAPT2_PATH || (sdk?path.join(sdk,'build-tools','35.0.0','aapt2'):null);
  if(!aapt2||!fs.existsSync(aapt2))blockers.push('NATIVE_AAPT2_NOT_FOUND');
  return Object.freeze({hostReadyToAttemptQa:!blockers.length,blockers,
    androidCompilationExecuted:false,androidTestExecuted:false,apkSigned:false});
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
  try{
    if(process.argv.length!==5 || process.argv[2]!=='--audit')throw Error('USAGE');
    const source=inspectReleaseCheckout(process.argv[3],process.argv[4]);
    const host=inspectHostEnvironment();
    const result={schema:'continuum-standalone-qa-host-preflight-v1',source,host,
      readyToAttemptQa:source.sourceAdmission&&host.hostReadyToAttemptQa,
      qaExecuted:false,ownerP12Accessed:false,apkCreated:false};
    process.stdout.write(JSON.stringify(result,null,2)+'\n');
    if(!result.readyToAttemptQa)process.exitCode=69;
  }catch(e){
    const message=/^[A-Z0-9_]+$/.test(e.message||'')?e.message:'HOST_PREFLIGHT_ERROR';
    process.stderr.write('CONTINUUM_QA_HOST_BLOCKED='+message+'\n');process.exitCode=69;
  }
}
