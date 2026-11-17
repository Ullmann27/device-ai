package at.heinz.deviceai.cognitive

sealed class UpdateResult {
    data class Accepted(val state: IdentityState) : UpdateResult()
    data class Rejected(val failures: List<ValidationFailure>) : UpdateResult()
}

/**
 * Applies persistent identity updates: proposal -> policy -> validator -> new generation.
 * Rejected updates leave all state untouched. Timestamps are supplied by the caller (determinism).
 */
class AdaptiveIdentity(val genome: CognitiveGenome, val history: IdentityHistory = IdentityHistory(IdentityState.initial(genome))) {
    val behaviorHistory = BehaviorHistory()
    val current: IdentityState get() = history.current

    fun applyBehaviorProposal(proposal: BehaviorProposal, timestamp: Long): UpdateResult {
        val before = current
        val decision = BehaviorUpdatePolicy.evaluate(genome, before.behaviorProfile, proposal)
        if (decision is UpdateDecision.Rejected) return UpdateResult.Rejected(decision.failures)
        decision as UpdateDecision.Accepted
        val candidate = before.evolve { it.copy(behaviorProfile = it.behaviorProfile.with(decision.trait, decision.acceptedValue)) }
        val verdict = IdentityUpdateValidator.validate(genome, before, candidate)
        if (verdict is ValidationResult.Invalid) return UpdateResult.Rejected(verdict.failures)
        history.append(candidate)
        behaviorHistory.record(BehaviorChange(
            trait = proposal.trait, previousValue = before.behaviorProfile[proposal.trait],
            proposedValue = proposal.proposedValue, acceptedValue = decision.acceptedValue,
            evidenceIds = proposal.evidence.map { it.id }, evidenceStrength = decision.strength,
            measuredOutcome = decision.outcome, reason = proposal.reason, timestamp = timestamp,
            sourceIdentityGeneration = before.generation, resultingIdentityGeneration = candidate.generation
        ))
        return UpdateResult.Accepted(candidate)
    }

    fun recordCapabilityEvidence(e: CapabilityEvidence): UpdateResult {
        val before = current
        val newModel = try {
            before.selfModel.withEvidence(e, genome.learningRate("selfModel"))
        } catch (ex: IllegalArgumentException) {
            return UpdateResult.Rejected(listOf(ValidationFailure(FailureCode.INVALID_NUMBER, ex.message ?: "invalid evidence")))
        }
        val candidate = before.evolve { it.copy(selfModel = newModel) }
        val verdict = IdentityUpdateValidator.validate(genome, before, candidate)
        if (verdict is ValidationResult.Invalid) return UpdateResult.Rejected(verdict.failures)
        history.append(candidate)
        return UpdateResult.Accepted(candidate)
    }
}
