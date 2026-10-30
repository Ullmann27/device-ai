import test from 'node:test';
import assert from 'node:assert/strict';
import {checkHostEvidence} from './continuum-host-evidence-gate.mjs';
const sha='e06e17a4e8ba12a1dce3d5308e5eb455cda30b6d';
function evidence() {
 const counts={tests:1268,failures:0,errors:0,skipped:0};
 const full={commit:sha,status:'PASS',counts,suites:240,
  gradleExit:0,nodeExit:0,stale:false};
 return {expectedSha:sha,full,manifest:{commit:sha,
  files:Object.fromEntries(Array.from({length:11},(_,i)=>['path'+i,'a'.repeat(64)]))},
  qa:{testedCode:sha,android:{...full,executedOnDevice:false,
   modelInferenceVerified:false,qualityImprovementProven:false},
   workerNode:{pass:390,fail:0,skip:0,exit:0},
   canonicalNode:{pass:226,fail:0,skip:0,exit:0},
   python:{pass:10,fail:0,exit:0},
   notRun:['Fold7','native Gemma inference','instrumentation','APK/build/sign/install']}};
}
test('host evidence never grants device, native Gemma, APK or production merge',()=>{
 const r=checkHostEvidence(evidence());
 assert.equal(r.hostEvidence,'CONSISTENT_WITH_RECORDED_QA');
 assert.equal(r.deviceAcceptance,'NOT_RUN');
 assert.equal(r.productionMergeAuthorized,false);
 assert.equal(r.intelligentImprovementProven,false);
});
test('source mismatch, incomplete Android suite or missing test evidence rejects',()=>{
 const e=evidence();e.full.commit='b'.repeat(40);
 assert.throws(()=>checkHostEvidence(e),/SOURCE_IDENTITY/);
 const f=evidence();f.qa.android.counts={...f.qa.android.counts,failures:1};
 assert.throws(()=>checkHostEvidence(f),/ANDROID_FAILURES/);
 const g=evidence();g.qa.workerNode.pass=389;
 assert.throws(()=>checkHostEvidence(g),/SECONDARY_QA/);
});
test('absence of on-device testing cannot be relabelled as proven',()=>{
 const e=evidence();e.qa.android.executedOnDevice=true;
 assert.throws(()=>checkHostEvidence(e),/DEVICE_OR_INTELLIGENCE/);
 const f=evidence();f.qa.notRun=[];
 assert.throws(()=>checkHostEvidence(f),/MISSING_PENDING/);
});

// M2 regression (Opus, 2026-10-30): the CLI path read the three summary files with plain readFileSync BEFORE the
// no-follow/nonblocking integrity check, so a FIFO hung the gate forever and a symlink was followed. The gate must
// verify first and parse exactly the verified bytes.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {createHash} from 'node:crypto';
const hash=x=>createHash('sha256').update(x).digest('hex');
function evidenceDir(){
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-host-gate-'));
 fs.mkdirSync(path.join(root,'final-qa'));
 const e=evidence(),docs={'qa-summary.json':e.qa,'final-qa/summary.json':e.full,'tested-source-manifest.json':e.manifest};
 const manifest={};
 for(const [name,value] of Object.entries(docs)){
  const data=JSON.stringify(value);fs.writeFileSync(path.join(root,name),data);manifest[name]=hash(data);
 }
 fs.writeFileSync(path.join(root,'sha256-manifest.json'),JSON.stringify(manifest));
 return root;
}
const gate=new URL('./continuum-host-evidence-gate.mjs',import.meta.url).pathname;
const probe=(root,ms=4000)=>spawnSync(process.execPath,['--input-type=module','-e',
 `import {readHostEvidence} from ${JSON.stringify(gate)};
  try{const r=readHostEvidence(${JSON.stringify(root)},${JSON.stringify(sha)},{minFiles:3});console.log('OK '+r.hostEvidence);}
  catch(e){console.log('REJECTED '+e.message);}`],{encoding:'utf8',timeout:ms});
test('the gate reads only verified bytes: consistent evidence passes with three files',()=>{
 const root=evidenceDir();try{
  const r=probe(root);
  assert.equal(r.status,0,r.stderr);assert.match(r.stdout,/^OK CONSISTENT_WITH_RECORDED_QA/);
 }finally{fs.rmSync(root,{recursive:true,force:true});}
});
test('a FIFO summary is rejected without hanging the gate',t=>{
 const root=evidenceDir();try{
  fs.rmSync(path.join(root,'qa-summary.json'));
  if(spawnSync('mkfifo',[path.join(root,'qa-summary.json')],{timeout:1500}).status!==0)return t.skip('mkfifo unavailable');
  const r=probe(root);
  assert.equal(r.signal,null,'gate hung on a FIFO');
  assert.match(r.stdout,/^REJECTED /);
 }finally{fs.rmSync(root,{recursive:true,force:true});}
});
test('a symlinked summary is rejected even when the target has the manifest hash',()=>{
 const root=evidenceDir(),outside=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-outside-'));try{
  const target=path.join(outside,'qa.json');
  fs.copyFileSync(path.join(root,'qa-summary.json'),target);
  fs.rmSync(path.join(root,'qa-summary.json'));fs.symlinkSync(target,path.join(root,'qa-summary.json'));
  const r=probe(root);
  assert.match(r.stdout,/^REJECTED /);
 }finally{fs.rmSync(root,{recursive:true,force:true});fs.rmSync(outside,{recursive:true,force:true});}
});
