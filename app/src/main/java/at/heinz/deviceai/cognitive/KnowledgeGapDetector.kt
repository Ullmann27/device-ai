package at.heinz.deviceai.cognitive

import java.util.Locale

/**
 * Bounded proposal-only detector for recurring knowledge gaps.
 * It never starts research; existing internet consent and Research Engine remain authoritative.
 */
internal class KnowledgeGapDetector(
    private val maxTopics: Int = 64
) {
    enum class Outcome { ANSWERED, QUALIFIED, RESEARCH_NEEDED, ABSTAINED, FAILED_VERIFICATION }

    data class TopicState(
        val topic: String,
        val observations: Int,
        val uncertainObservations: Int,
        val lastSeenAt: Long,
        val projectRelevance: Float,
        val freshnessNeed: Float,
        val estimatedResearchCost: Float
    ) {
        init {
            require(topic.isNotBlank() && topic.length <= 120)
            require(observations >= 1 && uncertainObservations in 0..observations)
            require(lastSeenAt >= 0)
            require(projectRelevance.isFinite() && projectRelevance in 0f..1f)
            require(freshnessNeed.isFinite() && freshnessNeed in 0f..1f)
            require(estimatedResearchCost.isFinite() && estimatedResearchCost > 0f)
        }

        val uncertaintyRate: Float get() = uncertainObservations.toFloat() / observations

        fun priority(nowMillis: Long): Float {
            require(nowMillis >= 0)
            val ageDays = ((nowMillis - lastSeenAt).coerceAtLeast(0L) / 86_400_000f)
            val recency = 1f / (1f + .08f * ageDays)
            val repeat = uncertainObservations.toFloat().let { it * it }
            return repeat * uncertaintyRate *
                (0.5f + projectRelevance) *
                (0.5f + freshnessNeed) *
                recency / estimatedResearchCost
        }
    }

    data class Proposal(
        val topic: String,
        val priority: Float,
        val reason: String
    )

    private val topics = LinkedHashMap<String, TopicState>()

    fun observe(
        topic: String,
        outcome: Outcome,
        atMillis: Long,
        projectRelevance: Float,
        freshnessNeed: Float,
        estimatedResearchCost: Float = 1f
    ) {
        require(atMillis >= 0)
        require(projectRelevance.isFinite() && projectRelevance in 0f..1f)
        require(freshnessNeed.isFinite() && freshnessNeed in 0f..1f)
        require(estimatedResearchCost.isFinite() && estimatedResearchCost > 0f)
        val key = normalizeTopic(topic)
        require(key.isNotBlank())
        val uncertain = outcome in UNCERTAIN
        val old = topics[key]
        val next = if (old == null) TopicState(
            key, 1, if (uncertain) 1 else 0, atMillis,
            projectRelevance, freshnessNeed, estimatedResearchCost
        ) else old.copy(
            observations = old.observations + 1,
            uncertainObservations = old.uncertainObservations + if (uncertain) 1 else 0,
            lastSeenAt = maxOf(old.lastSeenAt, atMillis),
            projectRelevance = ema(old.projectRelevance, projectRelevance),
            freshnessNeed = ema(old.freshnessNeed, freshnessNeed),
            estimatedResearchCost = ema(old.estimatedResearchCost, estimatedResearchCost)
        )
        topics[key] = next
        trim()
    }

    fun proposals(nowMillis: Long, minimumPriority: Float = 1.5f, limit: Int = 5): List<Proposal> {
        require(nowMillis >= 0)
        require(minimumPriority.isFinite() && minimumPriority >= 0f)
        require(limit in 1..16)
        return topics.values.asSequence()
            .map { it to it.priority(nowMillis) }
            .filter { it.second >= minimumPriority && it.first.uncertainObservations >= 2 }
            .sortedWith(compareByDescending<Pair<TopicState, Float>> { it.second }
                .thenByDescending { it.first.lastSeenAt }.thenBy { it.first.topic })
            .take(limit)
            .map { pair ->
                val state = pair.first
                Proposal(
                    state.topic,
                    pair.second,
                    "Wiederkehrende Unsicherheit: " + state.uncertainObservations + "/" +
                        state.observations + "; Recherche bleibt freigabepflichtig."
                )
            }.toList()
    }

    fun snapshot(): List<TopicState> = topics.values.toList()

    private fun trim() {
        while (topics.size > maxTopics) {
            val victim = topics.values.minWithOrNull(
                compareBy<TopicState> { it.uncertainObservations }
                    .thenBy { it.lastSeenAt }.thenBy { it.topic }
            ) ?: break
            topics.remove(victim.topic)
        }
    }

    private fun normalizeTopic(value: String): String =
        value.lowercase(Locale.GERMAN)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(120)

    private fun ema(old: Float, next: Float): Float = old + .2f * (next - old)

    companion object {
        private val UNCERTAIN = setOf(
            Outcome.QUALIFIED, Outcome.RESEARCH_NEEDED,
            Outcome.ABSTAINED, Outcome.FAILED_VERIFICATION
        )
    }
}
