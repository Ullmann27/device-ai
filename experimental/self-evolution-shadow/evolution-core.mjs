import { createHash } from 'node:crypto';

const SHA256 = /^[a-f0-9]{64}$/;
const STRATEGY = /^[a-z][a-z0-9-]{2,63}$/;
const hex = value => {
  let raw;
  try { raw = JSON.stringify(value); } catch { return null; }
  if (typeof raw !== 'string' || raw.length > 100_000) return null;
  return createHash('sha256').update(raw).digest('hex');
};
const boundedInt = (n, max) => Number.isSafeInteger(n) && n >= 0 && n <= max;

/** No real scheduler or authority is granted here. Caller must obtain trusted device snapshots. */
export function canStartShadowSelfPlay(s) {
  if (!s || s.ownerOptIn !== true || s.idle !== true || s.charging !== true ||
      s.connectedToTrustedHost !== true || s.activeWorker !== false ||
      !boundedInt(s.batteryPercent, 100) || s.batteryPercent < 40 ||
      typeof s.thermalHeadroom !== 'number' || !Number.isFinite(s.thermalHeadroom) ||
      s.thermalHeadroom < 0.5 || s.thermalHeadroom > 1 ||
      !boundedInt(s.availableTimeMs, 3_600_000) || s.availableTimeMs < 5_000) {
    return Object.freeze({ eligible: false, reason: 'REQUIRE_IDLE_CHARGING_HEADROOM_OWNER_OPT_IN' });
  }
  return Object.freeze({ eligible: true, reason: 'SHADOW_ELIGIBLE_NOT_AUTHORIZED_TO_WRITE' });
}

/** Mutation is intentionally limited to the existing two-field MASTER-EXP typed domain. */
export function mutateTypedPolicy(task, faultId) {
  if (!task || !STRATEGY.test(task.id ?? '') || !task.policy ||
      task.policy.sliceEndAdjustment !== 0 || task.policy.clampNonnegative !== true ||
      !Array.isArray(task.visible) || !Array.isArray(task.blind) ||
      !SHA256.test(hex(task) ?? '') ||
      !['slice-boundary', 'negative-clamp', 'combined'].includes(faultId)) {
    throw Error('UNVERIFIED_BASELINE_OR_MUTATION');
  }
  const policy = { ...task.policy };
  if (faultId !== 'negative-clamp') policy.sliceEndAdjustment = -1;
  if (faultId !== 'slice-boundary') policy.clampNonnegative = false;
  return Object.freeze({
    id: task.id, policy: Object.freeze(policy), visible: task.visible, blind: task.blind,
    experiment: Object.freeze({ faultId, baseHash: hex(task), isArtificialFault: true })
  });
}

/** Only test evidence bound to exactly one candidate is scored; absence is not a negative label. */
export function classifyIndependentEvidence(task, candidate, proof) {
  if (!task || !Array.isArray(task.blind) || task.blind.length < 1 ||
      task.blind.length > 100_000 || !proof || proof.evaluator !== 'independent' ||
      proof.taskHash !== hex(task) || proof.candidateHash !== hex(candidate) ||
      !SHA256.test(proof.testSetHash ?? '') || proof.testSetHash !== hex(task.blind) ||
      !boundedInt(proof.executedTests, 100_000) ||
      proof.executedTests !== task.blind.length ||
      (proof.skippedTests !== undefined && proof.skippedTests !== 0) ||
      !boundedInt(proof.failedTests, 100_000) || proof.failedTests > proof.executedTests ||
      proof.runnerError === true || proof.timedOut === true || proof.infrastructureAbort === true) {
    return Object.freeze({ classification: 'NEUTRAL', reason: 'MISSING_OR_UNTRUSTED_TEST_EVIDENCE' });
  }
  if (proof.executedTests === 0) return Object.freeze({ classification: 'NEUTRAL', reason: 'ZERO_TESTS_EXECUTED' });
  if (proof.exitCode === 0 && proof.failedTests === 0 && proof.evidenceComplete === true && proof.blindPass === true)
    return Object.freeze({ classification: 'VERIFIED_EXPERIMENTAL', reason: 'BOUND_INDEPENDENT_PASS' });
  if (proof.failedTests > 0 && proof.evidenceComplete === true && proof.blindPass === false && proof.exitCode === 1)
    return Object.freeze({ classification: 'VERIFIED_NEGATIVE', reason: 'EXECUTED_TEST_FAILURE' });
  return Object.freeze({ classification: 'NEUTRAL', reason: 'INCONCLUSIVE_TEST_EVIDENCE' });
}

/** Produce immutable, content-free autopsies. No direct NTW1 gradient is written. */
export function autopsyFromOutcome({ task, candidate, strategyId, proof }) {
  if (!STRATEGY.test(strategyId ?? '')) throw Error('INVALID_STRATEGY');
  const result = classifyIndependentEvidence(task, candidate, proof);
  const diagnosed = Object.freeze({
    faultFamily: task.experiment?.faultId ?? 'unknown',
    strategyId, taskHash: hex(task), candidateHash: hex(candidate),
    classification: result.classification, reason: result.reason,
    testsExecuted: boundedInt(proof?.executedTests, 100_000) ? proof.executedTests : 0,
    productionLearningAuthorized: false, neuralTrainingAuthorized: false
  });
  return diagnosed;
}

/** Read-only replay summary of an existing ledger. Does not mutate, prune, write or score a runner crash as failure. */
export function summarizeExistingLedger(ledger) {
  if (!ledger || ledger.schemaVersion !== 2 || !Array.isArray(ledger.attempts) ||
      ledger.attempts.length > 400 || !Array.isArray(ledger.experiences) || !Array.isArray(ledger.sessions))
    throw Error('INVALID_REPAIR_LEDGER');
  const observations = [];
  for (const attempt of ledger.attempts) {
    if (!attempt || typeof attempt.strategy !== 'string' ||
        !STRATEGY.test(attempt.strategy.toLowerCase().replaceAll('_', '-').slice(0, 64))) continue;
    const explicitRed = (Array.isArray(attempt.tests) ? attempt.tests : []).some(t => t && t.passed === false &&
      t.evidenceComplete !== false && !t.signal && !t.error && !t.timedOut &&
      [t.total, t.executed, t.failed, t.skipped].every(Number.isSafeInteger) &&
      t.executed > 0 && t.failed > 0 && t.failed <= t.executed &&
      t.total >= t.executed && t.skipped >= 0 && t.skipped <= t.total - t.executed &&
      [0,1].includes(t.exitCode));
    const positive = attempt.outcome === 'VERIFIED' && SHA256.test(attempt.patchSha256 ?? '');
    const negative = (attempt.outcome === 'FAILED_TESTS' && explicitRed) ||
      (attempt.outcome === 'FAILED_SYNTAX' && attempt.syntaxReason === 'INVALID_JS_SYNTAX');
    observations.push(Object.freeze({
      strategy: attempt.strategy,
      kind: positive ? 'LEDGER_VERIFIED_RECORD' : negative ? 'LEDGER_EXECUTED_FAILURE' : 'NEUTRAL',
      evidenceSha256: SHA256.test(attempt.patchSha256 ?? '') ? attempt.patchSha256 : null
    }));
  }
  return Object.freeze({ observations: Object.freeze(observations), readOnly: true, trainingAuthorized: false });
}

/** Macro promotion requires independent multi-task evidence, and remains review-only. */
export function proposeEvolutionMacros(records, minDifferentTasks = 2) {
  if (!Array.isArray(records) || records.length > 100 || !boundedInt(minDifferentTasks, 100) || minDifferentTasks < 2)
    throw Error('INVALID_MACRO_RECORDS');
  const byChain = new Map();
  for (const r of records) {
    if (r?.classification !== 'VERIFIED_EXPERIMENTAL' || !Array.isArray(r.chain) ||
        r.chain.length < 2 || r.chain.length > 3 || !STRATEGY.test(r.taskId ?? '') ||
        r.chain.some(v => !STRATEGY.test(v))) continue;
    const key = r.chain.join('--then--');
    if (!byChain.has(key)) byChain.set(key, new Set());
    byChain.get(key).add(r.taskId);
  }
  return Object.freeze([...byChain.entries()]
    .filter(([, ids]) => ids.size >= minDifferentTasks)
    .map(([id, ids]) => Object.freeze({id, independentTaskCount: ids.size,
      status:'SHADOW_REVIEW_CANDIDATE', promoted:false, writeAllowed:false, deleteAllowed:false})));
}

/** Exactly one finite cycle; no timers, no daemon, no hardware/branch/model mutations. */
export function executeShadowEvolutionCycle({ snapshot, baselineTasks, solver, independentVerifier,
    maxTasks = 3, maxEvaluations = 6, cycleId = 1 }) {
  const eligibility = canStartShadowSelfPlay(snapshot);
  if (!eligibility.eligible) return Object.freeze({status:'SKIPPED',reason:eligibility.reason,records:[]});
  if (!Array.isArray(baselineTasks) || baselineTasks.length < 1 || baselineTasks.length > 20 ||
      typeof solver !== 'function' || typeof independentVerifier !== 'function' ||
      !boundedInt(maxTasks,3) || maxTasks < 1 || !boundedInt(maxEvaluations,16) || maxEvaluations < 1 ||
      !Number.isSafeInteger(cycleId) || cycleId < 1 || cycleId > 50) throw Error('UNBOUNDED_EVOLUTION_REQUEST');
  const modes = ['slice-boundary','negative-clamp','combined'];
  const records = [];
  for (const [index, base] of baselineTasks.slice(0,maxTasks).entries()) {
    const task = mutateTypedPolicy(base, modes[index % modes.length]);
    let candidate;
    let raw;
    try {
      // The solver is not given the holdout cases. A second evaluator receives the full task.
      const solverView = Object.freeze({...task, blind:task.visible});
      candidate = solver(solverView, { maxEvaluations });
      raw = independentVerifier(task, candidate);
    } catch {
      raw = null;
    }
    const autopsy = autopsyFromOutcome({task, candidate, strategyId:'bounded-composition', proof:raw});
    records.push(Object.freeze({
      taskId:task.id, cycleId, chain:Array.isArray(candidate?.chain) ? candidate.chain : [],
      classification:autopsy.classification, autopsy,
      artificialTask:true, promoted:false
    }));
  }
  const verified = records.filter(x=>x.classification==='VERIFIED_EXPERIMENTAL').length;
  return Object.freeze({status:'FINISHED_SHADOW', cycleId, taskCount:records.length, verified,
    negative:records.filter(x=>x.classification==='VERIFIED_NEGATIVE').length,
    neutral:records.filter(x=>x.classification==='NEUTRAL').length,
    macroCandidates:proposeEvolutionMacros(records), records:Object.freeze(records),
    mainWriteAllowed:false, ntwTrainingAllowed:false, pruningAllowed:false});
}

export const evolutionDigest = hex;


/** Ephemeral research-only Bayesian-smoothed priors; NO NTW1 weight/ledger writes. */
export function deriveShadowOperatorPriors(records, operatorIds = ['correct-slice-end', 'clamp-negative-quota']) {
  if (!Array.isArray(records) || records.length > 100 || !Array.isArray(operatorIds) ||
      operatorIds.length < 2 || operatorIds.length > 8 ||
      new Set(operatorIds).size !== operatorIds.length || operatorIds.some(x => !STRATEGY.test(x)))
    throw Error('INVALID_PRIOR_EVIDENCE');
  const counts = new Map(operatorIds.map(id => [id, {success:0, failures:0}]));
  for (const record of records) {
    if (!record || !Array.isArray(record.chain) ||
        record.chain.length !== 1 || !counts.has(record.chain[0])) continue;
    if (record.classification === 'VERIFIED_EXPERIMENTAL') counts.get(record.chain[0]).success++;
    if (record.classification === 'VERIFIED_NEGATIVE') counts.get(record.chain[0]).failures++;
    // A multi-step outcome cannot identify which individual operator caused it.
    // Neutral/infrastructure failures must NEVER be treated as negative samples.
  }
  const weighted = operatorIds.map(id => {
    const c=counts.get(id);
    return (c.success + 1) / (c.success + c.failures + 2);
  });
  const sum = weighted.reduce((a,b)=>a+b,0);
  return Object.freeze({priors: Object.freeze(Object.fromEntries(operatorIds.map((id,i)=>[id,weighted[i]/sum]))),
    sampleCounts: Object.freeze(Object.fromEntries(operatorIds.map(id=>[id,Object.freeze({...counts.get(id)})]))),
    modelWeightsChanged:false, persisted:false, ownerApprovalRequired:true});
}
