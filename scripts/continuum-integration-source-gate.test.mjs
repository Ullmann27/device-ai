import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {assessContinuumIntegration} from './continuum-integration-source-gate.mjs';
const SCRIPT=fileURLToPath(new URL('./continuum-integration-source-gate.mjs',import.meta.url));
const BRIDGE_DIR=path.dirname(SCRIPT);
const files=['continuum-coding-bridge.mjs','continuum-coding-bridge.test.mjs',
  'continuum-selfhosted-coding.mjs','continuum-selfhosted-coding.test.mjs'];
const gradle='applicationId = if (continuumLocalStandalone) "at.heinz.deviceai.continuumlocal" else "at.heinz.deviceai"\n';
const release='#!/bin/sh\ngradle -PcontinuumLocalStandalone=true\n# at.heinz.deviceai.continuumlocal\n';
const owner='# standalone\ncontinuum-fold7-standalone-apk.sh\nCONTINUUM_OWNER_LOCAL_APPROVED_SHA\n';
const catalog='import com.google.ai.edge.litertlm.ModelInfo\n// Model SHA-256 does not match\n';
function fixture(run){
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-integrated-fixture-'));
  const write=(name,content)=>{
    const target=path.join(root,name);fs.mkdirSync(path.dirname(target),{recursive:true});
    fs.writeFileSync(target,content);return target;
  };
  try{
    for(const file of files) write('scripts/'+file,fs.readFileSync(path.join(BRIDGE_DIR,file)));
    write('app/build.gradle.kts',gradle);
    write('scripts/continuum-fold7-standalone-apk.sh',release);
    write('scripts/continuum-fold7-owner-update-gate.sh',owner);
    write('app/src/main/java/at/heinz/deviceai/localmodel/LocalModelCatalog.kt',catalog);
    return run({root,write});
  }finally{fs.rmSync(root,{recursive:true,force:true});}
}
test('the two inherited bridges have exact tested original Git blobs',()=>fixture(({root})=>{
  const r=assessContinuumIntegration(root);
  assert.equal(r.appId,'at.heinz.deviceai.continuumlocal');
  assert.equal(Object.keys(r.bridgeBlobShas).length,4);
  assert.equal(r.apkInstalled,false);
  assert.equal(r.modelActuallyRunning,false);
  assert.equal(r.fullAndroidRegressionVerified,false);
}));
test('old 0.3.0 root package source is not the new Android app',()=>fixture(({root,write})=>{
  write('app/build.gradle.kts','applicationId = "at.heinz.deviceai"');
  assert.throws(()=>assessContinuumIntegration(root),/MAIN_APP_PACKAGE_IDENTITY_MISSING/);
}));
test('historic mentorpaired release lane is refused',()=>fixture(({root,write})=>{
  write('scripts/continuum-fold7-owner-update-gate.sh','continuum-local-owner-apk.sh');
  assert.throws(()=>assessContinuumIntegration(root),/MAIN_APP_GATE_MISSING_OR_LEGACY/);
}));
test('missing standalone build identity is refused',()=>fixture(({root,write})=>{
  write('scripts/continuum-fold7-standalone-apk.sh','gradle assembleRelease');
  assert.throws(()=>assessContinuumIntegration(root),/MAIN_APP_RELEASE_LANE_MISSING/);
}));
test('a changed bridge byte is not accepted as previously tested code',()=>fixture(({root,write})=>{
  write('scripts/continuum-selfhosted-coding.mjs','console.log("a new untested model adapter")');
  assert.throws(()=>assessContinuumIntegration(root),/BRIDGE_SOURCE_DRIFT_/);
}));
test('symlinked source file cannot escape checkout',()=>fixture(({root})=>{
  const p=path.join(root,'scripts/continuum-coding-bridge.mjs');
  fs.unlinkSync(p);fs.symlinkSync(path.join(BRIDGE_DIR,'continuum-coding-bridge.mjs'),p);
  assert.throws(()=>assessContinuumIntegration(root),/SYMLINK_SOURCE_REFUSED/);
}));
test('LiteRT-LM remains original native model format',()=>fixture(({root,write})=>{
  write('app/src/main/java/at/heinz/deviceai/localmodel/LocalModelCatalog.kt','// fake GGUF model');
  assert.throws(()=>assessContinuumIntegration(root),/ORIGINAL_LITERT_MODEL_CATALOG_MISSING/);
}));
test('read-only CLI fails closed without any path or owner-secret exposure',()=>fixture(({root})=>{
  const r=spawnSync(process.execPath,[SCRIPT,root],{encoding:'utf8',timeout:5000});
  assert.equal(r.status,0,r.stderr);assert.equal(JSON.parse(r.stdout).appId,'at.heinz.deviceai.continuumlocal');
  const bad=spawnSync(process.execPath,[SCRIPT,path.join(root,'does-not-exist')],{encoding:'utf8',timeout:5000});
  assert.equal(bad.status,69);assert.match(bad.stderr,/CONTINUUM_INTEGRATION_BLOCKED=/);
  assert.doesNotMatch(bad.stderr,/does-not-exist/);
}));
