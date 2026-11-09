import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8');
const ledger = JSON.parse(read('docs/integration/CODE52_NO_ORPHANS_LEDGER.json'));
const jsonl = (relative) => read(relative).split(/\r?\n/)
  .filter((line) => line.startsWith('{')).map((line) => JSON.parse(line));
const prs = jsonl(ledger.snapshot.pr_path);
const branches = jsonl(ledger.snapshot.branch_path);
const sha = /^[a-f0-9]{40}$/;
const statuses = new Set([
  'AUDIT_REQUIRED', 'RESEARCH_ONLY', 'SOURCE_STAGED', 'PARTIAL_SOURCE_CONNECTED',
  'ALREADY_PRESENT_SOURCE', 'BLOCKED', 'SUPERSEDED', 'RELEASE_BASELINE', 'DEVICE_VERIFIED'
]);

test('complete PR and branch inventories are kept, never silently truncated', () => {
  assert.equal(ledger.schema, 'CONTINUUM_NO_ORPHANS_LEDGER_V1');
  assert.ok(prs.length >= 627);
  assert.ok(branches.length >= 727);
  assert.equal(prs.length, ledger.snapshot.prs, 'update ledger count after each inventory refresh');
  assert.equal(branches.length, ledger.snapshot.branches, 'update ledger count after each refresh');
  assert.equal(new Set(prs.map((p) => p.number)).size, prs.length);
  assert.equal(new Set(branches.map((p) => p.branch)).size, branches.length);
  for (const p of prs) {
    assert.match(p.head_sha, sha);
    assert.ok(p.audit_state, 'PR without explicit audit status: #' + p.number);
  }
  for (const b of branches) {
    assert.match(b.head_sha, sha);
    assert.ok(b.audit_state, 'Branch without explicit audit status: ' + b.branch);
  }
});

test('all recent PRs, including post-snapshot additions, have actionable integration owners', () => {
  const known = new Map(ledger.entries.map((entry) => [entry.pr, entry]));
  assert.equal(known.size, ledger.entries.length, 'duplicate PRs in integration ledger');
  const current = prs.filter((p) => p.priority_recent);
  assert.ok(current.length >= 29, 'recent queue vanished without evidence');
  for (const p of current) {
    assert.ok(known.has(p.number), 'recent PR orphaned: #' + p.number);
    assert.equal(known.get(p.number).branch, p.head_branch);
  }
  assert.ok(known.has(672), 'project isolation cleanup orphaned');
  assert.ok(known.has(673), 'response router repair orphaned');
  for (const entry of ledger.entries) {
    assert.ok(Number.isInteger(entry.pr) && entry.pr > 0);
    assert.match(entry.checkpoint_sha, sha);
    assert.match(entry.branch, /^[a-z0-9][a-z0-9/_-]{2,}$/i);
    assert.ok(statuses.has(entry.status), 'invalid integration status: #' + entry.pr);
    assert.equal(entry.integrator, 'CODE52_MASTER_INTEGRATOR');
    assert.equal(entry.target_pr, ledger.integration_target.pr);
    assert.ok(entry.next_step.length >= 30, 'no next implementation step: #' + entry.pr);
    assert.equal(entry.source, 'https://github.com/Ullmann27/device-ai/pull/' + entry.pr);
  }
});

test('staged or research-only patches never claim release or device success', () => {
  assert.equal(ledger.release_baseline.code, 51);
  assert.equal(ledger.release_baseline.source_sha,
    '6c23d295e2fca2d4e5950a866547733ec5ff22b0');
  assert.equal(ledger.release_baseline.package, 'at.heinz.deviceai.continuumlocal');
  assert.equal(ledger.release_baseline.immutable, true);
  assert.equal(ledger.integration_target.release_allowed, false);
  for (const entry of ledger.entries) {
    if (entry.status === 'DEVICE_VERIFIED') {
      assert.equal(entry.device_verified, true, 'missing device proof');
      assert.match(entry.device_evidence_sha, sha);
      assert.ok(entry.device_evidence_url, 'missing owner device evidence');
    } else {
      assert.equal(entry.device_verified, false, 'unearned device success: #' + entry.pr);
    }
  }
});
