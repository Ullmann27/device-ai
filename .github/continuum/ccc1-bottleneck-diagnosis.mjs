/**
 * CCC1 bottleneck diagnosis, stage 1: purely observational, no code execution.
 * Three arms share canonical candidate binding but never run a sandbox,
 * mutate weights, write a ledger, or claim a verified repair.
 */
import crypto from 'node:crypto';
import {validateCodingTask, taskFingerprint} from './coding-task.mjs';
import {CoinWmV3ShadowModel} from './coin-wm-v3-shadow-model.mjs';
import {isStructuredStatementOperator} from './structured-statement-translator.mjs';
import {validateCcc1CandidateBinding} from './ccc1-independent-verifier.mjs';

const HEX64 = /^[a-f0-9]{64}$/;
const CASE_ID = /^[A-Za-z0-9_-]{1,80}$/;
const hash = value => crypto.createHash('sha256').update(value, 'utf8').digest('hex');
const freeze = value => Object.freeze(value);
const ARMS = freeze(['REFERENCE_PATCH', 'GIVEN_OPERATOR', 'NEURAL_TOP1']);

function snapshotHash(model) {
  const keys = ['encoder', 'encoderAtInit', 'encoderBias', 'policyBias',
    'fisherEnc', 'policy', 'transitions', 'fisherPolicy', 'anchorEnc',
    'anchorPolicy', 'updatesCount', 'maxOperators'];
  const snapshot = Object.fromEntries(keys.map(key => [key, model[key]]));
  return hash(JSON.stringify(snapshot));
}

function candidateOf({task, source, code}) {
  if (typeof code !== 'string' || code.length === 0 || code.length > 16000 || code === source) {
    return null;
  }
  return freeze({status: 'CANDIDATE_UNVERIFIED', sourceCommit: task.sourceCommit,
    taskSha256: taskFingerprint(task), sourceSha256: hash(source),
    candidateSha256: hash(code), code});
}

function armResult(variant, selected, rank, translationStatus, candidate, reason) {
  return freeze({variant, selectedOperatorId: selected?.id ?? null,
    selectedOperatorIndex: selected?.index ?? null,
    selectedRank: rank, operatorAttempts: variant === 'REFERENCE_PATCH' ? 0 : 1,
    translationStatus, reason,
    candidateSha256: candidate?.candidateSha256 ?? null,
    candidate,
    baselineStatus: 'NOT_RUN', primaryStatus: 'NOT_RUN', freshStatus: 'NOT_RUN',
    independentOracleStatus: 'NOT_RUN',
    verifiedSuccess: false, learningEligible: false});
}

/**
 * Computes three distinct, frozen, source-bound hypotheses for a single task.
 * Reference content is retained ONLY in arm A, and is never used to rank C.
 * No implicit fallback is permitted: C always attempts rank[0] exactly once.
 * All model checkpoint/test manifest hashes are declared metadata, NOT attestations.
 */
export function diagnoseCcc1BottleneckCase({
  experimentId, caseId, typedTask, source, file, referenceCode,
  operators, referenceOperatorIndex, model, state16,
  modelCheckpointSha256, testManifestSha256
} = {}) {
  if (!CASE_ID.test(experimentId ?? '') || !CASE_ID.test(caseId ?? '')) throw Error('EXPERIMENT_ID_INVALID');
  if (!HEX64.test(modelCheckpointSha256 ?? '') || !HEX64.test(testManifestSha256 ?? '')) {
    throw Error('EXPERIMENT_MANIFEST_INVALID');
  }
  if (!(model instanceof CoinWmV3ShadowModel) || model.status !== 'RESEARCH_SHADOW' ||
    model.promotionEligible !== false) throw Error('RESEARCH_MODEL_REQUIRED');
  if (!Array.isArray(operators) || operators.length === 0 ||
    operators.length !== model.maxOperators || operators.length > 12 ||
    !operators.every(isStructuredStatementOperator) ||
    new Set(operators.map(op => op.id)).size !== operators.length) throw Error('OPERATOR_SET_INVALID');
  if (!Number.isSafeInteger(referenceOperatorIndex) || referenceOperatorIndex < 0 ||
    referenceOperatorIndex >= operators.length) throw Error('REFERENCE_OPERATOR_INVALID');
  if (typeof source !== 'string' || !source.length || source.length > 16000) {
    throw Error('SOURCE_BOUNDS');
  }
  const task = validateCodingTask(typedTask);
  const stateBefore = snapshotHash(model);
  const ranking = model.rank(state16, operators.length);
  if (!Array.isArray(ranking) || ranking.length !== operators.length ||
    new Set(ranking.map(entry => entry.operatorIndex)).size !== operators.length ||
    ranking.some(entry => !Number.isSafeInteger(entry.operatorIndex) ||
      entry.operatorIndex < 0 || entry.operatorIndex >= operators.length ||
      !Number.isFinite(entry.score))) throw Error('RANK_EVIDENCE_INVALID');
  const rawRanking = freeze(ranking.map((entry, idx) => freeze({
    rank: idx + 1, operatorIndex: entry.operatorIndex,
    operatorId: operators[entry.operatorIndex].id, score: entry.score
  })));

  const reference = candidateOf({task, source, code: referenceCode});
  if (!reference) throw Error('REFERENCE_CANDIDATE_INVALID');
  validateCcc1CandidateBinding({typedTask: task, candidate: reference, file});

  function translate(index, variant, rank) {
    const op = operators[index];
    let result;
    try {
      result = op.applyToCode(source);
    } catch {
      return armResult(variant, {id: op.id, index}, rank, 'TRANSLATOR_THROW', null, 'TRANSLATOR_ERROR');
    }
    if (result?.status !== 'CANDIDATE_UNVERIFIED' || typeof result.code !== 'string') {
      return armResult(variant, {id: op.id, index}, rank, 'NOT_APPLICABLE', null, 'NO_ATOMIC_PATCH');
    }
    const candidate = candidateOf({task, source, code: result.code});
    if (!candidate) return armResult(variant, {id: op.id, index}, rank,
      'INVALID_OR_NO_OP', null, 'NO_SOURCE_CHANGE');
    try {
      validateCcc1CandidateBinding({typedTask: task, candidate, file});
    } catch {
      return armResult(variant, {id: op.id, index}, rank,
        'BINDING_REJECTED', null, 'IDENTITY_OR_SCOPE_REJECTED');
    }
    return armResult(variant, {id: op.id, index}, rank,
      'CANDIDATE_UNVERIFIED', candidate, 'REQUIRES_INDEPENDENT_EXECUTION');
  }

  const arms = freeze([
    armResult(ARMS[0], null, null, 'REFERENCE_UNVERIFIED', reference,
      'REFERENCE_NOT_INDEPENDENTLY_PROVEN'),
    translate(referenceOperatorIndex, ARMS[1], null),
    translate(ranking[0].operatorIndex, ARMS[2], 1)
  ]);
  if (snapshotHash(model) !== stateBefore) throw Error('MODEL_MUTATED_DURING_DIAGNOSIS');
  return freeze({schema: 'continuum-bottleneck-diagnosis', version: 1,
    status: 'RESEARCH_SHADOW', experimentalExecution: 'NOT_RUN',
    promotionEligible: false, learningEligible: false,
    experimentId, caseId, sourceCommit: task.sourceCommit,
    taskSha256: taskFingerprint(task), sourceSha256: hash(source),
    modelCheckpointSha256, observedModelStateSha256: stateBefore,
    checkpointAttested: false, testManifestSha256,
    testManifestAttested: false,
    operatorDescriptorSha256: hash(JSON.stringify(operators.map(op => ({id: op.id, kind: op.kind})))),
    rawRanking, arms});
}
