package at.heinz.deviceai

import at.heinz.deviceai.learning.ContinuumLearningCore
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ContinuumDialogueSessionResetTest {
    @Test fun explicitNewSessionDropsOldTopicWithoutChangingOwnerFactsOrWeights() {
        val dir = Files.createTempDirectory("continuum-session-reset").toFile()
        try {
            val core = ContinuumLearningCore(dir)
            core.confirmOwnerFact("Max wohnt in Graz.")
            core.configureContextShadow(true)
            assertTrue(core.replyFromMemory("Wo wohnt Max?", "old-chat").grounded)
            val facts = core.activeOwnerFacts()
            val weights = core.modelSnapshot()
            assertTrue(core.contextShadowEvents("old-chat").isNotEmpty())

            core.resetDialogueConversation("old-chat")

            val historyAfterReset = core.dialogueHistory()
            assertTrue(core.contextShadowEvents("old-chat").isEmpty())
            assertEquals(facts, core.activeOwnerFacts())
            assertArrayEquals(weights, core.modelSnapshot())
            val reopened = ContinuumLearningCore(dir)
            val continuation = reopened.replyFromMemory("und wie?", "new-chat")
            assertEquals("und wie?", continuation.resolvedQuery)
            assertFalse("A new chat must not borrow the previous chat's subject", continuation.grounded)
            assertTrue("Explicit new session must discard the old baseline topic", historyAfterReset.isEmpty())
            assertTrue(reopened.replyFromMemory("Wo wohnt Max?", "new-chat").grounded)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun reopeningCoreWithoutExplicitResetPreservesNormalContinuation() {
        val dir = Files.createTempDirectory("continuum-session-restore").toFile()
        try {
            val core = ContinuumLearningCore(dir)
            core.confirmOwnerFact("Max wohnt in Graz.")
            assertTrue(core.replyFromMemory("Wo wohnt Max?", "same-chat").grounded)
            val reopened = ContinuumLearningCore(dir)
            val continuation = reopened.replyFromMemory("und wie?", "same-chat")
            assertEquals("Wo wohnt Max? und wie?", continuation.resolvedQuery)
            assertTrue(continuation.grounded)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun invalidConversationIdCannotDiscardExistingDialogue() {
        val dir = Files.createTempDirectory("continuum-invalid-session-reset").toFile()
        try {
            val core = ContinuumLearningCore(dir)
            core.confirmOwnerFact("Max wohnt in Graz.")
            core.replyFromMemory("Wo wohnt Max?", "same-chat")
            val history = core.dialogueHistory()
            val facts = core.activeOwnerFacts()
            val weights = core.modelSnapshot()
            assertThrows(IllegalArgumentException::class.java) {
                core.resetDialogueConversation("not a valid conversation")
            }
            assertEquals(history, core.dialogueHistory())
            assertEquals(facts, core.activeOwnerFacts())
            assertArrayEquals(weights, core.modelSnapshot())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun ownerFactWithdrawalStillAppliesAfterExplicitReset() {
        val dir = Files.createTempDirectory("continuum-reset-withdrawal").toFile()
        try {
            val core = ContinuumLearningCore(dir)
            val fact = "Max wohnt in Graz."
            core.confirmOwnerFact(fact)
            core.replyFromMemory("Wo wohnt Max?", "old-chat")
            core.resetDialogueConversation("old-chat")
            assertEquals(1, core.revokeOwnerFact(fact))
            assertTrue(core.activeOwnerFacts().isEmpty())
            assertFalse(core.replyFromMemory("Wo wohnt Max?", "new-chat").grounded)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun shadowOnlyResetRemainsCompatibleAndDoesNotDiscardBaselineHistory() {
        val dir = Files.createTempDirectory("continuum-shadow-only-reset").toFile()
        try {
            val core = ContinuumLearningCore(dir)
            core.confirmOwnerFact("Max wohnt in Graz.")
            core.configureContextShadow(true)
            core.replyFromMemory("Wo wohnt Max?", "same-chat")
            val history = core.dialogueHistory()
            core.resetContextConversation("same-chat")
            assertEquals(history, core.dialogueHistory())
            assertTrue(core.contextShadowEvents("same-chat").isEmpty())
            assertTrue(core.replyFromMemory("und wie?", "same-chat").grounded)
        } finally {
            dir.deleteRecursively()
        }
    }
}
