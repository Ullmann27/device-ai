#!/usr/bin/env bash
# Read-only Android package/APK metadata. This does not back up private app data.
set -euo pipefail
exec node --input-type=module - "$@" <<'NODE'
import {spawnSync} from 'node:child_process';
import {existsSync, statSync, createReadStream} from 'node:fs';
import {createHash} from 'node:crypto';
import {isAbsolute, normalize} from 'node:path';

const usage = 'Usage: bash scripts/continuum-app-inventory.sh [--expected-count 5] [--package PACKAGE]...\nWrites metadata JSON to stdout; never reads private app data or signing keys.';
const requested = new Set();
let expectedCount = null;
const args = process.argv.slice(2);
for (let i = 0; i < args.length; i++) {
  if (args[i] === '--help') { process.stdout.write(usage + '\n'); process.exit(0); }
  if (args[i] === '--package' && /^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+$/.test(args[i + 1] || '')) {
    requested.add(args[++i]);
  } else if (args[i] === '--expected-count' && /^(?:[1-9]|[1-4][0-9]|50)$/.test(args[i + 1] || '')) {
    expectedCount = Number(args[++i]);
  } else { process.stderr.write(usage + '\nInvalid argument.\n'); process.exit(64); }
}
if (requested.size > 50) { process.stderr.write('At most 50 explicit packages are permitted.\n'); process.exit(64); }

// Android app UIDs include the current user's ID; never request another user.
const androidUserId = Math.floor((process.getuid?.() ?? 0) / 100000);
const environment = Object.fromEntries(['PATH', 'HOME', 'PREFIX', 'JAVA_HOME', 'ANDROID_HOME', 'ANDROID_SDK_ROOT', 'TMPDIR']
  .filter(key => process.env[key]).map(key => [key, process.env[key]]));
environment.LANG = 'C'; environment.LC_ALL = 'C';
const androidCommand = name => existsSync('/system/bin/' + name) ? '/system/bin/' + name : name;
const failures = [];
function run(command, arguments_) {
  const result = spawnSync(command, arguments_, {
    encoding: 'utf8', timeout: 15000, killSignal: 'SIGKILL', maxBuffer: 512 * 1024,
    env: environment, stdio: ['ignore', 'pipe', 'pipe'], shell: false
  });
  const text = result.stdout || '';
  const ok = !result.error && result.status === 0 &&
    !/SecurityException|Permission denied|Failed transaction|Exception occurred/i.test(text);
  // Never include raw stdout/stderr from failed tools: Android/tool diagnostics
  // can contain unrelated paths or credentials from the caller's environment.
  return {ok, text: ok ? text : '', exitCode: result.status,
    reason: result.error?.code === 'ETIMEDOUT' ? 'TIMEOUT' :
      result.error?.code === 'ENOENT' ? 'TOOL_UNAVAILABLE' : ok ? null : 'DENIED_OR_FAILED'};
}
function observe(command, arguments_, result) {
  failures.push({command: command.split('/').at(-1), arguments: arguments_,
    status: result.ok ? 'SUCCESS' : result.reason, exitCode: result.exitCode});
}

const packages = new Set(requested);
let enumeration = 'UNAVAILABLE';
for (const name of ['cmd', 'pm']) {
  const arguments_ = [...(name === 'cmd' ? ['package'] : []), 'list', 'packages', '--user', String(androidUserId)];
  const result = run(androidCommand(name), arguments_);
  observe(name, arguments_, result);
  const listed = result.text.split(/\r?\n/).map(line => /^package:([A-Za-z][A-Za-z0-9_.]*)$/.exec(line)?.[1]).filter(Boolean);
  if (result.ok && listed.length) {
    enumeration = 'OBSERVED_CURRENT_USER';
    for (const value of listed) if (/^at\.heinz\.deviceai(?:\.|$)/.test(value)) packages.add(value);
    break;
  }
}
if (packages.size > 50) { process.stderr.write('Package inventory exceeds the 50-package bound.\n'); process.exit(65); }

const expectedCertificates = {
  'at.heinz.deviceai.mentorpaired': '2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc',
  'at.heinz.deviceai.continuumlocal': '6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23'
};
const privateCategories = ['ownerFacts', 'conversations', 'learningWeights', 'checkpoints',
  'knowledgeSources', 'researchRecords', 'modelImports', 'settings', 'backlog'];
async function inspectApk(file, includeVersion) {
  const report = {path: file, status: 'UNAVAILABLE', sha256: null,
    certificateSha256: [], signatureStatus: 'UNKNOWN', versionCode: null, versionName: null};
  if (!isAbsolute(file) || normalize(file) !== file || !file.endsWith('.apk') || /[\u0000-\u001f]/.test(file)) {
    report.status = 'REJECTED_NON_APK_PATH'; return report;
  }
  let before;
  try { before = statSync(file); } catch { report.status = 'APK_NOT_READABLE'; return report; }
  if (!before.isFile() || before.size > 512 * 1024 * 1024) {
    report.status = 'APK_SIZE_OR_TYPE_UNSUPPORTED'; return report;
  }
  try {
    const hash = createHash('sha256');
    for await (const chunk of createReadStream(file)) hash.update(chunk);
    report.sha256 = hash.digest('hex');
  } catch { report.status = 'APK_NOT_READABLE'; return report; }
  const signed = run('apksigner', ['verify', '--print-certs', file]);
  const certs = [...signed.text.matchAll(/^Signer #\d+ certificate SHA-256 digest:\s*([a-f0-9]{64})\s*$/gmi)]
    .map(match => match[1].toLowerCase());
  report.signatureStatus = signed.ok && certs.length ? 'VERIFIED' : signed.reason || 'UNRECOGNIZED_OUTPUT';
  report.certificateSha256 = signed.ok ? [...new Set(certs)].sort() : [];
  if (includeVersion) {
    const metadata = run('aapt2', ['dump', 'badging', file]);
    const fields = /^package:\s+name='([^']+)'\s+versionCode='([0-9]+)'\s+versionName='([^']*)'/m.exec(metadata.text);
    if (metadata.ok && fields) {
      report.packageId = fields[1]; report.versionCode = fields[2]; report.versionName = fields[3];
    }
  }
  try {
    const after = statSync(file);
    if (after.ino !== before.ino || after.size !== before.size || after.mtimeMs !== before.mtimeMs) {
      report.status = 'CHANGED_DURING_INSPECTION';
      report.sha256 = null; report.certificateSha256 = []; report.signatureStatus = 'UNKNOWN';
      report.versionCode = null; report.versionName = null;
      return report;
    }
  } catch { report.status = 'CHANGED_DURING_INSPECTION'; return report; }
  report.status = 'READABLE_APK';
  return report;
}

const rows = [];
for (const packageId of [...packages].sort()) {
  const row = {packageId, requestedExplicitly: requested.has(packageId), installationStatus: 'UNKNOWN',
    versionCode: null, versionName: null, apks: [], certificateStatus: 'UNKNOWN',
    expectedCertificateSha256: expectedCertificates[packageId] || null,
    expectedCertificateSource: expectedCertificates[packageId] ? 'OWNER_HANDOFF_20261028' : null,
    expectedCertificateMatches: null,
    privateDataAccess: 'NOT_INSPECTED_ANDROID_SANDBOX',
    privateData: Object.fromEntries(privateCategories.map(key => [key, 'UNKNOWN'])),
    exportImportAvailability: 'REQUIRES_SOURCE_APP_UI_REVIEW', migrationStatus: 'NOT_PERFORMED'};
  const arguments_ = ['package', 'path', '--user', String(androidUserId), packageId];
  const result = run(androidCommand('cmd'), arguments_);
  observe('cmd', arguments_, result);
  const paths = [...new Set(result.text.split(/\r?\n/).map(line => /^package:(.+)$/.exec(line)?.[1]).filter(Boolean))];
  if (result.ok && paths.length && paths.length <= 32) {
    row.installationStatus = 'PACKAGE_PATH_OBSERVED';
    const ordered = [...paths].sort((a, b) => Number(b.endsWith('/base.apk')) - Number(a.endsWith('/base.apk')) || a.localeCompare(b));
    for (let i = 0; i < ordered.length; i++) row.apks.push(await inspectApk(ordered[i], i === 0));
    const base = row.apks[0];
    if (base.status === 'READABLE_APK' && base.packageId === packageId) {
      row.versionCode = base.versionCode; row.versionName = base.versionName;
    }
    const signerSets = row.apks.map(apk => apk.certificateSha256.join(','));
    if (row.apks.every(apk => apk.status === 'READABLE_APK' && apk.signatureStatus === 'VERIFIED')) {
      row.certificateStatus = signerSets.every(value => value === signerSets[0]) ? 'VERIFIED_SAME_SIGNERS' : 'SIGNER_MISMATCH';
      if (row.expectedCertificateSha256) row.expectedCertificateMatches =
        row.certificateStatus === 'VERIFIED_SAME_SIGNERS' &&
        row.apks[0].certificateSha256.length === 1 && row.apks[0].certificateSha256[0] === row.expectedCertificateSha256;
    }
  }
  rows.push(row);
}
const observed = rows.filter(row => row.installationStatus === 'PACKAGE_PATH_OBSERVED').length;
process.stdout.write(JSON.stringify({schemaVersion: 1, observedAtUtc: new Date().toISOString(),
  mode: 'READ_ONLY_PACKAGE_METADATA', androidUserId,
  enumeration: {status: enumeration, scope: 'at.heinz.deviceai namespace for current user, plus explicit package IDs',
    isExhaustiveAcrossOtherNamespacesOrUsers: false},
  expectedInstallationCount: expectedCount, observedInstallationCount: observed,
  reportedCountMatched: expectedCount === null ? null : observed === expectedCount,
  fullDataInventoryComplete: false, migrationStatus: 'NOT_PERFORMED', commandObservations: failures,
  packages: rows}, null, 2) + '\n');
NODE
