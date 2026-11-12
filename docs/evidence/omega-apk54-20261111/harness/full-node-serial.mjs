import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import os from 'node:os';
import {spawnSync} from 'node:child_process';
import {pathToFileURL} from 'node:url';
import {gunzipSync} from 'node:zlib';

const args = process.argv.slice(2);
if (args.length !== 6 || args[0] !== '--repo' || args[2] !== '--expected-sha' || args[4] !== '--out')
  throw Error('Use --repo PATH --expected-sha SHA --out NEW_DIRECTORY');
if (process.env.CONTINUUM_QA_DEPTH || process.env.CONTINUUM_REPAIR_DEPTH)
  throw Error('NESTED_QA_REFUSED');
const repo = fs.realpathSync(args[1]);
const expected = args[3];
const out = path.resolve(args[5]);
if (out === repo || out.startsWith(repo + path.sep) || fs.existsSync(out))
  throw Error('FRESH_OUTPUT_OUTSIDE_CHECKOUT_REQUIRED');
const hash = value => crypto.createHash('sha256').update(value).digest('hex');
function git(argv) {
  const r = spawnSync('git', argv, {cwd: repo, encoding: 'utf8', timeout: 10000, maxBuffer: 16 * 1024 * 1024});
  if (r.status !== 0) throw Error('GIT_METADATA_UNAVAILABLE');
  return r.stdout.trim();
}
function identity() {
  const names = git(['ls-files', '-z', '--cached', '--others', '--exclude-standard']).split('\0').filter(Boolean).sort();
  const inputsSha256 = {};
  for (const name of names) {
    const file = path.join(repo, name);
    const stat = fs.lstatSync(file, {throwIfNoEntry: false});
    inputsSha256[name] = !stat ? 'DELETED' : stat.isSymbolicLink() ? hash(fs.readlinkSync(file)) :
      stat.isFile() ? hash(fs.readFileSync(file)) : 'NOT_FILE';
  }
  return {commit: git(['rev-parse', 'HEAD']), tree: git(['rev-parse', 'HEAD^{tree}']),
    branch: git(['branch', '--show-current']), status: git(['status', '--porcelain=v1', '--untracked-files=all']),
    inputsSha256, treeDigest: hash(JSON.stringify(inputsSha256))};
}
const before = identity();
if (before.commit !== expected || before.status) throw Error('EXACT_CLEAN_SOURCE_REQUIRED');
const files = ['.github/continuum', 'scripts'].flatMap(dir => fs.readdirSync(path.join(repo, dir))
  .filter(name => name.endsWith('.test.mjs')).sort().map(name => dir + '/' + name));
const baselinePath = path.join(repo, 'docs/evidence/omega-apk54-20261111/node-combined-c36258a8/report.json.gz');
const baseline = JSON.parse(gunzipSync(fs.readFileSync(baselinePath)));
const priorFiles = baseline.checks[0].command.slice(5);
if (files.length !== 354 || JSON.stringify(files) !== JSON.stringify(priorFiles))
  throw Error('FULL_FILE_INVENTORY_CHANGED_REVIEW_REQUIRED');
const {runOwnedCommand} = await import(pathToFileURL(path.join(repo, 'scripts/continuum-owned-process.mjs')));
const {assessNodeOutcome} = await import(pathToFileURL(path.join(repo, 'scripts/continuum-reliability-qa.mjs')));
fs.mkdirSync(path.dirname(out), {recursive: true});
fs.mkdirSync(out, {mode: 0o700});
const junit = path.join(out, 'full-node.xml');
const command = [process.execPath, '--test', '--test-concurrency=1', '--test-reporter=junit',
  '--test-reporter-destination=' + junit, ...files];
const report = {schema: 1, profile: 'full-node-outer-concurrency-1', source: before,
  startedAt: new Date().toISOString(), environment: {node: process.version, executable: process.execPath,
    platform: process.platform, arch: process.arch, release: os.release()}, command, cwd: repo,
  timeoutMs: 30 * 60 * 1000, inventoryMatchesOriginal: true, fileCount: files.length,
  assertionChanges: false, timeoutChanges: false, innerWorkerConcurrencyChanged: false,
  originalConcurrency2Status: 'FAIL: five process cases; retained evidence is not superseded',
  status: 'RUNNING', sourceUnchanged: false};
fs.writeFileSync(path.join(out, 'report.json'), JSON.stringify(report, null, 2) + '\n');
const controller = new AbortController();
const cancel = () => controller.abort();
process.on('SIGINT', cancel);
process.on('SIGTERM', cancel);
try {
  const result = await runOwnedCommand(command[0], command.slice(1), {cwd: repo,
    timeout: report.timeoutMs, env: {...process.env, CONTINUUM_QA_DEPTH: '1'},
    signal: controller.signal, maxOutputBytes: 2 * 1024 * 1024});
  fs.writeFileSync(path.join(out, 'full-node.log'), result.output, {flag: 'wx', mode: 0o600});
  const xml = fs.existsSync(junit) ? fs.readFileSync(junit, 'utf8') : '';
  report.check = {...assessNodeOutcome(result, xml, repo), code: result.code,
    exitSignal: result.exitSignal, error: result.error,
    terminationConfirmed: result.terminationConfirmed === true, junitSha256: hash(xml)};
} finally {
  process.removeListener('SIGINT', cancel);
  process.removeListener('SIGTERM', cancel);
  const after = identity();
  report.sourceAfter = {commit: after.commit, tree: after.tree, branch: after.branch,
    status: after.status, treeDigest: after.treeDigest};
  report.sourceUnchanged = before.commit === after.commit && before.branch === after.branch &&
    before.status === after.status && before.treeDigest === after.treeDigest;
  report.cancelled = controller.signal.aborted;
  report.finishedAt = new Date().toISOString();
  report.status = !report.cancelled && report.sourceUnchanged && report.check?.status === 'PASS' ? 'PASS' : 'FAIL';
  fs.writeFileSync(path.join(out, 'report.json'), JSON.stringify(report, null, 2) + '\n');
}
console.log(JSON.stringify({status: report.status, source: before.commit, files: files.length,
  passed: report.check?.tests.passed, failed: report.check?.tests.failed, skipped: report.check?.tests.skipped,
  sourceUnchanged: report.sourceUnchanged}));
process.exitCode = report.status === 'PASS' ? 0 : 1;
