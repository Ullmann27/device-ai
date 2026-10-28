package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MemoryResearchShadowComparatorTest {
    @Test fun mainAppSyntheticSelfCheckCoversFiveAbilitiesWithoutClaimingImprovement() {
        val check = MemoryResearchShadowComparator.syntheticSelfCheck()
        assertEquals(1, check.developmentBaseline.count)
        assertEquals(5, check.holdoutBaseline.count)
        assertEquals(MemoryResearchAbility.values().toSet(),
            check.holdoutBaseline.results.map { it.ability }.toSet())
        assertTrue(check.developmentBaseline.results.all { it.correctTopOneOrAbstention })
        assertTrue(check.developmentCandidate.results.all { it.correctTopOneOrAbstention })
        assertTrue(check.holdoutBaseline.results.all { it.correctTopOneOrAbstention })
        assertTrue(check.holdoutCandidate.results.all { it.correctTopOneOrAbstention })
        assertTrue(check.holdoutBaseline.results.single { it.expectedAbstention }
            .correctTopOneOrAbstention)
        assertTrue(check.noPerProbeRegression)
        assertFalse(check.developmentImproved)
        assertFalse(check.holdoutImproved)
        assertFalse(check.eligibleForOwnerReview)
    }

    private fun memory(
        id: String,
        content: String,
        validation: ValidationState = ValidationState.VERIFIED,
        privacy: PrivacyClass = PrivacyClass.PUBLIC
    ) = MemoryRecord(
        id = id,
        type = MemoryType.SEMANTIC,
        content = content,
        provenance = ProvenanceType.DIRECT_OBSERVATION,
        confidence = 0.9,
        createdAt = 1_000L,
        updatedAt = 1_000L,
        privacyClass = privacy,
        validationState = validation,
        validationEvidenceReferences = if (validation == ValidationState.VERIFIED)
            listOf("synthetic-evidence:$id") else emptyList()
    )

    private val records = listOf(
        memory("warm", "handset becomes warm during sustained inference"),
        memory("battery", "battery capacity decreases during background work"),
        memory("engine", "approved engine changed from old to Gemma"),
        memory("tool", "owner must approve tool execution"),
        memory("folder", "working folder contains the app source")
    )
    private val development = listOf(
        MemoryResearchProbe(
            "dev-thermal", MemoryResearchAbility.ENVIRONMENT_GOTCHA,
            "thermal throttling", setOf("warm")
        )
    )
    private val holdout = listOf(
        MemoryResearchProbe(
            "test-static", MemoryResearchAbility.STATIC_RECALL,
            "battery capacity", setOf("battery")
        ),
        MemoryResearchProbe(
            "test-dynamic", MemoryResearchAbility.DYNAMIC_STATE,
            "approved engine", setOf("engine")
        ),
        MemoryResearchProbe(
            "test-workflow", MemoryResearchAbility.WORKFLOW,
            "approve tool", setOf("tool")
        ),
        MemoryResearchProbe(
            "test-environment", MemoryResearchAbility.ENVIRONMENT_GOTCHA,
            "heat management", setOf("warm")
        ),
        MemoryResearchProbe(
            "test-unknown", MemoryResearchAbility.PREMISE_AWARENESS,
            "unrelatedwombat", emptySet()
        )
    )

    @Test fun betterLocalSemanticRecallStillRequiresManualReview() {
        val comparison = MemoryResearchShadowComparator.compare(
            developmentProbes = development,
            frozenHoldoutProbes = holdout,
            records = records,
            allowedPrivacy = setOf(PrivacyClass.PUBLIC),
            candidateSemanticScorer = { query, memory ->
                if (memory.id == "warm" &&
                    (query == "thermal throttling" || query == "heat management")) 0.9 else 0.0
            },
            nowMillis = 1_000L
        )
        assertTrue(comparison.developmentImproved)
        assertTrue(comparison.holdoutImproved)
        assertTrue(comparison.noPerProbeRegression)
        assertTrue(comparison.eligibleForOwnerReview)
        assertEquals(0.0, comparison.developmentBaseline.hitRateAtLimit, 0.0)
        assertEquals(1.0, comparison.developmentCandidate.hitRateAtLimit, 0.0)
        assertEquals(0.0, comparison.holdoutBaseline.results[3].reciprocalRank, 0.0)
        assertEquals(1.0, comparison.holdoutCandidate.results[3].reciprocalRank, 0.0)
        assertTrue(comparison.holdoutCandidate.results[4].correctTopOneOrAbstention)
    }

    @Test fun semanticOverRetrievalIsRejectedWhenAbstentionRegresses() {
        val comparison = MemoryResearchShadowComparator.compare(
            developmentProbes = development,
            frozenHoldoutProbes = holdout,
            records = records,
            allowedPrivacy = setOf(PrivacyClass.PUBLIC),
            candidateSemanticScorer = { query, memory ->
                if (query == "unrelatedwombat" ||
                    (memory.id == "warm" &&
                        (query == "thermal throttling" || query == "heat management"))) 0.9 else 0.0
            },
            nowMillis = 1_000L
        )
        assertFalse(comparison.noPerProbeRegression)
        assertFalse(comparison.holdoutImproved)
        assertFalse(comparison.eligibleForOwnerReview)
        assertFalse(comparison.holdoutCandidate.results[4].correctTopOneOrAbstention)
    }

    @Test fun identicalDevelopmentAndHoldoutQuestionsAreRejected() {
        val duplicated = holdout.mapIndexed { index, probe ->
            if (index == 0) probe.copy(query = "thermal throttling") else probe
        }
        try {
            MemoryResearchShadowComparator.compare(
                development, duplicated, records, setOf(PrivacyClass.PUBLIC),
                candidateSemanticScorer = { _, _ -> 0.0 }, nowMillis = 1_000L
            )
            fail("Leaked holdout query was accepted")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("overlap"))
        }
    }

    @Test fun contradictedOrPrivateGoldClaimsAreNotAllowed() {
        for (invalid in listOf(
            memory("warm", "handset becomes warm during sustained inference", ValidationState.CONTRADICTED),
            memory("warm", "handset becomes warm during sustained inference", privacy = PrivacyClass.PRIVATE)
        )) {
            try {
                MemoryResearchShadowComparator.compare(
                    development, holdout, records.map { if (it.id == "warm") invalid else it },
                    setOf(PrivacyClass.PUBLIC), candidateSemanticScorer = { _, _ -> 0.0 },
                    nowMillis = 1_000L
                )
                fail("Invalid gold claim was accepted")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message!!.contains("verified"))
            }
        }
    }

    @Test fun incompleteHoldoutOrDuplicateRecordIdsFailClosed() {
        val cases = listOf(
            holdout.dropLast(1) to records,
            holdout to (records + records.first())
        )
        for ((probes, corpus) in cases) {
            try {
                MemoryResearchShadowComparator.compare(
                    development, probes, corpus, setOf(PrivacyClass.PUBLIC),
                    candidateSemanticScorer = { _, _ -> 0.0 }, nowMillis = 1_000L
                )
                fail("Invalid evaluation input was accepted")
            } catch (_: IllegalArgumentException) {}
        }
    }
}
