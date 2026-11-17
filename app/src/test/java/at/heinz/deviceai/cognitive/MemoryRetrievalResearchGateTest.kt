package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MemoryRetrievalResearchGateTest {
    private fun record(
        id: String,
        content: String,
        privacy: PrivacyClass = PrivacyClass.PUBLIC,
        validation: ValidationState = ValidationState.VERIFIED
    ) = MemoryRecord(
        id = id,
        type = MemoryType.SEMANTIC,
        content = content,
        provenance = ProvenanceType.DIRECT_OBSERVATION,
        confidence = 0.9,
        createdAt = 1_000L,
        updatedAt = 1_000L,
        validationState = validation,
        privacyClass = privacy,
        validationEvidenceReferences = if (validation == ValidationState.VERIFIED)
            listOf("test-evidence:$id") else emptyList()
    )

    private fun probe(id: String, query: String, expected: Set<String>,
        ability: MemoryResearchAbility = MemoryResearchAbility.STATIC_RECALL) =
        MemoryResearchProbe(id, ability, query, expected)

    @Test fun evaluatesCurrentRetrieverWithoutCreatingAnyNewMemory() {
        val item = record("gemma", "Gemma model available offline on Android")
        val records = listOf(item)
        val report = MemoryRetrievalResearchGate.evaluate(
            probes = listOf(probe("known", "Gemma offline Android", setOf("gemma"))),
            records = records,
            allowedPrivacy = setOf(PrivacyClass.PUBLIC),
            nowMillis = 1_000L,
            nanoClock = { 0L }
        )
        assertEquals(1, report.count)
        assertEquals(1.0, report.topOneOrAbstentionAccuracy, 0.0)
        assertEquals(1.0, report.hitRateAtLimit, 0.0)
        assertEquals(1.0, report.meanReciprocalRank, 0.0)
        assertEquals(records, listOf(item))
        assertEquals(0L, report.totalDurationNanos)
        assertTrue(report.results.single().correctTopOneOrAbstention)
    }

    @Test fun missingFactsRequireActualAbstentionNotModelFabrication() {
        val report = MemoryRetrievalResearchGate.evaluate(
            listOf(probe("unknown", "unrelatedwombat", emptySet(),
                MemoryResearchAbility.PREMISE_AWARENESS)),
            listOf(record("one", "Gemma runs offline")),
            setOf(PrivacyClass.PUBLIC),
            nowMillis = 1_000L
        )
        assertTrue(report.results.single().expectedAbstention)
        assertTrue(report.results.single().correctTopOneOrAbstention)
        assertEquals(0.0, report.meanReciprocalRank, 0.0)
    }

    @Test fun retrievalOfAnyDistractorCountsAsFailedAbstention() {
        val report = MemoryRetrievalResearchGate.evaluate(
            listOf(probe("unknown", "Gemma", emptySet(),
                MemoryResearchAbility.PREMISE_AWARENESS)),
            listOf(record("one", "Gemma runs offline")),
            setOf(PrivacyClass.PUBLIC),
            nowMillis = 1_000L
        )
        assertFalse(report.results.single().correctTopOneOrAbstention)
        assertEquals(0.0, report.topOneOrAbstentionAccuracy, 0.0)
    }

    @Test fun privateFactsMustNotEnterPublicBenchmarkAndIdentifiersMustBeValid() {
        val secret = record("secret", "private model deployment detail", PrivacyClass.PRIVATE)
        try {
            MemoryRetrievalResearchGate.evaluate(
                listOf(probe("test", "deployment detail", setOf("secret"))),
                listOf(secret),
                setOf(PrivacyClass.PUBLIC)
            )
            fail("Private memory unexpectedly admitted to public evaluation")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("privacy-forbidden"))
        }
        val report = MemoryRetrievalResearchGate.evaluate(
            listOf(probe("none", "deployment detail", emptySet())),
            listOf(secret),
            setOf(PrivacyClass.PUBLIC)
        )
        assertEquals(1.0, report.topOneOrAbstentionAccuracy, 0.0)
    }

    @Test fun semanticRecallUsesExistingHybridRetrieverNotAnotherDatabase() {
        val item = record("device-heat", "handset becomes warm during long inference")
        val queries = listOf(probe("thermal", "thermal throttling", setOf("device-heat"),
            MemoryResearchAbility.ENVIRONMENT_GOTCHA))
        val baseline = MemoryRetrievalResearchGate.evaluate(
            queries, listOf(item), setOf(PrivacyClass.PUBLIC), nowMillis = 1_000L
        )
        assertEquals(0.0, baseline.hitRateAtLimit, 0.0)
        val improved = MemoryRetrievalResearchGate.evaluate(
            queries, listOf(item), setOf(PrivacyClass.PUBLIC), nowMillis = 1_000L,
            semanticScorer = { _, hit -> if (hit.id == "device-heat") 0.90 else 0.0 }
        )
        assertEquals(1.0, improved.hitRateAtLimit, 0.0)
        assertEquals(1.0, improved.meanReciprocalRank, 0.0)
    }

    @Test fun validatedRecentFactsTakePrecedenceOverContradictedOutdatedFacts() {
        val current = record("new", "latest approved model is Gemma")
        val old = record("old", "latest approved model is Gemma",
            validation = ValidationState.CONTRADICTED)
        val report = MemoryRetrievalResearchGate.evaluate(
            listOf(probe("temporal", "latest approved model", setOf("new"),
                MemoryResearchAbility.DYNAMIC_STATE)),
            listOf(old, current), setOf(PrivacyClass.PUBLIC), nowMillis = 1_000L
        )
        assertTrue(report.results.single().correctTopOneOrAbstention)
    }

    @Test fun duplicateCasesAndInvalidPrivacyFailClosed() {
        val one = probe("p", "Gemma", setOf("one"))
        val record = record("one", "Gemma runs offline")
        for (probes in listOf(emptyList(), listOf(one, one))) {
            try {
                MemoryRetrievalResearchGate.evaluate(probes, listOf(record),
                    setOf(PrivacyClass.PUBLIC))
                fail("Invalid probe collection was accepted")
            } catch (_: IllegalArgumentException) {}
        }
        try {
            MemoryRetrievalResearchGate.evaluate(listOf(one), listOf(record), emptySet())
            fail("Empty privacy allowlist was accepted")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun duplicateRecordIdsDoNotCountAsIndependentEvidence() {
        val duplicated = record("same", "Gemma runs offline")
        try {
            MemoryRetrievalResearchGate.evaluate(
                listOf(probe("dupe", "Gemma", setOf("same"))),
                listOf(duplicated, duplicated.copy(content = "Gemma does not run offline")),
                setOf(PrivacyClass.PUBLIC),
                nowMillis = 1_000L
            )
            fail("Ambiguous duplicate memory IDs were accepted")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("Duplicate memory identifiers"))
        }
    }

    @Test fun noModelTrainingToolAuthorityOrExternalDataInReport() {
        val secretText = "confidential benchmark memory"
        val item = record("private", secretText, PrivacyClass.PRIVATE)
        val report = MemoryRetrievalResearchGate.evaluate(
            listOf(probe("privacy", "confidential benchmark", setOf("private"),
                MemoryResearchAbility.WORKFLOW)),
            listOf(item), setOf(PrivacyClass.PRIVATE), nowMillis = 1_000L
        )
        assertEquals(1.0, report.meanReciprocalRank, 0.0)
        assertFalse(report.toString().contains(secretText))
        assertEquals("private", item.id)
    }
}
