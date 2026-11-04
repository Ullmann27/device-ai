package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Synthetic functional holdout frozen against ca31eb8b before candidate inspection.
 * These checks exercise the existing retrieval API, not model or device quality.
 */
class MemoryIdentityHoldoutTest {
    @Test
    fun equalCopiesCountOnceAndLeaveEveryScoreUnchanged() {
        val first = fact("equal-a", "zircon beacon")
        val second = fact("equal-b", "zircon archive")
        val unique = retrieve(listOf(first, second))
        val copied = first.copy(
            tags = first.tags.toMutableList(),
            parentIds = first.parentIds.toMutableList(),
            validationEvidenceReferences = first.validationEvidenceReferences.toMutableList()
        )

        assertEquals(unique, retrieve(listOf(first, second, copied, first.copy())))
    }

    @Test
    fun repeatedReferenceCannotConsumeTheDistinctResultLimit() {
        val winner = fact("limit-a", "zircon beacon")
        val other = winner.copy(id = "limit-b")
        val expected = retrieve(listOf(winner, other), limit = 2)

        assertEquals(expected, retrieve(List(12) { winner } + other, limit = 2))
        assertEquals(2, expected.map { it.memory.id }.distinct().size)
    }

    @Test
    fun negatedContentWithTheSameIdIsAmbiguous() {
        val positive = fact("content-conflict", "zircon hatch is open")
        assertAmbiguous(positive, positive.copy(content = "zircon hatch is not open"))
    }

    @Test
    fun inaccessiblePrivacyCounterpartStillMakesThePublicIdentityAmbiguous() {
        val public = fact("privacy-conflict", "zircon public fixture")
        for (privacy in listOf(PrivacyClass.PRIVATE, PrivacyClass.SENSITIVE, PrivacyClass.DEVICE_LOCAL_ONLY)) {
            assertAmbiguous(public, public.copy(privacyClass = privacy))
        }
    }

    @Test
    fun provenanceDifferenceMakesAnIdentityAmbiguous() {
        val original = fact("provenance-conflict", "zircon provenance fixture")
        assertAmbiguous(original, original.copy(provenance = ProvenanceType.MODEL_GENERATED))
    }

    @Test
    fun creationTimestampDifferenceMakesAnIdentityAmbiguous() {
        val original = fact("created-conflict", "zircon clock fixture")
        assertAmbiguous(original, original.copy(createdAt = original.createdAt + 1L))
    }

    @Test
    fun updateTimestampDifferenceMakesAnIdentityAmbiguous() {
        val original = fact("updated-conflict", "zircon clock fixture")
        assertAmbiguous(original, original.copy(updatedAt = original.updatedAt + 1L))
    }

    @Test
    fun evidenceReferenceDifferenceMakesAnIdentityAmbiguous() {
        val original = fact("evidence-conflict", "zircon evidence fixture")
        assertAmbiguous(original, original.copy(validationEvidenceReferences = listOf("holdout://evidence/other")))
    }

    @Test
    fun confidenceDifferenceMakesAnIdentityAmbiguous() {
        val original = fact("confidence-conflict", "zircon confidence fixture")
        assertAmbiguous(original, original.copy(confidence = 0.7))
    }

    @Test
    fun memoryTypeDifferenceMakesAnIdentityAmbiguous() {
        val original = fact("type-conflict", "zircon type fixture")
        assertAmbiguous(original, original.copy(type = MemoryType.PROCEDURAL))
    }

    @Test
    fun tagDifferenceMakesAnIdentityAmbiguous() {
        val original = fact("tags-conflict", "zircon tags fixture")
        assertAmbiguous(original, original.copy(tags = original.tags + "counterfactual"))
    }

    @Test
    fun parentReferenceDifferenceMakesAnIdentityAmbiguous() {
        val original = fact("parents-conflict", "zircon parent fixture")
        assertAmbiguous(original, original.copy(parentIds = listOf("different-parent")))
    }

    @Test
    fun sourceReferenceDifferenceMakesAnIdentityAmbiguous() {
        val original = fact("source-conflict", "zircon source fixture")
        assertAmbiguous(original, original.copy(sourceReference = null))
    }

    @Test
    fun validationStateDifferenceMakesAnIdentityAmbiguous() {
        val original = fact("validation-conflict", "zircon validation fixture")
        assertAmbiguous(original, original.copy(validationState = ValidationState.CORROBORATED))
    }

    @Test
    fun repetitionCannotVoteAnAmbiguousIdentityBackIntoResults() {
        val original = fact("majority-conflict", "zircon first value")
        val conflicting = original.copy(content = "zircon second value")
        val safe = fact("majority-safe", "zircon safe value")
        val expected = retrieve(listOf(safe))

        for (copies in listOf(1, 2, 17)) {
            val mixed = List(copies) { original.copy() } + conflicting + safe
            assertEquals(expected, retrieve(mixed))
            assertEquals(expected, retrieve(mixed.reversed()))
        }
    }

    @Test
    fun excludedConflictsCannotChangeTheScoresOfSafeRecords() {
        val first = fact("clean-a", "zircon beacon")
        val second = fact("clean-b", "zircon archive canal")
        val disputed = fact("excluded-long", "zircon ".repeat(200) + "disputed")
        val conflicting = disputed.copy(content = "unrelated ".repeat(400))
        val safe = listOf(first, second)
        val expected = retrieve(safe)

        for (input in permutations(listOf(first, second, disputed, conflicting))) {
            assertEquals(expected, retrieve(input))
        }
    }

    @Test
    fun scorersReceiveNeitherHiddenNorAmbiguousMemoriesAndSeeExactCopiesOnce() {
        val safe = fact("scorer-safe", "zircon beacon")
        val ambiguous = fact("scorer-ambiguous", "zircon contested")
        val hiddenCounterpart = ambiguous.copy(privacyClass = PrivacyClass.PRIVATE)
        val hidden = fact("scorer-hidden", "zircon hidden").copy(privacyClass = PrivacyClass.SENSITIVE)
        val semanticSeen = mutableListOf<MemoryRecord>()
        val trustSeen = mutableListOf<MemoryRecord>()

        val results = HybridMemoryRetriever.retrieve(
            query = "zircon",
            records = listOf(hidden, ambiguous, safe, hiddenCounterpart, safe.copy()),
            nowMillis = NOW,
            allowedPrivacy = setOf(PrivacyClass.PUBLIC),
            semanticScorer = { _, memory -> semanticSeen += memory; 0.0 },
            sourceTrustScorer = { memory -> trustSeen += memory; 0.5 }
        )

        assertEquals(listOf(safe), semanticSeen)
        assertEquals(listOf(safe), trustSeen)
        assertEquals(retrieve(listOf(safe)), results)
    }

    @Test
    fun uniqueInputPermutationsKeepScoresAndIdTieBreakStable() {
        val a = fact("tie-a", "zircon beacon")
        val b = a.copy(id = "tie-b")
        val older = a.copy(id = "tie-old", updatedAt = NOW - 2L * DAY)
        val unrelated = fact("tie-unrelated", "vellum canal").copy(tags = emptyList())
        val input = listOf(b, older, unrelated, a)
        val expected = retrieve(input)

        assertEquals(listOf("tie-a", "tie-b", "tie-old"), expected.map { it.memory.id })
        for (permutation in permutations(input)) assertEquals(expected, retrieve(permutation))
    }

    @Test
    fun staleAndContradictedEvidenceRemainsAvailableForConflictAudit() {
        val verified = fact("audit-verified", "zircon audit").copy(validationState = ValidationState.VERIFIED)
        val stale = verified.copy(id = "audit-stale", validationState = ValidationState.STALE)
        val contradicted = verified.copy(id = "audit-contradicted", validationState = ValidationState.CONTRADICTED)
        val results = retrieve(listOf(contradicted, stale, verified))

        assertEquals(listOf("audit-verified", "audit-stale", "audit-contradicted"), results.map { it.memory.id })
        assertEquals(0.0, results[0].score.contradictionPenalty, 0.0)
        assertEquals(0.15, results[1].score.contradictionPenalty, 0.0)
        assertEquals(0.35, results[2].score.contradictionPenalty, 0.0)
        assertEquals(0.0, results[2].score.total, 0.0)
    }

    @Test
    fun mixedCaseUnicodeAndLateUniqueTermsRemainSearchableInsideTheExistingTokenBound() {
        val long = fact("unicode-δ", "grund ".repeat(4_000) + "Messpunkt Ωμέγα Größe").copy(tags = emptyList())

        for (query in listOf("MESSPUNKT", "ΩΜΈΓΑ", "GRÖSSE")) {
            val results = HybridMemoryRetriever.retrieve(query, listOf(long), nowMillis = NOW)
            assertEquals("Query: $query", listOf("unicode-δ"), results.map { it.memory.id })
            assertTrue(results.single().score.lexicalBm25 > 0.0)
            assertEquals(long.content, results.single().memory.content)
        }
    }

    @Test
    fun absentTopicCannotBeInventedFromConfidenceOrValidationQuality() {
        val unrelated = fact("absent-topic", "vellum canal").copy(
            tags = listOf("archive"),
            confidence = 1.0,
            provenance = ProvenanceType.DIRECT_OBSERVATION,
            validationState = ValidationState.VERIFIED
        )

        for (query in listOf("plasma orchard", "What is plasma orchard?", "Was ist über plasma orchard bekannt?")) {
            assertTrue(HybridMemoryRetriever.retrieve(query, listOf(unrelated), nowMillis = NOW).isEmpty())
        }
    }

    @Test
    fun nonFiniteSemanticScoresCannotManufactureAnOtherwiseAbsentTopic() {
        val unrelated = fact("nonfinite-absent", "vellum canal").copy(tags = emptyList())

        for (score in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -0.25)) {
            val results = HybridMemoryRetriever.retrieve(
                "plasma orchard", listOf(unrelated), nowMillis = NOW,
                semanticScorer = { _, _ -> score }
            )
            assertTrue("Invalid semantic score: $score", results.isEmpty())
        }
    }

    @Test
    fun scorerFallbackAndFiniteClampingPreserveTheExistingContract() {
        val matching = fact("finite-match", "zircon beacon")
        val expected = retrieve(listOf(matching))
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            val actual = HybridMemoryRetriever.retrieve(
                "zircon", listOf(matching), nowMillis = NOW,
                semanticScorer = { _, _ -> invalid }, sourceTrustScorer = { invalid }
            )
            assertEquals(expected, actual)
        }
        for ((raw, clamped) in listOf(-0.75 to 0.0, 1.75 to 1.0)) {
            val actual = HybridMemoryRetriever.retrieve(
                "zircon", listOf(matching), nowMillis = NOW,
                semanticScorer = { _, _ -> raw }, sourceTrustScorer = { raw }
            ).single().score
            assertEquals(clamped, actual.semanticSimilarity, 0.0)
            assertEquals(clamped, actual.sourceTrust, 0.0)
            assertTrue(scoreValues(actual).all { it.isFinite() && it in 0.0..1.0 })
        }
    }

    @Test
    fun uniqueSafeFixtureRetainsItsFrozenBaselineScoreBreakdownsAndOrder() {
        val amber = fact("gold-amber", "quartz beacon").copy(
            tags = listOf("beacon"), createdAt = NOW, updatedAt = NOW,
            provenance = ProvenanceType.DIRECT_OBSERVATION, validationState = ValidationState.VERIFIED
        )
        val birch = amber.copy(
            id = "gold-birch", createdAt = NOW - DAY, updatedAt = NOW - DAY,
            provenance = ProvenanceType.EXTERNAL_SOURCE, validationState = ValidationState.CORROBORATED
        )
        val cedar = fact("gold-cedar", "quartz vault").copy(
            tags = emptyList(), createdAt = NOW - 10L * DAY, updatedAt = NOW - 10L * DAY
        )
        val dune = fact("gold-dune", "cobalt archive").copy(
            tags = emptyList(), createdAt = NOW, updatedAt = NOW, provenance = ProvenanceType.INFERENCE
        )
        val results = HybridMemoryRetriever.retrieve(
            "quartz beacon", listOf(dune, cedar, amber, birch), nowMillis = NOW,
            semanticScorer = { _, memory -> when (memory.id) { "gold-amber" -> 0.2; "gold-birch" -> 0.75; else -> 0.0 } },
            sourceTrustScorer = { memory -> when (memory.id) { "gold-amber" -> 0.9; "gold-birch" -> 0.4; "gold-cedar" -> 0.8; else -> 0.5 } }
        )

        assertEquals(listOf("gold-birch", "gold-amber", "gold-cedar"), results.map { it.memory.id })
        assertScore(results[0].score, listOf(0.23547711878820507, 0.75, 0.5, 0.5, 0.4, 0.85, 0.85, 0.967741935483871, 0.0, 0.5335302324106551))
        assertScore(results[1].score, listOf(0.23547711878820507, 0.2, 0.5, 0.5, 0.9, 1.0, 1.0, 1.0, 0.0, 0.4526431356364615))
        assertScore(results[2].score, listOf(0.08851807904991209, 0.0, 0.0, 0.5, 0.8, 0.45, 0.95, 0.75, 0.0, 0.23805542371497365))
    }

    @Test
    fun orderedMetadataDiscrepanciesAlsoRemainAmbiguous() {
        val original = fact("ordered-conflict", "zircon metadata").copy(tags = listOf("zircon", "beacon"))
        for (changed in listOf(
            original.copy(tags = original.tags.reversed()),
            original.copy(parentIds = original.parentIds.reversed()),
            original.copy(validationEvidenceReferences = original.validationEvidenceReferences.reversed())
        )) assertAmbiguous(original, changed)
    }

    private fun assertAmbiguous(first: MemoryRecord, second: MemoryRecord) {
        assertEquals(first.id, second.id)
        assertTrue(first != second)
        val safe = fact("unambiguous-anchor", "zircon safe anchor")
        val expected = retrieve(listOf(safe))
        for (input in permutations(listOf(first, second, safe))) {
            assertEquals("Ambiguous id: ${first.id}", expected, retrieve(input))
        }
        assertTrue(retrieve(listOf(first, second)).isEmpty())
        assertTrue(retrieve(listOf(second, first)).isEmpty())
    }

    private fun retrieve(records: List<MemoryRecord>, limit: Int = 8): List<ScoredMemory> =
        HybridMemoryRetriever.retrieve(
            "zircon", records, limit = limit, nowMillis = NOW,
            allowedPrivacy = setOf(PrivacyClass.PUBLIC)
        )

    private fun fact(id: String, content: String) = MemoryRecord(
        id = id,
        type = MemoryType.SEMANTIC,
        content = content,
        provenance = ProvenanceType.USER_PROVIDED,
        confidence = 0.9,
        createdAt = NOW - 2L * DAY,
        updatedAt = NOW - DAY,
        tags = listOf("zircon"),
        parentIds = listOf("synthetic-parent-a", "synthetic-parent-b"),
        sourceReference = "holdout://source/synthetic",
        validationState = ValidationState.UNVERIFIED,
        privacyClass = PrivacyClass.PUBLIC,
        validationEvidenceReferences = listOf("holdout://evidence/a", "holdout://evidence/b")
    )

    private fun <T> permutations(values: List<T>): List<List<T>> =
        if (values.isEmpty()) listOf(emptyList()) else values.indices.flatMap { index ->
            val remainder = values.filterIndexed { other, _ -> other != index }
            permutations(remainder).map { listOf(values[index]) + it }
        }

    private fun assertScore(actual: MemoryScoreBreakdown, expected: List<Double>) {
        val values = scoreValues(actual)
        assertEquals(expected.size, values.size)
        expected.zip(values).forEachIndexed { index, (gold, observed) ->
            assertEquals("Score component $index", gold, observed, 1e-12)
        }
    }

    private fun scoreValues(score: MemoryScoreBreakdown) = listOf(
        score.lexicalBm25, score.semanticSimilarity, score.entityMatch,
        score.temporalRelevance, score.sourceTrust, score.validationQuality,
        score.provenanceQuality, score.recency, score.contradictionPenalty, score.total
    )

    companion object {
        private const val DAY = 86_400_000L
        private const val NOW = 100L * DAY
    }
}
