import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {createOwnedApp, verifyOwnedApp, buildOwnedApp, projectPath} from './continuum-owned-app.mjs';
import {mockOwnedReports} from './fixtures/owned-build.mjs';

function fixture(t) {
  const home = fs.mkdtempSync(path.join(os.tmpdir(), 'continuum-m4-security-'));
  t.after(() => fs.rmSync(home, {recursive:true, force:true}));
  createOwnedApp({home});
  return {home, project:projectPath(home)};
}
for (const file of ['buildSrc/build.gradle.kts', 'app/src/debug/AndroidManifest.xml',
  'app/src/main/java/Injected.kt', 'init.gradle', 'app/extra.gradle.kts']) {
  test('unmanifested build input is rejected: '+file, t => {
    const f = fixture(t), target = path.join(f.project, file);
    fs.mkdirSync(path.dirname(target), {recursive:true});
    fs.writeFileSync(target, '// unapproved build input');
    assert.equal(verifyOwnedApp({home:f.home}).status, 'BLOCKED_UNEXPECTED_SOURCE');
  });
}
test('oversized source is rejected before reading it into memory', t => {
  const f = fixture(t), target = path.join(f.project, 'README.md');
  fs.truncateSync(target, 128*1024*1024);
  const read = fs.readFileSync;
  let oversizedReads = 0;
  fs.readFileSync = (file, ...args) => {
    if (file === target) oversizedReads++;
    return read(file, ...args);
  };
  t.after(() => {fs.readFileSync = read;});
  assert.equal(verifyOwnedApp({home:f.home}).status, 'BLOCKED_SOURCE_CHANGED');
  assert.equal(oversizedReads, 0);
});
test('Gradle project cache cannot redirect writes outside the project', async t => {
  const f = fixture(t), outside = path.join(f.home, 'private-owner-data');
  fs.mkdirSync(outside);
  fs.symlinkSync(outside, path.join(f.project, '.gradle'));
  let probes = 0;
  const result = await buildOwnedApp({home:f.home, inspect:async()=>{probes++;return {};}});
  assert.equal(result.status, 'BLOCKED_UNSAFE_BUILD_OUTPUT');
  assert.equal(probes, 0);
  assert.deepEqual(fs.readdirSync(outside), []);
});
test('changing private staged bytes between independent APK checks blocks publication', async t => {
  const f = fixture(t);
  const inspect = async()=>({status:'READY_FOR_EXPERIMENTAL_BUILD',
    bin:{gradle:'/fake/gradle', apksigner:'/fake/apksigner', aapt2:'/fake/aapt2'}});
  const result = await buildOwnedApp({home:f.home, inspect, lease:()=>{}, run:async(file,args)=>{
    if (file==='/fake/gradle') {
      mockOwnedReports(f.project);
      const apk = path.join(f.project, 'app/build/outputs/apk/debug/app-debug.apk');
      fs.mkdirSync(path.dirname(apk), {recursive:true});
      fs.writeFileSync(apk, Buffer.alloc(9000,42));
      return {ok:true, code:0, output:'mock build'};
    }
    if (file==='/fake/apksigner') return {ok:true, code:0,
      output:'Signer #1 certificate SHA-256 digest: '+'a'.repeat(64)};
    fs.writeFileSync(args.at(-1), 'DIFFERENT UNVERIFIED APK');
    return {ok:true, code:0,
      output:"package: name='at.heinz.continuum.m4notes' versionCode='1' versionName='0.1.0'"};
  }});
  assert.equal(result.status, 'FAILED_ARTIFACT_CHANGED');
  const state = path.join(f.home, '.local/state/device-ai-continuum/owned-projects');
  assert.deepEqual(fs.readdirSync(state).filter(n=>n.endsWith('.apk')||n.endsWith('.tmp')), []);
});
test('Gradle exit zero without fresh JUnit and lint evidence never certifies an APK', async t => {
  const f=fixture(t);
  const inspect=async()=>({status:'READY_FOR_EXPERIMENTAL_BUILD',
    bin:{gradle:'/fake/gradle',apksigner:'/fake/apksigner',aapt2:'/fake/aapt2'}});
  const result=await buildOwnedApp({home:f.home,inspect,lease:()=>{},run:async(file)=>{
    if(file==='/fake/gradle') {
      const apk=path.join(f.project,'app/build/outputs/apk/debug/app-debug.apk');
      fs.mkdirSync(path.dirname(apk),{recursive:true});fs.writeFileSync(apk,'APK');
      return {ok:true,code:0,output:'BUILD SUCCESSFUL'};
    }
    return {ok:true,code:0,output:file==='/fake/apksigner'?
      'Signer #1 certificate SHA-256 digest: '+'a'.repeat(64):
      "package: name='at.heinz.continuum.m4notes' versionCode='1' versionName='0.1.0'"};
  }});
  assert.equal(result.status,'FAILED_BUILD_EVIDENCE');
});
test('an already cancelled project build never probes or starts tools', async t=>{
  const f=fixture(t),controller=new AbortController();controller.abort();
  let calls=0;
  const result=await buildOwnedApp({home:f.home,signal:controller.signal,
    inspect:async()=>{calls++;return {};}});
  assert.equal(result.status,'BUILD_CANCELLED');
  assert.equal(calls,0);
});
test('a linked report directory is refused before Gradle can write through it',async t=>{
  const f=fixture(t),outside=path.join(f.home,'owner-data');
  fs.mkdirSync(outside);fs.mkdirSync(path.join(f.project,'app/build'),{recursive:true});
  fs.symlinkSync(outside,path.join(f.project,'app/build/reports'));
  let calls=0;
  const result=await buildOwnedApp({home:f.home,inspect:async()=>{calls++;return {};}});
  assert.equal(result.status,'BLOCKED_UNSAFE_BUILD_OUTPUT');
  assert.equal(calls,0);
  assert.deepEqual(fs.readdirSync(outside),[]);
});
test('unconfirmed process termination retains the shared lease for owner inspection',async t=>{
  const f=fixture(t);let releases=0;
  const inspect=async()=>({status:'READY_FOR_EXPERIMENTAL_BUILD',
    bin:{gradle:'/fake/gradle',apksigner:'/fake/apksigner',aapt2:'/fake/aapt2'}});
  const result=await buildOwnedApp({home:f.home,inspect,lease:()=>{releases++;},
    run:async()=>({ok:false,code:null,output:'termination not confirmed',terminationConfirmed:false})});
  assert.equal(result.status,'BUILD_TERMINATION_UNCONFIRMED');
  assert.equal(releases,0);
});
