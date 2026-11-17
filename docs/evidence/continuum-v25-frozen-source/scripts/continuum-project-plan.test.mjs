import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {createOwnedApp,verifyOwnedApp} from './continuum-owned-app.mjs';
import {doctor} from './continuum-mobile-builder.mjs';
import {getProjectPlan,projectPlanProblems,projectPlanSha256} from './continuum-project-plan.mjs';

function home(t) {
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-plan-'));
  t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
  return dir;
}
test('counter plan creates a distinct private Android project and sources',t=>{
  const h=home(t), created=createOwnedApp({home:h,recipeId:'m4-counter'});
  assert.equal(created.packageName,'at.heinz.continuum.m4counter');
  assert.equal(created.project,path.join(h,'continuum-projects/m4-counter'));
  assert.equal(verifyOwnedApp({home:h,recipeId:'m4-counter'}).status,'SOURCE_VERIFIED');
  assert.equal(fs.existsSync(path.join(h,'continuum-projects/m4-notes')),false);
  const manifest=JSON.parse(fs.readFileSync(path.join(created.project,'continuum-source-manifest.json')));
  assert.equal(manifest.version,2);
  assert.match(manifest.planSha256,/^[a-f0-9]{64}$/);
  const p=JSON.parse(fs.readFileSync(path.join(created.project,'continuum-project-plan.json')));
  assert.equal(p.schema,'continuum-project-plan');
  assert.equal(p.version,1);
  assert.deepEqual(p.permissions,[]);
  assert.equal(p.storage.kind,'private-preferences');
  assert.equal(p.screens[0].kind,'bounded-counter');
  assert.equal(p.build.network,'offline');
});
test('unknown or traversal project plans never create any project',t=>{
  const h=home(t);
  for(const recipeId of ['../device-ai','m4-unknown','m4-notes; id','at.heinz.deviceai.mentorpaired','']) {
    assert.throws(()=>createOwnedApp({home:h,recipeId}),/INVALID_PROJECT_PLAN/);
  }
  assert.deepEqual(fs.readdirSync(h),[]);
});
test('notes and counter plans coexist without replacement or data crossover',t=>{
  const h=home(t), notes=createOwnedApp({home:h});
  const count=createOwnedApp({home:h,recipeId:'m4-counter'});
  assert.equal(count.status,'PROJECT_CREATED_NOT_BUILT');
  assert.notEqual(notes.project,count.project);
  assert.notEqual(notes.sourceSha256,count.sourceSha256);
  assert.equal(createOwnedApp({home:h,recipeId:'m4-counter'}).status,'BLOCKED_PROJECT_EXISTS');
  assert.equal(verifyOwnedApp({home:h}).status,'SOURCE_VERIFIED');
});
test('host SDK profile is explicit and does not imply a Termux toolchain',async t=>{
  const h=home(t);
  const sdk=path.join(h,'sdk');fs.mkdirSync(path.join(sdk,'platforms/android-36'),{recursive:true});
  fs.writeFileSync(path.join(sdk,'platforms/android-36/android.jar'),'fixture');
  const run=async(file,args)=>({ok:true,code:0,output:args[0]==='-version'?
    'openjdk version "17.0.21"':args[0]==='--version'?'Gradle 8.11.1':'tool 1.0'});
  const env={PATH:'/usr/bin',ANDROID_HOME:sdk};
  const defaultCheck=await doctor({env,arch:'x64',run});
  assert.equal(defaultCheck.status,'UNAVAILABLE');
  const host=await doctor({env,arch:'x64',profile:'host-sdk',run});
  assert.equal(host.status,'READY_FOR_EXPERIMENTAL_BUILD');
  assert.equal(host.profile,'host-sdk');
  assert.equal(host.checks.termuxArm64,undefined);
  assert.equal(host.executionHost.arch,'x64');
  assert.equal((await doctor({env,arch:'x64',profile:'invented',run})).status,'UNAVAILABLE');
});
test('plan permissions, versions, budgets, routes and test contracts cannot be loosened',()=>{
  const plan=getProjectPlan();
  assert.deepEqual(projectPlanProblems(plan),[]);
  for(const mutate of [
    p=>{p.version=2;}, p=>{p.shell='id';}, p=>{p.permissions=['android.permission.INTERNET'];},
    p=>{p.app.packageName='at.heinz.deviceai.mentorpaired';}, p=>{p.app.minSdk=1;},
    p=>{p.navigation.links[0].to='missing';}, p=>{p.tests.cases=[];},
    p=>{p.limits.maxBuildMillis=Infinity;}, p=>{p.build.artifact='../../owner.apk';},
    p=>{p.build.tasks=['exec'];}, p=>{p.resources.external=['/private/weights'];}
  ]) {
    const changed=structuredClone(plan);mutate(changed);
    assert.notDeepEqual(projectPlanProblems(changed),[]);
    assert.throws(()=>projectPlanSha256(changed),/INVALID_PROJECT_PLAN/);
  }
  assert.throws(()=>{plan.permissions.push('network');},TypeError);
  assert.notEqual(projectPlanSha256(plan),projectPlanSha256(getProjectPlan('m4-counter')));
});
