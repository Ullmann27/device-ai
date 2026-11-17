package at.heinz.deviceai.cognitive

enum class FailureCode {
    INVALID_NUMBER, OUT_OF_BOUNDS, INSUFFICIENT_EVIDENCE, EVIDENCE_MISMATCH, IDENTITY_MISMATCH,
    BROKEN_ANCESTRY, GENOME_MISMATCH, EXCESSIVE_DRIFT, FORBIDDEN_CAPABILITY, INTEGRITY_FAILURE
}

data class ValidationFailure(val code: FailureCode, val message: String)

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val failures: List<ValidationFailure>) : ValidationResult()
    val isValid: Boolean get() = this is Valid
}

/** Central gatekeeper for persistent identity updates. */
object IdentityUpdateValidator {
    fun validate(genome: CognitiveGenome, current: IdentityState, candidate: IdentityState): ValidationResult {
        val f = mutableListOf<ValidationFailure>()
        fun fail(c: FailureCode, m: String) { f.add(ValidationFailure(c, m)) }

        if (genome.allowSelfReplication || genome.allowHiddenPersistence || genome.allowShutdownEvasion)
            fail(FailureCode.FORBIDDEN_CAPABILITY, "forbidden capability flag enabled")
        if (candidate.identityId != genome.identityId || candidate.identityId != current.identityId)
            fail(FailureCode.IDENTITY_MISMATCH, "identity id differs")
        if (current.cognitiveGenomeHash != genome.hash() || candidate.cognitiveGenomeHash != genome.hash())
            fail(FailureCode.GENOME_MISMATCH, "genome hash differs")
        if (!current.verify()) fail(FailureCode.INTEGRITY_FAILURE, "current state hash invalid")
        if (!candidate.verify()) fail(FailureCode.INTEGRITY_FAILURE, "candidate hash invalid")
        if (candidate.generation != current.generation + 1 || candidate.parentGeneration != current.generation ||
            candidate.parentHash != current.integrityHash)
            fail(FailureCode.BROKEN_ANCESTRY, "candidate is not the direct successor of generation ${current.generation}")

        for (t in BehaviorTrait.values()) {
            val v = candidate.behaviorProfile[t]
            if (!v.isFinite()) fail(FailureCode.INVALID_NUMBER, "$t not finite")
            else if (!genome.behaviorBounds.contains(v)) fail(FailureCode.OUT_OF_BOUNDS, "$t=$v outside ${genome.behaviorBounds}")
        }
        for (c in Capability.values()) {
            val e = candidate.selfModel[c]
            if (!e.competence.isUnit() || !e.calibrationError.isUnit()) fail(FailureCode.INVALID_NUMBER, "$c estimate invalid")
        }
        if (f.none { it.code == FailureCode.INVALID_NUMBER }) {
            val d = IdentityDrift.compute(genome, current, candidate)
            if (!d.withinAllowedRange) fail(FailureCode.EXCESSIVE_DRIFT, "drift ${d.overallDrift} > ${genome.updateLimits.maxDriftPerGeneration}")
        }
        return if (f.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(f)
    }
}
