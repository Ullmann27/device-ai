import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {gateSummary,parseGateArgs,runM4Gate,verifyGateReport,verifyReleaseProofFile} from './m4-local-gate.mjs';

function fixture(broken=false){
 const base=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-gate-fixture-'));
 const root=path.join(base,'repo'),out=path.join(base,'private-proof');
 fs.mkdirSync(path.join(root,'.github/continuum'),{recursive:true});
 const source=path.join(root,'.github/continuum','math.test.mjs');
 fs.writeFileSync(source,[
  "import test from 'node:test';",
  "import assert from 'node:assert/strict';",
  "test('real local gate fixture',()=>assert.equal(2*3,"+(broken?'7':'6')+"));",
  ""
 ].join('\n'));
 const git=(...args)=>{
  const r=spawnSync('git',args,{cwd:root,encoding:'utf8',timeout:15000});
  assert.equal(r.status,0,r.stderr);
  return r.stdout.trim();
 };
 git('init','-q');
 git('add','.');
 git('-c','user.name=fixture','-c','user.email=fixture@example.invalid',
  'commit','-qm','fixed fixture');
 return {base,root,out,git,head:git('rev-parse','HEAD')};
}
const nested=Boolean(process.env.CONTINUUM_REPAIR_DEPTH);

test('gate cannot certify zero tests or failed APK as successful',()=>{
 const empty={passed:true,results:[{passed:true,testProfile:true,tests:{executed:0,failed:0}}]};
 assert.equal(gateSummary(empty,null,null,false).passed,false);
 const valid={passed:true,results:[{passed:true,testProfile:true,tests:{executed:4,failed:0}}]};
 const fullNode={passed:true,results:[
  {profile:'node-continuum',passed:true,testProfile:true,tests:{executed:4,failed:0}},
  {profile:'node-backend',passed:true,testProfile:true,tests:{executed:3,failed:0}}
 ]};
 const fullAndroid={passed:true,results:[
  {profile:'android-compile',passed:true,exitCode:0,testProfile:false,tests:{executed:0,failed:0}},
  {profile:'android-unit',passed:true,exitCode:0,testProfile:true,tests:{executed:3,failed:0}}
 ]};
 assert.equal(gateSummary(valid,null,null,false).passed,true);
 assert.equal(gateSummary(valid,null,null,true).passed,false);
 assert.equal(gateSummary(valid,valid,{exitCode:1,timedOut:false},true).passed,false);
 assert.equal(gateSummary(valid,valid,{exitCode:0,timedOut:false},true).passed,false,
  'green Gradle exit without a retained APK is not a passing build');
 assert.equal(gateSummary(valid,valid,{
  exitCode:0,timedOut:false,artifactSha256:'a'.repeat(64),bytes:4096
 },true).passed,false,'backend verification is mandatory');
 assert.equal(gateSummary(fullNode,valid,{
  exitCode:0,timedOut:false,artifactSha256:'a'.repeat(64),bytes:4096
 },true).passed,false,'Android compile and unit reports are mandatory');
 assert.equal(gateSummary(fullNode,fullAndroid,{
  exitCode:0,timedOut:false,artifactSha256:'a'.repeat(64),bytes:4096
 },true).passed,true);
 assert.equal(gateSummary(fullNode,{
  passed:true,results:[fullAndroid.results[1]]
 },{
  exitCode:0,timedOut:false,artifactSha256:'a'.repeat(64),bytes:4096
 },true).passed,false,'cannot silently omit compilation');
 assert.equal(gateSummary(fullNode,{
  passed:true,results:[fullAndroid.results[0]]
 },{
  exitCode:0,timedOut:false,artifactSha256:'a'.repeat(64),bytes:4096
 },true).passed,false,'cannot omit executed JVM tests');
 assert.equal(gateSummary(fullNode,{
  passed:true,results:[fullAndroid.results[0],fullAndroid.results[1],fullAndroid.results[1]]
 },{
  exitCode:0,timedOut:false,artifactSha256:'a'.repeat(64),bytes:4096
 },true).passed,false,'duplicate profiles are not full evidence');
 assert.equal(gateSummary(valid,valid,{exitCode:0,timedOut:true},true).passed,false);
});

test('reloaded local gate proof rejects forged completion and never upgrades NODE_ONLY',()=>{
 const successful={passed:true,results:[{passed:true,testProfile:true,
   tests:{executed:4,failed:0}}]};
 const head='a'.repeat(40);
 const document={
  schema:'continuum-m4-local-gate',version:1,head,
  startedAt:'2026-10-17T00:00:00Z',node:successful,android:null,apk:null,
  errors:[],complete:true,summary:gateSummary(successful,null,null,false),
  finishedAt:'2026-10-17T00:00:01Z'
 };
 const digest=o=>crypto.createHash('sha256').update(JSON.stringify(o)).digest('hex');
 document.reportSha256=digest(document);
 assert.deepEqual(verifyGateReport(document,{expectedHead:head}),
  {valid:true,releaseReady:false,reason:'ANDROID_NOT_TESTED'});
 assert.equal(verifyGateReport(document,{expectedHead:'b'.repeat(40)}).valid,false);
 assert.equal(verifyGateReport({...document,complete:false}).valid,false);
 const modified={...document,summary:{...document.summary,passed:false}};
 assert.equal(verifyGateReport(modified).reason,'REPORT_HASH_MISMATCH');
 const withRecomputedChecksum={...modified};
 const {reportSha256,...payload}=withRecomputedChecksum;
 withRecomputedChecksum.reportSha256=digest(payload);
 assert.equal(verifyGateReport(withRecomputedChecksum).reason,'INCONSISTENT_PROOF');
});

test('release proof verifies retained APK bytes, path and corruption without executing it',()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-verify-proof-'));
 try{
  const head='a'.repeat(40),apkFile=path.join(dir,'app-debug.apk');
  const artifact=Buffer.from([0x50,0x4b,0x03,0x04,0x01,0x02,0x03,0x04]);
  fs.writeFileSync(apkFile,artifact);
  const good={passed:true,results:[
    {profile:'node-continuum',passed:true,testProfile:true,tests:{executed:5,failed:0}},
    {profile:'node-backend',passed:true,testProfile:true,tests:{executed:2,failed:0}}
  ]};
  const android={passed:true,results:[
    {profile:'android-compile',passed:true,exitCode:0,testProfile:false,
     tests:{executed:0,failed:0}},
    {profile:'android-unit',passed:true,exitCode:0,testProfile:true,
     tests:{executed:5,failed:0}}
  ]};
  const apk={exitCode:0,timedOut:false,artifactSha256:
    crypto.createHash('sha256').update(artifact).digest('hex'),
    bytes:artifact.length,artifact:apkFile};
  const document={schema:'continuum-m4-local-gate',version:1,head,
    startedAt:'2026-10-17T00:00:00Z',node:good,android,apk,
    errors:[],complete:true,summary:gateSummary(good,android,apk,true),
    finishedAt:'2026-10-17T00:00:01Z'};
  document.reportSha256=crypto.createHash('sha256')
    .update(JSON.stringify(document)).digest('hex');
  const report=path.join(dir,'gate-report.json');
  fs.writeFileSync(report,JSON.stringify(document,null,2)+'\n');
  assert.deepEqual(verifyReleaseProofFile(report,{expectedHead:head}),
    {valid:true,releaseReady:true,reason:null});
  fs.appendFileSync(apkFile,Buffer.from([0x05]));
  assert.equal(verifyReleaseProofFile(report).reason,'APK_MISSING_OR_CHANGED');
  fs.writeFileSync(apkFile,artifact);
  const unsafe={...document,apk:{...apk,artifact:path.join(dir,'other.apk')}};
  delete unsafe.reportSha256;
  unsafe.reportSha256=crypto.createHash('sha256')
    .update(JSON.stringify(unsafe)).digest('hex');
  fs.writeFileSync(report,JSON.stringify(unsafe));
  assert.equal(verifyReleaseProofFile(report).reason,'UNSAFE_APK_PATH');
  fs.writeFileSync(report,'not json');
  assert.equal(verifyReleaseProofFile(report).reason,'INVALID_REPORT_FILE');
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
});

test('gate CLI rejects unknown arguments, duplicates, and invalid time budgets',()=>{
 assert.deepEqual(parseGateArgs(['--android','--budget-minutes','40']),
  {android:true,budgetMinutes:40});
 assert.throws(()=>parseGateArgs(['--budget-minutes','0']),/budget/i);
 assert.throws(()=>parseGateArgs(['--budget-minutes','hello']),/budget/i);
 assert.throws(()=>parseGateArgs(['--android','--android']),/duplicate/);
 assert.throws(()=>parseGateArgs(['--exec','rm']),/unknown/i);
 assert.deepEqual(parseGateArgs(['--verify','/tmp/check.json']),{verify:'/tmp/check.json'});
 assert.throws(()=>parseGateArgs(['--verify','/tmp/check.json','--android']),/cannot be combined/);
 assert.throws(()=>parseGateArgs(['--verify']),/Missing value/);
 assert.equal(verifyReleaseProofFile('/missing-continuum-qa-report.json').releaseReady,false);
});

test('real standalone Git fixture produces an immutable Node test proof',
 {skip:nested,timeout:180000},()=>{
 const f=fixture(false);
 try{
  const result=runM4Gate({root:f.root,out:f.out,budgetMinutes:2});
  assert.equal(result.head,f.head);
  assert.equal(result.summary.scope,'NODE_ONLY');
  assert.equal(result.summary.nodePassed,true);
  assert.ok(result.summary.executedNodeTests>=1);
  assert.equal(result.complete,true,JSON.stringify(result.errors));
  assert.ok(fs.existsSync(result.path));
  const report=JSON.parse(fs.readFileSync(result.path,'utf8'));
  assert.equal(report.head,f.head);
  assert.equal(report.reportSha256,result.reportSha256);
  assert.equal(f.git('status','--porcelain'),'');
  assert.equal(fs.existsSync(path.join(f.root,'gate-report.json')),false);
 }finally{fs.rmSync(f.base,{recursive:true,force:true});}
});

test('broken Git fixture stays red and still preserves a failure proof',
 {skip:nested,timeout:180000},()=>{
 const f=fixture(true);
 try{
  const result=runM4Gate({root:f.root,out:f.out,budgetMinutes:2});
  assert.equal(result.summary.scope,'NODE_ONLY');
  assert.equal(result.summary.nodePassed,false);
  assert.equal(result.complete,false);
  assert.ok(fs.existsSync(result.path));
  assert.equal(f.git('status','--porcelain'),'');
 }finally{fs.rmSync(f.base,{recursive:true,force:true});}
});

test('gate refuses dirty source and repository-contained proof output',
 {skip:nested,timeout:60000},()=>{
 const f=fixture(false);
 try{
  assert.throws(()=>runM4Gate({
   root:f.root,out:path.join(f.root,'private-proof')
  }),/outside/);
  fs.writeFileSync(path.join(f.root,'untracked.txt'),'dirty');
  assert.throws(()=>runM4Gate({root:f.root,out:f.out}),/dirty/);
 }finally{fs.rmSync(f.base,{recursive:true,force:true});}
});
