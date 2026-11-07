import crypto from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {ANSK_BASELINE_FAILURE_CATEGORIES} from './ansk-baseline-triage.mjs';

const SHA40 = /^[0-9a-f]{40}$/;
const SHA64 = /^[0-9a-f]{64}$/;
const MAX_BUILD_REPORT_BYTES = 32_768;
const MAX_RECIPE_ENTRIES = 256;

function readBoundedJson(filename, maxBytes) {
  // NOFOLLOW and a bounded fstat prevent following a replaced report symlink
  // or loading arbitrarily large external data into the status process.
  const fd = fs.openSync(filename, fs.constants.O_RDONLY | fs.constants.O_NOFOLLOW);
  try {
    const info = fs.fstatSync(fd);
    if (!info.isFile() || info.size < 2 || info.size > maxBytes)
      throw Error('UNTRUSTED_REPORT_SIZE_OR_TYPE');
    return JSON.parse(fs.readFileSync(fd, 'utf8'));
  } finally {
    fs.closeSync(fd);
  }
}

function directoryState(directory) {
  try {
    const stat = fs.lstatSync(directory);
    if (!stat.isDirectory() || stat.isSymbolicLink()) return 'UNSAFE';
    return 'PRESENT';
  } catch (error) {
    if (error.code === 'ENOENT') return 'MISSING';
    return 'UNREADABLE';
  }
}

function inspectLease(base, root) {
  const directory = path.join(base, 'build-leases');
  const state = directoryState(directory);
  if (state === 'MISSING') return { status: 'NO_LEASE_FILE', executionConfirmed: false };
  if (state !== 'PRESENT') return { status: 'UNREADABLE', executionConfirmed: false };
  const id = crypto.createHash('sha256').update(root).digest('hex');
  const file = path.join(directory, id + '.lock');
  try {
    const record = readBoundedJson(file, 2_048);
    if (record?.schema !== 'continuum-local-build-lease' || record.version !== 1 ||
        record.root !== root || !Number.isSafeInteger(record.pid) || record.pid <= 0 ||
        !Number.isSafeInteger(record.startedAt) || record.startedAt < 0 ||
        typeof record.hostname !== 'string' || record.hostname.length > 255 ||
        !/^[a-f0-9]{32}$/.test(record.nonce ?? '')) {
      return { status: 'UNREADABLE', executionConfirmed: false };
    }
    return { status: 'LOCK_PRESENT_UNVERIFIED', executionConfirmed: false };
  } catch (error) {
    if (error.code === 'ENOENT') return { status: 'NO_LEASE_FILE', executionConfirmed: false };
    return { status: 'UNREADABLE', executionConfirmed: false };
  }
}

function inspectAnsk(base) {
  const lab = directoryState(path.join(base, 'ansk-lab'));
  const directory = path.join(base, 'ansk-lab', 'shadow-recipes');
  const state = lab === 'PRESENT' ? directoryState(directory) : lab;
  if (state === 'MISSING') return { status: 'NO_LOCAL_RECEIPTS', receiptFiles: 0,
    independentlyVerifiedHere: false, authority: 'SHADOW_ONLY' };
  if (state !== 'PRESENT') return { status: 'UNREADABLE', receiptFiles: null,
    independentlyVerifiedHere: false, authority: 'SHADOW_ONLY' };
  try {
    const entries = fs.readdirSync(directory, { withFileTypes: true });
    if (entries.length > MAX_RECIPE_ENTRIES || entries.some(entry =>
      !entry.isFile() || !SHA64.test(entry.name.slice(0, -5)) || !entry.name.endsWith('.json'))) {
      return { status: 'REVIEW_REQUIRED', receiptFiles: null,
        independentlyVerifiedHere: false, authority: 'SHADOW_ONLY' };
    }
    // Deliberately count files, not successful repairs. Recipe validation and
    // genuine compilation evidence belong to the canonical ANSK audit gate.
    return { status: entries.length ? 'QUARANTINED_RECEIPTS_PRESENT' : 'NO_LOCAL_RECEIPTS',
      receiptFiles: entries.length, independentlyVerifiedHere: false,
      authority: 'SHADOW_ONLY' };
  } catch {
    return { status: 'UNREADABLE', receiptFiles: null,
      independentlyVerifiedHere: false, authority: 'SHADOW_ONLY' };
  }
}

/** Research receipts are metadata, not live compiler evidence. */
function inspectAnskBaseline(base) {
  const dir = path.join(base, 'ansk-lab');
  const state = directoryState(dir);
  if (state === 'MISSING') return {status:'NO_BASELINE_FAILURE_RECORDED'};
  if (state !== 'PRESENT') return {status:'UNREADABLE'};
  try {
    const record = readBoundedJson(path.join(dir, 'last-baseline-failure.json'), 8_192);
    const d = record?.detail;
    if (record?.schema !== 'continuum-ansk-baseline-failure' || record.version !== 1 ||
        record.status !== 'BLOCKED_BASELINE_NOT_GREEN' || !SHA40.test(record.head ?? '') ||
        !Number.isSafeInteger(record.observedAt) || record.observedAt < 0 ||
        typeof d?.reason !== 'string' || !/^[A-Z][A-Z0-9_]{0,63}$/.test(d.reason) ||
        !ANSK_BASELINE_FAILURE_CATEGORIES.includes(d.failureCategory) ||
        typeof d.nextAction !== 'string' || !/^[A-Z][A-Z0-9_]{0,63}$/.test(d.nextAction) ||
        !Array.isArray(d.checks) || d.checks.length > 2)
      throw Error('INVALID_ANSK_BASELINE_REPORT');
    // Historical only: the checkout HEAD may have changed after this run.
    return {status:'HISTORICAL_BASELINE_FAILURE_REPORTED',
      head:record.head, observedAt:record.observedAt,
      failureCategory:d.failureCategory, nextAction:d.nextAction,
      independentlyVerifiedHere:false};
  } catch (error) {
    return {status:error.code === 'ENOENT' ? 'NO_BASELINE_FAILURE_RECORDED' : 'UNREADABLE'};
  }
}

function inspectMobileBuilder(base) {
  const directory = path.join(base, 'mobile-builder');
  const state = directoryState(directory);
  if (state === 'MISSING') return { status: 'NO_BUILD_REPORT',
    artifactVerifiedHere: false, deviceInstallationVerifiedHere: false };
  if (state !== 'PRESENT') return { status: 'UNREADABLE',
    artifactVerifiedHere: false, deviceInstallationVerifiedHere: false };
  try {
    const report = readBoundedJson(path.join(directory, 'latest-build.json'), MAX_BUILD_REPORT_BYTES);
    if (report?.schemaVersion !== 1 || typeof report.status !== 'string' ||
        !/^[A-Z][A-Z0-9_]{1,95}$/.test(report.status) ||
        (report.commit !== undefined && !SHA40.test(report.commit)) ||
        (report.apkSha256 !== undefined && !SHA64.test(report.apkSha256)))
      throw Error('INVALID_MOBILE_BUILDER_REPORT');
    // A historical report, even a successful build report, is not proof of
    // device installation or a currently running build.
    return { status: report.status,
      commit: report.commit ?? null,
      artifactSha256: report.apkSha256 ?? null,
      artifactVerifiedHere: false, deviceInstallationVerifiedHere: false };
  } catch (error) {
    return { status: error.code === 'ENOENT' ? 'NO_BUILD_REPORT' : 'UNREADABLE',
      artifactVerifiedHere: false, deviceInstallationVerifiedHere: false };
  }
}

/** Read-only summary for the existing Teamleader status interface.
 * No Git operations, shell execution, model loading, repairs or writes.
 * It never elevates a stored report into verified execution evidence.
 */
export function readLocalIntegrationStatus({ root, stateBase = path.join(os.homedir(), '.local', 'state', 'device-ai-continuum') } = {}) {
  if (typeof root !== 'string' || !path.isAbsolute(root))
    throw Error('CONTINUUM_STATUS_INVALID_ROOT');
  if (typeof stateBase !== 'string' || !path.isAbsolute(stateBase))
    throw Error('CONTINUUM_STATUS_INVALID_STATE');
  const canonicalRoot = fs.realpathSync(root);
  if (!fs.statSync(canonicalRoot).isDirectory()) throw Error('CONTINUUM_STATUS_INVALID_ROOT');
  const state = directoryState(stateBase);
  if (state !== 'PRESENT') return {
    schema: 'continuum-local-integration-status', version: 1,
    state: state === 'MISSING' ? 'NO_LOCAL_STATE' : 'UNREADABLE',
    buildLease: { status: 'UNKNOWN', executionConfirmed: false },
    ansk: { status: 'UNKNOWN', receiptFiles: null, authority: 'SHADOW_ONLY', independentlyVerifiedHere: false },
    mobileBuilder: { status: 'UNKNOWN', artifactVerifiedHere: false, deviceInstallationVerifiedHere: false }
  };
  return {
    schema: 'continuum-local-integration-status', version: 1,
    state: 'REPORTS_ONLY',
    buildLease: inspectLease(stateBase, canonicalRoot),
    ansk: {...inspectAnsk(stateBase), lastBaselineFailure: inspectAnskBaseline(stateBase)},
    mobileBuilder: inspectMobileBuilder(stateBase)
  };
}
