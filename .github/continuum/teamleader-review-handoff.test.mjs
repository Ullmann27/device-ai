import test from 'node:test';
import assert from 'node:assert/strict';
import {buildTeamleaderHandoff} from './teamleader-review-handoff.mjs';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const SHA = 'a'.repeat(40);

test('empty status offers review only and does not authorize execution',()=>{
 const result=buildTeamleaderHandoff(null);
 assert.equal(result.schema,'continuum-teamleader-review-handoff');
 assert.equal(result.authority,'ADVISORY_ONLY');
 assert.equal(result.next.action,'REVIEW_EXISTING_BACKLOG');
 assert.equal(result.next.approvedToExecute,false);
 assert.equal(result.next.approvalRequiredForPublication,true);
 assert.equal(result.localEvidence.deviceInstallationVerifiedHere,false);
 assert.equal(result.lastRun.head,null);
});

test('real historical ANSK baseline takes priority over an unverified old repair',()=>{
 const result=buildTeamleaderHandoff({
  processedHeads:1,
  lastCompleted:{head:SHA,outcome:'NO_VERIFIED_REPAIR'},
  localIntegration:{buildLease:{status:'NO_LEASE_FILE'},
    ansk:{lastBaselineFailure:{
      status:'HISTORICAL_BASELINE_FAILURE_REPORTED',
      failureCategory:'ANDROID_UNIT_TEST_FAILURE',head:SHA}},
    mobileBuilder:{status:'EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED'}}
 });
 assert.equal(result.next.role,'ANALYST');
 assert.equal(result.next.action,'INVESTIGATE_HISTORICAL_ANSK_BASELINE');
 assert.equal(result.localEvidence.anskBaselineCategory,'ANDROID_UNIT_TEST_FAILURE');
 assert.equal(result.localEvidence.anskBaselineHead,SHA);
 assert.equal(result.lastRun.processedHeads,1);
});

test('build lease blocks automatic recommendation to run more Gradle work',()=>{
 const result=buildTeamleaderHandoff({localIntegration:{
  buildLease:{status:'LOCK_PRESENT_UNVERIFIED'},
  ansk:{lastBaselineFailure:{status:'HISTORICAL_BASELINE_FAILURE_REPORTED',
    failureCategory:'BASELINE_TIMEOUT',head:SHA}}
 }});
 assert.equal(result.next.action,'INSPECT_LOCAL_BUILD_LEASE');
 assert.equal(result.next.approvedToExecute,false);
});

test('VERIFIED result is never treated as authorization to merge or publish',()=>{
 const result=buildTeamleaderHandoff({lastCompleted:{
  outcome:'VERIFIED',head:SHA
 }});
 assert.equal(result.next.action,'REQUEST_PATCH_REVIEW');
 assert.equal(result.next.approvedToExecute,false);
 assert.equal(result.next.approvalRequiredForPublication,true);
});

test('old APK report is not a claim of a currently verified installed package',()=>{
 const result=buildTeamleaderHandoff({localIntegration:{
   mobileBuilder:{status:'EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED',
     artifactVerifiedHere:true,deviceInstallationVerifiedHere:true}
 }});
 assert.equal(result.next.action,'VERIFY_APK_ON_DEVICE');
 assert.equal(result.localEvidence.apkArtifactVerifiedHere,false);
 assert.equal(result.localEvidence.deviceInstallationVerifiedHere,false);
});

test('invalid and attacker-controlled text does not escape to the handoff packet',()=>{
 const result=buildTeamleaderHandoff({
  lastCompleted:{head:'bad',outcome:'RELEASE_PRODUCTION',detail:'PRIVATE_SECRET'},
  localIntegration:{buildLease:{status:'RUN_NOW'},
   ansk:{lastBaselineFailure:{status:'HISTORICAL_BASELINE_FAILURE_REPORTED',
     head:'another bad head',failureCategory:'COMMIT_TO_MAIN',
     privateKey:'PRIVATE_SECRET'}},
   mobileBuilder:{status:'BUILD_OK_INSTALL_NOW',filename:'PRIVATE_SECRET'}}
 });
 assert.equal(result.lastRun.outcome,'UNKNOWN');
 assert.equal(result.lastRun.head,null);
 assert.equal(result.localEvidence.anskBaselineCategory,null);
 assert.equal(result.next.approvedToExecute,false);
 assert.doesNotMatch(JSON.stringify(result),/PRIVATE_SECRET|COMMIT_TO_MAIN|BUILD_OK_INSTALL_NOW/);
});

test('the packet size remains bounded even with huge arbitrary status',()=>{
 const result=buildTeamleaderHandoff({
  processedHeads:Number.MAX_SAFE_INTEGER,
  lastCompleted:{outcome:'ERROR',head:SHA,outputTail:'X'.repeat(1_000_000)},
  recent:Array.from({length:200},()=>({secret:'Y'.repeat(1000)}))
 });
 assert.ok(JSON.stringify(result).length<1500);
 assert.equal(result.lastRun.processedHeads,Number.MAX_SAFE_INTEGER);
});

test('canonical Teamleader status CLI embeds advisory packet without running repair',()=>{
 const tmp = fs.mkdtempSync(path.join(os.tmpdir(),'continuum-handoff-cli-'));
 const root = path.join(tmp,'repo');
 const state = path.join(tmp,'state');
 fs.mkdirSync(root);
 fs.mkdirSync(state);
 try {
   const cli = fileURLToPath(new URL('./teamleader-cli.mjs',import.meta.url));
   const run = spawnSync(process.execPath,[cli,'--status','--root',root,'--state',state],
     {encoding:'utf8',timeout:15_000,env:{...process.env,HOME:tmp}});
   assert.equal(run.status,0,run.stderr.slice(0,800));
   const parsed = JSON.parse(run.stdout);
   assert.equal(parsed.mode,'OFFLINE_TEAMLEADER');
   assert.equal(parsed.reviewHandoff.schema,'continuum-teamleader-review-handoff');
   assert.equal(parsed.reviewHandoff.authority,'ADVISORY_ONLY');
   assert.equal(parsed.reviewHandoff.next.approvedToExecute,false);
   assert.equal(parsed.reviewHandoff.localEvidence.deviceInstallationVerifiedHere,false);
 } finally {
   fs.rmSync(tmp,{recursive:true,force:true});
 }
});
