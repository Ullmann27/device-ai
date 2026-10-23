import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {createOwnedApp, verifyOwnedApp, buildOwnedApp, readOwnedAppStatus, projectPath, RECIPE} from './continuum-owned-app.mjs';
import {getProjectPlan} from './continuum-project-plan.mjs';
import {verifyOwnedProjectReceipt} from '../.github/continuum/ccc1-verifier.mjs';
import {mockOwnedReports} from './fixtures/owned-build.mjs';
const fresh=()=>fs.mkdtempSync(path.join(os.tmpdir(),'continuum-m4-test-'));
const ready={status:'READY_FOR_EXPERIMENTAL_BUILD',bin:{gradle:'/fake/gradle',aapt2:'/fake/aapt2',apksigner:'/fake/apksigner'}};
const inspect=async()=>ready;
const sha=x=>createHash('sha256').update(x).digest('hex');
function fake(home,{badGradle=false,badSigner=false,badPackage=false,mutate=false,missing=false,commands=[]}={}) {
 return async(file,args)=>{
  commands.push([file,args]);
  if(file==='/fake/gradle'){
   if(badGradle)return {ok:false,code:1,output:'failed tests'};
   mockOwnedReports(projectPath(home));
   if(mutate)fs.appendFileSync(path.join(projectPath(home),'app/build.gradle.kts'),'// attack');
   if(!missing){const dir=path.join(projectPath(home),'app/build/outputs/apk/debug');fs.mkdirSync(dir,{recursive:true});fs.writeFileSync(path.join(dir,'app-debug.apk'),Buffer.alloc(9000,42));}
   return {ok:true,code:0,output:'Gradle simulated'};
  }
  if(file==='/fake/apksigner')return {ok:!badSigner,code:badSigner?1:0,output:badSigner?'BAD SIGNER':'Signer #1 certificate SHA-256 digest: '+'a'.repeat(64)};
  if(file==='/fake/aapt2')return {ok:true,code:0,output:"package: name='"+(badPackage?'evil.package':RECIPE.packageName)+"' versionCode='1' versionName='0.1.0'"};
  throw Error('Unexpected command: '+file);
 };
}
const build=(home,extra={})=>buildOwnedApp({home,inspect,lease:()=>{},...extra});
test('M4 creates an isolated app with two screens and offline local persistence',()=>{
 const home=fresh(),result=createOwnedApp({home});
 assert.equal(result.status,'PROJECT_CREATED_NOT_BUILT');
 assert.equal(result.packageName,RECIPE.packageName);
 assert.equal(result.installed,false);
 assert.equal(verifyOwnedApp({home}).status,'SOURCE_VERIFIED');
 const app=fs.readFileSync(path.join(projectPath(home),'app/src/main/java/at/heinz/continuum/m4notes/MainActivity.kt'),'utf8');
 assert.match(app,/showNotes\(\)/);assert.match(app,/showAbout\(\)/);
 assert.match(app,/getSharedPreferences/);assert.match(app,/\.commit\(\)/);
 assert.match(app,/onSaveInstanceState\(outState: Bundle\)/);
 assert.match(app,/captureDraft\(\)/);
 assert.match(app,/draft \?: prefs\.getString\("note", ""\)/);
 assert.match(app,/if \(persisted\) draft = normalized/);
 const manifest=fs.readFileSync(path.join(projectPath(home),'app/src/main/AndroidManifest.xml'),'utf8');
 assert.doesNotMatch(manifest,/uses-permission|INTERNET|REQUEST_INSTALL_PACKAGES/);
 assert.match(manifest,/android:exported="true"/);
});
test('source manifest binds every created source file to a digest',()=>{
 const home=fresh();createOwnedApp({home});
 const m=JSON.parse(fs.readFileSync(path.join(projectPath(home),'continuum-source-manifest.json'),'utf8'));
 assert.equal(m.schema,'continuum-owned-project');assert.equal(m.recipe.packageName,RECIPE.packageName);
 assert.ok(Object.keys(m.files).length>=8);
 for(const [file,digest]of Object.entries(m.files))assert.equal(sha(fs.readFileSync(path.join(projectPath(home),file))),digest);
});
test('repeat create refuses replacement and does not touch owner notes',()=>{
 const home=fresh();createOwnedApp({home});
 const target=path.join(projectPath(home),'README.md');fs.writeFileSync(target,'OWNER CUSTOM DATA');
 assert.equal(createOwnedApp({home}).status,'BLOCKED_PROJECT_EXISTS');
 assert.equal(fs.readFileSync(target,'utf8'),'OWNER CUSTOM DATA');
});
test('empty project query blocks',()=>{assert.equal(verifyOwnedApp({home:fresh()}).status,'BLOCKED_PROJECT_NOT_FOUND');});
test('corrupted template is never promoted as verified',()=>{
 const home=fresh();createOwnedApp({home});
 fs.appendFileSync(path.join(projectPath(home),'app/build.gradle.kts'),'versionCode=99');
 assert.equal(verifyOwnedApp({home}).status,'BLOCKED_SOURCE_CHANGED');
});
test('removed file is never verified',()=>{
 const home=fresh();createOwnedApp({home});fs.unlinkSync(path.join(projectPath(home),'README.md'));
 assert.equal(verifyOwnedApp({home}).status,'BLOCKED_SOURCE_MISSING');
});
test('symlink source cannot escape trusted project',()=>{
 const home=fresh();createOwnedApp({home});const p=path.join(projectPath(home),'README.md');fs.unlinkSync(p);
 fs.symlinkSync('/etc/passwd',p);
 assert.equal(verifyOwnedApp({home}).status,'BLOCKED_SOURCE_CHANGED');
});
test('external symbolic project destination is never accepted',()=>{
 const home=fresh(),outside=fresh();fs.mkdirSync(path.join(home,'continuum-projects'));
 fs.symlinkSync(outside,path.join(home,'continuum-projects','m4-notes'));
 assert.equal(createOwnedApp({home}).status,'BLOCKED_PROJECT_EXISTS');
 assert.equal(verifyOwnedApp({home}).status,'BLOCKED_UNSAFE_PROJECT');
 assert.deepEqual(fs.readdirSync(outside),[]);
});
test('symbolic home or parent is rejected',()=>{
 const home=fresh(),link=home+'-link';fs.symlinkSync(home,link);
 assert.throws(()=>createOwnedApp({home:link}),/UNSAFE_HOME/);
 fs.symlinkSync(fresh(),path.join(home,'continuum-projects'));
 assert.throws(()=>createOwnedApp({home}),/UNSAFE_PROJECT_PARENT/);
});
test('relative paths are forbidden',()=>{assert.throws(()=>projectPath('../foo'),/INVALID_HOME/);});
test('build reports unavailable when recipe not created',async()=>{assert.equal((await build(fresh())).status,'BLOCKED_PROJECT_NOT_FOUND');});
test('toolchain unavailable cannot call build tool',async()=>{
 const home=fresh();createOwnedApp({home});let calls=0;
 const result=await build(home,{inspect:async()=>({status:'UNAVAILABLE'}),run:async()=>{calls++;throw Error('must not run')}});
 assert.equal(result.status,'BLOCKED_MISSING_TOOLCHAIN');assert.equal(calls,0);
});
test('failing Gradle tests prevent any APK claim',async()=>{
 const home=fresh();createOwnedApp({home});const result=await build(home,{run:fake(home,{badGradle:true})});
 assert.equal(result.status,'FAILED_BUILD');
});
test('a disappearing APK is never reported successful',async()=>{
 const home=fresh();createOwnedApp({home});
 assert.equal((await build(home,{run:fake(home,{missing:true})})).status,'FAILED_NO_APK');
});
test('false signature is rejected',async()=>{
 const home=fresh();createOwnedApp({home});
 assert.equal((await build(home,{run:fake(home,{badSigner:true})})).status,'FAILED_APK_SIGNATURE');
});
test('foreign package is rejected even if signed',async()=>{
 const home=fresh();createOwnedApp({home});
 assert.equal((await build(home,{run:fake(home,{badPackage:true})})).status,'FAILED_APK_IDENTITY');
});
test('source change during Gradle is blocked and not saved',async()=>{
 const home=fresh();createOwnedApp({home});
 assert.equal((await build(home,{run:fake(home,{mutate:true})})).status,'BLOCKED_SOURCE_CHANGED_DURING_BUILD');
});
test('successful mock Gradle, real SHA verification and signed receipt - not device success',async()=>{
 const home=fresh();createOwnedApp({home});const commands=[];
 const result=await build(home,{run:fake(home,{commands})});
 assert.equal(result.status,'EXPERIMENTAL_PROJECT_APK_BUILT_NOT_DEVICE_TESTED');
 assert.equal(result.packageName,RECIPE.packageName);assert.equal(result.installed,false);
 assert.equal(result.productionReady,false);assert.match(result.apkSha256,/^[0-9a-f]{64}$/);
 assert.equal(result.apkSha256,sha(fs.readFileSync(result.artifact)));
 const gradle=commands.find(x=>x[0]==='/fake/gradle');
 for(const task of [':app:testDebugUnitTest',':app:lintDebug',':app:assembleDebug','--offline','-Pkotlin.compiler.execution.strategy=in-process'])assert.ok(gradle[1].includes(task));
 assert.equal(verifyOwnedApp({home}).status,'SOURCE_VERIFIED');
});
test('corrupt preexisting artifact blocks overwrites',async()=>{
 const home=fresh();createOwnedApp({home});const bytes=Buffer.alloc(9000,42),digest=sha(bytes);
 const state=path.join(home,'.local/state/device-ai-continuum/owned-projects');fs.mkdirSync(state,{recursive:true});
 const dest=path.join(state,'Continuum-M4-Notes-'+digest.slice(0,16)+'.apk');fs.writeFileSync(dest,'NOT_OWNER_APK');
 const result=await build(home,{run:fake(home)});
 assert.equal(result.status,'FAILED_ARTIFACT_COPY');assert.equal(fs.readFileSync(dest,'utf8'),'NOT_OWNER_APK');
});
test('build never starts a package installer, app signature and source are validated',async()=>{
 const home=fresh();createOwnedApp({home});const commands=[];
 await build(home,{run:fake(home,{commands})});
 assert.deepEqual(commands.map(x=>x[0]),['/fake/gradle','/fake/apksigner','/fake/aapt2']);
});
test('symlinked existing project parent is refused before source reading',()=>{
 const home=fresh();createOwnedApp({home});
 const parent=path.join(home,'continuum-projects'), moved=path.join(home,'saved-projects');
 fs.renameSync(parent,moved);fs.symlinkSync(moved,parent);
 assert.equal(verifyOwnedApp({home}).status,'BLOCKED_UNSAFE_PROJECT_PARENT');
});
test('symlinked nested source ancestors are rejected before build',()=>{
 const home=fresh();createOwnedApp({home});
 const parent=path.join(projectPath(home),'app/src/main');
 fs.renameSync(parent,parent+'-moved');fs.symlinkSync(parent+'-moved',parent);
 assert.equal(verifyOwnedApp({home}).status,'BLOCKED_UNSAFE_SOURCE_PATH');
});
test('symbolic state parent never receives an APK artifact',async()=>{
 const home=fresh(),outside=fresh();createOwnedApp({home});
 fs.symlinkSync(outside,path.join(home,'.local'));
 const result=await build(home,{run:fake(home)});
 assert.equal(result.status,'BLOCKED_UNSAFE_STATE');
 assert.deepEqual(fs.readdirSync(outside),[]);
});

test('M4 refuses a symlinked Gradle build output before running Gradle',async()=>{
 const home=fresh(), outside=fresh(); createOwnedApp({home});
 const buildDir=path.join(projectPath(home),'app/build');
 fs.symlinkSync(outside,buildDir);
 const commands=[];
 const result=await build(home,{run:fake(home,{commands})});
 assert.equal(result.status,'BLOCKED_UNSAFE_BUILD_OUTPUT');
 assert.deepEqual(commands,[]);
 assert.deepEqual(fs.readdirSync(outside),[]);
});
test('M4 refuses a symlinked nested APK output before running Gradle',async()=>{
 const home=fresh(), outside=fresh(); createOwnedApp({home});
 const buildDir=path.join(projectPath(home),'app/build/outputs/apk');
 fs.mkdirSync(path.dirname(buildDir),{recursive:true});
 fs.symlinkSync(outside,buildDir);
 const commands=[];
 const result=await build(home,{run:fake(home,{commands})});
 assert.equal(result.status,'BLOCKED_UNSAFE_BUILD_OUTPUT');
 assert.deepEqual(commands,[]);
 assert.deepEqual(fs.readdirSync(outside),[]);
});
test('M4 refuses a redirected output appearing during a Gradle run',async()=>{
 const home=fresh(), outside=fresh(); createOwnedApp({home});
 const commands=[];
 const base=fake(home,{commands});
 const run=async(file,args,options)=>{
   if(file==='/fake/gradle'){
     const dir=path.join(projectPath(home),'app/build/outputs');
     fs.mkdirSync(path.dirname(dir),{recursive:true});
     fs.symlinkSync(outside,dir);
     return {ok:true,code:0,output:'Gradle mocked'};
   }
   return base(file,args,options);
 };
 const result=await build(home,{run});
 assert.equal(result.status,'BLOCKED_UNSAFE_BUILD_OUTPUT');
 assert.deepEqual(commands,[]);
 assert.deepEqual(fs.readdirSync(outside),[]);
});

test('M4 identity checker reads exactly the same private staged APK as apksigner',async()=>{
 const home=fresh();createOwnedApp({home});const commands=[];
 const result=await build(home,{run:fake(home,{commands})});
 assert.equal(result.status,'EXPERIMENTAL_PROJECT_APK_BUILT_NOT_DEVICE_TESTED');
 const signer=commands.find(x=>x[0]==='/fake/apksigner');
 const identity=commands.find(x=>x[0]==='/fake/aapt2');
 assert.ok(signer&&identity);
 const staged=signer[1].at(-1);
 assert.equal(staged,identity[1].at(-1));
 assert.notEqual(staged,path.join(projectPath(home),'app/build/outputs/apk/debug/app-debug.apk'));
 assert.ok(staged.startsWith(path.join(home,'.local/state/device-ai-continuum/owned-projects')+path.sep));
 assert.ok(!fs.existsSync(staged));
 assert.equal(sha(fs.readFileSync(result.artifact)),result.apkSha256);
});
test('changing the original Gradle APK after staged signature check cannot alter published bytes',async()=>{
 const home=fresh();createOwnedApp({home});
 const initial=Buffer.alloc(9000,42),modified=Buffer.alloc(9000,17);
 const original=path.join(projectPath(home),'app/build/outputs/apk/debug/app-debug.apk');
 const base=fake(home);
 const run=async(file,args,opts)=>{
  const reply=await base(file,args,opts);
  if(file==='/fake/apksigner')fs.writeFileSync(original,modified);
  return reply;
 };
 const result=await build(home,{run});
 assert.equal(result.status,'EXPERIMENTAL_PROJECT_APK_BUILT_NOT_DEVICE_TESTED');
 assert.equal(result.apkSha256,sha(initial));
 assert.equal(sha(fs.readFileSync(result.artifact)),sha(initial));
 assert.equal(sha(fs.readFileSync(original)),sha(modified));
});
test('failed signature validation leaves no private verification-stage residue',async()=>{
 const home=fresh();createOwnedApp({home});
 const result=await build(home,{run:fake(home,{badSigner:true})});
 assert.equal(result.status,'FAILED_APK_SIGNATURE');
 const state=path.join(home,'.local/state/device-ai-continuum/owned-projects');
 assert.deepEqual(fs.readdirSync(state).filter(n=>n.endsWith('.tmp')||n.endsWith('.apk')),[]);
 const status=readOwnedAppStatus({home});
 assert.equal(status.status,'PROJECT_BUILD_STATUS');
 assert.equal(status.buildStatus,'FAILED_APK_SIGNATURE');
 assert.equal(status.verification,'INCONCLUSIVE');
 assert.equal(status.artifact,undefined);
});
test('lost callback can recover a verified historical receipt but never changed APK bytes',async()=>{
 const home=fresh();createOwnedApp({home});
 assert.equal(readOwnedAppStatus({home}).status,'NO_PROJECT_BUILD_REPORT');
 const commands=[],result=await build(home,{run:fake(home,{commands})});
 const recovered=readOwnedAppStatus({home});
 assert.equal(recovered.verification,'CONFIRMED');
 assert.equal(recovered.apkSha256,result.apkSha256);
 assert.equal(commands.length,3,'status never executes tools or repeats a build');
 fs.writeFileSync(result.artifact,'changed APK');
 const changed=readOwnedAppStatus({home});
 assert.equal(changed.verification,'INCONCLUSIVE');
 assert.equal(changed.artifact,undefined);
});
test('CCC1 independently rejects forged plan, source, execution, evidence and device claims',async()=>{
 const home=fresh();createOwnedApp({home});
 const result=await build(home,{run:fake(home)});
 const binding={plan:getProjectPlan(),sourceSha256:result.sourceSha256,project:result.project,
  artifactRoot:path.dirname(result.artifact)};
 assert.equal(verifyOwnedProjectReceipt(result,binding).verdict,'CONFIRMED');
 for(const mutate of [
  r=>{r.planSha256='f'.repeat(64);},r=>{r.sourceSha256='f'.repeat(64);},
  r=>{r.packageName='at.heinz.deviceai.mentorpaired';},r=>{r.deviceVerified=true;},
  r=>{r.installed=true;},r=>{r.gradle.timedOut=true;},r=>{r.gradle.tasks=['exec'];},
  r=>{r.evidence.unit.file='../owner.xml';},r=>{r.evidence.lint.sha256='f'.repeat(64);},
  r=>{r.artifact='/etc/passwd';},r=>{r.finishedAt=r.startedAt-1;},r=>{r.toolchainProfile='unknown';}
 ]) {
  const changed=structuredClone(result);mutate(changed);
  assert.equal(verifyOwnedProjectReceipt(changed,binding).verdict,'INCONCLUSIVE');
 }
});
