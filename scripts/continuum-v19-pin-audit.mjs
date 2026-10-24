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
  const fullPath = join(root, source);
  let actual = null;
  try {
    if (lstatSync(fullPath).isFile()) {
      actual = execFileSync('git', ['hash-object', '--', source],
        { cwd: root, encoding: 'utf8' }).trim();
    }
  } catch { /* fail closed for absent, unreadable and unsupported files */ }
  return { path: source, expected, actual, matches: actual === expected };
});
const head = execFileSync('git', ['rev-parse', '--verify', 'HEAD'],
  { cwd: root, encoding: 'utf8' }).trim();
const matched = files.filter(entry => entry.matches).length;
const report = { sourceHead: head, total: files.length, matched,
  reviewRequired: files.length - matched,
  status: matched === files.length ? 'SOURCE_PINS_MATCH' : 'OWNER_REVIEW_REQUIRED',
  files: files.filter(entry => !entry.matches) };
process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
if (matched !== files.length && !process.argv.includes('--report-only')) process.exitCode = 2;
