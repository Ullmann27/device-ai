import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const xmlAttribute = (tag, name) => {
  const match = tag.match(new RegExp('(?:^|\\s)' + name + '="([0-9]+)"'));
  return match ? Number(match[1]) : null;
};

export function assessJUnitXml(xml) {
  const suite = xml.match(/<testsuite\b([^>]*)>/);
  if (!suite || !xml.includes('</testsuite>')) throw new Error('Invalid or incomplete JUnit XML suite');
  const counts = Object.fromEntries(['tests', 'failures', 'errors', 'skipped'].map(key => [key, xmlAttribute(suite[1], key)]));
  if (Object.values(counts).some(value => !Number.isSafeInteger(value) || value < 0)) {
    throw new Error('Missing or invalid JUnit counts');
  }
  const cases = [...xml.matchAll(/<testcase\b/g)].length;
  if (cases !== counts.tests || counts.failures + counts.errors + counts.skipped > counts.tests) {
    throw new Error('JUnit test-case count inconsistency');
  }
  if ((counts.failures > 0 && !xml.includes('<failure')) ||
      (counts.errors > 0 && !xml.includes('<error')) ||
      (counts.skipped > 0 && !xml.includes('<skipped'))) {
    throw new Error('Missing JUnit failure/error/skip details');
  }
  return counts;
}

export function assessRun({ suites, gradleExit, nodeExit, stale = false }) {
  const counts = { tests: 0, failures: 0, errors: 0, skipped: 0 };
  for (const suite of suites) {
    for (const key of Object.keys(counts)) counts[key] += suite[key];
  }
  const pass = suites.length > 0 && counts.tests > 0 && !stale &&
    gradleExit === 0 && nodeExit === 0 && counts.failures === 0 &&
    counts.errors === 0 && counts.skipped === 0;
  return { status: pass ? 'PASS' : 'FAIL', counts, suites: suites.length, stale };
}

const sha256 = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');

export function collectRun({ output, project, commit, gradleExit, nodeExit, startedMillis }) {
  if (!/^[0-9a-f]{40}$/.test(commit)) throw new Error('Expected full git SHA');
  const source = path.join(project, 'app/build/test-results/testDebugUnitTest');
  const xmlFiles = fs.existsSync(source)
    ? fs.readdirSync(source).filter(name => /^TEST-.*\.xml$/.test(name)).sort() : [];
  const destination = path.join(output, 'junit-xml');
  fs.mkdirSync(destination, { recursive: true, mode: 0o700 });
  const suites = [];
  let stale = false;
  const errors = [];
  for (const name of xmlFiles) {
    try {
      const file = path.join(source, name);
      const stat = fs.lstatSync(file);
      if (!stat.isFile() || stat.isSymbolicLink()) throw new Error('Not a regular report file');
      if (stat.mtimeMs < startedMillis - 2000) stale = true;
      const contents = fs.readFileSync(file, 'utf8');
      suites.push(assessJUnitXml(contents));
      fs.writeFileSync(path.join(destination, name), contents, { mode: 0o600, flag: 'wx' });
    } catch (error) {
      errors.push(name + ': ' + error.message);
    }
  }
  const evaluation = assessRun({ suites, gradleExit, nodeExit, stale });
  if (errors.length > 0) evaluation.status = 'FAIL';
  const summary = { commit, ...evaluation, errors, gradleExit, nodeExit,
    fullSuiteCommand: ':app:testDebugUnitTest (no --tests filter)',
    executedOnDevice: false, modelInferenceVerified: false,
    qualityImprovementProven: false };
  fs.writeFileSync(path.join(output, 'summary.json'), JSON.stringify(summary, null, 2) + '\n', { mode: 0o600 });
  const manifest = {};
  function record(directory, prefix = '') {
    for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
      if (entry.name === 'sha256-manifest.json') continue;
      const relative = path.join(prefix, entry.name);
      if (entry.isDirectory()) record(path.join(directory, entry.name), relative);
      else if (entry.isFile()) manifest[relative] = sha256(path.join(directory, entry.name));
    }
  }
  record(output);
  fs.writeFileSync(path.join(output, 'sha256-manifest.json'), JSON.stringify(manifest, null, 2) + '\n', { mode: 0o600 });
  return summary;
}

if (process.argv[1] && path.resolve(process.argv[1]) === path.resolve(new URL(import.meta.url).pathname)) {
  const [output, project, commit, gradleExit, nodeExit, startedMillis] = process.argv.slice(2);
  if (!output || !project || !commit || !/^\d+$/.test(gradleExit ?? '') ||
      !/^\d+$/.test(nodeExit ?? '') || !/^\d+$/.test(startedMillis ?? '')) {
    process.stderr.write('Usage: node continuum-fusion-regression-report.mjs <output> <checkout> <HEAD> <gradleExit> <nodeExit> <startedEpochMillis>\n');
    process.exitCode = 64;
  } else {
    try {
      const summary = collectRun({ output, project, commit,
        gradleExit: Number(gradleExit), nodeExit: Number(nodeExit), startedMillis: Number(startedMillis) });
      process.stdout.write('CONTINUUM_FULL_REGRESSION_' + summary.status + ' tests=' + summary.counts.tests + ' suites=' + summary.suites + '\n');
      if (summary.status !== 'PASS') process.exitCode = 1;
    } catch (error) {
      process.stderr.write('CONTINUUM_REPORT_FAILED: ' + error.message + '\n');
      process.exitCode = 1;
    }
  }
}
