#!/usr/bin/env node
import { execFileSync } from 'node:child_process';
import { lstatSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

const root = execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim();
const script = readFileSync(join(root, 'scripts/render-mentor-owner-update-v19.sh'), 'utf8');
const section = script.match(/done <<'APPROVED_RELEASE_SOURCES'\r?\n([\s\S]*?)\r?\nAPPROVED_RELEASE_SOURCES/);
if (!section) throw new Error('The owner release source gate is absent or malformed');
const entries = section[1].trim().split(/\r?\n/).map(line => line.trim().split(/\s+/));
if (entries.length !== 22) throw new Error(`Expected 22 source attestations, got ${entries.length}`);
const seen = new Set();
const files = entries.map(([expected, source, ...extra]) => {
  if (extra.length || !/^[a-f0-9]{40}$/.test(expected) ||
      !/^[A-Za-z0-9_.\/-]+$/.test(source ?? '') ||
      source.startsWith('/') || source.split('/').includes('..') || seen.has(source)) {
    throw new Error('Malformed, unsafe, or duplicate attested path');
  }
  seen.add(source);
  let actual = null;
  try {
    // lstat on only the final file misses symlinked parent directories.
    // Every path component must be a real directory except the final regular file.
    const parts = source.split('/');
    let current = root;
    for (let i = 0; i < parts.length; i++) {
      current = join(current, parts[i]);
      const item = lstatSync(current);
      if (item.isSymbolicLink() ||
          (i < parts.length - 1 && !item.isDirectory()) ||
          (i === parts.length - 1 && !item.isFile())) {
        throw new Error('Non-regular or redirected attested source path');
      }
    }
    actual = execFileSync('git', ['hash-object', '--', source],
      { cwd: root, encoding: 'utf8' }).trim();
  } catch { /* fail closed for symlinks, missing and unsupported path components */ }
  return { path: source, expected, actual, matches: actual === expected };
});
const head = execFileSync('git', ['rev-parse', '--verify', 'HEAD'],
  { cwd: root, encoding: 'utf8' }).trim();
const matched = files.filter(entry => entry.matches).length;
const git = (...args) => {
  try { return execFileSync('git', args, { cwd: root, encoding: 'utf8', maxBuffer: 64 << 20, stdio: ['ignore', 'pipe', 'ignore'] }).trim(); }
  catch { return ''; }
};
// What a reviewer must look at before re-attesting a source. A role is never an approval.
const roleOf = path => /(Test\.kt|\.test\.mjs)$/.test(path) ? 'TEST_ONLY'
  : /(BootstrapBrain|ContinuumMobileBuildPlan|ContinuumMobileResultPolicy|ContinuumMobileBuildResultReceiver|ContinuumWorkerChatRouter|ContinuumCcc1Bridge)\.kt$|^scripts\/continuum-owned-app\.mjs$/.test(path)
    ? 'SECURITY_REVIEW' : 'WIRING_OR_UI';
// Commits since the attested bytes were last this path's content, newest first (the reason for the deviation).
const changesSincePin = entry => {
  const out = [];
  for (const line of git('log', '--format=%H %s', '--', entry.path).split('\n').filter(Boolean)) {
    const [commit, ...subject] = line.split(' ');
    if (git('rev-parse', `${commit}:${entry.path}`) === entry.expected || out.length >= 30) break;
    out.push({ commit, subject: subject.join(' ') });
  }
  return out;
};
// Newest first-parent commit at which every attested path had its attested bytes.
const attested = new Map(files.map(entry => [entry.path, entry.expected]));
let attestedBaseCommit = null;
for (const commit of git('rev-list', '--first-parent', '--max-count=400', 'HEAD').split('\n').filter(Boolean)) {
  const blobs = new Map(git('ls-tree', commit, '--', ...attested.keys()).split('\n').filter(Boolean)
    .map(line => { const [meta, path] = line.split('\t'); return [path, meta.split(' ')[2]]; }));
  if ([...attested].every(([path, blob]) => blobs.get(path) === blob)) { attestedBaseCommit = commit; break; }
}
// APK sources added or changed since then that no attestation covers at all.
const unattestedApkSourceChanges = !attestedBaseCommit ? null :
  git('diff', '--name-status', '--no-renames', attestedBaseCommit, 'HEAD', '--', 'app/src/main').split('\n').filter(Boolean)
    .map(line => line.split('\t')).filter(([, path]) => !attested.has(path))
    .map(([status, path]) => ({ status, path, blob: status === 'D' ? null : git('rev-parse', `HEAD:${path}`) || null }));
const gateCommands = script.split(/\r?\n/).map(line => line.trim())
  .filter(line => /^no_signing_env node --test|^gradle /.test(line)).map(line => line.replace(/\s*\\$/, ''));
const sdkHome = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || '';
let sdkReady = false;
try { sdkReady = !!sdkHome && lstatSync(join(sdkHome, 'platforms', 'android-36')).isDirectory(); } catch { /* absent */ }
const report = { sourceHead: head, total: files.length, matched,
  reviewRequired: files.length - matched,
  status: matched === files.length ? 'SOURCE_PINS_MATCH' : 'OWNER_REVIEW_REQUIRED',
  files: files.filter(entry => !entry.matches).map(entry => ({ ...entry, role: roleOf(entry.path), changesSincePin: changesSincePin(entry) })),
  attestedBaseCommit, unattestedApkSourceChanges,
  releaseGate: { commands: gateCommands, androidSdk: sdkReady ? 'SDK_AVAILABLE' : 'SDK_UNAVAILABLE',
    note: 'Gradle tests, lint, assembleDebug, signing and the rollback check run only in the owner-approved Render build' } };
process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
if (matched !== files.length && !process.argv.includes('--report-only')) process.exitCode = 2;
