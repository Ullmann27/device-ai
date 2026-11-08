import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { ComputeReuseBroker } from '../continuum-virtual-fabric/reuse-broker.mjs';
import { matrixWork } from '../continuum-virtual-fabric/reuse-experiment.mjs';

const sha = value => createHash('sha256').update(value).digest('hex');
const protocol = JSON.parse(readFileSync(new URL('./reuse-protocol.json', import.meta.url), 'utf8'));
const revisions = {
  broker: sha(readFileSync(new URL('../continuum-virtual-fabric/reuse-broker.mjs', import.meta.url))),
  kernel: sha(readFileSync(new URL('../continuum-virtual-fabric/reuse-experiment.mjs', import.meta.url))),
  pilot: sha(readFileSync(fileURLToPath(import.meta.url))),
  protocol: sha(readFileSync(new URL('./reuse-protocol.json', import.meta.url))),
};
const inputDigest = (seed, size) => sha(JSON.stringify({ seed, size }));

export function referenceMatrix(seed, size) {
  assert(Number.isSafeInteger(seed) && seed >= 0 && seed <= 100_000);
  assert(Number.isInteger(size) && size >= 1 && size <= 32);
  const rows = Array.from({ length: size }, (_, i) =>
    Array.from({ length: size }, (_, k) => (seed + i * 3 + k * 7) % 19));
  const columns = Array.from({ length: size }, (_, j) =>
    Array.from({ length: size }, (_, k) => (seed * 5 + k * 11 + j * 2) % 17));
  const product = rows.map(row =>
    columns.map(column => row.reduce((sum, value, k) => sum + value * column[k], 0)));
  return sha(JSON.stringify(product));
}

export function jobFor(seed, size, index, memoryEpoch = 0) {
  return {
    id: `job-${index}`, tenantId: 'synthetic-pilot', modelRevision: revisions.kernel,
    inputDigest: inputDigest(seed, size), parametersDigest: sha(JSON.stringify({ size })),
    policyRevision: revisions.pilot, memoryEpoch, taskType: 'PURE', deterministic: true,
  };
}

export function independentBroker() {
  let verifierCalls = 0;
  const broker = new ComputeReuseBroker({
    maxCached: 1024,
    verifyReceipt: (job, receipt) => {
      if (job.inputDigest !== inputDigest(receipt.seed, receipt.size)) return false;
      verifierCalls++;
      return receipt.value === referenceMatrix(receipt.seed, receipt.size);
    },
  });
  return { broker, verifierCalls: () => verifierCalls };
}

function execute(workload, arm, count) {
  const { broker, verifierCalls } = independentBroker();
  const outputs = [];
  let kernelCalls = 0;
  for (let index = 0; index < count; index++) {
    const seed = workload.seedOffset + index % workload.distinct;
    const epoch = arm === 'no-reuse' ? index :
      workload.epochEvery ? Math.floor(index / workload.epochEvery) : 0;
    let value;
    if (arm === 'direct') {
      value = matrixWork(seed, workload.size);
      kernelCalls++;
    } else {
      const request = jobFor(seed, workload.size, index, epoch);
      const admission = broker.submit(request);
      if (admission.action === 'CACHE_HIT') {
        value = admission.result;
      } else {
        assert.equal(admission.action, 'EXECUTE');
        value = matrixWork(seed, workload.size);
        kernelCalls++;
        broker.complete(request.id, { seed, size: workload.size, value });
      }
    }
    outputs.push(`${index}:${value}`);
  }
  return { outputs, kernelCalls, verifierCalls: verifierCalls(), broker: broker.snapshot() };
}

export function runTrial(workload, arm, warmupRequests = protocol.warmupRequests) {
  assert(['direct', 'reuse', 'no-reuse'].includes(arm));
  assert(Number.isSafeInteger(workload.requests) && workload.requests >= 1 && workload.requests <= 3000);
  assert(Number.isSafeInteger(workload.distinct) && workload.distinct >= 1 && workload.distinct <= workload.requests);
  assert(Number.isSafeInteger(workload.epochEvery) && workload.epochEvery >= 0);
  assert(Number.isSafeInteger(warmupRequests) && warmupRequests >= 0 && warmupRequests <= 1000);
  referenceMatrix(workload.seedOffset + workload.distinct - 1, workload.size);
  assert(workload.seedOffset >= 0);
  execute(workload, arm, warmupRequests);
  const memoryBefore = process.memoryUsage();
  const cpuStart = process.cpuUsage();
  const start = process.hrtime.bigint();
  const result = execute(workload, arm, workload.requests);
  const wallNs = Number(process.hrtime.bigint() - start);
  const cpu = process.cpuUsage(cpuStart);
  const memoryAfter = process.memoryUsage();

  const auditCpuStart = process.cpuUsage();
  const auditStart = process.hrtime.bigint();
  result.outputs.forEach((value, index) => {
    const seed = workload.seedOffset + index % workload.distinct;
    assert.equal(value, `${index}:${referenceMatrix(seed, workload.size)}`);
  });
  const auditWallNs = Number(process.hrtime.bigint() - auditStart);
  const auditCpu = process.cpuUsage(auditCpuStart);
  return {
    arm, workload, wallNs, cpuUs: cpu.user + cpu.system,
    auditWallNs, auditCpuUs: auditCpu.user + auditCpu.system,
    memoryBefore, memoryAfter, maxProcessRssKiB: process.resourceUsage().maxRSS,
    kernelCalls: result.kernelCalls, verifierCalls: result.verifierCalls, broker: result.broker,
    retainedPayloadUtf8Bytes: result.broker.cachedResults * 64,
    exactResults: result.outputs.length, outputSha256: sha(JSON.stringify(result.outputs)),
    energyJoules: null,
  };
}

const median = values => {
  const sorted = [...values].sort((a, b) => a - b);
  return (sorted[Math.floor((sorted.length - 1) / 2)] + sorted[Math.ceil((sorted.length - 1) / 2)]) / 2;
};

function measure(outputPath) {
  const runs = [];
  const self = fileURLToPath(import.meta.url);
  for (const workload of protocol.workloads) {
    for (let pair = 0; pair < protocol.pairs; pair++) {
      const arms = pair % 2 === 0 ? ['direct', 'reuse', 'no-reuse'] : ['no-reuse', 'reuse', 'direct'];
      for (const arm of arms) {
        const result = JSON.parse(execFileSync(process.execPath,
          [`--max-old-space-size=${protocol.heapMiB}`, self, '--worker', workload.name, arm],
          { encoding: 'utf8', timeout: 30_000, maxBuffer: 1_000_000 }));
        runs.push({ pair, ...result });
      }
    }
  }
  const summary = protocol.workloads.map(workload => {
    const selected = runs.filter(run => run.workload.name === workload.name);
    assert.equal(new Set(selected.map(run => run.outputSha256)).size, 1);
    const arms = Object.fromEntries(['direct', 'reuse', 'no-reuse'].map(arm => {
      const samples = selected.filter(run => run.arm === arm);
      return [arm, {
        medianWallNs: median(samples.map(run => run.wallNs)),
        medianCpuUs: median(samples.map(run => run.cpuUs)),
        kernelCalls: samples[0].kernelCalls, verifierCalls: samples[0].verifierCalls,
        maxProcessRssKiB: Math.max(...samples.map(run => run.maxProcessRssKiB)),
        retainedPayloadUtf8Bytes: samples[0].retainedPayloadUtf8Bytes,
      }];
    }));
    const wins = selected.filter(run => run.arm === 'reuse').filter(run => {
      const baseline = selected.find(other => other.arm === 'direct' && other.pair === run.pair);
      return run.wallNs < baseline.wallNs && run.cpuUs < baseline.cpuUs;
    }).length;
    return {
      workload: workload.name, arms, pairsImprovingBoth: wins,
      thresholdMet: wins === protocol.pairs &&
        arms.reuse.medianWallNs <= arms.direct.medianWallNs * 0.9 &&
        arms.reuse.medianCpuUs <= arms.direct.medianCpuUs * 0.9,
    };
  });
  const report = {
    schemaVersion: 1, sourceCommit: execFileSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8' }).trim(),
    workingTree: execFileSync('git', ['status', '--porcelain'], { encoding: 'utf8' }).trim(),
    node: process.version, platform: `${process.platform}-${process.arch}`, revisions,
    protocol, summary, runs, energyJoules: null, device: 'HOST_ONLY',
  };
  writeFileSync(outputPath, JSON.stringify(report, null, 2) + '\n');
  console.log(JSON.stringify(summary, null, 2));
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  if (process.argv[2] === '--worker') {
    const workload = protocol.workloads.find(value => value.name === process.argv[3]);
    assert(workload, 'Unknown workload');
    console.log(JSON.stringify(runTrial(workload, process.argv[4])));
  } else {
    assert.equal(process.argv.length, 3, 'Pass output JSON path');
    measure(process.argv[2]);
  }
}
