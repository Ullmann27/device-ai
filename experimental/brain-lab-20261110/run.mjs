import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import os from 'node:os';
import { ARMS, WORKLOADS, trial } from './compute.mjs';
import { EVENT_STREAMS, eventTrial } from './salience.mjs';

const self = fileURLToPath(import.meta.url);
const root = fileURLToPath(new URL('../../', import.meta.url));
const median = values => {
  const sorted = [...values].sort((a, b) => a - b);
  return (sorted[Math.floor((sorted.length - 1) / 2)] + sorted[Math.ceil((sorted.length - 1) / 2)]) / 2;
};
const sources = [
  'experimental/brain-lab-20261110/PROTOCOL.md',
  'experimental/brain-lab-20261110/compute.mjs',
  'experimental/brain-lab-20261110/salience.mjs',
  'experimental/brain-lab-20261110/run.mjs',
  'experimental/brain-lab-20261110/lab.test.mjs',
  'tools/continuum-cognitive-pilots/verified-reuse-pilot.mjs',
  'tools/continuum-cognitive-pilots/reuse-protocol.json',
  'tools/continuum-virtual-fabric/reuse-experiment.mjs',
  'tools/continuum-virtual-fabric/reuse-broker.mjs',
];

export function summarize(runs) {
  return WORKLOADS.map(workload => {
    const rows = runs.filter(run => run.workload === workload.name);
    assert.equal(rows.length, 18);
    assert.equal(new Set(rows.map(x => x.outputSha256)).size, 1);
    const arms = Object.fromEntries(ARMS.map(arm => {
      const selected = rows.filter(x => x.arm === arm);
      assert.equal(selected.length, 6);
      for (const key of ['kernelCalls', 'verifierCalls', 'bypasses', 'admissions', 'hits'])
        assert.equal(new Set(selected.map(x => x[key])).size, 1);
      return [arm, {
        medianWallMs: median(selected.map(x => x.wallNs / 1e6)),
        medianCpuMs: median(selected.map(x => x.cpuUs / 1000)),
        maxProcessRssKiB: Math.max(...selected.map(x => x.maxProcessRssKiB)),
        kernelCalls: selected[0].kernelCalls, verifierCalls: selected[0].verifierCalls,
        bypasses: selected[0].bypasses, admissions: selected[0].admissions, hits: selected[0].hits,
      }];
    }));
    const wins = rows.filter(x => x.arm === 'adaptive').filter(run => {
      const direct = rows.find(x => x.arm === 'direct' && x.pair === run.pair);
      return run.wallNs < direct.wallNs && run.cpuUs < direct.cpuUs;
    }).length;
    const negative = workload.split === 'NEGATIVE_HOLDOUT';
    const gate = negative ?
      arms.adaptive.medianWallMs <= arms.direct.medianWallMs * 1.05 &&
        arms.adaptive.medianCpuMs <= arms.direct.medianCpuMs * 1.05 :
      wins === 6 && arms.adaptive.medianWallMs <= arms.direct.medianWallMs * 0.9 &&
        arms.adaptive.medianCpuMs <= arms.direct.medianCpuMs * 0.9;
    return { workload: workload.name, split: workload.split, arms, pairsImprovingBoth: wins,
      gate: gate ? 'PASS' : 'FAIL', gateMeaning: negative ? 'at most 5% overhead' : 'at least 10% savings and six paired wins' };
  });
}

function measure(output) {
  assert(path.isAbsolute(output), 'absolute output path required');
  assert(!path.resolve(output).startsWith(path.resolve(root) + path.sep), 'output outside source required');
  const git = args => execFileSync('git', args, { cwd: root, encoding: 'utf8' }).trim();
  const workingTree = git(['status', '--porcelain']);
  assert.equal(workingTree, '', 'commit source before measurement');
  const provenance = { sourceCommit: git(['rev-parse', 'HEAD']), branch: git(['branch', '--show-current']),
    workingTree, sourceSha256: Object.fromEntries(sources.map(file =>
      [file, createHash('sha256').update(readFileSync(path.join(root, file))).digest('hex')])) };
  const runs = [];
  for (const workload of WORKLOADS) for (let pair = 0; pair < 6; pair++) {
    for (const arm of pair % 2 ? [...ARMS].reverse() : ARMS) {
      const row = JSON.parse(execFileSync(process.execPath,
        ['--max-old-space-size=128', self, '--worker', workload.name, arm],
        { cwd: root, encoding: 'utf8', timeout: 30_000, maxBuffer: 1_000_000 }));
      runs.push({ pair, ...row });
    }
  }
  const summary = summarize(runs);
  const eventRows = EVENT_STREAMS.flatMap(stream =>
    ['always', 'threshold', 'accumulator'].map(arm => eventTrial(stream, arm)));
  const report = {
    schema: 'brain-lab-controlled-v1', generatedAt: new Date().toISOString(), ...provenance,
    host: { node: process.version, platform: process.platform, arch: process.arch,
      release: os.release(), cpuCount: os.cpus().length, cpuModel: os.cpus()[0]?.model ?? null,
      totalMemoryBytes: os.totalmem(), dedicatedHost: false },
    protocol: { pairs: 6, warmupRequests: 64, heapLimitMiB: 128, workloads: WORKLOADS },
    summary, runs, eventRows,
    decision: summary.filter(x => x.split !== 'DEV').every(x => x.gate === 'PASS') ?
      'ELIGIBLE_FOR_REAL_WORKLOAD_SHADOW_EVALUATION_ONLY' : 'REJECT_PROMOTION',
    exactResults: runs.reduce((sum, row) => sum + row.exactResults, 0),
    modelCalls: 0, energyJoules: null,
    notRun: ['REAL_MODEL', 'HIDDEN_STATE_RECURRENCE', 'LEARNED_MOE', 'LEARNED_PREDICTIVE_CODING',
      'TRAINED_SNN', 'PRODUCTION_BUDGET_ADAPTER', 'MEMORY_ADAPTER', 'ANDROID', 'FOLD7', 'GPU', 'ENERGY'],
    limitations: ['Same-author synthetic holdouts, not external blind tasks',
      'Process RSS includes runtime, warmup and audit; heap deltas are not allocation counts',
      'No isolated host or controlled thermals; timing is diagnostic, not device evidence',
      'Matrix identities are periodic and cases are correlated',
      'Event fixture labels are explicit synthetic expectations, not human salience judgments'],
  };
  assert.equal(git(['rev-parse', 'HEAD']), provenance.sourceCommit, 'source changed during measurement');
  assert.equal(git(['status', '--porcelain']), '', 'working tree changed during measurement');
  writeFileSync(output, JSON.stringify(report, null, 2) + '\n');
  console.log(JSON.stringify({ sourceCommit: report.sourceCommit, decision: report.decision,
    exactResults: report.exactResults, summary,
    events: eventRows.map(({ rows, ...result }) => result) }, null, 2));
}

if (process.argv[1] && path.resolve(process.argv[1]) === self) {
  if (process.argv[2] === '--worker') {
    assert.equal(process.argv.length, 5);
    const workload = WORKLOADS.find(x => x.name === process.argv[3]);
    assert(workload, 'unknown workload');
    console.log(JSON.stringify(trial(workload, process.argv[4])));
  } else {
    assert.equal(process.argv.length, 3, 'pass absolute output JSON path');
    measure(process.argv[2]);
  }
}
