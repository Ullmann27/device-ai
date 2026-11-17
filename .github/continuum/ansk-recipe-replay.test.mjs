import test from 'node:test';
import assert from 'node:assert/strict';
import {verifyAnskExperiment,verifyAnskRecipeReceipt} from './ansk-lab-contract.mjs';
import {planAnskShadowReplay} from './ansk-recipe-replay.mjs';
import {applyEdits} from './repair-strategies.mjs';

const origin='app/src/main/java/at/heinz/deviceai/learning/MutationProbe.kt';
const target='app/src/main/java/at/heinz/deviceai/learning/NewProbe.kt';
const source=[
 'package at.heinz.deviceai.learning',
 '',
 'import java.nio.file.Paths',
 '',
 'class NewProbe {',
 '  fun inspect(): Boolean {',
 '    val path = Paths.get("x")',
 '    return Files.exists(path)',
 '  }',
 '}',
 ''
].join('\n');
function profile(name,passed=true){
 return {profile:name,passed,testProfile:name==='android-unit',
  exitCode:passed?0:1,timedOut:false,
  tests:{executed:name==='android-unit'?7:0,failed:0,skipped:0}};
}
const green=()=>({passed:true,results:[
 profile('android-compile'),profile('android-unit')],diagnostics:[]});
const red=()=>({passed:false,results:[profile('android-compile',false)],
 diagnostics:[{kind:'UNRESOLVED_REFERENCE',symbol:'Files',file:origin,
  message:'Unresolved reference Files',fingerprint:'e'.repeat(16)}]});
function recipe(){
 const observed=verifyAnskExperiment({
  head:'f'.repeat(40),sourceFile:origin,importFqn:'java.nio.file.Files',
  baselineSha256:'a'.repeat(64),mutatedSha256:'b'.repeat(64),
  primarySha256:'c'.repeat(64),independentSha256:'c'.repeat(64),
  patchSha256:'d'.repeat(64),baselineRun:green(),mutatedRun:red(),
  primaryRun:green(),independentRun:green(),
  diagnostic:red().diagnostics[0],
  strategiesAttempted:['KOTLIN_MISSING_IMPORT'],
  successfulStrategy:'KOTLIN_MISSING_IMPORT'
 });
 assert.equal(observed.verified,true);
 return observed.recipe;
}
const diagnostic={file:target,kind:'UNRESOLVED_REFERENCE',
 symbol:'Files',fingerprint:'d'.repeat(16)};

test('integrity-checked research receipt is only a quarantined shadow hint',()=>{
 const r=recipe();
 assert.equal(verifyAnskRecipeReceipt(r).valid,true);
 assert.equal(r.modelTrainingAuthorized,false);
 assert.equal(r.automaticToolRegistration,false);
});

test('a recipe can propose a minimal import on an unseen file without writing it',()=>{
 const r=recipe(),before=source;
 const result=planAnskShadowReplay({recipe:r,sourceFile:target,source,diagnostic});
 assert.equal(result.status,'SHADOW_CANDIDATE_ONLY');
 assert.equal(result.authority,'SHADOW_ONLY');
 assert.equal(result.autoApplyAuthorized,false);
 assert.equal(result.modelTrainingAuthorized,false);
 assert.equal(result.independentTestRequired,true);
 assert.equal(result.candidate.strategy,'KOTLIN_MISSING_IMPORT');
 assert.equal(result.candidate.edits[0].file,target);
 const after=applyEdits(p=>p===target?source:null,result.candidate.edits).get(target);
 assert.match(after,/^import java\.nio\.file\.Files$/m);
 assert.equal(source,before);
 assert.ok(result.candidate.edits[0].oldText.length<source.length);
});

test('receipt tampering is rejected, including attempted privilege escalation',()=>{
 for(const change of [
   r=>{r.importFqn='java.lang.Runtime';},
   r=>{r.automaticToolRegistration=true;},
   r=>{r.modelTrainingAuthorized=true;},
   r=>{r.mainBranchWriteAuthorized=true;},
   r=>{r.primaryExecuted=0;}
 ]){
  const r=recipe();change(r);
  assert.equal(verifyAnskRecipeReceipt(r).valid,false);
  assert.equal(planAnskShadowReplay({recipe:r,sourceFile:target,source,diagnostic}).status,'REJECTED');
 }
});

test('replay never admits unsafe files or reuses a receipt against its own source',()=>{
 const r=recipe();
 for(const wrong of ['../other.kt','app/src/test/java/at/heinz/NewProbe.kt','/tmp/Shell.kt']){
  const result=planAnskShadowReplay({recipe:r,sourceFile:wrong,source,diagnostic});
  assert.equal(result.status,'REJECTED');
 }
 assert.equal(planAnskShadowReplay({
  recipe:r,sourceFile:origin,source,diagnostic:{...diagnostic,file:origin}
 }).reason,'REPLAY_REQUIRES_DIFFERENT_SOURCE');
});

test('replay requires a matching diagnostic and a real symbol in Kotlin code',()=>{
 const r=recipe();
 for(const bad of [
  {...diagnostic,symbol:'Whatever'},
  {...diagnostic,kind:'SYNTAX_ERROR'},
  {...diagnostic,fingerprint:'INVALID'},
  {...diagnostic,file:origin}
 ]){
  assert.equal(planAnskShadowReplay({recipe:r,sourceFile:target,source,diagnostic:bad}).reason,
   'INCOMPATIBLE_DIAGNOSTIC');
 }
 const comment=source.replace('return Files.exists(path)',
   'return true // Files was mentioned in a comment');
 assert.equal(planAnskShadowReplay({recipe:r,sourceFile:target,source:comment,diagnostic}).reason,
  'SYMBOL_NOT_USED_AS_CODE');
});

test('already imported symbol and ineligible receipt never generate modifications',()=>{
 const r=recipe();
 const present=source.replace('import java.nio.file.Paths',
   'import java.nio.file.Files\nimport java.nio.file.Paths');
 assert.equal(planAnskShadowReplay({
  recipe:r,sourceFile:target,source:present,diagnostic
 }).reason,'SYMBOL_ALREADY_IMPORTED');
 const tooLarge='A'.repeat(250_001);
 assert.equal(planAnskShadowReplay({
  recipe:r,sourceFile:target,source:tooLarge,diagnostic
 }).reason,'INVALID_SOURCE_BYTES');
});

test('repeat replay produces deterministic id and the same hunk without learning',()=>{
 const r=recipe();
 const a=planAnskShadowReplay({recipe:r,sourceFile:target,source,diagnostic});
 const b=planAnskShadowReplay({recipe:r,sourceFile:target,source,diagnostic});
 assert.equal(a.candidate.id,b.candidate.id);
 assert.deepEqual(a.candidate.edits,b.candidate.edits);
 assert.equal(a.autoApplyAuthorized,false);
});
