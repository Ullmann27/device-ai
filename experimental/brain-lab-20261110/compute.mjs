import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { independentBroker, jobFor, referenceMatrix } from '../../tools/continuum-cognitive-pilots/verified-reuse-pilot.mjs';
import { matrixWork } from '../../tools/continuum-virtual-fabric/reuse-experiment.mjs';

export const WORKLOADS = Object.freeze([
  { name: 'repeated-expensive', split: 'DEV', requests: 600, distinct: 8, size: 32, seedOffset: 0, epochEvery: 0 },
  { name: 'mixed-cost-repeat', split: 'HOLDOUT', requests: 600, distinct: 32, size: 24, seedOffset: 5000, epochEvery: 0 },
  { name: 'unique-expensive', split: 'NEGATIVE_HOLDOUT', requests: 600, distinct: 600, size: 24, seedOffset: 10000, epochEvery: 0 },
  { name: 'epoch-churn', split: 'NEGATIVE_HOLDOUT', requests: 600, distinct: 8, size: 24, seedOffset: 20000, epochEvery: 8 },
]);
export const ARMS = Object.freeze(['direct', 'reuse', 'adaptive']);
const sha = value => createHash('sha256').update(value).digest('hex');

export function inputAt(workload, index) {
  return { seed: workload.seedOffset + index % workload.distinct,
    size: workload.name === 'mixed-cost-repeat' && index % 2 ? 1 : workload.size,
    epoch: workload.epochEvery ? Math.floor(index / workload.epochEvery) : 0 };
}

export class AdmissionProbe {
  #seen = new Map();
  consider(request, size) {
    assert(Number.isInteger(size) && size >= 1 && size <= 32);
    const key = JSON.stringify([request.tenantId, request.modelRevision, request.inputDigest,
      request.parametersDigest, request.policyRevision, request.memoryEpoch]);
    const seen = this.#seen.has(key);
    this.#seen.delete(key);
    this.#seen.set(key, true);
    if (this.#seen.size > 256) this.#seen.delete(this.#seen.keys().next().value);
    return size >= 16 && seen;
  }
  get retainedIdentities() { return this.#seen.size; }
}

export function execute(workload, arm, count) {
  assert(ARMS.includes(arm));
  assert(Number.isSafeInteger(count) && count >= 0 && count <= 3000);
  const { broker, verifierCalls } = independentBroker();
  const admission = new AdmissionProbe();
  const outputs = [];
  let kernelCalls = 0, bypasses = 0, admissions = 0, hits = 0;
  for (let index = 0; index < count; index++) {
    const { seed, size, epoch } = inputAt(workload, index);
    let value;
    const request = arm === 'direct' ? null : jobFor(seed, size, index, epoch);
    const useBroker = arm === 'reuse' || arm === 'adaptive' && admission.consider(request, size);
    if (!useBroker) {
      value = matrixWork(seed, size);
      kernelCalls++;
      bypasses++;
    } else {
      admissions++;
      const response = broker.submit(request);
      if (response.action === 'CACHE_HIT') {
        value = response.result;
        hits++;
      } else {
        assert.equal(response.action, 'EXECUTE');
        value = matrixWork(seed, size);
        kernelCalls++;
        assert.equal(broker.complete(request.id, { seed, size, value }).result, value);
      }
    }
    outputs.push(`${index}:${value}`);
  }
  return { outputs, kernelCalls, verifierCalls: verifierCalls(), bypasses, admissions, hits,
    retainedIdentities: admission.retainedIdentities, broker: broker.snapshot() };
}

export function trial(workload, arm, warmup = 64) {
  assert(WORKLOADS.includes(workload));
  execute(workload, arm, warmup);
  const memoryBefore = process.memoryUsage();
  const cpuStart = process.cpuUsage();
  const start = process.hrtime.bigint();
  const result = execute(workload, arm, workload.requests);
  const wallNs = Number(process.hrtime.bigint() - start);
  const cpu = process.cpuUsage(cpuStart);
  const memoryAfter = process.memoryUsage();
  const auditStart = process.hrtime.bigint();
  const auditCpuStart = process.cpuUsage();
  result.outputs.forEach((value, index) => {
    const { seed, size } = inputAt(workload, index);
    assert.equal(value, `${index}:${referenceMatrix(seed, size)}`);
  });
  const auditCpu = process.cpuUsage(auditCpuStart);
  const { outputs, ...counts } = result;
  return { workload: workload.name, split: workload.split, arm, ...counts,
    wallNs, cpuUs: cpu.user + cpu.system, memoryBefore, memoryAfter,
    auditWallNs: Number(process.hrtime.bigint() - auditStart), auditCpuUs: auditCpu.user + auditCpu.system,
    maxProcessRssKiB: process.resourceUsage().maxRSS, exactResults: outputs.length,
    outputSha256: sha(JSON.stringify(outputs)), modelCalls: 0, energyJoules: null };
}
