#!/usr/bin/env node
// Read-only, fail-closed v21 source inventory check. This never signs or deploys.
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { lstatSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const target = join(root, 'docs/evidence/astra-0320-20261024/v21-source-proposal.json');
const SOURCE = 'd7429145ad5dff64364ed47f9ceb0c07a5a8ed85';
const MANIFEST_SHA = '134e847b8f2fad2f8131cdf3e962679eb1630ed12801973ad0f6cbb0799e177d';
const SIGNER = '2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc';
const REVIEWED_DISPATCHER = 'scripts/render-mentor-preview.sh';
const REVIEWED_DISPATCHER_BLOB = '6963c961a98dddd0a2b58c93072402fd0b10dc60';

const fail = message => { process.stderr.write('OWNER_V21_BLOCKED: ' + message + '\n'); process.exit(69); };
const git = (...args) => execFileSync('git', args, { cwd: root, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim();
const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');

try {
  // No environment default means no owner consent. Source and release-builder approvals are different.
  if (process.env.CONTINUUM_OWNER_V21_APPROVED_SOURCE_SHA !== SOURCE)
    fail('Exact original tested Android source SHA not approved');
  if (process.env.CONTINUUM_OWNER_V21_APPROVED_MANIFEST_SHA256 !== MANIFEST_SHA)
    fail('Exact release source inventory SHA not approved');
  const bytes = readFileSync(target);
  if (sha256(bytes) !== MANIFEST_SHA) fail('Release inventory was modified');
  const data = JSON.parse(bytes.toString('utf8'));
  if (data.schemaVersion !== 1 || data.state !== 'PROPOSAL_NOT_APPROVAL' ||
      data.sourceHead !== SOURCE || data.package !== 'at.heinz.deviceai.mentorpaired' ||
      data.versionCode !== 21 || data.versionName !== '0.3.20' ||
      data.certificateSha256 !== SIGNER)
    fail('Inventory metadata is not the approved v21 candidate');
  if (!Array.isArray(data.files) || data.files.length !== 245)
    fail('Incomplete v21 inventory');

  const seen = new Set();
  for (const item of data.files) {
    const p = item.path;
    if (typeof p !== 'string' || !/^[A-Za-z0-9_.\/-]+$/.test(p) ||
        p.startsWith('/') || p.split('/').includes('..') || seen.has(p) ||
        typeof item.blob !== 'string' || !/^[a-f0-9]{40}$/.test(item.blob) ||
        typeof item.sha256 !== 'string' || !/^[a-f0-9]{64}$/.test(item.sha256))
      fail('Malformed, duplicate or redirected inventory path');
    seen.add(p);
    let at = root;
    for (const [index, segment] of p.split('/').entries()) {
      at = join(at, segment);
      const info = lstatSync(at);
      if (info.isSymbolicLink() || (index < p.split('/').length - 1 && !info.isDirectory()) ||
          (index === p.split('/').length - 1 && !info.isFile()))
        fail('Refused source redirection: ' + p);
    }
    const actualBlob = git('hash-object', '--', p);
    if (p === REVIEWED_DISPATCHER) {
      // This is the ONLY original manifest input intentionally changed by
      // the separate owner-approved builder. The old source must remain
      // explicitly present in the manifest, while the new dispatcher is
      // pinned byte-for-byte by a distinct reviewed Git blob.
      if (item.blob !== '5666738634c2aba4607adf7136fb377be26d93fb' ||
          actualBlob !== REVIEWED_DISPATCHER_BLOB)
        fail('Unapproved or modified v21 release dispatcher');
    } else if (sha256(readFileSync(at)) !== item.sha256 || actualBlob !== item.blob) {
      fail('Changed or damaged release source: ' + p);
    }
  }
  const allMainPaths = git('ls-files', '-z', '--', 'app/build.gradle.kts', 'app/src/main')
    .split('\u0000').filter(Boolean);
  const missing = allMainPaths.filter(p => !seen.has(p));
  if (missing.length) fail('New or missing unattested Android inputs: ' + missing.slice(0, 5).join(', '));
  if (git('diff', '--name-only', 'HEAD', '--', 'app', 'scripts', 'backend', '.github/continuum'))
    fail('Tracked release inputs differ from HEAD');
  const untracked = git('ls-files', '--others', '--exclude-standard', '--',
    'app/src', 'scripts', 'backend/src', 'backend/test', '.github/continuum', '.github/workflows');
  if (untracked) fail('Untracked release executable or Android source');
  process.stdout.write(JSON.stringify({
    result: 'OWNER_V21_SOURCE_INVENTORY_VERIFIED_NOT_SIGNED',
    source: SOURCE, manifestSha256: MANIFEST_SHA,
    filesChecked: seen.size, package: data.package, versionCode: 21,
    expectedCertificateSha256: SIGNER
  }) + '\n');
} catch (error) {
  fail(error instanceof Error ? error.message : 'Unknown source verification error');
}
