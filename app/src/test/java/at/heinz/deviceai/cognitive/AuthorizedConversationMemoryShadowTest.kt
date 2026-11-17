package at.heinz.deviceai.cognitive

import at.heinz.deviceai.learning.ContinuumDialogueTurn
import at.heinz.deviceai.learning.ContinuumMemoryHit
import at.heinz.deviceai.learning.ContinuumShadowScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorizedConversationMemoryShadowTest {
    private val firstId = "lx-aaaaaaaaaaaaaaaaaaaaaaaa"
    private val secondId = "lx-bbbbbbbbbbbbbbbbbbbbbbbb"

    private fun hit(
        id: String,
        text: String = "Gemma engine",
        kind: String = "Bestätigte Nutzererinnerung",
        score: Double = 0.6
    ) = ContinuumMemoryHit(id, text, kind, null, score, 0.0)

    private fun turn(ids: List<String>, query: String = "Gemma engine") =
        ContinuumDialogueTurn(
            at = 1_000L,
            input = query,
            resolvedQuery = query,
            answer = "Existing SWB1 answer",
            topic = query,
            modelHash = "a".repeat(64),
            memoryIds = ids,
            grounded = ids.isNotEmpty()
        )

    @Test fun lexicalAndCre1CompareOnlyAlreadyAuthorizedHitsWithoutReorderingAnswer() {
        val hits = listOf(hit(firstId), hit(secondId))
        val original = hits.toList()
        val dialogue = turn(hits.map { it.id })
        val baseline = AuthorizedConversationMemoryShadow.observe(dialogue, hits)
        assertEquals(AuthorizedHybridShadowStatus.LEXICAL_ONLY, baseline.status)
        assertEquals(2, baseline.authorizedCount)
        assertEquals(2, baseline.lexicalReturned)
        assertTrue(baseline.lexicalTopMatchesDialogue)
        assertFalse(baseline.cre1WouldChangeTop)

        val candidate = AuthorizedConversationMemoryShadow.observe(
            dialogue, hits,
            listOf(
                ContinuumShadowScore(firstId, 0.0, 0.6),
                ContinuumShadowScore(secondId, 1.0, 0.6)
            )
        )
        assertEquals(AuthorizedHybridShadowStatus.COMPARED_WITH_CRE1, candidate.status)
        assertTrue(candidate.cre1WouldChangeTop)
        assertFalse(candidate.cre1TopMatchesDialogue)
        assertEquals("Existing SWB1 answer", dialogue.answer)
        assertEquals(original, hits)
        assertEquals(dialogue.memoryIds, hits.map { it.id })
    }

    @Test fun unknownOrUngroundedQuestionsCannotAcquireEvidenceFromTheObserver() {
        val absent = AuthorizedConversationMemoryShadow.observe(turn(emptyList()), emptyList())
        assertEquals(AuthorizedHybridShadowStatus.NO_AUTHORIZED_EVIDENCE, absent.status)
        assertEquals(0, absent.lexicalReturned)
        assertEquals(0, absent.cre1Returned)

        val forged = AuthorizedConversationMemoryShadow.observe(turn(emptyList()), listOf(hit(firstId)))
        assertEquals(AuthorizedHybridShadowStatus.REJECTED_EVIDENCE_IDENTITY, forged.status)
        assertEquals(0, forged.authorizedCount)
    }

    @Test fun missingExtraReorderedDuplicateAndUnrecognizedHitsFailClosed() {
        val a = hit(firstId)
        val b = hit(secondId)
        val cases = listOf(
            turn(listOf(firstId, secondId)) to listOf(a),
            turn(listOf(firstId)) to listOf(a, b),
            turn(listOf(firstId, secondId)) to listOf(b, a),
            turn(listOf(firstId, firstId)) to listOf(a, a),
            turn(listOf(firstId)) to listOf(hit(firstId, kind = "MODEL_GENERATED")),
            turn(listOf(firstId)) to listOf(hit(firstId, text = "X".repeat(4_097))),
            turn(listOf(firstId)) to listOf(hit(firstId, score = Double.NaN))
        )
        for ((dialogue, hits) in cases) {
            val result = AuthorizedConversationMemoryShadow.observe(dialogue, hits)
            assertEquals(AuthorizedHybridShadowStatus.REJECTED_EVIDENCE_IDENTITY, result.status)
            assertEquals(0, result.lexicalReturned)
        }
    }

    @Test fun forgedOrPartialCre1ScoresCannotInfluenceEvenShadowRanking() {
        val hits = listOf(hit(firstId), hit(secondId))
        val cases = listOf(
            listOf(ContinuumShadowScore(firstId, 0.9, 0.6)),
            listOf(
                ContinuumShadowScore(firstId, 0.9, 0.6),
                ContinuumShadowScore("lx-cccccccccccccccccccccccc", 0.9, 0.6)
            ),
            listOf(
                ContinuumShadowScore(firstId, 0.9, 0.6),
                ContinuumShadowScore(firstId, 0.9, 0.6)
            ),
            listOf(
                ContinuumShadowScore(firstId, Double.NaN, 0.6),
                ContinuumShadowScore(secondId, 0.9, 0.6)
            ),
            listOf(
                ContinuumShadowScore(firstId, 0.9, 0.7),
                ContinuumShadowScore(secondId, 0.9, 0.6)
            )
        )
        for (scores in cases) {
            val result = AuthorizedConversationMemoryShadow.observe(
                turn(listOf(firstId, secondId)), hits, scores
            )
            assertEquals(AuthorizedHybridShadowStatus.REJECTED_CRE1_SCORES, result.status)
            assertEquals(0, result.cre1Returned)
        }
    }

    @Test fun privateOwnerTextAndSourceUrlNeverEnterDiagnosticReport() {
        val secret = "PrivateOwnerSecretNotForLogs"
        val url = "https://example.org/private-reference"
        val evidence = ContinuumMemoryHit(
            firstId, "Gemma $secret", "Unbestätigte Quellenbeobachtung",
            url, 0.7, 0.0
        )
        val result = AuthorizedConversationMemoryShadow.observe(
            turn(listOf(firstId), "Gemma"), listOf(evidence)
        )
        assertEquals(AuthorizedHybridShadowStatus.LEXICAL_ONLY, result.status)
        assertEquals(1, result.authorizedCount)
        assertFalse(result.toString().contains(secret))
        assertFalse(result.toString().contains(firstId))
        assertFalse(result.toString().contains(url))
    }

    @Test fun syntheticResearchGateAndFrozenHoldoutCannotPromoteAConversationShadow() {
        val synthetic = MemoryRecord(
            id = firstId,
            type = MemoryType.SEMANTIC,
            content = "Gemma engine",
            provenance = ProvenanceType.DIRECT_OBSERVATION,
            confidence = 0.9,
            createdAt = 0L,
            updatedAt = 0L,
            privacyClass = PrivacyClass.DEVICE_LOCAL_ONLY,
            validationState = ValidationState.VERIFIED,
            validationEvidenceReferences = listOf("synthetic-test-evidence")
        )
        val allowed = setOf(PrivacyClass.DEVICE_LOCAL_ONLY)
        val lexical = MemoryRetrievalResearchGate.evaluate(
            probes = listOf(MemoryResearchProbe(
                "synthetic", MemoryResearchAbility.STATIC_RECALL,
                "Gemma engine", setOf(firstId)
            )),
            records = listOf(synthetic),
            allowedPrivacy = allowed,
            nowMillis = 1_000L
        )
        val observer = AuthorizedConversationMemoryShadow.observe(
            turn(listOf(firstId)), listOf(hit(firstId))
        )
        assertEquals(lexical.results.single().anyExpectedWithinLimit, observer.lexicalReturned > 0)

        val development = listOf(MemoryResearchProbe(
            "dev", MemoryResearchAbility.STATIC_RECALL, "Gemma", setOf(firstId)
        ))
        val holdout = listOf(
            MemoryResearchProbe("h-static", MemoryResearchAbility.STATIC_RECALL,
                "Gemma engine", setOf(firstId)),
            MemoryResearchProbe("h-dynamic", MemoryResearchAbility.DYNAMIC_STATE,
                "Gemma state", setOf(firstId)),
            MemoryResearchProbe("h-workflow", MemoryResearchAbility.WORKFLOW,
                "Gemma workflow", setOf(firstId)),
            MemoryResearchProbe("h-environment", MemoryResearchAbility.ENVIRONMENT_GOTCHA,
                "Gemma device", setOf(firstId)),
            MemoryResearchProbe("h-unknown", MemoryResearchAbility.PREMISE_AWARENESS,
                "unrelatedwombat", emptySet())
        )
        val comparison = MemoryResearchShadowComparator.compare(
            developmentProbes = development,
            frozenHoldoutProbes = holdout,
            records = listOf(synthetic),
            allowedPrivacy = allowed,
            candidateSemanticScorer = { _, _ -> 0.0 },
            nowMillis = 1_000L
        )
        assertFalse(comparison.eligibleForOwnerReview)
        assertFalse(comparison.developmentImproved)
        assertFalse(comparison.holdoutImproved)
    }

}
