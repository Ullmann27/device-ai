/**
 * Review-only context packet for Continuum Teamleader sessions.
 *
 * Inspired by multi-agent handoff and local/git-native agent-memory patterns.
 * It condenses existing evidence; it is NOT another backlog, learning store,
 * scheduler, repair engine, or permission grant.
 *
 * The caller provides the already-read status object. This module does no IO.
 */
const SHA40 = /^[0-9a-f]{40}$/;
const OUTCOMES = new Set([
  'NO_FAILURE', 'NO_CODE_DIAGNOSTIC', 'NO_VERIFIED_REPAIR',
  'VERIFIED', 'ERROR', 'INTERRUPTED', 'IDLE'
]);
const ANSK_CATEGORIES = new Set([
  'DEVICE_STORAGE_EXHAUSTED', 'DEVICE_MEMORY_EXHAUSTED',
  'ANDROID_TOOLCHAIN_UNAVAILABLE', 'GRADLE_DEPENDENCY_UNAVAILABLE',
  'BASELINE_TIMEOUT', 'ANDROID_UNIT_TEST_FAILURE',
  'ANDROID_UNIT_EVIDENCE_MISSING', 'ANDROID_UNIT_RUN_FAILED',
  'ANDROID_KOTLIN_COMPILE_FAILURE', 'ANDROID_COMPILE_RUN_FAILED',
  'BASELINE_CONTRACT_MISMATCH', 'BASELINE_INCOMPLETE'
]);

const verifiedHead = value => typeof value === 'string' && SHA40.test(value) ? value : null;
const statusValue = (value, allowed, fallback) =>
  typeof value === 'string' && allowed.has(value) ? value : fallback;
const safeCount = value => Number.isSafeInteger(value) && value >= 0 ? value : null;

/**
 * Produces a bounded, machine-readable handoff for the existing single
 * backlog and repair pipeline. All evidence is historical. Even "VERIFIED"
 * repair outcomes require independent owner review before publication.
 */
export function buildTeamleaderHandoff(status) {
  const data = status && typeof status === 'object' && !Array.isArray(status) ? status : {};
  const recent = data.lastCompleted && typeof data.lastCompleted === 'object'
    ? data.lastCompleted : {};
  const local = data.localIntegration && typeof data.localIntegration === 'object'
    ? data.localIntegration : {};
  const ansk = local.ansk && typeof local.ansk === 'object' ? local.ansk : {};
  const failure = ansk.lastBaselineFailure && typeof ansk.lastBaselineFailure === 'object'
    ? ansk.lastBaselineFailure : {};
  const lease = local.buildLease && typeof local.buildLease === 'object'
    ? local.buildLease : {};
  const apk = local.mobileBuilder && typeof local.mobileBuilder === 'object'
    ? local.mobileBuilder : {};
  const problem = failure.status === 'HISTORICAL_BASELINE_FAILURE_REPORTED'
    ? statusValue(failure.failureCategory, ANSK_CATEGORIES, null) : null;
  const leaseStatus = statusValue(lease.status,
    new Set(['LOCK_PRESENT_UNVERIFIED','NO_LEASE_FILE','UNREADABLE','UNKNOWN']),
    'UNKNOWN');
  const apkStatus = statusValue(apk.status,
    new Set(['EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED','NO_BUILD_REPORT',
      'UNREADABLE','UNKNOWN','READY_FOR_EXPERIMENTAL_BUILD']),
    'UNKNOWN');
  const repairOutcome = statusValue(recent.outcome, OUTCOMES, 'UNKNOWN');
  let proposedAction = 'REVIEW_EXISTING_BACKLOG';
  let suggestedRole = 'TEAMLEADER';
  if (leaseStatus === 'LOCK_PRESENT_UNVERIFIED' || leaseStatus === 'UNREADABLE') {
    proposedAction = 'INSPECT_LOCAL_BUILD_LEASE';
    suggestedRole = 'ANALYST';
  } else if (problem) {
    proposedAction = 'INVESTIGATE_HISTORICAL_ANSK_BASELINE';
    suggestedRole = 'ANALYST';
  } else if (repairOutcome === 'NO_VERIFIED_REPAIR') {
    proposedAction = 'INSPECT_LAST_UNVERIFIED_REPAIR';
    suggestedRole = 'ANALYST';
  } else if (repairOutcome === 'VERIFIED') {
    proposedAction = 'REQUEST_PATCH_REVIEW';
    suggestedRole = 'TEAMLEADER';
  } else if (apkStatus === 'EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED') {
    proposedAction = 'VERIFY_APK_ON_DEVICE';
    suggestedRole = 'DEVICE_BUILDER';
  }
  return {
    schema: 'continuum-teamleader-review-handoff',
    version: 1,
    authority: 'ADVISORY_ONLY',
    historical: true,
    project: 'device-ai',
    lastRun: {
      head: verifiedHead(recent.head),
      outcome: repairOutcome,
      processedHeads: safeCount(data.processedHeads)
    },
    localEvidence: {
      buildLease: leaseStatus,
      anskBaselineCategory: problem,
      anskBaselineHead: problem ? verifiedHead(failure.head) : null,
      apkReport: apkStatus,
      apkArtifactVerifiedHere: false,
      deviceInstallationVerifiedHere: false
    },
    next: {
      role: suggestedRole,
      action: proposedAction,
      approvedToExecute: false,
      approvalRequiredForPublication: true
    }
  };
}
