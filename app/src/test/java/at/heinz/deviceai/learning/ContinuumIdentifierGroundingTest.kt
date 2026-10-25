package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ContinuumIdentifierGroundingTest {
    private fun fact(text: String, source: Boolean = false) = LearningExperienceFactory.create(
        if (source) LearningExperienceType.SOURCE_TEXT else LearningExperienceType.USER_FACT,
        if (source) LearningProvenance.REVIEWED_WEB_SOURCE else LearningProvenance.USER_CONFIRMED,
        if (source) LearningTrustClass.SOURCE_OBSERVATION else LearningTrustClass.OWNER_CONFIRMED,
        if (source) LearningStatus.OBSERVED else LearningStatus.CONFIRMED,
        text, sourceRef = if (source) "https://example.test/research" else null, now = 1)

    @Test fun exactIdentifierGatePreservesPositivesAndRejectsWrongNumbersAcrossTrustClasses() {
        val bytes = TrainableSubwordEncoder().save()
        val before = bytes.copyOf()
        for ((correct, wrong) in listOf("ORION-47" to "ORION-48", "R1" to "R2",
            "A-7" to "A-8", "SN_15" to "SN_16", "PRJ-04" to "PRJ-4", "V4.1" to "V4.2")) {
            for (source in listOf(false, true)) {
                val record = fact("Roboter $correct wohnt in Graz.", source)
                assertEquals(listOf(record.id), ContinuumMemoryDialogue.rank(bytes,
                    "Wo wohnt $correct?", listOf(record)).map { it.id })
                assertTrue("Wrong ID must not ground an answer: $correct / $wrong",
                    ContinuumMemoryDialogue.rank(bytes, "Wo wohnt $wrong?", listOf(record)).isEmpty())
            }
        }
        assertArrayEquals(before, bytes)
    }

    @Test fun wrongIdentifierCannotGroundTheDialogueOrEnterCre1AndRevocationStillWins() {
        val dir = Files.createTempDirectory("continuum-id-grounding").toFile()
        try {
            val bytes = TrainableSubwordEncoder().save()
            val record = fact("Roboter ORION-47 wohnt in Graz.")
            val dialogue = ContinuumMemoryDialogue(dir)
            assertTrue(dialogue.replyInConversation(bytes, listOf(record),
                "Wo wohnt ORION-47?", 1, "test-chat").grounded)
            var rerankerCalled = false
            val wrong = dialogue.replyInConversation(bytes, listOf(record),
                "Wo wohnt ORION-48?", 2, "test-chat") { _, hits ->
                rerankerCalled = true
                hits
            }
            assertFalse(wrong.grounded)
            assertTrue(wrong.memoryIds.isEmpty())
            assertEquals("", wrong.topic)
            assertFalse(rerankerCalled)
            val revoked = record.copy(status = LearningStatus.REVOKED)
            assertFalse(dialogue.replyInConversation(bytes, listOf(revoked),
                "Wo wohnt ORION-47?", 3, "test-chat").grounded)
            assertFalse(dialogue.replyInConversation(bytes, listOf(revoked),
                "Und wie?", 4, "test-chat").grounded)
            assertEquals(LearningStatus.CONFIRMED, record.status)
        } finally { dir.deleteRecursively() }
    }
}
