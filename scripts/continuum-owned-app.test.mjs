import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {createOwnedApp, verifyOwnedApp, buildOwnedApp, projectPath, RECIPE} from './continuum-owned-app.mjs';
const fresh=()=>fs.mkdtempSync(path.join(os.tmpdir(),'continuum-m4-test-'));
const ready={status:'READY_FOR_EXPERIMENTAL_BUILD',bin:{gradle:'/fake/gradle',aapt2:'/fake/aapt2',apksigner:'/fake/apksigner',aapt:'/fake/aapt'}};
const inspect=async()=>ready;
const sha=x=>createHash('sha256').update(x).digest('hex');
function fake(home,{badGradle=false,badSigner=false,badPackage=false,mutate=false,missing=false,commands=[]}={}) {
 return async(file,args)=>{
  commands.push([file,args]);
  if(file==='/fake/gradle'){
   if(badGradle)return {ok:false,code:1,output:'failed tests'};
   if(mutate)fs.appendFileSync(path.join(projectPath(home),'app/build.gradle.kts'),'// attack');
   if(!missing){const dir=path.join(projectPath(home),'app/build/outputs/apk/debug');fs.mkdirSync(dir,{recursive:true});fs.writeFileSync(path.join(dir,'app-debug.apk'),Buffer.alloc(9000,42));}
   return {ok:true,code:0,output:'Gradle simulated'};
  }
  if(file==='/fake/apksigner')return {ok:!badSigner,code:badSigner?1:0,output:badSigner?'BAD SIGNER':'Signer #1 certificate SHA-256 digest: '+'a'.repeat(64)};
  if(file==='/fake/aapt')return {ok:true,code:0,output:"package: name='"+(badPackage?'evil.package':RECIPE.packageName)+"' versionCode='1' versionName='0.1.0'"};
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
 for(const task of [':app:testDebugUnitTest',':app:lintDebug',':app:assembleDebug','--offline'])assert.ok(gradle[1].includes(task));
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
 assert.deepEqual(commands.map(x=>x[0]),['/fake/gradle','/fake/apksigner','/fake/aapt']);
});
