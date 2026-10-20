import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import { readLocalIntegrationStatus } from './local-integration-status.mjs';

function scratch(action) {
  const d = fs.mkdtempSync(path.join(os.tmpdir(), 'continuum-status-'));
  const root = path.join(d, 'source');
  const stateBase = path.join(d, 'state');
  fs.mkdirSync(root);
  try { return action({ root, stateBase, d }); }
  finally { fs.rmSync(d, { recursive: true, force: true }); }
}

const report = { schemaVersion: 1, status: 'EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED',
  commit: 'a'.repeat(40), apkSha256: 'b'.repeat(64),
  productionReady: true, deviceInstallationVerified: true };

function ensure(base, name) {
  const dir = path.join(base, name);
  fs.mkdirSync(dir, { recursive: true });
  return dir;
}

test('no state cannot be confused with successful build or repair', () => scratch(({ root, stateBase }) => {
  const result = readLocalIntegrationStatus({ root, stateBase });
  assert.equal(result.schema, 'continuum-local-integration-status');
  assert.equal(result.state, 'NO_LOCAL_STATE');
  assert.equal(result.mobileBuilder.deviceInstallationVerifiedHere, false);
  assert.equal(result.ansk.independentlyVerifiedHere, false);
}));

test('historical builder claims cannot advertise a verified installation', () => scratch(({ root, stateBase }) => {
  fs.writeFileSync(path.join(ensure(stateBase, 'mobile-builder'), 'latest-build.json'), JSON.stringify(report));
  const result = readLocalIntegrationStatus({ root, stateBase });
  assert.equal(result.state, 'REPORTS_ONLY');
  assert.equal(result.mobileBuilder.status, report.status);
  assert.equal(result.mobileBuilder.commit, report.commit);
  assert.equal(result.mobileBuilder.artifactSha256, report.apkSha256);
  assert.equal(result.mobileBuilder.deviceInstallationVerifiedHere, false);
  assert.equal(result.mobileBuilder.artifactVerifiedHere, false);
}));

test('malformed or symlinked builder report fails closed', () => scratch(({ root, stateBase, d }) => {
  const dir = ensure(stateBase, 'mobile-builder');
  const file = path.join(dir, 'latest-build.json');
  fs.writeFileSync(file, '{ invalid');
  assert.equal(readLocalIntegrationStatus({ root, stateBase }).mobileBuilder.status, 'UNREADABLE');
  fs.unlinkSync(file);
  const privateFile = path.join(d, 'private.txt');
  fs.writeFileSync(privateFile, JSON.stringify(report));
  fs.symlinkSync(privateFile, file);
  assert.equal(readLocalIntegrationStatus({ root, stateBase }).mobileBuilder.status, 'UNREADABLE');
}));

test('build lease is reported but process liveness is never fabricated', () => scratch(({ root, stateBase }) => {
  const dir = ensure(stateBase, 'build-leases');
  const name = crypto.createHash('sha256').update(root).digest('hex') + '.lock';
  const lock = { schema: 'continuum-local-build-lease', version: 1, root,
    pid: 99999999, startedAt: 100, hostname: 'offline', nonce: 'a'.repeat(32) };
  fs.writeFileSync(path.join(dir, name), JSON.stringify(lock));
  assert.deepEqual(readLocalIntegrationStatus({ root, stateBase }).buildLease,
    { status: 'LOCK_PRESENT_UNVERIFIED', executionConfirmed: false });
  fs.writeFileSync(path.join(dir, name), '{}');
  assert.equal(readLocalIntegrationStatus({ root, stateBase }).buildLease.status, 'UNREADABLE');
}));

test('ANSK files remain research-only and are not counted as verified repairs', () => scratch(({ root, stateBase }) => {
  const dir = ensure(stateBase, 'ansk-lab/shadow-recipes');
  fs.writeFileSync(path.join(dir, 'a'.repeat(64) + '.json'), '{}');
  const result = readLocalIntegrationStatus({ root, stateBase });
  assert.equal(result.ansk.status, 'QUARANTINED_RECEIPTS_PRESENT');
  assert.equal(result.ansk.receiptFiles, 1);
  assert.equal(result.ansk.authority, 'SHADOW_ONLY');
  assert.equal(result.ansk.independentlyVerifiedHere, false);
}));

test('unsafe ANSK file names and symlinked directories are rejected', () => scratch(({ root, stateBase, d }) => {
  const dir = ensure(stateBase, 'ansk-lab/shadow-recipes');
  fs.writeFileSync(path.join(dir, 'untrusted.json'), '{}');
  assert.equal(readLocalIntegrationStatus({ root, stateBase }).ansk.status, 'REVIEW_REQUIRED');
  fs.rmSync(path.join(stateBase, 'ansk-lab'), { recursive: true });
  fs.symlinkSync(d, path.join(stateBase, 'ansk-lab'));
  assert.equal(readLocalIntegrationStatus({ root, stateBase }).ansk.status, 'UNREADABLE');
}));

test('source and state arguments are absolute paths only', () => scratch(({ root, stateBase }) => {
  assert.throws(() => readLocalIntegrationStatus({ root: 'relative', stateBase }), /INVALID_ROOT/);
  assert.throws(() => readLocalIntegrationStatus({ root, stateBase: 'relative' }), /INVALID_STATE/);
}));
