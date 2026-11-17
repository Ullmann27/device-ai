import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { assessJUnitXml, assessRun, collectRun } from './continuum-fusion-regression-report.mjs';

const goodXml = '<testsuite name="unit" tests="2" failures="0" errors="0" skipped="0"><testcase name="a"/><testcase name="b"/></testsuite>';
const badXml = '<testsuite name="unit" tests="2" failures="1" errors="0" skipped="0"><testcase name="a"><failure message="fail"/></testcase><testcase name="b"/></testsuite>';
const head = 'a'.repeat(40);

function fixture(body) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'continuum-regression-'));
  const project = path.join(dir, 'project');
  const output = path.join(dir, 'output');
  const xmlDir = path.join(project, 'app/build/test-results/testDebugUnitTest');
  fs.mkdirSync(xmlDir, { recursive: true });
  fs.mkdirSync(output);
  try { body({ dir, project, output, xmlDir }); }
  finally { fs.rmSync(dir, { recursive: true, force: true }); }
}

test('parses authentic-style nonempty JUnit counts', () => {
  assert.deepEqual(assessJUnitXml(goodXml), { tests: 2, failures: 0, errors: 0, skipped: 0 });
});

test('rejects truncated, missing or contradictory JUnit reports', () => {
  assert.throws(() => assessJUnitXml(goodXml.slice(0, -12)));
  assert.throws(() => assessJUnitXml(goodXml.replace('skipped="0"', '')));
  assert.throws(() => assessJUnitXml(goodXml.replace('tests="2"', 'tests="3"')));
});

test('forbids PASS after failed, skipped, missing or outdated tests', () => {
  const valid = assessJUnitXml(goodXml);
  const failed = assessJUnitXml(badXml);
  assert.equal(assessRun({ suites: [valid], gradleExit: 0, nodeExit: 0 }).status, 'PASS');
  assert.equal(assessRun({ suites: [failed], gradleExit: 0, nodeExit: 0 }).status, 'FAIL');
  assert.equal(assessRun({ suites: [], gradleExit: 0, nodeExit: 0 }).status, 'FAIL');
  assert.equal(assessRun({ suites: [valid], gradleExit: 1, nodeExit: 0 }).status, 'FAIL');
  assert.equal(assessRun({ suites: [valid], gradleExit: 0, nodeExit: 1 }).status, 'FAIL');
  assert.equal(assessRun({ suites: [valid], gradleExit: 0, nodeExit: 0, stale: true }).status, 'FAIL');
  assert.equal(assessRun({ suites: [{ ...valid, skipped: 1 }], gradleExit: 0, nodeExit: 0 }).status, 'FAIL');
});

test('copies real XML and hashes reports outside the checkout', () => fixture(({ project, output, xmlDir }) => {
  fs.writeFileSync(path.join(xmlDir, 'TEST-unit.xml'), goodXml);
  const now = Date.now();
  const summary = collectRun({ output, project, commit: head, gradleExit: 0, nodeExit: 0,
    startedMillis: now - 100 });
  assert.equal(summary.status, 'PASS');
  assert.equal(summary.counts.tests, 2);
  assert.equal(summary.executedOnDevice, false);
  assert.ok(fs.readFileSync(path.join(output, 'junit-xml/TEST-unit.xml'), 'utf8').includes('testcase'));
  const manifest = JSON.parse(fs.readFileSync(path.join(output, 'sha256-manifest.json'), 'utf8'));
  assert.match(manifest['junit-xml/TEST-unit.xml'], /^[0-9a-f]{64}$/);
  assert.match(manifest['summary.json'], /^[0-9a-f]{64}$/);
}));

test('keeps malformed JUnit XML as evidence while reporting FAIL', () => fixture(({ project, output, xmlDir }) => {
  const broken = '<testsuite name="unit" tests="2" failures="0" errors="0" skipped="0"><testcase name="a"/>';
  fs.writeFileSync(path.join(xmlDir, 'TEST-unit.xml'), broken);
  const summary = collectRun({ output, project, commit: head, gradleExit: 0, nodeExit: 0,
    startedMillis: Date.now() - 100 });
  assert.equal(summary.status, 'FAIL');
  assert.equal(summary.counts.tests, 0);
  assert.equal(summary.errors.length, 1);
  assert.equal(fs.readFileSync(path.join(output, 'junit-xml/TEST-unit.xml'), 'utf8'), broken);
  const manifest = JSON.parse(fs.readFileSync(path.join(output, 'sha256-manifest.json'), 'utf8'));
  assert.match(manifest['junit-xml/TEST-unit.xml'], /^[0-9a-f]{64}$/);
}));

test('rejects stale XML left over from an earlier filtered test run', () => fixture(({ project, output, xmlDir }) => {
  const target = path.join(xmlDir, 'TEST-unit.xml');
  fs.writeFileSync(target, goodXml);
  fs.utimesSync(target, new Date(1000), new Date(1000));
  const summary = collectRun({ output, project, commit: head, gradleExit: 0, nodeExit: 0,
    startedMillis: Date.now() });
  assert.equal(summary.status, 'FAIL');
  assert.equal(summary.stale, true);
}));

test('rejects missing XML even when the underlying Gradle exit is zero', () => fixture(({ project, output }) => {
  const summary = collectRun({ output, project, commit: head, gradleExit: 0, nodeExit: 0,
    startedMillis: Date.now() });
  assert.equal(summary.status, 'FAIL');
  assert.equal(summary.counts.tests, 0);
}));
