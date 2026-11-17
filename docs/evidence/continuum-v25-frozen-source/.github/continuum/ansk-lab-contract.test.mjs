import test from 'node:test';
import assert from 'node:assert/strict';
import {
 assertAnskSource,measureAnskChain,verifyAnskExperiment,
 ANSK_SCHEMA,ANSK_VERSION
} from './ansk-lab-contract.mjs';
import {continuumGradleProfile} from './repair-loop.mjs';

const file='app/src/main/java/at/heinz/deviceai/learning/MutationProbe.kt';
const fqn='java.nio.file.Files';
const base='a'.repeat(64),mut='b'.repeat(64),fix='c'.repeat(64),patch='d'.repeat(64);
function profile(profile,passed,executed,failed=0,skipped=0){
 return {profile,passed,testProfile:profile==='android-unit',
   exitCode:passed?0:1,timedOut:false,tests:{executed,failed,skipped}};
}
function chain(...profiles){
 return {passed:profiles.every(x=>x.passed),results:profiles,
   diagnostics:[]};
}
function green(tests=7){
 return chain(profile('android-compile',true,0),
  profile('android-unit',true,tests));
}
function red(){
 const r=chain(profile('android-compile',false,0));
 r.diagnostics=[{kind:'UNRESOLVED_REFERENCE',symbol:'Files',file,
   message:'Unresolved reference Files'}];
 return r;
}
function fixture(){
 return {
  head:'f'.repeat(40),sourceFile:file,importFqn:fqn,
  baselineSha256:base,mutatedSha256:mut,
  primarySha256:fix,independentSha256:fix,patchSha256:patch,
  baselineRun:green(),mutatedRun:red(),primaryRun:green(),
  independentRun:green(),
  diagnostic:red().diagnostics[0],
  strategiesAttempted:['KOTLIN_MISSING_IMPORT'],
  successfulStrategy:'KOTLIN_MISSING_IMPORT'
 };
}
test('positive triple-phase lab observation yields only a quarantined shadow recipe',()=>{
 const result=verifyAnskExperiment(fixture());
 assert.equal(result.verified,true);
 assert.equal(result.authority,'SHADOW_ONLY');
 assert.equal(result.recipe.schema,ANSK_SCHEMA);
 assert.equal(result.recipe.version,ANSK_VERSION);
 assert.match(result.recipe.recipeSha256,/^[0-9a-f]{64}$/);
 assert.equal(result.recipe.stage,'QUARANTINED_REVIEW_REQUIRED');
 assert.equal(result.recipe.automaticToolRegistration,false);
 assert.equal(result.recipe.modelTrainingAuthorized,false);
 assert.equal(result.recipe.mainBranchWriteAuthorized,false);
 assert.equal(result.recipe.primaryExecuted,7);
 assert.equal(result.recipe.independentExecuted,7);
});

test('source path and import name must be fixed Kotlin production files',()=>{
 for(const bad of ['../outside.kt','/tmp/X.kt',
   'app/src/test/java/at/heinz/Foo.kt',
   'app/src/main/java/at/heinz/../../Foo.kt',
   'app/src/main/java/at/heinz/Foo.kts',
   'app/src/main/java/at/heinz/SecureTokenStore.kt/../Foo.kt']){
  assert.throws(()=>assertAnskSource(bad,fqn),/ANSK_INVALID_KOTLIN_IMPORT_TARGET/);
 }
 assert.throws(()=>assertAnskSource(file,'java.lang.Thing;evil'),/ANSK_INVALID_KOTLIN_IMPORT_TARGET/);
 assert.equal(assertAnskSource(file,fqn),file);
});

test('baseline with zero tests, skips or failing checks never qualifies',()=>{
 for(const invalid of [
  chain(profile('android-compile',true,0),profile('android-unit',true,0)),
  chain(profile('android-compile',true,0),profile('android-unit',true,8,0,1)),
  chain(profile('android-compile',true,0)),
  chain(profile('android-compile',false,0)),
 ]){
  const f=fixture();f.baselineRun=invalid;
  assert.equal(verifyAnskExperiment(f).reason,'BASELINE_NOT_GREEN');
 }
});

test('mutation must genuinely fail at compile with exactly the expected diagnostic',()=>{
 const same=fixture();same.mutatedRun=green();
 assert.equal(verifyAnskExperiment(same).reason,'MUTATION_NOT_COMPILER_RED');
 const missing=fixture();missing.diagnostic={...missing.diagnostic,kind:'TEST_FAILURE'};
 assert.equal(verifyAnskExperiment(missing).reason,'NO_MATCHING_COMPILER_DIAGNOSTIC');
 const wrong=fixture();wrong.diagnostic={...wrong.diagnostic,file:'app/src/main/java/at/heinz/Other.kt'};
 assert.equal(verifyAnskExperiment(wrong).reason,'NO_MATCHING_COMPILER_DIAGNOSTIC');
 const silent=fixture();silent.mutatedRun.results[0].exitCode=0;
 assert.equal(verifyAnskExperiment(silent).reason,'MUTATION_NOT_COMPILER_RED');
});

test('primary and independent phases require all profiles and unreduced test coverage',()=>{
 const prim=fixture();prim.primaryRun=green(6);
 assert.equal(verifyAnskExperiment(prim).reason,'PRIMARY_NOT_GREEN');
 const second=fixture();second.independentRun=green(6);
 assert.equal(verifyAnskExperiment(second).reason,'INDEPENDENT_NOT_GREEN');
 const fail=fixture();fail.independentRun=red();
 assert.equal(verifyAnskExperiment(fail).reason,'INDEPENDENT_NOT_GREEN');
 const skipped=fixture();
 skipped.independentRun.results[1].tests.skipped=1;
 assert.equal(verifyAnskExperiment(skipped).reason,'INDEPENDENT_NOT_GREEN');
});

test('no source, mutated, patch or independent hash ambiguity passes',()=>{
 for(const key of ['head','baselineSha256','mutatedSha256',
   'primarySha256','independentSha256','patchSha256']){
  const f=fixture();f[key]='INVALID';
  assert.equal(verifyAnskExperiment(f).reason,'SOURCE_OR_PATCH_HASH_MISMATCH');
 }
 const f=fixture();f.mutatedSha256=f.baselineSha256;
 assert.equal(verifyAnskExperiment(f).reason,'SOURCE_OR_PATCH_HASH_MISMATCH');
});

test('only approved, observed deterministic import strategy is eligible',()=>{
 const f=fixture();f.successfulStrategy='GROQ_INVENTED_TOOL';
 assert.equal(verifyAnskExperiment(f).reason,'INVALID_REPAIR_STRATEGY');
 const no=fixture();no.strategiesAttempted=[];
 assert.equal(verifyAnskExperiment(no).reason,'INVALID_REPAIR_STRATEGY');
 const duplicate=fixture();
 duplicate.strategiesAttempted=['KOTLIN_MISSING_IMPORT','KOTLIN_MISSING_IMPORT'];
 assert.equal(verifyAnskExperiment(duplicate).reason,'INVALID_REPAIR_STRATEGY');
});

test('no caller-owned observation is mutated and schema hash is deterministic',()=>{
 const data=fixture(),old=JSON.stringify(data);
 const a=verifyAnskExperiment(data),b=verifyAnskExperiment(data);
 assert.equal(JSON.stringify(data),old);
 assert.equal(a.recipe.recipeSha256,b.recipe.recipeSha256);
});

test('Gradle profiles remain unchanged on non-Termux hardware',()=>{
 const compile=continuumGradleProfile('/tmp/example','compile',{env:{},arch:'x64'});
 const unit=continuumGradleProfile('/tmp/example','unit',{env:{},arch:'x64'});
 assert.deepEqual(compile.args,[':app:compileDebugUnitTestKotlin','--console=plain','-Pkotlin.incremental=false']);
 assert.deepEqual(unit.args,[':app:testDebugUnitTest','--console=plain','-Pkotlin.incremental=false']);
 assert.equal(compile.cmd,'gradle');
 assert.equal(unit.cmd,'gradle');
 assert.throws(()=>continuumGradleProfile('/tmp/example','evil'),/Unsupported Gradle/);
});

test('fake or missing pinned Termux toolchain fails closed rather than switching to desktop',()=>{
 assert.throws(()=>continuumGradleProfile('/tmp/example','compile',{
   arch:'arm64',
   env:{PREFIX:'/data/data/com.termux/files/usr',HOME:'/tmp/none',
     CONTINUUM_GRADLE_PATH:'/missing/gradle',CONTINUUM_AAPT2_PATH:'/missing/aapt2'}
 }),/ANSK_MISSING_PINNED_TERMUX_ANDROID_TOOLCHAIN/);
});

test('structural results cannot grant trust to imported JSON or authorize model training',()=>{
 const fake=fixture();
 assert.equal(verifyAnskExperiment(fake).authority,'SHADOW_ONLY');
 assert.equal(measureAnskChain(fake.baselineRun).passed,true);
 const bogus=fixture();bogus.baselineRun.results[1].passed=true;
 bogus.baselineRun.results[1].exitCode=1;
 assert.equal(measureAnskChain(bogus.baselineRun).passed,false);
 assert.equal(verifyAnskExperiment(bogus).verified,false);
});

test('real Samsung-style baseline accepts three identified skips with 632 executed tests',()=>{
  const sample=fixture();
  const digest='b'.repeat(64);
  for(const run of [sample.baselineRun,sample.primaryRun,sample.independentRun]){
    run.results[1].tests.executed=632;
    run.results[1].tests.skipped=3;
    run.results[1].tests.skippedCaseDigest=digest;
  }
  assert.deepEqual(measureAnskChain(sample.baselineRun),{passed:true,reason:null});
  const verified=verifyAnskExperiment(sample);
  assert.equal(verified.verified,true);
  assert.equal(verified.recipe.baselineExecuted,632);
  assert.equal(verified.recipe.primaryExecuted,632);
  assert.equal(verified.recipe.independentExecuted,632);
  assert.equal(verified.recipe.modelTrainingAuthorized,false);
});

test('ANSK rejects missing skip identities even if Gradle and 632 executed tests pass',()=>{
 const sample=fixture();
 sample.baselineRun.results[1].tests.executed=632;
 sample.baselineRun.results[1].tests.skipped=3;
 assert.equal(measureAnskChain(sample.baselineRun).reason,'INVALID_PROFILE');
 assert.equal(verifyAnskExperiment(sample).verified,false);
});

test('ANSK rejects swapped skipped tests even when all counts remain unchanged',()=>{
 const sample=fixture();
 for(const run of [sample.baselineRun,sample.primaryRun,sample.independentRun]){
   run.results[1].tests.executed=632;
   run.results[1].tests.skipped=3;
   run.results[1].tests.skippedCaseDigest='a'.repeat(64);
 }
 sample.primaryRun.results[1].tests.skippedCaseDigest='b'.repeat(64);
 assert.equal(measureAnskChain(sample.primaryRun,{baseline:sample.baselineRun}).reason,'EVIDENCE_REGRESSION');
 assert.equal(verifyAnskExperiment(sample).reason,'PRIMARY_NOT_GREEN');
 sample.primaryRun.results[1].tests.skippedCaseDigest='a'.repeat(64);
 sample.independentRun.results[1].tests.skippedCaseDigest='b'.repeat(64);
 assert.equal(verifyAnskExperiment(sample).reason,'INDEPENDENT_NOT_GREEN');
});

test('ANSK forbids extra skips or fewer executed tests in independent validation',()=>{
 const sample=fixture();
 for(const run of [sample.baselineRun,sample.primaryRun,sample.independentRun]){
   run.results[1].tests.executed=632;
   run.results[1].tests.skipped=3;
   run.results[1].tests.skippedCaseDigest='a'.repeat(64);
 }
 sample.independentRun.results[1].tests.executed=631;
 assert.equal(verifyAnskExperiment(sample).reason,'INDEPENDENT_NOT_GREEN');
 sample.independentRun.results[1].tests.executed=632;
 sample.independentRun.results[1].tests.skipped=4;
 assert.equal(verifyAnskExperiment(sample).reason,'INDEPENDENT_NOT_GREEN');
});
