package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridMemoryRetrieverTest {
    @Test
    fun functionWordOverlapDoesNotRecallUnrelatedOwnerMemory() {
        val now = 100L * DAY
        val unrelated = memory(
            id = "private-unrelated",
            content = "Meine Schwester ist Ärztin in Wien.",
            updatedAt = now
        ).copy(tags = emptyList())

        for (query in listOf("Was ist Photosynthese?", "Wie ist das Wetter?", "What is photosynthesis?")) {
            val candidate = if (query.startsWith("What"))
                unrelated.copy(content = "My sister is a doctor in Vienna.") else unrelated
            assertTrue(
                "Unrelated private memory was recalled for: $query",
                HybridMemoryRetriever.retrieve(query, listOf(candidate), nowMillis = now).isEmpty()
            )
        }
    }

    @Test
    fun removingFunctionWordsPreservesAUniqueTopicOrEntityMatch() {
        val now = 100L * DAY
        val fact = memory(
            id = "owner-location",
            content = "Meine Schwester ist Ärztin in Wien.",
            updatedAt = now
        ).copy(tags = listOf("Schwester"))

        for (query in listOf("Wien", "Was ist über meine Schwester bekannt?")) {
            assertEquals(
                listOf("owner-location"),
                HybridMemoryRetriever.retrieve(query, listOf(fact), nowMillis = now).map { it.memory.id }
            )
        }
        assertTrue(HybridMemoryRetriever.retrieve("Was ist das?", listOf(fact), nowMillis = now).isEmpty())
    }

    @Test
    fun verifiedRecentMemoryOutranksContradictedOlderMemory() {
        val now = 100L * DAY
        val recent = memory(
            id = "recent",
            content = "The latest local model benchmark selected Gemma for the device.",
            updatedAt = now - DAY,
            validation = ValidationState.VERIFIED,
            evidence = listOf("benchmark://recent")
        )
        val contradicted = memory(
            id = "old",
            content = "The latest local model benchmark selected Gemma for the device.",
            updatedAt = now - 90L * DAY,
            validation = ValidationState.CONTRADICTED
        )

        val results = HybridMemoryRetriever.retrieve(
            query = "latest local model benchmark",
            records = listOf(contradicted, recent),
            nowMillis = now
        )

        assertEquals(listOf("recent", "old"), results.map { it.memory.id })
        assertTrue(results.first().score.total > results.last().score.total)
        assertTrue(results.last().score.contradictionPenalty > 0.0)
    }

    @Test
    fun privacyFilterIsEnforcedBeforeScoring() {
        val now = 10L * DAY
        val privateMemory = memory(
            id = "private",
            content = "My preferred Android model is Gemma.",
            updatedAt = now,
            privacy = PrivacyClass.PRIVATE
        )
        val publicMemory = memory(
            id = "public",
            content = "Android model documentation mentions Gemma.",
            updatedAt = now,
            privacy = PrivacyClass.PUBLIC
        )

        val results = HybridMemoryRetriever.retrieve(
            query = "Android Gemma model",
            records = listOf(privateMemory, publicMemory),
            nowMillis = now,
            allowedPrivacy = setOf(PrivacyClass.PUBLIC)
        )

        assertEquals(listOf("public"), results.map { it.memory.id })
    }

    @Test
    fun optionalSemanticScoreCanRecoverAConceptWithoutLexicalOverlap() {
        val now = 20L * DAY
        val memory = memory(
            id = "semantic",
            content = "The handset becomes warm during sustained inference.",
            updatedAt = now
        )

        val lexicalOnly = HybridMemoryRetriever.retrieve(
            query = "thermal throttling",
            records = listOf(memory),
            nowMillis = now
        )
        assertTrue(lexicalOnly.isEmpty())

        val semantic = HybridMemoryRetriever.retrieve(
            query = "thermal throttling",
            records = listOf(memory),
            nowMillis = now,
            semanticScorer = { _, candidate -> if (candidate.id == "semantic") 0.9 else 0.0 }
        )

        assertEquals("semantic", semantic.single().memory.id)
        assertEquals(0.9, semantic.single().score.semanticSimilarity, 0.0001)
    }

    private fun memory(
        id: String,
        content: String,
        updatedAt: Long,
        validation: ValidationState = ValidationState.UNVERIFIED,
        evidence: List<String> = emptyList(),
        privacy: PrivacyClass = PrivacyClass.PRIVATE
    ) = MemoryRecord(
        id = id,
        type = MemoryType.SEMANTIC,
        content = content,
        provenance = ProvenanceType.USER_PROVIDED,
        confidence = 0.9,
        createdAt = updatedAt,
        updatedAt = updatedAt,
        tags = listOf("android", "model"),
        validationState = validation,
        privacyClass = privacy,
        validationEvidenceReferences = evidence
    )

    companion object {
        private const val DAY = 86_400_000L
    }
}
