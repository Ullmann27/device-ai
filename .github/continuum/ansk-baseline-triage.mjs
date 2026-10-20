/**
 * One diagnostic vocabulary for the experimental ANSK baseline gate.
 * Input is untrusted compiler/test data. Output is a small fixed-code summary:
 * never include raw stdout/stderr, stack traces, file paths, or secret values.
 * This module does not run checks, repair code, or authorize model promotion.
 */
import {measureAnskChain} from './ansk-lab-contract.mjs';

const PROFILE_ORDER = ['android-compile', 'android-unit'];
const DIAGNOSTIC_KIND = /^[A-Z][A-Z0-9_]{0,63}$/;

function classify(result, checks) {
  const failed = checks.find(check => !check.passed);
  if (!failed) {
    const unit = checks.find(check => check.profile === 'android-unit');
    if (!unit || unit.executed === 0)
      return ['ANDROID_UNIT_EVIDENCE_MISSING', 'VERIFY_REAL_JUNIT_RESULTS'];
    if (unit.skipped > 0) {
      const unitEvidence = result?.results?.find(row => row?.profile === 'android-unit')?.tests;
      if (typeof unitEvidence?.skippedCaseDigest !== 'string' ||
          !/^[0-9a-f]{64}$/.test(unitEvidence.skippedCaseDigest))
        return ['ANDROID_UNIT_SKIP_IDENTITIES_MISSING', 'INSPECT_SKIPPED_JUNIT_CASES'];
    }
    return ['BASELINE_CONTRACT_MISMATCH', 'INSPECT_BASELINE_CHECK_CONTRACT'];
  }
  if (failed.timedOut)
    return ['BASELINE_TIMEOUT', 'CHECK_THERMAL_AND_RESOURCE_BUDGET'];
  const original = result.results?.[checks.indexOf(failed)];
  const output = typeof original?.outputTail === 'string' ? original.outputTail.slice(-3_000) : '';
  if (/\b(?:ENOSPC|no space left on device)\b/i.test(output))
    return ['DEVICE_STORAGE_EXHAUSTED', 'CHECK_FREE_SPACE'];
  if (/\b(?:OutOfMemoryError|Java heap space|unable to create new native thread|could not reserve enough space)\b/i.test(output))
    return ['DEVICE_MEMORY_EXHAUSTED', 'CHECK_MEMORY_AND_GRADLE_WORKERS'];
  if (/\b(?:SDK location not found|Android SDK not found|aapt2[^\n]*not found|invalid JAVA_HOME)\b/i.test(output))
    return ['ANDROID_TOOLCHAIN_UNAVAILABLE', 'RUN_MOBILE_BUILDER_DOCTOR'];
  if (/\b(?:Could not resolve|Could not find [^\n]*dependency|UnknownHostException)\b/i.test(output))
    return ['GRADLE_DEPENDENCY_UNAVAILABLE', 'INSPECT_OFFLINE_DEPENDENCIES'];
  if (failed.profile === 'android-unit') {
    if (failed.failed > 0)
      return ['ANDROID_UNIT_TEST_FAILURE', 'INSPECT_JUNIT_FAILURES'];
    if (failed.executed === 0)
      return ['ANDROID_UNIT_EVIDENCE_MISSING', 'VERIFY_REAL_JUNIT_RESULTS'];
    return ['ANDROID_UNIT_RUN_FAILED', 'INSPECT_GRADLE_TEST_TASK'];
  }
  if (failed.profile === 'android-compile') {
    if (failed.diagnosticKinds.length > 0)
      return ['ANDROID_KOTLIN_COMPILE_FAILURE', 'INSPECT_COMPILER_DIAGNOSTICS'];
    return ['ANDROID_COMPILE_RUN_FAILED', 'INSPECT_GRADLE_COMPILE_TASK'];
  }
  return ['BASELINE_INCOMPLETE', 'INSPECT_BASELINE_CHECK_CONTRACT'];
}

export function summarizeAnskBaselineFailure(result, reason = undefined) {
  // Match the canonical ANSK contract if the caller omitted the reason.
  // Invalid/incomplete evidence never receives a fabricated success result.
  const observedReason = measureAnskChain(result).reason;
  const defaultReason = observedReason === 'NOT_ALL_PROFILES_GREEN'
    ? observedReason : 'BASELINE_NOT_GREEN';
  const rows = Array.isArray(result?.results) ? result.results.slice(0, 2) : [];
  const checks = rows.map((entry, index) => ({
    profile: PROFILE_ORDER.includes(entry?.profile) && entry.profile === PROFILE_ORDER[index]
      ? entry.profile : 'UNKNOWN',
    passed: entry?.passed === true,
    exitCode: Number.isSafeInteger(entry?.exitCode) ? entry.exitCode : null,
    timedOut: entry?.timedOut === true,
    executed: Number.isSafeInteger(entry?.tests?.executed) ? entry.tests.executed : null,
    failed: Number.isSafeInteger(entry?.tests?.failed) ? entry.tests.failed : null,
    skipped: Number.isSafeInteger(entry?.tests?.skipped) ? entry.tests.skipped : null,
    diagnosticKinds: Array.isArray(entry?.diagnostics) ?
      [...new Set(entry.diagnostics.map(d => d?.kind).filter(kind =>
        typeof kind === 'string' && DIAGNOSTIC_KIND.test(kind)))].slice(0, 6) : []
  }));
  const [failureCategory, nextAction] = classify(result, checks);
  return {
    reason: reason === undefined ? defaultReason :
      typeof reason === 'string' && /^(?:[A-Z][A-Z0-9_]{0,63})$/.test(reason)
        ? reason : 'BASELINE_NOT_GREEN',
    failureCategory,
    nextAction,
    checks
  };
}

export const ANSK_BASELINE_FAILURE_CATEGORIES = Object.freeze([
  'DEVICE_STORAGE_EXHAUSTED', 'DEVICE_MEMORY_EXHAUSTED',
  'ANDROID_TOOLCHAIN_UNAVAILABLE', 'GRADLE_DEPENDENCY_UNAVAILABLE',
  'BASELINE_TIMEOUT', 'ANDROID_UNIT_TEST_FAILURE',
  'ANDROID_UNIT_EVIDENCE_MISSING', 'ANDROID_UNIT_SKIP_IDENTITIES_MISSING',
  'ANDROID_UNIT_RUN_FAILED',
  'ANDROID_KOTLIN_COMPILE_FAILURE', 'ANDROID_COMPILE_RUN_FAILED',
  'BASELINE_CONTRACT_MISMATCH', 'BASELINE_INCOMPLETE'
]);
