// Trusted container entry point. Never imported by candidate code.
import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
const profile=process.argv[2];
const profiles=new Set(['node-continuum','node-backend','android-compile','android-unit','android-build','kotlin-fixture']);
if(!profiles.has(profile))throw Error('UNKNOWN_ISOLATED_PROFILE');
const env={PATH:'/tools/node/bin:/tools/jdk/bin:/tools/gradle/bin:/usr/bin:/bin',HOME:'/scratch',
  JAVA_HOME:'/tools/jdk',ANDROID_HOME:'/tools/android',ANDROID_SDK_ROOT:'/tools/android',
  GRADLE_USER_HOME:'/scratch/gradle',GRADLE_RO_DEP_CACHE:'/deps/modules-2',
  TMPDIR:'/scratch/tmp',CONTINUUM_REPAIR_DEPTH:'1',LANG:'C.UTF-8'};
fs.mkdirSync('/scratch/tmp',{recursive:true});
let cmd,args,reportDir;
if(profile.startsWith('node-')){
  const backend=profile==='node-backend';
  const dir=backend?'backend/test':'.github/continuum';
  const suffix=backend?'.test.js':'.test.mjs';
  const files=fs.readdirSync('/work/'+dir).filter(f=>/^[A-Za-z0-9_.-]+$/.test(f)&&f.endsWith(suffix));
  if(!files.length)throw Error('NO_ISOLATED_TESTS');
  cmd='/tools/node/bin/node';args=['--test','--test-concurrency=2','--test-reporter=junit',...files.map(f=>dir+'/'+f)];
}else if(profile==='kotlin-fixture'){
  // Fixed compiler/runner harness for genuine Kotlin namespace isolation proof.
  cmd='/bin/sh';args=['/control/kotlin-fixture.sh'];
}else{
  cmd='/tools/gradle/bin/gradle';
  const targets=profile==='android-compile'?[':app:compileDebugUnitTestKotlin']:
    profile==='android-build'?[':app:testDebugUnitTest',':app:lintDebug',':app:assembleDebug']:[':app:testDebugUnitTest'];
  args=['--offline','--no-daemon','--max-workers=2','--console=plain',
    '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m',
    '-Pkotlin.compiler.execution.strategy=in-process','-Pkotlin.incremental=false',
    '-PdeviceAiMentorPreview=true',...targets];
  reportDir='/work/app/build/test-results/testDebugUnitTest';
}
const result=spawnSync(cmd,args,{cwd:'/work',env,encoding:'utf8',timeout:240_000,maxBuffer:4*1024*1024});
const reports=[];
if(profile.startsWith('node-')){
  if(result.stdout?.length>3*1024*1024)throw Error('REPORT_TOO_LARGE');
  reports.push(result.stdout??'');
}else if(reportDir&&fs.existsSync(reportDir)){
  const reportNames=fs.readdirSync(reportDir).filter(x=>/^TEST-[A-Za-z0-9_.$-]+\.xml$/.test(x)).sort();
  if(reportNames.length>400)throw Error('ISOLATED_REPORT_COUNT_EXCEEDED');
  for(const name of reportNames){
    const file=path.join(reportDir,name),stat=fs.lstatSync(file);
    if(!stat.isFile()||stat.isSymbolicLink()||stat.size>2*1024*1024)throw Error('UNSAFE_ISOLATED_REPORT');
    reports.push(fs.readFileSync(file,'utf8'));
  }
}
if(reports.reduce((n,x)=>n+x.length,0)>6*1024*1024)throw Error('REPORTS_TOO_LARGE');
process.stdout.write(JSON.stringify({schema:'continuum-isolated-profile-v1',profile,
  exitCode:result.status,error:result.error?.code??null,signal:result.signal,
  logs:(result.stdout+'\n'+result.stderr).slice(-100_000),reports})+'\n');
