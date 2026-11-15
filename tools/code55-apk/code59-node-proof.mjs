import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {assessNodeOutcome, GATES} from '../../scripts/continuum-reliability-qa.mjs';

const require = condition => { if (!condition) throw Error('Code59 Node proof rejected'); };
const hash = data => crypto.createHash('sha256').update(data).digest('hex');
const walk = dir => fs.readdirSync(dir, {withFileTypes: true}).flatMap(item =>
  item.isDirectory() ? walk(path.join(dir, item.name)) : [path.join(dir, item.name)]);

export function selectChecks(report, expected) {
  const outside = ['gradle', 'lint', 'apk', 'fold7', 'model-inference'];
  const all = [...expected, ...outside];
  require(report.checks.length === all.length && new Set(report.checks.map(c => c.id)).size === all.length
    && report.checks.every(check => all.includes(check.id)));
  require(report.checks.filter(c => outside.includes(c.id)).every(check =>
    check.status === 'NOT_RUN' && check.reason === 'OUTSIDE_SELECTED_HOST_SCOPE' && check.severity === 'P0'));
  return report.checks.filter(check => expected.includes(check.id));
}

export function releaseProfile(branch) {
  if (branch === 'fix/code59-context-complete-20261114') return '59';
  if (branch === 'integrate/continuum-command03-android-20261115') return '60';
  throw Error('Unsupported release branch');
}

export function verify(directory, sha, branch) {
  const release = releaseProfile(branch);
  require(directory && /^[a-f0-9]{40}$/.test(sha));
  const inventory = spawnSync('git', ['ls-files', '-z', '--cached', '--others', '--exclude-standard'],
    {encoding: 'utf8', timeout: 10000, maxBuffer: 16 * 1024 * 1024});
  require(inventory.status === 0);
  const names = inventory.stdout.split('\0').filter(Boolean).sort();
  for (const profile of ['critical', 'full-node']) {
    const folder = profile === 'critical' ? `code${release}-critical` : `code${release}-full`;
    const files = walk(directory).filter(file => path.basename(file) === 'report.json'
      && path.basename(path.dirname(file)) === folder);
    require(files.length === 1);
    const report = JSON.parse(fs.readFileSync(files[0], 'utf8'));
    require(report.schemaVersion === 1 && report.profile === profile && report.status === 'PASS'
      && report.sourceUnchanged === true && report.cancelled === false
      && report.source.commit === sha && report.source.branch === branch);
    require(hash(JSON.stringify(report.source.inputsSha256)) === report.source.treeDigest);
    require(JSON.stringify(Object.keys(report.source.inputsSha256)) === JSON.stringify(names));
    for (const [name, digest] of Object.entries(report.source.inputsSha256)) {
      require(name && !path.isAbsolute(name) && !name.split('/').includes('..')
        && /^[a-f0-9]{64}$/.test(digest));
      const file = path.resolve(name);
      const stat = fs.lstatSync(file);
      require(stat.isFile() || stat.isSymbolicLink());
      require(hash(stat.isSymbolicLink() ? fs.readlinkSync(file) : fs.readFileSync(file)) === digest);
    }
    const expected = profile === 'critical' ? GATES.map(gate => gate.id) : ['full-node'];
    for (const check of selectChecks(report, expected)) {
      require(check.status === 'PASS' && check.code === 0 && check.error === null && check.exitSignal === null
        && check.terminationConfirmed === true);
      const xml = fs.readFileSync(path.join(path.dirname(files[0]), check.id + '.xml'), 'utf8');
      require(hash(xml) === check.junitSha256);
      const assessed = assessNodeOutcome({ok: true, code: check.code, error: check.error,
        exitSignal: check.exitSignal, terminationConfirmed: check.terminationConfirmed}, xml, process.cwd());
      require(assessed.status === 'PASS');
      if (profile === 'full-node') require(assessed.tests.total >= 2511 && assessed.tests.executed >= 2474);
      for (const key of ['total', 'passed', 'failed', 'skipped', 'executed'])
        require(assessed.tests[key] === check.tests[key]);
    }
    console.log(profile + ' SOURCE_BOUND_PROOF_VALID');
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  verify(...process.argv.slice(2));
}
