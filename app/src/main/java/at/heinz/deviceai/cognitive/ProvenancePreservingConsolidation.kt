package at.heinz.deviceai.cognitive

/**
 * Navigation-only consolidation plan over existing memory records.
 * A summary is never returned as hard proof; proof resolution always walks back to source records.
 */
internal object ProvenancePreservingConsolidation {
    data class NavigationSummary(
        val id: String,
        val text: String,
        val sourceRecordIds: List<String>,
        val createdAt: Long
    ) {
        init {
            require(id.matches(Regex("[A-Za-z0-9._:-]{1,96}")))
            require(text.isNotBlank() && text.length <= 2_000)
            require(sourceRecordIds.size in 1..128 && sourceRecordIds.none(String::isBlank))
            require(createdAt >= 0)
        }

        val canServeAsHardProof: Boolean get() = false
    }

    data class Resolution(
        val summary: NavigationSummary,
        val hardEvidence: List<MemoryRecord>,
        val missingSourceIds: List<String>,
        val unverifiedSourceIds: List<String>
    ) {
        val complete: Boolean get() = missingSourceIds.isEmpty() && unverifiedSourceIds.isEmpty()
    }

    fun plan(
        id: String,
        text: String,
        sourceRecords: Collection<MemoryRecord>,
        createdAt: Long
    ): NavigationSummary {
        require(sourceRecords.isNotEmpty() && sourceRecords.size <= 128)
        val ids = sourceRecords.map { it.id }.distinct().sorted()
        return NavigationSummary(id, text.trim(), ids, createdAt)
    }

    fun resolveHardEvidence(
        summary: NavigationSummary,
        records: Collection<MemoryRecord>
    ): Resolution {
        val byId = records.associateBy { it.id }
        val hard = mutableListOf<MemoryRecord>()
        val missing = mutableListOf<String>()
        val unverified = mutableListOf<String>()
        for (id in summary.sourceRecordIds) {
            val record = byId[id]
            when {
                record == null -> missing += id
                record.validationState !in HARD_STATES -> unverified += id
                record.provenance in NON_HARD_PROVENANCE -> unverified += id
                else -> hard += record
            }
        }
        return Resolution(summary, hard, missing, unverified)
    }

    private val HARD_STATES = setOf(ValidationState.VERIFIED)
    private val NON_HARD_PROVENANCE = setOf(
        ProvenanceType.MODEL_GENERATED, ProvenanceType.INFERENCE, ProvenanceType.UNVERIFIED
    )
}
