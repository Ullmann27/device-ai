package at.heinz.deviceai.cognitive

import java.util.Locale
import kotlin.math.ln

data class MemoryScoreBreakdown(
    val lexicalBm25: Double,
    val semanticSimilarity: Double,
    val entityMatch: Double,
    val temporalRelevance: Double,
    val sourceTrust: Double,
    val validationQuality: Double,
    val provenanceQuality: Double,
    val recency: Double,
    val contradictionPenalty: Double,
    val total: Double
) {
    init {
        listOf(
            lexicalBm25,
            semanticSimilarity,
            entityMatch,
            temporalRelevance,
            sourceTrust,
            validationQuality,
            provenanceQuality,
            recency,
            contradictionPenalty,
            total
        ).forEach { require(it.isFinite()) }
        require(
            listOf(
                lexicalBm25,
                semanticSimilarity,
                entityMatch,
                temporalRelevance,
                sourceTrust,
                validationQuality,
                provenanceQuality,
                recency,
                contradictionPenalty
            ).all { it in 0.0..1.0 }
        )
        require(total in 0.0..1.0)
    }
}

data class ScoredMemory(
    val memory: MemoryRecord,
    val score: MemoryScoreBreakdown
)

object HybridMemoryRetriever {
    fun retrieve(
        query: String,
        records: Collection<MemoryRecord>,
        limit: Int = 8,
        nowMillis: Long = System.currentTimeMillis(),
        allowedPrivacy: Set<PrivacyClass> = PrivacyClass.values().toSet(),
        semanticScorer: ((String, MemoryRecord) -> Double)? = null,
        sourceTrustScorer: ((MemoryRecord) -> Double)? = null
    ): List<ScoredMemory> {
        require(limit >= 0)
        require(nowMillis >= 0)
        if (limit == 0) return emptyList()

        val queryTerms = tokens(query).toSet()
        if (queryTerms.isEmpty()) return emptyList()

        val candidates = records.filter { it.privacyClass in allowedPrivacy }
        if (candidates.isEmpty()) return emptyList()

        val tokenized = candidates.associateWith { memory ->
            tokens(memory.content + " " + memory.tags.joinToString(" "))
        }
        val averageDocumentLength = tokenized.values.map { it.size }.average().coerceAtLeast(1.0)
        val documentFrequency = queryTerms.associateWith { term ->
            tokenized.values.count { term in it }.coerceAtLeast(0)
        }
        val temporalQuery = queryTerms.any { it in TEMPORAL_TERMS }

        return candidates.asSequence()
            .map { memory ->
                val documentTerms = tokenized.getValue(memory)
                val lexical = bm25Like(
                    queryTerms,
                    documentTerms,
                    documentFrequency,
                    candidates.size,
                    averageDocumentLength
                )
                val entity = entityScore(queryTerms, memory.tags)
                val semantic = semanticScorer
                    ?.invoke(query, memory)
                    ?.takeIf(Double::isFinite)
                    ?.coerceIn(0.0, 1.0)
                    ?: 0.0

                if (lexical <= 0.0 && entity <= 0.0 && semantic <= 0.0) {
                    return@map null
                }

                val temporal = temporalScore(memory, nowMillis, temporalQuery)
                val sourceTrust = sourceTrustScorer
                    ?.invoke(memory)
                    ?.takeIf(Double::isFinite)
                    ?.coerceIn(0.0, 1.0)
                    ?: 0.5
                val validation = validationScore(memory.validationState)
                val provenance = provenanceScore(memory.provenance)
                val recency = recencyScore(memory.updatedAt, nowMillis)
                val penalty = when (memory.validationState) {
                    ValidationState.CONTRADICTED -> 0.35
                    ValidationState.STALE -> 0.15
                    else -> 0.0
                }

                val weighted =
                    0.30 * lexical +
                        0.25 * semantic +
                        0.12 * entity +
                        0.10 * temporal +
                        0.08 * sourceTrust +
                        0.07 * validation +
                        0.03 * provenance +
                        0.05 * recency

                val total = (weighted - penalty).coerceIn(0.0, 1.0)
                ScoredMemory(
                    memory,
                    MemoryScoreBreakdown(
                        lexicalBm25 = lexical,
                        semanticSimilarity = semantic,
                        entityMatch = entity,
                        temporalRelevance = temporal,
                        sourceTrust = sourceTrust,
                        validationQuality = validation,
                        provenanceQuality = provenance,
                        recency = recency,
                        contradictionPenalty = penalty,
                        total = total
                    )
                )
            }
            .filterNotNull()
            .filter { it.score.total > 0.0 }
            .sortedWith(
                compareByDescending<ScoredMemory> { it.score.total }
                    .thenByDescending { it.memory.updatedAt }
                    .thenBy { it.memory.id }
            )
            .take(limit)
            .toList()
    }

    private fun bm25Like(
        queryTerms: Set<String>,
        documentTerms: List<String>,
        documentFrequency: Map<String, Int>,
        documentCount: Int,
        averageDocumentLength: Double
    ): Double {
        if (documentTerms.isEmpty()) return 0.0
        val termFrequency = documentTerms.groupingBy { it }.eachCount()
        val length = documentTerms.size.toDouble()
        var raw = 0.0
        queryTerms.forEach { term ->
            val tf = termFrequency[term]?.toDouble() ?: return@forEach
            val df = (documentFrequency[term] ?: 0).toDouble()
            val idf = ln(1.0 + ((documentCount - df + 0.5) / (df + 0.5)))
            val denominator = tf + K1 * (1.0 - B + B * length / averageDocumentLength)
            raw += idf * (tf * (K1 + 1.0) / denominator)
        }
        return if (raw <= 0.0) 0.0 else (raw / (raw + BM25_NORMALIZER)).coerceIn(0.0, 1.0)
    }

    private fun entityScore(queryTerms: Set<String>, tags: List<String>): Double {
        if (tags.isEmpty()) return 0.0
        val tagTerms = tags.flatMap(::tokens).toSet()
        if (tagTerms.isEmpty()) return 0.0
        return queryTerms.intersect(tagTerms).size.toDouble() / queryTerms.size.toDouble()
    }

    private fun temporalScore(memory: MemoryRecord, nowMillis: Long, temporalQuery: Boolean): Double {
        if (!temporalQuery) return 0.5
        val ageDays = ageDays(memory.updatedAt, nowMillis)
        return when {
            ageDays <= 1.0 -> 1.0
            ageDays <= 7.0 -> 0.85
            ageDays <= 30.0 -> 0.60
            ageDays <= 180.0 -> 0.35
            else -> 0.15
        }
    }

    private fun recencyScore(updatedAt: Long, nowMillis: Long): Double {
        val ageDays = ageDays(updatedAt, nowMillis)
        return (1.0 / (1.0 + ageDays / 30.0)).coerceIn(0.0, 1.0)
    }

    private fun ageDays(updatedAt: Long, nowMillis: Long): Double {
        val ageMillis = (nowMillis - updatedAt).coerceAtLeast(0L)
        return ageMillis.toDouble() / MILLIS_PER_DAY.toDouble()
    }

    private fun validationScore(state: ValidationState): Double = when (state) {
        ValidationState.VERIFIED -> 1.0
        ValidationState.CORROBORATED -> 0.85
        ValidationState.UNVERIFIED -> 0.45
        ValidationState.STALE -> 0.20
        ValidationState.CONTRADICTED -> 0.0
    }

    private fun provenanceScore(provenance: ProvenanceType): Double = when (provenance) {
        ProvenanceType.DIRECT_OBSERVATION -> 1.0
        ProvenanceType.DEVICE_OBSERVATION -> 0.95
        ProvenanceType.USER_PROVIDED -> 0.95
        ProvenanceType.EXTERNAL_SOURCE -> 0.85
        ProvenanceType.INFERENCE -> 0.65
        ProvenanceType.MODEL_GENERATED -> 0.40
        ProvenanceType.UNVERIFIED -> 0.20
    }

    private fun tokens(text: String): List<String> =
        text.lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter(String::isNotBlank)
            .take(MAX_TOKENS)

    private val TEMPORAL_TERMS = setOf(
        "today", "yesterday", "recent", "recently", "latest", "current", "now",
        "heute", "gestern", "kürzlich", "zuletzt", "aktuell", "jetzt"
    )

    private const val K1 = 1.2
    private const val B = 0.75
    private const val BM25_NORMALIZER = 4.0
    private const val MAX_TOKENS = 4_096
    private const val MILLIS_PER_DAY = 86_400_000L
}
