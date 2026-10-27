package at.heinz.deviceai.cognitive

/**
 * Research-only evaluation of the EXISTING local hybrid memory retriever.
 *
 * LongMemEval-inspired assessment without importing a benchmark runtime or
 * giving external questions access to owner memory. Callers supply synthetic,
 * consented and/or already privacy-scoped records. No memory writes, training,
 * model calls, tool execution or promotion are performed.
 */
enum class MemoryResearchAbility {
    STATIC_RECALL,
    DYNAMIC_STATE,
    WORKFLOW,
    ENVIRONMENT_GOTCHA,
    PREMISE_AWARENESS
}

data class MemoryResearchProbe(
    val id: String,
    val ability: MemoryResearchAbility,
    val query: String,
    val expectedMemoryIds: Set<String>
) {
    init {
        require(id.matches(Regex("[A-Za-z0-9._:-]{1,96}")))
        require(query.isNotBlank() && query.length <= 512)
        require(expectedMemoryIds.size <= 16)
        require(expectedMemoryIds.none(String::isBlank))
    }
}

data class MemoryResearchProbeResult(
    val probeId: String,
    val ability: MemoryResearchAbility,
    val expectedAbstention: Boolean,
    val correctTopOneOrAbstention: Boolean,
    val anyExpectedWithinLimit: Boolean,
    val reciprocalRank: Double,
    val durationNanos: Long
) {
    init {
        require(reciprocalRank.isFinite() && reciprocalRank in 0.0..1.0)
        require(durationNanos >= 0L)
    }
}

data class MemoryResearchSummary(
    val count: Int,
    val topOneOrAbstentionAccuracy: Double,
    val hitRateAtLimit: Double,
    val meanReciprocalRank: Double,
    val totalDurationNanos: Long,
    val results: List<MemoryResearchProbeResult>
) {
    init {
        require(count == results.size)
        require(listOf(topOneOrAbstentionAccuracy, hitRateAtLimit, meanReciprocalRank)
            .all { it.isFinite() && it in 0.0..1.0 })
        require(totalDurationNanos >= 0L)
    }
}

/**
 * Shadow quality gate over HybridMemoryRetriever: the production retrieval
 * algorithm, owner-authority checks, evidence flags and CRE1 remain untouched.
 * Returning only metrics (not memory content) prevents benchmark reports
 * becoming a second unauthorized personal-memory export path.
 */
object MemoryRetrievalResearchGate {
    fun evaluate(
        probes: List<MemoryResearchProbe>,
        records: Collection<MemoryRecord>,
        allowedPrivacy: Set<PrivacyClass>,
        limit: Int = 8,
        nowMillis: Long = System.currentTimeMillis(),
        semanticScorer: ((String, MemoryRecord) -> Double)? = null,
        nanoClock: () -> Long = System::nanoTime
    ): MemoryResearchSummary {
        require(probes.size in 1..512) { "Provide between 1 and 512 bounded memory probes" }
        require(probes.map { it.id }.distinct().size == probes.size) { "Duplicate probe identifiers" }
        require(records.size <= 10_000) { "Oversized local benchmark corpus" }
        require(limit in 1..32)
        require(nowMillis >= 0L)
        require(allowedPrivacy.isNotEmpty())
        val availableIds = records.filter { it.privacyClass in allowedPrivacy }.map { it.id }.toSet()
        require(probes.all { availableIds.containsAll(it.expectedMemoryIds) }) {
            "Probe references a missing or privacy-forbidden memory"
        }
        val results = probes.map { probe ->
            val start = nanoClock()
            val hits = HybridMemoryRetriever.retrieve(
                query = probe.query,
                records = records,
                limit = limit,
                nowMillis = nowMillis,
                allowedPrivacy = allowedPrivacy,
                semanticScorer = semanticScorer
            )
            val end = nanoClock()
            // nanoTime wraps safely for short intervals on supported JVMs.
            val elapsed = (end - start).coerceAtLeast(0L)
            val firstRank = hits.indexOfFirst { it.memory.id in probe.expectedMemoryIds }
            val abstention = probe.expectedMemoryIds.isEmpty()
            MemoryResearchProbeResult(
                probeId = probe.id,
                ability = probe.ability,
                expectedAbstention = abstention,
                correctTopOneOrAbstention = if (abstention) hits.isEmpty() else firstRank == 0,
                anyExpectedWithinLimit = if (abstention) hits.isEmpty() else firstRank >= 0,
                reciprocalRank = if (firstRank >= 0) 1.0 / (firstRank + 1) else 0.0,
                durationNanos = elapsed
            )
        }
        val denominator = results.size.toDouble()
        val duration = results.fold(0L) { sum, result ->
            if (Long.MAX_VALUE - sum < result.durationNanos) Long.MAX_VALUE
            else sum + result.durationNanos
        }
        return MemoryResearchSummary(
            count = results.size,
            topOneOrAbstentionAccuracy =
                results.count { it.correctTopOneOrAbstention } / denominator,
            hitRateAtLimit = results.count { it.anyExpectedWithinLimit } / denominator,
            meanReciprocalRank = results.sumOf { it.reciprocalRank } / denominator,
            totalDurationNanos = duration,
            results = results.toList()
        )
    }
}
