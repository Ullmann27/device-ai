package at.heinz.deviceai.cognitive

/** One observation supporting a behavior change. outcome: -1 (trait hurt) .. +1 (trait helped). */
data class BehaviorEvidence(
    val id: String,
    val trait: BehaviorTrait,
    val strength: Double,
    val measuredOutcome: Double,
    val description: String = ""
)

data class BehaviorProposal(
    val trait: BehaviorTrait,
    val proposedValue: Double,
    val evidence: List<BehaviorEvidence>,
    val reason: String
)

/** Audit record for an accepted persistent behavior change. */
data class BehaviorChange(
    val trait: BehaviorTrait,
    val previousValue: Double,
    val proposedValue: Double,
    val acceptedValue: Double,
    val evidenceIds: List<String>,
    val evidenceStrength: Double,
    val measuredOutcome: Double,
    val reason: String,
    val timestamp: Long,
    val sourceIdentityGeneration: Int,
    val resultingIdentityGeneration: Int
)

/** Append-only log of accepted changes. */
class BehaviorHistory {
    private val entries = mutableListOf<BehaviorChange>()
    val changes: List<BehaviorChange> get() = entries.toList()
    fun record(change: BehaviorChange) { entries.add(change) }
    fun forTrait(trait: BehaviorTrait) = entries.filter { it.trait == trait }
}

sealed class UpdateDecision {
    data class Accepted(val trait: BehaviorTrait, val acceptedValue: Double, val strength: Double, val outcome: Double) : UpdateDecision()
    data class Rejected(val failures: List<ValidationFailure>) : UpdateDecision()
}

/**
 * Bounded, deterministic update model:
 * step = learningRate * meanStrength * meanOutcome, limited to +-maxBehaviorStep,
 * never overshooting the proposed value, then clamped to genome bounds.
 */
object BehaviorUpdatePolicy {
    fun evaluate(genome: CognitiveGenome, profile: BehaviorProfile, proposal: BehaviorProposal): UpdateDecision {
        val f = mutableListOf<ValidationFailure>()
        fun fail(code: FailureCode, msg: String) { f.add(ValidationFailure(code, msg)) }

        if (!proposal.proposedValue.isFinite()) fail(FailureCode.INVALID_NUMBER, "proposedValue not finite")
        else if (!genome.behaviorBounds.contains(proposal.proposedValue))
            fail(FailureCode.OUT_OF_BOUNDS, "proposedValue ${proposal.proposedValue} outside ${genome.behaviorBounds}")
        if (proposal.evidence.isEmpty()) fail(FailureCode.INSUFFICIENT_EVIDENCE, "no evidence")
        if (proposal.reason.isBlank()) fail(FailureCode.INSUFFICIENT_EVIDENCE, "reason required")
        if (proposal.evidence.map { it.id }.toSet().size != proposal.evidence.size)
            fail(FailureCode.INSUFFICIENT_EVIDENCE, "duplicate evidence ids")
        for (e in proposal.evidence) {
            if (e.id.isBlank()) fail(FailureCode.INSUFFICIENT_EVIDENCE, "blank evidence id")
            if (!e.strength.isUnit() || !(e.measuredOutcome.isFinite() && e.measuredOutcome in -1.0..1.0))
                fail(FailureCode.INVALID_NUMBER, "evidence ${e.id} has invalid numbers")
            if (e.trait != proposal.trait) fail(FailureCode.EVIDENCE_MISMATCH, "evidence ${e.id} is for ${e.trait}")
        }
        if (f.isNotEmpty()) return UpdateDecision.Rejected(f)

        val strength = proposal.evidence.map { it.strength }.average()
        val outcome = proposal.evidence.map { it.measuredOutcome }.average()
        if (strength < genome.updateLimits.minEvidenceStrength)
            return UpdateDecision.Rejected(listOf(ValidationFailure(FailureCode.INSUFFICIENT_EVIDENCE,
                "evidence strength $strength < ${genome.updateLimits.minEvidenceStrength}")))

        val old = profile[proposal.trait]
        val wanted = proposal.proposedValue - old
        val raw = genome.learningRate("behavior") * strength * outcome
        val step = raw.coerceIn(-genome.updateLimits.maxBehaviorStep, genome.updateLimits.maxBehaviorStep)
        if (wanted == 0.0 || step == 0.0 || (wanted > 0) != (step > 0))
            return UpdateDecision.Rejected(listOf(ValidationFailure(FailureCode.EVIDENCE_MISMATCH,
                "evidence does not support moving $old toward ${proposal.proposedValue}")))
        val move = if (wanted > 0) minOf(step, wanted) else maxOf(step, wanted)
        val accepted = genome.behaviorBounds.clamp(old + move)
        return UpdateDecision.Accepted(proposal.trait, accepted, strength, outcome)
    }
}
