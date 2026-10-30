import { createHash } from 'node:crypto';

const OPERATIONS = Object.freeze({
  'correct-slice-end': Object.freeze({ id: 'correct-slice-end', costBits: 10, gas: 2 }),
  'clamp-negative-quota': Object.freeze({ id: 'clamp-negative-quota', costBits: 10, gas: 2 })
});
const OPS = Object.freeze(Object.keys(OPERATIONS));
const ID = /^[a-z][a-z0-9-]{2,63}$/;
const integer = n => Number.isSafeInteger(n) && Math.abs(n) <= 1_000_000;
const safeInputs = x => Array.isArray(x?.samples) && x.samples.length <= 64 &&
  x.samples.every(integer) && ['start', 'span', 'reserved', 'ceiling'].every(k => integer(x[k]) && x[k] >= 0);

export function validateTask(t) {
  if (!t || !ID.test(t.id ?? '') || !t.policy ||
    ![-1, 0].includes(t.policy.sliceEndAdjustment) ||
    typeof t.policy.clampNonnegative !== 'boolean' ||
    !Array.isArray(t.visible) || !Array.isArray(t.blind) ||
    t.visible.length < 1 || t.visible.length > 20 || t.blind.length < 1 || t.blind.length > 20 ||
    [...t.visible, ...t.blind].some(x => !safeInputs(x.input) || !integer(x.expected)) ) {
    throw Error('INVALID_TYPED_TASK');
  }
  return t;
}

// Pure typed transforms only. Never executes provided JavaScript, shell, Android APIs or file operations.
export function applyOperator(policy, id) {
  if (!OPS.includes(id) || !policy || ![-1, 0].includes(policy.sliceEndAdjustment) ||
    typeof policy.clampNonnegative !== 'boolean') throw Error('INVALID_OPERATOR');
  if (id === 'correct-slice-end') {
    if (policy.sliceEndAdjustment !== -1) return null;
    return { ...policy, sliceEndAdjustment: 0 };
  }
  if (policy.clampNonnegative) return null;
  return { ...policy, clampNonnegative: true };
}

export function runTypedModel(policy, input) {
  if (!safeInputs(input)) throw Error('INVALID_INPUT');
  const start = Math.min(input.start, input.samples.length);
  const end = Math.max(start, start + input.span + policy.sliceEndAdjustment);
  const actual = Math.min(input.samples.slice(start, end).length - input.reserved, input.ceiling);
  return policy.clampNonnegative ? Math.max(0, actual) : actual;
}

export function oracle(input) {
  // Independently written expected-behaviour specification: candidate policy is never read here.
  if (!safeInputs(input)) throw Error('INVALID_INPUT');
  const start = Math.min(input.start, input.samples.length);
  const len = input.samples.slice(start, start + input.span).length;
  return Math.max(0, Math.min(len - input.reserved, input.ceiling));
}

export function inspect(task, policy, phase = 'visible') {
  validateTask(task);
  if (!['visible','blind'].includes(phase)) throw Error('INVALID_PHASE');
  const cases = task[phase];
  let passes = 0;
  for (const tc of cases) {
    if (oracle(tc.input) !== tc.expected) throw Error('MALFORMED_TEST_ORACLE');
    if (runTypedModel(policy, tc.input) === tc.expected) passes++;
  }
  return Object.freeze({ passed: passes === cases.length, passedCount: passes,
    count: cases.length, phase });
}

function validatePriors(priors) {
  if (priors === null || typeof priors !== 'object' || Array.isArray(priors) ||
    OPS.some(id => typeof priors[id] !== 'number' || !Number.isFinite(priors[id]) ||
      priors[id] <= 0 || priors[id] >= 1) ||
    Object.keys(priors).some(id => !OPS.includes(id))) throw Error('INVALID_PRIORS');
  const total = OPS.reduce((sum, id) => sum + priors[id], 0);
  if (Math.abs(total - 1) > 1e-8) throw Error('PRIORS_MUST_SUM_TO_ONE');
}

export function enumeratePrograms({ priors, maxDepth = 2, maxOperators = 2, maxGas = 8,
  lambda = 0.08, compositionBits = 4 } = {}) {
  validatePriors(priors);
  if (![1, 2].includes(maxDepth) || !Number.isSafeInteger(maxOperators) || maxOperators < 1 ||
    maxOperators > OPS.length || !Number.isSafeInteger(maxGas) || maxGas < 1 || maxGas > 100 ||
    !Number.isFinite(lambda) || lambda < 0 || lambda > 1 ||
    !Number.isFinite(compositionBits) || compositionBits < 0 || compositionBits > 128)
    throw Error('INVALID_BOUNDED_SEARCH_CONFIG');
  const available = OPS.slice(0, maxOperators);
  const programs = [];
  const push = chain => {
    const gas = chain.reduce((x, op) => x + OPERATIONS[op].gas, 0);
    if (gas > maxGas) return;
    const bits = chain.reduce((x, op) => x + OPERATIONS[op].costBits, 0) +
      (chain.length > 1 ? compositionBits : 0);
    const prior = chain.reduce((x, op) => x * priors[op], 1);
    // Finite heuristic, NOT a true Levin schedule or Kolmogorov complexity.
    const priorityCost = -Math.log2(prior) + lambda * bits + Math.log2(Math.max(1, gas));
    programs.push(Object.freeze({chain:Object.freeze([...chain]), id:chain.join('--then--'), gas,
      encodedBits:bits, prior, priorityCost}));
  };
  for (const a of available) {
    push([a]);
    if (maxDepth > 1) for (const b of available) if (a !== b) push([a, b]);
  }
  return Object.freeze(programs.sort((a,b) => a.priorityCost-b.priorityCost || a.id.localeCompare(b.id)));
}

export function applyProgram(task, chain) {
  validateTask(task);
  if (!Array.isArray(chain) || chain.length < 1 || chain.length > 2 ||
    new Set(chain).size !== chain.length || chain.some(x => !OPS.includes(x)))
    throw Error('INVALID_PROGRAM');
  let policy = { ...task.policy };
  for (const id of chain) {
    const next = applyOperator(policy, id);
    if (next === null) return null;
    policy = next;
  }
  return policy;
}

export function solve(task, { priors = { 'correct-slice-end': 0.5, 'clamp-negative-quota': 0.5 },
  maxDepth = 2, maxEvaluations = 4, maxGas = 8 } = {}) {
  validateTask(task);
  if (!Number.isSafeInteger(maxEvaluations) || maxEvaluations < 1 || maxEvaluations > 16)
    throw Error('INVALID_EVALUATION_BUDGET');
  const plans = enumeratePrograms({priors,maxDepth,maxGas});
  let evaluated = 0;
  const failures = [];
  for (const plan of plans) {
    if (evaluated === maxEvaluations) break;
    const next = applyProgram(task, plan.chain);
    if (next === null) continue;
    evaluated++;
    const visible = inspect(task, next, 'visible');
    const blind = visible.passed ? inspect(task, next, 'blind') : null;
    if (visible.passed && blind?.passed) {
      return Object.freeze({solved:true, evaluated, chain:plan.chain,
        proposedMacro:plan.chain.length > 1 ? plan.id : null,
        shadowOnly:true, promoted:false, counterexamples:failures.length});
    }
    failures.push({id:plan.id,reason:!visible.passed?'VISIBLE_FAILURE':'FALSIFIED_BY_BLIND_TEST'});
  }
  return Object.freeze({solved:false,evaluated,chain:null,proposedMacro:null,
    shadowOnly:true,promoted:false,counterexamples:failures.length,failures});
}

export function proposeMacro(task, result, {independentDevConfirmations=0} = {}) {
  validateTask(task);
  if (!result?.solved || !result.proposedMacro || !Number.isSafeInteger(independentDevConfirmations) ||
    independentDevConfirmations < 2) return Object.freeze({status:'NOT_ELIGIBLE',writeAllowed:false});
  const digest = createHash('sha256').update(JSON.stringify({taskId:task.id,chain:result.chain})).digest('hex');
  return Object.freeze({status:'SHADOW_REVIEW_CANDIDATE',macroId:result.proposedMacro,
    evidenceHash:digest,writeAllowed:false,productionActivationAllowed:false});
}

export const SEARCH_OPERATOR_IDS = OPS;
