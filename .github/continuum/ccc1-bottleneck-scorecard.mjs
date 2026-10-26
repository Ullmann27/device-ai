/**
 * Non-executing, privacy-minimized bottleneck scorecard and local-first
 * routing recommendation. This module never calls tools, a specialist
 * model, a sandbox, the repair ledger, or any learning API.
 *
 * A scorecard is ANALYSIS_ONLY. It is never an authenticated CCC1 receipt.
 */
import {isCcc1BottleneckObservation} from './ccc1-bottleneck-diagnosis.mjs';

const freeze = value => Object.freeze(value);
const STATUS = new Set(['CANDIDATE_UNVERIFIED', 'NOT_APPLICABLE',
  'INVALID_OR_NO_OP', 'BINDING_REJECTED', 'TRANSLATOR_THROW']);
const CANDIDATE = 'CANDIDATE_UNVERIFIED';

function minimalArm(arm) {
  if (arm === null || typeof arm !== 'object' ||
      arm.baselineStatus !== 'NOT_RUN' || arm.primaryStatus !== 'NOT_RUN' ||
      arm.freshStatus !== 'NOT_RUN' || arm.independentOracleStatus !== 'NOT_RUN' ||
      arm.verifiedSuccess !== false || arm.learningEligible !== false) {
    throw Error('UNVERIFIED_EXECUTION_BOUNDARY_REQUIRED');
  }
  if (!Number.isSafeInteger(arm.operatorAttempts) || arm.operatorAttempts < 0 ||
      arm.operatorAttempts > 1) throw Error('INVALID_ATTEMPT_COUNT');
  if (arm.translationStatus === CANDIDATE && (
      arm.candidate?.status !== CANDIDATE ||
      arm.candidate?.candidateSha256 !== arm.candidateSha256)) {
    throw Error('INCONSISTENT_CANDIDATE');
  }
  return freeze({
    variant: arm.variant,
    selectedOperatorId: arm.selectedOperatorId,
    selectedOperatorIndex: arm.selectedOperatorIndex,
    selectedRank: arm.selectedRank,
    operatorAttempts: arm.operatorAttempts,
    translationStatus: arm.translationStatus,
    candidateSha256: arm.candidateSha256,
    hasBoundedCandidate: arm.candidate !== null
  });
}

/**
 * Read-only technical triage:
 * A is merely a candidate until independently RED -> GREEN -> fresh GREEN.
 * A passing B or C cannot be asserted from a syntactically valid patch.
 * External specialist escalation is never triggered here; the recommendation
 * tells the owner what to inspect LOCALLY first.
 */
export function buildCcc1BottleneckScorecard(observation) {
  if (!isCcc1BottleneckObservation(observation) ||
      observation.schema !== 'continuum-bottleneck-diagnosis' ||
      observation.version !== 1 || observation.status !== 'RESEARCH_SHADOW' ||
      observation.experimentalExecution !== 'NOT_RUN' ||
      observation.learningEligible !== false ||
      observation.promotionEligible !== false ||
      observation.checkpointAttested !== false ||
      observation.testManifestAttested !== false) {
    throw Error('AUTHENTIC_STAGE1_OBSERVATION_REQUIRED');
  }
  if (!Array.isArray(observation.arms) || observation.arms.length !== 3 ||
      observation.arms[0].variant !== 'REFERENCE_PATCH' ||
      observation.arms[1].variant !== 'GIVEN_OPERATOR' ||
      observation.arms[2].variant !== 'NEURAL_TOP1' ||
      !Array.isArray(observation.rawRanking) ||
      observation.rawRanking.length === 0 ||
      observation.arms[2].selectedRank !== 1 ||
      observation.arms[2].selectedOperatorIndex !== observation.rawRanking[0].operatorIndex ||
      observation.arms[2].operatorAttempts !== 1 ||
      !STATUS.has(observation.arms[2].translationStatus)) {
    throw Error('RANK1_MEASUREMENT_INCONSISTENT');
  }
  const [reference, forced, neural] = observation.arms.map(minimalArm);
  if (reference.translationStatus !== 'REFERENCE_UNVERIFIED' ||
      reference.operatorAttempts !== 0 || !reference.hasBoundedCandidate ||
      !STATUS.has(forced.translationStatus) || forced.operatorAttempts !== 1) {
    throw Error('THREE_ARM_MEASUREMENT_INCONSISTENT');
  }

  const forcedHasCandidate = forced.translationStatus === CANDIDATE;
  const neuralHasCandidate = neural.translationStatus === CANDIDATE;
  let observationFinding;
  let nextLocalGate;
  if (!forcedHasCandidate) {
    observationFinding = 'GIVEN_OPERATOR_NO_TRANSLATION';
    nextLocalGate = 'VERIFY_REFERENCE_THEN_REVIEW_TRANSLATOR_SCOPE';
  } else if (!neuralHasCandidate) {
    observationFinding = 'TOP1_NOT_TRANSLATABLE_WHILE_FORCED_TRANSLATES';
    nextLocalGate = 'VERIFY_REFERENCE_THEN_REVIEW_NEURAL_RANKING';
  } else if (forced.candidateSha256 === neural.candidateSha256) {
    observationFinding = 'BOTH_ARMS_SAME_UNVERIFIED_PATCH';
    nextLocalGate = 'VERIFY_REFERENCE_FIRST_THEN_BOTH_CANDIDATES';
  } else {
    observationFinding = 'BOTH_ARMS_DIFFERENT_UNVERIFIED_PATCHES';
    nextLocalGate = 'VERIFY_REFERENCE_FIRST_THEN_COMPARE_PATCHES';
  }
  // Even if the forced operator is known to be suitable, no model success is
  // established until the identical external CCC1 evidence is available.
  return freeze({
    schema: 'continuum-bottleneck-scorecard',
    version: 1,
    status: 'ANALYSIS_ONLY',
    experimentId: observation.experimentId,
    caseId: observation.caseId,
    sourceCommit: observation.sourceCommit,
    taskSha256: observation.taskSha256,
    sourceSha256: observation.sourceSha256,
    observedModelStateSha256: observation.observedModelStateSha256,
    declaredCheckpointSha256: observation.modelCheckpointSha256,
    declaredTestManifestSha256: observation.testManifestSha256,
    checkpointAttested: false,
    testManifestAttested: false,
    operatorDescriptorSha256: observation.operatorDescriptorSha256,
    top1OperatorId: observation.rawRanking[0].operatorId,
    top1Rank: 1,
    top1Score: observation.rawRanking[0].score,
    operatorCount: observation.rawRanking.length,
    arms: freeze([reference, forced, neural]),
    observedNeuralRankCalls: 1,
    observedOperatorTranslationAttempts: forced.operatorAttempts + neural.operatorAttempts,
    observedExternalModelCalls: 0,
    observedOsExecutionAttempts: 0,
    observationFinding, nextLocalGate,
    optionalLargeSpecialist: 'NOT_REQUESTED',
    requiresOwnerAuthorization: true,
    baselineStatus: 'NOT_RUN',
    independentVerification: 'NOT_RUN',
    generalizationProven: false,
    verifiedSuccess: false,
    learningEligible: false,
    promotionEligible: false
  });
}
