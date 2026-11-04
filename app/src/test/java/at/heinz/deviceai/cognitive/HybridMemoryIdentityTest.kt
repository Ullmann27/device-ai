package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridMemoryIdentityTest {
    @Test
    fun conflictingContentsCannotShareAnEvidenceIdentity() {
        val first = memory("collision", "Werkstatt schließt am Montag.")
        val conflicting = first.copy(content = "Werkstatt schließt am Freitag.")
        val safe = memory("safe", "Werkstatt ist geöffnet.")

        for (records in listOf(listOf(first, safe, conflicting), listOf(conflicting, safe, first))) {
            assertEquals(
                listOf("safe"),
                retrieve("Werkstatt", records).map { it.memory.id }
            )
        }
    }

    @Test
    fun identicalTextDoesNotHideConflictingProvenanceOrEvidence() {
        val first = memory("collision", "Werkstatt ist geöffnet.")
        val conflictingVariants = listOf(
            first.copy(provenance = ProvenanceType.MODEL_GENERATED),
            first.copy(sourceReference = "source://other"),
            first.copy(validationState = ValidationState.CONTRADICTED),
            first.copy(validationEvidenceReferences = listOf("evidence://other")),
            first.copy(updatedAt = NOW + 1),
            first.copy(tags = listOf("owner")),
            first.copy(parentIds = listOf("parent")),
            first.copy(confidence = 0.5),
            first.copy(type = MemoryType.SOURCE)
        )

        for (conflicting in conflictingVariants) {
            assertTrue(
                "Conflicting identity remained retrievable: $conflicting",
                retrieve("Werkstatt", listOf(first, conflicting)).isEmpty()
            )
        }
    }

    @Test
    fun privacyConflictsAreRejectedBeforeAnyScorerSeesEitherVariant() {
        val publicAlias = memory("collision", "Werkstatt ist geöffnet.")
        val hiddenVariant = publicAlias.copy(
            content = "Synthetische vertrauliche Werkstattnotiz.",
            privacyClass = PrivacyClass.SENSITIVE
        )
        val safe = memory("safe", "Werkstatt ist geöffnet.")

        for (records in listOf(listOf(publicAlias, safe, hiddenVariant), listOf(hiddenVariant, safe, publicAlias))) {
            val semanticIds = mutableListOf<String>()
            val trustIds = mutableListOf<String>()
            val result = HybridMemoryRetriever.retrieve(
                query = "Werkstatt",
                records = records,
                nowMillis = NOW,
                allowedPrivacy = setOf(PrivacyClass.PUBLIC),
                semanticScorer = { _, record -> semanticIds.add(record.id); 0.5 },
                sourceTrustScorer = { record -> trustIds.add(record.id); 0.5 }
            )

            assertEquals(listOf("safe"), result.map { it.memory.id })
            assertEquals(listOf("safe"), semanticIds)
            assertEquals(listOf("safe"), trustIds)
        }
    }

    @Test
    fun repeatedIdenticalRecordsHaveTheSameScoresAsOneRecord() {
        val repeated = memory("repeated", "Werkstatt öffnet am Montag.")
        val other = memory("other", "Werkstatt öffnet am Dienstag.")
        val expected = retrieve("Werkstatt Montag", listOf(repeated, other))
        val semanticIds = mutableListOf<String>()

        val actual = HybridMemoryRetriever.retrieve(
            query = "Werkstatt Montag",
            records = listOf(repeated, other, repeated.copy(), repeated),
            nowMillis = NOW,
            semanticScorer = { _, record -> semanticIds.add(record.id); 0.0 }
        )

        assertEquals(expected, actual)
        assertEquals(listOf("repeated", "other"), semanticIds)
    }

    @Test
    fun aThirdCopyCannotReviveAnAlreadyConflictingIdentity() {
        val first = memory("collision", "Werkstatt öffnet am Montag.")
        val conflicting = first.copy(content = "Werkstatt öffnet am Freitag.")

        assertTrue(retrieve("Werkstatt", listOf(first, conflicting, first.copy())).isEmpty())
    }

    @Test
    fun aLateConflictIsRejectedEvenWhenTheResultLimitIsOne() {
        val first = memory("collision", "Werkstatt öffnet am Montag.")
        val safe = memory("safe", "Werkstatt öffnet am Dienstag.")
        val irrelevant = (0 until 40).map { memory("irrelevant-$it", "Neutrale Wetterbeobachtung.") }
        val result = HybridMemoryRetriever.retrieve(
            query = "Werkstatt Montag",
            records = listOf(first, safe) + irrelevant + first.copy(content = "Werkstatt öffnet am Freitag."),
            limit = 1,
            nowMillis = NOW
        )

        assertEquals(listOf("safe"), result.map { it.memory.id })
    }

    @Test
    fun hiddenUniqueRecordsNeverReachScorersOrChangeVisibleScores() {
        val safe = memory("safe", "Werkstatt ist geöffnet.")
        val hidden = memory("hidden", "Werkstatt Werkstatt Werkstatt.").copy(privacyClass = PrivacyClass.PRIVATE)
        val expected = HybridMemoryRetriever.retrieve(
            "Werkstatt", listOf(safe), nowMillis = NOW, allowedPrivacy = setOf(PrivacyClass.PUBLIC)
        )
        val semanticIds = mutableListOf<String>()
        val actual = HybridMemoryRetriever.retrieve(
            "Werkstatt", listOf(hidden, safe), nowMillis = NOW,
            allowedPrivacy = setOf(PrivacyClass.PUBLIC),
            semanticScorer = { _, record -> semanticIds.add(record.id); 0.0 },
            sourceTrustScorer = { record -> assertEquals("safe", record.id); 0.5 }
        )

        assertEquals(expected, actual)
        assertEquals(listOf("safe"), semanticIds)
    }

    private fun retrieve(query: String, records: List<MemoryRecord>) =
        HybridMemoryRetriever.retrieve(query, records, nowMillis = NOW)

    private fun memory(id: String, content: String) = MemoryRecord(
        id = id,
        type = MemoryType.SEMANTIC,
        content = content,
        provenance = ProvenanceType.USER_PROVIDED,
        confidence = 0.9,
        createdAt = NOW,
        updatedAt = NOW,
        privacyClass = PrivacyClass.PUBLIC
    )

    private companion object {
        const val NOW = 1_730_678_400_000L
    }
}
