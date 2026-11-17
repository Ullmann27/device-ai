package at.heinz.deviceai.cognitive

import at.heinz.deviceai.cognitive.fixtures.HybridMemoryRetriever as BaselineRetriever
import at.heinz.deviceai.cognitive.fixtures.MemoryScoreBreakdown as BaselineScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class HybridMemoryRetrievalParityTest {
    @Test
    fun uniqueIdentityScoresMatchTheFrozenBaselineAcrossQueriesAndPrivacyClasses() {
        val random = Random(71_204L)
        val topics = listOf("Werkstatt", "Kinder", "Neffen", "Großeltern", "Zahnarzttermin", "Android", "Wetter", "Motor")
        val records = (0 until 128).map { index ->
            val state = ValidationState.values()[index % ValidationState.values().size]
            memory("record-$index", List(8 + random.nextInt(24)) { topics[random.nextInt(topics.size)] }.joinToString(" "))
                .copy(
                    tags = if (index % 3 == 0) listOf(topics[index % topics.size], "synthetisch") else emptyList(),
                    privacyClass = PrivacyClass.values()[index % PrivacyClass.values().size],
                    provenance = ProvenanceType.values()[index % ProvenanceType.values().size],
                    validationState = state,
                    validationEvidenceReferences = if (state == ValidationState.VERIFIED) listOf("synthetic://$index") else emptyList(),
                    updatedAt = NOW - (index % 80) * DAY
                )
        }
        val semantic: (String, MemoryRecord) -> Double = { _, record ->
            when (record.id.substringAfter('-').toInt() % 7) {
                0 -> Double.NaN
                1 -> Double.POSITIVE_INFINITY
                2 -> -0.5
                3 -> 1.5
                else -> 0.35
            }
        }
        val trust: (MemoryRecord) -> Double = { record ->
            if (record.id.endsWith("1")) Double.NaN else if (record.id.endsWith("2")) -1.0 else 0.8
        }

        for (query in listOf("Werkstatt", "Kinder Neffen", "Grosseltern", "Termin heute", "Android aktuell", "Motor Wetter", "unauffindbar", "Was ist das?")) {
            for (limit in listOf(1, 8, 200)) {
                for (privacy in listOf(setOf(PrivacyClass.PUBLIC), PrivacyClass.values().toSet())) {
                    assertParity(query, records, limit, privacy)
                    assertParity(query, records, limit, privacy, semantic, trust)
                }
            }
        }
    }

    @Test
    fun scoreTiesUseTimestampThenIdRegardlessOfInputOrder() {
        val records = listOf(
            memory("z", "Werkstatt"), memory("a", "Werkstatt"),
            memory("old", "Werkstatt").copy(updatedAt = NOW - DAY)
        )
        for (order in listOf(records, records.reversed(), listOf(records[1], records[2], records[0]))) {
            assertEquals(listOf("a", "z", "old"), retrieve("Werkstatt", order).map { it.memory.id })
            assertParity("Werkstatt", order, limit = 1)
            assertParity("Werkstatt", order, limit = 2)
            assertParity("Werkstatt", order, limit = 10)
        }
    }

    @Test
    fun aLateUniqueMatchSurvivesAResultLimitOfOne() {
        val records = (0 until 1_024).map { memory("noise-$it", "Neutrale synthetische Nachricht.") } +
            memory("last", "Quantenkristall ist hier dokumentiert.")

        assertEquals(listOf("last"), HybridMemoryRetriever.retrieve("Quantenkristall", records, 1, NOW).map { it.memory.id })
        assertParity("Quantenkristall", records, limit = 1)
    }

    @Test
    fun tokenBoundaryAndGermanMorphologyRemainUnchanged() {
        val records = listOf(
            memory("boundary", List(4_095) { "neutral" }.joinToString(" ") + " einzigartig unsichtbar"),
            memory("family", "Kinder Neffen Großeltern"),
            memory("compound", "Zahnarzttermin Werkstattplanung"),
            memory("tag", "Neutrale Notiz").copy(tags = listOf("Kühlung", "Öffnungszeiten"))
        )
        for (query in listOf("einzigartig", "unsichtbar", "Kind", "Neffe", "Grosseltern", "Termin", "Planung", "Kuhlung", "Offnungszeiten")) {
            assertParity(query, records)
        }
        assertEquals(listOf("boundary"), retrieve("einzigartig", records).map { it.memory.id })
        assertTrue(retrieve("unsichtbar", records).isEmpty())
        assertEquals(listOf("family"), retrieve("Kind Neffe Grosseltern", records).map { it.memory.id })
        assertEquals(listOf("compound"), retrieve("Termin", records).map { it.memory.id })
    }

    @Test
    fun irrelevantOrGrammarOnlyQueriesHaveNoEvidence() {
        val records = listOf(memory("private", "Meine Schwester ist Ärztin in Wien."))
        for (query in listOf("", "Was ist das?", "What is photosynthesis?", "Was weißt du über Photosynthese?")) {
            assertTrue(retrieve(query, records).isEmpty())
            assertParity(query, records)
        }
        assertParity("Werkstatt", emptyList())
        assertParity("Wien", records, limit = 0)
    }

    @Test
    fun contradictedAndStaleRecordsRemainAvailableForExplicitAudits() {
        val records = listOf(
            memory("contradicted", "Werkstatt").copy(validationState = ValidationState.CONTRADICTED),
            memory("stale", "Werkstatt").copy(validationState = ValidationState.STALE),
            memory("current", "Werkstatt")
        )

        assertEquals(listOf("current", "stale", "contradicted"), retrieve("Werkstatt", records).map { it.memory.id })
        assertParity("Werkstatt", records)
    }

    private fun assertParity(
        query: String,
        records: List<MemoryRecord>,
        limit: Int = 8,
        privacy: Set<PrivacyClass> = PrivacyClass.values().toSet(),
        semantic: ((String, MemoryRecord) -> Double)? = null,
        trust: ((MemoryRecord) -> Double)? = null
    ) {
        val expected = BaselineRetriever.retrieve(query, records, limit, NOW, privacy, semantic, trust)
        val actual = HybridMemoryRetriever.retrieve(query, records, limit, NOW, privacy, semantic, trust)
        assertEquals("Ranked records for '$query', limit=$limit, privacy=$privacy", expected.map { it.memory }, actual.map { it.memory })
        assertEquals("Every score component for '$query'", expected.map { it.score.asCurrentScore() }, actual.map { it.score })
    }

    private fun BaselineScore.asCurrentScore() = MemoryScoreBreakdown(
        lexicalBm25, semanticSimilarity, entityMatch, temporalRelevance, sourceTrust,
        validationQuality, provenanceQuality, recency, contradictionPenalty, total
    )

    private fun retrieve(query: String, records: List<MemoryRecord>) =
        HybridMemoryRetriever.retrieve(query, records, nowMillis = NOW)

    private fun memory(id: String, content: String) = MemoryRecord(
        id, MemoryType.SEMANTIC, content, ProvenanceType.USER_PROVIDED, 0.9,
        createdAt = 0, updatedAt = NOW, privacyClass = PrivacyClass.PUBLIC
    )

    private companion object {
        const val NOW = 1_730_678_400_000L
        const val DAY = 86_400_000L
    }
}
