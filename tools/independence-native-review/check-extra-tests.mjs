// Reviewer-only fixed-profile check. Never submits a task or invokes a model.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { fileURLToPath, pathToFileURL } from 'node:url';

const [sourceArg, sourceSha, patchArg, patchSha, outArg, configArg] = process.argv.slice(2);
assert.ok(sourceArg && /^[a-f0-9]{40}$/.test(sourceSha ?? '') &&
  patchArg && /^[a-f0-9]{64}$/.test(patchSha ?? '') && outArg && configArg);
const source = fs.realpathSync(sourceArg), patch = fs.realpathSync(patchArg);
const out = path.resolve(outArg), hash = bytes => createHash('sha256').update(bytes).digest('hex');
function git(cwd, ...args) {
  const result = spawnSync('git', ['-C', cwd, ...args], { encoding: 'utf8', shell: false });
  assert.equal(result.status, 0, result.stderr);
  return result.stdout.trim();
}
assert.equal(git(source, 'rev-parse', 'HEAD'), sourceSha);
assert.equal(git(source, 'status', '--porcelain'), '');
assert.equal(hash(fs.readFileSync(patch)), patchSha);
assert.ok(!fs.existsSync(out), 'Do not overwrite prior independent execution evidence');
const here = path.dirname(fileURLToPath(import.meta.url));
const frozen = path.join(here, 'frozen/s01');
const manifest = JSON.parse(fs.readFileSync(path.join(frozen, 'manifest.json')));
for (const [name, expected] of Object.entries(manifest.files)) {
  const bytes = fs.readFileSync(path.join(frozen, name));
  assert.equal(bytes.length, expected.bytes); assert.equal(hash(bytes), expected.sha256);
}
const materialization = JSON.parse(fs.readFileSync(path.join(here,
  '../../docs/research/omega/independence/native-review/materialization.json')));
assert.equal(git(materialization.repository, 'rev-parse', 'HEAD'), materialization.commit);
assert.equal(git(materialization.repository, 'status', '--porcelain'), '');
fs.mkdirSync(out, { recursive: true, mode: 0o700 });
const repository = path.join(out, 'fixture');
const clone = spawnSync('git', ['clone', '-q', '--no-hardlinks', '--',
  materialization.repository, repository], { encoding: 'utf8', shell: false });
assert.equal(clone.status, 0, clone.stderr);
git(repository, 'apply', '--check', '--', patch);
git(repository, 'apply', '--index', '--', patch);
assert.deepEqual(git(repository, 'diff', '--cached', '--name-only').split('\n'), ['lib/subject.mjs']);
const ownerTest = path.join(repository, '.github/continuum/owner.test.mjs');
assert.equal(hash(fs.readFileSync(ownerTest)), materialization.ownerTestSha256);
const extra = fs.readFileSync(path.join(frozen, 'independent.test.mjs'), 'utf8');
assert.equal(extra.split("from './subject.mjs'").length, 2);
const materializedExtra = extra.replace("from './subject.mjs'", "from '../../lib/subject.mjs'");
fs.writeFileSync(path.join(repository, '.github/continuum/independent.test.mjs'),
  materializedExtra, { flag: 'wx' });
git(repository, 'add', '--', '.github/continuum/independent.test.mjs');
git(repository, '-c', 'user.name=Continuum independent reviewer', '-c',
  'user.email=review@localhost', 'commit', '-q', '-m', 'Exact returned patch plus frozen extra checks');
const { createIsolatedExecutor, loadIsolationConfig, validIsolationEvidence } = await import(
  pathToFileURL(path.join(source, '.github/continuum/isolated-executor.mjs')));
const { parseJUnit } = await import(pathToFileURL(path.join(source,
  '.github/continuum/repair-diagnostics.mjs')));
const config = loadIsolationConfig(configArg, { sourceRoot: repository });
assert.ok(config.memoryMb <= 256 && config.pids <= 64 && config.cpus === 1 && config.timeoutMs <= 10000);
const executor = createIsolatedExecutor(config);
const result = executor.execute('node-continuum', repository, path.join(out, 'runner'));
assert.ok(validIsolationEvidence(result.isolation, 'node-continuum'));
assert.equal(result.isolation.teardownConfirmed, true);
const tests = result.junitFiles.map(file => ({ file, sha256: hash(fs.readFileSync(file)),
  result: parseJUnit(fs.readFileSync(file, 'utf8'), repository) }));
const count = field => tests.reduce((sum, item) => sum + item.result[field], 0);
const passed = result.status === 0 && !result.error && !result.signal &&
  count('executed') === 10 && count('failed') === 0 && count('skipped') === 0 &&
  count('placeholders') === 0 && tests.every(item => item.result.evidenceComplete);
const report = { schema: 'independence-s01-extra-check-v1', sourceSha,
  authority: 'REVIEWER_FIXED_PROFILE_ONLY_NOT_CANONICAL_BOT_RECEIPT',
  originalFixtureCommit: materialization.commit, patchSha256: patchSha,
  checkedCommit: git(repository, 'rev-parse', 'HEAD'),
  checkedTree: git(repository, 'rev-parse', 'HEAD^{tree}'),
  ownerTestSha256: hash(fs.readFileSync(ownerTest)),
  extraTestSha256: hash(materializedExtra),
  sourceFileSha256: hash(fs.readFileSync(path.join(repository, 'lib/subject.mjs'))),
  modelCalls: 0, verdict: passed ? 'PASS' : 'FAIL', counts: {
    total: count('total'), executed: count('executed'), failed: count('failed'), skipped: count('skipped')
  }, tests, result };
fs.writeFileSync(path.join(out, 'result.json'), JSON.stringify(report, null, 2) + '\n', { flag: 'wx' });
console.log(JSON.stringify({ verdict: report.verdict, counts: report.counts,
  result: path.join(out, 'result.json'), modelCalls: 0 }));
process.exitCode = passed ? 0 : 1;
