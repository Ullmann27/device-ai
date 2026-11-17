import test from 'node:test';
import assert from 'node:assert/strict';
import { summarizeAnskBaselineFailure, ANSK_BASELINE_FAILURE_CATEGORIES } from './ansk-baseline-triage.mjs';

function profile(name, passed, {executed = 0, failed = 0, skipped = 0, timedOut = false, outputTail = '', diagnostics = []} = {}) {
  return {profile: name, passed, exitCode: passed ? 0 : 1, timedOut,
    outputTail, diagnostics, tests: {executed, failed, skipped}};
}
function report(...results) { return {passed: results.every(x => x.passed), results}; }

test('classifies failing Kotlin source but never copies raw details or secrets', () => {
  const input = report(profile('android-compile', false, {
    diagnostics: [{kind:'UNRESOLVED_REFERENCE', file:'Secret.kt', message:'GITHUB_TOKEN=secret'}],
    outputTail:'failed at /home/private/Secret.kt: GITHUB_TOKEN=secret'
  }));
  const result = summarizeAnskBaselineFailure(input, 'NOT_ALL_PROFILES_GREEN');
  assert.equal(result.failureCategory,'ANDROID_KOTLIN_COMPILE_FAILURE');
  assert.equal(result.nextAction,'INSPECT_COMPILER_DIAGNOSTICS');
  assert.equal(result.reason,'NOT_ALL_PROFILES_GREEN');
  assert.deepEqual(result.checks[0].diagnosticKinds,['UNRESOLVED_REFERENCE']);
  assert.doesNotMatch(JSON.stringify(result),/secret|GITHUB_TOKEN|Secret.kt/);
});

test('recognizes exhausted local storage before generic compiler failure', () => {
  const result = summarizeAnskBaselineFailure(report(profile('android-compile', false,
    {outputTail:'java.io.IOException: No space left on device'})));
  assert.equal(result.failureCategory,'DEVICE_STORAGE_EXHAUSTED');
});

test('recognizes exhausted memory without claiming a Kotlin repair', () => {
  const result = summarizeAnskBaselineFailure(report(profile('android-compile', false,
    {outputTail:'java.lang.OutOfMemoryError: Java heap space'})));
  assert.equal(result.failureCategory,'DEVICE_MEMORY_EXHAUSTED');
});

test('distinguishes missing SDK/toolchain from source compiler errors', () => {
  const result = summarizeAnskBaselineFailure(report(profile('android-compile', false,
    {outputTail:'SDK location not found. Set ANDROID_HOME'})));
  assert.equal(result.failureCategory,'ANDROID_TOOLCHAIN_UNAVAILABLE');
  assert.equal(result.nextAction,'RUN_MOBILE_BUILDER_DOCTOR');
});

test('distinguishes missing Gradle dependency from source compiler errors', () => {
  const result = summarizeAnskBaselineFailure(report(profile('android-compile', false,
    {outputTail:'Could not resolve all dependencies for configuration'})));
  assert.equal(result.failureCategory,'GRADLE_DEPENDENCY_UNAVAILABLE');
});

test('classifies JUnit failure by real failed test count', () => {
  const result = summarizeAnskBaselineFailure(report(
    profile('android-compile',true), profile('android-unit',false,{executed:40,failed:2})));
  assert.equal(result.failureCategory,'ANDROID_UNIT_TEST_FAILURE');
  assert.equal(result.checks[1].failed,2);
});

test('missing JUnit execution evidence is not a successful baseline', () => {
  const result = summarizeAnskBaselineFailure(report(
    profile('android-compile',true), profile('android-unit',true,{executed:0})));
  assert.equal(result.failureCategory,'ANDROID_UNIT_EVIDENCE_MISSING');
  assert.equal(result.checks[1].passed,true);
});

test('a timed out operation is not silently retried or promoted', () => {
  const result = summarizeAnskBaselineFailure(report(
    profile('android-compile',false,{timedOut:true,outputTail:'arbitrary error'})));
  assert.equal(result.failureCategory,'BASELINE_TIMEOUT');
  assert.equal(result.nextAction,'CHECK_THERMAL_AND_RESOURCE_BUDGET');
});

test('untrusted diagnostic labels are ignored and no arbitrary log line escapes', () => {
  const result = summarizeAnskBaselineFailure(report(profile('android-compile',false,
    {diagnostics:[{kind:'TOKEN=abcdefgh'}, {kind:'TYPE_MISMATCH'}],
      outputTail:'some secret no matching known pattern'})));
  assert.deepEqual(result.checks[0].diagnosticKinds,['TYPE_MISMATCH']);
  assert.equal(result.failureCategory,'ANDROID_KOTLIN_COMPILE_FAILURE');
  assert.doesNotMatch(JSON.stringify(result),/abcdefgh|some secret/);
});

test('empty or malformed evidence remains incomplete and outputs bounded enums', () => {
  const cases = [null,{}, {results:[]}, {results:[{profile:'unexpected',passed:false,outputTail:'my secret'}]}];
  for (const value of cases) {
    const result = summarizeAnskBaselineFailure(value,'unexpected-secret');
    assert.ok(ANSK_BASELINE_FAILURE_CATEGORIES.includes(result.failureCategory));
    assert.equal(result.reason,'BASELINE_NOT_GREEN');
    assert.ok(JSON.stringify(result).length < 1200);
  }
});

test('omitted reason uses canonical failed Android baseline instead of generic fallback', () => {
  const broken = {passed:false,results:[{
    profile:'android-compile',passed:false,testProfile:false,
    exitCode:1,timedOut:false,tests:{executed:0,failed:0,skipped:0},
    diagnostics:[{kind:'UNRESOLVED_REFERENCE'}]
  }]};
  const result = summarizeAnskBaselineFailure(broken);
  assert.equal(result.reason,'NOT_ALL_PROFILES_GREEN');
  assert.equal(result.failureCategory,'ANDROID_KOTLIN_COMPILE_FAILURE');
});

test('omitted reason preserves missing unit-test evidence in a nominally green chain', () => {
  const broken = {passed:true,results:[
    {profile:'android-compile',passed:true,testProfile:false,
     exitCode:0,timedOut:false,tests:{executed:0,failed:0,skipped:0}},
    {profile:'android-unit',passed:true,testProfile:true,
     exitCode:0,timedOut:false,tests:{executed:0,failed:0,skipped:0}}
  ]};
  const result = summarizeAnskBaselineFailure(broken);
  assert.equal(result.reason,'NOT_ALL_PROFILES_GREEN');
  assert.equal(result.failureCategory,'ANDROID_UNIT_EVIDENCE_MISSING');
});

test('three successful but unidentifiable JUnit skips explain ANSK baseline block', () => {
  const result = summarizeAnskBaselineFailure(report(
    profile('android-compile',true),
    profile('android-unit',true,{executed:632,skipped:3})
  ),'INVALID_PROFILE');
  assert.equal(result.reason,'INVALID_PROFILE');
  assert.equal(result.failureCategory,'ANDROID_UNIT_SKIP_IDENTITIES_MISSING');
  assert.equal(result.nextAction,'INSPECT_SKIPPED_JUNIT_CASES');
  assert.equal(result.checks[1].executed,632);
  assert.equal(result.checks[1].skipped,3);
});
