package at.heinz.deviceai.cognitive

/**
 * Evidence-ranked causal relation. Sequence/correlation are never silently promoted to causation.
 */
internal object CausalEpistemicLedger {
    enum class Rank {
        TEMPORAL_SEQUENCE,
        CORRELATION,
        MECHANISTIC_HYPOTHESIS,
        INTERVENTION_CONFIRMED
    }

    data class Relation(
        val id: String,
        val causeSignature: String,
        val effectSignature: String,
        val coOccurrences: Int = 0,
        val causeWithoutEffect: Int = 0,
        val effectWithoutCause: Int = 0,
        val mechanismEvidenceId: String? = null,
        val interventionEvidenceId: String? = null
    ) {
        init {
            require(id.matches(Regex("[A-Za-z0-9._:-]{1,96}")))
            require(causeSignature.isNotBlank() && causeSignature.length <= 180)
            require(effectSignature.isNotBlank() && effectSignature.length <= 180)
            require(coOccurrences >= 0 && causeWithoutEffect >= 0 && effectWithoutCause >= 0)
            require(mechanismEvidenceId == null || mechanismEvidenceId.isNotBlank())
            require(interventionEvidenceId == null || interventionEvidenceId.isNotBlank())
        }

        fun rank(): Rank = when {
            mechanismEvidenceId != null && interventionEvidenceId != null ->
                Rank.INTERVENTION_CONFIRMED
            mechanismEvidenceId != null && coOccurrences > 0 ->
                Rank.MECHANISTIC_HYPOTHESIS
            coOccurrences >= 3 && coOccurrences > causeWithoutEffect * 2 &&
                coOccurrences > effectWithoutCause * 2 ->
                Rank.CORRELATION
            else -> Rank.TEMPORAL_SEQUENCE
        }

        fun observe(causePresent: Boolean, effectPresent: Boolean): Relation = when {
            causePresent && effectPresent -> copy(coOccurrences = coOccurrences + 1)
            causePresent -> copy(causeWithoutEffect = causeWithoutEffect + 1)
            effectPresent -> copy(effectWithoutCause = effectWithoutCause + 1)
            else -> this
        }

        fun withMechanism(evidenceId: String): Relation =
            copy(mechanismEvidenceId = evidenceId.trim().take(128).also { require(it.isNotBlank()) })

        fun withIntervention(evidenceId: String): Relation =
            copy(interventionEvidenceId = evidenceId.trim().take(128).also { require(it.isNotBlank()) })

        fun permitsStrongCausalLanguage(): Boolean = rank() == Rank.INTERVENTION_CONFIRMED
    }
}
