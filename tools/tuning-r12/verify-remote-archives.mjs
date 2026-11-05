import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { resolve } from 'node:path';
import assert from 'node:assert/strict';

const root = resolve(process.argv[2] || '.');
const archiveSpecs = [
  ['R8', '464c8c1e676d12640c5b737c6a34f06d3837307e274e8adb5035dbf4d22a02ac', ['StepperBench.kt','aa.csv','ab.csv']],
  ['R9', 'cefa10199763df4033abf0b221e35837a3eb161052a4d3e734d60c5645f0738c', ['R9Bench.kt','r9-aa.csv','r9-ab.csv']],
];

function decodeVerifiedBase64(value, expectedSha) {
  const b64 = value.trimEnd();
  assert.match(b64, /^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/);
  const decoded = Buffer.from(b64, 'base64');
  assert.equal(decoded.toString('base64'), b64, 'noncanonical Base64');
  const sha = createHash('sha256').update(decoded).digest('hex');
  assert.equal(sha, expectedSha, 'archive SHA-256 mismatch');
  return decoded;
}

function validateCsv(text) {
  const lines = text.trimEnd().split(/\r?\n/);
  assert.equal(lines.shift(), 'phase,scenario,pair,arm,cpu_ns,alloc_bytes,checksum');
  assert.equal(lines.length, 156);
  const groups = new Map();
  let measured = 0, warmup = 0;
  for (const line of lines) {
    const [phase, scenario, pairStr, arm, cpuStr, allocStr, checksum] = line.split(',');
    assert.ok(phase === 'warmup' || phase === 'measured');
    assert.ok(arm === 'A' || arm === 'B');
    for (const v of [pairStr, cpuStr, allocStr]) assert.match(v, /^\d+$/);
    assert.match(checksum, /^-?\d+$/);
    const pair = Number(pairStr);
    assert.ok(phase === 'warmup' ? pair >= 0 && pair < 4 : pair >= 4 && pair < 13);
    const key = `${scenario}/${phase}/${pair}`;
    if (!groups.has(key)) groups.set(key, new Map());
    const group = groups.get(key);
    assert.ok(!group.has(arm), 'duplicate arm');
    group.set(arm, checksum);
    phase === 'warmup' ? warmup++ : measured++;
  }
  assert.equal(groups.size, 78);
  assert.equal(warmup, 48);
  assert.equal(measured, 108);
  for (const group of groups.values()) {
    assert.equal(group.size, 2);
    assert.equal(group.get('A'), group.get('B'), 'A/B checksum mismatch');
  }
  return { rows: lines.length, warmup, measured, matchedPairs: groups.size };
}

function auditArchive([round, expectedSha, files]) {
  const path = resolve(root, `docs/evidence/tuning-r11-20261105/${round}-evidence.tar.xz.b64`);
  const decoded = decodeVerifiedBase64(readFileSync(path, 'utf8'), expectedSha);
  const opts = { input: decoded, encoding: 'utf8', maxBuffer: 1_000_000 };
  const contents = execFileSync('tar', ['-tJf', '-'], opts).trimEnd().split('\n');
  assert.deepEqual(contents.sort(), [...files].sort());
  let measured = 0, warmup = 0, pairs = 0;
  for (const file of files.filter((f) => f.endsWith('.csv'))) {
    const csv = execFileSync('tar', ['-xOJf', '-', file], opts);
    const result = validateCsv(csv);
    measured += result.measured;
    warmup += result.warmup;
    pairs += result.matchedPairs;
  }
  return { round, archiveSha256: expectedSha, bytes: decoded.length,
    measured, warmup, matchedPairs: pairs, files: contents };
}

const results = archiveSpecs.map(auditArchive);
assert.equal(results.reduce((x, r) => x + r.measured + r.warmup, 0), 624);
for (const [round, sha] of archiveSpecs) {
  const source = readFileSync(resolve(root, `docs/evidence/tuning-r11-20261105/${round}-evidence.tar.xz.b64`), 'utf8');
  const corrupted = `${source[0] === 'A' ? 'B' : 'A'}${source.slice(1)}`;
  assert.throws(() => decodeVerifiedBase64(corrupted, sha), /archive SHA-256 mismatch/);
}
console.log(JSON.stringify({ status: 'PASS', results, negativeShaControls: 2 }));
