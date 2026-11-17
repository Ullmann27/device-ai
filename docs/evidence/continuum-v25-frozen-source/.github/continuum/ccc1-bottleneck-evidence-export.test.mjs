import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import {CoinWmV3ShadowModel} from './coin-wm-v3-shadow-model.mjs';
import {induceStructuredStatementOperator} from './structured-statement-translator.mjs';
import {validateCodingTask} from './coding-task.mjs';
import {diagnoseCcc1BottleneckCase} from './ccc1-bottleneck-diagnosis.mjs';
import {exportCcc1BottleneckScorecard} from './ccc1-bottleneck-evidence-export.mjs';

const source='function sortVisible(items) { return items.sort((a, b) => a - b); }';
const fixed='function sortVisible(items) { return [...items].sort((a, b) => a - b); }';
const op=induceStructuredStatementOperator('sort',
 'function rank(xs) { return xs.sort((a, b) => a - b); }',
 'function rank(xs) { return [...xs].sort((a, b) => a - b); }');
const task=validateCodingTask({
 schema:'continuum-coding-task',version:1,id:'diagnostic-export-01',
 sourceCommit:'a'.repeat(40),kind:'BUGFIX',chain:'node',
 goal:'Prevent sorting from modifying the original array.',
 acceptance:['All canonical Node tests must pass in the owner sandbox.'],
 allowedPaths:['src/sort.mjs'],limits:{maxModelCalls:1,maxContextChars:2048}
});
function observation(){
 return diagnoseCcc1BottleneckCase({experimentId:'EXPORT_001',caseId:'SORT_001',
  typedTask:task,source,file:'src/sort.mjs',referenceCode:fixed,
  operators:[op],referenceOperatorIndex:0,
  model:new CoinWmV3ShadowModel(1),state16:new Float64Array(16),
  modelCheckpointSha256:'b'.repeat(64),testManifestSha256:'c'.repeat(64)});
}
function fixture(){
 const base=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-scorecard-test-'));
 const projectRoot=path.join(base,'project');
 const stateDir=path.join(base,'state');
 fs.mkdirSync(projectRoot,{mode:0o700});
 fs.mkdirSync(stateDir,{mode:0o700});
 fs.mkdirSync(path.join(stateDir,'research-scorecards'),{mode:0o700});
 return {base,projectRoot,stateDir};
}
function withFixture(fn){
 const args=fixture();
 try{fn(args);}finally{fs.rmSync(args.base,{recursive:true,force:true});}
}
test('write immutable redacted research-only observation with digest and owner mode',()=>{
 withFixture(({projectRoot,stateDir})=>{
  const result=exportCcc1BottleneckScorecard({observation:observation(),projectRoot,stateDir});
  assert.equal(result.status,'ANALYSIS_ONLY_WRITTEN');
  assert.equal(result.learningEligible,false);
  assert.ok(fs.existsSync(result.filename));
  assert.equal(fs.statSync(result.filename).mode&0o077,0);
  const bytes=fs.readFileSync(result.filename);
  assert.equal(crypto.createHash('sha256').update(bytes).digest('hex'),result.envelopeSha256);
  const record=JSON.parse(bytes.toString('utf8'));
  assert.equal(record.classification,'NON_ATTESTED_ANALYSIS_ONLY');
  assert.equal(record.scorecard.verifiedSuccess,false);
  assert.equal(record.scorecard.independentVerification,'NOT_RUN');
  assert.equal(record.scorecard.optionalLargeSpecialist,'NOT_REQUESTED');
  assert.equal(crypto.createHash('sha256').update(JSON.stringify(record.scorecard))
   .digest('hex'),record.scorecardSha256);
  assert.equal(bytes.includes(Buffer.from('function sortVisible')),false);
 });
});

test('same content cannot be silently overwritten or double-counted',()=>{
 withFixture(({projectRoot,stateDir})=>{
  const args={observation:observation(),projectRoot,stateDir};
  const first=exportCcc1BottleneckScorecard(args);
  assert.throws(()=>exportCcc1BottleneckScorecard(args),/EEXIST/);
  assert.equal(fs.readdirSync(path.join(stateDir,'research-scorecards')).length,1);
  assert.equal(fs.existsSync(first.filename),true);
 });
});

test('no export into repository, public directory or unprepared state',()=>{
 withFixture(({projectRoot,stateDir})=>{
  const obs=observation();
  assert.throws(()=>exportCcc1BottleneckScorecard({
   observation:obs,projectRoot,stateDir:path.join(projectRoot,'private')
  }),/CCC1_STATE_INSIDE_SOURCE/);
  fs.chmodSync(path.join(stateDir,'research-scorecards'),0o755);
  assert.throws(()=>exportCcc1BottleneckScorecard({observation:obs,projectRoot,stateDir}),
   /EVIDENCE_DIRECTORY_PERMISSIONS/);
 });
});

test('symlinked report directory is refused',()=>{
 withFixture(({projectRoot,stateDir})=>{
  const target=path.join(stateDir,'other');
  fs.mkdirSync(target,{mode:0o700});
  const folder=path.join(stateDir,'research-scorecards');
  fs.rmdirSync(folder);
  fs.symlinkSync(target,folder);
  assert.throws(()=>exportCcc1BottleneckScorecard({
    observation:observation(),projectRoot,stateDir}),/EVIDENCE_DIRECTORY_NOT_CANONICAL/);
 });
});
